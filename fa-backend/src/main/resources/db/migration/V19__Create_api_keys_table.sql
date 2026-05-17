-- ============================================================
-- V19: Integration Access — API Keys for third-party services
-- ============================================================

CREATE TABLE api_keys (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID        NOT NULL,
    key_id          VARCHAR(40) NOT NULL,     -- public identifier (prefix shown to client)
    key_hash        VARCHAR(255) NOT NULL,    -- SHA-256 of full secret
    name            VARCHAR(255) NOT NULL,    -- human label e.g. "ERP Integration - SAP"
    description     TEXT,
    role            VARCHAR(50)  NOT NULL DEFAULT 'DATA_ENTRY',  -- permissions this key gets
    scopes          TEXT,                     -- comma-separated: read:journals,write:invoices
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMPTZ,              -- null = never expires
    last_used_at    TIMESTAMPTZ,
    last_used_ip    VARCHAR(45),
    created_by      UUID        NOT NULL,     -- user who created this key
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID,
    revocation_reason TEXT,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_keys_key_id   UNIQUE (key_id),
    CONSTRAINT uq_api_keys_name_ent UNIQUE (entity_id, name)
);

CREATE INDEX idx_api_keys_entity_status ON api_keys(entity_id, status);
CREATE INDEX idx_api_keys_key_id        ON api_keys(key_id);
CREATE INDEX idx_api_keys_expires       ON api_keys(expires_at) WHERE expires_at IS NOT NULL;
