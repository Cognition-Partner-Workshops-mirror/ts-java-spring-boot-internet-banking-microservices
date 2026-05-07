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
    public OpenAPI coreBankingOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Core Banking Service API")
                .description("System of record for bank accounts, users, transactions, and utility accounts. Provides ledger operations for fund transfers and utility payments.")
                .version("2.0.0")
                .contact(new Contact()
                    .name("JavaToDev")
                    .url("https://javatodev.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8092").description("Local Development"),
                new Server().url("http://core-banking-service:8092").description("Docker Environment")));
    }
}
