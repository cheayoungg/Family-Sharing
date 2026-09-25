package org.miniproject.homeproject.domain.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExpenseCreateRequest(
		@NotBlank @Size(max = 50) String category,
		// DB 컬럼이 numeric(38,2)라 소수점 셋째 자리부터는 반올림돼 저장되므로 요청 단계에서 막는다
		@NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
		@NotNull LocalDate dueDate,
		@Size(max = 255) String memo
) {
}
