# 초대코드로 가족을 모으는 JWT 회원가입·로그인 만들기

가족끼리 일정, 가계부, 집안일 분담, "문앞에 택배 받아줘" 같은 공유사항을 나누는 서비스를 만들고 있어요. 이번에는 그 첫 관문인 회원가입과 로그인을 붙였습니다. 기능을 만든 김에 API 명세서도 같이 정리해 뒀어요.

## 오늘 무엇을 구현했는가

커밋은 두 개예요.

- `feat: JWT 기반 회원가입·로그인 및 초대코드 기능 구현`
- `docs: 인증·초대코드 API 명세서 추가`

기능 단위로 나눠 보면 이렇습니다.

**1. 가족장 가입 + 초대코드 발급** (`POST /api/auth/signup-owner`)

가족장(`OWNER`)이 먼저 가입하면서 가족이 모두 몇 명인지 알려주면, 나머지 가족이 쓸 초대코드가 같이 나와요. 가족장 본인은 이미 가입했으니 코드는 `familySize - 1`번까지만 쓸 수 있어요.

```java
User owner = userRepository.save(newUser(request.name(), request.email(), request.password(), Role.OWNER));
// 가족장 본인은 이미 가입했으므로 나머지 인원만큼 코드를 쓸 수 있다
InviteCode inviteCode = inviteCodeService.issue(owner, request.familySize() - 1);
return SignupOwnerResponse.of(owner, inviteCode);
```

**2. 초대코드로 구성원 가입** (`POST /api/auth/signup`)

구성원(`MEMBER`)은 초대코드를 들고 가입하고, 가입할 때마다 코드의 `usedCount`가 하나씩 올라가요.

**3. 로그인** (`POST /api/auth/login`)

이메일과 비밀번호가 맞으면 24시간짜리 Access Token을 돌려줘요. 세션 없이 Stateless JWT로 갔습니다.

**4. 초대코드 사용 한도 늘리기** (`PATCH /api/invite-code/{id}/increase-max-uses`)

가족이 늘었을 때 코드를 새로 만들지 않고 한도만 늘리는 API예요. 코드를 발급한 사람만 부를 수 있어요.

그 밖에 공통 응답 래퍼(`ApiResponse`), 에러 코드, 전역 예외 처리, 그리고 앞으로 쓸 일정·가계부·역할 분담·공유사항 엔티티를 미리 깔아 뒀어요.

## 왜 그렇게 설계했는가

### 초대코드 정원은 행 잠금으로 지킨다

제일 신경 쓴 부분이에요. 가족 두 명이 같은 코드로 동시에 가입하면, 둘 다 `usedCount`를 같은 값으로 읽고 정원 검사를 함께 통과해 버릴 수 있어요. 그래서 코드를 조회할 때 `SELECT ... FOR UPDATE`로 잠금을 걸었습니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from InviteCode i where i.code = :code and i.deletedAt is null")
Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);
```

잠금은 트랜잭션이 끝나야 풀리니까, 이 조회를 트랜잭션 밖에서 부르면 아무 의미가 없어요. 그래서 서비스 메서드에 `Propagation.MANDATORY`를 걸어서 트랜잭션 없이 호출하면 아예 실패하도록 막았어요. 한도를 늘리는 API도 같은 행을 수정하니까 똑같이 잠금을 걸었고요.

### 로그인 실패 이유는 하나로 뭉친다

이메일이 없어도, 비밀번호가 틀려도, 탈퇴한 계정이어도 전부 같은 `INVALID_CREDENTIALS`로 응답해요. 에러를 구분해 주면 "이 이메일은 가입돼 있구나"가 드러나거든요.

```java
User user = userRepository.findByEmail(request.email())
        .filter(found -> found.getDeletedAt() == null)
        .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
```

### 사람이 옮겨 적기 쉬운 초대코드

초대코드는 가족한테 카톡으로 보내거나 말로 불러줄 수도 있어서, 헷갈리는 `0/O`, `1/I`는 뺐어요. 형식은 `FAMILY-7KQM-X2PA` 같은 식입니다.

```java
private static final String CODE_PREFIX = "FAMILY-";
// 손으로 옮겨 적을 때 헷갈리는 0/O, 1/I는 뺐다
private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
```

### 토큰이 잘못돼도 필터에서 끊지 않는다

JWT 필터는 토큰이 이상하면 이유(만료인지, 위조인지)만 요청에 적어 두고 그냥 넘겨요. 실제 401 응답은 인증이 필요한 경로에서만 `JwtAuthenticationEntryPoint`가 보내고요. 이렇게 하면 로그인처럼 인증이 필요 없는 API는 만료된 토큰이 헤더에 남아 있어도 문제없이 동작해요.

## 막혔던 부분과 해결 과정

diff에 남은 주석과 설정을 보면 중간에 걸린 부분이 몇 군데 있었던 것 같아요. 실제로 어떤 일이 있었는지는 직접 채워 넣어야 해요.

**`/error` 경로를 열어 둔 이유**

```java
// /error를 막으면 404/500 같은 오류 응답까지 401로 바뀐다
.requestMatchers("/api/auth/**", "/error").permitAll()
```

> 여기서 어떤 문제가 있었나요? (예: 없는 URL을 호출했는데 404 대신 401이 떨어졌다든지)

**JWT 필터를 빈으로 등록하지 않은 이유**

```java
// 빈으로 등록하면 서블릿 필터로도 자동 등록되므로 여기서 직접 생성한다
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
        UsernamePasswordAuthenticationFilter.class)
```

> 여기서 어떤 문제가 있었나요? (필터가 두 번 실행되는 걸 발견하신 건가요?)

**`UserDetailsServiceAutoConfiguration` 제외**

```java
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
```

> 여기서 어떤 문제가 있었나요? (기동할 때 찍히는 기본 비밀번호 로그 때문이었나요?)

**로컬 환경 변수에 `HOMEPROJECT_` 접두어를 붙인 이유**

처음에는 로컬 설정도 dev/prod처럼 `DB_HOST`, `JWT_SECRET` 같은 이름으로 읽고, 값이 없으면 로컬 기본값을 쓰게 해 뒀어요. 그런데 앱을 띄웠더니 로컬 DB가 아니라 엉뚱한 DB에 붙더라고요. 다른 프로젝트 때문에 셸에 export해 둔 `DB_HOST`, `JWT_SECRET`이 그대로 남아 있었고, 그 값이 기본값보다 먼저 적용된 거였어요.

기본값은 환경 변수가 없을 때만 쓰이니까, 이름이 겹치는 순간 조용히 다른 값으로 바뀌어요. 에러도 안 나서 알아채기가 더 어려웠고요. 그래서 로컬 프로필은 이 프로젝트 전용 이름을 쓰도록 접두어를 붙였어요.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${HOMEPROJECT_DB_HOST:localhost}:${HOMEPROJECT_DB_PORT:5432}/${HOMEPROJECT_DB_NAME:homeproject}
jwt:
  secret: ${HOMEPROJECT_JWT_SECRET:local-only-secret-change-me-please-32-bytes-min}
```

dev/prod는 배포 환경에서 값을 명시적으로 넣어 주니까 `DB_HOST` 그대로 두고, 여러 프로젝트가 한 셸을 같이 쓰는 로컬에만 접두어를 붙였어요.

## 다음에 할 일

- 일정·가계부·역할 분담·공유사항은 아직 엔티티와 Repository만 있어요. 이제 이 API들을 만들 차례예요.
- Refresh Token이 없어서 24시간이 지나면 다시 로그인해야 해요.
- 가족장은 시스템 전체에 한 명만 가입할 수 있어요(`OWNER_ALREADY_EXISTS`). 여러 가족을 받으려면 "가족" 단위 엔티티가 필요해요.
- `familySize`를 1로 보내면 한도가 0인 초대코드가 발급돼요. 이게 의도한 동작인지 정해야 해요.
- 테스트를 아직 못 돌렸어요. 특히 동시 가입 시나리오는 테스트로 확인해 두고 싶어요.
