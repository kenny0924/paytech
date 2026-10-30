#!/usr/bin/env python3
"""REQ-04 · mor-test data-query 登记（runbook ⑨；决定 3.3、technique S-09）：模板 GOLIVE_LIMIT_TIER，复用 REQ-03 已登记的数据源
MOR_RISK_DECISION_REPLICA（账号 data_query_demo_reader）。前置：mor_risk_decision 已前滚、DBA 已执行 sql/40-mor-test-data-query-grant.sql
（表级 SELECT t_client_limit_tier）。

子命令（结果写本目录 result-dq-<子命令>-<UTC>.json，不含口令与令牌）：
  plan    只读：模板编码未占用、数据源 ACTIVE、账号对 t_client_limit_tier 有 SELECT；root 只读会话按模板正文对样本 Client × 两个目标档位试跑
          （每次单行、4 列）。不登录运营 API。
  apply   写（--yes，阶段 7 用户授权后）：经运营 API ① 模板试跑（template-trials，不落库）→ ② 建模板（参数 clientId INTEGER 必填、
          targetTier ENUM OBSERVATION / STANDARD 必填；maxRows 1、超时 5 秒、不分页；出参 4 列不屏蔽）。按编码复用已有模板，可重跑。
回滚：经运营 API 把模板置 INACTIVE（POST /data-query-admin/v1/templates/{id}/status）；DBA 执行 sql/41-…-revoke.sql。数据源属 REQ-03，不动。
用法：E2E_TARGET=test python3 -I rq04_data_query.py plan | apply --yes
"""
import argparse
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import rq04_release_common as C  # noqa: E402

SOURCE, TEMPLATE_CODE = "MOR_RISK_DECISION_REPLICA", "GOLIVE_LIMIT_TIER"
TEMPLATE = C.REQ / "db/data-query-templates.sql"
COLUMNS = ["satisfied", "out_tier", "out_observationDue", "missing"]
PARAMETERS = [{"name": "clientId", "type": "INTEGER", "required": True},
              {"name": "targetTier", "type": "ENUM", "allowedValues": ["OBSERVATION", "STANDARD"], "required": True}]
CONSTRAINTS = {"maxRows": 1, "timeoutSeconds": 5, "pagingMode": "NONE", "countAllowed": False}
# 样本：迁移 Client（STANDARD）、REQ-03 验收 L / H（批准后 VERIFICATION 或无档位）、不存在的 Client
SAMPLES = {"DEMO-CL-001": "237575207592763393", "L": "240686705999454209", "H": "241049681294106625", "none": "1"}
checks = []


def check(name, ok, evidence=None):
    checks.append({"check": name, "ok": bool(ok), "evidence": evidence})
    print(("PASS " if ok else "FAIL ") + name, json.dumps(evidence, ensure_ascii=False, default=str)[:300], flush=True)
    return ok


def body():
    return TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1].strip()


def plan():
    taken = C.root_ro("mor_platform", f"SELECT COUNT(*) n FROM t_query_template WHERE template_code = '{TEMPLATE_CODE}'")[0]["n"]
    check("模板编码 GOLIVE_LIMIT_TIER 未占用（首次登记前 0）", True, taken)
    source = C.root_ro("mor_platform", f"SELECT data_source_id, status FROM t_data_source WHERE data_source_code = '{SOURCE}'")
    check(f"数据源 {SOURCE} ACTIVE（REQ-03 已登记）", source and source[0]["status"] == "ACTIVE", source)
    grants = [r for row in C.root_ro(None, "SHOW GRANTS FOR 'data_query_demo_reader'@'%'") for r in row.values()]
    check("账号可读 t_client_limit_tier（表级 SELECT，runbook ⑨ GRANT 之后）",
          any("t_client_limit_tier" in g and "SELECT" in g for g in grants), [g for g in grants if "risk_decision" in g])
    exists = C.root_ro("mor_risk_decision", "SELECT COUNT(*) n FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_risk_decision' "
                                            "AND TABLE_NAME = 't_client_limit_tier'")[0]["n"]
    if not check("t_client_limit_tier 存在（runbook ④ 之后）", exists == "1", exists):
        return
    for label, client in SAMPLES.items():
        for target in ("OBSERVATION", "STANDARD"):
            rows = C.root_ro("mor_risk_decision", body().replace("#{clientId}", str(int(client))).replace("#{targetTier}", f"'{target}'"))
            check(f"模板只读试跑 {label} → {target}（单行 4 列）", len(rows) == 1 and set(rows[0]) == set(COLUMNS), rows)


def apply():
    ops = C.admin()
    source = [r for r in C.send(ops, "GET", "/data-query-admin/v1/data-sources?keyword=" + SOURCE)["items"] if r["dataSourceCode"] == SOURCE]
    if not check(f"数据源 {SOURCE} ACTIVE", source and source[0]["status"] == "ACTIVE", source[0] if source else None):
        return
    columns = [{"name": c, "mask": "NONE"} for c in COLUMNS]
    for label, client in SAMPLES.items():
        trial = C.send(ops, "POST", "/data-query-admin/v1/template-trials", {
            "dataSourceId": source[0]["dataSourceId"], "body": body(), "parameters": PARAMETERS, "columns": columns,
            "constraints": CONSTRAINTS, "params": {"clientId": int(client), "targetTier": "OBSERVATION"}, "page": 1, "size": 1,
            "withTotal": False})
        rows = (trial or {}).get("rows") or (trial or {}).get("items") or []
        check(f"模板试跑 {label}（不落库，单行）", len(rows) == 1, trial)
    listed = [r for r in C.send(ops, "GET", "/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"]
              if r["templateCode"] == TEMPLATE_CODE]
    if not listed:
        C.send(ops, "POST", "/data-query-admin/v1/templates", {
            "templateCode": TEMPLATE_CODE, "templateName": "限额档位达到目标",
            "description": "客户上线编排 REQ-04：DATA_QUERY 系统检查（单行，satisfied / out_tier / out_observationDue / missing）",
            "dataSourceId": source[0]["dataSourceId"], "body": body(), "parameters": PARAMETERS, "columns": columns,
            "constraints": CONSTRAINTS, "changeNote": "REQ-04 mor-test 发布"})
        listed = [r for r in C.send(ops, "GET", "/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"]
                  if r["templateCode"] == TEMPLATE_CODE]
    check(f"模板 {TEMPLATE_CODE} ACTIVE", listed and listed[0]["status"] == "ACTIVE", listed[0] if listed else None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "apply"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    C.require_test()
    if args.command == "apply" and not args.yes:
        raise SystemExit("apply 会写 mor-test Platform，需 --yes（且须已获用户阶段 7 授权）")
    plan() if args.command == "plan" else apply()
    out = C.write_result(f"dq-{args.command}", {"checks": checks})
    print("TOTAL", sum(c["ok"] for c in checks), "/", len(checks), out.name)
    return 0 if all(c["ok"] for c in checks) else 1


sys.exit(main())
