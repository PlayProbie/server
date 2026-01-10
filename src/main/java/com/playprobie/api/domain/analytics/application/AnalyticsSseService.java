package com.playprobie.api.domain.analytics.application;

import com.playprobie.api.infra.sse.repository.AnalyticsSseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsSseService {

	private final AnalyticsSseRepository analyticsSseRepository;
	private static final Long DEFAULT_TIMEOUT = 60000L; // 60초

	public SseEmitter subscribe(UUID surveyUuid) {
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
		analyticsSseRepository.add(surveyUuid, emitter);

		emitter.onCompletion(() -> analyticsSseRepository.remove(surveyUuid, emitter));
		emitter.onTimeout(() -> {
			log.debug("SSE connection timed out for survey: {}", surveyUuid);
			analyticsSseRepository.remove(surveyUuid, emitter);
		});
		emitter.onError((e) -> {
			log.debug("SSE connection error for survey: {}, error: {}", surveyUuid, e.getMessage());
			analyticsSseRepository.remove(surveyUuid, emitter);
		});

		// 초기 연결 성공 이벤트
		try {
			emitter.send(SseEmitter.event().name("connect").data("connected"));
		} catch (IOException e) {
			emitter.completeWithError(e);
		}

		return emitter;
	}

	public void notifyUpdate(UUID surveyUuid) {
		List<SseEmitter> emitters = analyticsSseRepository.findAllBySurveyUuid(surveyUuid);
		if (emitters.isEmpty())
			return;

		log.info("📢 Broadcasting 'update' event to {} clients for survey {}", emitters.size(), surveyUuid);

		emitters.forEach(emitter -> {
			try {
				emitter.send(SseEmitter.event().name("refresh").data("UPDATE"));
			} catch (Exception e) {
				analyticsSseRepository.remove(surveyUuid, emitter);
			}
		});
	}

	@Scheduled(fixedRate = 30000) // 30초마다 Heartbeat 전송
	public void sendHeartbeat() {
		analyticsSseRepository.forEach((uuid, emitter) -> {
			try {
				emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
			} catch (Exception e) {
				analyticsSseRepository.remove(uuid, emitter);
			}
		});
	}
}
