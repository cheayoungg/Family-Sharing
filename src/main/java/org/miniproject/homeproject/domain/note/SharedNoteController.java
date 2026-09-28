package org.miniproject.homeproject.domain.note;

import java.util.List;

import org.miniproject.homeproject.domain.note.dto.SharedNoteCreateRequest;
import org.miniproject.homeproject.domain.note.dto.SharedNoteResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class SharedNoteController {

	private final SharedNoteService sharedNoteService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<SharedNoteResponse>>> getNotes(
			@RequestParam(required = false) String category) {
		return ResponseEntity.ok(ApiResponse.success(sharedNoteService.getNotes(category)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SharedNoteResponse>> create(@Valid @RequestBody SharedNoteCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sharedNoteService.create(request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
		sharedNoteService.delete(id);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
