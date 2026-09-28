package org.miniproject.homeproject.domain.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	@Query("select s from Schedule s left join fetch s.assignee where s.id = :id and s.deletedAt is null")
	Optional<Schedule> findActiveById(@Param("id") Long id);

	/*
	 * [from, to) 구간과 겹치는 일정. 월을 걸쳐 있는 일정(9/30~10/2)은 양쪽 달 모두에 나온다.
	 * 종료 시각이 항상 시작 시각보다 뒤이므로(Schedule 참고) 두 조건만으로 겹침을 판단할 수 있다
	 */
	@Query("""
			select s from Schedule s left join fetch s.assignee
			where s.deletedAt is null
			  and s.startTime < :to
			  and s.endTime > :from
			order by s.startTime, s.id
			""")
	List<Schedule> findAllOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
