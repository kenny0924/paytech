#!/usr/bin/env python3
"""REQ-02 · mor-test data-query 登记（runbook 第 8 节，单独授权；technique §11、决定附件 3.3）：模板 GOLIVE_MID_READY，复用已登记数据源
MOR_CHANNEL_CONNECTOR_REPLICA（账号 data_query_demo_reader，对 mor_channel_connector.* SELECT）。不建数据源、不授权账号。

参数：entityNo / paymentMethods / mccCode STRING 必填；currencies STRING 可选、default ""（双保险：主会话已把模板里的 #{currencies} 改为
COALESCE(#{currencies}, '')，阶段 6 发现旧正文在 NULL 时误判满足；plan 用 NULL 字面量复跑确认已修复）。
口径（Q-4 修订）：currencies 给出时，列出的币种中任一就绪即该支付方式就绪。
约束 maxRows 1、超时 5 秒、不分页、不计数；出参 3 列不屏蔽。

子命令（结果写本目录 result-dq-<子命令>-<UTC>.json，不含口令与令牌）：
  plan    只读：模板编码未占用、数据源 ACTIVE、账号可读 mor_channel_connector；root 只读会话按模板正文字面量替换试跑 6 组（单行 3 列）。
  apply   写（--yes，阶段 7 用户单独授权后）：经运营 API ① 模板试跑（template-trials，不落库）8 组，含"不传 currencies"一组须与空串同结论（主会话要求保留）
          → ② 建模板。按编码复用已有模板，可重跑。
回滚：经运营 API 把模板置 INACTIVE（POST /data-query-admin/v1/templates/{id}/status）。数据源不动。
用法：python3 -I req02_data_query.py plan | apply --yes
"""
import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as E  # noqa: E402

SOURCE, TEMPLATE_CODE = "MOR_CHANNEL_CONNECTOR_REPLICA", "GOLIVE_MID_READY"
TEMPLATE = HERE.parent / "sql/03-golive-mid-ready-template.sql"  # 登记材料：db/data-query-templates.sql 的同步副本（COALESCE 修订后）
SOURCE_OF_TRUTH = HERE.parent.parent / "db/data-query-templates.sql"
COLUMNS = ["satisfied", "out_missing_methods", "missing"]
PARAMETERS = [{"name": "entityNo", "type": "STRING", "required": True},
              {"name": "paymentMethods", "type": "STRING", "required": True},
              {"name": "mccCode", "type": "STRING", "required": True},
              {"name": "currencies", "type": "STRING", "required": False, "default": ""}]
CONSTRAINTS = {"maxRows": 1, "timeoutSeconds": 5, "pagingMode": "NONE", "countAllowed": False}
# (参数, 期望 satisfied / out_missing_methods / missing)；与阶段 3 / 5 / 6 只读试跑一致（AE001 只有 CARD × USD × 5999 可收款能力行）
SAMPLES = [({"entityNo": "AE001", "paymentMethods": "CARD,APPLE_PAY", "mccCode": "5999", "currencies": "USD"}, ("0", "APPLE_PAY", "PAYMENT_METHOD_NOT_READY")),
           ({"entityNo": "AE001", "paymentMethods": "CARD", "mccCode": "5999", "currencies": ""}, ("1", "", None)),
           ({"entityNo": "AE001", "paymentMethods": "CARD", "mccCode": "5999"}, ("1", "", None)),
           ({"entityNo": "AE001", "paymentMethods": "CARD,APPLE_PAY", "mccCode": "5999"}, ("0", "APPLE_PAY", "PAYMENT_METHOD_NOT_READY")),
           ({"entityNo": "AE001", "paymentMethods": "CARD", "mccCode": "5999", "currencies": "USD,AED"}, ("1", "", None)),
           ({"entityNo": "AE001", "paymentMethods": "CARD", "mccCode": "5999", "currencies": "AED"}, ("0", "CARD", "PAYMENT_METHOD_NOT_READY")),
           ({"entityNo": "AE001", "paymentMethods": "CARD", "mccCode": "5816", "currencies": ""}, ("0", "CARD", "PAYMENT_METHOD_NOT_READY")),
           ({"entityNo": "SA001", "paymentMethods": "CARD", "mccCode": "5999", "currencies": ""}, ("0", "CARD", "MID_NOT_FOUND"))]
checks = []


def check(name, ok, evidence=None):
    checks.append({"check": name, "ok": bool(ok), "evidence": evidence})
    print(("PASS " if ok else "FAIL ") + name, json.dumps(evidence, ensure_ascii=False, default=str)[:300], flush=True)
    return ok


def root_ro(database, statement):
    pw = E.read_env(MAIN / "test-environment/.env.test")["MOR_MYSQL_ROOT_PASSWORD"]
    out = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                          "--default-character-set=utf8mb4", "--init-command=SET SESSION TRANSACTION READ ONLY"] + ([database] if database else []),
                         input=statement, env={**os.environ, "MYSQL_PWD": pw}, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("SQL 失败：" + out.stderr.strip()[:300])
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def body():
    text = TEMPLATE.read_text()
    return text[text.index("\nWITH RECURSIVE") + 1:].strip()  # 阶段 7：模板改为 WITH RECURSIVE 拆分列表（JSON_TABLE 不接受绑定参数）


def literal(params):
    sql = body()
    for p in PARAMETERS:
        value = params.get(p["name"], p.get("default"))
        sql = sql.replace("#{" + p["name"] + "}", "NULL" if value is None else "'" + value.replace("'", "''") + "'")
    return sql


def normalize(row):
    return (str(row[0]), row[1] if len(row) > 1 else "", None if len(row) < 3 or row[2] == "NULL" else row[2])


def plan():
    check("登记材料与 db/data-query-templates.sql 逐字节相同", TEMPLATE.read_bytes() == SOURCE_OF_TRUTH.read_bytes(), TEMPLATE.name)
    check("模板正文每处 #{currencies} 均包在 COALESCE(#{currencies}, '') 内", body().count("#{currencies}") >= 1
          and body().count("COALESCE(#{currencies}, '')") == body().count("#{currencies}"), body().count("#{currencies}"))
    taken = root_ro("mor_platform", f"SELECT COUNT(*) FROM t_query_template WHERE template_code = '{TEMPLATE_CODE}'")[0][0]
    check(f"模板编码 {TEMPLATE_CODE} 未占用（首次登记前 0）", True, taken)
    source = root_ro("mor_platform", f"SELECT data_source_id, status, username FROM t_data_source WHERE data_source_code = '{SOURCE}'")
    check(f"数据源 {SOURCE} ACTIVE", source and source[0][1] == "ACTIVE", source)
    grants = [r[0] for r in root_ro(None, f"SHOW GRANTS FOR '{source[0][2]}'@'%'")] if source else []
    check("账号可读 mor_channel_connector", any("`mor_channel_connector`.*" in g and "SELECT" in g for g in grants),
          [g for g in grants if "channel_connector" in g])
    for params, expected in SAMPLES:
        rows = root_ro("mor_channel_connector", literal(params))
        got = normalize(rows[0]) if len(rows) == 1 else None
        check(f"模板只读试跑 {params} → {expected}", got == expected, rows)
    nulled = literal({"entityNo": "AE001", "paymentMethods": "CARD,APPLE_PAY", "mccCode": "5999"}).replace(
        "COALESCE('', '')", "COALESCE(NULL, '')")
    rows = root_ro("mor_channel_connector", nulled)
    check("currencies 绑定为 NULL 时与空串同结论（0 / APPLE_PAY，防误判满足）", rows and normalize(rows[0]) == ("0", "APPLE_PAY", "PAYMENT_METHOD_NOT_READY"), rows)


def apply():
    s = E.secrets()
    ops = E.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    source = [r for r in ops.get("/data-query-admin/v1/data-sources?keyword=" + SOURCE)["items"] if r["dataSourceCode"] == SOURCE]
    if not check(f"数据源 {SOURCE} ACTIVE", source and source[0]["status"] == "ACTIVE", source[0] if source else None):
        return
    if not check("登记材料与 db/data-query-templates.sql 逐字节相同", TEMPLATE.read_bytes() == SOURCE_OF_TRUTH.read_bytes()):
        return
    columns = [{"name": c, "mask": "NONE"} for c in COLUMNS]
    for params, expected in SAMPLES:
        trial = ops.send("POST", "/data-query-admin/v1/template-trials", {
            "dataSourceId": source[0]["dataSourceId"], "body": body(), "parameters": PARAMETERS, "columns": columns,
            "constraints": CONSTRAINTS, "params": params, "page": 1, "size": 1, "withTotal": False})
        rows = (trial or {}).get("rows") or (trial or {}).get("items") or []
        row = rows[0] if len(rows) == 1 else {}
        if isinstance(row, list):  # data-query 试跑按列顺序返回行数组
            row = dict(zip(COLUMNS, row))
        got = (str(row.get("satisfied")), row.get("out_missing_methods") or "", row.get("missing"))
        check(f"模板试跑 {params} → {expected}（不落库）", got == expected, trial)
    if not all(c["ok"] for c in checks):
        check("试跑有不符，停止，不建模板", False)
        return
    listed = [r for r in ops.get("/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"] if r["templateCode"] == TEMPLATE_CODE]
    if not listed:
        ops.send("POST", "/data-query-admin/v1/templates", {
            "templateCode": TEMPLATE_CODE, "templateName": "MID 就绪（渠道身份）",
            "description": "客户上线编排 REQ-02：N5c / N5d DATA_QUERY 系统检查（单行，satisfied / out_missing_methods / missing），与开通项前置 ④ 同口径",
            "dataSourceId": source[0]["dataSourceId"], "body": body(), "parameters": PARAMETERS, "columns": columns,
            "constraints": CONSTRAINTS, "changeNote": "REQ-02 mor-test 发布"})
        listed = [r for r in ops.get("/data-query-admin/v1/templates?keyword=" + TEMPLATE_CODE)["items"] if r["templateCode"] == TEMPLATE_CODE]
    check(f"模板 {TEMPLATE_CODE} ACTIVE", listed and listed[0]["status"] == "ACTIVE", listed[0] if listed else None)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("plan", "apply"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    if E.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")
    if args.command == "apply" and not args.yes:
        raise SystemExit("apply 会写 mor-test Platform，需 --yes（且须已获用户单独授权）")
    plan() if args.command == "plan" else apply()
    at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
    out = HERE / f"result-dq-{args.command}-{at}.json"
    out.write_text(json.dumps({"command": args.command, "checks": checks}, ensure_ascii=False, indent=1, default=str) + "\n")
    print("TOTAL", sum(c["ok"] for c in checks), "/", len(checks), out.name)
    return 0 if all(c["ok"] for c in checks) else 1


if __name__ == "__main__":
    sys.exit(main())
