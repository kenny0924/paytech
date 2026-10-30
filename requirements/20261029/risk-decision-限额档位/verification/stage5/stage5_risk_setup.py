#!/usr/bin/env python3
"""REQ-04 阶段 5：按 paytech/risk-decision/docs/limit-tier-configuration.md，经运营入口（Nginx 18444 → Gateway → ops-bff → risk-decision）
为 PAYMENT_ATTEMPT_RISK 与 CHECKOUT_PREPARE 建立带档位分支的执行发布。

  build     建拒绝原因、档位名单变量、放行累计变量（DAILY_SUM_AMOUNT）、额度常量、币种集合、档位分支 / 额度规则集与流程，
            建执行发布并校验到 SHADOWING，不激活；记录当前 ACTIVE 发布（回滚基线）。结果写 stage5-risk-setup.json。
  activate  激活 build 建好的两个发布（迁移核对 0 缺口之后执行）。
  rollback  用回滚基线的流程版本新建执行发布并激活（已作废的发布不能重新激活）。

支付点：START → 档位分支 → RESTRICTED 拒绝 / VERIFICATION / OBSERVATION / STANDARD 额度规则集 → 超额拒绝，否则进入原流程
        （block → auth → 放行 / 3DS 放行，原规则集版本原样引用）；不在任何档位拒绝。
结账点：START → 档位分支 → RESTRICTED 拒绝 / VERIFICATION / OBSERVATION 币种集合规则集 → 不在集合拒绝，否则放行；STANDARD 放行；无档位拒绝。
额度（本机 TEST 值，非风控正式值）：见 QUOTAS。不打印令牌或口令。
"""
import json
import sys
import time

from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as C  # noqa: E402

OA = C.OA
PAY, CO = "PAYMENT_ATTEMPT_RISK", "CHECKOUT_PREPARE"
EXECUTIONS = "/decision-releases/v1/executions"
STATE = "stage5-risk-setup.json"
STAMP = time.strftime("%m%d%H%M")
TIERS = ("VERIFICATION", "OBSERVATION", "STANDARD", "RESTRICTED")
# 档位 → 币种 → (单笔, UTC 当日, 近 30 个 UTC 日)
QUOTAS = {
    "VERIFICATION": {"USD": ("50", "250", "400"), "AED": ("200", "1000", "3000")},
    "OBSERVATION": {"USD": ("2000", "10000", "40000"), "AED": ("7000", "35000", "140000")},
    "STANDARD": {"USD": ("10000", "100000", "1000000"), "AED": ("36000", "360000", "3600000")},
}
CURRENCY_SETS = {"VERIFICATION": ("USD", "AED"), "OBSERVATION": ("USD", "AED")}  # STANDARD 不建：未配置币种放行
REASON = "LIMIT_TIER_DENIED"
DAY, MONTH = "PAY_CLIENT_ALLOWED_DAY", "PAY_CLIENT_ALLOWED_30D"
# 原支付流程（S5_CARD3DS v1）引用的冻结规则集版本；原结账流程为 START → 放行
PAY_BLOCK, PAY_AUTH = "239045321827565568", "239045322867752960"


def send(ops, method, path, body=None, ok=(200, 201)):
    status, reply = ops.call(method, path, body, json_empty=body is None and method != "GET")
    if status not in ok:
        raise SystemExit(f"FAIL {method} {path} HTTP {status} {OA.problem(reply)}")
    return reply


def exists(ops, path):
    status, _ = ops.get(path)
    return status == 200


# ---------------------------------------------------------------------------------------------------------------- 变量

def operand(role, source, value_type, literal=None, variable=None, unit=None, currency_variable=None):
    return {"operandRole": role, "valueSequence": 1, "sourceType": source, "fieldCode": None, "valueType": value_type,
            "literalValue": literal, "variableCode": variable, "numericUnit": unit, "currencyCode": None,
            "currencyVariableCode": currency_variable}


def variable(code, name, source, value_type, point, **extra):
    """ops-bff 的变量写入要求 record 全部键（不适用的显式 null）。"""
    body = {"variableCode": code, "variableName": name, "description": None, "sourceType": source, "valueType": value_type,
            "pointCode": point, "sourceContractCode": None, "sourceContractVersion": None, "sourceField": None,
            "usages": ["RULE_CONDITION", "FLOW_CONDITION"], "formatCode": "NONE", "numericUnit": None, "currencyCode": None,
            "currencyVariableCode": None, "allowedValuesVariableCode": None, "currentValue": None, "status": "ACTIVE",
            "velocity": None, "list": None}
    body.update(extra)
    return body


def ensure_variable(ops, body, members=None):
    code = body["variableCode"]
    if exists(ops, f"/decision-variables/v1/variables/{code}"):
        print(f"exists   {code}")
        return
    send(ops, "POST", "/decision-variables/v1/variables", body)
    if members:
        send(ops, "PUT", f"/decision-variables/v1/variables/{code}/members",
             {"expectedVersion": "1", "members": [{"value": m, "displayName": m} for m in members]})
    print(f"created  {code}")


def variables(ops):
    for tier in TIERS:
        for prefix, point in (("PAY", PAY), ("CO", CO)):
            ensure_variable(ops, variable(f"{prefix}_TIER_IN_{tier}", f"{'支付' if point == PAY else '结账'}档位 {tier}", "LIST",
                                          "BOOLEAN", point, list={"listCode": f"TIER_{tier}", "subjectType": "CLIENT"}))
    for code, window, name in ((DAY, 86400, "Client UTC 当日放行合计"), (MONTH, 2592000, "Client 近 30 个 UTC 日放行合计")):
        ensure_variable(ops, variable(code, name, "VELOCITY", "NUMBER", PAY, numericUnit="MONEY",
                                      currencyVariableCode="PAY_INPUT_CURRENCY",
                                      velocity={"eventCode": "PAYMENT_ATTEMPT_ALLOWED", "anchorSubjectType": "CLIENT",
                                                "metric": "DAILY_SUM_AMOUNT", "distinctSubjectType": None,
                                                "windowSeconds": window, "includeCurrent": True}))
    for tier, currencies in QUOTAS.items():
        for currency, values in currencies.items():
            for period, value in zip(("SINGLE", "DAY", "MONTH"), values):
                ensure_variable(ops, variable(f"TIER_{tier}_{currency}_{period}", f"{tier} {currency} {period} 上限", "CONSTANT",
                                              "NUMBER", PAY, numericUnit="MONEY", currencyVariableCode="PAY_INPUT_CURRENCY",
                                              currentValue=value))
    for tier, currencies in CURRENCY_SETS.items():
        ensure_variable(ops, variable(f"TIER_{tier}_CURRENCIES", f"{tier} 允许币种", "COLLECTION", "STRING", None,
                                      formatCode="UPPER_ALPHA_3"), members=currencies)


def ensure_reason(ops):
    if exists(ops, f"/decision-dictionaries/v1/rejection-reasons/{REASON}"):
        return
    send(ops, "POST", "/decision-dictionaries/v1/rejection-reasons",
         {"reasonCode": REASON, "name": "超出档位额度", "description": "REQ-04 限额档位：无档位、受限档位、币种或额度超出",
          "defaultMessage": "超出档位额度"})


# ---------------------------------------------------------------------------------------------------------------- 规则集

def predicate(key, parent, seq, operator, left, right):
    return {"nodeKey": key, "parentNodeKey": parent, "sequenceNo": seq, "nodeType": "PREDICATE", "groupOperator": None,
            "comparisonOperator": operator, "operands": [left, right]}


def group(key="root"):
    return {"nodeKey": key, "parentNodeKey": None, "sequenceNo": 1, "nodeType": "GROUP", "groupOperator": "AND",
            "comparisonOperator": None, "operands": []}


def right_variable(code, value_type):
    return dict(operand("RIGHT", "VARIABLE", value_type, variable=code))


def deny_rule(code, priority, action, nodes):
    return {"ruleCode": code, "ruleName": code, "priorityNo": priority, "enabledFlag": True, "reasonCode": REASON,
            "conditionNodes": nodes, "action": {"actionCode": action, "actionVersionNo": 1}, "reasonMessage": "超出档位额度",
            "additionalActions": []}


def frozen_rule_set(ops, point, code, rules):
    created = send(ops, "POST", "/decision-rule-sets/v1/rule-sets", {
        "pointCode": point, "ruleSetCode": code, "ruleSetName": code, "baseRuleSetVersionId": None, "baseContentHash": None,
        "changeReason": "REQ-04 阶段 5 本机端到端", "rules": rules, "operandFormat": "VARIABLE_REFERENCE"})
    version_id = str(created["ruleSetVersionId"])
    send(ops, "POST", f"/decision-rule-sets/v1/rule-sets/{version_id}/freeze", {"expectedContentHash": created["contentHash"]})
    return version_id


def currency_and(currency, left_code, constant):
    return [group(), predicate("currency", "root", 1, "EQ", operand("LEFT", "VARIABLE", "STRING", variable="PAY_INPUT_CURRENCY"),
                               operand("RIGHT", "LITERAL", "STRING", literal=currency)),
            predicate("cmp", "root", 2, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable=left_code),
                      right_variable(constant, "NUMBER"))]


def not_in(currency_variable, collection):
    return [predicate("root", None, 1, "NOT_IN", operand("LEFT", "VARIABLE", "STRING", variable=currency_variable),
                      right_variable(collection, "STRING"))]


def pay_limit_rule_set(ops, tier):
    rules = []
    if tier in CURRENCY_SETS:
        rules.append(("CURRENCY_NOT_IN_TIER", not_in("PAY_INPUT_CURRENCY", f"TIER_{tier}_CURRENCIES")))
    for currency in QUOTAS[tier]:
        rules.append((f"{currency}_SINGLE", currency_and(currency, "PAY_INPUT_AMOUNT", f"TIER_{tier}_{currency}_SINGLE")))
        rules.append((f"{currency}_DAY", currency_and(currency, DAY, f"TIER_{tier}_{currency}_DAY")))
        rules.append((f"{currency}_MONTH", currency_and(currency, MONTH, f"TIER_{tier}_{currency}_MONTH")))
    return frozen_rule_set(ops, PAY, f"RQ04_PAY_TIER_{tier[0]}_{STAMP}",
                           [deny_rule(code, (i + 1) * 10, "DENY_PAYMENT", nodes) for i, (code, nodes) in enumerate(rules)])


def gate_rule_set(ops, point):
    """档位分支节点：一条无终态动作的规则，只产生轨迹。"""
    if point == PAY:
        node = predicate("root", None, 1, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable="PAY_INPUT_AMOUNT"),
                         operand("RIGHT", "LITERAL", "NUMBER", literal="0", unit="MONEY", currency_variable="PAY_INPUT_CURRENCY"))
    else:
        node = predicate("root", None, 1, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable="INPUT_LINE_COUNT"),
                         operand("RIGHT", "LITERAL", "NUMBER", literal="0", unit="SCALAR"))
    rule = {"ruleCode": "TIER_GATE", "ruleName": "档位分支", "priorityNo": 10, "enabledFlag": True, "reasonCode": None,
            "conditionNodes": [node], "action": None, "reasonMessage": None, "additionalActions": []}
    return frozen_rule_set(ops, point, f"RQ04_{'PAY' if point == PAY else 'CO'}_TIER_GATE_{STAMP}", [rule])


def checkout_currency_rule_set(ops, tier):
    return frozen_rule_set(ops, CO, f"RQ04_CO_TIER_{tier[0]}_{STAMP}", [
        deny_rule("CURRENCY_NOT_IN_TIER", 10, "DENY_CHECKOUT", not_in("INPUT_CURRENCY", f"TIER_{tier}_CURRENCIES"))])


# ---------------------------------------------------------------------------------------------------------------- 流程

def condition(variable_code, value_type, literal):
    return {"schemaVersion": 2, "root": {"kind": "PREDICATE", "operator": "EQ", "operands": [
        operand("LEFT", "VARIABLE", value_type, variable=variable_code), operand("RIGHT", "LITERAL", value_type, literal=literal)]}}


def node(key, kind, name, x, y, rule_set=None, decision=None, reason=None, message=None, outputs=None):
    item = {"nodeKey": key, "type": kind, "name": name, "position": {"x": str(x), "y": str(y)}, "ruleSetVersionId": rule_set,
            "decisionCode": decision, "reasonCode": reason, "reasonMessage": message}
    if outputs:
        item["outputs"] = outputs
    return item


def edge(key, source, target, kind, priority, name, cond=None):
    return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target, "type": kind, "priority": priority, "name": name,
            "condition": cond}


def tier_layer(prefix, gate, branches, after):
    """START → 档位分支 → 受限拒绝 / 各档位节点 / 无档位拒绝；branches: [(tier, nodeKey 或 None 表示直接到 after)]。"""
    nodes = [node("start", "START", "Start", 0, 0), node("tier_gate", "RULE_SET", "档位分支", 120, 0, gate),
             node("deny_restricted", "END", "受限档位", 240, -360, decision="DENY", reason=REASON, message="受限档位"),
             node("deny_no_tier", "END", "无档位", 240, 360, decision="DENY", reason=REASON, message="未完成承保 / 无交易额度"),
             node("deny_limit", "END", "超出档位额度", 480, -240, decision="DENY", reason=REASON, message="超出档位额度")]
    edges = [edge("enter", "start", "tier_gate", "UNCONDITIONAL", 0, "Enter"),
             edge("to_restricted", "tier_gate", "deny_restricted", "CONDITIONAL", 1, "受限",
                  condition(f"{prefix}_TIER_IN_RESTRICTED", "BOOLEAN", True)),
             edge("to_no_tier", "tier_gate", "deny_no_tier", "ELSE", 0, "无档位")]
    node_decision = "PAY_NODE_DECISION" if prefix == "PAY" else "NODE_DECISION"
    for index, (tier, rule_set) in enumerate(branches):
        cond = condition(f"{prefix}_TIER_IN_{tier}", "BOOLEAN", True)
        if rule_set is None:
            edges.append(edge(f"to_{tier.lower()}", "tier_gate", after, "CONDITIONAL", index + 2, tier, cond))
            continue
        key = f"tier_{tier.lower()}"
        nodes.append(node(key, "RULE_SET", f"档位额度 · {tier}", 240, -120 + index * 120, rule_set))
        edges += [edge(f"to_{tier.lower()}", "tier_gate", key, "CONDITIONAL", index + 2, tier, cond),
                  edge(f"{tier.lower()}_limit", key, "deny_limit", "CONDITIONAL", 1, "超额", condition(node_decision, "STRING", "DENY")),
                  edge(f"{tier.lower()}_pass", key, after, "ELSE", 0, "额度内")]
    return nodes, edges


def pay_graph(gate, limits):
    nodes, edges = tier_layer("PAY", gate, [(t, limits[t]) for t in ("VERIFICATION", "OBSERVATION", "STANDARD")], "block")
    nodes += [node("block", "RULE_SET", "Block", 600, 0, PAY_BLOCK), node("auth", "RULE_SET", "Auth", 720, 0, PAY_AUTH),
              node("review", "END", "Review", 720, -120, decision="REVIEW"), node("allow", "END", "Allow", 840, 0, decision="ALLOW"),
              node("allow_3ds", "END", "Allow with 3DS", 840, 120, decision="ALLOW",
                   outputs={"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"})]
    edges += [edge("to_review", "block", "review", "CONDITIONAL", 1, "Reviewed", condition("PAY_NODE_DECISION", "STRING", "REVIEW")),
              edge("to_auth", "block", "auth", "ELSE", 0, "Otherwise"),
              edge("to_3ds", "auth", "allow_3ds", "CONDITIONAL", 1, "Authentication", condition("PAY_NODE_MATCHED", "BOOLEAN", True)),
              edge("finish", "auth", "allow", "ELSE", 0, "Finish")]
    return {"schemaVersion": 1, "nodes": nodes, "edges": edges}


def checkout_graph(gate, sets):
    nodes, edges = tier_layer("CO", gate, [("VERIFICATION", sets["VERIFICATION"]), ("OBSERVATION", sets["OBSERVATION"]),
                                          ("STANDARD", None)], "allow")
    nodes.append(node("allow", "END", "Allow", 600, 0, decision="ALLOW"))
    return {"schemaVersion": 1, "nodes": nodes, "edges": edges}


def save_flow(ops, point, code, name, graph):
    return str(send(ops, "POST", "/decision-flows/v1/flows", {
        "pointCode": point, "flowCode": code, "flowName": name, "baseFlowVersionId": None, "expectedVersion": None,
        "changeReason": "REQ-04 阶段 5 本机端到端：档位分支", "graph": graph})["flowVersionId"])


def validated_release(ops, point, flow_version_id):
    release_id = str(send(ops, "POST", EXECUTIONS, {"pointCode": point, "flowVersionId": flow_version_id})["releaseId"])
    validated = send(ops, "POST", f"{EXECUTIONS}/{release_id}/validate")
    if validated.get("status") != "SHADOWING":
        raise SystemExit(f"校验未通过 {point}：{json.dumps(validated, ensure_ascii=False)[:800]}")
    return release_id


def active_releases():
    rows = C.root_sql("SELECT p.point_code, r.release_id, r.decision_flow_version_id, f.flow_code FROM t_decision_release r "
                      "JOIN t_decision_point p ON p.decision_point_id = r.decision_point_id "
                      "JOIN t_decision_flow_version v ON v.flow_version_id = r.decision_flow_version_id "
                      "JOIN t_decision_flow f ON f.flow_id = v.flow_id WHERE r.status = 'ACTIVE'", C.RD)
    return {row["point_code"]: {"releaseId": row["release_id"], "flowVersionId": row["decision_flow_version_id"],
                                "flowCode": row["flow_code"]} for row in rows}


# ---------------------------------------------------------------------------------------------------------------- 命令

def build():
    ops = C.admin()
    state = C.load_state(STATE)
    state.setdefault("baseline", active_releases())
    ensure_reason(ops)
    variables(ops)
    pay_gate, co_gate = gate_rule_set(ops, PAY), gate_rule_set(ops, CO)
    limits = {tier: pay_limit_rule_set(ops, tier) for tier in QUOTAS}
    sets = {tier: checkout_currency_rule_set(ops, tier) for tier in CURRENCY_SETS}
    pay_flow = save_flow(ops, PAY, f"RQ04_PAY_TIER_{STAMP}", "REQ-04 支付尝试 · 档位分支", pay_graph(pay_gate, limits))
    co_flow = save_flow(ops, CO, f"RQ04_CO_TIER_{STAMP}", "REQ-04 结账 · 档位分支", checkout_graph(co_gate, sets))
    state["candidate"] = {PAY: {"flowVersionId": pay_flow, "releaseId": validated_release(ops, PAY, pay_flow),
                                "gateRuleSet": pay_gate, "limitRuleSets": limits},
                          CO: {"flowVersionId": co_flow, "releaseId": validated_release(ops, CO, co_flow),
                               "gateRuleSet": co_gate, "currencyRuleSets": sets}}
    state["quotas"] = QUOTAS
    state["currencySets"] = CURRENCY_SETS
    state["builtAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    C.save_state(STATE, state)
    print(json.dumps(state, ensure_ascii=False))


def shadow(ops, release_id, sample_target=200):
    """激活前置：以当前生效发布为基线回放最近的成功正式评估，等待批次完成并返回汇总（含放行 → 拒绝的条数）。"""
    send(ops, "POST", f"{EXECUTIONS}/{release_id}/shadow-runs", {"sampleTarget": sample_target})
    for _ in range(90):
        run = (send(ops, "GET", f"{EXECUTIONS}/{release_id}") or {}).get("shadowRun") or {}
        if run.get("status") not in (None, "RUNNING", "PENDING"):
            return run
        time.sleep(2)
    raise SystemExit(f"影子运行未在 180 秒内完成：{release_id}")


def activate():
    ops = C.admin()
    state = C.load_state(STATE)
    for point in (PAY, CO):
        release_id = state["candidate"][point]["releaseId"]
        run = shadow(ops, release_id)
        state["candidate"][point]["shadowRun"] = run
        print(f"{point} 影子运行 {json.dumps(run, ensure_ascii=False)}")
        reply = send(ops, "POST", f"{EXECUTIONS}/{release_id}/activate")
        state["candidate"][point]["status"] = reply.get("status")
        state["candidate"][point]["activatedAt"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        print(f"{point} release {release_id} → {reply.get('status')}")
    C.save_state(STATE, state)


def rollback():
    ops = C.admin()
    state = C.load_state(STATE)
    restored = {}
    for point, base in state["baseline"].items():
        release_id = validated_release(ops, point, base["flowVersionId"])
        shadow(ops, release_id)
        reply = send(ops, "POST", f"{EXECUTIONS}/{release_id}/activate")
        restored[point] = {"releaseId": release_id, "flowVersionId": base["flowVersionId"], "status": reply.get("status")}
        print(f"{point} 回滚发布 {release_id}（流程版本 {base['flowVersionId']}）→ {reply.get('status')}")
    state["rollback"] = restored
    C.save_state(STATE, state)


if __name__ == "__main__":
    {"build": build, "activate": activate, "rollback": rollback}[sys.argv[1]]()
