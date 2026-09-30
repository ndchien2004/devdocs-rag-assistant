#!/usr/bin/env bash
# Phase 4 — chạy các thí nghiệm E1..E5, lưu báo cáo JSON vào docs/eval/phase4/ và in bảng tổng hợp.
# Backend phải đang chạy và đã nạp tài liệu mẫu. E5 gọi LLM thật nên mất vài phút.
#   ./scripts/run-experiments.sh            # E1..E5
#   SKIP_E5=1 ./scripts/run-experiments.sh  # chỉ các thí nghiệm retrieval
set -euo pipefail
cd "$(dirname "$0")/.."
API_URL="${API_URL:-http://localhost:8080}"
OUT=docs/eval/phase4
mkdir -p "$OUT"

post() { # $1 = path, $2 = body, $3 = output file
  curl -sf --max-time 1800 -X POST "$API_URL$1" -H 'Content-Type: application/json' -d "$2" > "$OUT/$3"
  echo "  saved $OUT/$3"
}

echo "E1 — chunk size (TokenTextSplitter, K=5, θ=0.5)"
for size in 150 300 500 800; do
  post /api/v1/evaluation/experiments/chunking "{\"chunkSize\":$size,\"strategy\":\"TOKEN\"}" "e1-token-$size.json"
done

echo "E2 — similarity threshold (chunk 500, K=5)"
for t in 0.3 0.4 0.5 0.6 0.7; do
  post /api/v1/evaluation/run "{\"similarityThreshold\":$t}" "e2-threshold-$t.json"
done

echo "E3 — top-K (chunk 500, θ=0.5)"
for k in 1 3 5 8; do
  post /api/v1/evaluation/run "{\"topK\":$k}" "e3-topk-$k.json"
done

echo "E4 — overlap (WINDOW splitter, chunk 150, K=5, θ=0.5)"
for o in 0 30 60; do
  post /api/v1/evaluation/experiments/chunking "{\"chunkSize\":150,\"chunkOverlap\":$o,\"strategy\":\"WINDOW\"}" "e4-window-150-o$o.json"
done

if [[ -z "${SKIP_E5:-}" ]]; then
  echo "E5 — manual RAG vs Advisor (answer level, calls the LLM)"
  for mode in MANUAL QA_ADVISOR RAG_ADVISOR; do
    curl -sf --max-time 3600 -X POST "$API_URL/api/v1/evaluation/answers?mode=$mode" > "$OUT/e5-$mode.json"
    echo "  saved $OUT/e5-$mode.json"
  done
fi

python scripts/experiments_summary.py "$OUT"
