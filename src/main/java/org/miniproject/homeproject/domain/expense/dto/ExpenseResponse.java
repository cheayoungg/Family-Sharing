package org.miniproject.homeproject.domain.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.miniproject.homeproject.domain.expense.Expense;

public record ExpenseResponse(Long id, String category, BigDecimal amount, LocalDate dueDate, boolean paidStatus,
		String memo) {

	public static ExpenseResponse from(Expense expense) {
		return new ExpenseResponse(expense.getId(), expense.getCategory(), expense.getAmount(), expense.getDueDate(),
				expense.isPaidStatus(), expense.getMemo());
	}
}
