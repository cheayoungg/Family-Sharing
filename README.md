# Family-Sharing

가족이 일정, 가계부, 집안일 분담, 공유사항("문앞에 택배 받아줘~")을 함께 관리하는 서비스의 백엔드 API입니다.

가족장이 가입하면 초대코드가 발급되고, 나머지 가족은 그 초대코드로 가입합니다.

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 회원가입·로그인 | 가족장 가입 시 초대코드 발급, 초대코드로 구성원 가입, JWT 로그인 |
| 초대코드 | 가족 인원만큼만 가입 가능 (동시 가입에도 정원 초과 없음), 한도 늘리기 |
| 일정 | 월별 달력 조회, 등록·수정·완료·삭제 (수정·삭제는 담당자 본인만) |
| 가계부 | 납부 상태(미납/납부) 필터 조회, 등록, 납부 처리, 삭제 |
| 할 일 | 담당자별 조회, 등록, 완료 체크, 삭제 (삭제는 담당자 본인만) |
| 공유사항 | 카테고리별 조회, 등록, 삭제 |
| 대시보드 | 오늘 일정·미완료 할 일·미납 지출을 한 번에 조회 (한국 시간 기준) |

전체 API 명세는 [docs/API.md](docs/API.md)에 있습니다.

## 기술 스택

- Java 17, Spring Boot 3.5.9, Gradle 9.7.1 (Wrapper 포함)
- Spring Data JPA, PostgreSQL, Flyway (prod 스키마 마이그레이션)
- Spring Security + JWT ([jjwt](https://github.com/jwtk/jjwt) 0.12.6), Stateless 인증
- springdoc-openapi 2.8.17 (Swagger UI)
- JUnit 5, Testcontainers (테스트용 PostgreSQL)

## 로컬에서 실행하기 (Gradle)

개발할 때는 이 방법을 씁니다. Docker 이미지로 실행하는 방법은 [Docker로 실행하기](#docker로-실행하기)를 보세요.

### 준비물

- JDK 17
- PostgreSQL (로컬 실행용)
- Docker (테스트 실행용. 테스트가 PostgreSQL 컨테이너를 띄웁니다)

### 1. 로컬 DB 만들기

```sql
CREATE USER admin WITH PASSWORD '1234';
CREATE DATABASE homeproject OWNER admin;
```

다른 계정이나 DB 이름을 쓰려면 아래 환경변수로 바꿀 수 있습니다.

### 2. 로컬 설정 파일 만들기

`src/main/resources/application-local.yml`은 각자 관리하는 파일이라 **저장소에 없습니다**(gitignore 대상). 아래 내용으로 직접 만들어 주세요.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${HOMEPROJECT_DB_HOST:localhost}:${HOMEPROJECT_DB_PORT:5432}/${HOMEPROJECT_DB_NAME:homeproject}
    username: ${HOMEPROJECT_DB_USERNAME:admin}
    password: ${HOMEPROJECT_DB_PASSWORD:1234}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

jwt:
  secret: ${HOMEPROJECT_JWT_SECRET:local-only-secret-change-me-please-32-bytes-min}
  expiration: 86400000 # 24h

cors:
  allowed-origins: ${HOMEPROJECT_CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

`cors.allowed-origins`를 빠뜨려도 앱은 뜨지만, 프론트에서 보내는 요청이 모두 막힙니다.

### 3. 실행

```bash
./gradlew bootRun
```

기본 프로필은 `local`이고, 서버는 `http://localhost:8080`에서 뜹니다.

### 4. Swagger로 API 써 보기

http://localhost:8080/swagger-ui/index.html

1. **인증 → `POST /api/auth/signup-owner`** 로 가족장을 만듭니다. 응답에 초대코드가 들어 있습니다.
2. (선택) **`POST /api/auth/signup`** 에 초대코드를 넣어 다른 가족을 가입시킵니다.
3. **`POST /api/auth/login`** 응답의 `accessToken`을 복사합니다.
4. 오른쪽 위 **Authorize** 버튼에 토큰을 붙여 넣습니다. `Bearer `는 자동으로 붙습니다.
5. 나머지 API를 바로 호출할 수 있습니다.

가족장은 시스템 전체에 한 명만 가입할 수 있습니다. 이미 가족장이 있으면 `signup-owner`는 `OWNER_ALREADY_EXISTS`(409)로 실패합니다.

## Docker로 실행하기

배포와 같은 이미지를 로컬에서 띄워 보는 방법입니다. `dev` 프로필을 쓰면 테이블이 자동으로 만들어지고 Swagger도 켜집니다.

```bash
# 1. 이미지 빌드 (멀티스테이지: JDK로 빌드하고 JRE 이미지에 담음. 처음엔 몇 분 걸립니다)
docker build -t homeproject .

# 2. 앱과 DB가 서로 찾을 수 있게 네트워크를 만들고 PostgreSQL을 띄움
docker network create homeproject
docker run -d --name homeproject-db --network homeproject \
  -e POSTGRES_USER=homeproject -e POSTGRES_PASSWORD=homeproject -e POSTGRES_DB=homeproject \
  -v homeproject-db:/var/lib/postgresql/data \
  postgres:16-alpine

# 3. 환경변수 파일 준비 후 값 채우기
cp .env.example .env
```

로컬 확인용 `.env` 예시입니다.

```dotenv
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://homeproject-db:5432/homeproject
DB_USERNAME=homeproject
DB_PASSWORD=homeproject
JWT_SECRET=여기에-openssl-rand-base64-48-결과를-붙여넣기
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

```bash
# 4. 앱 실행 (./gradlew bootRun이 8080을 쓰고 있으면 먼저 끄거나 -p 18080:8080처럼 바꿉니다)
docker run -d --name homeproject --network homeproject -p 8080:8080 --env-file .env homeproject

# 로그 확인: "Started HomeProjectApplication"이 보이면 준비 완료
docker logs -f homeproject
```

정리할 때는 `docker rm -f homeproject homeproject-db && docker network rm homeproject`를 실행합니다. DB 데이터까지 지우려면 `docker volume rm homeproject-db`도 실행합니다.

### docker compose로 실행하기

위 과정(이미지 빌드, 네트워크, DB, 앱 실행)을 [`docker-compose.yml`](docker-compose.yml) 하나로 묶어 두었습니다. `.env`는 위와 같이 준비합니다.

```bash
# 이미지를 빌드하고 DB → 앱 순서로 띄움 (DB 헬스체크가 통과한 뒤 앱이 시작됩니다)
docker compose up -d --build

# 로그 확인: "Started HomeProjectApplication"이 보이면 준비 완료
docker compose logs -f app

# 정리 (DB 데이터까지 지우려면 docker compose down -v)
docker compose down
```

- `DB_URL`은 compose 안의 DB 컨테이너(`jdbc:postgresql://db:5432/homeproject`)로 덮어쓰므로 `.env` 값은 쓰이지 않습니다.
- `DB_USERNAME`/`DB_PASSWORD`는 DB 컨테이너를 처음 만들 때 계정으로도 쓰입니다. 볼륨이 이미 있으면 바꿔도 반영되지 않으니 `docker compose down -v`로 지우고 다시 띄웁니다.
- `SPRING_PROFILES_ACTIVE`를 비워 두면 Swagger를 볼 수 있는 `dev`로 뜹니다. `prod`도 빈 DB에서 시작할 수 있습니다 (Flyway가 테이블을 만듭니다).
- `dev`로 만든 DB 볼륨을 `prod`로 다시 띄우면 시작에 실패합니다. `dev`는 Flyway 없이 `ddl-auto=update`로 테이블을 만들어 Flyway 이력이 없기 때문입니다. `dev` → `prod`로 바꿀 때는 `docker compose down -v`로 볼륨을 지우고 띄웁니다.
- 8080을 `./gradlew bootRun`이 쓰고 있으면 `APP_PORT=18080 docker compose up -d --build`처럼 포트를 바꿉니다.
- DB 포트는 로컬 PostgreSQL(5432)과 겹치지 않도록 호스트에 열지 않았습니다.

### 이미지 구성

- **build stage** (`eclipse-temurin:17-jdk`): 의존성을 먼저 받아 캐시해 두고, `./gradlew bootJar -x test`로 jar를 만든 뒤 Spring Boot 레이어별로 풀어 둡니다. 코드만 바뀌면 의존성 다운로드를 건너뛰어 빨리 빌드됩니다.
- **run stage** (`eclipse-temurin:17-jre`): JRE와 실행 파일만 담고, root가 아닌 `app` 사용자로 실행합니다.
- 기본 프로필은 `prod`입니다. dev 서버는 `SPRING_PROFILES_ACTIVE=dev`로 덮어씁니다.
- JVM 시간대를 `Asia/Seoul`로 고정했습니다. `createdAt` 같은 기록 시각도 일정 시각과 같은 한국 시간으로 저장됩니다.
- heap 상한은 컨테이너 메모리 제한의 50%입니다(`JAVA_TOOL_OPTIONS`로 변경 가능, [배포 5단계](#5-실행) 참고). GC는 작은 heap에 맞는 Serial GC로 고정했고, OutOfMemoryError가 나면 프로세스를 종료해 `--restart` 정책으로 다시 뜨게 합니다.
- 테스트는 이미지 빌드 안에서 돌리지 않습니다. Testcontainers가 Docker를 필요로 하기 때문입니다. 배포 전에 `./gradlew test`로 따로 돌립니다.

## 배포하기

서버에 Docker가 설치돼 있고, 앱이 붙을 PostgreSQL 서버가 있다고 가정합니다. 운영 DB를 처음 만드는 방법은 [4단계](#4-db-스키마-flyway)에 있습니다.

### 1. 테스트

```bash
./gradlew test
```

### 2. 이미지 빌드와 업로드

```bash
# 태그는 커밋 해시처럼 버전을 구분할 수 있는 값을 씁니다
TAG=$(git rev-parse --short HEAD)

# Apple Silicon(M1 등) Mac에서 일반 x86 서버용 이미지를 만들 때는 --platform을 꼭 붙입니다
docker buildx build --platform linux/amd64 -t <레지스트리>/homeproject:$TAG --push .
```

`<레지스트리>`는 Docker Hub 계정, `ghcr.io/<계정>`, AWS ECR 주소 등 사용하는 레지스트리로 바꿉니다. 처음이라면 `docker login`이 필요합니다.

### 3. 서버에 환경변수 파일 준비

`.env.example`을 서버로 복사해 `.env`로 저장하고 값을 채웁니다. 비밀값이 들어가므로 권한을 좁혀 둡니다.

```bash
chmod 600 .env
```

| 변수 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` 또는 `dev` |
| `DB_URL` | `jdbc:postgresql://<DB 호스트>:5432/<DB 이름>` |
| `DB_USERNAME`, `DB_PASSWORD` | DB 계정 |
| `JWT_SECRET` | `openssl rand -base64 48`로 만든 값. 서버마다 다르게, 한 번 정하면 바꾸지 않기 (바꾸면 모든 사용자가 다시 로그인해야 함) |
| `CORS_ALLOWED_ORIGINS` | 배포된 프론트 주소 (예: `https://family.example.com`) |

### 4. DB 스키마 (Flyway)

`prod` 프로필은 시작할 때 Flyway가 [`src/main/resources/db/migration`](src/main/resources/db/migration)의 마이그레이션을 DB에 적용하고, 그 결과를 `ddl-auto: validate`가 엔티티와 대조합니다. 빈 DB라면 첫 실행에서 `V1__init_schema.sql`로 테이블이 만들어지므로 따로 준비할 것이 없습니다. 적용 이력은 DB의 `flyway_schema_history` 테이블에 남습니다.

**운영 DB를 처음 만들 때**는 빈 데이터베이스와 앱 계정만 만들고, 테이블은 Flyway에 맡깁니다. PostgreSQL 관리자 계정으로 실행합니다.

```sql
CREATE USER homeproject WITH PASSWORD '<DB_PASSWORD 값>';
CREATE DATABASE homeproject OWNER homeproject;
```

- 데이터베이스 소유자를 앱 계정으로 둡니다. PostgreSQL 15부터는 소유자가 아닌 일반 계정이 `public` 스키마에 테이블을 만들 수 없어서, 소유자가 아니면 Flyway가 `permission denied for schema public`으로 멈춥니다.
- DB를 Docker 컨테이너로 띄운다면 `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`로 이 단계가 대신 처리됩니다.
- 운영 DB에 붙는 **첫 실행은 반드시 `prod` 프로필**로 합니다. `dev`(`ddl-auto: update`)로 한 번이라도 띄우면 Flyway 이력 없이 테이블이 생겨, 이후 `prod`가 시작하지 못합니다. 운영 DB를 dev 서버와 함께 쓰지 않습니다.

첫 실행 뒤에는 마이그레이션이 기록됐는지 확인합니다.

```sql
SELECT version, description, success FROM flyway_schema_history;
-- 1 | init schema | t
```

**스키마를 바꿀 때**는 엔티티를 고치고 `V2__add_xxx.sql`처럼 버전 번호를 올린 새 파일을 추가합니다. 이미 배포된 마이그레이션 파일은 수정하지 않습니다(체크섬이 달라져 시작이 멈춥니다). `FlywayMigrationTest`가 빈 DB에 마이그레이션을 적용해 엔티티와 맞는지 확인하므로, 파일을 빠뜨리면 `./gradlew test`에서 걸립니다.

**Flyway 도입 전에 테이블을 만든 DB**(예전 방식대로 `dev`로 한 번 띄워 만든 prod DB)는 `flyway_schema_history`가 없어 `Found non-empty schema(s) ... but no schema history table`로 시작이 멈춥니다. 지켜야 할 데이터가 없다면 DB를 지우고 위의 처음 만드는 방법대로 다시 만드는 것이 가장 간단합니다. 데이터를 살려야 한다면 먼저 스키마가 `V1`과 같은지 확인합니다. baseline은 `V1`을 실행하지 않고 건너뛰기 때문입니다. `validate`는 빠진 테이블·컬럼은 잡지만, 남는 컬럼이나 달라진 제약조건(예: `ddl-auto=update`가 갱신하지 않는 CHECK 제약)은 잡지 못합니다.

```bash
# V1만 적용한 비교용 DB를 임시 컨테이너로 만들어 스키마를 덤프한다
docker run -d --name v1-check -e POSTGRES_PASSWORD=check postgres:16-alpine
until docker exec v1-check pg_isready -q -h 127.0.0.1 -U postgres; do sleep 1; done
docker exec -i v1-check psql -q -v ON_ERROR_STOP=1 -U postgres < src/main/resources/db/migration/V1__init_schema.sql
docker exec v1-check pg_dump -U postgres --schema-only --no-owner postgres > v1-schema.sql
docker rm -f v1-check

# 운영 DB의 스키마를 덤프해 비교한다 (pg_dump 버전은 DB 서버 버전 이상이어야 한다)
pg_dump --schema-only --no-owner -h <DB 호스트> -U <DB_USERNAME> <DB 이름> > prod-schema.sql
diff v1-schema.sql prod-schema.sql   # 주석·SET 줄 외의 차이가 없어야 한다
```

차이가 없으면 한 번만 `.env`에 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 추가해 실행합니다. 현재 상태를 버전 1로 기록하고 `V1`은 건너뜁니다. 기록이 생긴 뒤에는 이 줄을 지웁니다. 차이가 있으면 baseline 전에 운영 DB를 `V1`과 같게 고칩니다.

### 5. 실행

```bash
docker pull <레지스트리>/homeproject:$TAG
docker run -d --name homeproject --restart unless-stopped \
  --memory=768m \
  -p 8080:8080 --env-file .env \
  <레지스트리>/homeproject:$TAG

docker logs -f homeproject   # "Started HomeProjectApplication" 확인
```

`--memory`는 꼭 붙입니다. JVM은 heap 상한을 컨테이너 메모리 제한의 비율(기본 50%)로 잡는데, 제한이 없으면 서버 전체 메모리를 기준으로 잡아 OS와 다른 프로세스가 쓸 메모리까지 넘봅니다.

| 서버 메모리 | `--memory` | heap 비율 |
| --- | --- | --- |
| 1GB | `768m` | 기본값(50%, heap 384MB) 그대로 |
| 2GB | `1536m` | `-e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`로 올려도 됨 (heap 1152MB) |

heap 밖(metaspace, code cache 등)에서 약 300MB를 서버 크기와 관계없이 고정으로 쓰기 때문에, 1GB 서버에서 75%로 올리면 컨테이너가 메모리 제한에 걸려 강제 종료(OOMKilled)될 수 있습니다. PostgreSQL도 같은 서버에 띄운다면 그 메모리도 남겨야 하므로 2GB 이상 서버를 권장합니다.

DB가 같은 서버의 Docker 컨테이너라면 [Docker로 실행하기](#docker로-실행하기)처럼 같은 네트워크(`--network`)에 붙이고, `DB_URL`의 호스트에 DB 컨테이너 이름을 씁니다.

HTTPS는 앱 앞에 Nginx 같은 리버스 프록시나 클라우드 로드밸런서를 두고 처리합니다.

### 6. 업데이트와 되돌리기

새 이미지를 받아 컨테이너만 바꿉니다. 문제가 생기면 이전 태그로 같은 명령을 다시 실행하면 됩니다.

```bash
docker pull <레지스트리>/homeproject:$NEW_TAG
docker rm -f homeproject
docker run -d --name homeproject --restart unless-stopped --memory=768m -p 8080:8080 --env-file .env <레지스트리>/homeproject:$NEW_TAG
```

### 시작이 안 될 때

필수 설정이 빠지면 앱은 시작 단계에서 멈춥니다(종료 코드 1). `docker logs homeproject`에서 아래 메시지를 찾아보세요.

| 로그 메시지 | 원인 |
| --- | --- |
| `cors.allowed-origins에 쓰인 환경변수가 설정되지 않았습니다: ${CORS_ALLOWED_ORIGINS}` | `CORS_ALLOWED_ORIGINS` 없음 |
| `Could not resolve placeholder 'JWT_SECRET'` | `JWT_SECRET` 없음 |
| `'url' must start with "jdbc"` | `DB_URL` 없음 또는 형식 오류 |
| `password authentication failed for user "${DB_USERNAME}"` | `DB_USERNAME` 없음 (변수 이름이 그대로 계정명으로 쓰임) |
| `Schema-validation: missing ...` | 엔티티는 바뀌었는데 마이그레이션 파일을 추가하지 않음 ([4단계](#4-db-스키마-flyway) 참고) |
| `Found non-empty schema(s) ... but no schema history table` | Flyway 도입 전에 만든 DB ([4단계](#4-db-스키마-flyway)의 baseline 참고) |
| `permission denied for schema public` | 앱 계정이 데이터베이스 소유자가 아님 ([4단계](#4-db-스키마-flyway)의 처음 만들 때 참고) |
| `Migration checksum mismatch` | 이미 적용된 마이그레이션 파일을 수정함. 되돌리고 새 버전 파일로 변경 |

## 설정

`application.yml`은 프로필만 고르고(`SPRING_PROFILES_ACTIVE`, 기본값 `local`), 환경별 값은 프로필 파일에서 환경변수로 읽습니다. DB 계정 같은 비밀값은 저장소에 커밋하지 않습니다.

| 프로필 | 용도 | `ddl-auto` | Swagger |
| --- | --- | --- | --- |
| `local` | 로컬 개발 | `update` | 켜짐 |
| `dev` | 배포된 개발 서버 | `update` | 켜짐 |
| `prod` | 운영 | `validate` (스키마는 Flyway로 관리) | 꺼짐 |

### 환경변수

**local** (`./gradlew bootRun`, `application-local.yml`) — 모두 기본값이 있어서 설정하지 않아도 됩니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `HOMEPROJECT_DB_HOST` | `localhost` | DB 호스트 |
| `HOMEPROJECT_DB_PORT` | `5432` | DB 포트 |
| `HOMEPROJECT_DB_NAME` | `homeproject` | DB 이름 |
| `HOMEPROJECT_DB_USERNAME` | `admin` | DB 계정 |
| `HOMEPROJECT_DB_PASSWORD` | `1234` | DB 비밀번호 |
| `HOMEPROJECT_JWT_SECRET` | 로컬 전용 값 | JWT 서명 키 (32바이트 이상) |
| `HOMEPROJECT_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | 허용할 프론트 origin, 쉼표로 구분 |

로컬 환경변수에 `HOMEPROJECT_` 접두어를 붙이는 이유가 있습니다. 다른 프로젝트 때문에 셸에 export해 둔 `DB_HOST`, `JWT_SECRET` 같은 값이 끼어들면, 앱이 조용히 엉뚱한 DB에 붙을 수 있기 때문입니다.

**dev / prod** (Docker 실행) — 모두 필수이고 기본값이 없습니다. 목록과 설명은 [`.env.example`](.env.example)에 있습니다.

| 환경변수 | 설명 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod`(이미지 기본값) 또는 `dev` |
| `DB_URL` | JDBC URL (예: `jdbc:postgresql://db.example.com:5432/homeproject`) |
| `DB_USERNAME` | DB 계정 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상) |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 origin, 쉼표로 구분 |

필수 환경변수가 하나라도 없으면 앱이 시작되지 않습니다([시작이 안 될 때](#시작이-안-될-때) 참고).

## 테스트

```bash
./gradlew test                                   # 전체 테스트
./gradlew test --tests 'ScheduleApiTest'         # 테스트 클래스 하나
```

- API 테스트는 Testcontainers로 테스트 클래스마다 PostgreSQL 16 컨테이너를 띄우므로 **Docker가 실행 중이어야 합니다.** 로컬 DB는 건드리지 않습니다.
- `HomeProjectApplicationTests`(컨텍스트 로딩 테스트)만 예외로, 로컬 DB와 `application-local.yml`을 사용합니다.

## API 공통 규칙

모든 응답은 같은 형식으로 감싸집니다.

```json
{ "success": true, "data": { "...": "..." }, "error": null }
```

```json
{ "success": false, "data": null, "error": { "code": "FORBIDDEN", "message": "접근 권한이 없습니다." } }
```

- 인증: `Authorization: Bearer <accessToken>` (토큰 유효기간 24시간, 리프레시 토큰은 아직 없음)
- 토큰 없이 호출할 수 있는 API는 `/api/auth/**`뿐입니다.
- 삭제는 모두 soft delete입니다(`deletedAt`만 채움). 삭제된 데이터는 모든 API에서 404로 취급합니다.
- 시각은 시간대 없는 한국 시간(`2026-10-02T10:00:00`)으로 주고받습니다.

에러 코드 전체 목록은 [docs/API.md](docs/API.md#공통-에러-코드)에 있습니다.

## 프로젝트 구조

```
src/main/java/org/miniproject/homeproject
├── domain          # 도메인별 entity / repository / service / controller / dto
│   ├── auth        # 회원가입·로그인
│   ├── user        # 사용자 (다른 도메인이 참조하는 공통 주체)
│   ├── invitecode  # 초대코드
│   ├── schedule    # 일정
│   ├── expense     # 가계부
│   ├── task        # 할 일
│   ├── note        # 공유사항
│   └── dashboard   # 대시보드 (자체 저장소 없이 다른 도메인 서비스 조합)
└── global          # 여러 도메인이 함께 쓰는 것
    ├── config      # Security, CORS, Swagger, JPA Auditing, Clock
    ├── security    # JWT 필터·토큰 발급·인증 실패 응답
    ├── exception   # ErrorCode, 전역 예외 처리
    ├── response    # 공통 응답 형식(ApiResponse)
    └── entity      # BaseEntity (id, 생성·수정·삭제 시각)
```

도메인 사이 의존은 한 방향으로만 둡니다. 예를 들어 `schedule`, `task`는 `user`를 참조하지만, `user`는 다른 도메인을 모릅니다.

## 커밋 규칙

커밋 제목 앞에 변경 종류를 붙입니다.

- `feat:` 새 기능
- `fix:` 버그 수정
- `refactor:` 동작을 바꾸지 않는 코드 정리
- `docs:` 문서
- `test:` 테스트
