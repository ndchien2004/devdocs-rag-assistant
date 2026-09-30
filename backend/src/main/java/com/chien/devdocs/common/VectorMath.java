package com.chien.devdocs.common;

public final class VectorMath {

    private VectorMath() {
    }

    /**
     * cos(a, b) = (a · b) / (|a| · |b|), nằm trong [-1, 1]. Càng gần 1 càng giống nhau.
     * pgvector dùng cosine distance = 1 - cosine similarity.
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have the same dimensions: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
