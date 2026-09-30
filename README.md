# 📚 DevDocs RAG Assistant

Hệ thống hỏi đáp (RAG — Retrieval-Augmented Generation) trên chính bộ tài liệu học lập trình của bạn,
xây dựng bằng **Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL/pgvector · Ollama**.

> 🚧 Đang phát triển theo từng phase — đặc tả đầy đủ: [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).

## Tiến độ

- [x] Phase 0 — Hello Spring AI (chat, embedding, cosine similarity)
- [x] Phase 1 — Ingestion pipeline (PDF/Markdown → chunk → pgvector)
- [ ] Phase 2 — RAG thủ công
- [ ] Phase 3 — Evaluation
- [ ] Phase 4 — Tối ưu & so sánh với Advisor
- [ ] Phase 5 — Frontend React
- [ ] Phase 6 — Hoàn thiện portfolio

## Chạy nhanh

```bash
cp .env.example .env                       # đổi DB_PORT nếu 5432 đã bị chiếm
docker compose up -d                       # PostgreSQL + pgvector, Ollama
# GPU NVIDIA: docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d

docker exec -it devdocs-ollama ollama pull bge-m3
docker exec -it devdocs-ollama ollama pull qwen2.5:7b     # hoặc qwen2.5:3b nếu máy yếu

cd backend && ./mvnw spring-boot:run       # đọc biến môi trường DB_PORT, CHAT_MODEL...
./scripts/ingest-samples.sh                # nạp tài liệu mẫu (từ thư mục gốc)
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

## Phase 0 — Embedding hoạt động thế nào?

`GET /api/v1/playground/similarity?a=...&b=...` embedding hai câu bằng **bge-m3** (1024 chiều) rồi tính cosine similarity.
Kết quả chạy thật:

| Câu A | Câu B | Quan hệ | Cosine |
|---|---|---|---:|
| Spring Boot là framework Java | Spring Boot is a Java framework | cùng nghĩa, khác ngôn ngữ | **0.958** |
| Transaction bị rollback | Giao dịch bị hoàn tác | cùng nghĩa, khác từ | **0.713** |
| Transaction bị rollback | Hôm nay trời mưa | khác nghĩa | **0.491** |

Nhận xét:

- bge-m3 là model **đa ngôn ngữ**: câu tiếng Việt và bản dịch tiếng Anh gần như trùng vector (0.96) — nên hỏi bằng tiếng Việt trên tài liệu tiếng Anh vẫn tìm được.
- "rollback" ↔ "hoàn tác" không chung chữ nào nhưng vẫn đạt 0.71: embedding nắm **nghĩa**, không so khớp từ khóa.
- Hai câu không liên quan vẫn đạt ~0.49 chứ không gần 0. Cosine của bge-m3 có "mức nền" khá cao, nên ngưỡng
  `similarity-threshold = 0.5` trong đặc tả có thể **quá thấp** để chặn câu hỏi ngoài phạm vi — sẽ đo lại ở Phase 3–4.

## Phase 1 — Ingestion

```bash
curl -F "file=@sample-docs/02_Spring_Boot.pdf;type=application/pdf" -F topic=SPRING \
     http://localhost:8080/api/v1/documents
```

Luồng: validate (đuôi file + Content-Type + chữ ký `%PDF-`, ≤ 20 MB) → SHA-256 chống nạp trùng (409) →
đọc (PDF: 1 Document/trang; Markdown: 1 Document/mục) → `TextCleaner` (NFC, xóa header/footer lặp lại, gộp khoảng trắng,
giữ nguyên code block) → `TokenTextSplitter` (500 token) → gắn 5 metadata bắt buộc → `vectorStore.add()` (bge-m3).

Kết quả nạp 5 tài liệu mẫu: 54 chunk, ~15 giây (RTX 3050 Laptop 4 GB).

```sql
SELECT metadata->>'file_name', metadata->>'page_number', left(content, 80) FROM vector_store LIMIT 10;
```
