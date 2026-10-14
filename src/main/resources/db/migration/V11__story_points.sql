-- INSIGHT-04: story_points_read separates "never read" (rows from before V11, or an unresolved site field) from "read, no estimate" (NULL points, read TRUE).
-- No backfill: OPEN releases fill on refresh, FROZEN releases keep their audit snapshot.
ALTER TABLE release_ticket_item ADD COLUMN story_points NUMERIC, ADD COLUMN story_points_read BOOLEAN NOT NULL DEFAULT FALSE;
