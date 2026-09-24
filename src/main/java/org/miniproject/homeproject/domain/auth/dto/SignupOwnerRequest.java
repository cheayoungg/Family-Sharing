package org.miniproject.homeproject.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupOwnerRequest(
		@NotBlank @Size(max = 50) String name,
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 64) String password,
		// 본인 포함 총 가족 인원
		@NotNull @Min(1) Integer familySize
) {
}
