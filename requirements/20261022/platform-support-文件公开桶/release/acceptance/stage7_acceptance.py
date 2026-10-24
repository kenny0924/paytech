"""文件公开桶阶段 7 mor-test 验收（release.md 第 9 节；六个镜像替换、SQL 与激活之后执行）。

前提：公开测试桶已按 release.md 第 8 节由用户 / 主会话在存储侧建好（版本控制、默认加密、匿名只读、Platform 账号可写）；
本脚本只经运营接口登记它。缺省为 mor-test MinIO 的 mor-platform-public，改用 AWS 时传 --public-instance S3_TEST 等三个参数。

写入（验收后由 stage7_cleanup.py 处理，见该文件说明）：
  · 合成人员 / 角色 2 组（文件记录只读、无下载权限）；
  · 公开测试桶登记（保留，见 release.md 第 11 节待决定项）；
  · 写入规则 (pubbucket-qa, PUBLIC) 与检查策略生命周期用的 (pubbucket-qa-policy, PRIVATE)，检查策略 QA_PB7_<run>；
  · 合成来源 pubbucket-qa 的公开文件 1 个（2 个版本）、私有文件 1 个（2 个版本）；
  · 回归：client-console-bff 身份的入驻材料上传 1 个（写 S3_TEST）、工作流附件上传 1 个、BIN CSV 上传 1 个（只上传，不建版本）。
读取：已有入驻材料 O10 下载、已有工作流附件下载，各核对 SHA-256；两个存量私有桶重查；文件记录列表 / 详情 / 下载（写下载审计）。
用法：python3 -I stage7_acceptance.py [--public-instance MINIO] [--public-bucket mor-platform-public]
                                       [--public-base-url https://minio.mor-test.localhost:29400/mor-platform-public] [--skip-regression]
      → 运行记录 PB7_STATE/run-<run>.json（0600，含合成人员口令，不含存储密钥与预签名地址）
"""
import argparse
import json
import re
import sys
import threading
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pb7_common as c  # noqa: E402

REASON = "文件公开桶阶段 7 mor-test 验收（合成数据验收后清理）"
WRONG_BASE = {"MINIO": "https://minio.mor-test.localhost:29400/mor-platform-files",
              "S3_TEST": "https://s3.ap-southeast-1.amazonaws.com/mor-test-platform-files-k7q2"}
RED, GREEN = c.png((200, 30, 30)), c.png((30, 160, 60))
PDF_V2 = c.PDF + b"% pubbucket replacement v2\n"
CSV = b"bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name\n" \
      b"40128888,VISA,CREDIT,PLATINUM,CONSUMER,AE,PB7 SYNTHETIC ISSUER\n"


# 本需求之后的发布覆盖文件：其中的镜像同样包含本需求（基线 be688db7 起），按后发布者优先。
LATER_RELEASES = ("platform-support-工作流体验优化",)


def candidate_images():
    images = {}
    for path in [c.HERE.parent / "compose-candidate.yaml"] + [
            c.HERE.parents[2] / name / "release/compose-candidate.yaml" for name in LATER_RELEASES]:
        if path.exists():
            images.update(re.findall(r"^  ([a-z-]+):\n(?:    #.*\n)*    image: (sha256:[0-9a-f]{64})", path.read_text(), re.M))
    return images


def preconditions():
    expected = candidate_images()
    for service in c.SERVICES:
        image = c.subprocess.run(["docker", "inspect", "-f", "{{.Image}}", f"mor-test-{service}-1"], capture_output=True,
                                 text=True).stdout.strip()
        c.check("运行", f"{service} 运行候选镜像且 healthy（{c.health(service)}）",
                image == expected.get(service) and c.health(service).startswith("healthy"), {"image": image[:19]})


# ---------- 存储管理：私有桶重查、公开桶登记、写入规则 ----------

def private_buckets(admin):
    for instance_code, bucket in c.PRIVATE_BUCKETS:
        status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{instance_code}/buckets/{bucket}/check", typed_empty=True)
        c.check("AC-02", f"私有桶 {instance_code}/{bucket} 重查仍 OK（匿名不可读）：{status} {body.get('result')}",
                status == 200 and body.get("result") == "OK", body)
    status, items, _ = c.instances(admin)
    rows = [c.bucket_of(items.get(i), b) or {} for i, b in c.PRIVATE_BUCKETS]
    c.check("AC-01", "存量两个桶回填为 PRIVATE、无对外地址", status == 200
            and all(r.get("accessLevel") == "PRIVATE" and r.get("publicBaseUrl") is None for r in rows), rows)


def bucket_validation(admin, args):
    path = f"{c.ADMIN}/instances/{args.public_instance}/buckets"
    cases = [("私有桶带对外地址", {"accessLevel": "PRIVATE", "publicBaseUrl": args.public_base_url}),
             ("公开桶缺对外地址", {"accessLevel": "PUBLIC"}),
             ("公开桶 http 地址", {"accessLevel": "PUBLIC", "publicBaseUrl": args.public_base_url.replace("https://", "http://")}),
             ("公开桶地址以 / 结尾", {"accessLevel": "PUBLIC", "publicBaseUrl": args.public_base_url + "/"})]
    for name, extra in cases:
        status, body, _ = c.raw(admin, "POST", path, {"bucketName": args.public_bucket + "-invalid", "reason": REASON, **extra})
        c.check("AC-01", f"登记校验：{name} 被拒（{status}）", status in (400, 422), body)


def public_bucket(admin, args, record):
    """AC-02 / AC-03：公开桶先以错误对外地址登记 → 检查 PUBLIC_READ_FAILED、不能启用 → 改地址 → 检查 OK → 启用 → 启用后不能改地址。"""
    base = f"{c.ADMIN}/instances/{args.public_instance}/buckets"
    status, items, _ = c.instances(admin)
    existing = c.bucket_of(items.get(args.public_instance), args.public_bucket)
    if existing and existing.get("status") == "ACTIVE":
        record["publicBucket"] = {"reused": True}
        status, body, _ = c.raw(admin, "POST", f"{base}/{args.public_bucket}/check", typed_empty=True)
        c.check("AC-02", f"公开桶已登记并启用（重跑），检查 OK（{status} {body.get('result')}）",
                status == 200 and body.get("result") == "OK" and existing.get("publicBaseUrl") == args.public_base_url, body)
        return
    if existing and existing.get("publicBaseUrl") == args.public_base_url:
        # 用户已在控制台以正确对外地址登记（草稿）：只验证检查 → 启用 → 启用后不能改地址。
        record["publicBucket"] = {"registeredByUser": True}
        status, check, _ = c.raw(admin, "POST", f"{base}/{args.public_bucket}/check", typed_empty=True)
        c.check("AC-02", f"公开桶检查 OK（{status} {check.get('result')}）", status == 200 and check.get("result") == "OK", check)
        enable_and_lock(admin, args, base, check)
        return
    if not existing:
        status, body, _ = c.raw(admin, "POST", base, {"bucketName": args.public_bucket, "accessLevel": "PUBLIC",
                                                      "publicBaseUrl": WRONG_BASE[args.public_instance], "reason": REASON})
        c.check("AC-01", f"登记公开桶（错误对外地址）{args.public_bucket}：{status}",
                status in (200, 201) and body.get("status") == "DRAFT", body)
        record["publicBucket"] = {"registeredAt": c.now_iso(), "instance": args.public_instance, "bucket": args.public_bucket}
        c.save(f"run-{record['run']}.json", record)
    status, check, _ = c.raw(admin, "POST", f"{base}/{args.public_bucket}/check", typed_empty=True)
    c.check("AC-02", f"错误对外地址：检查 PUBLIC_READ_FAILED（{status} {check.get('result')}）",
            status == 200 and check.get("result") == "PUBLIC_READ_FAILED", check)
    status, body, _ = c.raw(admin, "PATCH", f"{base}/{args.public_bucket}/status",
                            {"status": "ACTIVE", "expectedVersion": check.get("version") or 1, "reason": REASON})
    c.check("AC-02", f"检查未通过的公开桶不能启用（{status}）", status == 400, body)
    status, body, _ = c.raw(admin, "PUT", f"{base}/{args.public_bucket}", {
        "publicBaseUrl": args.public_base_url, "expectedVersion": check.get("version") or 1, "reason": REASON})
    c.check("AC-01", f"草稿公开桶修改对外地址（{status}）", status == 200, body)
    status, check, _ = c.raw(admin, "POST", f"{base}/{args.public_bucket}/check", typed_empty=True)
    c.check("AC-02", f"正确对外地址：检查 OK（{status} {check.get('result')}）", status == 200 and check.get("result") == "OK", check)
    enable_and_lock(admin, args, base, check)


def enable_and_lock(admin, args, base, check):
    status, body, _ = c.raw(admin, "PATCH", f"{base}/{args.public_bucket}/status",
                            {"status": "ACTIVE", "expectedVersion": check.get("version"), "reason": REASON})
    c.check("AC-02", f"启用公开桶（{status}）", status == 200 and body.get("status") == "ACTIVE", body)
    status, body, _ = c.raw(admin, "PUT", f"{base}/{args.public_bucket}", {
        "publicBaseUrl": args.public_base_url + "-changed", "expectedVersion": body.get("version") or 0, "reason": REASON})
    c.check("AC-01", f"启用后不能再改对外地址（{status}）", status in (400, 409), body)


def route_matrix(admin, args, record):
    """AC-03 / AC-06：(来源, 级别) 路由；公开路由只能指向公开桶；路径带 /{accessLevel} 的编辑；默认私有规则不能删。"""
    status, items, _ = c.routes(admin)
    for key, (instance_code, bucket, policy) in c.EXISTING_ROUTES.items():
        row = items.get(key) or {}
        c.check("AC-03", f"存量写入规则 {key[0]}/{key[1]} → {instance_code}/{bucket}/{policy} 不变",
                (row.get("instanceCode"), row.get("bucketName"), row.get("scanPolicyCode")) == (instance_code, bucket, policy), row)
    body = {"sourceSystem": c.QA_SOURCE, "accessLevel": "PUBLIC", "instanceCode": "MINIO", "bucketName": "mor-platform-files",
            "scanPolicyCode": "CLIENT_ONBOARDING_FORMAT", "maxUploadSeconds": 900, "reason": REASON}
    status, response, _ = c.raw(admin, "POST", c.ADMIN + "/routes", body)
    c.check("AC-03", f"公开写入规则指向私有桶被拒（{status}）", status == 400, response)
    if (c.QA_SOURCE, "PUBLIC") not in items:
        body.update(instanceCode=args.public_instance, bucketName=args.public_bucket)
        status, response, _ = c.raw(admin, "POST", c.ADMIN + "/routes", body)
        c.check("AC-03", f"新建写入规则 {c.QA_SOURCE}/PUBLIC → {args.public_bucket}（{status}）", status in (200, 201), response)
        record.setdefault("routes", []).append([c.QA_SOURCE, "PUBLIC"])
        c.save(f"run-{record['run']}.json", record)
    status, items, _ = c.routes(admin)
    route = items.get((c.QA_SOURCE, "PUBLIC")) or {}
    status, response, _ = c.raw(admin, "PUT", f"{c.ADMIN}/routes/{c.QA_SOURCE}/PUBLIC", {
        "instanceCode": args.public_instance, "bucketName": args.public_bucket, "scanPolicyCode": "CLIENT_ONBOARDING_FORMAT",
        "maxUploadSeconds": 600, "expectedVersion": route.get("version"), "reason": REASON})
    c.check("AC-03", f"编辑写入规则走新路径 /routes/{{来源}}/{{级别}}（{status}）", status == 200, response)
    default = items.get(("*", "PRIVATE")) or {}
    status, response, _ = c.raw(admin, "DELETE", f"{c.ADMIN}/routes/{c.segment('*')}/PRIVATE",
                                {"expectedVersion": default.get("version"), "reason": REASON})
    c.check("AC-03", f"默认私有规则 */PRIVATE 不能删除（{status}）", status == 400, response)


# ---------- 内部文件接口 ----------

def start(caller, filename, mime, data, access_level=None, reason=c.QA_REASON):
    body = {"reasonCode": reason, "filename": filename, "mimeType": mime, "sizeBytes": str(len(data))}
    if access_level:
        body["accessLevel"] = access_level
    return c.internal("POST", "/uploads", caller, body)


def upload(caller, filename, mime, data, access_level=None, reason=c.QA_REASON):
    """开始 → 直传 → 完成，返回 (完成回执, 失败说明)。"""
    status, grant = start(caller, filename, mime, data, access_level, reason)
    if status != 201:
        return None, f"开始上传 {status} {grant.get('code')} {grant.get('detail')}"
    put = c.put_upload(grant, data)
    if put != 200:
        return None, f"直传 {put}"
    status, done = c.internal("POST", f"/uploads/{grant['uploadId']}/completion", caller)
    if status != 200:
        return None, f"完成 {status} {done.get('code')} {done.get('detail')}"
    return done, None


def no_fallback():
    status, body = start(c.QA_NO_ROUTE_SOURCE, "noroute.png", "image/png", RED, "PUBLIC")
    c.check("AC-03", f"没有公开路由的来源声明 PUBLIC：503，不回落到私有（{status}）", status == 503, body)


def anonymous(url):
    return c.object_request("GET", url)


def public_file(record):
    """AC-04 / AC-05：公开上传 → 检查通过后 publicUrl 可匿名读取；替换后地址不变、读到新内容；并发替换只有一个成功。"""
    done, problem = upload(c.QA_SOURCE, "pb7-public.png", "image/png", RED, "PUBLIC")
    if not c.check("AC-04", f"公开文件上传完成（{problem or 'versionNo=' + str(done.get('versionNo'))}）",
                   done and done.get("versionNo") == 1 and done.get("accessLevel") == "PUBLIC", done):
        return None
    file_id = done["fileId"]
    record.setdefault("files", {})["public"] = file_id
    c.save(f"run-{record['run']}.json", record)
    status, meta = c.wait_file(c.QA_SOURCE, file_id, lambda m: m.get("scanStatus") == "AVAILABLE")
    public_url, versioned = meta.get("publicUrl"), meta.get("versionedUrl")
    c.check("AC-05", "检查通过后返回 publicUrl 与 versionedUrl（?v=1）", status == 200 and public_url
            and versioned == f"{public_url}?v=1" and meta.get("accessLevel") == "PUBLIC", meta)
    if not public_url:
        return file_id
    http_status, data = anonymous(public_url)
    c.check("AC-05", f"publicUrl 不带凭据可读，内容为 v1（HTTP {http_status}）", http_status == 200 and data == RED)
    record["publicUrlHost"] = c.urllib.parse.urlsplit(public_url).netloc

    body = {"filename": "pb7-public.png", "mimeType": "image/png", "sizeBytes": str(len(GREEN)), "accessLevel": "PRIVATE"}
    status, response = c.internal("POST", f"/files/{file_id}/replacements", c.QA_SOURCE, body)
    c.check("AC-12", f"替换时修改访问级别被拒（{status}）", status in (400, 422), response)
    body.pop("accessLevel")
    outcomes = {}

    def go(slot):
        outcomes[slot] = c.internal("POST", f"/files/{file_id}/replacements", c.QA_SOURCE, body)
    threads = [threading.Thread(target=go, args=(slot,)) for slot in ("a", "b")]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    codes = sorted(status for status, _ in outcomes.values())
    winner = next((grant for status, grant in outcomes.values() if status == 201), None)
    loser = next((grant for status, grant in outcomes.values() if status == 409), {})
    # 后到者看到进行中的替换为 FILE_STATE_CONFLICT；两者同时拿锁时 TRY_LOCK 失败方为 CONCURRENT_REQUEST，两种都是 409。
    c.check("AC-10", f"同时开始两次替换：一个 201、一个 409（{codes}，{loser.get('code')}）",
            codes == [201, 409] and loser.get("code") in ("FILE_STATE_CONFLICT", "CONCURRENT_REQUEST"),
            {"codes": codes, "loser": loser.get("code")})
    if not winner:
        return file_id
    put = c.put_upload(winner, GREEN)
    status, done = c.internal("POST", f"/uploads/{winner['uploadId']}/completion", c.QA_SOURCE)
    c.check("AC-10", f"替换直传（不带 If-None-Match）{put} 并完成：{status} versionNo={done.get('versionNo')}",
            put == 200 and status == 200 and done.get("versionNo") == 2 and done.get("fileId") == file_id, done)
    status, meta = c.wait_file(c.QA_SOURCE, file_id, lambda m: m.get("versionNo") == 2 and m.get("pendingVersionNo") is None)
    c.check("AC-11", "替换检查通过：versionNo 2、无检查中版本、publicUrl 不变、versionedUrl ?v=2",
            status == 200 and meta.get("publicUrl") == public_url and meta.get("versionedUrl") == f"{public_url}?v=2", meta)
    http_status, data = anonymous(public_url)
    c.check("AC-11", f"同一 publicUrl 匿名读到 v2（HTTP {http_status}）", http_status == 200 and data == GREEN)
    return file_id


def download_bytes(caller, file_id):
    status, grant = c.internal("POST", f"/files/{file_id}/download", caller)
    if status != 200:
        return status, None
    return c.object_request("GET", grant["url"])


def private_file(record):
    """AC-04 / AC-13：私有上传无 publicUrl；替换直传后、检查通过前下载仍是旧版本；通过后是新版本。"""
    done, problem = upload(c.QA_SOURCE, "pb7-private.pdf", "application/pdf", c.PDF)
    if not c.check("AC-04", f"私有文件上传（未声明级别，按默认 */PRIVATE）：{problem or 'versionNo=' + str(done.get('versionNo'))}",
                   done and done.get("accessLevel") == "PRIVATE", done):
        return None
    file_id = done["fileId"]
    record.setdefault("files", {})["private"] = file_id
    c.save(f"run-{record['run']}.json", record)
    status, meta = c.wait_file(c.QA_SOURCE, file_id, lambda m: m.get("scanStatus") == "AVAILABLE")
    c.check("AC-04", "私有文件没有 publicUrl / versionedUrl", status == 200 and meta.get("accessLevel") == "PRIVATE"
            and meta.get("publicUrl") is None and meta.get("versionedUrl") is None, meta)
    row = c.sql("mor_platform", f"SELECT storage_provider, storage_container_ref FROM t_file_object WHERE file_id={int(file_id)}")
    c.check("AC-03", f"私有文件按默认路由写入 MINIO / mor-platform-files（{row}）", row == [["MINIO", "mor-platform-files"]])
    status, grant = c.internal("POST", f"/files/{file_id}/replacements", c.QA_SOURCE,
                               {"filename": "pb7-private.pdf", "mimeType": "application/pdf", "sizeBytes": str(len(PDF_V2))})
    if not c.check("AC-13", f"私有文件开始替换（{status}）", status == 201, grant):
        return file_id
    put = c.put_upload(grant, PDF_V2)
    http_status, data = download_bytes(c.QA_SOURCE, file_id)
    c.check("AC-13", f"替换直传后（{put}）、完成前下载仍是 v1（HTTP {http_status}）", put == 200 and http_status == 200 and data == c.PDF)
    status, done = c.internal("POST", f"/uploads/{grant['uploadId']}/completion", c.QA_SOURCE)
    status_meta, meta = c.get_file(c.QA_SOURCE, file_id)
    http_status, data = download_bytes(c.QA_SOURCE, file_id)
    if meta.get("pendingVersionNo") == 2:
        c.check("AC-13", f"候选检查中下载仍是 v1（HTTP {http_status}）", http_status == 200 and data == c.PDF)
    else:
        print(f"[记录] AC-13 完成后立即查询时 v2 已检查通过（versionNo={meta.get('versionNo')}），“检查中读旧版本”在 mor-test 未能构造，"
              "由本机 InternalFileReplacementApiTest 覆盖", flush=True)
    status, meta = c.wait_file(c.QA_SOURCE, file_id, lambda m: m.get("versionNo") == 2 and m.get("pendingVersionNo") is None)
    http_status, data = download_bytes(c.QA_SOURCE, file_id)
    c.check("AC-13", f"检查通过后下载为 v2（HTTP {http_status}）", status == 200 and http_status == 200 and data == PDF_V2)
    return file_id


# ---------- 文件记录 ----------

def file_records(admin, reader, record):
    public_id, private_id = (record.get("files") or {}).get("public"), (record.get("files") or {}).get("private")
    created_from, created_to = c.created_window()
    query = f"createdFrom={created_from}&createdTo={created_to}&sourceSystem={c.QA_SOURCE}&page=1&size=50"
    status, page, _ = c.raw(admin, "GET", f"{c.ADMIN}/files?{query}")
    ids = {item.get("fileId") for item in page.get("items") or []}
    c.check("AC-15", f"文件记录按来源筛选包含本次两个文件（{status}，共 {page.get('total')}）",
            status == 200 and {public_id, private_id} <= ids, {"status": status})
    status, page, _ = c.raw(admin, "GET", f"{c.ADMIN}/files?{query}&accessLevel=PUBLIC")
    ids = {item.get("fileId") for item in page.get("items") or []}
    c.check("AC-15", "按访问级别 PUBLIC 筛选只含公开文件", status == 200 and public_id in ids and private_id not in ids, ids)
    status, page, _ = c.raw(admin, "GET", f"{c.ADMIN}/files?{query}&hasPendingReplacement=true")
    c.check("AC-15", "按“有替换进行中”筛选不含已完成替换的文件", status == 200
            and not {public_id, private_id} & {i.get("fileId") for i in page.get("items") or []}, page)
    status, body, _ = c.raw(admin, "GET", f"{c.ADMIN}/files?createdFrom=2026-01-01T00:00:00Z&createdTo=2026-03-01T00:00:00Z")
    c.check("AC-15", f"时间跨度超过 31 天被拒（{status}）", status == 400, body)
    if not public_id:
        return
    status, detail, _ = c.raw(admin, "GET", f"{c.ADMIN}/files/{public_id}")
    versions = {v.get("versionNo"): v for v in detail.get("versions") or []}
    kinds = sorted(u.get("kind") for u in detail.get("uploads") or [])
    scanned = sorted({s.get("versionNo") for s in detail.get("scanResults") or []})
    c.check("AC-15", "详情：v2 CURRENT、v1 SUPERSEDED 均可下载；检查结果覆盖 v1 / v2；上传记录 ORIGINAL + REPLACEMENT（含落败的并发替换不计）",
            status == 200 and versions.get(2, {}).get("versionStatus") == "CURRENT"
            and versions.get(1, {}).get("versionStatus") == "SUPERSEDED"
            and versions[1].get("downloadable") is True and versions[2].get("downloadable") is True
            and scanned == [1, 2] and kinds.count("ORIGINAL") == 1 and "REPLACEMENT" in kinds
            and detail.get("publicUrl") and detail.get("accessLevel") == "PUBLIC",
            {"versions": {k: v.get("versionStatus") for k, v in versions.items()}, "kinds": kinds, "scanned": scanned})
    audits_before = int(c.sql("mor_platform", "SELECT COUNT(*) FROM t_audit_event WHERE action_code='FILE_DOWNLOAD_AUTHORIZE' "
                              f"AND resource_id='{public_id}'")[0][0])
    status, grant, text = c.raw(admin, "POST", f"{c.ADMIN}/files/{public_id}/download?versionNo=1", typed_empty=True)
    http_status, data = c.object_request("GET", grant["url"]) if status == 200 else (None, None)
    c.check("AC-15", f"运营下载历史版本 v1（{status}，对象 HTTP {http_status}）内容为 v1", status == 200 and data == RED,
            {"status": status, "code": grant.get("code")})
    audits_after = int(c.sql("mor_platform", "SELECT COUNT(*) FROM t_audit_event WHERE action_code='FILE_DOWNLOAD_AUTHORIZE' "
                             f"AND resource_id='{public_id}'")[0][0])
    c.check("AC-15", f"下载写 FILE_DOWNLOAD_AUTHORIZE 审计（{audits_before} → {audits_after}）", audits_after == audits_before + 1)
    c.check("AC-15", "下载地址不出现在审计属性中", not c.sql("mor_platform", "SELECT 1 FROM t_audit_event_attr a JOIN t_audit_event e "
                                                    "ON e.audit_event_id=a.audit_event_id WHERE e.resource_id="
                                                    f"'{public_id}' AND a.value_text LIKE 'https://%' LIMIT 1"))
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/files/{public_id}/download?versionNo=9", typed_empty=True)
    c.check("AC-15", f"不存在 / 不可下载的版本被拒（{status}）", status in (404, 409), body)
    status, _, _ = c.raw(reader, "GET", f"{c.ADMIN}/files/{public_id}")
    c.check("AC-15", f"只读人员可看详情（{status}）", status == 200)
    status, body, _ = c.raw(reader, "POST", f"{c.ADMIN}/files/{public_id}/download", typed_empty=True)
    c.check("AC-15", f"无下载权限的人员下载被拒（{status}）", status == 403, body)
    status, nav, _ = c.raw(reader, "GET", "/authorization/v1/navigation")
    text = json.dumps(nav, ensure_ascii=False)
    c.check("AC-15", "只读人员导航有文件记录、没有下载按钮", status == 200 and "OPERATIONS_FILE_RECORDS" in text
            and "OPERATIONS_FILE_RECORD_DOWNLOAD" not in text, {"status": status})


# ---------- 检查策略 ----------

def policy_detail(admin, code):
    status, body, _ = c.raw(admin, "GET", f"{c.ADMIN}/scan-policies/{code}")
    return status, body, {r.get("revisionNo"): r for r in body.get("revisions") or []}


def scan_policies(admin, run, record):
    """AC-07 / AC-14：新建 → 修改草稿 → 激活 → 新修订 / 已有草稿 409 → 废弃 → 新修订激活（旧 → RETIRED）→ 被引用时停用 400 → 解除后停用。"""
    status, listing, _ = c.raw(admin, "GET", c.ADMIN + "/scan-policies")
    items = {i.get("policyCode"): i for i in listing.get("items") or []}
    c.check("AC-07", "策略列表：supportedMimeTypes 含 pdf / png / jpeg / csv；存量两个策略 ACTIVE 且有引用",
            status == 200 and {"application/pdf", "image/png", "image/jpeg", "text/csv"} <= set(listing.get("supportedMimeTypes") or [])
            and items.get("CLIENT_ONBOARDING_FORMAT", {}).get("status") == "ACTIVE" and items.get("CARD_BIN_CSV", {}).get("routeCount", 0) >= 1,
            {"status": status, "codes": sorted(items)})
    code = f"QA_PB7_{run}"
    status, body, _ = c.raw(admin, "POST", c.ADMIN + "/scan-policies", {"policyCode": code + "_BAD", "allowedMimeTypes": ["application/zip"],
                                                                        "maxSizeBytes": "1024", "reason": REASON})
    c.check("AC-07", f"不可识别的类型被拒（{status}）", status == 400, body)
    status, body, _ = c.raw(admin, "POST", c.ADMIN + "/scan-policies", {"policyCode": code, "allowedMimeTypes": ["application/pdf"],
                                                                        "maxSizeBytes": "1048576", "reason": REASON})
    if not c.check("AC-07", f"新建策略 {code}（{status}）", status in (200, 201), body):
        return
    record["policy"] = code
    c.save(f"run-{run}.json", record)
    _, _, revisions = policy_detail(admin, code)
    status, body, _ = c.raw(admin, "PUT", f"{c.ADMIN}/scan-policies/{code}/revisions/1", {
        "allowedMimeTypes": ["application/pdf", "image/png"], "maxSizeBytes": "2097152",
        "expectedVersion": revisions.get(1, {}).get("version"), "reason": REASON})
    c.check("AC-07", f"修改草稿 r1（{status}）", status == 200, body)
    _, _, revisions = policy_detail(admin, code)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions/1/activation",
                            {"expectedVersion": revisions.get(1, {}).get("version"), "reason": REASON})
    c.check("AC-07", f"激活 r1（{status}）", status == 200, body)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions", {"reason": REASON})
    c.check("AC-07", f"以 ACTIVE 为模板新建 r2（{status}）", status in (200, 201), body)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions", {"reason": REASON})
    c.check("AC-07", f"已有草稿时再建修订 409（{status}）", status == 409, body)
    _, _, revisions = policy_detail(admin, code)
    status, body, _ = c.raw(admin, "DELETE", f"{c.ADMIN}/scan-policies/{code}/revisions/2",
                            {"expectedVersion": revisions.get(2, {}).get("version"), "reason": REASON})
    c.check("AC-07", f"废弃草稿 r2（{status}）", status == 200, body)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions", {"reason": REASON})
    _, _, revisions = policy_detail(admin, code)
    draft = max(revisions)
    status, body, _ = c.raw(admin, "PUT", f"{c.ADMIN}/scan-policies/{code}/revisions/{draft}", {
        "allowedMimeTypes": ["image/png"], "maxSizeBytes": "1048576", "expectedVersion": revisions[draft].get("version"), "reason": REASON})
    _, _, revisions = policy_detail(admin, code)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions/{draft}/activation",
                            {"expectedVersion": revisions[draft].get("version"), "reason": REASON})
    _, detail, revisions = policy_detail(admin, code)
    c.check("AC-07", f"激活 r{draft}：r1 → RETIRED、r{draft} ACTIVE（{status}）", status == 200
            and revisions.get(1, {}).get("status") == "RETIRED" and revisions.get(draft, {}).get("status") == "ACTIVE",
            {k: v.get("status") for k, v in revisions.items()})
    c.check("AC-07", "修订自动带一条 PLATFORM_FORMAT / FORMAT / ≥1 / FORMAT_VALID 检查要求",
            all([(q.get("scannerCode"), q.get("scanType"), q.get("acceptedResultCodes")) for q in r.get("requirements") or []]
                == [("PLATFORM_FORMAT", "FORMAT", ["FORMAT_VALID"])] for r in revisions.values()), revisions)
    status, body, _ = c.raw(admin, "POST", c.ADMIN + "/routes", {
        "sourceSystem": c.QA_POLICY_SOURCE, "accessLevel": "PRIVATE", "instanceCode": "MINIO", "bucketName": "mor-platform-files",
        "scanPolicyCode": code, "maxUploadSeconds": 900, "reason": REASON})
    c.check("AC-07", f"写入规则引用新策略（{status}）", status in (200, 201), body)
    record.setdefault("routes", []).append([c.QA_POLICY_SOURCE, "PRIVATE"])
    c.save(f"run-{run}.json", record)
    _, _, revisions = policy_detail(admin, code)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/retirement",
                            {"expectedVersion": revisions[draft].get("version"), "reason": REASON})
    c.check("AC-07", f"被写入规则引用的策略不能停用（{status}）", status == 400, body)
    status, items, _ = c.routes(admin)
    route = items.get((c.QA_POLICY_SOURCE, "PRIVATE")) or {}
    status, body, _ = c.raw(admin, "DELETE", f"{c.ADMIN}/routes/{c.QA_POLICY_SOURCE}/PRIVATE",
                            {"expectedVersion": route.get("version"), "reason": REASON})
    c.check("AC-03", f"删除非默认写入规则（{status}）", status == 200, body)
    if status == 200:
        record["routes"].remove([c.QA_POLICY_SOURCE, "PRIVATE"])
        c.save(f"run-{run}.json", record)
    status, body, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/retirement",
                            {"expectedVersion": revisions[draft].get("version"), "reason": REASON})
    status_list, listing, _ = c.raw(admin, "GET", c.ADMIN + "/scan-policies")
    final = next((i for i in listing.get("items") or [] if i.get("policyCode") == code), {})
    c.check("AC-07", f"解除引用后停用（{status}），列表状态 RETIRED", status == 200 and final.get("status") == "RETIRED", final)


# ---------- 回归 ----------

def walk(node, key):
    found = []
    if isinstance(node, dict):
        for k, v in node.items():
            if k == key and isinstance(v, (str, int)):
                found.append(str(v))
            found.extend(walk(v, key))
    elif isinstance(node, list):
        for item in node:
            found.extend(walk(item, key))
    return found


def regression(admin, record):
    # 客户入驻上传（client-console-bff 路由 → S3_TEST）
    done, problem = upload("client-console-bff", "pb7-onboarding.pdf", "application/pdf", c.PDF, reason="CLIENT_ONBOARDING_MATERIAL")
    if c.check("AC-08", f"client-console-bff 身份上传入驻材料（{problem or 'ok'}）", done and done.get("accessLevel") == "PRIVATE", done):
        file_id = done["fileId"]
        record.setdefault("files", {})["onboarding"] = file_id
        c.save(f"run-{record['run']}.json", record)
        c.wait_file("client-console-bff", file_id, lambda m: m.get("scanStatus") == "AVAILABLE", seconds=120)
        row = c.sql("mor_platform", f"SELECT storage_provider, storage_container_ref FROM t_file_object WHERE file_id={int(file_id)}")
        http_status, data = download_bytes("client-console-bff", file_id)
        c.check("AC-08", f"入驻材料仍写 S3_TEST / k7q2（{row}），下载字节一致（HTTP {http_status}）",
                row == [["S3_TEST", "mor-test-platform-files-k7q2"]] and http_status == 200 and data == c.PDF)
    # O10 审核下载已有材料
    status, listing, _ = c.raw(admin, "GET", "/client-onboarding-admin/v1/cases")
    candidate = None
    for case_id in walk(listing, "caseId")[:30]:
        _, detail, _ = c.raw(admin, "GET", f"/client-onboarding-admin/v1/cases/{case_id}")
        for file_id in walk(detail, "fileId"):
            if c.sql("mor_platform", f"SELECT COUNT(*) FROM t_file_object WHERE file_id={int(file_id)} AND scan_status='AVAILABLE'")[0][0] == "1":
                candidate = (case_id, file_id)
                break
        if candidate:
            break
    if c.check("AC-08", f"找到带已有材料的审核任务（列表 {status}）", candidate is not None):
        case_id, file_id = candidate
        record["legacyFile"] = {"caseId": case_id, "fileId": file_id}
        status, grant, _ = c.raw(admin, "POST", f"/client-onboarding-admin/v1/cases/{case_id}/files/{file_id}/download",
                                 key=False, typed_empty=True)
        expected = c.sql("mor_platform", f"SELECT content_hash FROM t_file_object WHERE file_id={int(file_id)}")[0][0]
        http_status, data = c.object_request("GET", grant["url"]) if status == 200 else (None, b"")
        c.check("AC-08", f"O10 审核下载已有材料（{status} / {http_status}），SHA-256 一致",
                status == 200 and http_status == 200 and c.sha256_hex(data) == expected.lower(), {"status": status})
    # 工作流附件：上传一个、下载一个已有附件
    status, grant, _ = c.raw(admin, "POST", "/workflow/v1/attachments/uploads",
                             {"filename": "pb7-workflow.pdf", "mimeType": "application/pdf", "sizeBytes": str(len(c.PDF))})
    put = c.put_upload(grant, c.PDF) if status == 201 else None
    status_done, done, _ = c.raw(admin, "POST", f"/workflow/v1/attachments/uploads/{grant.get('uploadId')}/completion", typed_empty=True)
    c.check("AC-08", f"工作流附件上传（开始 {status}、直传 {put}、完成 {status_done}）", status == 201 and put == 200 and status_done == 200
            and done.get("fileAddress") == f"/files/v1/{done.get('fileId')}", done)
    if done.get("fileId"):
        record.setdefault("files", {})["workflow"] = done["fileId"]
    # 工作流附件登记在动作运行的 output_json.files（形如 /files/v1/{fileId}），不在启动参数中。
    rows = c.sql("mor_platform", "SELECT r.instance_id, f.file_id, f.content_hash FROM t_workflow_action_run r JOIN t_file_object f "
                 "ON f.reason_code='WORKFLOW_ATTACHMENT' AND f.scan_status='AVAILABLE' "
                 "AND JSON_CONTAINS(r.output_json, JSON_QUOTE(CONCAT('/files/v1/', f.file_id)), '$.files') LIMIT 1")
    if c.check("AC-08", "找到带附件的已有流程实例", bool(rows)):
        instance_id, file_id, expected = rows[0]
        status, grant, _ = c.raw(admin, "POST", f"/workflow/v1/instances/{instance_id}/files/{file_id}/download", typed_empty=True)
        http_status, data = c.object_request("GET", grant["url"]) if status == 200 else (None, b"")
        c.check("AC-08", f"已有工作流附件下载（{status} / {http_status}），SHA-256 一致",
                status == 200 and http_status == 200 and c.sha256_hex(data) == expected.lower(), {"status": status})
    # 卡 BIN CSV 上传（只上传、完成，不建版本）
    status, grant, _ = c.raw(admin, "POST", "/card-admin/v1/bin-library/uploads", {"fileName": "pb7-bin.csv", "sizeBytes": str(len(CSV))})
    put = c.put_upload(grant, CSV) if status in (200, 201) else None
    status_done, done, _ = c.raw(admin, "POST", f"/card-admin/v1/bin-library/uploads/{grant.get('uploadId')}/completion", typed_empty=True)
    file_id = (done.get("fileAddress") or "").rsplit("/", 1)[-1]
    row = c.sql("mor_platform", "SELECT f.storage_container_ref, p.policy_code FROM t_file_object f JOIN t_file_scan_policy p "
                f"ON p.policy_id=f.scan_policy_id WHERE f.file_id={int(file_id)}") if file_id.isdigit() else []
    c.check("AC-08", f"卡 BIN CSV 上传（开始 {status}、直传 {put}、完成 {status_done}），按 CARD_BIN_CSV 写 mor-platform-files（{row}）",
            status in (200, 201) and put == 200 and status_done == 200 and row == [["mor-platform-files", "CARD_BIN_CSV"]], done)
    if file_id.isdigit():
        record.setdefault("files", {})["bin"] = file_id


def runtime(since):
    for service in c.JAVA_SERVICES:
        errors = c.errors_since(service, since)
        c.check("运行", f"{service} 验收期间 ERROR 0", not errors, errors[:3])
    platform_log = c.logs_since(c.PLATFORM, since)
    c.check("运行", "Platform 无存储快照刷新失败", "snapshot refresh failed" not in platform_log)
    for service in c.SERVICES:
        c.check("运行", f"{service} healthy 且未重启（{c.health(service)}）", c.health(service) == "healthy 0")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--public-instance", default="MINIO", choices=sorted(WRONG_BASE))
    parser.add_argument("--public-bucket", default="mor-platform-public")
    parser.add_argument("--public-base-url", default="https://minio.mor-test.localhost:29400/mor-platform-public")
    parser.add_argument("--skip-regression", action="store_true")
    args = parser.parse_args()
    run = c.datetime.now(c.timezone.utc).strftime("%Y%m%d%H%M%S")
    since = c.now_iso()
    record = {"run": run, "startedAt": since, "publicInstance": args.public_instance, "publicBucketName": args.public_bucket}
    c.save(f"run-{run}.json", record)
    preconditions()
    admin = c.admin_token()
    private_buckets(admin)
    bucket_validation(admin, args)
    public_bucket(admin, args, record)
    route_matrix(admin, args, record)
    no_fallback()
    public_file(record)
    private_file(record)
    reader = c.synthetic_person(admin, run, record, "reader",
                                [c.PERM_FILE_RECORD_READ, c.PERM_STORAGE_READ, c.PERM_PLATFORM_CONFIGURATION_READ])
    file_records(admin, reader, record)
    scan_policies(admin, run, record)
    if not args.skip_regression:
        regression(admin, record)
    runtime(since)
    record["finishedAt"] = c.now_iso()
    record["results"] = c.results
    path = c.save(f"run-{run}.json", record)
    print(f"运行记录 {path}；清理：python3 -I stage7_cleanup.py {path}")
    if not c.summary():
        raise SystemExit(1)


if __name__ == "__main__":
    main()
