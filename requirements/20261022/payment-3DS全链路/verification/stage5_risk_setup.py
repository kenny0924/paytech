"""阶段 5（tasks/E-stage5.md，用户 T1 决定）：经运营入口（Nginx 18444 → Gateway → ops-bff → risk-decision）为 PAYMENT_ATTEMPT_RISK 建立测试发布。

规则（统一变量格式，发布绑定支付点新冻结版本 220003）：
  拦截规则集 S5_BLOCK：PAY_INPUT_ISSUER_COUNTRY IN [NG] → REVIEW_PAYMENT（BIN AC-08：规则可按发卡国命中）
  认证规则集 S5_AUTH ：PAY_INPUT_AMOUNT EQ 333 USD（标记金额）→ ALLOW_PAYMENT；PAY_INPUT_ISSUER_COUNTRY EQ GB → ALLOW_PAYMENT
  流程：开始 → 拦截 →（节点审核）审核结束 / 否则 → 认证 →（有命中）放行 + AUTHENTICATION_REQUIREMENT=THREE_DS_REQUIRED / 否则放行
与 risk-decision 自动测试 PaymentRiskCardAttributeApiAutoTest 的 twoStageGraph 同形（去掉拒绝结束）。
CHECKOUT_PREPARE 不建（T2 不做，acquiring 不运行）。输出发布 ID 与点版本，写 stage5-risk-setup.json；不打印令牌或口令。
"""
import json
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
from e2e_http import Failure, Operations, secrets, sql  # noqa: E402

POINT = "PAYMENT_ATTEMPT_RISK"
EXECUTIONS = "/decision-releases/v1/executions"
STAMP = time.strftime("%m%d%H%M%S")


def operand(role, source, value_type, literal=None, variable=None, unit=None, currency_variable=None):
    return {"operandRole": role, "valueSequence": 1, "sourceType": source, "fieldCode": None, "valueType": value_type,
            "literalValue": literal, "variableCode": variable, "numericUnit": unit, "currencyCode": None,
            "currencyVariableCode": currency_variable}


def predicate(operator, left, right):
    return {"nodeKey": "root", "parentNodeKey": None, "sequenceNo": 1, "nodeType": "PREDICATE", "groupOperator": None,
            "comparisonOperator": operator, "operands": [left, right]}


def rule(code, priority, action, node):
    return {"ruleCode": code, "ruleName": code, "priorityNo": priority, "enabledFlag": True, "reasonCode": None,
            "conditionNodes": [node], "action": {"actionCode": action, "actionVersionNo": 1}, "reasonMessage": None,
            "additionalActions": []}


def frozen_rule_set(ops, code, rules):
    created = ops.send("POST", "/decision-rule-sets/v1/rule-sets", {
        "pointCode": POINT, "ruleSetCode": code, "ruleSetName": code, "baseRuleSetVersionId": None, "baseContentHash": None,
        "changeReason": "3DS / BIN 阶段 5 本机端到端", "rules": rules, "operandFormat": "VARIABLE_REFERENCE"})
    version_id = created["ruleSetVersionId"]
    ops.send("POST", f"/decision-rule-sets/v1/rule-sets/{version_id}/freeze", {"expectedContentHash": created["contentHash"]})
    return str(version_id)


def node_condition(variable, value_type, literal):
    return {"schemaVersion": 2, "root": {"kind": "PREDICATE", "operator": "EQ", "operands": [
        operand("LEFT", "VARIABLE", value_type, variable=variable), operand("RIGHT", "LITERAL", value_type, literal=literal)]}}


def graph(block, auth):
    def node(key, kind, name, x, y, rule_set=None, decision=None, outputs=None):
        item = {"nodeKey": key, "type": kind, "name": name, "position": {"x": str(x), "y": str(y)},
                "ruleSetVersionId": rule_set, "decisionCode": decision, "reasonCode": None, "reasonMessage": None}
        if outputs:
            item["outputs"] = outputs
        return item

    def edge(key, source, target, kind, priority, name, condition=None):
        return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target, "type": kind, "priority": priority,
                "name": name, "condition": condition}
    return {"schemaVersion": 1, "nodes": [
        node("start", "START", "Start", 0, 0), node("block", "RULE_SET", "Block", 120, 0, block),
        node("auth", "RULE_SET", "Auth", 240, 0, auth), node("review", "END", "Review", 240, -120, decision="REVIEW"),
        node("allow", "END", "Allow", 360, 0, decision="ALLOW"),
        node("allow_3ds", "END", "Allow with 3DS", 360, 120, decision="ALLOW",
             outputs={"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"})],
        "edges": [edge("enter", "start", "block", "UNCONDITIONAL", 0, "Enter"),
                  edge("to_review", "block", "review", "CONDITIONAL", 1, "Reviewed",
                       node_condition("PAY_NODE_DECISION", "STRING", "REVIEW")),
                  edge("to_auth", "block", "auth", "ELSE", 0, "Otherwise"),
                  edge("to_3ds", "auth", "allow_3ds", "CONDITIONAL", 1, "Authentication",
                       node_condition("PAY_NODE_MATCHED", "BOOLEAN", True)),
                  edge("finish", "auth", "allow", "ELSE", 0, "Finish")]}


def main():
    s = secrets()
    ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    try:
        block = frozen_rule_set(ops, f"S5_BLOCK_{STAMP}", [rule("ISSUER_NG", 10, "REVIEW_PAYMENT", predicate(
            "IN", operand("LEFT", "VARIABLE", "STRING", variable="PAY_INPUT_ISSUER_COUNTRY"),
            operand("RIGHT", "LITERAL", "STRING", literal="NG")))])
        auth = frozen_rule_set(ops, f"S5_AUTH_{STAMP}", [
            rule("MARKER_AMOUNT_333", 10, "ALLOW_PAYMENT", predicate(
                "EQ", operand("LEFT", "VARIABLE", "NUMBER", variable="PAY_INPUT_AMOUNT"),
                operand("RIGHT", "LITERAL", "NUMBER", literal="333", unit="MONEY", currency_variable="PAY_INPUT_CURRENCY"))),
            rule("ISSUER_GB", 20, "ALLOW_PAYMENT", predicate(
                "EQ", operand("LEFT", "VARIABLE", "STRING", variable="PAY_INPUT_ISSUER_COUNTRY"),
                operand("RIGHT", "LITERAL", "STRING", literal="GB")))])
        flow = ops.send("POST", "/decision-flows/v1/flows", {
            "pointCode": POINT, "flowCode": f"S5_CARD3DS_{STAMP}", "flowName": "3DS / BIN 阶段 5", "baseFlowVersionId": None,
            "expectedVersion": None, "changeReason": "3DS / BIN 阶段 5 本机端到端", "graph": graph(block, auth)})
        release = ops.send("POST", EXECUTIONS, {"pointCode": POINT, "flowVersionId": str(flow["flowVersionId"])})
        release_id = release["releaseId"]
        validated = ops.send("POST", f"{EXECUTIONS}/{release_id}/validate")
        if validated.get("status") != "SHADOWING":
            raise SystemExit(f"校验未通过：{json.dumps(validated, ensure_ascii=False)[:800]}")
        activated = ops.send("POST", f"{EXECUTIONS}/{release_id}/activate")
    except Failure as failure:
        raise SystemExit(f"FAIL {failure}") from None
    point_version = sql("risk-decision", f"SELECT point_version_id, environment_code, status FROM t_decision_release WHERE release_id={release_id}")
    result = {"blockRuleSetVersionId": block, "authRuleSetVersionId": auth, "flowVersionId": str(flow["flowVersionId"]),
              "releaseId": str(release_id), "status": activated.get("status"), "pointVersionId": point_version}
    (HERE / "stage5-risk-setup.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
