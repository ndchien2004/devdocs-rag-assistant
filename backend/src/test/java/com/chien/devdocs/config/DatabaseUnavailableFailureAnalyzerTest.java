package com.chien.devdocs.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUnavailableFailureAnalyzerTest {

    private final DatabaseUnavailableFailureAnalyzer analyzer = new DatabaseUnavailableFailureAnalyzer();

    /** Cùng chuỗi nguyên nhân với log thật: Flyway → PSQLException → ConnectException. */
    private static Throwable databaseDown(String message) {
        var sql = new SQLException(message, "08001", new ConnectException("Connection refused: getsockopt"));
        return new IllegalStateException("Error creating bean with name 'flywayInitializer'",
                new RuntimeException("Unable to obtain connection from database", sql));
    }

    @Test
    void explainsHowToStartPostgresAndNamesTheAddress() {
        FailureAnalysis analysis = analyzer.analyze(databaseDown(
                "Connection to localhost:5433 refused. Check that the hostname and port are correct"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("PostgreSQL", "localhost:5433");
        assertThat(analysis.getAction()).contains("docker compose up -d --wait", "DB_PORT", ".env");
    }

    @Test
    void stillHelpsWhenTheAddressIsNotInTheMessage() {
        FailureAnalysis analysis = analyzer.analyze(databaseDown("I/O error"));

        assertThat(analysis.getDescription()).contains("spring.datasource.url");
    }

    @Test
    void ignoresConnectionErrorsThatAreNotFromTheDatabase() {
        assertThat(analyzer.analyze(new RuntimeException(new ConnectException("Ollama refused")))).isNull();
        assertThat(analyzer.analyze(new IllegalStateException("unrelated"))).isNull();
    }
}
