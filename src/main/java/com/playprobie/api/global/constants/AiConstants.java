package com.playprobie.api.global.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiConstants {

	// SSE Event Names
	public static final String EVENT_START = "start";
	public static final String EVENT_DONE = "done";
	public static final String EVENT_QUESTION = "question";
	public static final String EVENT_ANALYZE_ANSWER = "analyze_answer";
	public static final String EVENT_TOKEN = "token"; // Legacy
	public static final String EVENT_CONTINUE = "continue"; // New
	public static final String EVENT_GENERATE_TAIL_COMPLETE = "generate_tail_complete";
	public static final String EVENT_INTERVIEW_COMPLETE = "interview_complete";
	public static final String EVENT_ERROR = "error";
	public static final String EVENT_PROGRESS = "progress";
	public static final String EVENT_REACTION = "reaction"; // AI reactions to user answers
	// Opening Phase Events (인사말/첫번째 질문 분리)
	public static final String EVENT_GREETING_CONTINUE = "greeting_continue";
	public static final String EVENT_GREETING_DONE = "greeting_done";
	public static final String EVENT_RETRY_REQUEST = "retry_request"; // Re-input request

	// ===== 새로 추가 =====
	public static final String EVENT_VALIDITY_RESULT = "validity_result";
	public static final String EVENT_QUALITY_RESULT = "quality_result";

	// AI Actions
	public static final String ACTION_TAIL_QUESTION = "TAIL_QUESTION";
	public static final String ACTION_RETRY_QUESTION = "RETRY_QUESTION";
	public static final String ACTION_PASS_TO_NEXT = "PASS_TO_NEXT";
	public static final String ACTION_OPENING = "OPENING";
	public static final String ACTION_CLOSING = "CLOSING";
	public static final String ACTION_FIXED = "FIXED";

	// Reasons
	public static final String REASON_FATIGUE = "FATIGUE";
	public static final String REASON_ALL_DONE = "ALL_DONE";

	// Analysis Steps (Optional, if needed for matching string literals)
	public static final String STEP_LOADING = "loading";
	public static final String STEP_LOADED = "loaded";
	public static final String STEP_REDUCING = "reducing";
	public static final String STEP_CLUSTERING = "clustering";
	public static final String STEP_EXTRACTING_KEYWORDS = "extracting_keywords";
	public static final String STEP_ANALYZING = "analyzing";
	public static final String STEP_FINALIZING = "finalizing";
}
