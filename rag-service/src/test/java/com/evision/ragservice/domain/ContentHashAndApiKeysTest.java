package com.evision.ragservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentHashAndApiKeysTest {

    @Test
    void contentHashIsDeterministic32Bytes() {
        assertThat(ContentHash.of("t", "x")).hasSize(32).isEqualTo(ContentHash.of("t", "x"));
    }

    @Test
    void contentHashCoversTitleAndText() {
        assertThat(ContentHash.of("t", "x")).isNotEqualTo(ContentHash.of("t", "y"));
        assertThat(ContentHash.of("t", "x")).isNotEqualTo(ContentHash.of("u", "x"));
        assertThat(ContentHash.of("ab", "c")).isNotEqualTo(ContentHash.of("a", "bc"));
    }

    @Test
    void generatedKeysAre43UrlSafeCharsAndDistinct() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String key = ApiKeys.generate();
            assertThat(key).matches("[A-Za-z0-9_-]{43}");
            keys.add(key);
        }
        assertThat(keys).hasSize(1000);
    }

    @Test
    void hashIsSha256OfUtf8() throws Exception {
        String key = ApiKeys.generate();

        assertThat(ApiKeys.hash(key))
                .hasSize(32)
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    }
}
