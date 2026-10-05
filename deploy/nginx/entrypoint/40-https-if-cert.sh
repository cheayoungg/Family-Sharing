#!/bin/sh
# 인증서가 아직 없으면(최초 발급 전) HTTPS server 설정을 빼고 80만으로 시작한다.
# 없는 인증서 파일을 가리키는 설정이 있으면 nginx가 시작하지 못하고, 그러면 certbot이 발급에 쓸 80도 열리지 않기 때문이다.
# 이미지의 20-envsubst-on-templates.sh가 templates/를 /etc/nginx/conf.d/로 만든 뒤에 실행된다
set -eu

cert="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"

if [ -f "$cert" ]; then
	echo "$0: 인증서 확인 ($cert), HTTPS(443)를 켭니다"
else
	rm -f /etc/nginx/conf.d/https.conf
	echo "$0: 인증서가 없어($cert) HTTPS(443)를 끄고 80(ACME 챌린지)만 엽니다. 발급 후 'docker compose restart nginx'"
fi
