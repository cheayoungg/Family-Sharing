#!/bin/sh
# 갱신된 인증서를 반영하도록 주기적으로 설정을 다시 읽는다 (무중단).
# certbot 컨테이너는 nginx를 직접 건드리지 않는다. 그러려면 Docker 소켓을 넘겨야 해서 이 방식을 쓴다
set -eu

interval="${NGINX_RELOAD_INTERVAL:-6h}"

(
	while :; do
		sleep "$interval"
		nginx -s reload || echo "$0: nginx reload 실패" >&2
	done
) &

echo "$0: ${interval}마다 nginx 설정·인증서를 다시 읽습니다"
