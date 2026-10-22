"""工作流阶段 5 本机全链路 API 验收（浏览器 → Nginx 18444 → Gateway → ops-bff → Platform 同一链路，用 Bearer 调用）。

子命令（状态写入 STATE 文件，只含 ID；第二个运营账号口令只写 SECRET 文件，权限 600，不打印）：
  setup        创建第二个运营账号（PLATFORM_ADMIN）、动作模板（FORM / MANUAL_CONFIRM / DATA_QUERY）、流程定义（非法发布 400 + 合法发布）
  start1       发起实例 1（APPLE_PAY 分支），供浏览器脚本处理
  u2 <op>      第二个账号对实例 1 的操作：return（退回修改）/ approve（同意 fill 审批）/ confirm（提交 MANUAL_CONFIRM）
  inst2        实例 2：动作转交、转交后待办迁移、同一人审批拒绝、审批转交、终止、终止后只读与待办清空
  inst3        实例 3：默认分支（CARD）走纯 API 直到完成，验证排他网关默认分支
  verify       实例 1 终态、活动记录、已处理列表、数据库只读核对
用法：python3 workflow_flow.py <子命令>
"""
import json
import os
import secrets as pysecrets
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[3] / "20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
from e2e_http import Failure, Operations, call, OPERATIONS, secrets  # noqa: E402

SCRATCH = Path(os.environ.get("WF_SCRATCH", "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/"
                                             "49da8772-1276-46ac-8174-179e39b15018/scratchpad"))
STATE = SCRATCH / "workflow-e2e-state.json"
SECRET = SCRATCH / "workflow-e2e-user2.json"
QSH = SCRATCH / "q.sh"


def load():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=1))


def db(statement):
    out = subprocess.run([str(QSH), "mor_platform_v2e2e"], input=statement, capture_output=True, text=True)
    if out.returncode:
        raise RuntimeError(out.stderr[:300])
    return [line.split("\t") for line in out.stdout.splitlines()]


def log(tag, text):
    print(f"[{tag}] {text}", flush=True)


def expect_failure(tag, fn, status):
    try:
        fn()
    except Failure as failure:
        body = failure.body
        log(tag, f"预期失败 HTTP {failure.status}（期望 {status}）：{body[:300]}")
        if failure.status != status:
            raise
        return body
    raise AssertionError(f"{tag}: 期望 HTTP {status}，实际成功")


def admin():
    s = secrets()
    return Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def user2():
    cred = json.loads(SECRET.read_text())
    return Operations(cred["email"], cred["password"])


# ---------------------------------------------------------------- setup

def ensure_user2(op, state):
    if SECRET.exists() and state.get("user2Id"):
        return
    stamp = time.strftime("%H%M%S")
    email = f"wf-e2e-{stamp}@example.test"
    initial = "Wf!" + pysecrets.token_urlsafe(18)
    final = "Wf!" + pysecrets.token_urlsafe(18)
    roles = op.get("/iam/v1/roles?page=1&size=50")["items"]
    admin_role = next(r["roleId"] for r in roles if r["roleCode"] == "PLATFORM_ADMIN")
    created = op.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": initial, "roleIds": [admin_role],
                                                   "reason": "工作流阶段 5 本机验收第二操作人"})
    log("user2", f"创建运营账号 HTTP 2xx accountId={created.get('accountId')} email={email[0]}***@example.test")
    first = Operations(email, initial)
    first.send("POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": final}, key=False)
    SECRET.write_text(json.dumps({"email": email, "password": final}))
    os.chmod(SECRET, 0o600)
    second = Operations(email, final)
    ctx = second.get("/auth/v1/context")
    state["user2Id"] = ctx["accountId"]
    state["user2Mask"] = email[0] + "***@example.test"
    log("user2", f"改密后登录 accountId={ctx['accountId']} passwordChangeRequired={ctx.get('passwordChangeRequired')}")


FORM_FIELDS = {"fields": [
    {"key": "merchantName", "label": "商户名称", "type": "TEXT", "required": True, "rules": {"maxLength": 64}},
    {"key": "monthlyVolume", "label": "月交易量", "type": "NUMBER", "required": True, "rules": {"min": "0", "max": "100000000", "scale": 2}},
    {"key": "region", "label": "区域", "type": "ENUM", "required": False,
     "rules": {"options": [{"value": "GCC", "label": "海湾"}, {"value": "EU", "label": "欧洲"}]}},
]}


def create_templates(op, state, stamp):
    form = op.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_FORM_{stamp}", "templateName": f"E2E 商户资料表单 {stamp}", "actionType": "FORM",
        "description": "阶段 5 验收", "config": FORM_FIELDS})
    log("template", f"FORM 创建 {form}")
    state["formTemplateId"] = form["objectId"]
    confirm = op.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_CONFIRM_{stamp}", "templateName": f"E2E 人工确认 {stamp}", "actionType": "MANUAL_CONFIRM",
        "config": {"instruction": "确认 Apple Pay 证书已上传", "noteRequired": True}})
    state["confirmTemplateId"] = confirm["objectId"]
    log("template", f"MANUAL_CONFIRM 创建 {confirm}")
    try:
        dq = op.send("POST", "/workflow-admin/v1/action-templates", {
            "templateCode": f"WF_E2E_DQ_{stamp}", "templateName": f"E2E 数据查询 {stamp}", "actionType": "DATA_QUERY",
            "config": {"queryCode": "E2E_PLACEHOLDER", "parameters": [], "passRule": {}, "evidenceColumns": []}})
        state["dataQueryTemplateId"] = dq["objectId"]
        log("template", f"DATA_QUERY 创建 {dq}")
    except Failure as failure:
        log("template", f"DATA_QUERY 创建被拒 HTTP {failure.status}: {failure.body[:200]}")
    listing = op.get(f"/workflow-admin/v1/action-templates?page=1&size=20&keyword={stamp}")
    log("template", f"列表 keyword={stamp} total={listing['total']} codes={[i.get('templateCode') for i in listing['items']]}")
    detail = op.get(f"/workflow-admin/v1/action-templates/{state['formTemplateId']}")
    log("template", f"FORM 详情 status={detail.get('status')} version={detail.get('version')} fields={len(detail['config']['fields'])}")
    # 修改一次：带 expectedVersion
    upd = op.send("PUT", f"/workflow-admin/v1/action-templates/{state['formTemplateId']}", {
        "templateName": detail["templateName"] + "（改）", "description": "阶段 5 验收（修改）", "config": FORM_FIELDS,
        "expectedVersion": detail["version"]})
    log("template", f"FORM 修改 {upd}")
    expect_failure("template", lambda: op.send("PUT", f"/workflow-admin/v1/action-templates/{state['formTemplateId']}", {
        "templateName": "冲突", "config": FORM_FIELDS, "expectedVersion": detail["version"]}), 409)
    expect_failure("template", lambda: op.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_BAD_{stamp}", "templateName": "非法配置", "actionType": "FORM",
        "config": {"fields": [{"key": "a", "label": "A", "type": "NOPE"}]}}), 400)


def graph(state, admin_id, user2_id, invalid=False):
    pos = lambda x, y: {"x": x, "y": y}
    if invalid:
        return {"nodes": [
            {"key": "start", "type": "START", "name": "开始", "position": pos(0, 0)},
            {"key": "empty", "type": "TASK", "name": "空任务", "position": pos(0, 120), "actions": []},
            {"key": "dq", "type": "TASK", "name": "数据查询节点", "position": pos(0, 240),
             "actions": [{"key": "dq1", "templateId": state.get("dataQueryTemplateId") or state["formTemplateId"],
                          "required": True, "executorId": admin_id}]},
            {"key": "end", "type": "END", "name": "结束", "position": pos(0, 360)}],
            "edges": [{"key": "e1", "from": "start", "to": "empty"}, {"key": "e2", "from": "empty", "to": "dq"},
                      {"key": "e3", "from": "dq", "to": "end"}]}
    return {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(250, 0)},
        {"key": "fill", "type": "TASK", "name": "填写商户资料", "position": pos(250, 120),
         "approval": {"mode": "ANY", "approverIds": [user2_id]},
         "actions": [{"key": "merchantInfo", "templateId": state["formTemplateId"], "required": True, "executorId": admin_id,
                      "inputs": {}}],
         "notifications": [
             {"trigger": "PASSED", "type": "CALLBACK", "callbackCode": "WF_E2E_FILL_PASSED"},
             {"trigger": "RETURNED_FOR_REVISION", "type": "NOTIFY", "recipients": ["NODE_EXECUTORS"], "channels": ["INBOX"]}]},
        {"key": "gw", "type": "EXCLUSIVE", "name": "含 Apple Pay？", "position": pos(250, 240),
         "conditions": [{"edgeKey": "e3", "all": [{"variable": "start.paymentMethod", "operator": "EQ", "value": "APPLE_PAY"}]}],
         "defaultEdgeKey": "e4"},
        {"key": "confirm", "type": "TASK", "name": "Apple Pay 证书确认", "position": pos(450, 360),
         "actions": [{"key": "applePayConfirm", "templateId": state["confirmTemplateId"], "required": True,
                      "executorId": user2_id}]},
        {"key": "finalApprove", "type": "TASK", "name": "上线终审", "position": pos(250, 480),
         "approval": {"mode": "ANY", "approverIds": [admin_id]}},
        {"key": "end", "type": "END", "name": "结束", "position": pos(250, 600)}],
        "edges": [{"key": "e1", "from": "start", "to": "fill"}, {"key": "e2", "from": "fill", "to": "gw"},
                  {"key": "e3", "from": "gw", "to": "confirm"}, {"key": "e4", "from": "gw", "to": "finalApprove"},
                  {"key": "e5", "from": "confirm", "to": "finalApprove"}, {"key": "e6", "from": "finalApprove", "to": "end"}],
        "settings": {"notifications": [{"trigger": "COMPLETED", "type": "NOTIFY", "recipients": ["STARTER"],
                                        "channels": ["INBOX", "EMAIL"]},
                                       {"trigger": "TERMINATED", "type": "CALLBACK", "callbackCode": "WF_E2E_TERMINATED"}]}}


START_FORM = {"fields": [
    {"key": "channelCode", "label": "渠道编码", "type": "TEXT", "required": True, "rules": {"maxLength": 64}},
    {"key": "paymentMethod", "label": "支付方式", "type": "ENUM", "required": True,
     "rules": {"options": [{"value": "CARD", "label": "卡"}, {"value": "APPLE_PAY", "label": "Apple Pay"}]}},
]}


def create_definitions(op, state, stamp, admin_id):
    # 非法草稿：保存返回问题但不阻止；发布 400；问题从详情重读
    bad = op.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"WF_E2E_BAD_{stamp}",
                                                             "definitionName": f"E2E 非法流程 {stamp}"})
    state["badDefinitionId"] = bad["objectId"]
    log("definition", f"非法流程创建 {bad}")
    detail = op.get(f"/workflow-admin/v1/definitions/{state['badDefinitionId']}")
    draft = op.send("PUT", f"/workflow-admin/v1/definitions/{state['badDefinitionId']}/draft", {
        "definitionName": f"E2E 非法流程 {stamp}", "graph": graph(state, admin_id, state["user2Id"], invalid=True),
        "startForm": {"fields": []}, "expectedVersion": detail["definition"]["version"]})
    log("definition", f"非法草稿保存 problems={len(draft.get('problems') or [])} {[p.get('code') for p in draft.get('problems') or []]}")
    detail = op.get(f"/workflow-admin/v1/definitions/{state['badDefinitionId']}")
    expect_failure("definition", lambda: op.send("POST", f"/workflow-admin/v1/definitions/{state['badDefinitionId']}/publication",
                                                 {"expectedVersion": detail["definition"]["version"]}), 400)
    detail = op.get(f"/workflow-admin/v1/definitions/{state['badDefinitionId']}")
    problems = detail.get("problems") or []
    log("definition", f"发布失败后重读详情 problems={len(problems)}：" + "；".join(
        f"{p.get('code')}@{p.get('nodeKey') or p.get('edgeKey') or ''}:{p.get('message')}" for p in problems))

    good = op.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"WF_E2E_ONBOARD_{stamp}",
                                                              "definitionName": f"E2E 渠道上线 {stamp}",
                                                              "description": "阶段 5 验收"})
    state["definitionId"] = good["objectId"]
    detail = op.get(f"/workflow-admin/v1/definitions/{state['definitionId']}")
    log("definition", f"新建 {good} 初始草稿节点={[n['key'] for n in detail['draft']['graph']['nodes']]}")
    draft = op.send("PUT", f"/workflow-admin/v1/definitions/{state['definitionId']}/draft", {
        "definitionName": f"E2E 渠道上线 {stamp}", "description": "阶段 5 验收",
        "graph": graph(state, admin_id, state["user2Id"]), "startForm": START_FORM, "expectedVersion": detail["definition"]["version"]})
    log("definition", f"合法草稿保存 problems={draft.get('problems')}")
    detail = op.get(f"/workflow-admin/v1/definitions/{state['definitionId']}")
    published = op.send("POST", f"/workflow-admin/v1/definitions/{state['definitionId']}/publication",
                        {"expectedVersion": detail["definition"]["version"]})
    log("definition", f"发布 {published}")
    detail = op.get(f"/workflow-admin/v1/definitions/{state['definitionId']}")
    state["versionNo"] = detail["definition"]["currentVersionNo"]
    log("definition", f"发布后 status={detail['definition']['status']} versions={[v.get('versionNo') for v in detail.get('versions', [])]} draft={'有' if detail.get('draft') else '无'}")
    version = op.get(f"/workflow-admin/v1/definitions/{state['definitionId']}/versions/{state['versionNo']}")
    log("definition", f"版本 {state['versionNo']} 节点={[n['key'] for n in version['graph']['nodes']]}")
    refs = op.get(f"/workflow-admin/v1/action-templates/{state['formTemplateId']}/references")
    log("template", f"FORM 引用 {json.dumps(refs, ensure_ascii=False)[:300]}")
    launch = op.get("/workflow/v1/launchable-definitions?page=1&size=50")
    log("launch", f"可发起 total={launch['total']} 含本流程={any(i.get('definitionId') == state['definitionId'] for i in launch['items'])}")
    one = op.get(f"/workflow/v1/launchable-definitions/{state['definitionId']}")
    log("launch", f"可发起详情 keys={sorted(one.keys())}")


def setup():
    op = admin()
    state = load()
    ctx = op.get("/auth/v1/context")
    state["adminId"] = ctx["accountId"]
    ensure_user2(op, state)
    save(state)
    stamp = time.strftime("%H%M%S")
    state["stamp"] = stamp
    create_templates(op, state, stamp)
    save(state)
    create_definitions(op, state, stamp, state["adminId"])
    save(state)
    log("setup", json.dumps(state, ensure_ascii=False))


# ---------------------------------------------------------------- runtime helpers

def start(op, state, tag, method):
    body = {"definitionId": state["definitionId"], "versionNo": state["versionNo"], "title": f"E2E {tag} {state['stamp']}",
            "businessKey": f"CHANNEL:E2E-{tag}-{state['stamp']}", "startValues": {"channelCode": f"E2E-{tag}", "paymentMethod": method}}
    result = op.send("POST", "/workflow/v1/instances", body)
    log("start", f"{tag} 发起 {result}")
    return result["objectId"]


def detail(op, instance_id):
    return op.get(f"/workflow/v1/instances/{instance_id}")


def active_action(d, node_key):
    runs = {r["nodeRunId"]: r for r in d["nodeRuns"]}
    candidates = [a for a in d["actionRuns"] if runs[a["nodeRunId"]]["nodeKey"] == node_key and a["status"] in ("PENDING", "UNVERIFIED")]
    return candidates[-1]


def pending_approval(d, node_key):
    runs = {r["nodeRunId"]: r for r in d["nodeRuns"]}
    return [a for a in d["approvals"] if runs[a["nodeRunId"]]["nodeKey"] == node_key and a["status"] == "PENDING"][-1]


def tasks(op):
    return op.get("/workflow/v1/my-tasks?page=1&size=50")["items"]


def summary(d):
    return {"status": d["instance"]["status"],
            "nodes": [(r["nodeKey"], r["roundNo"], r["status"]) for r in d["nodeRuns"]],
            "actions": [(a["actionKey"], a["status"]) for a in d["actionRuns"]],
            "approvals": [(a["approvalId"], a["status"]) for a in d["approvals"]]}


def u2(op_name):
    state = load()
    op = user2()
    d = detail(op, state["instance1"])
    if op_name == "return":
        approval = pending_approval(d, "fill")
        res = op.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision",
                      {"decision": "RETURN", "comment": "月交易量请按实际填写（E2E 退回修改）"})
        log("u2", f"RETURN {approval['approvalId']} -> {res}")
    elif op_name == "approve":
        approval = pending_approval(d, "fill")
        res = op.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", {"decision": "APPROVE", "comment": "资料无误"})
        log("u2", f"APPROVE {approval['approvalId']} -> {res}")
    elif op_name == "confirm":
        action = active_action(d, "confirm")
        expect_failure("u2", lambda: op.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
                                             {"output": {"note": ""}}), 200) if False else None
        res = op.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission", {"output": {"note": "证书已上传并核对"}})
        log("u2", f"MANUAL_CONFIRM 提交 -> {res}")
    log("u2", f"实例1 {summary(detail(op, state['instance1']))}")


def start1():
    state = load()
    op = admin()
    state["instance1"] = start(op, state, "I1", "APPLE_PAY")
    save(state)
    log("start1", f"实例1 {summary(detail(op, state['instance1']))}")


def a1(op_name):
    """浏览器不可用时 admin 对实例 1 的 API 操作：submit <volume> / final（终审同意）。"""
    state = load()
    op = admin()
    d = detail(op, state["instance1"])
    if op_name.startswith("submit"):
        action = active_action(d, "fill")
        res = op.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
                      {"output": {"merchantName": "Apple Co", "monthlyVolume": op_name.split(":")[1], "region": "GCC"}})
        log("a1", f"fill 提交 round 动作 {action['actionRunId']} -> {res}")
    elif op_name == "final":
        approval = pending_approval(d, "finalApprove")
        res = op.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", {"decision": "APPROVE"})
        log("a1", f"终审同意 -> {res}")
    log("a1", f"实例1 {summary(detail(op, state['instance1']))}")


def inst2():
    state = load()
    a, b = admin(), user2()
    iid = start(a, state, "I2", "CARD")
    state["instance2"] = iid
    save(state)
    d = detail(a, iid)
    action = active_action(d, "fill")
    # 非指派人提交：期望拒绝
    expect_failure("inst2", lambda: b.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
                                           {"output": {"merchantName": "X", "monthlyVolume": "1"}}), 400)
    # 转交给自己：拒绝
    expect_failure("inst2", lambda: a.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/transfer",
                                           {"toUserId": state["adminId"], "reason": "self"}), 400)
    before_b = [t for t in tasks(b) if t["instanceId"] == iid]
    res = a.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/transfer", {"toUserId": state["user2Id"], "reason": "E2E 转交给第二操作人"})
    log("inst2", f"动作转交 -> {res}")
    after_a = [t for t in tasks(a) if t["instanceId"] == iid]
    after_b = [t for t in tasks(b) if t["instanceId"] == iid]
    log("inst2", f"转交前 user2 待办={len(before_b)}；转交后 admin 待办={len(after_a)} user2 待办={[(t['taskType'], t['nodeKey']) for t in after_b]}")
    # 不合法输出：验证未通过（UNVERIFIED），节点不放行
    bad = b.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
                 {"output": {"merchantName": "", "monthlyVolume": "abc"}})
    log("inst2", f"非法提交 -> {bad}")
    good = b.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
                  {"output": {"merchantName": "Transfer Co", "monthlyVolume": "1000.50", "region": "EU"}})
    log("inst2", f"合法提交 -> {good}")
    d = detail(a, iid)
    log("inst2", f"提交后 {summary(d)}")
    approval = pending_approval(d, "fill")
    # 执行人同时为审批人：同一人限制
    expect_failure("inst2", lambda: b.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision",
                                           {"decision": "APPROVE"}), 400)
    # admin 不是该审批人：不能审批
    expect_failure("inst2", lambda: a.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision",
                                           {"decision": "APPROVE"}), 400)
    # 审批转交给 admin（admin 已转出动作、未执行）
    res = b.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/transfer", {"toUserId": state["adminId"], "reason": "E2E 审批转交"})
    log("inst2", f"审批转交 -> {res}；admin 待办={[(t['taskType'], t['nodeKey']) for t in tasks(a) if t['instanceId'] == iid]}")
    # user2 非发起人且有 INSTANCE_MANAGE（PLATFORM_ADMIN），此处由发起人 admin 终止
    expect_failure("inst2", lambda: a.send("POST", f"/workflow/v1/instances/{iid}/termination", {"reason": ""}), 422)
    res = a.send("POST", f"/workflow/v1/instances/{iid}/termination", {"reason": "E2E 终止验证"})
    log("inst2", f"终止 -> {res}")
    d = detail(a, iid)
    log("inst2", f"终止后 {summary(d)} transfers={len(d['transfers'])}")
    log("inst2", f"终止后待办 admin={[t for t in tasks(a) if t['instanceId'] == iid]} user2={[t for t in tasks(b) if t['instanceId'] == iid]}")
    expect_failure("inst2", lambda: a.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", {"decision": "APPROVE"}), 409)


def inst3():
    """默认分支：CARD → fill → gw 默认 → finalApprove → end。"""
    state = load()
    a, b = admin(), user2()
    iid = start(a, state, "I3", "CARD")
    state["instance3"] = iid
    save(state)
    d = detail(a, iid)
    action = active_action(d, "fill")
    a.send("POST", f"/workflow/v1/action-runs/{action['actionRunId']}/submission",
           {"output": {"merchantName": "Default Co", "monthlyVolume": "20"}})
    d = detail(a, iid)
    # admin 是执行人，不能审批 fill；user2 审批
    approval = pending_approval(d, "fill")
    b.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", {"decision": "APPROVE"})
    d = detail(a, iid)
    gw = [r for r in d["nodeRuns"] if r["nodeKey"] == "gw"]
    log("inst3", f"网关 selectedEdgeKey={gw[-1]['selectedEdgeKey'] if gw else None}（期望默认 e4）")
    approval = pending_approval(d, "finalApprove")
    a.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", {"decision": "APPROVE"})
    log("inst3", f"完成后 {summary(detail(a, iid))}")


def verify():
    state = load()
    a = admin()
    for key in ("instance1", "instance2", "instance3"):
        if key not in state:
            continue
        d = detail(a, state[key])
        log("verify", f"{key}={state[key]} {summary(d)}")
        acts = a.get(f"/workflow/v1/instances/{state[key]}/activities")
        items = acts.get("items", acts) if isinstance(acts, dict) else acts
        log("verify", f"{key} 活动记录 {len(items)} 条：" + " | ".join(
            f"{i.get('activityType')}:{i.get('nodeKey') or ''}:{i.get('status') or ''}:{(i.get('detail') or '')[:40]}" for i in items))
    handled = a.get("/workflow/v1/my-tasks/handled?page=1&size=50")
    log("verify", "admin 已处理：" + str([(h["instanceTitle"], h["nodeKey"], h["roundNo"], h["result"]) for h in handled["items"]][:10]))
    lst = a.get("/workflow/v1/instances?scope=ALL&page=1&size=20")
    log("verify", "实例列表：" + str([(i["title"], i["status"]) for i in lst["items"]][:10]))
    ids = ",".join(state[k] for k in ("instance1", "instance2", "instance3") if k in state)
    for statement in (
            f"SELECT instance_id,status,version FROM t_workflow_instance WHERE instance_id IN ({ids})",
            f"SELECT instance_id,node_key,round_no,status FROM t_workflow_node_run WHERE instance_id IN ({ids}) ORDER BY instance_id,node_run_id",
            f"SELECT a.instance_id,a.status,count(*) FROM t_workflow_approval a WHERE a.instance_id IN ({ids}) GROUP BY 1,2",
            f"SELECT callback_id,instance_id,`trigger_code`,callback_type,target,status,attempt_count,left(last_error,160) FROM t_workflow_callback WHERE instance_id IN ({ids})",
            "SELECT event_type,status,count(*) FROM compensation_event WHERE create_time > UTC_TIMESTAMP() - INTERVAL 2 HOUR GROUP BY 1,2"):
        try:
            rows = db(statement)
        except RuntimeError as failure:
            rows = [["ERR", str(failure)]]
        log("db", statement[:90] + " => " + json.dumps(rows, ensure_ascii=False))



# ---------------------------------------------------------------- 并行与驳回（追加）

def par():
    """并行分叉 / 汇合与 B5 驳回：分支首节点不能驳回、分支内驳回回上一节点、汇合后驳回需指定分支、纯审批节点不能退回修改。"""
    state = load()
    a, b = admin(), user2()
    ad, u2id, stamp = state["adminId"], state["user2Id"], state["stamp"]
    pos = lambda x, y: {"x": x, "y": y}
    appr = lambda: {"mode": "ANY", "approverIds": [u2id]}
    g = {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(300, 0)},
        {"key": "a", "type": "TASK", "name": "A 立项资料", "position": pos(300, 100),
         "actions": [{"key": "aForm", "templateId": state["formTemplateId"], "required": True, "executorId": ad}]},
        {"key": "split", "type": "PARALLEL_SPLIT", "name": "分叉", "position": pos(300, 200)},
        {"key": "b1", "type": "TASK", "name": "B1 风控确认", "position": pos(150, 300), "approval": appr(),
         "actions": [{"key": "b1Confirm", "templateId": state["confirmTemplateId"], "required": True, "executorId": ad}]},
        {"key": "b1x", "type": "TASK", "name": "B1X 风控复核", "position": pos(150, 400), "approval": appr()},
        {"key": "b2", "type": "TASK", "name": "B2 商务审批", "position": pos(450, 350), "approval": appr()},
        {"key": "join", "type": "PARALLEL_JOIN", "name": "汇合", "position": pos(300, 500)},
        {"key": "c", "type": "TASK", "name": "C 终审", "position": pos(300, 600), "approval": appr()},
        {"key": "end", "type": "END", "name": "结束", "position": pos(300, 700)}],
        "edges": [{"key": "p1", "from": "start", "to": "a"}, {"key": "p2", "from": "a", "to": "split"},
                  {"key": "p3", "from": "split", "to": "b1"}, {"key": "p4", "from": "b1", "to": "b1x"},
                  {"key": "p5", "from": "b1x", "to": "join"}, {"key": "p6", "from": "split", "to": "b2"},
                  {"key": "p7", "from": "b2", "to": "join"}, {"key": "p8", "from": "join", "to": "c"},
                  {"key": "p9", "from": "c", "to": "end"}]}
    created = a.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"WF_E2E_PAR_{stamp}", "definitionName": f"E2E 并行 {stamp}"})
    did = created["objectId"]
    d = a.get(f"/workflow-admin/v1/definitions/{did}")
    draft = a.send("PUT", f"/workflow-admin/v1/definitions/{did}/draft", {"definitionName": f"E2E 并行 {stamp}", "graph": g,
                                                                          "startForm": START_FORM, "expectedVersion": d["definition"]["version"]})
    log("par", f"草稿 problems={draft.get('problems')}")
    d = a.get(f"/workflow-admin/v1/definitions/{did}")
    a.send("POST", f"/workflow-admin/v1/definitions/{did}/publication", {"expectedVersion": d["definition"]["version"]})
    state["parDefinitionId"] = did
    res = a.send("POST", "/workflow/v1/instances", {"definitionId": did, "versionNo": 1, "title": f"E2E PAR {stamp}",
                                                     "startValues": {"channelCode": "E2E-PAR", "paymentMethod": "CARD"}})
    iid = res["objectId"]
    state["parInstance"] = iid
    save(state)
    dd = lambda: detail(a, iid)
    act = lambda node: active_action(dd(), node)
    pend = lambda node: pending_approval(dd(), node)
    decide = lambda op, node, decision, **kw: op.send("POST", f"/workflow/v1/approvals/{pend(node)['approvalId']}/decision",
                                                     {"decision": decision, "comment": f"E2E {decision}", **kw})
    a.send("POST", f"/workflow/v1/action-runs/{act('a')['actionRunId']}/submission", {"output": {"merchantName": "Par", "monthlyVolume": "1"}})
    log("par", f"A 通过后（期望 b1 ACTIVE 与 b2 APPROVING 同时激活）{summary(dd())['nodes']}")
    expect_failure("par", lambda: decide(b, "b2", "REJECT"), 400)          # 分支首节点不能驳回
    expect_failure("par", lambda: decide(b, "b2", "RETURN"), 400)          # 纯审批节点不能退回修改
    a.send("POST", f"/workflow/v1/action-runs/{act('b1')['actionRunId']}/submission", {"output": {"note": "风控已确认"}})
    expect_failure("par", lambda: decide(b, "b1", "REJECT"), 400)          # 分支首节点（有动作）不能驳回
    decide(b, "b1", "APPROVE")
    decide(b, "b1x", "REJECT")                                              # 分支内第 2 个节点驳回 → 回到 b1
    log("par", f"B1X 驳回后 {summary(dd())['nodes']}")
    a.send("POST", f"/workflow/v1/action-runs/{act('b1')['actionRunId']}/submission", {"output": {"note": "重新确认"}})
    decide(b, "b1", "APPROVE")
    decide(b, "b1x", "APPROVE")
    log("par", f"分支 1 完成、b2 未完成（期望 join WAITING，c 未激活）{summary(dd())['nodes']}")
    decide(b, "b2", "APPROVE")
    log("par", f"b2 同意后（期望 join PASSED，c APPROVING）{summary(dd())['nodes']}")
    expect_failure("par", lambda: decide(b, "c", "REJECT"), 400)           # 汇合后驳回须指定分支
    decide(b, "c", "REJECT", rejectTargetNodeKey="b2")
    log("par", f"C 驳回到 b2 后（期望 b2 第 2 轮、join WAITING、分支 1 保持）{summary(dd())['nodes']}")
    decide(b, "b2", "APPROVE")
    decide(b, "c", "APPROVE")
    log("par", f"最终 {summary(dd())}")


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "startx":
        st = load(); iid = start(admin(), st, sys.argv[2], sys.argv[3]); st.setdefault("extra", []).append(iid); save(st)
    elif command == "a1":
        a1(sys.argv[2])
    elif command == "u2":
        u2(sys.argv[2])
    else:
        globals()[command]()

