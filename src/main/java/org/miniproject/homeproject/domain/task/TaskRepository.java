package org.miniproject.homeproject.domain.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

	@Query("select t from Task t left join fetch t.assignee where t.id = :id and t.deletedAt is null")
	Optional<Task> findActiveById(@Param("id") Long id);

	@Query("select t from Task t left join fetch t.assignee where t.deletedAt is null order by t.id")
	List<Task> findAllActive();

	@Query("select t from Task t join fetch t.assignee a where t.deletedAt is null and a.id = :assigneeId order by t.id")
	List<Task> findAllActiveByAssigneeId(@Param("assigneeId") Long assigneeId);
}
