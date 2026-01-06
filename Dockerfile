# Stage 1: Build Stage (JDK 사용)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# 1. Gradle 래퍼와 설정 파일만 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# 2. 의존성 미리 다운로드 (레이어 캐싱 포인트)
# --refresh-dependencies를 사용하여 강제로 새로 다운로드
# Retry logic for transient network issues
RUN ./gradlew dependencies --no-daemon --refresh-dependencies || \
    (echo "Retrying dependency download..." && sleep 5 && ./gradlew dependencies --no-daemon) || \
    (echo "Second retry..." && sleep 10 && ./gradlew dependencies --no-daemon)

# 3. 소스 코드 복사 및 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run Stage (JRE 사용으로 경량화)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 보안을 위한 비관리자 유저 생성
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 타임존 설정 (선택 사항)
ENV TZ=Asia/Seoul

# 빌드 결과물만 복사
COPY --from=builder /build/build/libs/app.jar .

EXPOSE 8080

# JVM 메모리 옵션 등 추가 가능
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "app.jar"]
