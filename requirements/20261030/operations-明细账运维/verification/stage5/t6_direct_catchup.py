"""D-3 绕行：直接调 ledger 私网追平（不经 BFF / Gateway），每次 maxBatches=9（8 KB 响应上限内），补到目标序号；记录每次耗时。
用法：python3 t6_direct_catchup.py <目标序号>"""
import statistics
import sys
import time
import t6lib as L

target = int(sys.argv[1])
st = L.load()
body = {"entityType": "OPERATING_ENTITY", "entityId": st["entityNo"], "currency": "USD", "shardNo": 0}
times, last = [], None
while True:
    cur = int(L.q("mor_ledger_v2e2e", "SELECT version FROM t_consumed_aggregate_version WHERE aggregate_id='OE.%s.USD.0'" % st["entityNo"])[0][0]) \
        if False else None
    t0 = time.monotonic()
    s, b = L.private("ledger", "POST", "/posting-streams/v1/catch-up", {**body, "maxBatches": min(9, target - (last or 0)) if last else 9},
                     L.actor_headers(st["makerId"]))
    dt = time.monotonic() - t0
    if s != 200:
        raise SystemExit(f"{s} {b}")
    last = int(b["lastSequence"])
    times.append((int(b["processedCount"]), dt))
    if last >= target or b["caughtUp"] or int(b["processedCount"]) == 0:
        break
batches = sum(n for n, _ in times)
per = [dt / n for n, dt in times if n]
res = {"calls": len(times), "batches": batches, "last": last, "totalSeconds": round(sum(dt for _, dt in times), 2),
       "perBatchMs": {"mean": round(statistics.mean(per) * 1000, 1), "median": round(statistics.median(per) * 1000, 1),
                      "p90": round(sorted(per)[int(len(per) * 0.9) - 1] * 1000, 1), "max": round(max(per) * 1000, 1)},
       "per9CallSeconds": {"mean": round(statistics.mean(dt for _, dt in times), 3), "max": round(max(dt for _, dt in times), 3)}}
res["extrapolated200Seconds"] = round(res["perBatchMs"]["mean"] * 200 / 1000, 2)
print(res)
st.setdefault("directCatchUp", []).append(res)
L.save(st)
