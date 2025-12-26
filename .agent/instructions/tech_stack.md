# 기술 스택

## Core Framework

| 기술        | 버전         | 비고                      |
| ----------- | ------------ | ------------------------- |
| Java        | 21           | LTS, Virtual Threads 지원 |
| Spring Boot | 3.5.9        | 최신 안정 버전            |
| Gradle      | Wrapper 사용 | 빌드 도구                 |

---

## Dependencies

### Production

| 라이브러리                            | 버전   | 용도                          |
| ------------------------------------- | ------ | ----------------------------- |
| `spring-boot-starter-web`             | -      | REST API                      |
| `spring-boot-starter-data-jpa`        | -      | 데이터 액세스 (JPA/Hibernate) |
| `spring-boot-starter-validation`      | -      | Bean Validation (JSR-380)     |
| `spring-boot-starter-actuator`        | -      | 헬스체크, 메트릭              |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.14 | Swagger UI / API 문서화       |
| `lombok`                              | -      | 보일러플레이트 코드 감소      |

### Development / Test

| 라이브러리                 | 용도                      |
| -------------------------- | ------------------------- |
| `h2`                       | 인메모리 DB (개발/테스트) |
| `spring-boot-starter-test` | 테스트 프레임워크         |
| `junit-platform-launcher`  | JUnit 5 실행              |

---

## Database

| 환경  | Database       | 비고             |
| ----- | -------------- | ---------------- |
| local | H2 (In-Memory) | 빠른 개발 사이클 |
| dev   | TBD            | 추후 결정        |
| prod  | TBD            | 추후 결정        |

---

## Scripts

```bash
# 개발 서버 실행
./gradlew bootRun

# 테스트 실행
./gradlew test

# 빌드
./gradlew build

# 클린 빌드
./gradlew clean build
```

---

## IDE 설정 권장사항

### IntelliJ IDEA

- **Lombok Plugin** 설치 필수
- **Annotation Processing** 활성화
- **Google Java Format** 또는 기본 Java 포맷터 사용

---

## 버전 관리 원칙

- Spring Boot BOM(Bill of Materials)으로 관리되는 의존성은 버전 생략
- 외부 라이브러리(springdoc 등)는 `build.gradle`에 버전 명시
- 메이저 버전 업그레이드 시 호환성 테스트 필수
