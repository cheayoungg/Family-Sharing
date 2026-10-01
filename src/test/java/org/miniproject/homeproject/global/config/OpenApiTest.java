package org.miniproject.homeproject.global.config;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-openapi-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class OpenApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void Swagger_UI는_토큰_없이_열린다() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void API_스펙에_모든_API와_JWT_인증_방식이_들어간다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
				.andExpect(jsonPath("$.paths", hasKey("/api/schedules/{id}")))
				.andExpect(jsonPath("$.paths", hasKey("/api/dashboard")))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/api/dashboard'].get.tags[0]").value("대시보드"));
	}

	@Test
	void 로그인_API는_인증_없이_표시되고_나머지는_토큰이_필요하다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security").value(empty()))
				.andExpect(jsonPath("$.security[0]", hasKey("bearerAuth")));
	}

	@Test
	void 로그인한_사용자_id는_요청_파라미터로_노출되지_않는다() throws Exception {
		// @AuthenticationPrincipal Long userId는 토큰에서 꺼내는 값이라 문서에 입력칸이 생기면 안 된다
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/schedules/{id}'].delete.parameters[*].name").value(contains("id")));
	}
}
