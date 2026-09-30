package com.chien.devdocs.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Toàn bộ tham số RAG tập trung ở {@code app.rag.*}.
 */
@Validated
@ConfigurationProperties("app.rag")
public record RagProperties(
        // --- Query ---
        @DefaultValue("5") @Min(1) @Max(10) int topK,
        @DefaultValue("0.5") @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @DefaultValue("3000") @Min(100) int maxContextTokens,

        // --- Ingestion ---
        @DefaultValue("500") @Min(50) int chunkSize,
        /** 0 = TokenTextSplitter (baseline); &gt; 0 = OverlapTextSplitter (thí nghiệm E4). */
        @DefaultValue("0") @Min(0) int chunkOverlap,
        @DefaultValue("200") @Min(0) int minChunkSizeChars,
        @DefaultValue("10") @Min(0) int minChunkLengthToEmbed,
        @DefaultValue("30") @Min(0) int minPageChars,
        @DefaultValue("20MB") DataSize maxFileSize,
        @DefaultValue("./storage") @NotBlank String storageDir
) {
}
