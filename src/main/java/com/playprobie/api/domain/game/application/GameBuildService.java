package com.playprobie.api.domain.game.application;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.dao.GameBuildRepository;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.game.dto.CompleteUploadRequest;
import com.playprobie.api.domain.game.dto.CreatePresignedUrlRequest;
import com.playprobie.api.domain.game.dto.CreatePresignedUrlResponse;
import com.playprobie.api.domain.game.dto.GameBuildResponse;
import com.playprobie.api.domain.game.exception.GameBuildNotFoundException;
import com.playprobie.api.domain.game.exception.GameNotFoundException;
import com.playprobie.api.domain.game.exception.S3AccessException;
import com.playprobie.api.global.error.ErrorCode;
import com.playprobie.api.global.error.exception.BusinessException;
import com.playprobie.api.infra.config.AwsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameBuildService {

    private final GameBuildRepository gameBuildRepository;
    private final GameRepository gameRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    @Transactional
    public CreatePresignedUrlResponse createPresignedUrl(UUID gameUuid, CreatePresignedUrlRequest request) {
        // 1. Game 조회 (UUID)
        Game game = gameRepository.findByUuid(gameUuid)
                .orElseThrow(GameNotFoundException::new);

        // 2. Build UUID 생성
        UUID buildUuid = UUID.randomUUID();

        // 3. S3 Key 생성 (gameUuid/buildUuid-filename)
        String s3Key = String.format("%s/%s-%s",
                game.getUuid(),
                buildUuid,
                request.filename());

        // 4. GameBuild 생성
        GameBuild gameBuild = GameBuild.builder()
                .game(game)
                .uuid(buildUuid)
                .originalFilename(request.filename())
                .s3Key(s3Key)
                .fileSize(request.fileSize())
                .build();
        gameBuildRepository.save(gameBuild);

        // 5. Presigned URL 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.getS3().getBucketName())
                .key(s3Key)
                .contentType("application/zip")
                .contentLength(request.fileSize())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        log.info("Presigned URL generated for buildId={}, s3Key={}", gameBuild.getUuid(), s3Key);

        return new CreatePresignedUrlResponse(
                gameBuild.getUuid(),
                presignedRequest.url().toString(),
                s3Key,
                PRESIGNED_URL_EXPIRATION.toSeconds());
    }

    @Transactional
    public GameBuildResponse completeUpload(UUID gameUuid, UUID buildId, CompleteUploadRequest request) {
        // 1. UUID로 GameBuild 조회 및 Game 소속 확인
        GameBuild gameBuild = gameBuildRepository.findByUuid(buildId)
                .orElseThrow(GameBuildNotFoundException::new);

        // 2. GameBuild가 해당 Game에 속하는지 확인
        if (!gameBuild.getGame().getUuid().equals(gameUuid)) {
            throw new GameBuildNotFoundException();
        }

        // 3. S3 Key 일치 확인
        if (!gameBuild.getS3Key().equals(request.s3Key())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. S3 HeadObject로 파일 존재 및 크기 검증
        verifyS3Upload(gameBuild);

        // 5. 상태 변경
        gameBuild.markAsUploaded();

        log.info("Upload completed for buildId={}", gameBuild.getUuid());
        return GameBuildResponse.from(gameBuild);
    }

    private void verifyS3Upload(GameBuild gameBuild) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .key(gameBuild.getS3Key())
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            if (!headResponse.contentLength().equals(gameBuild.getFileSize())) {
                log.warn("File size mismatch: expected={}, actual={}",
                        gameBuild.getFileSize(), headResponse.contentLength());
            }
        } catch (NoSuchKeyException e) {
            log.error("S3 file not found: s3Key={}", gameBuild.getS3Key());
            throw new BusinessException(ErrorCode.S3_FILE_NOT_FOUND);
        } catch (S3Exception e) {
            log.error("S3 access failed: {}", e.getMessage(), e);
            throw new S3AccessException();
        }
    }
}
