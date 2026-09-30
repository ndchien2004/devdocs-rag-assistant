#!/usr/bin/env bash
# Nạp các tài liệu mẫu trong sample-docs/ qua API. Backend phải đang chạy.
#   API_URL=http://localhost:8080 ./scripts/ingest-samples.sh
set -uo pipefail
cd "$(dirname "$0")/.."
API_URL="${API_URL:-http://localhost:8080}"

upload() { # $1 = file, $2 = topic, $3 = content-type
  printf '%-28s %-10s ' "$1" "$2"
  curl -s -w '  HTTP %{http_code}\n' \
    -F "file=@sample-docs/$1;type=$3" -F "topic=$2" \
    "$API_URL/api/v1/documents" \
    | sed -E 's/.*"status":"([A-Z]+)".*"pageCount":([0-9null]+),"chunkCount":([0-9null]+).*(HTTP [0-9]+)/\1 pages=\2 chunks=\3 \4/'
}

upload 01_Java_Core.md      JAVA      text/markdown
upload 02_Spring_Boot.pdf   SPRING    application/pdf
upload 03_Hibernate_JPA.pdf HIBERNATE application/pdf
upload 04_SQL_Database.md   DATABASE  text/markdown
upload 05_React.md          REACT     text/markdown
