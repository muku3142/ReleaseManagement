-- The Release aggregate: the first entity this system owns rather than reads from Jira.
-- Ticket items store a full materialised copy of the Jira ticket (D-03-06) so that opening a
-- release never issues a Jira call, and so a frozen release keeps the field values it had at
-- freeze rather than whatever Jira says today.

CREATE TABLE release (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(200)  NOT NULL,
    version            VARCHAR(100)  NOT NULL,
    status             VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'FROZEN')),
    jira_project_key   VARCHAR(64),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(255)  NOT NULL,
    frozen_at          TIMESTAMPTZ,
    frozen_by          VARCHAR(255),
    last_refreshed_at  TIMESTAMPTZ,
    CONSTRAINT release_name_version_unique UNIQUE (name, version)
);

CREATE TABLE release_ticket_item (
    id             UUID PRIMARY KEY,
    release_id     UUID          NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    ticket_key     VARCHAR(64)   NOT NULL,
    summary        VARCHAR(1000) NOT NULL,
    issue_type     VARCHAR(100)  NOT NULL,
    status         VARCHAR(100)  NOT NULL,
    resolution     VARCHAR(100),
    assignee       VARCHAR(255),
    reporter       VARCHAR(255),
    fix_versions   TEXT          NOT NULL,
    description    TEXT          NOT NULL,
    comments       TEXT          NOT NULL,
    comment_total  INTEGER       NOT NULL,
    added_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT release_ticket_item_key_unique UNIQUE (release_id, ticket_key)
);

CREATE TABLE release_manual_item (
    id           UUID         PRIMARY KEY,
    release_id   UUID         NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL,
    category     VARCHAR(32)  NOT NULL
                 CHECK (category IN ('CONFIG_CHANGE', 'DATABASE_SCRIPT', 'INFRASTRUCTURE', 'OTHER')),
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX release_manual_item_release_id_idx ON release_manual_item (release_id);
