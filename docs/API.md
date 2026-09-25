# HomeProject API 명세서

작성일: 2026-09-24

## 개요

현재 구현된 API는 10개입니다: 인증 3개(`/api/auth/**`), 초대코드 1개(`/api/invite-code/**`), 일정 6개(`/api/schedules/**`). 기준 브랜치는 `feature/schedule`입니다.

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
| 400 | `INVALID_INPUT` | 잘못된 입력입니다. | 검증 실패, JSON 파싱 실패, 경로 변수 타입 불일치, 쿼리 파라미터 누락·범위 초과 |
| 400 | `INVALID_SCHEDULE_TIME` | 종료 시간은 시작 시간보다 빠를 수 없습니다. | 일정 종료 시각이 시작 시각보다 빠름 |
| 401 | `UNAUTHORIZED` | 인증이 필요합니다. | 토큰 없이 보호된 API 호출 |
| 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다. | 서명 오류·형식 오류 토큰 |
| 401 | `EXPIRED_TOKEN` | 만료된 토큰입니다. | 만료된 토큰 |
| 401 | `INVALID_CREDENTIALS` | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 |
| 403 | `FORBIDDEN` | 접근 권한이 없습니다. | 권한 없는 리소스 접근 |
| 404 | `INVALID_INVITE_CODE` | 유효하지 않은 초대코드입니다. | 가입 시 존재하지 않는 코드 |
| 404 | `INVITE_CODE_NOT_FOUND` | 초대코드를 찾을 수 없습니다. | id로 조회한 코드 없음 |
| 404 | `USER_NOT_FOUND` | 사용자를 찾을 수 없습니다. | 없거나 탈퇴한 사용자를 담당자로 지정 |
| 404 | `SCHEDULE_NOT_FOUND` | 일정을 찾을 수 없습니다. | 없거나 삭제된 일정 |
| 409 | `DUPLICATE_EMAIL` | 이미 가입된 이메일입니다. | 이메일 중복 |
| 409 | `OWNER_ALREADY_EXISTS` | 이미 가족장이 등록되어 있습니다. | 가족장 중복 가입 |
| 409 | `INVITE_CODE_FULL` | 이미 정원이 찼습니다. | 초대코드 사용 한도 초과 |
| 409 | `SCHEDULE_CANCELED` | 취소된 일정은 완료 처리할 수 없습니다. | 취소된 일정 완료 처리 |
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

## 일정(Schedule) API

모든 일정 API는 인증이 필요합니다. 수정·삭제는 일정 담당자(`assignee`) 본인만 할 수 있고, 조회·등록·완료 처리는 로그인한 사용자 누구나 할 수 있습니다. 삭제된 일정은 모든 API에서 없는 것으로 취급됩니다(404).

| Method | URL | 설명 | 권한 | 성공 상태 |
| --- | --- | --- | --- | --- |
| GET | `/api/schedules?year=&month=` | 해당 월의 일정 목록 | 로그인 | 200 |
| GET | `/api/schedules/{id}` | 일정 상세 | 로그인 | 200 |
| POST | `/api/schedules` | 일정 등록 | 로그인 | 201 |
| PATCH | `/api/schedules/{id}` | 일정 수정 | 담당자 본인 | 200 |
| PATCH | `/api/schedules/{id}/complete` | 완료 처리 | 로그인 | 200 |
| DELETE | `/api/schedules/{id}` | 삭제 (soft delete) | 담당자 본인 | 200 |

**일정 응답 객체** (`ScheduleResponse`)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 일정 id |
| `title` | string | 제목 |
| `startTime` | string | 시작 시각 (`yyyy-MM-ddTHH:mm:ss`, 시간대 없음) |
| `endTime` | string | 종료 시각 (`yyyy-MM-ddTHH:mm:ss`, 시간대 없음) |
| `assignee` | object | 담당자 `{ id, name }` |
| `status` | string | `PLANNED` \| `DONE` \| `CANCELED` |

```json
{
  "id": 1,
  "title": "병원 예약",
  "startTime": "2026-10-02T10:00:00",
  "endTime": "2026-10-02T11:00:00",
  "assignee": { "id": 6, "name": "홍길동" },
  "status": "PLANNED"
}
```

### GET /api/schedules — 월별 목록

해당 월에 조금이라도 걸친 일정을 시작 시각 순으로 돌려줍니다. 9/30~10/2 일정은 9월과 10월 목록에 모두 나옵니다.

**Query Parameter**

| 이름 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `year` | integer | O | 1~9999 | 연도 |
| `month` | integer | O | 1~12 | 월 |

```http
GET /api/schedules?year=2026&month=10
Authorization: Bearer <accessToken>
```

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "병원 예약",
      "startTime": "2026-10-02T10:00:00",
      "endTime": "2026-10-02T11:00:00",
      "assignee": { "id": 6, "name": "홍길동" },
      "status": "PLANNED"
    }
  ],
  "error": null
}
```

일정이 없으면 `data` 는 빈 배열입니다.

**Errors**: 400 `INVALID_INPUT` (파라미터 누락·범위 초과) · 401

### GET /api/schedules/{id} — 상세 조회

**Response 200**: `data` 에 일정 응답 객체 하나.

**Errors**: 401 · 404 `SCHEDULE_NOT_FOUND`

### POST /api/schedules — 등록

새 일정은 항상 `PLANNED` 상태로 만들어집니다.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `title` | string | O | 공백 불가, 최대 100자 | 제목 |
| `startTime` | string | O | `yyyy-MM-ddTHH:mm:ss` | 시작 시각 |
| `endTime` | string | O | `startTime` 이상 | 종료 시각 |
| `assigneeId` | number | O | 존재하는 사용자 id | 담당자 |

```json
{
  "title": "병원 예약",
  "startTime": "2026-10-02T10:00:00",
  "endTime": "2026-10-02T11:00:00",
  "assigneeId": 6
}
```

**Response 201**: `data` 에 생성된 일정 응답 객체.

**Errors**: 400 `INVALID_INPUT` · 400 `INVALID_SCHEDULE_TIME` · 401 · 404 `USER_NOT_FOUND`

### PATCH /api/schedules/{id} — 수정

담당자 본인만 수정할 수 있습니다. 보낸 필드만 바뀌고, 빠진 필드(또는 `null`)는 기존 값을 유지합니다. `assigneeId` 를 보내면 담당자를 다른 사람으로 넘길 수 있고, 넘긴 뒤에는 새 담당자만 수정·삭제할 수 있습니다.

**Request Body** (모두 선택)

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `title` | string | 공백 불가, 최대 100자 | 제목 |
| `startTime` | string | `yyyy-MM-ddTHH:mm:ss` | 시작 시각 |
| `endTime` | string | `yyyy-MM-ddTHH:mm:ss` | 종료 시각 |
| `assigneeId` | number | 존재하는 사용자 id | 새 담당자 |

시작·종료 시각은 하나만 보내도 기존 값과 합쳐서 검사합니다. 예를 들어 `startTime` 만 기존 `endTime` 보다 늦게 보내면 `INVALID_SCHEDULE_TIME` 입니다.

```json
{
  "title": "마트 장보기"
}
```

**Response 200**: `data` 에 수정된 일정 응답 객체.

**Errors**: 400 `INVALID_INPUT` · 400 `INVALID_SCHEDULE_TIME` · 401 · 403 `FORBIDDEN` (담당자가 아님) · 404 `SCHEDULE_NOT_FOUND` · 404 `USER_NOT_FOUND`

### PATCH /api/schedules/{id}/complete — 완료 처리

상태를 `DONE` 으로 바꿉니다. 요청 본문은 없습니다. 이미 `DONE` 인 일정은 그대로 성공합니다.

**Response 200**: `data` 에 `status: "DONE"` 인 일정 응답 객체.

**Errors**: 401 · 404 `SCHEDULE_NOT_FOUND` · 409 `SCHEDULE_CANCELED`

### DELETE /api/schedules/{id} — 삭제

담당자 본인만 삭제할 수 있습니다. 데이터는 지우지 않고 `deletedAt` 만 채우며(soft delete), 이후 조회·수정·삭제에서는 404가 납니다.

**Response 200**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors**: 401 · 403 `FORBIDDEN` (담당자가 아님) · 404 `SCHEDULE_NOT_FOUND`

가계부(expense)·역할 분담(task)·공유사항(note)은 엔티티와 Repository만 있고 API는 아직 없습니다.

## 데이터 모델

모든 엔티티는 공통 필드 `id`, `createdAt`, `updatedAt`, `deletedAt`(soft delete)을 가집니다. `deletedAt` 이 채워진 사용자는 로그인할 수 없고 담당자로 지정할 수 없습니다. 초대코드와 일정은 없는 것으로 취급됩니다.

| 엔티티 (테이블) | 주요 필드 | API 여부 |
| --- | --- | --- |
| User (`users`) | `name`, `email`(unique), `password`(BCrypt), `role` | 인증 API에서 사용 |
| InviteCode (`invite_codes`) | `code`(unique), `maxUses`, `usedCount`, `createdBy`→User | 있음 |
| Schedule (`schedules`) | `title`, `startTime`, `endTime`, `assignee`→User, `status` | 있음 |
| Expense (`expenses`) | `category`, `amount`(decimal), `dueDate`, `paidStatus`, `memo` | 없음 |
| Task (`tasks`) | `title`, `assignee`→User, `recurring`, `status` | 없음 |
| SharedNote (`shared_notes`) | `title`, `content`(text), `category`, `assignee`→User | 없음 |

### Enum

| Enum | 값 |
| --- | --- |
| `Role` | `OWNER`(가족장), `MEMBER`(구성원) |
| `ScheduleStatus` | `PLANNED`, `DONE`, `CANCELED` |
| `TaskStatus` | `TODO`, `DONE` |
