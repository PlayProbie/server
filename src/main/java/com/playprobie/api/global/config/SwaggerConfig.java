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

	private static final String COOKIE_AUTH_NAME = "cookieAuth";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.servers(servers())
				.components(components())
				.addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_NAME))
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
		SecurityScheme cookieAuthScheme = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.COOKIE)
				.name("access_token")
				.description("Access token stored in cookie");

		return new Components()
				.addSecuritySchemes(COOKIE_AUTH_NAME, cookieAuthScheme);
	}

	private Info apiInfo() {
		return new Info()
				.title("PlayProbie")
				.version("1.0.0")
				.description("PlayProbie API Documentation");
	}
}
