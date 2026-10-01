package org.miniproject.homeproject.domain.schedule;

import java.util.List;

import org.miniproject.homeproject.domain.schedule.dto.ScheduleCreateRequest;
import org.miniproject.homeproject.domain.schedule.dto.ScheduleResponse;
import org.miniproject.homeproject.domain.schedule.dto.ScheduleUpdateRequest;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@Tag(name = "일정")
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

	private final ScheduleService scheduleService;

	@Operation(summary = "월별 일정 목록")
	@GetMapping
	public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getMonthlySchedules(
			@RequestParam @Min(1) @Max(9999) int year, @RequestParam @Min(1) @Max(12) int month) {
		return ResponseEntity.ok(ApiResponse.success(scheduleService.getMonthlySchedules(year, month)));
	}

	@Operation(summary = "일정 상세")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ScheduleResponse>> getSchedule(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedule(id)));
	}

	@Operation(summary = "일정 등록")
	@PostMapping
	public ResponseEntity<ApiResponse<ScheduleResponse>> create(@Valid @RequestBody ScheduleCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(scheduleService.create(request)));
	}

	@Operation(summary = "일정 수정 (담당자 본인만, 보낸 필드만 수정)")
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<ScheduleResponse>> update(@PathVariable Long id,
			@AuthenticationPrincipal Long userId, @Valid @RequestBody ScheduleUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(scheduleService.update(id, userId, request)));
	}

	@Operation(summary = "일정 완료 처리")
	@PatchMapping("/{id}/complete")
	public ResponseEntity<ApiResponse<ScheduleResponse>> complete(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(scheduleService.complete(id)));
	}

	@Operation(summary = "일정 삭제 (담당자 본인만)")
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
		scheduleService.delete(id, userId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
