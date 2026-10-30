"""联合查询阶段 5：本机运营入口（Nginx 18444 → Gateway → ops-bff → Platform）的演示数据准备与 API 联调。

只调用运营 API（真实令牌）；数据库只读核对经 q.sh（会话只读）。凭据读 local-environment/e2e/secrets.env 与 .env.local，不打印；
新建的受限运营账号口令只写会话暂存区（0600）。可重跑：数据源 / 键类型 / 模板按编码复用。
用法：python3 -I stage5_e2e.py <scratch_dir> [--with-slow]
  --prepare：只做数据源、键类型 K1—K5 与演示模板（只用已启用的路由），在模板往返之前停止；
  --with-slow：额外演示失败隔离（临时启用 JQ_SLOW_ORDER，执行后恢复 INACTIVE）；默认不运行，正常链路保持干净。
结果写 stage5-e2e-result.json。
"""
import json
import os
import secrets as ps
import site
import sys
import time
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

HERE = Path(__file__).resolve().parent
SCRATCH = Path(sys.argv[1])
WITH_SLOW = "--with-slow" in sys.argv
PREPARE = "--prepare" in sys.argv
ENV_LOCAL = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local")
ADMIN = "/data-query-admin/v1"
JOINT = "/data-query/v1/joint-query"
REFUND_NO = "rfd_A00000000000000000000000007001"
s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
results = []
demo = {"templates": {}, "keyTypes": {}}


def record(step, ok, detail=None):
    results.append({"step": step, "ok": bool(ok), "detail": detail})
    print(("PASS " if ok else "FAIL ") + step, json.dumps(detail, ensure_ascii=False)[:260] if detail else "", flush=True)


def failure_of(fn):
    """调用并返回 (status, body)；成功为 (200, reply)。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {"raw": failure.body[:200]}


def expect(step, status, fn, code=None, field=None):
    actual, body = failure_of(fn)
    fields = [error.get("field") for error in (body.get("errors") or [])] if isinstance(body, dict) else []
    ok = actual == status and (code is None or body.get("code") == code) and (field is None or field in fields)
    record(step, ok, {"status": actual, "code": body.get("code") if isinstance(body, dict) else None, "fields": fields,
                      "detail": body.get("detail") if isinstance(body, dict) else None})
    return body


def env_value(key):
    for line in ENV_LOCAL.read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("missing " + key)


# ---------- 1 数据源 ----------
def data_source(code):
    listed = [row for row in ops.get(f"{ADMIN}/data-sources?keyword={code}")["items"] if row["dataSourceCode"] == code]
    if not listed:
        ops.send("POST", f"{ADMIN}/data-sources", {
            "dataSourceCode": code, "dataSourceName": "本机被查库 fixture（联合查询演示）", "engineType": "MYSQL",
            "host": "127.0.0.1", "port": 13306, "databaseName": "mor_data_query_fixture",
            "username": "data_query_fixture_reader", "password": env_value("MOR_DATA_QUERY_FIXTURE_READER_PASSWORD"),
            "maxPoolSize": 4, "description": "联合查询阶段 5 本机联调（t_jq_* 链路表）"})
        listed = [row for row in ops.get(f"{ADMIN}/data-sources?keyword={code}")["items"] if row["dataSourceCode"] == code]
    source = listed[0]
    if source["status"] != "ACTIVE":
        ops.send("POST", f"{ADMIN}/data-sources/{source['dataSourceId']}/status",
                 {"status": "ACTIVE", "expectedVersion": source["version"]})
    record(f"数据源 {code} ACTIVE", True, {"dataSourceId": source["dataSourceId"]})
    return source["dataSourceId"]


source_id = data_source("FIXTURE_JQ")
demo["dataSource"] = {"code": "FIXTURE_JQ", "id": source_id}


# ---------- 2 键类型 K1—K5 ----------
def key_types(query=""):
    return ops.get(f"{ADMIN}/key-types?size=200{query}")["items"]


def key_type(code):
    return [item for item in key_types() if item["keyTypeCode"] == code][0]


seeded = key_types()
record("K1 列表含首批 26 个键类型且按 sortNo 升序", len([k for k in seeded if k["keyTypeId"].startswith("900000000000144")]) == 26
       and [k["sortNo"] for k in seeded] == sorted(k["sortNo"] for k in seeded), {"total": len(seeded)})
record("K1 按状态 / 关键字筛选", all(k["status"] == "ACTIVE" for k in key_types("&status=ACTIVE"))
       and [k["keyTypeCode"] for k in key_types("&keyword=REFUND_REQUEST")] == ["REFUND_REQUEST_ID", "REFUND_REQUEST_NO"])
DEMO_KEY = "JQ_E2E_SEQ"
if not [k for k in seeded if k["keyTypeCode"] == DEMO_KEY]:
    created = ops.send("POST", f"{ADMIN}/key-types", {
        "keyTypeCode": DEMO_KEY, "keyTypeName": "演示序号", "groupName": "计费", "valueType": "INTEGER",
        "defaultRole": "LINK", "entryAllowed": False, "valuePattern": "[0-9]{1,6}", "inputHint": "1—6 位数字",
        "sortNo": 105, "description": "联合查询阶段 5 演示键类型"})
    record("K2 新建键类型", created["objectType"] == "QUERY_KEY_TYPE" and created["version"] == 1, created)
demo_key = key_type(DEMO_KEY)
demo["keyTypes"][DEMO_KEY] = demo_key["keyTypeId"]
expect("K2 编码重复 400", 400, lambda: ops.send("POST", f"{ADMIN}/key-types", {
    "keyTypeCode": DEMO_KEY, "keyTypeName": "x", "groupName": "计费", "valueType": "INTEGER", "defaultRole": "LINK",
    "entryAllowed": False, "sortNo": 1}), "BUSINESS_CHECK_FAILED")
expect("K2 正则含前瞻断言 422 valuePattern", 422, lambda: ops.send("POST", f"{ADMIN}/key-types", {
    "keyTypeCode": "JQ_E2E_BAD", "keyTypeName": "x", "groupName": "计费", "valueType": "STRING", "defaultRole": "LINK",
    "entryAllowed": True, "valuePattern": "a(?=b)", "sortNo": 1}), "VALIDATION_ERROR", "valuePattern")


def update_body(item, **changes):
    body = {key: item[key] for key in ("keyTypeName", "groupName", "valueType", "defaultRole", "entryAllowed", "valuePattern",
                                        "inputHint", "sortNo", "description")}
    body.update(changes)
    body["expectedVersion"] = changes.get("expectedVersion", item["version"])
    return body


updated = ops.send("PUT", f"{ADMIN}/key-types/{demo_key['keyTypeId']}", update_body(demo_key, keyTypeName="演示序号（联调）"))
record("K3 修改加版本", updated["version"] == demo_key["version"] + 1, updated)
expect("K3 版本过期 409", 409, lambda: ops.send("PUT", f"{ADMIN}/key-types/{demo_key['keyTypeId']}",
                                              update_body(demo_key, expectedVersion=demo_key["version"])), "CONCURRENT_REQUEST")
expect("K3 不存在 404", 404, lambda: ops.send("PUT", f"{ADMIN}/key-types/999999999",
                                           update_body(demo_key, expectedVersion=1)), "NOT_FOUND")
demo_key = key_type(DEMO_KEY)
off = ops.send("POST", f"{ADMIN}/key-types/{demo_key['keyTypeId']}/status", {"status": "INACTIVE", "expectedVersion": demo_key["version"]})
same = ops.send("POST", f"{ADMIN}/key-types/{demo_key['keyTypeId']}/status", {"status": "INACTIVE", "expectedVersion": off["version"]})
on = ops.send("POST", f"{ADMIN}/key-types/{demo_key['keyTypeId']}/status", {"status": "ACTIVE", "expectedVersion": off["version"]})
record("K4 停用 / 同状态不加版本 / 启用", off["status"] == "INACTIVE" and same["version"] == off["version"]
       and on["status"] == "ACTIVE" and on["version"] == off["version"] + 1, {"off": off, "same": same, "on": on})
current = key_types()
order = [{"keyTypeId": item["keyTypeId"], "expectedVersion": item["version"]} for item in current]
expect("K5 未覆盖全部键类型 400", 400, lambda: ops.send("PUT", f"{ADMIN}/key-type-order", {"items": order[1:]}),
       "BUSINESS_CHECK_FAILED")
stale = [dict(order[0], expectedVersion=order[0]["expectedVersion"] + 9)] + order[1:]
expect("K5 任一版本不符 409", 409, lambda: ops.send("PUT", f"{ADMIN}/key-type-order", {"items": stale}), "CONCURRENT_REQUEST")
reordered = ops.send("PUT", f"{ADMIN}/key-type-order", {"items": order})
record("K5 按原顺序整体重写 sortNo = 序号 × 10", [item["sortNo"] for item in reordered["items"]]
       == [10 * (index + 1) for index in range(len(order))], {"count": len(reordered["items"])})


# ---------- 3 模板（联合查询配置） ----------
def joint(subject, key_name, key_type_code, *columns):
    return {"subjectKeyType": subject, "keyParameter": {"name": key_name, "keyType": key_type_code},
            "columns": [{"name": n, "keyType": k, "keyRole": r} for n, k, r in (c.split(":") for c in columns)]}


def lst(name, element):
    return [{"name": name, "type": "LIST", "elementType": element, "required": True}]


def scalar(name, kind):
    return [{"name": name, "type": kind, "required": True}]


NONE = {"maxRows": 100, "timeoutSeconds": 5, "pagingMode": "NONE", "countAllowed": False}


def find_template(code):
    found = [row for row in ops.get(f"{ADMIN}/templates?keyword={code}&size=50")["items"] if row["templateCode"] == code]
    return found[0] if found else None


def template(code, name, body, parameters, constraints=NONE, columns=None, joint_config=None, active=True):
    row = find_template(code)
    if row is None:
        saved = ops.send("POST", f"{ADMIN}/templates", {
            "templateCode": code, "templateName": name, "description": "联合查询阶段 5 演示", "dataSourceId": source_id,
            "body": body, "parameters": parameters, "columns": columns or [], "constraints": constraints,
            "joint": joint_config, "changeNote": "联合查询阶段 5"})
        participation = [w for w in saved["warnings"] if w.get("field") == "joint"]
        record(f"模板 {code} 保存", True, {"warnings": [f"{w['level']}:{w['message']}" for w in participation]})
        row = find_template(code)
    if row["status"] != ("ACTIVE" if active else "INACTIVE"):
        ops.send("POST", f"{ADMIN}/templates/{row['templateId']}/status",
                 {"status": "ACTIVE" if active else "INACTIVE", "expectedVersion": row["version"]})
        row = find_template(code)
    demo["templates"][code] = row["templateId"]
    return row


def ids(column, key):
    return f"{{{{#if {key}}}}}WHERE {column} IN (#{{{key}}}){{{{/if}}}}"


template("JQ_REFUND_BY_NO", "退款申请 · 按退款单号", "SELECT refund_request_id, refund_request_no, order_id, refund_amount FROM t_jq_refund "
         + ids("refund_request_no", "refundNos") + " ORDER BY refund_request_id", lst("refundNos", "STRING"),
         joint_config=joint("REFUND_REQUEST_ID", "refundNos", "REFUND_REQUEST_NO", "refund_request_id:REFUND_REQUEST_ID:LINK",
                            "order_id:ORDER_ID:LINK"))
template("JQ_REFUND_BY_ORDER", "退款申请 · 按订单", "SELECT refund_request_id, refund_request_no, order_id, refund_amount FROM t_jq_refund "
         + ids("order_id", "orderIds") + " ORDER BY refund_request_id", lst("orderIds", "INTEGER"),
         joint_config=joint("REFUND_REQUEST_ID", "orderIds", "ORDER_ID", "refund_request_id:REFUND_REQUEST_ID:LINK",
                            "order_id:ORDER_ID:LINK"))
template("JQ_ORDER_BY_ID", "交易订单 · 按订单 ID", "SELECT order_id, order_no, client_order_id, client_id, checkout_session_id, buyer_email"
         " FROM t_jq_order " + ids("order_id", "orderIds") + " ORDER BY order_id", lst("orderIds", "INTEGER"),
         {"maxRows": 100, "timeoutSeconds": 5, "pagingMode": "OFFSET", "maxPageSize": 50, "countAllowed": False},
         [{"name": "buyer_email", "label": "买家邮箱", "mask": "KEEP_EDGES", "keepStart": 2, "keepEnd": 5}],
         joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK", "checkout_session_id:CHECKOUT_SESSION_ID:LINK",
               "client_id:CLIENT_ID:DIMENSION", "client_order_id:CLIENT_ORDER_ID:DIMENSION"))
template("JQ_SESSION_BY_ID", "收银台会话 · 按会话 ID", "SELECT checkout_session_id, order_id, session_status FROM t_jq_checkout_session"
         " WHERE checkout_session_id = #{sessionId}", scalar("sessionId", "INTEGER"),
         joint_config=joint("CHECKOUT_SESSION_ID", "sessionId", "CHECKOUT_SESSION_ID",
                            "checkout_session_id:CHECKOUT_SESSION_ID:LINK", "order_id:ORDER_ID:LINK"))
template("JQ_INTENT_BY_ORDER", "支付意图 · 按订单（source_id VARCHAR）", "SELECT payment_intent_id, source_type, source_id, intent_status"
         " FROM t_jq_payment_intent WHERE source_type = 'COMMERCE_ORDER'"
         " {{#if orderIds}}AND source_id IN (#{orderIds}){{/if}} ORDER BY payment_intent_id", lst("orderIds", "STRING"),
         joint_config=joint("PAYMENT_INTENT_ID", "orderIds", "ORDER_ID", "payment_intent_id:PAYMENT_INTENT_ID:LINK",
                            "source_id:ORDER_ID:LINK"))
template("JQ_INTENT_BY_ID", "支付意图 · 按意图 ID", "SELECT payment_intent_id, source_type, source_id, intent_status FROM t_jq_payment_intent "
         + ids("payment_intent_id", "intentIds") + " ORDER BY payment_intent_id", lst("intentIds", "INTEGER"),
         joint_config=joint("PAYMENT_INTENT_ID", "intentIds", "PAYMENT_INTENT_ID", "payment_intent_id:PAYMENT_INTENT_ID:LINK",
                            "source_id:ORDER_ID:LINK"))
template("JQ_ATTEMPT_BY_INTENT", "支付尝试 · 按意图（游标分页，每页 1 行）", "SELECT payment_attempt_id, payment_intent_id, attempt_status"
         " FROM t_jq_payment_attempt {{#join \" AND \" prefix=\"WHERE \"}}{{#if intentIds}}payment_intent_id IN (#{intentIds}){{/if}}"
         "{{#if __cursor}}payment_attempt_id > #{__cursor.payment_attempt_id}{{/if}}{{/join}} ORDER BY payment_attempt_id",
         lst("intentIds", "INTEGER"),
         {"maxRows": 100, "timeoutSeconds": 5, "pagingMode": "CURSOR", "maxPageSize": 1, "countAllowed": False,
          "cursorColumns": ["payment_attempt_id"]},
         joint_config=joint("PAYMENT_ATTEMPT_ID", "intentIds", "PAYMENT_INTENT_ID",
                            "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK", "payment_intent_id:PAYMENT_INTENT_ID:LINK"))
template("JQ_ATTEMPT_BY_ID", "支付尝试 · 按尝试 ID", "SELECT payment_attempt_id, payment_intent_id, attempt_status FROM t_jq_payment_attempt "
         + ids("payment_attempt_id", "attemptIds") + " ORDER BY payment_attempt_id", lst("attemptIds", "INTEGER"),
         joint_config=joint("PAYMENT_ATTEMPT_ID", "attemptIds", "PAYMENT_ATTEMPT_ID",
                            "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK", "payment_intent_id:PAYMENT_INTENT_ID:LINK"))
template("JQ_ACTION_BY_ATTEMPT", "支付动作 · 按尝试（偏移分页，每页 1 行）", "SELECT payment_action_id, payment_attempt_id, action_type,"
         " external_transaction_id FROM t_jq_payment_action " + ids("payment_attempt_id", "attemptIds")
         + " ORDER BY payment_action_id", lst("attemptIds", "INTEGER"),
         {"maxRows": 100, "timeoutSeconds": 5, "pagingMode": "OFFSET", "maxPageSize": 1, "countAllowed": True},
         joint_config=joint("PAYMENT_ACTION_ID", "attemptIds", "PAYMENT_ATTEMPT_ID", "payment_action_id:PAYMENT_ACTION_ID:LINK",
                            "payment_attempt_id:PAYMENT_ATTEMPT_ID:LINK",
                            "external_transaction_id:EXTERNAL_TRANSACTION_ID:LINK"))
template("JQ_BILLING_BY_ORDER", "计费事实 · 按订单（order_ref VARCHAR）", "SELECT billing_fact_id, order_ref, fact_amount FROM t_jq_billing_fact "
         + ids("order_ref", "orderRefs") + " ORDER BY billing_fact_id", lst("orderRefs", "STRING"),
         joint_config=joint("BILLING_FACT_ID", "orderRefs", "ORDER_ID", "billing_fact_id:BILLING_FACT_ID:LINK",
                            "order_ref:ORDER_ID:LINK"))
template("JQ_ORDER_BY_CLIENT_ORDER_ID", "交易订单 · 按客户订单号", "SELECT order_id, client_order_id, client_id FROM t_jq_order "
         + ids("client_order_id", "clientOrderIds") + " ORDER BY order_id", lst("clientOrderIds", "STRING"),
         joint_config=joint("ORDER_ID", "clientOrderIds", "CLIENT_ORDER_ID", "order_id:ORDER_ID:LINK",
                            "client_id:CLIENT_ID:DIMENSION"))
template("JQ_ORDER_BY_CLIENT", "交易订单 · 按客户", "SELECT order_id, client_order_id, client_id FROM t_jq_order"
         " WHERE client_id = #{clientId} ORDER BY order_id", scalar("clientId", "INTEGER"),
         joint_config=joint("ORDER_ID", "clientId", "CLIENT_ID", "order_id:ORDER_ID:LINK", "client_id:CLIENT_ID:DIMENSION"))
SLOW_BODY = ("SELECT o.order_id, (SELECT SUM(a.n + b.n + c.n) FROM t_number_fixture a CROSS JOIN t_number_fixture b"
             " CROSS JOIN t_number_fixture c) AS total FROM t_jq_order o " + ids("o.order_id", "orderIds"))
template("JQ_SLOW_ORDER", "慢查询演示（默认停用，2 秒超时）", SLOW_BODY, lst("orderIds", "INTEGER"),
         {"maxRows": 10, "timeoutSeconds": 2, "pagingMode": "NONE", "countAllowed": False},
         joint_config=joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK"), active=False)
record("演示模板 13 个就绪", len(demo["templates"]) == 13, demo["templates"])


def finish():
    passed = sum(1 for r in results if r["ok"])
    name = "stage5-e2e-prepare-result.json" if PREPARE else "stage5-e2e-result.json"
    (HERE / name).write_text(json.dumps({"passed": passed, "total": len(results), "demo": demo, "results": results},
                                        ensure_ascii=False, indent=2))
    print(f"\n{passed} / {len(results)} passed")


if PREPARE:
    finish()
    sys.exit(0)

# 保存校验、回显、去掉配置、恢复版本（单独的模板，完成后停用，不进入链路）
ROUND = "JQ_E2E_ROUNDTRIP"
round_joint = joint("ORDER_ID", "orderIds", "ORDER_ID", "order_id:ORDER_ID:LINK", "client_id:CLIENT_ID:LINK")
round_row = find_template(ROUND)
if round_row is None:
    saved = ops.send("POST", f"{ADMIN}/templates", {
        "templateCode": ROUND, "templateName": "联合查询配置往返", "dataSourceId": source_id,
        "body": "SELECT order_id, client_id FROM t_jq_order " + ids("order_id", "orderIds"),
        "parameters": lst("orderIds", "INTEGER"), "columns": [], "constraints": NONE, "joint": round_joint})
    levels = [(w["level"], w["field"]) for w in saved["warnings"]]
    record("保存：维度键标为链路提醒 + 参与提示 INFO", ("WARNING", "joint.columns[1].keyRole") in levels and ("INFO", "joint") in levels,
           saved["warnings"])
    round_row = find_template(ROUND)
    tid = round_row["templateId"]
    detail = ops.get(f"{ADMIN}/templates/{tid}")
    record("详情回显 joint 与 jointQuery", detail["joint"] == detail["currentVersion"]["joint"]
           and detail["joint"]["keyParameter"]["name"] == "orderIds" and detail["jointQuery"]["participating"], detail["jointQuery"])
    cleared = ops.send("PUT", f"{ADMIN}/templates/{tid}", {
        "templateName": "联合查询配置往返", "dataSourceId": source_id, "body": detail["currentVersion"]["body"],
        "parameters": detail["currentVersion"]["parameters"], "columns": [], "constraints": NONE, "joint": None,
        "changeNote": "去掉联合查询配置", "expectedVersion": detail["version"]})
    v2 = ops.get(f"{ADMIN}/templates/{tid}/versions/2")
    record("不传 joint 保存为不参与：v2 joint / jointQuery 为空", v2["joint"] is None and v2["jointQuery"] is None
           and not cleared["warnings"], {"v2": {"joint": v2["joint"], "jointQuery": v2["jointQuery"]}})
    restored = ops.send("POST", f"{ADMIN}/templates/{tid}/restoration", {"versionNo": 1, "expectedVersion": cleared["version"]})
    v3 = ops.get(f"{ADMIN}/templates/{tid}/versions/3")
    record("恢复版本 1：配置随版本恢复", v3["joint"] == detail["joint"] and v3["jointQuery"]["participating"], {"version": restored["version"]})
    ops.send("POST", f"{ADMIN}/templates/{tid}/status", {"status": "INACTIVE", "expectedVersion": restored["version"]})
else:
    tid = round_row["templateId"]
    v1, v2, v3 = (ops.get(f"{ADMIN}/templates/{tid}/versions/{n}") for n in (1, 2, 3))
    record("往返结果（已存在，读回核对）：v1 有配置、v2 不参与、v3 由 v1 恢复且配置相同、模板已停用",
           v1["joint"] is not None and v2["joint"] is None and v2["jointQuery"] is None and v3["joint"] == v1["joint"]
           and v3["restoredFromVersionNo"] == 1 and round_row["status"] == "INACTIVE"
           and v3["jointQuery"]["reasonCode"] == "TEMPLATE_INACTIVE", {"v3": v3["jointQuery"]})
demo["templates"][ROUND] = find_template(ROUND)["templateId"]
expect("保存：键参数未声明 422 joint.keyParameter.name", 422, lambda: ops.send("POST", f"{ADMIN}/templates", {
    "templateCode": "JQ_E2E_INVALID", "templateName": "x", "dataSourceId": source_id,
    "body": "SELECT order_id FROM t_jq_order " + ids("order_id", "orderIds"), "parameters": lst("orderIds", "INTEGER"),
    "columns": [], "constraints": NONE, "joint": joint("ORDER_ID", "missing", "ORDER_ID")}), "VALIDATION_ERROR",
    "joint.keyParameter.name")
validation = ops.send("POST", f"{ADMIN}/template-validations", {
    "dataSourceId": source_id, "body": "SELECT order_id FROM t_jq_order " + ids("order_id", "orderIds"),
    "parameters": lst("orderIds", "INTEGER"), "columns": [], "constraints": NONE,
    "joint": joint("NO_SUCH_KEY", "orderIds", "ORDER_ID")}, key=False)
record("编辑器校验返回 joint.subjectKeyType 错误", not validation["valid"]
       and any(p["field"] == "joint.subjectKeyType" for p in validation["problems"]), validation["problems"])
order_key = key_type("ORDER_ID")
expect("K4 被 ACTIVE 模板引用时停用 400（列模板编码）", 400, lambda: ops.send(
    "POST", f"{ADMIN}/key-types/{order_key['keyTypeId']}/status", {"status": "INACTIVE", "expectedVersion": order_key["version"]}),
    "BUSINESS_CHECK_FAILED")
record("K1 引用数", key_type("ORDER_ID")["referencedTemplateCount"] >= 6, {"ORDER_ID": key_type("ORDER_ID")["referencedTemplateCount"]})


# ---------- 4 J1 / J2 ----------
settings = ops.get(f"{JOINT}/settings")
groups = [g["groupName"] for g in settings["groups"]]
record("J1 入口键类型、只含泳道的分组与护栏", "REFUND_REQUEST_NO" in [e["keyTypeCode"] for e in settings["entryKeyTypes"]]
       and "归属维度" not in groups and groups[:2] == ["收单", "支付"] and settings["limits"]["maxDepth"] == 6,
       {"groups": settings["groups"], "limits": settings["limits"]})
graph = ops.get(f"{JOINT}/graph?entryKeyType=REFUND_REQUEST_NO")
steps = {t["templateCode"]: t["minSteps"] for g in graph["groups"] for t in g["templates"]}
record("J2 可达预览：最少步数与断点", steps.get("JQ_REFUND_BY_NO") == 0 and steps.get("JQ_ORDER_BY_ID") == 1
       and steps.get("JQ_ACTION_BY_ATTEMPT") == 3 and "JQ_SLOW_ORDER" not in steps
       and {"REFUND_REQUEST_ID", "BILLING_FACT_ID"} <= {b["keyType"] for b in graph["breaks"]}
       and any(e["templateCode"] == "JQ_SLOW_ORDER" and e["reasonCode"] == "TEMPLATE_INACTIVE" for e in graph["excluded"]),
       {"minSteps": steps, "breaks": [b["keyType"] for b in graph["breaks"]]})
expect("J2 不可作入口 400", 400, lambda: ops.get(f"{JOINT}/graph?entryKeyType=CHANNEL_INTERACTION_ID"), "BUSINESS_CHECK_FAILED")
# ops-bff 的 DataQueryJointController 自己声明 entryKeyType 必填（含格式），缺失时在 BFF 返回 400 INVALID_REQUEST，不到 Platform。
expect("J2 缺少 entryKeyType 400（BFF 参数校验）", 400, lambda: ops.get(f"{JOINT}/graph"), "INVALID_REQUEST")
expect("J2 未知键类型 400", 400, lambda: ops.get(f"{JOINT}/graph?entryKeyType=NO_SUCH_KEY"), "BUSINESS_CHECK_FAILED")


# ---------- 5 J3 ----------
def step(depth, keys, template_codes=None):
    body = {"depth": depth, "keys": [{"keyType": k, "values": v} for k, v in keys]}
    if template_codes:
        body["templateCodes"] = template_codes
    return ops.send("POST", f"{JOINT}/steps", body, key=False)


def run_chain(key_type_code, *values):
    visited = {key_type_code: set(values)}
    keys, layers = [(key_type_code, list(values))], []
    for depth in range(settings["limits"]["maxDepth"]):
        if not keys:
            break
        reply = step(depth, keys)
        layers.append(reply)
        keys = []
        for found in reply["discoveredKeys"]:
            seen = visited.setdefault(found["keyType"], set())
            fresh = [v for v in found["values"] if v not in seen]
            seen.update(fresh)
            if fresh:
                keys.append((found["keyType"], fresh))
    return layers


def nodes(layers):
    return [n for layer in layers for n in layer["nodes"]]


refund_chain = run_chain("REFUND_REQUEST_NO", REFUND_NO)
codes = [n["templateCode"] for n in nodes(refund_chain)]
CHAIN = {"JQ_REFUND_BY_NO", "JQ_REFUND_BY_ORDER", "JQ_ORDER_BY_ID", "JQ_INTENT_BY_ORDER", "JQ_BILLING_BY_ORDER", "JQ_SESSION_BY_ID",
         "JQ_INTENT_BY_ID", "JQ_ATTEMPT_BY_INTENT", "JQ_ATTEMPT_BY_ID", "JQ_ACTION_BY_ATTEMPT"}
record("J3 退款单号入口逐层展开 10 个链路模板、各执行一次、全部成功", set(codes) == CHAIN and len(codes) == len(CHAIN)
       and all(n["status"] == "SUCCEEDED" for n in nodes(refund_chain)),
       {"layers": [[n["templateCode"] for n in layer["nodes"]] for layer in refund_chain]})
order_node = [n for n in nodes(refund_chain) if n["templateCode"] == "JQ_ORDER_BY_ID"][0]
record("J3 屏蔽列沿用模板屏蔽、维度列不进入发现键", order_node["rows"][0][5].startswith("bu") and "*" in order_node["rows"][0][5]
       and not any(k["keyType"] == "CLIENT_ID" for layer in refund_chain for k in layer["discoveredKeys"]),
       {"buyer_email": order_node["rows"][0][5]})
link = refund_chain[0]["links"][0]
record("J3 边可追溯到来源节点、行与列", link["nodeId"].startswith("0:JQ_REFUND_BY_NO:") and link["rowIndex"] == 0, link)
attempt_chain = run_chain("PAYMENT_ATTEMPT_ID", "9001")
lane = {n["templateCode"]: (n["groupName"], n["laneRank"], n["subjectSortNo"]) for n in nodes(refund_chain)}
upstream = {n["templateCode"]: (n["groupName"], n["laneRank"], n["subjectSortNo"]) for n in nodes(attempt_chain)}
record("J3 支付尝试入口查回订单与收银台，泳道位置相同", all(upstream.get(c) == lane[c] for c in ("JQ_ORDER_BY_ID", "JQ_SESSION_BY_ID")),
       {c: upstream.get(c) for c in ("JQ_ORDER_BY_ID", "JQ_SESSION_BY_ID")})
multi = step(0, [("CLIENT_ORDER_ID", ["CO-1"])])
record("J3 CLIENT_ORDER_ID 命中两个 Client 全部展开", [k["values"] for k in multi["discoveredKeys"] if k["keyType"] == "ORDER_ID"]
       == [["5001", "5002"]] and sorted({r[2] for n in multi["nodes"] for r in n["rows"]}) == ["3101", "3102"], multi["discoveredKeys"])
merged = step(1, [("ORDER_ID", ["5001", "05001", "5002"]), ("CHECKOUT_SESSION_ID", ["6001", "6002"])],
              ["JQ_ORDER_BY_ID", "JQ_SESSION_BY_ID", "NO_SUCH_TEMPLATE"])
record("J3 LIST 合并一次执行、标量逐值、templateCodes 只执行指定模板（重试）",
       [(n["templateCode"], n["triggerValues"]) for n in merged["nodes"]] == [
           ("JQ_ORDER_BY_ID", ["5001", "5002"]), ("JQ_SESSION_BY_ID", ["6001"]), ("JQ_SESSION_BY_ID", ["6002"])],
       [n["nodeId"] for n in merged["nodes"]])
expect("J3 正则不符 422 keys[0].values[0]", 422, lambda: step(0, [("REFUND_REQUEST_NO", ["rfd_short"])]), "VALIDATION_ERROR",
       "keys[0].values[0]")
expect("J3 超过 20 个入口值 422 keys[0].values", 422, lambda: step(0, [("ORDER_ID", [str(i) for i in range(1, 22)])]),
       "VALIDATION_ERROR", "keys[0].values")
expect("J3 不可作入口键类型 422 keys[0].keyType", 422, lambda: step(0, [("CHANNEL_INTERACTION_ID", ["1"])]), "VALIDATION_ERROR",
       "keys[0].keyType")
expect("J3 层号越界 422 depth", 422, lambda: step(6, [("ORDER_ID", ["5001"])]), "VALIDATION_ERROR", "depth")


# ---------- 6 加载更多（经单模板执行） ----------
paged = step(1, [("PAYMENT_INTENT_ID", ["8001"])], ["JQ_ATTEMPT_BY_INTENT", "JQ_INTENT_BY_ID"])
attempt_node = [n for n in paged["nodes"] if n["templateCode"] == "JQ_ATTEMPT_BY_INTENT"][0]
more = ops.send("POST", "/data-query/v1/templates/JQ_ATTEMPT_BY_INTENT/executions", {
    "params": {attempt_node["keyParameter"]: attempt_node["triggerValues"]}, "cursor": attempt_node["nextCursor"]})
record("游标节点截断并给出 nextCursor，经单模板执行取下一页", attempt_node["truncated"] and attempt_node["nextCursor"]
       and attempt_node["keyParameterList"] and more["rows"] == [["9002", "8001", "SUCCEEDED"]],
       {"first": attempt_node["rows"], "next": more["rows"]})
actions = step(1, [("PAYMENT_ATTEMPT_ID", ["9001", "9002"])], ["JQ_ACTION_BY_ATTEMPT"])["nodes"][0]
page2 = ops.send("POST", "/data-query/v1/templates/JQ_ACTION_BY_ATTEMPT/executions", {
    "params": {"attemptIds": actions["triggerValues"]}, "page": 2, "size": 1, "withTotal": True})
record("偏移节点截断（无 nextCursor），经单模板执行取第 2 页", actions["truncated"] and actions["nextCursor"] is None
       and page2["rows"][0][0] == "9102" and page2["total"] == 2, {"first": actions["rows"], "page2": page2["rows"]})


# ---------- 7 权限与调用方 ----------
creds_file = SCRATCH / "proc" / "jq-operators.json"
if not creds_file.exists():
    stamp = time.strftime("%H%M%S")
    role = ops.send("POST", "/iam/v1/roles", {"roleCode": f"JQ_READER_{stamp}", "roleName": f"联合查询阶段 5 只读 {stamp}",
                                             "permissionIds": ["900000000000131101"], "reason": "联合查询阶段 5 权限反例"})
    email = f"jq-reader-{stamp}@example.test"
    p1, p2 = "Jq!" + ps.token_urlsafe(18), "Jq!" + ps.token_urlsafe(18)
    account = ops.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1,
                                                   "roleIds": [role.get("roleId") or role.get("objectId")],
                                                   "reason": "联合查询阶段 5 权限反例"})
    e2e_http.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
    creds_file.write_text(json.dumps({"email": email, "password": p2, "accountId": account.get("accountId"),
                                      "roleId": role.get("roleId") or role.get("objectId")}))
    os.chmod(creds_file, 0o600)
reader_creds = json.loads(creds_file.read_text())
demo["readerAccountId"] = reader_creds["accountId"]
reader = e2e_http.Operations(reader_creds["email"], reader_creds["password"])
expect("只有 DATA_QUERY_READ：J1 403", 403, lambda: reader.get(f"{JOINT}/settings"))
expect("只有 DATA_QUERY_READ：J3 403", 403, lambda: reader.send("POST", f"{JOINT}/steps",
                                                             {"depth": 0, "keys": [{"keyType": "ORDER_ID", "values": ["5001"]}]}, key=False))
expect("只有 DATA_QUERY_READ：K2 403", 403, lambda: reader.send("POST", f"{ADMIN}/key-types", {
    "keyTypeCode": "JQ_E2E_READER", "keyTypeName": "x", "groupName": "计费", "valueType": "INTEGER", "defaultRole": "LINK",
    "entryAllowed": False, "sortNo": 1}))
listed = reader.get(f"{ADMIN}/key-types?size=10")
record("只有 DATA_QUERY_READ：K1 可读", listed["total"] >= 27, {"total": listed["total"]})

# 合并 ops-bff 仍保留税务接口（与税务会话共用本机栈）
tax_status, tax_body = failure_of(lambda: ops.get("/tax/v1/orders/ord_000000000000000000000000000000"))
record("合并 ops-bff：税务查询路由未出现 5xx（只作观察：状态与错误码见明细）", tax_status < 500,
       {"status": tax_status, "code": tax_body.get("code") if isinstance(tax_body, dict) else None})


# ---------- 8 失败隔离（可选） ----------
if WITH_SLOW:
    slow = find_template("JQ_SLOW_ORDER")
    ops.send("POST", f"{ADMIN}/templates/{slow['templateId']}/status", {"status": "ACTIVE", "expectedVersion": slow["version"]})
    try:
        reply = step(1, [("ORDER_ID", ["5001"])], ["JQ_SLOW_ORDER", "JQ_ORDER_BY_ID", "JQ_REFUND_BY_ORDER"])
        by_code = {n["templateCode"]: n for n in reply["nodes"]}
        record("失败隔离：慢查询 FAILED DATA_QUERY_TIMEOUT，其他节点成功",
               by_code["JQ_SLOW_ORDER"]["status"] == "FAILED" and by_code["JQ_SLOW_ORDER"]["errorCode"] == "DATA_QUERY_TIMEOUT"
               and by_code["JQ_ORDER_BY_ID"]["status"] == "SUCCEEDED", {c: n["status"] for c, n in by_code.items()})
    finally:
        slow = find_template("JQ_SLOW_ORDER")
        ops.send("POST", f"{ADMIN}/templates/{slow['templateId']}/status", {"status": "INACTIVE", "expectedVersion": slow["version"]})

finish()
