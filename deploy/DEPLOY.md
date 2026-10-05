# EC2 배포 런북

EC2를 처음 만드는 것부터 HTTPS 첫 배포, 롤백, 백업, 장애 대응까지 순서대로 따라 하는 문서입니다. 각 단계 끝의 **✅ 확인**이 설명대로 나오면 다음 단계로 넘어갑니다.

- 명령 앞의 `[로컬]`은 내 PC(저장소 폴더)에서, `[서버]`는 EC2에 SSH로 접속해서 실행한다는 뜻입니다.
- `<EIP>`(Elastic IP), `<DOMAIN>`(예: `api.example.com`), `<키 파일>` 같은 꺾쇠 표시는 실제 값으로 바꿉니다.

| 단계 | 내용 |
| --- | --- |
| [0](#0-준비물) | 준비물 |
| [1](#1-ec2-인스턴스-만들기) | EC2 인스턴스 만들기 |
| [2](#2-보안-그룹) | 보안 그룹 (80/443 전체, 22는 내 IP만) |
| [3](#3-elastic-ip) | Elastic IP |
| [4](#4-서버-기본-설정) | 서버 기본 설정 (업데이트, 시간대, 스왑) |
| [5](#5-docker와-compose-설치) | Docker와 Compose 설치 |
| [6](#6-배포-디렉터리-구성) | 배포 디렉터리 `/opt/family-app` 구성 |
| [7](#7-이미지-빌드와-업로드) | 이미지 빌드와 업로드 |
| [8](#8-env-작성) | `.env` 작성 |
| [9](#9-도메인-연결) | 도메인 연결 |
| [10](#10-첫-기동-80만) | 첫 기동 (80만) |
| [11](#11-https-인증서-발급) | HTTPS 인증서 발급 |
| [12](#12-스모크-테스트) | 스모크 테스트 |
| [13](#13-백업-설정과-확인) | 백업 설정과 확인 |
| [14](#14-이후-배포와-롤백) | 이후 배포와 롤백 |
| [15](#15-장애-대응-로그와-상태-보기) | 장애 대응: 로그와 상태 보기 |

## 0. 준비물

- AWS 계정, EC2·Elastic IP를 만들 수 있는 권한
- 도메인과 DNS 관리 권한 (Route 53, 가비아, Cloudflare 등)
- 이미지 레지스트리: GHCR(`ghcr.io/<계정>`) 또는 AWS ECR
- 로컬 PC: Git, Docker, JDK 17 (`build-and-push.sh`가 `./gradlew build`로 테스트를 돌림), `ssh`, `rsync`

## 1. EC2 인스턴스 만들기

AWS 콘솔 → EC2 → **인스턴스 시작**

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 이름 | `family-app` | |
| AMI | **Ubuntu Server 24.04 LTS** (64비트 x86) | 이미지가 기본 `linux/amd64`로 빌드됨 |
| 인스턴스 유형 | **t3.small** (2 vCPU, 2GB) | 앱·PostgreSQL·nginx를 한 대에 띄움. 1GB(t3.micro)는 메모리가 빠듯함 (README 배포 5단계) |
| 키 페어 | 새로 만들기 → `.pem` 다운로드 | SSH 접속용. 다시 받을 수 없으니 잘 보관 |
| 네트워크 설정 | 보안 그룹 새로 만들기 (다음 단계에서 규칙 설정) | |
| 스토리지 | **20GiB gp3** 이상 | Docker 이미지, DB, 백업 |
| 고급 → IAM 인스턴스 프로파일 | (S3 백업·ECR을 쓸 때만) 13단계, 7단계의 권한을 가진 역할 | 서버에 액세스 키를 두지 않기 위해 |

Graviton(arm, 예: t4g.small)을 고르면 7단계에서 `PLATFORM=linux/arm64`로 빌드합니다.

키 파일 권한을 좁힙니다. 권한이 넓으면 ssh가 키를 거부합니다.

```bash
[로컬] chmod 400 ~/Downloads/<키 파일>.pem
```

✅ **확인**: EC2 콘솔에서 인스턴스 상태가 **실행 중**, 상태 검사가 **2/2 검사 통과**.

## 2. 보안 그룹

EC2 → 인스턴스 선택 → 보안 탭 → 보안 그룹 → **인바운드 규칙 편집**

| 유형 | 포트 | 소스 | 용도 |
| --- | --- | --- | --- |
| SSH | 22 | **내 IP** | 관리 접속. 전체 공개(0.0.0.0/0)하지 않는다 |
| HTTP | 80 | 0.0.0.0/0, ::/0 | https 리다이렉트, **인증서 발급·갱신** (HTTPS 후에도 닫지 않는다) |
| HTTPS | 443 | 0.0.0.0/0, ::/0 | 서비스 |

**5432(PostgreSQL), 8080(앱)은 규칙을 추가하지 않습니다.** 보안 그룹은 허용한 포트 외에는 모두 막습니다. compose도 이 두 포트를 서버 밖으로 열지 않으므로, 이중으로 막힙니다. 아웃바운드는 기본값(전체 허용)을 둡니다. 이미지 pull, 인증서 발급, S3 업로드에 필요합니다.

내 IP가 바뀌면(집↔회사, 공유기 재시작) SSH가 막힙니다. 그때는 이 규칙의 소스를 다시 **내 IP**로 바꿉니다.

✅ **확인**: 인바운드 규칙이 위 세 줄(80·443은 IPv4·IPv6 각각이면 다섯 줄)뿐이고 5432, 8080은 없음.

## 3. Elastic IP

인스턴스를 껐다 켜면 공인 IP가 바뀝니다. DNS가 가리킬 고정 IP를 붙입니다.

EC2 → 네트워크 및 보안 → **탄력적 IP** → 탄력적 IP 주소 할당 → 할당된 IP 선택 → 작업 → **탄력적 IP 주소 연결** → 인스턴스 `family-app` 선택

Elastic IP를 붙인 뒤 인스턴스를 지우거나 연결을 끊으면, IP를 쓰지 않아도 요금이 나옵니다. 정리할 때는 IP도 **릴리스**합니다.

```bash
[로컬] ssh -i ~/Downloads/<키 파일>.pem ubuntu@<EIP>
```

✅ **확인**: 인스턴스 상세의 퍼블릭 IPv4 주소가 Elastic IP와 같고, SSH 접속 후 프롬프트가 `ubuntu@ip-...:~$`로 보임.

이후 명령은 접속 편의를 위해 `~/.ssh/config`에 등록해 두면 `ssh family-app`으로 짧아집니다(선택).

```
Host family-app
    HostName <EIP>
    User ubuntu
    IdentityFile ~/Downloads/<키 파일>.pem
```

## 4. 서버 기본 설정

### 4-1. 패키지 업데이트

```bash
[서버] sudo apt-get update && sudo apt-get upgrade -y
[서버] sudo reboot                     # 커널이 업데이트됐다면. 1분 뒤 다시 ssh 접속
```

✅ **확인**: `sudo apt-get upgrade`를 다시 실행하면 `0 upgraded, 0 newly installed`.

### 4-2. 시간대 (한국)

EC2 기본은 UTC입니다. 백업 cron(매일 03:00)과 로그 시각을 한국 시간으로 맞춥니다.

```bash
[서버] sudo timedatectl set-timezone Asia/Seoul
[서버] timedatectl | grep 'Time zone'
```

✅ **확인**: `Time zone: Asia/Seoul (KST, +0900)`

### 4-3. 스왑 2GB

메모리가 순간적으로 모자랄 때 프로세스가 강제 종료되는 대신 디스크를 쓰게 하는 안전망입니다. `swappiness=10`으로 평소에는 거의 쓰지 않게 합니다.

```bash
[서버] sudo fallocate -l 2G /swapfile
[서버] sudo chmod 600 /swapfile
[서버] sudo mkswap /swapfile
[서버] sudo swapon /swapfile
[서버] echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab        # 재부팅 후에도 유지
[서버] echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf
[서버] sudo sysctl -p /etc/sysctl.d/99-swappiness.conf
```

✅ **확인**: `free -h`의 `Swap:` 줄 total이 `2.0Gi`, `cat /proc/sys/vm/swappiness`가 `10`. 재부팅 후에도 `swapon --show`에 `/swapfile`이 보임.

## 5. Docker와 Compose 설치

Docker 공식 apt 저장소로 설치합니다. Ubuntu 기본 저장소의 `docker.io`에는 `docker compose`(v2)가 없을 수 있습니다.

```bash
[서버] sudo apt-get install -y ca-certificates curl
[서버] sudo install -m 0755 -d /etc/apt/keyrings
[서버] sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
[서버] sudo chmod a+r /etc/apt/keyrings/docker.asc
[서버] echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
[서버] sudo apt-get update
[서버] sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# sudo 없이 docker를 쓰게 한다. 적용하려면 로그아웃 후 다시 접속
[서버] sudo usermod -aG docker ubuntu
[서버] exit
[로컬] ssh -i ~/Downloads/<키 파일>.pem ubuntu@<EIP>
```

✅ **확인**

```bash
[서버] docker run --rm hello-world | grep 'Hello from Docker'     # Hello from Docker!
[서버] docker compose version                                     # Docker Compose version v2.x.x
[서버] systemctl is-enabled docker                                # enabled (재부팅 시 자동 시작)
```

`docker: permission denied`가 나오면 다시 로그인하지 않은 것입니다.

**참고**: 서버의 방화벽은 보안 그룹으로 관리합니다. Ubuntu의 `ufw`로 포트를 막아도 Docker가 연 포트는 `ufw`를 우회하므로, `ufw`에 의존하지 않습니다.

## 6. 배포 디렉터리 구성

```bash
[서버] sudo mkdir -p /opt/family-app
[서버] sudo chown ubuntu:ubuntu /opt/family-app
```

로컬 저장소의 `deploy/` 내용을 서버로 복사합니다. 로컬에 있을 수 있는 `.env`, 백업, 배포 기록은 보내지 않습니다. 이후 배포 파일이 바뀔 때도 같은 명령으로 갱신합니다(서버의 `.env`는 덮어쓰지 않음).

```bash
[로컬] rsync -av -e "ssh -i ~/Downloads/<키 파일>.pem" \
         --exclude='.env' --exclude='backups/' --exclude='.previous-tag' --exclude='deploy-history.log' \
         deploy/ ubuntu@<EIP>:/opt/family-app/
```

`deploy/` 뒤의 `/`를 빼면 `/opt/family-app/deploy/`가 한 단계 더 생깁니다.

✅ **확인**

```bash
[서버] ls -l /opt/family-app
# -rw-r--r-- .env.example  BACKUP.md  DEPLOY.md  HTTPS.md  docker-compose.yml
# -rwxr-xr-x backup.sh  build-and-push.sh  deploy.sh  restore.sh  rollback.sh   ← 실행 권한(x)이 있어야 함
# drwxr-xr-x nginx
[서버] ls -l /opt/family-app/nginx/entrypoint     # 두 파일 모두 -rwxr-xr-x
```

## 7. 이미지 빌드와 업로드

변경 사항을 커밋한 뒤 로컬에서 실행합니다. 테스트 → 이미지 빌드 → 레지스트리 업로드까지 한 번에 진행합니다.

```bash
# GHCR 로그인 (토큰은 write:packages 권한의 Personal Access Token, 표준입력으로)
[로컬] docker login ghcr.io -u <GitHub 계정> --password-stdin

[로컬] IMAGE_REGISTRY=ghcr.io/<GitHub 계정> deploy/build-and-push.sh
```

✅ **확인**: 마지막에 다음이 출력되고, GitHub → Packages에 `family-app` 패키지와 그 태그가 보임.

```
완료: ghcr.io/<계정>/family-app:<커밋 해시>
서버의 배포 폴더에서: ./deploy.sh <커밋 해시>
```

출력된 **커밋 해시(태그)를 적어 둡니다.** 8단계 `.env`의 `IMAGE_TAG`에 씁니다.

**서버에서 레지스트리 로그인** (비공개 이미지일 때):

```bash
# GHCR: read:packages 권한의 토큰
[서버] docker login ghcr.io -u <GitHub 계정> --password-stdin
# ECR: 인스턴스 역할에 ECR 읽기 권한(AmazonEC2ContainerRegistryReadOnly) 필요
[서버] aws ecr get-login-password --region <리전> | docker login --username AWS --password-stdin <계정 ID>.dkr.ecr.<리전>.amazonaws.com
```

✅ **확인**: `Login Succeeded`

ECR 로그인은 12시간 뒤 만료됩니다. ECR을 쓰면 배포(`deploy.sh`) 전마다 위 로그인 명령을 다시 실행합니다. GHCR 토큰은 만료일까지 유지됩니다.

## 8. `.env` 작성

```bash
[서버] cd /opt/family-app
[서버] cp .env.example .env
[서버] chmod 600 .env
```

비밀값을 생성합니다. 출력된 값을 복사해 둡니다.

```bash
[서버] openssl rand -base64 24     # DB_PASSWORD
[서버] openssl rand -base64 48     # JWT_SECRET
```

```bash
[서버] vi .env
```

| 키 | 값 |
| --- | --- |
| `IMAGE_REGISTRY` | `ghcr.io/<GitHub 계정>` (또는 ECR 주소) |
| `IMAGE_TAG` | 7단계의 커밋 해시 |
| `DOMAIN` | `<DOMAIN>` (예: `api.example.com`) |
| `CERTBOT_EMAIL` | 내 이메일 |
| `DB_NAME`, `DB_USERNAME` | 예: `familyapp`, `familyapp` |
| `DB_PASSWORD`, `JWT_SECRET` | 위에서 생성한 값. 값에 따옴표를 붙이지 않는다 |
| `CORS_ALLOWED_ORIGINS` | 프론트엔드 주소, 예: `https://family.example.com` |
| 메모리·백업 항목 | 비워 두면 기본값 (t3.small이면 `APP_MEM_LIMIT=1536m`, `APP_HEAP_PERCENT=75`도 가능, README 배포 5단계) |

**`.env`의 `DB_PASSWORD`, `JWT_SECRET`은 비밀번호 관리자에도 저장합니다.** 백업에 들어가지 않으므로, 서버를 잃으면 이 값이 필요합니다.

✅ **확인**

```bash
[서버] ls -l .env                                  # -rw------- 1 ubuntu ubuntu ... .env
[서버] docker compose config -q && echo CONFIG_OK  # CONFIG_OK
```

필수 값이 비어 있으면 `CONFIG_OK` 대신 `required variable DOMAIN is missing a value: DOMAIN을 .env에 설정하세요`처럼 빠진 키가 나옵니다.

## 9. 도메인 연결

DNS 관리 화면에서 **A 레코드**를 추가합니다.

| 유형 | 이름 | 값 | TTL |
| --- | --- | --- | --- |
| A | `api` (→ `api.example.com`) | `<EIP>` | 300 |

✅ **확인** (반영까지 몇 분 걸릴 수 있음)

```bash
[로컬] dig +short <DOMAIN>      # <EIP> 한 줄만 나와야 함
```

다른 IP가 나오거나 아무것도 안 나오면 아직 반영 전이거나 레코드가 잘못된 것입니다. 이 확인이 통과해야 11단계 인증서 발급이 됩니다.

## 10. 첫 기동 (80만)

nginx는 app이 healthy가 된 뒤에 시작하므로, 인증서 발급에 필요한 80번을 열려면 전체 스택을 먼저 띄웁니다. 인증서가 아직 없으므로 nginx는 443 없이 80만 엽니다(HTTPS.md 참고).

```bash
[서버] cd /opt/family-app
[서버] docker compose pull
[서버] docker compose up -d
```

✅ **확인** (app 기동에 30초~1분)

```bash
[서버] docker compose ps            # (일부 열 생략)
# NAME                   SERVICE   STATUS
# family-app-app-1       app       Up ... (healthy)
# family-app-certbot-1   certbot   Up ...
# family-app-db-1        db        Up ... (healthy)
# family-app-nginx-1     nginx     Up ...

[서버] docker compose logs app | grep -E 'Successfully applied|Started HomeProjectApplication'
# ... Successfully applied 1 migration to schema "public", now at version v1
# ... Started HomeProjectApplication in ... seconds

[서버] docker compose logs nginx | grep 40-https
# ... 인증서가 없어(...) HTTPS(443)를 끄고 80(ACME 챌린지)만 엽니다.

[로컬] curl -sI http://<DOMAIN>/ | head -3
# HTTP/1.1 301 Moved Permanently
# Location: https://<DOMAIN>/
```

app이 `(healthy)`가 되지 않으면 [15단계](#15-장애-대응-로그와-상태-보기)로 로그를 봅니다. `.env` 값 오류가 가장 흔합니다.

## 11. HTTPS 인증서 발급

자세한 설명은 [HTTPS.md](HTTPS.md)에 있습니다. 여기서는 명령만 순서대로 적습니다.

**11-1. 외부에서 80이 챌린지 경로에 닿는지 확인**

```bash
[서버] docker compose exec certbot sh -c 'mkdir -p /var/www/certbot/.well-known/acme-challenge && echo ok > /var/www/certbot/.well-known/acme-challenge/ping'
[로컬] curl http://<DOMAIN>/.well-known/acme-challenge/ping           # ok
[서버] docker compose exec certbot rm /var/www/certbot/.well-known/acme-challenge/ping
```

**11-2. 시험 발급 (staging, 횟수 제한 보호)**

```bash
[서버] docker compose run --rm --entrypoint sh certbot -c \
  'certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" --cert-name "$DOMAIN" --email "$CERTBOT_EMAIL" --agree-tos --no-eff-email --dry-run'
```

✅ **확인**: `The dry run was successful.`

**11-3. 실제 발급** (11-2 명령에서 `--dry-run`만 뺌)

```bash
[서버] docker compose run --rm --entrypoint sh certbot -c \
  'certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" --cert-name "$DOMAIN" --email "$CERTBOT_EMAIL" --agree-tos --no-eff-email'
```

✅ **확인**: `Successfully received certificate.`

**11-4. HTTPS 켜기**

```bash
[서버] docker compose restart nginx
[서버] docker compose logs nginx | grep 40-https | tail -1
```

✅ **확인**: `인증서 확인 (...), HTTPS(443)를 켭니다`, 그리고

```bash
[로컬] curl -s https://<DOMAIN>/actuator/health        # {"status":"UP"}
```

## 12. 스모크 테스트

서비스가 밖에서 의도대로 보이는지 **로컬 PC에서** 확인합니다. 운영 데이터를 만들지 않는 요청만 씁니다. 가족장(owner) 가입은 한 번만 가능하므로, 실제 사용자가 프론트엔드에서 합니다.

```bash
[로컬] D=<DOMAIN>
```

| # | 명령 | ✅ 성공하면 |
| --- | --- | --- |
| 1 | `curl -s https://$D/actuator/health` | `{"status":"UP"}` |
| 2 | `curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' http://$D/api/schedules` | `301 https://<DOMAIN>/api/schedules` |
| 3 | `curl -s -o /dev/null -w '%{http_code} HTTP/%{http_version}\n' https://$D/actuator/health` | `200 HTTP/2` |
| 4 | `curl -sI https://$D/actuator/health \| grep -iE '^(strict-transport\|x-content\|x-frame\|content-security\|referrer)'` | 보안 헤더 5줄 (`strict-transport-security: max-age=63072000; includeSubDomains` 등) |
| 5 | `curl -s -o /dev/null -w '%{http_code}\n' https://$D/actuator/env` | `404` (health 외 actuator 차단) |
| 6 | `curl -s -w ' %{http_code}\n' "https://$D/api/schedules?year=2026&month=1"` | `{"success":false,...,"code":"UNAUTHORIZED",...} 401` (인증 필요) |
| 7 | `curl -s -H 'Content-Type: application/json' -d '{"email":"nobody@example.com","password":"wrong-password"}' https://$D/api/auth/login` | `"code":"INVALID_CREDENTIALS"` (앱이 DB까지 조회함) |
| 8 | `nc -zv -w 3 <EIP> 8080; nc -zv -w 3 <EIP> 5432` | 둘 다 `timed out` 또는 `refused` (밖에서 앱·DB 포트에 닿지 않음) |
| 9 | `echo \| openssl s_client -connect $D:443 -servername $D -tls1_1 -cipher 'DEFAULT@SECLEVEL=0' 2>&1 \| grep -oE 'alert protocol version\|Protocol *: *TLSv1\.1'` | `alert protocol version` (서버가 TLS 1.1 거부). `-tls1_2`로 바꾸면 연결됨 |
| 10 | `echo \| openssl s_client -connect $D:443 -servername $D 2>/dev/null \| openssl x509 -noout -issuer -enddate` | `issuer=... Let's Encrypt ...`, 만료일이 약 90일 뒤 |

프론트엔드에서 가족장 가입 → 로그인 → 일정 만들기를 한 번 해 보면 끝까지 확인됩니다. CORS 오류가 나면 `.env`의 `CORS_ALLOWED_ORIGINS`가 프론트엔드 주소와 정확히 같은지 확인합니다(끝의 `/` 없이, `https://` 포함). 바꾼 뒤에는 `docker compose up -d app`으로 적용합니다.

## 13. 백업 설정과 확인

자세한 내용은 [BACKUP.md](BACKUP.md)에 있습니다.

**13-1. 한 번 직접 실행**

```bash
[서버] cd /opt/family-app && ./backup.sh
```

✅ **확인**

```
[...] 백업 시작: familyapp → ./backups/familyapp-YYYYmmdd-HHMMSS.sql.gz
[...] 백업 완료: ./backups/familyapp-YYYYmmdd-HHMMSS.sql.gz (...K)
[...] BACKUP_S3_BUCKET이 없어 로컬에만 보관합니다.
```

```bash
[서버] ls -l backups/        # -rw------- ... familyapp-....sql.gz
```

**13-2. 매일 03:00 자동 실행 등록** (4-2에서 시간대를 Asia/Seoul로 바꿨으므로 03:00 = 한국 시간)

```bash
[서버] crontab -e
```

```cron
PATH=/usr/local/bin:/usr/bin:/bin
0 3 * * * cd /opt/family-app && ./backup.sh >> backups/backup.log 2>&1
```

✅ **확인**: `crontab -l`에 두 줄이 보이고, **다음 날** `tail -3 /opt/family-app/backups/backup.log`에 `백업 완료`가 03:00 무렵 시각으로 찍혀 있음.

**13-3. (선택) S3 업로드**

1. 1단계의 인스턴스 역할에 `s3:PutObject` 권한을 줍니다(BACKUP.md의 정책 예시).
2. aws CLI를 설치합니다. `/usr/local/bin/aws`에 설치되어 cron의 PATH로 찾을 수 있습니다. snap 설치는 `/snap/bin`이라 PATH에 따로 추가해야 합니다.

   ```bash
   [서버] sudo apt-get install -y unzip
   [서버] curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
   [서버] unzip -q /tmp/awscliv2.zip -d /tmp && sudo /tmp/aws/install && rm -rf /tmp/aws /tmp/awscliv2.zip
   [서버] aws --version                        # aws-cli/2.x.x ...
   [서버] aws sts get-caller-identity          # "Arn": "arn:aws:sts::...:assumed-role/<역할 이름>/..."
   ```

3. `.env`에 `BACKUP_S3_BUCKET=<버킷>`을 넣고 `./backup.sh`를 실행합니다.

✅ **확인**: `S3 업로드 완료`가 출력되고 `aws s3 ls s3://<버킷>/db/`에 파일이 보임.

**복구는 BACKUP.md의 복구 절차를 따릅니다.** 운영 백업이 실제로 복구되는지 한 달에 한 번쯤 운영이 아닌 곳에서 확인하는 것을 권합니다(BACKUP.md의 복구 훈련).

## 14. 이후 배포와 롤백

### 배포

```bash
[로컬] IMAGE_REGISTRY=ghcr.io/<계정> deploy/build-and-push.sh       # 출력된 태그 확인
[서버] cd /opt/family-app && ./deploy.sh <태그>
```

✅ **확인**

```
[3/3] 완료: app이 <태그> 로 healthy 상태입니다.
nginx 경유 확인: https://<DOMAIN>/actuator/health OK
```

- 새 버전이 healthy가 되지 않으면 `deploy.sh`가 새 app의 로그를 보여 주고 **이전 태그로 자동 복구**합니다. 이때는 `<태그> 배포에 실패해 <이전 태그> 로 복구했습니다`가 나옵니다.
- 교체하는 몇 초 동안 502가 날 수 있습니다(무중단 배포 아님).
- 배포 파일(compose, nginx 설정, 스크립트)이 바뀐 버전이면 먼저 6단계의 `rsync`로 서버를 갱신하고, `docker compose up -d`로 적용합니다.

### 롤백 (배포 후에 문제를 발견했을 때)

```bash
[서버] cd /opt/family-app && ./rollback.sh
# 현재 태그: <새 태그>
# 되돌릴 태그: <이전 태그>
# app을 <이전 태그> 로 되돌릴까요? [y/N] y
```

✅ **확인**: `[3/3] 완료: app이 <이전 태그> 로 healthy 상태입니다.` 그리고

```bash
[서버] tail -3 deploy-history.log
# ... deploy <이전 태그> -> <새 태그> OK
# ... deploy <새 태그> -> <이전 태그> OK
[서버] grep IMAGE_TAG .env              # IMAGE_TAG=<이전 태그>
```

- `rollback.sh`를 한 번 더 실행하면 롤백 전 태그로 돌아갑니다.
- 두 단계 이상 전으로 가려면 `deploy-history.log`에서 태그를 찾아 `./deploy.sh <태그>`를 실행합니다.
- **DB는 롤백되지 않습니다.** 새 버전이 마이그레이션(V2 등)을 적용했다면 이전 버전 앱이 그 스키마에서 동작하는지 확인해야 합니다. 데이터까지 되돌려야 하면 BACKUP.md의 복구를 따릅니다.

## 15. 장애 대응: 로그와 상태 보기

모든 명령은 `/opt/family-app`에서 실행합니다.

### 먼저 볼 것

```bash
docker compose ps                                  # 어느 서비스가 멈췄거나 unhealthy인지
docker compose logs --tail 100 app                 # 앱 최근 로그
docker compose logs --since 30m app | grep -E 'ERROR|Exception'
docker compose logs -f app                         # 실시간 (Ctrl+C로 종료)
curl -s https://<DOMAIN>/actuator/health           # 밖에서 본 상태 (로컬에서도)
```

### 서비스별 로그

| 대상 | 명령 | 볼 것 |
| --- | --- | --- |
| 앱 | `docker compose logs --tail 200 app` | 시작 실패 메시지(README "시작이 안 될 때" 표), `ERROR`, Flyway |
| nginx | `docker compose logs --tail 100 nginx` | `[error]`, `[emerg]`, 502 시 `connect() failed` |
| DB | `docker compose logs --tail 100 db` | `FATAL`, `could not`, 디스크 부족 |
| certbot | `docker compose logs --tail 50 certbot` | 갱신 오류 |
| 배포 기록 | `tail -20 deploy-history.log` | 언제 어떤 태그로 바뀌었는지, 실패·복구 |
| 백업 기록 | `tail -20 backups/backup.log` | 마지막 백업 시각, 오류 |

### 자원

```bash
docker stats --no-stream                       # 컨테이너별 CPU·메모리 (app이 MEM LIMIT에 붙어 있는지)
free -h                                        # 서버 메모리·스왑
df -h /                                        # 디스크 (90% 넘으면 위험)
docker system df                               # 이미지·볼륨이 차지하는 용량
docker inspect -f '{{.State.OOMKilled}} restarts={{.RestartCount}}' $(docker compose ps -q app)
                                               # true면 메모리 제한에 걸려 강제 종료된 것
sudo dmesg -T | grep -i -E 'oom|killed process' | tail
```

### 증상별

| 증상 | 확인 | 조치 |
| --- | --- | --- |
| 사이트 접속 안 됨 (타임아웃) | 보안 그룹 80/443, `docker compose ps`의 nginx, `dig +short <DOMAIN>` | nginx가 멈췄으면 `docker compose up -d` |
| `502 Bad Gateway` | `docker compose ps`의 app 상태, `logs app` | 배포 직후라면 몇 초 기다림. 계속되면 로그 확인 후 `./rollback.sh` |
| app이 계속 재시작 | `logs app`의 마지막 오류, `OOMKilled` | 설정 오류면 `.env` 수정 후 `docker compose up -d app`, 메모리면 README 배포 5단계 |
| 인증서 오류 (`ERR_CERT_DATE_INVALID`) | `docker compose exec certbot certbot certificates` | HTTPS.md 문제 해결 |
| 디스크 가득 참 | `df -h /`, `docker system df`, `du -sh backups` | `docker image prune -a --filter "until=168h"`로 7일 넘은 안 쓰는 이미지 정리 (롤백 시에는 다시 pull) |
| SSH 접속 안 됨 | 내 IP 변경 여부 | 보안 그룹 22번 소스를 다시 "내 IP"로 |
| 데이터가 잘못됨 | 언제부터인지, `backups/` 목록 | BACKUP.md 복구 (복구 직전 상태는 자동으로 따로 덤프됨) |

### 재시작

```bash
docker compose restart app          # 앱만 (DB·nginx 유지)
docker compose up -d                # 멈춘 서비스를 설정대로 다시 띄움 (데이터 유지)
```

**`docker compose down -v`는 DB 볼륨까지 지웁니다.** 운영에서는 쓰지 않습니다. 컨테이너만 내리려면 `-v` 없이 `docker compose down`을 씁니다.

서버를 재부팅해도 Docker와 모든 컨테이너는 자동으로 다시 뜹니다(`restart: unless-stopped`). 재부팅 후에는 10단계의 `docker compose ps`로 확인합니다.
