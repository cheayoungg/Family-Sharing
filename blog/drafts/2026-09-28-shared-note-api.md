# 마지막 도메인, 가족 칠판(공유사항) API까지 채우기

냉장고 옆 화이트보드에 "문앞에 택배 받아줘~"라고 적어 두는 것처럼, 가족끼리 짧게 남기는 공유사항 기능을 오늘 붙였어요. 이걸로 처음에 잡아 둔 다섯 도메인(일정, 가계부, 할 일, 공유사항, 그리고 인증)에 전부 API가 생겼어요.

## 오늘 무엇을 구현했는가

오늘 커밋은 네 개예요.

| 커밋 | 내용 |
| --- | --- |
| `feat: 공유사항(SharedNote) REST API 구현` | 카테고리 필터 조회, 등록, 삭제 |
| `docs: API 명세서에 할 일 API 추가` | 명세서 갱신 |
| `docs: API 명세서에 공유사항 API 추가` | 명세서 갱신 |
| `docs: 할 일 API 블로그 초안 추가` | 어제 쓴 글 |

공유사항 API는 세 개예요.

| Method | URL | 설명 |
| --- | --- | --- |
| GET | `/api/notes?category=` | 카테고리 필터 조회 (없으면 전체, 최신순) |
| POST | `/api/notes` | 등록 (`title`, `content`, `category`) |
| DELETE | `/api/notes/{id}` | 삭제 (soft delete) |

구조는 지금까지와 같아요. Controller → Service → Repository로 나누고, 응답은 `{success, data, error}`로 감쌌고, Testcontainers로 PostgreSQL을 띄워서 API 테스트 6개를 짰어요. 명세서도 공유사항까지 반영해서, 이제 API가 모두 21개예요.

## 왜 그렇게 설계했는가

### 이번엔 일정 패턴을 일부러 안 따른 부분

일정과 할 일은 "담당자 본인만 삭제"였어요. 공유사항도 엔티티에 `assignee` 필드가 있어서 같은 규칙을 걸 수는 있었는데, 등록 요청에 담당자를 받지 않아요. 그러면 담당자가 늘 비어 있으니까, 그 규칙을 걸면 아무도 지울 수 없는 글이 생겨 버려요.

그래서 삭제는 가계부처럼 로그인한 사람이면 누구나 할 수 있게 뒀어요. 서비스도 그만큼 단순해졌고요.

```java
@Transactional
public void delete(Long noteId) {
    findNote(noteId).markDeleted();
}
```

"작성자만 지우기"를 하려면 작성자를 저장할 필드가 따로 필요해요. 등록하는 사람을 `assignee`에 넣는 방법도 있지만, 그러면 "이 일을 맡은 사람"과 "글을 쓴 사람"의 의미가 섞여서 나중에 헷갈릴 것 같아 그렇게는 안 했어요.

### 칠판은 최신 글이 위로

일정은 시작 시간순, 할 일은 등록순이었는데, 공유사항은 게시판처럼 새 글이 위에 오는 게 자연스러워서 `createdAt` 내림차순으로 정렬했어요. 시각이 같을 때를 대비해서 id를 두 번째 기준으로 넣었고요.

```java
@Query("""
        select n from SharedNote n left join fetch n.assignee
        where n.deletedAt is null and n.category = :category
        order by n.createdAt desc, n.id desc
        """)
List<SharedNote> findAllActiveByCategory(@Param("category") String category);
```

화면에서 "3분 전" 같은 표시를 할 수 있게, 응답에도 `createdAt`을 넣었어요. 다른 도메인 응답에는 없던 필드예요.

### 내용 길이는 요청에서 먼저 제한

`content` 컬럼은 `text` 타입이라 DB에서는 길이 제한이 사실상 없어요. 그래도 칠판에 적는 짧은 메모라는 성격에 맞게, 요청 DTO에서 5,000자로 막아 뒀어요.

```java
public record SharedNoteCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotBlank @Size(max = 50) String category
) {
}
```

## 다음에 할 일

- **작성자(`createdBy`) 필드**: 가계부와 공유사항은 누가 등록했는지 저장하지 않아서, 누구나 삭제할 수 있어요. 두 도메인에 작성자 필드를 넣고 "본인만 삭제"를 걸지 정해야 해요.
- **카테고리 정리**: 카테고리가 자유 문자열이라 "부탁"과 "부탁 "(뒤에 공백)이 다른 카테고리가 돼요. 정해진 목록으로 쓸 거면 enum으로 바꾸고, 아니면 최소한 앞뒤 공백은 정리해야 할 것 같아요.
- **공통 코드 정리**: 응답 안의 `Assignee { id, name }` 레코드가 일정·할 일·공유사항 세 곳에 똑같이 있어요. 도메인이 다 채워진 지금이 공통으로 뺄 타이밍 같아요.
- **브랜치 정리**: 도메인마다 브랜치를 이어서 따다 보니 `feature/login` → `schedule` → `expense` → `task` → `note`로 줄줄이 연결돼 있어요. `main`에 어떤 순서로 머지할지 정해야 해요.
