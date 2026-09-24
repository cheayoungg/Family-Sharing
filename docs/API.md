# HomeProject API 명세서

작성일: 2026-09-24

## 개요

현재 구현된 API는 4개입니다: 인증 3개(`/api/auth/**`), 초대코드 1개(`/api/invite-code/**`). 기준 브랜치는 `feature/login`입니다.

### 인증

- 방식: Stateless JWT (HS256). 세션·쿠키를 쓰지 않습니다.
- 헤더: `Authorization: Bearer <accessToken>`
- 토큰 만료: 24시간 (86,400초). Refresh 토큰은 아직 없습니다.
- 토큰 payload: `sub` = 사용자 id, `role` = `OWNER` | `MEMBER`
- 인증 없이 호출 가능: `/api/auth/**`. 그 외 모든 경로는 인증이 필요합니다(없는 경로도 토큰이 없으면 404가 아니라 401).

### 공통 응답 포맷

모든 응답은 `ApiResponse` 로 감싸집니다.

성공:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "DUPLICATE_EMAIL",
    "message": "이미 가입된 이메일입니다."
  }
}
```

요청 값 검증(`@Valid`) 실패 시 `code` 는 `INVALID_INPUT`, `message` 는 `필드명: 검증 메시지` 형식입니다(첫 번째 오류 1건만).

### 공통 에러 코드

| HTTP | code | message | 발생 상황 |
| --- | --- | --- | --- |
| 400 | `INVALID_INPUT` | 잘못된 입력입니다. | 검증 실패, JSON 파싱 실패, 경로 변수 타입 불일치 |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 없이 보호된 API 호출 |
| 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다. | 서명 오류·형식 오류 토큰 |
| 401 | `EXPIRED_TOKEN` | 만료된 토큰입니다. | 만료된 토큰 |
| 401 | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 |
| 403 | `FORBIDDEN` | 접근 권한이 없습니다. | 권한 없는 리소스 접근 |
| 404 | `INVALID_INVITE_CODE` | 유효하지 않은 초대코드입니다. | 가입 시 존재하지 않는 코드 |
| 404 | `INVITE_CODE_NOT_FOUND` | 초대코드를 찾을 수 없습니다. | id로 조회한 코드 없음 |
| 409 | `DUPLICATE_EMAIL` | 이미 가입된 이메일입니다. | 이메일 중복 |
| 409 | `OWNER_ALREADY_EXISTS` | 이미 가족장이 등록되어 있습니다. | 가족장 중복 가입 |
| 409 | `INVITE_CODE_FULL` | 이미 정원이 찼습니다. | 초대코드 사용 한도 초과 |
| 409 | `DATA_CONFLICT` | 요청이 기존 데이터와 충돌합니다. | DB 무결성 제약 위반(동시 가입 등) |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류가 발생했습니다. | 처리되지 않은 예외 |

404/405/415 같은 Spring MVC 표준 오류는 `code` 에 HTTP 상태명(`NOT_FOUND`, `METHOD_NOT_ALLOWED` 등)이 들어갑니다.

## 인증(Auth) API

| Method | URL | 설명 | 인증 | 성공 상태 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup-owner` | 가족장 회원가입 + 초대코드 발급 | 불필요 | 201 |
| POST | `/api/auth/signup` | 초대코드로 가족 구성원 회원가입 | 불필요 | 201 |
| POST | `/api/auth/login` | 로그인, Access Token 발급 | 불필요 | 200 |

### POST /api/auth/signup-owner — 가족장 회원가입

가족장(`OWNER`)을 만들고, 나머지 가족이 쓸 초대코드를 함께 발급합니다. 시스템 전체에 가족장은 1명만 등록할 수 있습니다.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | string | O | 최대 50자 | 이름 |
| `email` | string | O | 이메일 형식 | 로그인 이메일 |
| `password` | string | O | 8~64자 | 비밀번호 |
| `familySize` | integer | O | 1 이상 | 본인 포함 총 가족 인원 |

```json
{
  "name": "홍길동",
  "email": "owner@example.com",
  "password": "password123",
  "familySize": 4
}
```

**Response 201**

초대코드 `maxUses` = `familySize - 1` (본인 제외). 코드는 `FAMILY-XXXX-XXXX` 형식이며, 헷갈리는 문자(0/O, 1/I)는 쓰지 않습니다.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "홍길동",
    "email": "owner@example.com",
    "role": "OWNER",
    "inviteCode": {
      "id": 1,
      "code": "FAMILY-7KQM-X2PA",
      "maxUses": 3,
      "usedCount": 0
    }
  },
  "error": null
}
```

**Errors**: 400 `INVALID_INPUT` · 409 `OWNER_ALREADY_EXISTS` · 409 `DUPLICATE_EMAIL`

### POST /api/auth/signup — 구성원 회원가입

가족장이 받은 초대코드로 `MEMBER` 를 만듭니다. 성공하면 초대코드 `usedCount` 가 1 증가합니다. 동시 가입은 행 잠금으로 직렬화되어 정원을 넘지 않습니다.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `name` | string | O | 최대 50자 | 이름 |
| `email` | string | O | 이메일 형식 | 로그인 이메일 |
| `password` | string | O | 8~64자 | 비밀번호 |
| `inviteCode` | string | O | 공백 불가 | 초대코드 (예: `FAMILY-7KQM-X2PA`) |

```json
{
  "name": "홍길순",
  "email": "member@example.com",
  "password": "password123",
  "inviteCode": "FAMILY-7KQM-X2PA"
}
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 2,
    "name": "홍길순",
    "email": "member@example.com",
    "role": "MEMBER"
  },
  "error": null
}
```

**Errors**: 400 `INVALID_INPUT` · 404 `INVALID_INVITE_CODE` · 409 `INVITE_CODE_FULL` · 409 `DUPLICATE_EMAIL` · 409 `DATA_CONFLICT`

### POST /api/auth/login — 로그인

이메일·비밀번호가 맞으면 Access Token을 돌려줍니다. 가입 여부가 드러나지 않도록 이메일 없음·비밀번호 불일치·탈퇴 계정을 모두 같은 에러로 응답합니다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | O | 로그인 이메일 |
| `password` | string | O | 비밀번호 |

```json
{
  "email": "owner@example.com",
  "password": "password123"
}
```

**Response 200**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | JWT |
| `tokenType` | string | 항상 `Bearer` |
| `expiresIn` | number | 만료까지 남은 초 (86400) |

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  },
  "error": null
}
```

**Errors**: 400 `INVALID_INPUT` · 401 `INVALID_CREDENTIALS`

## 초대코드(InviteCode) API

| Method | URL | 설명 | 인증 | 성공 상태 |
| --- | --- | --- | --- | --- |
| PATCH | `/api/invite-code/{id}/increase-max-uses` | 초대코드 사용 한도 늘리기 | 필요 (코드 발급자만) | 200 |

### PATCH /api/invite-code/{id}/increase-max-uses — 사용 한도 늘리기

가족이 늘었을 때 기존 초대코드의 `maxUses` 를 `additionalSlots` 만큼 늘립니다. 코드를 발급한 사용자(가족장)만 호출할 수 있습니다.

**Headers**: `Authorization: Bearer <accessToken>`

**Path Variable**

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 초대코드 id (가족장 가입 응답의 `inviteCode.id`) |

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `additionalSlots` | integer | O | 1 이상 | 추가할 사용 횟수 |

```json
{
  "additionalSlots": 2
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "code": "FAMILY-7KQM-X2PA",
    "maxUses": 5,
    "usedCount": 3
  },
  "error": null
}
```

**Errors**: 400 `INVALID_INPUT` · 401 `UNAUTHORIZED` / `INVALID_TOKEN` / `EXPIRED_TOKEN` · 403 `FORBIDDEN` (발급자가 아님) · 404 `INVITE_CODE_NOT_FOUND`

일정(schedule)·가계부(expense)·역할 분담(task)·공유사항(note)은 엔티티와 Repository만 있고 API는 아직 없습니다.

## 데이터 모델

모든 엔티티는 공통 필드 `id`, `createdAt`, `updatedAt`, `deletedAt`(soft delete)을 가집니다. `deletedAt` 이 채워진 사용자는 로그인할 수 없고, 초대코드는 없는 것으로 취급됩니다.

| 엔티티 (테이블) | 주요 필드 | API 여부 |
| --- | --- | --- |
| User (`users`) | `name`, `email`(unique), `password`(BCrypt), `role` | 인증 API에서 사용 |
| InviteCode (`invite_codes`) | `code`(unique), `maxUses`, `usedCount`, `createdBy`→User | 있음 |
| Schedule (`schedules`) | `title`, `startTime`, `endTime`, `assignee`→User, `status` | 없음 |
| Expense (`expenses`) | `category`, `amount`(decimal), `dueDate`, `paidStatus`, `memo` | 없음 |
| Task (`tasks`) | `title`, `assignee`→User, `recurring`, `status` | 없음 |
| SharedNote (`shared_notes`) | `title`, `content`(text), `category`, `assignee`→User | 없음 |

### Enum

| Enum | 값 |
| --- | --- |
| `Role` | `OWNER`(가족장), `MEMBER`(구성원) |
| `ScheduleStatus` | `PLANNED`, `DONE`, `CANCELED` |
| `TaskStatus` | `TODO`, `DONE` |
