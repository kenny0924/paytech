"""联合查询发布 b（technique 8.7 已加载对象跳过）· mor-test 接口验收（E2E_TARGET=test；运营入口 → Gateway → ops-bff → Platform）。

除登录外全部只读。先运行发布 a 的 jq_api.py（不带 loadedSubjects，行为应与发布 a 相同），再做 b 的检查。
预期取阶段 6 只读模拟（JQ_SKIP_LOADED=1）：../precheck/demo-chains-mortest-b-20261030.json。
  B1 AC-13：交易订单号入口按页面方式带 loadedSubjects 逐层展开——订单 / 收银台会话 / 支付意图 / 支付尝试 / 计费事实 / 记账批次 /
     会计凭证每个对象只在一个节点出现；“按自身 ID 查”的订单、会话、计费事实、记账批次、会计凭证记入 alreadyLoaded；执行数 < 24。
  B2 会计凭证 ID 入口（上游方向）：按自身 ID 查的记账批次、计费事实、订单模板照常执行且有数据，全部 SUCCEEDED。
  B3 5 个入口逐层与模拟一致：有数据的节点与 alreadyLoaded（模板, 层）不少于模拟。
  B4 按需查看：同层键 + templateCodes=[凭证分录]、不带 loadedSubjects，单独执行得到 6 行。
用法：E2E_TARGET=test python3 -I jq_api_b.py [--skip-a]
结果写本目录 result-api-b-<UTC>.json（不含口令与令牌）。
"""
import collections
import datetime
import json
import os
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "demo"))
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402
import jq_demo_spec as spec  # noqa: E402

JOINT = "/data-query/v1/joint-query"
SIMULATED = {c["entry"]["keyType"]: c for c in
             json.loads((HERE.parent / "precheck/demo-chains-mortest-b-20261030.json").read_text())["chains"]}
AC13_SUBJECTS = ["ORDER_ID", "CHECKOUT_SESSION_ID", "PAYMENT_INTENT_ID", "PAYMENT_ATTEMPT_ID", "BILLING_FACT_ID",
                 "ACCOUNT_POSTING_ID", "JOURNAL_ENTRY_ID"]
AC13_SKIPPED = {"JQ_ORDER_BY_ID", "JQ_CHECKOUT_SESSION_BY_ID", "JQ_BILLING_FACT_BY_ID", "JQ_ACCOUNT_POSTING_BY_ID",
                "JQ_JOURNAL_ENTRY_BY_ID"}
checks = []


def check(name, ok, evidence=None):
    checks.append({"name": name, "pass": bool(ok), "evidence": evidence})
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False)[:400]),
          flush=True)
    return ok


def run_page_style(ops, key_type, values, max_depth):
    """页面同款：已访问键去重 + 已加载对象（SUCCEEDED 节点中键类型 = 节点行主体键类型的列的非空值）。"""
    visited = {key_type: set(values)}
    loaded = collections.OrderedDict()
    keys, layers = [(key_type, list(values))], []
    for depth in range(max_depth):
        if not keys:
            break
        body = {"depth": depth, "keys": [{"keyType": k, "values": v} for k, v in keys]}
        if loaded:
            body["loadedSubjects"] = [{"keyType": k, "values": sorted(v)[:200]} for k, v in list(loaded.items())[:30] if v]
        reply = ops.send("POST", f"{JOINT}/steps", body, key=False)
        layers.append(reply)
        for node in reply["nodes"]:
            if node["status"] != "SUCCEEDED":
                continue
            for index, column in enumerate(node["columns"]):
                if column.get("keyType") == node["subjectKeyType"]:
                    loaded.setdefault(column["keyType"], set()).update(
                        str(row[index]) for row in node["rows"] if row[index] is not None)
        keys = []
        for found in reply["discoveredKeys"]:
            seen = visited.setdefault(found["keyType"], set())
            fresh = [v for v in found["values"] if v not in seen]
            seen.update(fresh)
            if fresh:
                keys.append((found["keyType"], fresh))
    return layers


def objects(layers, subjects):
    """(行主体键类型, 值) → 出现的节点数；只统计行主体属于 subjects 的节点。"""
    count = collections.Counter()
    for layer in layers:
        for node in layer["nodes"]:
            if node["subjectKeyType"] not in subjects:
                continue
            index = next((i for i, c in enumerate(node["columns"]) if c.get("keyType") == node["subjectKeyType"]), None)
            if index is None:
                continue
            for value in {str(row[index]) for row in node["rows"] if row[index] is not None}:
                count[(node["subjectKeyType"], value)] += 1
    return count


def main():
    if "--skip-a" not in sys.argv:
        a = subprocess.run([sys.executable, "-I", str(HERE / "jq_api.py")], env={**os.environ, "E2E_TARGET": e2e_http.TARGET})
        check("发布 a 的 jq_api.py（不带 loadedSubjects）全部通过", a.returncode == 0, {"exit": a.returncode})
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    max_depth = ops.get(f"{JOINT}/settings")["limits"]["maxDepth"]
    inactive = {t["templateCode"] for t in ops.get("/data-query-admin/v1/templates?size=200")["items"]
                if t["templateCode"].startswith("JQ_") and t["status"] != "ACTIVE"}

    # B1
    order = run_page_style(ops, "ORDER_NO", ["ord_3LOyZii4ct3BgT48vPZhmHbyC302a8"], max_depth)
    executed = sum(len(layer["nodes"]) for layer in order)
    skipped = {item["templateCode"] for layer in order for item in layer.get("alreadyLoaded") or []}
    duplicated = {f"{k}={v}": n for (k, v), n in objects(order, AC13_SUBJECTS).items() if n > 1}
    check("B1 AC-13 订单号入口：每个对象只出现一次", not duplicated, {"duplicated": duplicated})
    check(f"B1 AC-13 执行数 {executed} < 24（模拟 {sum(len(l['nodes']) for l in SIMULATED['ORDER_NO']['layers'])}）",
          executed < 24, {"layers": [[n["templateCode"] for n in layer["nodes"]] for layer in order]})
    check("B1 AC-13 按自身 ID 查的订单 / 会话 / 计费事实 / 记账批次 / 会计凭证记入 alreadyLoaded", AC13_SKIPPED <= skipped,
          {"missing": sorted(AC13_SKIPPED - skipped), "alreadyLoaded": sorted(skipped)})

    # B2
    upstream = run_page_style(ops, "JOURNAL_ENTRY_ID", ["241801561934753792"], max_depth)
    with_rows = {n["templateCode"] for layer in upstream for n in layer["nodes"] if n["rows"]}
    nodes = [n for layer in upstream for n in layer["nodes"]]
    check("B2 会计凭证入口：按自身 ID 查的记账批次、计费事实、订单照常执行且有数据，全部 SUCCEEDED",
          {"JQ_ACCOUNT_POSTING_BY_ID", "JQ_BILLING_FACT_BY_ID", "JQ_ORDER_BY_ID"} <= with_rows
          and all(n["status"] == "SUCCEEDED" for n in nodes), {"withRows": sorted(with_rows)})

    # B3
    for key_type, sim in SIMULATED.items():
        layers = order if key_type == "ORDER_NO" else upstream if key_type == "JOURNAL_ENTRY_ID" else \
            run_page_style(ops, key_type, sim["entry"]["values"], max_depth)
        got_rows = {(i, n["templateCode"]) for i, layer in enumerate(layers) for n in layer["nodes"] if n["rows"]}
        got_skip = {(i, a["templateCode"]) for i, layer in enumerate(layers) for a in layer.get("alreadyLoaded") or []}
        # 模拟时全部 JQ_* 为 ACTIVE；之后被停用的模板（如 D-b1 的计费事实按支付尝试）不再要求
        sim_rows = {(l["depth"], n["templateCode"]) for l in sim["layers"] for n in l["nodes"] if n["rows"]
                    and n["templateCode"] not in inactive}
        sim_skip = {(l["depth"], a["templateCode"]) for l in sim["layers"] for a in l.get("alreadyLoaded", [])
                    if a["templateCode"] not in inactive}
        check(f"B3 {key_type} 与模拟一致（有数据节点 {len(sim_rows)}、alreadyLoaded {len(sim_skip)}）",
              sim_rows <= got_rows and sim_skip <= got_skip,
              {"missingRows": sorted(f"{d}:{c}" for d, c in sim_rows - got_rows),
               "missingSkip": sorted(f"{d}:{c}" for d, c in sim_skip - got_skip)})

    # B4
    on_demand = ops.send("POST", f"{JOINT}/steps", {"depth": 4, "keys": [{"keyType": "JOURNAL_ENTRY_ID",
                         "values": ["241801561934753792"]}], "templateCodes": ["JQ_JOURNAL_LINE_BY_ENTRY"]}, key=False)
    check("B4 按需查看凭证分录（templateCodes、不带 loadedSubjects）6 行",
          len(on_demand["nodes"]) == 1 and len(on_demand["nodes"][0]["rows"]) == 6 and not on_demand["alreadyLoaded"],
          {"nodes": len(on_demand["nodes"])})

    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
    path = HERE / f"result-api-b-{stamp}.json"
    path.write_text(json.dumps({"target": e2e_http.TARGET, "checks": checks, "verdict": all(c["pass"] for c in checks)},
                               ensure_ascii=False, indent=2) + "\n")
    print("结果：", path, "通过", sum(c["pass"] for c in checks), "/", len(checks))
    sys.exit(0 if all(c["pass"] for c in checks) else 1)


if __name__ == "__main__":
    main()
