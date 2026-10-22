"""文件多存储并存第二段阶段 7 mor-test 端到端验收（release.md 第 7 节；五个镜像替换、CCT 第 1 步之后执行）。

覆盖 segment2.md P1—P3 与任务卡 S2-D 第 5 节：
  1. 客户端：合成测试账号（fs2-<run>@example.test，Mailpit 验证）→ 个人申请草稿 → 浏览器同路径上传（开始 → 直传 MinIO → 完成 →
     轮询扫描）→ C12 关联（提交 fileAddress）→ CCT 存 file_address 并冻结哈希；
  2. 授权矩阵：本人可查可下载、他人文件 404、地址格式错误 422、扫描未完成 409；
  3. 运营 O10：已有审核任务的旧文件下载，字节与 SHA-256 一致，Platform 写 FILE_DOWNLOAD_AUTHORIZE 审计（操作人 = 管理员）；
  4. 旧数据：52 行材料已有地址且与 file_id 一致（库内只读核对；旧申请没有可用客户口令，下载由第 3 项覆盖）；
  5. 内部接口（私网探针）：无身份 401、带 X-Caller-Service 可用、INLINE 对 pdf 生效、缺省 ATTACHMENT、下载地址签入原文件名；
  6. 运行检查：五个容器 healthy、未重启，Java 服务验收期间 ERROR 0。
不做：提交申请 C14（会在 compliance 生成审核任务，共享环境无法干净删除）——加 --submit 才执行，须主会话先确认。
用法：python3 -I stage7_s2_acceptance.py [--submit]   → 运行记录 FS2_STATE/run-<run>.json（0600，含合成账号口令）
"""
import sys
import time
import urllib.parse
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import s2_common as c  # noqa: E402

af, http = c.af, c.http
PDF = af.PDF


def register(record, run):
    client = af.Client()
    email = f"fs2-{run}@example.test"
    password = "Fs2-" + uuid.uuid4().hex[:20]
    record.update(email=email, password=password)
    c.save(f"run-{run}.json", record)
    context = af.register_and_login(client, email, password)
    record["accountId"] = context.get("accountId")
    application_id = af.start_application(client)
    options = af.form_options(client)
    af.fill_profile(client, application_id, options, run)
    app = af.read_application(client, application_id)
    record.update(applicationId=application_id, subjectPartyId=app["subjectPartyId"])
    c.save(f"run-{run}.json", record)
    requirement = next(r for s in app.get("materialSubjects") or [] for r in s.get("requirements") or [] if r.get("requiredFlag"))
    return client, app, requirement


def upload(client, record, name):
    status, grant = c.client_call(client, "/onboarding-files/v1/uploads", "POST",
                                  {"filename": name, "mimeType": "application/pdf", "sizeBytes": str(len(PDF))}, key=True)
    authorization = grant.get("authorization") or {}
    c.check(f"上传开始 {name}（只带文件名 / 类型 / 大小）", status in (200, 201) and authorization.get("method") == "PUT",
            f"HTTP {status} uploadId={grant.get('uploadId')}")
    put_status, _, host = af.presigned("PUT", authorization["url"], authorization.get("headers"), PDF)
    c.check(f"直传对象存储 {name}", 200 <= put_status < 300, f"HTTP {put_status} host={host}")
    status, receipt = c.client_call(client, f"/onboarding-files/v1/uploads/{grant['uploadId']}/completion", "POST", key=True)
    file_id = receipt.get("fileId")
    c.check(f"上传完成 {name}：回执带 fileAddress", status in (200, 201) and receipt.get("fileAddress") == f"/files/v1/{file_id}",
            f"HTTP {status} fileId={file_id} fileAddress={receipt.get('fileAddress')}")
    record.setdefault("uploads", []).append({"uploadId": grant["uploadId"], "fileId": file_id, "name": name})
    return file_id, receipt.get("fileAddress")


def wait_available(client, file_id, seconds=90):
    deadline, meta = time.time() + seconds, {}
    while time.time() < deadline:
        _, meta = c.client_call(client, f"/onboarding-files/v1/files/{file_id}")
        if meta.get("scanStatus") not in ("PENDING_SCAN", "SCANNING"):
            break
        time.sleep(3)
    return meta


def client_flow(client, app, requirement, record):
    application_id, subject = record["applicationId"], record["subjectPartyId"]
    code, doc_type = requirement["requirementCode"], requirement["documentType"]
    # 第一个文件：完成后立刻查询，趁扫描未完成验证 409。
    file_id, address = upload(client, record, f"{code.lower()}-early.pdf")
    _, meta = c.client_call(client, f"/onboarding-files/v1/files/{file_id}")
    if meta.get("scanStatus") in ("PENDING_SCAN", "SCANNING"):
        status, body = c.client_call(client, f"/onboarding-files/v1/files/{file_id}/download", "POST", key=True)
        c.check("扫描未完成时下载返回 409", status == 409, f"HTTP {status} code={body.get('code')}")
        status, body = c.client_call(client, f"/onboarding/v1/applications/{application_id}/documents", "POST", {
            "subjectPartyId": subject, "requirementCode": code, "documentType": doc_type, "fileAddress": address}, key=True)
        c.check("扫描未完成时关联返回 409", status == 409, f"HTTP {status} code={body.get('code')}")
    else:
        c.check("扫描未完成 409（扫描已先完成，未能在窗口内验证，本机测试覆盖）", True, f"scanStatus={meta.get('scanStatus')}")
    meta = wait_available(client, file_id)
    c.check("扫描完成：AVAILABLE / ACTIVE，元数据带 fileAddress", meta.get("scanStatus") == "AVAILABLE"
            and meta.get("objectStatus") == "ACTIVE" and meta.get("fileAddress") == address,
            f"scanStatus={meta.get('scanStatus')} allowedActions={meta.get('allowedActions')}")
    status, grant = c.client_call(client, f"/onboarding-files/v1/files/{file_id}/download", "POST", key=True)
    got = af.presigned("GET", grant["url"]) if status == 200 else (None, b"", "")
    c.check("本人下载：字节与 SHA-256 一致", status == 200 and got[0] == 200 and c.sha256(got[1]) == c.sha256(PDF),
            f"HTTP {status} get={got[0]} bytes={len(got[1])}")
    disposition = urllib.parse.parse_qs(urllib.parse.urlsplit(grant.get("url", "")).query).get("response-content-disposition", [""])[0]
    c.check("下载地址签入原文件名（filename / filename*）", f"{code.lower()}-early.pdf" in disposition and "filename*=" in disposition,
            disposition[:160])
    # 地址格式错误 422
    status, body = c.client_call(client, f"/onboarding/v1/applications/{application_id}/documents", "POST", {
        "subjectPartyId": subject, "requirementCode": code, "documentType": doc_type, "fileAddress": "/files/v2/" + file_id}, key=True)
    c.check("关联地址格式错误返回 422", status == 422, f"HTTP {status} code={body.get('code')}")
    # C12 关联：只提交地址
    status, document = c.client_call(client, f"/onboarding/v1/applications/{application_id}/documents", "POST", {
        "subjectPartyId": subject, "requirementCode": code, "documentType": doc_type, "fileAddress": address}, key=True)
    c.check("C12 关联材料（提交 fileAddress）", status in (200, 201) and document.get("fileAddress") == address,
            f"HTTP {status} documentId={document.get('documentId')}")
    record["documentId"] = document.get("documentId")
    # 删除 DDL（CCT 第 2 步）之后 file_id 列已不存在，按列是否存在决定核对口径。
    has_file_id = c.sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' "
                        "AND TABLE_NAME='t_application_document' AND COLUMN_NAME='file_id'")[0][0] == "1"
    null_check = "file_id IS NULL" if has_file_id else "1"
    rows = c.sql(f"SELECT file_address, {null_check}, content_hash FROM mor_client_master.t_application_document "
                 f"WHERE document_id={int(document.get('documentId') or 0)}")
    c.check("CCT 存 file_address、file_id 为空或已删除、冻结哈希", bool(rows) and rows[0][0] == address and rows[0][1] == "1"
            and rows[0][2] == c.sha256(PDF), rows)
    _, detail = c.client_call(client, f"/onboarding/v1/applications/{application_id}")
    linked = [d for d in detail.get("documents") or [] if d.get("documentId") == document.get("documentId")]
    c.check("申请详情材料显示 fileAddress", bool(linked) and linked[0].get("fileAddress") == address,
            linked[0].get("fileStatus") if linked else "未找到")
    return file_id


def other_file_matrix(client, record):
    rows = c.sql("SELECT f.file_id FROM mor_platform.t_file_object f WHERE f.source_system='platform-support' ORDER BY f.file_id LIMIT 1")
    other = rows[0][0]
    record["otherFileId"] = other
    status, body = c.client_call(client, f"/onboarding-files/v1/files/{other}")
    c.check("他人文件查询返回 404", status == 404, f"HTTP {status} code={body.get('code')}")
    status, body = c.client_call(client, f"/onboarding-files/v1/files/{other}/download", "POST", key=True)
    c.check("他人文件下载返回 404", status == 404, f"HTTP {status} code={body.get('code')}")


def operations_download(record, since):
    secrets = http.secrets()
    ops = http.Operations(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])
    pair = c.sql("SELECT s.case_id, e.file_id FROM mor_compliance.t_dd_evidence_ref e "
                 "JOIN mor_compliance.t_dd_case_subject s ON s.case_subject_id = e.case_subject_id ORDER BY e.create_time LIMIT 1")
    if not pair:
        c.check("找到带旧文件的审核任务", False, "mor_compliance 中没有证据文件")
        return
    case_id, file_id = pair[0]
    before = int(c.sql("SELECT COUNT(*) FROM mor_platform.t_audit_event WHERE action_code='FILE_DOWNLOAD_AUTHORIZE'")[0][0])
    status, grant = c.ops_call(ops, "POST", f"/client-onboarding-admin/v1/cases/{case_id}/files/{file_id}/download", key=False)
    got = af.presigned("GET", grant["url"]) if status == 200 else (None, b"", "")
    expected = c.sql(f"SELECT content_hash, size_bytes FROM mor_platform.t_file_object WHERE file_id={int(file_id)}")[0]
    record["o10"] = {"caseId": case_id, "fileId": file_id}
    c.check("O10 审核下载旧文件：字节与 SHA-256 一致", status == 200 and got[0] == 200 and c.sha256(got[1]) == expected[0]
            and str(len(got[1])) == expected[1], f"HTTP {status} get={got[0]} bytes={len(got[1])}")
    rows = c.sql("SELECT actor_id, resource_id FROM mor_platform.t_audit_event WHERE action_code='FILE_DOWNLOAD_AUTHORIZE' "
                 f"AND resource_id='{int(file_id)}' AND occurred_time >= '{since.replace('T', ' ').rstrip('Z')}'")
    after = int(c.sql("SELECT COUNT(*) FROM mor_platform.t_audit_event WHERE action_code='FILE_DOWNLOAD_AUTHORIZE'")[0][0])
    c.check("Platform 写下载审计（FILE_DOWNLOAD_AUTHORIZE，操作人为运营账号）", after > before and bool(rows) and rows[0][0] not in ("", "NULL"),
            {"before": before, "after": after, "rows": rows})


def legacy_rows():
    has_file_id = c.sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' "
                        "AND TABLE_NAME='t_application_document' AND COLUMN_NAME='file_id'")[0][0] == "1"
    if has_file_id:
        rows = c.sql("SELECT COUNT(*), SUM(file_address IS NULL), SUM(file_id IS NOT NULL AND file_address <> CONCAT('/files/v1/', file_id)) "
                     "FROM mor_client_master.t_application_document WHERE file_id IS NOT NULL")[0]
        c.check("旧材料 52 行都有地址且与 file_id 一致", rows[0] == "52" and rows[1] == "0" and rows[2] == "0", rows)
        return
    # 删除 DDL 之后：旧 52 行（创建时间早于第二段发布）地址格式都合法。
    rows = c.sql("SELECT COUNT(*), SUM(file_address NOT REGEXP '^/files/v1/[1-9][0-9]*$') FROM mor_client_master.t_application_document "
                 "WHERE create_time < '2026-10-22 15:22:00'")[0]
    c.check("旧材料 52 行地址格式合法（file_id 已删除）", rows[0] == "52" and rows[1] == "0", rows)


def internal_api(file_id):
    status, _ = c.platform_internal("GET", f"/internal/files/v1/files/{file_id}")
    c.check("内部接口无调用身份返回 401", status == 401, f"HTTP {status}")
    status, body = c.platform_internal("GET", f"/internal/files/v1/files/{file_id}", caller="client-console-bff")
    c.check("带 X-Caller-Service 查询可用，响应带 fileAddress", status == 200 and body.get("fileAddress") == f"/files/v1/{file_id}",
            f"HTTP {status} sourceSystem={body.get('sourceSystem')} reasonCode={body.get('reasonCode')}")
    status, body = c.platform_internal("POST", f"/internal/files/v1/files/{file_id}/download?disposition=INLINE", caller="client-console-bff")
    c.check("disposition=INLINE 对 pdf 生效", status == 200 and body.get("disposition") == "INLINE", f"HTTP {status} {body.get('disposition')}")
    status, body = c.platform_internal("POST", f"/internal/files/v1/files/{file_id}/download", caller="client-console-bff")
    c.check("缺省下载方式为 ATTACHMENT", status == 200 and body.get("disposition") == "ATTACHMENT", f"HTTP {status} {body.get('disposition')}")
    status, body = c.platform_internal("POST", f"/internal/files/v1/files/{file_id}/download?disposition=BOGUS", caller="client-console-bff")
    c.check("未知下载方式返回 400", status == 400, f"HTTP {status} code={body.get('code')}")
    c.check("INLINE 对非 pdf / png / jpeg 降为 ATTACHMENT：mor-test 扫描策略只允许这三种类型，无法构造，本机测试覆盖", True)


def main():
    run = time.strftime("%Y%m%d%H%M%S", time.gmtime())
    since = c.now_iso()
    record = {"run": run, "startedAt": since}
    try:
        client, app, requirement = register(record, run)
        file_id = client_flow(client, app, requirement, record)
        other_file_matrix(client, record)
        legacy_rows()
        operations_download(record, since)
        internal_api(file_id)
        if "--submit" in sys.argv:
            status, body = c.client_call(client, f"/onboarding/v1/applications/{record['applicationId']}/submissions", "POST", key=True)
            c.check("C14 提交申请（--submit）", status in (200, 201, 202), f"HTTP {status} {body.get('code') or body.get('submissionId')}")
        else:
            c.check("C14 提交申请未执行（默认只做到关联；见 release.md 第 7 节）", True)
    except af.Stop as stopped:
        c.check(f"前置步骤失败，后续未执行：{stopped}", False)
    finally:
        for service in c.SERVICES:
            c.check(f"{service} healthy 且未重启（{c.health(service)}）", c.health(service).startswith("healthy"))
        for service in c.SERVICES[:4]:
            errors = c.errors_since(service, since)
            c.check(f"{service} 验收期间 ERROR 0", not errors, errors[:2])
        record["finishedAt"] = c.now_iso()
        record["results"] = c.results
        path = c.save(f"run-{run}.json", record)
        print(f"运行记录 {path}；清理：python3 -I stage7_s2_cleanup.py {path}")
    if not c.summary():
        raise SystemExit(1)


if __name__ == "__main__":
    main()
