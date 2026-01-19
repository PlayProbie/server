package com.playprobie.api.infra.sse.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.playprobie.api.global.config.properties.AiProperties;
import com.playprobie.api.infra.sse.repository.SseEmitterRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class SseEmitterService {

	private static final String EVENT_CONNECT = "connect";
	private static final String DATA_CONNECTED = "connected";

	private final SseEmitterRepository emitterRepository;
	private final AiProperties aiProperties;

	public SseEmitter connect(UUID uuid) {
		String sessionUuid = uuid.toString();
		SseEmitter emitter = new SseEmitter(aiProperties.sse().timeout().toMillis());
		emitterRepository.save(sessionUuid, emitter);

		send(sessionUuid, EVENT_CONNECT, DATA_CONNECTED);
		return emitter;
	}

	public boolean send(String sessionId, String eventName, Object data) {
		return emitterRepository.findById(sessionId).map(emitter -> {
			try {
				emitter.send(SseEmitter.event()
					.name(eventName)
					.data(data));
				return true;
			} catch (IOException | IllegalStateException e) {
				log.warn("Failed to send SSE event. SessionId: {}", sessionId);
				emitterRepository.deleteById(sessionId);
				emitter.completeWithError(e);
				return false;
			}
		}).orElseGet(() -> {
			log.warn("SSE Emitter를 찾을 수 없습니다. SessionId: {}", sessionId);
			return false;
		});
	}

	public void complete(String sessionId) {
		emitterRepository.findById(sessionId).ifPresent(SseEmitter::complete);
	}
}
