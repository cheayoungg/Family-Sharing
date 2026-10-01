package org.miniproject.homeproject.domain.expense;

import java.util.List;

import org.miniproject.homeproject.domain.expense.dto.ExpenseCreateRequest;
import org.miniproject.homeproject.domain.expense.dto.ExpenseResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "가계부")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;

	@Operation(summary = "지출 목록 (납부 상태 필터, 없으면 전체)")
	@GetMapping
	public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
			@RequestParam(required = false) ExpensePaymentStatus status) {
		return ResponseEntity.ok(ApiResponse.success(expenseService.getExpenses(status)));
	}

	@Operation(summary = "지출 등록")
	@PostMapping
	public ResponseEntity<ApiResponse<ExpenseResponse>> create(@Valid @RequestBody ExpenseCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(expenseService.create(request)));
	}

	@Operation(summary = "납부 처리")
	@PatchMapping("/{id}/pay")
	public ResponseEntity<ApiResponse<ExpenseResponse>> pay(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(expenseService.pay(id)));
	}

	@Operation(summary = "지출 삭제")
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		expenseService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
