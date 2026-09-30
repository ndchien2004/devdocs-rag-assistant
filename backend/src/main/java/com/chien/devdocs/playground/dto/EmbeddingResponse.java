package com.chien.devdocs.playground.dto;

public record EmbeddingResponse(String text, int dimensions, float[] head) {
}
