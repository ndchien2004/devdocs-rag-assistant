"""Gộp các báo cáo JSON của Phase 4 (docs/eval/phase4) thành các bảng Markdown."""
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding="utf-8")
out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "docs/eval/phase4")


def load(name):
    path = out / name
    if not path.exists() or path.stat().st_size == 0:
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def pct(v):
    return "—" if v is None else f"{v:.1f}%"


def retrieval_table(title, rows):
    print(f"\n### {title}\n")
    print("| Cấu hình | Chunks | Hit@1 | Hit@3 | Hit@5 | Hit@K | MRR@K | In-scope found | Out-of-scope Acc | Avg latency |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for label, name in rows:
        r = load(name)
        if not r:
            continue
        s, c = r["summary"], r["config"]
        chunks = c["label"].split("(")[-1].split(" ")[0] if "(" in c["label"] else "54"
        print(f"| {label} | {chunks} | {pct(s['hitAt1'])} | {pct(s['hitAt3'])} | {pct(s['hitAt5'])} "
              f"| {pct(s['hitAtK'])} | {s['mrr']:.3f} | {pct(s['inScopeFoundRate'])} "
              f"| {pct(s['outOfScopeAccuracy'])} | {s['avgLatencyMs']:.0f} ms |")


retrieval_table("E1 — Chunk size (TokenTextSplitter, K = 5, θ = 0.5)",
                [(f"chunk {n}", f"e1-token-{n}.json") for n in (150, 300, 500, 800)])
retrieval_table("E2 — Similarity threshold (chunk 500, K = 5)",
                [(f"θ = {t}", f"e2-threshold-{t}.json") for t in ("0.3", "0.4", "0.5", "0.6", "0.7")])
retrieval_table("E3 — Top-K (chunk 500, θ = 0.5)",
                [(f"K = {k}", f"e3-topk-{k}.json") for k in (1, 3, 5, 8)])
retrieval_table("E4 — Overlap (WINDOW splitter, chunk 150, K = 5, θ = 0.5)",
                [(f"overlap {o}", f"e4-window-150-o{o}.json") for o in (0, 30, 60)])

rows = [(m, load(f"e5-{m}.json")) for m in ("MANUAL", "QA_ADVISOR", "RAG_ADVISOR")]
if any(r for _, r in rows):
    print("\n### E5 — RAG thủ công vs Advisor (mức câu trả lời, qwen2.5:3b, K = 5, θ = 0.5)\n")
    print("| Mode | In-scope trả lời | Có trích dẫn [n] | Nguồn đúng trang | Số nguồn TB | Out-of-scope từ chối "
          "| LLM bị gọi cho câu ngoài phạm vi | Latency TB (trong / ngoài phạm vi) |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|")
    for mode, r in rows:
        if not r:
            continue
        s = r["summary"]
        print(f"| {mode} | {pct(s['inScopeAnsweredRate'])} | {pct(s['citationRate'])} | {pct(s['sourceAccuracy'])} "
              f"| {s['avgSourceCount']} | {pct(s['outOfScopeRefusalRate'])} | {s['outOfScopeLlmCalls']}/5 "
              f"| {s['avgInScopeLatencyMs']:.0f} ms / {s['avgOutOfScopeLatencyMs']:.0f} ms |")
