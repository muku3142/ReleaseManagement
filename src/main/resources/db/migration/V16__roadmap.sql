-- Phase 17 (ROAD-01..07, PKG-01): the RICE roadmap (items and an append-only input history) and the release packages link.
-- The RICE score is never stored; it is computed from reach, impact, confidence and effort on read.

CREATE TABLE roadmap_item (
    id                     UUID         PRIMARY KEY,
    source                 VARCHAR(8)   NOT NULL CHECK (source IN ('JIRA', 'MANUAL')),
    jira_key               VARCHAR(32),
    jira_issue_id          VARCHAR(32),
    title                  VARCHAR(500) NOT NULL,
    jira_status            VARCHAR(100),
    jira_updated_at        TIMESTAMPTZ,
    jira_synced_at         TIMESTAMPTZ,
    jira_unavailable_since TIMESTAMPTZ,
    product_id             UUID REFERENCES product (id) ON DELETE RESTRICT,
    status                 VARCHAR(16)  NOT NULL DEFAULT 'IDEA'
                           CHECK (status IN ('IDEA', 'PLANNED', 'IN_PROGRESS', 'DONE')),
    notes                  VARCHAR(4000),
    target_quarter         VARCHAR(20),
    owner                  VARCHAR(100),
    reach                  INTEGER      CHECK (reach >= 0),
    impact                 NUMERIC(3,2) CHECK (impact IN (0.25, 0.5, 1, 2, 3)),
    confidence             SMALLINT     CHECK (confidence IN (50, 80, 100)),
    effort                 NUMERIC(8,2) CHECK (effort > 0),
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(255) NOT NULL,
    archived_at            TIMESTAMPTZ,
    CONSTRAINT roadmap_item_source_shape CHECK ((source = 'JIRA') = (jira_key IS NOT NULL)),
    CONSTRAINT roadmap_item_title_not_blank CHECK (length(btrim(title)) > 0)
);
CREATE UNIQUE INDEX roadmap_item_jira_key_unique ON roadmap_item (jira_key) WHERE jira_key IS NOT NULL;
CREATE UNIQUE INDEX roadmap_item_jira_issue_id_unique ON roadmap_item (jira_issue_id) WHERE jira_issue_id IS NOT NULL;
CREATE INDEX roadmap_item_product_idx ON roadmap_item (product_id);

CREATE TABLE roadmap_item_input_history (
    id          UUID         PRIMARY KEY,
    item_id     UUID         NOT NULL REFERENCES roadmap_item (id) ON DELETE RESTRICT,
    field       VARCHAR(16)  NOT NULL CHECK (field IN ('REACH', 'IMPACT', 'CONFIDENCE', 'EFFORT')),
    old_value   NUMERIC,
    new_value   NUMERIC,
    changed_at  TIMESTAMPTZ  NOT NULL,
    changed_by  VARCHAR(255) NOT NULL
);
CREATE INDEX roadmap_item_input_history_item_idx ON roadmap_item_input_history (item_id, changed_at DESC);

-- The trigger is the append-only guarantee because the app role owns the tables. There is deliberately no statement-level
-- truncate trigger: test cleanup truncates with CASCADE and row-level triggers do not fire on truncation (see V10).
CREATE FUNCTION roadmap_item_input_history_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'roadmap_item_input_history is append-only (ROAD-06)' USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER roadmap_item_input_history_no_update_delete
    BEFORE UPDATE OR DELETE ON roadmap_item_input_history
    FOR EACH ROW EXECUTE FUNCTION roadmap_item_input_history_append_only();

-- D-17-19: the link to the packages folder; nullable, validated by the server, not release content (items_changed_at untouched).
ALTER TABLE release ADD COLUMN packages_url VARCHAR(2048);
