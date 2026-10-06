package org.miniproject.homeproject.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
		"jwt.secret=test-only-secret-for-task-api-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class TaskApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	private User mom;
	private User dad;

	@BeforeEach
	void setUp() {
		mom = saveUser("엄마", "mom@example.com", Role.OWNER);
		dad = saveUser("아빠", "dad@example.com", Role.MEMBER);
	}

	@AfterEach
	void tearDown() {
		taskRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 등록하면_TODO_상태로_생성된다() throws Exception {
		perform(post("/api/tasks"), mom, """
				{"title": "분리수거", "assigneeId": %d, "isRecurring": true}
				""".formatted(dad.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.title").value("분리수거"))
				.andExpect(jsonPath("$.data.assignee.id").value(dad.getId()))
				.andExpect(jsonPath("$.data.assignee.name").value("아빠"))
				.andExpect(jsonPath("$.data.isRecurring").value(true))
				.andExpect(jsonPath("$.data.status").value("TODO"))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 필수_값이_빠지면_400() throws Exception {
		perform(post("/api/tasks"), mom, """
				{"title": "분리수거", "assigneeId": %d}
				""".formatted(dad.getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		perform(post("/api/tasks"), mom, """
				{"title": " ", "assigneeId": %d, "isRecurring": false}
				""".formatted(dad.getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 없는_사용자를_담당자로_지정하면_404() throws Exception {
		perform(post("/api/tasks"), mom, """
				{"title": "분리수거", "assigneeId": 999999, "isRecurring": false}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 담당자로_필터링하고_없으면_전체를_등록_순으로_돌려준다() throws Exception {
		saveTask("설거지", mom);
		saveTask("분리수거", dad);
		saveTask("빨래", mom);
		Task deleted = saveTask("삭제된 할 일", mom);
		deleted.markDeleted(LocalDateTime.now());
		taskRepository.save(deleted);

		perform(get("/api/tasks").param("assigneeId", String.valueOf(mom.getId())), mom, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("설거지", "빨래")));
		perform(get("/api/tasks"), mom, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("설거지", "분리수거", "빨래")));
		perform(get("/api/tasks").param("assigneeId", "999999"), mom, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(empty()));
	}

	@Test
	void 담당자_id가_숫자가_아니면_400() throws Exception {
		perform(get("/api/tasks").param("assigneeId", "abc"), mom, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 완료_체크하면_DONE이_된다() throws Exception {
		Task task = saveTask("분리수거", dad);

		perform(patch("/api/tasks/{id}/complete", task.getId()), dad, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DONE"));
	}

	@Test
	void 없는_할_일을_완료_체크하면_404() throws Exception {
		perform(patch("/api/tasks/{id}/complete", 999999), dad, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
	}

	@Test
	void 담당자가_아니면_삭제할_수_없다() throws Exception {
		Task task = saveTask("분리수거", dad);

		perform(delete("/api/tasks/{id}", task.getId()), mom, null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(taskRepository.findById(task.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void 담당자가_삭제하면_soft_delete되고_이후_완료_체크할_수_없다() throws Exception {
		Task task = saveTask("분리수거", dad);

		perform(delete("/api/tasks/{id}", task.getId()), dad, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));

		assertThat(taskRepository.findById(task.getId()).orElseThrow().getDeletedAt()).isNotNull();
		perform(patch("/api/tasks/{id}/complete", task.getId()), dad, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
	}

	@Test
	void 토큰_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/tasks"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, User user, String body) throws Exception {
		request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private User saveUser(String name, String email, Role role) {
		return userRepository.save(User.builder().name(name).email(email).password("encoded").role(role).build());
	}

	private Task saveTask(String title, User assignee) {
		return taskRepository.save(Task.builder()
				.title(title)
				.assignee(assignee)
				.recurring(false)
				.status(TaskStatus.TODO)
				.build());
	}
}
