package org.miniproject.homeproject.domain.note;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedNoteRepository extends JpaRepository<SharedNote, Long> {
}
