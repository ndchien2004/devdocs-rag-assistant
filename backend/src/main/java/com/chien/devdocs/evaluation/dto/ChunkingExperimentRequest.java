package com.chien.devdocs.evaluation.dto;

import com.chien.devdocs.document.ChunkSplitter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param strategy bỏ trống = TOKEN nếu không overlap, WINDOW nếu có overlap
 */
public record ChunkingExperimentRequest(
        @NotNull @Min(50) @Max(2000) Integer chunkSize,
        @Min(0) @Max(1000) Integer chunkOverlap,
        ChunkSplitter.Strategy strategy,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold
) {
    public int chunkOverlapOrZero() {
        return chunkOverlap != null ? chunkOverlap : 0;
    }

    public ChunkSplitter.Strategy strategyOrDefault() {
        if (strategy != null) {
            return strategy;
        }
        return chunkOverlapOrZero() > 0 ? ChunkSplitter.Strategy.WINDOW : ChunkSplitter.Strategy.TOKEN;
    }

    public EvaluationRequest toEvaluation() {
        return new EvaluationRequest(topK, similarityThreshold, false);
    }
}
