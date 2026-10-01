package org.miniproject.homeproject.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
		"jwt.secret=test-only-secret-for-cors-test-32-bytes-min",
		"jwt.expiration=3600000",
		// 환경변수처럼 쉼표로 구분한 값이 목록으로 바인딩되는지도 함께 확인한다
		"cors.allowed-origins=http://localhost:3000,https://family.example.com"
})
class CorsTest {

	private static final String LOCAL_FRONT = "http://localhost:3000";
	private static final String DEPLOYED_FRONT = "https://family.example.com";

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 개발_서버에서_온_preflight는_토큰_없이도_허용된다() throws Exception {
		preflight("/api/schedules", LOCAL_FRONT, "PATCH", "authorization,content-type")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONT))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
	}

	@Test
	void 배포된_프론트에서_온_preflight도_허용된다() throws Exception {
		preflight("/api/tasks/1", DEPLOYED_FRONT, "DELETE", "authorization")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEPLOYED_FRONT));
	}

	@Test
	void 허용되지_않은_origin의_preflight는_거부된다() throws Exception {
		preflight("/api/schedules", "https://evil.example.com", "GET", "authorization")
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void 허용되지_않은_메서드나_헤더를_요청하는_preflight는_거부된다() throws Exception {
		preflight("/api/schedules", LOCAL_FRONT, "PUT", "authorization")
				.andExpect(status().isForbidden());
		preflight("/api/schedules", LOCAL_FRONT, "GET", "x-custom-header")
				.andExpect(status().isForbidden());
	}

	@Test
	void 인증_실패_응답에도_CORS_헤더가_붙어_프론트가_에러를_읽을_수_있다() throws Exception {
		mockMvc.perform(get("/api/dashboard").header(HttpHeaders.ORIGIN, LOCAL_FRONT))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_FRONT))
				.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	private ResultActions preflight(String path, String origin, String method, String headers) throws Exception {
		return mockMvc.perform(options(path)
				.header(HttpHeaders.ORIGIN, origin)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers));
	}
}
