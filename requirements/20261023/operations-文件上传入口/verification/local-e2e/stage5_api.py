"""阶段 5 本机验收的接口部分（由 stage5_browser.mjs 逐步调用，也可单独运行）。每个子命令输出一行 JSON。
只记录状态码、错误代码 / 原因与必要字段；不输出口令、令牌与签名地址。合成人员凭据只写 scratchpad（0600），不进结果文件。
用法：python3 stage5_api.py <命令> [参数]
  constraints | files | precheck-reject | person <run> <label> [permissionId...] | viewer-start <label> | unroute | wait-configured <true|false> | restore
  public-get <url> <sha256> | card-start | log-mark | log-scan <offset> | cleanup
"""
import hashlib
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
SCRATCH = Path(os.environ.get("STAGE5_SCRATCH", "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/b348a3f3-d233-4740-85e3-de4698ec8851/scratchpad"))
os.environ.setdefault("PB7_STATE", str(SCRATCH / "pb7-state"))
sys.path.insert(0, str(ROOT / "requirements/20261022/platform-support-文件公开桶/release/acceptance"))
import pb7_common as c  # noqa: E402

LOCAL = ROOT / "local-environment"
c.ORIGIN = "https://127.0.0.1:18444"
_secrets = c.read_env(LOCAL / "e2e/secrets.env")
UNVERIFIED = ssl._create_unverified_context()
urllib.request.install_opener(urllib.request.build_opener(urllib.request.HTTPSHandler(context=UNVERIFIED)))
UPLOAD = "/file-upload/v1"
SOURCE = "operations-console-bff"
REASON = "运营控制台文件上传入口阶段 5 本机验收"
PEOPLE = SCRATCH / "stage5-people.json"
PERM_FILE_UPLOAD_READ = "900000000000136101"
OPS_LOG = LOCAL / "logs/ops-bff.log"


def out(**fields):
    print(json.dumps(fields, ensure_ascii=False, default=str), flush=True)


def admin():
    return c.login(_secrets["E2E_ADMIN_EMAIL"], _secrets["E2E_ADMIN_PASSWORD"])


def problem(body):
    return {"code": body.get("code"), "detail": body.get("detail")}


def files_total(token):
    status, body, _ = c.raw(token, "GET", f"{UPLOAD}/files?" + window() + "&mine=false&page=1&size=10")
    return status, body.get("total")


def window():
    start, end = c.created_window(60 * 24, 5)
    return f"createdFrom={start}&createdTo={end}"


def start(token, name, mime, size):
    return c.raw(token, "POST", f"{UPLOAD}/uploads", {"filename": name, "mimeType": mime, "sizeBytes": str(size)})


def cmd_constraints():
    status, body, _ = c.raw(admin(), "GET", f"{UPLOAD}/constraints")
    out(status=status, constraints=body)


def cmd_files():
    token = admin()
    status, total = files_total(token)
    out(status=status, total=total)


def cmd_precheck_reject():
    """AC-02 接口：不允许的类型与超过 20 MiB 直接调开始上传都应 422，且最近上传的数量不变。"""
    token = admin()
    _, before = files_total(token)
    s1, b1, _ = start(token, "stage5.svg", "image/svg+xml", 512)
    s2, b2, _ = start(token, "stage5-big.png", "image/png", 20 * 1024 * 1024 + 1)
    _, after = files_total(token)
    out(svg={"status": s1, **problem(b1)}, oversize={"status": s2, **problem(b2)}, totalBefore=before, totalAfter=after)


def people():
    return json.loads(PEOPLE.read_text()) if PEOPLE.exists() else {}


def cmd_person(run, label, *extra):
    """AC-04：经 IAM 新建只含 FILE_UPLOAD_READ（及传入的额外权限）的合成角色与人员，凭据写 scratchpad（0600）。"""
    token = admin()
    record = {"run": run}
    permissions = [PERM_FILE_UPLOAD_READ, *extra]
    viewer = c.synthetic_person(token, run, record, label, permissions)
    person = record["people"][label]
    saved = people()
    saved[label] = {"email": person["email"], "password": person["password"], "accountId": person["accountId"], "roleId": record["platformRoles"][0]}
    PEOPLE.write_text(json.dumps(saved))
    os.chmod(PEOPLE, 0o600)
    out(status="created", accountId=person["accountId"], roleId=record["platformRoles"][0], permissions=permissions, tokenObtained=bool(viewer))


def viewer_token(label):
    person = people()[label]
    return c.login(person["email"], person["password"])


def cmd_viewer_start(label):
    token = viewer_token(label)
    s1, b1, _ = c.raw(token, "GET", f"{UPLOAD}/constraints")
    s2, _, _ = c.raw(token, "GET", f"{UPLOAD}/files?" + window() + "&mine=false&page=1&size=10")
    s3, b3, _ = start(token, "stage5-viewer.png", "image/png", 1024)
    out(constraints=s1, configured=b1.get("configured"), files=s2, start={"status": s3, **problem(b3)})


def cmd_unroute():
    """AC-03：经存储管理接口删除 (operations-console-bff, PUBLIC) 写入规则；只删这一条。"""
    token = admin()
    _, routes, _ = c.routes(token)
    route = routes.get((SOURCE, "PUBLIC"))
    if not route:
        out(status="absent")
        return
    status, body, _ = c.raw(token, "DELETE", f"{c.ADMIN}/routes/{SOURCE}/PUBLIC", {"expectedVersion": route.get("version"), "reason": REASON})
    _, routes, _ = c.routes(token)
    out(status=status, detail=body.get("detail"), remaining=sorted(f"{s}/{l}" for s, l in routes))


def cmd_wait_configured(expected):
    """等存储快照刷新：每 5 秒查一次限制提示，直到 configured 等于期望值，最多 120 秒；返回耗时与开始上传结果。"""
    want = expected == "true"
    token = admin()
    began = time.time()
    while True:
        status, body, _ = c.raw(token, "GET", f"{UPLOAD}/constraints")
        if status == 200 and body.get("configured") is want or time.time() - began > 120:
            break
        time.sleep(5)
    s, b, _ = start(token, "stage5-unconfigured.png", "image/png", 1024) if not want else (None, {}, None)
    out(status=status, configured=body.get("configured"), seconds=round(time.time() - began), start={"status": s, **problem(b)} if s else None,
        routeSourceSystem=body.get("routeSourceSystem"), bucketName=body.get("bucketName"))


def cmd_restore():
    result = subprocess.run([sys.executable, str(HERE / "stage5_setup.py")], capture_output=True, text=True)
    token = admin()
    _, routes, _ = c.routes(token)
    route = routes.get((SOURCE, "PUBLIC")) or {}
    out(exit=result.returncode, setupOutput=result.stdout.strip().splitlines()[-6:],
        route={k: route.get(k) for k in ("instanceCode", "bucketName", "scanPolicyCode", "maxUploadSeconds")},
        privateRoute={k: (routes.get((SOURCE, "PRIVATE")) or {}).get(k) for k in ("instanceCode", "bucketName", "scanPolicyCode")})


def cmd_public_get(url, digest):
    """AC-01：不带任何凭据读取公开地址，核对字节摘要。只接受本机公开桶地址。"""
    if not url.startswith("https://127.0.0.1:19400/mor-platform-public/"):
        out(status="rejected-host")
        return
    request = urllib.request.Request(url, headers={"Accept": "*/*"})
    with urllib.request.urlopen(request, timeout=30, context=UNVERIFIED) as response:
        data = response.read()
        out(status=response.status, contentType=response.headers.get("Content-Type"), bytes=len(data),
            sha256Match=hashlib.sha256(data).hexdigest() == digest)


def cmd_card_start():
    """AC-08：卡 BIN 开始上传（不直传、不导入），期望 201。"""
    status, body, _ = c.raw(admin(), "POST", "/card-admin/v1/bin-library/uploads", {"fileName": "stage5-regression.csv", "sizeBytes": "128"})
    out(status=status, uploadId=body.get("uploadId"), hasAuthorization=bool(body.get("authorization")), **({} if status == 201 else problem(body)))


def cmd_log_mark():
    out(offset=OPS_LOG.stat().st_size)


def cmd_log_scan(offset):
    data = OPS_LOG.read_bytes()[int(offset):].decode(errors="replace")
    hits = [marker for marker in ("X-Amz-Signature", "X-Amz-Credential", "x-amz-signature", "X-Amz-Algorithm") if marker in data]
    out(bytesScanned=len(data), lines=data.count("\n"), markers=hits, fileUploadLines=data.count("/file-upload/v1"))


def cmd_cleanup():
    """删除合成人员与角色（IAM 删除接口；需要版本号，先查列表）。"""
    token = admin()
    report = {label: cleanup_person(token, person) for label, person in people().items() if isinstance(person, dict)}
    PEOPLE.unlink(missing_ok=True)
    out(**report)


def cleanup_person(token, person):
    report = {}
    page, account = 1, None
    while account is None:
        status, body, _ = c.raw(token, "GET", f"/iam/v1/accounts?page={page}&size=100")
        items = body.get("items") or []
        account = next((a for a in items if a.get("accountId") == person["accountId"]), None)
        if account or not items or page * 100 >= (body.get("total") or 0):
            break
        page += 1
    if account:
        status, body, _ = c.raw(token, "DELETE", f"/iam/v1/accounts/{person['accountId']}",
                                {"expectedVersion": account["version"], "expectedMemberVersion": account["memberVersion"], "reason": REASON})
        report["account"] = {"status": status, **({} if status in (200, 204) else problem(body))}
    else:
        report["account"] = "not found"
    status, role, _ = c.raw(token, "GET", f"/iam/v1/roles/{person['roleId']}")
    if status == 200:
        version = role.get("version") or (role.get("role") or {}).get("version")
        status, body, _ = c.raw(token, "DELETE", f"/iam/v1/roles/{person['roleId']}", {"expectedVersion": version, "reason": REASON})
        report["role"] = {"status": status, **({} if status in (200, 204) else problem(body))}
    else:
        report["role"] = {"lookup": status}
    return report


if __name__ == "__main__":
    name, *args = sys.argv[1:]
    globals()["cmd_" + name.replace("-", "_")](*args)
