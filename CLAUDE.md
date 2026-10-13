# CLAUDE.md — hướng dẫn cho AI agent làm việc trên repo này

Đọc file này trước khi sửa code. README.md dành cho người đọc (kiến trúc, số liệu, cách chạy);
file này ghi những gì agent cần để làm tiếp **mà không làm hỏng**: môi trường, quy tắc, bẫy, cách kiểm chứng.
Đặc tả gốc: `PROJECT_REQUIREMENTS.md` (mã quy tắc BR-ING-xx, BR-QRY-xx, TC-xx trong code/test lấy từ đó).

## Dự án trong 5 dòng

RAG hỏi đáp trên tài liệu học lập trình (PDF/Markdown) — trả lời tiếng Việt, chỉ dựa trên tài liệu, trích dẫn `[n]`.
Backend Spring Boot 4.1.1 + Spring AI 2.0.1 (Java 21, Maven wrapper) · PostgreSQL 16 + pgvector · Ollama
(embedding `bge-m3` 1024 chiều, chat `qwen2.5`) · Frontend React 19 + Vite 8 + Tailwind 4.
Phase 0–6 của đặc tả đã xong. Việc còn lại: "Phase mở rộng" (cuối file này).

## Chạy trên máy của chủ repo (Windows 11, RTX 3050 4 GB)

```powershell
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --wait   # postgres + ollama (model đã pull sẵn)
cd backend; .\mvnw.cmd spring-boot:run          # DB_PORT=5433, CHAT_MODEL=qwen2.5:3b lấy từ .env ở thư mục gốc
cd frontend; npm run dev                                                 # PHẢI là http://localhost:5173
```

- **Postgres ở cổng 5433** (5432 bị container khác của chủ repo chiếm — đừng dừng container đó). Backend đọc cùng
  file `.env` ở thư mục gốc với docker compose (`spring.config.import`), nên không cần set `DB_PORT` trong terminal;
  biến môi trường thật vẫn ghi đè `.env`. Postgres chưa chạy → backend in hướng dẫn ngắn (`DatabaseUnavailableFailureAnalyzer`).
- Sau khi đổi nhánh, chạy `.\mvnw.cmd clean spring-boot:run`: `target/classes` còn sót migration của nhánh cũ sẽ làm
  Flyway báo "Found more than one migration with version N".
- **Chat model trên máy này là `qwen2.5:3b`** (mặc định trong yml là `qwen2.5:7b` — chưa pull, không vừa VRAM).
- CORS chỉ cho `http://localhost:5173`; Vite để `strictPort: true`. Frontend ở cổng khác / `127.0.0.1` → trình duyệt
  báo "Không kết nối được tới backend" dù backend vẫn chạy.
- Trước khi start lại backend, kiểm tra cổng 8080/5173 còn tiến trình cũ không (lỗi "Port 8080 was already in use").
- DB đang có 5 tài liệu mẫu + tài liệu chủ repo tự nạp; vector đang index với chunk 150 / overlap 60.

## Kiểm chứng sau khi sửa (bắt buộc)

| Sửa gì | Chạy gì |
|---|---|
| Bất kỳ backend | `cd backend; .\mvnw.cmd verify` — 92+ test, cần **Docker đang chạy** (Testcontainers pgvector). JaCoCo fail build nếu class `*Service` < 70% line coverage → viết test cho service mới |
| Retrieval / chunking / prompt / ngưỡng | `bash scripts/run-eval.sh` (retrieval, không gọi LLM) và so với baseline; thay đổi lớn → `bash scripts/run-experiments.sh` (E5 gọi LLM thật ~10 phút) |
| Prompt / context | thêm `python scripts/check_prompt_injection.py 20` (TC-QRY-08, phải 20/20) |
| Frontend | `cd frontend; npm run build`, rồi `node scripts/e2e-screenshots.mjs` (cần backend + frontend + Chrome; ghi đè ảnh trong `docs/screenshots/`) |

Baseline hiện tại (chunk 150/overlap 60, K=5, θ=0.5, **chỉ 5 tài liệu mẫu**): Hit@1 100%, MRR 1.0, out-of-scope 100%.
Tài liệu nạp thêm ngoài `sample-docs/` sẽ làm thay đổi số liệu eval — muốn so sánh công bằng thì đo trên đúng 5 tài liệu mẫu.
Không bao giờ ghi số liệu giả vào README; mọi con số phải từ lần chạy thật (lưu JSON vào `docs/eval/`).

## Bản đồ code (backend `backend/src/main/java/com/chien/devdocs/`)

Tổ chức theo feature module, không theo tầng.

| Package | Class chính | Vai trò |
|---|---|---|
| `document/` | `IngestionService` | upload: validate (đuôi + Content-Type + chữ ký `%PDF-`, 20 MB) → SHA-256 chống trùng (409; bản ghi FAILED được nạp lại) → lưu file (`FileStorage`) → `ChunkingPipeline` → `vectorStore.add`. Không `@Transactional` cả luồng để trạng thái FAILED được lưu |
| | `ChunkingPipeline` | `DocumentLoader` (PDF 1 trang/Document; MD 1 mục/Document, `page_number` = số thứ tự mục) → `TextCleaner` → `ChunkSplitter` → gắn metadata |
| | `ChunkSplitter`, `OverlapTextSplitter` | `TOKEN` = TokenTextSplitter của Spring AI; `WINDOW` = splitter tự viết có overlap (mặc định hiện tại) |
| | `ChunkMetadata` | 5 key bắt buộc: `document_id`, `file_name`, `page_number`, `topic`, `chunk_index` (+ `section_title` cho MD) |
| `chat/` | `ChatService` | chọn `RagMode`: `MANUAL` (mặc định) → `RagService`; `QA_ADVISOR` / `RAG_ADVISOR` → `AdvisorRagService` |
| | `RagService` | RAG thủ công: `Retriever` → 0 hit thì trả "không tìm thấy" **không gọi LLM** → `PromptBuilder` → ChatClient → `RefusalDetector` → `SourceMapper` → `query_logs` |
| | `PromptBuilder` | đánh số `[n] (file — trang)`, cắt theo `max-context-tokens` (JTokkit), gọi `PromptInjectionGuard` |
| | `SourceMapper` | parse `[1][3]` / `[1, 3]` (bỏ qua code và số ngoài khoảng) → chỉ trả nguồn được trích; không có trích dẫn hợp lệ nào → `RagService` **giữ** câu trả lời, trả mọi chunk của CONTEXT làm nguồn, frontend gắn cảnh báo "không có trích dẫn", `query_logs.found = false` (log INFO ghi lý do). Chỉ câu từ chối (`RefusalDetector`) mới thành "không tìm thấy" |
| | `ConversationService` | hội thoại đã lưu + phạm vi tài liệu. `ask` / `askStream` = 2 transaction ngắn (đọc lịch sử / ghi tin nhắn có khóa dòng), **không** bọc lời gọi LLM; `askStream` ghi xong hai tin nhắn rồi mới phát `done`, dừng/lỗi giữa chừng thì không ghi. Tin nhắn trả lời lưu câu đã chuẩn hóa (`RagAnswer.toResponse()`), không phải câu thô của LLM. `RagService` tìm bằng câu hỏi hiện tại trước, chỉ ghép câu hỏi liền trước khi không ra hit; lịch sử trong prompt bị gỡ `[n]` và cắt theo `max-history-tokens` |
| `evaluation/` | `EvaluationService` | Hit@1/3/5, MRR@K… trên `eval/eval-dataset.json` (30 câu); nhận `VectorStore` bất kỳ |
| | `ExperimentService` | E1/E4: index vào bảng riêng `exp_<splitter>_<size>_o<overlap>`, không đụng `vector_store` |
| | `AnswerEvaluationService` | E5: eval mức câu trả lời (gọi LLM thật) |
| `common/exception/` | `ApiException` + các lớp con | kế thừa `ErrorResponseException` → Problem Details tự động; `GlobalExceptionHandler` map lỗi kết nối Ollama → 503 |
| `config/` | `RagProperties` | mọi tham số RAG ở `app.rag.*` (record, `@ConfigurationProperties`). Thêm tham số → thêm field vào record **và** sửa các test đang `new RagProperties(...)` theo vị trí |

Tài nguyên: `resources/prompts/rag-system.st`, `rag-user.st` · `db/migration/V*.sql` (Flyway; bảng `vector_store`
do PgVectorStore tự tạo) · `eval/eval-dataset.json`.
Frontend: `src/App.jsx` (layout, giữ state tài liệu + tin nhắn), `components/DocumentSidebar.jsx` (3 chế độ
collapsed/open/expanded, lưu localStorage), `pages/ChatPage.jsx`, `components/Composer.jsx`,
`AnswerMessage.jsx` (biến `[n]` thành nút), `SourceList.jsx`, `api/client.js`. Màu (token theo vai trò: `accent`, `muted`, `line`…) định nghĩa trong `src/index.css` (`@theme`), cùng các utility
kính `glass` / `glass-thin` / `glass-strong`, `liquid-sheen`, `btn-cyan`, `chip`; nền trắng thuần, kính tách khỏi nền nhờ viền phản quang + bóng đổ.
Phần tử có `backdrop-filter` giam `position: fixed` bên trong nó và làm popup tràn ra ngoài bị vẽ sai → `glass` đặt blur
trên `::after` (đừng chuyển lại lên chính panel); modal / overlay vẫn render qua `Portal` (`components/Overlay.jsx`);
`<dialog>` mở bằng `showModal()` thì không bị ảnh hưởng.

## Quy tắc bắt buộc

- **Commit message không có dòng `Co-Authored-By` hay attribution AI nào.** Chỉ push khi chủ repo yêu cầu.
- Mỗi commit là một thay đổi có nghĩa, message kiểu `feat(scope): ...` / `fix(...)`, build + test pass ở từng commit.
- Comment / Javadoc / thông báo lỗi cho người dùng viết **tiếng Việt**; tên class/method tiếng Anh. Giữ mật độ comment như code xung quanh.
- Controller chỉ nhận request + validate. `ChatClient`, `VectorStore`, `EmbeddingModel` chỉ inject vào service.
- Prompt nằm trong `resources/prompts/*.st`, không hard-code trong Java. Template dùng `{biến}` — nội dung prompt không được có dấu `{ }` lẻ.
- Filter vector store dựng bằng `FilterExpressionBuilder` từ enum/UUID — **không bao giờ ghép chuỗi người dùng** (NFR-06).
- **Migration Flyway: không bao giờ sửa hay đổi số file `V*.sql` đã có trên `main`.** Nhánh mới luôn lấy số kế tiếp
  của `main` tại thời điểm merge; hai nhánh song song cùng thêm migration thì nhánh merge sau phải đổi số của mình.
  DB dev đã chạy migration của nhánh chưa merge (số bị đổi sau đó) → xóa volume `pgdata` và tạo lại.
- Không bỏ `spring.ai.ollama.chat.num-predict` / `spring.http.clients.read-timeout`: qwen2.5:3b từng sinh 38.000+ token, treo request.
- Đổi embedding model = phải re-index toàn bộ và đổi `spring.ai.vectorstore.pgvector.dimensions`.
- Không commit tài liệu có bản quyền: tài liệu thật để trong `sample-docs/private/` (đã gitignore). Không commit `.env`.
- Chưa có LICENSE — chủ repo tự quyết, đừng thêm.

## Bẫy phiên bản (Spring Boot 4 / Spring AI 2.0 — tutorial trên mạng thường sai)

- Property Spring AI 2.0 **không có `.options`**: `spring.ai.ollama.chat.model`, `...chat.num-predict`,
  `spring.ai.ollama.embedding.model`. Kiểm tra key thật trong `META-INF/spring-configuration-metadata.json` của jar
  (`~/.m2/repository/org/springframework/ai/...`) thay vì đoán.
- Boot 4: starter `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`; timeout HTTP client là
  `spring.http.clients.*`; test slice import từ `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`;
  integration test dùng `RestTestClient` + `@AutoConfigureRestTestClient`; mock bean là `@MockitoBean`.
- JSON: Boot 4 dùng **Jackson 3** (`tools.jackson.databind`); annotation vẫn `com.fasterxml.jackson.annotation`.
- Module advisor: `spring-ai-vector-store-advisor` (tên mới) và `spring-ai-rag`. `QuestionAnswerAdvisor` mặc định
  prompt tiếng Anh, không đánh số context; advisor **luôn gọi LLM** kể cả khi không có chunk.
- `TokenCountEstimator` có nhiều method trừu tượng — cần đếm token thì truyền `ToIntFunction<String>` như `PromptBuilder`.
- Puppeteer 25 `page.screenshot()` trả `Uint8Array` (bọc `Buffer.from`); `gifenc`/`pngjs` là CommonJS (import default).
- Tra API thật bằng `javap -cp <jar> <class>` khi không chắc (trên Windows classpath ngăn cách bằng `;`).

## Bẫy môi trường Windows

- Tool Bash là Git Bash: `curl` là bản Windows → **không đọc được đường dẫn `/tmp/...`** và có thể làm hỏng tiếng Việt
  trong `-d '...'`. Gửi request có tiếng Việt / multipart bằng Python (`urllib`) hoặc file nằm trong thư mục repo.
- Python in tiếng Việt ra console: `sys.stdout.reconfigure(encoding="utf-8")`; đọc stdin bằng `sys.stdin.buffer`.
- Python trên Windows ghi stdin ở text mode sẽ đổi `\n`→`\r\n` (làm hỏng patch cho `git apply`) — dùng bytes.
- Repo dùng LF (`.gitattributes`), trừ `*.cmd`.
- Mạng chậm (~0.5–1 MB/s): tải model / image Docker mất hàng chục phút; đừng tưởng bị treo.

## Hành vi của LLM cần biết khi đánh giá thay đổi

- qwen2.5:3b không tất định: ~13% câu trả lời thiếu `[n]`, đôi khi hiểu sai context, lẫn chữ Trung, từ chối nhầm câu
  hỏi diễn đạt khác (Q16/Q18/Q20 trong bộ eval). Đừng kết luận từ 1–2 lần chạy — dùng `AnswerEvaluationService`.
- Bộ eval 25 câu trong phạm vi → 1 câu = 4 điểm %; chênh < ~8 điểm coi là nhiễu.
- `RefusalDetector` và `PromptInjectionGuard` là heuristic theo mẫu câu; thêm mẫu thì thêm test tương ứng.

## Việc có thể làm tiếp (Phase mở rộng trong đặc tả)

Đã xong: streaming SSE (`/chat/stream`, `/conversations/{id}/messages/stream` — `RagService.prepare/finish` dùng chung
cho call và stream), hội thoại nhiều lượt có lưu. Còn: viết lại câu hỏi nối tiếp bằng LLM, hybrid search (pgvector + `tsvector`), re-ranking, ingestion bất đồng bộ (`@Async`, 202 + polling),
LLM-as-a-Judge, observability (Micrometer/Actuator), thử `qwen2.5:7b` hoặc model qua API và đo lại E5.
