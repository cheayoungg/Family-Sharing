package org.miniproject.homeproject.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

	@Test
	void 설정이_없으면_아무_origin도_허용하지_않는다() {
		assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
	}

	@Test
	void 환경변수가_비어_글자_그대로_남은_값이면_시작을_막는다() {
		assertThatThrownBy(() -> new CorsProperties(List.of("${CORS_ALLOWED_ORIGINS}")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("${CORS_ALLOWED_ORIGINS}");
	}
}
