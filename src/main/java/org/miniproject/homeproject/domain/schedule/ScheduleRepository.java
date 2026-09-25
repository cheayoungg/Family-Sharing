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
	 * 시작과 끝이 같은 일정이 from 시각에 딱 놓이면 endTime > from 조건에 걸리지 않으므로 startTime >= from으로 보완한다
	 */
	@Query("""
			select s from Schedule s left join fetch s.assignee
			where s.deletedAt is null
			  and s.startTime < :to
			  and (s.endTime > :from or s.startTime >= :from)
			order by s.startTime, s.id
			""")
	List<Schedule> findAllOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
