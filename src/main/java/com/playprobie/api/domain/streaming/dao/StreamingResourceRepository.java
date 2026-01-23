package com.playprobie.api.domain.streaming.dao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.streaming.domain.StreamingResource;

/**
 * StreamingResource Repository.
 */
public interface StreamingResourceRepository extends JpaRepository<StreamingResource, Long> {

	/**
	 * Survey PK로 StreamingResource를 조회합니다.
	 */
	Optional<StreamingResource> findBySurveyId(Long surveyId);

	/**
	 * Survey에 연결된 StreamingResource가 존재하는지 확인합니다.
	 */
	boolean existsBySurveyId(Long surveyId);

	/**
	 * AWS Stream Group ID로 StreamingResource를 조회합니다.
	 */
	Optional<StreamingResource> findByAwsStreamGroupId(String awsStreamGroupId);

	/**
	 * Survey의 UUID로 StreamingResource를 조회합니다.
	 * <p>
	 * JPA 표준 네이밍: Survey 테이블을 JOIN하여 조회합니다.
	 */
	Optional<StreamingResource> findBySurvey_Uuid(UUID surveyUuid);

	/**
	 * StreamingResource 자체의 UUID로 조회합니다.
	 * <p>
	 * 혼동 방지: findBySurvey_Uuid와 구분됩니다.
	 */
	Optional<StreamingResource> findByUuid(UUID resourceUuid);

	/**
	 * ID로 StreamingResource를 조회하면서 Survey, Build, Game을 함께 Fetch Join합니다.
	 * <p>
	 * <b>용도</b>: 비동기 스레드에서 Lazy Loading 예외를 방지합니다.
	 * <p>
	 * <b>Note</b>: GameBuild.getS3Prefix()가 Game.getUuid()를 사용하므로 Game도 포함합니다.
	 */
	@org.springframework.data.jpa.repository.Query("SELECT sr FROM StreamingResource sr " +
		"JOIN FETCH sr.survey s " +
		"JOIN FETCH sr.build b " +
		"JOIN FETCH b.game g " +
		"WHERE sr.id = :resourceId")
	Optional<StreamingResource> findByIdWithAssociations(@org.springframework.data.repository.query.Param("resourceId")
	Long resourceId);
}
