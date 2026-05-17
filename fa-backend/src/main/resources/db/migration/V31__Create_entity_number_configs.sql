CREATE TABLE entity_number_configs (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id  UUID         NOT NULL,
    module_key VARCHAR(30)  NOT NULL,
    prefix     VARCHAR(20)  NOT NULL,
    CONSTRAINT uq_entity_number_configs UNIQUE (entity_id, module_key)
);

CREATE INDEX idx_entity_number_configs_entity ON entity_number_configs (entity_id);
