package org.miniproject.homeproject.domain.auth;

import org.miniproject.homeproject.domain.auth.dto.LoginRequest;
import org.miniproject.homeproject.domain.auth.dto.LoginResponse;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerRequest;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerResponse;
import org.miniproject.homeproject.domain.auth.dto.SignupRequest;
import org.miniproject.homeproject.domain.auth.dto.SignupResponse;
import org.miniproject.homeproject.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "회원가입·로그인. 토큰 없이 호출합니다.")
// 토큰 없이 호출하는 API라 Swagger UI에서 자물쇠를 표시하지 않는다
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "가족장 회원가입 + 초대코드 발급")
	@PostMapping("/signup-owner")
	public ResponseEntity<ApiResponse<SignupOwnerResponse>> signupOwner(
			@Valid @RequestBody SignupOwnerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signupOwner(request)));
	}

	@Operation(summary = "초대코드로 구성원 회원가입")
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signup(request)));
	}

	@Operation(summary = "로그인 (Access Token 발급)")
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}
}
