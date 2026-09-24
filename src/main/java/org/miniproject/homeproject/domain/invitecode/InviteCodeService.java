package org.miniproject.homeproject.domain.invitecode;

import java.security.SecureRandom;

import org.miniproject.homeproject.domain.invitecode.dto.InviteCodeResponse;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InviteCodeService {

	private static final String CODE_PREFIX = "FAMILY-";
	// 손으로 옮겨 적을 때 헷갈리는 0/O, 1/I는 뺐다
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_GROUP_LENGTH = 4;
	private static final int MAX_GENERATE_ATTEMPTS = 5;

	private final SecureRandom random = new SecureRandom();
	private final InviteCodeRepository inviteCodeRepository;

	@Transactional
	public InviteCode issue(User owner, int maxUses) {
		return inviteCodeRepository.save(InviteCode.builder()
				.code(generateUniqueCode())
				.maxUses(maxUses)
				.createdBy(owner)
				.build());
	}

	/**
	 * 가입에 쓸 초대코드를 행 잠금과 함께 조회하고 정원을 검사한다.
	 * 잠금은 트랜잭션이 끝날 때 풀리므로, 호출하는 쪽 트랜잭션 안에서만 부를 수 있게 MANDATORY로 강제한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public InviteCode getAvailableForUpdate(String code) {
		InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
		if (inviteCode.isFull()) {
			throw new BusinessException(ErrorCode.INVITE_CODE_FULL);
		}
		return inviteCode;
	}

	@Transactional
	public InviteCodeResponse increaseMaxUses(Long inviteCodeId, Long requesterId, int additionalSlots) {
		InviteCode inviteCode = inviteCodeRepository.findByIdForUpdate(inviteCodeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));
		if (!inviteCode.isCreatedBy(requesterId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		inviteCode.increaseMaxUses(additionalSlots);
		return InviteCodeResponse.from(inviteCode);
	}

	private String generateUniqueCode() {
		for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
			String code = CODE_PREFIX + randomGroup() + "-" + randomGroup();
			if (!inviteCodeRepository.existsByCode(code)) {
				return code;
			}
		}
		throw new IllegalStateException("초대코드 생성에 실패했습니다.");
	}

	private String randomGroup() {
		StringBuilder group = new StringBuilder(CODE_GROUP_LENGTH);
		for (int i = 0; i < CODE_GROUP_LENGTH; i++) {
			group.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
		}
		return group.toString();
	}
}
