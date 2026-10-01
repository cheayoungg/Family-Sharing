package org.miniproject.homeproject.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger UI: /swagger-ui/index.html, 스펙 JSON: /v3/api-docs (prod에서는 꺼 둔다)
 * 로그인 API로 받은 accessToken을 UI의 Authorize 버튼에 넣으면 이후 요청에 Bearer 헤더가 붙는다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("HomeProject API")
						.description("가족 공유 서비스 API. 응답은 모두 {success, data, error} 형식으로 감싸진다.")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				// 인증 API를 뺀 나머지는 모두 토큰이 필요하므로 기본값으로 건다
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}
}
