package com.playprobie.api.infra.sse.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.playprobie.api.infra.sse.repository.SseEmitterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class SseEmitterService {

	private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 10;

	private final SseEmitterRepository emitterRepository;

	public SseEmitter connect(UUID uuid) {
		String sessionUuid = uuid.toString();
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
		emitterRepository.save(sessionUuid, emitter);

		//TODO: spring 자체 메시지 전송
		send(sessionUuid, "connect", "connected");
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
				() -> log.warn("SSE Emitter를 찾을 수 없습니다. SessionId: {}", sessionId));
	}

	public void complete(String sessionId) {
		emitterRepository.findById(sessionId).ifPresent(SseEmitter::complete);
	}
}
