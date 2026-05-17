-- ============================================================
-- V21: Organization (Entity) master data
-- The entityId UUID used throughout the system maps to
-- a record in this table. Every account, journal entry,
-- ledger entry, invoice etc. is scoped to one organization.
-- ============================================================

CREATE TABLE organizations (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    legal_name              VARCHAR(255),
    registration_number     VARCHAR(100),
    tax_identification_number VARCHAR(100),
    functional_currency     VARCHAR(3)   NOT NULL DEFAULT 'USD',
    reporting_currency      VARCHAR(3)   NOT NULL DEFAULT 'USD',
    country_code            VARCHAR(2)   NOT NULL DEFAULT 'KE',
    timezone                VARCHAR(100) NOT NULL DEFAULT 'Africa/Nairobi',
    fiscal_year_start_month INT          NOT NULL DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(100),
    postal_code             VARCHAR(20),
    phone                   VARCHAR(30),
    email                   VARCHAR(255),
    website                 VARCHAR(255),
    logo_url                VARCHAR(500),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID         NOT NULL,
    modified_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by             UUID         NOT NULL,
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    deactivated_at          TIMESTAMPTZ,
    deactivated_by          UUID,
    deactivation_reason     TEXT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_org_registration UNIQUE (registration_number),
    CONSTRAINT uq_org_name         UNIQUE (name)
);

CREATE INDEX idx_organizations_status   ON organizations(status);
CREATE INDEX idx_organizations_country  ON organizations(country_code);
