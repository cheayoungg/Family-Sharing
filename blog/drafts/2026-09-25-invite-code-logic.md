# 가족 초대코드 만들기: 발급부터 동시 가입까지

가족 공유 서비스에서는 아무나 가족 공간에 들어오면 안 되니까, 가족장이 초대코드를 받아서 가족에게 나눠주는 방식으로 가입을 열었어요. 코드 하나로 정해진 인원만 가입할 수 있어야 해서, 생각보다 신경 쓸 게 많았어요. 발급, 사용, 한도 변경 순서로 정리해 볼게요.

## 전체 흐름

1. 가족장이 가입하면서 가족이 모두 몇 명인지(`familySize`) 알려주면, 초대코드가 하나 발급돼요.
2. 가족장이 이 코드를 가족에게 공유하고, 가족은 코드를 넣어서 가입해요.
3. 가입할 때마다 코드의 사용 횟수(`usedCount`)가 하나씩 올라가고, 한도(`maxUses`)에 닿으면 더 이상 가입할 수 없어요.
4. 가족이 늘면 가족장이 한도를 늘릴 수 있어요.

초대코드 엔티티는 이 네 가지 값이 전부예요.

```java
@Column(nullable = false, unique = true)
private String code;        // FAMILY-7KQM-X2PA

@Column(nullable = false)
private int maxUses;        // 최대 가입 가능 인원

@Column(nullable = false)
private int usedCount;      // 지금까지 가입한 인원

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "created_by_id", nullable = false)
private User createdBy;     // 발급한 가족장
```

## 1. 발급: 가족장 가입과 함께

초대코드는 따로 발급하는 API가 없고, 가족장이 가입할 때 같이 만들어져요. 가족장 본인은 이미 가입했으니 한도는 `familySize - 1`이에요. 4인 가족이면 코드로 3명이 더 들어올 수 있는 거죠.

```java
User owner = userRepository.save(newUser(request.name(), request.email(), request.password(), Role.OWNER));
// 가족장 본인은 이미 가입했으므로 나머지 인원만큼 코드를 쓸 수 있다
InviteCode inviteCode = inviteCodeService.issue(owner, request.familySize() - 1);
```

가족장 가입과 코드 발급은 한 트랜잭션이라, 코드 생성에 실패하면 가족장 가입도 같이 취소돼요.

### 코드는 사람이 옮겨 적기 쉽게

코드는 카톡으로 보낼 수도 있지만, 옆에 있는 가족한테 말로 불러주거나 손으로 입력할 수도 있어요. 그래서 헷갈리기 쉬운 `0/O`, `1/I`는 문자 목록에서 뺐어요.

```java
private static final String CODE_PREFIX = "FAMILY-";
// 손으로 옮겨 적을 때 헷갈리는 0/O, 1/I는 뺐다
private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
private static final int CODE_GROUP_LENGTH = 4;
```

32개 문자 중에서 4자리씩 두 묶음을 뽑으니까 경우의 수는 32⁸, 약 1조 개예요. 난수는 예측하기 어려운 `SecureRandom`으로 뽑았어요. 초대코드가 곧 가입 권한이라, 순서를 예측할 수 있으면 안 되거든요.

### 중복은 두 겹으로 막기

코드를 만들 때마다 DB에 같은 코드가 있는지 확인하고, 있으면 다시 뽑아요. 다섯 번 연속으로 겹치면 포기하고요.

```java
for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
    String code = CODE_PREFIX + randomGroup() + "-" + randomGroup();
    if (!inviteCodeRepository.existsByCode(code)) {
        return code;
    }
}
throw new IllegalStateException("초대코드 생성에 실패했습니다.");
```

다만 "확인하고 저장하기" 사이에 틈이 있어서, 두 요청이 정말 같은 순간에 같은 코드를 뽑으면 둘 다 확인을 통과할 수 있어요. 그래서 `code` 컬럼에 `unique` 제약도 걸어 뒀어요. 이 경우 DB가 두 번째 저장을 막고, 전역 예외 처리기가 409 `DATA_CONFLICT`로 응답해요. 1조 분의 1 확률이라 실제로 일어날 일은 거의 없지만, 마지막 방어선은 DB에 두는 게 마음이 편하더라고요.

## 2. 사용: 동시에 가입해도 정원을 넘지 않게

제일 고민한 부분이에요. 가입 로직을 단순하게 쓰면 이렇게 돼요.

1. 코드를 조회한다.
2. `usedCount < maxUses`인지 확인한다.
3. 회원을 저장하고 `usedCount`를 1 올린다.

한 명씩 가입하면 문제없어요. 그런데 한도가 1명 남은 상태에서 두 사람이 동시에 가입하면 이런 일이 생겨요.

| 순서 | 요청 A | 요청 B |
| --- | --- | --- |
| 1 | 코드 조회 (`usedCount = 2`, `maxUses = 3`) | |
| 2 | | 코드 조회 (`usedCount = 2`) |
| 3 | 정원 검사 통과 | |
| 4 | | 정원 검사 통과 |
| 5 | 가입, `usedCount = 3` 저장 | |
| 6 | | 가입, `usedCount = 3` 저장 |

두 명 다 가입했는데 `usedCount`는 3이에요. 정원을 넘겼고, 사용 횟수까지 틀어졌어요.

### 비관적 락으로 한 줄로 세우기

그래서 코드를 조회할 때 행에 잠금을 걸었어요. JPA에서 `PESSIMISTIC_WRITE`를 쓰면 `SELECT ... FOR UPDATE` 쿼리가 나가요.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from InviteCode i where i.code = :code and i.deletedAt is null")
Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);
```

이제 요청 B는 2번에서 멈춰서, 요청 A가 커밋할 때까지 기다려요. A가 커밋하면 B는 `usedCount = 3`으로 바뀐 값을 읽고, 정원 검사에서 걸려서 409 `INVITE_CODE_FULL`을 받아요.

가입 서비스는 이 순서로 동작해요.

```java
validateEmailNotTaken(request.email());

// 여기서 잡은 행 잠금은 이 트랜잭션이 커밋될 때까지 유지된다
InviteCode inviteCode = inviteCodeService.getAvailableForUpdate(request.inviteCode());

User user = userRepository.save(newUser(request.name(), request.email(), request.password(), Role.MEMBER));
inviteCode.increaseUsedCount();
```

`increaseUsedCount()`는 필드 값만 바꾸고 따로 `save`를 부르지 않아요. 트랜잭션이 끝날 때 JPA 변경 감지가 UPDATE 쿼리를 보내고, 커밋과 함께 잠금이 풀려요.

### 잠금이 트랜잭션 밖으로 새지 않게

여기서 한 가지 함정이 있어요. `FOR UPDATE` 잠금은 트랜잭션이 끝날 때 풀려요. 그래서 만약 누가 트랜잭션 없이 이 조회를 부르면, 조회가 끝나자마자 잠금이 풀려서 아무것도 지켜주지 못해요.

이걸 막으려고 조회 메서드에 `Propagation.MANDATORY`를 걸었어요. 이미 진행 중인 트랜잭션이 없으면 예외를 던지는 옵션이에요.

```java
@Transactional(propagation = Propagation.MANDATORY)
public InviteCode getAvailableForUpdate(String code) {
    InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
    if (inviteCode.isFull()) {
        throw new BusinessException(ErrorCode.INVITE_CODE_FULL);
    }
    return inviteCode;
}
```

"이 메서드는 잠금 안에서만 쓰세요"를 주석으로만 남기면 언젠가 잊어버리니까, 규칙을 코드로 강제한 거예요.

### 왜 비관적 락이었나

동시성 제어 방법이 이것만 있는 건 아니에요.

- **낙관적 락(`@Version`)**: 충돌이 나면 한쪽이 실패하고, 재시도 로직을 따로 짜야 해요.
- **조건부 UPDATE**: `UPDATE invite_codes SET used_count = used_count + 1 WHERE code = ? AND used_count < max_uses`처럼 DB가 한 번에 검사하고 올리게 하는 방법이에요. 영향받은 행이 0이면 정원 초과예요.

초대코드는 한 가족이 같이 쓰는 거라 한 코드에 요청이 몰릴 일이 거의 없어요. 잠깐 기다리는 비용이 작으니, 흐름이 코드에 그대로 드러나는 비관적 락이 읽기 편하다고 판단했어요.

## 3. 한도 늘리기: 발급자만, 같은 잠금으로

가족이 늘었을 때 새 코드를 만들지 않고 기존 코드의 한도만 늘리는 API예요.

```http
PATCH /api/invite-code/{id}/increase-max-uses
Authorization: Bearer <accessToken>

{ "additionalSlots": 2 }
```

여기에도 같은 잠금을 걸었어요. 한도를 늘리는 요청과 가입 요청은 같은 행을 고치거든요. 잠금이 없으면 한쪽이 옛날 값을 읽고 저장해서, 다른 쪽의 변경을 덮어쓸 수 있어요.

```java
InviteCode inviteCode = inviteCodeRepository.findByIdForUpdate(inviteCodeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));
if (!inviteCode.isCreatedBy(requesterId)) {
    throw new BusinessException(ErrorCode.FORBIDDEN);
}
inviteCode.increaseMaxUses(additionalSlots);
```

권한은 역할(`OWNER`)이 아니라 "이 코드를 발급한 사람인가"로 검사해요. 요청한 사람의 id는 JWT에서 꺼낸 값이라 클라이언트가 바꿀 수 없어요.

## 에러 정리

| 상황 | HTTP | 에러 코드 |
| --- | --- | --- |
| 없는 코드, 삭제된 코드로 가입 | 404 | `INVALID_INVITE_CODE` |
| 정원이 찬 코드로 가입 | 409 | `INVITE_CODE_FULL` |
| 없는 코드의 한도 변경 | 404 | `INVITE_CODE_NOT_FOUND` |
| 발급자가 아닌 사람이 한도 변경 | 403 | `FORBIDDEN` |
| 코드 중복 등 DB 제약 위반 | 409 | `DATA_CONFLICT` |

## 아직 남은 것

- **1인 가족**: `familySize`를 1로 보내면 한도가 0인 코드가 발급돼요. 막을지, 나중에 한도를 늘려 쓰게 둘지 정해야 해요.
- **만료와 폐기**: 코드에 유효기간이 없고, 유출됐을 때 폐기하거나 새로 발급하는 방법도 아직 없어요.
- **한도 줄이기**: 늘리기만 되고 줄이기는 안 돼요.
- **동시성 테스트**: 잠금이 제대로 동작하는지 여러 스레드로 동시에 가입시키는 테스트를 아직 못 짰어요. 다음에 꼭 확인해 볼 부분이에요.
