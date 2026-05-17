package com.qesuite.accounting.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.sql.DriverManager

@Configuration
class FlywayDatabaseCreationConfig {

    private val log = org.slf4j.LoggerFactory.getLogger(FlywayDatabaseCreationConfig::class.java)

    @Bean
    fun flywayMigrationStrategy(env: Environment): FlywayMigrationStrategy {
        return FlywayMigrationStrategy { flyway ->
            // §18 — Auto-bootstrap logic (Lazy resolution to prevent early bean initialization)
            val datasourceUrl = env.getProperty("spring.datasource.url")
                ?: throw IllegalStateException("spring.datasource.url is missing")
            val username = env.getProperty("spring.datasource.username")
                ?: "postgres"
            val password = env.getProperty("spring.datasource.password") ?: ""

            ensureDatabaseExists(datasourceUrl, username, password)
            flyway.migrate()
        }
    }

    private fun ensureDatabaseExists(datasourceUrl: String, username: String, password: String) {
        // Strip query parameters to get the raw database name
        val rawDbName = datasourceUrl.substringAfterLast("/").substringBefore("?").trim()

        // Validate: must start with a letter and contain only alphanumeric + underscores
        require(rawDbName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*"))) {
            "Database name '$rawDbName' contains invalid characters. Only alphanumeric and underscores allowed."
        }
        val dbName = rawDbName

        val baseUrl = datasourceUrl.substringBeforeLast("/") + "/postgres"

        try {
            DriverManager.getConnection(baseUrl, username, password).use { conn ->
                val resultSet = conn.metaData.getCatalogs()
                var exists = false
                while (resultSet.next()) {
                    if (resultSet.getString(1).equals(dbName, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }

                if (!exists) {
                    conn.createStatement().use { stmt ->
                        // dbName has been validated against [a-zA-Z][a-zA-Z0-9_]* — safe to interpolate
                        stmt.executeUpdate("CREATE DATABASE $dbName")
                    }
                    log.info("flyway.bootstrap: database '{}' created successfully", dbName)
                } else {
                    log.info("flyway.bootstrap: database '{}' already exists — skipping creation", dbName)
                }
            }
        } catch (e: Exception) {
            // Log and continue; the primary Flyway migration will fail with a clear error if DB is absent
            log.warn("flyway.bootstrap: pre-flight database check encountered an issue: {}", e.message)
        }
    }
}
