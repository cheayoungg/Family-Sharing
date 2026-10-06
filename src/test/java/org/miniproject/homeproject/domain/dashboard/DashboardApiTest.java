package org.miniproject.homeproject.domain.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.miniproject.homeproject.domain.expense.Expense;
import org.miniproject.homeproject.domain.expense.ExpenseRepository;
import org.miniproject.homeproject.domain.schedule.Schedule;
import org.miniproject.homeproject.domain.schedule.ScheduleRepository;
import org.miniproject.homeproject.domain.schedule.ScheduleStatus;
import org.miniproject.homeproject.domain.task.Task;
import org.miniproject.homeproject.domain.task.TaskRepository;
import org.miniproject.homeproject.domain.task.TaskStatus;
import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-dashboard-api-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class DashboardApiTest {

	/**
	 * UTC로는 아직 10월 1일 16:30이지만 한국 시간으로는 10월 2일 01:30이다.
	 * 대시보드가 서버 시간대가 아니라 한국 시간으로 "오늘"을 판단하는지 함께 확인한다.
	 */
	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-10-01T16:30:00Z"), ZoneId.of("Asia/Seoul"));
		}
	}

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
	private TaskRepository taskRepository;

	@Autowired
	private ExpenseRepository expenseRepository;

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
		scheduleRepository.deleteAllInBatch();
		taskRepository.deleteAllInBatch();
		expenseRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 오늘_일정_미완료_할_일_미납_지출을_한_번에_돌려준다() throws Exception {
		saveSchedule("어제 일정", "2026-10-01T10:00", "2026-10-01T11:00");
		saveSchedule("어제 자정에 끝남", "2026-10-01T22:00", "2026-10-02T00:00");
		saveSchedule("어젯밤부터 새벽까지", "2026-10-01T23:00", "2026-10-02T01:00");
		saveSchedule("오늘 병원", "2026-10-02T10:00", "2026-10-02T11:00");
		saveSchedule("내일 일정", "2026-10-03T00:00", "2026-10-03T01:00");
		Schedule deletedSchedule = saveSchedule("삭제된 오늘 일정", "2026-10-02T15:00", "2026-10-02T16:00");
		deletedSchedule.markDeleted(LocalDateTime.now());
		scheduleRepository.save(deletedSchedule);

		saveTask("분리수거", TaskStatus.TODO);
		saveTask("설거지", TaskStatus.DONE);
		saveTask("빨래", TaskStatus.TODO);

		saveExpense("관리비", "2026-10-25", false);
		saveExpense("통신비", "2026-09-25", false);
		saveExpense("보험료", "2026-10-05", true);

		perform()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.date").value("2026-10-02"))
				.andExpect(jsonPath("$.data.todaySchedules[*].title").value(contains("어젯밤부터 새벽까지", "오늘 병원")))
				.andExpect(jsonPath("$.data.incompleteTasks[*].title").value(contains("분리수거", "빨래")))
				.andExpect(jsonPath("$.data.unpaidExpenses[*].category").value(contains("통신비", "관리비")))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 아무것도_없으면_빈_목록들을_돌려준다() throws Exception {
		perform()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.date").value("2026-10-02"))
				.andExpect(jsonPath("$.data.todaySchedules").value(empty()))
				.andExpect(jsonPath("$.data.incompleteTasks").value(empty()))
				.andExpect(jsonPath("$.data.unpaidExpenses").value(empty()));
	}

	@Test
	void 토큰_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/dashboard"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions perform() throws Exception {
		return mockMvc.perform(get("/api/dashboard")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(user)));
	}

	private Schedule saveSchedule(String title, String startTime, String endTime) {
		return scheduleRepository.save(Schedule.builder()
				.title(title)
				.startTime(LocalDateTime.parse(startTime))
				.endTime(LocalDateTime.parse(endTime))
				.assignee(user)
				.status(ScheduleStatus.PLANNED)
				.build());
	}

	private void saveTask(String title, TaskStatus status) {
		taskRepository.save(Task.builder().title(title).assignee(user).recurring(false).status(status).build());
	}

	private void saveExpense(String category, String dueDate, boolean paid) {
		expenseRepository.save(Expense.builder()
				.category(category)
				.amount(new BigDecimal("10000"))
				.dueDate(LocalDate.parse(dueDate))
				.paidStatus(paid)
				.build());
	}
}
