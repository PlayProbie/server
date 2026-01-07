package com.playprobie.api.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

	private String secret;
	private long accessTokenExpiration; // milliseconds

	/**
	 * Access Token 만료 시간 (초 단위)
	 */
	public long getAccessTokenExpirationSeconds() {
		return accessTokenExpiration / 1000;
	}
}
