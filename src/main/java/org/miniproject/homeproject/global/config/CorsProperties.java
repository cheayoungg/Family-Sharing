package org.miniproject.homeproject.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cors.allowed-origins: 요청을 허용할 프론트 origin 목록. 환경변수로는 쉼표로 구분해 넣는다.
 * 예) https://family.example.com,http://localhost:3000
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		// 설정이 없으면 아무 origin도 허용하지 않는다
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}
}
