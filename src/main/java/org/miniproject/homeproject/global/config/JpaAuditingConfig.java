package org.miniproject.homeproject.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	// createdAt·updatedAt도 서비스와 같은 Clock(한국 시간)으로 기록한다. 기본값은 JVM 시간대의 시스템 시계다
	@Bean
	public DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(LocalDateTime.now(clock));
	}
}
