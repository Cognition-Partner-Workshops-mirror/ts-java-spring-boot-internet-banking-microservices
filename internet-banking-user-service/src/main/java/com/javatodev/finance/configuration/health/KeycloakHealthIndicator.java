package com.javatodev.finance.configuration.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KeycloakHealthIndicator implements HealthIndicator {

    @Value("${app.config.keycloak.server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Health health() {
        try {
            String url = keycloakServerUrl + "/realms/master";
            restTemplate.getForEntity(url, String.class);
            return Health.up()
                    .withDetail("url", keycloakServerUrl)
                    .build();
        } catch (Exception e) {
            log.warn("Keycloak health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", keycloakServerUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
