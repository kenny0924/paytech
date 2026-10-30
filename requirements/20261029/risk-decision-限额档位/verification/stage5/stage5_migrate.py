#!/usr/bin/env python3
"""REQ-04 阶段 5：存量迁移（配置说明第 6 节），本机 *_v2e2e 库。

  export   只读导出 mor_commercial_v2e2e 中有 ENABLED 支付方式开通项的 CLIENT 合同对应的 Client，
           从 mor_client_master_v2e2e 只读补全编号、类型、辖区；写 stage5-migration-export.json。
  run      私网调用 risk-decision L12（POST /risk-limit-tiers/v1/internal/tier-migrations，X-Caller-Service = CALLER）；
           risk-decision 须已用 RISK_LIMIT_TIER_MIGRATION_CALLERS=CALLER 重启。结果写 stage5-migration-result.json。
  forbidden  白名单清空后再调用一次，期望 403 RISK_LIMIT_TIER_MIGRATION_FORBIDDEN。
  verify   核对导出名单中每个 Client 都在 t_client_limit_tier，且在对应 TIER 名单中有生效成员；输出缺口数。

本机夹具说明：RELSNAP-* 两个夹具 Client 在 client-master 没有辖区与名称属性，迁移请求用 client_no 作名称、UNSPECIFIED 作辖区快照；
另加 Payment 内部夹具 Client 880000000901（3DS / BIN 阶段 5 脚本以 acquiring 身份直调 Payment 使用，不在 client-master 与 commercial），
避免激活档位发布后本机其他会话的 3DS 回归被"无档位"拒绝；它不计入"导出名单 0 缺口"核对。
"""
import json
import sys
import uuid

from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as C  # noqa: E402

CALLER = "rq04-stage5-migration"
ADMIN_ACCOUNT_ID = "236033080844881920"  # 本机 e2e 管理员（OPERATIONS），执行人
PATH = "/risk-limit-tiers/v1/internal/tier-migrations"
EXPORT, RESULT = "stage5-migration-export.json", "stage5-migration-result.json"
EXTRA = [{"clientId": "880000000901", "clientDisplayName": "E2E Payment fixture 880000000901", "clientNo": "E2E-880000000901",
          "clientType": "ORGANIZATION", "jurisdictionCode": "AE"}]


def export():
    ids = [row["client_id"] for row in C.root_sql(
        "SELECT DISTINCT a.client_id FROM t_agreement_capability c JOIN t_agreement a ON a.agreement_id = c.agreement_id "
        "WHERE c.execution_status = 'ENABLED' AND a.agreement_type = 'CLIENT' AND a.client_id IS NOT NULL ORDER BY a.client_id",
        "mor_commercial_v2e2e")]
    rows = C.root_sql("SELECT c.client_id, c.client_no, c.jurisdiction_code, p.party_type FROM t_client c "
                      "JOIN t_party p ON p.party_id = c.party_id WHERE c.client_id IN (" + ",".join(ids) + ")",
                      "mor_client_master_v2e2e") if ids else []
    clients = [{"clientId": row["client_id"], "clientDisplayName": row["client_no"], "clientNo": row["client_no"],
                "clientType": row["party_type"],
                "jurisdictionCode": row["jurisdiction_code"] if row["jurisdiction_code"] != "NULL" else "UNSPECIFIED"}
               for row in rows]
    missing = sorted(set(ids) - {c["clientId"] for c in clients})
    data = {"commercialClientIds": ids, "clients": clients, "missingInClientMaster": missing, "extra": EXTRA}
    C.save_state(EXPORT, data)
    print(json.dumps(data, ensure_ascii=False))


def call(caller):
    data = C.load_state(EXPORT)
    body = {"executedBy": ADMIN_ACCOUNT_ID, "reason": "REQ-04 上线前存量迁移（本机阶段 5）",
            "clients": data["clients"] + data["extra"]}
    headers = {"X-Request-Id": f"rq04-migrate-{uuid.uuid4()}", "X-Caller-Service": caller}
    status, text = C.E.private_call("risk-decision", "POST", C.LT + "/internal/tier-migrations", headers=headers, body=body)
    return status, json.loads(text) if text.strip() else {}


def run():
    status, reply = call(CALLER)
    print(f"HTTP {status}")
    if status != 200:
        raise SystemExit(json.dumps(reply, ensure_ascii=False)[:600])
    C.save_state(RESULT, reply)
    print(json.dumps(reply, ensure_ascii=False))


def forbidden():
    status, reply = call(CALLER)
    print(f"HTTP {status} code={reply.get('code')}")
    if status != 403 or reply.get("code") != "RISK_LIMIT_TIER_MIGRATION_FORBIDDEN":
        raise SystemExit("白名单清空后仍可调用")


def verify():
    data = C.load_state(EXPORT)
    ids = [c["clientId"] for c in data["clients"]] + [c["clientId"] for c in data["extra"]]
    rows = C.root_sql("SELECT t.client_id, t.tier, t.source_type, l.list_code, e.entry_id, e.status entry_status "
                      "FROM t_client_limit_tier t LEFT JOIN t_risk_list_entry e ON e.subject_type = 'CLIENT' "
                      "AND e.subject_key = CAST(t.client_id AS CHAR) COLLATE utf8mb4_general_ci AND e.status = 'ACTIVE' "
                      "AND e.list_id IN (SELECT list_id FROM t_risk_list WHERE list_purpose = 'TIER') "
                      "LEFT JOIN t_risk_list l ON l.list_id = e.list_id WHERE t.client_id IN (" + ",".join(ids) + ")", C.RD)
    found = {}
    for row in rows:
        found.setdefault(row["client_id"], []).append(row)
    gaps = [cid for cid in data["commercialClientIds"]
            if [r["list_code"] for r in found.get(cid, [])] != ["TIER_" + found[cid][0]["tier"]] if cid in found] + \
           [cid for cid in data["commercialClientIds"] if cid not in found]
    print(json.dumps({"rows": rows, "gaps": gaps, "gapCount": len(gaps)}, ensure_ascii=False))
    if gaps:
        raise SystemExit(f"迁移缺口 {len(gaps)}")


if __name__ == "__main__":
    {"export": export, "run": run, "forbidden": forbidden, "verify": verify}[sys.argv[1]]()
