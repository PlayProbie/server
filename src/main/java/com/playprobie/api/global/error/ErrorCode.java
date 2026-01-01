package com.playprobie.api.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
	// Common
	INVALID_INPUT_VALUE(400, "C001", "잘못된 입력값입니다."),
	METHOD_NOT_ALLOWED(405, "C002", "허용되지 않은 메서드입니다."),
	ENTITY_NOT_FOUND(404, "C003", "Entity Not Found"),
	INTERNAL_SERVER_ERROR(500, "C004", "서버 내부 오류가 발생했습니다."),
	HANDLE_ACCESS_DENIED(403, "C005", "접근 권한이 없습니다."),
	INVALID_TYPE_VALUE(400, "C006", "잘못된 타입의 값입니다."),

	// Game
	GAME_NOT_FOUND(404, "G001", "게임을 찾을 수 없습니다."),

	// Survey
	SURVEY_NOT_FOUND(404, "S001", "설문을 찾을 수 없습니다."),
	QUESTION_NOT_FOUND(404, "S002", "질문을 찾을 수 없습니다."),
	QUESTION_ALREADY_CONFIRMED(400, "S003", "이미 확정된 질문은 수정할 수 없습니다."),
	SURVEY_SESSION_NOT_FOUND(404, "S004", "요청하신 설문 세션을 찾을 수 없습니다."),

	// User
	USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다."),
	EMAIL_DUPLICATE(400, "U002", "이미 사용 중인 이메일입니다."),

	// Workspace
	WORKSPACE_NOT_FOUND(404, "W001", "워크스페이스를 찾을 수 없습니다."),
	WORKSPACE_MEMBER_NOT_FOUND(404, "W002", "워크스페이스 멤버를 찾을 수 없습니다."),
	WORKSPACE_ACCESS_DENIED(403, "W003", "해당 워크스페이스에 접근 권한이 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
