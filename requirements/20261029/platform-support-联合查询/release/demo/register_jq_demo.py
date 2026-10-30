"""联合查询 · mor-test 演示数据登记（阶段 7 第 ⑨ 步，主会话执行；阶段 6 只编写、未对 mor-test 运行）。

只经运营 API（http://localhost:28080 → Gateway → ops-bff → Platform），不直接写 mor_platform。定义取 jq_demo_spec.py。
子命令：
  plan   只读：登录后列出数据源 / 模板现状与将要做的动作（登录本身会写一条运营会话，阶段 7 之前不要对 mor-test 运行）。
  apply  写（需 --yes），可重跑，按编码复用：
         1 数据源：existing 必须已 ACTIVE（不改）；activate 把 INACTIVE 改 ACTIVE（Platform 会做连接检查）；
           create 新建（host mysql:3306、账号 data_query_demo_reader，口令读 test-environment/private/data-query-demo.env，不打印），
           然后启用。前提：root 已执行 sql/root-demo-reader-grants.sql。
         2 模板：不存在则新建；已存在且当前版本与定义不同则修改（新版本，changeNote 写明）；相同则跳过；最后都设为 ACTIVE。
         3 核对：联合查询设置可读；J2 可达预览（入口 ORDER_NO）中全部 JQ_* 模板都在、无 excluded；保存提示 jointQuery.participating = true。
  verify 只读：只做第 3 步。
  修订 c（2026-10-30）：apply 在第 1 步前先确保 jq_demo_spec.KEY_TYPES 的 3 个子行键类型存在且 ACTIVE（缺失则经 K2 新建，
         已存在则核对定义、不改）；3 个子行模板因定义变化保存新版本（changeNote 见 CHANGE_NOTE_C）。
  rollback-c 写（需 --yes）：3 个子行模板当前版本若是本修订保存的，恢复到上一版本（restoration）；再把 3 个子行键类型改为 INACTIVE。
结果写本目录 result-<子命令>-<UTC>.json（不含口令与令牌）。
用法：E2E_TARGET=test python3 -I register_jq_demo.py <plan|apply|verify> [--yes]
"""
import argparse
import datetime
import json
import os
import sys
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402
import jq_demo_spec as spec  # noqa: E402

ADMIN = "/data-query-admin/v1"
READER_ENV = Path("/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/data-query-demo.env")
CHANGE_NOTE = "联合查询 mor-test 演示（jq-20261030a）"
CHANGE_NOTE_C = "联合查询修订 c：子行以自身键类型为行主体（jq-20261030c）"
checks, actions = [], []


def now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def check(name, ok, evidence=None):
    checks.append({"name": name, "pass": bool(ok), "evidence": evidence})
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False)[:300]),
          flush=True)
    return ok


def find_source(ops, code):
    rows = [r for r in ops.get(f"{ADMIN}/data-sources?keyword={code}&size=50")["items"] if r["dataSourceCode"] == code]
    return rows[0] if rows else None


def find_template(ops, code):
    rows = [r for r in ops.get(f"{ADMIN}/templates?keyword={code}&size=50")["items"] if r["templateCode"] == code]
    return ops.get(f"{ADMIN}/templates/{rows[0]['templateId']}") if rows else None


def reader_password():
    for line in READER_ENV.read_text().splitlines():
        if line.startswith("DATA_QUERY_DEMO_READER_PASSWORD="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("data-query-demo.env 缺少 DATA_QUERY_DEMO_READER_PASSWORD")


def ensure_sources(ops, write):
    ids = {}
    for s in spec.DATA_SOURCES:
        row = find_source(ops, s["code"])
        if row is None and s["action"] != "create":
            check(f"数据源 {s['code']} 应已存在", False)
            continue
        if row is None:
            actions.append(f"create data source {s['code']} ({s['database']})")
            if write:
                ops.send("POST", f"{ADMIN}/data-sources", {
                    "dataSourceCode": s["code"], "dataSourceName": s["name"], "engineType": "MYSQL", "host": "mysql", "port": 3306,
                    "databaseName": s["database"], "username": "data_query_demo_reader", "password": reader_password(),
                    "maxPoolSize": 4, "description": "联合查询演示只读数据源（mor-test，data_query_demo_reader 只有 SELECT）"})
                row = find_source(ops, s["code"])
        if row is None:
            continue
        if row["databaseName"] != s["database"]:
            check(f"数据源 {s['code']} 库名 = {s['database']}", False, {"actual": row["databaseName"]})
        if row["status"] != "ACTIVE":
            if s["action"] == "existing":
                check(f"数据源 {s['code']} 应已 ACTIVE（本脚本不改既有数据源）", False, {"status": row["status"]})
            else:
                actions.append(f"activate data source {s['code']}")
                if write:
                    ops.send("POST", f"{ADMIN}/data-sources/{row['dataSourceId']}/status",
                             {"status": "ACTIVE", "expectedVersion": row["version"]})
                    row = find_source(ops, s["code"])
        check(f"数据源 {s['code']} {'ACTIVE' if write else '现状'}", row["status"] == "ACTIVE" or not write,
              {"dataSourceId": row["dataSourceId"], "status": row["status"]})
        ids[s["code"]] = row["dataSourceId"]
    return ids


def key_types(ops):
    return {k["keyTypeCode"]: k for k in ops.get(f"{ADMIN}/key-types?size=200")["items"]}


def ensure_key_types(ops, write):
    existing = key_types(ops)
    for k in getattr(spec, "KEY_TYPES", []):
        row = existing.get(k["keyTypeCode"])
        if row is None:
            actions.append(f"create key type {k['keyTypeCode']}")
            if write:
                ops.send("POST", f"{ADMIN}/key-types", {**k, "description": "联合查询修订 c 子行键类型"})
                row = key_types(ops).get(k["keyTypeCode"])
        if row is None:
            continue
        same = all(row.get(f) == k[f] for f in ("keyTypeName", "groupName", "valueType", "defaultRole", "entryAllowed"))
        check(f"键类型 {k['keyTypeCode']} 存在、ACTIVE、定义一致", row["status"] == "ACTIVE" and same,
              {"keyTypeId": row["keyTypeId"], "status": row["status"], "sortNo": row["sortNo"]})


def rollback_c(ops):
    for code in spec.REVISION_C_TEMPLATES:
        detail = find_template(ops, code)
        if detail is None or detail["currentVersion"].get("changeNote") != CHANGE_NOTE_C:
            check(f"模板 {code} 当前版本不是修订 c，不恢复", True, None if detail is None else {"version": detail["currentVersionNo"]})
            continue
        ops.send("POST", f"{ADMIN}/templates/{detail['templateId']}/restoration",
                 {"versionNo": detail["currentVersionNo"] - 1, "expectedVersion": detail["version"],
                  "changeNote": "回滚联合查询修订 c"})
        after = find_template(ops, code)
        check(f"模板 {code} 已恢复到 v{detail['currentVersionNo'] - 1} 的定义",
              after["currentVersion"].get("restoredFromVersionNo") == detail["currentVersionNo"] - 1,
              {"currentVersionNo": after["currentVersionNo"]})
    existing = key_types(ops)
    for k in getattr(spec, "KEY_TYPES", []):
        row = existing.get(k["keyTypeCode"])
        if row and row["status"] == "ACTIVE":
            ops.send("POST", f"{ADMIN}/key-types/{row['keyTypeId']}/status", {"status": "INACTIVE",
                                                                             "expectedVersion": row["version"]})
        check(f"键类型 {k['keyTypeCode']} INACTIVE", row is None or key_types(ops)[k["keyTypeCode"]]["status"] == "INACTIVE")


def same_version(detail, t, source_id):
    v = detail["currentVersion"]
    columns = [{k: c.get(k) for k in ("name", "label")} for c in v.get("columns") or []]
    return (v["dataSourceId"] == source_id and v["body"] == t["body"] and v["parameters"] == t["parameters"]
            and columns == t["columns"] and (v.get("joint") or None) == t["joint"]
            and {k: v["constraints"].get(k) for k in t["constraints"]} == t["constraints"])


def ensure_templates(ops, source_ids, write):
    for t in spec.TEMPLATES:
        source_id = source_ids.get(t["dataSourceCode"])
        detail = find_template(ops, t["templateCode"])
        body = {"templateName": t["templateName"], "description": t["description"], "dataSourceId": source_id, "body": t["body"],
                "parameters": t["parameters"], "columns": t["columns"], "constraints": t["constraints"], "joint": t["joint"],
                "changeNote": CHANGE_NOTE}
        if source_id is None:
            check(f"模板 {t['templateCode']} 的数据源 {t['dataSourceCode']} 已就绪", not write)
            continue
        if detail is None:
            actions.append(f"create template {t['templateCode']}")
            if write:
                saved = ops.send("POST", f"{ADMIN}/templates", {"templateCode": t["templateCode"], **body})
                check(f"模板 {t['templateCode']} 新建", True, {"warnings": [w.get("message") for w in saved.get("warnings", [])]})
        elif not same_version(detail, t, source_id):
            actions.append(f"update template {t['templateCode']} (v{detail['currentVersionNo']} → new version)")
            if write:
                note = CHANGE_NOTE_C if t["templateCode"] in getattr(spec, "REVISION_C_TEMPLATES", []) else CHANGE_NOTE
                ops.send("PUT", f"{ADMIN}/templates/{detail['templateId']}",
                         {**body, "changeNote": note, "expectedVersion": detail["version"]})
        if write:
            detail = find_template(ops, t["templateCode"])
            if detail["status"] != "ACTIVE":
                ops.send("POST", f"{ADMIN}/templates/{detail['templateId']}/status",
                         {"status": "ACTIVE", "expectedVersion": detail["version"]})
                detail = find_template(ops, t["templateCode"])
            participation = detail.get("jointQuery") or {}
            check(f"模板 {t['templateCode']}「{t['templateName']}」ACTIVE 且参与联合查询",
                  detail["status"] == "ACTIVE" and participation.get("participating") is True,
                  {"version": detail["currentVersionNo"], "jointQuery": participation})


def verify(ops):
    settings = ops.get("/data-query/v1/joint-query/settings")
    check("J1 联合查询设置可读", bool(settings), settings)
    codes = {t["templateCode"] for t in spec.TEMPLATES}
    # 阶段 6 在 mor-test 只读模拟展开得到的各入口应可达模板（precheck/demo-chains-mortest-20261030.json）
    simulated = json.loads((HERE.parent / "precheck/demo-chains-mortest-20261030.json").read_text())["chains"]
    expected_by_entry = {c["entry"]["keyType"]: {n["templateCode"] for layer in c["layers"] for n in layer["nodes"]}
                         for c in simulated}
    for entry in spec.ACCEPTANCE_ENTRIES:
        graph = ops.get(f"/data-query/v1/joint-query/graph?entryKeyType={entry['keyType']}")
        reachable = {t["templateCode"] for g in graph.get("groups", []) for t in g.get("templates", [])}
        excluded = [e for e in graph.get("excluded", []) if e["templateCode"] in codes]
        expected = expected_by_entry.get(entry["keyType"], set())
        check(f"J2 可达预览 {entry['keyType']}：JQ_* 无 excluded，阶段 6 模拟到的 {len(expected)} 个模板都可达",
              not excluded and expected <= reachable,
              {"reachableJq": len(reachable & codes), "missing": sorted(expected - reachable), "excluded": excluded[:5]})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["plan", "apply", "verify", "rollback-c"])
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    if args.command in ("apply", "rollback-c") and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test Platform 数据，需 --yes")
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    if args.command == "rollback-c":
        rollback_c(ops)
    if args.command in ("plan", "apply"):
        ensure_key_types(ops, args.command == "apply")
        ids = ensure_sources(ops, args.command == "apply")
        ensure_templates(ops, ids, args.command == "apply")
    if args.command in ("apply", "verify"):
        verify(ops)
    path = HERE / f"result-{args.command}-{now().replace(':', '')}.json"
    path.write_text(json.dumps({"target": e2e_http.TARGET, "actions": actions, "checks": checks,
                                "verdict": all(c["pass"] for c in checks)}, ensure_ascii=False, indent=2) + "\n")
    print("动作：", len(actions), "；结果：", path, "通过", sum(c["pass"] for c in checks), "/", len(checks))


if __name__ == "__main__":
    main()
