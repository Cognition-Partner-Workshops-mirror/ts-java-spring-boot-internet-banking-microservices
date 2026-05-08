package com.banking.oracle.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Configuration class for Oracle DataSource with HikariCP connection pooling.
 * Reads connection properties from application.properties and creates
 * a HikariCP-backed DataSource optimized for Oracle Database.
 */
@Configuration
public class OracleDataSourceConfig {

    /**
     * Loads DataSource properties (url, username, password, driver-class-name)
     * from the spring.datasource.* prefix in application.properties.
     *
     * @return DataSourceProperties populated from configuration
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Creates a HikariCP DataSource configured for Oracle Database connectivity.
     * HikariCP is the default connection pool in Spring Boot and provides
     * high-performance JDBC connection pooling.
     *
     * @param properties the DataSource properties loaded from configuration
     * @return a HikariDataSource configured with Oracle connection details
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        /* Build HikariDataSource using the configured Oracle connection properties */
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
