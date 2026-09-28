package org.miniproject.homeproject.domain.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@NotNull Long assigneeId,
		@NotNull Boolean isRecurring
) {
}
