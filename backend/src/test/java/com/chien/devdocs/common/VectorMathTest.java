package com.chien.devdocs.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorMathTest {

    @Test
    void identicalVectorsHaveSimilarityOne() {
        float[] v = {0.3f, -1.2f, 4f};
        assertThat(VectorMath.cosineSimilarity(v, v)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void orthogonalVectorsHaveSimilarityZero() {
        assertThat(VectorMath.cosineSimilarity(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void oppositeVectorsHaveSimilarityMinusOne() {
        assertThat(VectorMath.cosineSimilarity(new float[]{1, 2}, new float[]{-1, -2})).isCloseTo(-1.0, within(1e-6));
    }

    @Test
    void magnitudeDoesNotMatter() {
        assertThat(VectorMath.cosineSimilarity(new float[]{1, 2, 3}, new float[]{10, 20, 30}))
                .isCloseTo(1.0, within(1e-6));
    }

    @Test
    void zeroVectorReturnsZero() {
        assertThat(VectorMath.cosineSimilarity(new float[]{0, 0}, new float[]{1, 1})).isZero();
    }

    @Test
    void differentDimensionsAreRejected() {
        assertThatThrownBy(() -> VectorMath.cosineSimilarity(new float[]{1}, new float[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
