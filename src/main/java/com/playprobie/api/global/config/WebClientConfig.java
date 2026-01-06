package com.playprobie.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
public class WebClientConfig {

	@Value("${ai.server.url}")
	private String aiServerURl;

	@Bean
	public WebClient aiWebClient() {
		HttpClient httpClient = HttpClient.create()
				.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30초 연결 타임아웃
				.responseTimeout(java.time.Duration.ofSeconds(300)); // 5분 응답 타임아웃

		return WebClient.builder()
				.baseUrl(aiServerURl)
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.filter(logRequest())
				.filter(logResponse())
				.build();
	}

	private ExchangeFilterFunction logRequest() {
		return ExchangeFilterFunction.ofRequestProcessor(request -> {
			log.info("🌐 WebClient Request: {} {}", request.method(), request.url());
			return Mono.just(request);
		});
	}

	private ExchangeFilterFunction logResponse() {
		return ExchangeFilterFunction.ofResponseProcessor(response -> {
			log.info("🌐 WebClient Response: status={}", response.statusCode());
			return Mono.just(response);
		});
	}
}
