-- D-17.2-07: an optional bundle name per ticket item; NULL means ungrouped, so no backfill.
ALTER TABLE release_ticket_item ADD COLUMN bundle VARCHAR(100);

-- D-17.2-07: documents can group by bundle; the V5 inline CHECK is replaced, not widened in place.
ALTER TABLE template DROP CONSTRAINT template_grouping_dimension_check;
ALTER TABLE template ADD CONSTRAINT template_grouping_dimension_check
    CHECK (grouping_dimension IN ('ISSUE_TYPE', 'COMPONENT', 'BUNDLE'));
