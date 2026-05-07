package com.javatodev.finance.configuration;

import com.javatodev.finance.exception.FeignErrorDecoder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.Retryer;
import feign.codec.ErrorDecoder;

@Configuration
public class CustomFeignClientConfiguration {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    @Bean
    ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

}
