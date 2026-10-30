"""REQ-04 阶段 5 浏览器走查前的只读核对：用 req 账号读 4 个走查 Client 的工作台摘要（不打印凭据）。"""
import json, site, sys
from pathlib import Path
sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402
acc = json.loads((Path.home() / "Code-MoR-backups/rq04-stage5/accounts.json").read_text())
ops = e2e_http.Operations(acc["req"]["email"], acc["req"]["password"])
for cid in sys.argv[1:]:
    w = ops.get(f"/risk-limit-tiers/v1/clients/{cid}/workspace")
    t = w.get("tier") or {}
    print(cid, w["client"].get("displayName"), t.get("tier"), t.get("crr"), "v", t.get("version"), "prev", t.get("previousTier"),
          "quotas", [(q["currency"], q.get("day"), q.get("usedToday")) for q in t.get("quotas") or []], "unconf", t.get("unconfiguredCurrencies"),
          "checks", [(c["code"], c["status"]) for c in w["checklist"]["items"]], "history", len(w["history"]["items"]),
          "pending", [h["changeId"] for h in w["history"]["items"] if h["status"] == "PENDING"], "canReq", w.get("canRequest"), w.get("requestableTargets"))
