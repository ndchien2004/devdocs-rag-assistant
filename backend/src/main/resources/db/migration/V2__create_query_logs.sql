CREATE TABLE query_logs (
    id                  BIGSERIAL PRIMARY KEY,
    question            TEXT          NOT NULL,
    topic               VARCHAR(20),
    top_k               INT           NOT NULL,
    retrieved_count     INT           NOT NULL,
    max_score           DOUBLE PRECISION,
    found               BOOLEAN       NOT NULL,
    retrieval_ms        BIGINT        NOT NULL,
    llm_ms              BIGINT,
    total_ms            BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_logs_created_at ON query_logs (created_at);
