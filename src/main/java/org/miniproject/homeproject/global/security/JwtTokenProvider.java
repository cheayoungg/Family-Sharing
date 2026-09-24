package org.miniproject.homeproject.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.miniproject.homeproject.domain.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";

	private final SecretKey key;
	@Getter
	private final long expirationMillis;

	public JwtTokenProvider(@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration}") long expirationMillis) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMillis = expirationMillis;
	}

	public String createAccessToken(User user) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.claim(ROLE_CLAIM, user.getRole().name())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMillis))
				.signWith(key)
				.compact();
	}

	/**
	 * 토큰을 검증하고 인증 객체를 만든다. principal은 사용자 id(Long)다.
	 *
	 * @throws io.jsonwebtoken.JwtException 서명이 틀리거나 만료된 경우
	 */
	public Authentication getAuthentication(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		Long userId = Long.valueOf(claims.getSubject());
		String role = claims.get(ROLE_CLAIM, String.class);
		return new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role)));
	}
}
