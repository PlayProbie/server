package com.playprobie.api.domain.game.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.BuildStatus;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.dto.CompleteUploadRequest;
import com.playprobie.api.domain.game.dto.CreateGameBuildRequest;
import com.playprobie.api.domain.game.dto.CreateGameBuildResponse;
import com.playprobie.api.domain.game.dto.GameBuildResponse;
import com.playprobie.api.domain.game.exception.GameBuildNotFoundException;
import com.playprobie.api.domain.game.exception.GameNotFoundException;
import com.playprobie.api.domain.game.exception.S3AccessException;
import com.playprobie.api.global.config.properties.AwsProperties;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameBuildService {

	private static final String SESSION_PREFIX_GAME_UPLOAD = "GameUpload-";

	private final GameBuildRepository gameBuildRepository;
	private final GameRepository gameRepository;
	private final StsClient stsClient;
	private final S3Client s3Client;
	private final AwsProperties awsProperties;

	/**
	 * 새 게임 빌드를 생성하고 임시 자격 증명을 발급합니다.
	 */
	@Transactional
	public CreateGameBuildResponse createBuild(UUID gameUuid, CreateGameBuildRequest request) {
		Game game = gameRepository.findByUuid(gameUuid)
			.orElseThrow(GameNotFoundException::new);

		UUID buildUuid = UUID.randomUUID();

		GameBuild gameBuild = GameBuild.builder()
			.game(game)
			.uuid(buildUuid)
			.version(request.version())
			.build();

		gameBuildRepository.save(gameBuild);

		String s3Prefix = gameBuild.getS3Prefix();

		// STS AssumeRole & Policy Injection
		Credentials creds = getTemporaryCredentials(s3Prefix);

		log.info("Game build created and credentials generated: buildId={}, s3Prefix={}", buildUuid, s3Prefix);

		return new CreateGameBuildResponse(
			buildUuid,
			request.version(),
			s3Prefix,
			new CreateGameBuildResponse.AwsCredentials(
				creds.accessKeyId(),
				creds.secretAccessKey(),
				creds.sessionToken(),
				creds.expiration().toEpochMilli()));
	}

	/**
	 * 특정 게임의 모든 빌드를 조회합니다.
	 */
	public List<GameBuildResponse> getBuildsByGameUuid(UUID gameUuid) {
		return gameBuildRepository.findByGameUuidOrderByCreatedAtDesc(gameUuid)
			.stream()
			.map(GameBuildResponse::from)
			.toList();
	}

	/**
	 * 업로드 완료를 확인하고 빌드 상태를 변경합니다.
	 */
	@Transactional
	public GameBuildResponse completeUpload(
		UUID gameUuid, UUID buildId, CompleteUploadRequest request) {

		GameBuild gameBuild = getVerifiedBuild(gameUuid, buildId);

		if (gameBuild.getStatus() == BuildStatus.UPLOADED) {
			return GameBuildResponse.forComplete(gameBuild);
		}

		// Light Verification: 최소 1개 파일 존재 확인
		verifyAtLeastOneFileExists(gameBuild.getS3Prefix());

		// 클라이언트 값 신뢰 (GameLift가 최종 검증)
		gameBuild.markAsUploaded(
			request.expectedFileCount(),
			request.expectedTotalSize(),
			request.osType(),
			request.executablePath());

		log.info("Upload completed: buildId={}, files={}, size={}, osType={}, executablePath={}",
			buildId, request.expectedFileCount(), request.expectedTotalSize(),
			request.osType(), request.executablePath());

		return GameBuildResponse.forComplete(gameBuild);
	}

	/**
	 * 빌드 삭제 - S3 파일 일괄 삭제
	 */
	@Transactional
	public void deleteBuild(UUID gameUuid, UUID buildId) {
		GameBuild gameBuild = getVerifiedBuild(gameUuid, buildId);

		// S3에서 prefix 하위 모든 파일 삭제
		deleteS3Objects(gameBuild.getS3Prefix());

		// Soft Delete 처리 (DB Row 유지)
		gameBuild.markAsDeleted();

		log.info("Build soft-deleted: buildId={}, s3Prefix={}", buildId, gameBuild.getS3Prefix());
	}

	private Credentials getTemporaryCredentials(String s3Prefix) {
		try {
			// [Security] Session Policy: 오직 이 Prefix에만 PutObject 허용
			String policy = buildSessionPolicy(s3Prefix);

			AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
				.roleArn(awsProperties.s3().roleArn())
				.roleSessionName(SESSION_PREFIX_GAME_UPLOAD + UUID.randomUUID())
				.policy(policy)
				.durationSeconds((int)awsProperties.s3().credentialsDuration().toSeconds())
				.build();

			AssumeRoleResponse response = stsClient.assumeRole(assumeRoleRequest);
			return response.credentials();
		} catch (StsException e) {
			log.error("STS AssumeRole failed: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.STS_CLIENT_ERROR);
		} catch (Exception e) {
			log.error("Unexpected error during STS AssumeRole: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.STS_CLIENT_ERROR);
		}
	}

	private String buildSessionPolicy(String s3Prefix) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode policy = mapper.createObjectNode();
			policy.put("Version", "2012-10-17");

			ArrayNode statements = policy.putArray("Statement");
			ObjectNode statement = statements.addObject();
			statement.put("Effect", "Allow");
			statement.put("Action", "s3:PutObject");
			statement.put("Resource", String.format("arn:aws:s3:::%s/%s*",
				awsProperties.s3().bucketName(), s3Prefix));

			return mapper.writeValueAsString(policy);
		} catch (Exception e) {
			log.error("Failed to build session policy: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.STS_CLIENT_ERROR);
		}
	}

	private void deleteS3Objects(String prefix) {
		try {
			String continuationToken = null;
			do {
				ListObjectsV2Request.Builder listReqBuilder = ListObjectsV2Request.builder()
					.bucket(awsProperties.s3().bucketName())
					.prefix(prefix);

				if (continuationToken != null) {
					listReqBuilder.continuationToken(continuationToken);
				}

				ListObjectsV2Response listResponse = s3Client.listObjectsV2(listReqBuilder.build());

				if (listResponse.hasContents()) {
					List<ObjectIdentifier> objectIds = listResponse.contents().stream()
						.map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
						.toList();

					log.info("Found {} objects to delete for prefix: {}", objectIds.size(), prefix);

					DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
						.bucket(awsProperties.s3().bucketName())
						.delete(Delete.builder().objects(objectIds).build())
						.build();

					var deleteResponse = s3Client.deleteObjects(deleteRequest);

					if (deleteResponse.hasErrors()) {
						log.error("Failed to delete some S3 objects. Errors: {}", deleteResponse.errors().stream()
							.map(e -> String.format("[Key: %s, Code: %s, Message: %s]", e.key(), e.code(), e.message()))
							.toList());
					} else {
						log.info("Successfully deleted {} objects", deleteResponse.deleted().size());
					}
				} else {
					log.info("No objects found for prefix: {}", prefix);
				}

				continuationToken = listResponse.isTruncated() ? listResponse.nextContinuationToken() : null;
			} while (continuationToken != null);

		} catch (S3Exception e) {
			log.error("S3 delete failed: {}", e.getMessage(), e);
			throw new S3AccessException();
		}
	}

	private void verifyAtLeastOneFileExists(String prefix) {
		try {
			ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(awsProperties.s3().bucketName())
				.prefix(prefix)
				.maxKeys(1) // 1개만 확인
				.build();

			ListObjectsV2Response response = s3Client.listObjectsV2(request);

			if (!response.hasContents() || response.contents().isEmpty()) {
				log.warn("No files found in S3 prefix: {}", prefix);
				throw new BusinessException(ErrorCode.S3_FILE_NOT_FOUND);
			}

			log.debug("At least one file exists in prefix: {}", prefix);

		} catch (S3Exception e) {
			log.error("S3 list failed: {}", e.getMessage(), e);
			throw new S3AccessException();
		}
	}

	private GameBuild getVerifiedBuild(UUID gameUuid, UUID buildId) {
		GameBuild gameBuild = gameBuildRepository.findByUuid(buildId)
			.orElseThrow(GameBuildNotFoundException::new);

		if (!gameBuild.getGame().getUuid().equals(gameUuid)) {
			throw new GameBuildNotFoundException();
		}

		return gameBuild;
	}

}
