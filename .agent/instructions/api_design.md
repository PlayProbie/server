****# API 설계 가이드

>
참고: [cheese10yun/spring-guide - Exception Guide](https://github.com/cheese10yun/spring-guide/blob/master/docs/exception-guide.md)

## URL 규칙

### 기본 원칙

| 규칙        | 설명              | 예시                  |
|-----------|-----------------|---------------------|
| Base Path | `/api/v1` 사용    | `/api/v1/users`     |
| 리소스명      | 복수형, kebab-case | `/users`            |
| 계층 표현     | 중첩 리소스로 표현      | `/users/{id}/roles` |

### URL 예시

```
GET    /api/v1/users              # 사용자 목록
POST   /api/v1/users              # 사용자 생성
GET    /api/v1/users/{id}         # 사용자 상세
PUT    /api/v1/users/{id}         # 사용자 전체 수정
PATCH  /api/v1/users/{id}         # 사용자 부분 수정
DELETE /api/v1/users/{id}         # 사용자 삭제

GET    /api/v1/users/{id}/roles   # 사용자의 권한 목록
POST   /api/v1/users/{id}/roles   # 권한 추가
```

---

## HTTP 메서드

| 메서드    | 용도        | 멱등성 | Request Body |
|--------|-----------|-----|--------------|
| GET    | 리소스 조회    | ✅   | ❌            |
| POST   | 리소스 생성    | ❌   | ✅            |
| PUT    | 리소스 전체 교체 | ✅   | ✅            |
| PATCH  | 리소스 부분 수정 | ❌   | ✅            |
| DELETE | 리소스 삭제    | ✅   | ❌            |

---

## HTTP 상태 코드

### 성공 응답

| 코드             | 의미    | 사용 시점              |
|----------------|-------|--------------------|
| 200 OK         | 성공    | GET, PUT, PATCH 성공 |
| 201 Created    | 생성 완료 | POST 성공            |
| 204 No Content | 내용 없음 | DELETE 성공          |

### 클라이언트 에러

| 코드                     | 의미          | 사용 시점        |
|------------------------|-------------|--------------|
| 400 Bad Request        | 잘못된 요청      | 유효성 검증 실패    |
| 401 Unauthorized       | 인증 필요       | 인증 토큰 없음/만료  |
| 403 Forbidden          | 권한 없음       | 접근 권한 부족     |
| 404 Not Found          | 리소스 없음      | 존재하지 않는 리소스  |
| 405 Method Not Allowed | 허용되지 않은 메서드 | 잘못된 HTTP 메서드 |
| 409 Conflict           | 충돌          | 중복 생성, 상태 충돌 |

### 서버 에러

| 코드                        | 의미    | 사용 시점     |
|---------------------------|-------|-----------|
| 500 Internal Server Error | 서버 오류 | 예상치 못한 예외 |

---

## 통일된 Error Response

### 원칙

- Error Response 객체는 **항상 동일한 형식**을 유지해야 합니다
- `Map<Key, Value>` 형식 사용 금지 → 명확한 POJO 객체 사용
- 빈 errors는 `null`이 아닌 **빈 배열 `[]`** 반환

### Error Response JSON

```json
{
  "message": "Invalid Input Value",
  "status": 400,
  "errors": [
    {
      "field": "name",
      "value": "",
      "reason": "must not be empty"
    },
    {
      "field": "email",
      "value": "invalid-email",
      "reason": "must be a well-formed email address"
    }
  ],
  "code": "C001"
}
```

### ErrorResponse 클래스

```java

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorResponse {

	private String message;
	private int status;
	private List<FieldError> errors;
	private String code;

	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class FieldError {
		private String field;
		private String value;
		private String reason;
	}
}
```

---

## ErrorCode 정의

모든 에러 코드는 **enum으로 한 곳에서 관리**합니다.

```java
public enum ErrorCode {

	// Common
	INVALID_INPUT_VALUE(400, "C001", "잘못된 입력값입니다."),
	METHOD_NOT_ALLOWED(405, "C002", "허용되지 않은 메서드입니다."),
	ENTITY_NOT_FOUND(404, "C003", "Entity Not Found"),
	INTERNAL_SERVER_ERROR(500, "C004", "서버 내부 오류가 발생했습니다."),
	HANDLE_ACCESS_DENIED(403, "C005", "접근 권한이 없습니다."),
	INVALID_TYPE_VALUE(400, "C006", "잘못된 타입의 값입니다."),

	// User
	USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다."),
	EMAIL_DUPLICATION(409, "U002", "이미 사용 중인 이메일입니다."),

	// Session
	SESSION_EXPIRED(400, "S001", "세션이 만료되었습니다."),
	;

	private final int status;
	private final String code;
	private final String message;
}
```

---

## 예외 처리 전략

### BusinessException 계층 구조

```
BusinessException (최상위)
├── InvalidValueException       # 유효하지 않은 값
│   ├── CouponExpiredException
│   └── CouponAlreadyUsedException
└── EntityNotFoundException     # 엔티티 조회 실패
    ├── UserNotFoundException
    └── EmailDuplicateException
```

### BusinessException 클래스

```java
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
```

### 도메인 예외 예시

```java
// domain/user/exception/UserNotFoundException.java
public class UserNotFoundException extends EntityNotFoundException {
	public UserNotFoundException() {
		super(ErrorCode.USER_NOT_FOUND);
	}
}
```

---

## GlobalExceptionHandler

```java

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	// @Valid 검증 실패
	@ExceptionHandler(MethodArgumentNotValidException.class)
	protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException e) {
		log.warn("handleMethodArgumentNotValidException", e);
		final ErrorResponse response = ErrorResponse.of(
			ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// @ModelAttribute 바인딩 실패
	@ExceptionHandler(BindException.class)
	protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
		log.warn("handleBindException", e);
		final ErrorResponse response = ErrorResponse.of(
			ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// Enum 타입 불일치 등
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException e) {
		log.warn("handleMethodArgumentTypeMismatchException", e);
		final ErrorResponse response = ErrorResponse.of(e);
		return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
	}

	// 지원하지 않는 HTTP 메서드
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
		HttpRequestMethodNotSupportedException e) {
		log.warn("handleHttpRequestMethodNotSupportedException", e);
		final ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
		return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
	}

	// 비즈니스 예외
	@ExceptionHandler(BusinessException.class)
	protected ResponseEntity<ErrorResponse> handleBusinessException(
		BusinessException e) {
		log.warn("handleBusinessException", e);
		final ErrorCode errorCode = e.getErrorCode();
		final ErrorResponse response = ErrorResponse.of(errorCode);
		return new ResponseEntity<>(response,
			HttpStatus.valueOf(errorCode.getStatus()));
	}

	// 그 외 모든 예외
	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ErrorResponse> handleException(Exception e) {
		log.error("handleException", e);
		final ErrorResponse response = ErrorResponse.of(
			ErrorCode.INTERNAL_SERVER_ERROR);
		return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
	}
}
```

---

## Controller 작성 예시

```java

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserApi {

	private final UserService userService;

	@GetMapping
	public ResponseEntity<List<UserResponse>> getUsers() {
		return ResponseEntity.ok(userService.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
		return ResponseEntity.ok(userService.findById(id));
	}

	@PostMapping
	public ResponseEntity<UserResponse> createUser(
		@Valid @RequestBody CreateUserRequest request) {
		UserResponse response = userService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		userService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
```

---

## 예외 발생 예시

```java

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public UserResponse findById(Long id) {
		User user = userRepository.findById(id)
			.orElseThrow(UserNotFoundException::new);
		return UserResponse.from(user);
	}

	public void updateName(Long id, String newName) {
		User user = userRepository.findById(id)
			.orElseThrow(UserNotFoundException::new);

		user.updateName(newName);
	}
}
```
