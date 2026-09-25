package org.miniproject.homeproject.domain.schedule.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보낸 필드만 수정한다. null인 필드는 기존 값을 유지한다.
 */
public record ScheduleUpdateRequest(
		@Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "공백일 수 없습니다") String title,
		LocalDateTime startTime,
		LocalDateTime endTime,
		Long assigneeId
) {
}
