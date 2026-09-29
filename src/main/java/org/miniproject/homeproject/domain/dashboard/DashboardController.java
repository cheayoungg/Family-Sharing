package org.miniproject.homeproject.domain.dashboard;

import org.miniproject.homeproject.domain.dashboard.dto.DashboardResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping
	public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
		return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboard()));
	}
}
