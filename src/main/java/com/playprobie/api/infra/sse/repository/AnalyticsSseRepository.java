package com.playprobie.api.infra.sse.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

@Slf4j
@Repository
public class AnalyticsSseRepository {

	private final Map<UUID, List<SseEmitter>> surveyEmitters = new ConcurrentHashMap<>();

	public void add(UUID surveyUuid, SseEmitter emitter) {
		List<SseEmitter> emitters = surveyEmitters.computeIfAbsent(surveyUuid, k -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);
		log.info("SSE Emitter Added. Survey: {}, Total Clients: {}", surveyUuid, emitters.size());
	}

	public List<SseEmitter> findAllBySurveyUuid(UUID surveyUuid) {
		return surveyEmitters.getOrDefault(surveyUuid, List.of());
	}

	public void remove(UUID surveyUuid, SseEmitter emitter) {
		List<SseEmitter> emitters = surveyEmitters.get(surveyUuid);
		if (emitters != null) {
			emitters.remove(emitter);
			if (emitters.isEmpty()) {
				surveyEmitters.remove(surveyUuid);
				log.info("All emitters removed for survey: {}", surveyUuid);
			}
		}
	}

	// Heartbeat 전송 등을 위한 전체 순회 메서드
	public void forEach(BiConsumer<UUID, SseEmitter> action) {
		surveyEmitters.forEach((uuid, emitters) -> {
			emitters.forEach(emitter -> action.accept(uuid, emitter));
		});
	}
}
