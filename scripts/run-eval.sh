#!/usr/bin/env bash
# Chạy bộ eval và in bảng tóm tắt. Backend phải đang chạy và đã nạp tài liệu mẫu.
#   ./scripts/run-eval.sh                                             # cấu hình mặc định (app.rag.*)
#   ./scripts/run-eval.sh '{"topK":3,"similarityThreshold":0.6}' docs/eval/k3.json
set -euo pipefail
cd "$(dirname "$0")/.."
API_URL="${API_URL:-http://localhost:8080}"
BODY="${1:-"{}"}"
OUT="${2:-}"

RESULT=$(curl -sf -X POST "$API_URL/api/v1/evaluation/run" -H 'Content-Type: application/json' -d "$BODY")
if [[ -n "$OUT" ]]; then
  mkdir -p "$(dirname "$OUT")"
  echo "$RESULT" > "$OUT"
fi
echo "$RESULT" | python scripts/eval_summary.py
