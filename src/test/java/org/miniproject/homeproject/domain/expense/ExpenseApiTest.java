package org.miniproject.homeproject.domain.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

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
		"jwt.secret=test-only-secret-for-expense-api-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class ExpenseApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

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
		expenseRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 등록하면_미납_상태로_생성된다() throws Exception {
		perform(post("/api/expenses"), """
				{"category": "관리비", "amount": 250000, "dueDate": "2026-10-25", "memo": "10월분"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.category").value("관리비"))
				.andExpect(jsonPath("$.data.amount").value(250000))
				.andExpect(jsonPath("$.data.dueDate").value("2026-10-25"))
				.andExpect(jsonPath("$.data.paidStatus").value(false))
				.andExpect(jsonPath("$.data.memo").value("10월분"))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 메모_없이도_등록된다() throws Exception {
		perform(post("/api/expenses"), """
				{"category": "통신비", "amount": 55000, "dueDate": "2026-10-20"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.memo").value(nullValue()));
	}

	@Test
	void 금액이_0_이하이거나_소수점_셋째_자리가_있으면_400() throws Exception {
		perform(post("/api/expenses"), """
				{"category": "관리비", "amount": 0, "dueDate": "2026-10-25"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		perform(post("/api/expenses"), """
				{"category": "관리비", "amount": 100.123, "dueDate": "2026-10-25"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 필수_값이_빠지면_400() throws Exception {
		perform(post("/api/expenses"), """
				{"amount": 1000, "dueDate": "2026-10-25"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 상태로_필터링하고_납부_기한이_빠른_순으로_돌려준다() throws Exception {
		saveExpense("관리비", "2026-10-25", false);
		saveExpense("통신비", "2026-10-20", false);
		saveExpense("보험료", "2026-10-05", true);
		Expense deleted = saveExpense("삭제된 지출", "2026-10-01", false);
		deleted.markDeleted();
		expenseRepository.save(deleted);

		perform(get("/api/expenses").param("status", "UNPAID"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].category").value(contains("통신비", "관리비")));
		perform(get("/api/expenses").param("status", "PAID"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].category").value(contains("보험료")));
		perform(get("/api/expenses"), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].category").value(contains("보험료", "통신비", "관리비")));
	}

	@Test
	void 없는_상태값으로_조회하면_400() throws Exception {
		perform(get("/api/expenses").param("status", "DONE"), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 납부_처리하면_paidStatus가_true가_된다() throws Exception {
		Expense expense = saveExpense("관리비", "2026-10-25", false);

		perform(patch("/api/expenses/{id}/pay", expense.getId()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.paidStatus").value(true));

		assertThat(expenseRepository.findById(expense.getId()).orElseThrow().isPaidStatus()).isTrue();
	}

	@Test
	void 없는_지출을_납부_처리하면_404() throws Exception {
		perform(patch("/api/expenses/{id}/pay", 999999), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"));
	}

	@Test
	void 삭제하면_soft_delete되고_이후_납부_처리할_수_없다() throws Exception {
		Expense expense = saveExpense("관리비", "2026-10-25", false);

		perform(delete("/api/expenses/{id}", expense.getId()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));

		assertThat(expenseRepository.findById(expense.getId()).orElseThrow().getDeletedAt()).isNotNull();
		perform(patch("/api/expenses/{id}/pay", expense.getId()), null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"));
		perform(delete("/api/expenses/{id}", expense.getId()), null)
				.andExpect(status().isNotFound());
	}

	@Test
	void 토큰_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/expenses"))
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

	private Expense saveExpense(String category, String dueDate, boolean paid) {
		return expenseRepository.save(Expense.builder()
				.category(category)
				.amount(new BigDecimal("10000"))
				.dueDate(LocalDate.parse(dueDate))
				.paidStatus(paid)
				.build());
	}
}
