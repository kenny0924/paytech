"""任务卡 P1-stage5 C3：基线（stage5-baseline.json）与新版本（stage5-after.json）逐项对照，输出 stage5-compare.json。

对照规则：
  - 链路：候选查询、各操作的 HTTP 状态 / outcome / failureCode / channelStatus / paymentStatus、渠道流水条数与每条的操作、结果码、
    验签结论逐项相等；Vault Reveal 只比较动作、数据类型、用途与结果，调用方单列（允许 platform-support-service → channel-connector-service）。
  - card 调用方探针：单列（允许 channel-connector-service 由 404 变为越过调用方校验）。
  - 沙箱：运行结论、步骤结论、执行状态、输出、断言、证据卡号遮蔽、渠道流水逐项相等；编码与运行号属于允许差异。
  - 管理读取：新版本快照去掉本次新建的验证账号、用例与运行后，与基线快照逐字段相等；新建运行的详情与基线本轮（label 同构）
    运行详情比较，忽略编码、运行号、名称与每次随机的渠道订单号 / 签名 / 渠道流水号。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name):
    return json.loads((HERE / name).read_text())


def diff(x, y, path=""):
    out = []
    if type(x) != type(y):
        return [[path, "type", str(x)[:160], str(y)[:160]]]
    if isinstance(x, dict):
        for k in sorted(set(x) | set(y)):
            if k not in x or k not in y:
                out.append([f"{path}/{k}", "missing", k in x, k in y])
            else:
                out += diff(x[k], y[k], f"{path}/{k}")
    elif isinstance(x, list):
        if len(x) != len(y):
            out.append([path, "len", len(x), len(y)])
        for i, (p, q) in enumerate(zip(x, y)):
            out += diff(p, q, f"{path}[{i}]")
    elif x != y:
        out.append([path, "value", str(x)[:160], str(y)[:160]])
    return out


def chain_view(step):
    if "mine" in step:
        return step
    return {"httpStatus": step["httpStatus"], "outcome": step["outcome"], "failureCode": step["failureCode"],
            "channelStatus": step["channelStatus"], "paymentStatus": step["paymentStatus"],
            "hasChannelTransactionRef": step["hasChannelTransactionRef"], "channelJournal": step["channelJournal"],
            "vault": [{k: v for k, v in e.items() if k != "callerService"} for e in step["vaultChannelAccountEvents"]]}


def without_new(snapshot, new_codes):
    """去掉新版本本次新建的对象（列表项与详情调用）。"""
    responses = {}
    for name, value in snapshot["responses"].items():
        if any(name.endswith("/" + code) for code in new_codes):
            continue
        value = json.loads(json.dumps(value))
        for key in ("items", "body"):
            items = value.get(key)
            if isinstance(items, list):
                value[key] = [i for i in items if not (isinstance(i, dict) and
                                                       {i.get("accountCode"), i.get("caseCode"), i.get("runCode")} & new_codes)]
        if "total" in value and isinstance(value.get("items"), list) and value["total"] is not None:
            value["total"] = len(value["items"]) if isinstance(value["total"], int) else str(len(value["items"]))
        responses[name] = value
    return responses


RANDOM_KEYS = re.compile(r"(attemptId|outTradeNo|tradeOrderNo|channelTransactionRef|sign|signature|requestTime|runCode|caseCode|"
                         r"caseName|accountCode|accountName|accountId|verifyRunId|verifyCaseId|verifyAccountId|runStepId|elapsed|^date$)", re.I)


def scrub(value):
    """运行详情中每次随机或本次新建的值替换为占位（键名命中，或字符串内含渠道订单号 PMXC… / 签名长串）。"""
    if isinstance(value, dict):
        return {k: ("[varies]" if RANDOM_KEYS.search(k) and not isinstance(v, (dict, list)) else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    if isinstance(value, str):
        value = re.sub(r"PMXC\d{12}", "PMXC[varies]", value)
        value = re.sub(r"T?PMXC\[varies\]", "PMXC[varies]", value)
        value = re.sub(r'"sign":"[^"]*"', '"sign":"[varies]"', value)
        value = re.sub(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?([+-]\d\d:\d\d|Z)", "[time]", value)
        value = re.sub(r"(?i)(sign(ature)?[\"']?\s*[:=]\s*[\"']?)[A-Za-z0-9+/=_-]{40,}", r"\1[varies]", value)
        value = re.sub(r"S5_(BASELINE2|AFTER)_(ACCT|CASE)", "S5_[label]_\\2", value)
    return value


def main():
    base, after = load("stage5-baseline.json"), load("stage5-after.json")
    result = {"chain": {}, "cardGate": {}, "sandbox": {}, "snapshot": {}}
    for key in base["chain"]:
        b, a = chain_view(base["chain"][key]), chain_view(after["chain"][key])
        callers = (sorted({e["callerService"] for e in base["chain"][key].get("vaultChannelAccountEvents", [])}),
                   sorted({e["callerService"] for e in after["chain"][key].get("vaultChannelAccountEvents", [])}))
        result["chain"][key] = {"equal": not diff(b, a), "diff": diff(b, a), "vaultCallers": callers}
    result["cardGate"] = {"baseline": base["cardGate"], "after": after["cardGate"],
                          "cardLogDuringRun": [base.get("cardLogDuringRun"), after.get("cardLogDuringRun")]}
    keys = ("createAccount", "createCase", "startRun", "runStatus", "failureSummary", "steps", "channelJournal")
    sb, sa = {k: base["sandbox"][k] for k in keys}, {k: after["sandbox"][k] for k in keys}
    result["sandbox"] = {"equal": not diff(sb, sa), "diff": diff(sb, sa),
                         "codes": [[base["sandbox"]["accountCode"], base["sandbox"]["caseCode"], base["sandbox"]["runCode"]],
                                   [after["sandbox"]["accountCode"], after["sandbox"]["caseCode"], after["sandbox"]["runCode"]]]}
    new_codes = {after["sandbox"]["accountCode"], after["sandbox"]["caseCode"], after["sandbox"]["runCode"]}
    trimmed = without_new(after["snapshot"], new_codes)
    snap_diff = diff(base["snapshot"]["responses"], trimmed)
    calls_b = [c for c in base["snapshot"]["calls"]]
    calls_a = [c for c in after["snapshot"]["calls"] if not any(c[0].endswith("/" + code) for code in new_codes)]
    result["snapshot"] = {"calls": [len(base["snapshot"]["calls"]), len(after["snapshot"]["calls"])],
                          "callsEqualAfterRemovingNew": calls_a == calls_b, "nonOkCalls": [c for c in after["snapshot"]["calls"] if c[1] != 200],
                          "equalAfterRemovingNew": not snap_diff, "diff": snap_diff[:50]}
    # 新建运行详情与基线本轮运行详情（同构用例）
    run_b = base["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/verify-runs/{base['sandbox']['runCode']}"]
    run_a = after["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/verify-runs/{after['sandbox']['runCode']}"]
    run_diff = diff(scrub(run_b), scrub(run_a))
    result["snapshot"]["newRunVsBaselineRun"] = {"equal": not run_diff, "diff": run_diff[:80]}
    for kind in ("verify-accounts", "verify-cases"):
        code_b = base["sandbox"]["accountCode" if kind == "verify-accounts" else "caseCode"]
        code_a = after["sandbox"]["accountCode" if kind == "verify-accounts" else "caseCode"]
        detail_diff = diff(scrub(base["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/{kind}/{code_b}"]),
                           scrub(after["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/{kind}/{code_a}"]))
        result["snapshot"][f"new-{kind}-detail"] = {"equal": not detail_diff, "diff": detail_diff[:40]}
    (HERE / "stage5-compare.json").write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n")
    summary = {
        "chain": {k: [v["equal"], v["vaultCallers"]] for k, v in result["chain"].items()},
        "sandboxEqual": result["sandbox"]["equal"], "snapshotEqualAfterRemovingNew": result["snapshot"]["equalAfterRemovingNew"],
        "callsEqual": result["snapshot"]["callsEqualAfterRemovingNew"],
        "newRunVsBaselineRun": [result["snapshot"]["newRunVsBaselineRun"]["equal"], len(result["snapshot"]["newRunVsBaselineRun"]["diff"])],
        "newAccountDetail": result["snapshot"]["new-verify-accounts-detail"]["equal"],
        "newCaseDetail": result["snapshot"]["new-verify-cases-detail"]["equal"]}
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
