package org.miniproject.homeproject.domain.auth;

import org.miniproject.homeproject.domain.auth.dto.LoginRequest;
import org.miniproject.homeproject.domain.auth.dto.LoginResponse;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerRequest;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerResponse;
import org.miniproject.homeproject.domain.auth.dto.SignupRequest;
import org.miniproject.homeproject.domain.auth.dto.SignupResponse;
import org.miniproject.homeproject.domain.invitecode.InviteCode;
import org.miniproject.homeproject.domain.invitecode.InviteCodeService;
import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.User;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.miniproject.homeproject.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final InviteCodeService inviteCodeService;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public SignupOwnerResponse signupOwner(SignupOwnerRequest request) {
		if (userRepository.existsByRole(Role.OWNER)) {
			throw new BusinessException(ErrorCode.OWNER_ALREADY_EXISTS);
		}
		validateEmailNotTaken(request.email());

		User owner = userRepository.save(newUser(request.name(), request.email(), request.password(), Role.OWNER));
		// 가족장 본인은 이미 가입했으므로 나머지 인원만큼 코드를 쓸 수 있다
		InviteCode inviteCode = inviteCodeService.issue(owner, request.familySize() - 1);
		return SignupOwnerResponse.of(owner, inviteCode);
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		validateEmailNotTaken(request.email());

		// 여기서 잡은 행 잠금은 이 트랜잭션이 커밋될 때까지 유지된다.
		// 그래서 같은 코드로 동시에 들어온 다른 가입 요청은 아래 usedCount 증가가 커밋될 때까지 기다렸다가
		// 증가된 값으로 정원 검사를 한다 (자세한 이유는 InviteCodeRepository.findByCodeForUpdate 참고)
		InviteCode inviteCode = inviteCodeService.getAvailableForUpdate(request.inviteCode());

		User user = userRepository.save(newUser(request.name(), request.email(), request.password(), Role.MEMBER));
		inviteCode.increaseUsedCount();
		return SignupResponse.from(user);
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		// 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않아 가입 여부가 드러나지 않게 한다
		User user = userRepository.findByEmail(request.email())
				.filter(found -> found.getDeletedAt() == null)
				.filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		String accessToken = jwtTokenProvider.createAccessToken(user);
		return LoginResponse.bearer(accessToken, jwtTokenProvider.getExpirationMillis() / 1000);
	}

	private void validateEmailNotTaken(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
	}

	private User newUser(String name, String email, String rawPassword, Role role) {
		return User.builder()
				.name(name)
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.role(role)
				.build();
	}
}
