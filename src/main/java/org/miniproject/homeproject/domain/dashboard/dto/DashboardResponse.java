package org.miniproject.homeproject.domain.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import org.miniproject.homeproject.domain.expense.dto.ExpenseResponse;
import org.miniproject.homeproject.domain.schedule.dto.ScheduleResponse;
import org.miniproject.homeproject.domain.task.dto.TaskResponse;

public record DashboardResponse(
		LocalDate date,
		List<ScheduleResponse> todaySchedules,
		List<TaskResponse> incompleteTasks,
		List<ExpenseResponse> unpaidExpenses
) {
}
