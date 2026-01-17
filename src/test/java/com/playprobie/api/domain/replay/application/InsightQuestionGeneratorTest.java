package com.playprobie.api.domain.replay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.playprobie.api.domain.interview.domain.SurveySession;
import com.playprobie.api.domain.replay.domain.AnalysisTag;
import com.playprobie.api.domain.replay.domain.InsightType;
import com.playprobie.api.domain.replay.dto.InsightQuestionPayload;

/**
 * InsightQuestionGenerator 단위 테스트
 * 랜덤 선택 및 템플릿 기반 질문 생성 검증
 */
class InsightQuestionGeneratorTest {

	private InsightQuestionGenerator generator;
	private SurveySession mockSession;

	@BeforeEach
	void setUp() {
		generator = new InsightQuestionGenerator();
		mockSession = mock(SurveySession.class);
		when(mockSession.getId()).thenReturn(1L);
	}

	@Nested
	@DisplayName("랜덤 선택 테스트")
	class RandomSelection {

		@Test
		@DisplayName("3개 이상 태그 중 최대 2개 선택")
		void selectMax2_whenMoreThan2Tags() {
			// given
			List<AnalysisTag> tags = createTags(5);

			// when
			List<AnalysisTag> selected = generator.selectRandomInsights(tags);

			// then
			assertThat(selected).hasSize(2);
		}

		@Test
		@DisplayName("2개 이하 태그는 전체 반환")
		void returnAll_whenLessThan3Tags() {
			// given
			List<AnalysisTag> tags = createTags(2);

			// when
			List<AnalysisTag> selected = generator.selectRandomInsights(tags);

			// then
			assertThat(selected).hasSize(2);
		}

		@Test
		@DisplayName("1개 태그는 그대로 반환")
		void returnSingle_whenOnlyOneTag() {
			// given
			List<AnalysisTag> tags = createTags(1);

			// when
			List<AnalysisTag> selected = generator.selectRandomInsights(tags);

			// then
			assertThat(selected).hasSize(1);
		}

		@Test
		@DisplayName("빈 리스트 처리")
		void returnEmpty_whenNoTags() {
			// when
			List<AnalysisTag> selected = generator.selectRandomInsights(List.of());

			// then
			assertThat(selected).isEmpty();
		}

		@Test
		@DisplayName("null 입력 처리")
		void returnEmpty_whenNullInput() {
			// when
			List<AnalysisTag> selected = generator.selectRandomInsights(null);

			// then
			assertThat(selected).isEmpty();
		}
	}

	@Nested
	@DisplayName("질문 생성 테스트")
	class QuestionGeneration {

		@Test
		@DisplayName("PANIC 타입 질문 템플릿 적용")
		void generatePanicQuestion() {
			// given
			AnalysisTag panicTag = createTagWithType(InsightType.PANIC, 45000L, 3000);

			// when
			InsightQuestionPayload payload = generator.generate(panicTag, 1, 1);

			// then
			assertThat(payload.insightType()).isEqualTo(InsightType.PANIC);
			assertThat(payload.questionText()).contains("45초~48초");
			assertThat(payload.questionText()).contains("버튼을 빠르게 여러 번");
			assertThat(payload.videoStartMs()).isEqualTo(45000L);
			assertThat(payload.videoEndMs()).isEqualTo(48000L);
		}

		@Test
		@DisplayName("IDLE 타입 질문 템플릿 적용")
		void generateIdleQuestion() {
			// given
			AnalysisTag idleTag = createTagWithType(InsightType.IDLE, 60000L, 15000);

			// when
			InsightQuestionPayload payload = generator.generate(idleTag, 1, 0);

			// then
			assertThat(payload.insightType()).isEqualTo(InsightType.IDLE);
			assertThat(payload.questionText()).contains("60초~75초");
			assertThat(payload.questionText()).contains("잠시 멈추셨는데");
		}

		@Test
		@DisplayName("turnNum과 remaining 정확히 반영")
		void verifyTurnNumAndRemaining() {
			// given
			AnalysisTag tag = createTagWithType(InsightType.PANIC, 10000L, 3000);

			// when
			InsightQuestionPayload payload = generator.generate(tag, 2, 3);

			// then
			assertThat(payload.turnNum()).isEqualTo(2);
			assertThat(payload.remainingInsights()).isEqualTo(3);
		}

		@Test
		@DisplayName("durationMs가 null일 때 기본값 3초 적용")
		void useDefaultDuration_whenNull() {
			// given
			AnalysisTag tag = createTagWithType(InsightType.PANIC, 30000L, null);

			// when
			InsightQuestionPayload payload = generator.generate(tag, 1, 0);

			// then
			// videoEndMs = 30000 + 3000 (기본값) = 33000
			assertThat(payload.videoEndMs()).isEqualTo(33000L);
			assertThat(payload.questionText()).contains("30초~33초");
		}
	}

	// Helper methods
	private List<AnalysisTag> createTags(int count) {
		List<AnalysisTag> tags = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			tags.add(createTagWithType(InsightType.PANIC, i * 10000L, 3000));
		}
		return tags;
	}

	private AnalysisTag createTagWithType(InsightType type, Long videoTimeMs, Integer durationMs) {
		return AnalysisTag.builder()
			.session(mockSession)
			.insightType(type)
			.videoTimeMs(videoTimeMs)
			.durationMs(durationMs)
			.build();
	}
}
