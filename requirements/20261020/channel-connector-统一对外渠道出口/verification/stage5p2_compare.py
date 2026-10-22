"""任务卡 P2-stage5 D：基线（stage5p2-baseline.json，第一段版本）与新版本（stage5p2-after.json，第二段版本）逐项对照，输出 stage5p2-compare.json。

自 stage5_compare.py 复制改写（原文件不改）。每处差异按卡片 D2 的允许类别归类，其余列为"未解释"：
  - ID / 时间 / 请求 ID、本次新建对象（编码、运行号、名称）与每次随机的渠道订单号 / 签名；
  - 接入方相关字段名与路径改为渠道；已删除的接入方列表与详情（基线有、新版本 404）；
  - 渠道管理读取新增字段（channelType、description、endpoints、version、endpointCount、activeGrantCount）；
  - 配置变更目标类型的新名称。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from stage5_compare import diff, scrub as scrub_p1  # noqa: E402

NEW_CHANNEL_FIELDS = {"channelType", "description", "endpoints", "version", "endpointCount", "activeGrantCount"}
TARGET_TYPE_RENAMES = {"INTEGRATION_API": "CHANNEL_API", "INTEGRATION_SCRIPT": "CHANNEL_SCRIPT",
                       "INTEGRATION_IMPLEMENTATION": "CHANNEL_IMPLEMENTATION"}


def load(name):
    return json.loads((HERE / name).read_text())


def chain_view(step):
    if "mine" in step:
        return step
    return {"httpStatus": step["httpStatus"], "outcome": step["outcome"], "failureCode": step["failureCode"],
            "channelStatus": step["channelStatus"], "paymentStatus": step["paymentStatus"],
            "hasChannelTransactionRef": step["hasChannelTransactionRef"], "channelJournal": step["channelJournal"],
            "vault": step.get("vaultChannelAccountEvents"), "cardLog": step.get("cardLog")}


def card_view(real):
    keep = ("instrument", "ticket", "ticketUsedAfterPay", "reuseTicket", "readerCorrelation", "panInLogs")
    out = {k: real.get(k) for k in keep}
    out["pay"] = chain_view(real["pay"]) if real.get("pay") else None
    return out


def scrub(value):
    value = scrub_p1(value)
    text = json.dumps(value, ensure_ascii=False)
    text = re.sub(r"S5_P2(BASELINE\d?|AFTER)_(ACCT|CASE)", r"S5_P2[label]_\2", text)
    text = re.sub(r"阶段 5 P2(BASELINE\d?|AFTER)", "阶段 5 P2[label]", text)
    text = re.sub(r"阶段 5 p2(baseline\d?|after)", "阶段 5 p2[label]", text)
    return json.loads(text)


def without_new(responses, new_codes):
    out = {}
    for name, value in responses.items():
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
        out[name] = value
    return out


def classify_channel_admin(base, after):
    """渠道管理读取：逐处差异归类。"""
    allowed, unexplained = [], []
    for item in diff(base, after):
        path, kind = item[0], item[1]
        leaf = path.rsplit("/", 1)[-1]
        if kind == "missing" and leaf in NEW_CHANNEL_FIELDS and item[2] is False and item[3] is True:
            allowed.append(item + ["第二段新增字段"])
        elif kind == "value" and item[2] == "渠道与账号" and item[3] == "渠道":
            allowed.append(item + ["菜单改名"])
        elif kind == "value" and TARGET_TYPE_RENAMES.get(item[2]) == item[3]:
            allowed.append(item + ["配置变更目标类型新名称"])
        else:
            unexplained.append(item)
    return allowed, unexplained


def main():
    base, after = load("stage5p2-baseline.json"), load("stage5p2-after.json")
    result = {"versions": [base["version"], after["version"]], "chain": {}, "realCard": {}, "cardGate": {}, "sandbox": {},
              "snapshot": {}, "channelAdmin": {}}
    for key in ("candidates", "refund", "payWithCardRead", "notSentStandardApiMissing", "notSentInvalidInput"):
        b, a = chain_view(base["chain"][key]), chain_view(after["chain"][key])
        result["chain"][key] = {"equal": not diff(b, a), "diff": diff(b, a)}
    b, a = card_view(base["chain"]["payWithRealCard"]), card_view(after["chain"]["payWithRealCard"])
    result["realCard"] = {"equal": not diff(b, a), "diff": diff(b, a),
                          "afterPlatformCallerReveal": after["chain"]["payWithRealCard"].get("platformCallerReveal")}
    result["cardGate"] = {"equal": base["cardGate"] == after["cardGate"], "baseline": base["cardGate"], "after": after["cardGate"]}
    keys = ("createAccount", "createCase", "startRun", "runStatus", "failureSummary", "steps", "channelJournal")
    sb, sa = {k: base["sandbox"][k] for k in keys}, {k: after["sandbox"][k] for k in keys}
    result["sandbox"] = {"equal": not diff(sb, sa), "diff": diff(sb, sa),
                         "codes": [[base["sandbox"]["accountCode"], base["sandbox"]["caseCode"], base["sandbox"]["runCode"]],
                                   [after["sandbox"]["accountCode"], after["sandbox"]["caseCode"], after["sandbox"]["runCode"]]]}

    # 外部 API 管理读取：去掉新版本本次新建对象；基线的接入方列表与详情在新版本已删除（单列）
    new_codes = {after["sandbox"]["accountCode"], after["sandbox"]["caseCode"], after["sandbox"]["runCode"]}
    removed_names = [n for n in base["snapshot"]["responses"] if n in ("integrations", "integrations/PAYERMAX_MOCK")]
    base_resp = {k: v for k, v in base["snapshot"]["responses"].items() if k not in removed_names}
    after_resp = without_new(after["snapshot"]["responses"], new_codes)
    snap_diff = diff(base_resp, after_resp)
    calls_b = [c for c in base["snapshot"]["calls"] if c[0] not in removed_names]
    calls_a = [c for c in after["snapshot"]["calls"] if not any(c[0].endswith("/" + code) for code in new_codes)]
    result["snapshot"] = {
        "calls": [len(base["snapshot"]["calls"]), len(after["snapshot"]["calls"])],
        "callsEqualAfterRemovingNewAndRemoved": calls_a == calls_b,
        "nonOkCalls": [c for c in after["snapshot"]["calls"] if c[1] != 200],
        "removedIntegrationCrud": {"baseline": {n: base["snapshot"]["responses"][n]["status"] for n in removed_names},
                                   "after": after["snapshot"]["removedIntegrationCrud"]},
        "equalAfterRemovingNew": not snap_diff, "diff": snap_diff[:80]}
    run_b = base["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/verify-runs/{base['sandbox']['runCode']}"]
    run_a = after["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/verify-runs/{after['sandbox']['runCode']}"]
    run_diff = diff(scrub(run_b), scrub(run_a))
    result["snapshot"]["newRunVsBaselineRun"] = {"equal": not run_diff, "diff": run_diff[:80]}
    for kind, key in (("verify-accounts", "accountCode"), ("verify-cases", "caseCode")):
        detail_diff = diff(scrub(base["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/{kind}/{base['sandbox'][key]}"]),
                           scrub(after["snapshot"]["responses"][f"integrations/PAYERMAX_MOCK/{kind}/{after['sandbox'][key]}"]))
        result["snapshot"][f"new-{kind}-detail"] = {"equal": not detail_diff, "diff": detail_diff[:40]}

    for name, value in base["snapshot"]["channelAdmin"].items():
        allowed, unexplained = classify_channel_admin(value, after["snapshot"]["channelAdmin"][name])
        result["channelAdmin"][name] = {"allowed": allowed, "unexplained": unexplained}

    (HERE / "stage5p2-compare.json").write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n")
    summary = {
        "chain": {k: v["equal"] for k, v in result["chain"].items()},
        "realCardEqual": result["realCard"]["equal"], "platformCallerReveal": result["realCard"]["afterPlatformCallerReveal"],
        "cardGateEqual": result["cardGate"]["equal"], "sandboxEqual": result["sandbox"]["equal"],
        "snapshotEqualAfterRemovingNew": result["snapshot"]["equalAfterRemovingNew"],
        "callsEqual": result["snapshot"]["callsEqualAfterRemovingNewAndRemoved"],
        "removedIntegrationCrud": result["snapshot"]["removedIntegrationCrud"],
        "newRunVsBaselineRun": [result["snapshot"]["newRunVsBaselineRun"]["equal"], len(result["snapshot"]["newRunVsBaselineRun"]["diff"])],
        "newAccountDetail": result["snapshot"]["new-verify-accounts-detail"]["equal"],
        "newCaseDetail": result["snapshot"]["new-verify-cases-detail"]["equal"],
        "channelAdmin": {k: [len(v["allowed"]), len(v["unexplained"])] for k, v in result["channelAdmin"].items()}}
    print(json.dumps(summary, ensure_ascii=False, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
