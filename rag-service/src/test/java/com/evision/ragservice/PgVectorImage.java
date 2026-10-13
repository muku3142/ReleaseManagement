package com.evision.ragservice;

import org.testcontainers.utility.DockerImageName;

public final class PgVectorImage {

    public static final DockerImageName IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.7-pg18-trixie")
            .asCompatibleSubstituteFor("postgres");

    private PgVectorImage() {
    }
}
