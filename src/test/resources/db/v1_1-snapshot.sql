-- Synthetic, anonymised data shaped like a v1.1 (schema V13) database. Invented names, keys and accounts only.

INSERT INTO release (id, name, version, status, jira_project_key, created_at, created_by, frozen_at, frozen_by, last_refreshed_at, items_changed_at) VALUES
 ('00000000-0000-0000-0000-0000000000a1', 'Orion', '1.0', 'FROZEN', 'ALPHA', '2025-01-10T09:00:00Z', 'acc-demo-1', '2025-01-12T09:00:00Z', 'acc-demo-1', '2025-01-11T09:00:00Z', NULL),
 ('00000000-0000-0000-0000-0000000000a2', 'Orion', '1.1', 'FROZEN', 'ALPHA', '2025-02-10T09:00:00Z', 'acc-demo-1', '2025-02-12T09:00:00Z', 'acc-demo-1', '2025-02-11T09:00:00Z', '2025-02-11T10:00:00Z'),
 ('00000000-0000-0000-0000-0000000000a3', 'Lyra', '2.0', 'FROZEN', 'BETA', '2025-03-10T09:00:00Z', 'acc-demo-2', '2025-03-12T09:00:00Z', 'acc-demo-2', NULL, '2025-03-11T10:00:00Z'),
 ('00000000-0000-0000-0000-0000000000a4', 'Orion', '1.2', 'OPEN', 'alpha', '2025-04-10T09:00:00Z', 'acc-demo-1', NULL, NULL, NULL, '2025-04-11T10:00:00Z'),
 ('00000000-0000-0000-0000-0000000000a5', 'Misc', '0.1', 'OPEN', NULL, '2025-05-10T09:00:00Z', 'acc-demo-2', NULL, NULL, NULL, NULL);

INSERT INTO release_ticket_item (id, release_id, ticket_key, summary, issue_type, status, resolution, assignee, reporter, fix_versions, description, comments, comment_total, component, story_points, story_points_read) VALUES
 ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000000a1', 'ALPHA-1', 'Add login page', 'Story', 'Done', 'Done', 'Demo Dev One', 'Demo Rep', '["1.0"]', 'Plain description', '[]', 0, 'Web', NULL, FALSE),
 ('00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000000a2', 'ALPHA-2', 'Fix export crash', 'Bug', 'Done', 'Done', 'Demo Dev Two', 'Demo Rep', '["1.1"]', 'Crash on export', '[]', 0, NULL, 3, TRUE),
 ('00000000-0000-0000-0000-0000000001a3', '00000000-0000-0000-0000-0000000000a3', 'BETA-7', 'Faster search', 'Story', 'Done', 'Done', NULL, 'Demo Rep', '["2.0"]', 'Index tuning', '[]', 0, 'API', 5, TRUE),
 ('00000000-0000-0000-0000-0000000001a4', '00000000-0000-0000-0000-0000000000a4', 'ALPHA-9', 'Dark mode', 'Story', 'In Progress', NULL, 'Demo Dev One', 'Demo Rep', '[]', 'Theme', '[]', 0, 'Web', NULL, FALSE);

UPDATE release_ticket_item SET jira_issue_id = '10042', jira_updated_at = '2025-02-11T08:00:00Z', jira_status_category = 'DONE',
    enrichment_status = 'LINKED', enrichment_incomplete = FALSE, enrichment_attempted_at = '2025-02-11T09:00:00Z',
    commits_source = 'KEY_TAGGED', branches = '["feature/ALPHA-2"]', history_state = 'AVAILABLE',
    history_synced_updated_at = '2025-02-11T08:00:00Z', first_done_at = '2025-02-10T12:00:00Z', reopen_count = 0
 WHERE id = '00000000-0000-0000-0000-0000000001a2';

INSERT INTO release_manual_item (id, release_id, title, description, category) VALUES
 ('00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000000a2', 'Raise pool size', 'Set pool to 20', 'CONFIG_CHANGE');

INSERT INTO promotion (id, release_id, stage_id, outcome, deployed_at, recorded_at, recorded_by, note, supersedes_id, correction_reason) VALUES
 ('00000000-0000-0000-0000-0000000003a1', '00000000-0000-0000-0000-0000000000a1', (SELECT id FROM environment_stage WHERE name = 'DEV'), 'SUCCESS', '2025-01-13T09:00:00Z', '2025-01-13T09:05:00Z', 'acc-demo-1', 'dev ok', NULL, NULL),
 ('00000000-0000-0000-0000-0000000003a2', '00000000-0000-0000-0000-0000000000a1', (SELECT id FROM environment_stage WHERE name = 'PROD'), 'SUCCESS', '2025-01-20T09:00:00Z', '2025-01-20T09:05:00Z', 'acc-demo-1', NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000003a3', '00000000-0000-0000-0000-0000000000a1', (SELECT id FROM environment_stage WHERE name = 'PROD'), 'FAILED', '2025-01-20T09:00:00Z', '2025-01-21T09:05:00Z', 'acc-demo-1', NULL, '00000000-0000-0000-0000-0000000003a2', 'wrong outcome entered'),
 ('00000000-0000-0000-0000-0000000003a4', '00000000-0000-0000-0000-0000000000a1', (SELECT id FROM environment_stage WHERE name = 'PROD'), 'VOIDED', '2025-01-20T09:00:00Z', '2025-01-22T09:05:00Z', 'acc-demo-1', NULL, '00000000-0000-0000-0000-0000000003a3', 'entered by mistake'),
 ('00000000-0000-0000-0000-0000000003a5', '00000000-0000-0000-0000-0000000000a2', (SELECT id FROM environment_stage WHERE name = 'DEV'), 'SUCCESS', '2025-02-13T09:00:00Z', '2025-02-13T09:05:00Z', 'acc-demo-1', NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000003a6', '00000000-0000-0000-0000-0000000000a3', (SELECT id FROM environment_stage WHERE name = 'DEV'), 'SUCCESS', '2025-03-13T09:00:00Z', '2025-03-13T09:05:00Z', 'acc-demo-2', NULL, NULL, NULL);

INSERT INTO template (id, name, document_type, body, active, created_by, updated_by) VALUES
 ('00000000-0000-0000-0000-0000000004a1', 'Default notes', 'RELEASE_NOTES', '# {{release.name}}', TRUE, 'acc-demo-1', 'acc-demo-1');

INSERT INTO document_snapshot (id, release_id, document_type, revision, markdown, template_id, template_name, template_body,
                               cover_enabled, cover_title, cover_subtitle, header_text, footer_text, page_numbers_enabled,
                               generated_at, generated_by, ai_mode, ai_instruction, ai_source_revision, basis_generated_at) VALUES
 ('00000000-0000-0000-0000-0000000005a1', '00000000-0000-0000-0000-0000000000a2', 'RELEASE_NOTES', 1, '# Orion', '00000000-0000-0000-0000-0000000004a1', 'Default notes', '# {{release.name}}',
  FALSE, '', '', '', '', FALSE, '2025-02-12T10:00:00Z', 'acc-demo-1', NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000000a2', 'RELEASE_NOTES', 2, '# Orion (tidied)', '00000000-0000-0000-0000-0000000004a1', 'Default notes', '# {{release.name}}',
  FALSE, '', '', '', '', FALSE, '2025-02-12T11:00:00Z', 'acc-demo-1', 'SUMMARIZE', 'keep it short', 1, '2025-02-12T10:00:00Z');
