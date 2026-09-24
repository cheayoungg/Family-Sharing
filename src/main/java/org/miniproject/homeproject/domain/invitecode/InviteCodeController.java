package org.miniproject.homeproject.domain.invitecode;

import org.miniproject.homeproject.domain.invitecode.dto.IncreaseMaxUsesRequest;
import org.miniproject.homeproject.domain.invitecode.dto.InviteCodeResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/invite-code")
@RequiredArgsConstructor
public class InviteCodeController {

	private final InviteCodeService inviteCodeService;

	@PatchMapping("/{id}/increase-max-uses")
	public ResponseEntity<ApiResponse<InviteCodeResponse>> increaseMaxUses(@PathVariable Long id,
			@AuthenticationPrincipal Long userId, @Valid @RequestBody IncreaseMaxUsesRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				inviteCodeService.increaseMaxUses(id, userId, request.additionalSlots())));
	}
}
