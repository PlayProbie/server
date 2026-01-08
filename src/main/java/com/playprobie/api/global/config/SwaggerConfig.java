package com.playprobie.api.global.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

	private static final String BEARER_AUTH_NAME = "bearerAuth";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.servers(servers())
			.components(components())
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_NAME))
			.info(apiInfo());
	}

	private List<Server> servers() {
		Server devServer = new Server()
			.url("https://dev-api.playprobie.shop")
			.description("Dev Server");

		Server localServer = new Server()
			.url("http://localhost:8080")
			.description("Local Server");

		return List.of(devServer, localServer);
	}

	private Components components() {
		SecurityScheme bearerAuthScheme = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.description("JWT Bearer Token (Login API에서 발급받은 access_token)");

		return new Components()
			.addSecuritySchemes(BEARER_AUTH_NAME, bearerAuthScheme);
	}

	private Info apiInfo() {
		return new Info()
			.title("PlayProbie")
			.version("1.0.0")
			.description("PlayProbie API Documentation");
	}
}
