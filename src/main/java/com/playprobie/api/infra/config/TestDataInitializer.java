package com.playprobie.api.infra.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.domain.GameGenre;
import com.playprobie.api.domain.game.dao.GameRepository;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dao.SurveyRepository;

import lombok.RequiredArgsConstructor;

/**
 * 테스트용 초기 데이터 설정
 * - local 프로파일에서만 실행
 */
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class TestDataInitializer {

    private final GameRepository gameRepository;
    private final SurveyRepository surveyRepository;

    @Bean
    CommandLineRunner initTestData() {
        return args -> {
            // 이미 데이터가 있으면 스킵
            if (gameRepository.count() > 0) {
                return;
            }

            // 게임 생성
            Game game = Game.builder()
                    .name("테스트 게임")
                    .genres(List.of(GameGenre.SHOOTER, GameGenre.CASUAL))
                    .context("테스트용 게임입니다. 이 게임은 슈터와 캐주얼 장르가 결합된 형태로, 누구나 쉽게 즐길 수 있습니다.")
                    .build();
            Game savedGame = gameRepository.save(game);

            // 설문 생성
            Survey survey = Survey.builder()
                    .game(savedGame)
                    .name("UI/UX 테스트")
                    .testPurpose(TestPurpose.UI_UX_FEEDBACK)
                    .build();
            surveyRepository.save(survey);

            System.out.println("=== 테스트 데이터 초기화 완료 ===");
            System.out.println("Game ID: " + savedGame.getId());
            System.out.println("Survey ID: " + survey.getId());
        };
    }
}
