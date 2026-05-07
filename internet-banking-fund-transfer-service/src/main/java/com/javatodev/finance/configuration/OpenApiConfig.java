package com.javatodev.finance.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fundTransferServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Internet Banking Fund Transfer Service API")
                        .description("Fund transfer processing between bank accounts")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("JavaToDev")
                                .url("https://javatodev.com")));
    }
}
