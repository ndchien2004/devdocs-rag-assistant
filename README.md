# 📚 DevDocs RAG Assistant

Hệ thống hỏi đáp (RAG — Retrieval-Augmented Generation) trên chính bộ tài liệu học lập trình của bạn,
xây dựng bằng **Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL/pgvector · Ollama**.

> 🚧 Đang phát triển theo từng phase — đặc tả đầy đủ: [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).

## Tiến độ

- [x] Phase 0 — Hello Spring AI (chat, embedding, cosine similarity)
- [x] Phase 1 — Ingestion pipeline (PDF/Markdown → chunk → pgvector)
- [x] Phase 2 — RAG thủ công
- [x] Phase 3 — Evaluation
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

## Phase 3 — Evaluation

Bộ eval [`eval-dataset.json`](backend/src/main/resources/eval/eval-dataset.json) gồm 30 câu hỏi viết tay trên tài liệu mẫu,
mỗi câu gắn trang/mục chứa đáp án:

| Nhóm | Số câu | Kiểm tra |
|---|---:|---|
| `DIRECT` — hỏi thẳng khái niệm | 12 | retrieval cơ bản |
| `PARAPHRASE` — diễn đạt khác tài liệu | 8 | sức mạnh embedding ngữ nghĩa |
| `CROSS_LINGUAL` — hỏi tiếng Việt, tài liệu tiếng Anh | 5 | khả năng đa ngôn ngữ |
| `OUT_OF_SCOPE` — ngoài phạm vi | 5 | từ chối đúng (BR-QRY-03) |

```bash
./scripts/run-eval.sh                                            # cấu hình mặc định
./scripts/run-eval.sh '{"topK":3,"similarityThreshold":0.6}'     # thử cấu hình khác
```

Eval chỉ đo **retrieval** (không gọi LLM). Chunk "đúng" = `file_name` khớp và `page_number` nằm trong `expected`.

### Baseline (chunk 500 token, K = 5, θ = 0.5, tìm trên mọi tài liệu)

| Cấu hình | Hit@1 | Hit@3 | Hit@5 | MRR | Out-of-scope Acc | Avg latency |
|---|---:|---:|---:|---:|---:|---:|
| Baseline (chunk 500, K=5, θ=0.5) | 84.0% | 96.0% | 100% | 0.903 | 100% | ~20 ms |

Theo nhóm câu hỏi: DIRECT Hit@1 = 100%, PARAPHRASE 75%, CROSS_LINGUAL 60%.
Báo cáo đầy đủ từng câu: [`docs/eval/phase3-baseline.json`](docs/eval/phase3-baseline.json).

Đọc kết quả một cách trung thực:

- **Hit@5 = 100% không có nghĩa là hệ thống hoàn hảo.** Bộ tài liệu nhỏ (54 chunk), mỗi trang một chủ đề rõ ràng,
  và câu hỏi do chính người viết tài liệu đặt ra → kết quả lạc quan hơn tài liệu thật (sách dày, nhiều trang na ná nhau).
- **Hỏi tiếng Việt trên tài liệu tiếng Anh yếu nhất** (Hit@1 60%): "cơ chế tự phát hiện thay đổi của entity" không
  khớp được với "dirty checking" — chunk đúng chỉ đứng thứ 4. Thuật ngữ kỹ thuật dịch sang tiếng Việt làm mất tín hiệu.
- **Biên an toàn của ngưỡng rất mỏng**: chunk đúng của các câu khó chỉ đạt score 0.52–0.58, trong khi câu ngoài
  phạm vi cao nhất 0.39. θ = 0.5 đang nằm vừa khít giữa hai nhóm — Phase 4 đo xem xê dịch θ ảnh hưởng thế nào.
- Độ trễ retrieval (embedding câu hỏi bằng bge-m3 trên GPU + HNSW search) ~20 ms khi model đã nạp; lần gọi đầu
  ~470 ms do Ollama nạp model vào VRAM.
