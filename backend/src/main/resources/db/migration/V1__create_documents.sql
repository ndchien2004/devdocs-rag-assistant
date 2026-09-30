CREATE TABLE documents (
    id              UUID PRIMARY KEY,
    file_name       VARCHAR(255)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    file_size       BIGINT        NOT NULL,
    checksum        CHAR(64)      NOT NULL,          -- SHA-256 hex
    topic           VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,          -- PENDING | PROCESSING | INDEXED | FAILED
    page_count      INT,
    chunk_count     INT,
    error_message   TEXT,
    storage_path    VARCHAR(500)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    indexed_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_documents_checksum ON documents (checksum);
CREATE INDEX idx_documents_topic ON documents (topic);
