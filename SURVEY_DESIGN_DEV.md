# 설문 설계 백엔드 개발 문서

> 이슈: #12 / 브랜치: `feat/#12`
> PR: `feat: 설문 설계 백엔드 API 구현 (#12)`

---

## 📋 Phase 체크리스트

| Phase | 설명 | 상태 |
|-------|------|------|
| 1 | Repository 추가 | ✅ |
| 2 | Game/Survey CRUD API | ✅ |
| 3 | AI Mock 질문 생성/수정/리뷰/확정 | ✅ |
| **리팩토링** | DraftQuestion → FixedQuestion + status 통합 | ✅ |

---

## 🔌 API 엔드포인트

### Survey API (`/api/v1/surveys`)

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/surveys` | 설문 생성 |
| GET | `/surveys/{id}` | 설문 조회 |
| POST | `/surveys/{id}/generate-questions` | AI 질문 10개 생성 (DRAFT) |
| GET | `/surveys/{id}/draft-questions` | 임시 질문 목록 |
| GET | `/surveys/{id}/questions` | 확정 질문 목록 |
| PUT | `/surveys/{id}/questions/{qId}` | 질문 수정 (DRAFT만) |
| POST | `/surveys/{id}/questions/{qId}/review` | 피드백 + 대안 3개 |
| POST | `/surveys/{id}/confirm` | 설문 확정 (status → CONFIRMED) |

### Game API (`/api/v1/games`)

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/games` | 게임 생성 |
| GET | `/games/{id}` | 게임 조회 |

---

## 📂 파일 구조

### Domain - Survey
```
survey/
├── domain/
│   ├── Survey.java
│   ├── FixedQuestion.java      # status 필드 추가 (DRAFT/CONFIRMED)
│   ├── QuestionStatus.java     # enum
│   └── TestPurpose.java
├── repository/
│   ├── SurveyRepository.java
│   └── FixedQuestionRepository.java
├── service/
│   └── SurveyService.java      # 질문 관련 로직 통합
├── controller/
│   └── SurveyController.java   # 모든 엔드포인트 통합
└── dto/
    ├── CreateSurveyRequest.java
    ├── SurveyResponse.java
    ├── FixedQuestionResponse.java
    ├── UpdateQuestionRequest.java
    └── QuestionReviewResponse.java
```

### Infra - AI
```
infra/ai/
├── AiClient.java               # 인터페이스
└── MockAiClient.java           # Mock 구현체
```

---

## 🎯 비즈니스 플로우

```
1. 게임 생성 (POST /games)
          ↓
2. 설문 생성 (POST /surveys)
          ↓
3. AI 질문 생성 (POST /surveys/{id}/generate-questions)
          ↓  → FixedQuestion 10개 저장 (status = DRAFT)
4. 질문 수정 (PUT /surveys/{id}/questions/{qId})
          ↓
5. 질문 리뷰 (POST /surveys/{id}/questions/{qId}/review)
          ↓  → 피드백 + 대안 3개 반환
6. 설문 확정 (POST /surveys/{id}/confirm)
          ↓  → status = CONFIRMED로 UPDATE
7. 확정 질문 조회 (GET /surveys/{id}/questions)
```

---

## 📊 엔티티 구조

### FixedQuestion
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `fixed_q_id` | BIGINT (PK) | 질문 ID |
| `survey_id` | BIGINT (FK) | 설문 ID |
| `q_content` | TEXT | 질문 내용 |
| `q_order` | INT | 질문 순서 |
| `q_status` | ENUM | DRAFT / CONFIRMED |

---

## 🧪 테스트 방법

1. 서버 실행: `./gradlew bootRun`
2. Swagger UI: `http://localhost:8080/swagger-ui.html`

### 테스트 데이터 (자동 생성)
- Game ID: 1 (테스트 게임)
- Survey ID: 1 (UI/UX 테스트)

### 테스트 순서
```bash
# 1. 질문 생성
POST /api/v1/surveys/1/generate-questions

# 2. 임시 질문 확인
GET /api/v1/surveys/1/draft-questions

# 3. 질문 수정
PUT /api/v1/surveys/1/questions/1
{ "q_content": "수정된 질문" }

# 4. 리뷰 요청
POST /api/v1/surveys/1/questions/1/review

# 5. 확정
POST /api/v1/surveys/1/confirm

# 6. 확정 질문 확인
GET /api/v1/surveys/1/questions
```
