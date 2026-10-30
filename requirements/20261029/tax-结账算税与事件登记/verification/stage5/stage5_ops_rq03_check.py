"""tax 阶段 5：合并 ops-bff 替换后，经 Nginx 18444 只读验证 REQ-03 承保接口仍可应答（账号 lead，凭据读
~/Code-MoR-backups/rq03-stage5/accounts.json，不打印）。只调用 GET 与纯计算的 POST /previews（不写库）。
用法：python3 -I stage5_ops_rq03_check.py"""
import json
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

UW = "/risk-underwriting/v1"
lead = json.loads((Path.home() / "Code-MoR-backups/rq03-stage5/accounts.json").read_text())["lead"]
ops = e2e_http.Operations(lead["email"], lead["password"])


def check(label, fn):
    try:
        body = fn()
        keys = sorted(body)[:8] if isinstance(body, dict) else type(body).__name__
        print(json.dumps({"check": label, "status": 200, "keys": keys}, ensure_ascii=False))
        return body
    except e2e_http.Failure as failure:
        print(json.dumps({"check": label, "status": failure.status, "body": failure.body[:200]}, ensure_ascii=False))
        return None


listing = check("list", lambda: ops.get(f"{UW}/underwritings?page=1&size=10"))
items = (listing or {}).get("items") or []
print(json.dumps({"listTotal": (listing or {}).get("total"), "firstStatus": items[0].get("status") if items else None}))
client = items[0].get("clientId") if items else None
if client:
    check("workspace", lambda: ops.get(f"{UW}/clients/{client}/workspace"))
    check("history", lambda: ops.get(f"{UW}/clients/{client}/underwritings"))
    check("detail", lambda: ops.get(f"{UW}/underwritings/{items[0]['underwritingId']}"))
    preview = check("preview", lambda: ops.send("POST", f"{UW}/previews", {"clientId": client, "suggestedSettlementDays": None}, key=False))
check("statistics", lambda: ops.get(f"{UW}/underwriting-statistics"))
