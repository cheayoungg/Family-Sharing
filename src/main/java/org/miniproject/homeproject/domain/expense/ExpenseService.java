package org.miniproject.homeproject.domain.expense;

import java.util.List;

import org.miniproject.homeproject.domain.expense.dto.ExpenseCreateRequest;
import org.miniproject.homeproject.domain.expense.dto.ExpenseResponse;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

	private final ExpenseRepository expenseRepository;

	/**
	 * status가 null이면 전체를 돌려준다. 납부 기한이 빠른 순.
	 */
	@Transactional(readOnly = true)
	public List<ExpenseResponse> getExpenses(ExpensePaymentStatus status) {
		List<Expense> expenses = status == null
				? expenseRepository.findAllByDeletedAtIsNullOrderByDueDateAscIdAsc()
				: expenseRepository.findAllByPaidStatusAndDeletedAtIsNullOrderByDueDateAscIdAsc(status.isPaid());
		return expenses.stream()
				.map(ExpenseResponse::from)
				.toList();
	}

	@Transactional
	public ExpenseResponse create(ExpenseCreateRequest request) {
		Expense expense = expenseRepository.save(Expense.builder()
				.category(request.category())
				.amount(request.amount())
				.dueDate(request.dueDate())
				.paidStatus(false)
				.memo(request.memo())
				.build());
		return ExpenseResponse.from(expense);
	}

	@Transactional
	public ExpenseResponse pay(Long expenseId) {
		Expense expense = findExpense(expenseId);
		expense.pay();
		return ExpenseResponse.from(expense);
	}

	@Transactional
	public void delete(Long expenseId) {
		findExpense(expenseId).markDeleted();
	}

	private Expense findExpense(Long expenseId) {
		return expenseRepository.findByIdAndDeletedAtIsNull(expenseId)
				.orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
	}
}
