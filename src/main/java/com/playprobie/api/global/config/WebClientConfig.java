package com.playprobie.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.playprobie.api.global.config.properties.AiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

	private final AiProperties aiProperties;

	@Bean
	public WebClient aiWebClient() {
		HttpClient httpClient = HttpClient.create()
			.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
				(int)aiProperties.client().connectTimeout().toMillis())
			.responseTimeout(aiProperties.client().readTimeout());

		return WebClient.builder()
			.baseUrl(aiProperties.server().url())
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
