-- Ghi log cho mọi RagMode (BR-QRY-07), không chỉ MANUAL.
-- Advisor tự retrieve bên trong lời gọi LLM nên không tách được thời gian retrieval → retrieval_ms cho phép NULL.
ALTER TABLE query_logs ADD COLUMN mode VARCHAR(20);
UPDATE query_logs SET mode = 'MANUAL';
ALTER TABLE query_logs ALTER COLUMN mode SET NOT NULL;
ALTER TABLE query_logs ALTER COLUMN retrieval_ms DROP NOT NULL;
