package org.miniproject.homeproject.domain.schedule;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.miniproject.homeproject.domain.schedule.dto.ScheduleCreateRequest;
import org.miniproject.homeproject.domain.schedule.dto.ScheduleResponse;
import org.miniproject.homeproject.domain.schedule.dto.ScheduleUpdateRequest;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleService {

	private final ScheduleRepository scheduleRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<ScheduleResponse> getMonthlySchedules(int year, int month) {
		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
		LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
		return scheduleRepository.findAllOverlapping(from, to).stream()
				.map(ScheduleResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleResponse getSchedule(Long scheduleId) {
		return ScheduleResponse.from(findSchedule(scheduleId));
	}

	@Transactional
	public ScheduleResponse create(ScheduleCreateRequest request) {
		validateTimeRange(request.startTime(), request.endTime());
		Schedule schedule = scheduleRepository.save(Schedule.builder()
				.title(request.title())
				.startTime(request.startTime())
				.endTime(request.endTime())
				.assignee(findUser(request.assigneeId()))
				.status(ScheduleStatus.PLANNED)
				.build());
		return ScheduleResponse.from(schedule);
	}

	@Transactional
	public ScheduleResponse update(Long scheduleId, Long requesterId, ScheduleUpdateRequest request) {
		Schedule schedule = findSchedule(scheduleId);
		validateAssignee(schedule, requesterId);

		// 한쪽 시간만 바꿔도 기존 나머지 시간과 비교해야 하므로 합친 값으로 검사한다
		LocalDateTime startTime = request.startTime() != null ? request.startTime() : schedule.getStartTime();
		LocalDateTime endTime = request.endTime() != null ? request.endTime() : schedule.getEndTime();
		validateTimeRange(startTime, endTime);

		schedule.update(
				request.title() != null ? request.title() : schedule.getTitle(),
				startTime,
				endTime,
				request.assigneeId() != null ? findUser(request.assigneeId()) : schedule.getAssignee());
		return ScheduleResponse.from(schedule);
	}

	@Transactional
	public ScheduleResponse complete(Long scheduleId) {
		Schedule schedule = findSchedule(scheduleId);
		if (schedule.getStatus() == ScheduleStatus.CANCELED) {
			throw new BusinessException(ErrorCode.SCHEDULE_CANCELED);
		}
		schedule.complete();
		return ScheduleResponse.from(schedule);
	}

	@Transactional
	public void delete(Long scheduleId, Long requesterId) {
		Schedule schedule = findSchedule(scheduleId);
		validateAssignee(schedule, requesterId);
		schedule.markDeleted();
	}

	/**
	 * 일정 담당자 본인만 할 수 있는 작업(수정, 삭제 등) 앞에서 호출한다.
	 */
	private void validateAssignee(Schedule schedule, Long userId) {
		if (!schedule.isAssignedTo(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (endTime.isBefore(startTime)) {
			throw new BusinessException(ErrorCode.INVALID_SCHEDULE_TIME);
		}
	}

	private Schedule findSchedule(Long scheduleId) {
		return scheduleRepository.findActiveById(scheduleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
	}

	private User findUser(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
