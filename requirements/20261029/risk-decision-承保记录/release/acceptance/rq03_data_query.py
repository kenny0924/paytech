#!/usr/bin/env python3
"""REQ-03 · mor-test data-query 登记（runbook ⑨，决定 3.3 / technique 10.2）：数据源 MOR_RISK_DECISION_REPLICA + 模板 GOLIVE_UNDERWRITING_APPROVED。
前置：DBA 已执行 sql/40-mor-test-data-query-grant.sql（data_query_demo_reader 只读 t_client_underwriting），且 mor_risk_decision 已前滚。

子命令（结果写本目录 result-dq-<子命令>-<UTC>.json，不含口令与令牌）：
  plan    只读：root READ ONLY 会话核对数据源 / 模板编码未占用、账号权限（SHOW GRANTS）、三张表存在；以只读会话按模板正文在 mor_risk_decision
          逐个验收 Client 试跑（参数替换为整数）。不登录运营 API。
  apply   写（需 --yes，阶段 7 用户授权后）：经运营 API ① 连接测试（不落库）→ ② 建数据源（host mysql:3306，库 mor_risk_decision，账号
          data_query_demo_reader，口令读 test-environment/private/data-query-demo.env，不打印；连接参数与连接池同 MOR_COMMERCIAL_REPLICA）
          → ③ 模板试跑（template-trials，不落库）→ ④ 建模板（参数 clientId INTEGER 必填；maxRows 1、超时 5 秒、不分页；出参 6 列不屏蔽）。
          按编码复用已有对象，可重跑。
回滚：经运营 API 把模板与数据源置 INACTIVE（POST …/templates/{id}/status、…/data-sources/{id}/status）；DBA 执行 sql/41-…-revoke.sql。
用法：E2E_TARGET=test python3 -I rq03_data_query.py plan | apply --yes
"""
import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
TEMPLATE = REQ / "db/data-query-templates.sql"
TEST_ENV = Path("/Users/kenny/Desktop/Workspace/Code-MoR/test-environment")
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

SOURCE, TEMPLATE_CODE = "MOR_RISK_DECISION_REPLICA", "GOLIVE_UNDERWRITING_APPROVED"
COLUMNS = ["satisfied", "out_requireDeposit", "out_depositBlocksCollection", "out_highRiskMcc", "out_suggestedSettlementDays", "missing"]
PARAMETERS = [{"name": "clientId", "type": "INTEGER", "required": True}]
CONSTRAINTS = {"maxRows": 1, "timeoutSeconds": 5, "pagingMode": "NONE", "countAllowed": False}
CLIENTS = {"L": "240686705999454209", "H": "241049681294106625", "M": "239260386824658945", "none": "1"}
checks = []


def check(name, ok, evidence=None):
    checks.append({"check": name, "ok": bool(ok), "evidence": evidence})
    print(("PASS " if ok else "FAIL ") + name, json.dumps(evidence, ensure_ascii=False, default=str)[:300], flush=True)
    return ok


def env_value(path, key):
    return next(line.split("=", 1)[1].strip() for line in Path(path).read_text().splitlines() if line.startswith(key + "="))


def root_ro(database, statement):
    pw = env_value(TEST_ENV / ".env.test", "MOR_MYSQL_ROOT_PASSWORD")
    out = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", "mor-test-mysql-1", "mysql", "-uroot", "-B",
                          "--default-character-set=utf8mb4"] + ([database] if database else []),
                         input="SET SESSION TRANSACTION READ ONLY;\n" + statement, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:300])
    lines = [line for line in out.stdout.splitlines() if line]
    head = lines[0].split("\t") if lines else []
    return [dict(zip(head, line.split("\t"))) for line in lines[1:]]


def body():
    return TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1].strip()


def plan():
    taken = root_ro("mor_platform", f"SELECT (SELECT COUNT(*) FROM t_data_source WHERE data_source_code='{SOURCE}') s, "
                                    f"(SELECT COUNT(*) FROM t_query_template WHERE template_code='{TEMPLATE_CODE}') t")[0]
    check("数据源 / 模板编码状态（首次登记前 0 / 0）", True, taken)
    reference = root_ro("mor_platform", "SELECT connection_params_json, max_pool_size FROM t_data_source "
                                        "WHERE data_source_code='MOR_COMMERCIAL_REPLICA'")[0]
    check("参照数据源 MOR_COMMERCIAL_REPLICA 连接参数", True, reference)
    grants = [r for row in root_ro(None, "SHOW GRANTS FOR 'data_query_demo_reader'@'%'") for r in row.values()]
    check("账号 data_query_demo_reader 可读 t_client_underwriting（库级或表级 SELECT）",
          any("mor_risk_decision" in g and "SELECT" in g for g in grants), grants)
    tables = root_ro("mor_risk_decision", "SELECT COUNT(*) n FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_risk_decision' "
                                          "AND TABLE_NAME='t_client_underwriting'")[0]["n"]
    if not check("t_client_underwriting 存在（runbook ① 之后）", tables == "1", tables):
        return
    for label, client in CLIENTS.items():
        rows = root_ro("mor_risk_decision", body().replace("#{clientId}", str(int(client))))
        check(f"模板只读试跑 {label}（单行）", len(rows) == 1 and set(rows[0]) == set(COLUMNS), rows)


def apply():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    reference = root_ro("mor_platform", "SELECT connection_params_json, max_pool_size FROM t_data_source "
                                        "WHERE data_source_code='MOR_COMMERCIAL_REPLICA'")[0]
    params, pool = json.loads(reference["connection_params_json"]), int(reference["max_pool_size"])
    password = env_value(TEST_ENV / "private/data-query-demo.env", "DATA_QUERY_DEMO_READER_PASSWORD")
    connection = {"engineType": "MYSQL", "host": "mysql", "port": 3306, "databaseName": "mor_risk_decision",
                  "username": "data_query_demo_reader", "password": password, "connectionParams": params}
    found = [r for r in ops.get("/data-query-admin/v1/data-sources?keyword=" + SOURCE)["items"] if r["dataSourceCode"] == SOURCE]
    if not found:
        test = ops.send("POST", "/data-query-admin/v1/data-source-connection-tests", connection)
        if not check("连接测试（不落库）：可连接且只读权限校验通过", (test or {}).get("connected") is True
                     and (test or {}).get("privilegesPassed") is True, test):
            return
        ops.send("POST", "/data-query-admin/v1/data-sources", {
            "dataSourceCode": SOURCE, "dataSourceName": "风控决策（Risk Decision）从库", **connection, "maxPoolSize": pool,
            "description": "REQ-03 客户上线：承保结论查询（只读 t_client_underwriting）"})
        found = [r for r in ops.get("/data-query-admin/v1/data-sources?keyword=" + SOURCE)["items"] if r["dataSourceCode"] == SOURCE]
    source = found[0]
    if source["status"] != "ACTIVE":
        ops.send("POST", f"/data-query-admin/v1/data-sources/{source['dataSourceId']}/status",
                 {"status": "ACTIVE", "expectedVersion": source["version"]})
        source = [r for r in ops.get("/data-query-admin/v1/data-sources?keyword=" + SOURCE)["items"] if r["dataSourceCode"] == SOURCE][0]
    check(f"数据源 {SOURCE} ACTIVE", source["status"] == "ACTIVE", {"dataSourceId": source["dataSourceId"]})
    columns = [{"name": c, "mask": "NONE"} for c in COLUMNS]
    for label, client in CLIENTS.items():
        trial = ops.send("POST", "/data-query-admin/v1/template-trials", {
            "dataSourceId": source["dataSourceId"], "body": body(), "parameters": PARAMETERS, "columns": columns,
            "constraints": CONSTRAINTS, "params": {"clientId": int(client)}, "page": 1, "size": 1, "withTotal": False}, key=False)
        rows = (trial or {}).get("rows") or (trial or {}).get("items") or []
        check(f"模板试跑 {label}（不落库，单行）", len(rows) == 1, trial)
    listed = [r for r in ops.get("/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"] if r["templateCode"] == TEMPLATE_CODE]
    if not listed:
        ops.send("POST", "/data-query-admin/v1/templates", {
            "templateCode": TEMPLATE_CODE, "templateName": "承保已批准（有效结论）",
            "description": "客户上线编排 REQ-03：DATA_QUERY 系统检查（单行，satisfied / out_* / missing）", "dataSourceId": source["dataSourceId"],
            "body": body(), "parameters": PARAMETERS, "columns": columns, "constraints": CONSTRAINTS, "changeNote": "REQ-03 mor-test 发布"})
        listed = [r for r in ops.get("/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"] if r["templateCode"] == TEMPLATE_CODE]
    check(f"模板 {TEMPLATE_CODE} ACTIVE", listed and listed[0]["status"] == "ACTIVE", listed[0] if listed else None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "apply"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    if e2e_http.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")
    if args.command == "apply" and not args.yes:
        raise SystemExit("apply 会写 mor-test Platform，需 --yes（且须已获用户阶段 7 授权）")
    started = datetime.now(timezone.utc)
    plan() if args.command == "plan" else apply()
    out = HERE / f"result-dq-{args.command}-{started.strftime('%Y-%m-%dT%H%M%SZ')}.json"
    out.write_text(json.dumps({"startedAt": started.isoformat(), "checks": checks}, ensure_ascii=False, indent=1, default=str) + "\n")
    print("TOTAL", sum(c["ok"] for c in checks), "/", len(checks), out.name)
    return 0 if all(c["ok"] for c in checks) else 1


sys.exit(main())
