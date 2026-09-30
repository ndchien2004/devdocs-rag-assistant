# Tài liệu mẫu

Bộ tài liệu nhỏ **tự viết cho dự án này** để demo và làm bộ đánh giá (eval). Không chứa nội dung có bản quyền của bên thứ ba.

| File | Ngôn ngữ | Topic | Đơn vị "trang" |
|---|---|---|---|
| `01_Java_Core.md` | Tiếng Việt | `JAVA` | mục (heading) — 11 mục |
| `02_Spring_Boot.pdf` | English | `SPRING` | trang PDF — 11 trang |
| `03_Hibernate_JPA.pdf` | English | `HIBERNATE` | trang PDF — 10 trang |
| `04_SQL_Database.md` | Tiếng Việt | `DATABASE` | mục (heading) — 11 mục |
| `05_React.md` | Tiếng Việt | `REACT` | mục (heading) — 10 mục |

- Hai file PDF được sinh từ `src/*.txt` bằng `tools/make-pdfs.sh` (PDFBox). Mỗi trang PDF có header/footer lặp lại — cố ý, để kiểm tra `TextCleaner`.
- Với Markdown, `page_number` trong metadata là **số thứ tự mục** (mục 1 = tiêu đề `#` và đoạn mở đầu).
- Tài liệu thật của bạn (có thể có bản quyền) đặt trong `sample-docs/private/` — thư mục này đã được `.gitignore`.

Nạp toàn bộ:

```bash
./scripts/ingest-samples.sh     # từ thư mục gốc repo, backend đang chạy
```
