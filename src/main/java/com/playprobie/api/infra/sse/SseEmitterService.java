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
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

		emitter.onCompletion(() -> emitterRepository.deleteById(sessionId));
		emitter.onTimeout(() -> emitterRepository.deleteById(sessionId));
		emitter.onError((e) -> emitterRepository.deleteById(sessionId));
		emitterRepository.save(sessionId, emitter);

		send(sessionId, "connect", "connected! [session=" + sessionId + "]");
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
			() -> log.warn("No active emitter found for SessionId: {}", sessionId)
		);
	}

	// [추가] 스트림 종료 알림용 메서드
	public void complete(String sessionId) {
		emitterRepository.findById(sessionId).ifPresent(SseEmitter::complete);
	}
}
