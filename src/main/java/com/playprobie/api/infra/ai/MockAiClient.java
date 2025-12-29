package com.playprobie.api.infra.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Mock AI 클라이언트
 */
@Component
public class MockAiClient implements AiClient {

    private static final Random RANDOM = new Random();

    private static final String[] QUESTION_TEMPLATES = {
            "%s 게임을 플레이하면서 가장 인상 깊었던 부분은 무엇인가요?",
            "%s의 전반적인 난이도가 적절하다고 생각하시나요?",
            "게임 내 튜토리얼이 충분히 이해하기 쉬웠나요?",
            "UI/UX 측면에서 개선이 필요한 부분이 있다면 무엇인가요?",
            "게임의 그래픽과 비주얼에 대해 어떻게 생각하시나요?",
            "사운드 효과와 배경음악이 게임 분위기와 잘 어울린다고 느끼셨나요?",
            "게임 진행 속도가 적절하다고 생각하시나요?",
            "다른 플레이어에게 이 게임을 추천하시겠습니까? 이유는?",
            "게임에서 가장 재미있었던 기능이나 시스템은 무엇인가요?",
            "게임에서 가장 불편했거나 답답했던 부분이 있다면 무엇인가요?",
            "게임의 스토리/세계관에 몰입이 잘 되었나요?",
            "게임 내 보상 시스템이 만족스러웠나요?",
            "게임을 플레이하는 동안 버그나 오류를 경험하셨나요?",
            "게임의 밸런스(캐릭터, 아이템 등)가 적절하다고 느끼셨나요?",
            "게임을 처음 시작했을 때 진입 장벽이 있었나요?"
    };

    private static final String[] FEEDBACK_TEMPLATES = {
            "좋은 질문입니다. 더 구체적인 상황을 물어보면 좋겠습니다.",
            "질문이 명확합니다. 개방형 질문으로 변경하면 더 다양한 답변을 얻을 수 있습니다.",
            "핵심을 잘 짚었습니다. 부정적인 뉘앙스를 줄이면 더 솔직한 피드백을 받을 수 있습니다.",
            "질문 의도가 분명합니다. 구체적인 예시를 추가하면 응답자가 답변하기 수월합니다.",
            "좋은 접근입니다. 5점 척도 질문과 함께 사용하면 정량/정성 데이터를 모두 얻을 수 있습니다."
    };

    @Override
    public List<String> generateQuestions(String gameName, String gameGenre, String gameContext, String testPurpose,
            int count) {
        List<String> questions = new ArrayList<>();
        List<Integer> usedIndices = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int index;
            do {
                index = RANDOM.nextInt(QUESTION_TEMPLATES.length);
            } while (usedIndices.contains(index) && usedIndices.size() < QUESTION_TEMPLATES.length);

            usedIndices.add(index);
            String template = QUESTION_TEMPLATES[index];
            String question = template.contains("%s") ? String.format(template, gameName) : template;
            questions.add(question);
        }

        return questions;
    }

    @Override
    public QuestionReview reviewQuestion(String questionContent) {
        String feedback = FEEDBACK_TEMPLATES[RANDOM.nextInt(FEEDBACK_TEMPLATES.length)];

        List<String> alternatives = new ArrayList<>();
        alternatives.add("대안 1: " + questionContent + " (더 구체적으로)");
        alternatives.add("대안 2: " + questionContent + " (긍정적 표현으로)");
        alternatives.add("대안 3: " + questionContent + " (예시 포함)");

        return new QuestionReview(feedback, alternatives);
    }
}
