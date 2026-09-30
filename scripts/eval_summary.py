"""In bảng tóm tắt từ JSON của POST /api/v1/evaluation/run (đọc từ stdin)."""
import json
import sys

sys.stdout.reconfigure(encoding="utf-8")
r = json.loads(sys.stdin.buffer.read().decode("utf-8"))
c, s = r["config"], r["summary"]

print(f"config: {c['label']}  K={c['topK']}  threshold={c['similarityThreshold']}  "
      f"topicFilter={c['useTopicFilter']}  questions={c['questionCount']}")
print("| Hit@1 | Hit@3 | Hit@5 | MRR | In-scope found | Out-of-scope Acc | Avg latency |")
print("|---:|---:|---:|---:|---:|---:|---:|")
print(f"| {s['hitAt1']}% | {s['hitAt3']}% | {s['hitAt5']}% | {s['mrr']} | {s['inScopeFoundRate']}% "
      f"| {s['outOfScopeAccuracy']}% | {s['avgLatencyMs']} ms |")

misses = [d for d in r["details"] if d["category"] != "OUT_OF_SCOPE" and d["firstRelevantRank"] != 1]
wrong_oos = [d for d in r["details"] if d["category"] == "OUT_OF_SCOPE" and d["found"]]
if misses:
    print("\nCâu hỏi chưa xếp chunk đúng lên đầu:")
    for d in misses:
        top = ", ".join(f"{h['fileName']} p{h['pageNumber']} ({h['score']})" for h in d["retrieved"][:3])
        print(f"  {d['id']} [{d['category']}] rank={d['firstRelevantRank']}: {d['question']}\n      top-3: {top}")
if wrong_oos:
    print("\nCâu ngoài phạm vi bị trả lời nhầm:")
    for d in wrong_oos:
        top = ", ".join(f"{h['fileName']} p{h['pageNumber']} ({h['score']})" for h in d["retrieved"][:3])
        print(f"  {d['id']}: {d['question']}\n      top-3: {top}")
