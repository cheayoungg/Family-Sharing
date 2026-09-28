package org.miniproject.homeproject.domain.note;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharedNoteRepository extends JpaRepository<SharedNote, Long> {

	@Query("select n from SharedNote n left join fetch n.assignee where n.id = :id and n.deletedAt is null")
	Optional<SharedNote> findActiveById(@Param("id") Long id);

	@Query("select n from SharedNote n left join fetch n.assignee where n.deletedAt is null order by n.createdAt desc, n.id desc")
	List<SharedNote> findAllActive();

	@Query("""
			select n from SharedNote n left join fetch n.assignee
			where n.deletedAt is null and n.category = :category
			order by n.createdAt desc, n.id desc
			""")
	List<SharedNote> findAllActiveByCategory(@Param("category") String category);
}
