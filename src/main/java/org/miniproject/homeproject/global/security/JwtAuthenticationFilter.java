package org.miniproject.homeproject.global.security;

import java.io.IOException;

import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization 헤더의 Bearer 토큰을 검증해 SecurityContext에 인증 정보를 넣는다.
 * 토큰이 잘못돼도 여기서 요청을 끊지 않고, 인증이 필요한 경로라면 {@link JwtAuthenticationEntryPoint}가 401을 응답한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);
		if (token != null) {
			try {
				SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.getAuthentication(token));
			} catch (ExpiredJwtException e) {
				request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.EXPIRED_TOKEN);
			} catch (JwtException | IllegalArgumentException e) {
				request.setAttribute(JwtAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
			}
		}
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		return null;
	}
}
