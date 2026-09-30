#!/usr/bin/env bash
# Sinh lại các PDF mẫu từ sample-docs/src/*.txt
# Cần chạy `./mvnw compile` trong backend/ một lần trước để PDFBox có trong ~/.m2.
set -euo pipefail
cd "$(dirname "$0")/.."

M2="${HOME}/.m2/repository"
SEP=":"
to_native() { echo "$1"; }
if command -v cygpath >/dev/null 2>&1; then   # Git Bash trên Windows
  SEP=";"
  to_native() { cygpath -w "$1"; }
fi

latest_jar() { # $1 = thư mục artifact, $2 = artifactId
  local version
  version=$(ls "$1" | grep -E '^[0-9]' | sort -V | while read -r v; do
    [[ -f "$1/$v/$2-$v.jar" ]] && echo "$v"; done | tail -1)
  [[ -n "$version" ]] || { echo "Missing $2 in $1 — run ./mvnw compile in backend/ first" >&2; exit 1; }
  to_native "$1/$version/$2-$version.jar"
}

CP="$(latest_jar "$M2/org/apache/pdfbox/pdfbox" pdfbox)"
CP="$CP$SEP$(latest_jar "$M2/org/apache/pdfbox/pdfbox-io" pdfbox-io)"
CP="$CP$SEP$(latest_jar "$M2/org/apache/pdfbox/fontbox" fontbox)"
CP="$CP$SEP$(latest_jar "$M2/commons-logging/commons-logging" commons-logging)"

for src in src/*.txt; do
  name="$(basename "$src" .txt)"
  java -cp "$CP" tools/MakePdf.java "$src" "$name.pdf"
done
