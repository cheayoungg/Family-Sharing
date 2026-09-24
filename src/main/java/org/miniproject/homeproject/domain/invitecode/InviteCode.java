package org.miniproject.homeproject.domain.invitecode;

import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "invite_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private int maxUses;

	@Column(nullable = false)
	private int usedCount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_id", nullable = false)
	private User createdBy;

	@Builder
	private InviteCode(String code, int maxUses, User createdBy) {
		this.code = code;
		this.maxUses = maxUses;
		this.usedCount = 0;
		this.createdBy = createdBy;
	}

	public boolean isFull() {
		return usedCount >= maxUses;
	}

	public boolean isCreatedBy(Long userId) {
		return createdBy.getId().equals(userId);
	}

	/**
	 * 반드시 {@link InviteCodeRepository#findByCodeForUpdate}로 잠금을 잡은 뒤 호출해야 한다.
	 * 잠금 없이 부르면 동시에 들어온 가입 요청들이 같은 usedCount를 읽고 정원 검사를 함께 통과해 maxUses를 넘길 수 있다.
	 */
	public void increaseUsedCount() {
		this.usedCount++;
	}

	public void increaseMaxUses(int additionalSlots) {
		this.maxUses += additionalSlots;
	}
}
