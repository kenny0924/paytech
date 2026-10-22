"""文件多存储并存阶段 7 mor-test 验收（release.md 第 7 节；登记 stage7_register_storage.py 完成之后执行）。

覆盖 requirement.md AC-01—AC-12 中能在 mor-test 验证的部分；其余由本机 auto 测试覆盖（见 release.md 第 7 节表格）。
写入（验收后由 stage7_cleanup.py 处理）：
  · 合成只读人员与角色各 1（删除）；
  · 实例 MINIO 下登记一个不存在的桶 fs7-missing-<run>（DRAFT，检查不通过，删除该行；审计保留）；
  · 实例 MINIO 用同一账号更换一次密钥（AC-07：新 Token 生效、旧 Token 不再引用；保留，属于正常运维记录）。
读取：一份已有入驻材料经审核下载接口（O10）取得 60 秒地址并实际下载，SHA-256 与登记值一致（AC-04 / AC-10）。
用法：python3 -I stage7_acceptance.py   → 运行记录 FS7_STATE/run-<run>.json（0600，含合成人员口令，不含存储密钥）
      python3 -I stage7_acceptance.py --legacy-only   → 替换前基线：只经旧 Platform 审核下载一份已有材料并核对字节，不写数据
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fs7_common as c  # noqa: E402

REASON = "文件多存储并存阶段 7 mor-test 验收（合成数据验收后清理）"


def walk(node, key_pattern):
    """返回 JSON 中所有键名匹配的字符串 / 数字值（深度优先，保持顺序）。"""
    found = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key_pattern(key) and isinstance(value, (str, int)):
                found.append(str(value))
            found.extend(walk(value, key_pattern))
    elif isinstance(node, list):
        for item in node:
            found.extend(walk(item, key_pattern))
    return found


def registration_state(admin, record):
    status, items, text = c.instances(admin)
    instance = items.get(c.INSTANCE) or {}
    bucket = c.bucket_of(instance, c.BUCKET) or {}
    access_key, secret_key = c.storage_secret()
    token = (c.sql("mor_platform", f"SELECT credential_vault_token FROM t_file_storage_instance WHERE instance_code='{c.INSTANCE}'")
             or [[None]])[0][0]
    record["tokenBefore"] = token
    c.check("AC-01", "存储实例列表：MINIO ACTIVE / STATIC / 密钥已配置，桶 ACTIVE 且检查 OK",
            status == 200 and instance.get("status") == "ACTIVE" and instance.get("credentialMode") == "STATIC"
            and instance.get("credentialConfigured") is True and bucket.get("status") == "ACTIVE" and bucket.get("lastCheckResult") == "OK",
            {"status": status, "instance": instance.get("status"), "bucket": bucket.get("status")})
    c.check("AC-01", "列表响应不含密钥原值与 Vault Token", access_key not in text and secret_key not in text
            and bool(token) and token not in text)
    row = c.sql("mor_platform", f"SELECT * FROM t_file_storage_instance WHERE instance_code='{c.INSTANCE}'")
    flat = "\t".join(row[0]) if row else ""
    c.check("AC-01", "t_file_storage_instance 行不含密钥原值（只有 Vault Token）", bool(flat) and access_key not in flat and secret_key not in flat)
    status, items, _ = c.routes(admin)
    route = items.get(c.DEFAULT_ROUTE) or {}
    c.check("AC-03", "默认路由 * → MINIO / mor-platform-files / CLIENT_ONBOARDING_FORMAT",
            status == 200 and route.get("instanceCode") == c.INSTANCE and route.get("bucketName") == c.BUCKET
            and route.get("scanPolicyCode") == c.SCAN_POLICY, route)
    return instance


def migration_backfill():
    rows = c.sql("mor_platform", "SELECT "
                 "(SELECT COUNT(*) FROM t_file_upload WHERE NOT (source_system='platform-support' AND reason_code='CLIENT_ONBOARDING_MATERIAL')),"
                 "(SELECT COUNT(*) FROM t_file_object WHERE NOT (source_system='platform-support' AND reason_code='CLIENT_ONBOARDING_MATERIAL')),"
                 "(SELECT COUNT(*) FROM t_file_upload), (SELECT COUNT(*) FROM t_file_object)")
    other_uploads, other_files, uploads, files = (int(v) for v in rows[0])
    c.check("AC-10", f"已有记录回填 platform-support / CLIENT_ONBOARDING_MATERIAL（上传 {uploads}、文件 {files}）",
            other_uploads == 0 and other_files == 0, {"otherUploads": other_uploads, "otherFiles": other_files})


def legacy_download(admin, record):
    """AC-04 / AC-10：找一个审核任务中的已有入驻材料，经 O10 下载并核对字节。"""
    status, listing, _ = c.raw(admin, "GET", "/client-onboarding-admin/v1/cases")
    case_ids = walk(listing, lambda k: k == "caseId")
    candidate = None
    for case_id in case_ids[:30]:
        _, detail, _ = c.raw(admin, "GET", f"/client-onboarding-admin/v1/cases/{case_id}")
        for file_id in walk(detail, lambda k: k == "fileId"):
            if c.sql("mor_platform", f"SELECT COUNT(*) FROM t_file_object WHERE file_id={int(file_id)} AND scan_status='AVAILABLE'")[0][0] == "1":
                candidate = (case_id, file_id)
                break
        if candidate:
            break
    if not c.check("AC-10", f"找到带已有入驻材料的审核任务（列表 {status}，任务 {len(case_ids)} 个）", candidate is not None):
        return
    case_id, file_id = candidate
    record["legacyFile"] = {"caseId": case_id, "fileId": file_id}
    # 该路由不登记 idempotency-key 请求头，与 ops-web 一样只声明 JSON 内容类型、不带正文。
    status, body, _ = c.raw(admin, "POST", f"/client-onboarding-admin/v1/cases/{case_id}/files/{file_id}/download",
                            key=False, typed_empty=True)
    c.check("AC-10", f"审核下载签发地址（{status}）", status == 200 and str(body.get("url", "")).startswith(c.ENDPOINT + "/"),
            {"status": status, "code": body.get("code"), "detail": body.get("detail")})
    if status != 200:
        return
    http_status, data = c.fetch_presigned(body["url"])
    expected = c.sql("mor_platform", f"SELECT content_hash, size_bytes, storage_provider, storage_container_ref FROM t_file_object WHERE file_id={int(file_id)}")[0]
    record["legacyFile"].update(httpStatus=http_status, bytes=len(data), recordedProvider=expected[2], recordedContainer=expected[3])
    c.check("AC-04", f"旧文件（记录 {expected[2]} / {expected[3]}）按原坐标下载：HTTP {http_status}、{len(data)} 字节、SHA-256 与登记一致",
            http_status == 200 and c.sha256_hex(data) == expected[0].lower() and str(len(data)) == expected[1],
            {"http": http_status, "size": len(data), "expectedSize": expected[1]})


def bucket_check_rejected(admin, run, record):
    """AC-02：登记一个不存在的桶，检查不通过，启用被拒。"""
    name = f"fs7-missing-{run}"
    record["missingBucket"] = name
    c.save(f"run-{run}.json", record)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets", {"bucketName": name, "reason": REASON})
    c.check("AC-02", f"登记不存在的桶 {name}（{status}）", status in (200, 201) and body.get("status") == "DRAFT", body)
    status, check, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{name}/check", typed_empty=True)
    record["missingBucketCheck"] = check.get("result")
    c.check("AC-02", f"检查结果不是 OK（{status} {check.get('result')}）",
            status == 200 and check.get("result") in ("NOT_FOUND", "ACCESS_DENIED"), check)
    status, body, _ = c.raw(admin, "PATCH", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{name}/status",
                            {"status": "ACTIVE", "expectedVersion": check.get("version") or 1, "reason": REASON})
    c.check("AC-02", f"检查未通过的桶不能启用（{status}）", c.rejected(status, body, 400, "检查通过"), body)


def replace_credential(admin, record, instance):
    """AC-07：用同一账号更换一次密钥，Token 变化，旧 Token 不再被引用，之后的检查使用新 Token。"""
    access_key, secret_key = c.storage_secret()
    reveals_before = int(c.sql("mor_vault", "SELECT COUNT(*) FROM t_vault_access_event WHERE caller_service='platform-support-service' "
                               "AND data_type='FILE_STORAGE_CREDENTIAL_V1' AND action='REVEAL' AND outcome='SUCCESS'")[0][0])
    status, body, text = c.raw(admin, "PUT", f"{c.ADMIN}/instances/{c.INSTANCE}/credential", {
        "credentialMode": "STATIC", "accessKey": access_key, "secretKey": secret_key,
        "expectedVersion": instance["version"], "reason": REASON + "：更换密钥（同一账号）"})
    c.check("AC-07", f"更换密钥（{status}），版本 {instance['version']} → {body.get('version')}",
            status == 200 and body.get("version") == instance["version"] + 1, body)
    c.check("AC-07", "更换密钥响应不含密钥原值", access_key not in text and secret_key not in text)
    token_after = c.sql("mor_platform", f"SELECT credential_vault_token FROM t_file_storage_instance WHERE instance_code='{c.INSTANCE}'")[0][0]
    referenced = int(c.sql("mor_platform", f"SELECT COUNT(*) FROM t_file_storage_instance WHERE credential_vault_token='{record['tokenBefore']}'")[0][0])
    record["tokenAfter"] = token_after
    c.check("AC-07", "新 Token 已替换，旧 Token 不再被引用", token_after and token_after != record["tokenBefore"] and referenced == 0)
    status, check, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{c.BUCKET}/check", typed_empty=True)
    reveals_after = int(c.sql("mor_vault", "SELECT COUNT(*) FROM t_vault_access_event WHERE caller_service='platform-support-service' "
                              "AND data_type='FILE_STORAGE_CREDENTIAL_V1' AND action='REVEAL' AND outcome='SUCCESS'")[0][0])
    record_ids = c.sql("mor_vault", "SELECT record_id FROM t_vault_access_event WHERE caller_service='platform-support-service' "
                       "AND data_type='FILE_STORAGE_CREDENTIAL_V1' AND action='REVEAL' ORDER BY create_time DESC LIMIT 1")
    record["lastRevealRecordId"] = record_ids[0][0] if record_ids else None
    c.check("AC-07", f"更换后桶检查用新密钥通过（{status} {check.get('result')}，Vault REVEAL {reveals_before} → {reveals_after}）",
            status == 200 and check.get("result") == "OK" and reveals_after > reveals_before, check)


def reader_checks(admin, run, record):
    reader = c.synthetic_reader(admin, run, record)
    status, _, _ = c.instances(reader)
    c.check("AC-01", f"只读人员可看实例列表（{status}）", status == 200)
    status, _, _ = c.routes(reader)
    c.check("AC-01", f"只读人员可看写入路由（{status}）", status == 200)
    status, body, _ = c.raw(reader, "POST", c.ADMIN + "/instances", {"instanceCode": "FS7_DENIED", "storageType": "S3", "region": c.REGION,
                                                                    "pathStyleAccess": True, "credentialMode": "IAM_ROLE", "reason": REASON})
    c.check("AC-01", f"只读人员新建实例被拒（{status}）", status == 403, body)
    status, body, _ = c.raw(reader, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{c.BUCKET}/check", typed_empty=True)
    c.check("AC-01", f"只读人员检查桶被拒（{status}）", status == 403, body)
    status, nav, _ = c.raw(reader, "GET", "/authorization/v1/navigation")
    text = json.dumps(nav, ensure_ascii=False)
    c.check("AC-01", "只读人员导航有存储管理页、没有维护按钮", status == 200 and "OPERATIONS_FILE_STORAGE" in text
            and "OPERATIONS_FILE_STORAGE_MANAGE" not in text, {"status": status})


def internal_interface():
    statuses = c.internal_probe()
    c.check("AC-09", f"内部文件接口无调用身份一律 401（mor-test internal-auth=OFF）：{statuses}",
            len(statuses) == 5 and all(v == "401" for v in statuses.values()), statuses)


def audit_and_logs(record, since):
    rows = c.sql("mor_platform", "SELECT resource_type, result, COUNT(*) FROM t_audit_event WHERE resource_type LIKE 'FILE\\_STORAGE\\_%' "
                 f"AND occurred_time >= '{since.replace('T', ' ').rstrip('Z')}' GROUP BY resource_type, result")
    record["audit"] = rows
    succeeded = {r[0] for r in rows if r[1] == "SUCCEEDED"}
    c.check("AC-01", f"存储管理写入有审计（{rows}）", {"FILE_STORAGE_BUCKET", "FILE_STORAGE_INSTANCE"} <= succeeded, rows)
    access_key, secret_key = c.storage_secret()
    attrs = c.sql("mor_platform", "SELECT IFNULL(a.value_text, ''), IFNULL(a.value_masked, '') FROM t_audit_event_attr a "
                  "JOIN t_audit_event e ON e.audit_event_id=a.audit_event_id WHERE e.resource_type LIKE 'FILE\\_STORAGE\\_%'")
    flat = "\n".join("\t".join(r) for r in attrs)
    c.check("AC-01", f"存储审计属性（{len(attrs)} 条）不含密钥原值", access_key not in flat and secret_key not in flat)
    for service in ("platform-support-service", "operations-console-bff", "api-gateway"):
        text = c.logs_since(service, since)
        c.check("AC-01", f"{service} 日志不含密钥原值（自 {since}）", access_key not in text and secret_key not in text)
    for service in ("platform-support-service", "operations-console-bff"):
        errors = c.errors_since(service, since)
        c.check("运行", f"{service} 验收期间 ERROR 0", not errors, errors[:3])
    for service in c.SERVICES:
        c.check("运行", f"{service} healthy 且未重启（{c.health(service)}）", c.health(service) == "healthy 0")


def main():
    run = c.datetime.now(c.timezone.utc).strftime("%Y%m%d%H%M%S")
    since = c.now_iso()
    record = {"run": run, "startedAt": since}
    admin = c.admin_token()
    if "--legacy-only" in sys.argv:
        # 替换前基线（release.md 第 5 节第 2 步）：只做旧文件审核下载与字节核对，不写任何数据。
        record["mode"] = "legacy-only"
        legacy_download(admin, record)
        c.save(f"baseline-{run}.json", record)
        if not c.summary():
            raise SystemExit(1)
        return
    c.save(f"run-{run}.json", record)
    instance = registration_state(admin, record)
    migration_backfill()
    legacy_download(admin, record)
    bucket_check_rejected(admin, run, record)
    if instance.get("version"):
        replace_credential(admin, record, instance)
    reader_checks(admin, run, record)
    internal_interface()
    audit_and_logs(record, since)
    record["finishedAt"] = c.now_iso()
    record["results"] = c.results
    path = c.save(f"run-{run}.json", record)
    print(f"运行记录 {path}；清理：python3 -I stage7_cleanup.py {path}")
    if not c.summary():
        raise SystemExit(1)


if __name__ == "__main__":
    main()
