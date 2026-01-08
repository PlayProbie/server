package com.playprobie.api.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 처리를 위한 ThreadPool 설정.
 *
 * <p>
 * Bounded Queue를 사용하여 배압(Backpressure)을 형성하고,
 * 큐가 가득 찼을 때 TaskRejectedException을 발생시켜 Fail-Fast 전략을 지원합니다.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(10);
		executor.setMaxPoolSize(20);
		executor.setQueueCapacity(50); // Bounded Queue for Safety
		executor.setThreadNamePrefix("Async-Probie-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		log.info("Initialized Async TaskExecutor with QueueCapacity=50");
		return executor;
	}
}
