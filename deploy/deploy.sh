#!/usr/bin/env bash
# 서버에서 실행: app을 지정한 이미지 태그로 바꾸고, health가 통과하지 않으면 이전 태그로 되돌린다
set -euo pipefail

ENV_FILE=.env
PREVIOUS_TAG_FILE=.previous-tag   # 직전에 돌던 태그. rollback.sh가 읽는다
HISTORY_FILE=deploy-history.log
LOCK_DIR=.deploy.lock
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}
ASSUME_YES=false

usage() {
	cat <<'EOF'
사용법: ./deploy.sh [-y] <IMAGE_TAG>

  .env의 IMAGE_TAG를 바꾸고 app만 새 이미지로 교체한다 (nginx·db는 그대로).
    1. .env의 IMAGE_TAG 갱신
    2. docker compose pull app  (실패하면 .env만 원래대로 돌리고 끝. 실행 중인 app은 그대로)
    3. docker compose up -d app
    4. app이 healthy가 될 때까지 대기 (최대 HEALTH_TIMEOUT초)
    5. 실패하면 이전 태그로 자동 복구
  성공하면 교체 전 태그를 .previous-tag에 기록한다 (rollback.sh가 사용).

환경변수:
  HEALTH_TIMEOUT  (선택) healthy 대기 시간(초). 기본값 180

옵션:
  -y  확인 질문 없이 진행
  -h  이 도움말

예: ./deploy.sh 8f83b08
EOF
}

die() {
	echo "오류: $*" >&2
	exit 1
}

confirm() {
	[ "$ASSUME_YES" = true ] && return 0
	[ -t 0 ] || die "확인 입력을 받을 수 없습니다(터미널이 아님). 진행하려면 -y를 붙이세요."
	local answer
	read -r -p "$1 [y/N] " answer
	case "$answer" in
		y | Y | yes | YES) ;;
		*) echo "취소했습니다." && exit 1 ;;
	esac
}

log_history() {
	echo "$(date '+%Y-%m-%d %H:%M:%S %z') $*" >>"$HISTORY_FILE"
}

current_tag() {
	sed -n 's/^IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1
}

# .env의 IMAGE_TAG 줄만 바꾼다. 기존 파일에 덮어써서 권한(600)과 소유자를 유지한다
set_tag() {
	local tmp
	tmp=$(mktemp "$ENV_FILE.XXXXXX")
	awk -v tag="$1" '
		/^IMAGE_TAG=/ { print "IMAGE_TAG=" tag; found = 1; next }
		{ print }
		END { if (!found) print "IMAGE_TAG=" tag }
	' "$ENV_FILE" >"$tmp"
	cat "$tmp" >"$ENV_FILE"
	rm -f "$tmp"
}

# app 컨테이너가 healthy가 되면 0, unhealthy·재시작·시간 초과면 1
wait_healthy() {
	local cid status restarts deadline
	cid=$(docker compose ps -q app)
	[ -n "$cid" ] || { echo "  app 컨테이너를 찾지 못했습니다." >&2; return 1; }
	deadline=$(($(date +%s) + HEALTH_TIMEOUT))
	while :; do
		status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")
		restarts=$(docker inspect -f '{{.RestartCount}}' "$cid")
		case "$status" in
			healthy) return 0 ;;
			unhealthy) echo "  app이 unhealthy 상태입니다." >&2 && return 1 ;;
		esac
		if [ "$restarts" -gt 0 ]; then
			echo "  app이 시작 중에 종료돼 재시작됐습니다 (${restarts}회)." >&2
			return 1
		fi
		if [ "$(date +%s)" -ge "$deadline" ]; then
			echo "  ${HEALTH_TIMEOUT}초 안에 healthy가 되지 않았습니다 (현재: $status)." >&2
			return 1
		fi
		printf '.'
		sleep 3
	done
}

# 같은 태그로 다시 배포해도 컨테이너를 새로 만들어, 재시작 횟수·health를 새 컨테이너 기준으로 본다
start_app() {
	docker compose up -d --force-recreate app && wait_healthy
}

check_via_nginx() {
	command -v curl >/dev/null 2>&1 || return 0
	if curl -fsS --max-time 5 http://localhost/actuator/health >/dev/null 2>&1; then
		echo "nginx 경유 확인: http://localhost/actuator/health OK"
	else
		echo "경고: nginx 경유 health 확인에 실패했습니다. 'docker compose ps'로 nginx 상태를 확인하세요." >&2
	fi
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
NEW_TAG=$1

# 태그가 .env에 그대로 들어가므로 허용 문자를 제한한다
printf '%s' "$NEW_TAG" | grep -Eq '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$' || die "태그 형식이 올바르지 않습니다: $NEW_TAG"
[ "$NEW_TAG" != latest ] || die "latest 태그는 쓰지 않습니다. 커밋 해시 태그를 지정하세요."

cd "$(dirname "$0")"
[ -f docker-compose.yml ] || die "docker-compose.yml이 없습니다. 배포 폴더에서 실행하세요."
[ -f "$ENV_FILE" ] || die "$ENV_FILE 이 없습니다. .env.example을 복사해 값을 채우세요."

# 두 배포가 동시에 .env를 고치지 않도록 잠근다
mkdir "$LOCK_DIR" 2>/dev/null || die "다른 배포가 진행 중입니다. 아니라면 $LOCK_DIR 폴더를 지우고 다시 실행하세요."
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

OLD_TAG=$(current_tag)

echo "배포 폴더: $(pwd)"
echo "현재 태그: ${OLD_TAG:-(없음)}"
echo "새 태그  : $NEW_TAG"
[ "$NEW_TAG" != "$OLD_TAG" ] || echo "현재와 같은 태그입니다. 진행하면 app을 같은 이미지로 다시 확인합니다."
confirm "app을 $NEW_TAG 로 교체할까요?"

echo
echo "[1/3] .env 갱신, 이미지 받기"
set_tag "$NEW_TAG"
if ! docker compose pull app; then
	set_tag "$OLD_TAG"
	log_history "deploy ${OLD_TAG:-none} -> $NEW_TAG FAILED (pull)"
	die "이미지를 받지 못했습니다. .env를 ${OLD_TAG:-(없음)}로 되돌렸고, 실행 중인 app은 바뀌지 않았습니다."
fi

echo
echo "[2/3] app 교체, healthy 대기 (최대 ${HEALTH_TIMEOUT}초)"
if start_app; then
	echo
	if [ -n "$OLD_TAG" ] && [ "$OLD_TAG" != "$NEW_TAG" ]; then
		echo "$OLD_TAG" >"$PREVIOUS_TAG_FILE"
	fi
	log_history "deploy ${OLD_TAG:-none} -> $NEW_TAG OK"
	echo "[3/3] 완료: app이 $NEW_TAG 로 healthy 상태입니다."
	check_via_nginx
	exit 0
fi

echo
echo "새 app의 최근 로그:" >&2
docker compose logs --tail 40 app >&2 || true

if [ -z "$OLD_TAG" ] || [ "$OLD_TAG" = "$NEW_TAG" ]; then
	log_history "deploy ${OLD_TAG:-none} -> $NEW_TAG FAILED (되돌릴 이전 태그 없음)"
	die "$NEW_TAG 배포에 실패했고, 되돌릴 이전 태그가 없습니다."
fi

echo
echo "[3/3] 이전 태그 $OLD_TAG 로 복구"
set_tag "$OLD_TAG"
if start_app; then
	echo
	log_history "deploy $OLD_TAG -> $NEW_TAG FAILED, $OLD_TAG 로 복구"
	check_via_nginx
	die "$NEW_TAG 배포에 실패해 $OLD_TAG 로 복구했습니다. 위 로그에서 원인을 확인하세요."
fi

log_history "deploy $OLD_TAG -> $NEW_TAG FAILED, $OLD_TAG 복구도 실패"
echo "오류: $OLD_TAG 로 복구하는 것도 실패했습니다. 'docker compose logs app'을 확인하세요." >&2
exit 2
