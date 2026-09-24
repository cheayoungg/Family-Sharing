package org.miniproject.homeproject.domain.note;

import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "shared_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedNote extends BaseEntity {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(nullable = false)
	private String category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Builder
	private SharedNote(String title, String content, String category, User assignee) {
		this.title = title;
		this.content = content;
		this.category = category;
		this.assignee = assignee;
	}
}
