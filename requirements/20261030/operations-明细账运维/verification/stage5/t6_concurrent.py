"""AC-08 409：另一名有执行权限的运营（payout 阶段 5 的 checker，PLATFORM_ADMIN）经运营入口同时追平同一条流，ledger TRY_LOCK 冲突。
用法：python3 t6_concurrent.py api   两个账号并发各调一次；python3 t6_concurrent.py checker   只由 checker 调一次（供浏览器并发）"""
import json
import sys
import threading
from pathlib import Path
import t6lib as L

payout = json.loads((Path.home() / "Code-MoR-backups/payout-s1-stage5/state.json").read_text())["operators"]["checker"]
PATH = "/finance/posting-streams/v1/OE.ZQ344.USD.0/catch-up"
checker = L.login(payout["email"], payout["password"])
if sys.argv[1] == "checker":
    s, b, dt = L.ops("POST", PATH, checker)
    (L.HERE / "t6-concurrent-checker.json").write_text(json.dumps({"status": s, "body": b, "seconds": round(dt, 3)}, ensure_ascii=False))
    raise SystemExit
admin = L.login()
out = {}
def run(name, tok):
    out[name] = L.ops("POST", PATH, tok)
ts = [threading.Thread(target=run, args=(n, t)) for n, t in (("admin", admin), ("checker", checker))]
[t.start() for t in ts]; [t.join() for t in ts]
for n, (s, b, dt) in out.items():
    print(n, s, json.dumps(b, ensure_ascii=False)[:300], round(dt, 3))
