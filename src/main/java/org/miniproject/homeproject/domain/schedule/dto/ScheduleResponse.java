package org.miniproject.homeproject.domain.schedule.dto;

import java.time.LocalDateTime;

import org.miniproject.homeproject.domain.schedule.Schedule;
import org.miniproject.homeproject.domain.schedule.ScheduleStatus;
import org.miniproject.homeproject.domain.user.User;

public record ScheduleResponse(Long id, String title, LocalDateTime startTime, LocalDateTime endTime,
		Assignee assignee, ScheduleStatus status) {

	public static ScheduleResponse from(Schedule schedule) {
		return new ScheduleResponse(schedule.getId(), schedule.getTitle(), schedule.getStartTime(),
				schedule.getEndTime(), Assignee.from(schedule.getAssignee()), schedule.getStatus());
	}

	public record Assignee(Long id, String name) {

		static Assignee from(User user) {
			return user == null ? null : new Assignee(user.getId(), user.getName());
		}
	}
}
