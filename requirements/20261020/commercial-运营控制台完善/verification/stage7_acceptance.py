"""第二切片阶段 7 mor-test 验收（release/acceptance.md F1–F9 与运行观察）：经运营入口 http://localhost:28080 走 Gateway → ops-bff → commercial。

账号：A = test-environment/private/access.md 的 PLATFORM_ADMIN；B = 经 IAM 接口新建的合成审批人（合同读、价目读、价目审批）；
C = 合成只读人员（只有 COMMERCIAL_PRICING_READ，用于 F2-6 / F4-4 的 403）。验收结束停用 B、C，清空合成角色权限。
合成商务数据带 RUN 前缀，ID 写入运行记录 /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/runtime/commercial-ops2-stage7-<ts>.json，
由 acceptance-cleanup-test.sql 按 ID 清理。CL-F1105E 只读。不打印 Token、口令。
用法：E2E_TARGET=test python3 -I stage7_acceptance.py
"""
import json
import os
import secrets as pysecrets
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

os.environ.setdefault("E2E_TARGET", "test")
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

if e2e_http.TARGET != "test":
    raise SystemExit("本脚本只用于 mor-test（E2E_TARGET=test）")

RUNTIME = MAIN / "local-environment/e2e/runtime"
RUN = uuid.uuid4().hex[:6].upper()
ENTITY_NO = "AE001"
CLIENT_ID = "770000000801"
REAL_CLIENT = "237610139979456513"
REAL_AGREEMENT = "238132079668301824"
BASE = "/commercial-admin/v1"
READ_CONTRACT, READ_PRICING, APPROVE = "900000000000095101", "900000000000095103", "900000000000095105"
steps = []
record = {"run": RUN, "startedAt": None, "ids": {}, "steps": steps}


def now_iso(delta_seconds=0):
    return (datetime.now(timezone.utc) + timedelta(seconds=delta_seconds)).strftime("%Y-%m-%dT%H:%M:%SZ")


def raw(token, method, path, body=None, key=None):
    """返回 (status, body)；成功记 200（BFF 写命令 200 / 201 统一视为 200）。"""
    if key is None:
        key = method != "GET"
    try:
        return 200, e2e_http.call(e2e_http.OPERATIONS, "/api/operations" + path, method, body, token, key)
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {"raw": failure.body[:300]}


def check(item, name, ok, detail=None):
    steps.append({"item": item, "check": name, "ok": bool(ok), "detail": detail})
    shown = "" if ok else f" {json.dumps(detail, ensure_ascii=False)[:400]}"
    print(f"[{'通过' if ok else '失败'}] {item} {name}{shown}")
    return ok


def expect(item, name, token, method, path, body=None, status=200, key=None):
    code, resp = raw(token, method, path, body, key)
    check(item, f"{name} → {code}", code == status, {"status": code, "body": resp} if code != status else None)
    return resp if code == status else None


def login(identifier, password):
    return e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                         {"loginIdentifier": identifier, "password": password})


def save():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    path = RUNTIME / f"commercial-ops2-stage7-{record['startedAt'].replace(':', '').replace('-', '')}.json"
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    return path


def metrics(token):
    body = raw(token, "GET", f"{BASE}/dashboard")[1]
    return {m["metricCode"]: m["count"] for m in body.get("metrics", [])}, body


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


def synthetic_person(admin, label, permissions):
    """经 IAM 新建合成角色与人员，首登改口令后返回 token。"""
    role = expect("准备", f"IAM 新建合成角色 {label}", admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_COMMERCIAL_{label}_{RUN}", "roleName": f"商务二期验收 {label} {RUN}",
        "permissionIds": permissions, "reason": f"commercial 第二切片阶段 7 验收：合成角色 {label}"})
    if not role:
        return None
    record["ids"].setdefault("roles", []).append(role["roleId"])
    email = f"commercial-ops2-{label.lower()}-{RUN.lower()}@example.test"
    initial = "Commercial initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Commercial ops2 " + pysecrets.token_urlsafe(18) + "!"
    created = expect("准备", f"IAM 新建合成人员 {label}", admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": f"commercial 第二切片阶段 7 验收：合成人员 {label}"})
    if not created:
        return None
    record["ids"].setdefault("accounts", []).append(created["accountId"])
    first = login(email, initial)
    e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/password", "POST",
                  {"currentPassword": initial, "newPassword": password}, first["accessToken"])
    return login(email, password)["accessToken"]


def cleanup_people(admin):
    for account_id in record["ids"].get("accounts", []):
        page, row = 1, None
        while True:
            result = raw(admin, "GET", f"/iam/v1/accounts?page={page}&size=200")[1]
            row = next((r for r in result["items"] if r["accountId"] == account_id), None)
            if row or page * 200 >= result["total"]:
                break
            page += 1
        if row and row["status"] == "ACTIVE":
            expect("收尾", f"停用合成人员 {account_id}", admin, "POST", f"/iam/v1/accounts/{account_id}/disable",
                   {"expectedVersion": row["version"], "reason": "commercial 第二切片阶段 7 验收结束：停用合成人员"})
    for role_id in record["ids"].get("roles", []):
        current = raw(admin, "GET", f"/iam/v1/roles/{role_id}")[1]
        expect("收尾", f"清空合成角色 {role_id}", admin, "PUT", f"/iam/v1/roles/{role_id}", {
            "roleName": current["roleName"], "permissionIds": [], "expectedVersion": current["version"],
            "reason": "commercial 第二切片阶段 7 验收结束：清空合成角色权限"})


def read_only_checks(admin):
    nav = raw(admin, "GET", "/authorization/v1/navigation")[1]["resources"]
    parent = next(r for r in nav if r["resourceCode"] == "OPERATIONS_COMMERCIAL")
    menus = sorted((r for r in nav if r.get("parentResourceId") == parent["resourceId"] and r["resourceType"] == "MENU"),
                   key=lambda r: r["sortOrder"])
    order = [(r["displayName"], r["sortOrder"]) for r in menus]
    check("F8-1", "商务 8 个菜单与排序", order == [("工作台", 5), ("合同", 10), ("价目", 20), ("费率预览", 22), ("价格试算", 24),
                                              ("定价画像", 30), ("产品目录", 40), ("变更历史", 50)], order)
    buttons = {r["resourceCode"] for r in nav if r["resourceType"] == "BUTTON" and r["resourceCode"].startswith("OPERATIONS_COMMERCIAL")}
    check("F8-1", "首切片 4 个按钮", len(buttons) == 4, sorted(buttons))

    agreements = expect("F1-1", "按 Client 列表", admin, "GET", f"{BASE}/agreements?clientId={REAL_CLIENT}")
    check("F1-1", "含 CL-F1105E（ACTIVE、AE001）", agreements and any(
        i["agreementNo"] == "CL-F1105E" and i["status"] == "ACTIVE" and i["entityNo"] == "AE001" for i in agreements["items"]),
          agreements and [i["agreementNo"] for i in agreements["items"]])
    by_no = expect("F1-2", "合同号前缀 CL-F11", admin, "GET", f"{BASE}/agreements?agreementNo=CL-F11")
    check("F1-2", "前缀命中 CL-F1105E", by_no and any(i["agreementNo"] == "CL-F1105E" for i in by_no["items"]))
    expect("F1-3", "page=0 被拒", admin, "GET", f"{BASE}/agreements?page=0&size=10", status=400)
    expect("F1-3", "size=1 被拒", admin, "GET", f"{BASE}/agreements?page=1&size=1", status=400)
    expect("F1-5", "size=201 被拒", admin, "GET", f"{BASE}/agreements?size=201", status=400)
    expect("F1-6", "未知查询参数 foo 被拒（Gateway 白名单）", admin, "GET", f"{BASE}/agreements?foo=1", status=400)
    if agreements and agreements["items"]:
        row = agreements["items"][0]
        detail = expect("F1-7", "列表行进入详情", admin, "GET", f"{BASE}/agreements/{row['agreementId']}")
        body = (detail or {}).get("agreement", detail or {})
        check("F1-7", "详情与列表行一致", body.get("agreementNo") == row["agreementNo"] and body.get("status") == row["status"])

    products = expect("F4-1", "产品列表", admin, "GET", f"{BASE}/products")
    codes = sorted(p["productCode"] for p in (products or {}).get("products", []))
    check("F4-1", "7 个产品", codes == sorted(["ACQUIRING", "PAYOUT", "FX", "DISPUTE", "TAX_INVOICE", "DATA_ANALYTICS", "PLATFORM"]), codes)
    caps = expect("F4-2", "可开通项", admin, "GET", f"{BASE}/products/capabilities")
    acq = expect("F4-2", "ACQUIRING 可开通项", admin, "GET", f"{BASE}/products/capabilities?productCode=ACQUIRING")
    visa = next((c for c in (acq or {}).get("capabilities", []) if c["capabilityCode"] == "CARD_VISA"), {})
    check("F4-2", "共 11 项、ACQUIRING 6 项、CARD_VISA=CARD_BRAND/VISA",
          len((caps or {}).get("capabilities", [])) == 11 and len((acq or {}).get("capabilities", [])) == 6
          and visa.get("dimensionCode") == "CARD_BRAND" and visa.get("dimensionValue") == "VISA",
          {"all": len((caps or {}).get("capabilities", [])), "acq": len((acq or {}).get("capabilities", []))})
    fees = expect("F4-3", "计费事件", admin, "GET", f"{BASE}/products/fee-events")
    acq_fees = expect("F4-3", "ACQUIRING / CLIENT_PRICING 计费事件", admin, "GET",
                      f"{BASE}/products/fee-events?productCode=ACQUIRING&termType=CLIENT_PRICING")
    names = sorted(f["feeEventCode"] for f in (acq_fees or {}).get("feeEvents", []))
    check("F4-3", "共 21 项；过滤后 6 项", len((fees or {}).get("feeEvents", [])) == 21
          and names == sorted(["AUTH", "CAPTURE", "SALE", "REFUND", "CHARGEBACK", "TOKEN_STORED"]), names)

    history = expect("F5-3", "CL-F1105E 变更历史", admin, "GET",
                     f"{BASE}/business-changes?aggregateRootType=AGREEMENT&aggregateRootId={REAL_AGREEMENT}&page=1&size=50")
    check("F5-3", "历史条目可见", history and history["total"] >= 1, history and history.get("total"))
    expect("F5-4", "缺 aggregateRootId 被拒", admin, "GET", f"{BASE}/business-changes?aggregateRootType=AGREEMENT", status=400)
    card = expect("F6-1", "CL-F1105E 费率卡", admin, "GET", f"{BASE}/rate-cards?clientId={REAL_CLIENT}&asOf={now_iso()}&currency=USD")
    if card:
        blocks = card.get("agreements") or []
        check("F6-1", "合同块在、无条款版本（记录实际值）", any(b.get("agreementNo") == "CL-F1105E" or b.get("agreementId") == REAL_AGREEMENT
                                                    for b in blocks) or not blocks,
              {"agreements": len(blocks), "termVersions": [len(b.get("termVersions") or []) for b in blocks]})
    expect("F6-3", "费率卡未知参数 bad 被拒", admin, "GET", f"{BASE}/rate-cards?clientId={REAL_CLIENT}&asOf={now_iso()}&bad=1", status=400)


def business_flow(admin, checker, reader, base_metrics):
    ids = record["ids"]
    profiles = {}
    for key, method, conditions in (
            ("specific", "CARD", [{"dimensionCode": "CARD_BRAND", "conditionValue": "VISA"},
                                  {"dimensionCode": "FUNDING_TYPE", "conditionValue": "CREDIT"},
                                  {"dimensionCode": "ISSUER_REGION", "conditionValue": "DOMESTIC"}]),
            ("fallback", "CARD", []), ("applepay", "APPLE_PAY", [])):
        code = f"G8_{RUN}_{key.upper()}"
        created = expect("准备", f"创建画像 {key}", admin, "POST", f"{BASE}/pricing-profiles", {
            "pricingProfileCode": code, "profileKind": "PAYMENT_METHOD", "profileName": f"Ops2 {key} {RUN}",
            "paymentMethodCode": method, "conditions": conditions})
        if not created:
            return
        ids.setdefault("pricingProfileIds", []).append(created["pricingProfileId"])
        active = expect("准备", f"激活画像 {key}", admin, "POST", f"{BASE}/pricing-profiles/{created['pricingProfileId']}/activate",
                        {"expectedChangeVersion": created["changeVersion"]})
        if not active:
            return
        profiles[key] = active
    # F9-3：用过期版本号再次激活。不能用「当前版本 - 1」：commercial 把「已 ACTIVE 且版本 = 期望 + 1」视为原请求重放直接返回 200
    # （ActivatePricingProfileProcessor 类注释），首轮因此误判；用不可能的期望版本 5 触发 CAS 冲突。
    stale = raw(admin, "POST", f"{BASE}/pricing-profiles/{profiles['specific']['pricingProfileId']}/activate", {"expectedChangeVersion": 5})
    check("F9-3", f"过期 expectedChangeVersion → {stale[0]}，detail 为 Owner 原文",
          stale[0] in (409, 400) and stale[1].get("code") in ("CONCURRENT_REQUEST", "BUSINESS_CHECK_FAILED")
          and stale[1].get("detail") not in (None, "", "请求参数不正确", "业务校验失败"), stale[1])

    agreement = expect("准备", "创建 CLIENT 合同", admin, "POST", f"{BASE}/agreements", {
        "agreementNo": f"G8-CLIENT-{RUN}", "agreementName": f"Ops2 client agreement {RUN}", "agreementType": "CLIENT",
        "businessMode": "MOR", "fundMode": "SETTLE_THROUGH_PLATFORM", "pricingAuthority": "PLATFORM", "clientId": CLIENT_ID,
        "entityNo": ENTITY_NO, "effectiveFrom": now_iso(-3600),
        "parties": [{"partyId": "880000000801", "partyProfileId": "881000000801", "partyRole": "CLIENT"},
                    {"partyId": "880000000802", "partyProfileId": "881000000802", "partyRole": "OPERATING_ENTITY"}],
        "documents": [{"documentType": "SIGNING_DOCUMENT", "fileId": "990000000801",
                       "contentHash": "fb98189c25640a38ca1169a96dec802e7ac376b1cf9252476a1bc6438b21a86c"}]})
    if not agreement:
        return
    agreement_id = agreement["agreementId"]
    ids["agreementId"] = agreement_id
    m, _ = metrics(admin)
    check("F2", "新建合同后 DRAFT_AGREEMENTS +1", m.get("DRAFT_AGREEMENTS") == base_metrics.get("DRAFT_AGREEMENTS", 0) + 1, m)
    version = agreement["changeVersion"]
    result = expect("准备", "submit-for-signature", admin, "POST", f"{BASE}/agreements/{agreement_id}/submit-for-signature",
                    {"expectedChangeVersion": version})
    if not result:
        return
    version = result["changeVersion"]
    m, _ = metrics(admin)
    check("F2", "待签署后 PENDING_SIGNATURE_AGREEMENTS +1",
          m.get("PENDING_SIGNATURE_AGREEMENTS") == base_metrics.get("PENDING_SIGNATURE_AGREEMENTS", 0) + 1, m)
    for party_id, actor in (("880000000801", "770000000899"), ("880000000802", "880000000899")):
        result = expect("准备", f"参与方 {party_id} acceptance", admin, "POST",
                        f"{BASE}/agreements/{agreement_id}/parties/{party_id}/acceptance",
                        {"expectedChangeVersion": version, "acceptanceReference": f"G8-ACC-{party_id}-{RUN}",
                         "acceptanceMethod": "CLICK_ACCEPTANCE", "acceptedByActorId": actor, "acceptedAt": now_iso(-60)})
        if not result:
            return
        version = result["changeVersion"]
    result = expect("准备", "合同 activate", admin, "POST", f"{BASE}/agreements/{agreement_id}/activate", {"expectedChangeVersion": version})
    if not result:
        return
    version = result["changeVersion"]
    result = expect("准备", "PUT products [ACQUIRING]", admin, "PUT", f"{BASE}/agreements/{agreement_id}/products",
                    {"expectedChangeVersion": version, "productCodes": ["ACQUIRING"]})
    if not result:
        return
    version = result["changeVersion"]
    for code, limits in (("CARD_VISA", ["AED", "USD"]), ("APPLE_PAY", ["AED"])):
        result = expect("准备", f"开通 {code}", admin, "POST", f"{BASE}/agreements/{agreement_id}/capabilities", {
            "expectedChangeVersion": version, "entityNo": ENTITY_NO, "capabilityCode": code, "effectiveFrom": now_iso(-3600),
            "limits": [{"limitCode": "CURRENCY", "limitValue": c} for c in limits]})
        if not result:
            return
        version = result["changeVersion"]

    plan = expect("准备", "创建方案", admin, "POST", f"{BASE}/commercial-terms/plans", {
        "planCode": f"G8-CLIENT-PRICING-{RUN}", "termType": "CLIENT_PRICING", "agreementId": agreement_id, "clientId": CLIENT_ID})
    if not plan:
        return
    plan_id = plan["planId"]
    ids["planId"] = plan_id
    v1 = expect("准备", "创建 V1", admin, "POST", f"{BASE}/commercial-terms/plans/{plan_id}/versions", {
        "expectedChangeVersion": plan["changeVersion"], "pricingModel": "BLENDED", "feeTaxTreatment": "EXCLUSIVE"})
    if not v1:
        return
    ids["v1"] = v1["versionId"]
    sp, fb, ap = (profiles[k]["pricingProfileCode"] for k in ("specific", "fallback", "applepay"))
    v1_prices = [price("AED", sp, 1, "0.025"), price("AED", fb, 2, "0.030"), price("USD", sp, 1, "0.025"), price("USD", fb, 2, "0.030")]
    r = expect("准备", "V1 PUT prices（CARD 4 条）", admin, "PUT", f"{BASE}/commercial-terms/versions/{ids['v1']}/prices",
               {"expectedChangeVersion": v1["changeVersion"], "prices": v1_prices})
    if not r:
        return
    r = expect("F2-2", "A submit V1", admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v1']}/submit",
               {"expectedChangeVersion": r["changeVersion"]})
    if not r:
        return
    m, dash = metrics(admin)
    queue = [q for q in dash.get("approvalQueue", []) if q.get("versionId") == ids["v1"]]
    check("F2-2", "PENDING_APPROVAL_VERSIONS +1、队列含 V1 且带 maker / effectiveFrom",
          m.get("PENDING_APPROVAL_VERSIONS") == base_metrics.get("PENDING_APPROVAL_VERSIONS", 0) + 1 and queue
          and queue[0].get("makerAccountId") and "effectiveFrom" in queue[0], {"metrics": m, "queue": queue[:1]})
    r = expect("F2-3", "B approve V1", checker, "POST", f"{BASE}/commercial-terms/versions/{ids['v1']}/approve",
               {"expectedChangeVersion": r["changeVersion"]})
    if not r:
        return
    m, _ = metrics(admin)
    check("F2-3", "APPROVED 且待审批数回到基线", r.get("status") == "APPROVED"
          and m.get("PENDING_APPROVAL_VERSIONS") == base_metrics.get("PENDING_APPROVAL_VERSIONS", 0), {"status": r.get("status"), "metrics": m})
    code, body = raw(admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v1']}/schedule",
                     {"expectedChangeVersion": r["changeVersion"], "effectiveFrom": now_iso(60)})
    detail = body.get("detail") or ""
    check("F9-1", f"V1 排期因 APPLE_PAY 无价被拒 → {code}，detail 为 Owner 原文",
          code == 400 and body.get("code") == "BUSINESS_CHECK_FAILED" and "激活校验" in detail and "AE001/APPLE_PAY/AED" in detail, body)
    record["f91Detail"] = detail

    plans = expect("F3-1", "方案列表（按合同）", admin, "GET", f"{BASE}/commercial-terms/plans?agreementId={agreement_id}")
    plan_row = next((p for p in (plans or {}).get("items", []) if p["planId"] == plan_id), None)
    check("F3-1", "含合成方案", plan_row is not None, plans and [p["planCode"] for p in plans["items"]])
    versions = expect("F3-2", "版本列表 APPROVED / SCHEDULED", admin, "GET",
                      f"{BASE}/commercial-terms/versions?planId={plan_id}&status=APPROVED&status=SCHEDULED&page=1&size=50")
    v1_row = next((v for v in (versions or {}).get("items", []) if v["versionId"] == ids["v1"]), {})
    check("F3-2", "V1 APPROVED、maker / checker、priceCount=4",
          v1_row.get("status") == "APPROVED" and v1_row.get("makerAccountId") and v1_row.get("checkerAccountId")
          and v1_row.get("priceCount") == 4, v1_row)

    v2 = expect("F3-3", "复制 V1 建 V2", admin, "POST", f"{BASE}/commercial-terms/plans/{plan_id}/versions", {
        "expectedChangeVersion": plan_row["changeVersion"] if plan_row else plan["changeVersion"], "copyFromVersionId": ids["v1"]})
    if not v2:
        return
    ids["v2"] = v2["versionId"]
    v2_prices = [price("AED", sp, 1, "0.022"), price("AED", fb, 2, "0.030"), price("USD", sp, 1, "0.025"), price("USD", fb, 2, "0.030"),
                 price("AED", ap, 3, "0.030")]
    r = expect("F3-3", "V2 PUT prices（5 条）", admin, "PUT", f"{BASE}/commercial-terms/versions/{ids['v2']}/prices",
               {"expectedChangeVersion": v2["changeVersion"], "prices": v2_prices})
    if not r:
        return
    diff = expect("F3-4", "V1 → V2 对比", admin, "GET", f"{BASE}/commercial-terms/versions/{ids['v1']}/diff/{ids['v2']}")
    if diff:
        changed = diff.get("changed") or []
        fields = [(d["field"], d["baseValue"], d["targetValue"]) for c in changed for d in c.get("differences") or []]
        check("F3-4", "added 1（APPLE_PAY）、changed 1（CAPTURE_PCT 0.025→0.022）、removed 0、unchanged 3、versionDifferences []",
              len(diff.get("added") or []) == 1 and diff["added"][0].get("pricingProfileCode") == ap
              and len(changed) == 1 and changed[0].get("currency") == "AED" and changed[0].get("pricingProfileCode") == sp
              and len(diff.get("removed") or []) == 0 and len(diff.get("unchanged") or []) == 3 and diff.get("versionDifferences") == [],
              {"added": len(diff.get("added") or []), "changed": len(changed), "fields": fields, "removed": len(diff.get("removed") or []),
               "unchanged": len(diff.get("unchanged") or []), "versionDifferences": diff.get("versionDifferences")})
        record["diffFields"] = fields
    back = expect("F3-5", "V2 → V1 对比", admin, "GET", f"{BASE}/commercial-terms/versions/{ids['v2']}/diff/{ids['v1']}")
    if back:
        check("F3-5", "removed 1、added 0", len(back.get("removed") or []) == 1 and len(back.get("added") or []) == 0,
              {"removed": len(back.get("removed") or []), "added": len(back.get("added") or [])})
    code, body = raw(admin, "GET", f"{BASE}/commercial-terms/versions/{ids['v1']}/diff/999999999999999999")
    check("F3-6", f"对比不存在的版本 → {code}（404 / 400，非 500）", code in (400, 404), body)

    r = expect("F2-4", "A submit V2", admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/submit",
               {"expectedChangeVersion": r["changeVersion"]})
    if not r:
        return
    code, body = raw(admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/approve", {"expectedChangeVersion": r["changeVersion"]})
    check("F9-2", f"A 自批 V2 → {code}，detail 含「审批人不能是提交人」", code == 400 and "审批人不能是提交人" in (body.get("detail") or ""), body)
    rej = expect("F2-4", "B reject V2", checker, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/reject",
                 {"expectedChangeVersion": r["changeVersion"]})
    if not rej:
        return
    _, dash = metrics(admin)
    check("F2-4", f"驳回后状态 {rej.get('status')}，队列不含 V2",
          not any(q.get("versionId") == ids["v2"] for q in dash.get("approvalQueue", [])), {"status": rej.get("status")})
    record["rejectStatus"] = rej.get("status")
    r = expect("F2-5", "A 重新 submit V2", admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/submit",
               {"expectedChangeVersion": rej["changeVersion"]})
    if not r:
        return
    r = expect("F2-5", "B approve V2", checker, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/approve",
               {"expectedChangeVersion": r["changeVersion"]})
    if not r:
        return
    effective_from = now_iso(30)
    r = expect("F2-5", "A schedule V2（当前 + 30 秒）", admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/schedule",
               {"expectedChangeVersion": r["changeVersion"], "effectiveFrom": effective_from})
    if not r:
        return
    m, _ = metrics(admin)
    check("F2-5", "SCHEDULED_VERSIONS +1", m.get("SCHEDULED_VERSIONS") == base_metrics.get("SCHEDULED_VERSIONS", 0) + 1, m)
    scheduled = raw(admin, "GET", f"{BASE}/commercial-terms/versions?planId={plan_id}&status=SCHEDULED&page=1&size=10")[1]
    check("F2-5", "排期版本带生效时间", any(v["versionId"] == ids["v2"] and v.get("effectiveFrom") == effective_from
                                     for v in scheduled.get("items", [])), scheduled.get("items"))
    ids["v2EffectiveFrom"] = effective_from
    wait = (datetime.strptime(effective_from, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc) - datetime.now(timezone.utc)).total_seconds() + 3
    time.sleep(max(wait, 0))
    r = expect("F6-2", "A activate V2", admin, "POST", f"{BASE}/commercial-terms/versions/{ids['v2']}/activate",
               {"expectedChangeVersion": r["changeVersion"]})
    check("F6-2", "V2 ACTIVE", r and r.get("status") == "ACTIVE", r)

    card = expect("F6-2", "合成 Client 费率卡（AED）", admin, "GET", f"{BASE}/rate-cards?clientId={CLIENT_ID}&asOf={now_iso()}&currency=AED")
    if card:
        text = json.dumps(card, ensure_ascii=False)
        tv = [t for b in card.get("agreements") or [] for t in b.get("termVersions") or []]
        check("F6-2", "termVersions 含 V2（ACTIVE、CURRENT），含三个画像",
              any(t.get("versionId") == ids["v2"] and t.get("status") == "ACTIVE" and t.get("timing") == "CURRENT" for t in tv)
              and sp in text and fb in text and ap in text, {"termVersions": tv})

    sim = {"termVersionId": ids["v2"], "entityNo": ENTITY_NO, "agreementId": agreement_id, "termType": "CLIENT_PRICING", "currency": "USD",
           "clientId": CLIENT_ID, "contextAsOfTime": now_iso(),
           "paymentFacts": {"paymentMethodCode": "CARD", "dimensions": {"CARD_BRAND": "VISA", "FUNDING_TYPE": "CREDIT", "ISSUER_REGION": "DOMESTIC"}},
           "amount": "100.00"}
    s1 = expect("F7-1", "试算 VISA / CREDIT / DOMESTIC", admin, "POST", f"{BASE}/commercial-prices/simulate", sim, key=False)
    if s1:
        ill = s1.get("illustration") or {}
        capture = next((f for f in ill.get("feeEvents") or [] if f.get("feeEventCode") == "CAPTURE"), {})
        check("F7-1", "命中具体画像、fractionDigits 2、CAPTURE 示例 3.50",
              (s1.get("price") or {}).get("pricingProfileCode") == sp and ill.get("fractionDigits") == 2
              and capture.get("totalAmount") is not None and Decimal(capture["totalAmount"]) == Decimal("3.50"),
              {"profile": (s1.get("price") or {}).get("pricingProfileCode"), "illustration": ill})
    missing = json.loads(json.dumps(sim))
    del missing["paymentFacts"]["dimensions"]["FUNDING_TYPE"]
    code, body = raw(admin, "POST", f"{BASE}/commercial-prices/simulate", missing, key=False)
    check("F7-2", f"缺 FUNDING_TYPE → {code}", code == 422 and body.get("code") == "PRICING_FACTS_INCOMPLETE"
          and "FUNDING_TYPE" in (body.get("detail") or ""), body)
    amex = json.loads(json.dumps(sim))
    amex["paymentFacts"]["dimensions"]["CARD_BRAND"] = "AMEX"
    s3 = expect("F7-3", "试算 AMEX", admin, "POST", f"{BASE}/commercial-prices/simulate", amex, key=False)
    check("F7-3", "命中兜底画像", s3 and (s3.get("price") or {}).get("pricingProfileCode") == fb, s3 and (s3.get("price") or {}).get("pricingProfileCode"))
    big = json.loads(json.dumps(sim))
    big["clientId"] = CLIENT_ID + " " * 9000
    code, body = raw(admin, "POST", f"{BASE}/commercial-prices/simulate", big, key=False)
    check("F7-4", f"body > 8192 字节 → {code}", code in (400, 413), body)

    hist = expect("F5-1", "合成合同变更历史", admin, "GET",
                  f"{BASE}/business-changes?aggregateRootType=AGREEMENT&aggregateRootId={agreement_id}&page=1&size=50")
    if hist:
        items = hist.get("items") or []
        nos = [i.get("versionNo") for i in items]
        check("F5-1", "versionNo 倒序、含 createBy / createTime / operationType / meta，相邻版本有 changedFields",
              items and nos == sorted(nos, reverse=True) and all(i.get("createBy") and i.get("createTime") and i.get("operationType")
                                                                and "meta" in i for i in items)
              and any(i.get("changedFields") for i in items),
              {"versionNos": nos[:10], "changed": [i.get("changedFields") for i in items[:3]]})
    plan_hist = expect("F5-2", "方案变更历史", admin, "GET",
                       f"{BASE}/business-changes?aggregateRootType=COMMERCIAL_TERM_PLAN&aggregateRootId={plan_id}&page=1&size=100")
    if plan_hist:
        items = plan_hist.get("items") or []
        statuses = [((i.get("data") or {}).get("status"), i.get("recordType")) for i in items]
        compared = [i for i in items if i.get("previousVersionNo") is not None]
        check("F5-2", "含版本 submit / approve / reject / schedule / activate 各步，带 previousVersionNo / previousData",
              len(items) >= 10 and compared and all("previousData" in i for i in compared),
              {"total": plan_hist.get("total"), "statuses": statuses[:20]})

    agreements = expect("F1-3", "ACTIVE / CLIENT / AE001 分页 page=1 size=10", admin, "GET",
                        f"{BASE}/agreements?status=ACTIVE&agreementType=CLIENT&entityNo={ENTITY_NO}&page=1&size=10")
    if agreements:
        items = agreements["items"]
        check("F1-3", "≤10 条、全部 ACTIVE / CLIENT / AE001、total ≥ 2",
              agreements["page"] == 1 and agreements["size"] == 10 and len(items) <= 10 and agreements["total"] >= 2
              and all(i["status"] == "ACTIVE" and i["agreementType"] == "CLIENT" and i["entityNo"] == ENTITY_NO for i in items),
              {"total": agreements["total"], "page": agreements["page"], "size": agreements["size"]})
    multi = expect("F1-4", "status 可重复（DRAFT、PENDING_SIGNATURE）", admin, "GET", f"{BASE}/agreements?status=DRAFT&status=PENDING_SIGNATURE")
    if multi:
        check("F1-4", "结果状态只在两个值中", all(i["status"] in ("DRAFT", "PENDING_SIGNATURE") for i in multi["items"]), multi["total"])

    if reader:
        expect("F2-6", "只有价目读的会话读工作台", reader, "GET", f"{BASE}/dashboard", status=403)
        expect("F4-4", "只有价目读的会话读产品目录", reader, "GET", f"{BASE}/products", status=403)


def main():
    record["startedAt"] = now_iso()
    s = e2e_http.secrets()
    admin = login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])["accessToken"]
    base_metrics, dash = metrics(admin)
    check("F2-1", "工作台 5 项指标、ACTIVE_AGREEMENTS ≥ 1",
          sorted(base_metrics) == sorted(["PENDING_APPROVAL_VERSIONS", "SCHEDULED_VERSIONS", "DRAFT_AGREEMENTS",
                                          "PENDING_SIGNATURE_AGREEMENTS", "ACTIVE_AGREEMENTS"]) and base_metrics["ACTIVE_AGREEMENTS"] >= 1, base_metrics)
    record["baseMetrics"] = base_metrics
    try:
        read_only_checks(admin)
        checker = synthetic_person(admin, "CHECKER", [READ_CONTRACT, READ_PRICING, APPROVE])
        reader = synthetic_person(admin, "PRICING_READER", [READ_PRICING])
        if checker:
            nav = raw(checker, "GET", "/authorization/v1/navigation")[1]["resources"]
            visible = {r["resourceCode"] for r in nav if r["resourceCode"].startswith("OPERATIONS_COMMERCIAL")}
            expected = {"OPERATIONS_COMMERCIAL", "OPERATIONS_COMMERCIAL_WORKBENCH", "OPERATIONS_COMMERCIAL_AGREEMENTS",
                        "OPERATIONS_COMMERCIAL_TERMS", "OPERATIONS_COMMERCIAL_RATE_CARDS", "OPERATIONS_COMMERCIAL_PRICE_SIMULATOR",
                        "OPERATIONS_COMMERCIAL_PRICING_PROFILES", "OPERATIONS_COMMERCIAL_CATALOG", "OPERATIONS_COMMERCIAL_HISTORY",
                        "OPERATIONS_COMMERCIAL_TERM_APPROVE"}
            check("F8-2", "B 只见 8 个菜单与审批按钮、无维护按钮", visible == expected, sorted(visible ^ expected))
        business_flow(admin, checker, reader, base_metrics)
    finally:
        try:
            cleanup_people(admin)
        except Exception as failure:  # noqa: BLE001 - 收尾失败计入结果，保留运行记录
            check("收尾", "合成人员 / 角色", False, str(failure)[:300])
        path = save()
    status, text = e2e_http._probe_call("GET", "http://commercial-service:19108/actuator/health", {}, None, 30)
    check("运行", f"commercial /actuator/health → {status}", status == 200 and json.loads(text).get("status") == "UP", text[:200])
    path = save()
    passed = sum(1 for s_ in steps if s_["ok"])
    print(f"\n{passed}/{len(steps)} 通过；运行记录 {path}")
    return 0 if passed == len(steps) else 1


if __name__ == "__main__":
    sys.exit(main())
