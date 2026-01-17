package com.playprobie.api.domain.replay.application;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.interview.dao.SurveySessionRepository;
import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.replay.dao.AnalysisTagRepository;
import com.playprobie.api.domain.replay.dao.VideoSegmentRepository;
import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.domain.VideoSegment;
import com.playprobie.api.domain.replay.dto.InputLogDto;
import com.playprobie.api.domain.replay.dto.PresignedUrlRequest;
import com.playprobie.api.domain.replay.dto.PresignedUrlResponse;
import com.playprobie.api.domain.replay.dto.ReplayLogRequest;
import com.playprobie.api.domain.replay.dto.UploadCompleteRequest;
import com.playprobie.api.global.config.properties.AwsProperties;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 리플레이 서비스
 * 입력 로그 분석, Presigned URL 발급, 영상 세그먼트 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

	private final InputLogAnalyzer inputLogAnalyzer;
	private final SurveySessionRepository surveySessionRepository;
	private final AnalysisTagRepository analysisTagRepository;
	private final VideoSegmentRepository videoSegmentRepository;
	private final S3Presigner replayS3Presigner;
	private final AwsProperties awsProperties;

	private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 300;

	/**
	 * 입력 로그 배치 수신 및 분석
	 * 로그는 메모리에서 분석 후 폐기, AnalysisTag만 저장
	 */
	@Transactional
	public void processInputLogs(String sessionUuid, ReplayLogRequest request) {
		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionUuid))
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		List<InputLogDto> logs = request.logs();

		if (logs == null || logs.isEmpty()) {
			log.info("[ReplayService] No logs to process for session: {}", sessionUuid);
			return;
		}

		// 메모리에서 즉시 분석 (로그는 저장하지 않음)
		List<AnalysisTag> detectedTags = inputLogAnalyzer.analyze(session, logs);

		// 감지된 태그만 DB에 저장
		if (!detectedTags.isEmpty()) {
			analysisTagRepository.saveAll(detectedTags);
			log.info("[ReplayService] Saved {} analysis tags for session: {}", detectedTags.size(), sessionUuid);
		}

		// 로그는 여기서 폐기됨 (GC 대상)
		log.debug("[ReplayService] Processed and discarded {} logs for session: {}", logs.size(), sessionUuid);
	}

	/**
	 * Presigned URL 발급 (AWS S3 연동)
	 */
	@Transactional
	public PresignedUrlResponse generatePresignedUrl(String sessionUuid, PresignedUrlRequest request) {
		SurveySession session = surveySessionRepository.findByUuid(UUID.fromString(sessionUuid))
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.SURVEY_SESSION_NOT_FOUND));

		// S3 Key 생성: replays/{sessionUuid}/{sequence}_{timestamp}.webm
		String s3Key = String.format("replays/%s/%d_%d.webm",
			sessionUuid,
			request.sequence(),
			System.currentTimeMillis());

		// VideoSegment 생성 (PENDING 상태)
		VideoSegment segment = VideoSegment.builder()
			.session(session)
			.s3Key(s3Key)
			.sequence(request.sequence())
			.videoStartMs(request.videoStartMs())
			.videoEndMs(request.videoEndMs())
			.build();

		VideoSegment savedSegment = videoSegmentRepository.save(segment);

		// S3 Presigned PUT URL 생성
		String presignedUrl = generateS3PresignedUrl(s3Key, request.contentType());

		log.info("[ReplayService] Generated presigned URL for session: {}, segment: {}, bucket: {}",
			sessionUuid, savedSegment.getUuid(), awsProperties.s3().getReplayBucketName());

		return new PresignedUrlResponse(
			savedSegment.getUuid().toString(),
			presignedUrl,
			PRESIGNED_URL_EXPIRATION_SECONDS);
	}

	/**
	 * 업로드 완료 처리
	 */
	@Transactional
	public void completeUpload(String sessionUuid, UploadCompleteRequest request) {
		UUID segmentUuid = UUID.fromString(request.segmentId());

		VideoSegment segment = videoSegmentRepository.findByUuid(segmentUuid)
			.orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

		segment.markUploaded();

		log.info("[ReplayService] Upload completed for segment: {}", segmentUuid);
	}

	/**
	 * S3 Presigned PUT URL 생성
	 */
	private String generateS3PresignedUrl(String s3Key, String contentType) {
		String bucketName = awsProperties.s3().getReplayBucketName();

		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(bucketName)
			.key(s3Key)
			.contentType(contentType != null ? contentType : "video/webm")
			.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRATION_SECONDS))
			.putObjectRequest(putObjectRequest)
			.build();

		PresignedPutObjectRequest presignedRequest = replayS3Presigner.presignPutObject(presignRequest);

		log.debug("[ReplayService] S3 presigned URL generated: bucket={}, key={}", bucketName, s3Key);

		return presignedRequest.url().toString();
	}
}
