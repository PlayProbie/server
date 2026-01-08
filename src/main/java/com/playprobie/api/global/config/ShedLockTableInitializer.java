package com.playprobie.api.global.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ShedLock 테이블 초기화.
 *
 * <p>
 * ddl-auto: create 환경에서 서버 시작 시 shedlock 테이블을 보장하기 위해 사용됩니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShedLockTableInitializer implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public void run(ApplicationArguments args) {
		log.info("Checking ShedLock table...");
		try {
			jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS shedlock (
					name VARCHAR(64) NOT NULL,
					lock_until TIMESTAMP(3) NOT NULL,
					locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
					locked_by VARCHAR(255) NOT NULL,
					PRIMARY KEY (name)
				)
				""");
			log.info("ShedLock table initialized successfully.");
		} catch (Exception e) {
			log.error("Failed to initialize ShedLock table: {}", e.getMessage());
			// 테이블이 이미 존재하거나 권한 문제 등일 수 있음. 치명적이지 않으므로 로깅만 수행.
		}
	}
}
