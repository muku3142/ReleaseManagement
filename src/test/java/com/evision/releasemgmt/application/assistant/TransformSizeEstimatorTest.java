package com.evision.releasemgmt.application.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// The vectors are shared with rag-service's PromptBuilderTest: a drift on either side fails a test.
class TransformSizeEstimatorTest {

    private final TransformSizeEstimator estimator = new TransformSizeEstimator(4096, 3.0, 152);

    @ParameterizedTest
    @CsvSource({"14,5523", "300,5382", "301,5379", "330,5367", "500,5280", "2000,4530"})
    void maxCharsMatchesRagServiceBoundaryVectors(int instructionChars, int expected) {
        assertThat(estimator.maxChars(instructionChars)).isEqualTo(expected);
    }

    @Test
    void documentOfMaxCharsFitsAndOneMoreDoesNot() {
        assertThat(estimator.fits(5523, 14)).isTrue();
        assertThat(estimator.fits(5524, 14)).isFalse();
    }

    @Test
    void biggerContextWindowRaisesTheLimit() {
        assertThat(new TransformSizeEstimator(8192, 3.0, 152).maxChars(300)).isGreaterThan(estimator.maxChars(300));
    }

    @Test
    void instructionFillingTheWindowYieldsZeroNeverNegative() {
        assertThat(estimator.maxChars(20_000)).isZero();
        assertThat(estimator.fits(1, 20_000)).isFalse();
    }
}
