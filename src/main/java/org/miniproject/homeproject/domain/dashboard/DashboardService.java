package org.miniproject.homeproject.domain.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.miniproject.homeproject.domain.dashboard.dto.DashboardResponse;
import org.miniproject.homeproject.domain.expense.ExpensePaymentStatus;
import org.miniproject.homeproject.domain.expense.ExpenseService;
import org.miniproject.homeproject.domain.schedule.ScheduleService;
import org.miniproject.homeproject.domain.task.TaskService;
import org.miniproject.homeproject.domain.task.TaskStatus;
import org.miniproject.homeproject.domain.task.dto.TaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 각 도메인 서비스의 조회 결과를 모아 대시보드 한 화면으로 만든다. 자체 Repository는 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	private final ScheduleService scheduleService;
	private final TaskService taskService;
	private final ExpenseService expenseService;
	private final Clock clock;

	// 세 조회를 한 트랜잭션(한 커넥션)으로 묶는다
	@Transactional(readOnly = true)
	public DashboardResponse getDashboard() {
		LocalDate today = LocalDate.now(clock);
		return new DashboardResponse(
				today,
				scheduleService.getDailySchedules(today),
				getIncompleteTasks(),
				expenseService.getExpenses(ExpensePaymentStatus.UNPAID));
	}

	// TaskService에는 상태 필터가 없어 전체를 받아 TODO만 남긴다
	private List<TaskResponse> getIncompleteTasks() {
		return taskService.getTasks(null).stream()
				.filter(task -> task.status() == TaskStatus.TODO)
				.toList();
	}
}
