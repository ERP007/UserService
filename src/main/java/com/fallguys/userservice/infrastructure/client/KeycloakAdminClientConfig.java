package com.fallguys.userservice.infrastructure.client;

import jakarta.ws.rs.client.Client;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KeycloakAdminClientConfig {

    private final KeycloakAdminProperties properties;

    @Bean(destroyMethod = "close")
    public Keycloak keycloak() {
        Client resteasyClient = createResteasyClient();

        return KeycloakBuilder.builder()
                .serverUrl(properties.serverUrl())
                .realm(properties.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .resteasyClient(resteasyClient)
                .build();
    }

    private Client createResteasyClient() {
        return ResteasyClientBuilder.newBuilder()
                .connectTimeout(properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }
}
