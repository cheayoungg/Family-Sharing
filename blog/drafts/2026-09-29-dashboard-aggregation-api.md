# 홈 화면 한 장에 모으기: 대시보드 API와 "오늘"의 기준

도메인 API를 다 만들고 나니, 앱을 열었을 때 제일 먼저 보여줄 화면이 필요해졌어요. "오늘 뭐 있지? 아직 안 한 집안일은? 이번 달 안 낸 돈은?"을 한눈에 보는 대시보드요. 오늘은 이걸 API 하나로 묶었어요.

## 오늘 무엇을 구현했는가

오늘 커밋은 두 개예요.

- `feat: 대시보드 API 구현`
- `docs: API 명세서에 대시보드 API 추가`

`GET /api/dashboard` 하나를 부르면 세 가지를 한 번에 돌려줘요.

```json
{
  "date": "2026-10-02",
  "todaySchedules": [ ... ],
  "incompleteTasks": [ ... ],
  "unpaidExpenses": [ ... ]
}
```

- **오늘 일정**: 오늘에 조금이라도 걸친 일정 (어젯밤에 시작해서 오늘 새벽에 끝나는 것도 포함)
- **미완료 할 일**: 상태가 `TODO`인 할 일
- **미납 지출**: 아직 안 낸 지출 전부, 기한 순

이걸 위해 `DashboardService`를 새로 만들고, 일정 서비스에 하루 단위 조회를 추가하고, "오늘"을 판단할 시계(`Clock`)를 빈으로 등록했어요.

## 왜 그렇게 설계했는가

### 대시보드는 저장소 없이, 다른 서비스만 조합

대시보드는 자기 데이터가 없어요. 그래서 Repository를 두지 않고, 이미 만들어 둔 세 서비스의 조회 메서드를 모아서 조립만 하게 했어요.

```java
@Transactional(readOnly = true)
public DashboardResponse getDashboard() {
    LocalDate today = LocalDate.now(clock);
    return new DashboardResponse(
            today,
            scheduleService.getDailySchedules(today),
            getIncompleteTasks(),
            expenseService.getExpenses(ExpensePaymentStatus.UNPAID));
}
```

이렇게 하면 "삭제된 건 빼고", "담당자는 같이 조회" 같은 규칙을 대시보드에서 다시 짤 필요가 없어요. 각 도메인이 이미 지키고 있으니까요. 의존 방향도 대시보드 → 각 도메인 한쪽으로만 흐르고, 도메인 쪽은 대시보드의 존재를 몰라요.

세 조회는 읽기 전용 트랜잭션 하나로 묶었어요. 각 서비스 메서드에도 `@Transactional`이 붙어 있지만, 바깥 트랜잭션에 합류해서 커넥션 하나로 처리돼요.

### 일정은 월별 조회와 같은 쿼리를 재사용

일정 서비스에는 월 단위 조회만 있었어요. 하루 단위 조회를 새로 만들면서 쿼리를 따로 짜지 않고, "구간과 겹치는 일정" 쿼리를 그대로 쓰도록 공통 부분을 빼냈어요.

```java
public List<ScheduleResponse> getDailySchedules(LocalDate date) {
    return getOverlappingSchedules(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
}

private List<ScheduleResponse> getOverlappingSchedules(LocalDateTime from, LocalDateTime to) {
    return scheduleRepository.findAllOverlapping(from, to).stream()
            .map(ScheduleResponse::from)
            .toList();
}
```

월이든 하루든 "시작이 구간 끝보다 앞이고, 끝이 구간 시작보다 뒤"라는 같은 조건이라, 경계 처리가 두 곳에서 따로 놀 일이 없어요.

### "오늘"은 한국 시간으로

제일 고민한 부분이에요. `LocalDate.now()`를 그냥 쓰면 서버 시간대를 따라가요. 서버가 UTC로 돌면 한국 시간 새벽 0시~9시 사이에는 대시보드가 **어제**를 "오늘"로 보여주게 돼요. 일정 시각은 시간대 없이 한국 시간으로 저장되고 있으니, 기준이 어긋나는 거죠.

그래서 한국 시간 시계를 빈으로 등록하고, 대시보드는 이 시계로 날짜를 구해요.

```java
// 일정 시각은 시간대 없이 한국 시간으로 저장되므로, "오늘"도 서버 시간대와 관계없이 한국 시간으로 판단한다
private static final ZoneId FAMILY_ZONE = ZoneId.of("Asia/Seoul");

@Bean
public Clock clock() {
    return Clock.system(FAMILY_ZONE);
}
```

`Clock`을 주입받게 해 두니 테스트도 편해졌어요. 테스트에서는 UTC로는 아직 10월 1일인데 한국 시간으로는 10월 2일 새벽인 시각으로 시계를 고정했어요. 대시보드가 10월 2일 일정을 보여주면 한국 시간 기준으로 동작하는 거예요.

```java
@Bean
@Primary
Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-10-01T16:30:00Z"), ZoneId.of("Asia/Seoul"));
}
```

### 미완료 할 일은 일단 메모리에서 거르기

할 일 서비스에는 상태 필터가 없어요. 요청대로 기존 조회 메서드를 재사용하려고, 전체를 받아서 대시보드에서 `TODO`만 남겼어요.

```java
return taskService.getTasks(null).stream()
        .filter(task -> task.status() == TaskStatus.TODO)
        .toList();
```

가족 단위라 할 일이 수천 개씩 쌓일 일은 없을 것 같아서 지금은 이걸로 충분하다고 봤어요. 다만 완료된 할 일까지 매번 읽어 오는 건 맞아요.

## 다음에 할 일

- **할 일 상태 필터**: 완료된 할 일이 쌓이면 `TaskService`에 상태 조건을 넣어서 DB에서 거르는 게 좋겠어요.
- **오늘 일정에 취소된 일정도 나오는 문제**: 지금은 완료·취소된 일정도 같이 나와요. 홈 화면에서 취소된 일정까지 보여줄지 정해야 해요.
- **시간대 기준 맞추기**: 대시보드는 한국 시간을 쓰는데, `createdAt`(JPA Auditing)이나 삭제할 때 찍는 `deletedAt`(`LocalDateTime.now()`)은 여전히 서버 시간대를 따라가요. 서버를 UTC로 띄우면 이 시각들만 9시간 어긋나요. 전체 기준을 한 번 정리해야 할 것 같아요.
- **브랜치 머지**: 이제 `feature/login`부터 `feature/dashboard`까지 브랜치가 여섯 개 이어져 있어요. 슬슬 `main`에 올려야 해요.
