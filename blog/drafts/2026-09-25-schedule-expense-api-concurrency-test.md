# 동시성 테스트로 잠금을 검증하고, 일정·가계부 API까지 붙인 하루

어제 회원가입이랑 초대코드를 만들어 뒀는데, 정말 동시에 가입해도 정원을 안 넘는지 확인을 못 한 게 계속 마음에 걸렸어요. 그래서 오늘은 동시성 테스트로 하루를 시작했고, 그다음 일정과 가계부 API를 차례로 붙였습니다.

## 오늘 무엇을 구현했는가

오늘 커밋은 여섯 개예요.

| 커밋 | 내용 |
| --- | --- |
| `test: 초대코드 동시성 테스트 추가` | 실제 PostgreSQL에서 행 잠금 검증 |
| `feat: 일정(Schedule) REST API 구현` | 월별 목록, 상세, 등록, 수정, 완료, 삭제 |
| `feat: 가계부(Expense) REST API 구현` | 납부 상태 필터 조회, 등록, 납부 처리, 삭제 |
| `docs: API 명세서에 일정 API 추가` / `가계부 API 추가` | 명세서 갱신 |
| `docs: 블로그 초안 작성 스킬과 ... 초안 추가` | 이 글을 쓰게 해 준 스킬 |

### 1. 초대코드 동시성 테스트

스레드 10개를 준비시켜 놓고 한꺼번에 출발시켜서, 요청이 최대한 겹치게 만들었어요.

```java
for (int i = 0; i < count; i++) {
    int index = i;
    futures.add(executor.submit(() -> {
        ready.countDown();
        start.await();   // 모두 준비될 때까지 대기
        task.run(index);
        return null;
    }));
}
ready.await();
start.countDown();       // 동시에 출발
```

시나리오는 세 가지예요.

- 정원 3명인 코드로 10명이 동시에 가입 → 딱 3명만 성공
- 한도 +1 요청 10건이 동시에 들어옴 → 한도가 정확히 10 늘어남
- 가입과 한도 증가가 섞여서 들어옴 → 서로의 변경을 덮어쓰지 않음

### 2. 일정 API

| Method | URL | 설명 |
| --- | --- | --- |
| GET | `/api/schedules?year=&month=` | 해당 월 일정 목록 |
| GET | `/api/schedules/{id}` | 상세 |
| POST | `/api/schedules` | 등록 |
| PATCH | `/api/schedules/{id}` | 수정 (담당자 본인만) |
| PATCH | `/api/schedules/{id}/complete` | 완료 처리 |
| DELETE | `/api/schedules/{id}` | 삭제 (담당자 본인만, soft delete) |

### 3. 가계부 API

| Method | URL | 설명 |
| --- | --- | --- |
| GET | `/api/expenses?status=UNPAID\|PAID` | 납부 상태로 필터 조회 |
| POST | `/api/expenses` | 등록 |
| PATCH | `/api/expenses/{id}/pay` | 납부 처리 |
| DELETE | `/api/expenses/{id}` | 삭제 (soft delete) |

두 도메인 모두 Controller → Service → Repository로 나누고, 응답은 `{success, data, error}` 공통 포맷으로 맞췄어요.

## 왜 그렇게 설계했는가

### 동시성 테스트는 H2가 아니라 진짜 PostgreSQL로

초대코드는 `SELECT ... FOR UPDATE`로 잠금을 걸어서 정원을 지켜요. 그런데 이걸 H2 같은 인메모리 DB로 테스트하면, 잠금 동작이 PostgreSQL과 달라서 "테스트는 통과했는데 운영에서는 깨지는" 상황이 생길 수 있어요. 그래서 Testcontainers로 테스트마다 PostgreSQL 컨테이너를 띄웠습니다.

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
```

Spring Boot의 `@ServiceConnection` 덕분에 datasource 설정을 따로 안 해도 컨테이너에 알아서 붙어요. 덤으로 로컬 DB를 건드리지 않아서, 가족장이 이미 가입돼 있는 로컬 DB 때문에 테스트가 깨질 일도 없어졌어요.

### 담당자 권한 검사는 한 곳에서

일정 수정과 삭제는 담당자 본인만 할 수 있어요. 이 검사를 메서드마다 쓰지 않고 서비스의 별도 메서드로 뺐어요.

```java
private void validateAssignee(Schedule schedule, Long userId) {
    if (!schedule.isAssignedTo(userId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
```

"담당자인가?"라는 판단은 엔티티(`isAssignedTo`)가 하고, "아니면 403"이라는 정책은 서비스가 가져요. 나중에 담당자만 할 수 있는 작업이 늘어나도 한 줄만 추가하면 돼요.

### 월별 조회는 "그 달에 걸친" 일정 전부

9월 30일에 떠나서 10월 2일에 돌아오는 여행은 9월 달력에도, 10월 달력에도 보여야 하잖아요. 그래서 "시작일이 그 달인 일정"이 아니라 "그 달과 겹치는 일정"을 가져오게 했어요.

```java
select s from Schedule s left join fetch s.assignee
where s.deletedAt is null
  and s.startTime < :to
  and (s.endTime > :from or s.startTime >= :from)
order by s.startTime, s.id
```

`left join fetch s.assignee`는 목록마다 담당자 이름을 보여주기 때문에 넣었어요. 이게 없으면 일정 개수만큼 사용자 조회 쿼리가 추가로 나가요(N+1).

### 부분 수정인데 시간 검사는 합쳐서

일정 수정은 PATCH라서 보낸 필드만 바꿔요. 그런데 시작 시간만 보냈을 때 그 값만 검사하면, 기존 종료 시간보다 늦은 시작 시간이 들어갈 수 있어요. 그래서 새 값과 기존 값을 먼저 합친 다음에 검사해요.

```java
LocalDateTime startTime = request.startTime() != null ? request.startTime() : schedule.getStartTime();
LocalDateTime endTime = request.endTime() != null ? request.endTime() : schedule.getEndTime();
validateTimeRange(startTime, endTime);
```

### 잘못된 쿼리 파라미터도 같은 에러 포맷으로

`month=13`처럼 쿼리 파라미터가 잘못되면 원래는 Spring 기본 예외가 나서 에러 코드가 `BAD_REQUEST`로 나갔어요. 다른 검증 오류는 전부 `INVALID_INPUT`인데 이것만 달라서, 전역 예외 처리기에 `HandlerMethodValidationException`과 `MissingServletRequestParameterException` 처리를 추가해서 맞췄어요.

### 가계부 금액은 요청 단계에서 막기

금액 컬럼은 `numeric(38,2)`라서 소수점 둘째 자리까지만 저장돼요. `100.123`을 그냥 받으면 에러 없이 `100.12`로 반올림돼 버려요. 돈이 조용히 바뀌는 건 싫어서 요청 DTO에서 막았어요.

```java
// DB 컬럼이 numeric(38,2)라 소수점 셋째 자리부터는 반올림돼 저장되므로 요청 단계에서 막는다
@NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
```

납부 상태 필터는 엔티티에 `boolean paidStatus`로 저장돼 있지만, 쿼리 파라미터는 `?status=UNPAID|PAID`처럼 읽기 쉬운 값으로 받고 싶어서 필터용 enum(`ExpensePaymentStatus`)을 따로 만들었어요. 목록에 없는 값이 오면 자동으로 400이 나가요.

## 다음에 할 일

- **역할 분담(task)·공유사항(note) API**: 엔티티와 Repository만 있어요. 남은 두 도메인이에요.
- **일정 취소 API**: `ScheduleStatus`에 `CANCELED`가 있고 취소된 일정은 완료 처리를 막아 뒀는데, 정작 일정을 취소하는 API가 아직 없어요.
- **가계부 권한**: `Expense`에는 등록자 정보가 없어서 로그인한 사람이면 누구나 삭제할 수 있어요. 본인만 지우게 하려면 `createdBy` 같은 필드가 필요해요.
- **테스트 컨테이너 공유**: 테스트 클래스 세 개가 각자 PostgreSQL 컨테이너를 띄우고 있어요. 공통 베이스 클래스로 묶으면 테스트가 더 빨라질 것 같아요.
- **기존 `contextLoads` 테스트**: 아직 로컬 DB와 `application-local.yml`에 기대고 있어서, 설정 파일이 없는 환경에서는 깨질 수 있어요.
