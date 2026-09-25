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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

	private final ExpenseService expenseService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
			@RequestParam(required = false) ExpensePaymentStatus status) {
		return ResponseEntity.ok(ApiResponse.success(expenseService.getExpenses(status)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ExpenseResponse>> create(@Valid @RequestBody ExpenseCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(expenseService.create(request)));
	}

	@PatchMapping("/{id}/pay")
	public ResponseEntity<ApiResponse<ExpenseResponse>> pay(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(expenseService.pay(id)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		expenseService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
