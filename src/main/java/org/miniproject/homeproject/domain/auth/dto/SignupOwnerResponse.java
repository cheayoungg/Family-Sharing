package org.miniproject.homeproject.domain.auth.dto;

import org.miniproject.homeproject.domain.invitecode.InviteCode;
import org.miniproject.homeproject.domain.invitecode.dto.InviteCodeResponse;
import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.User;

public record SignupOwnerResponse(Long id, String name, String email, Role role, InviteCodeResponse inviteCode) {

	public static SignupOwnerResponse of(User owner, InviteCode inviteCode) {
		return new SignupOwnerResponse(owner.getId(), owner.getName(), owner.getEmail(), owner.getRole(),
				InviteCodeResponse.from(inviteCode));
	}
}
