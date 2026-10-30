#!/usr/bin/env python3
"""REQ-04 · mor-test 存量迁移与激活前后核对（runbook ⑪—⑭；配置说明第 5、6 节）。

子命令：
  export     只读：mor_commercial 中有 ENABLED 支付方式开通项的 CLIENT 合同对应 Client，从 mor_client_master 补全编号、类型、辖区；
             另列"有有效承保、无开通项"的 Client（F-1 后在下次承保批准时得到 VERIFICATION，不迁移）。写 result-migrate-export-<UTC>.json。
  run        写（--yes）：经私网探针调用 L12 POST /risk-limit-tiers/v1/internal/tier-migrations（X-Caller-Service = rq04-release-migration）；
             risk-decision 须已用 compose-candidate-migration.risk-decision-service.yaml 起。重复执行返回 SKIPPED_EXISTING。
  forbidden  只读语义（L12 拒绝时不写库）：白名单清空（以 compose-candidate.risk-decision-service.yaml 重启）后再调用一次，期望 403。
  verify     只读：导出名单中每个 Client 都在 t_client_limit_tier 且只在对应 TIER 名单中有生效成员；输出缺口数（须为 0 才可激活）。
  smoke      写评估记录（--yes，激活后）：对每个迁移 Client 经统一评估入口做一笔额度内支付评估（USD 10，期望 ALLOW、路径经 tier_standard）
             并对一个不在任何档位的 Client 做一笔（期望 DENY、deny_no_tier）。评估记录与放行汇总是测试数据，保留。
执行人账号 ID 取现网运营管理员（只读查 t_user_account）。不打印口令与令牌。
用法：E2E_TARGET=test python3 -I rq04_migrate.py <export|run|forbidden|verify|smoke> [--yes]
"""
import argparse
import json
import random
import sys
import uuid
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "acceptance"))
import rq04_release_common as C  # noqa: E402

CALLER = "rq04-release-migration"
PATH = C.LT + "/internal/tier-migrations"
STATE = "migration.json"


def export():
    ids = [r["client_id"] for r in C.root_ro("mor_commercial", (
        "SELECT DISTINCT a.client_id FROM t_agreement_capability c JOIN t_agreement a ON a.agreement_id = c.agreement_id "
        "WHERE c.execution_status = 'ENABLED' AND a.agreement_type = 'CLIENT' AND a.client_id IS NOT NULL ORDER BY a.client_id"))]
    rows = C.root_ro("mor_client_master", "SELECT c.client_id, c.client_no, c.status, c.jurisdiction_code, p.party_type FROM t_client c "
                     "JOIN t_party p ON p.party_id = c.party_id WHERE c.client_id IN (" + ",".join(ids) + ")") if ids else []
    clients = [{"clientId": r["client_id"], "clientDisplayName": r["client_no"], "clientNo": r["client_no"], "clientType": r["party_type"],
                "jurisdictionCode": r["jurisdiction_code"] if r["jurisdiction_code"] != "NULL" else "UNSPECIFIED", "clientStatus": r["status"]}
               for r in rows]
    underwritten = C.root_ro("mor_risk_decision", "SELECT client_id, version_no, crr, client_display_name FROM t_client_underwriting "
                             "WHERE effective_slot = 1 ORDER BY client_id")
    data = {"commercialClientIds": ids, "clients": clients, "missingInClientMaster": sorted(set(ids) - {c["clientId"] for c in clients}),
            "approvedUnderwritingWithoutCapability": [u for u in underwritten if u["client_id"] not in ids]}
    C.save_state(STATE, data)
    C.write_result("migrate-export", data)
    print(json.dumps(data, ensure_ascii=False, indent=1))
    return not data["missingInClientMaster"]


def executor():
    rows = C.root_ro("mor_platform", "SELECT a.account_id FROM t_tenant_member m JOIN t_tenant_member_role r ON r.tenant_member_id = m.member_id "
                     "JOIN t_user_account a ON a.account_id = m.account_id WHERE r.role_id = 900000000000000510 AND r.status = 'ACTIVE' "
                     "AND a.realm = 'OPERATIONS' AND a.status = 'ACTIVE' ORDER BY a.create_time LIMIT 1")
    return rows[0]["account_id"]


def call():
    data = C.load_state(STATE)
    body = {"executedBy": executor(), "reason": "REQ-04 上线前存量迁移（mor-test）",
            "clients": [{k: c[k] for k in ("clientId", "clientDisplayName", "clientNo", "clientType", "jurisdictionCode")}
                        for c in data["clients"]]}
    return C.private("risk-decision", "POST", PATH, body=body,
                     headers={"X-Request-Id": f"rq04-migrate-{uuid.uuid4()}", "X-Caller-Service": CALLER})


def run():
    status, reply = call()
    print(f"HTTP {status}", json.dumps(reply, ensure_ascii=False))
    C.write_result("migrate-run", {"http": status, "reply": reply})
    return status == 200


def forbidden():
    status, reply = call()
    print(f"HTTP {status} code={reply.get('code')}")
    C.write_result("migrate-forbidden", {"http": status, "reply": reply})
    return status == 403 and reply.get("code") == "RISK_LIMIT_TIER_MIGRATION_FORBIDDEN"


def verify():
    data = C.load_state(STATE)
    ids = data["commercialClientIds"]
    rows = C.root_ro("mor_risk_decision", (
        "SELECT t.client_id, t.tier, t.source_type, l.list_code FROM t_client_limit_tier t LEFT JOIN t_risk_list_entry e "
        "ON e.subject_type = 'CLIENT' AND e.subject_key = CAST(t.client_id AS CHAR) COLLATE utf8mb4_general_ci AND e.status = 'ACTIVE' "
        "AND e.list_id IN (SELECT list_id FROM t_risk_list WHERE list_purpose = 'TIER') LEFT JOIN t_risk_list l ON l.list_id = e.list_id "
        "WHERE t.client_id IN (" + ",".join(ids) + ")")) if ids else []
    found = {}
    for row in rows:
        found.setdefault(row["client_id"], []).append(row)
    gaps = [cid for cid in ids if cid not in found or [r["list_code"] for r in found[cid]] != ["TIER_" + found[cid][0]["tier"]]]
    result = {"rows": rows, "gaps": gaps, "gapCount": len(gaps)}
    C.write_result("migrate-verify", result)
    print(json.dumps(result, ensure_ascii=False, indent=1))
    return not gaps


def rid():
    return str(random.randrange(10 ** 14, 9 * 10 ** 14))


def evaluate(client, amount="10.00", currency="USD"):
    body = {"pointCode": "PAYMENT_ATTEMPT_RISK", "contractVersion": 1, "businessKey": rid(), "inputs": {
        "paymentIntentId": rid(), "riskActionId": rid(), "orderId": rid(), "clientId": client, "entityNo": "AE001",
        "paymentMethodCode": "CARD_VISA", "captureMode": "AUTOMATIC", "amount": amount, "currency": currency,
        "industryCode": "DIGITAL_GOODS", "mccCode": "5816", "consumerCountryCode": "AE", "consumerReference": "rq04-release-smoke",
        "clientIpAddress": "203.0.113.10", "userAgent": "Mozilla/5.0 rq04-release-smoke", "cardFingerprint": "fp_rq04_" + rid(),
        "cardBin": "411111", "issuerCountryCode": "AE", "cardBrand": "VISA", "fundingType": "CREDIT"}}
    status, reply = C.private("risk-decision", "POST", "/risk-decisions/v1/evaluations", body=body,
                              headers={"X-Request-Id": f"rq04-smoke-{uuid.uuid4()}"})
    if status != 200:
        return {"http": status, "problem": C.OA.problem(reply)}
    path = [r["node_key"] for r in C.root_ro("mor_risk_decision", "SELECT node_key FROM t_evaluation_flow_trace WHERE evaluation_id = "
                                             f"{reply['evaluationId']} ORDER BY 1")]
    timing = C.root_ro("mor_risk_decision", f"SELECT technical_status, elapsed_millis FROM t_decision_evaluation WHERE evaluation_id = "
                                            f"{reply['evaluationId']}")[0]
    return {"http": status, "result": reply.get("result"), "evaluationId": reply.get("evaluationId"), "path": path, **timing}


def smoke():
    data = C.load_state(STATE)
    out, ok = [], True
    for client in data["commercialClientIds"]:
        outcome = evaluate(client)
        passed = outcome.get("result") == "ALLOW" and "tier_standard" in outcome.get("path", [])
        ok &= passed
        out.append({"client": client, "expect": "ALLOW via tier_standard", "pass": passed, **outcome})
        print(("PASS " if passed else "FAIL ") + client, json.dumps(outcome, ensure_ascii=False))
    outcome = evaluate("1")
    passed = outcome.get("result") == "DENY" and "deny_no_tier" in outcome.get("path", [])
    ok &= passed
    out.append({"client": "1", "expect": "DENY via deny_no_tier", "pass": passed, **outcome})
    print(("PASS " if passed else "FAIL ") + "no-tier client 1", json.dumps(outcome, ensure_ascii=False))
    C.write_result("migrate-smoke", out)
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("export", "run", "forbidden", "verify", "smoke"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    C.require_test()
    if args.command in ("run", "smoke") and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test，需 --yes（且须已获用户阶段 7 授权）")
    ok = {"export": export, "run": run, "forbidden": forbidden, "verify": verify, "smoke": smoke}[args.command]()
    return 0 if ok else 1


sys.exit(main())
