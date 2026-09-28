package org.miniproject.homeproject.domain.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SharedNoteCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 5000) String content,
		@NotBlank @Size(max = 50) String category
) {
}
