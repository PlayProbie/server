# 프로젝트 구조

> 참고: [cheese10yun/spring-guide - Directory Guide](https://github.com/cheese10yun/spring-guide/blob/master/docs/directory-guide.md)

## 전체 구조

도메인형 디렉토리 구조를 채택합니다. **domain**, **global**, **infra** 세 가지 최상위 패키지로 구성됩니다.

```
src/main/java/com/playprobie/api/
├── PlayProbieApplication.java       # 애플리케이션 진입점
│
├── domain/                          # 📦 도메인 패키지
│   ├── user/                        # 유저 도메인
│   │   ├── api/                     # Controller (REST API)
│   │   ├── application/             # Service (비즈니스 로직)
│   │   ├── dao/                     # Repository (데이터 액세스)
│   │   ├── domain/                  # Entity
│   │   ├── dto/                     # Request/Response DTO
│   │   └── exception/               # 도메인 전용 예외
│   ├── session/                     # 세션 도메인
│   └── model/                       # 공통 Value Object (Embeddable, Enum)
│
├── global/                          # 🌐 전역 설정
│   ├── common/                      # 공통 객체
│   │   ├── request/                 # 공통 Request (페이징 등)
│   │   └── response/                # 공통 Response
│   ├── config/                      # Spring 설정
│   ├── error/                       # 예외 처리
│   │   ├── ErrorCode.java           # 에러 코드 enum
│   │   ├── ErrorResponse.java       # 통일된 에러 응답 객체
│   │   ├── GlobalExceptionHandler.java
│   │   └── exception/               # 공통 예외 클래스
│   │       ├── BusinessException.java
│   │       ├── EntityNotFoundException.java
│   │       └── InvalidValueException.java
│   └── util/                        # 유틸리티 클래스
│
└── infra/                           # 🔌 외부 인프라
    ├── email/                       # 이메일 서비스
    └── sms/                         # SMS 서비스
```

---

## 패키지 역할 상세

### `domain/` - 도메인 모듈

각 도메인은 독립적인 모듈로 관리됩니다.

| 패키지        | 역할                           | 네이밍                                            |
| ------------- | ------------------------------ | ------------------------------------------------- |
| `api`         | REST API Controller            | `[Domain]Api.java` 또는 `[Domain]Controller.java` |
| `application` | 비즈니스 로직, 트랜잭션 처리   | `[Domain]Service.java`                            |
| `dao`         | 데이터 액세스 (JPA Repository) | `[Domain]Repository.java`                         |
| `domain`      | JPA Entity, Embeddable         | `[Domain].java`                                   |
| `dto`         | Request/Response 객체          | `[Action][Domain]Request.java`                    |
| `exception`   | 도메인 전용 예외               | `[Domain]NotFoundException.java`                  |

> **왜 `application`인가?** `service`로 하면 `XXXService` 클래스명을 강제하는 느낌이 있어, 더 유연한 `application`을 사용합니다.

> **왜 `dao`인가?** 조회 전용 구현체가 많아지면 `Repository`보다 `DAO`가 더 직관적입니다.

### `domain/model/` - 공통 Value Object

여러 도메인에서 공통으로 사용하는 `@Embeddable`, `Enum` 클래스를 위치시킵니다.

```java
// 예시: 여러 Entity에서 사용하는 Embeddable
@Embeddable
public class Address { ... }

@Embeddable
public class Email { ... }
```

---

### `global/` - 전역 설정

프로젝트 전반에서 사용되는 설정과 공통 클래스입니다.

| 패키지            | 역할                                           |
| ----------------- | ---------------------------------------------- |
| `common/request`  | 페이징, 정렬 등 공통 Request 객체              |
| `common/response` | API 공통 Response 객체                         |
| `config`          | Spring 설정 (`WebConfig`, `SecurityConfig` 등) |
| `error`           | 예외 핸들링 (아래 상세 설명)                   |
| `util`            | 유틸리티 클래스                                |

---

### `global/error/` - 예외 처리 구조

```
global/error/
├── ErrorCode.java                   # 모든 에러 코드 enum
├── ErrorResponse.java               # 통일된 JSON 응답 객체
├── GlobalExceptionHandler.java      # @ControllerAdvice
└── exception/
    ├── BusinessException.java       # 비즈니스 예외 최상위 클래스
    ├── EntityNotFoundException.java # 엔티티 조회 실패
    └── InvalidValueException.java   # 잘못된 값
```

---

### `infra/` - 외부 인프라

외부 서비스 연동 코드입니다. **인터페이스 기반**으로 구현하여 대체 가능성을 높입니다.

```java
// 인터페이스
public interface SmsClient {
    void send(SmsRequest request);
}

// 구현체
public class AmazonSmsClient implements SmsClient { ... }
public class KtSmsClient implements SmsClient { ... }
```

---

## 도메인 예시: User

```
domain/user/
├── api/
│   └── UserApi.java
├── application/
│   └── UserService.java
│   └── UserSearchService.java
├── dao/
│   └── UserRepository.java
├── domain/
│   ├── User.java
│   └── UserStatus.java           # 도메인 전용 Enum
├── dto/
│   ├── CreateUserRequest.java
│   ├── UpdateUserRequest.java
│   └── UserResponse.java
└── exception/
│   ├── UserNotFoundException.java
│   └── EmailDuplicateException.java
```

---

## 신규 도메인 추가 체크리스트

- [ ] `domain/[도메인]/` 패키지 생성
- [ ] Entity 클래스 (`domain/`)
- [ ] Repository 인터페이스 (`dao/`)
- [ ] Service 클래스 (`application/`)
- [ ] DTO 클래스 (`dto/`)
- [ ] Controller (`api/`)
- [ ] 도메인 전용 예외 (`exception/`)
- [ ] 테스트 코드 작성
