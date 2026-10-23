"""工作流体验优化阶段 5 本机 e2e：造数与 API 校验（Nginx 18444 → Gateway → ops-bff → Platform，Bearer 调用）。

子命令（状态写暂存区 workflow-v2-state.json，只含 ID；口令不打印）：
  setup         建 3 个动作模板（表单 / 上传 / 人工确认）与流程 D1（资料准备：3 个动作 + user2 审批 → 复核：admin 纯审批），
                启动表单含 FILE 字段；发起 I1（浏览器主流程）、I2（API 校验）、I3（浏览器未通过文件）、B1—B3（批量）、
                R1（驳回）、T1（转交）、W1（撤回）；B/R/T 由 API 推进到 admin 的「复核」审批。
  apichecks     I2：必填缺失 422（字段路径）、扫描中文件可完成、未通过扫描文件 422（文件路径）、APPROVING 后改必选 409、
                可选动作 APPROVING 后仍可完成、扫描未通过文件发起 422（startValues 路径）。
  rejectedfile  上传一个内容与声明类型不符的 PDF（格式检查 REJECTED），打印 fileId（供浏览器在 I3 选用时对照）。
  show <key>    打印实例摘要（节点、动作、审批状态）。
  u2approve <key> / u2return <key>   user2 对实例「资料准备」审批同意 / 退回修改。
用法：python3 seed_v2.py <子命令>
"""
import json
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
OLD = HERE.parents[3] / "20261021/platform-support-工作流/verification/local-e2e"
sys.path.insert(0, str(OLD))
import file_flow as f  # noqa: E402
import workflow_flow as w  # noqa: E402
from e2e_http import Failure  # noqa: E402

STATE = w.SCRATCH / "workflow-v2-state.json"


def load():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save(s):
    STATE.write_text(json.dumps(s, ensure_ascii=False, indent=1))


log = w.log
pos = lambda x, y: {"x": x, "y": y}  # noqa: E731

FORM = {"fields": [
    {"key": "merchantName", "label": "商户名称", "type": "TEXT", "required": True},
    {"key": "monthlyVolume", "label": "月交易量", "type": "NUMBER", "required": True},
    {"key": "region", "label": "区域", "type": "ENUM", "required": False,
     "rules": {"options": [{"value": "GCC", "label": "海湾"}, {"value": "EU", "label": "欧洲"}]}},
]}
START_FORM = {"fields": [
    {"key": "channelCode", "label": "渠道编码", "type": "TEXT", "required": True},
    {"key": "contract", "label": "合同附件", "type": "FILE", "required": False},
]}


def graph(s, admin_id, user2_id):
    return {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(0, 120)},
        {"key": "fill", "type": "TASK", "name": "资料准备", "position": pos(220, 100),
         "approval": {"mode": "ANY", "approverIds": [user2_id]},
         "actions": [{"key": "merchantInfo", "templateId": s["formTemplateId"], "required": True, "executorId": admin_id},
                     {"key": "licenseFiles", "templateId": s["uploadTemplateId"], "required": True, "executorId": admin_id},
                     {"key": "complianceNote", "templateId": s["confirmTemplateId"], "required": False, "executorId": admin_id}],
         "notifications": [{"trigger": "RETURNED_FOR_REVISION", "type": "NOTIFY", "recipients": ["NODE_EXECUTORS"], "channels": ["INBOX"]}]},
        {"key": "review", "type": "TASK", "name": "复核", "position": pos(500, 100),
         "approval": {"mode": "ANY", "approverIds": [admin_id]}},
        {"key": "end", "type": "END", "name": "结束", "position": pos(760, 120)}],
        "edges": [{"key": "e1", "from": "start", "to": "fill"}, {"key": "e2", "from": "fill", "to": "review"},
                  {"key": "e3", "from": "review", "to": "end"}]}


def instance(op, s, tag, start_values=None):
    body = {"definitionId": s["definitionId"], "versionNo": s["versionNo"], "title": f"V2 {tag} {s['stamp']}",
            "businessKey": f"V2:{tag}:{s['stamp']}", "startValues": start_values or {"channelCode": f"CH-{tag}"}}
    oid = op.send("POST", "/workflow/v1/instances", body)["objectId"]
    log("start", f"{tag} -> {oid}")
    return oid


def runs_of(d, node_key):
    nr = {r["nodeRunId"]: r for r in d["nodeRuns"]}
    return [a for a in d["actionRuns"] if nr[a["nodeRunId"]]["nodeKey"] == node_key]


def latest_runs(d, node_key):
    runs = runs_of(d, node_key)
    last = max((r["roundNo"] for r in d["nodeRuns"] if r["nodeKey"] == node_key), default=1)
    ids = {r["nodeRunId"] for r in d["nodeRuns"] if r["nodeKey"] == node_key and r["roundNo"] == last}
    return {a["actionKey"]: a for a in runs if a["nodeRunId"] in ids}


def complete(op, run_id, output):
    return op.send("POST", f"/workflow/v1/action-runs/{run_id}/submission", {"output": output})


def drive_fill(a, s, iid, pdf_id):
    runs = latest_runs(w.detail(a, iid), "fill")
    complete(a, runs["merchantInfo"]["actionRunId"], {"merchantName": f"商户 {iid[-4:]}", "monthlyVolume": "120000", "region": "GCC"})
    complete(a, runs["licenseFiles"]["actionRunId"], {"files": [f.ADDR(pdf_id)]})


def u2_decide(iid, node_key, decision, comment=None):
    u = w.user2()
    approval = w.pending_approval(w.detail(u, iid), node_key)
    body = {"decision": decision, **({"comment": comment} if comment else {})}
    res = u.send("POST", f"/workflow/v1/approvals/{approval['approvalId']}/decision", body)
    log("u2", f"{decision} {node_key}@{iid} -> {res}")


def setup():
    a = w.admin()
    base = w.load()
    admin_id, user2_id = base["adminId"], base["user2Id"]
    stamp = time.strftime("%H%M%S")
    s = {"stamp": stamp, "adminId": admin_id, "user2Id": user2_id}
    s["formTemplateId"] = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"V2_FORM_{stamp}", "templateName": f"V2 商户资料 {stamp}", "actionType": "FORM",
        "description": "体验优化 e2e", "config": FORM})["objectId"]
    s["uploadTemplateId"] = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"V2_UPLOAD_{stamp}", "templateName": f"V2 资质文件 {stamp}", "actionType": "FILE_UPLOAD",
        "config": {"required": True, "instruction": "上传营业执照 PDF"}})["objectId"]
    s["confirmTemplateId"] = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"V2_CONFIRM_{stamp}", "templateName": f"V2 合规确认 {stamp}", "actionType": "MANUAL_CONFIRM",
        "config": {"instruction": "确认商户不在制裁名单\n第二行说明"}})["objectId"]
    log("template", f"form={s['formTemplateId']} upload={s['uploadTemplateId']} confirm={s['confirmTemplateId']}")
    d = a.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"V2_MULTI_{stamp}", "definitionName": f"V2 多动作 {stamp}",
                                                         "description": "体验优化 e2e"})
    s["definitionId"] = d["objectId"]
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    draft = a.send("PUT", f"/workflow-admin/v1/definitions/{s['definitionId']}/draft", {
        "definitionName": f"V2 多动作 {stamp}", "description": "体验优化 e2e", "graph": graph(s, admin_id, user2_id),
        "startForm": START_FORM, "expectedVersion": det["definition"]["version"]})
    log("definition", f"草稿 problems={draft.get('problems')}")
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    a.send("POST", f"/workflow-admin/v1/definitions/{s['definitionId']}/publication", {"expectedVersion": det["definition"]["version"]})
    s["versionNo"] = 1
    pdf_id = f.upload(a, f.pdf(f"v2-{stamp}.pdf", f"V2 license {stamp}"))
    f.wait_available(pdf_id)
    s["pdfFileId"] = pdf_id
    for tag in ("I1", "I2", "I3", "B1", "B2", "B3", "R1", "T1", "W1"):
        s[tag] = instance(a, s, tag)
    save(s)
    drive_all(a, s)


def drive_all(a, s):
    pdf_id, stamp = s["pdfFileId"], s["stamp"]
    for tag in ("B1", "B2", "B3", "R1", "T1"):
        # 每个实例的文件只能用于本人在本实例的提交：各自上传
        fid = pdf_id if tag == "B1" else f.upload(a, f.pdf(f"v2-{tag}-{stamp}.pdf", f"V2 {tag} {stamp}"))
        if fid != pdf_id:
            f.wait_available(fid)
        drive_fill(a, s, s[tag], fid)
        u2_decide(s[tag], "fill", "APPROVE", "资料无误（API 推进）")
    save(s)
    log("setup", json.dumps(s, ensure_ascii=False))


def upload_rejected(op, stamp):
    """内容不是 PDF 却声明 application/pdf：格式检查判定 REJECTED（未通过安全检查）。"""
    path = f.PDF_DIR / f"v2-bad-{stamp}.pdf"
    path.write_bytes(b"this is plain text, not a pdf document\n" * 4)
    fid = f.upload(op, path)
    for _ in range(60):
        st = f.scan_status(fid)
        if st and st[0] in ("AVAILABLE", "REJECTED", "QUARANTINED"):
            break
        time.sleep(1)
    log("rejected", f"fileId={fid} scan={f.scan_status(fid)}")
    return fid


def expect(tag, fn, status):
    try:
        fn()
    except Failure as failure:
        log(tag, f"HTTP {failure.status}（期望 {status}）：{failure.body[:500]}")
        return failure.status == status, failure.body
    log(tag, f"成功（期望 {status}）")
    return False, ""


def apichecks():
    s = load()
    a = w.admin()
    iid = s["I2"]
    runs = latest_runs(w.detail(a, iid), "fill")
    form, upload, confirm = runs["merchantInfo"], runs["licenseFiles"], runs["complianceNote"]
    results = {}
    # 1 必填缺失 → 422，字段路径 output.monthlyVolume
    ok, body = expect("422-required", lambda: complete(a, form["actionRunId"], {"merchantName": "缺月交易量"}), 422)
    results["required422"] = ok and "monthlyVolume" in body
    # 2 上传动作空文件 → 422（至少一个）
    ok, body = expect("422-upload-empty", lambda: complete(a, upload["actionRunId"], {"files": []}), 422)
    results["uploadEmpty422"] = ok
    # 1b 显式 null：BFF 只回传请求体里存在的路径，缺键时 422 不带字段路径
    ok, body = expect("422-required-null", lambda: complete(a, form["actionRunId"], {"merchantName": "缺月交易量", "monthlyVolume": None}), 422)
    results["required422WithPath"] = ok and "output.monthlyVolume" in body
    # 3 未通过扫描的文件：本机只有 PLATFORM_FORMAT 检查器，完成上传时已按同一规则拒绝类型不符的内容（FILE_POLICY_REJECTED），
    #   API 无法造出 REJECTED / QUARANTINED 文件；由 Platform 自动化测试与浏览器拦截演示覆盖
    try:
        upload_rejected(a, s["stamp"] + "a")
        results["scanFailedSeed"] = "unexpected"
    except Failure as failure:
        results["scanFailedSeed"] = f"upload completion {failure.status} {failure.body[:80]}"
    st = w.detail(a, iid)
    results["stateUnchanged"] = latest_runs(st, "fill")["licenseFiles"]["status"] == "PENDING"
    # 4 扫描中文件可完成：上传后立刻完成（记录完成时的扫描状态）
    fid = f.upload(a, f.pdf(f"v2-scan-{s['stamp']}.pdf", "scanning"))
    scan_at_submit = f.scan_status(fid)
    res = complete(a, upload["actionRunId"], {"files": [f.ADDR(fid)]})
    log("scanning", f"完成时扫描状态={scan_at_submit} -> {res}")
    results["scanningCompleted"] = res.get("status") == "COMPLETED"
    results["scanStateAtSubmit"] = scan_at_submit
    # 5 表单完成 → 节点进入 APPROVING
    res = complete(a, form["actionRunId"], {"merchantName": "API 商户", "monthlyVolume": "5000"})
    log("form", f"完成 -> {res}")
    st = w.detail(a, iid)
    node = [r for r in st["nodeRuns"] if r["nodeKey"] == "fill"][-1]
    results["nodeApproving"] = node["status"]
    # 6 APPROVING 后修改必选 → 409
    ok, body = expect("409-locked", lambda: complete(a, form["actionRunId"], {"merchantName": "改", "monthlyVolume": "1"}), 409)
    results["lockedRequired409"] = ok
    # 7 可选动作在放行前仍可完成
    res = complete(a, confirm["actionRunId"], {"note": "APPROVING 中完成可选动作"})
    results["optionalWhileApproving"] = res.get("status")
    # 9 发起：扫描中的文件可发起
    fid2 = f.upload(a, f.pdf(f"v2-start-{s['stamp']}.pdf", "start scanning"))
    scan2 = f.scan_status(fid2)
    oid = a.send("POST", "/workflow/v1/instances", {"definitionId": s["definitionId"], "versionNo": 1, "title": f"V2 start-scan {s['stamp']}",
                                                    "startValues": {"channelCode": "SCAN", "contract": [f.ADDR(fid2)]}})["objectId"]
    results["startWhileScanning"] = {"scan": scan2, "instance": oid}
    s["S1"] = oid
    s["apichecks"] = results
    save(s)
    log("apichecks", json.dumps(results, ensure_ascii=False))


def show(key):
    s = load()
    a = w.admin()
    log("show", json.dumps(w.summary(w.detail(a, s.get(key, key))), ensure_ascii=False))


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "setup":
        setup()
    elif cmd == "driveall":
        drive_all(w.admin(), load())
    elif cmd == "apichecks":
        apichecks()
    elif cmd == "rejectedfile":
        s = load()
        s["rejectedFileId2"] = upload_rejected(w.admin(), s["stamp"] + "b")
        save(s)
    elif cmd == "show":
        show(sys.argv[2])
    elif cmd in ("u2approve", "u2return"):
        s = load()
        u2_decide(s[sys.argv[2]], "fill", "APPROVE" if cmd == "u2approve" else "RETURN",
                  "资料无误" if cmd == "u2approve" else "请补充区域（e2e 退回修改）")
