#!/usr/bin/env bash
# 서버에서 실행: backup.sh가 만든 백업 파일로 DB를 통째로 되돌린다 (현재 데이터는 사라진다)
set -euo pipefail

ENV_FILE=.env
LOCK_DIR=.deploy.lock   # deploy.sh와 같은 잠금. 복구 중에 배포가 app을 다시 띄우지 않게 한다
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}
ASSUME_YES=false

usage() {
	cat <<'EOF'
사용법: ./restore.sh [-y] <백업 파일(.sql.gz)>

  backup.sh가 만든 백업으로 DB를 통째로 되돌린다. 백업 이후에 생긴 데이터는 사라진다.
    1. 백업 파일 검사 (gzip 무결성, 덤프가 끝까지 기록됐는지)
    2. 확인: DB 이름을 직접 입력해야 진행 (-y면 생략)
    3. 현재 DB를 <BACKUP_DIR>/pre-restore-<DB>-<시각>.sql.gz로 덤프 (잘못 복구했을 때 되돌릴 용도)
    4. app 중지 (복구 중 쓰기 방지. 이 동안 nginx는 502)
    5. DB를 지우고 새로 만든 뒤 백업 적용 (한 트랜잭션, 오류가 나면 바로 멈춤)
    6. app 시작, healthy 대기 (Flyway가 백업의 스키마 버전을 확인·필요하면 이후 마이그레이션 적용)

  S3에 있는 백업은 먼저 받는다: aws s3 cp s3://<버킷>/db/<파일> ./backups/

환경변수:
  BACKUP_DIR      안전 백업을 저장할 폴더. 기본값 .env의 BACKUP_DIR, 없으면 ./backups
  HEALTH_TIMEOUT  app healthy 대기 시간(초). 기본값 180

옵션:
  -y  확인 입력 없이 진행 (복구 훈련 자동화용. 운영에서는 쓰지 않는다)
  -h  이 도움말
EOF
}

log() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

die() {
	log "오류: $*" >&2
	exit 1
}

env_value() {
	sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1
}

wait_app_healthy() {
	local cid status deadline
	cid=$(docker compose ps -q app)
	[ -n "$cid" ] || return 1
	deadline=$(($(date +%s) + HEALTH_TIMEOUT))
	while :; do
		status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")
		[ "$status" = healthy ] && return 0
		[ "$status" = unhealthy ] && return 1
		[ "$(date +%s)" -lt "$deadline" ] || return 1
		sleep 3
	done
}

while getopts "yh" opt; do
	case "$opt" in
		y) ASSUME_YES=true ;;
		h) usage && exit 0 ;;
		*) usage >&2 && exit 1 ;;
	esac
done
shift $((OPTIND - 1))
[ $# -eq 1 ] || { usage >&2; exit 1; }

# 상대 경로로 받은 파일을 배포 폴더로 이동한 뒤에도 찾을 수 있게 절대 경로로 바꾼다
BACKUP_FILE=$1
[ -f "$BACKUP_FILE" ] || die "백업 파일이 없습니다: $BACKUP_FILE"
BACKUP_FILE="$(cd "$(dirname "$BACKUP_FILE")" && pwd)/$(basename "$BACKUP_FILE")"

cd "$(dirname "$0")"
[ -f docker-compose.yml ] || die "docker-compose.yml이 없습니다. 배포 폴더에서 실행하세요."
[ -f "$ENV_FILE" ] || die "$ENV_FILE 이 없습니다."
command -v docker >/dev/null 2>&1 || die "docker를 찾을 수 없습니다 (PATH=$PATH)."
DB_NAME=$(env_value DB_NAME)
[ -n "$DB_NAME" ] || die ".env의 DB_NAME이 비어 있습니다."
BACKUP_DIR=${BACKUP_DIR:-$(env_value BACKUP_DIR)}
BACKUP_DIR=${BACKUP_DIR:-./backups}

log "백업 파일 검사: $BACKUP_FILE"
gzip -t "$BACKUP_FILE" || die "gzip 파일이 손상됐습니다."
gzip -dc "$BACKUP_FILE" | tail -n 5 | grep -q 'PostgreSQL database dump complete' ||
	die "덤프가 끝까지 기록되지 않은 파일입니다."
gzip -dc "$BACKUP_FILE" | grep -q 'flyway_schema_history' ||
	die "이 앱의 백업이 아닌 것 같습니다 (flyway_schema_history 없음)."

echo
echo "  대상 DB   : $DB_NAME  ($(pwd))"
echo "  백업 파일 : $(basename "$BACKUP_FILE")  ($(du -h "$BACKUP_FILE" | cut -f1), $(date -r "$BACKUP_FILE" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo '?'))"
echo "  현재 DB의 모든 데이터가 이 백업 시점으로 바뀝니다. 그 이후 데이터는 사라집니다."
echo "  (현재 DB는 복구 전에 $BACKUP_DIR/pre-restore-*.sql.gz 로 따로 덤프합니다)"
echo
if [ "$ASSUME_YES" != true ]; then
	[ -t 0 ] || die "확인 입력을 받을 수 없습니다(터미널이 아님). 진행하려면 -y를 붙이세요."
	read -r -p "진행하려면 DB 이름($DB_NAME)을 입력하세요: " answer
	[ "$answer" = "$DB_NAME" ] || { echo "입력이 달라 취소했습니다."; exit 1; }
fi

mkdir "$LOCK_DIR" 2>/dev/null || die "배포나 다른 복구가 진행 중입니다. 아니라면 $LOCK_DIR 폴더를 지우고 다시 실행하세요."
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

log "[1/5] db 준비"
docker compose up -d --wait db >/dev/null

log "[2/5] 현재 DB를 안전 백업으로 덤프"
umask 077
mkdir -p "$BACKUP_DIR"
SAFETY_FILE="$BACKUP_DIR/pre-restore-$DB_NAME-$(date '+%Y%m%d-%H%M%S').sql.gz"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' |
	gzip -9 >"$SAFETY_FILE"
gzip -t "$SAFETY_FILE"
log "  $SAFETY_FILE"

log "[3/5] app 중지"
docker compose stop app >/dev/null

log "[4/5] DB를 새로 만들고 백업 적용"
# WITH (FORCE): 남은 연결이 있어도 끊고 지운다 (PostgreSQL 13+)
docker compose exec -T db sh -c '
	psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -q \
		-c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" \
		-c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\""'
if ! gzip -dc "$BACKUP_FILE" |
	docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q --single-transaction' >/dev/null; then
	log "오류: 백업 적용에 실패했습니다. app은 멈춘 상태입니다." >&2
	log "  복구 전 상태로 돌아가려면: ./restore.sh $SAFETY_FILE" >&2
	exit 1
fi

log "[5/5] app 시작, healthy 대기 (최대 ${HEALTH_TIMEOUT}초)"
docker compose up -d app >/dev/null
if ! wait_app_healthy; then
	docker compose logs --tail 40 app >&2 || true
	log "오류: 복구는 끝났지만 app이 healthy가 되지 않았습니다. 위 로그를 확인하세요." >&2
	log "  백업의 스키마가 지금 앱 버전보다 새로우면 스키마 검증에 실패할 수 있습니다 (BACKUP.md의 Flyway 표 참고)." >&2
	log "  복구 전 상태로 돌아가려면: ./restore.sh $SAFETY_FILE" >&2
	exit 1
fi

log "복구 완료: $DB_NAME ← $(basename "$BACKUP_FILE")"
log "복구 전 상태 덤프: $SAFETY_FILE"
