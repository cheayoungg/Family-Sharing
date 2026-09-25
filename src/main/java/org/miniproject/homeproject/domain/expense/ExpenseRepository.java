package org.miniproject.homeproject.domain.expense;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

	Optional<Expense> findByIdAndDeletedAtIsNull(Long id);

	List<Expense> findAllByDeletedAtIsNullOrderByDueDateAscIdAsc();

	List<Expense> findAllByPaidStatusAndDeletedAtIsNullOrderByDueDateAscIdAsc(boolean paidStatus);
}
