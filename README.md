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
- Spring Data JPA, PostgreSQL
- Spring Security + JWT ([jjwt](https://github.com/jwtk/jjwt) 0.12.6), Stateless 인증
- springdoc-openapi 2.8.17 (Swagger UI)
- JUnit 5, Testcontainers (테스트용 PostgreSQL)

## 시작하기

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

## 설정

`application.yml`은 프로필만 고르고(`SPRING_PROFILES_ACTIVE`, 기본값 `local`), 환경별 값은 프로필 파일에서 환경변수로 읽습니다. DB 계정 같은 비밀값은 저장소에 커밋하지 않습니다.

| 프로필 | 용도 | `ddl-auto` | Swagger |
| --- | --- | --- | --- |
| `local` | 로컬 개발 | `update` | 켜짐 |
| `dev` | 배포된 개발 서버 | `update` | 켜짐 |
| `prod` | 운영 | `validate` (스키마는 직접 관리) | 꺼짐 |

### 환경변수

| local (기본값 있음) | dev / prod (필수, 기본값 없음) | 설명 |
| --- | --- | --- |
| `HOMEPROJECT_DB_HOST` (`localhost`) | `DB_HOST` | DB 호스트 |
| `HOMEPROJECT_DB_PORT` (`5432`) | `DB_PORT` | DB 포트 |
| `HOMEPROJECT_DB_NAME` (`homeproject`) | `DB_NAME` | DB 이름 |
| `HOMEPROJECT_DB_USERNAME` (`admin`) | `DB_USERNAME` | DB 계정 |
| `HOMEPROJECT_DB_PASSWORD` (`1234`) | `DB_PASSWORD` | DB 비밀번호 |
| `HOMEPROJECT_JWT_SECRET` (로컬 전용 값) | `JWT_SECRET` | JWT 서명 키 (32바이트 이상) |
| `HOMEPROJECT_CORS_ALLOWED_ORIGINS` (`http://localhost:3000`) | `CORS_ALLOWED_ORIGINS` | 허용할 프론트 origin, 쉼표로 구분 |

로컬 환경변수에 `HOMEPROJECT_` 접두어를 붙이는 이유가 있습니다. 다른 프로젝트 때문에 셸에 export해 둔 `DB_HOST`, `JWT_SECRET` 같은 값이 끼어들면, 앱이 조용히 엉뚱한 DB에 붙을 수 있기 때문입니다.

dev/prod는 필수 환경변수가 하나라도 없으면 앱이 시작되지 않습니다.

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
