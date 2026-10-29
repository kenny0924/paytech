"""REQ-05 阶段 5：本机运营入口（Nginx 18444 → Gateway → ops-bff → Platform）的数据准备与 API 联调。

只调用运营 API（真实令牌）；数据库只读断言经 root 只读查询。凭据读 local-environment/e2e/secrets.env 与 .env.local，不打印；
新建的两个受限运营账号口令只写暂存区（0600）。可重跑：已登记的数据源 / 模板 / 定义按编码复用。
用法：python3 -I stage5_e2e.py <scratch_dir>；结果写 stage5-e2e-result.json。
"""
import json
import os
import re
import secrets as ps
import site
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

HERE = Path(__file__).resolve().parent
SCRATCH = Path(sys.argv[1])
TEMPLATES_SQL = Path("/Users/kenny/Desktop/Workspace/Code-MoR-wfdq/requirements/20261028/commercial-开通项前置门禁/db/data-query-templates.sql")
ENV_LOCAL = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local")
s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
results = []


def record(step, ok, detail):
    results.append({"step": step, "ok": ok, "detail": detail})
    print(("PASS " if ok else "FAIL ") + step, json.dumps(detail, ensure_ascii=False)[:300], flush=True)


def expect_status(step, status, fn):
    try:
        reply = fn()
        record(step, False, {"expectedStatus": status, "actual": 200, "body": reply})
    except e2e_http.Failure as failure:
        body = json.loads(failure.body) if failure.body.startswith("{") else failure.body
        record(step, failure.status == status, {"status": failure.status, "code": body.get("code") if isinstance(body, dict) else None})


def env_value(key):
    for line in ENV_LOCAL.read_text().splitlines():
        if line.startswith(key + "="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("missing " + key)


def platform_db(statement):
    """root 只读查询 mor_platform_v2e2e，返回行（制表符分隔）。"""
    out = subprocess.run([str(SCRATCH / "mysql-e2e.sh"), "mor_platform_v2e2e"], input="SET SESSION TRANSACTION READ ONLY;\n"
                         + statement, capture_output=True, text=True, check=True).stdout
    lines = [line for line in out.splitlines() if line]
    head = lines[0].split("\t") if lines else []
    return [dict(zip(head, line.split("\t"))) for line in lines[1:]]


# ---------- 1 数据源 ----------
def listed_source(code):
    return [row for row in ops.get("/data-query-admin/v1/data-sources?keyword=" + code)["items"]
            if row["dataSourceCode"] == code][0]


def data_source(code, name, database, username, password):
    listed = ops.get("/data-query-admin/v1/data-sources?keyword=" + code)
    found = [row for row in listed["items"] if row["dataSourceCode"] == code]
    if found:
        source = found[0]
    else:
        created = ops.send("POST", "/data-query-admin/v1/data-sources", {
            "dataSourceCode": code, "dataSourceName": name, "engineType": "MYSQL", "host": "127.0.0.1", "port": 13306,
            "databaseName": database, "username": username, "password": password, "maxPoolSize": 2,
            "description": "REQ-05 阶段 5 本机联调"})
        source = listed_source(code)
    if source["status"] != "ACTIVE":
        ops.send("POST", f"/data-query-admin/v1/data-sources/{source['dataSourceId']}/status",
                 {"status": "ACTIVE", "expectedVersion": source["version"]})
        source = listed_source(code)
    record(f"数据源 {code} ACTIVE", source["status"] == "ACTIVE", {"dataSourceId": source["dataSourceId"]})
    return source["dataSourceId"]


commercial_source = data_source("GOLIVE_COMMERCIAL_LOCAL", "本机 Commercial 只读（相当于 MOR_COMMERCIAL_REPLICA）",
                                "mor_commercial_v2e2e", "golive_e2e_reader",
                                (SCRATCH / "proc" / "golive_reader.pw").read_text().strip())
fixture_source = data_source("FIXTURE_REQ05", "本机被查库 fixture（REQ-05）", "mor_data_query_fixture",
                             "data_query_fixture_reader", env_value("MOR_DATA_QUERY_FIXTURE_READER_PASSWORD"))


# ---------- 2 查询模板 ----------
def golive_bodies():
    text = "\n".join(line for line in TEMPLATES_SQL.read_text().splitlines() if not line.lstrip().startswith("--"))
    return [statement.strip() for statement in text.split(";") if "SELECT" in statement]


def columns(*names):
    return [{"name": name, "mask": "NONE"} for name in names]


def query_template(code, name, source, body, parameters, cols, timeout):
    listed = ops.get("/data-query-admin/v1/templates?keyword=" + code)
    found = [row for row in listed["items"] if row["templateCode"] == code]
    if not found:
        ops.send("POST", "/data-query-admin/v1/templates", {
            "templateCode": code, "templateName": name, "description": "REQ-05 阶段 5 本机联调", "dataSourceId": source,
            "body": body, "parameters": parameters, "columns": cols,
            "constraints": {"maxRows": 1, "timeoutSeconds": timeout, "pagingMode": "NONE", "countAllowed": False},
            "changeNote": "REQ-05 阶段 5"})
    record(f"查询模板 {code}", True, {"created": not found})


drafted_sql, active_sql, collection_sql = golive_bodies()
query_template("GOLIVE_AGREEMENT_DRAFTED", "合同已起草且价格版本已批准", commercial_source, drafted_sql,
               [{"name": "clientId", "type": "INTEGER", "required": True}],
               columns("satisfied", "out_agreement_id", "out_business_mode", "out_entity_no", "out_capability_codes", "missing"), 5)
query_template("GOLIVE_AGREEMENT_ACTIVE", "合同已生效且价格版本生效", commercial_source, active_sql,
               [{"name": "agreementId", "type": "INTEGER", "required": True}], columns("satisfied", "missing"), 5)
query_template("GOLIVE_COLLECTION_CAPABILITIES_ENABLED", "收款开通项全部已开通", commercial_source, collection_sql,
               [{"name": "agreementId", "type": "INTEGER", "required": True}],
               columns("satisfied", "out_pending_capabilities", "missing"), 5)
query_template("E2E_REQ05_SLOW", "REQ-05 慢查询（1 秒超时）", fixture_source,
               "SELECT 1 AS satisfied, SUM(a.n + b.n + c.n) AS total FROM t_number_fixture a CROSS JOIN t_number_fixture b"
               " CROSS JOIN t_number_fixture c", [], None, 1)

# ---------- 3 模板选择与配置校验 ----------
picker = ops.get("/workflow-admin/v1/data-query-templates?keyword=GOLIVE&page=1&size=20")
item = {row["templateCode"]: row for row in picker["items"]}.get("GOLIVE_AGREEMENT_DRAFTED", {})
record("模板选择：GOLIVE_* 带 versionNo / timeoutSeconds / 参数", picker["total"] == 3 and item.get("versionNo") == 1
       and item.get("timeoutSeconds") == 5 and item.get("parameters") == [{"name": "clientId", "type": "INTEGER", "required": True}],
       {"total": picker["total"], "item": item})
DRAFTED_OUTPUTS = [{"name": "agreement_id", "type": "TEXT"}, {"name": "business_mode", "type": "ENUM", "enumValues": ["MOR", "PSP"]},
                   {"name": "capability_codes", "type": "TEXT"}]


def dq_config(code, parameters, outputs):
    return {"queryCode": code, "parameters": parameters, "outputs": outputs,
            "descriptionKey": "workflow.dataQuery.criteria." + code, "instruction": None}


valid = ops.send("POST", "/workflow-admin/v1/action-templates/validation",
                 {"actionType": "DATA_QUERY", "config": dq_config("GOLIVE_AGREEMENT_DRAFTED", ["clientId"], DRAFTED_OUTPUTS)})
record("配置校验：合法配置无问题", valid["problems"] == [], valid)
invalid = ops.send("POST", "/workflow-admin/v1/action-templates/validation", {"actionType": "DATA_QUERY", "config": dq_config(
    "GOLIVE_AGREEMENT_DRAFTED", ["agreementId"], DRAFTED_OUTPUTS + [{"name": "client_name", "type": "TEXT"}])})
record("配置校验：参数未声明 / 必填缺失 / 输出列未声明，itemKey 定位",
       [(p["code"], p["itemKey"]) for p in invalid["problems"]] == [
           ("DATA_QUERY_PARAMETER_UNDECLARED", "agreementId"), ("DATA_QUERY_PARAMETER_MISSING", "clientId"),
           ("DATA_QUERY_OUTPUT_UNDECLARED", "client_name")], invalid)
missing_template = ops.send("POST", "/workflow-admin/v1/action-templates/validation",
                            {"actionType": "DATA_QUERY", "config": dq_config("GOLIVE_NOPE", [], [])})
record("配置校验：模板不存在 itemKey 为空", [(p["code"], p["itemKey"]) for p in missing_template["problems"]]
       == [("DATA_QUERY_TEMPLATE_NOT_FOUND", None)], missing_template)


# ---------- 4 动作模板与流程定义 ----------
def action_template(code, name, action_type, config):
    listed = ops.get("/workflow-admin/v1/action-templates?keyword=" + code + "&page=1&size=50")
    found = [row for row in listed["items"] if row["templateCode"] == code]
    if found:
        return found[0]["templateId"]
    return ops.send("POST", "/workflow-admin/v1/action-templates", {"templateCode": code, "templateName": name,
                                                                    "actionType": action_type, "config": config})["objectId"]


drafted_tpl = action_template("REQ05_GOLIVE_DRAFTED", "合同已起草检查", "DATA_QUERY",
                              dq_config("GOLIVE_AGREEMENT_DRAFTED", ["clientId"], DRAFTED_OUTPUTS))
active_tpl = action_template("REQ05_GOLIVE_ACTIVE", "合同生效检查", "DATA_QUERY",
                             dq_config("GOLIVE_AGREEMENT_ACTIVE", ["agreementId"], []))
collection_tpl = action_template("REQ05_GOLIVE_COLLECTION", "收款开通检查", "DATA_QUERY",
                                 dq_config("GOLIVE_COLLECTION_CAPABILITIES_ENABLED", ["agreementId"],
                                           [{"name": "pending_capabilities", "type": "TEXT"}]))
slow_tpl = action_template("REQ05_SLOW_CHECK", "慢查询检查（超时）", "DATA_QUERY", dq_config("E2E_REQ05_SLOW", [], []))
confirm_tpl = action_template("REQ05_PSP_CONFIRM", "PSP 人工复核", "MANUAL_CONFIRM",
                              {"instruction": "PSP 模式人工复核", "noteRequired": False})
expect_status("新建 DATA_QUERY 模板：引用不存在的查询模板 400", 400, lambda: ops.send(
    "POST", "/workflow-admin/v1/action-templates", {"templateCode": "REQ05_BAD_" + time.strftime("%H%M%S"),
                                                    "templateName": "坏模板", "actionType": "DATA_QUERY",
                                                    "config": dq_config("GOLIVE_NOPE", [], [])}))
admin_id = [c for c in ops.get("/workflow/v1/assignee-candidates?keyword=" + s["E2E_ADMIN_EMAIL"])["items"]][0]["accountId"]


def node(key, node_type, name, **extra):
    return {"key": key, "type": node_type, "name": name, **extra}


def action(key, template, inputs=None):
    return {"key": key, "templateId": template, "required": True, "executorId": admin_id, "inputs": inputs or {}}


def var(name):
    return {"source": "VARIABLE", "value": name}


GOLIVE_GRAPH = {
    "nodes": [node("start", "START", "开始"),
              node("n3", "TASK", "N3 合同起草检查", actions=[action("drafted", drafted_tpl, {"clientId": var("start.clientId")})]),
              node("g1", "EXCLUSIVE", "业务模式", conditions=[{"edgeKey": "g1_n4", "all": [
                  {"variable": "drafted.business_mode", "operator": "EQ", "value": "MOR"}]}], defaultEdgeKey="g1_psp"),
              node("n4", "TASK", "N4 合同生效检查", actions=[action("active", active_tpl, {"agreementId": var("drafted.agreement_id")})]),
              node("n8", "TASK", "N8 收款开通检查",
                   actions=[action("collection", collection_tpl, {"agreementId": var("drafted.agreement_id")})]),
              node("psp", "TASK", "PSP 人工复核", actions=[action("pspConfirm", confirm_tpl)]),
              node("end", "END", "结束"), node("endPsp", "END", "结束（PSP）")],
    "edges": [{"key": "start_n3", "from": "start", "to": "n3"}, {"key": "n3_g1", "from": "n3", "to": "g1"},
              {"key": "g1_n4", "from": "g1", "to": "n4"}, {"key": "g1_psp", "from": "g1", "to": "psp"},
              {"key": "n4_n8", "from": "n4", "to": "n8"}, {"key": "n8_end", "from": "n8", "to": "end"},
              {"key": "psp_endPsp", "from": "psp", "to": "endPsp"}]}
CLIENT_FORM = {"fields": [{"key": "clientId", "label": "Client ID", "type": "TEXT", "required": True}]}
SLOW_GRAPH = {"nodes": [node("start", "START", "开始"), node("slow", "TASK", "慢检查", actions=[action("slow", slow_tpl)]),
                        node("end", "END", "结束")],
              "edges": [{"key": "start_slow", "from": "start", "to": "slow"}, {"key": "slow_end", "from": "slow", "to": "end"}]}


def definition(code, name, graph, form):
    listed = ops.get("/workflow-admin/v1/definitions?keyword=" + code + "&page=1&size=50")
    found = [row for row in listed["items"] if row["definitionCode"] == code]
    if found and found[0].get("currentVersionNo"):
        return found[0]["definitionId"]
    definition_id = found[0]["definitionId"] if found else ops.send(
        "POST", "/workflow-admin/v1/definitions", {"definitionCode": code, "definitionName": name})["objectId"]
    version = ops.get(f"/workflow-admin/v1/definitions/{definition_id}")["definition"]["version"]
    draft = ops.send("PUT", f"/workflow-admin/v1/definitions/{definition_id}/draft",
                     {"definitionName": name, "graph": graph, "startForm": form, "expectedVersion": version})
    record(f"草稿 {code} 无问题", draft["problems"] == [], draft["problems"])
    ops.send("POST", f"/workflow-admin/v1/definitions/{definition_id}/publication", {"expectedVersion": draft["version"]})
    record(f"发布 {code}", True, {"definitionId": definition_id})
    return definition_id


golive_def = definition("ACC_REQ05_GOLIVE_COMMERCIAL", "REQ-05 验收 · 客户上线（commercial）", GOLIVE_GRAPH, CLIENT_FORM)
slow_def = definition("ACC_REQ05_CHECK_FAILURE", "REQ-05 验收 · 检查失败与终止", SLOW_GRAPH, {"fields": []})


# ---------- 5 发起与运行 ----------
def start(code, title, key, values):
    return ops.send("POST", "/workflow/v1/instances", {"definitionCode": code, "title": title, "businessKey": key,
                                                      "startValues": values, "approvers": [], "executors": []})


def detail(instance_id):
    return ops.get(f"/workflow/v1/instances/{instance_id}")


def latest(instance, action_key):
    runs = [run for run in instance["actionRuns"] if run["actionKey"] == action_key]
    return runs[-1] if runs else None


def wait_settled(instance_id, predicate, timeout=30):
    for _ in range(timeout * 2):
        current = detail(instance_id)
        if predicate(current):
            return current
        time.sleep(0.5)
    return detail(instance_id)


def summary(instance, *keys):
    out = {"status": instance["instance"]["status"]}
    for key in keys:
        run = latest(instance, key)
        out[key] = None if run is None else {"actionRunId": run["actionRunId"], "status": run["status"],
                                             "check": run.get("dataQueryCheck"), "output": run.get("output"),
                                             "submittedBy": run.get("submittedBy")}
    return out


CLIENTS = {"complete": "237161687952666625", "stuckAtCollection": "240674482170114049",
           "notDrafted": "240674874987655169", "psp": "900000000001380201"}
instances = {}
for label, client in CLIENTS.items():
    reply = start("ACC_REQ05_GOLIVE_COMMERCIAL", f"上线 · {client}", f"CLIENT:{client}", {"clientId": client})
    instances[label] = reply["objectId"]
    record(f"按编码发起 {label}", reply["status"] == "RUNNING" or reply.get("created") is False, reply)

again = start("ACC_REQ05_GOLIVE_COMMERCIAL", "上线 · 重复", f"CLIENT:{CLIENTS['stuckAtCollection']}",
              {"clientId": CLIENTS["stuckAtCollection"]})
record("同键重复发起返回已有实例 created=false", again["created"] is False and again["objectId"] == instances["stuckAtCollection"], again)
expect_status("按编码发起：编码不存在 404", 404, lambda: start("NO_SUCH_FLOW", "x", None, {}))
expect_status("二选一违反 400", 400, lambda: ops.send("POST", "/workflow/v1/instances", {
    "definitionCode": "ACC_REQ05_GOLIVE_COMMERCIAL", "definitionId": golive_def, "versionNo": 1, "title": "x",
    "startValues": {"clientId": "1"}, "approvers": [], "executors": []}))

complete = wait_settled(instances["complete"], lambda i: i["instance"]["status"] == "COMPLETED")
record("满足：起草 → MOR 网关 → 生效 → 收款开通，系统级联完成，实例 COMPLETED", complete["instance"]["status"] == "COMPLETED"
       and all(latest(complete, k)["status"] == "COMPLETED" and latest(complete, k)["submittedBy"] is None
               for k in ("drafted", "active", "collection")), summary(complete, "drafted", "active", "collection"))
stuck = wait_settled(instances["stuckAtCollection"], lambda i: (latest(i, "collection") or {}).get("dataQueryCheck", {})
                     .get("result") is not None)
collection = latest(stuck, "collection")
record("N8 未满足：停在收款开通检查，missing=CAPABILITY_NOT_ENABLED", collection["status"] == "PENDING"
       and collection["dataQueryCheck"]["result"] == "UNSATISFIED"
       and collection["dataQueryCheck"]["missing"] == ["CAPABILITY_NOT_ENABLED"], summary(stuck, "drafted", "active", "collection"))
not_drafted = wait_settled(instances["notDrafted"], lambda i: latest(i, "drafted")["dataQueryCheck"]["result"] is not None)
record("N3 未满足：AGREEMENT_NOT_DRAFTED", latest(not_drafted, "drafted")["dataQueryCheck"]["missing"] == ["AGREEMENT_NOT_DRAFTED"],
       summary(not_drafted, "drafted"))
psp = wait_settled(instances["psp"], lambda i: latest(i, "pspConfirm") is not None)
record("PSP：网关走默认边到人工复核", latest(psp, "drafted")["output"]["business_mode"] == "PSP"
       and latest(psp, "pspConfirm")["status"] == "PENDING", summary(psp, "drafted", "pspConfirm"))
tasks = ops.get("/workflow/v1/my-tasks?taskType=ACTION&page=1&size=50")
task_types = {row["instanceId"]: row.get("actionType") for row in tasks["items"]}
record("我的待办带 actionType", task_types.get(instances["stuckAtCollection"]) == "DATA_QUERY"
       and task_types.get(instances["psp"]) == "MANUAL_CONFIRM", {k: task_types.get(v) for k, v in instances.items()})

# 立即检查（未满足）与人工完成拒绝
check_now = ops.send("POST", f"/workflow/v1/action-runs/{collection['actionRunId']}/data-query-check", {})
record("立即检查：仍未满足，返回检查块", check_now["actionStatus"] == "PENDING" and check_now["inProgress"] is False
       and check_now["check"]["result"] == "UNSATISFIED", check_now)
expect_status("DATA_QUERY 不能人工完成 400", 400, lambda: ops.send(
    "POST", f"/workflow/v1/action-runs/{collection['actionRunId']}/submission", {"output": {}}))
expect_status("立即检查：非 DATA_QUERY 动作 400", 400, lambda: ops.send(
    "POST", f"/workflow/v1/action-runs/{latest(psp, 'pspConfirm')['actionRunId']}/data-query-check", {}))

# 检查失败（超时）与立即检查累计
failing_id = start("ACC_REQ05_CHECK_FAILURE", "检查失败 · 超时", "E2E:REQ05:FAILED", {})["objectId"]
failing = wait_settled(failing_id, lambda i: latest(i, "slow")["dataQueryCheck"]["result"] is not None)
slow_run = latest(failing, "slow")
again_failed = ops.send("POST", f"/workflow/v1/action-runs/{slow_run['actionRunId']}/data-query-check", {})
record("检查失败：QUERY_TIMEOUT，立即检查后连续失败 2", slow_run["dataQueryCheck"]["failureCode"] == "QUERY_TIMEOUT"
       and again_failed["check"]["consecutiveFailures"] == 2, {"first": slow_run["dataQueryCheck"], "after": again_failed["check"]})

# 检查中终止
terminate_id = start("ACC_REQ05_CHECK_FAILURE", "检查中终止", "E2E:REQ05:TERMINATE", {})["objectId"]
first = wait_settled(terminate_id, lambda i: latest(i, "slow")["dataQueryCheck"]["result"] is not None)
run_id = latest(first, "slow")["actionRunId"]
holder = {}
worker = threading.Thread(target=lambda: holder.update(
    reply=ops.send("POST", f"/workflow/v1/action-runs/{run_id}/data-query-check", {})))
worker.start()
time.sleep(0.4)
terminated = ops.send("POST", f"/workflow/v1/instances/{terminate_id}/termination", {"reason": "REQ-05 阶段 5：检查中终止"})
worker.join(30)
reply = holder.get("reply", {})
record("慢查询中终止：终止不被阻塞，检查结果丢弃，动作 CANCELLED", terminated["status"] == "TERMINATED"
       and reply.get("actionStatus") == "CANCELLED" and reply.get("instanceStatus") == "TERMINATED", {"terminate": terminated,
                                                                                                    "check": reply})

# ---------- 6 权限 ----------
creds_file = SCRATCH / "proc" / "req05-operators.json"
if not creds_file.exists():
    stamp = time.strftime("%H%M%S")
    made = {}
    for label, permissions in (("handler", ["900000000000127105"]), ("reader", ["900000000000131101"])):
        role = ops.send("POST", "/iam/v1/roles", {"roleCode": f"REQ05_{label.upper()}_{stamp}",
                                                 "roleName": f"REQ-05 阶段 5 {label} {stamp}", "permissionIds": permissions,
                                                 "reason": "REQ-05 阶段 5 权限反例"})
        email = f"req05-{label}-{stamp}@example.test"
        p1, p2 = "Rq!" + ps.token_urlsafe(18), "Rq!" + ps.token_urlsafe(18)
        account = ops.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1,
                                                       "roleIds": [role.get("roleId") or role.get("objectId")],
                                                       "reason": "REQ-05 阶段 5 权限反例"})
        e2e_http.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        made[label] = {"email": email, "password": p2, "accountId": account.get("accountId"),
                       "roleId": role.get("roleId") or role.get("objectId")}
    creds_file.write_text(json.dumps(made))
    os.chmod(creds_file, 0o600)
creds = json.loads(creds_file.read_text())
handler = e2e_http.Operations(creds["handler"]["email"], creds["handler"]["password"])
reader = e2e_http.Operations(creds["reader"]["email"], creds["reader"]["password"])
expect_status("无 TASK_HANDLE：立即检查 403", 403, lambda: reader.send(
    "POST", f"/workflow/v1/action-runs/{collection['actionRunId']}/data-query-check", {}))
expect_status("只有 TASK_HANDLE 且非参与人：立即检查 404", 404, lambda: handler.send(
    "POST", f"/workflow/v1/action-runs/{collection['actionRunId']}/data-query-check", {}))
expect_status("无动作模板管理：模板选择 403", 403, lambda: handler.get("/workflow-admin/v1/data-query-templates"))
expect_status("无动作模板管理：配置校验 403", 403, lambda: handler.send(
    "POST", "/workflow-admin/v1/action-templates/validation", {"actionType": "DATA_QUERY", "config": dq_config("X1", [], [])}))
expect_status("无 INSTANCE_START：按编码发起 403", 403, lambda: handler.send("POST", "/workflow/v1/instances", {
    "definitionCode": "ACC_REQ05_GOLIVE_COMMERCIAL", "title": "x", "startValues": {"clientId": "1"}, "approvers": [],
    "executors": []}))

# ---------- 7 落库核对（只读） ----------
rows = platform_db("SELECT i.instance_id, i.business_key, i.status, i.active_slot FROM t_workflow_instance i JOIN "
                   "t_workflow_definition d ON d.definition_id = i.definition_id WHERE d.definition_code LIKE 'ACC_REQ05_%' "
                   "ORDER BY i.start_time;")
record("实例槽位：RUNNING 为 1，结束为空", all((r["status"] == "RUNNING") == (r["active_slot"] == "1") for r in rows), rows)
masked = {label: {"accountId": value["accountId"], "email": value["email"][:5] + "***@example.test"} for label, value in creds.items()}
result = {"results": results, "instances": instances, "failing": failing_id, "terminated": terminate_id,
          "definitions": {"ACC_REQ05_GOLIVE_COMMERCIAL": golive_def, "ACC_REQ05_CHECK_FAILURE": slow_def},
          "dataSources": {"GOLIVE_COMMERCIAL_LOCAL": commercial_source, "FIXTURE_REQ05": fixture_source},
          "operators": masked}
(HERE / "stage5-e2e-result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
print("TOTAL", sum(r["ok"] for r in results), "/", len(results))
