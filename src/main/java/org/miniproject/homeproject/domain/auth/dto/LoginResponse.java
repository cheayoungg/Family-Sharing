package org.miniproject.homeproject.domain.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

	public static LoginResponse bearer(String accessToken, long expiresInSeconds) {
		return new LoginResponse(accessToken, "Bearer", expiresInSeconds);
	}
}
