package com.playprobie.api.domain.replay.domain;

/**
 * 영상 세그먼트 업로드 상태
 */
public enum UploadStatus {
	PENDING, // 업로드 대기
	UPLOADED, // 업로드 완료
	FAILED // 업로드 실패
}
