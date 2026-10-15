"""阶段 5 接口验收：经本机 Nginx 运营入口（→ Gateway → BFF → Risk）配置一套支付规则并走完两次执行发布。

只连接本机。证据写入第一个参数指定的 Markdown 文件；不打印凭据、Token 与主体原值（证据中原值以 <RAW> 代替）。
评估属于 Payment 的职责，本脚本只直连本机 Risk 调用几次统一评估，作为“运营配置的发布确实生效”的佐证。
最后保留一个 SHADOWING 的回滚发布（以第 1 个发布的流程版本新建），供浏览器验收展示激活确认。
用法：python3 -I stage5_api.py <证据文件>
mor-test：E2E_TARGET=test RISK_EVALUATION_URL=http://127.0.0.1:28114 python3 -I stage5_api.py <证据文件>
"""
import json
import os
import site
import sys
import time
import urllib.request
import uuid
from pathlib import Path

sys.path.append(site.getusersitepackages())
HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
SFX = str(int(time.time()))[-6:]
PAYMENT = "PAYMENT_ATTEMPT_RISK"
# mor-test 的 Risk 只在后端网络可达，验收窗口内经临时代理（release.md 第 4 节）访问。
RISK = os.environ.get("RISK_EVALUATION_URL", "http://127.0.0.1:18104")
RAW_BLOCKED = f"fp_s5_block_{SFX}"
RAW_SECOND = f"fp_s5_other_{SFX}"
RAWS = [RAW_BLOCKED, RAW_SECOND]
lines, results = [f"# 阶段 5 接口验收证据（{time.strftime('%Y-%m-%d %H:%M:%S')}）", "", f"后缀 `{SFX}`；入口 `{e2e_http.OPERATIONS}`。", ""], []


def scrub(text):
    for raw in RAWS:
        text = text.replace(raw, "<RAW>")
    return text


def section(title):
    lines.extend(["", f"## {title}", ""])


def record(name, method, path, status, body):
    text = scrub(json.dumps(body, ensure_ascii=False) if not isinstance(body, str) else body)
    lines.append(f"- {name}：`{method} {scrub(path)}` → **{status}** `{text[:360]}{'…' if len(text) > 360 else ''}`")


def check(name, ok, detail=""):
    results.append((name, ok))
    lines.append(f"  - {'✅' if ok else '❌'} {name}{('：' + scrub(detail)) if detail else ''}")
    print(f"[{'通过' if ok else '失败'}] {name}")


class Console:
    def __init__(self):
        s = e2e_http.secrets()
        self.ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])

    def call(self, name, method, path, body=None, expect=200):
        try:
            data = self.ops.get(path) if method == "GET" else self.ops.send(method, path, body)
            status = 200
        except e2e_http.Failure as failure:
            status, data = failure.status, json.loads(failure.body) if failure.body.startswith("{") else failure.body
        else:
            # e2e_http 只在失败时给出状态码；成功时按预期的 2xx 记录（例如变量创建为 201）。
            status = expect if 200 <= expect < 300 else status
        record(name, method, path, status, data)
        if status != expect:
            check(f"{name} 状态 {expect}", False, f"实际 {status}")
        return status, data


def evaluate(name, amount, fingerprint):
    """直连本机 Risk 统一评估（Payment 的调用方式），只作发布生效佐证。"""
    inputs = {"paymentIntentId": rid(), "riskActionId": rid(), "orderId": rid(), "clientId": "800001", "entityNo": "AE001",
              "paymentMethodCode": "CARD_VISA", "captureMode": "AUTOMATIC", "amount": amount, "currency": "USD",
              "industryCode": "DIGITAL_GOODS", "mccCode": "5816", "consumerCountryCode": "AE", "consumerReference": "s5-consumer",
              "clientIpAddress": "203.0.113.10", "userAgent": "Mozilla/5.0 s5", "cardFingerprint": fingerprint, "cardBin": "411111"}
    body = {"pointCode": PAYMENT, "contractVersion": 1, "businessKey": rid(), "inputs": inputs}
    request = urllib.request.Request(RISK + "/risk-decisions/v1/evaluations", json.dumps(body).encode(), method="POST",
                                     headers={"Content-Type": "application/json", "Accept": "application/json",
                                              "X-Request-Id": "s5-eval-" + str(uuid.uuid4())})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = json.loads(response.read())
    record(f"评估（直连 Risk）{name}", "POST", "/risk-decisions/v1/evaluations", 200, data)
    return data


def rid():
    return str(int(time.time() * 1000) * 1000 + uuid.uuid4().int % 1000)


def var(code, value_type):
    return {"operandRole": "LEFT", "valueSequence": 1, "sourceType": "VARIABLE", "fieldCode": None, "valueType": value_type,
            "literalValue": None, "variableCode": code, "numericUnit": None, "currencyCode": None, "currencyVariableCode": None}


def lit(value_type, value, unit=None, currency_variable=None):
    return {"operandRole": "RIGHT", "valueSequence": 1, "sourceType": "LITERAL", "fieldCode": None, "valueType": value_type,
            "literalValue": value, "variableCode": None, "numericUnit": unit, "currencyCode": None, "currencyVariableCode": currency_variable}


def rule(code, priority, action, operator, left, right, reason=None):
    deny = action.startswith("DENY_")
    return {"ruleCode": code, "ruleName": code, "priorityNo": priority, "enabledFlag": True, "reasonCode": reason if deny else None,
            "conditionNodes": [{"nodeKey": "root", "parentNodeKey": None, "sequenceNo": 1, "nodeType": "PREDICATE", "groupOperator": None,
                                "comparisonOperator": operator, "operands": [left, right]}],
            "action": {"actionCode": action, "actionVersionNo": 1}, "reasonMessage": "支付被风控拒绝" if deny else None, "additionalActions": []}


def rule_set(code, rules):
    return {"pointCode": PAYMENT, "ruleSetCode": code, "ruleSetName": f"验收 {code}", "baseRuleSetVersionId": None,
            "baseContentHash": None, "changeReason": "阶段 5 验收", "rules": rules, "operandFormat": "VARIABLE_REFERENCE"}


def node_condition(variable, value_type, value):
    return {"schemaVersion": 2, "root": {"kind": "PREDICATE", "operator": "EQ", "operands": [
        {**var(variable, value_type)}, {**lit(value_type, value)}]}}


def graph(block, auth, reason):
    def node(key, kind, name, x, y, rule_set_id=None, decision=None, reason_code=None, message=None, outputs=None):
        n = {"nodeKey": key, "type": kind, "name": name, "position": {"x": str(x), "y": str(y)}, "ruleSetVersionId": rule_set_id,
             "decisionCode": decision, "reasonCode": reason_code, "reasonMessage": message}
        if outputs:
            n["outputs"] = outputs
        return n

    def edge(key, source, target, kind, priority, name, condition=None):
        return {"edgeKey": key, "sourceNodeKey": source, "targetNodeKey": target, "type": kind, "priority": priority, "name": name,
                "condition": condition}
    return {"schemaVersion": 1, "nodes": [
        node("start", "START", "开始", 0, 0), node("block", "RULE_SET", "拦截", 200, 0, block), node("auth", "RULE_SET", "认证", 400, 0, auth),
        node("deny", "END", "拒绝", 400, 160, decision="DENY", reason_code=reason, message="支付被风控拒绝"),
        node("review", "END", "审核", 400, -160, decision="REVIEW"), node("allow", "END", "放行", 600, 0, decision="ALLOW"),
        node("allow_3ds", "END", "放行（需要 3DS）", 600, 160, decision="ALLOW", outputs={"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"})],
        "edges": [edge("enter", "start", "block", "UNCONDITIONAL", 0, "进入"),
                  edge("to_deny", "block", "deny", "CONDITIONAL", 1, "拒绝", node_condition("PAY_NODE_DECISION", "STRING", "DENY")),
                  edge("to_review", "block", "review", "CONDITIONAL", 2, "审核", node_condition("PAY_NODE_DECISION", "STRING", "REVIEW")),
                  edge("to_auth", "block", "auth", "ELSE", 0, "否则"),
                  edge("to_3ds", "auth", "allow_3ds", "CONDITIONAL", 1, "需认证", node_condition("PAY_NODE_MATCHED", "BOOLEAN", True)),
                  edge("finish", "auth", "allow", "ELSE", 0, "否则")]}


def frozen(c, name, body):
    _, created = c.call(f"保存规则集 {name}", "POST", "/decision-rule-sets/v1/rule-sets", body)
    _, done = c.call(f"冻结规则集 {name}", "POST", f"/decision-rule-sets/v1/rule-sets/{created['ruleSetVersionId']}/freeze",
                     {"expectedContentHash": created["contentHash"]})
    check(f"规则集 {name} 冻结", done.get("status") == "FROZEN")
    return created["ruleSetVersionId"], created


def release(c, label, flow_version_id):
    _, created = c.call(f"创建执行发布（{label}）", "POST", "/decision-releases/v1/executions",
                        {"pointCode": PAYMENT, "flowVersionId": flow_version_id})
    check(f"{label} 创建后为 DRAFT", created.get("status") == "DRAFT")
    _, validated = c.call(f"静态校验（{label}）", "POST", f"/decision-releases/v1/executions/{created['releaseId']}/validate")
    checks = validated.get("checks") or []
    check(f"{label} 校验后为 SHADOWING 且检查全部通过", validated.get("status") == "SHADOWING"
          and checks and all(x["checkStatus"] == "PASSED" for x in checks), f"{len(checks)} 项检查")
    return created["releaseId"]


def main():
    c = Console()
    lists(c)
    reason = dictionary(c)
    velocity, listed = variables(c)
    first, auth = first_release(c, reason, velocity, listed)
    evaluations_first()
    second_release(c, reason, velocity, listed, first, auth)
    rollback_and_boundaries(c, first)
    lines.extend(["", "## 结论", "", f"{sum(ok for _, ok in results)}/{len(results)} 项检查通过。"])
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{sum(ok for _, ok in results)}/{len(results)} 通过，证据 {OUT}")
    sys.exit(0 if all(ok for _, ok in results) else 1)


STATE = {}


def lists(c):
    section("1. 风控名单")
    code = f"S5_FP_{SFX}"
    STATE["list"] = code
    c.call("名单目录", "GET", "/risk-lists/v1/lists?page=1&size=20")
    _, created = c.call("新建卡指纹黑名单", "POST", "/risk-lists/v1/lists",
                        {"listCode": code, "listName": "验收卡指纹黑名单", "subjectType": "CARD_FINGERPRINT", "purpose": "BLOCK"})
    check("名单创建后有效", created.get("active") is True and created.get("listCode") == code)
    status, dup = c.call("重复编码新建", "POST", "/risk-lists/v1/lists",
                         {"listCode": code, "listName": "重复", "subjectType": "CARD_FINGERPRINT", "purpose": "BLOCK"}, expect=400)
    check("重复编码被 Risk 拒绝并透传错误码", isinstance(dup, dict) and bool(dup.get("code")), str(dup.get("code") if isinstance(dup, dict) else dup))
    _, entry = c.call("加入成员", "POST", f"/risk-lists/v1/lists/{code}/entries", {"subjectValue": RAW_BLOCKED, "effectiveFrom": None, "effectiveTo": None, "reason": "验收加入"})
    text = json.dumps(entry, ensure_ascii=False)
    check("成员响应只含脱敏主体键、不含原值", RAW_BLOCKED not in text and "*" in str(entry.get("maskedSubjectKey")),
          str(entry.get("maskedSubjectKey")))
    _, other = c.call("加入第二个成员", "POST", f"/risk-lists/v1/lists/{code}/entries", {"subjectValue": RAW_SECOND, "effectiveFrom": None, "effectiveTo": None, "reason": "验收加入"})
    _, found = c.call("按原值查询成员", "GET", f"/risk-lists/v1/lists/{code}/entries?subjectValue={RAW_BLOCKED}")
    check("按原值查询只返回该成员", [x["entryId"] for x in found.get("items", [])] == [entry["entryId"]])
    _, gone = c.call("失效第二个成员", "POST", f"/risk-lists/v1/lists/{code}/entries/{other['entryId']}/deactivate", {"reason": "验收失效"})
    check("失效后状态非 ACTIVE 且记录失效原因", gone.get("status") != "ACTIVE" and gone.get("deactivatedReason") == "验收失效",
          str(gone.get("status")))
    _, active = c.call("按状态查询有效成员", "GET", f"/risk-lists/v1/lists/{code}/entries?status=ACTIVE")
    check("有效成员只剩 1 个", active.get("total") == 1)
    c.call("名单目录带未知查询参数", "GET", "/risk-lists/v1/lists?keyword=x", expect=400)
    c.call("加入成员缺少原因", "POST", f"/risk-lists/v1/lists/{code}/entries", {"subjectValue": "fp_s5_missing", "effectiveFrom": None, "effectiveTo": None}, expect=400)


def dictionary(c):
    section("2. 拒绝原因")
    code = f"S5_REASON_{SFX}"
    _, created = c.call("新建拒绝原因", "POST", "/decision-dictionaries/v1/rejection-reasons",
                        {"reasonCode": code, "name": "验收拒绝", "description": None, "defaultMessage": "支付被风控拒绝"})
    check("拒绝原因生效", created.get("status") == "ACTIVE")
    return code


def variables(c):
    section("3. 实时累计与名单命中变量")
    base = {"description": None, "valueType": "NUMBER", "pointCode": PAYMENT, "sourceContractCode": None, "sourceContractVersion": None,
            "sourceField": None, "usages": ["RULE_CONDITION", "FLOW_CONDITION"], "formatCode": "NONE", "numericUnit": "SCALAR",
            "currencyCode": None, "currencyVariableCode": None, "allowedValuesVariableCode": None, "currentValue": None, "status": "ACTIVE"}
    velocity = f"S5_FP_COUNT_1H_{SFX}"
    _, v = c.call("新建实时累计变量（同卡 1 小时评估次数，不含本次）", "POST", "/decision-variables/v1/variables",
                  {**base, "variableCode": velocity, "variableName": "同卡 1 小时评估次数", "sourceType": "VELOCITY",
                   "velocity": {"eventCode": "PAYMENT_ATTEMPT_ASSESSED", "anchorSubjectType": "CARD_FINGERPRINT", "metric": "COUNT",
                                "distinctSubjectType": None, "windowSeconds": 3600, "includeCurrent": False}, "list": None}, expect=201)
    check("实时累计变量创建", v.get("sourceType") == "VELOCITY")
    listed = f"S5_FP_BLOCKED_{SFX}"
    _, l = c.call("新建名单命中变量", "POST", "/decision-variables/v1/variables",
                  {**base, "variableCode": listed, "variableName": "卡指纹在验收黑名单", "sourceType": "LIST", "valueType": "BOOLEAN",
                   "numericUnit": None, "velocity": None, "list": {"listCode": STATE["list"], "subjectType": "CARD_FINGERPRINT"}}, expect=201)
    check("名单命中变量创建", l.get("sourceType") == "LIST" and l.get("valueType") == "BOOLEAN")
    _, detail = c.call("实时累计变量详情", "GET", f"/decision-variables/v1/variables/{velocity}")
    check("变量详情不回显累计参数（R-Q3 不做）", "velocity" not in detail and detail.get("sourceType") == "VELOCITY")
    c.call("实时累计窗口为字符串", "POST", "/decision-variables/v1/variables",
           {**base, "variableCode": f"S5_BAD_{SFX}", "variableName": "非法", "sourceType": "VELOCITY", "list": None,
            "velocity": {"eventCode": "PAYMENT_ATTEMPT_ASSESSED", "anchorSubjectType": "CARD_FINGERPRINT", "metric": "COUNT",
                         "distinctSubjectType": None, "windowSeconds": "3600", "includeCurrent": False}}, expect=400)
    return velocity, listed


def first_release(c, reason, velocity, listed):
    section("4. 规则集、决策流与首个执行发布")
    _, rc = c.call("支付点规则集编辑目录", "GET", f"/decision-rule-sets/v1/authoring-context/{PAYMENT}")
    check("规则集目录为统一变量格式并含支付终态动作", rc.get("operandFormat") == "VARIABLE_REFERENCE"
          and {"ALLOW_PAYMENT", "REVIEW_PAYMENT", "DENY_PAYMENT"} <= {a["actionCode"] for a in rc.get("actions", [])})
    _, fc = c.call("支付点决策流上下文", "GET", f"/decision-flows/v1/authoring-context?pointCode={PAYMENT}")
    outputs = fc.get("outputs") or []
    check("决策流上下文声明认证要求输出", outputs and outputs[0]["outputCode"] == "AUTHENTICATION_REQUIREMENT"
          and outputs[0]["allowedEndDecisions"] == ["ALLOW"])
    block, block_created = frozen(c, "拦截", rule_set(f"S5_BLOCK_{SFX}", [
        rule("BLOCKED_CARD", 10, "DENY_PAYMENT", "EQ", var(listed, "BOOLEAN"), lit("BOOLEAN", True), reason),
        rule("CARD_FREQUENCY", 20, "DENY_PAYMENT", "GTE", var(velocity, "NUMBER"), lit("NUMBER", "3", "SCALAR"), reason),
        rule("LARGE_AMOUNT", 30, "REVIEW_PAYMENT", "GT", var("PAY_INPUT_AMOUNT", "NUMBER"), lit("NUMBER", "5000", "MONEY", "PAY_INPUT_CURRENCY"))]))
    STATE["block_code"] = block_created["ruleSetCode"]
    auth, _ = frozen(c, "认证（金额 > 1000 需要 3DS）", rule_set(f"S5_AUTH_{SFX}", [
        rule("THREE_DS_AMOUNT", 10, "ALLOW_PAYMENT", "GT", var("PAY_INPUT_AMOUNT", "NUMBER"), lit("NUMBER", "1000", "MONEY", "PAY_INPUT_CURRENCY"))]))
    flow = {"pointCode": PAYMENT, "flowCode": f"S5_FLOW_{SFX}", "flowName": "验收支付流程", "baseFlowVersionId": None,
            "expectedVersion": None, "changeReason": "阶段 5 验收", "graph": graph(block, auth, reason)}
    c.call("静态校验决策流", "POST", "/decision-flows/v1/flows/validate", flow)
    _, saved = c.call("保存决策流", "POST", "/decision-flows/v1/flows", flow)
    STATE["flow"] = saved
    _, detail = c.call("决策流详情", "GET", f"/decision-flows/v1/flows/{saved['flowId']}")
    nodes = {n["nodeKey"]: n for n in detail.get("graph", {}).get("nodes", [])}
    check("“放行（需要 3DS）”结束节点保存了认证要求", nodes.get("allow_3ds", {}).get("outputs") == {"AUTHENTICATION_REQUIREMENT": "THREE_DS_REQUIRED"})
    check("普通放行结束节点由 Risk 补齐默认值", nodes.get("allow", {}).get("outputs") in (None, {"AUTHENTICATION_REQUIREMENT": "NOT_REQUIRED"}),
          json.dumps(nodes.get("allow", {}).get("outputs")))
    _, none_active = c.call("当前生效发布（尚无）", "GET", f"/decision-releases/v1/executions?pointCode={PAYMENT}&status=ACTIVE")
    check("首发前没有生效发布", none_active.get("total") == 0)
    first = release(c, "发布 1", saved["flowVersionId"])
    _, activated = c.call("首发激活", "POST", f"/decision-releases/v1/executions/{first}/activate")
    check("发布 1 激活后为 ACTIVE", activated.get("status") == "ACTIVE")
    _, active = c.call("当前生效发布", "GET", f"/decision-releases/v1/executions?pointCode={PAYMENT}&status=ACTIVE")
    check("列表中唯一生效的是发布 1", [x["releaseId"] for x in active.get("items", [])] == [first])
    STATE["first_flow_version"] = saved["flowVersionId"]
    return first, auth


def evaluations_first():
    section("5. 发布 1 生效佐证（直连 Risk 统一评估）")
    for name, amount, fp, result, auth in [("小额", "100.00", f"fp_s5_a_{SFX}", "ALLOW", "NOT_REQUIRED"),
                                           ("1500", "1500.00", f"fp_s5_b_{SFX}", "ALLOW", "THREE_DS_REQUIRED"),
                                           ("6000", "6000.00", f"fp_s5_c_{SFX}", "REVIEW", None),
                                           ("黑名单卡", "100.00", RAW_BLOCKED, "DENY", None)]:
        data = evaluate(name, amount, fp)
        check(f"{name} → {result}/{auth}", data.get("result") == result and data.get("outputs", {}).get("AUTHENTICATION_REQUIREMENT") == auth)
    fp = f"fp_s5_freq_{SFX}"
    for i in range(1, 5):
        data = evaluate(f"同卡第 {i} 次", "100.00", fp)
        check(f"同卡第 {i} 次 → {'DENY' if i == 4 else 'ALLOW'}", data.get("result") == ("DENY" if i == 4 else "ALLOW"))


def second_release(c, reason, velocity, listed, first, auth):
    section("6. 发布 2：影子运行后替代")
    auth2, _ = frozen(c, "认证 v2（金额 > 50 需要 3DS）", rule_set(f"S5_AUTH2_{SFX}", [
        rule("THREE_DS_AMOUNT", 10, "ALLOW_PAYMENT", "GT", var("PAY_INPUT_AMOUNT", "NUMBER"), lit("NUMBER", "50", "MONEY", "PAY_INPUT_CURRENCY"))]))
    flow = STATE["flow"]
    _, detail = c.call("读取当前流程版本", "GET", f"/decision-flows/v1/flows/{flow['flowId']}")
    g = detail["graph"]
    for n in g["nodes"]:
        if n["nodeKey"] == "auth":
            n["ruleSetVersionId"] = auth2
    body = {"pointCode": PAYMENT, "flowCode": detail["flowCode"], "flowName": detail["flowName"], "baseFlowVersionId": detail["flowVersionId"],
            "expectedVersion": detail["version"], "changeReason": "3DS 门槛降为 50", "graph": g}
    _, saved = c.call("保存流程新版本", "POST", "/decision-flows/v1/flows", body)
    check("流程新版本号为 2", saved.get("versionNo") == 2)
    second = release(c, "发布 2", saved["flowVersionId"])
    _, started = c.call("启动影子运行（样本 20）", "POST", f"/decision-releases/v1/executions/{second}/shadow-runs", {"sampleTarget": 20})
    shadow = started.get("shadowRun") or {}
    for _ in range(60):
        if shadow.get("status") not in ("PENDING", "RUNNING", None):
            break
        time.sleep(2)
        _, polled = c.call("轮询发布详情", "GET", f"/decision-releases/v1/executions/{second}")
        shadow = polled.get("shadowRun") or {}
    lines.append(f"  - 影子运行结果 `{json.dumps(shadow, ensure_ascii=False)}`")
    check("影子运行完成且无失败/超时", shadow.get("status") == "COMPLETED" and shadow.get("failedCount") == 0 and shadow.get("timedOutCount") == 0)
    check("影子运行统计到认证要求变化", (shadow.get("actionChangeCount") or 0) > 0, f"actionChangeCount={shadow.get('actionChangeCount')}")
    _, activated = c.call("发布 2 激活", "POST", f"/decision-releases/v1/executions/{second}/activate")
    check("发布 2 激活后为 ACTIVE", activated.get("status") == "ACTIVE")
    _, old = c.call("发布 1 详情", "GET", f"/decision-releases/v1/executions/{first}")
    check("发布 1 被替代", old.get("status") == "SUPERSEDED")
    data = evaluate("发布 2 生效后 100", "100.00", f"fp_s5_d_{SFX}")
    check("100 → ALLOW/THREE_DS_REQUIRED（新门槛生效）", data.get("result") == "ALLOW"
          and data.get("outputs", {}).get("AUTHENTICATION_REQUIREMENT") == "THREE_DS_REQUIRED")
    STATE["second"] = second


def rollback_and_boundaries(c, first):
    section("7. 回滚准备与边界")
    rollback = release(c, "回滚发布（以发布 1 的流程版本新建）", STATE["first_flow_version"])
    STATE["rollback"] = rollback
    _, page = c.call("执行发布列表", "GET", f"/decision-releases/v1/executions?pointCode={PAYMENT}&page=1&size=10")
    statuses = {x["releaseId"]: x["status"] for x in page.get("items", [])}
    check("列表含 ACTIVE / SUPERSEDED / SHADOWING 三个发布",
          statuses.get(STATE["second"]) == "ACTIVE" and statuses.get(first) == "SUPERSEDED" and statuses.get(rollback) == "SHADOWING")
    c.call("执行发布列表带未知查询参数", "GET", f"/decision-releases/v1/executions?pointCode={PAYMENT}&keyword=x", expect=400)
    _, problem = c.call("已替代的发布不能再激活", "POST", f"/decision-releases/v1/executions/{first}/activate", expect=400)
    check("Risk 拒绝激活已替代的发布", isinstance(problem, dict) and problem.get("code") == "BUSINESS_CHECK_FAILED")
    c.call("影子样本数超上限", "POST", f"/decision-releases/v1/executions/{rollback}/shadow-runs", {"sampleTarget": 2001}, expect=422)
    _, rule_sets = c.call("拦截规则集列表", "GET", f"/decision-rule-sets/v1/rule-sets?pointCode={PAYMENT}&ruleSetCode={STATE['block_code']}")
    items = rule_sets.get("items") or []
    if items:
        rule_set_id = items[0].get("ruleSetId")
        check("规则集列表标记为正在使用", items[0].get("inUse") is True)
        status, problem = c.call("删除被生效发布引用的规则集", "DELETE", f"/decision-rule-sets/v1/rule-sets/{rule_set_id}",
                                 {"expectedVersion": items[0].get("managementVersion")}, expect=409)
        check("返回 RISK_RULE_SET_IN_USE", isinstance(problem, dict) and problem.get("code") == "RISK_RULE_SET_IN_USE")
    lines.append(f"\n保留：回滚发布 `{rollback}`（SHADOWING）供浏览器验收；生效发布 `{STATE['second']}`。")
    Path(str(OUT) + ".state.json").write_text(json.dumps(STATE, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
