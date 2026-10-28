-- Phase 15 (PORT-01..07, D-15-17): products, clients, the product<->client link, the product set of a release, and an
-- append-only client_deployment ledger. promotion and environment_stage are untouched; no existing table is altered or updated.
-- Products and clients are archived (archived_at), never deleted. Names are unique case-insensitively across archived rows too.

CREATE TABLE product (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT product_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX product_name_unique ON product (lower(name));

CREATE TABLE client (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT client_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX client_name_unique ON client (lower(name));

CREATE TABLE product_client (
    product_id UUID        NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    client_id  UUID        NOT NULL REFERENCES client (id) ON DELETE RESTRICT,
    linked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, client_id)
);
CREATE INDEX product_client_client_idx ON product_client (client_id);

-- CASCADE is safe: only OPEN releases are deletable and installs need a FROZEN release.
CREATE TABLE release_product (
    release_id UUID NOT NULL REFERENCES release (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    PRIMARY KEY (release_id, product_id)
);
CREATE INDEX release_product_product_idx ON release_product (product_id);

-- D-15-17: one product per existing Jira project key, named the key; DISTINCT ON lower() so product_name_unique can never trip.
INSERT INTO product (id, name)
SELECT gen_random_uuid(), k.name
FROM (SELECT DISTINCT ON (lower(btrim(jira_project_key))) btrim(jira_project_key) AS name
      FROM release
      WHERE nullif(btrim(jira_project_key), '') IS NOT NULL
      ORDER BY lower(btrim(jira_project_key)), btrim(jira_project_key) COLLATE "C") k;

-- D-15-17: keyless releases share one "Default" product, created only when needed; reused if a key already produced that name.
INSERT INTO product (id, name)
SELECT gen_random_uuid(), 'Default'
WHERE EXISTS (SELECT 1 FROM release WHERE nullif(btrim(jira_project_key), '') IS NULL)
  AND NOT EXISTS (SELECT 1 FROM product WHERE lower(name) = 'default');

INSERT INTO release_product (release_id, product_id)
SELECT r.id, p.id
FROM release r
JOIN product p ON lower(p.name) = lower(coalesce(nullif(btrim(r.jira_project_key), ''), 'Default'));

-- Ledger of per-client installs (PORT-05). ON DELETE RESTRICT everywhere: rows are audit records, so a release_product or
-- product_client pair that any row references (voided and superseded included) cannot be removed (D-15-13, D-15-15).
-- All composite-FK columns are NOT NULL, so MATCH SIMPLE never skips the check. A correction must target the same release,
-- product AND client as the row it supersedes (D-15-04), otherwise a row for client B could hide client A's install.
CREATE TABLE client_deployment (
    id                UUID         PRIMARY KEY,
    release_id        UUID         NOT NULL REFERENCES release (id) ON DELETE RESTRICT,
    product_id        UUID         NOT NULL,
    client_id         UUID         NOT NULL,
    outcome           VARCHAR(16)  NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED', 'ROLLED_BACK', 'VOIDED')),
    installed_at      TIMESTAMPTZ  NOT NULL,
    recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    recorded_by       VARCHAR(255) NOT NULL,
    note              VARCHAR(2000),
    supersedes_id     UUID,
    correction_reason VARCHAR(2000),
    CONSTRAINT client_deployment_release_covers_product
        FOREIGN KEY (release_id, product_id) REFERENCES release_product (release_id, product_id) ON DELETE RESTRICT,
    CONSTRAINT client_deployment_client_has_product
        FOREIGN KEY (product_id, client_id) REFERENCES product_client (product_id, client_id) ON DELETE RESTRICT,
    CONSTRAINT client_deployment_key_unique UNIQUE (id, release_id, product_id, client_id),
    CONSTRAINT client_deployment_supersedes_same_target
        FOREIGN KEY (supersedes_id, release_id, product_id, client_id)
        REFERENCES client_deployment (id, release_id, product_id, client_id),
    CONSTRAINT client_deployment_correction_has_reason
        CHECK ((supersedes_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT client_deployment_correction_reason_not_blank
        CHECK (correction_reason IS NULL OR length(btrim(correction_reason)) > 0),
    CONSTRAINT client_deployment_voided_only_by_correction
        CHECK (outcome <> 'VOIDED' OR supersedes_id IS NOT NULL)
);
CREATE UNIQUE INDEX client_deployment_superseded_once ON client_deployment (supersedes_id) WHERE supersedes_id IS NOT NULL;
CREATE INDEX client_deployment_client_product_idx ON client_deployment (client_id, product_id, installed_at);
CREATE INDEX client_deployment_release_product_idx ON client_deployment (release_id, product_id);

-- The trigger is the append-only guarantee because the app role owns the tables. There is deliberately no statement-level
-- truncate trigger: test cleanup truncates release with CASCADE and row-level triggers do not fire on truncation (see V10).
CREATE FUNCTION client_deployment_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'client_deployment is append-only (PORT-05)' USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER client_deployment_no_update_delete
    BEFORE UPDATE OR DELETE ON client_deployment
    FOR EACH ROW EXECUTE FUNCTION client_deployment_append_only();

-- Single home of the D-15-05 rule; Phase 16 reads current_client_release.
CREATE VIEW effective_client_deployment AS
SELECT d.id, d.release_id, d.product_id, d.client_id, d.outcome, d.installed_at, d.recorded_at, d.recorded_by, d.note
FROM client_deployment d
WHERE d.outcome <> 'VOIDED'
  AND NOT EXISTS (SELECT 1 FROM client_deployment s WHERE s.supersedes_id = d.id);

CREATE VIEW current_client_release AS
SELECT DISTINCT ON (client_id, product_id) client_id, product_id, release_id, id AS deployment_id, installed_at, recorded_at
FROM effective_client_deployment
WHERE outcome = 'SUCCESS'
ORDER BY client_id, product_id, installed_at DESC, recorded_at DESC;
