# 📚 DevDocs RAG Assistant

Hệ thống hỏi đáp (RAG — Retrieval-Augmented Generation) trên chính bộ tài liệu học lập trình của bạn,
xây dựng bằng **Java 21 · Spring Boot 4 · Spring AI 2.0 · PostgreSQL/pgvector · Ollama**.

> 🚧 Đang phát triển theo từng phase — xem [PROJECT_REQUIREMENTS.md](PROJECT_REQUIREMENTS.md).

## Chạy nhanh

```bash
docker compose up -d                 # PostgreSQL + pgvector, Ollama
cd backend && ./mvnw spring-boot:run # tự pull bge-m3 + chat model ở lần chạy đầu
```
