"""文件多存储并存阶段 7 第 8 步：经运营「存储管理」登记 mor-test 现有 MinIO（release.md 第 5 节）。

前置：Vault DML、Platform DDL、块 130 注册与激活已完成，三个新镜像 healthy，Gateway Snapshot 已刷新（激活后 ≤ 60 秒）。
步骤（每步先读当前状态，已完成则跳过，可重复执行）：
  1. 新建实例 MINIO：MINIO / STATIC，地址 https://minio.mor-test.localhost:29400，区域 us-east-1，路径风格，私有 CA = test-ca.crt 文本；
     密钥取 test-environment/.env.test 的 MOR_MINIO_PLATFORM_ACCESS_KEY / SECRET_KEY（与原 FILES_ONBOARDING_MINIO_* 同一账号），
     只在内存中使用，经 Platform 写入 Vault，不打印、不写入运行记录；
  2. 登记桶 mor-platform-files；3. 检查（必须 OK）；4. 启用桶；5. 启用实例；
  6. 新建默认路由 * → MINIO / mor-platform-files，扫描策略 CLIENT_ONBOARDING_FORMAT，上传期限 900 秒（旧默认值）。
完成后 Platform 入驻上传恢复（写入路由），旧文件按记录坐标 MINIO / mor-platform-files 读取恢复。
运行记录：FS7_STATE/register-<run>.json（0600，不含密钥）。之后执行 apply_sql.sh <备份目录> storage-check。
用法：python3 -I stage7_register_storage.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fs7_common as c  # noqa: E402

REASON = "文件多存储并存阶段 7：登记 mor-test 现有 MinIO（原 FILES_ONBOARDING_MINIO_* 配置迁入存储管理）"


def mutation(step, record, status, body, expected=(200, 201)):
    record["steps"].append({"step": step, "status": status, "objectCode": body.get("objectCode"),
                            "resultStatus": body.get("status"), "version": body.get("version"),
                            "code": body.get("code"), "detail": body.get("detail")})
    c.save(f"register-{record['run']}.json", record)
    if not c.check("登记", f"{step}（{status}）", status in expected, body):
        raise SystemExit(f"{step} 失败，停止；已完成的步骤保留，修正后重跑本脚本会从这一步继续")


def ensure_instance(admin, record):
    status, items, _ = c.instances(admin)
    if status != 200:
        raise SystemExit(f"读取实例列表失败 {status}；确认块 130 已激活且 Gateway Snapshot 已刷新")
    if c.INSTANCE in items:
        record["steps"].append({"step": "新建实例", "skipped": "已存在"})
        return
    access_key, secret_key = c.storage_secret()
    body = {"instanceCode": c.INSTANCE, "storageType": "MINIO", "endpoint": c.ENDPOINT, "region": c.REGION,
            "pathStyleAccess": True, "trustedCertificatePem": c.CA_FILE.read_text(), "credentialMode": "STATIC",
            "accessKey": access_key, "secretKey": secret_key, "reason": REASON}
    status, result, _ = c.raw(admin, "POST", c.ADMIN + "/instances", body)
    mutation("新建实例 MINIO", record, status, result)


def ensure_bucket(admin, record):
    _, items, _ = c.instances(admin)
    if c.bucket_of(items.get(c.INSTANCE), c.BUCKET):
        record["steps"].append({"step": "登记桶", "skipped": "已存在"})
        return
    status, result, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets", {"bucketName": c.BUCKET, "reason": REASON})
    mutation(f"登记桶 {c.BUCKET}", record, status, result)


def ensure_bucket_active(admin, record):
    _, items, _ = c.instances(admin)
    bucket = c.bucket_of(items.get(c.INSTANCE), c.BUCKET)
    if bucket.get("status") == "ACTIVE":
        record["steps"].append({"step": "检查并启用桶", "skipped": "已 ACTIVE"})
        return
    status, result, _ = c.raw(admin, "POST", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{c.BUCKET}/check", typed_empty=True)
    record["check"] = {"status": status, "result": result.get("result"), "checkedAt": result.get("checkedAt"),
                       "version": result.get("version"), "code": result.get("code"), "detail": result.get("detail")}
    c.save(f"register-{record['run']}.json", record)
    if not c.check("登记", f"桶检查结果 OK（{status} {result.get('result')}）", status == 200 and result.get("result") == "OK", result):
        raise SystemExit("桶检查未通过：核对 MinIO 桶版本控制 / 加密、Platform 账号策略与 Vault 规则（sql-vault.out）")
    status, result, _ = c.raw(admin, "PATCH", f"{c.ADMIN}/instances/{c.INSTANCE}/buckets/{c.BUCKET}/status",
                              {"status": "ACTIVE", "expectedVersion": result["version"], "reason": REASON})
    mutation("启用桶", record, status, result)


def ensure_instance_active(admin, record):
    _, items, _ = c.instances(admin)
    instance = items[c.INSTANCE]
    if instance.get("status") == "ACTIVE":
        record["steps"].append({"step": "启用实例", "skipped": "已 ACTIVE"})
        return
    status, result, _ = c.raw(admin, "PATCH", f"{c.ADMIN}/instances/{c.INSTANCE}/status",
                              {"status": "ACTIVE", "expectedVersion": instance["version"], "reason": REASON})
    mutation("启用实例", record, status, result)


def ensure_default_route(admin, record):
    _, items, _ = c.routes(admin)
    route = items.get(c.DEFAULT_ROUTE)
    if route:
        same = (route.get("instanceCode"), route.get("bucketName"), route.get("scanPolicyCode"), route.get("maxUploadSeconds")) \
            == (c.INSTANCE, c.BUCKET, c.SCAN_POLICY, c.UPLOAD_SECONDS)
        record["steps"].append({"step": "默认路由", "skipped": "已存在", "sameTarget": same})
        c.check("登记", "已有默认路由指向 MINIO / mor-platform-files / CLIENT_ONBOARDING_FORMAT / 900", same, route)
        return
    status, result, _ = c.raw(admin, "POST", c.ADMIN + "/routes", {
        "sourceSystem": c.DEFAULT_ROUTE, "instanceCode": c.INSTANCE, "bucketName": c.BUCKET,
        "scanPolicyCode": c.SCAN_POLICY, "maxUploadSeconds": c.UPLOAD_SECONDS, "reason": REASON})
    mutation("新建默认路由 *", record, status, result)


def main():
    run = c.datetime.now(c.timezone.utc).strftime("%Y%m%d%H%M%S")
    record = {"run": run, "startedAt": c.now_iso(), "steps": []}
    c.save(f"register-{run}.json", record)
    admin = c.admin_token()
    ensure_instance(admin, record)
    ensure_bucket(admin, record)
    ensure_bucket_active(admin, record)
    ensure_instance_active(admin, record)
    ensure_default_route(admin, record)
    _, items, text = c.instances(admin)
    instance = items.get(c.INSTANCE) or {}
    bucket = c.bucket_of(instance, c.BUCKET) or {}
    access_key, secret_key = c.storage_secret()
    c.check("登记", "实例 MINIO ACTIVE、STATIC、密钥已配置；桶 ACTIVE 且最近检查 OK",
            instance.get("status") == "ACTIVE" and instance.get("credentialMode") == "STATIC" and instance.get("credentialConfigured")
            and bucket.get("status") == "ACTIVE" and bucket.get("lastCheckResult") == "OK",
            {"instance": instance.get("status"), "bucket": bucket.get("status"), "check": bucket.get("lastCheckResult")})
    c.check("登记", "实例列表响应不含存储密钥原值", access_key not in text and secret_key not in text)
    record["finishedAt"] = c.now_iso()
    record["final"] = {"instanceVersion": instance.get("version"), "bucketVersion": bucket.get("version"),
                       "bucketCheckedAt": bucket.get("lastCheckTime")}
    path = c.save(f"register-{run}.json", record)
    print(f"运行记录 {path}")
    if not c.summary():
        raise SystemExit(1)


if __name__ == "__main__":
    main()
