"""工作流带文件验收（API 链路）：建模板与流程、上传、扫描、发起、FILE_UPLOAD 提交与重新验证、下载与拒绝情形、数据库只读核对。
子命令：setup / launch / upload <who> <name> / reject / verify。状态写暂存区 workflow-file-state.json（只含 ID）。
"""
import json
import ssl
import sys
import time
import urllib.request
from pathlib import Path

import workflow_flow as w

STATE = w.SCRATCH / "workflow-file-state.json"
PDF_DIR = w.SCRATCH / "wf-pdf"
PDF_DIR.mkdir(exist_ok=True)


def load():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save(s):
    STATE.write_text(json.dumps(s, ensure_ascii=False, indent=1))


def pdf(name, text):
    """生成一页最小 PDF。"""
    content = f"BT /F1 18 Tf 72 720 Td ({text}) Tj ET".encode()
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + o + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    for off in offsets:
        out += b"%010d 00000 n \n" % off
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    path = PDF_DIR / name
    path.write_bytes(bytes(out))
    return path


def upload(op, path, mime="application/pdf"):
    data = Path(path).read_bytes()
    grant = op.send("POST", "/workflow/v1/attachments/uploads", {"filename": Path(path).name, "mimeType": mime, "sizeBytes": str(len(data))})
    auth = grant["authorization"]
    req = urllib.request.Request(auth["url"], data=data, method="PUT", headers=auth.get("headers") or {})
    with urllib.request.urlopen(req, context=ssl._create_unverified_context(), timeout=30) as r:
        put_status = r.status
    done = op.send("POST", f"/workflow/v1/attachments/uploads/{grant['uploadId']}/completion", None)
    w.log("upload", f"{Path(path).name} uploadId={grant['uploadId']} PUT={put_status} fileId={done['fileId']}")
    return done["fileId"]


def scan_status(file_id):
    rows = w.db(f"SELECT scan_status,object_status FROM t_file_object WHERE file_id={int(file_id)}")
    return rows[1] if len(rows) > 1 else None


def wait_available(file_id, timeout=90):
    start = time.time()
    while time.time() - start < timeout:
        st = scan_status(file_id)
        if st and st[0] == "AVAILABLE":
            w.log("scan", f"{file_id} AVAILABLE 用时 {time.time() - start:.1f}s")
            return True
        time.sleep(1)
    w.log("scan", f"{file_id} 超时，当前 {scan_status(file_id)}")
    return False


ADDR = lambda fid: f"/files/v1/{fid}"  # noqa: E731


def setup():
    s = load()
    base = w.load()
    a = w.admin()
    stamp = time.strftime("%H%M%S")
    s.update(stamp=stamp, adminId=base["adminId"], user2Id=base["user2Id"])
    fu = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_UPLOAD_{stamp}", "templateName": f"E2E 上传合同 {stamp}", "actionType": "FILE_UPLOAD",
        "config": {"minFiles": 1, "maxFiles": 3, "mimeTypes": ["application/pdf"], "instruction": "上传签署后的渠道合同 PDF"}})
    s["uploadTemplateId"] = fu["objectId"]
    ff = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_FILEFORM_{stamp}", "templateName": f"E2E 带附件表单 {stamp}", "actionType": "FORM",
        "config": {"fields": [{"key": "summary", "label": "说明", "type": "TEXT", "required": True, "rules": {"maxLength": 64}},
                              {"key": "evidence", "label": "证据文件", "type": "FILE", "required": True,
                               "rules": {"minFiles": 1, "maxFiles": 2, "mimeTypes": ["application/pdf"]}}]}})
    s["fileFormTemplateId"] = ff["objectId"]
    pos = lambda x, y: {"x": x, "y": y}  # noqa: E731
    graph = {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(250, 0)},
        {"key": "docs", "type": "TASK", "name": "上传合同与证据", "position": pos(250, 120),
         "approval": {"mode": "ANY", "approverIds": [s["user2Id"]]},
         "actions": [{"key": "contractUpload", "templateId": s["uploadTemplateId"], "required": True, "executorId": s["adminId"]},
                     {"key": "evidenceForm", "templateId": s["fileFormTemplateId"], "required": True, "executorId": s["adminId"]}]},
        {"key": "end", "type": "END", "name": "结束", "position": pos(250, 240)}],
        "edges": [{"key": "e1", "from": "start", "to": "docs"}, {"key": "e2", "from": "docs", "to": "end"}]}
    start_form = {"fields": [
        {"key": "channelCode", "label": "渠道编码", "type": "TEXT", "required": True, "rules": {"maxLength": 64}},
        {"key": "kyc", "label": "渠道资质文件", "type": "FILE", "required": True, "rules": {"minFiles": 1, "maxFiles": 3, "mimeTypes": ["application/pdf"]}}]}
    d = a.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"WF_E2E_FILE_{stamp}", "definitionName": f"E2E 带文件流程 {stamp}"})
    s["definitionId"] = d["objectId"]
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    draft = a.send("PUT", f"/workflow-admin/v1/definitions/{s['definitionId']}/draft", {
        "definitionName": f"E2E 带文件流程 {stamp}", "graph": graph, "startForm": start_form, "expectedVersion": det["definition"]["version"]})
    w.log("setup", f"草稿 problems={draft.get('problems')}")
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    a.send("POST", f"/workflow-admin/v1/definitions/{s['definitionId']}/publication", {"expectedVersion": det["definition"]["version"]})
    save(s)
    w.log("setup", json.dumps(s, ensure_ascii=False))


def start_body(s, title, files):
    return {"definitionId": s["definitionId"], "versionNo": 1, "title": f"{title} {s['stamp']}", "businessKey": f"FILE:{title}",
            "startValues": {"channelCode": "E2E-FILE", "kyc": [ADDR(f) for f in files]}}


def api_flow():
    """浏览器之外的 API 校验：扫描中发起、FILE_UPLOAD 扫描中提交 → UNVERIFIED → 重新验证、拒绝情形。"""
    s = load()
    a, b = w.admin(), w.user2()
    # 3. 扫描未完成发起：上传后立即发起
    f1 = upload(a, pdf("scan-race.pdf", "scan race"))
    st = scan_status(f1)
    w.log("3", f"完成上传后立即读扫描状态 {st}（格式扫描在完成上传时同步完成，无法构造扫描中）")
    wait_available(f1)
    inst = a.send("POST", "/workflow/v1/instances", start_body(s, "E2E FILE API", [f1]))["objectId"]
    s["apiInstance"] = inst
    s["apiStartFile"] = f1
    save(s)
    d = w.detail(a, inst)
    w.log("2", f"API 实例 {inst} startValues={d['startValues']} files={[(x.get('fileAddress'), x.get('scanStatus')) for x in d.get('files', [])]}")
    # 4. FILE_UPLOAD：先提交不合规的（非 PDF 声明 → 内容问题）看 UNVERIFIED，再重新验证（不改内容，期望仍 UNVERIFIED），最后合规提交
    try:
        fpng = upload(a, pdf("not-really.png", "png"), mime="image/png")
        bad = a.send("POST", f"/workflow/v1/action-runs/{next(x for x in w.detail(a, inst)['actionRuns'] if x['actionKey'] == 'contractUpload')['actionRunId']}/submission",
                     {"output": {"files": [ADDR(fpng)]}})
        w.log("4", f"类型不符提交 -> {bad}")
        if bad["status"] == "UNVERIFIED":
            rv = a.send("POST", f"/workflow/v1/action-runs/{bad['actionRunId']}/verification", None)
            w.log("4", f"类型不符后重新验证 -> {rv}")
    except w.Failure as f:
        w.log("4", f"类型不符路径 HTTP {f.status} {f.body[:200]}")
    f2 = upload(a, pdf("contract-scan.pdf", "contract"))
    st2 = scan_status(f2)
    run = w.active_action(d, "docs") if False else next(x for x in d["actionRuns"] if x["actionKey"] == "contractUpload")
    sub = a.send("POST", f"/workflow/v1/action-runs/{run['actionRunId']}/submission", {"output": {"files": [ADDR(f2)]}})
    w.log("4", f"FILE_UPLOAD 提交（提交时扫描 {st2}）-> {sub}")
    wait_available(f2)
    if sub["status"] == "UNVERIFIED":
        rv = a.send("POST", f"/workflow/v1/action-runs/{run['actionRunId']}/verification", None)
        w.log("4", f"重新验证 -> {rv}")
    # 6. 拒绝情形
    form_run = next(x for x in d["actionRuns"] if x["actionKey"] == "evidenceForm")
    fb = upload(b, pdf("user2-file.pdf", "user2"))
    wait_available(fb)
    s["user2File"] = fb
    save(s)
    for name, fn, exp in [
        ("别人上传的文件提交", lambda: a.send("POST", f"/workflow/v1/action-runs/{form_run['actionRunId']}/submission",
                                         {"output": {"summary": "x", "evidence": [ADDR(fb)]}}), 400),
        ("非工作流附件提交", lambda: a.send("POST", f"/workflow/v1/action-runs/{form_run['actionRunId']}/submission",
                                        {"output": {"summary": "x", "evidence": [ADDR(onboarding_file())]}}), 400),
        ("非法地址提交", lambda: a.send("POST", f"/workflow/v1/action-runs/{form_run['actionRunId']}/submission",
                                     {"output": {"summary": "x", "evidence": ["https://evil/files/v1/1"]}}), 422),
        ("非法地址发起", lambda: a.send("POST", "/workflow/v1/instances", {**start_body(s, "E2E FILE BAD", []),
                                                                         "startValues": {"channelCode": "x", "kyc": ["/files/v1/0123"]}}), 422),
        ("同一文件用于另一个实例", lambda: a.send("POST", "/workflow/v1/instances", start_body(s, "E2E FILE 复用", [f1])), 400),
        ("非参与人下载", lambda: b.send("POST", f"/workflow/v1/instances/{inst}/files/{f1}/download", None), 404),
    ]:
        try:
            fn()
            w.log("6", f"{name}：意外成功（期望 {exp}）")
        except w.Failure as f:
            w.log("6", f"{name}：HTTP {f.status}（期望 {exp}）{f.body[:260]}")
    # 正常提交表单 + 下载
    f3 = upload(a, pdf("evidence.pdf", "evidence"))
    wait_available(f3)
    sub = a.send("POST", f"/workflow/v1/action-runs/{form_run['actionRunId']}/submission", {"output": {"summary": "合同与证据已上传", "evidence": [ADDR(f3)]}})
    w.log("4", f"带文件 FORM 提交 -> {sub}")
    dl = a.send("POST", f"/workflow/v1/instances/{inst}/files/{f1}/download", None)
    with urllib.request.urlopen(dl["url"], context=ssl._create_unverified_context(), timeout=20) as r:
        w.log("5", f"下载 HTTP {r.status} Content-Type={r.headers.get('Content-Type')} Content-Disposition={r.headers.get('Content-Disposition')} bytes={len(r.read())}")
    # user2 是审批人（参与人）：可以下载
    dl2 = b.send("POST", f"/workflow/v1/instances/{inst}/files/{f3}/download", None)
    w.log("5", f"审批人 user2 下载授权 OK expiresAt={dl2.get('expiresAt')}")
    s["apiFiles"] = [f1, f2, f3]
    save(s)
    d = w.detail(a, inst)
    w.log("verify", f"API 实例 {w.summary(d)}")


def onboarding_file():
    rows = w.db("SELECT file_id FROM t_file_object WHERE reason_code<>'WORKFLOW_ATTACHMENT' AND scan_status='AVAILABLE' LIMIT 1")
    return rows[1][0]


def verify():
    s = load()
    ids = ",".join(str(x) for x in (s.get("apiFiles", []) + [s.get("user2File")] + s.get("browserFiles", [])) if x)
    for q in (f"SELECT u.upload_id,u.source_system,u.reason_code,u.uploader_id,u.status,o.file_id,o.scan_status,o.object_status,o.mime_type FROM t_file_upload u JOIN t_file_object o ON o.upload_id=u.upload_id WHERE o.file_id IN ({ids})",
              f"SELECT v.action_run_id,v.result,v.evidence_json FROM t_workflow_verification v JOIN t_workflow_action_run r ON r.action_run_id=v.action_run_id WHERE r.instance_id IN ({','.join(str(x) for x in [s.get('apiInstance'), s.get('browserInstance')] if x)})",
              f"SELECT instance_id,status,start_values_json FROM t_workflow_instance WHERE definition_id={s['definitionId']}"):
        try:
            w.log("db", json.dumps(w.db(q), ensure_ascii=False))
        except RuntimeError as e:
            w.log("db", f"ERR {e}")


if __name__ == "__main__":
    {"setup": setup, "api": api_flow, "verify": verify}[sys.argv[1]]()
