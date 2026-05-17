-- ============================================================
-- V18: User Management — users and password_reset_tokens
-- ============================================================

CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id               UUID NOT NULL,
    period_id               UUID,
    full_name               VARCHAR(255) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    role                    VARCHAR(50)  NOT NULL DEFAULT 'DATA_ENTRY',
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,
    email_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at       TIMESTAMPTZ,
    must_change_password    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID         NOT NULL,
    modified_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by             UUID         NOT NULL,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    deactivated_at          TIMESTAMPTZ,
    deactivated_by          UUID,
    deactivation_reason     TEXT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email_entity UNIQUE (entity_id, email)
);

CREATE INDEX idx_users_entity_role   ON users(entity_id, role);
CREATE INDEX idx_users_entity_status ON users(entity_id, status);
CREATE INDEX idx_users_email         ON users(email);

-- Password reset tokens (single-use, short-lived)
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_prt_token UNIQUE (token_hash)
);

CREATE INDEX idx_prt_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_prt_expires ON password_reset_tokens(expires_at);
