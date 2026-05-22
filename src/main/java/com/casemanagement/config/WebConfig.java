package com.casemanagement.config;

import com.casemanagement.interceptor.ApiRequestLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the Case Management API.
 * Registers the API request logging interceptor to capture all API requests.
 * Enables async processing for non-blocking log persistence.
 *
 * The interceptor is configured to capture requests to /api/** endpoints only,
 * excluding actuator and H2 console endpoints to avoid noise in reports.
 */
@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer {

    private final ApiRequestLoggingInterceptor loggingInterceptor;

    @Autowired
    public WebConfig(ApiRequestLoggingInterceptor loggingInterceptor) {
        this.loggingInterceptor = loggingInterceptor;
    }

    /**
     * Register the API request logging interceptor.
     * Only intercepts /api/** paths to keep reporting data clean.
     * Excludes actuator endpoints and H2 console.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/actuator/**",
                        "/h2-console/**"
                );
    }
}
