-- Phase 7 (DEPL-01..04, AUDIT-01, AUDIT-03): one environment ladder and an append-only promotion ledger.
-- Current stage is never stored (D-07-03): it is derived on read from effective promotions and the current ladder order.

-- D-07-02/D-07-05: one ladder for the whole instance, no application dimension. D-07-03: ids are permanent; a used stage is
-- retired (retired_at), never deleted. Names are unique case-insensitively across retired stages too, so a history label is
-- never ambiguous.
-- UNIQUE (position) is DEFERRABLE so a reorder can rewrite every position in one transaction (RESEARCH Pitfall 5).
-- environment_stage_one_production gives at-most-one Production and the CHECK blocks retiring it; "at least one" is owned by
-- the API, which exposes no operation that clears the flag without setting another (D-07-04).
CREATE TABLE environment_stage (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    position      INTEGER      NOT NULL,
    is_production BOOLEAN      NOT NULL DEFAULT FALSE,
    retired_at    TIMESTAMPTZ,
    CONSTRAINT environment_stage_position_unique UNIQUE (position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT environment_stage_production_not_retired CHECK (NOT (is_production AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX environment_stage_name_unique ON environment_stage (lower(name));
CREATE UNIQUE INDEX environment_stage_one_production ON environment_stage (is_production) WHERE is_production;

-- D-07-01: the seeded ladder; only PROD is flagged Production (D-07-04).
INSERT INTO environment_stage (id, name, position, is_production) VALUES
    (gen_random_uuid(), 'DEV',  1, FALSE),
    (gen_random_uuid(), 'TEST', 2, FALSE),
    (gen_random_uuid(), 'UAT',  3, FALSE),
    (gen_random_uuid(), 'PROD', 4, TRUE);

-- ON DELETE RESTRICT (not CASCADE, unlike document_snapshot): promotions are audit records, and only OPEN releases are
-- deletable anyway. D-07-09: no in-progress outcome. D-07-15: VOIDED exists only as a correction of an earlier entry.
-- The composite FK keeps a correction on the same release as the entry it supersedes (IDOR backstop).
-- promotion_superseded_once = "superseded at most once" (D-07-11).
-- Phase 8 defines "effective" as NOT EXISTS (SELECT 1 FROM promotion s WHERE s.supersedes_id = p.id) and must also
-- exclude outcome = 'VOIDED' (D-07-15). promotion_stage_deployed_idx serves Phase 8's (environment, deployed_at) filter (D-08-14).
CREATE TABLE promotion (
    id                UUID          PRIMARY KEY,
    release_id        UUID          NOT NULL REFERENCES release (id) ON DELETE RESTRICT,
    stage_id          UUID          NOT NULL REFERENCES environment_stage (id) ON DELETE RESTRICT,
    outcome           VARCHAR(16)   NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED', 'ROLLED_BACK', 'VOIDED')),
    deployed_at       TIMESTAMPTZ   NOT NULL,
    recorded_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recorded_by       VARCHAR(255)  NOT NULL,
    note              VARCHAR(2000),
    supersedes_id     UUID,
    correction_reason VARCHAR(2000),
    CONSTRAINT promotion_id_release_unique UNIQUE (id, release_id),
    CONSTRAINT promotion_supersedes_same_release
        FOREIGN KEY (supersedes_id, release_id) REFERENCES promotion (id, release_id),
    CONSTRAINT promotion_correction_has_reason
        CHECK ((supersedes_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT promotion_correction_reason_not_blank
        CHECK (correction_reason IS NULL OR length(btrim(correction_reason)) > 0),
    CONSTRAINT promotion_voided_only_by_correction
        CHECK (outcome <> 'VOIDED' OR supersedes_id IS NOT NULL)
);
CREATE UNIQUE INDEX promotion_superseded_once ON promotion (supersedes_id) WHERE supersedes_id IS NOT NULL;
CREATE INDEX promotion_stage_deployed_idx ON promotion (stage_id, deployed_at);
CREATE INDEX promotion_release_deployed_idx ON promotion (release_id, deployed_at);

-- AUDIT-03: the trigger is the append-only guarantee because the app role owns the tables, so revoking privileges would
-- not bind it (RESEARCH A1). There is deliberately no statement-level truncate trigger: test cleanup truncates release
-- with CASCADE and row-level triggers do not fire on truncation.
CREATE FUNCTION promotion_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'promotion is append-only (AUDIT-03)' USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER promotion_no_update_delete
    BEFORE UPDATE OR DELETE ON promotion
    FOR EACH ROW EXECUTE FUNCTION promotion_append_only();
