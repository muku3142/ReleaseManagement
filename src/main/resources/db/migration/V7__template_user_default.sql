-- D-05.4-03/D-05.4-04: a user's own default template per document type. Generation resolves
-- explicit choice, then this row, then the global active template.
--
-- account_id is the Jira accountId (principal.getName(), the same identifier every actor column
-- stores, D-05.3-01). It has no FK because no user table exists.
--
-- ON DELETE CASCADE: deleting a template drops the defaults pointing at it, and those users fall
-- back to the global active template. Template deletion is never blocked (D-04-13).
--
-- "The default's template has the same document_type as this row" is enforced by the set path
-- (TemplateRepositoryAdapter.setUserDefault copies the type from the template row), not by the table.
CREATE TABLE template_user_default (
    account_id     VARCHAR(255) NOT NULL,
    document_type  VARCHAR(32)  NOT NULL
                   CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    template_id    UUID         NOT NULL REFERENCES template (id) ON DELETE CASCADE,
    PRIMARY KEY (account_id, document_type)
);
