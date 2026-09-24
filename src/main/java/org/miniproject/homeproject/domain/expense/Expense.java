package org.miniproject.homeproject.domain.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.miniproject.homeproject.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "expenses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends BaseEntity {

	@Column(nullable = false)
	private String category;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false)
	private LocalDate dueDate;

	@Column(nullable = false)
	private boolean paidStatus;

	private String memo;

	@Builder
	private Expense(String category, BigDecimal amount, LocalDate dueDate, boolean paidStatus, String memo) {
		this.category = category;
		this.amount = amount;
		this.dueDate = dueDate;
		this.paidStatus = paidStatus;
		this.memo = memo;
	}
}