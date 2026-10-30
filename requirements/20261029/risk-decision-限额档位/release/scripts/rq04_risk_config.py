#!/usr/bin/env python3
"""REQ-04 · mor-test 风控配置（runbook ⑩ 建发布、⑬ 激活；按 paytech/risk-decision/docs/limit-tier-configuration.md）。
经运营入口（http://localhost:28080 → Gateway → ops-bff → risk-decision），与阶段 5 本机 verification/stage5/stage5_risk_setup.py 同形，
数值与现网流程按 mor-test 改写。

子命令：
  plan      只读：核对两个风控点当前 ACTIVE 发布仍是回滚基线（第 1 节），变量 / 拒绝原因编码未占用，打印将创建的对象清单与额度表。
  build     写（--yes）：建拒绝原因 LIMIT_TIER_DENIED、8 个档位名单变量、2 个 DAILY_SUM_AMOUNT 变量、36 个额度常量、2 个币种集合、
            7 个冻结规则集、2 个流程，并为两个风控点各建执行发布、校验到 SHADOWING。**不激活。**按编码复用已有对象，可重跑。
  activate  写（--yes，迁移核对 0 缺口之后）：每个发布先跑影子（激活的技术门槛；结果不用于核对迁移，见配置说明第 5 节）再激活。
  rollback  写（--yes）：以回滚基线流程版本新建执行发布 → 校验 → 影子 → 激活（已作废的发布不能重新激活）。
状态（发布 ID 等，非机密）写 ~/Code-MoR-backups/rq04-stage7/risk-config.json；结果写 acceptance/result-risk-config-<子命令>-<UTC>.json。
额度为**建议默认值，须风控确认**（stage6-report Q-2）；激活后改常量即时生效（变量页面或 PUT /decision-variables/v1/variables/{code}），无需新发布。
用法：E2E_TARGET=test python3 -I rq04_risk_config.py <plan|build|activate|rollback> [--yes]
"""
import argparse
import json
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "acceptance"))
import rq04_release_common as C  # noqa: E402

OA = C.OA
TIMEOUT_CODES = {504}


def write(ops, method, path, body=None, found=None, ok=(200, 201), tries=20):
    """写请求。504（ops-bff 等待超时，Risk 可能已提交）不盲目重发：调用 found() 按业务编码只读复核，已生效则视为成功；
    仍未生效再重发一次（重发前同样复核）。found 为空时 504 直接失败。"""
    status, reply = ops.call(method, path, body, json_empty=body is None and method != "GET")
    if status in ok:
        return reply
    if status in TIMEOUT_CODES and found is not None:
        for _ in range(tries):
            time.sleep(3)
            result = found()
            if result is not None:
                print(f"504 后复核已生效：{method} {path}")
                return result
        status, reply = ops.call(method, path, body, json_empty=body is None and method != "GET")
        if status in ok:
            return reply
    raise SystemExit(f"FAIL {method} {path} HTTP {status} {OA.problem(reply)}（重跑 build 会按编码复用已建对象）")


def db(statement):
    return C.root_ro("mor_risk_decision", statement)
PAY, CO = "PAYMENT_ATTEMPT_RISK", "CHECKOUT_PREPARE"
STATE = "risk-config.json"
STAMP = time.strftime("%m%d%H%M")
TIERS = ("VERIFICATION", "OBSERVATION", "STANDARD", "RESTRICTED")
# 档位 → 币种 → (单笔, UTC 当日, 近 30 个 UTC 自然日)。建议默认值：AED / SAR 按 USD 联系汇率取整，EGP 按约 50 取整；须风控确认。
QUOTAS = {
    "VERIFICATION": {"USD": ("500", "2000", "10000"), "AED": ("1800", "7300", "36700"), "SAR": ("1875", "7500", "37500"),
                     "EGP": ("25000", "100000", "500000")},
    "OBSERVATION": {"USD": ("2000", "10000", "50000"), "AED": ("7300", "36700", "183500"), "SAR": ("7500", "37500", "187500"),
                    "EGP": ("100000", "500000", "2500000")},
    "STANDARD": {"USD": ("10000", "100000", "1000000"), "AED": ("36700", "367000", "3670000"), "SAR": ("37500", "375000", "3750000"),
                 "EGP": ("500000", "5000000", "50000000")},
}
CURRENCY_SETS = {"VERIFICATION": ("USD", "AED", "SAR", "EGP"), "OBSERVATION": ("USD", "AED", "SAR", "EGP")}  # STANDARD 不建：未配置币种放行
REASON = "LIMIT_TIER_DENIED"
DAY, MONTH = "PAY_CLIENT_ALLOWED_DAY", "PAY_CLIENT_ALLOWED_30D"
# mor-test 回滚基线（阶段 6 只读读取，2026-10-30）：支付尝试 PAY_ATTEMPT_FLOW v1、结账 CHECKOUT_BASELINE_ALLOW v1
BASELINE = {PAY: {"releaseId": "236518950932942848", "flowVersionId": "236518950786142209", "flowCode": "PAY_ATTEMPT_FLOW"},
            CO: {"releaseId": "237117104682274816", "flowVersionId": "236943227222069249", "flowCode": "CHECKOUT_BASELINE_ALLOW"}}
# 现网支付流程的冻结规则集（档位层之后原样引用）
PAY_BLOCK, PAY_AUTH = "236518950081499136", "236518950366711808"


def operand(role, source, value_type, literal=None, variable=None, unit=None, currency_variable=None):
    return {"operandRole": role, "valueSequence": 1, "sourceType": source, "fieldCode": None, "valueType": value_type,
            "literalValue": literal, "variableCode": variable, "numericUnit": unit, "currencyCode": None,
            "currencyVariableCode": currency_variable}


def variable(code, name, source, value_type, point, **extra):
    """ops-bff 的变量写入要求 record 全部键（不适用的显式 null）。"""
    body = {"variableCode": code, "variableName": name, "description": "REQ-04 限额档位", "sourceType": source, "valueType": value_type,
            "pointCode": point, "sourceContractCode": None, "sourceContractVersion": None, "sourceField": None,
            "usages": ["RULE_CONDITION", "FLOW_CONDITION"], "formatCode": "NONE", "numericUnit": None, "currencyCode": None,
            "currencyVariableCode": None, "allowedValuesVariableCode": None, "currentValue": None, "status": "ACTIVE",
            "velocity": None, "list": None}
    body.update(extra)
    return body


def variables():
    out = []
    for tier in TIERS:
        for prefix, point in (("PAY", PAY), ("CO", CO)):
            out.append((variable(f"{prefix}_TIER_IN_{tier}", f"{'支付' if point == PAY else '结账'}档位 · {tier}", "LIST", "BOOLEAN", point,
                                 list={"listCode": f"TIER_{tier}", "subjectType": "CLIENT"}), None))
    for code, window, name in ((DAY, 86400, "Client UTC 当日放行合计"), (MONTH, 2592000, "Client 近 30 个 UTC 自然日放行合计")):
        out.append((variable(code, name, "VELOCITY", "NUMBER", PAY, numericUnit="MONEY", currencyVariableCode="PAY_INPUT_CURRENCY",
                             velocity={"eventCode": "PAYMENT_ATTEMPT_ALLOWED", "anchorSubjectType": "CLIENT", "metric": "DAILY_SUM_AMOUNT",
                                       "distinctSubjectType": None, "windowSeconds": window, "includeCurrent": True}), None))
    for tier, currencies in QUOTAS.items():
        for currency, values in currencies.items():
            for period, value in zip(("SINGLE", "DAY", "MONTH"), values):
                out.append((variable(f"TIER_{tier}_{currency}_{period}", f"{tier} {currency} {period} 上限", "CONSTANT", "NUMBER", PAY,
                                     numericUnit="MONEY", currencyVariableCode="PAY_INPUT_CURRENCY", currentValue=value), None))
    for tier, currencies in CURRENCY_SETS.items():
        out.append((variable(f"TIER_{tier}_CURRENCIES", f"{tier} 允许币种", "COLLECTION", "STRING", None, formatCode="UPPER_ALPHA_3"),
                    currencies))
    return out


def exists(ops, path):
    status, _ = ops.get(path)
    return status == 200


def ensure_variables(ops):
    for body, members in variables():
        code = body["variableCode"]
        if exists(ops, f"/decision-variables/v1/variables/{code}"):
            if members and not db(f"SELECT 1 x FROM t_risk_variable_member WHERE variable_code = '{code}' LIMIT 1"):
                write(ops, "PUT", f"/decision-variables/v1/variables/{code}/members",
                      {"expectedVersion": "1", "members": [{"value": m, "displayName": m} for m in members]})
            print(f"exists   {code}")
            continue
        write(ops, "POST", "/decision-variables/v1/variables", body,
              found=lambda: {} if exists(ops, f"/decision-variables/v1/variables/{code}") else None)
        if members:
            write(ops, "PUT", f"/decision-variables/v1/variables/{code}/members",
                  {"expectedVersion": "1", "members": [{"value": m, "displayName": m} for m in members]},
                  found=lambda: {} if db(f"SELECT 1 x FROM t_risk_variable_member WHERE variable_code = '{code}' LIMIT 1") else None)
        print(f"created  {code}")


def ensure_reason(ops):
    if not exists(ops, f"/decision-dictionaries/v1/rejection-reasons/{REASON}"):
        write(ops, "POST", "/decision-dictionaries/v1/rejection-reasons", found=lambda: {} if db(
            f"SELECT 1 x FROM t_rule_reject_reason WHERE reason_code = '{REASON}'") else None, body=
               {"reasonCode": REASON, "name": "超出档位额度", "description": "REQ-04 限额档位：无档位、受限档位、币种或额度超出",
                "defaultMessage": "超出档位额度"})


# ---------------------------------------------------------------------------------------------------------------- 规则集与流程

def predicate(key, parent, seq, operator, left, right):
    return {"nodeKey": key, "parentNodeKey": parent, "sequenceNo": seq, "nodeType": "PREDICATE", "groupOperator": None,
            "comparisonOperator": operator, "operands": [left, right]}


def group(key="root"):
    return {"nodeKey": key, "parentNodeKey": None, "sequenceNo": 1, "nodeType": "GROUP", "groupOperator": "AND",
            "comparisonOperator": None, "operands": []}


def deny_rule(code, priority, action, nodes):
    return {"ruleCode": code, "ruleName": code, "priorityNo": priority, "enabledFlag": True, "reasonCode": REASON,
            "conditionNodes": nodes, "action": {"actionCode": action, "actionVersionNo": 1}, "reasonMessage": "超出档位额度",
            "additionalActions": []}


def rule_set_row(code):
    rows = db(f"SELECT rule_set_version_id, status, content_hash FROM t_rule_set_version WHERE rule_set_code = '{code}' "
              "ORDER BY version_no DESC LIMIT 1")
    return rows[0] if rows else None


def frozen_rule_set(ops, point, code, rules):
    row = rule_set_row(code)
    if row is None:
        write(ops, "POST", "/decision-rule-sets/v1/rule-sets", found=lambda: rule_set_row(code), body={
        "pointCode": point, "ruleSetCode": code, "ruleSetName": code, "baseRuleSetVersionId": None, "baseContentHash": None,
        "changeReason": "REQ-04 限额档位 mor-test 发布", "rules": rules, "operandFormat": "VARIABLE_REFERENCE"})
        row = rule_set_row(code)
    version_id = row["rule_set_version_id"]
    if row["status"] != "FROZEN":
        write(ops, "POST", f"/decision-rule-sets/v1/rule-sets/{version_id}/freeze", {"expectedContentHash": row["content_hash"]},
              found=lambda: {} if (rule_set_row(code) or {}).get("status") == "FROZEN" else None)
    print(f"rule set {code} → {version_id}")
    return version_id


def currency_and(currency, left_code, constant):
    return [group(), predicate("currency", "root", 1, "EQ", operand("LEFT", "VARIABLE", "STRING", variable="PAY_INPUT_CURRENCY"),
                               operand("RIGHT", "LITERAL", "STRING", literal=currency)),
            predicate("cmp", "root", 2, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable=left_code),
                      operand("RIGHT", "VARIABLE", "NUMBER", variable=constant))]


def not_in(currency_variable, collection):
    return [predicate("root", None, 1, "NOT_IN", operand("LEFT", "VARIABLE", "STRING", variable=currency_variable),
                      operand("RIGHT", "VARIABLE", "STRING", variable=collection))]


def pay_limit_rule_set(ops, tier):
    rules = []
    if tier in CURRENCY_SETS:
        rules.append(("CURRENCY_NOT_IN_TIER", not_in("PAY_INPUT_CURRENCY", f"TIER_{tier}_CURRENCIES")))
    for currency in QUOTAS[tier]:
        rules.append((f"{currency}_SINGLE", currency_and(currency, "PAY_INPUT_AMOUNT", f"TIER_{tier}_{currency}_SINGLE")))
        rules.append((f"{currency}_DAY", currency_and(currency, DAY, f"TIER_{tier}_{currency}_DAY")))
        rules.append((f"{currency}_MONTH", currency_and(currency, MONTH, f"TIER_{tier}_{currency}_MONTH")))
    return frozen_rule_set(ops, PAY, f"TIER_LIMIT_{tier}",
                           [deny_rule(code, (i + 1) * 10, "DENY_PAYMENT", nodes) for i, (code, nodes) in enumerate(rules)])


def gate_rule_set(ops, point):
    if point == PAY:
        node = predicate("root", None, 1, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable="PAY_INPUT_AMOUNT"),
                         operand("RIGHT", "LITERAL", "NUMBER", literal="0", unit="MONEY", currency_variable="PAY_INPUT_CURRENCY"))
    else:
        node = predicate("root", None, 1, "GT", operand("LEFT", "VARIABLE", "NUMBER", variable="INPUT_LINE_COUNT"),
                         operand("RIGHT", "LITERAL", "NUMBER", literal="0", unit="SCALAR"))
    rule = {"ruleCode": "TIER_GATE", "ruleName": "档位分支", "priorityNo": 10, "enabledFlag": True, "reasonCode": None,
            "conditionNodes": [node], "action": None, "reasonMessage": None, "additionalActions": []}
    return frozen_rule_set(ops, point, f"TIER_GATE_{'PAY' if point == PAY else 'CO'}", [rule])


def checkout_currency_rule_set(ops, tier):
    return frozen_rule_set(ops, CO, f"TIER_CO_CURRENCY_{tier}", [
        deny_rule("CURRENCY_NOT_IN_TIER", 10, "DENY_CHECKOUT", not_in("INPUT_CURRENCY", f"TIER_{tier}_CURRENCIES"))])


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
    nodes = [node("start", "START", "开始", 0, 0), node("tier_gate", "RULE_SET", "档位分支", 120, 0, gate),
             node("deny_restricted", "END", "受限档位", 240, -360, decision="DENY", reason=REASON, message="受限档位"),
             node("deny_no_tier", "END", "无档位", 240, 360, decision="DENY", reason=REASON, message="未完成承保 / 无交易额度"),
             node("deny_limit", "END", "超出档位额度", 480, -240, decision="DENY", reason=REASON, message="超出档位额度")]
    edges = [edge("enter", "start", "tier_gate", "UNCONDITIONAL", 0, "进入"),
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
    """档位层之后接现网 PAY_ATTEMPT_FLOW v1 的全部节点与连线（拦截 → 拒绝 / 审核 / 认证 → 放行 / 3DS 放行），规则集版本原样引用。"""
    nodes, edges = tier_layer("PAY", gate, [(t, limits[t]) for t in ("VERIFICATION", "OBSERVATION", "STANDARD")], "block")
    nodes += [node("block", "RULE_SET", "拦截", 600, 0, PAY_BLOCK), node("auth", "RULE_SET", "认证", 720, 0, PAY_AUTH),
              node("deny", "END", "拒绝", 720, -240, decision="DENY", reason="PAY_RISK_DECLINED", message="支付被风控拒绝"),
              node("review", "END", "审核", 720, -120, decision="REVIEW"),
              node("allow", "END", "放行", 840, 0, decision="ALLOW", outputs={"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}),
              node("allow_3ds", "END", "放行（需要 3DS）", 840, 120, decision="ALLOW",
                   outputs={"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"})]
    edges += [edge("to_deny", "block", "deny", "CONDITIONAL", 1, "拒绝", condition("PAY_NODE_DECISION", "STRING", "DENY")),
              edge("to_review", "block", "review", "CONDITIONAL", 2, "审核", condition("PAY_NODE_DECISION", "STRING", "REVIEW")),
              edge("to_auth", "block", "auth", "ELSE", 0, "否则"),
              edge("to_3ds", "auth", "allow_3ds", "CONDITIONAL", 1, "需要认证", condition("PAY_NODE_MATCHED", "BOOLEAN", True)),
              edge("finish", "auth", "allow", "ELSE", 0, "完成")]
    return {"schemaVersion": 1, "nodes": nodes, "edges": edges}


def checkout_graph(gate, sets):
    nodes, edges = tier_layer("CO", gate, [("VERIFICATION", sets["VERIFICATION"]), ("OBSERVATION", sets["OBSERVATION"]),
                                          ("STANDARD", None)], "allow")
    nodes.append(node("allow", "END", "放行", 600, 0, decision="ALLOW"))
    return {"schemaVersion": 1, "nodes": nodes, "edges": edges}


def flow_row(code):
    rows = db(f"SELECT current_flow_version_id FROM t_decision_flow WHERE flow_code = '{code}'")
    return rows[0] if rows else None


def save_flow(ops, point, code, name, graph):
    if flow_row(code) is None:
        write(ops, "POST", "/decision-flows/v1/flows", found=lambda: flow_row(code), body={
            "pointCode": point, "flowCode": code, "flowName": name, "baseFlowVersionId": None, "expectedVersion": None,
            "changeReason": "REQ-04 限额档位：档位分支", "graph": graph})
    version_id = flow_row(code)["current_flow_version_id"]
    print(f"flow {code} → {version_id}")
    return version_id


def release_row(flow_version_id):
    rows = db(f"SELECT release_id, status FROM t_decision_release WHERE decision_flow_version_id = {flow_version_id} "
              "AND status IN ('DRAFT', 'VALIDATING', 'SHADOWING', 'ACTIVE') ORDER BY create_time DESC LIMIT 1")
    return rows[0] if rows else None


def validated_release(ops, point, flow_version_id):
    row = release_row(flow_version_id)
    if row is None:
        write(ops, "POST", C.EXECUTIONS, {"pointCode": point, "flowVersionId": flow_version_id}, found=lambda: release_row(flow_version_id))
        row = release_row(flow_version_id)
    release_id = row["release_id"]
    if row["status"] in ("SHADOWING", "ACTIVE"):
        return release_id
    validated = write(ops, "POST", f"{C.EXECUTIONS}/{release_id}/validate",
                      found=lambda: {"status": "SHADOWING"} if (release_row(flow_version_id) or {}).get("status") == "SHADOWING" else None)
    if validated.get("status") != "SHADOWING":
        raise SystemExit(f"校验未通过 {point}：{json.dumps(validated, ensure_ascii=False)[:800]}")
    return release_id


def shadow(ops, release_id, sample_target=200):
    def started():
        rows = db(f"SELECT status FROM t_release_shadow_run WHERE candidate_release_id = {release_id} ORDER BY create_time DESC LIMIT 1")
        return {} if rows else None
    write(ops, "POST", f"{C.EXECUTIONS}/{release_id}/shadow-runs", {"sampleTarget": sample_target}, found=started)
    for _ in range(90):
        run = (C.send(ops, "GET", f"{C.EXECUTIONS}/{release_id}") or {}).get("shadowRun") or {}
        if run.get("status") not in (None, "CREATED", "RUNNING", "PENDING"):
            return run
        time.sleep(2)
    raise SystemExit(f"影子运行未在 180 秒内完成：{release_id}")


def active_releases():
    rows = C.root_ro("mor_risk_decision", "SELECT p.point_code, r.release_id, r.decision_flow_version_id FROM t_decision_release r "
                     "JOIN t_decision_point p ON p.decision_point_id = r.decision_point_id WHERE r.status = 'ACTIVE'")
    return {row["point_code"]: {"releaseId": row["release_id"], "flowVersionId": row["decision_flow_version_id"]} for row in rows}


# ---------------------------------------------------------------------------------------------------------------- 命令

def plan():
    active = active_releases()
    checks = {"baseline": {p: active.get(p, {}).get("releaseId") == BASELINE[p]["releaseId"] for p in (PAY, CO)}}
    codes = [body["variableCode"] for body, _ in variables()]
    taken = C.root_ro("mor_risk_decision", "SELECT variable_code FROM t_risk_variable WHERE variable_code IN ("
                      + ",".join(f"'{c}'" for c in codes) + ")")
    checks["variablesTaken"] = [r["variable_code"] for r in taken]
    checks["reasonTaken"] = bool(C.root_ro("mor_risk_decision", f"SELECT 1 FROM t_rule_reject_reason WHERE reason_code = '{REASON}'"))
    checks["tierLists"] = C.root_ro("mor_risk_decision", "SELECT list_id, list_code, status FROM t_risk_list WHERE list_purpose = 'TIER'")
    checks["payRuleSets"] = C.root_ro("mor_risk_decision", "SELECT rule_set_version_id, status FROM t_rule_set_version WHERE rule_set_version_id IN "
                                      f"({PAY_BLOCK}, {PAY_AUTH})")
    checks["objects"] = {"variables": len(codes), "ruleSets": 1 + 3 + 1 + 2, "flows": 2, "releases": 2}
    checks["quotas"] = QUOTAS
    print(json.dumps(checks, ensure_ascii=False, indent=1))
    C.write_result("risk-config-plan", checks)
    return all(checks["baseline"].values()) and not checks["variablesTaken"] and not checks["reasonTaken"]


def build():
    ops = C.admin()
    state = C.load_state(STATE)
    active = active_releases()
    if any(active.get(p, {}).get("releaseId") != BASELINE[p]["releaseId"] for p in (PAY, CO)):
        raise SystemExit(f"当前 ACTIVE 发布与阶段 6 回滚基线不同，停止：{active}")
    ensure_reason(ops)
    ensure_variables(ops)
    if state.get("candidate"):
        print("发布已建：", json.dumps(state["candidate"], ensure_ascii=False))
        return True
    pay_gate, co_gate = gate_rule_set(ops, PAY), gate_rule_set(ops, CO)
    limits = {tier: pay_limit_rule_set(ops, tier) for tier in QUOTAS}
    sets = {tier: checkout_currency_rule_set(ops, tier) for tier in CURRENCY_SETS}
    pay_flow = save_flow(ops, PAY, "PAY_ATTEMPT_TIER_FLOW", "支付尝试风控流程（档位分支）", pay_graph(pay_gate, limits))
    co_flow = save_flow(ops, CO, "CHECKOUT_TIER_FLOW", "结账档位分支", checkout_graph(co_gate, sets))
    state.update(baseline=BASELINE, quotas=QUOTAS, currencySets=CURRENCY_SETS, builtAt=C.now().isoformat(), candidate={
        PAY: {"flowVersionId": pay_flow, "releaseId": validated_release(ops, PAY, pay_flow), "gateRuleSet": pay_gate, "limitRuleSets": limits},
        CO: {"flowVersionId": co_flow, "releaseId": validated_release(ops, CO, co_flow), "gateRuleSet": co_gate, "currencyRuleSets": sets}})
    C.save_state(STATE, state)
    C.write_result("risk-config-build", state)
    print(json.dumps(state["candidate"], ensure_ascii=False))
    return True


def activate():
    ops = C.admin()
    state = C.load_state(STATE)
    for point in (PAY, CO):
        release_id = state["candidate"][point]["releaseId"]
        run = shadow(ops, release_id)
        state["candidate"][point]["shadowRun"] = run
        print(f"{point} 影子 {json.dumps(run, ensure_ascii=False)}")
        if run.get("checkStatus") != "PASSED":
            raise SystemExit(f"{point} 影子技术门槛未通过，停止（不激活）")
        reply = write(ops, "POST", f"{C.EXECUTIONS}/{release_id}/activate", found=lambda: {"status": "ACTIVE"} if db(
            f"SELECT 1 x FROM t_decision_release WHERE release_id = {release_id} AND status = 'ACTIVE'") else None)
        state["candidate"][point].update(status=reply.get("status"), activatedAt=C.now().isoformat())
        print(f"{point} release {release_id} → {reply.get('status')}")
    C.save_state(STATE, state)
    C.write_result("risk-config-activate", state)
    return True


def rollback():
    ops = C.admin()
    state = C.load_state(STATE)
    restored = {}
    for point, base in BASELINE.items():
        release_id = validated_release(ops, point, base["flowVersionId"])
        shadow(ops, release_id)
        reply = write(ops, "POST", f"{C.EXECUTIONS}/{release_id}/activate", found=lambda: {"status": "ACTIVE"} if db(
            f"SELECT 1 x FROM t_decision_release WHERE release_id = {release_id} AND status = 'ACTIVE'") else None)
        restored[point] = {"releaseId": release_id, "flowVersionId": base["flowVersionId"], "status": reply.get("status")}
        print(f"{point} 回滚发布 {release_id}（流程版本 {base['flowVersionId']}）→ {reply.get('status')}")
    state["rollback"] = restored
    C.save_state(STATE, state)
    C.write_result("risk-config-rollback", state)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "build", "activate", "rollback"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    C.require_test()
    if args.command != "plan" and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test 风控配置，需 --yes（且须已获用户阶段 7 授权）")
    ok = {"plan": plan, "build": build, "activate": activate, "rollback": rollback}[args.command]()
    return 0 if ok else 1


sys.exit(main())
