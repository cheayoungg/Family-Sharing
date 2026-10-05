package org.miniproject.homeproject.global.config;

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

/**
 * 배포 헬스체크가 토큰 없이 /actuator/health를 부를 수 있고, 그 밖의 actuator 엔드포인트는 열리지 않는지 확인한다.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-actuator-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class ActuatorTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void health는_토큰_없이_호출되고_상세_정보는_감춘다() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void health_외의_actuator_엔드포인트는_열리지_않는다() throws Exception {
		mockMvc.perform(get("/actuator/env"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isUnauthorized());
	}
}
