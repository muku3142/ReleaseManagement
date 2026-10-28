-- Quick 261026-l31: Reach/Impact/Confidence are calculated; reach/impact/confidence columns now hold only user-entered values.
ALTER TABLE roadmap_item
    ADD COLUMN jira_priority              VARCHAR(100),
    ADD COLUMN jira_readiness             SMALLINT CHECK (jira_readiness BETWEEN 0 AND 3),
    ADD COLUMN reach_override_reason      VARCHAR(200),
    ADD COLUMN impact_override_reason     VARCHAR(200),
    ADD COLUMN confidence_override_reason VARCHAR(200);

-- Values typed before this change are kept as overrides so no score moves; the user can reset them.
UPDATE roadmap_item SET reach_override_reason = 'Entered before Reach was calculated' WHERE reach IS NOT NULL;
UPDATE roadmap_item SET impact_override_reason = 'Entered before Impact was calculated'
 WHERE source = 'JIRA' AND impact IS NOT NULL;
UPDATE roadmap_item SET confidence_override_reason = 'Entered before Confidence was calculated'
 WHERE source = 'JIRA' AND confidence IS NOT NULL;

ALTER TABLE roadmap_item
    ADD CONSTRAINT roadmap_item_reach_override CHECK ((reach IS NULL) = (reach_override_reason IS NULL)),
    ADD CONSTRAINT roadmap_item_impact_override CHECK (CASE WHEN source = 'MANUAL' THEN impact_override_reason IS NULL
        ELSE (impact IS NULL) = (impact_override_reason IS NULL) END),
    ADD CONSTRAINT roadmap_item_confidence_override CHECK (CASE WHEN source = 'MANUAL' THEN confidence_override_reason IS NULL
        ELSE (confidence IS NULL) = (confidence_override_reason IS NULL) END);

-- ADD COLUMN ... DEFAULT does not fire the row-level append-only trigger.
ALTER TABLE roadmap_item_input_history
    ADD COLUMN kind   VARCHAR(12) NOT NULL DEFAULT 'ENTERED' CHECK (kind IN ('ENTERED', 'OVERRIDDEN', 'RESET')),
    ADD COLUMN reason VARCHAR(200),
    ADD CONSTRAINT roadmap_item_input_history_reason CHECK ((kind = 'OVERRIDDEN') = (reason IS NOT NULL));
