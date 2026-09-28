package org.miniproject.homeproject.domain.note.dto;

import java.time.LocalDateTime;

import org.miniproject.homeproject.domain.note.SharedNote;
import org.miniproject.homeproject.domain.user.User;

public record SharedNoteResponse(Long id, String title, String content, String category, Assignee assignee,
		LocalDateTime createdAt) {

	public static SharedNoteResponse from(SharedNote note) {
		return new SharedNoteResponse(note.getId(), note.getTitle(), note.getContent(), note.getCategory(),
				Assignee.from(note.getAssignee()), note.getCreatedAt());
	}

	public record Assignee(Long id, String name) {

		static Assignee from(User user) {
			return user == null ? null : new Assignee(user.getId(), user.getName());
		}
	}
}
