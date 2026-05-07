package com.javatodev.finance.configuration.health;

import com.netflix.discovery.EurekaClient;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(EurekaClient.class)
public class CoreBankingHealthIndicator implements HealthIndicator {

    private final EurekaClient eurekaClient;

    @Override
    public Health health() {
        try {
            var instances = eurekaClient.getInstancesByVipAddress("core-banking-service", false);
            if (instances != null && !instances.isEmpty()) {
                return Health.up()
                        .withDetail("instances", instances.size())
                        .build();
            }
            return Health.down()
                    .withDetail("reason", "No instances registered")
                    .build();
        } catch (Exception e) {
            log.warn("Core banking health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
