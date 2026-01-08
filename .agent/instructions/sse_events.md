# SSE 이벤트 형식 명세

## 개요

Spring 서버 ↔ FastAPI 서버 간 Server-Sent Events (SSE) 통신 형식

## 기본 구조

모든 SSE 이벤트는 다음 형식을 따릅니다:

```json
{
  "event": "이벤트_타입",
  "data": {
    // 이벤트별 데이터
  }
}
```

---

## 📡 인터뷰 흐름별 SSE 이벤트

### 1️⃣ Phase 1: 세션 연결

**클라이언트 → Spring**
```
GET /interview/{sessionUuid}/stream
```

**Spring → 클라이언트**
```json
event: "connect"
data: "connected"
```

---

### 2️⃣ Phase 2: 오프닝 (Opening)

**Spring → FastAPI**
```
POST /surveys/start-session
Content-Type: application/json

{
  "session_id": "uuid",
  "game_info": { /* 게임 정보 */ },
  "tester_profile": { /* 테스터 프로필 */ }
}
```

**FastAPI → Spring SSE 응답**

#### 2-1. `start` 이벤트
```json
{
  "event": "start",
  "data": {
    "status": "generating_opening"
  }
}
```

#### 2-2. `continue` 이벤트 (스트리밍)
```json
{
  "event": "continue",
  "data": {
    "content": "안녕하세요! 오늘 테스트에..."  // 오프닝 멘트 토큰
  }
}
```
- 여러 번 전송됨 (스트리밍)
- `q_type`: `"OPENING"`
- `turn_num`: `0`

#### 2-3. `done` 이벤트
```json
{
  "event": "done",
  "data": {
    "question_text": "첫 번째 질문은..."  // 완성된 오프닝 질문
  }
}
```

---

### 3️⃣ Phase 3: 고정 질문 (Fixed Question)

**클라이언트 → Spring**
```
POST /interview/{sessionUuid}/messages
Content-Type: application/json

{
  "fixed_q_id": 1,
  "turn_num": 1,
  "question_text": "게임의 조작법은 어떠셨나요?",
  "answer_text": "직관적이었습니다."
}
```

**Spring → FastAPI**
```
POST /surveys/interaction
Content-Type: application/json

{
  "session_id": "uuid",
  "answer_text": "직관적이었습니다.",
  "question_text": "게임의 조작법은 어떠셨나요?",
  "game_info": null,
  "conversation_history": null,
  // ===== Option A: 질문 정보 추가 =====
  "survey_id": 123,
  "current_question_order": 3,
  "total_questions": 5
}
```

**FastAPI는 이제 마지막 질문 여부를 판단할 수 있습니다:**
- `current_question_order == total_questions` → 마지막 질문
- `done` 이벤트에서 `should_end: true` 반환

**FastAPI → Spring SSE 응답**

#### 3-1. `start` 이벤트
```json
{
  "event": "start",
  "data": {
    "status": "analyzing"
  }
}
```

#### 3-2. `analyze_answer` 이벤트
```json
{
  "event": "analyze_answer",
  "data": {
    "action": "TAIL_QUESTION",  // 또는 "PASS_TO_NEXT"
    "analysis": "사용자는 긍정적인 반응을..."
  }
}
```

**Actions:**
- `TAIL_QUESTION`: 꼬리 질문 생성
- `PASS_TO_NEXT`: 다음 고정 질문으로 이동

#### 3-3. `continue` 이벤트 (꼬리 질문 생성 시)
```json
{
  "event": "continue",
  "data": {
    "content": "구체적으로 어떤 점이..."  // 꼬리 질문 토큰
  }
}
```
- `q_type`: `"TAIL"`
- `turn_num`: `2` (또는 현재 턴 + 1)

#### 3-4. `generate_tail_complete` 이벤트
```json
{
  "event": "generate_tail_complete",
  "data": {
    "message": "구체적으로 어떤 점이 직관적이었나요?",
    "tail_question_count": 1
  }
}
```
→ Spring 서버가 InterviewLog에 꼬리 질문 저장

#### 3-5. `done` 이벤트
```json
{
  "event": "done",
  "data": {
    "should_end": false,  // 인터뷰 종료 권장 여부
    "end_reason": null    // "FATIGUE", "TIMEOUT", "ALL_DONE"
  }
}
```

**`should_end` 로직:**
- `should_end: true` → Spring이 `streamClosing()` 호출
- `should_end: false` + `action: PASS_TO_NEXT` → 다음 고정 질문
- `should_end: false` + `action: TAIL_QUESTION` → 사용자 답변 대기

---

### 4️⃣ Phase 4: 다음 질문 전송

**Spring → 클라이언트**
```json
event: "question"
data: {
  "fixed_q_id": 2,
  "q_type": "FIXED",
  "question_text": "게임의 난이도는 어떠셨나요?",
  "turn_num": 1
}
```

---

### 5️⃣ Phase 5: 클로징 (Closing)

**Spring → FastAPI**
```
POST /surveys/end-session
Content-Type: application/json

{
  "session_id": "uuid",
  "end_reason": "ALL_DONE",  // "FATIGUE", "TIMEOUT"
  "game_info": null
}
```

**FastAPI → Spring SSE 응답**

#### 5-1. `start` 이벤트
```json
{
  "event": "start",
  "data": {
    "status": "generating_closing"
  }
}
```

#### 5-2. `continue` 이벤트 (스트리밍)
```json
{
  "event": "continue",
  "data": {
    "content": "오늘 테스트에 참여해주셔서..."  // 클로징 멘트 토큰
  }
}
```
- `q_type`: `"CLOSING"`
- `turn_num`: `0`

#### 5-3. `done` 이벤트
```json
{
  "event": "done",
  "data": {}
}
```
→ Spring 서버가 `sendInterviewComplete()` 호출

---

### 6️⃣ Phase 6: 인터뷰 완료

**Spring → 클라이언트**
```json
event: "interview_complete"
data: {
  "status": "completed"
}
```

→ SSE 연결 종료 (`emitter.complete()`)

---

## 🚨 에러 이벤트

**FastAPI → Spring**
```json
{
  "event": "error",
  "data": {
    "message": "AI 서버 내부 오류가 발생했습니다."
  }
}
```

**Spring → 클라이언트**
```json
event: "error"
data: {
  "message": "AI 서버 내부 오류가 발생했습니다."
}
```

---

## 📊 데이터 타입 정의

### QuestionPayload
```java
{
  "fixed_q_id": Long,      // 고정 질문 ID (null 가능)
  "q_type": String,        // "FIXED", "TAIL", "OPENING", "CLOSING"
  "question_text": String, // 질문 내용
  "turn_num": Integer      // 턴 번호
}
```

### StatusPayload
```java
{
  "status": String  // "connected", "analyzing", "generating_opening", "completed" 등
}
```

### ErrorPayload
```java
{
  "message": String  // 에러 메시지
}
```

### AnalysisPayload
```java
{
  "action": String,    // "TAIL_QUESTION", "PASS_TO_NEXT"
  "analysis": String   // 분석 내용
}
```

---

## 🔄 전체 흐름 요약

```
1. 클라이언트 SSE 연결
   └─> Spring: connect 이벤트

2. 오프닝
   └─> Spring → FastAPI: /surveys/start-session
       └─> start → continue (스트리밍) → done

3. 고정 질문 루프
   ├─> Spring → 클라이언트: question 이벤트
   ├─> 클라이언트 → Spring: 답변 전송
   ├─> Spring → FastAPI: /surveys/interaction
   │   └─> start → analyze_answer
   │       ├─> action: TAIL_QUESTION
   │       │   └─> continue → generate_tail_complete → done
   │       │       └─> (사용자 답변 대기, 3번으로 돌아감)
   │       └─> action: PASS_TO_NEXT
   │           └─> done → 다음 고정 질문 (3번으로 돌아감)
   └─> (모든 질문 완료 시 4번으로)

4. 클로징
   └─> Spring → FastAPI: /surveys/end-session
       └─> start → continue (스트리밍) → done

5. 인터뷰 완료
   └─> Spring → 클라이언트: interview_complete
       └─> SSE 연결 종료
```

---

## 📝 중요 사항

### 꼬리 질문 제한
- 최대 횟수: `application.yml`의 `ai.interview.max-tail-questions`
- 초과 시: AI 호출 없이 바로 다음 고정 질문으로 이동
- Spring 서버에서 `currentTailCount >= maxTailQuestions` 체크

### 종료 조건
1. **모든 질문 완료**: `getNextQuestion()` 결과 없음 → `REASON_ALL_DONE`
2. **AI 권장 종료**: `should_end: true` → `end_reason` 확인
   - `FATIGUE`: 피로도 감지
   - `TIMEOUT`: 15분 타임아웃
3. **SSE 타임아웃**: `ai.sse.timeout` 초과 (기본 10분)

### 이벤트 순서 보장
- FastAPI는 반드시 `start` → 처리 → `done` 순서로 이벤트 전송
- `done` 이벤트 없이 스트림이 끊기면 에러로 간주

---

## 참고 코드

- **상수 정의**: [`AiConstants.java`](file:///Users/nobbkim/PlayProbie/server/src/main/java/com/playprobie/api/global/constants/AiConstants.java)
- **SSE 처리**: [`FastApiClient.java`](file:///Users/nobbkim/PlayProbie/server/src/main/java/com/playprobie/api/infra/ai/impl/FastApiClient.java)
- **Payload 정의**: `src/main/java/com/playprobie/api/infra/sse/dto/`
