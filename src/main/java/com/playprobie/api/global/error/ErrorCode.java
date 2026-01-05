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
	SESSION_EXPIRED(400, "C007", "세션이 만료되었습니다."), // Moved from S001 and re-coded to C007

	// Game
	GAME_NOT_FOUND(404, "G001", "게임을 찾을 수 없습니다."),
	GAME_BUILD_NOT_FOUND(404, "G002", "게임 빌드를 찾을 수 없습니다."),

	// Game Build (S3 operations)
	S3_FILE_NOT_FOUND(404, "G003", "S3에서 파일을 찾을 수 없습니다."), // Renamed from S3_FILE_NOT_FOUND(G003) and message updated
	S3_ACCESS_ERROR(500, "G004", "S3 접근 중 오류가 발생했습니다."), // Replaced S3_ACCESS_FAILED(G004)
	STS_CLIENT_ERROR(500, "G005", "임시 자격 증명 발급 중 오류가 발생했습니다."),

	// Survey
	SURVEY_NOT_FOUND(404, "S001", "설문을 찾을 수 없습니다."),
	QUESTION_NOT_FOUND(404, "S002", "질문을 찾을 수 없습니다."),
	QUESTION_ALREADY_CONFIRMED(400, "S003", "이미 확정된 질문은 수정할 수 없습니다."),
	SURVEY_SESSION_NOT_FOUND(404, "S004", "요청하신 설문 세션을 찾을 수 없습니다."),

	// User
	USER_NOT_FOUND(404, "U001", "사용자를 찾을 수 없습니다."),
	EMAIL_DUPLICATE(400, "U002", "이미 사용 중인 이메일입니다."),
	INVALID_CREDENTIALS(401, "U003", "이메일 또는 비밀번호가 올바르지 않습니다."),

	// Workspace
	WORKSPACE_NOT_FOUND(404, "W001", "워크스페이스를 찾을 수 없습니다."),
	WORKSPACE_MEMBER_NOT_FOUND(404, "W002", "워크스페이스 멤버를 찾을 수 없습니다."),
	WORKSPACE_ACCESS_DENIED(403, "W003", "해당 워크스페이스에 접근 권한이 없습니다.");

	private final int status;
	private final String code;
	private final String message;
}
