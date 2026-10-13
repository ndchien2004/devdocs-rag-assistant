package com.chien.devdocs.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ghi {@code query_logs} cho mọi {@link RagMode} (BR-QRY-07). Dùng chung cho {@link RagService} và
 * {@link AdvisorRagService} để hai chế độ log giống nhau.
 */
@Component
public class QueryLogger {

    private static final Logger log = LoggerFactory.getLogger(QueryLogger.class);

    private final QueryLogRepository repository;

    public QueryLogger(QueryLogRepository repository) {
        this.repository = repository;
    }

    public void save(QueryLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            // Ghi log thất bại không được làm hỏng câu trả lời.
            log.warn("Could not save query log: {}", e.getMessage());
        }
    }
}
