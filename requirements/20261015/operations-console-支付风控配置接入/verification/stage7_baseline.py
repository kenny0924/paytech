"""阶段 7 联调基线：经 mor-test 运营入口（→ Gateway → BFF → Risk）配置并激活支付风控基线，留给 Payment 联调。

阈值按用户确认的 release.md 第 6 节 D1：3DS 门槛 500、审核门槛 5000（USD）、同卡 1 小时评估 ≥ 5 次拒绝、黑名单卡拒绝。
生效确认经临时代理直连 Risk 评估 4 次（合成指纹）；为此加入的合成黑名单成员在确认后失效。
复用 stage5_api.py 的请求构造与证据记录；不打印凭据、Token 与主体原值。
用法：E2E_TARGET=test RISK_EVALUATION_URL=http://127.0.0.1:28114 python3 -I stage7_baseline.py <证据文件>
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import stage5_api as s5  # noqa: E402

LIST, REASON = "PAY_CARD_BLOCK", "PAY_RISK_DECLINED"
VELOCITY, LISTED = "PAY_CARD_ATTEMPTS_1H", "PAY_CARD_BLOCKED"
RAW_BLOCKED = f"fp_prc_baseline_{s5.SFX}"
s5.RAWS.append(RAW_BLOCKED)


def named_rule_set(code, name, rules):
    body = s5.rule_set(code, rules)
    body.update({"ruleSetName": name, "changeReason": "mor-test 支付风控联调基线"})
    return body


def configure(c):
    s5.section("1. 名单、拒绝原因与变量")
    c.call("新建卡指纹黑名单", "POST", "/risk-lists/v1/lists",
           {"listCode": LIST, "listName": "支付卡指纹黑名单", "subjectType": "CARD_FINGERPRINT", "purpose": "BLOCK"})
    c.call("新建拒绝原因", "POST", "/decision-dictionaries/v1/rejection-reasons",
           {"reasonCode": REASON, "name": "支付被风控拒绝", "description": None, "defaultMessage": "支付被风控拒绝"})
    base = {"description": None, "valueType": "NUMBER", "pointCode": s5.PAYMENT, "sourceContractCode": None, "sourceContractVersion": None,
            "sourceField": None, "usages": ["RULE_CONDITION", "FLOW_CONDITION"], "formatCode": "NONE", "numericUnit": "SCALAR",
            "currencyCode": None, "currencyVariableCode": None, "allowedValuesVariableCode": None, "currentValue": None, "status": "ACTIVE"}
    c.call("新建同卡 1 小时评估次数", "POST", "/decision-variables/v1/variables",
           {**base, "variableCode": VELOCITY, "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY", "list": None,
            "velocity": {"eventCode": "PAYMENT_ATTEMPT_ASSESSED", "anchorSubjectType": "CARD_FINGERPRINT", "metric": "COUNT",
                         "distinctSubjectType": None, "windowSeconds": 3600, "includeCurrent": False}}, expect=201)
    c.call("新建卡指纹命中黑名单", "POST", "/decision-variables/v1/variables",
           {**base, "variableCode": LISTED, "variableName": "卡指纹在支付黑名单", "sourceType": "LIST", "valueType": "BOOLEAN",
            "numericUnit": None, "velocity": None, "list": {"listCode": LIST, "subjectType": "CARD_FINGERPRINT"}}, expect=201)

    s5.section("2. 规则集、决策流与执行发布")
    block, _ = s5.frozen(c, "拦截", named_rule_set("PAY_BLOCK", "支付拦截", [
        s5.rule("BLOCKED_CARD", 10, "DENY_PAYMENT", "EQ", s5.var(LISTED, "BOOLEAN"), s5.lit("BOOLEAN", True), REASON),
        s5.rule("CARD_FREQUENCY", 20, "DENY_PAYMENT", "GTE", s5.var(VELOCITY, "NUMBER"), s5.lit("NUMBER", "5", "SCALAR"), REASON),
        s5.rule("LARGE_AMOUNT", 30, "REVIEW_PAYMENT", "GT", s5.var("PAY_INPUT_AMOUNT", "NUMBER"),
                s5.lit("NUMBER", "5000", "MONEY", "PAY_INPUT_CURRENCY"))]))
    auth, _ = s5.frozen(c, "认证", named_rule_set("PAY_AUTH", "支付认证（金额 > 500 需要 3DS）", [
        s5.rule("THREE_DS_AMOUNT", 10, "ALLOW_PAYMENT", "GT", s5.var("PAY_INPUT_AMOUNT", "NUMBER"),
                s5.lit("NUMBER", "500", "MONEY", "PAY_INPUT_CURRENCY"))]))
    flow = {"pointCode": s5.PAYMENT, "flowCode": "PAY_ATTEMPT_FLOW", "flowName": "支付尝试风控流程", "baseFlowVersionId": None,
            "expectedVersion": None, "changeReason": "mor-test 支付风控联调基线", "graph": s5.graph(block, auth, REASON)}
    _, saved = c.call("保存决策流", "POST", "/decision-flows/v1/flows", flow)
    release_id = s5.release(c, "基线发布", saved["flowVersionId"])
    _, activated = c.call("激活基线发布", "POST", f"/decision-releases/v1/executions/{release_id}/activate")
    s5.check("基线发布激活后为 ACTIVE", activated.get("status") == "ACTIVE")


def confirm(c):
    s5.section("3. 生效确认（经临时代理直连 Risk，合成指纹）")
    _, entry = c.call("加入合成黑名单成员", "POST", f"/risk-lists/v1/lists/{LIST}/entries",
                      {"subjectValue": RAW_BLOCKED, "effectiveFrom": None, "effectiveTo": None, "reason": "联调基线生效确认"})
    for name, amount, fp, result, auth in [("100", "100.00", f"fp_prc_a_{s5.SFX}", "ALLOW", "NOT_REQUIRED"),
                                           ("800", "800.00", f"fp_prc_b_{s5.SFX}", "ALLOW", "THREE_DS_REQUIRED"),
                                           ("6000", "6000.00", f"fp_prc_c_{s5.SFX}", "REVIEW", None),
                                           ("黑名单卡", "100.00", RAW_BLOCKED, "DENY", None)]:
        data = s5.evaluate(name, amount, fp)
        s5.check(f"{name} → {result}/{auth}", data.get("result") == result
                 and data.get("outputs", {}).get("AUTHENTICATION_REQUIREMENT") == auth)
    _, gone = c.call("失效合成黑名单成员", "POST", f"/risk-lists/v1/lists/{LIST}/entries/{entry['entryId']}/deactivate",
                     {"reason": "联调基线生效确认完成"})
    s5.check("合成成员已失效", gone.get("status") != "ACTIVE")


def main():
    s5.lines[0] = "# 阶段 7 联调基线配置证据"
    c = s5.Console()
    configure(c)
    confirm(c)
    s5.lines.extend(["", "## 结论", "", f"{sum(ok for _, ok in s5.results)}/{len(s5.results)} 项检查通过。"])
    s5.OUT.write_text("\n".join(s5.lines) + "\n", encoding="utf-8")
    print(f"{sum(ok for _, ok in s5.results)}/{len(s5.results)} 通过，证据 {s5.OUT}")
    sys.exit(0 if all(ok for _, ok in s5.results) else 1)


if __name__ == "__main__":
    main()
