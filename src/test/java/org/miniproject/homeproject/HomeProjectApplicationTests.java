package org.miniproject.homeproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * gitignore된 application-local.yml·로컬 DB 없이도(새로 받은 저장소, CI) 컨텍스트가 뜨는지 확인한다.
 */
@Testcontainers
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-context-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class HomeProjectApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Test
	void contextLoads() {
	}

}
