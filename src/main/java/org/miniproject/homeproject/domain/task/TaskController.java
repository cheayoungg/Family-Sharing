package org.miniproject.homeproject.domain.task;

import java.util.List;

import org.miniproject.homeproject.domain.task.dto.TaskCreateRequest;
import org.miniproject.homeproject.domain.task.dto.TaskResponse;
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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

	private final TaskService taskService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(@RequestParam(required = false) Long assigneeId) {
		return ResponseEntity.ok(ApiResponse.success(taskService.getTasks(assigneeId)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(taskService.create(request)));
	}

	@PatchMapping("/{id}/complete")
	public ResponseEntity<ApiResponse<TaskResponse>> complete(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(taskService.complete(id)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
		taskService.delete(id, userId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
