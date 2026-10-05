# HTTPS (Let's Encrypt + certbot)

nginx가 TLS를 종료하고 app으로 프록시합니다. 인증서는 certbot 컨테이너가 webroot 방식으로 발급·갱신합니다.

```
                ┌ 80  : /.well-known/acme-challenge/ → certbot-www 볼륨의 파일
인터넷 ──▶ nginx ┤       그 밖의 모든 요청 → 301 https://<DOMAIN>
                └ 443 : TLS 종료 → app:8080

certbot ── 12시간마다 renew ──▶ certbot-etc 볼륨(인증서) ◀── nginx가 6시간마다 reload해서 읽음
```

| 파일 | 역할 |
| --- | --- |
| `nginx/templates/default.conf.template` | 80: ACME 챌린지 응답, 나머지는 https 리다이렉트 |
| `nginx/templates/https.conf.template` | 443: TLS 설정, 보안 헤더, app 프록시 |
| `nginx/entrypoint/40-https-if-cert.sh` | 인증서가 없으면 443 설정을 빼고 80만으로 시작 (최초 발급용) |
| `nginx/entrypoint/41-reload-loop.sh` | 갱신된 인증서를 반영하려고 6시간마다 `nginx -s reload` |
| `docker-compose.yml`의 `certbot` 서비스 | 12시간마다 `certbot renew` |

템플릿의 `${DOMAIN}`은 nginx 컨테이너가 시작할 때 `.env`의 `DOMAIN` 값으로 바뀝니다. 설정 파일에 도메인을 직접 쓰지 않습니다.

## 최초 발급

처음에는 인증서가 없습니다. 없는 인증서를 가리키는 설정이 있으면 nginx가 시작하지 못하고, 그러면 발급에 필요한 80번도 열리지 않습니다. 그래서 `40-https-if-cert.sh`가 인증서가 없을 때 443 설정을 빼고 80만으로 nginx를 띄웁니다. 발급이 끝나면 nginx를 한 번 재시작해 443을 켭니다.

### 0. 준비

- **DNS**: `DOMAIN`의 A 레코드가 서버의 공인 IP를 가리켜야 합니다. `dig +short <DOMAIN>`으로 확인합니다.
- **방화벽(보안 그룹)**: 인바운드 80, 443을 엽니다. **80은 HTTPS를 켠 뒤에도 닫지 않습니다.** 갱신할 때 Let's Encrypt가 80으로 확인하기 때문입니다.
- **`.env`**: `DOMAIN`(예: `api.example.com`)과 `CERTBOT_EMAIL`을 채웁니다. 나머지 값은 README 배포 3단계를 따릅니다.

### 1. 80만으로 시작

```bash
cd /opt/family-app
docker compose up -d
docker compose logs nginx | grep 40-https
# ... 인증서가 없어(/etc/letsencrypt/live/<DOMAIN>/fullchain.pem) HTTPS(443)를 끄고 80(ACME 챌린지)만 엽니다.
```

외부에서 80이 챌린지 경로에 응답하는지 확인합니다. 로컬 PC 등 **서버 밖에서** 실행합니다.

```bash
# 서버에서: 확인용 파일 만들기
docker compose exec certbot sh -c 'mkdir -p /var/www/certbot/.well-known/acme-challenge && echo ok > /var/www/certbot/.well-known/acme-challenge/ping'

# 서버 밖에서: ok가 나와야 한다
curl http://<DOMAIN>/.well-known/acme-challenge/ping

# 서버에서: 확인용 파일 지우기
docker compose exec certbot rm /var/www/certbot/.well-known/acme-challenge/ping
```

### 2. 시험 발급 (staging)

Let's Encrypt는 실패한 요청에도 횟수 제한(rate limit)을 겁니다. 먼저 `--dry-run`(staging 서버, 인증서를 저장하지 않음)으로 확인합니다.

```bash
docker compose run --rm --entrypoint sh certbot -c \
  'certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" --cert-name "$DOMAIN" --email "$CERTBOT_EMAIL" --agree-tos --no-eff-email --dry-run'
# The dry run was successful.
```

`DOMAIN`, `CERTBOT_EMAIL`은 compose가 `.env`에서 certbot 컨테이너로 넘겨 줍니다. 명령에 직접 쓰지 않습니다.

### 3. 실제 발급

2단계 명령에서 `--dry-run`만 뺍니다.

```bash
docker compose run --rm --entrypoint sh certbot -c \
  'certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" --cert-name "$DOMAIN" --email "$CERTBOT_EMAIL" --agree-tos --no-eff-email'
# Successfully received certificate.
```

`--cert-name "$DOMAIN"`은 인증서를 `live/<DOMAIN>/`에 저장하게 고정합니다. 빼면 이전 발급 기록이 있을 때 `live/<DOMAIN>-0001/`처럼 다른 폴더에 저장돼 nginx가 찾지 못할 수 있습니다.

### 4. HTTPS 켜기

```bash
docker compose restart nginx
docker compose logs nginx | grep 40-https
# ... 인증서 확인 (...), HTTPS(443)를 켭니다

curl -sI http://<DOMAIN>/actuator/health    # 301 → https://<DOMAIN>/actuator/health
curl -s https://<DOMAIN>/actuator/health     # {"status":"UP"}
```

설정 등급은 [SSL Labs](https://www.ssllabs.com/ssltest/)에서 도메인을 넣어 확인할 수 있습니다.

## 자동 갱신

별도로 할 일은 없습니다.

- **certbot**: 12시간마다 `certbot renew`를 실행합니다. 만료 30일 이내인 인증서만 실제로 갱신하고, 나머지는 건너뜁니다. 발급할 때 쓴 방식(webroot)을 그대로 씁니다.
- **nginx**: 6시간마다 `nginx -s reload`로 설정과 인증서를 다시 읽습니다. 진행 중인 연결은 끊지 않습니다. 갱신된 인증서는 늦어도 6시간 안에 반영되고, 이때는 기존 인증서도 아직 30일 가까이 유효합니다.

certbot이 nginx를 직접 reload하지 않는 이유가 있습니다. 그러려면 certbot 컨테이너에 Docker 소켓을 넘겨야 하는데, 이는 사실상 서버의 root 권한을 주는 것과 같습니다.

### 5. 갱신 확인

갱신 경로가 지금도 동작하는지 staging으로 시험합니다. 실제 인증서는 바뀌지 않습니다.

```bash
docker compose exec certbot certbot renew --dry-run
# Congratulations, all simulated renewals succeeded
```

만료일을 확인합니다.

```bash
docker compose exec certbot certbot certificates                # Expiry Date: ... (VALID: 89 days)
echo | openssl s_client -connect <DOMAIN>:443 -servername <DOMAIN> 2>/dev/null | openssl x509 -noout -enddate
```

Let's Encrypt는 2025년부터 만료 안내 메일을 보내지 않습니다. 갱신이 조용히 실패하는 것에 대비해 위 명령을 주기적으로 확인하거나, 외부 모니터링(만료 30일 이내 경고)을 걸어 둡니다.

## TLS·보안 헤더 설정

`https.conf.template`에 있습니다. Mozilla의 "intermediate" 권장 설정을 기준으로 했습니다.

| 항목 | 설정 | 이유 |
| --- | --- | --- |
| 프로토콜 | TLS 1.2, 1.3 | 1.0/1.1은 취약하고 모든 주요 브라우저가 지원을 끝냄 |
| 암호 스위트 | ECDHE + AES-GCM / ChaCha20 | 전방 비밀성(PFS)이 있는 AEAD 스위트만. DHE는 별도 dhparam이 필요해 뺐다 |
| HTTP/2 | 켬 | |
| 세션 티켓 | 끔 | 티켓 키가 바뀌지 않으면 전방 비밀성이 약해진다. 세션 재사용은 서버 캐시로 한다 |
| OCSP stapling | 끔 | Let's Encrypt가 2025년에 OCSP를 종료했다 |
| `server_tokens` | 끔 | 응답에 nginx 버전을 노출하지 않음 |

| 헤더 | 값 | 의미 |
| --- | --- | --- |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains` | 2년 동안 브라우저가 이 도메인과 하위 도메인에 https로만 접속 |
| `X-Content-Type-Options` | `nosniff` | 응답 형식을 추측하지 않음 |
| `X-Frame-Options` | `DENY` | 다른 사이트에 프레임으로 삽입 금지 |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | JSON API라 어떤 리소스 로드도 필요 없음 (prod는 Swagger 꺼짐) |
| `Referrer-Policy` | `no-referrer` | 다른 곳으로 이동할 때 주소를 넘기지 않음 |

- 헤더는 nginx 한 곳에서만 붙입니다. Spring Security가 붙이는 같은 헤더(`Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`)는 `proxy_hide_header`로 숨겨 응답에 두 번 나오지 않게 했습니다.
- `always`를 붙여 401·404 같은 오류 응답에도 헤더가 나갑니다.
- CORS 헤더는 앱이 `CORS_ALLOWED_ORIGINS`로 붙이며, nginx는 건드리지 않습니다.

**HSTS 주의**: 한 번이라도 HSTS 응답을 받은 브라우저는 `max-age` 동안(2년) 이 도메인에 http로 접속하지 않습니다. 나중에 HTTPS를 끄면 그 브라우저에서는 접속할 수 없습니다. `includeSubDomains` 때문에 `*.<DOMAIN>` 하위 도메인도 https가 필요합니다. 처음 적용할 때 불안하다면 `max-age=300`처럼 짧게 시작해 문제가 없음을 확인한 뒤 늘립니다.

## 문제 해결

| 증상 | 원인과 해결 |
| --- | --- |
| 발급했는데 443이 안 열림 | nginx를 재시작하지 않음. 443 설정을 넣을지는 시작할 때만 정하므로 `docker compose restart nginx` |
| 발급 실패: `Timeout during connect`, `Connection refused` | 80이 외부에서 닿지 않음. 보안 그룹의 80 인바운드, `docker compose ps`의 nginx 상태, 1단계의 `curl` 확인 |
| 발급 실패: `NXDOMAIN`, `DNS problem` | DNS A 레코드가 없거나 아직 전파되지 않음. `dig +short <DOMAIN>` |
| 발급 실패: `Invalid response ... 404` | 요청이 다른 서버로 감(DNS가 다른 IP를 가리킴) 또는 webroot 경로 불일치. 명령의 `-w /var/www/certbot` 확인 |
| `too many failed authorizations` / `too many certificates` | rate limit. 시간이 지나면 풀린다. 그 전까지는 `--dry-run`으로만 시험 |
| nginx 로그 `cannot load certificate` | `live/<DOMAIN>/`이 아닌 곳에 발급됨. `docker compose exec certbot certbot certificates`로 이름 확인 후 `--cert-name "$DOMAIN"`으로 다시 발급 |
| 브라우저 `NET::ERR_CERT_DATE_INVALID` | 갱신 실패. 5단계의 `renew --dry-run`과 `docker compose logs certbot` 확인 |

## 도메인 변경·서버 이전

- **도메인 변경**: `.env`의 `DOMAIN`을 바꾸고 1~4단계를 다시 진행합니다. 예전 인증서는 `docker compose exec certbot certbot delete --cert-name <예전 도메인>`으로 지웁니다.
- **서버 이전**: 새 서버에서 1~4단계로 새로 발급하는 것이 가장 간단합니다. 인증서는 `certbot-etc` 볼륨에 있으므로 `docker compose down -v`를 하면 함께 지워집니다.
