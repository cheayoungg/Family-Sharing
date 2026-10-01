package org.miniproject.homeproject.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * application-prod.yml과 같은 springdoc 설정으로 Swagger가 실제로 닫히는지 확인한다.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-openapi-test-32-bytes-min",
		"jwt.expiration=3600000",
		"springdoc.api-docs.enabled=false",
		"springdoc.swagger-ui.enabled=false"
})
class OpenApiDisabledTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 운영_설정에서는_Swagger_UI와_스펙이_열리지_않는다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isNotFound());
	}
}
