#!/usr/bin/env python3
"""REQ-12 阶段 5：为浏览器走查预置 3 个 Client 账号（经真实外部入口；凭据追加到 ~/Code-MoR-backups/rq12-stage5/accounts.json，0600）。
  D 空账号：门户新建申请；E AE 机构 DRAFT：已填 PRE_ORDER + 21 天、APPLE_PAY,CARD，未填退款政策；
  F AE 机构 DRAFT 改冻结 v2（本机测试数据，仅该申请）：门户应不出现新字段。
"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import stage5_e2e as T  # noqa: E402

run = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
out = {}
for label in ("D", "E", "F"):
    session = T.OA.client_register(f"RQ12{label}", run)
    out[label] = {"email": session.email, "password": session.password, "accountId": session.account_id}
    if label == "D":
        continue
    status, created = T.OA.create_application(session, "BROWSER", label, "AE", "ORGANIZATION", "LLC")
    app_id = created["applicationId"]
    out[label]["applicationId"] = app_id
    if label == "E":
        status, reply = T.patch(session, app_id, {"expectedPaymentMethods": "card,apple_pay", "fulfillmentType": "PRE_ORDER",
                                                  "fulfillmentDays": "21"})
        print("E draft", status, T.problem(reply) if status != 200 else "")
    else:
        T.root_sql(f"UPDATE t_client_application SET schema_id=9270000000900301 WHERE application_id={int(app_id)} AND status='DRAFT'")
        print(f"F schema pinned to v2 9270000000900301 for application {app_id}")
target = T.STATE / "accounts.json"
data = json.loads(target.read_text()) if target.exists() else {}
data.update(out)
target.write_text(json.dumps(data, indent=1) + "\n")
os.chmod(target, 0o600)
print(json.dumps({k: {kk: vv for kk, vv in v.items() if kk != "password"} for k, v in out.items()}, indent=1))
