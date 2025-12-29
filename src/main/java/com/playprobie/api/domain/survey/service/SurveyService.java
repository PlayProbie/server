package com.playprobie.api.domain.survey.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.playprobie.api.domain.game.domain.Game;
import com.playprobie.api.domain.game.service.GameService;
import com.playprobie.api.domain.survey.domain.Survey;
import com.playprobie.api.domain.survey.domain.TestPurpose;
import com.playprobie.api.domain.survey.dto.CreateSurveyRequest;
import com.playprobie.api.domain.survey.dto.SurveyResponse;
import com.playprobie.api.domain.survey.repository.SurveyRepository;
import com.playprobie.api.global.error.exception.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

	private final SurveyRepository surveyRepository;
	private final GameService gameService;

	@Transactional
	public SurveyResponse createSurvey(CreateSurveyRequest request) {
		Game game = gameService.getGameEntity(request.gameId());
		TestPurpose testPurpose = parseTestPurpose(request.testPurpose());

		Survey survey = Survey.builder()
			.game(game)
			.name(request.surveyName())
			.testPurpose(testPurpose)
			.startAt(request.startedAt())
			.endAt(request.endedAt())
			.build();

		Survey savedSurvey = surveyRepository.save(survey);
		return SurveyResponse.from(savedSurvey);
	}

	public SurveyResponse getSurvey(Long surveyId) {
		Survey survey = surveyRepository.findById(surveyId)
			.orElseThrow(EntityNotFoundException::new);
		return SurveyResponse.from(survey);
	}

	public Survey getSurveyEntity(Long surveyId) {
		return surveyRepository.findById(surveyId)
			.orElseThrow(EntityNotFoundException::new);
	}

	private TestPurpose parseTestPurpose(String code) {
		for (TestPurpose tp : TestPurpose.values()) {
			if (tp.getCode().equals(code)) {
				return tp;
			}
		}
		throw new IllegalArgumentException("유효하지 않은 테스트 목적 코드: " + code);
	}
}
