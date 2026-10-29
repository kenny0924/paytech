"""REQ-05 workflow 按钮发起与数据验证 · mor-test 阶段 7 登记与接口验收（E2E_TARGET=test；运营入口 http://localhost:28080 → Gateway → ops-bff → Platform）。
阶段 5 本机 38 项（verification/stage5/stage5_e2e.py）的 mor-test 子集；mor-test 没有 PSP 模式合同、没有慢查询 fixture 库、不新建受限账号，
所以 PSP 分支、QUERY_TIMEOUT / 检查中终止、权限反例只在本机验证（stage5-report 第 4 节）。

子命令（按 runbook 顺序；结果写本目录 result-<子命令>-<UTC>.json，不含口令与令牌）：
  setup     登记（写操作，需 --yes；可重跑，按编码复用已有对象）：
            1 数据源 --data-source（缺省 MOR_COMMERCIAL_REPLICA）必须已存在且 ACTIVE（不新建、不改）；
            2 查询模板 GOLIVE_AGREEMENT_DRAFTED / GOLIVE_AGREEMENT_ACTIVE / GOLIVE_COLLECTION_CAPABILITIES_ENABLED（正文取 REQ-01
              db/data-query-templates.sql，参数 INTEGER 必填，maxRows 1、超时 5 秒、不分页，声明全部输出列）；
            3 动作模板 REQ05_GOLIVE_DRAFTED / REQ05_GOLIVE_ACTIVE / REQ05_GOLIVE_COLLECTION（DATA_QUERY）、REQ05_PSP_CONFIRM（MANUAL_CONFIRM）；
            4 流程定义 ACC_REQ05_GOLIVE_COMMERCIAL：草稿无问题后发布（执行人 = 运营管理员）。
  check     只读核对（不写业务数据；配置校验接口不落库）：模板选择带 versionNo / timeoutSeconds / 参数；配置校验合法 / 参数未声明 /
            必填缺失 / 输出列未声明（itemKey 定位）/ 模板不存在（itemKey 为空）。
  scenario  运行场景（写操作，需 --yes）：按编码发起 4 个 Client（见 CLIENTS；阶段 6 在 mor-test 只读推演过走向，
            precheck/golive-templates-mortest-20261029.out）→ 完成 / 停在 N8 / 停在 N4 / 停在 N3；同键重复发起 created=false；
            编码不存在 404；二选一违反 400；我的待办 actionType；立即检查（未满足，返回检查块）；DATA_QUERY 人工完成 400。
  recheck   只读（XXL 任务启用且至少一轮之后）：scenario 记录的 3 个未满足动作的最近检查时间都晚于 scenario 时的值，结果仍为未满足。
  cleanup   收尾（写操作，需 --yes）：终止 scenario 发起且仍 RUNNING 的实例（原因写明验收收尾）。定义与模板保留。
用法：E2E_TARGET=test python3 -I req05_api.py <setup|check|scenario|recheck|cleanup> [--yes] [--data-source CODE] [--clients JSON]
本机演练：E2E_TARGET=local python3 -I req05_api.py check --data-source GOLIVE_COMMERCIAL_LOCAL --clients '<本机 Client JSON>'
"""
import argparse
import datetime
import json
import os
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

TEMPLATES_SQL = Path("/Users/kenny/Desktop/Workspace/Code-MoR-wfdq/requirements/20261028/commercial-开通项前置门禁/db/data-query-templates.sql")
DEFINITION = "ACC_REQ05_GOLIVE_COMMERCIAL"
# mor-test（阶段 6 只读推演，2026-10-29）：Client → 预期走向
CLIENTS = {
    "complete": "237575207592763393",           # DEMO-CL-001：N3 / N4 / N8 全部满足 → COMPLETED
    "stuckAtCollection": "237573267559391233",  # DEMO2-CL-103：N8 未满足 CAPABILITY_NOT_ENABLED（GOOGLE_PAY、STC_PAY 未开通）
    "stuckAtActive": "237573382420406273",      # DEMO2-CL-102：N4 未满足 TERM_VERSION_NOT_ACTIVE（价格版本 APPROVED）
    "notDrafted": "237573699337822209",         # DEMO-CL-004：N3 未满足 AGREEMENT_NOT_DRAFTED（合同 DRAFT）
}
EXPECT = {"stuckAtCollection": ("collection", "CAPABILITY_NOT_ENABLED"), "stuckAtActive": ("active", "TERM_VERSION_NOT_ACTIVE"),
          "notDrafted": ("drafted", "AGREEMENT_NOT_DRAFTED")}
DRAFTED_OUTPUTS = [{"name": "agreement_id", "type": "TEXT"}, {"name": "business_mode", "type": "ENUM", "enumValues": ["MOR", "PSP"]},
                   {"name": "capability_codes", "type": "TEXT"}]
checks = []


def now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def check(name, ok, evidence=None):
    checks.append({"name": name, "pass": bool(ok), "evidence": evidence})
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False)[:400]),
          flush=True)
    return ok


def expect_status(name, status, fn):
    try:
        reply = fn()
        return check(name, False, {"expectedStatus": status, "actual": 200, "body": reply})
    except e2e_http.Failure as failure:
        body = json.loads(failure.body) if failure.body.startswith("{") else failure.body
        return check(name, failure.status == status, {"status": failure.status, "code": body.get("code") if isinstance(body, dict) else None})


def save(label, data):
    path = HERE / f"result-{label}-{now().replace(':', '')}.json"
    path.write_text(json.dumps({**data, "target": e2e_http.TARGET, "checks": checks,
                                "verdict": all(c["pass"] for c in checks)}, ensure_ascii=False, indent=2) + "\n")
    print("结果：", path, "通过", sum(c["pass"] for c in checks), "/", len(checks))
    return path


def latest_result(label):
    files = sorted(HERE.glob(f"result-{label}-*.json"))
    if not files:
        raise SystemExit(f"缺少 result-{label}-*.json，先运行 {label}")
    return json.loads(files[-1].read_text())


def dq_config(code, parameters, outputs):
    return {"queryCode": code, "parameters": parameters, "outputs": outputs,
            "descriptionKey": "workflow.dataQuery.criteria." + code, "instruction": None}


def golive_bodies():
    text = "\n".join(line for line in TEMPLATES_SQL.read_text().splitlines() if not line.lstrip().startswith("--"))
    return [statement.strip() for statement in text.split(";") if "SELECT" in statement]


class Runner:
    def __init__(self, args):
        s = e2e_http.secrets()
        self.admin_email = s["E2E_ADMIN_EMAIL"]
        self.ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
        self.args = args
        self.clients = json.loads(args.clients) if args.clients else CLIENTS

    # ---------- setup ----------
    def data_source(self):
        code = self.args.data_source
        found = [row for row in self.ops.get("/data-query-admin/v1/data-sources?keyword=" + code)["items"]
                 if row["dataSourceCode"] == code]
        if not check(f"数据源 {code} 存在且 ACTIVE（不新建、不改）", found and found[0]["status"] == "ACTIVE",
                     found[0] if found else None):
            raise SystemExit("数据源不可用，停止")
        return found[0]["dataSourceId"]

    def query_template(self, code, name, source, body, parameters, columns):
        listed = self.ops.get("/data-query-admin/v1/templates?keyword=" + code)
        found = [row for row in listed["items"] if row["templateCode"] == code]
        if not found:
            self.ops.send("POST", "/data-query-admin/v1/templates", {
                "templateCode": code, "templateName": name, "description": "客户上线编排 REQ-05：DATA_QUERY 系统检查（单行，satisfied / out_* / missing）",
                "dataSourceId": source, "body": body, "parameters": parameters,
                "columns": [{"name": column, "mask": "NONE"} for column in columns],
                "constraints": {"maxRows": 1, "timeoutSeconds": 5, "pagingMode": "NONE", "countAllowed": False},
                "changeNote": "REQ-05 mor-test 发布"})
        listed = [row for row in self.ops.get("/data-query-admin/v1/templates?keyword=" + code)["items"] if row["templateCode"] == code]
        check(f"查询模板 {code} ACTIVE", listed and listed[0]["status"] == "ACTIVE", {"created": not found, "row": listed[0] if listed else None})

    def action_template(self, code, name, action_type, config):
        listed = self.ops.get("/workflow-admin/v1/action-templates?keyword=" + code + "&page=1&size=50")
        found = [row for row in listed["items"] if row["templateCode"] == code]
        template_id = found[0]["templateId"] if found else self.ops.send(
            "POST", "/workflow-admin/v1/action-templates", {"templateCode": code, "templateName": name, "actionType": action_type,
                                                            "config": config})["objectId"]
        check(f"动作模板 {code}", True, {"templateId": template_id, "created": not found})
        return template_id

    def setup(self):
        source = self.data_source()
        drafted_sql, active_sql, collection_sql = golive_bodies()
        self.query_template("GOLIVE_AGREEMENT_DRAFTED", "合同已起草且价格版本已批准", source, drafted_sql,
                            [{"name": "clientId", "type": "INTEGER", "required": True}],
                            ["satisfied", "out_agreement_id", "out_business_mode", "out_entity_no", "out_capability_codes", "missing"])
        self.query_template("GOLIVE_AGREEMENT_ACTIVE", "合同已生效且价格版本生效", source, active_sql,
                            [{"name": "agreementId", "type": "INTEGER", "required": True}], ["satisfied", "missing"])
        self.query_template("GOLIVE_COLLECTION_CAPABILITIES_ENABLED", "收款开通项全部已开通", source, collection_sql,
                            [{"name": "agreementId", "type": "INTEGER", "required": True}],
                            ["satisfied", "out_pending_capabilities", "missing"])
        drafted = self.action_template("REQ05_GOLIVE_DRAFTED", "合同已起草检查", "DATA_QUERY",
                                       dq_config("GOLIVE_AGREEMENT_DRAFTED", ["clientId"], DRAFTED_OUTPUTS))
        active = self.action_template("REQ05_GOLIVE_ACTIVE", "合同生效检查", "DATA_QUERY",
                                      dq_config("GOLIVE_AGREEMENT_ACTIVE", ["agreementId"], []))
        collection = self.action_template("REQ05_GOLIVE_COLLECTION", "收款开通检查", "DATA_QUERY",
                                          dq_config("GOLIVE_COLLECTION_CAPABILITIES_ENABLED", ["agreementId"],
                                                    [{"name": "pending_capabilities", "type": "TEXT"}]))
        confirm = self.action_template("REQ05_PSP_CONFIRM", "PSP 人工复核", "MANUAL_CONFIRM",
                                       {"instruction": "PSP 模式人工复核", "noteRequired": False})
        admin_id = self.ops.get("/workflow/v1/assignee-candidates?keyword=" + self.admin_email)["items"][0]["accountId"]

        def node(key, node_type, name, **extra):
            return {"key": key, "type": node_type, "name": name, **extra}

        def action(key, template, inputs=None):
            return {"key": key, "templateId": template, "required": True, "executorId": admin_id, "inputs": inputs or {}}

        def var(name):
            return {"source": "VARIABLE", "value": name}

        graph = {
            "nodes": [node("start", "START", "开始"),
                      node("n3", "TASK", "N3 合同起草检查", actions=[action("drafted", drafted, {"clientId": var("start.clientId")})]),
                      node("g1", "EXCLUSIVE", "业务模式", conditions=[{"edgeKey": "g1_n4", "all": [
                          {"variable": "drafted.business_mode", "operator": "EQ", "value": "MOR"}]}], defaultEdgeKey="g1_psp"),
                      node("n4", "TASK", "N4 合同生效检查", actions=[action("active", active, {"agreementId": var("drafted.agreement_id")})]),
                      node("n8", "TASK", "N8 收款开通检查",
                           actions=[action("collection", collection, {"agreementId": var("drafted.agreement_id")})]),
                      node("psp", "TASK", "PSP 人工复核", actions=[action("pspConfirm", confirm)]),
                      node("end", "END", "结束"), node("endPsp", "END", "结束（PSP）")],
            "edges": [{"key": "start_n3", "from": "start", "to": "n3"}, {"key": "n3_g1", "from": "n3", "to": "g1"},
                      {"key": "g1_n4", "from": "g1", "to": "n4"}, {"key": "g1_psp", "from": "g1", "to": "psp"},
                      {"key": "n4_n8", "from": "n4", "to": "n8"}, {"key": "n8_end", "from": "n8", "to": "end"},
                      {"key": "psp_endPsp", "from": "psp", "to": "endPsp"}]}
        form = {"fields": [{"key": "clientId", "label": "Client ID", "type": "TEXT", "required": True}]}
        name = "REQ-05 验收 · 客户上线（commercial）"
        listed = [row for row in self.ops.get("/workflow-admin/v1/definitions?keyword=" + DEFINITION + "&page=1&size=50")["items"]
                  if row["definitionCode"] == DEFINITION]
        if listed and listed[0].get("currentVersionNo"):
            definition_id = listed[0]["definitionId"]
            check(f"流程定义 {DEFINITION} 已发布（复用）", True, {"definitionId": definition_id, "versionNo": listed[0]["currentVersionNo"]})
        else:
            definition_id = listed[0]["definitionId"] if listed else self.ops.send(
                "POST", "/workflow-admin/v1/definitions", {"definitionCode": DEFINITION, "definitionName": name})["objectId"]
            version = self.ops.get(f"/workflow-admin/v1/definitions/{definition_id}")["definition"]["version"]
            draft = self.ops.send("PUT", f"/workflow-admin/v1/definitions/{definition_id}/draft",
                                  {"definitionName": name, "graph": graph, "startForm": form, "expectedVersion": version})
            if not check(f"草稿 {DEFINITION} 无问题", draft["problems"] == [], draft["problems"]):
                return save("setup", {"definitionId": definition_id})
            self.ops.send("POST", f"/workflow-admin/v1/definitions/{definition_id}/publication", {"expectedVersion": draft["version"]})
            check(f"发布 {DEFINITION}", True, {"definitionId": definition_id})
        return save("setup", {"dataSourceId": source, "definitionId": definition_id,
                              "actionTemplates": {"drafted": drafted, "active": active, "collection": collection, "pspConfirm": confirm}})

    # ---------- check（只读） ----------
    def check_only(self):
        picker = self.ops.get("/workflow-admin/v1/data-query-templates?keyword=GOLIVE&page=1&size=20")
        item = {row["templateCode"]: row for row in picker["items"]}.get("GOLIVE_AGREEMENT_DRAFTED", {})
        check("模板选择：GOLIVE_* 3 条，带 versionNo / timeoutSeconds / 参数", picker["total"] == 3 and item.get("versionNo") is not None
              and item.get("timeoutSeconds") == 5 and item.get("parameters") == [{"name": "clientId", "type": "INTEGER", "required": True}],
              {"total": picker["total"], "item": item})
        validate = lambda config: self.ops.send("POST", "/workflow-admin/v1/action-templates/validation",
                                                {"actionType": "DATA_QUERY", "config": config})
        valid = validate(dq_config("GOLIVE_AGREEMENT_DRAFTED", ["clientId"], DRAFTED_OUTPUTS))
        check("配置校验：合法配置无问题", valid["problems"] == [], valid)
        invalid = validate(dq_config("GOLIVE_AGREEMENT_DRAFTED", ["agreementId"], DRAFTED_OUTPUTS + [{"name": "client_name", "type": "TEXT"}]))
        check("配置校验：参数未声明 / 必填缺失 / 输出列未声明，itemKey 定位",
              [(p["code"], p["itemKey"]) for p in invalid["problems"]] == [
                  ("DATA_QUERY_PARAMETER_UNDECLARED", "agreementId"), ("DATA_QUERY_PARAMETER_MISSING", "clientId"),
                  ("DATA_QUERY_OUTPUT_UNDECLARED", "client_name")], invalid)
        missing = validate(dq_config("GOLIVE_NOPE", [], []))
        check("配置校验：模板不存在 itemKey 为空", [(p["code"], p["itemKey"]) for p in missing["problems"]]
              == [("DATA_QUERY_TEMPLATE_NOT_FOUND", None)], missing)
        return save("check", {})

    # ---------- scenario ----------
    def start(self, title, key, values, code=DEFINITION):
        return self.ops.send("POST", "/workflow/v1/instances", {"definitionCode": code, "title": title, "businessKey": key,
                                                               "startValues": values, "approvers": [], "executors": []})

    def detail(self, instance_id):
        return self.ops.get(f"/workflow/v1/instances/{instance_id}")

    @staticmethod
    def latest(instance, action_key):
        runs = [run for run in instance["actionRuns"] if run["actionKey"] == action_key]
        return runs[-1] if runs else None

    def wait(self, instance_id, predicate, timeout=60):
        for _ in range(timeout):
            current = self.detail(instance_id)
            if predicate(current):
                return current
            time.sleep(1)
        return self.detail(instance_id)

    def scenario(self):
        instances, created = {}, {}
        for label, client in self.clients.items():
            reply = self.start(f"上线验收 · {client}", f"CLIENT:{client}", {"clientId": client})
            instances[label], created[label] = reply["objectId"], reply.get("created")
            check(f"按编码发起 {label}（CLIENT:{client}）", reply["status"] in ("RUNNING", "COMPLETED"), reply)
        label = "stuckAtCollection" if "stuckAtCollection" in self.clients else next(iter(self.clients))
        again = self.start("上线验收 · 重复", f"CLIENT:{self.clients[label]}", {"clientId": self.clients[label]})
        check("同键重复发起返回已有实例 created=false", again.get("created") is False and again["objectId"] == instances[label], again)
        expect_status("按编码发起：编码不存在 404", 404, lambda: self.start("x", None, {}, code="NO_SUCH_FLOW"))
        definition_id = self.ops.get("/workflow-admin/v1/definitions?keyword=" + DEFINITION + "&page=1&size=50")["items"][0]["definitionId"]
        expect_status("definitionCode 与 definitionId + versionNo 二选一违反 400", 400, lambda: self.ops.send(
            "POST", "/workflow/v1/instances", {"definitionCode": DEFINITION, "definitionId": definition_id, "versionNo": 1, "title": "x",
                                               "startValues": {"clientId": "1"}, "approvers": [], "executors": []}))
        result = {"instances": instances, "created": created, "pending": {}}
        if "complete" in instances:
            done = self.wait(instances["complete"], lambda i: i["instance"]["status"] == "COMPLETED")
            check("满足：N3 → MOR → N4 → N8 系统级联完成，实例 COMPLETED，提交人为空", done["instance"]["status"] == "COMPLETED"
                  and all((self.latest(done, k) or {}).get("status") == "COMPLETED" and self.latest(done, k).get("submittedBy") is None
                          for k in ("drafted", "active", "collection")),
                  {k: (self.latest(done, k) or {}).get("status") for k in ("drafted", "active", "collection")})
        for label, (action_key, code) in EXPECT.items():
            if label not in instances:
                continue
            current = self.wait(instances[label], lambda i, k=action_key: ((self.latest(i, k) or {}).get("dataQueryCheck") or {})
                                .get("result") is not None)
            run = self.latest(current, action_key) or {}
            q = run.get("dataQueryCheck") or {}
            check(f"{label}：停在 {action_key}，UNSATISFIED，missing={code}", run.get("status") == "PENDING"
                  and q.get("result") == "UNSATISFIED" and q.get("missing") == [code], {"actionRunId": run.get("actionRunId"), "check": q})
            result["pending"][label] = {"actionRunId": run.get("actionRunId"), "lastCheckTime": q.get("lastCheckedAt")}
        tasks = self.ops.get("/workflow/v1/my-tasks?taskType=ACTION&page=1&size=100")
        types = {row["instanceId"]: row.get("actionType") for row in tasks["items"]}
        check("我的待办：未满足的检查动作 actionType=DATA_QUERY",
              all(types.get(instances[label]) == "DATA_QUERY" for label in result["pending"]),
              {label: types.get(instances[label]) for label in result["pending"]})
        if result["pending"]:
            label, pending = next(iter(result["pending"].items()))
            reply = self.ops.send("POST", f"/workflow/v1/action-runs/{pending['actionRunId']}/data-query-check", {})
            check(f"立即检查（{label}）：仍未满足，返回检查块", reply["actionStatus"] == "PENDING" and reply["inProgress"] is False
                  and reply["check"]["result"] == "UNSATISFIED", reply)
            result["pending"][label]["lastCheckTime"] = reply["check"].get("lastCheckedAt")
            expect_status("DATA_QUERY 不能人工完成 400", 400, lambda: self.ops.send(
                "POST", f"/workflow/v1/action-runs/{pending['actionRunId']}/submission", {"output": {}}))
        result["scenarioTime"] = now()
        return save("scenario", result)

    # ---------- recheck（只读） ----------
    def recheck(self):
        before = latest_result("scenario")
        for label, pending in before["pending"].items():
            current = self.detail(before["instances"][label])
            run = next((r for r in current["actionRuns"] if r["actionRunId"] == pending["actionRunId"]), {})
            q = run.get("dataQueryCheck") or {}
            check(f"XXL 重查（{label}）：最近检查时间前进，仍 UNSATISFIED", run.get("status") == "PENDING" and q.get("result") == "UNSATISFIED"
                  and (q.get("lastCheckedAt") or "") > (pending["lastCheckTime"] or ""),
                  {"before": pending["lastCheckTime"], "after": q.get("lastCheckedAt")})
        return save("recheck", {})

    # ---------- cleanup ----------
    def cleanup(self):
        before = latest_result("scenario")
        for label, instance_id in before["instances"].items():
            current = self.detail(instance_id)["instance"]
            if current["status"] != "RUNNING":
                check(f"{label} 已结束（{current['status']}），不处理", True)
                continue
            reply = self.ops.send("POST", f"/workflow/v1/instances/{instance_id}/termination", {"reason": "REQ-05 mor-test 验收收尾"})
            check(f"终止 {label}", reply["status"] == "TERMINATED", reply)
        return save("cleanup", {})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["setup", "check", "scenario", "recheck", "cleanup"])
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--data-source", default="MOR_COMMERCIAL_REPLICA")
    parser.add_argument("--clients")
    args = parser.parse_args()
    if args.command in ("setup", "scenario", "cleanup") and not args.yes:
        raise SystemExit(f"{args.command} 会写数据，确认后加 --yes")
    runner = Runner(args)
    {"setup": runner.setup, "check": runner.check_only, "scenario": runner.scenario, "recheck": runner.recheck,
     "cleanup": runner.cleanup}[args.command]()
    return 0 if all(c["pass"] for c in checks) else 1


if __name__ == "__main__":
    sys.exit(main())
