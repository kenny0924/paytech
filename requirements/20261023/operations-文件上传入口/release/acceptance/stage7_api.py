"""阶段 7 mor-test 验收的接口部分（由 stage7_browser.mjs 逐步调用，也可单独运行）。改自阶段 5 本机脚本 verification/local-e2e/stage5_api.py：
入口改为 mor-test 运营 http://localhost:28080（fu7_common / pb7_common），管理员取 test-environment/private/access.md；日志取
mor-test-operations-console-bff-1 的 docker logs；公开地址只接受本入口的 AWS 公开桶。每个子命令输出一行 JSON，只记录状态码、错误代码 /
原因与必要字段；不输出口令、令牌与签名地址。合成人员凭据写运行记录目录（0600），不进结果文件。
用法：python3 -I stage7_api.py <命令> [参数]
  runtime <since-iso>        3 个容器为候选、healthy、重启 0，自 <since> 起 ERROR 数（只读）
  constraints | files        写入限制 / 最近上传总数（只读）
  precheck-reject            AC-02：不允许的类型与超过 20 MiB 直接调开始上传都应 422，最近上传数量不变（被拒绝，不产生上传）
  person <run> <label> [permissionId...] | viewer-start <label>   AC-04：合成只读人员；其开始上传应 403（不产生上传）
  public-get <url> <sha256>  AC-01：不带凭据读取公开地址，核对摘要
  private-rule               AC-08：(operations-console-bff, PRIVATE) 规则不变（只读 SQL）
  log-scan <since-iso>       AC-07：ops-bff 日志不含预签名参数
  cleanup                    删除合成人员与角色
--dry-run（放在命令前）：只核对参数与常量，不登录、不调用任何接口。
"""
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fu7_common as o  # noqa: E402

c = o.c
REASON = "运营控制台文件上传入口阶段 7 mor-test 验收"
PEOPLE = c.STATE / "stage7-people.json"
SIGNATURE_MARKERS = ("X-Amz-Signature", "X-Amz-Credential", "x-amz-signature", "X-Amz-Algorithm")


def out(**fields):
    print(json.dumps(fields, ensure_ascii=False, default=str), flush=True)


def files_total(token):
    status, body, _ = c.raw(token, "GET", f"{o.UPLOAD}/files?{o.window()}&mine=false&page=1&size=10")
    return status, body.get("total")


def cmd_runtime(since):
    report = {}
    for service in o.SERVICES:
        image = o.container(service, "{{.Image}}")
        report[service] = {"candidate": image == o.CANDIDATE[service], "health": c.health(service)}
        if service in o.JAVA_SERVICES:
            report[service]["errors"] = len(c.errors_since(service, since))
    out(**report)


def cmd_constraints():
    status, body = o.constraints(c.admin_token())
    out(status=status, constraints=body)


def cmd_files():
    status, total = files_total(c.admin_token())
    out(status=status, total=total)


def cmd_precheck_reject():
    token = c.admin_token()
    _, before = files_total(token)
    s1, b1, _ = o.start_upload(token, "stage7.svg", "image/svg+xml", 512)
    s2, b2, _ = o.start_upload(token, "stage7-big.png", "image/png", 20 * 1024 * 1024 + 1)
    _, after = files_total(token)
    out(svg={"status": s1, **o.problem(b1)}, oversize={"status": s2, **o.problem(b2)}, totalBefore=before, totalAfter=after)


def people():
    return json.loads(PEOPLE.read_text()) if PEOPLE.exists() else {}


def cmd_person(run, label, *extra):
    token = c.admin_token()
    record = {"run": run}
    permissions = [o.PERM_FILE_UPLOAD_READ, *extra]
    viewer = c.synthetic_person(token, run, record, label, permissions)
    person = record["people"][label]
    saved = people()
    saved[label] = {"email": person["email"], "password": person["password"], "accountId": person["accountId"], "roleId": record["platformRoles"][0]}
    PEOPLE.write_text(json.dumps(saved))
    PEOPLE.chmod(0o600)
    out(status="created", accountId=person["accountId"], roleId=record["platformRoles"][0], permissions=permissions, tokenObtained=bool(viewer))


def cmd_viewer_start(label):
    person = people()[label]
    token = c.login(person["email"], person["password"])
    s1, b1 = o.constraints(token)
    s2, _, _ = c.raw(token, "GET", f"{o.UPLOAD}/files?{o.window()}&mine=false&page=1&size=10")
    s3, b3, _ = o.start_upload(token, "stage7-viewer.png", "image/png", 1024)
    out(constraints=s1, configured=b1.get("configured"), files=s2, start={"status": s3, **o.problem(b3)})


def cmd_public_get(url, digest):
    if not url.startswith(f"https://{o.PUBLIC_HOST}/"):
        out(status="rejected-host")
        return
    status, data = c.object_request("GET", url)
    out(status=status, bytes=len(data), sha256Match=c.sha256_hex(data) == digest)


def cmd_private_rule():
    rows = o.sql_rows("SELECT i.instance_code, b.bucket_name, r.scan_policy_code, r.max_upload_seconds FROM t_file_storage_route r "
                      "JOIN t_file_storage_bucket b ON b.bucket_id = r.bucket_id JOIN t_file_storage_instance i ON i.instance_id = b.instance_id "
                      f"WHERE r.source_system = '{o.SOURCE}' AND r.access_level = 'PRIVATE'")
    out(rule=rows[0] if rows else None, unchanged=rows == [["MINIO", "mor-platform-files", "OPERATIONS_BFF_IMPORT", "900"]])


def cmd_log_scan(since):
    data = c.logs_since(o.SOURCE, since)
    out(bytesScanned=len(data), lines=data.count("\n"), markers=[m for m in SIGNATURE_MARKERS if m in data],
        fileUploadLines=data.count("/file-upload/v1"))


def cmd_cleanup():
    token = c.admin_token()
    report = {label: cleanup_person(token, person) for label, person in people().items() if isinstance(person, dict)}
    PEOPLE.unlink(missing_ok=True)
    out(**report)


def cleanup_person(token, person):
    report, page, account = {}, 1, None
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
        report["account"] = {"status": status, **({} if status in (200, 204) else o.problem(body))}
    else:
        report["account"] = "not found"
    status, role, _ = c.raw(token, "GET", f"/iam/v1/roles/{person['roleId']}")
    if status == 200:
        version = role.get("version") or (role.get("role") or {}).get("version")
        status, body, _ = c.raw(token, "DELETE", f"/iam/v1/roles/{person['roleId']}", {"expectedVersion": version, "reason": REASON})
        report["role"] = {"status": status, **({} if status in (200, 204) else o.problem(body))}
    else:
        report["role"] = {"lookup": status}
    return report


ARITY = {"runtime": 1, "constraints": 0, "files": 0, "precheck-reject": 0, "person": (2, 9), "viewer-start": 1, "public-get": 2,
         "private-rule": 0, "log-scan": 1, "cleanup": 0}


if __name__ == "__main__":
    args = sys.argv[1:]
    dry = args[:1] == ["--dry-run"]
    name, *rest = args[1:] if dry else args
    arity = ARITY[name]
    low, high = (arity, arity) if isinstance(arity, int) else arity
    if not low <= len(rest) <= high:
        raise SystemExit(f"{name} 需要 {low}—{high} 个参数")
    if dry:
        out(dryRun=name, args=len(rest), origin=c.ORIGIN, upload=o.UPLOAD, publicHost=o.PUBLIC_HOST, state=str(c.STATE))
    else:
        globals()["cmd_" + name.replace("-", "_")](*rest)
