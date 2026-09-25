package org.miniproject.homeproject.domain.expense;

/**
 * 목록 조회 필터용. 엔티티에는 paidStatus(boolean)로 저장한다.
 */
public enum ExpensePaymentStatus {
	UNPAID,
	PAID;

	public boolean isPaid() {
		return this == PAID;
	}
}
