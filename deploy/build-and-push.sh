#!/usr/bin/env bash
# 로컬/CI에서 실행: 테스트·빌드 → 이미지 빌드 → 레지스트리에 push
set -euo pipefail

IMAGE_NAME=family-app
PLATFORM=${PLATFORM:-linux/amd64}
ASSUME_YES=false

usage() {
	cat <<'EOF'
사용법: IMAGE_REGISTRY=<레지스트리> deploy/build-and-push.sh [-y]

  ./gradlew build(테스트 포함) → docker build → docker push
  이미지 태그는 현재 커밋의 short sha다. latest 태그는 만들지 않는다.

환경변수:
  IMAGE_REGISTRY  (필수) 예: ghcr.io/<계정>, <계정 ID>.dkr.ecr.ap-northeast-2.amazonaws.com
  PLATFORM        (선택) 이미지 플랫폼. 기본값 linux/amd64 (x86 EC2). Graviton(arm) 인스턴스면 linux/arm64

옵션:
  -y  확인 질문 없이 진행 (CI용)
  -h  이 도움말

레지스트리에는 미리 로그인해 둔다.
  GHCR: docker login ghcr.io -u <계정> --password-stdin   (토큰을 표준입력으로)
  ECR : aws ecr get-login-password --region <리전> | docker login --username AWS --password-stdin <레지스트리>
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

if [ -z "${IMAGE_REGISTRY:-}" ]; then
	usage >&2
	echo >&2
	die "IMAGE_REGISTRY 환경변수가 필요합니다."
fi
REGISTRY=${IMAGE_REGISTRY%/}

# 저장소 루트에서 실행한다 (스크립트를 어디서 부르든 같게)
cd "$(dirname "$0")/.."

# 태그가 커밋을 가리키므로, 커밋되지 않은 변경이 섞인 이미지를 만들지 않는다
if [ -n "$(git status --porcelain)" ]; then
	git status --short >&2
	die "커밋되지 않은 변경이 있습니다. 커밋하거나 stash한 뒤 다시 실행하세요."
fi

TAG=$(git rev-parse --short HEAD)
IMAGE="$REGISTRY/$IMAGE_NAME:$TAG"

echo "브랜치  : $(git rev-parse --abbrev-ref HEAD)"
echo "커밋    : $(git log -1 --format='%h %s')"
echo "이미지  : $IMAGE"
echo "플랫폼  : $PLATFORM"

if docker manifest inspect "$IMAGE" >/dev/null 2>&1; then
	echo "레지스트리에 $IMAGE 가 이미 있습니다. 같은 커밋이므로 다시 올리지 않습니다."
	echo "서버에서: ./deploy.sh $TAG"
	exit 0
fi

confirm "테스트·빌드 후 위 이미지를 push할까요?"

echo
echo "[1/3] ./gradlew build (테스트 포함)"
./gradlew build

echo
echo "[2/3] docker build"
docker build --platform "$PLATFORM" \
	--label "org.opencontainers.image.revision=$(git rev-parse HEAD)" \
	-t "$IMAGE" .

echo
echo "[3/3] docker push"
docker push "$IMAGE"

echo
echo "완료: $IMAGE"
echo "서버의 배포 폴더에서: ./deploy.sh $TAG"
