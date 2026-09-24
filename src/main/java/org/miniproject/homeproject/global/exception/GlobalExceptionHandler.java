package org.miniproject.homeproject.global.exception;

import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		return toResponse(e.getErrorCode(), e.getErrorCode().getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
		FieldError fieldError = e.getBindingResult().getFieldError();
		String message = fieldError == null
				? ErrorCode.INVALID_INPUT.getMessage()
				: fieldError.getField() + ": " + fieldError.getDefaultMessage();
		return toResponse(ErrorCode.INVALID_INPUT, message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadableException(HttpMessageNotReadableException e) {
		return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
		return toResponse(ErrorCode.INVALID_INPUT, e.getName() + ": 올바르지 않은 형식입니다.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
		log.warn("Data integrity violation", e);
		return toResponse(ErrorCode.DATA_CONFLICT, ErrorCode.DATA_CONFLICT.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		// 404, 405, 415 같은 Spring MVC 표준 예외는 원래 상태 코드를 유지한다
		if (e instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			HttpStatus httpStatus = HttpStatus.resolve(status.value());
			String code = httpStatus != null ? httpStatus.name() : String.valueOf(status.value());
			return ResponseEntity.status(status)
					.body(ApiResponse.error(code, errorResponse.getBody().getDetail()));
		}
		log.error("Unhandled exception", e);
		return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
	}

	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode, message));
	}
}
