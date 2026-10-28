-- Phase 17.1 briefings (BRIEF-01..07; D-17.1-05, -09, -11, -16, -20). Additive: no existing column changes.
-- Written once and complete; never edit after it has run on the live DB.
CREATE TABLE product_jira_project (
    product_id  UUID        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    project_key VARCHAR(10) NOT NULL,
    PRIMARY KEY (product_id, project_key),
    CONSTRAINT product_jira_project_key_format CHECK (project_key ~ '^[A-Z][A-Z0-9_]{1,9}$')
);
-- D-17.1-20: a Jira project key belongs to exactly one product.
CREATE UNIQUE INDEX product_jira_project_key_unique ON product_jira_project (upper(project_key));

-- D-17.1-20: deterministic owner = the product whose earliest release carries the key.
INSERT INTO product_jira_project (product_id, project_key)
SELECT DISTINCT ON (k.project_key) k.product_id, k.project_key
FROM (SELECT rp.product_id, upper(btrim(r.jira_project_key)) AS project_key, min(r.created_at) AS first_release_at
      FROM release r JOIN release_product rp ON rp.release_id = r.id
      WHERE upper(btrim(r.jira_project_key)) ~ '^[A-Z][A-Z0-9_]{1,9}$'
      GROUP BY rp.product_id, upper(btrim(r.jira_project_key))) k
ORDER BY k.project_key, k.first_release_at, k.product_id;

-- Singleton. Null thresholds/keep_runs mean "use briefings/config.json, then the built-in default" (D-17.1-16).
CREATE TABLE briefing_settings (
    id                   SMALLINT     PRIMARY KEY CHECK (id = 1),
    scheduler_account_id VARCHAR(255),
    grace_minutes        INTEGER      NOT NULL DEFAULT 30 CHECK (grace_minutes BETWEEN 1 AND 240),
    work_days            VARCHAR(27)  NOT NULL DEFAULT 'SUN,MON,TUE,WED,THU',
    keep_runs            INTEGER      CHECK (keep_runs BETWEEN 10 AND 500),
    -- D-17.1-24: the app runs briefings on its own schedule only after the user says Claude is unavailable.
    fallback_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(255)
);
INSERT INTO briefing_settings (id) VALUES (1);

CREATE TABLE briefing_task_setting (
    task                     VARCHAR(20) PRIMARY KEY CHECK (task IN ('jira_digest', 'sprint_check', 'weekly_status')),
    enabled                  BOOLEAN     NOT NULL DEFAULT TRUE,
    cron                     VARCHAR(64) NOT NULL,
    stale_after_working_days INTEGER     CHECK (stale_after_working_days BETWEEN 1 AND 30),
    due_soon_days            INTEGER     CHECK (due_soon_days BETWEEN 1 AND 60),
    backlog_items            INTEGER     CHECK (backlog_items BETWEEN 1 AND 100)
);
-- Spring 6-field cron, Africa/Cairo; the exported Claude schedules (scheduledTasks/README.md).
INSERT INTO briefing_task_setting (task, cron) VALUES
    ('jira_digest',   '0 44 8 * * SUN,MON,TUE,WED,THU'),
    ('sprint_check',  '0 41 9 * * WED'),
    ('weekly_status', '0 41 13 * * THU');

CREATE TABLE briefing_run (
    id          UUID         PRIMARY KEY,
    task        VARCHAR(20)  NOT NULL CHECK (task IN ('jira_digest', 'sprint_check', 'weekly_status')),
    product_id  UUID         NOT NULL REFERENCES product (id) ON DELETE RESTRICT,
    slot_at     TIMESTAMPTZ,
    run_trigger VARCHAR(10)  NOT NULL CHECK (run_trigger IN ('SLOT', 'MANUAL')),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('ok', 'attention', 'quiet', 'failed', 'needs_credential', 'skipped')),
    reason      VARCHAR(40),
    attention   INTEGER      CHECK (attention >= 0),
    headline    VARCHAR(140) NOT NULL,
    summary     VARCHAR(400),
    body        TEXT         NOT NULL DEFAULT '[]',
    started_at  TIMESTAMPTZ  NOT NULL,
    finished_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT briefing_run_slot_matches_trigger CHECK ((run_trigger = 'SLOT') = (slot_at IS NOT NULL))
);
-- Idempotency record for the scheduler: one evaluation per (task, product, slot).
CREATE UNIQUE INDEX briefing_run_slot_unique ON briefing_run (task, product_id, slot_at) WHERE slot_at IS NOT NULL;
CREATE INDEX briefing_run_task_product_finished ON briefing_run (task, product_id, finished_at DESC);
