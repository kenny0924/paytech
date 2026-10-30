"""AC-07 / 11 造落后：停 ledger → 经 billing 登记并批准 N 条事实（account 正常入账并发出 AccountPosted）→ 等 account 全部发出 →
只清空 ledger 自己的 AccountPosted 分区队列 ledger.account.p0—p3（/mor-e2e，不碰 DLQ 与其他服务队列）→ 启动 ledger。account 数据不丢。
用法：python3 t6_lag.py <N> [模式 数量]...   例：t6_lag.py 3 fe 1（3 条普通 + 1 条带服务费）；t6_lag.py 0 client2 1"""
import base64
import json
import subprocess
import sys
import time
import urllib.request
import t6lib as L

n = int(sys.argv[1])
st = L.load()
en = st["entityNo"]
RUN = str(L.HERE / "run-stack.sh")
QUEUES = ["ledger.account.p0", "ledger.account.p1", "ledger.account.p2", "ledger.account.p3"]
print(subprocess.run(["bash", RUN, "stop", "ledger"], capture_output=True, text=True).stdout.strip())
before = int(L.q("mor_account_v2e2e", f"SELECT IFNULL(MAX(posting_sequence),0) FROM t_account_posting WHERE entity_id='{en}'")[0][0])
t0 = time.monotonic()
if n:
    subprocess.run(["python3", str(L.HERE / "t6_facts.py"), str(n)], check=True)
extra = sys.argv[2:]
for mode, count in zip(extra[::2], extra[1::2]):
    subprocess.run(["python3", str(L.HERE / "t6_facts.py"), count, mode], check=True)
    n += int(count)
target = before + n
for _ in range(120):
    row = L.q("mor_account_v2e2e", f"SELECT MAX(posting_sequence), SUM(published_time IS NULL) FROM t_account_posting WHERE entity_id='{en}'")[0]
    if int(row[0]) >= target and row[1] == "0":
        break
    time.sleep(2)
print("account", row, f"{time.monotonic() - t0:.1f}s")
counts = {q: m for q, m, c in L.E.rabbit_queues("ledger.account.")}
print("queues before purge", counts)
auth = base64.b64encode(f"{L.E.LOCAL['MOR_RABBITMQ_USER']}:{L.E.LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
vhost = urllib.request.quote(L.E.RABBITMQ_VHOST, safe="")
for q in QUEUES:
    req = urllib.request.Request(f"{L.E.RABBITMQ_MANAGEMENT}/api/queues/{vhost}/{q}/contents", method="DELETE",
                                 headers={"Authorization": "Basic " + auth})
    urllib.request.urlopen(req, timeout=10).read()
print("queues after purge", {q: m for q, m, c in L.E.rabbit_queues("ledger.account.")})
print(subprocess.run(["bash", RUN, "start", "ledger"], capture_output=True, text=True).stdout.strip().splitlines()[-1])
st.setdefault("lag", []).append({"n": n, "accountFrom": before + 1, "accountTo": target, "purged": counts})
L.save(st)
