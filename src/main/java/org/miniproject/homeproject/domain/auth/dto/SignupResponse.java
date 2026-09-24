package org.miniproject.homeproject.domain.auth.dto;

import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.User;

public record SignupResponse(Long id, String name, String email, Role role) {

	public static SignupResponse from(User user) {
		return new SignupResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
	}
}
