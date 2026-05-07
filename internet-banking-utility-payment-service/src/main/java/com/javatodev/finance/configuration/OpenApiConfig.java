package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI utilityPaymentOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Internet Banking Utility Payment Service API")
                .description("Processes utility bill payments (electricity, water, telecom) by coordinating with the core banking service for account debiting.")
                .version("2.0.0")
                .contact(new Contact()
                    .name("JavaToDev")
                    .url("https://javatodev.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8085").description("Local Development"),
                new Server().url("http://internet-banking-utility-payment-service:8085").description("Docker Environment")));
    }
}
