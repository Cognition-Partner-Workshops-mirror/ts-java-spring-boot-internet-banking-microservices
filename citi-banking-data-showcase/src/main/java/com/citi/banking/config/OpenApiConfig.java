package com.citi.banking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI citiBankingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Citi Banking Data Showcase API")
                        .description("REST API showcasing retail and commercial banking data — " +
                                "customers, accounts, transactions, credit cards, loans, and branches. " +
                                "Built with Spring Boot 3.2 and backed by an H2 in-memory database " +
                                "pre-loaded with realistic sample data.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Citi Banking Engineering")
                                .email("banking-api@citi.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
