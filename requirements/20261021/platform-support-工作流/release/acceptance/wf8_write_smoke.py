#!/usr/bin/env python3
"""工作流 mor-test 写数据最小冒烟（2026-10-22 用户同意）：经 http://localhost:28080 → Gateway → ops-bff → platform-support。
数据名称前缀 WF-SMOKE-20261022（编码前缀 WF_SMOKE_20261022_）。管理员取 test-environment/private/access.md，口令不打印。
流程：FORM 模板（TEXT + ENUM）→ 定义（启动 TEXT；fill 动作节点执行人 admin、无审批人；approve 纯审批 admin；end；不配通知）→ 发布 v1
→ 发起 → 提交表单 → 审批同意 → COMPLETED → 活动记录。不新建账号、不改权限、不执行 SQL（只读查询 t_workflow_callback）。
用法：python3 wf8_write_smoke.py；ID 写 stdout 与 wf8-state.json（同目录，只含 ID）。
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
from e2e_http import Failure, Operations, secrets  # noqa: E402

P = "WF-SMOKE-20261022"
C = "WF_SMOKE_20261022"
STATE = HERE / "wf8-state.json"


def log(text):
    print(text, flush=True)


def ro_sql(statement):
    env = {}
    for line in (ROOT / "test-environment/.env.test").read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            env["MYSQL_PWD"] = line.split("=", 1)[1].strip().strip('"')
    out = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                          "--default-character-set=utf8mb4", "mor_platform", "-e", statement],
                         env={**os.environ, **env}, capture_output=True, text=True)
    return out.stdout.strip() or out.stderr.strip()[:200]


def main():
    s = secrets()
    op = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    me = op.get("/auth/v1/context")["accountId"]
    state = {"adminId": me, "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    tpl = op.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"{C}_FORM", "templateName": f"{P} 冒烟表单", "actionType": "FORM", "description": "mor-test 写数据冒烟",
        "config": {"fields": [{"key": "summary", "label": "说明", "type": "TEXT", "required": True, "rules": {"maxLength": 64}},
                              {"key": "result", "label": "结论", "type": "ENUM", "required": True,
                               "rules": {"options": [{"value": "OK", "label": "通过"}, {"value": "NG", "label": "不通过"}]}}]}})
    state["templateId"] = tpl["objectId"]
    log(f"模板 {tpl}")
    d = op.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"{C}_FLOW", "definitionName": f"{P} 冒烟流程",
                                                           "description": "mor-test 写数据冒烟"})
    state["definitionId"] = did = d["objectId"]
    pos = lambda x, y: {"x": x, "y": y}  # noqa: E731
    graph = {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(250, 0)},
        {"key": "fill", "type": "TASK", "name": "填写冒烟表单", "position": pos(250, 120),
         "actions": [{"key": "smokeForm", "templateId": state["templateId"], "required": True, "executorId": me}]},
        {"key": "approve", "type": "TASK", "name": "冒烟审批", "position": pos(250, 240), "approval": {"mode": "ANY", "approverIds": [me]}},
        {"key": "end", "type": "END", "name": "结束", "position": pos(250, 360)}],
        "edges": [{"key": "e1", "from": "start", "to": "fill"}, {"key": "e2", "from": "fill", "to": "approve"},
                  {"key": "e3", "from": "approve", "to": "end"}]}
    det = op.get(f"/workflow-admin/v1/definitions/{did}")
    draft = op.send("PUT", f"/workflow-admin/v1/definitions/{did}/draft", {
        "definitionName": f"{P} 冒烟流程", "description": "mor-test 写数据冒烟", "graph": graph,
        "startForm": {"fields": [{"key": "topic", "label": "冒烟主题", "type": "TEXT", "required": True, "rules": {"maxLength": 64}}]},
        "expectedVersion": det["definition"]["version"]})
    log(f"草稿 problems={draft.get('problems')}")
    det = op.get(f"/workflow-admin/v1/definitions/{did}")
    log(f"发布 {op.send('POST', f'/workflow-admin/v1/definitions/{did}/publication', {'expectedVersion': det['definition']['version']})}")
    inst = op.send("POST", "/workflow/v1/instances", {"definitionId": did, "versionNo": 1, "title": f"{P} 冒烟实例",
                                                      "businessKey": f"{P}", "startValues": {"topic": "mor-test 写数据冒烟"}})
    state["instanceId"] = iid = inst["objectId"]
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=1))
    log(f"发起 {inst}")
    tasks = [t for t in op.get("/workflow/v1/my-tasks?page=1&size=50")["items"] if t["instanceId"] == iid]
    log(f"我的待办（本实例）{[(t['taskType'], t['nodeKey']) for t in tasks]}")
    if os.environ.get("STOP_AFTER_START"):
        return
    finish(op, state)


def finish(op, state):
    iid = state["instanceId"]
    d = op.get(f"/workflow/v1/instances/{iid}")
    run = next(a for a in d["actionRuns"] if a["status"] == "PENDING")
    sub = op.send("POST", f"/workflow/v1/action-runs/{run['actionRunId']}/submission", {"output": {"summary": "冒烟提交", "result": "OK"}})
    log(f"提交 {sub}")
    d = op.get(f"/workflow/v1/instances/{iid}")
    ap = next(a for a in d["approvals"] if a["status"] == "PENDING")
    res = op.send("POST", f"/workflow/v1/approvals/{ap['approvalId']}/decision", {"decision": "APPROVE", "comment": "冒烟通过"})
    log(f"审批同意 {res}")
    d = op.get(f"/workflow/v1/instances/{iid}")
    log(f"实例 status={d['instance']['status']} nodes={[(r['nodeKey'], r['status']) for r in d['nodeRuns']]}")
    acts = op.get(f"/workflow/v1/instances/{iid}/activities")["items"]
    log(f"活动 {len(acts)} 条：" + " | ".join(f"{a['activityType']}:{a.get('nodeKey') or ''}:{a.get('status') or ''}" for a in acts))
    log(f"t_workflow_callback（只读）：{ro_sql(f'SELECT callback_id,trigger_code,callback_type,status FROM t_workflow_callback WHERE instance_id={int(iid)}') or '无记录'}")
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "finish":
        s = secrets()
        finish(Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]), json.loads(STATE.read_text()))
    else:
        main()
