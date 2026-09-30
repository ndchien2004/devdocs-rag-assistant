# 📚 DevDocs RAG Assistant

Hệ thống hỏi đáp (RAG — Retrieval-Augmented Generation) trên chính bộ tài liệu học lập trình của bạn,
xây dựng bằng **Java 21 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL/pgvector · Ollama**.

> 🚧 Đang phát triển theo từng phase — đặc tả đầy đủ: [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).

## Tiến độ

- [x] Phase 0 — Hello Spring AI (chat, embedding, cosine similarity)
- [x] Phase 1 — Ingestion pipeline (PDF/Markdown → chunk → pgvector)
- [x] Phase 2 — RAG thủ công
- [x] Phase 3 — Evaluation
- [x] Phase 4 — Tối ưu & so sánh với Advisor
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
giữ nguyên code block) → cắt chunk → gắn 5 metadata bắt buộc → `vectorStore.add()` (bge-m3).

Baseline của đặc tả cắt bằng `TokenTextSplitter` 500 token (54 chunk cho 5 tài liệu mẫu, ~15 giây trên RTX 3050
Laptop 4 GB). Sau Phase 4, mặc định là `OverlapTextSplitter` 150 token / overlap 60 (243 chunk) — xem kết luận Phase 4.

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

## Phase 4 — Tối ưu và so sánh (số liệu thật)

Mỗi thí nghiệm chỉ đổi **một** tham số so với baseline (chunk 500, K = 5, θ = 0.5). Chạy lại toàn bộ:
`./scripts/run-experiments.sh` — báo cáo JSON từng lần chạy nằm trong [`docs/eval/phase4/`](docs/eval/phase4).
E1/E4 index lại tài liệu vào bảng vector **riêng** (`exp_<splitter>_<size>_o<overlap>`), không đụng dữ liệu chính.

> ⚠️ Bộ eval có 25 câu trong phạm vi → **1 câu = 4 điểm %**. Chênh lệch dưới ~8 điểm nên coi là nhiễu.

### E1 — Chunk size (TokenTextSplitter)

| Cấu hình | Chunks | Hit@1 | Hit@3 | Hit@5 | MRR | Out-of-scope Acc |
|---|---:|---:|---:|---:|---:|---:|
| chunk 150 | 122 | **96.0%** | 96.0% | 100% | **0.970** | 100% |
| chunk 300 | 68 | 84.0% | 92.0% | 96.0% | 0.890 | 100% |
| chunk 500 (baseline) | 54 | 84.0% | 96.0% | 100% | 0.903 | 100% |
| chunk 800 | 54 | 84.0% | 96.0% | 100% | 0.903 | 100% |

Chunk nhỏ tập trung vào một ý nên xếp hạng chính xác hơn. Chunk 800 = chunk 500 vì mỗi trang tài liệu mẫu < 500 token
và splitter không gộp các trang lại với nhau.

### E2 — Similarity threshold θ

| θ | Hit@1 | Hit@5 | In-scope found | Out-of-scope Acc |
|---|---:|---:|---:|---:|
| 0.3 | 84.0% | 100% | 100% | **0%** |
| 0.4 | 84.0% | 100% | 100% | 80% |
| **0.5** | 84.0% | 100% | **100%** | **100%** |
| 0.6 | 72.0% | 72.0% | 72% | 100% |
| 0.7 | 36.0% | 36.0% | 36% | 100% |

Giả thuyết đúng: θ cao tăng khả năng từ chối nhưng giết Hit@K. **θ là tham số nhạy nhất** — cửa sổ an toàn chỉ
nằm trong khoảng 0.4–0.5 với bge-m3; θ = 0.3 trả lời *mọi* câu ngoài phạm vi, θ = 0.7 từ chối 64% câu hợp lệ.

### E3 — Top-K

| K | Hit@K | MRR@K | Latency |
|---|---:|---:|---:|
| 1 | 84.0% | 0.840 | 15 ms |
| 3 | 96.0% | 0.893 | 15 ms |
| 5 | 100% | 0.903 | 16 ms |
| 8 | 100% | 0.903 | 15 ms |

K lớn hơn 5 không thêm gì cho retrieval ở kho 54 chunk; độ trễ retrieval gần như không đổi (HNSW). Chi phí thật của K
lớn nằm ở context dài hơn cho LLM.

### E4 — Overlap (OverlapTextSplitter tự viết, chunk 150)

So sánh **cùng một splitter** — chỉ thay overlap — để không lẫn tác động của việc đổi thuật toán cắt.

| Overlap | Chunks | Hit@1 | Hit@3 | Hit@5 | MRR |
|---|---:|---:|---:|---:|---:|
| 0 | 176 | 88.0% | 92.0% | 96.0% | 0.910 |
| 30 | 203 | 96.0% | 100% | 100% | 0.980 |
| **60** | 243 | **100%** | **100%** | **100%** | **1.000** |

Overlap giữ trọn những ý nằm ngay ranh giới chunk — đổi lại số chunk (và chi phí embedding/lưu trữ) tăng 38%.

### E5 — RAG thủ công vs Advisor (mức câu trả lời, gọi LLM thật)

Đo retrieval thì vô nghĩa (cả ba cùng gọi `VectorStore.similaritySearch`), nên E5 chạy 30 câu hỏi qua LLM
(qwen2.5:3b, chunk 500) và đo câu trả lời. `POST /api/v1/evaluation/answers?mode=...`

| Mode | In-scope trả lời | Có trích dẫn [n] | Nguồn đúng trang | Số nguồn TB | Out-of-scope từ chối | LLM bị gọi cho câu ngoài phạm vi | Latency TB (trong / ngoài phạm vi) |
|---|---:|---:|---:|---:|---:|---:|---:|
| `MANUAL` (Phase 2) | 92.0% | 87.0% | 91.3% | 1.5 | 100% | **0/5** | 2.9 s / **46 ms** |
| `QA_ADVISOR` (mặc định) | 52.0% | **0%** | 100% | 3.0 | 100% | 5/5 | 3.1 s / 831 ms |
| `RAG_ADVISOR` (cấu hình tương đương) | 92.0% | 91.3% | 95.7% | 1.3 | 100% | 5/5 | 2.3 s / 330 ms |

- **`RetrievalAugmentationAdvisor` cấu hình tương đương cho chất lượng ngang RAG thủ công**, với ít code hơn
  (`documentFormatter` dùng lại `PromptBuilder`) → giả thuyết E5 đúng, *nhưng chỉ khi cấu hình tương đương*.
- **`QuestionAnswerAdvisor` dùng mặc định kém hẳn**: template tiếng Anh khiến model trả lời bằng tiếng Anh, "rào đón"
  kiểu *"The context provided does not contain…"* (48% câu hợp lệ bị bỏ ngỏ), không có trích dẫn vì context không đánh số.
- **Khác biệt quan trọng nhất: advisor luôn gọi LLM**, kể cả khi không có chunk nào vượt ngưỡng. RAG thủ công trả
  "không tìm thấy" sau 46 ms mà không tốn lượt gọi LLM nào — đúng BR-QRY-03. Đây là lý do `MANUAL` là chế độ mặc định.

### Kết luận — cấu hình được chọn

| Tham số | Baseline | **Chọn** | Lý do |
|---|---|---|---|
| Splitter / chunk | Token 500 | **Window 150, overlap 60** | E1 + E4: Hit@1 84% → 100%, MRR 0.903 → 1.0 |
| θ | 0.5 | **0.5** | E2: điểm duy nhất vừa từ chối 100% câu ngoài phạm vi vừa giữ 100% câu hợp lệ |
| K | 5 | **5** | E3: K < 5 mất câu, K > 5 không thêm gì |
| RAG | — | **MANUAL** | E5: chất lượng ngang advisor cấu hình tốt, nhưng không gọi LLM khi không có context |

Kiểm chứng cấu hình được chọn ở mức câu trả lời (không chỉ retrieval), vì giả thuyết E1 lo chunk nhỏ "thiếu ngữ cảnh":

| Cấu hình (MANUAL) | Hit@1 | In-scope trả lời | Có trích dẫn | Nguồn đúng trang | Latency TB |
|---|---:|---:|---:|---:|---:|
| Token 500 (baseline) | 84.0% | 92.0% | 87.0% | 91.3% | 2.9 s |
| **Window 150 / overlap 60** | **100%** | 88.0% | 86.4% | **100%** | **1.8 s** |

Tỷ lệ trả lời giảm đúng 1 câu (Q16) — trong ngưỡng nhiễu; Q18/Q20 bị model từ chối ở cả hai cấu hình (model 3B quá
thận trọng với câu diễn đạt khác). Không thấy bằng chứng chunk 150 "thiếu ngữ cảnh" *trên bộ tài liệu mẫu* — với sách
thật (giải thích dài trải nhiều đoạn) cần đo lại.

### Chuyện thật khi chạy LLM nhỏ (qwen2.5:3b)

- **Vòng lặp vô hạn**: một lần model sinh **38.000+ token** cho một câu hỏi, request treo mãi →
  thêm `spring.ai.ollama.chat.num-predict: 1024` và `spring.http.clients.read-timeout` (hết giờ → 503).
- **Trích dẫn**: với prompt đúng nguyên văn đặc tả, cả 2 câu hỏi thử đều *không* có `[n]` (nên `sources` rơi về
  "trả toàn bộ chunk", kể cả chunk không liên quan). Thêm ví dụ định dạng vào system prompt và nhắc lại yêu cầu ngay
  sau câu hỏi → 87% câu trả lời có trích dẫn trên bộ eval. Tác dụng phụ: 1–2 câu model chép lại luôn câu nhắc.
- **Prompt injection (TC-QRY-08)**: system prompt chặn được việc model *làm theo* chỉ thị trong tài liệu, nhưng 1/10 lần
  model *chép* nguyên câu "…reply HACKED" vào câu trả lời. Thêm `PromptInjectionGuard` lược dòng dạng chỉ thị khỏi
  context → **20/20** lần sạch (`python scripts/check_prompt_injection.py 20`). Guard dựa trên mẫu câu, không chặn được
  mọi biến thể.
- Thỉnh thoảng lẫn vài chữ tiếng Trung / Indonesia, hoặc chép lại câu nhắc trong prompt — giới hạn của model 3B;
  model 7B+ (`CHAT_MODEL=qwen2.5:7b`) sẽ tốt hơn nhưng không vừa 4 GB VRAM của máy dev.
