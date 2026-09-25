package org.miniproject.homeproject.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// common
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
	DATA_CONFLICT(HttpStatus.CONFLICT, "요청이 기존 데이터와 충돌합니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// auth
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

	// user
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	OWNER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가족장이 등록되어 있습니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

	// schedule
	SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
	INVALID_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 빠를 수 없습니다."),
	SCHEDULE_CANCELED(HttpStatus.CONFLICT, "취소된 일정은 완료 처리할 수 없습니다."),

	// invite code
	INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대코드입니다."),
	INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대코드를 찾을 수 없습니다."),
	INVITE_CODE_FULL(HttpStatus.CONFLICT, "이미 정원이 찼습니다.");

	private final HttpStatus status;
	private final String message;
}
