#!/usr/bin/env bash
# 서버에서 실행: 직전 배포 태그(.previous-tag)로 되돌린다
set -euo pipefail

PREVIOUS_TAG_FILE=.previous-tag
ASSUME_YES=false

usage() {
	cat <<'EOF'
사용법: ./rollback.sh [-y]

  .previous-tag에 기록된 직전 태그로 deploy.sh를 실행한다.
  배포와 같은 절차(pull → 교체 → healthy 대기 → 실패 시 자동 복구)를 거친다.
  되돌린 뒤에는 되돌리기 전 태그가 .previous-tag에 남으므로, 한 번 더 실행하면 원래 태그로 돌아간다.

옵션:
  -y  확인 질문 없이 진행
  -h  이 도움말
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

while getopts "yh" opt; do
	case "$opt" in
		y) ASSUME_YES=true ;;
		h) usage && exit 0 ;;
		*) usage >&2 && exit 1 ;;
	esac
done
shift $((OPTIND - 1))
[ $# -eq 0 ] || { usage >&2; exit 1; }

cd "$(dirname "$0")"
[ -f "$PREVIOUS_TAG_FILE" ] || die "$PREVIOUS_TAG_FILE 이 없습니다. deploy.sh로 한 번 이상 태그를 바꾼 뒤에 되돌릴 수 있습니다."
[ -f .env ] || die ".env가 없습니다. 배포 폴더에서 실행하세요."

PREVIOUS_TAG=$(head -n 1 "$PREVIOUS_TAG_FILE")
CURRENT_TAG=$(sed -n 's/^IMAGE_TAG=//p' .env | tail -n 1)
[ -n "$PREVIOUS_TAG" ] || die "$PREVIOUS_TAG_FILE 이 비어 있습니다."

echo "현재 태그: ${CURRENT_TAG:-(없음)}"
echo "되돌릴 태그: $PREVIOUS_TAG"
confirm "app을 $PREVIOUS_TAG 로 되돌릴까요?"

exec ./deploy.sh -y "$PREVIOUS_TAG"
