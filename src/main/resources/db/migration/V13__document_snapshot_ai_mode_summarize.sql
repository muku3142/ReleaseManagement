-- D-14-13: summaries can be saved as AI-assisted revisions (reverses D-14-03).
ALTER TABLE document_snapshot DROP CONSTRAINT IF EXISTS document_snapshot_ai_mode_check;
ALTER TABLE document_snapshot ADD CONSTRAINT document_snapshot_ai_mode_check CHECK (ai_mode IN ('CLEAN_UP', 'CUSTOM', 'SUMMARIZE'));
