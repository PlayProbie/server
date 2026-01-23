package com.playprobie.api.infra.sse.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class SseEmitterRepository {

	private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter save(String sessionId, SseEmitter emitter) {
		emitters.put(sessionId, emitter);
		log.info("SSE Emitter Saved. SessionId: {}", sessionId);

		emitter.onCompletion(() -> {
			log.info("SSE Connection Completed. Removing SessionId: {}", sessionId);
			this.delete(sessionId, emitter);
		});

		emitter.onTimeout(() -> {
			log.warn("SSE Connection Timed Out. Removing SessionId: {}", sessionId);
			emitter.complete();
			this.delete(sessionId, emitter);
		});

		emitter.onError((e) -> {
			log.error("SSE Connection Error. SessionId: {}", sessionId, e);
			emitter.complete();
			this.delete(sessionId, emitter);
		});

		return emitter;
	}

	public Optional<SseEmitter> findById(String sessionId) {
		return Optional.ofNullable(emitters.get(sessionId));
	}

	public void deleteById(String sessionId) {
		emitters.remove(sessionId);
	}

	public void delete(String sessionId, SseEmitter emitter) {
		if (emitters.remove(sessionId, emitter)) {
			log.info("SSE Emitter safely removed. SessionId: {}", sessionId);
		} else {
			log.debug("SSE Emitter removal skipped (already replaced or removed). SessionId: {}", sessionId);
		}
	}
}
