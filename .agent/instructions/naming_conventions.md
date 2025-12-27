# 네이밍 규칙

## 파일명 규칙

| 유형          | 스타일       | 예시                   |
| ------------- | ------------ | ---------------------- |
| Java 클래스   | `PascalCase` | `UserApi.java`         |
| 리소스 파일   | `kebab-case` | `application-dev.yml`  |
| 테스트 클래스 | `[대상]Test` | `UserServiceTest.java` |

---

## 클래스명 규칙

### Controller (API)

```java
// 패턴: [도메인]Api 또는 [도메인]Controller
@RestController
public class UserApi { }        // REST API 명시적 표현
public class UserController { } // 전통적인 방식
```

### Service (Application)

```java
// 패턴: [도메인]Service 또는 [기능]Service
@Service
public class UserService { }
public class UserSearchService { }    // 조회 전용
public class UserCommandService { }   // 명령 전용
```

### Repository (DAO)

```java
// 패턴: [도메인]Repository
public interface UserRepository extends JpaRepository<User, Long> { }

// 조회 전용 확장
public interface UserFindDao { }
public class UserFindDaoImpl implements UserFindDao { }
```

### Entity (Domain)

```java
// 패턴: [도메인] (단수형)
@Entity
public class User { }
public class Session { }
public class Participant { }
```

### DTO

```java
// 패턴: [동작][도메인][Request/Response]
// 패턴: [동작][도메인][Request/Response]
public record CreateUserRequest(String email, String name) { }
public record UpdateUserRequest(String name) { }
public record UserResponse(Long id, String email) { }

// 목록 조회 시
public record UserListResponse(List<UserResponse> users) { }
```

### Exception

```java
// 패턴: [도메인][상황]Exception
// 패턴: [도메인][상황]Exception
public class UserNotFoundException extends EntityNotFoundException { }
public class EmailDuplicateException extends InvalidValueException { }
public class CouponExpiredException extends InvalidValueException { }
```

---

## 메서드명 규칙

### Controller (API)

| HTTP Method | 메서드 패턴                        | 예시               |
| ----------- | ---------------------------------- | ------------------ |
| GET (단건)  | `get[도메인]`                      | `getUser()`        |
| GET (목록)  | `get[도메인]s` / `get[도메인]List` | `getUsers()`       |
| POST        | `create[도메인]`                   | `createUser()`     |
| PUT         | `update[도메인]`                   | `updateUser()`     |
| PATCH       | `update[도메인][속성]`             | `updateUserName()` |
| DELETE      | `delete[도메인]`                   | `deleteUser()`     |

### Service (Application)

| 동작      | 메서드 패턴                    | 예시                 |
| --------- | ------------------------------ | -------------------- |
| 단건 조회 | `findById`, `find[도메인]ById` | `findById(id)`       |
| 목록 조회 | `findAll`, `findAllBy[조건]`   | `findAllByStatus()`  |
| 생성      | `create[도메인]`               | `createUser()`       |
| 수정      | `update[도메인]`               | `updateUser()`       |
| 삭제      | `delete[도메인]`               | `deleteUser()`       |
| 검증      | `verify[대상]`                 | `verifyExpiration()` |

### Entity (Domain)

```java
// 비즈니스 로직은 Entity 내부에서 처리
public class Coupon {

    public void use() {
        verifyExpiration();
        verifyUsed();
        this.used = true;
    }

    private void verifyUsed() {
        if (used) throw new CouponAlreadyUsedException();
    }

    private void verifyExpiration() {
        if (LocalDate.now().isAfter(expirationDate)) {
            throw new CouponExpiredException();
        }
    }
}
```

### Repository (DAO)

```java
// Spring Data JPA 쿼리 메서드 규칙 준수
// Spring Data JPA 쿼리 메서드 규칙 준수
Optional<User> findById(Long id);
List<User> findAllByStatus(UserStatus status);
boolean existsByEmail(String email);
```

---

## 변수명 규칙

| 유형      | 스타일                 | 예시                                    |
| --------- | ---------------------- | --------------------------------------- |
| 일반 변수 | `camelCase`            | `userName`, `loginCount`                |
| 상수      | `SCREAMING_SNAKE_CASE` | `MAX_LOGIN_ATTEMPTS`, `DEFAULT_TIMEOUT` |
| Boolean   | `is/has/can` 접두사    | `isActive`, `hasPermission`, `canEdit`  |
| 컬렉션    | 복수형                 | `users`, `roles`, `permissions`         |

---

## 도메인별 네이밍 예시

| 도메인      | Entity        | API              | Service              | DTO                   | Exception                      |
| ----------- | ------------- | ---------------- | -------------------- | --------------------- | ------------------------------ |
| User        | `User`        | `UserApi`        | `UserService`        | `CreateUserRequest`   | `UserNotFoundException`        |
| Session     | `Session`     | `SessionApi`     | `SessionService`     | `JoinSessionRequest`  | `SessionExpiredException`      |
| Participant | `Participant` | `ParticipantApi` | `ParticipantService` | `ParticipantResponse` | `ParticipantNotFoundException` |

---

## 피해야 할 패턴

```java
// ❌ Bad
public class UserCtrl { }           // 약어 사용
public class UserDTO { }            // DTO를 클래스명에 포함
public class UsersEntity { }      // 복수형 + 접미사
public void processUser() { }       // 모호한 동작
public void handleUser() { }        // 너무 일반적

// ✅ Good
public class UserController { }     // 또는 UserApi
public record UserResponse() { }
public class User { }
public void createUser() { }
public void verifyEmail() { }  // 명확한 동작
```

---

## 패키지명 규칙

| 규칙           | 예시                        |
| -------------- | --------------------------- |
| 모두 소문자    | `controller`, `service`     |
| 단수형 사용    | `domain` (O), `domains` (X) |
| 약어 사용 가능 | `dto`, `dao`, `api`         |
