package com.fallguys.userservice.shared.infrastructure.swagger;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Value("${OPENAPI_SERVER_URL:}")
    private String serverUrl;

    @Bean
    OpenAPI userServiceOpenApi() {
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("UserService API")
                        .version("v1")
                        .description("ERP 사용자 관리 서비스 API"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));

        if (serverUrl != null && !serverUrl.isBlank()) {
            openApi.servers(List.of(new Server().url(serverUrl)));
        }

        return openApi;
    }
}
