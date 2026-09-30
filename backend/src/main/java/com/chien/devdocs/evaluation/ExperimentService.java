package com.chien.devdocs.evaluation;

import com.chien.devdocs.document.ChunkSplitter;
import com.chien.devdocs.document.ChunkingPipeline;
import com.chien.devdocs.document.DocumentStatus;
import com.chien.devdocs.document.SourceDocument;
import com.chien.devdocs.document.SourceDocumentRepository;
import com.chien.devdocs.evaluation.dto.EvaluationReport;
import com.chien.devdocs.evaluation.dto.EvaluationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 — thí nghiệm cắt chunk (E1: chunk size, E4: overlap).
 * <p>
 * Mỗi cấu hình được index vào một <b>bảng vector riêng</b> ({@code exp_chunks_<size>_o<overlap>}) từ chính các file
 * đã upload, rồi chạy cùng bộ eval. Dữ liệu chính trong {@code vector_store} không bị đụng tới.
 */
@Service
public class ExperimentService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final SourceDocumentRepository repository;
    private final ChunkingPipeline pipeline;
    private final EvaluationService evaluationService;
    private final int dimensions;

    public ExperimentService(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
                             SourceDocumentRepository repository, ChunkingPipeline pipeline,
                             EvaluationService evaluationService,
                             @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}") int dimensions) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.repository = repository;
        this.pipeline = pipeline;
        this.evaluationService = evaluationService;
        this.dimensions = dimensions;
    }

    public EvaluationReport runChunking(int chunkSize, int chunkOverlap, ChunkSplitter.Strategy strategy,
                                        EvaluationRequest request) {
        if (chunkSize < 50 || chunkSize > 2000 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("Require 50 <= chunkSize <= 2000 and 0 <= chunkOverlap < chunkSize");
        }
        // Tên bảng chỉ ghép từ enum và số nguyên đã kiểm tra → an toàn.
        String table = "exp_%s_%d_o%d".formatted(strategy.name().toLowerCase(), chunkSize, chunkOverlap);
        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(table)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .removeExistingVectorStoreTable(true)   // mỗi lần chạy index lại từ đầu
                .build();
        store.afterPropertiesSet();

        long start = System.currentTimeMillis();
        List<Document> chunks = new ArrayList<>();
        for (SourceDocument doc : repository.search(null, DocumentStatus.INDEXED)) {
            chunks.addAll(pipeline.chunk(doc, chunkSize, chunkOverlap, strategy).chunks());
        }
        store.add(chunks);
        log.info("Experiment table {}: {} chunks indexed in {} ms", table, chunks.size(),
                System.currentTimeMillis() - start);

        String label = "%s chunk=%d overlap=%d (%d chunks)".formatted(strategy, chunkSize, chunkOverlap, chunks.size());
        return evaluationService.run(request, store, label);
    }
}
