package com.playprobie.api.infra.sse;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class SseEmitterService {

	private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 10;

	private final SseEmitterRepository emitterRepository;

	public SseEmitter connect(String sessionId) {
		log.info("Creating new SseEmitter. SessionId: {}", sessionId);
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

		emitter.onCompletion(() -> {
			log.info("SSE completed. SessionId: {}", sessionId);
			emitterRepository.deleteById(sessionId);
		});
		emitter.onTimeout(() -> {
			log.warn("SSE timed out. SessionId: {}", sessionId);
			emitterRepository.deleteById(sessionId);
		});
		emitter.onError((e) -> {
			log.error("SSE error: {}. SessionId: {}", e.getMessage(), sessionId);
			emitterRepository.deleteById(sessionId);
		});
		emitterRepository.save(sessionId, emitter);
		log.info("SseEmitter saved. SessionId: {}", sessionId);

		send(sessionId, "connect", "connected");
		return emitter;
	}

	public void send(String sessionId, String eventName, Object data) {
		emitterRepository.findById(sessionId).ifPresentOrElse(
				emitter -> {
					try {
						emitter.send(SseEmitter.event()
								.id(sessionId)
								.name(eventName)
								.data(data));
					} catch (IOException | IllegalStateException e) {
						log.warn("Failed to send SSE event. SessionId: {}", sessionId);
						emitterRepository.deleteById(sessionId);
						emitter.completeWithError(e);
					}
				},
				() -> log.warn("No active emitter found. SessionId: {}", sessionId));
	}

	public void complete(String sessionId) {
		emitterRepository.findById(sessionId).ifPresent(SseEmitter::complete);
	}
}
