-- ADO-04: one shared, org-scoped Azure DevOps PAT (Phase 6 credential model).
--
-- Singleton row (id is pinned to 1). expires_on is entered by the user because no Azure DevOps API
-- exposes a PAT's own validTo; global PATs stop working on 2026-12-01, so expiry must be tracked
-- from the start. The token is stored only through EncryptedTokenConverter (AES-256-GCM, key version).
CREATE TABLE ado_credential (
    id               SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    organization     VARCHAR(64)  NOT NULL,
    encrypted_token  TEXT         NOT NULL,
    key_version      SMALLINT     NOT NULL,
    expires_on       DATE         NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       VARCHAR(255) NOT NULL
);
