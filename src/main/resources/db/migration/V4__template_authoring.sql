-- Document templates: many named templates per document type with exactly one active (D-04-12).
-- "Exactly one active" is a partial unique index rather than a service-layer check, because a
-- convention that only lives in Java is one concurrent request away from being false.
-- Templates are freely editable and freely deletable with no guard and no soft-delete (D-04-13):
-- Phase 5's document snapshot stores rendered content, so deleting a template destroys nothing
-- readable. Chrome fields (D-04-15) sit on the row; the logo (D-04-16) lives in its own table so
-- that listing templates never reads image bytes.

CREATE TABLE template (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    document_type        VARCHAR(32)  NOT NULL
                         CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    body                 TEXT         NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT false,
    cover_enabled        BOOLEAN      NOT NULL DEFAULT false,
    cover_title          VARCHAR(500) NOT NULL DEFAULT '',
    cover_subtitle       VARCHAR(500) NOT NULL DEFAULT '',
    header_text          VARCHAR(500) NOT NULL DEFAULT '',
    footer_text          VARCHAR(500) NOT NULL DEFAULT '',
    page_numbers_enabled BOOLEAN      NOT NULL DEFAULT false,
    warnings             TEXT         NOT NULL DEFAULT '[]',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(255) NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(255) NOT NULL,
    CONSTRAINT template_type_name_unique UNIQUE (document_type, name)
);

CREATE UNIQUE INDEX template_one_active_per_type_idx ON template (document_type) WHERE active;

CREATE TABLE template_logo (
    template_id  UUID PRIMARY KEY REFERENCES template (id) ON DELETE CASCADE,
    bytes        BYTEA        NOT NULL,
    content_type VARCHAR(64)  NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    filename     VARCHAR(255) NOT NULL,
    byte_size    INTEGER      NOT NULL,
    width        INTEGER      NOT NULL,
    height       INTEGER      NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
