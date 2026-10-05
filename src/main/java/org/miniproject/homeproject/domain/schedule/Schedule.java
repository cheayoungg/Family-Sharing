package org.miniproject.homeproject.domain.schedule;

import java.time.LocalDateTime;

import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.global.entity.BaseEntity;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "schedules", indexes = @Index(name = "idx_schedules_start_time", columnList = "start_time"))
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
		validateTimeRange(startTime, endTime);
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.assignee = assignee;
		this.status = status;
	}

	public boolean isAssignedTo(Long userId) {
		return assignee != null && assignee.getId().equals(userId);
	}

	public void update(String title, LocalDateTime startTime, LocalDateTime endTime, User assignee) {
		validateTimeRange(startTime, endTime);
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.assignee = assignee;
	}

	public void complete() {
		this.status = ScheduleStatus.DONE;
	}

	// 종료 시각은 시작 시각보다 반드시 뒤여야 한다. 월별 조회 쿼리(ScheduleRepository.findAllOverlapping)가 이 규칙에 기댄다
	private static void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
		}
	}
}