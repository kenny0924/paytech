"""联合查询 · mor-test 阶段 7 接口验收（E2E_TARGET=test；运营入口 http://localhost:28080 → Gateway → ops-bff → Platform）。

前提：runbook ①—⑨ 完成（DDL、两个新镜像、块 144 激活、root 授权、register_jq_demo.py apply）。除登录外全部只读；
K2 反例与 J3 反例在参数校验阶段即被拒绝，不落库。阶段 5 本机 41 项中依赖 fixture（慢查询、游标分页、受限账号 403、多 Client）
的场景只在本机验证；这里验证真实 mor-test 表上的链路。
预期取阶段 6 只读模拟：../precheck/demo-chains-mortest-20261030.json（同一入口、同一模板集合、同一护栏）。
检查：
  A  K1 键类型：首批 26 个 ACTIVE、按 sortNo 升序。
  B  J1 设置：入口下拉含验收入口的键类型；护栏 = 层数 6 / 执行 60 / 60 秒。
  C  J2 可达预览：每个入口无 JQ_* excluded，模拟到的模板全部可达。
  D  J3 逐层展开：每个入口逐层调用（页面同款：已访问键去重），全部节点 SUCCEEDED；模拟中有数据的模板在同层有数据；
     累计执行数 ≤ 60、层数 ≤ 6；链路覆盖收单、支付、计费、账户、总账、对账 6 个泳道（入口 1 与 5 合计）。
  E  分页：凭证分录（OFFSET）经单模板执行取第 2 页（size 2，withTotal）。
  F  反例：J2 缺 entryKeyType 400、未知键类型 400、不可作入口 400；J3 入口值格式不符 422；K2 正则含前瞻 422。
  G  回归（只读）：税务订单查询 200、承保记录列表 200、数据查询模板列表 200（含 GOLIVE_*）。
用法：E2E_TARGET=test python3 -I jq_api.py
结果写本目录 result-api-<UTC>.json（不含口令与令牌）。
"""
import datetime
import json
import os
import sys
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "demo"))
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402
import jq_demo_spec as spec  # noqa: E402

ADMIN, JOINT = "/data-query-admin/v1", "/data-query/v1/joint-query"
SIMULATED = json.loads((HERE.parent / "precheck/demo-chains-mortest-20261030.json").read_text())["chains"]
TAX_ORDER = "ord_3LOyZii4ct3BgT48vPZhmHbyC302a8"
checks = []


def check(name, ok, evidence=None):
    checks.append({"name": name, "pass": bool(ok), "evidence": evidence})
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False)[:300]),
          flush=True)
    return ok


def expect(name, status, fn, code=None):
    try:
        reply = fn()
        return check(name, False, {"expectedStatus": status, "actual": 200, "body": str(reply)[:200]})
    except e2e_http.Failure as failure:
        body = json.loads(failure.body) if failure.body.startswith("{") else {}
        return check(name, failure.status == status and (code is None or body.get("code") == code),
                     {"status": failure.status, "code": body.get("code")})


s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])

# A
key_types = ops.get(f"{ADMIN}/key-types?size=200")["items"]
seeded = [k for k in key_types if k["keyTypeId"].startswith("900000000000144")]
check("A K1 首批 26 个键类型 ACTIVE、按 sortNo 升序", len(seeded) == 26 and all(k["status"] == "ACTIVE" for k in seeded)
      and [k["sortNo"] for k in key_types] == sorted(k["sortNo"] for k in key_types), {"total": len(key_types)})

# B
settings = ops.get(f"{JOINT}/settings")
entry_types = {k["keyTypeCode"] for k in settings["entryKeyTypes"]}
check("B J1 入口含验收入口键类型、护栏默认值", {e["keyType"] for e in spec.ACCEPTANCE_ENTRIES} <= entry_types
      and settings["limits"].get("maxDepth") == 6, settings["limits"])

# C、D
codes = {t["templateCode"] for t in spec.TEMPLATES}
lanes = set()
for sim in SIMULATED:
    entry = sim["entry"]
    graph = ops.get(f"{JOINT}/graph?entryKeyType={entry['keyType']}")
    reachable = {t["templateCode"] for g in graph["groups"] for t in g["templates"]}
    expected = {n["templateCode"] for layer in sim["layers"] for n in layer["nodes"]}
    excluded = [e for e in graph["excluded"] if e["templateCode"] in codes]
    check(f"C J2 {entry['keyType']} 可达 {len(expected)} 个模板、无 excluded", not excluded and expected <= reachable,
          {"missing": sorted(expected - reachable), "excluded": excluded[:3]})
    visited = {entry["keyType"]: set(entry["values"])}
    keys, executed, layers = [(entry["keyType"], list(entry["values"]))], 0, []
    for depth in range(settings["limits"]["maxDepth"]):
        if not keys:
            break
        reply = ops.send("POST", f"{JOINT}/steps", {"depth": depth, "keys": [{"keyType": k, "values": v} for k, v in keys]},
                         key=False)
        layers.append(reply)
        executed += len(reply["nodes"])
        keys = []
        for found in reply["discoveredKeys"]:
            seen = visited.setdefault(found["keyType"], set())
            fresh = [v for v in found["values"] if v not in seen]
            seen.update(fresh)
            if fresh:
                keys.append((found["keyType"], fresh))
    nodes = [n for layer in layers for n in layer["nodes"]]
    lanes |= {n["groupName"] for n in nodes if n["rows"]}
    with_rows = {(i, n["templateCode"]) for i, layer in enumerate(layers) for n in layer["nodes"] if n["rows"]}
    sim_rows = {(layer["depth"], n["templateCode"]) for layer in sim["layers"] for n in layer["nodes"] if n["rows"]}
    check(f"D J3 {entry['keyType']}={entry['values'][0]}：{len(layers)} 层 {executed} 次执行全部成功，模拟中有数据的节点都有数据",
          all(n["status"] == "SUCCEEDED" for n in nodes) and sim_rows <= with_rows and executed <= 60 and len(layers) <= 6,
          {"layers": [[f"{n['templateCode']}:{len(n['rows'])}" for n in layer["nodes"]] for layer in layers],
           "missingVsSimulation": sorted(f"{d}:{c}" for d, c in sim_rows - with_rows),
           "failed": [(n["templateCode"], n["errorCode"]) for n in nodes if n["status"] != "SUCCEEDED"]})
check("D 链路覆盖 收单 / 支付 / 计费 / 账户 / 总账 / 对账 泳道", {"收单", "支付", "计费", "账户", "总账", "对账"} <= lanes, sorted(lanes))

# E
page2 = ops.send("POST", "/data-query/v1/templates/JQ_JOURNAL_LINE_BY_ENTRY/executions",
                 {"params": {"entryIds": ["241801561934753792"]}, "page": 2, "size": 2, "withTotal": True})
check("E 凭证分录经单模板执行取第 2 页（size 2）", len(page2["rows"]) == 2 and page2.get("total") == 6,
      {"rows": len(page2["rows"]), "total": page2.get("total")})

# F
expect("F J2 缺 entryKeyType 400（BFF 参数校验）", 400, lambda: ops.get(f"{JOINT}/graph"), "INVALID_REQUEST")
expect("F J2 未知键类型 400", 400, lambda: ops.get(f"{JOINT}/graph?entryKeyType=NO_SUCH_KEY"), "BUSINESS_CHECK_FAILED")
expect("F J2 不可作入口 400", 400, lambda: ops.get(f"{JOINT}/graph?entryKeyType=CHANNEL_INTERACTION_ID"), "BUSINESS_CHECK_FAILED")
expect("F J3 入口值格式不符 422", 422, lambda: ops.send("POST", f"{JOINT}/steps", {
    "depth": 0, "keys": [{"keyType": "REFUND_REQUEST_NO", "values": ["rfd_short"]}]}, key=False), "VALIDATION_ERROR")
expect("F K2 正则含前瞻 422（校验阶段拒绝，不落库）", 422, lambda: ops.send("POST", f"{ADMIN}/key-types", {
    "keyTypeCode": "JQ_ACCEPT_BAD", "keyTypeName": "x", "groupName": "计费", "valueType": "STRING", "defaultRole": "LINK",
    "entryAllowed": True, "valuePattern": "a(?=b)", "sortNo": 1}), "VALIDATION_ERROR")

# G
tax = ops.get(f"/tax/v1/orders/{TAX_ORDER}")
check("G 税务订单查询 200", bool(tax), {"keys": sorted(tax)[:6] if isinstance(tax, dict) else None})
underwriting = ops.get("/risk-underwriting/v1/underwritings?page=1&size=10")
check("G 承保记录列表 200", isinstance(underwriting, dict), {"keys": sorted(underwriting)[:5]})
templates = ops.get(f"{ADMIN}/templates?size=100")["items"]
check("G 数据查询模板列表含 GOLIVE_* 与 JQ_*", any(t["templateCode"].startswith("GOLIVE_") for t in templates)
      and len([t for t in templates if t["templateCode"] in codes]) == len(codes), {"total": len(templates)})

stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H%M%SZ")
path = HERE / f"result-api-{stamp}.json"
path.write_text(json.dumps({"target": e2e_http.TARGET, "checks": checks, "verdict": all(c["pass"] for c in checks)},
                           ensure_ascii=False, indent=2) + "\n")
print("结果：", path, "通过", sum(c["pass"] for c in checks), "/", len(checks))
sys.exit(0 if all(c["pass"] for c in checks) else 1)
