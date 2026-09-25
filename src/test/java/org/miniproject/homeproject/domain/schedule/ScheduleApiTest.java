package org.miniproject.homeproject.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
		"jwt.secret=test-only-secret-for-schedule-api-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class ScheduleApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private ScheduleRepository scheduleRepository;

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
		scheduleRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 일정을_등록하면_PLANNED_상태로_생성된다() throws Exception {
		perform(post("/api/schedules"), mom, """
				{"title": "병원 예약", "startTime": "2026-10-02T10:00:00", "endTime": "2026-10-02T11:00:00", "assigneeId": %d}
				""".formatted(mom.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.title").value("병원 예약"))
				.andExpect(jsonPath("$.data.status").value("PLANNED"))
				.andExpect(jsonPath("$.data.assignee.id").value(mom.getId()))
				.andExpect(jsonPath("$.data.assignee.name").value("엄마"))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 종료_시간이_시작_시간보다_빠르면_400() throws Exception {
		perform(post("/api/schedules"), mom, """
				{"title": "병원 예약", "startTime": "2026-10-02T11:00:00", "endTime": "2026-10-02T10:00:00", "assigneeId": %d}
				""".formatted(mom.getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_SCHEDULE_TIME"));
	}

	@Test
	void 없는_사용자를_담당자로_지정하면_404() throws Exception {
		perform(post("/api/schedules"), mom, """
				{"title": "병원 예약", "startTime": "2026-10-02T10:00:00", "endTime": "2026-10-02T11:00:00", "assigneeId": 999999}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
	}

	@Test
	void 월별_조회는_그_달에_걸친_일정만_시작_시간순으로_돌려준다() throws Exception {
		saveSchedule("9월 일정", "2026-09-10T10:00", "2026-09-10T11:00", mom);
		saveSchedule("9월말~10월초 여행", "2026-09-30T09:00", "2026-10-02T18:00", mom);
		saveSchedule("10월 1일 0시 알림", "2026-10-01T00:00", "2026-10-01T00:00", dad);
		saveSchedule("10월 중순", "2026-10-15T10:00", "2026-10-15T11:00", dad);
		saveSchedule("9월 30일 자정에 끝남", "2026-09-30T22:00", "2026-10-01T00:00", dad);
		saveSchedule("11월 일정", "2026-11-01T00:00", "2026-11-01T01:00", mom);
		Schedule deleted = saveSchedule("삭제된 일정", "2026-10-20T10:00", "2026-10-20T11:00", mom);
		deleted.markDeleted();
		scheduleRepository.save(deleted);

		perform(get("/api/schedules").param("year", "2026").param("month", "10"), mom, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].title").value(contains("9월말~10월초 여행", "10월 1일 0시 알림", "10월 중순")));
	}

	@Test
	void 월이_범위를_벗어나거나_빠지면_400() throws Exception {
		perform(get("/api/schedules").param("year", "2026").param("month", "13"), mom, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		perform(get("/api/schedules").param("year", "2026"), mom, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 상세_조회() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(get("/api/schedules/{id}", schedule.getId()), mom, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(schedule.getId()))
				.andExpect(jsonPath("$.data.title").value("장보기"))
				.andExpect(jsonPath("$.data.startTime").value("2026-10-03T15:00:00"));
	}

	@Test
	void 담당자_본인은_보낸_필드만_수정할_수_있다() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(patch("/api/schedules/{id}", schedule.getId()), dad, """
				{"title": "마트 장보기"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("마트 장보기"))
				.andExpect(jsonPath("$.data.startTime").value("2026-10-03T15:00:00"))
				.andExpect(jsonPath("$.data.assignee.id").value(dad.getId()));
	}

	@Test
	void 담당자가_아니면_수정할_수_없다() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(patch("/api/schedules/{id}", schedule.getId()), mom, """
				{"title": "마트 장보기"}
				""")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(scheduleRepository.findById(schedule.getId()).orElseThrow().getTitle()).isEqualTo("장보기");
	}

	@Test
	void 시작_시간만_바꿔도_기존_종료_시간보다_늦으면_400() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(patch("/api/schedules/{id}", schedule.getId()), dad, """
				{"startTime": "2026-10-03T17:00:00"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_SCHEDULE_TIME"));
	}

	@Test
	void 완료_처리하면_DONE이_된다() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(patch("/api/schedules/{id}/complete", schedule.getId()), dad, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DONE"));
	}

	@Test
	void 담당자가_아니면_삭제할_수_없다() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(delete("/api/schedules/{id}", schedule.getId()), mom, null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(scheduleRepository.findById(schedule.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void 담당자가_삭제하면_soft_delete되고_이후_조회되지_않는다() throws Exception {
		Schedule schedule = saveSchedule("장보기", "2026-10-03T15:00", "2026-10-03T16:00", dad);

		perform(delete("/api/schedules/{id}", schedule.getId()), dad, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));

		// 행은 남아 있고 deletedAt만 채워진다
		assertThat(scheduleRepository.findById(schedule.getId()).orElseThrow().getDeletedAt()).isNotNull();
		perform(get("/api/schedules/{id}", schedule.getId()), dad, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
	}

	@Test
	void 토큰_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/schedules").param("year", "2026").param("month", "10"))
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

	private Schedule saveSchedule(String title, String startTime, String endTime, User assignee) {
		return scheduleRepository.save(Schedule.builder()
				.title(title)
				.startTime(LocalDateTime.parse(startTime))
				.endTime(LocalDateTime.parse(endTime))
				.assignee(assignee)
				.status(ScheduleStatus.PLANNED)
				.build());
	}
}
