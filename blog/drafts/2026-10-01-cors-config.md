# 프론트랑 처음 연결하기 전에: CORS 설정, 그리고 Security가 먼저 받는다는 것

API를 다 만들어 두고 이제 프론트를 붙일 차례예요. 그런데 프론트 개발 서버(`localhost:3000`)와 백엔드(`localhost:8080`)는 포트가 달라서, 브라우저 입장에서는 서로 다른 출처(origin)예요. 따로 허용하지 않으면 브라우저가 요청을 막아 버려요. 그래서 오늘은 CORS 설정을 넣었어요.

## 오늘 무엇을 구현했는가

오늘 커밋은 두 개예요.

- `feat: 프론트 요청을 위한 CORS 설정 추가`
- `docs: 대시보드 API 블로그 초안 추가` (지난번 글)

CORS 설정은 이렇게 정리했어요.

| 항목 | 설정 |
| --- | --- |
| 대상 경로 | `/api/**` |
| 허용 origin | 설정값 `cors.allowed-origins` (쉼표로 여러 개) |
| 허용 메서드 | `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS` |
| 허용 헤더 | `Authorization`, `Content-Type` |
| 쿠키(credentials) | 허용 안 함 |
| preflight 캐시 | 1시간 |

origin은 환경마다 달라서 설정 파일로 뺐어요. 로컬은 기본값이 `http://localhost:3000`이고, dev/prod는 환경변수 `CORS_ALLOWED_ORIGINS`로 꼭 넣어야 떠요. 테스트는 다섯 개 짰어요.

## 왜 그렇게 설계했는가

### WebMvcConfigurer만으로는 안 되는 이유

CORS 설정은 `WebMvcConfigurer`로 등록했어요.

```java
registry.addMapping("/api/**")
        .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
        .allowCredentials(false)
        .maxAge(PREFLIGHT_CACHE_SECONDS);
```

그런데 이 설정은 Spring MVC 단계에서 적용돼요. 이 프로젝트에는 그보다 앞에 Spring Security 필터가 있어요. 브라우저는 `Authorization` 헤더를 붙인 요청을 보내기 전에 "이 요청 보내도 돼?"라고 묻는 preflight(`OPTIONS`) 요청을 먼저 보내는데, 여기에는 토큰이 없어요. 그러면 Security가 이걸 "인증 안 된 요청"으로 보고 401을 돌려줘서, MVC의 CORS 설정까지 가지도 못해요.

그래서 Security에서도 CORS를 켜 줬어요.

```java
return http
        // WebConfig의 CORS 설정을 Security 필터 앞단에서 적용한다.
        // 이게 없으면 토큰이 없는 preflight(OPTIONS) 요청이 인증 단계에서 401로 막힌다
        .cors(Customizer.withDefaults())
```

`Customizer.withDefaults()`로 켜면 Security가 따로 설정을 들고 있지 않고, MVC 쪽에 등록한 CORS 설정을 그대로 가져다 써요. 설정이 한 곳에만 있으니 두 군데를 맞춰 줄 필요가 없어요.

덤으로, CORS 처리가 인증보다 앞에서 일어나니까 401 같은 에러 응답에도 CORS 헤더가 붙어요. 그래서 프론트가 "토큰 만료됐어요" 같은 에러 JSON을 읽을 수 있어요. 이것도 테스트로 확인해 뒀어요.

```java
mockMvc.perform(get("/api/dashboard").header(HttpHeaders.ORIGIN, LOCAL_FRONT))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONT))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
```

### 필요한 것만 열기

메서드와 헤더는 지금 API에서 실제로 쓰는 것만 허용했어요. `PUT`은 쓰는 API가 없어서 뺐어요.

쿠키(credentials)도 허용하지 않았어요. 로그인 토큰을 쿠키가 아니라 `Authorization` 헤더로 주고받으니까 필요가 없거든요. 쓰지 않는 기능은 열어 두지 않는 게 마음 편하더라고요.

### origin은 설정으로, 없으면 아무것도 허용하지 않기

배포될 프론트 도메인은 아직 안 정해졌어요. 그래서 코드에 박지 않고 설정값으로 받게 했어요. 설정은 record 하나로 받는데, 값이 아예 없을 때는 빈 목록이 되게 했어요.

```java
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        // 설정이 없으면 아무 origin도 허용하지 않는다
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
```

설정을 깜빡했을 때 "전부 허용"이 되는 것보다 "아무것도 허용 안 함"이 되는 게 안전하니까요.

### 환경변수 이름은 지난번 교훈 그대로

예전에 `DB_HOST`처럼 흔한 이름을 쓰다가, 다른 프로젝트 때문에 export해 둔 값이 끼어들어서 엉뚱한 DB에 붙은 적이 있었어요. 그래서 로컬은 `HOMEPROJECT_` 접두어를 붙이는 규칙을 만들었는데, 이번에도 그대로 따랐어요.

```yaml
# application-dev.yml / application-prod.yml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS} # 쉼표로 구분, 예) https://family.example.com
```

로컬은 `HOMEPROJECT_CORS_ALLOWED_ORIGINS`를 쓰고, 기본값은 `http://localhost:3000`이에요. dev/prod는 다른 설정들처럼 기본값을 두지 않았어요. 환경변수를 빠뜨리면 앱이 아예 안 떠서, 배포하자마자 바로 알 수 있어요.

## 다음에 할 일

- **배포 환경변수 추가**: dev/prod에 `CORS_ALLOWED_ORIGINS`를 넣기 전까지는 이 브랜치를 배포하면 앱이 안 떠요. 프론트 도메인이 정해지면 바로 넣어야 해요.
- **다른 개발자의 로컬 설정**: `application-local.yml`은 gitignore 대상이라, 다른 사람은 `cors.allowed-origins`를 직접 추가해야 해요. 빠뜨려도 앱은 뜨지만 프론트 요청이 전부 막혀요. 로컬 설정 예시 파일을 하나 커밋해 두면 좋겠어요.
- **리프레시 토큰을 쿠키로 바꾼다면**: 지금은 credentials를 막아 뒀는데, 나중에 리프레시 토큰을 HttpOnly 쿠키로 주고받게 되면 `allowCredentials(true)`로 바꿔야 해요.
- **브랜치 머지**: `feature/login`부터 `feature/cors`까지 브랜치가 일곱 개 이어져 있어요. 이제는 정말 `main`에 올릴 때가 된 것 같아요.
