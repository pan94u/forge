package com.forge.user.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DatabaseConfig {

    @Value("\${spring.datasource.url}")
    private lateinit var datasourceUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var datasourceUsername: String

    @Value("\${spring.datasource.password}")
    private lateinit var datasourcePassword: String

    @Bean
    @Primary
    fun dataSource(): DataSource {
        return HikariDataSource().apply {
            jdbcUrl = datasourceUrl
            username = datasourceUsername
            password = datasourcePassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 20
            minimumIdle = 5
            idleTimeout = 300000
            connectionTimeout = 20000
            maxLifetime = 1200000
        }
    }
}