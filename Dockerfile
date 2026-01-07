# Stage 1: Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# 1. Gradle 래퍼와 설정 파일 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# 2. 실행 권한 부여 (윈도우 환경 등 대비)
RUN chmod +x gradlew

# 3. 의존성 다운로드 (캐싱 활용)
# --refresh-dependencies 제거: 캐시 활용을 위해 필수
# retry 로직은 유지하되, 네트워크가 정말 불안정한 환경이 아니라면 제거하는 것이 좋습니다.
RUN ./gradlew dependencies --no-daemon || \
    (echo "Retrying dependency download..." && sleep 5 && ./gradlew dependencies --no-daemon)

# 4. 소스 코드 복사 및 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 5. 타임존 데이터 설치 (Alpine 필수)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Seoul

# 6. 보안 계정 생성
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 7. 빌드 결과물 복사 (와일드카드로 유연하게 처리 후 app.jar로 이름 변경)
COPY --from=builder /build/build/libs/*.jar app.jar

EXPOSE 8080

# Java 실행
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "app.jar"]