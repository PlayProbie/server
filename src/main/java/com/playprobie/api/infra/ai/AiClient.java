package com.playprobie.api.infra.ai;

public interface AiClient {
	void streamNextQuestion(String sessionId, String userAnswer);
}
