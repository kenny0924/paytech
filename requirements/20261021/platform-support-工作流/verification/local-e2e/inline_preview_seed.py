"""附件内嵌预览 e2e 造数：FILE_UPLOAD（PDF / PNG）模板 + 流程（上传节点 user2 审批），发起实例并由 admin 上传真实 PDF 与 PNG 后提交，
使 user2 的审批页“文件预览”出现两份文件。状态写暂存区 workflow-inline-state.json（只含 ID）。用法：python3 inline_preview_seed.py
"""
import json
import struct
import time
import zlib

import file_flow as f
import workflow_flow as w

STATE = w.SCRATCH / "workflow-inline-state.json"


def png(name, width=240, height=120):
    """生成 RGB PNG：左蓝右橙两色块，便于截图辨认。"""
    rows = b"".join(b"\x00" + b"".join(bytes((30, 90, 200)) if x < width // 2 else bytes((240, 140, 30)) for x in range(width))
                    for _ in range(height))
    chunk = lambda kind, data: struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)  # noqa: E731
    body = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")
    path = f.PDF_DIR / name
    path.write_bytes(body)
    return path


def main():
    base = w.load()
    a = w.admin()
    stamp = time.strftime("%H%M%S")
    s = {"stamp": stamp}
    tpl = a.send("POST", "/workflow-admin/v1/action-templates", {
        "templateCode": f"WF_E2E_INLINE_{stamp}", "templateName": f"E2E 内嵌预览上传 {stamp}", "actionType": "FILE_UPLOAD",
        "config": {"minFiles": 1, "maxFiles": 3, "mimeTypes": ["application/pdf", "image/png"], "instruction": "上传 PDF 与 PNG"}})
    s["templateId"] = tpl["objectId"]
    pos = lambda x, y: {"x": x, "y": y}  # noqa: E731
    graph = {"nodes": [
        {"key": "start", "type": "START", "name": "开始", "position": pos(250, 0)},
        {"key": "docs", "type": "TASK", "name": "上传待审文件", "position": pos(250, 120),
         "approval": {"mode": "ANY", "approverIds": [base["user2Id"]]},
         "actions": [{"key": "inlineUpload", "templateId": s["templateId"], "required": True, "executorId": base["adminId"]}]},
        {"key": "end", "type": "END", "name": "结束", "position": pos(250, 240)}],
        "edges": [{"key": "e1", "from": "start", "to": "docs"}, {"key": "e2", "from": "docs", "to": "end"}]}
    start_form = {"fields": [{"key": "note", "label": "说明", "type": "TEXT", "required": True, "rules": {"maxLength": 64}}]}
    d = a.send("POST", "/workflow-admin/v1/definitions", {"definitionCode": f"WF_E2E_INLINE_{stamp}", "definitionName": f"E2E 内嵌预览 {stamp}"})
    s["definitionId"] = d["objectId"]
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    draft = a.send("PUT", f"/workflow-admin/v1/definitions/{s['definitionId']}/draft", {
        "definitionName": f"E2E 内嵌预览 {stamp}", "graph": graph, "startForm": start_form, "expectedVersion": det["definition"]["version"]})
    w.log("seed", f"草稿 problems={draft.get('problems')}")
    det = a.get(f"/workflow-admin/v1/definitions/{s['definitionId']}")
    a.send("POST", f"/workflow-admin/v1/definitions/{s['definitionId']}/publication", {"expectedVersion": det["definition"]["version"]})
    s["title"] = f"E2E INLINE {stamp}"
    s["instanceId"] = a.send("POST", "/workflow/v1/instances", {"definitionId": s["definitionId"], "versionNo": 1, "title": s["title"],
                                                                "businessKey": f"INLINE:{stamp}", "startValues": {"note": "内嵌预览验收"}})["objectId"]
    pdf_id = f.upload(a, f.pdf(f"inline-{stamp}.pdf", f"Inline preview {stamp}"))
    png_id = f.upload(a, png(f"inline-{stamp}.png"), mime="image/png")
    for fid in (pdf_id, png_id):
        f.wait_available(fid)
    s.update(pdfFileId=pdf_id, pngFileId=png_id, pdfName=f"inline-{stamp}.pdf", pngName=f"inline-{stamp}.png")
    run = next(x for x in w.detail(a, s["instanceId"])["actionRuns"] if x["actionKey"] == "inlineUpload")
    sub = a.send("POST", f"/workflow/v1/action-runs/{run['actionRunId']}/submission", {"output": {"files": [f.ADDR(pdf_id), f.ADDR(png_id)]}})
    w.log("seed", f"提交 status={sub.get('status')} reason={sub.get('failureReason')}")
    STATE.write_text(json.dumps(s, ensure_ascii=False, indent=1))
    w.log("seed", json.dumps(s, ensure_ascii=False))


if __name__ == "__main__":
    main()
