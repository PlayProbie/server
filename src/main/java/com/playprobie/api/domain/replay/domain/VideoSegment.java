package com.playprobie.api.domain.replay.domain;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 영상 세그먼트 Entity
 * 20~30초 단위로 분할된 플레이 영상 메타데이터 저장
 */
@Entity
@Table(name = "video_segment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class VideoSegment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "segment_id")
	private Long id;

	@UuidGenerator(style = UuidGenerator.Style.TIME)
	@Column(name = "segment_uuid", nullable = false, unique = true)
	private UUID uuid;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	private SurveySession session;

	@Column(name = "s3_key", nullable = false, length = 512)
	private String s3Key;

	@Column(name = "sequence", nullable = false)
	private Integer sequence;

	@Column(name = "video_start_ms", nullable = false)
	private Long videoStartMs;

	@Column(name = "video_end_ms", nullable = false)
	private Long videoEndMs;

	@Enumerated(EnumType.STRING)
	@Column(name = "upload_status", nullable = false, length = 20)
	private UploadStatus uploadStatus = UploadStatus.PENDING;

	@Builder
	public VideoSegment(SurveySession session, String s3Key, Integer sequence,
		Long videoStartMs, Long videoEndMs) {
		this.session = Objects.requireNonNull(session, "VideoSegment 생성 시 session은 필수입니다");
		this.s3Key = Objects.requireNonNull(s3Key, "VideoSegment 생성 시 s3Key는 필수입니다");
		this.sequence = Objects.requireNonNull(sequence, "VideoSegment 생성 시 sequence는 필수입니다");
		this.videoStartMs = Objects.requireNonNull(videoStartMs, "VideoSegment 생성 시 videoStartMs는 필수입니다");
		this.videoEndMs = Objects.requireNonNull(videoEndMs, "VideoSegment 생성 시 videoEndMs는 필수입니다");
		this.uploadStatus = UploadStatus.PENDING;
	}

	/**
	 * 업로드 완료 처리
	 */
	public void markUploaded() {
		this.uploadStatus = UploadStatus.UPLOADED;
	}

	/**
	 * 업로드 실패 처리
	 */
	public void markFailed() {
		this.uploadStatus = UploadStatus.FAILED;
	}
}
