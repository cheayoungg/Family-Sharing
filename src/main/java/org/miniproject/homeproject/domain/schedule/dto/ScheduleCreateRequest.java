package org.miniproject.homeproject.domain.schedule.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleCreateRequest(
		@NotBlank @Size(max = 100) String title,
		@NotNull LocalDateTime startTime,
		@NotNull LocalDateTime endTime,
		@NotNull Long assigneeId
) {
}
