"""联合查询修订 c（technique 8.7 补充：子行模板以自身键类型为行主体）· mor-test 接口验收（E2E_TARGET=test）。

前提：runbook-c 的演示登记（register_jq_demo.py apply）已完成。除登录外全部只读。按对象合并显示只在页面做，这里只验证接口层：
  C1 3 个子行键类型 ACCOUNT_ENTRY_ID / JOURNAL_LINE_ID / STATEMENT_LINE_ID ACTIVE、不作入口（不在 J1 入口下拉）。
  C2 3 个子行模板当前版本为修订 c（行主体 = 自身键类型、触发键 = 父键类型）且参与联合查询。
  C3 订单号入口按页面方式带 loadedSubjects：账户分录（1 行）、凭证分录（6 行）、渠道对账单明细作为节点执行，不在 alreadyLoaded；
     订单 / 会话 / 计费事实 / 记账批次 / 会计凭证的“按自身 ID 查”仍记入 alreadyLoaded；执行数 < 24。
  C4 会计凭证 ID 入口：凭证分录在入口跳执行（6 行）；上游按自身 ID 查的记账批次 / 计费事实 / 订单照常执行。
  C5 5 个入口逐层与修订 c 模拟一致（../precheck/demo-chains-mortest-c-20261030.json）。
用法：E2E_TARGET=test python3 -I jq_api_c.py
结果写本目录 result-api-c-<UTC>.json（不含口令与令牌）。
"""
import datetime
import json
import os
import sys
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "demo"))
import jq_api_b as b  # noqa: E402  （复用页面同款逐层执行；导入不登录）
import jq_demo_spec as spec  # noqa: E402
import register_jq_demo as reg  # noqa: E402

e2e_http = b.e2e_http
ADMIN, JOINT = "/data-query-admin/v1", "/data-query/v1/joint-query"
SIMULATED = {c["entry"]["keyType"]: c for c in
             json.loads((HERE.parent / "precheck/demo-chains-mortest-c-20261030.json").read_text())["chains"]}
CHILD = {"JQ_ACCOUNT_ENTRY_BY_POSTING": "ACCOUNT_ENTRY_ID", "JQ_JOURNAL_LINE_BY_ENTRY": "JOURNAL_LINE_ID",
         "JQ_STATEMENT_LINE_BY_PLATFORM_TXN": "STATEMENT_LINE_ID"}
check, checks = b.check, b.checks


def nodes_of(layers, code):
    return [(depth, n) for depth, layer in enumerate(layers) for n in layer["nodes"] if n["templateCode"] == code]


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    settings = ops.get(f"{JOINT}/settings")
    max_depth = settings["limits"]["maxDepth"]

    # C1
    types = {k["keyTypeCode"]: k for k in ops.get(f"{ADMIN}/key-types?size=200")["items"]}
    entries = {k["keyTypeCode"] for k in settings["entryKeyTypes"]}
    for k in spec.KEY_TYPES:
        row = types.get(k["keyTypeCode"], {})
        check(f"C1 键类型 {k['keyTypeCode']} ACTIVE、{k['groupName']}、不作入口",
              row.get("status") == "ACTIVE" and row.get("groupName") == k["groupName"] and not row.get("entryAllowed")
              and k["keyTypeCode"] not in entries, {"sortNo": row.get("sortNo")})

    # C2
    for code, subject in CHILD.items():
        detail = reg.find_template(ops, code)
        joint = detail["currentVersion"].get("joint") or {}
        check(f"C2 模板 {code} 当前版本为修订 c、行主体 {subject}、参与联合查询",
              detail["currentVersion"].get("changeNote") == reg.CHANGE_NOTE_C and joint.get("subjectKeyType") == subject
              and (detail.get("jointQuery") or {}).get("participating") is True, {"versionNo": detail["currentVersionNo"]})

    # C3
    order = b.run_page_style(ops, "ORDER_NO", ["ord_3LOyZii4ct3BgT48vPZhmHbyC302a8"], max_depth)
    skipped = {a["templateCode"] for layer in order for a in layer.get("alreadyLoaded") or []}
    rows = {code: [len(n["rows"]) for _, n in nodes_of(order, code)] for code in CHILD}
    check("C3 订单号入口：3 个子行模板作为节点执行（账户分录 1 行、凭证分录 6 行），不在 alreadyLoaded",
          rows["JQ_ACCOUNT_ENTRY_BY_POSTING"] == [1] and rows["JQ_JOURNAL_LINE_BY_ENTRY"] == [6]
          and len(rows["JQ_STATEMENT_LINE_BY_PLATFORM_TXN"]) == 1 and not set(CHILD) & skipped, rows)
    check("C3 已加载对象跳过仍生效（订单 / 会话 / 计费事实 / 记账批次 / 会计凭证按自身 ID 查记入 alreadyLoaded）",
          b.AC13_SKIPPED <= skipped, {"missing": sorted(b.AC13_SKIPPED - skipped)})
    executed = sum(len(layer["nodes"]) for layer in order)
    check(f"C3 执行数 {executed} < 24（模拟 {sum(len(l['nodes']) for l in SIMULATED['ORDER_NO']['layers'])}）", executed < 24)

    # C4
    upstream = b.run_page_style(ops, "JOURNAL_ENTRY_ID", ["241801561934753792"], max_depth)
    lines = nodes_of(upstream, "JQ_JOURNAL_LINE_BY_ENTRY")
    with_rows = {n["templateCode"] for layer in upstream for n in layer["nodes"] if n["rows"]}
    check("C4 会计凭证入口：凭证分录在入口跳执行 6 行；上游记账批次 / 计费事实 / 订单按自身 ID 查照常执行",
          [(d, len(n["rows"])) for d, n in lines] == [(0, 6)]
          and {"JQ_ACCOUNT_POSTING_BY_ID", "JQ_BILLING_FACT_BY_ID", "JQ_ORDER_BY_ID"} <= with_rows,
          {"lines": [(d, len(n["rows"])) for d, n in lines]})

    # C5
    for key_type, sim in SIMULATED.items():
        layers = order if key_type == "ORDER_NO" else upstream if key_type == "JOURNAL_ENTRY_ID" else \
            b.run_page_style(ops, key_type, sim["entry"]["values"], max_depth)
        got_rows = {(i, n["templateCode"]) for i, layer in enumerate(layers) for n in layer["nodes"] if n["rows"]}
        got_skip = {(i, a["templateCode"]) for i, layer in enumerate(layers) for a in layer.get("alreadyLoaded") or []}
        sim_rows = {(l["depth"], n["templateCode"]) for l in sim["layers"] for n in l["nodes"] if n["rows"]}
        sim_skip = {(l["depth"], a["templateCode"]) for l in sim["layers"] for a in l.get("alreadyLoaded", [])}
        check(f"C5 {key_type} 与修订 c 模拟一致（有数据节点 {len(sim_rows)}、alreadyLoaded {len(sim_skip)}）",
              sim_rows <= got_rows and sim_skip == got_skip,
              {"missingRows": sorted(f"{d}:{c}" for d, c in sim_rows - got_rows),
               "skipDiff": sorted(f"{d}:{c}" for d, c in sim_skip ^ got_skip)})

    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
    path = HERE / f"result-api-c-{stamp}.json"
    path.write_text(json.dumps({"target": e2e_http.TARGET, "checks": checks, "verdict": all(c["pass"] for c in checks)},
                               ensure_ascii=False, indent=2) + "\n")
    print("结果：", path, "通过", sum(c["pass"] for c in checks), "/", len(checks))
    sys.exit(0 if all(c["pass"] for c in checks) else 1)


if __name__ == "__main__":
    main()
