-- RAG-01..05: client apps, collections, documents (the indexing queue) and chunks.
CREATE EXTENSION IF NOT EXISTS vector;  -- created by the superuser init script; a no-op for the rag role

CREATE TABLE client_app (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT         NOT NULL UNIQUE,
    key_hash      BYTEA        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ
);

CREATE TABLE collection (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id        UUID         NOT NULL REFERENCES client_app (id),
    name             TEXT         NOT NULL,
    embedding_model  TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (client_id, name)
);

CREATE TABLE document (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id  UUID         NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    external_id    TEXT         NOT NULL,
    title          TEXT         NOT NULL,
    url            TEXT,
    metadata       JSONB        NOT NULL DEFAULT '{}',
    body           TEXT         NOT NULL,
    content_hash   BYTEA        NOT NULL,
    status         TEXT         NOT NULL CHECK (status IN ('PENDING', 'INDEXED', 'FAILED')),
    status_reason  TEXT,
    indexed_at     TIMESTAMPTZ,
    lease_until    TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (collection_id, external_id)
);
CREATE INDEX document_queue ON document (updated_at) WHERE status = 'PENDING';

CREATE TABLE chunk (
    id               BIGSERIAL    PRIMARY KEY,
    document_id      UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    collection_id    UUID         NOT NULL,
    ordinal          INT          NOT NULL,
    content          TEXT         NOT NULL,
    embedding        vector(768)  NOT NULL,
    embedding_model  TEXT         NOT NULL,
    UNIQUE (document_id, ordinal)
);
CREATE INDEX chunk_collection ON chunk (collection_id);  -- btree only: exact cosine scan, no ANN index until a slow query is measured
