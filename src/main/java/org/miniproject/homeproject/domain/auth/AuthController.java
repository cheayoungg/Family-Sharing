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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup-owner")
	public ResponseEntity<ApiResponse<SignupOwnerResponse>> signupOwner(
			@Valid @RequestBody SignupOwnerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signupOwner(request)));
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signup(request)));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}
}
