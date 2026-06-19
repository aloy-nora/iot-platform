package com.example.iotplatform.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源：
 * - MySQL    → 设备台账(device 表)
 * - TDengine → 时序数据(env_sensor 超级表)
 * 各自独立一个 JdbcTemplate，靠 @Qualifier 区分注入。
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties("datasource.tdengine")
    public DataSource tdengineDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    public JdbcTemplate tdengineJdbcTemplate(@Qualifier("tdengineDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
