package org.miniproject.homeproject.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * application-prod.yml과 같은 설정(Flyway 켜짐, ddl-auto=validate)으로 빈 DB에서 시작해,
 * 마이그레이션으로 만든 스키마가 엔티티와 맞는지 확인한다.
 * 엔티티를 바꾸고 마이그레이션을 추가하지 않으면 이 테스트가 컨텍스트 로딩 단계에서 실패한다.
 */
@Testcontainers
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"jwt.secret=test-only-secret-for-flyway-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class FlywayMigrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private Flyway flyway;

	@Test
	void 빈_DB에_마이그레이션을_적용하면_엔티티와_스키마가_일치한다() {
		assertThat(flyway.info().applied()).isNotEmpty();
		assertThat(flyway.info().pending()).isEmpty();
	}
}
