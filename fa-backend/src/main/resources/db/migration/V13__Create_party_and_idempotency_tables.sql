-- §14.3 — Customer master data (Party module).
CREATE TABLE customers (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    customer_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_number VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(20),
    credit_limit DECIMAL(20,6) NOT NULL DEFAULT 0,
    payment_terms VARCHAR(50),
    default_ar_account_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    modified_at TIMESTAMP NOT NULL,
    modified_by UUID,
    deactivated_at TIMESTAMP,
    deactivated_by UUID,
    deactivation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_customers_entity_code UNIQUE (entity_id, customer_code),
    CONSTRAINT fk_customers_ar_account FOREIGN KEY (default_ar_account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_customers_credit_limit_nonneg CHECK (credit_limit >= 0)
);

-- §8.2 — Indexes (no CONCURRENTLY: Flyway runs each migration in a transaction and
-- CREATE INDEX CONCURRENTLY cannot run inside one).
CREATE INDEX idx_customers_entity_id ON customers(entity_id);
CREATE INDEX idx_customers_is_active ON customers(is_active);
CREATE INDEX idx_customers_customer_code ON customers(entity_id, customer_code);

-- §14.3 — Supplier master data (Party module — AP cycle).
CREATE TABLE suppliers (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    supplier_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_number VARCHAR(50),
    email VARCHAR(255),
    phone VARCHAR(20),
    payment_terms VARCHAR(50),
    default_ap_account_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    modified_at TIMESTAMP NOT NULL,
    modified_by UUID,
    deactivated_at TIMESTAMP,
    deactivated_by UUID,
    deactivation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_suppliers_entity_code UNIQUE (entity_id, supplier_code),
    CONSTRAINT fk_suppliers_ap_account FOREIGN KEY (default_ap_account_id) REFERENCES accounts(id) ON DELETE RESTRICT
);

CREATE INDEX idx_suppliers_entity_id ON suppliers(entity_id);
CREATE INDEX idx_suppliers_is_active ON suppliers(is_active);
CREATE INDEX idx_suppliers_supplier_code ON suppliers(entity_id, supplier_code);

-- §7.2 — Idempotency keys: write-through cache backing for IdempotencyService. The DB
-- is the source of truth; Redis is the read-side cache.
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    request_hash VARCHAR(255),
    response_body TEXT,
    created_at TIMESTAMP NOT NULL,
    ttl_expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key, entity_id)
);

CREATE INDEX idx_idempotency_key ON idempotency_keys(idempotency_key, entity_id);
CREATE INDEX idx_idempotency_entity ON idempotency_keys(entity_id);
CREATE INDEX idx_idempotency_ttl ON idempotency_keys(ttl_expires_at);
