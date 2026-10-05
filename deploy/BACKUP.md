# DB 백업과 복구

| 스크립트 | 하는 일 |
| --- | --- |
| `backup.sh` | db 컨테이너에서 `pg_dump` → gzip → `backups/<DB>-YYYYmmdd-HHMMSS.sql.gz`. 성공하면 7일 이상 지난 백업 삭제. 버킷을 설정하면 S3에도 업로드 |
| `restore.sh <파일>` | 백업 파일로 DB를 통째로 되돌림. 복구 직전 상태를 먼저 따로 덤프 |

모두 서버의 배포 폴더(`/opt/family-app`)에서 실행합니다. `-h`로 사용법을 볼 수 있습니다.

**백업 대상은 PostgreSQL 데이터뿐입니다.** 나머지는 이렇게 다룹니다.

- `.env`(DB 비밀번호, JWT 키): 백업 파일에 들어가지 않습니다. 서버를 잃으면 다시 만들어야 하므로 비밀번호 관리자 등에 따로 보관합니다. 특히 `JWT_SECRET`이 바뀌면 모든 사용자가 다시 로그인해야 합니다.
- HTTPS 인증서: 새로 발급하면 됩니다([HTTPS.md](HTTPS.md)).

## 백업

### 설정

`.env`에 넣거나(cron에서도 같은 값을 쓰도록) 실행할 때 환경변수로 줍니다. 모두 선택입니다.

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `BACKUP_DIR` | `./backups` | 백업 폴더 (gitignore 대상) |
| `RETENTION_DAYS` | `7` | 로컬 보관 일수. 이보다 오래된 `<DB>-*.sql.gz`만 지움 |
| `BACKUP_S3_BUCKET` | (없음) | 있으면 S3에도 올림. 없으면 로컬에만 보관 |
| `BACKUP_S3_PREFIX` | `db` | S3 경로 앞부분. `s3://<버킷>/<PREFIX>/<파일>` |

### 직접 실행

```bash
cd /opt/family-app
./backup.sh
# [2026-10-05 03:00:00] 백업 시작: familyapp → ./backups/familyapp-20261005-030000.sql.gz
# [2026-10-05 03:00:01] 백업 완료: ./backups/familyapp-20261005-030000.sql.gz (4.0K)
# [2026-10-05 03:00:01] BACKUP_S3_BUCKET이 없어 로컬에만 보관합니다.
```

- 백업 파일과 폴더는 소유자만 읽을 수 있습니다(파일 600, 폴더 700). 개인정보와 비밀번호 해시가 들어 있기 때문입니다.
- 덤프는 임시 파일에 쓴 뒤, gzip 검사를 통과하고 덤프가 끝까지 기록된 것을 확인했을 때만 최종 이름으로 바꿉니다. 실패하면 종료 코드 1로 끝나고, 기존 백업은 하나도 지우지 않습니다.
- `--no-owner --no-privileges`로 덤프하므로, DB 계정 이름이 다른 서버에도 그대로 복구할 수 있습니다.
- 같은 시각에 두 번 실행되지 않도록 잠금(`.backup.lock`)을 겁니다.

### 매일 03:00 자동 실행 (cron)

**1. 서버 시간대를 확인합니다.** EC2는 기본이 UTC라서, 그대로 `0 3 * * *`을 쓰면 한국 시간 낮 12시에 실행됩니다.

```bash
timedatectl | grep 'Time zone'
# UTC라면 둘 중 하나:
sudo timedatectl set-timezone Asia/Seoul && sudo systemctl restart cron   # 서버 시간대를 한국으로
# 또는 crontab에 0 18 * * * (UTC 18:00 = 한국 03:00)
```

**2. crontab에 등록합니다.** docker를 쓸 수 있는 사용자(docker 그룹)로 등록합니다.

```bash
crontab -e
```

```cron
# cron은 PATH가 /usr/bin:/bin뿐이라 docker·aws를 못 찾을 수 있다. 직접 지정한다
PATH=/usr/local/bin:/usr/bin:/bin

# 매일 03:00 DB 백업
0 3 * * * cd /opt/family-app && ./backup.sh >> backups/backup.log 2>&1
```

- 경로(`/opt/family-app`)는 실제 배포 폴더로 바꿉니다. `which docker`, `which aws`로 위치를 확인해 PATH에 들어 있는지 봅니다.
- PATH가 빠지면 로그에 `docker를 찾을 수 없습니다 (PATH=/usr/bin:/bin)`가 남습니다.

**3. 다음 날 확인합니다.**

```bash
tail -5 /opt/family-app/backups/backup.log
ls -lt /opt/family-app/backups | head
```

`backup.log`는 하루 몇 줄씩만 늘어납니다. 너무 커지면 지우거나 logrotate에 등록합니다.

### S3 업로드 (선택)

`.env`에 `BACKUP_S3_BUCKET=<버킷 이름>`을 넣으면 백업할 때마다 `s3://<버킷>/db/<파일>`로 올립니다.

1. **서버에 aws CLI v2를 설치합니다.** cron의 PATH에 설치 위치가 들어 있어야 합니다.
2. **버킷을 만듭니다.** 퍼블릭 액세스 차단은 켠 상태로 둡니다(기본값). 새로 올리는 객체는 S3가 기본으로 암호화합니다(SSE-S3).
3. **EC2 인스턴스 역할(IAM role)로 권한을 줍니다.** 액세스 키를 서버 파일에 두지 않습니다. 업로드에 필요한 최소 권한은 다음과 같습니다.

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": "s3:PutObject", "Resource": "arn:aws:s3:::<버킷>/db/*" }
     ]
   }
   ```

   서버에서 S3 백업을 받아 복구하려면 `s3:GetObject`(같은 Resource)와 `s3:ListBucket`(`arn:aws:s3:::<버킷>`)도 줍니다.
4. **S3 보관 기간은 버킷의 수명 주기(Lifecycle) 규칙으로 정합니다.** `backup.sh`는 S3의 파일을 지우지 않습니다(예: `db/` 접두어, 30일 후 삭제).

업로드에 실패해도 로컬 백업은 남습니다. 이때 스크립트는 종료 코드 1과 함께 `S3 업로드에 실패했습니다. 로컬 백업은 저장됐습니다: ...`를 남깁니다.

## 복구

복구하면 **백업 이후의 데이터가 사라집니다.** 복구하는 동안(측정 약 10초) app이 멈춰 nginx가 502를 돌려줍니다.

`restore.sh`의 진행 순서는 다음과 같습니다.

1. 백업 파일 검사: gzip 무결성, 덤프가 끝까지 기록됐는지, 이 앱의 백업인지(`flyway_schema_history` 포함)
2. 대상과 파일을 보여 주고 **DB 이름을 직접 입력**해야 진행
3. 현재 DB를 `backups/pre-restore-<DB>-<시각>.sql.gz`로 덤프 (잘못 복구했을 때 되돌릴 용도)
4. app 중지
5. DB를 지우고 새로 만든 뒤 백업 적용 (한 트랜잭션, 오류가 나면 바로 멈춤)
6. app 시작, healthy 대기. Flyway가 백업의 스키마 버전을 확인합니다.

배포(`deploy.sh`)와 같은 잠금(`.deploy.lock`)을 써서, 복구 중에 배포가 app을 다시 띄우지 않게 합니다.

### A. 같은 서버에서 특정 시점으로 되돌리기

```bash
cd /opt/family-app
ls -lt backups/*.sql.gz | head          # 되돌릴 시점 고르기

./restore.sh backups/familyapp-20261005-030000.sql.gz
#   대상 DB   : familyapp
#   백업 파일 : familyapp-20261005-030000.sql.gz  (4.0K, 2026-10-05 03:00:00)
# 진행하려면 DB 이름(familyapp)을 입력하세요: familyapp
# [1/5] db 준비 ... [5/5] app 시작, healthy 대기
# 복구 완료: familyapp ← familyapp-20261005-030000.sql.gz
# 복구 전 상태 덤프: ./backups/pre-restore-familyapp-20261005-101500.sql.gz

curl -s https://<DOMAIN>/actuator/health   # {"status":"UP"}
```

**복구를 되돌리려면** 마지막 줄에 나온 복구 전 상태 덤프로 다시 복구합니다.

```bash
./restore.sh backups/pre-restore-familyapp-20261005-101500.sql.gz
```

`pre-restore-*` 파일은 7일 보관 규칙에서 빠져 있어 자동으로 지워지지 않습니다. 복구 결과를 확인한 뒤 직접 지웁니다.

### B. S3의 백업으로 복구

```bash
aws s3 ls s3://<버킷>/db/ | tail
aws s3 cp s3://<버킷>/db/familyapp-20261005-030000.sql.gz backups/
./restore.sh backups/familyapp-20261005-030000.sql.gz
```

### C. 서버를 잃었을 때 (새 서버에 복구)

1. 새 서버에 Docker를 설치하고, README 배포 3단계대로 `deploy/` 폴더와 `.env`를 준비합니다. `JWT_SECRET`은 예전 값을 쓰면 사용자가 다시 로그인하지 않아도 됩니다.
2. `.env`의 `IMAGE_TAG`는 **백업을 만든 시점과 같거나 더 새로운 앱 버전**으로 둡니다(아래 Flyway 참고).
3. 백업 파일을 서버로 옮깁니다(`scp` 또는 B의 `aws s3 cp`).
4. 복구합니다. db만 먼저 띄워 복구하고 app까지 시작합니다.

   ```bash
   ./restore.sh backups/familyapp-20261005-030000.sql.gz
   docker compose up -d          # nginx·certbot까지 시작
   ```

5. [HTTPS.md](HTTPS.md)의 최초 발급 1~4단계로 인증서를 받습니다.
6. 1~5단계를 마친 뒤 cron(백업 자동 실행)을 다시 등록합니다.

### 앱 버전과 백업의 스키마 버전 (Flyway)

백업에는 `flyway_schema_history`가 들어 있습니다. app이 시작할 때 Flyway가 이 기록을 보고 처리합니다.

| 상황 | 결과 |
| --- | --- |
| 백업과 앱의 마이그레이션 버전이 같음 | 그대로 시작 (`Schema "public" is up to date`). 검증 기록의 복구 테스트가 이 경우 |
| 백업이 더 오래됨 (앱에 새 마이그레이션 있음) | 시작할 때 새 마이그레이션을 적용하고 시작 (Flyway 기본 동작) |
| 백업이 더 새로움 (앱이 모르는 마이그레이션 있음) | Flyway는 기본 설정상 모르는 이후 마이그레이션을 무시하고 넘어갑니다. 하지만 스키마가 앱이 기대하는 것과 달라, `ddl-auto: validate`에서 시작이 실패하거나 시작돼도 저장할 때 오류가 날 수 있습니다. 백업 시점 이상의 이미지로 `./deploy.sh <태그>`를 먼저 실행합니다 |

백업 파일의 마이그레이션 버전은 이렇게 확인합니다.

```bash
gzip -dc backups/<파일>.sql.gz | sed -n '/^COPY public.flyway_schema_history/,/^\\\./p' | cut -f2,3
```

## 복구 훈련

백업은 복구해 봐야 믿을 수 있습니다. 한 달에 한 번쯤 최신 백업이 실제로 복구되는지 운영이 아닌 곳에서 확인합니다. 예를 들어 로컬 PC에서 다음과 같이 합니다.

1. `deploy/`를 다른 폴더로 복사하고 `.env`를 만듭니다(시험용 값).
2. 운영과 같은 태그의 이미지로 `docker compose up -d`를 실행합니다.
3. 운영 백업 파일을 가져와 `./restore.sh -y <파일>`을 실행합니다(`-y`는 확인 입력을 생략하는 훈련용 옵션).
4. 데이터 건수와 로그인을 확인한 뒤 `docker compose down -v`로 지웁니다. 백업 파일도 개인정보이므로 확인 후 지웁니다.

## 검증 기록

로컬 Docker에서 deploy/ 구성(PostgreSQL 16)으로 확인했습니다.

| 시나리오 | 결과 |
| --- | --- |
| 백업 | 파일 600 권한, 테이블 7개와 데이터 포함, OWNER/GRANT 구문 없음 |
| 보관 기간 | 7일 이상 지난 `<DB>-*.sql.gz`만 삭제. 6일 전 백업, 다른 DB 백업, `pre-restore-*`, 그 밖의 파일은 유지 |
| 백업 실패 (db 멈춤, 잠금, 잘못된 설정) | 종료 코드 1, 기존 백업 유지, 임시 파일·잠금 남지 않음 |
| S3 (S3 호환 서버) | 환경변수 지정·`.env` 지정 모두 업로드, 받은 파일이 로컬과 동일. 업로드 실패·aws 없음 → 종료 코드 1, 로컬 백업 유지 |
| cron 환경 (`env -i`, 최소 PATH) | PATH 지정 시 성공. 빠지면 원인을 알려 주는 오류 |
| 복구 거부 (손상·중간에 끊김·다른 앱의 덤프, DB 이름 오입력) | DB를 건드리기 전에 중단 |
| 복구 | 백업 이후의 추가·변경이 사라지고 백업 시점과 건수·내용 일치. 로그인, API 조회 정상, ID 시퀀스도 복구(새 행 충돌 없음). 약 9초 |
| 복구 되돌리기 | `pre-restore-*`로 다시 복구 → 복구 직전 상태로 돌아감 |
| 서버 유실 (`down -v`로 볼륨까지 삭제) | 백업 파일 하나로 14초 만에 복구, `docker compose up -d`로 전체 서비스 정상 |

## 문제 해결

| 증상 | 원인과 해결 |
| --- | --- |
| cron 로그에 `docker를 찾을 수 없습니다` | crontab에 `PATH=` 줄이 없음. 위 cron 예시대로 추가 |
| cron이 엉뚱한 시각에 실행됨 | 서버 시간대가 UTC. `timedatectl`로 확인 (위 cron 1단계) |
| `permission denied ... docker.sock` | crontab 사용자가 docker 그룹이 아님. `sudo usermod -aG docker <사용자>` 후 다시 로그인 |
| `다른 백업이 진행 중입니다` | 이전 실행이 강제 종료돼 `.backup.lock`이 남음. 실행 중인 백업이 없으면 그 폴더를 지움 |
| `S3 업로드에 실패했습니다` | 인스턴스 역할 권한(`s3:PutObject`), 버킷 이름, 리전 확인. `aws s3 ls s3://<버킷>`으로 시험 |
| 복구 중 `invalid command \restrict` | PostgreSQL 16.10 이상에서 만든 덤프를 더 낮은 버전의 psql로 적용함. `restore.sh`는 db 컨테이너의 psql을 쓰므로 생기지 않는다. 직접 복구할 때는 같은 버전 이상의 psql을 쓴다 |
| 복구 후 app이 healthy가 되지 않음 | `restore.sh`가 보여 주는 app 로그 확인. `Schema-validation`이면 위 Flyway 표의 "백업이 더 새로움"이므로 백업 시점 이상의 태그로 배포. 원인을 바로 못 찾으면 `pre-restore-*`로 되돌림 |
