package com.tinder.clone.moderation.infrastructure.persistence

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.test.assertEquals

class ModerationMigrationTest {
    @Test
    fun `migrations create the complete moderation schema on PostgreSQL`() {
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val result = Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()

            assertEquals(3, result.migrationsExecuted)
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    val rows = statement.executeQuery(
                        """SELECT count(*) FROM information_schema.tables
                           WHERE table_schema = 'public' AND table_name LIKE 'moderation_%'"""
                    )
                    rows.next()
                    assertEquals(7, rows.getInt(1))
                }
            }
        }
    }
}
