-- Source document file attachments
CREATE TABLE source_document_attachments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID        NOT NULL,
    document_id     UUID        NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT      NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    uploaded_by     UUID        NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active       BOOLEAN     NOT NULL DEFAULT true
);

CREATE INDEX idx_sda_document_id ON source_document_attachments(document_id);
CREATE INDEX idx_sda_entity_id   ON source_document_attachments(entity_id);
