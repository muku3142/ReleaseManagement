-- Logout previously only cleared cookies, so a refresh token captured before sign-out
-- stayed valid for its full TTL and could be chained to the absolute cap. Recording when
-- an account last invalidated its sessions lets refresh reject tokens issued before that
-- moment. Nullable: no rows have invalidated anything yet.
ALTER TABLE jira_credential ADD COLUMN sessions_invalidated_at TIMESTAMPTZ;
