package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coreBankingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Core Banking Service API")
                        .description("Core banking operations including user management, account management, fund transfers, and utility payments")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("JavaToDev")
                                .url("https://javatodev.com")));
    }
}
