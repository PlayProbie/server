package com.playprobie.api.infra.ai.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.playprobie.api.infra.sse.SseEmitterService;
import com.playprobie.api.infra.ai.AiClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockAiClient implements AiClient {

	private final SseEmitterService sseEmitterService;

	@Async
	public void streamNextQuestion(String sessionId, String userMessage) {
		try {
			// 시나리오: 3개의 토큰을 1초 간격으로 전송
			String[] tokens = {"AI가 ", "생각한", "다음_질문입니다"};

			for (String token : tokens) {
				// AI 생성 지연 시간 시뮬레이션
				Thread.sleep(1000);

				// 클라이언트로 데이터 전송 (이벤트명: question)
				sseEmitterService.send(sessionId, "question", token);
			}

			// 완료 이벤트 전송
			sseEmitterService.send(sessionId, "done", "finished");
			// (선택) 연결을 서버에서 끊고 싶다면: emitter.complete();

		} catch (InterruptedException e) {
			log.error("[MockAiService] Error during streaming: {}", e.getMessage());
			Thread.currentThread().interrupt();
		}
	}
}
