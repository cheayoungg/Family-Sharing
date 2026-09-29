package org.miniproject.homeproject.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	// 일정 시각은 시간대 없이 한국 시간으로 저장되므로, "오늘"도 서버 시간대와 관계없이 한국 시간으로 판단한다
	private static final ZoneId FAMILY_ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(FAMILY_ZONE);
	}
}
