# Entity 작성 규칙

> Rich Domain Object 패턴 가이드

## Entity 책임 범위

### ✅ Entity가 가져야 하는 책임

| 책임                     | 예시                                   | 설명                                    |
| ------------------------ | -------------------------------------- | --------------------------------------- |
| **자신의 상태 변경**     | `complete()`, `drop()`, `updateName()` | Setter 대신 의미 있는 메서드명 사용     |
| **자기 필드 기반 검증**  | `isOpen()`, `isFinished()`             | 현재 상태가 특정 조건을 만족하는지 확인 |
| **자식 컬렉션 조작**     | `addQuestion()`, `removeQuestion()`    | Aggregate 내 자식 Entity 관리           |
| **연관관계 편의 메서드** | `assignSurvey(survey)`                 | 양방향 관계 동기화                      |

### ❌ Entity가 가지면 안 되는 책임

| 피해야 할 패턴               | 이유                     | 대안                    |
| ---------------------------- | ------------------------ | ----------------------- |
| 다른 Aggregate Root 조회     | Entity가 Repository 의존 | Service 계층에서 처리   |
| 외부 서비스 호출 (AI API 등) | 인프라 의존성 발생       | Service 또는 infra 계층 |
| Repository 직접 접근         | 계층 위반                | Service 계층에서 주입   |
| 복잡한 비즈니스 로직         | 테스트 어려움            | Domain Service 분리     |

---

## 상태 전이 패턴

상태를 가진 Entity는 **명시적인 전이 메서드**를 제공해야 합니다.

```java
// ✅ Good: 상태 전이 규칙이 Entity 내부에 캡슐화
public void complete() {
    if (this.status.isFinished()) {
        throw new IllegalStateException("이미 종료된 세션입니다.");
    }
    this.status = SessionStatus.COMPLETED;
    this.endedAt = LocalDateTime.now();
}

// ❌ Bad: 외부에서 직접 상태 변경 (Setter 노출)
session.setStatus(SessionStatus.COMPLETED);
session.setEndedAt(LocalDateTime.now());
```

### 상태 Enum에 헬퍼 메서드 추가

```java
public enum SessionStatus {
    IN_PROGRESS, COMPLETED, DROPPED;

    public boolean isFinished() {
        return this == COMPLETED || this == DROPPED;
    }
}
```

---

## Aggregate 관리 패턴

### 부모-자식 관계

```java
// Survey (Aggregate Root)
@OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
private List<FixedQuestion> fixedQuestions = new ArrayList<>();

// 편의 메서드로 연관관계 동기화
public void addQuestion(FixedQuestion question) {
    this.fixedQuestions.add(question);
    question.assignSurvey(this);  // 양방향 동기화
}

// 외부 수정 방지 (불변 리스트 반환)
public List<FixedQuestion> getFixedQuestions() {
    return Collections.unmodifiableList(fixedQuestions);
}
```

### 자식 Entity의 연관관계 메서드

```java
// FixedQuestion (자식)
// package-private으로 외부 호출 방지
void assignSurvey(Survey survey) {
    this.survey = survey;
}
```

---

## 정적 팩토리 메서드

복잡한 생성 로직은 정적 팩토리 메서드로 캡슐화합니다.

```java
// ✅ Good: 생성 로직 캡슐화
public static TesterProfile createAnonymous(String ageGroup, String gender, String preferGenre) {
    return TesterProfile.builder()
        .testerId(UUID.randomUUID().toString())  // 자동 생성
        .ageGroup(ageGroup)
        .gender(gender)
        .preferGenre(preferGenre)
        .build();
}

// 사용
TesterProfile profile = TesterProfile.createAnonymous("20s", "M", "RPG");
```

---

## @Builder 사용 가이드

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 필수
public class SurveySession {

    @Builder
    public SurveySession(Survey survey, TesterProfile testerProfile) {
        this.survey = survey;
        this.testerProfile = testerProfile;
        this.status = SessionStatus.IN_PROGRESS;  // 기본값 설정
        this.startedAt = LocalDateTime.now();
    }
}
```

### 주의사항

- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`: JPA용 기본 생성자, 외부 사용 방지
- `@Builder`는 생성자에 적용하여 필드 선택적 제어
- 기본값은 생성자 내부에서 설정

---

## 코드 리뷰 체크리스트

Entity 코드 리뷰 시 확인할 항목:

- [ ] Setter 메서드가 노출되어 있지 않은가?
- [ ] 상태 변경 로직이 Entity 내부에 캡슐화되어 있는가?
- [ ] 컬렉션이 불변 리스트로 반환되는가?
- [ ] 연관관계 편의 메서드가 올바르게 동기화되는가?
- [ ] `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 적용되었는가?
