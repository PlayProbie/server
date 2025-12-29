package com.playprobie.api.infra.sse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class SseEmitterRepository {

	//Thread-Safe
	private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter save(String sessionId, SseEmitter emitter) {
		emitters.put(sessionId, emitter);
		return emitter;
	}

	public Optional<SseEmitter> findById(String sessionId) {
		return Optional.ofNullable(emitters.get(sessionId));
	}

	public void deleteById(String sessionId) {
		emitters.remove(sessionId);
	}

}
