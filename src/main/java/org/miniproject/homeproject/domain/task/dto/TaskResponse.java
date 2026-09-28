package org.miniproject.homeproject.domain.task.dto;

import org.miniproject.homeproject.domain.task.Task;
import org.miniproject.homeproject.domain.task.TaskStatus;
import org.miniproject.homeproject.domain.user.User;

public record TaskResponse(Long id, String title, Assignee assignee, boolean isRecurring, TaskStatus status) {

	public static TaskResponse from(Task task) {
		return new TaskResponse(task.getId(), task.getTitle(), Assignee.from(task.getAssignee()), task.isRecurring(),
				task.getStatus());
	}

	public record Assignee(Long id, String name) {

		static Assignee from(User user) {
			return user == null ? null : new Assignee(user.getId(), user.getName());
		}
	}
}
