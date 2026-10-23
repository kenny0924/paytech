"""billing 支付事实自动登记 · Q-2：在 mor-test 建 DEMO_NI 渠道成本方案并经 Maker-Checker 批准（阶段 7 第 4 步，复核方式 A）。

经运营入口（Nginx 28080 → Gateway → ops-bff → channel-connector / Platform）调用控制台页面使用的同一组接口；
makerId / checkerId 由 ops-bff 以会话 Account ID 补入，页面与本脚本都不能自报。
  1. 管理员（test-environment/private/access.md）登录；确认 DEMO_NI_COST 不存在。
  2. IAM 新建合成复核角色（CHANNEL_CONFIG_READ / CHANNEL_COST_READ / CHANNEL_COST_APPROVE / CHANNEL_CHANGE_READ）与合成复核人员，
     首登改口令（口令随机生成、不记录、不打印；人员保留，用途写执行记录）。
  3. 管理员建方案 DEMO_NI_COST（渠道 DEMO_NI 238508277900615680）→ 版本 1（生效时间 = 现在 − 2 分钟）：
     范围 MID 238508278684950528 / AE001 / DEMO-NI-AGR-001 / AE / USD / APPLE_PAY / PAY，
     组成 PROCESSING_PCT / PROCESSING_FEE / TRANSACTION_AMOUNT / 0.005 / HALF_UP（无固定费）→ 提交审批。
  4. 合成复核人以版本 contentHash 批准 → 生效时间已到 → ACTIVE。
用法：E2E_TARGET=test python3 -I setup_cost_plan.py [--out <记录 json>]；默认 dry-run 只做第 1 步，加 --apply 执行 2–4。
"""
import base64
import json
import secrets as pysecrets
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

BASE = "/channel-admin/v1"
CHANNEL_ID, MID_ID, ENTITY_NO = "238508277900615680", "238508278684950528", "AE001"
PLAN_CODE, AGREEMENT_REF = "DEMO_NI_COST", "DEMO-NI-AGR-001"
PERM = {"CONFIG_READ": "900000000000092101", "COST_READ": "900000000000092104",
        "COST_APPROVE": "900000000000092106", "CHANGE_READ": "900000000000092107"}
RUN = uuid.uuid4().hex[:6].upper()
record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat()}


def raw(token, method, path, body=None, prefix="/api/operations" + BASE):
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(12):
        request = urllib.request.Request(e2e_http.OPERATIONS + prefix + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=e2e_http.TLS, timeout=30) as response:
                text = response.read().decode()
                return response.status, json.loads(text) if text else {}
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 11:
                time.sleep(int(failure.headers.get("Retry-After") or 10))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def iam(token, method, path, body=None):
    return raw(token, method, path, body, prefix="/api/operations")


def login(identifier, password):
    return e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                         {"loginIdentifier": identifier, "password": password})["accessToken"]


def jwt_sub(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]


def step(name, ok, evidence):
    print(("OK   " if ok else "FAIL ") + name, json.dumps(evidence, ensure_ascii=False)[:400])
    if not ok:
        sys.exit(1)


def synthetic_checker(admin):
    reason = "billing 支付事实自动登记 阶段 7：DEMO_NI 渠道成本方案复核（合成复核人，方式 A）"
    status, role = iam(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_BILLB_CHECKER_{RUN}", "roleName": f"渠道成本合成复核 {RUN}",
        "permissionIds": [PERM["CONFIG_READ"], PERM["COST_READ"], PERM["COST_APPROVE"], PERM["CHANGE_READ"]], "reason": reason})
    step("IAM 新建合成复核角色", status in (200, 201) and role.get("roleId"), {"status": status, "roleId": role.get("roleId")})
    email = f"billb-checker-{RUN.lower()}@example.test"
    initial = "Billb initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Billb checker " + pysecrets.token_urlsafe(18) + "!"
    status, created = iam(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": reason})
    step("IAM 新建合成复核人员", status in (200, 201) and created.get("accountId"),
         {"status": status, "accountId": created.get("accountId"), "email": email})
    first = login(email, initial)
    e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/password", "POST",
                  {"currentPassword": initial, "newPassword": password}, first)
    record.update({"checkerRoleId": role["roleId"], "checkerRoleCode": f"QA_BILLB_CHECKER_{RUN}",
                   "checkerAccountId": created["accountId"], "checkerEmail": email})
    return login(email, password), created["accountId"]


def version_body():
    effective = datetime.now(timezone.utc) - timedelta(minutes=2)
    component = {"componentCode": "PROCESSING_PCT", "componentType": "PROCESSING_FEE", "calculationBase": "TRANSACTION_AMOUNT",
                 "rateValue": "0.005", "fixedAmount": None, "currency": None, "roundingMode": "HALF_UP"}
    scope = {"scopeNo": 1, "midId": MID_ID, "entityNo": ENTITY_NO, "channelAgreementRef": AGREEMENT_REF, "countryCode": "AE",
             "currency": "USD", "paymentMethodCode": "APPLE_PAY", "actionType": "PAY", "components": [component]}
    return {"effectiveFrom": effective.strftime("%Y-%m-%dT%H:%M:%SZ"), "scopes": [scope]}


def main():
    apply = "--apply" in sys.argv
    out = Path(sys.argv[sys.argv.index("--out") + 1]) if "--out" in sys.argv else None
    s = e2e_http.secrets()
    admin = login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    record["makerAccountId"] = jwt_sub(admin)
    status, existing = raw(admin, "GET", f"/channel-cost-plans/{PLAN_CODE}")
    step("DEMO_NI_COST 尚不存在（Owner 返回 400 BUSINESS_CHECK_FAILED「渠道成本方案不存在」）",
         status == 400 and "不存在" in (existing.get("detail") or ""), {"status": status, "detail": existing.get("detail")})
    status, plans = raw(admin, "GET", f"/channel-cost-plans?channelId={CHANNEL_ID}&page=1&size=20")
    step("DEMO_NI 渠道下无成本方案", status == 200 and plans.get("total") == 0, {"status": status, "total": plans.get("total")})
    if not apply:
        print("dry-run 结束：加 --apply 执行 IAM 合成复核人、建方案 / 版本、提交、批准")
        return
    checker, checker_id = synthetic_checker(admin)
    status, plan = raw(admin, "POST", "/channel-cost-plans", {"channelId": CHANNEL_ID, "planCode": PLAN_CODE})
    step("新建方案 DEMO_NI_COST", status in (200, 201), {"status": status, "planCode": plan.get("planCode"), "id": plan.get("id") or plan.get("planId")})
    record["plan"] = plan
    path = f"/channel-cost-plans/{PLAN_CODE}"
    status, v1 = raw(admin, "POST", path + "/versions", version_body())
    cost_ver = v1.get("costVer")
    step("新建版本 1", status in (200, 201) and cost_ver is not None, {"status": status, "costVer": cost_ver, "versionId": v1.get("id"), "state": v1.get("status")})
    status, submitted = raw(admin, "POST", f"{path}/versions/{cost_ver}/submit")
    step("提交审批 → PENDING_APPROVAL", status == 200 and submitted.get("status") == "PENDING_APPROVAL",
         {"status": status, "state": submitted.get("status"), "makerId": submitted.get("makerId")})
    content_hash = submitted.get("contentHash") or v1.get("contentHash")
    status, approved = raw(checker, "POST", f"{path}/versions/{cost_ver}/approve", {"approvedContentHash": content_hash})
    step("合成复核人批准 → ACTIVE", status == 200 and approved.get("status") == "ACTIVE" and approved.get("checkerId") == checker_id
         and approved.get("makerId") != checker_id,
         {"status": status, "state": approved.get("status"), "makerId": approved.get("makerId"), "checkerId": approved.get("checkerId")})
    record["version"] = {k: approved.get(k) for k in ("id", "costVer", "status", "makerId", "checkerId", "effectiveFrom", "contentHash")}
    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    if out:
        out.write_text(json.dumps(record, ensure_ascii=False, indent=2))
        print("record:", out)
    print(json.dumps(record, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
