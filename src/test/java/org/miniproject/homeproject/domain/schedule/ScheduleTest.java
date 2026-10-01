package org.miniproject.homeproject.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;

/**
 * 서비스를 거치지 않고 엔티티를 직접 만들거나 고쳐도 시간 규칙이 지켜지는지 확인한다.
 */
class ScheduleTest {

	private static final LocalDateTime TEN = LocalDateTime.parse("2026-10-02T10:00");
	private static final LocalDateTime ELEVEN = LocalDateTime.parse("2026-10-02T11:00");

	@Test
	void 종료가_시작보다_뒤면_생성된다() {
		Schedule schedule = schedule(TEN, ELEVEN);

		assertThat(schedule.getStartTime()).isEqualTo(TEN);
		assertThat(schedule.getEndTime()).isEqualTo(ELEVEN);
	}

	@Test
	void 종료가_시작과_같거나_앞이면_생성할_수_없다() {
		assertInvalidTime(() -> schedule(TEN, TEN));
		assertInvalidTime(() -> schedule(ELEVEN, TEN));
	}

	@Test
	void 종료가_시작과_같거나_앞이_되도록_수정할_수_없고_기존_값은_그대로다() {
		Schedule schedule = schedule(TEN, ELEVEN);

		assertInvalidTime(() -> schedule.update("장보기", ELEVEN, ELEVEN, null));
		assertInvalidTime(() -> schedule.update("장보기", ELEVEN, TEN, null));

		assertThat(schedule.getStartTime()).isEqualTo(TEN);
		assertThat(schedule.getEndTime()).isEqualTo(ELEVEN);
	}

	private Schedule schedule(LocalDateTime startTime, LocalDateTime endTime) {
		return Schedule.builder()
				.title("병원 예약")
				.startTime(startTime)
				.endTime(endTime)
				.status(ScheduleStatus.PLANNED)
				.build();
	}

	private void assertInvalidTime(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_SCHEDULE_TIME);
	}
}
