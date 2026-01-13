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
}
