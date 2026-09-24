package org.miniproject.homeproject.global.response;

import org.miniproject.homeproject.global.exception.ErrorCode;

public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode) {
		return error(errorCode, errorCode.getMessage());
	}

	public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
		return error(errorCode.name(), message);
	}

	public static ApiResponse<Void> error(String code, String message) {
		return new ApiResponse<>(false, null, new ErrorResponse(code, message));
	}

	public record ErrorResponse(String code, String message) {
	}
}
