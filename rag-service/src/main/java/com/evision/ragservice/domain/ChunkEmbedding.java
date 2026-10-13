package com.evision.ragservice.domain;

public record ChunkEmbedding(int ordinal, String content, float[] embedding) {
}
