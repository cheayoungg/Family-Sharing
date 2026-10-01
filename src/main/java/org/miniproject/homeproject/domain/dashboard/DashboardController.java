package org.miniproject.homeproject.domain.dashboard;

import org.miniproject.homeproject.domain.dashboard.dto.DashboardResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@Operation(summary = "오늘 일정·미완료 할 일·미납 지출 한 번에 조회")
	@GetMapping
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
		return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard()));
	}
}
