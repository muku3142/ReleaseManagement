-- D-05-02/D-05-03: the ticket's Jira component, materialised onto the release's own snapshot
-- alongside every other ticket field. Nullable, no default and NO BACKFILL: an existing row keeps
-- NULL, an OPEN release picks the value up on its next refresh (D-03-07), and a FROZEN release is
-- never touched - backfilling one would mutate an audit record, contradicting D-03-10 and
-- undermining the immutability guarantee DOC-07 rests on. A migration-time Jira call was also
-- declined: nothing reaches an external system while no user is signed in (D-14).
ALTER TABLE release_ticket_item ADD COLUMN component VARCHAR(255);

-- D-05-09: staleness compares a document's generated_at against the last time the release changed.
-- last_refreshed_at (D-03-07) covers refreshes but not adds and, critically, not REMOVALS - removing
-- an item leaves no timestamp anywhere, so max(added_at) cannot see it. This column is stamped on
-- every item mutation so "stale relative to the release's current ticket set" is actually answerable.
-- Nullable with no backfill: a release that has not changed since V5 is correctly "not known to have
-- changed", and last_refreshed_at still covers the refresh case for older rows.
ALTER TABLE release ADD COLUMN items_changed_at TIMESTAMPTZ;

-- D-05-04: the grouping dimension is a per-template setting. ISSUE_TYPE is the default precisely so
-- every template authored in Phase 4 keeps rendering {{#groups}} exactly as it did - the section
-- shape and {{groupName}} do not change, only which field keys the grouping.
ALTER TABLE template ADD COLUMN grouping_dimension VARCHAR(16) NOT NULL DEFAULT 'ISSUE_TYPE'
    CHECK (grouping_dimension IN ('ISSUE_TYPE', 'COMPONENT'));

-- Generated documents (DOC-07). Append-only (D-05-06): there is deliberately no updated_at,
-- no updated_by and no soft-delete flag, because nothing is ever overwritten or pruned - Phase 7's
-- AUDIT-02 surfaces superseded revisions and they must still be there.
--
-- D-05-05: the template is COPIED, not referenced. D-04-13 makes templates freely deletable, so a
-- foreign key to template would dangle. template_id is a plain UUID with no REFERENCES clause: it
-- exists only so the API can answer "does the source template still exist", never to join for
-- content. markdown holds the post-Mustache, pre-flexmark body; template_body holds the raw
-- template body for provenance; the chrome columns hold the RESOLVED chrome text (post-Mustache),
-- because the viewer and both exporters need resolved chrome and re-resolving it later would read
-- today's release data into yesterday's document.
--
-- The FK to release DOES cascade: deleting a release deletes its paperwork with it, which is the
-- opposite of the template relationship and is intentional.
CREATE TABLE document_snapshot (
    id                   UUID PRIMARY KEY,
    release_id           UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    document_type        VARCHAR(32)  NOT NULL
                         CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    revision             INTEGER      NOT NULL CHECK (revision >= 1),
    markdown             TEXT         NOT NULL,
    template_id          UUID,
    template_name        VARCHAR(200) NOT NULL,
    template_body        TEXT         NOT NULL,
    cover_enabled        BOOLEAN      NOT NULL,
    cover_title          TEXT         NOT NULL,
    cover_subtitle       TEXT         NOT NULL,
    header_text          TEXT         NOT NULL,
    footer_text          TEXT         NOT NULL,
    page_numbers_enabled BOOLEAN      NOT NULL,
    generated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by         VARCHAR(255) NOT NULL,
    CONSTRAINT document_snapshot_revision_unique UNIQUE (release_id, document_type, revision)
);

CREATE INDEX document_snapshot_release_type_idx
    ON document_snapshot (release_id, document_type, revision DESC);

-- D-05-05 again: the logo bytes are copied too, not pointed at. template_logo cascades away with its
-- template; a generated document whose cover shows a logo must keep showing it after that template
-- is deleted or its logo replaced. Column set mirrors template_logo exactly so LogoImage/LogoMetadata
-- round-trip without a second mapping shape.
CREATE TABLE document_snapshot_logo (
    snapshot_id  UUID PRIMARY KEY REFERENCES document_snapshot (id) ON DELETE CASCADE,
    bytes        BYTEA        NOT NULL,
    content_type VARCHAR(64)  NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg')),
    filename     VARCHAR(255) NOT NULL,
    byte_size    INTEGER      NOT NULL,
    width        INTEGER      NOT NULL,
    height       INTEGER      NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL
);
