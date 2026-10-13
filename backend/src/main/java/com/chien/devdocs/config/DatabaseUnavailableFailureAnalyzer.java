package com.chien.devdocs.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App không khởi động được vì PostgreSQL chưa chạy → in hướng dẫn ngắn thay cho ~100 dòng stack trace.
 * Đăng ký trong {@code META-INF/spring.factories}.
 * <p>
 * Chỉ xử lý khi chuỗi nguyên nhân có cả {@link SQLException} (lỗi từ JDBC / Flyway) và {@link ConnectException}:
 * lỗi kết nối tới thứ khác (Ollama) không thuộc phạm vi này.
 */
public class DatabaseUnavailableFailureAnalyzer extends AbstractFailureAnalyzer<ConnectException> {

    /** PgJDBC: "Connection to localhost:5432 refused. Check that …" */
    private static final Pattern ADDRESS = Pattern.compile("Connection to (\\S+) refused");

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConnectException cause) {
        SQLException sql = findCause(rootFailure, SQLException.class);
        if (sql == null) {
            return null;
        }
        Matcher m = ADDRESS.matcher(String.valueOf(sql.getMessage()));
        String address = m.find() ? m.group(1) : "cấu hình trong spring.datasource.url";
        return new FailureAnalysis(
                "Không kết nối được PostgreSQL ở " + address + " (Connection refused).",
                """
                        PostgreSQL chưa chạy hoặc đang nghe ở cổng khác:
                          1. Bật Docker Desktop, rồi ở thư mục gốc repo chạy:  docker compose up -d --wait
                          2. Kiểm tra cổng:  docker compose ps   (cột PORTS, ví dụ 0.0.0.0:5432->5432)
                          3. Nếu cổng khác 5432, đặt DB_PORT trong file .env ở thư mục gốc repo cho khớp
                             (backend đọc cùng file .env với docker compose).""",
                cause);
    }
}
