package org.miniproject.homeproject.domain.invitecode.dto;

import org.miniproject.homeproject.domain.invitecode.InviteCode;

public record InviteCodeResponse(Long id, String code, int maxUses, int usedCount) {

	public static InviteCodeResponse from(InviteCode inviteCode) {
		return new InviteCodeResponse(inviteCode.getId(), inviteCode.getCode(), inviteCode.getMaxUses(),
				inviteCode.getUsedCount());
	}
}
