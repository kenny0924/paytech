"""阶段 7 mor-test 验收（任务卡 G 第 7 步）：经运营入口 http://localhost:28080（Gateway → Operations BFF → commercial）走商务主链路，
再私网直连 commercial 做一次价格命中与健康检查，最后核对 Web 入口与商务三页。

账号：A = private/access.md 的运营管理员（PLATFORM_ADMIN，test-activate.sql 已授予 5 个商务权限）；
B = 本脚本经运营 IAM 接口新建的合成审批人（合成角色只含 COMMERCIAL_CONTRACT_READ / PRICING_READ / PRICING_APPROVE），
验收结束停用 B、清空合成角色权限（与 test-environment/scripts/roles_smoke.py 的收尾一致）。
合成业务数据（画像、合同、方案、版本、价格）写入 mor_commercial，ID 记入运行记录，由 acceptance-cleanup-test.sql 按 ID 清理。

不打印 Token、口令；连接目标固定 E2E_TARGET=test（见 e2e_http.py）。
用法：E2E_TARGET=test python3 -I stage7_acceptance.py
运行记录：local-environment/e2e/runtime/commercial-stage7-<ts>.json
"""
import json
import os
import secrets as pysecrets
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

os.environ.setdefault("E2E_TARGET", "test")
ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

if e2e_http.TARGET != "test":
    raise SystemExit("本脚本只用于 mor-test（E2E_TARGET=test）")

RUNTIME = ROOT / "local-environment/e2e/runtime"
RUN = uuid.uuid4().hex[:6].upper()
ENTITY_NO = "AE001"
CLIENT_ID = "770000000701"
PERMISSIONS = {"CONTRACT_READ": "900000000000095101", "PRICING_READ": "900000000000095103", "PRICING_APPROVE": "900000000000095105"}
steps = []
record = {"run": RUN, "startedAt": None, "ids": {}, "steps": steps}


def now_iso(delta_seconds=0):
    return (datetime.now(timezone.utc) + timedelta(seconds=delta_seconds)).strftime("%Y-%m-%dT%H:%M:%SZ")


def step(name, fn, expect=200):
    """执行一步，记录状态码与是否符合预期；成功统一记 200。"""
    try:
        status, body = 200, fn()
    except e2e_http.Failure as failure:
        status = failure.status
        try:
            body = json.loads(failure.body)
        except ValueError:
            body = {"raw": failure.body[:300]}
    ok = status == expect
    steps.append({"step": name, "status": status, "expected": expect, "ok": ok,
                  "detail": (None if ok else body)})
    print(f"[{'通过' if ok else '失败'}] {name} → {status}" + ("" if ok else f" {json.dumps(body, ensure_ascii=False)[:300]}"))
    return body if ok else None


def check(name, ok, detail=None):
    steps.append({"step": name, "status": None, "expected": None, "ok": bool(ok), "detail": detail})
    print(f"[{'通过' if ok else '失败'}] {name}" + ("" if ok else f" {detail}"))


def call(session_token, method, path, body=None, key=None):
    if key is None:
        key = method not in ("GET",)
    return e2e_http.call(e2e_http.OPERATIONS, "/api/operations" + path, method, body, session_token, key)


def login(identifier, password):
    tokens = e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                           {"loginIdentifier": identifier, "password": password})
    return tokens


def save():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    path = RUNTIME / f"commercial-stage7-{record['startedAt'].replace(':', '').replace('-', '')}.json"
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    return path


def components(currency, capture_rate):
    return [
        {"componentCode": "CAPTURE_PCT", "componentType": "PERCENTAGE", "triggerCode": "CAPTURE", "calculationBase": "GROSS_AMOUNT",
         "refundBehavior": "RETURN_PRORATA", "rate": capture_rate, "currency": currency},
        {"componentCode": "CAPTURE_FIXED", "componentType": "FIXED", "triggerCode": "CAPTURE", "calculationBase": "FIXED_PER_EVENT",
         "refundBehavior": "KEEP", "fixedAmount": "1.00", "currency": currency},
        {"componentCode": "REFUND_FIXED", "componentType": "FIXED", "triggerCode": "REFUND", "calculationBase": "FIXED_PER_EVENT",
         "refundBehavior": "RETURN_PRORATA", "fixedAmount": "2.00", "currency": currency},
    ]


def price(currency, profile_code, priority, capture_rate):
    return {"entityNo": ENTITY_NO, "currency": currency, "pricingProfileCode": profile_code, "priorityNo": priority,
            "settlementBusinessDays": 2, "businessCalendarCode": "AE_BUSINESS_DAYS", "settlementTimezone": "UTC",
            "cutoffSecondOfDay": 86399, "components": components(currency, capture_rate)}


def setup_checker(admin):
    """经 IAM 接口建合成审批角色与人员 B，返回 (B 的 token, state)。"""
    options = call(admin, "GET", "/iam/v1/role-permission-options")

    def walk(nodes):
        for node in nodes:
            yield node
            yield from walk(node.get("children") or [])
    offered = {node.get("permissionId") for node in walk(options["nodes"])}
    check("IAM 授权选项含 3 个商务权限（合同读、价目读、价目审批）", set(PERMISSIONS.values()) <= offered)
    role = step("IAM 新建合成审批角色", lambda: call(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_COMMERCIAL_CHECKER_{RUN}", "roleName": f"商务审批验收 {RUN}",
        "permissionIds": list(PERMISSIONS.values()), "reason": "commercial 阶段 7 验收：合成审批角色"}))
    if not role:
        return None, None
    record["ids"]["checkerRoleId"] = role["roleId"]
    email = f"commercial-checker-{RUN.lower()}@example.test"
    initial = "Commercial initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Commercial checker " + pysecrets.token_urlsafe(18) + "!"
    created = step("IAM 新建合成审批人员 B", lambda: call(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": "commercial 阶段 7 验收：合成审批人"}))
    if not created:
        return None, None
    record["ids"]["checkerAccountId"] = created["accountId"]
    first = step("B 首次登录", lambda: login(email, initial))
    if not first:
        return None, None
    step("B 修改初始口令", lambda: e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/password", "POST",
                                              {"currentPassword": initial, "newPassword": password}, first["accessToken"]))
    tokens = step("B 以新口令登录", lambda: login(email, password))
    return (tokens["accessToken"] if tokens else None), {"email": email}


def cleanup_checker(admin):
    account_id = record["ids"].get("checkerAccountId")
    role_id = record["ids"].get("checkerRoleId")
    if account_id:
        page = 1
        while True:
            result = call(admin, "GET", f"/iam/v1/accounts?page={page}&size=200")
            row = next((r for r in result["items"] if r["accountId"] == account_id), None)
            if row or page * 200 >= result["total"]:
                break
            page += 1
        if row and row["status"] == "ACTIVE":
            step("收尾：停用合成审批人员 B", lambda: call(admin, "POST", f"/iam/v1/accounts/{account_id}/disable", {
                "expectedVersion": row["version"], "reason": "commercial 阶段 7 验收结束：停用合成审批人"}))
    if role_id:
        current = call(admin, "GET", f"/iam/v1/roles/{role_id}")
        step("收尾：清空合成审批角色权限", lambda: call(admin, "PUT", f"/iam/v1/roles/{role_id}", {
            "roleName": current["roleName"], "permissionIds": [], "expectedVersion": current["version"],
            "reason": "commercial 阶段 7 验收结束：清空合成角色权限"}))


def business_flow(admin, checker):
    ids = record["ids"]
    base = "/commercial-admin/v1"
    step("A 商务 BFF：GET 画像列表", lambda: call(admin, "GET", f"{base}/pricing-profiles"))

    # 画像：具体（VISA / CREDIT / DOMESTIC）与兜底（CARD 无条件），各自激活
    profiles = {}
    for key, code, conditions in (
            ("specific", f"G7_{RUN}_CARD_VISA_CREDIT_DOM", [{"dimensionCode": "CARD_BRAND", "conditionValue": "VISA"},
                                                          {"dimensionCode": "FUNDING_TYPE", "conditionValue": "CREDIT"},
                                                          {"dimensionCode": "ISSUER_REGION", "conditionValue": "DOMESTIC"}]),
            ("fallback", f"G7_{RUN}_CARD_DEFAULT", [])):
        created = step(f"A 创建画像 {key}", lambda: call(admin, "POST", f"{base}/pricing-profiles", {
            "pricingProfileCode": code, "profileKind": "PAYMENT_METHOD", "profileName": f"Stage7 {key} {RUN}",
            "paymentMethodCode": "CARD", "conditions": conditions}))
        if not created:
            return
        profiles[key] = created
        ids.setdefault("pricingProfileIds", []).append(created["pricingProfileId"])
        active = step(f"A 激活画像 {key}", lambda: call(admin, "POST", f"{base}/pricing-profiles/{created['pricingProfileId']}/activate",
                                                    {"expectedChangeVersion": created["changeVersion"]}))
        check(f"画像 {key} 状态 ACTIVE", active and active.get("status") == "ACTIVE", active)

    # 合同：CLIENT，MOR，两方 acceptance 后激活
    agreement = step("A 创建 CLIENT 合同", lambda: call(admin, "POST", f"{base}/agreements", {
        "agreementNo": f"G7-CLIENT-{RUN}", "agreementName": f"Stage7 client agreement {RUN}", "agreementType": "CLIENT",
        "businessMode": "MOR", "fundMode": "SETTLE_THROUGH_PLATFORM", "pricingAuthority": "PLATFORM", "clientId": CLIENT_ID,
        "entityNo": ENTITY_NO, "effectiveFrom": now_iso(-3600),
        "parties": [{"partyId": "880000000701", "partyProfileId": "881000000701", "partyRole": "CLIENT"},
                    {"partyId": "880000000702", "partyProfileId": "881000000702", "partyRole": "OPERATING_ENTITY"}],
        "documents": [{"documentType": "SIGNING_DOCUMENT", "fileId": "990000000701",
                       "contentHash": "fb98189c25640a38ca1169a96dec802e7ac376b1cf9252476a1bc6438b21a86c"}]}))
    if not agreement:
        return
    agreement_id = agreement["agreementId"]
    ids["agreementId"] = agreement_id
    version = agreement["changeVersion"]
    result = step("A 合同 submit-for-signature", lambda: call(admin, "POST", f"{base}/agreements/{agreement_id}/submit-for-signature",
                                                           {"expectedChangeVersion": version}))
    if not result:
        return
    version = result["changeVersion"]
    for party_id, actor in (("880000000701", "770000000799"), ("880000000702", "880000000799")):
        result = step(f"A 合同参与方 {party_id} acceptance", lambda: call(
            admin, "POST", f"{base}/agreements/{agreement_id}/parties/{party_id}/acceptance",
            {"expectedChangeVersion": version, "acceptanceReference": f"G7-ACC-{party_id}-{RUN}",
             "acceptanceMethod": "CLICK_ACCEPTANCE", "acceptedByActorId": actor, "acceptedAt": now_iso(-60)}))
        if not result:
            return
        version = result["changeVersion"]
    check("两方接受后合同 SIGNED", result.get("status") == "SIGNED", result)
    result = step("A 合同 activate", lambda: call(admin, "POST", f"{base}/agreements/{agreement_id}/activate",
                                                 {"expectedChangeVersion": version}))
    if not result:
        return
    check("合同 ACTIVE", result.get("status") == "ACTIVE", result)
    version = result["changeVersion"]
    result = step("A PUT products [ACQUIRING]", lambda: call(admin, "PUT", f"{base}/agreements/{agreement_id}/products",
                                                            {"expectedChangeVersion": version, "productCodes": ["ACQUIRING"]}))
    if not result:
        return
    version = result["changeVersion"]
    capability = step("A POST capability CARD_VISA（AED / USD）", lambda: call(
        admin, "POST", f"{base}/agreements/{agreement_id}/capabilities",
        {"expectedChangeVersion": version, "entityNo": ENTITY_NO, "capabilityCode": "CARD_VISA", "effectiveFrom": now_iso(-3600),
         "rollingReserveRate": "0.05", "rollingReserveDays": 180,
         "limits": [{"limitCode": "CURRENCY", "limitValue": "AED"}, {"limitCode": "CURRENCY", "limitValue": "USD"}]}))
    if not capability:
        return
    ids["agreementCapabilityId"] = capability["agreementCapabilityId"]
    detail = step("A GET 合同详情", lambda: call(admin, "GET", f"{base}/agreements/{agreement_id}"))
    if detail:
        body = detail.get("agreement", detail)
        check("合同详情：ACTIVE、产品 ACQUIRING、开通 CARD_VISA",
              body.get("status") == "ACTIVE" and body.get("productCodes") == ["ACQUIRING"]
              and any(c.get("capabilityCode") == "CARD_VISA" for c in body.get("capabilities") or []),
              {k: body.get(k) for k in ("status", "productCodes")})

    # 条款：Plan → Version → prices → submit(A) → approve(B) → schedule → activate
    plan = step("A 创建条款方案", lambda: call(admin, "POST", f"{base}/commercial-terms/plans", {
        "planCode": f"G7-CLIENT-PRICING-{RUN}", "termType": "CLIENT_PRICING", "agreementId": agreement_id, "clientId": CLIENT_ID}))
    if not plan:
        return
    ids["planId"] = plan["planId"]
    term = step("A 创建版本", lambda: call(admin, "POST", f"{base}/commercial-terms/plans/{plan['planId']}/versions", {
        "expectedChangeVersion": plan["changeVersion"], "pricingModel": "BLENDED", "feeTaxTreatment": "EXCLUSIVE"}))
    if not term:
        return
    version_id = term["versionId"]
    ids["versionId"] = version_id
    specific, fallback = profiles["specific"]["pricingProfileCode"], profiles["fallback"]["pricingProfileCode"]
    prices = [price("AED", specific, 1, "0.025"), price("AED", fallback, 2, "0.030"),
              price("USD", specific, 1, "0.025"), price("USD", fallback, 2, "0.030")]
    result = step("A PUT prices（AED / USD × 具体 / 兜底）", lambda: call(admin, "PUT", f"{base}/commercial-terms/versions/{version_id}/prices",
                                                                       {"expectedChangeVersion": term["changeVersion"], "prices": prices}))
    if not result:
        return
    change = result["changeVersion"]
    result = step("A submit", lambda: call(admin, "POST", f"{base}/commercial-terms/versions/{version_id}/submit",
                                           {"expectedChangeVersion": change}))
    if not result:
        return
    change = result["changeVersion"]
    step("A approve 自己提交的版本被拒（批准人 ≠ 提交人）", lambda: call(
        admin, "POST", f"{base}/commercial-terms/versions/{version_id}/approve", {"expectedChangeVersion": change}), expect=400)
    if not checker:
        check("B approve", False, "缺少合成审批人 B")
        return
    result = step("B approve", lambda: call(checker, "POST", f"{base}/commercial-terms/versions/{version_id}/approve",
                                            {"expectedChangeVersion": change}))
    if not result:
        return
    check("版本 APPROVED", result.get("status") == "APPROVED", result)
    change = result["changeVersion"]
    effective_from = now_iso(30)
    result = step("A schedule（生效 = 当前 + 30 秒）", lambda: call(
        admin, "POST", f"{base}/commercial-terms/versions/{version_id}/schedule",
        {"expectedChangeVersion": change, "effectiveFrom": effective_from}))
    if not result:
        return
    change = result["changeVersion"]
    ids["effectiveFrom"] = effective_from
    wait = (datetime.strptime(effective_from, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
            - datetime.now(timezone.utc)).total_seconds() + 3
    time.sleep(max(wait, 0))
    result = step("A activate", lambda: call(admin, "POST", f"{base}/commercial-terms/versions/{version_id}/activate",
                                             {"expectedChangeVersion": change}))
    if result:
        check("版本 ACTIVE", result.get("status") == "ACTIVE", result)
    workspace = step("A GET versions/{id} 聚合", lambda: call(admin, "GET", f"{base}/commercial-terms/versions/{version_id}"))
    if workspace:
        check("聚合含版本 ACTIVE、合同与 4 条价格",
              workspace.get("version", {}).get("status") == "ACTIVE" and workspace.get("agreement", {}).get("agreementId") == agreement_id
              and len(workspace.get("prices") or []) == 4,
              {"status": workspace.get("version", {}).get("status"), "prices": len(workspace.get("prices") or [])})
    listed = step("A GET versions/{id}/prices（entityNo、termType、currency=USD）", lambda: call(
        admin, "GET", f"{base}/commercial-terms/versions/{version_id}/prices?entityNo={ENTITY_NO}&termType=CLIENT_PRICING&currency=USD"))
    if listed:
        check("USD 价格 2 条", len(listed.get("prices") or []) == 2, {"prices": len(listed.get("prices") or [])})


def direct_checks():
    ids = record["ids"]
    if ids.get("versionId"):
        body = {"termVersionId": ids["versionId"], "entityNo": ENTITY_NO, "agreementId": ids["agreementId"],
                "contextAsOfTime": now_iso(), "termType": "CLIENT_PRICING", "currency": "USD", "clientId": CLIENT_ID,
                "paymentFactId": "660000000701",
                "paymentFacts": {"paymentMethodCode": "CARD",
                                 "dimensions": {"CARD_BRAND": "VISA", "FUNDING_TYPE": "CREDIT", "ISSUER_REGION": "DOMESTIC"}}}
        status, text = e2e_http.private_call("commercial", "POST", "/commercial-prices/v1/resolutions", body=body)
        hit = json.loads(text) if status == 200 else {}
        check(f"commercial 直连 /commercial-prices/v1/resolutions → {status}，命中具体画像",
              status == 200 and hit.get("pricingProfileCode", "").endswith("CARD_VISA_CREDIT_DOM"),
              {"status": status, "body": text[:300]} if status != 200 else {"priceId": hit.get("priceId")})
    status, text = e2e_http._probe_call("GET", "http://commercial-service:19108/actuator/health", {}, None, 30)
    check(f"commercial /actuator/health → {status} UP", status == 200 and json.loads(text).get("status") == "UP", text[:200])


def web_checks():
    with urllib.request.urlopen(e2e_http.OPERATIONS + "/", timeout=10) as response:
        index = response.read().decode()
    check("Web index.html 引用新入口 assets/index-CZvT-J3j.js", "assets/index-CZvT-J3j.js" in index)
    for route in ("/commercial/agreements", "/commercial/pricing-profiles", "/commercial/terms"):
        with urllib.request.urlopen(e2e_http.OPERATIONS + route, timeout=10) as response:
            check(f"Web GET {route} → {response.status}", response.status == 200)


def main():
    record["startedAt"] = now_iso()
    s = e2e_http.secrets()
    admin_tokens = step("A 登录运营入口", lambda: login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]))
    if not admin_tokens:
        save()
        return 1
    admin = admin_tokens["accessToken"]
    navigation = call(admin, "GET", "/authorization/v1/navigation")
    codes = {r["resourceCode"] for r in navigation["resources"]}
    check("A 导航含商务菜单与 4 个按钮", {"OPERATIONS_COMMERCIAL", "OPERATIONS_COMMERCIAL_AGREEMENTS", "OPERATIONS_COMMERCIAL_PRICING_PROFILES",
                                   "OPERATIONS_COMMERCIAL_TERMS", "OPERATIONS_COMMERCIAL_AGREEMENT_MANAGE", "OPERATIONS_COMMERCIAL_PROFILE_MANAGE",
                                   "OPERATIONS_COMMERCIAL_TERM_MANAGE", "OPERATIONS_COMMERCIAL_TERM_APPROVE"} <= codes)
    checker = None
    try:
        checker, _ = setup_checker(admin)
        business_flow(admin, checker)
    finally:
        try:
            cleanup_checker(admin)
        except Exception as failure:  # noqa: BLE001 - 收尾失败计入结果并保留运行记录
            check("收尾：合成审批人 / 角色", False, str(failure)[:300])
        path = save()
    direct_checks()
    web_checks()
    path = save()
    passed = sum(1 for s_ in steps if s_["ok"])
    print(f"\n{passed}/{len(steps)} 通过；运行记录 {path.relative_to(ROOT)}")
    return 0 if passed == len(steps) else 1


if __name__ == "__main__":
    sys.exit(main())
