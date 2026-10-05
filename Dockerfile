# syntax=docker/dockerfile:1

# ===== build stage: Gradle로 실행 가능한 jar를 만든다 =====
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 빌드 설정만 먼저 복사해 의존성을 받아 둔다.
# build.gradle이 바뀌지 않으면 소스만 고쳐도 이 레이어는 캐시를 그대로 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 Testcontainers(Docker)가 필요해 이미지 빌드 안에서는 돌릴 수 없다. 배포 전에 ./gradlew test로 따로 돌린다
RUN ./gradlew bootJar -x test --no-daemon \
	&& java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination build/extracted

# ===== run stage: JRE와 실행에 필요한 파일만 담는다 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# 자주 바뀌지 않는 것(라이브러리)부터 복사해, 코드만 바뀐 배포에서는 마지막 레이어만 새로 받게 한다
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

USER app

# 프로필은 기본 prod. dev 서버는 실행할 때 SPRING_PROFILES_ACTIVE=dev로 덮어쓴다
# heap 상한은 컨테이너 메모리 제한(docker run --memory)의 비율이다. 제한이 없으면 호스트 전체 메모리 기준이 된다.
# heap 밖(metaspace·code cache·네이티브)이 약 300MB로 고정이라, 1GB 서버(--memory=768m)는 50%라야 여유가 남는다.
# 2GB 서버(--memory=1536m)는 -e JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75로 올려도 된다
ENV SPRING_PROFILES_ACTIVE=prod \
	JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"

EXPOSE 8080

# DB 연결까지 포함한 /actuator/health로 상태를 본다. start-period는 JVM 기동과 Flyway 마이그레이션 시간
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
	CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# 서버 크기와 관계없이 고정할 JVM 옵션. JAVA_TOOL_OPTIONS로 heap 비율만 바꿔도 이 옵션들은 유지된다
# UseSerialGC: 작은 heap에서는 G1보다 부가 메모리가 적다. 지정하지 않으면 CPU 2개·2GB 이상에서만 G1으로 바뀌어 서버마다 동작이 달라진다
# ExitOnOutOfMemoryError: OOM 뒤 반쯤 죽은 상태로 남지 않고 종료해 --restart 정책으로 다시 뜨게 한다
# user.timezone: createdAt·deletedAt 같은 시각도 일정 시각과 같은 한국 시간으로 기록되게 한다
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=Asia/Seoul", \
	"org.springframework.boot.loader.launch.JarLauncher"]
