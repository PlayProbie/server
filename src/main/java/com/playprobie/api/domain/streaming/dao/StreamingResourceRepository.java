package com.playprobie.api.domain.streaming.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playprobie.api.domain.streaming.domain.StreamingResource;

/**
 * StreamingResource Repository.
 */
public interface StreamingResourceRepository extends JpaRepository<StreamingResource, Long> {

    /**
     * Survey ID로 StreamingResource를 조회합니다.
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

    Optional<StreamingResource> findBySurveyUuid(java.util.UUID surveyUuid);

    Optional<StreamingResource> findByUuid(java.util.UUID uuid);
}
