#!/usr/bin/env bash
# 서버에서 실행(cron): db 컨테이너에서 pg_dump → gzip → 날짜별 파일, 오래된 파일 삭제, 선택적으로 S3 업로드
set -euo pipefail

ENV_FILE=.env
LOCK_DIR=.backup.lock

usage() {
	cat <<'EOF'
사용법: ./backup.sh

  db 컨테이너의 DB를 pg_dump로 덤프해 gzip으로 압축하고 BACKUP_DIR에 날짜별 파일로 저장한다.
    <BACKUP_DIR>/<DB_NAME>-YYYYmmdd-HHMMSS.sql.gz   (권한 600)
  저장에 성공한 뒤 RETENTION_DAYS일 이상 지난 백업 파일을 지운다.
  BACKUP_S3_BUCKET이 있으면 S3에도 올린다. 없으면 로컬에만 보관한다.
  확인 질문 없이 실행된다(cron용). 데이터를 바꾸지 않는다.

설정 (환경변수가 우선, 없으면 .env의 같은 키, 둘 다 없으면 기본값):
  BACKUP_DIR        백업 폴더. 기본값 ./backups (이 스크립트가 있는 폴더 기준)
  RETENTION_DAYS    로컬 보관 일수. 기본값 7
  BACKUP_S3_BUCKET  (선택) S3 버킷 이름. 예: family-app-backups
  BACKUP_S3_PREFIX  (선택) S3 경로 앞부분. 기본값 db

S3 업로드에는 aws CLI와 권한이 필요하다. 액세스 키를 파일에 두지 말고 EC2 인스턴스 역할(IAM role)을 쓴다.

옵션:
  -h  이 도움말

cron 예 (매일 03:00, 서버 시간대 기준. BACKUP.md 참고):
  0 3 * * * cd /opt/family-app && ./backup.sh >> backups/backup.log 2>&1
EOF
}

log() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

die() {
	log "오류: $*" >&2
	exit 1
}

# .env에서 키 하나만 읽는다. 비밀값이 든 .env를 통째로 source하지 않는다
env_value() {
	[ -f "$ENV_FILE" ] || return 0
	sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1
}

# 환경변수 → .env → 기본값 순서
setting() {
	local from_env
	from_env=$(printenv "$1" || true)
	if [ -n "$from_env" ]; then
		echo "$from_env"
		return
	fi
	from_env=$(env_value "$1")
	echo "${from_env:-$2}"
}

while getopts "h" opt; do
	case "$opt" in
		h) usage && exit 0 ;;
		*) usage >&2 && exit 1 ;;
	esac
done
shift $((OPTIND - 1))
[ $# -eq 0 ] || { usage >&2; exit 1; }

cd "$(dirname "$0")"
[ -f docker-compose.yml ] || die "docker-compose.yml이 없습니다. 배포 폴더에서 실행하세요."
[ -f "$ENV_FILE" ] || die "$ENV_FILE 이 없습니다."
# cron은 PATH가 짧아(/usr/bin:/bin) docker를 못 찾는 경우가 많다
command -v docker >/dev/null 2>&1 || die "docker를 찾을 수 없습니다 (PATH=$PATH). cron이라면 crontab에 PATH를 지정하세요 (BACKUP.md)."

BACKUP_DIR=$(setting BACKUP_DIR ./backups)
RETENTION_DAYS=$(setting RETENTION_DAYS 7)
S3_BUCKET=$(setting BACKUP_S3_BUCKET "")
S3_PREFIX=$(setting BACKUP_S3_PREFIX db)
DB_NAME=$(env_value DB_NAME)

[ -n "$DB_NAME" ] || die ".env의 DB_NAME이 비어 있습니다."
case "$RETENTION_DAYS" in
	'' | *[!0-9]*) die "RETENTION_DAYS는 1 이상의 정수여야 합니다: $RETENTION_DAYS" ;;
esac
[ "$RETENTION_DAYS" -ge 1 ] || die "RETENTION_DAYS는 1 이상이어야 합니다."

# 백업에는 개인정보와 비밀번호 해시가 들어 있다. 만드는 파일·폴더를 소유자만 읽게 한다
umask 077
mkdir -p "$BACKUP_DIR"

# cron이 이전 백업이 끝나기 전에 다시 실행해도 겹치지 않게 한다
mkdir "$LOCK_DIR" 2>/dev/null || die "다른 백업이 진행 중입니다. 아니라면 $LOCK_DIR 폴더를 지우고 다시 실행하세요."
TMP_FILE=""
cleanup() {
	[ -z "$TMP_FILE" ] || rm -f "$TMP_FILE"
	rmdir "$LOCK_DIR" 2>/dev/null || true
}
trap cleanup EXIT

[ -n "$(docker compose ps -q --status running db)" ] || die "db 컨테이너가 실행 중이 아닙니다. 'docker compose up -d db'"

FILE_NAME="$DB_NAME-$(date '+%Y%m%d-%H%M%S').sql.gz"
FINAL_FILE="$BACKUP_DIR/$FILE_NAME"
TMP_FILE="$BACKUP_DIR/.$FILE_NAME.partial"

log "백업 시작: $DB_NAME → $FINAL_FILE"

# 덤프가 중간에 실패하면 pipefail로 멈추고, 임시 파일은 cleanup이 지운다.
# --no-owner/--no-privileges: 다른 계정 이름으로 만든 DB에도 그대로 복구할 수 있게 한다
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges' |
	gzip -9 >"$TMP_FILE"

gzip -t "$TMP_FILE" || die "압축 파일 검사에 실패했습니다: $TMP_FILE"
# 정상 덤프는 마지막에 이 주석으로 끝난다. 없으면 중간에 끊긴 덤프다
gzip -dc "$TMP_FILE" | tail -n 5 | grep -q 'PostgreSQL database dump complete' ||
	die "덤프가 끝까지 기록되지 않았습니다."

mv "$TMP_FILE" "$FINAL_FILE"
TMP_FILE=""
log "백업 완료: $FINAL_FILE ($(du -h "$FINAL_FILE" | cut -f1))"

# 이번 백업이 성공한 뒤에만 지운다. 이름 규칙이 맞는 백업 파일만 대상으로 한다
# -mtime +N: 마지막 수정 후 (N+1)일 이상 지남 → RETENTION_DAYS일 이상 지난 파일
deleted=$(find "$BACKUP_DIR" -maxdepth 1 -type f -name "$DB_NAME-*.sql.gz" -mtime +$((RETENTION_DAYS - 1)) -print -delete)
if [ -n "$deleted" ]; then
	log "${RETENTION_DAYS}일 이상 지난 백업 삭제:"
	while IFS= read -r f; do echo "  $f"; done <<<"$deleted"
fi

if [ -z "$S3_BUCKET" ]; then
	log "BACKUP_S3_BUCKET이 없어 로컬에만 보관합니다."
	exit 0
fi

command -v aws >/dev/null 2>&1 || die "BACKUP_S3_BUCKET이 설정됐지만 aws CLI가 없습니다. 로컬 백업은 저장됐습니다: $FINAL_FILE"
S3_URI="s3://$S3_BUCKET/${S3_PREFIX:+$S3_PREFIX/}$FILE_NAME"
log "S3 업로드: $S3_URI"
aws s3 cp "$FINAL_FILE" "$S3_URI" --only-show-errors ||
	die "S3 업로드에 실패했습니다. 로컬 백업은 저장됐습니다: $FINAL_FILE"
log "S3 업로드 완료"
