package org.yc.learn.rag.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author zhengyanchuang
 */
class TextNormalizerTest {

    @Test
    void removesBomAndNormalizesNewlines() {
        assertThat(TextNormalizer.normalize("﻿a\r\nb\rc")).isEqualTo("a\nb\nc");
    }

    @Test
    void handlesNull() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.isBlank(null)).isTrue();
        assertThat(TextNormalizer.isBlank(" \n\t")).isTrue();
        assertThat(TextNormalizer.isBlank("x")).isFalse();
    }
}
