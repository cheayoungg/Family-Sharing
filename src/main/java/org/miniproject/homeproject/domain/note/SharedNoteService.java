package org.miniproject.homeproject.domain.note;

import java.util.List;

import org.miniproject.homeproject.domain.note.dto.SharedNoteCreateRequest;
import org.miniproject.homeproject.domain.note.dto.SharedNoteResponse;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SharedNoteService {

	private final SharedNoteRepository sharedNoteRepository;

	/**
	 * category가 null이면 전체를 돌려준다. 최신 글이 위로.
	 */
	@Transactional(readOnly = true)
	public List<SharedNoteResponse> getNotes(String category) {
		List<SharedNote> notes = category == null
				? sharedNoteRepository.findAllActive()
				: sharedNoteRepository.findAllActiveByCategory(category);
		return notes.stream()
				.map(SharedNoteResponse::from)
				.toList();
	}

	@Transactional
	public SharedNoteResponse create(SharedNoteCreateRequest request) {
		SharedNote note = sharedNoteRepository.save(SharedNote.builder()
				.title(request.title())
				.content(request.content())
				.category(request.category())
				.build());
		return SharedNoteResponse.from(note);
	}

	@Transactional
	public void delete(Long noteId) {
		findNote(noteId).markDeleted();
	}

	private SharedNote findNote(Long noteId) {
		return sharedNoteRepository.findActiveById(noteId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));
	}
}
