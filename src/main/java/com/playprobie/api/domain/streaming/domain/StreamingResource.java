package com.playprobie.api.domain.streaming.domain;

import java.util.Objects;

import com.playprobie.api.domain.game.domain.GameBuild;
import com.playprobie.api.domain.survey.domain.Survey;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JIT Streaming Resource Entity.
 * 
 * <p>
 * 설문에 연결된 AWS GameLift Streams 리소스를 관리합니다.
 * Survey와 1:1 관계, GameBuild와 N:1 관계를 가집니다.
 */
@Entity
@Table(name = "streaming_resource")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamingResource extends BaseTimeEntity {

    @Column(name = "resource_uuid", nullable = false, unique = true)
    private java.util.UUID uuid = java.util.UUID.randomUUID();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resource_id")
    private Long id;

    @Column(name = "aws_application_id")
    private String awsApplicationId;

    @Column(name = "aws_stream_group_id")
    private String awsStreamGroupId;

    @Column(name = "instance_type", nullable = false, length = 50)
    private String instanceType;

    @Column(name = "os_type", nullable = false, length = 20)
    private String osType;

    @Column(name = "current_capacity")
    private Integer currentCapacity = 0;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StreamingResourceStatus status = StreamingResourceStatus.PENDING;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false, unique = true)
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id", nullable = false)
    private GameBuild build;

    @Builder
    public StreamingResource(Survey survey, GameBuild build, String instanceType, Integer maxCapacity) {
        this.survey = Objects.requireNonNull(survey, "StreamingResource 생성 시 Survey는 필수입니다");
        this.build = Objects.requireNonNull(build, "StreamingResource 생성 시 GameBuild는 필수입니다");
        this.instanceType = Objects.requireNonNull(instanceType, "StreamingResource 생성 시 instanceType은 필수입니다");
        this.maxCapacity = Objects.requireNonNull(maxCapacity, "StreamingResource 생성 시 maxCapacity는 필수입니다");
        this.osType = build.getOsType();
        this.status = StreamingResourceStatus.PENDING;
        this.currentCapacity = 0;
    }

    // ========== 상태 전이 메서드 ==========

    /**
     * AWS Application 생성 완료 시 호출합니다.
     */
    public void assignApplication(String awsApplicationId) {
        this.awsApplicationId = awsApplicationId;
        this.status = StreamingResourceStatus.PROVISIONING;
    }

    /**
     * AWS StreamGroup 생성 및 연결 완료 시 호출합니다.
     */
    public void assignStreamGroup(String awsStreamGroupId) {
        this.awsStreamGroupId = awsStreamGroupId;
        this.status = StreamingResourceStatus.READY;
    }

    /**
     * 관리자 테스트 시작 시 호출합니다.
     */
    public void startTest() {
        if (!this.status.canScale()) {
            throw new IllegalStateException("테스트를 시작할 수 없는 상태입니다: " + this.status);
        }
        this.status = StreamingResourceStatus.TESTING;
        this.currentCapacity = 1;
    }

    /**
     * 관리자 테스트 종료 시 호출합니다.
     */
    public void stopTest() {
        if (this.status != StreamingResourceStatus.TESTING) {
            throw new IllegalStateException("테스트 중이 아닙니다: " + this.status);
        }
        this.status = StreamingResourceStatus.READY;
        this.currentCapacity = 0;
    }

    /**
     * 서비스 오픈(Scale Out) 시 호출합니다.
     */
    public void activate(int targetCapacity) {
        if (!this.status.canScale()) {
            throw new IllegalStateException("활성화할 수 없는 상태입니다: " + this.status);
        }
        this.status = StreamingResourceStatus.SCALING;
        this.currentCapacity = targetCapacity;
    }

    /**
     * 스케일링 완료 시 호출합니다.
     */
    public void markActive() {
        this.status = StreamingResourceStatus.ACTIVE;
    }

    /**
     * 리소스 정리 시작 시 호출합니다.
     */
    public void startCleanup() {
        this.status = StreamingResourceStatus.CLEANING;
    }

    /**
     * 리소스 삭제 완료 시 호출합니다.
     */
    public void terminate() {
        this.status = StreamingResourceStatus.TERMINATED;
        this.currentCapacity = 0;
    }

    /**
     * Capacity를 업데이트합니다.
     */
    public void updateCapacity(int capacity) {
        this.currentCapacity = capacity;
    }
}
