package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI utilityPaymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Utility Payment Service API")
                        .description("Processes utility bill payments. Orchestrates payments by delegating to the Core Banking Service.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("JavaToDev")
                                .url("https://javatodev.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
