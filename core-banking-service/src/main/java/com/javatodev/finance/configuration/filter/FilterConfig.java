package com.javatodev.finance.configuration.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<AppAuthUserFilter> authUserFilter() {
        FilterRegistrationBean<AppAuthUserFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new AppAuthUserFilter());
        registrationBean.addUrlPatterns("/api/*");
        return registrationBean;
    }
}
