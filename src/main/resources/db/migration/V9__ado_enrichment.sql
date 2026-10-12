-- Phase 6 (D-06-01, D-06-02, D-06-08): enrichment is materialised per item and never fetched on read.
-- NOT_ATTEMPTED is the column default, so every existing row reads as "never enriched" with no backfill.
-- Pull requests, commits and status changes are child tables rather than JSONB because Phase 8 joins
-- release_item_commit.authored_at against release_ticket_item.first_done_at in set-based SQL.
ALTER TABLE release_ticket_item
    ADD COLUMN jira_issue_id             VARCHAR(32),
    ADD COLUMN jira_updated_at           TIMESTAMPTZ,
    ADD COLUMN jira_status_category      VARCHAR(16) CHECK (jira_status_category IN ('TO_DO', 'IN_PROGRESS', 'DONE')),
    ADD COLUMN enrichment_status         VARCHAR(16) NOT NULL DEFAULT 'NOT_ATTEMPTED'
               CHECK (enrichment_status IN ('NOT_ATTEMPTED', 'NO_LINK', 'LINKED', 'FAILED')),
    -- No CHECK: the reason set is expected to grow with drift handling; the enum is the contract.
    ADD COLUMN enrichment_reason         VARCHAR(40),
    ADD COLUMN enrichment_incomplete     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN enrichment_attempted_at   TIMESTAMPTZ,
    ADD COLUMN commits_source            VARCHAR(16) CHECK (commits_source IN ('KEY_TAGGED', 'ADO_COMPLETE')),
    ADD COLUMN branches                  TEXT        NOT NULL DEFAULT '[]',
    ADD COLUMN history_state             VARCHAR(16) NOT NULL DEFAULT 'NOT_READ'
               CHECK (history_state IN ('NOT_READ', 'AVAILABLE', 'UNAVAILABLE')),
    ADD COLUMN history_synced_updated_at TIMESTAMPTZ,
    ADD COLUMN first_done_at             TIMESTAMPTZ,
    ADD COLUMN reopen_count              INTEGER;

CREATE TABLE release_item_pull_request (
    id                    UUID          PRIMARY KEY,
    item_id               UUID          NOT NULL REFERENCES release_ticket_item (id) ON DELETE CASCADE,
    repository_id         VARCHAR(64)   NOT NULL,
    pr_id                 VARCHAR(32)   NOT NULL,
    title                 VARCHAR(500)  NOT NULL,
    status                VARCHAR(32)   NOT NULL,
    url                   VARCHAR(1000) NOT NULL,
    source_branch         VARCHAR(500)  NOT NULL,
    target_branch         VARCHAR(500),
    counts_toward_commits BOOLEAN       NOT NULL,
    commits_fetched_at    TIMESTAMPTZ,
    CONSTRAINT release_item_pull_request_unique UNIQUE (item_id, repository_id, pr_id)
);

CREATE TABLE release_item_commit (
    id          UUID          PRIMARY KEY,
    item_id     UUID          NOT NULL REFERENCES release_ticket_item (id) ON DELETE CASCADE,
    sha         VARCHAR(64)   NOT NULL,
    message     VARCHAR(1000) NOT NULL,
    author_name VARCHAR(255),
    authored_at TIMESTAMPTZ   NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    CONSTRAINT release_item_commit_unique UNIQUE (item_id, sha)
);
CREATE INDEX release_item_commit_item_authored_idx ON release_item_commit (item_id, authored_at);

CREATE TABLE release_item_status_change (
    id             UUID        PRIMARY KEY,
    item_id        UUID        NOT NULL REFERENCES release_ticket_item (id) ON DELETE CASCADE,
    changed_at     TIMESTAMPTZ NOT NULL,
    from_status_id VARCHAR(32),
    to_status_id   VARCHAR(32) NOT NULL,
    -- NULL category = the status id is unknown to Jira's status list.
    from_category  VARCHAR(16),
    to_category    VARCHAR(16)
);
CREATE INDEX release_item_status_change_item_idx ON release_item_status_change (item_id, changed_at);
