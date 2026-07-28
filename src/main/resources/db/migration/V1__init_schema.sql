CREATE TABLE jira_site_config (
    id          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    base_url    VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL
);

CREATE TABLE jira_credential (
    jira_account_id  VARCHAR(255) PRIMARY KEY,
    jira_email       VARCHAR(255) NOT NULL,
    encrypted_token  TEXT NOT NULL,
    key_version      SMALLINT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
