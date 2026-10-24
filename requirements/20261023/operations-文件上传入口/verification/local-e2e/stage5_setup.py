"""阶段 5 本机联调：经运营「存储管理」接口登记本机 MinIO 公开桶、公开检查策略 PUBLIC_DOCUMENT 与写入规则
(operations-console-bff, PUBLIC)。可重跑：已存在的跳过。只打印状态码与必要字段，不打印口令和令牌。
用法：python3 stage5_setup.py
"""
import os
import ssl
import sys
import urllib.request
from pathlib import Path

ROOT = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(ROOT / "requirements/20261022/platform-support-文件公开桶/release/acceptance"))
import pb7_common as c  # noqa: E402

LOCAL = ROOT / "local-environment"
c.ORIGIN = "https://127.0.0.1:18444"
_secrets = c.read_env(LOCAL / "e2e/secrets.env")
c.admin_token = lambda: c.login(_secrets["E2E_ADMIN_EMAIL"], _secrets["E2E_ADMIN_PASSWORD"])
urllib.request.install_opener(urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))

REASON = "运营控制台文件上传入口阶段 5 本机联调"
INSTANCE, BUCKET = "MINIO", "mor-platform-public"
BASE_URL = "https://127.0.0.1:19400/mor-platform-public"
POLICY = ("PUBLIC_DOCUMENT", ["application/pdf", "image/png", "image/jpeg"], 20 * 1024 * 1024)
SOURCE = "operations-console-bff"


def bucket(admin):
    base = f"{c.ADMIN}/instances/{INSTANCE}/buckets"
    _, items, _ = c.instances(admin)
    existing = c.bucket_of(items.get(INSTANCE), BUCKET)
    if not existing:
        s, b, _ = c.raw(admin, "POST", base, {"bucketName": BUCKET, "accessLevel": "PUBLIC", "publicBaseUrl": BASE_URL, "reason": REASON})
        print("登记公开桶", s, b.get("status"), b.get("detail"))
    elif existing.get("status") == "ACTIVE":
        print("公开桶已启用，跳过")
        return
    s, check, _ = c.raw(admin, "POST", f"{base}/{BUCKET}/check", typed_empty=True)
    print("检查", s, check.get("result"))
    s, b, _ = c.raw(admin, "PATCH", f"{base}/{BUCKET}/status", {"status": "ACTIVE", "expectedVersion": check.get("version"), "reason": REASON})
    print("启用", s, b.get("status"), b.get("detail"))


def policy(admin):
    code, types, size = POLICY
    _, listing, _ = c.raw(admin, "GET", c.ADMIN + "/scan-policies")
    if any(i.get("policyCode") == code for i in listing.get("items") or []):
        print("策略已存在，跳过", code)
        return
    s, _, _ = c.raw(admin, "POST", c.ADMIN + "/scan-policies",
                    {"policyCode": code, "allowedMimeTypes": types, "maxSizeBytes": str(size), "reason": REASON})
    print("新建策略", code, s)
    _, d, _ = c.raw(admin, "GET", f"{c.ADMIN}/scan-policies/{code}")
    rev = next(r for r in d.get("revisions") or [] if r.get("revisionNo") == 1)
    s, b, _ = c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/revisions/1/activation",
                    {"expectedVersion": rev.get("version"), "reason": REASON})
    print("激活 r1", s, b.get("detail") if s != 200 else "")


def route(admin):
    _, routes, _ = c.routes(admin)
    if (SOURCE, "PUBLIC") in routes:
        print("写入规则已存在，跳过")
    else:
        s, b, _ = c.raw(admin, "POST", c.ADMIN + "/routes", {
            "sourceSystem": SOURCE, "accessLevel": "PUBLIC", "instanceCode": INSTANCE, "bucketName": BUCKET,
            "scanPolicyCode": POLICY[0], "maxUploadSeconds": 900, "reason": REASON})
        print("新建写入规则", s, b.get("detail") if s not in (200, 201) else "")
    _, routes, _ = c.routes(admin)
    for k, v in sorted(routes.items()):
        print(" ", k, v.get("instanceCode"), v.get("bucketName"), v.get("scanPolicyCode"), v.get("maxUploadSeconds"))


if __name__ == "__main__":
    token = c.admin_token()
    bucket(token)
    policy(token)
    route(token)
