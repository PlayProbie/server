package com.playprobie.api.domain.survey.domain;

import java.util.Objects;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 임시 질문 엔티티
 * - AI가 생성한 질문을 임시 저장
 * - 사용자가 수정 가능
 * - 확정 시 FixedQuestion으로 복사 후 삭제
 */
@Entity
@Table(name = "draft_question")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DraftQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_q_id")
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "q_content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "q_order")
    private Integer order;

    @Builder
    public DraftQuestion(Long surveyId, String content, Integer order) {
        this.surveyId = Objects.requireNonNull(surveyId, "DraftQuestion 생성 시 surveyId는 필수입니다");
        this.content = content;
        this.order = order;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}
