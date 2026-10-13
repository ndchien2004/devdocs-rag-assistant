CREATE TABLE conversations (
    id          UUID PRIMARY KEY,
    title       VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_document_scopes (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, document_id)
);

CREATE INDEX idx_conversations_updated_at ON conversations (updated_at DESC);

CREATE TABLE conversation_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    sequence_no     INT NOT NULL,
    content         TEXT NOT NULL,
    found           BOOLEAN,
    sources         JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_conversation_message_sequence ON conversation_messages (conversation_id, sequence_no);
