package org.miniproject.homeproject.domain.task;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.miniproject.homeproject.domain.task.dto.TaskCreateRequest;
import org.miniproject.homeproject.domain.task.dto.TaskResponse;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	/**
	 * assigneeId가 null이면 전체를 돌려준다. 등록 순.
	 */
	@Transactional(readOnly = true)
	public List<TaskResponse> getTasks(Long assigneeId) {
		List<Task> tasks = assigneeId == null
				? taskRepository.findAllActive()
				: taskRepository.findAllActiveByAssigneeId(assigneeId);
		return tasks.stream()
				.map(TaskResponse::from)
				.toList();
	}

	@Transactional
	public TaskResponse create(TaskCreateRequest request) {
		Task task = taskRepository.save(Task.builder()
				.title(request.title())
				.assignee(findUser(request.assigneeId()))
				.recurring(request.isRecurring())
				.status(TaskStatus.TODO)
				.build());
		return TaskResponse.from(task);
	}

	@Transactional
	public TaskResponse complete(Long taskId) {
		Task task = findTask(taskId);
		task.complete();
		return TaskResponse.from(task);
	}

	@Transactional
	public void delete(Long taskId, Long requesterId) {
		Task task = findTask(taskId);
		validateAssignee(task, requesterId);
		task.markDeleted(LocalDateTime.now(clock));
	}

	/**
	 * 담당자 본인만 할 수 있는 작업(삭제 등) 앞에서 호출한다.
	 */
	private void validateAssignee(Task task, Long userId) {
		if (!task.isAssignedTo(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	private Task findTask(Long taskId) {
		return taskRepository.findActiveById(taskId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
	}

	private User findUser(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
