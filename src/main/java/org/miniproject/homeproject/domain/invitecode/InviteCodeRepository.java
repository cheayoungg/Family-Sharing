package org.miniproject.homeproject.domain.invitecode;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

	boolean existsByCode(String code);

	/*
	 * 비관적 락(SELECT ... FOR UPDATE)
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InviteCode i where i.code = :code and i.deletedAt is null")
	Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

	// maxUses 증가도 가입 요청과 같은 행을 수정하므로 같은 락을 건다. 락이 없으면 서로의 변경을 덮어쓸 수 있다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InviteCode i where i.id = :id and i.deletedAt is null")
	Optional<InviteCode> findByIdForUpdate(@Param("id") Long id);
}
