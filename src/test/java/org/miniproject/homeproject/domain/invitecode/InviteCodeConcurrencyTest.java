package org.miniproject.homeproject.domain.invitecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.miniproject.homeproject.domain.auth.AuthService;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerRequest;
import org.miniproject.homeproject.domain.auth.dto.SignupOwnerResponse;
import org.miniproject.homeproject.domain.auth.dto.SignupRequest;
import org.miniproject.homeproject.domain.user.Role;
import org.miniproject.homeproject.domain.user.UserRepository;
import org.miniproject.homeproject.global.exception.BusinessException;
import org.miniproject.homeproject.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 초대코드 행 잠금(SELECT ... FOR UPDATE)이 실제 PostgreSQL에서 동시 요청을 직렬화하는지 확인한다.
 * H2는 잠금 동작이 달라 검증이 안 되므로 Testcontainers로 PostgreSQL을 띄운다.
 */
@Testcontainers
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create",
		"jwt.secret=test-only-secret-for-concurrency-test-32-bytes-min",
		"jwt.expiration=3600000"
})
class InviteCodeConcurrencyTest {

	private static final int THREAD_COUNT = 10;

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private AuthService authService;

	@Autowired
	private InviteCodeService inviteCodeService;

	@Autowired
	private InviteCodeRepository inviteCodeRepository;

	@Autowired
	private UserRepository userRepository;

	@AfterEach
	void tearDown() {
		// 가족장은 시스템에 한 명만 가입할 수 있으므로 테스트마다 비운다. 초대코드가 사용자를 참조하므로 먼저 지운다
		inviteCodeRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 정원보다_많은_인원이_동시에_가입해도_정원만큼만_가입된다() throws Exception {
		// 4인 가족 → 초대코드 정원 3
		SignupOwnerResponse owner = signupOwner(4);
		String code = owner.inviteCode().code();

		Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();
		List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger successCount = new AtomicInteger();

		runConcurrently(THREAD_COUNT, i -> {
			try {
				authService.signup(new SignupRequest("member" + i, "member" + i + "@example.com", "password123", code));
				successCount.incrementAndGet();
			} catch (BusinessException e) {
				failures.computeIfAbsent(e.getErrorCode(), key -> new AtomicInteger()).incrementAndGet();
			} catch (Throwable e) {
				unexpected.add(e);
			}
		});

		InviteCode inviteCode = inviteCodeRepository.findById(owner.inviteCode().id()).orElseThrow();
		assertThat(unexpected).isEmpty();
		assertThat(successCount).hasValue(3);
		assertThat(failures.get(ErrorCode.INVITE_CODE_FULL)).hasValue(THREAD_COUNT - 3);
		assertThat(inviteCode.getUsedCount()).isEqualTo(3);
		assertThat(userRepository.findAll()).filteredOn(user -> user.getRole() == Role.MEMBER).hasSize(3);
	}

	@Test
	void 한도_증가_요청이_동시에_들어와도_증가분이_유실되지_않는다() throws Exception {
		SignupOwnerResponse owner = signupOwner(2);
		Long inviteCodeId = owner.inviteCode().id();
		List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

		runConcurrently(THREAD_COUNT, i -> {
			try {
				inviteCodeService.increaseMaxUses(inviteCodeId, owner.id(), 1);
			} catch (Throwable e) {
				unexpected.add(e);
			}
		});

		InviteCode inviteCode = inviteCodeRepository.findById(inviteCodeId).orElseThrow();
		assertThat(unexpected).isEmpty();
		assertThat(inviteCode.getMaxUses()).isEqualTo(1 + THREAD_COUNT);
	}

	@Test
	void 가입과_한도_증가가_섞여_들어와도_서로의_변경을_덮어쓰지_않는다() throws Exception {
		// 정원 1에서 시작해 가입 5건, 한도 +1 요청 5건을 동시에 보낸다
		SignupOwnerResponse owner = signupOwner(2);
		Long inviteCodeId = owner.inviteCode().id();
		String code = owner.inviteCode().code();

		AtomicInteger signupSuccess = new AtomicInteger();
		AtomicInteger signupFull = new AtomicInteger();
		List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

		runConcurrently(THREAD_COUNT, i -> {
			try {
				if (i % 2 == 0) {
					authService.signup(new SignupRequest("member" + i, "member" + i + "@example.com", "password123", code));
					signupSuccess.incrementAndGet();
				} else {
					inviteCodeService.increaseMaxUses(inviteCodeId, owner.id(), 1);
				}
			} catch (BusinessException e) {
				if (e.getErrorCode() == ErrorCode.INVITE_CODE_FULL) {
					signupFull.incrementAndGet();
				} else {
					unexpected.add(e);
				}
			} catch (Throwable e) {
				unexpected.add(e);
			}
		});

		// 어떤 순서로 실행됐든 한도 증가 5건은 모두 반영되고, usedCount는 실제 가입 수와 같으며 한도를 넘지 않아야 한다
		InviteCode inviteCode = inviteCodeRepository.findById(inviteCodeId).orElseThrow();
		assertThat(unexpected).isEmpty();
		assertThat(inviteCode.getMaxUses()).isEqualTo(1 + THREAD_COUNT / 2);
		assertThat(inviteCode.getUsedCount()).isEqualTo(signupSuccess.get());
		assertThat(inviteCode.getUsedCount()).isLessThanOrEqualTo(inviteCode.getMaxUses());
		assertThat(signupSuccess.get() + signupFull.get()).isEqualTo(THREAD_COUNT / 2);
	}

	private SignupOwnerResponse signupOwner(int familySize) {
		return authService.signupOwner(new SignupOwnerRequest("owner", "owner@example.com", "password123", familySize));
	}

	/**
	 * 모든 스레드를 준비시킨 뒤 한꺼번에 출발시켜 요청이 최대한 겹치게 한다.
	 */
	private void runConcurrently(int count, IndexedTask task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(count);
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < count; i++) {
				int index = i;
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					task.run(index);
					return null;
				}));
			}
			ready.await();
			start.countDown();
			for (Future<?> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@FunctionalInterface
	private interface IndexedTask {
		void run(int index);
	}
}
