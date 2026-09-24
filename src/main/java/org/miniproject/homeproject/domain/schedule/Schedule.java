package org.miniproject.homeproject.domain.schedule;

import java.time.LocalDateTime;

import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseEntity {

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private LocalDateTime startTime;

	@Column(nullable = false)
	private LocalDateTime endTime;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ScheduleStatus status;

	@Builder
	private Schedule(String title, LocalDateTime startTime, LocalDateTime endTime, User assignee,
			ScheduleStatus status) {
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.assignee = assignee;
		this.status = status;
	}
}