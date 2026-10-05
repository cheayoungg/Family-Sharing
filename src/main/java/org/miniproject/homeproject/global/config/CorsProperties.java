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
		allowedOrigins.forEach(CorsProperties::requireResolved);
	}

	/*
	 * @ConfigurationProperties는 찾지 못한 환경변수를 에러로 막지 않고 "${CORS_ALLOWED_ORIGINS}" 글자 그대로 넣는다.
	 * 그대로 두면 앱은 뜨지만 모든 프론트 요청이 조용히 403으로 막히므로, 시작 단계에서 실패시킨다
	 */
	private static void requireResolved(String origin) {
		if (origin.contains("${")) {
			throw new IllegalStateException(
					"cors.allowed-origins에 쓰인 환경변수가 설정되지 않았습니다: " + origin);
		}
	}
}
