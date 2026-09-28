package org.miniproject.homeproject.domain.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-note-api-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class SharedNoteApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private SharedNoteRepository sharedNoteRepository;

	@Autowired
	private UserRepository userRepository;

	private User user;

	@BeforeEach
	void setUp() {
		user = userRepository.save(User.builder()
				.name("엄마").email("mom@example.com").password("encoded").role(Role.OWNER).build());
	}

	@AfterEach
	void tearDown() {
		sharedNoteRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 공유사항을_등록한다() throws Exception {
		perform(post("/api/notes"), """
				{"title": "택배", "content": "문앞에 택배 받아줘~", "category": "부탁"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(notNullValue()))
				.andExpect(jsonPath("$.data.title").value("택배"))
				.andExpect(jsonPath("$.data.content").value("문앞에 택배 받아줘~"))
				.andExpect(jsonPath("$.data.category").value("부탁"))
				.andExpect(jsonPath("$.data.assignee").value(nullValue()))
				.andExpect(jsonPath("$.data.createdAt").value(notNullValue()))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 필수_값이_비어_있으면_400() throws Exception {
		perform(post("/api/notes"), """
				{"title": "택배", "content": " ", "category": "부탁"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		perform(post("/api/notes"), """
				{"title": "택배", "content": "문앞에 택배 받아줘~"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 카테고리로_필터링하고_없으면_전체를_최신순으로_돌려준다() throws Exception {
		saveNote("택배", "부탁");
		saveNote("가스 점검", "공지");
		saveNote("우유 사 와", "부탁");
		SharedNote deleted = saveNote("삭제된 공유사항", "부탁");
		deleted.markDeleted();
		sharedNoteRepository.save(deleted);

		perform(get("/api/notes").param("category", "부탁"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("우유 사 와", "택배")));
		perform(get("/api/notes"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("우유 사 와", "가스 점검", "택배")));
		perform(get("/api/notes").param("category", "없는 카테고리"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(empty()));
	}

	@Test
	void 삭제하면_soft_delete되고_목록에서_빠진다() throws Exception {
		SharedNote note = saveNote("택배", "부탁");

		perform(delete("/api/notes/{id}", note.getId()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));

		assertThat(sharedNoteRepository.findById(note.getId()).orElseThrow().getDeletedAt()).isNotNull();
		perform(get("/api/notes"), null)
				.andExpect(jsonPath("$.data").value(empty()));
		perform(delete("/api/notes/{id}", note.getId()), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTE_NOT_FOUND"));
	}

	@Test
	void 없는_공유사항을_삭제하면_404() throws Exception {
		perform(delete("/api/notes/{id}", 999999), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTE_NOT_FOUND"));
	}

	@Test
	void 토큰_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/notes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
		request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private SharedNote saveNote(String title, String category) {
		return sharedNoteRepository.save(SharedNote.builder()
				.title(title)
				.content(title + " 내용")
				.category(category)
				.build());
	}
}
