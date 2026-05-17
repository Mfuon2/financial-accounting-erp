-- ============================================================
-- V20: Refresh token store (server-side revocation support)
-- ============================================================
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    revoked_by  UUID,
    user_agent  VARCHAR(500),
    client_ip   VARCHAR(45),
    CONSTRAINT uq_rt_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_rt_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_rt_expires    ON refresh_tokens(expires_at);
CREATE INDEX idx_rt_token_hash ON refresh_tokens(token_hash);
