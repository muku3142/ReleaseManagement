-- D-14-01 / ASST-10: AI provenance on a stored document revision. An AI-assisted revision is an ordinary
-- append-only snapshot row (D-05-06) that additionally records how it was produced.
--
-- The columns are nullable because template-generated revisions, and every row that existed before this
-- migration, are not AI-assisted: no backfill and no default. There is deliberately no `ai_assisted`
-- boolean. `ai_mode IS NOT NULL` already says it, and a second flag could disagree with the first.
--
-- basis_generated_at is the staleness basis copied from the source revision, so a chain of AI revisions
-- keeps the original generation time (readiness compares against it, not against the AI save time).
ALTER TABLE document_snapshot
    ADD COLUMN ai_mode            VARCHAR(16) CHECK (ai_mode IN ('CLEAN_UP', 'CUSTOM')),
    ADD COLUMN ai_instruction     TEXT,
    ADD COLUMN ai_source_revision INTEGER     CHECK (ai_source_revision >= 1),
    ADD COLUMN basis_generated_at TIMESTAMPTZ;

ALTER TABLE document_snapshot ADD CONSTRAINT document_snapshot_ai_all_or_none CHECK (
    (ai_mode IS NULL AND ai_instruction IS NULL AND ai_source_revision IS NULL AND basis_generated_at IS NULL)
    OR (ai_mode IS NOT NULL AND ai_instruction IS NOT NULL AND ai_source_revision IS NOT NULL
        AND basis_generated_at IS NOT NULL));
