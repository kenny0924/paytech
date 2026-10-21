"""commercial 开通与定价模型调整（一期）阶段 7：mor-test 经运营入口验收 AC-01–AC-11，并创建 AC-13 测试数据（technique.md 第 9 节）。

A = PLATFORM_ADMIN（maker），B = 本次新建的合成审批人（checker：合同读、价目读、价目复核、标准价目复核），结束时停用 B 并清空其角色权限。
数据全部经 Gateway → ops-bff → commercial 写入；数据库只用 root 只读核对（迁移数据一致性）。不打印 Token、口令。
重复运行：目录项、画像、模板方案、DEMO2-* 合同按编号先查，已存在则跳过写入（只做读核对），运行记录仍写出。
用法：python3 -I stage7_acceptance.py
"""
import hashlib
import json
import os
import re
import secrets as pysecrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

os.environ["E2E_TARGET"] = "test"
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

BASE = "/commercial-admin/v1"
OE = "AE001"
OE_PARTY = ("880000000702", "881000000702")
PLATFORM_PARTY = ("880000000701", "881000000701")
# DEMO-PT-001 已占用 Local Partner 770000001001 的 PARTNER_SHARE 方案（同 Partner 同条款类型唯一），DEMO2-PT-101 用新的合成 Partner
PARTNER_ID = "770000001002"
PARTNER_PARTY = ("880000001002", "881000001002")
# 真实 ACTIVE Client（client_id, party_id, profile_id）
CLIENTS = {"DEMO2-CL-101": ("237573476041465857", "237573476041465858", "237573476041465859"),
           "DEMO2-CL-102": ("237573382420406273", "237573382420406274", "237573382420406275"),
           "DEMO2-CL-103": ("237573267559391233", "237573267559391234", "237573267559391235"),
           "DEMO2-CL-901": ("237573040244891649", "237573040244891650", "237573040244891651")}
PERMISSIONS = {"COMMERCIAL_CONTRACT_READ": "900000000000095101", "COMMERCIAL_PRICING_READ": "900000000000095103",
               "COMMERCIAL_PRICING_APPROVE": "900000000000095105", "COMMERCIAL_TEMPLATE_APPROVE": "900000000000124103"}
# 迁移前的存量数据（AC-08 免签、AC-11 迁移）
LEGACY = {"CL-F1105E": "238132079668301824", "DEMO-CL-001": "238331035912310784", "DEMO-CL-002": "238331040203083776"}
LEGACY_ACQ_PLAN = "238331390490382336"     # DEMO-CL-001-ACQ：v1 ACTIVE、v2 SCHEDULED（存量免签）
LEGACY_PENDING_PLAN = "238331040953864192"  # DEMO-CL-002-ACQ：v1 PENDING_APPROVAL（非免签）
BACKUP = Path("/Users/kenny/Code-MoR-backups/20261020T205752Z-commercial-capability-pricing/mor_commercial.sql")
SCHEDULE_LEAD = 45

STAMP = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
OUT = MAIN / f"local-environment/e2e/runtime/commercial-capability-stage7-{STAMP}.json"
record = {"startedAt": STAMP, "checks": [], "ids": {}, "http": [], "notes": []}


def now_iso(seconds=0):
    return (datetime.now(timezone.utc) + timedelta(seconds=seconds)).strftime("%Y-%m-%dT%H:%M:%SZ")


def save():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    record["finishedAt"] = now_iso()
    OUT.write_text(json.dumps(record, ensure_ascii=False, indent=1))


class Stop(Exception):
    pass


# ---------- HTTP ----------

def http(token, method, path, body=None):
    """经运营入口调用，返回 (status, 解析后的响应)；429 按 Retry-After 用同一幂等键重试。"""
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    if method != "GET":
        headers["Content-Type"] = "application/json"
        headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    for attempt in range(12):
        request = urllib.request.Request(e2e_http.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read().decode()
                status = response.status
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 11:
                time.sleep(int(failure.headers.get("Retry-After") or 10))
                continue
            raw, status = failure.read().decode(), failure.code
        try:
            parsed = json.loads(raw) if raw else None
        except ValueError:
            parsed = raw
        entry = {"method": method, "path": path.split("?")[0], "status": status}
        if status >= 400:
            entry["problem"] = {k: parsed.get(k) for k in ("code", "title", "detail")} if isinstance(parsed, dict) else str(parsed)[:300]
        record["http"].append(entry)
        return status, parsed
    raise Stop()


def detail(parsed):
    return parsed.get("detail") if isinstance(parsed, dict) else str(parsed)[:300]


def must(token, method, path, body=None, label=None):
    status, parsed = http(token, method, path, body)
    if status >= 400:
        print(f"[失败] {label or method + ' ' + path} → {status} {detail(parsed)}")
        record["notes"].append({"failedStep": label or f"{method} {path}", "status": status, "detail": detail(parsed)})
        raise Stop()
    if label:
        print(f"[完成] {label}")
    return parsed


def plain(value):
    """证据转为可序列化形态（tuple 键转字符串）。"""
    if isinstance(value, dict):
        return {(k if isinstance(k, str) else "/".join(map(str, k)) if isinstance(k, tuple) else str(k)): plain(v)
                for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [plain(v) for v in value]
    return value


def check(ac, name, ok, evidence=None):
    evidence = plain(evidence)
    record["checks"].append({"ac": ac, "name": name, "ok": bool(ok), "evidence": evidence})
    print(f"[{'通过' if ok else '失败'}] {ac} {name}" + ("" if ok else f" ← {json.dumps(evidence, ensure_ascii=False)[:500]}"))
    return bool(ok)


def expect_reject(ac, name, token, method, path, body, statuses=(400,), needle=None):
    """预期 Owner / BFF 拒绝：记录状态与 detail 原文。"""
    status, parsed = http(token, method, path, body)
    text = detail(parsed)
    ok = status in statuses and (needle is None or (text and re.search(needle, text)))
    check(ac, name, ok, {"status": status, "code": parsed.get("code") if isinstance(parsed, dict) else None, "detail": text})
    return status, parsed


def skipped(ac, name, why):
    record["checks"].append({"ac": ac, "name": name, "ok": None, "evidence": why})
    print(f"[跳过] {ac} {name}：{why}")


# ---------- 登录与审批人 ----------

def login(identifier, password):
    status, parsed = http(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password})
    if status != 200:
        raise Stop()
    return parsed


def create_checker(admin):
    code = f"CAP_STAGE7_CHECKER_{STAMP[:8]}{STAMP[9:15]}"
    role = must(admin, "POST", "/iam/v1/roles", {"roleCode": code, "roleName": f"商务开通定价验收审批 {STAMP}",
                                                 "permissionIds": list(PERMISSIONS.values()),
                                                 "reason": "commercial 开通与定价阶段 7 验收：条款与标准价目复核人"}, "IAM 新建合成审批角色")
    record["ids"]["checkerRoleId"] = role["roleId"]
    email = f"cap-stage7-checker-{STAMP.lower()}@example.test"
    initial = "Stage7 initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Stage7 checker " + pysecrets.token_urlsafe(18) + "!"
    created = must(admin, "POST", "/iam/v1/accounts", {"email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
                                                       "reason": "commercial 开通与定价阶段 7 验收：复核人"}, "IAM 新建合成审批人")
    record["ids"]["checkerAccountId"] = created["accountId"]
    first = login(email, initial)
    must(first["accessToken"], "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, "审批人首登改口令")
    return login(email, password)["accessToken"]


def disable_checker(admin):
    account_id, role_id = record["ids"].get("checkerAccountId"), record["ids"].get("checkerRoleId")
    if account_id:
        page, row = 1, None
        while True:
            result = must(admin, "GET", f"/iam/v1/accounts?page={page}&size=200")
            row = next((r for r in result["items"] if r["accountId"] == account_id), None)
            if row or page * 200 >= result["total"]:
                break
            page += 1
        if row and row["status"] == "ACTIVE":
            must(admin, "POST", f"/iam/v1/accounts/{account_id}/disable",
                 {"expectedVersion": row["version"], "reason": "阶段 7 验收完成：停用合成审批人"}, "停用合成审批人")
    if role_id:
        current = must(admin, "GET", f"/iam/v1/roles/{role_id}")
        must(admin, "PUT", f"/iam/v1/roles/{role_id}", {"roleName": current["roleName"], "permissionIds": [],
                                                        "expectedVersion": current["version"],
                                                        "reason": "阶段 7 验收完成：清空合成角色权限"}, "清空合成角色权限")


# ---------- 查询 ----------

def capabilities(admin):
    return must(admin, "GET", f"{BASE}/products/capabilities")["capabilities"]


def capability(admin, code):
    return next((c for c in capabilities(admin) if c["capabilityCode"] == code), None)


def find_agreement(admin, no):
    rows = must(admin, "GET", f"{BASE}/agreements?agreementNo={no}&page=1&size=20")["items"]
    row = next((r for r in rows if r["agreementNo"] == no), None)
    return must(admin, "GET", f"{BASE}/agreements/{row['agreementId']}")["agreement"] if row else None


def agreement(admin, agreement_id):
    return must(admin, "GET", f"{BASE}/agreements/{agreement_id}")["agreement"]


def plans(admin, agreement_id):
    return must(admin, "GET", f"{BASE}/commercial-terms/plans?agreementId={agreement_id}&page=1&size=50")["items"]


def versions(admin, plan_id):
    return must(admin, "GET", f"{BASE}/commercial-terms/versions?planId={plan_id}&page=1&size=50")["items"]


def workspace(token, version_id):
    return must(token, "GET", f"{BASE}/commercial-terms/versions/{version_id}")


def templates(admin):
    return must(admin, "GET", f"{BASE}/commercial-terms/templates?entityNo={OE}")["templates"]


def cap_conditions(cap):
    return {c["dimensionCode"]: sorted(c["values"]) for c in cap.get("conditions") or []}


# ---------- 价格 ----------

def pct(code, trigger, rate, currency=None, base="GROSS_AMOUNT", refund="RETURN_PRORATA", min_amount=None):
    c = {"componentCode": code, "componentType": "PERCENTAGE", "triggerCode": trigger, "calculationBase": base,
         "refundBehavior": refund, "rate": rate}
    if min_amount:
        c.update(currency=currency, minAmount=min_amount)
    return c


def fixed(code, trigger, amount, currency, refund="KEEP"):
    return {"componentCode": code, "componentType": "FIXED", "triggerCode": trigger, "calculationBase": "FIXED_PER_EVENT",
            "refundBehavior": refund, "fixedAmount": amount, "currency": currency}


def client_price(currency, profile, priority, components, source=None):
    p = {"entityNo": OE, "currency": currency, "pricingProfileCode": profile, "priorityNo": priority, "settlementBusinessDays": 2,
         "businessCalendarCode": "AE_BUSINESS_DAYS", "settlementTimezone": "UTC", "cutoffSecondOfDay": 86399, "components": components}
    if source:
        p["sourceTemplatePriceId"] = source
    return p


def acquiring(currency, rate, per_txn, refund_fee, chargeback_fee, token_fee=None, min_amount=None):
    comps = [pct("CAPTURE_PCT", "CAPTURE", rate, currency, min_amount=min_amount), fixed("CAPTURE_FIXED", "CAPTURE", per_txn, currency),
             pct("SALE_PCT", "SALE", rate, currency, min_amount=min_amount), fixed("SALE_FIXED", "SALE", per_txn, currency),
             fixed("REFUND_FIXED", "REFUND", refund_fee, currency), fixed("CHARGEBACK_FIXED", "CHARGEBACK", chargeback_fee, currency)]
    if token_fee:
        comps.append(fixed("TOKEN_STORED_FIXED", "TOKEN_STORED", token_fee, currency))
    return comps


# (画像, 优先级, 费率, 单笔固定, 退款费, 拒付费, 存卡费, 最低收费)
STANDARD_SHEET = {
    "AED": [("CARD_VISA_DEBIT", 1, "0.0180", "1.00", "3.00", "55.00", "0.50", None),
            ("CARD_AMEX", 2, "0.0350", "1.00", "3.00", "75.00", None, None),
            ("CARD_INTERNATIONAL", 3, "0.0320", "1.00", "3.00", "75.00", None, "2.00"),
            ("CARD_STANDARD", 4, "0.0260", "1.00", "3.00", "55.00", "0.50", None),
            ("APPLE_PAY_STANDARD", 5, "0.0240", "1.00", "3.00", "55.00", None, None),
            ("GOOGLE_PAY_STANDARD", 6, "0.0240", "1.00", "3.00", "55.00", None, None)],
    "USD": [("CARD_VISA_DEBIT", 1, "0.0200", "0.30", "1.00", "15.00", "0.15", None),
            ("CARD_AMEX", 2, "0.0390", "0.30", "1.00", "20.00", None, None),
            ("CARD_INTERNATIONAL", 3, "0.0350", "0.30", "1.00", "20.00", None, "0.50"),
            ("CARD_STANDARD", 4, "0.0290", "0.30", "1.00", "15.00", "0.15", None),
            ("APPLE_PAY_STANDARD", 5, "0.0270", "0.30", "1.00", "15.00", None, None),
            ("GOOGLE_PAY_STANDARD", 6, "0.0270", "0.30", "1.00", "15.00", None, None)],
}


def sheet_prices(sheet, discount="0"):
    prices = []
    for currency, rows in sheet.items():
        for profile, priority, rate, per_txn, refund, chargeback, token_fee, min_amount in rows:
            rate = str((__import__("decimal").Decimal(rate) - __import__("decimal").Decimal(discount)).quantize(
                __import__("decimal").Decimal("0.0001")))
            prices.append(client_price(currency, profile, priority,
                                       acquiring(currency, rate, per_txn, refund, chargeback, token_fee, min_amount)))
    return prices


PLATFORM_FEE_PRICES = [client_price("USD", "PLATFORM_SUBSCRIPTION", 1, [fixed("MONTHLY_FEE", "MONTHLY_CYCLE", "99.00", "USD"),
                                                                        fixed("SETUP_FEE", "SETUP", "500.00", "USD"),
                                                                        fixed("KYB_FEE", "KYB_REVIEW", "150.00", "USD")])]
FX_PRICES = [client_price("USD", "FX_USD_AED", 1, [pct("FX_SPREAD_PCT", "FX_DEAL", "0.0050", refund="KEEP")])]

TEMPLATES = [
    # planCode, 名称, 条款类型, 价格
    ("TPL-AE001-STANDARD", "标准价 · AE001", "CLIENT_PRICING", lambda: sheet_prices(STANDARD_SHEET)),
    ("TPL-AE001-PROMO", "优惠价 · AE001", "CLIENT_PRICING", lambda: sheet_prices(STANDARD_SHEET, "0.0030")),
    ("TPL-AE001-PLATFORM", "平台费标准价 · AE001", "PLATFORM_FEE", lambda: PLATFORM_FEE_PRICES),
    ("TPL-AE001-FX", "外汇点差标准价 · AE001", "FX_SPREAD", lambda: FX_PRICES),
]


def write_price(p):
    """读模型价格 → 写请求（结算四字段平铺；非阶梯组件档位为 null；保留来源模板价格）。"""
    st = p.get("settlement") or {}
    out = {"entityNo": p["entityNo"], "currency": p["currency"], "pricingProfileCode": p["pricingProfileCode"],
           "priorityNo": p["priorityNo"], "components": []}
    if st:
        out.update({k: st.get(k) for k in ("settlementBusinessDays", "businessCalendarCode", "settlementTimezone", "cutoffSecondOfDay")})
    if p.get("sourceTemplatePriceId"):
        out["sourceTemplatePriceId"] = p["sourceTemplatePriceId"]
    for c in p["components"]:
        w = {k: c.get(k) for k in ("componentCode", "componentType", "triggerCode", "calculationBase", "refundBehavior", "rate",
                                   "fixedAmount", "currency", "minAmount", "maxAmount", "bandBasis", "bandMode")}
        w["bands"] = [{k: b.get(k) for k in ("lowerBound", "upperBound", "rate", "fixedAmount")} for b in c.get("bands") or []] or None
        out["components"].append({k: v for k, v in w.items() if v is not None})
    return out


def price_rows(ws):
    return sorted((p["currency"], p["pricingProfileCode"]) for p in ws["prices"])


def price_signature(ws):
    return sorted(json.dumps(write_price(p), sort_keys=True) for p in ws["prices"])


# ---------- 版本流程 ----------

def sign_body(version, party_id, reference):
    return {"expectedChangeVersion": version["changeVersion"], "partyId": party_id, "acceptanceMethod": "E_SIGNATURE",
            "acceptedAt": now_iso(-60), "acceptanceReference": reference,
            "fileId": str(990000004000 + int(hashlib.sha256(reference.encode()).hexdigest(), 16) % 1000),
            "contentHash": hashlib.sha256(("price-attachment:" + reference).encode()).hexdigest()}


def submit_approve(admin, checker, version, label, route="commercial-terms/versions"):
    v = must(admin, "POST", f"{BASE}/{route}/{version['versionId']}/submit", {"expectedChangeVersion": version["changeVersion"]},
             f"{label} 提交复核")
    return must(checker, "POST", f"{BASE}/{route}/{version['versionId']}/approve", {"expectedChangeVersion": v["changeVersion"]},
                f"{label} B 复核通过")


pending = []   # (label, versionId, route, effectiveFrom)


def schedule(admin, version, label, route="commercial-terms/versions"):
    at = now_iso(SCHEDULE_LEAD)
    v = must(admin, "POST", f"{BASE}/{route}/{version['versionId']}/schedule",
             {"expectedChangeVersion": version["changeVersion"], "effectiveFrom": at}, f"{label} 排期 {at}")
    pending.append((label, version["versionId"], route, at))
    return v


def activate_pending(admin):
    if not pending:
        return {}
    latest = max(datetime.strptime(at, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc) for _, _, _, at in pending)
    wait = (latest - datetime.now(timezone.utc)).total_seconds() + 3
    print(f"[等待] 排期到点 {max(wait, 0):.0f} 秒")
    time.sleep(max(wait, 0))
    result = {}
    for label, version_id, route, _ in pending:
        ws = workspace(admin, version_id)
        if ws["version"]["status"] != "ACTIVE":
            status, parsed = http(admin, "POST", f"{BASE}/{route}/{version_id}/activate",
                                  {"expectedChangeVersion": ws["version"]["changeVersion"]})
            if status >= 400:
                print(f"[说明] {label} 激活 {status} {detail(parsed)}")
            ws = workspace(admin, version_id)
        result[version_id] = ws["version"]["status"]
        print(f"[完成] {label} → {ws['version']['status']}")
    pending.clear()
    return result


# ---------- 合同 ----------

def doc_hash(no):
    return hashlib.sha256(("agreement-document:" + no).encode()).hexdigest()


def accept_party(admin, agreement_id, no, change, party_id):
    return must(admin, "POST", f"{BASE}/agreements/{agreement_id}/parties/{party_id}/acceptance", {
        "expectedChangeVersion": change, "acceptanceReference": f"ACC-{no}-{party_id}", "acceptanceMethod": "CLICK_ACCEPTANCE",
        "acceptedByActorId": party_id, "acceptedAt": now_iso(-120)}, f"合同 {no} 参与方 {party_id} 签署")["changeVersion"]


def create_client_agreement(admin, no, name, file_no):
    client_id, party_id, profile_id = CLIENTS[no]
    a = must(admin, "POST", f"{BASE}/agreements", {
        "agreementNo": no, "agreementName": name, "agreementType": "CLIENT", "businessMode": "MOR",
        "fundMode": "SETTLE_THROUGH_PLATFORM", "pricingAuthority": "PLATFORM", "clientId": client_id, "entityNo": OE,
        "effectiveFrom": now_iso(-86400),
        "parties": [{"partyId": party_id, "partyProfileId": profile_id, "partyRole": "CLIENT"},
                    {"partyId": OE_PARTY[0], "partyProfileId": OE_PARTY[1], "partyRole": "OPERATING_ENTITY"}],
        "documents": [{"documentType": "SIGNING_DOCUMENT", "fileId": str(file_no), "contentHash": doc_hash(no)}]}, f"合同 {no} 创建")
    change = must(admin, "POST", f"{BASE}/agreements/{a['agreementId']}/submit-for-signature",
                  {"expectedChangeVersion": a["changeVersion"]}, f"合同 {no} 提交签署")["changeVersion"]
    for party in (party_id, OE_PARTY[0]):
        change = accept_party(admin, a["agreementId"], no, change, party)
    must(admin, "POST", f"{BASE}/agreements/{a['agreementId']}/activate", {"expectedChangeVersion": change}, f"合同 {no} 生效")
    record["ids"].setdefault("agreements", {})[no] = a["agreementId"]
    return agreement(admin, a["agreementId"])


def set_products(admin, ag, products):
    return must(admin, "PUT", f"{BASE}/agreements/{ag['agreementId']}/products",
                {"expectedChangeVersion": ag["changeVersion"], "productCodes": products}, f"合同 {ag['agreementNo']} 订阅 {products}")


def cap_body(change, code, conditions, require3ds=False):
    return {"expectedChangeVersion": change, "entityNo": OE, "capabilityCode": code, "effectiveFrom": now_iso(-3600),
            "conditions": [{"dimensionCode": d, "values": v} for d, v in conditions.items()], "require3ds": require3ds}


def add_capability(admin, agreement_id, code, conditions, require3ds=False, label=None):
    ag = agreement(admin, agreement_id)
    return must(admin, "POST", f"{BASE}/agreements/{agreement_id}/capabilities", cap_body(ag["changeVersion"], code, conditions, require3ds),
                label or f"合同 {ag['agreementNo']} 开通 {code} {conditions}")


def plan_of(admin, code, term_type, agreement_id, client_id=None, partner_id=None):
    body = {"planCode": code, "termType": term_type, "agreementId": agreement_id}
    body.update({"clientId": client_id} if client_id else {"localPartnerId": partner_id})
    p = must(admin, "POST", f"{BASE}/commercial-terms/plans", body, f"方案 {code} 创建")
    record["ids"].setdefault("plans", {})[code] = p["planId"]
    return p


def plan_change(admin, agreement_id, plan_id):
    return next(r for r in plans(admin, agreement_id) if r["planId"] == plan_id)["changeVersion"]


# ---------- AC-02 / AC-01 目录 ----------

def ac02_catalog(admin):
    acq = {c["capabilityCode"]: c for c in capabilities(admin) if c["productCode"] == "ACQUIRING"}
    base = {"CARD", "APPLE_PAY", "GOOGLE_PAY", "TOKEN_STORAGE"}
    check("AC-02", "收单目录含 CARD / APPLE_PAY / GOOGLE_PAY（支付方式）+ TOKEN_STORAGE（功能），无 CARD_*",
          base <= set(acq) and not any(c.startswith("CARD_") for c in acq)
          and all(acq[c]["capabilityKind"] == "PAYMENT_METHOD" for c in ("CARD", "APPLE_PAY", "GOOGLE_PAY"))
          and acq["TOKEN_STORAGE"]["capabilityKind"] == "FEATURE",
          {c: acq[c]["capabilityKind"] for c in acq})
    card_dims = {d["dimensionCode"]: d["required"] for d in acq["CARD"]["dimensions"]}
    check("AC-02", "CARD 可配 CARD_BRAND / FUNDING_TYPE / ISSUER_COUNTRY / ISSUER_REGION / CURRENCY(必填) / CONSUMER_COUNTRY",
          {"CARD_BRAND", "FUNDING_TYPE", "ISSUER_COUNTRY", "ISSUER_REGION", "CURRENCY", "CONSUMER_COUNTRY"} <= set(card_dims)
          and card_dims["CURRENCY"] is True, card_dims)
    events = must(admin, "GET", f"{BASE}/products/fee-events?productCode=ACQUIRING")
    events = events.get("feeEvents") or events.get("items") or []
    token = [e for e in events if e["feeEventCode"] == "TOKEN_STORED"]
    check("AC-02", "TOKEN_STORED 计费事件挂到功能开通项 TOKEN_STORAGE；其余收单事件挂 PAYMENT_METHOD",
          token and all(e["capabilityKind"] == "FEATURE" and e["capabilityCode"] == "TOKEN_STORAGE" for e in token)
          and all(e["capabilityKind"] == "PAYMENT_METHOD" for e in events if e["feeEventCode"] != "TOKEN_STORED"),
          [(e["feeEventCode"], e.get("termType"), e["capabilityKind"], e["capabilityCode"]) for e in events])
    dims = must(admin, "GET", f"{BASE}/products/dimensions")["dimensions"]
    check("S2", "维度清单 14 项，THREE_DS_RESULT / TRANSACTION_TYPE / DISPUTE_STAGE 不可用于开通",
          len(dims) == 14 and {d["dimensionCode"] for d in dims if not d["acceptanceAllowed"]}
          == {"THREE_DS_RESULT", "TRANSACTION_TYPE", "DISPUTE_STAGE"}, [d["dimensionCode"] for d in dims])
    return events


def ac01_catalog(admin, events):
    for code, name, kind, method, dims in (
            ("RECURRING", "周期扣款", "FEATURE", None, []),
            ("STC_PAY", "STC Pay", "PAYMENT_METHOD", "STC_PAY", [("CURRENCY", True), ("CONSUMER_COUNTRY", False)])):
        existing = capability(admin, code)
        if existing:
            skipped("AC-01", f"新增开通项 {code}", f"已存在 {existing['productCapabilityId']}（{existing['status']}），跳过创建")
            record["ids"].setdefault("catalog", {})[code] = existing["productCapabilityId"]
            continue
        body = {"productCode": "ACQUIRING", "capabilityCode": code, "capabilityName": name, "capabilityKind": kind,
                "dimensions": [{"dimensionCode": d, "required": r} for d, r in dims]}
        if method:
            body["paymentMethodCode"] = method
        status, created = http(admin, "POST", f"{BASE}/products/capabilities", body)
        check("AC-01", f"新增{'功能' if kind == 'FEATURE' else '支付方式'}开通项 {code} → 201", status == 201
              and created.get("capabilityKind") == kind and created.get("status") == "ACTIVE"
              and sorted((d["dimensionCode"], d["required"]) for d in created.get("dimensions") or []) == sorted(dims),
              {"status": status, "body": created if status >= 400 else {k: created.get(k) for k in
                                                                         ("productCapabilityId", "status", "dimensions", "paymentMethodCode")}})
        if status == 201:
            record["ids"].setdefault("catalog", {})[code] = created["productCapabilityId"]
    expect_reject("AC-01", "重复代码 RECURRING → 409", admin, "POST", f"{BASE}/products/capabilities",
                  {"productCode": "ACQUIRING", "capabilityCode": "RECURRING", "capabilityName": "周期扣款", "capabilityKind": "FEATURE",
                   "dimensions": []}, statuses=(409,))
    expect_reject("AC-01", "代码格式错误 recurring-x 被拒（字段校验 422 VALIDATION_ERROR，系统 RFC 9457 规则）", admin, "POST",
                  f"{BASE}/products/capabilities",
                  {"productCode": "ACQUIRING", "capabilityCode": "recurring-x", "capabilityName": "x", "capabilityKind": "FEATURE",
                   "dimensions": []}, statuses=(400, 422))
    expect_reject("AC-01", "FEATURE 带支付方式代码 → 400", admin, "POST", f"{BASE}/products/capabilities",
                  {"productCode": "ACQUIRING", "capabilityCode": "RECURRING_X", "capabilityName": "x", "capabilityKind": "FEATURE",
                   "paymentMethodCode": "CARD", "dimensions": []})
    expect_reject("AC-01", "可配维度用不可开通维度 THREE_DS_RESULT → 400", admin, "POST", f"{BASE}/products/capabilities",
                  {"productCode": "ACQUIRING", "capabilityCode": "MADA", "capabilityName": "mada", "capabilityKind": "PAYMENT_METHOD",
                   "paymentMethodCode": "MADA", "dimensions": [{"dimensionCode": "CURRENCY", "required": True},
                                                               {"dimensionCode": "THREE_DS_RESULT", "required": False}]})
    stc = capability(admin, "STC_PAY")
    if stc:
        status, updated = http(admin, "PUT", f"{BASE}/products/capabilities/{stc['productCapabilityId']}", {
            "expectedChangeVersion": stc["changeVersion"], "capabilityName": "STC Pay", "productCode": "ACQUIRING",
            "dimensions": [{"dimensionCode": d["dimensionCode"], "required": d["required"]} for d in stc["dimensions"]]})
        check("AC-01", "编辑开通项（名称、可配维度）→ 200，changeVersion 递增",
              status == 200 and int(updated["changeVersion"]) == int(stc["changeVersion"]) + 1,
              {"status": status, "changeVersion": updated.get("changeVersion") if isinstance(updated, dict) else updated})
    token = next(e for e in events if e["feeEventCode"] == "TOKEN_STORED")
    status, parsed = http(admin, "PUT", f"{BASE}/products/fee-events/{token['productFeeEventId']}/capability",
                          {"expectedChangeVersion": token["changeVersion"], "capabilityKind": "FEATURE", "capabilityCode": "TOKEN_STORAGE"})
    check("S1", "计费事件挂接（TOKEN_STORED → FEATURE/TOKEN_STORAGE，同值保存）→ 200", status == 200,
          {"status": status, "detail": detail(parsed) if status >= 400 else parsed.get("capabilityCode")})
    token = next(e for e in must(admin, "GET", f"{BASE}/products/fee-events?productCode=ACQUIRING")["feeEvents"]
                 if e["feeEventCode"] == "TOKEN_STORED")
    expect_reject("S1", "计费事件挂接到支付方式开通项 CARD 作为 FEATURE → 400", admin, "PUT",
                  f"{BASE}/products/fee-events/{token['productFeeEventId']}/capability",
                  {"expectedChangeVersion": token["changeVersion"], "capabilityKind": "FEATURE", "capabilityCode": "CARD"})


# ---------- 画像 ----------

PROFILES = [("CARD_VISA_DEBIT", "Visa 借记卡", {"CARD_BRAND": ["VISA"], "FUNDING_TYPE": ["DEBIT"]}),
            ("CARD_VISA", "Visa 卡（全部）", {"CARD_BRAND": ["VISA"]})]


def ensure_profiles(admin):
    existing = {p["pricingProfileCode"]: p for p in must(admin, "GET", f"{BASE}/pricing-profiles")["profiles"]}
    for code, name, conditions in PROFILES:
        if code in existing:
            print(f"[跳过] 画像 {code} 已存在（{existing[code]['status']}）")
            continue
        created = must(admin, "POST", f"{BASE}/pricing-profiles", {
            "pricingProfileCode": code, "profileKind": "PAYMENT_METHOD", "profileName": name, "paymentMethodCode": "CARD",
            "conditions": [{"dimensionCode": d, "conditionValue": v} for d, values in conditions.items() for v in values]},
                       f"画像 {code} 创建")
        must(admin, "POST", f"{BASE}/pricing-profiles/{created['pricingProfileId']}/activate",
             {"expectedChangeVersion": created["changeVersion"]}, f"画像 {code} 激活")


# ---------- AC-04 标准价目 ----------

def ensure_templates(admin, checker):
    tpl = {}
    existing = {t["planCode"]: t for t in templates(admin)}
    first = True
    for code, name, term_type, prices in TEMPLATES:
        if code in existing and existing[code].get("activeVersionId"):
            t = existing[code]
            skipped("AC-04", f"模板 {code}", f"已存在，生效版本 v{t['activeVersionNo']}（{t['activeVersionId']}）")
            tpl[code] = {"planId": t["planId"], "v1": t["activeVersionId"]}
            continue
        try:
            plan = existing.get(code) or must(admin, "POST", f"{BASE}/commercial-terms/templates",
                                              {"planCode": code, "templateName": name, "termType": term_type, "entityNo": OE},
                                              f"模板方案 {code} 创建")
            if first:
                check("AC-04", "模板方案无合同：ownerType=PLATFORM_TEMPLATE、agreementId / clientId 为空、entityNo=AE001",
                      plan.get("ownerType") == "PLATFORM_TEMPLATE" and not plan.get("agreementId") and not plan.get("clientId")
                      and plan.get("entityNo") == OE, {k: plan.get(k) for k in ("planId", "ownerType", "agreementId", "entityNo")})
            v = must(admin, "POST", f"{BASE}/commercial-terms/templates/{plan['planId']}/versions",
                     {"expectedChangeVersion": plan["changeVersion"], "pricingModel": "BLENDED", "feeTaxTreatment": "EXCLUSIVE"},
                     f"模板 {code} 新建版本")
            v = must(admin, "PUT", f"{BASE}/commercial-terms/template-versions/{v['versionId']}/prices",
                     {"expectedChangeVersion": v["changeVersion"], "prices": prices()}, f"模板 {code} 写价格")
            v = must(admin, "POST", f"{BASE}/commercial-terms/template-versions/{v['versionId']}/submit",
                     {"expectedChangeVersion": v["changeVersion"]}, f"模板 {code} 提交复核")
            if first:
                expect_reject("AC-04", "maker = checker：A 复核自己提交的模板版本被拒", admin, "POST",
                              f"{BASE}/commercial-terms/template-versions/{v['versionId']}/approve",
                              {"expectedChangeVersion": v["changeVersion"]}, statuses=(400, 403, 409))
                expect_reject("AC-04", "路由隔离：合同复核路由处理模板版本被拒", checker, "POST",
                              f"{BASE}/commercial-terms/versions/{v['versionId']}/approve",
                              {"expectedChangeVersion": v["changeVersion"]}, statuses=(400, 403, 404))
            v = must(checker, "POST", f"{BASE}/commercial-terms/template-versions/{v['versionId']}/approve",
                     {"expectedChangeVersion": v["changeVersion"]}, f"模板 {code} B 复核通过（TEMPLATE_APPROVE）")
            if first:
                check("AC-04", "模板版本 APPROVED 后无签署要求即可排期", v.get("status") == "APPROVED", {"status": v.get("status")})
            schedule(admin, v, f"模板 {code} v1", route="commercial-terms/template-versions")
            tpl[code] = {"planId": plan["planId"], "v1": v["versionId"], "new": True}
            first = False
        except Stop:
            check("AC-04", f"模板 {code} 建立并复核", False, record["notes"][-1] if record["notes"] else None)
    record["ids"]["templates"] = tpl
    return tpl


def ac04_after_activation(admin, tpl, activated):
    rows = {t["planCode"]: t for t in templates(admin)}
    for code, info in tpl.items():
        if info.get("new"):
            check("AC-04", f"模板 {code} v1 排期后生效 ACTIVE；列表显示生效版本",
                  activated.get(info["v1"]) == "ACTIVE" and rows.get(code, {}).get("activeVersionId") == info["v1"],
                  {"status": activated.get(info["v1"]), "row": {k: rows.get(code, {}).get(k) for k in
                                                                 ("activeVersionNo", "latestVersionNo", "latestVersionStatus")}})


def ac04_not_resolvable(admin, tpl, any_agreement_id, any_client_id):
    std = tpl.get("TPL-AE001-STANDARD", {}).get("v1")
    if not std:
        return
    body = {"termVersionId": std, "entityNo": OE, "agreementId": any_agreement_id, "termType": "CLIENT_PRICING", "currency": "AED",
            "contextAsOfTime": now_iso(), "clientId": any_client_id, "amount": "100.00",
            "paymentFacts": {"paymentMethodCode": "CARD", "dimensions": {"CARD_BRAND": "VISA", "FUNDING_TYPE": "DEBIT"}}}
    expect_reject("AC-04", "价格试算（BFF simulate）用模板版本不命中", admin, "POST", f"{BASE}/commercial-prices/simulate", body,
                  statuses=(400, 404, 409))
    direct = {k: body[k] for k in ("termVersionId", "entityNo", "agreementId", "contextAsOfTime", "termType", "currency", "clientId")}
    direct.update(paymentFactId="660000000901", paymentFacts=body["paymentFacts"])
    try:
        status, text = e2e_http.private_call("commercial", "POST", "/commercial-prices/v1/resolutions", body=direct)
        check("AC-04", "commercial 直连价格命中 /commercial-prices/v1/resolutions 用模板版本不命中", status in (400, 404, 409),
              {"status": status, "body": text[:400]})
    except Exception as failure:  # noqa: BLE001
        check("AC-04", "commercial 直连价格命中（私网探针）", False, str(failure)[:300])


# ---------- AC-03 开通条件 ----------

def ac03_conditions(admin, ag):
    aid, change = ag["agreementId"], ag["changeVersion"]
    path = f"{BASE}/agreements/{aid}/capabilities"
    expect_reject("AC-03", "CARD 缺 CURRENCY 被拒", admin, "POST", path,
                  cap_body(change, "CARD", {"CARD_BRAND": ["VISA"], "FUNDING_TYPE": ["DEBIT"]}), needle="CURRENCY|币种")
    expect_reject("AC-03", "CARD 使用不可配维度 DESTINATION_COUNTRY 被拒", admin, "POST", path,
                  cap_body(change, "CARD", {"CURRENCY": ["AED"], "DESTINATION_COUNTRY": ["AE"]}))
    expect_reject("AC-03", "CARD 使用不可开通维度 THREE_DS_RESULT 被拒", admin, "POST", path,
                  cap_body(change, "CARD", {"CURRENCY": ["AED"], "THREE_DS_RESULT": ["AUTHENTICATED"]}))
    expect_reject("AC-03", "币种格式错误 US 被拒", admin, "POST", path, cap_body(change, "CARD", {"CURRENCY": ["US"]}))
    wanted = {"CARD_BRAND": ["MASTERCARD", "VISA"], "FUNDING_TYPE": ["DEBIT"], "CURRENCY": ["AED", "USD"]}
    created = must(admin, "POST", path, cap_body(change, "CARD", {"CARD_BRAND": ["VISA", "MASTERCARD"], "FUNDING_TYPE": ["DEBIT"],
                                                                  "CURRENCY": ["AED", "USD"]}, True), "DEMO2-CL-101 开通 CARD（AC-03 配置）")
    cap = next(c for c in agreement(admin, aid)["capabilities"] if c["capabilityCode"] == "CARD")
    check("AC-03", "CARD + VISA,MASTERCARD + DEBIT + AED,USD + 3DS 读回一致",
          cap_conditions(cap) == wanted and cap["require3ds"] is True, {"conditions": cap.get("conditions"), "require3ds": cap["require3ds"]})
    ag2 = agreement(admin, aid)
    status, updated = http(admin, "PUT", f"{path}/{cap['agreementCapabilityId']}", {
        "expectedChangeVersion": ag2["changeVersion"], "status": "ENABLED", "effectiveFrom": cap["effectiveFrom"],
        "conditions": [{"dimensionCode": "CARD_BRAND", "values": ["VISA"]}, {"dimensionCode": "FUNDING_TYPE", "values": ["DEBIT"]},
                       {"dimensionCode": "CURRENCY", "values": ["AED"]}], "require3ds": True})
    cap = next(c for c in agreement(admin, aid)["capabilities"] if c["capabilityCode"] == "CARD")
    check("AC-03", "编辑为只收 Visa 借记卡（CARD + VISA + DEBIT + AED + 3DS）→ 200 读回一致",
          status == 200 and cap_conditions(cap) == {"CARD_BRAND": ["VISA"], "FUNDING_TYPE": ["DEBIT"], "CURRENCY": ["AED"]},
          {"status": status, "detail": detail(updated) if status >= 400 else None, "conditions": cap.get("conditions")})
    return created


# ---------- 场景 ----------

def existing_scene(admin, no, plan_code):
    """合同已存在：跳过写入，返回已有 ID；v1 仍为 SCHEDULED 时加入待激活。"""
    ag = find_agreement(admin, no)
    if not ag:
        return None
    record["ids"].setdefault("agreements", {})[no] = ag["agreementId"]
    scene = {"agreementId": ag["agreementId"], "existing": True}
    plan = next((r for r in plans(admin, ag["agreementId"]) if r["planCode"] == plan_code), None)
    if plan:
        scene["planId"] = plan["planId"]
        v1 = next((r for r in versions(admin, plan["planId"]) if r["versionNo"] == "1"), None)
        if v1:
            scene["v1"] = v1["versionId"]
            if v1["status"] == "SCHEDULED":
                route = "commercial-terms/versions"
                pending.append((f"{no} v1（已存在）", v1["versionId"], route, max(v1["effectiveFrom"][:19] + "Z", now_iso())))
    skipped("AC-13", no, f"合同已存在（{ag['agreementId']}），跳过写入")
    return scene

def scenario_101(admin, checker, tpl):
    no = "DEMO2-CL-101"
    if find_agreement(admin, no):
        return existing_scene(admin, no, f"{no}-ACQ")
    std = tpl["TPL-AE001-STANDARD"]["v1"]
    ag = create_client_agreement(admin, no, "演示 2 · 只收 Visa 借记卡 + 存卡（基于标准价、破标、已签署生效）", 990000003101)
    ag = set_products(admin, ag, ["ACQUIRING"])
    ac03_conditions(admin, agreement(admin, ag["agreementId"]))
    add_capability(admin, ag["agreementId"], "TOKEN_STORAGE", {})
    plan = plan_of(admin, f"{no}-ACQ", "CLIENT_PRICING", ag["agreementId"], client_id=CLIENTS[no][0])
    v = must(admin, "POST", f"{BASE}/commercial-terms/plans/{plan['planId']}/versions",
             {"expectedChangeVersion": plan["changeVersion"], "sourceTemplateVersionId": std}, f"{no} 基于标准价生成 v1")
    record["ids"].setdefault("versions", {})[f"{no}-ACQ v1"] = v["versionId"]
    ws = workspace(admin, v["versionId"])
    rows = price_rows(ws)
    check("AC-05", "只收 Visa 借记 AED：生成 Visa 借记 / 境外卡 / 兜底三行，无 Amex、无 USD、无 Apple / Google Pay",
          rows == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_STANDARD"), ("AED", "CARD_VISA_DEBIT")], rows)
    check("AC-05", "每行带来源模板价格，版本 sourceTemplateVersionId = 标准价 v1",
          all(p.get("sourceTemplatePriceId") for p in ws["prices"]) and ws["version"]["sourceTemplateVersionId"] == std,
          {"source": ws["version"]["sourceTemplateVersionId"], "rows": [p.get("sourceTemplatePriceId") for p in ws["prices"]]})
    debit = next(p for p in ws["prices"] if p["pricingProfileCode"] == "CARD_VISA_DEBIT")
    check("AC-05", "存卡功能已开通：Visa 借记行保留 TOKEN_STORED 组件",
          any(c["triggerCode"] == "TOKEN_STORED" for c in debit["components"]), [c["componentCode"] for c in debit["components"]])
    check("AC-07", "未调价时组件均 AT_OR_ABOVE，破标数 0",
          ws["version"]["belowStandardCount"] == 0 and all(c["benchmark"]["status"] == "AT_OR_ABOVE"
                                                           for p in ws["prices"] for c in p["components"]),
          {"belowStandardCount": ws["version"]["belowStandardCount"]})

    # 调价：Visa 借记 AED 费率 1.80% → 1.50%、SALE_FIXED 1.00 → 0.80（破标）；去掉境外卡行（随后补齐）；新增无来源的高端卡行
    prices = []
    for p in ws["prices"]:
        if p["pricingProfileCode"] == "CARD_INTERNATIONAL":
            continue
        w = write_price(p)
        if p["pricingProfileCode"] == "CARD_VISA_DEBIT":
            for c in w["components"]:
                if c["componentCode"] in ("CAPTURE_PCT", "SALE_PCT"):
                    c["rate"] = "0.0150"
                if c["componentCode"] == "SALE_FIXED":
                    c["fixedAmount"] = "0.80"
        prices.append(w)
    prices.append(client_price("AED", "CARD_PREMIUM", 7, acquiring("AED", "0.0290", "1.00", "3.00", "55.00")))
    v = must(admin, "PUT", f"{BASE}/commercial-terms/versions/{v['versionId']}/prices",
             {"expectedChangeVersion": ws["version"]["changeVersion"], "prices": prices}, f"{no} 调价（破标）")
    check("AC-06", "相关价格保存无相关性提示", v.get("relevanceWarnings") == [], v.get("relevanceWarnings"))
    v = must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/fill-from-template",
             {"expectedChangeVersion": v["changeVersion"], "templateVersionId": std}, f"{no} 从标准价补齐")
    ws = workspace(admin, v["versionId"])
    check("S5", "从标准价补齐只追加缺失的境外卡行（4 行，带来源）",
          price_rows(ws) == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_PREMIUM"), ("AED", "CARD_STANDARD"), ("AED", "CARD_VISA_DEBIT")]
          and next(p for p in ws["prices"] if p["pricingProfileCode"] == "CARD_INTERNATIONAL").get("sourceTemplatePriceId"),
          price_rows(ws))
    marks = {(p["pricingProfileCode"], c["componentCode"]): c["benchmark"]["status"] for p in ws["prices"] for c in p["components"]}
    below = sorted(k for k, s in marks.items() if s == "BELOW_STANDARD")
    check("AC-07", "Visa 借记 AED 的 CAPTURE_PCT / SALE_PCT（费率）与 SALE_FIXED（固定额）标破标，其余有来源组件不破标",
          below == [("CARD_VISA_DEBIT", "CAPTURE_PCT"), ("CARD_VISA_DEBIT", "SALE_FIXED"), ("CARD_VISA_DEBIT", "SALE_PCT")], below)
    debit_capture = next(c for p in ws["prices"] if p["pricingProfileCode"] == "CARD_VISA_DEBIT" for c in p["components"]
                         if c["componentCode"] == "CAPTURE_PCT")
    check("AC-07", "破标组件带模板值（standardRate 0.018）", str(debit_capture["benchmark"].get("standardRate")).rstrip("0") in ("0.018",),
          debit_capture["benchmark"])
    check("AC-07", "无来源的高端卡行标「无标准可比」NO_BENCHMARK",
          all(s == "NO_BENCHMARK" for (prof, _), s in marks.items() if prof == "CARD_PREMIUM"),
          {k: s for k, s in marks.items() if k[0] == "CARD_PREMIUM"})
    check("AC-07", "版本破标数 = 3", ws["version"]["belowStandardCount"] == 3, ws["version"]["belowStandardCount"])

    v = must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/submit", {"expectedChangeVersion": ws["version"]["changeVersion"]},
             f"{no} 提交复核")
    queue = must(admin, "GET", f"{BASE}/dashboard")["approvalQueue"]
    row = next((q for q in queue if q["versionId"] == v["versionId"]), None)
    check("AC-07", "工作台待复核队列显示该版本破标数 3", row and row.get("belowStandardCount") == 3, row)
    listed = next(r for r in versions(admin, plan["planId"]) if r["versionId"] == v["versionId"])
    check("AC-07", "版本列表行破标数 3、签署列 hasAcceptance=false",
          listed.get("belowStandardCount") == 3 and listed.get("hasAcceptance") is False,
          {k: listed.get(k) for k in ("belowStandardCount", "hasAcceptance", "signatureExempt")})
    v = must(checker, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/approve", {"expectedChangeVersion": v["changeVersion"]},
             f"{no} B 复核通过")
    expect_reject("AC-08", "签署方不是合同 CLIENT 参与方（OE Party）被拒", admin, "POST",
                  f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", sign_body(v, OE_PARTY[0], f"PA-{no}-V1-WRONG"))
    body = sign_body(v, CLIENTS[no][1], f"PA-{no}-V1")
    signed = must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", body, f"{no} 登记客户签署")
    acc = signed.get("acceptance") or {}
    check("AC-08", "登记签署（文件 ID + SHA-256 + 方式 + 时刻 + 编号）后为 CLIENT_SIGNED",
          signed.get("status") == "CLIENT_SIGNED" and acc.get("fileId") == body["fileId"] and acc.get("contentHash") == body["contentHash"]
          and acc.get("acceptanceReference") == body["acceptanceReference"] and acc.get("acceptanceMethod") == "E_SIGNATURE",
          {"status": signed.get("status"), "acceptance": acc})
    status, replay = http(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", body)
    check("AC-08", "同编号同内容重放返回原签署结果", status == 200 and (replay.get("acceptance") or {}).get("versionAcceptanceId")
          == acc.get("versionAcceptanceId"), {"status": status, "detail": detail(replay) if status >= 400 else None})
    schedule(admin, signed, f"{no} v1")
    return {"agreementId": ag["agreementId"], "planId": plan["planId"], "v1": v["versionId"]}


def scenario_102(admin, checker, tpl):
    no = "DEMO2-CL-102"
    if find_agreement(admin, no):
        return existing_scene(admin, no, f"{no}-ACQ")
    promo = tpl["TPL-AE001-PROMO"]["v1"]
    ag = create_client_agreement(admin, no, "演示 2 · Apple Pay 限 Visa / MC + 银行卡全卡组织（基于优惠价、待客户签署）", 990000003102)
    set_products(admin, ag, ["ACQUIRING"])
    add_capability(admin, ag["agreementId"], "APPLE_PAY", {"CARD_BRAND": ["VISA", "MASTERCARD"], "CURRENCY": ["AED", "USD"]})
    add_capability(admin, ag["agreementId"], "CARD", {"CURRENCY": ["AED", "USD"]})
    plan = plan_of(admin, f"{no}-ACQ", "CLIENT_PRICING", ag["agreementId"], client_id=CLIENTS[no][0])
    v = must(admin, "POST", f"{BASE}/commercial-terms/plans/{plan['planId']}/versions",
             {"expectedChangeVersion": plan["changeVersion"], "sourceTemplateVersionId": promo}, f"{no} 基于优惠价生成 v1")
    record["ids"].setdefault("versions", {})[f"{no}-ACQ v1"] = v["versionId"]
    ws = workspace(admin, v["versionId"])
    profiles = {prof for _, prof in price_rows(ws)}
    check("AC-05", "Apple Pay 限 Visa / MC + CARD 全卡组织：生成 CARD 四类 + Apple Pay × AED/USD 共 10 行，无 Google Pay",
          len(ws["prices"]) == 10 and "GOOGLE_PAY_STANDARD" not in profiles and "APPLE_PAY_STANDARD" in profiles
          and {c for c, _ in price_rows(ws)} == {"AED", "USD"}, price_rows(ws))
    check("AC-05", "存卡功能未开通：生成行不含 TOKEN_STORED 组件",
          not any(c["triggerCode"] == "TOKEN_STORED" for p in ws["prices"] for c in p["components"]),
          sorted({c["componentCode"] for p in ws["prices"] for c in p["components"]}))
    v = submit_approve(admin, checker, {"versionId": v["versionId"], "changeVersion": ws["version"]["changeVersion"]}, no)
    expect_reject("AC-08", "APPROVED 未签署的合同版本排期被拒", admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                  {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": now_iso(SCHEDULE_LEAD)}, needle="签署")
    final = workspace(admin, v["versionId"])["version"]
    check("AC-13", "DEMO2-CL-102 停在 APPROVED（待客户签署）", final["status"] == "APPROVED", final["status"])
    return {"agreementId": ag["agreementId"], "planId": plan["planId"], "v1": v["versionId"]}


def scenario_103(admin, checker, tpl):
    no = "DEMO2-CL-103"
    if find_agreement(admin, no):
        return existing_scene(admin, no, f"{no}-ACQ")
    ag = create_client_agreement(admin, no, "演示 2 · 银行卡生效后新开 Google Pay（已开通待定价）", 990000003103)
    set_products(admin, ag, ["ACQUIRING"])
    add_capability(admin, ag["agreementId"], "CARD", {"CURRENCY": ["AED", "USD"]})
    plan = plan_of(admin, f"{no}-ACQ", "CLIENT_PRICING", ag["agreementId"], client_id=CLIENTS[no][0])
    v = must(admin, "POST", f"{BASE}/commercial-terms/plans/{plan['planId']}/versions",
             {"expectedChangeVersion": plan["changeVersion"], "sourceTemplateVersionId": tpl["TPL-AE001-STANDARD"]["v1"]},
             f"{no} 基于标准价生成 v1")
    record["ids"].setdefault("versions", {})[f"{no}-ACQ v1"] = v["versionId"]
    ws = workspace(admin, v["versionId"])
    check("AC-05", "CARD 无卡组织限制 AED / USD：生成 CARD 四类 × 2 币种 8 行，无钱包", len(ws["prices"]) == 8
          and all(p["paymentMethodCode"] == "CARD" for p in ws["prices"]), price_rows(ws))
    v = submit_approve(admin, checker, {"versionId": v["versionId"], "changeVersion": ws["version"]["changeVersion"]}, no)
    v = must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", sign_body(v, CLIENTS[no][1], f"PA-{no}-V1"),
             f"{no} 登记客户签署")
    schedule(admin, v, f"{no} v1")
    return {"agreementId": ag["agreementId"], "planId": plan["planId"], "v1": v["versionId"]}


def scenario_pt101(admin, checker):
    no = "DEMO2-PT-101"
    if find_agreement(admin, no):
        return existing_scene(admin, no, f"{no}-SHARE")
    a = must(admin, "POST", f"{BASE}/agreements", {
        "agreementNo": no, "agreementName": "演示 2 · Local Partner 分润（价格协议签署后生效）", "agreementType": "PARTNER",
        "localPartnerId": PARTNER_ID, "operatingEntityNos": [OE], "effectiveFrom": now_iso(-86400),
        "parties": [{"partyId": PARTNER_PARTY[0], "partyProfileId": PARTNER_PARTY[1], "partyRole": "LOCAL_PARTNER"},
                    {"partyId": PLATFORM_PARTY[0], "partyProfileId": PLATFORM_PARTY[1], "partyRole": "PLATFORM_SIGNATORY"}],
        "documents": [{"documentType": "SIGNING_DOCUMENT", "fileId": "990000003201", "contentHash": doc_hash(no)}]}, f"合同 {no} 创建")
    change = must(admin, "POST", f"{BASE}/agreements/{a['agreementId']}/submit-for-signature", {"expectedChangeVersion": a["changeVersion"]},
                  f"合同 {no} 提交签署")["changeVersion"]
    for party in (PARTNER_PARTY[0], PLATFORM_PARTY[0]):
        change = accept_party(admin, a["agreementId"], no, change, party)
    must(admin, "POST", f"{BASE}/agreements/{a['agreementId']}/activate", {"expectedChangeVersion": change}, f"合同 {no} 生效")
    record["ids"].setdefault("agreements", {})[no] = a["agreementId"]
    plan = plan_of(admin, f"{no}-SHARE", "PARTNER_SHARE", a["agreementId"], partner_id=PARTNER_ID)
    v = must(admin, "POST", f"{BASE}/commercial-terms/plans/{plan['planId']}/versions",
             {"expectedChangeVersion": plan["changeVersion"], "pricingModel": "BLENDED", "feeTaxTreatment": "EXCLUSIVE",
              "agreementId": a["agreementId"]}, f"{no} 新建分润版本 v1")
    record["ids"].setdefault("versions", {})[f"{no}-SHARE v1"] = v["versionId"]
    prices = [{"entityNo": OE, "currency": c, "pricingProfileCode": "CARD_STANDARD", "priorityNo": 1,
               "components": [pct("SHARE_CAPTURE", "CAPTURE", "0.35", base="ACTUAL_CLIENT_FEE"),
                              pct("SHARE_SALE", "SALE", "0.35", base="ACTUAL_CLIENT_FEE")]} for c in ("AED", "USD")]
    v = must(admin, "PUT", f"{BASE}/commercial-terms/versions/{v['versionId']}/prices",
             {"expectedChangeVersion": v["changeVersion"], "prices": prices}, f"{no} 写分润价格")
    check("S6", "Partner 条款不受相关性约束（保存无提示）", not v.get("relevanceWarnings"), v.get("relevanceWarnings"))
    v = submit_approve(admin, checker, v, no)
    expect_reject("AC-08", "Partner 方案 APPROVED 未签署排期被拒", admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                  {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": now_iso(SCHEDULE_LEAD)}, needle="签署")
    expect_reject("AC-08", "Partner 方案签署方须为 LOCAL_PARTNER（平台签约方被拒）", admin, "POST",
                  f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", sign_body(v, PLATFORM_PARTY[0], f"PA-{no}-V1-WRONG"))
    v = must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance", sign_body(v, PARTNER_PARTY[0], f"PA-{no}-V1"),
             f"{no} 登记 Partner 签署")
    check("AC-08", "Partner 方案签署后 CLIENT_SIGNED", v.get("status") == "CLIENT_SIGNED", v.get("status"))
    schedule(admin, v, f"{no} v1")
    return {"agreementId": a["agreementId"], "planId": plan["planId"], "v1": v["versionId"]}


def blank_version(admin, plan_id, agreement_id, prices, label):
    change = plan_change(admin, agreement_id, plan_id)
    v = must(admin, "POST", f"{BASE}/commercial-terms/plans/{plan_id}/versions",
             {"expectedChangeVersion": change, "pricingModel": "BLENDED", "feeTaxTreatment": "EXCLUSIVE"}, f"{label} 新建版本")
    record["ids"].setdefault("versions", {})[label] = v["versionId"]
    return must(admin, "PUT", f"{BASE}/commercial-terms/versions/{v['versionId']}/prices",
                {"expectedChangeVersion": v["changeVersion"], "prices": prices}, f"{label} 写价格")


def to_signed(admin, checker, v, no, label, ref):
    v = submit_approve(admin, checker, v, label)
    return must(admin, "POST", f"{BASE}/commercial-terms/versions/{v['versionId']}/acceptance",
                sign_body(v, CLIENTS[no][1], f"PA-{no}-{ref}"), f"{label} 登记客户签署")


def scenario_901(admin, checker, tpl):
    """反例合同：零开通、完整性、相关性（版本停在 CLIENT_SIGNED，排期被拒），外汇未开通不生成 FX。"""
    no = "DEMO2-CL-901"
    if find_agreement(admin, no):
        skipped("AC-06", no, "反例合同已存在，跳过写入")
        return None
    ag = create_client_agreement(admin, no, "演示 2 · 校验反例（零开通 / 完整性 / 相关性，均不能生效）", 990000003901)
    set_products(admin, ag, ["ACQUIRING"])
    aid = ag["agreementId"]
    plan = plan_of(admin, f"{no}-ACQ", "CLIENT_PRICING", aid, client_id=CLIENTS[no][0])
    std_aed = lambda profile, priority: client_price("AED", profile, priority, acquiring("AED", "0.0260", "1.00", "3.00", "55.00"))  # noqa: E731

    # 零开通：OE 无开通项却有 Client 价格
    v = blank_version(admin, plan["planId"], aid, [std_aed("CARD_STANDARD", 1)], f"{no}-ACQ v1 零开通")
    v = to_signed(admin, checker, v, no, f"{no}-ACQ v1 零开通", "V1")
    expect_reject("AC-06", "零开通：OE 无开通项却有 Client 价格，排期（激活校验）被拒", admin, "POST",
                  f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                  {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": now_iso(SCHEDULE_LEAD)})

    # 完整性：CARD 限 VISA / MC × AED，只有 Visa 价
    add_capability(admin, aid, "CARD", {"CARD_BRAND": ["VISA", "MASTERCARD"], "CURRENCY": ["AED"]})
    v = blank_version(admin, plan["planId"], aid, [std_aed("CARD_VISA", 1)], f"{no}-ACQ v2 完整性")
    v = to_signed(admin, checker, v, no, f"{no}-ACQ v2 完整性", "V2")
    expect_reject("AC-06", "完整性：CARD 限 VISA / MC 时缺 MC 价，排期被拒", admin, "POST",
                  f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                  {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": now_iso(SCHEDULE_LEAD)}, needle="MASTERCARD")

    # 相关性：未开通的 Apple Pay 价、未开通的 USD 价
    v = blank_version(admin, plan["planId"], aid, [std_aed("CARD_STANDARD", 1), std_aed("APPLE_PAY_STANDARD", 2),
                                                   client_price("USD", "CARD_STANDARD", 1, acquiring("USD", "0.0290", "0.30", "1.00", "15.00"))],
                      f"{no}-ACQ v3 相关性")
    warnings = {(w["currency"], w["pricingProfileCode"], w["reasonCode"]) for w in v.get("relevanceWarnings") or []}
    check("AC-06", "相关性：保存含未开通支付方式 / 币种价格时返回提示列表且保存成功",
          ("AED", "APPLE_PAY_STANDARD", "PAYMENT_METHOD_NOT_ENABLED") in warnings and ("USD", "CARD_STANDARD", "CURRENCY_NOT_ENABLED") in warnings,
          v.get("relevanceWarnings"))
    ws = workspace(admin, v["versionId"])
    check("AC-06", "版本读模型按当前开通状态返回 relevanceWarnings", len(ws["version"].get("relevanceWarnings") or []) == len(warnings),
          ws["version"].get("relevanceWarnings"))
    v = to_signed(admin, checker, v, no, f"{no}-ACQ v3 相关性", "V3")
    expect_reject("AC-06", "相关性：排期（激活校验）被拒，detail 含开通项与价格标识", admin, "POST",
                  f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                  {"expectedChangeVersion": v["changeVersion"], "effectiveFrom": now_iso(SCHEDULE_LEAD)}, needle="APPLE_PAY")

    # 外汇未开通：基于外汇模板生成 0 行
    fx = tpl.get("TPL-AE001-FX", {}).get("v1")
    if fx:
        try:
            fx_check(admin, aid, no, fx)
        except Stop:
            check("AC-05", "未开通外汇：基于外汇点差模板生成", False, record["notes"][-1])
    return {"agreementId": aid, "planId": plan["planId"]}


def fx_check(admin, aid, no, fx):
    fx_plan = plan_of(admin, f"{no}-FX", "FX_SPREAD", aid, client_id=CLIENTS[no][0])
    status, created = http(admin, "POST", f"{BASE}/commercial-terms/plans/{fx_plan['planId']}/versions",
                           {"expectedChangeVersion": fx_plan["changeVersion"], "sourceTemplateVersionId": fx})
    rows = workspace(admin, created["versionId"])["prices"] if status == 201 else None
    check("AC-05", "未开通外汇：基于外汇点差模板生成 0 条 FX 价格", status == 201 and rows == [],
          {"status": status, "detail": detail(created) if status >= 400 else None, "rows": None if rows is None else len(rows)})


# ---------- 生效后 ----------

def after_activation(admin, s101, s102, s103, spt, activated):
    for label, s in (("DEMO2-CL-101", s101), ("DEMO2-CL-103", s103), ("DEMO2-PT-101", spt)):
        if s and s.get("v1"):
            status = activated.get(s["v1"]) or workspace(admin, s["v1"])["version"]["status"]
            check("AC-08", f"{label} 签署后排期 → 到点 ACTIVE", status == "ACTIVE", status)
    if s101:
        row = next(r for r in versions(admin, s101["planId"]) if r["versionId"] == s101["v1"])
        check("AC-08", "DEMO2-CL-101 版本列表 hasAcceptance=true、非免签",
              row.get("hasAcceptance") is True and row.get("signatureExempt") is False,
              {k: row.get(k) for k in ("status", "hasAcceptance", "signatureExempt", "belowStandardCount")})


def scenario_103_pending(admin, s103):
    """生效后新开 Google Pay → 已开通待定价；STC_PAY 开通后目录停用 / 启用。"""
    if not s103:
        return
    aid = s103["agreementId"]
    enabled = {c["capabilityCode"] for c in agreement(admin, aid)["capabilities"]}
    if "GOOGLE_PAY" not in enabled:
        add_capability(admin, aid, "GOOGLE_PAY", {"CURRENCY": ["AED"]}, label="DEMO2-CL-103 生效后新开 GOOGLE_PAY AED")
    ws = workspace(admin, s103["v1"])
    gp = [p for p in ws["prices"] if p["paymentMethodCode"] == "GOOGLE_PAY"]
    check("AC-09", "DEMO2-CL-103 GOOGLE_PAY 已开通、生效版本无该开通项价格（已开通 · 待定价）",
          any(c["capabilityCode"] == "GOOGLE_PAY" and c["status"] == "ENABLED" for c in agreement(admin, aid)["capabilities"]) and not gp,
          {"activeVersion": ws["version"]["status"], "googlePayPrices": len(gp)})
    card = must(admin, "GET", f"{BASE}/rate-cards?clientId={CLIENTS['DEMO2-CL-103'][0]}&currency=AED")
    caps = [c for a in card.get("agreements") or [] for tv in a.get("termVersions") or [] for c in tv.get("capabilities") or []]
    priced = {c["capabilityCode"]: sum(len(cur.get("profiles") or []) for cur in c.get("currencies") or []) for c in caps}
    check("AC-09", "费率卡按开通项分组：CARD 有价、GOOGLE_PAY 无价", priced.get("CARD", 0) > 0 and priced.get("GOOGLE_PAY", 0) == 0, priced)
    checkout(aid)
    stc = capability(admin, "STC_PAY")
    if not stc or "STC_PAY" in enabled:
        skipped("AC-01", "停用 / 启用 STC_PAY", "STC_PAY 已在 DEMO2-CL-103 开通（先前运行已验证），跳过")
        return
    add_capability(admin, aid, "STC_PAY", {"CURRENCY": ["AED"]}, label="DEMO2-CL-103 开通 STC_PAY AED（停用前）")
    retired = must(admin, "POST", f"{BASE}/products/capabilities/{stc['productCapabilityId']}/retire",
                   {"expectedChangeVersion": stc["changeVersion"]}, "目录停用 STC_PAY")
    check("AC-01", "停用后目录状态 RETIRED", retired.get("status") == "RETIRED", retired.get("status"))
    cap = next((c for c in agreement(admin, aid)["capabilities"] if c["capabilityCode"] == "STC_PAY"), None)
    check("AC-01", "停用不影响已开通：DEMO2-CL-103 的 STC_PAY 仍 ENABLED", cap and cap["status"] == "ENABLED", cap)
    other = record["ids"].get("agreements", {}).get("DEMO2-CL-102")
    if other:
        ag = agreement(admin, other)
        expect_reject("AC-01", "停用后新开通 STC_PAY 被拒（DEMO2-CL-102）", admin, "POST", f"{BASE}/agreements/{other}/capabilities",
                      cap_body(ag["changeVersion"], "STC_PAY", {"CURRENCY": ["AED"]}))
    restored = must(admin, "POST", f"{BASE}/products/capabilities/{stc['productCapabilityId']}/activate",
                    {"expectedChangeVersion": retired["changeVersion"]}, "目录重新启用 STC_PAY")
    check("AC-01", "重新启用后 ACTIVE", restored.get("status") == "ACTIVE", restored.get("status"))


def checkout(agreement_id):
    """AC-09 直连：Checkout 解析（acquiring 调用方）。Risk 资格未接入时预期失败关闭，记录实际状态。"""
    body = {"clientId": CLIENTS["DEMO2-CL-103"][0], "orderNo": "STAGE7-CAP-0001", "consumerCountryCode": "AE", "currency": "AED",
            "locale": "en-AE", "contextAsOfTime": now_iso()}
    try:
        status, text = e2e_http.private_call("commercial", "POST", "/checkout-context/v1/resolutions",
                                             headers={"X-Caller-Service": "acquiring-service"}, body=body)
    except Exception as failure:  # noqa: BLE001
        status, text = None, str(failure)[:300]
    record["checkout"] = {"status": status, "body": text[:600]}
    if status == 200:
        resolved = json.loads(text)
        caps = (resolved.get("clientTerms") or {}).get("capabilities") or []
        codes = {c["capabilityCode"] for c in caps}
        check("AC-09", "Checkout 解析含开通项接受条件与 3DS，无价 GOOGLE_PAY 不出现",
              "CARD" in codes and "GOOGLE_PAY" not in codes and all("conditions" in c and "require3ds" in c for c in caps), caps)
    else:
        skipped("AC-09", "Checkout 解析端到端（commercial /checkout-context/v1/resolutions）",
                f"mor-test 返回 {status}：{text[:300]}（Risk 资格合同未接入时失败关闭；以 commercial auto 测试与费率卡间接核对）")


TEMPLATE_SETTLEMENT = {"settlementBusinessDays": 2, "businessCalendarCode": "AE_BUSINESS_DAYS", "settlementTimezone": "UTC",
                       "cutoffSecondOfDay": 86399}


def template_v2(admin, checker, tpl):
    """标准价 v2（复制 v1，Visa 借记 AED 下调）；可从 DRAFT / APPROVED / SCHEDULED 续做。"""
    info = tpl.get("TPL-AE001-STANDARD")
    plan_row = next((t for t in templates(admin) if info and t["planId"] == info["planId"]), None)
    if not plan_row or plan_row.get("latestVersionNo") not in ("1", "2"):
        skipped("AC-10", "标准价 v2", f"最新版本 {plan_row and plan_row.get('latestVersionNo')}，跳过")
        return None
    route = "commercial-terms/template-versions"
    if plan_row["latestVersionNo"] == "1":
        v = must(admin, "POST", f"{BASE}/commercial-terms/templates/{info['planId']}/versions",
                 {"expectedChangeVersion": plan_row["changeVersion"], "copyFromVersionId": info["v1"]}, "标准价 v2 复制 v1")
        version_id = v["versionId"]
    else:
        version_id = plan_row["latestVersionId"]
    info["v2"] = version_id
    ws = workspace(admin, version_id)
    status = ws["version"]["status"]
    if status == "DRAFT":
        check("缺陷", "模板版本读模型不返回结算四字段（CommercialPriceConverter 按 clientId 判断 Client 条款）",
              all(p.get("settlement") for p in ws["prices"]), {"settlement": [p.get("settlement") for p in ws["prices"][:2]]})
        prices = []
        for p in ws["prices"]:
            w = write_price(p)
            if not p.get("settlement"):
                w.update(TEMPLATE_SETTLEMENT)   # 绕过读模型缺陷：按 v1 写入值补回结算四字段
            if (p["currency"], p["pricingProfileCode"]) == ("AED", "CARD_VISA_DEBIT"):
                for c in w["components"]:
                    if c["componentCode"] in ("CAPTURE_PCT", "SALE_PCT"):
                        c["rate"] = "0.0170"
            prices.append(w)
        v = must(admin, "PUT", f"{BASE}/{route}/{version_id}/prices",
                 {"expectedChangeVersion": ws["version"]["changeVersion"], "prices": prices}, "标准价 v2 Visa 借记 AED 下调到 1.70%")
        v = submit_approve(admin, checker, v, "标准价 v2", route=route)
        status = v["status"]
        ws = {"version": v}
    if status == "PENDING_APPROVAL":
        ws = {"version": must(checker, "POST", f"{BASE}/{route}/{version_id}/approve",
                              {"expectedChangeVersion": ws["version"]["changeVersion"]}, "标准价 v2 B 复核通过")}
        status = "APPROVED"
    if status == "APPROVED":
        schedule(admin, ws["version"], "标准价 v2", route=route)
    elif status == "SCHEDULED":
        pending.append(("标准价 v2（已排期）", version_id, route, max(ws["version"]["effectiveFrom"][:19] + "Z", now_iso())))
    return version_id


def ac10(admin, s101, before, tpl, activated):
    v2 = tpl.get("TPL-AE001-STANDARD", {}).get("v2")
    if not (s101 and v2):
        return
    status = activated.get(v2) or workspace(admin, v2)["version"]["status"]
    check("AC-10", "标准价 v2 生效", status == "ACTIVE", status)
    ws = workspace(admin, s101["v1"])
    update = ws["version"].get("templateUpdate") or {}
    check("AC-10", "DEMO2-CL-101 v1 显示「标准价已更新」（latestVersionNo=2）",
          update.get("latestTemplateVersionId") == v2 and str(update.get("latestVersionNo")) == "2", update)
    check("AC-10", "DEMO2-CL-101 合同价格不变", price_signature(ws) == before, None)


# ---------- AC-08 免签 / AC-11 迁移 ----------

def ac08_exempt(admin):
    rows = {r["versionNo"]: r for r in versions(admin, LEGACY_ACQ_PLAN)}
    check("AC-08", "存量 DEMO-CL-001-ACQ v1 ACTIVE / v2 SCHEDULED 为免签",
          rows["1"]["status"] == "ACTIVE" and rows["1"]["signatureExempt"] is True
          and rows["2"]["status"] == "SCHEDULED" and rows["2"]["signatureExempt"] is True,
          {n: (r["status"], r["signatureExempt"]) for n, r in rows.items()})
    pending_row = next(r for r in versions(admin, LEGACY_PENDING_PLAN) if r["versionNo"] == "1")
    check("AC-08", "存量 PENDING_APPROVAL 版本（DEMO-CL-002-ACQ v1）非免签，今后须签署",
          pending_row["status"] == "PENDING_APPROVAL" and pending_row["signatureExempt"] is False,
          (pending_row["status"], pending_row["signatureExempt"]))


def ac11_migration(admin):
    expected = {"CL-F1105E": {"CARD_BRAND": ["VISA"], "CURRENCY": ["AED", "USD"]},
                "DEMO-CL-001": {"CARD_BRAND": ["AMEX", "MASTERCARD", "VISA"], "CURRENCY": ["AED", "USD"]},
                "DEMO-CL-002": {"CARD_BRAND": ["VISA"], "CURRENCY": ["AED"]}}
    for no, aid in LEGACY.items():
        caps = agreement(admin, aid)["capabilities"]
        card = [c for c in caps if c["capabilityCode"] == "CARD"]
        check("AC-11", f"{no} 的 CARD_* 合并为一条 CARD，条件 = 原卡组织并集、币种并集；无 CARD_* 残留",
              len(card) == 1 and cap_conditions(card[0]) == expected[no] and not any(c["capabilityCode"].startswith("CARD_") for c in caps),
              {"capabilities": [(c["capabilityCode"], cap_conditions(c)) for c in caps]})
    reserve = next(c for c in agreement(admin, LEGACY["CL-F1105E"])["capabilities"] if c["capabilityCode"] == "CARD")
    check("AC-11", "CL-F1105E 保留行的保证金沿用（5% / 180 天）", reserve.get("rollingReserveDays") == 180
          and str(reserve.get("rollingReserveRate", "")).startswith("0.05"), {k: reserve.get(k) for k in ("rollingReserveRate", "rollingReserveDays")})
    changes = must(admin, "GET", f"{BASE}/business-changes?aggregateRootType=AGREEMENT&aggregateRootId={LEGACY['DEMO-CL-001']}&page=1&size=100")
    text = json.dumps(changes, ensure_ascii=False)
    check("AC-11", "历史可追溯：DEMO-CL-001 业务变更历史仍含迁移前 CARD_VISA / CARD_MC / CARD_AMEX 开通记录",
          all(code in text for code in ("CARD_VISA", "CARD_MC", "CARD_AMEX")), {"total": changes.get("total"),
                                                                                "found": [c for c in ("CARD_VISA", "CARD_MC", "CARD_AMEX") if c in text]})
    identity = data_identity()
    check("AC-11", "价格数据与迁移前备份逐行一致（价格 / 组件 / 档位 / 画像 / 画像条件，迁移前存在的行）",
          identity and all(v["same"] == v["backup"] and v["backup"] > 0 for k, v in identity.items() if k != "t_commercial_term_component_band"),
          identity)
    simulate_grid(admin)


def card(brand, funding, region, level):
    return {"CARD_BRAND": brand, "FUNDING_TYPE": funding, "ISSUER_REGION": region, "CARD_LEVEL": level}


SIM = [  # (币种, 支付方式, 维度, 预期画像)；价格用到的维度须全部给出（命中引擎对缺失事实失败关闭）
    ("USD", "CARD", card("VISA", "CREDIT", "DOMESTIC", "CLASSIC"), "CARD_VISA_CREDIT_DOMESTIC"),
    ("USD", "CARD", card("AMEX", "CREDIT", "DOMESTIC", "CLASSIC"), "CARD_AMEX"),
    ("AED", "CARD", card("AMEX", "CREDIT", "DOMESTIC", "CLASSIC"), "CARD_STANDARD"),
    ("USD", "CARD", card("MASTERCARD", "CREDIT", "INTERNATIONAL", "CLASSIC"), "CARD_INTERNATIONAL"),
    ("USD", "CARD", card("VISA", "DEBIT", "DOMESTIC", "PLATINUM"), "CARD_PREMIUM"),
    ("AED", "APPLE_PAY", {}, "APPLE_PAY_STANDARD"),
]


def simulate_grid(admin):
    v1 = next(r for r in versions(admin, LEGACY_ACQ_PLAN) if r["versionNo"] == "1")
    results = []
    for currency, method, dims, want in SIM:
        status, parsed = http(admin, "POST", f"{BASE}/commercial-prices/simulate", {
            "termVersionId": v1["versionId"], "entityNo": OE, "agreementId": LEGACY["DEMO-CL-001"], "termType": "CLIENT_PRICING",
            "currency": currency, "contextAsOfTime": now_iso(), "clientId": v1["clientId"], "amount": "100.00",
            "paymentFacts": {"paymentMethodCode": method, "dimensions": dims}})
        got = (parsed.get("price") or {}).get("pricingProfileCode") if status == 200 else detail(parsed)
        results.append({"facts": [currency, method, dims], "want": want, "status": status, "got": got})
    check("AC-11", "DEMO-CL-001-ACQ v1 价格试算命中与迁移前画像语义一致（6 组事实）",
          all(r["status"] == 200 and r["got"] == r["want"] for r in results), results)


def parse_dump(path, tables):
    """mysqldump 的 CREATE TABLE 列名 + INSERT 值 → {表: [行 dict]}。"""
    text = path.read_text()
    out = {}
    for table in tables:
        create = re.search(rf"CREATE TABLE `{table}` \((.*?)\n\) ENGINE", text, re.S)
        columns = re.findall(r"^\s+`(\w+)`", create.group(1), re.M)
        rows = []
        for insert in re.finditer(rf"INSERT INTO `{table}` VALUES (.*?);\n", text, re.S):
            rows.extend(dict(zip(columns, values)) for values in split_values(insert.group(1)))
        out[table] = rows
    return out


def split_values(blob):
    rows, row, i, n = [], [], 0, len(blob)
    while i < n:
        ch = blob[i]
        if ch == "(":
            row, i = [], i + 1
        elif ch == ")":
            rows.append(row)
            i += 1
        elif ch in ", ":
            i += 1
        elif ch == "'":
            j, buf = i + 1, []
            while blob[j] != "'":
                if blob[j] == "\\":
                    buf.append({"n": "\n", "t": "\t", "0": "\0", "r": "\r"}.get(blob[j + 1], blob[j + 1]))
                    j += 2
                else:
                    buf.append(blob[j])
                    j += 1
            row.append("".join(buf))
            i = j + 1
        else:
            j = i
            while blob[j] not in ",)":
                j += 1
            token = blob[i:j]
            row.append(None if token == "NULL" else token)
            i = j
    return rows


def root_rows(table, columns):
    env_text = (MAIN / "test-environment/.env.test").read_text()
    password = re.search(r"^MOR_MYSQL_ROOT_PASSWORD=(.*)$", env_text, re.M).group(1).strip().strip('"')
    result = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                             "--default-character-set=utf8mb4", "mor_commercial", "-e",
                             f"SELECT {','.join(columns)} FROM {table}"], env={**os.environ, "MYSQL_PWD": password},
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [[None if v == "NULL" else v for v in line.split("\t")] for line in result.stdout.splitlines()]


IDENTITY_TABLES = {"t_commercial_term_price": "price_id", "t_commercial_term_component": "component_id",
                   "t_commercial_term_component_band": "band_id", "t_pricing_profile": "pricing_profile_id",
                   "t_pricing_profile_cond": "pricing_profile_cond_id"}


def data_identity():
    if not BACKUP.exists():
        return None
    try:
        dump = parse_dump(BACKUP, list(IDENTITY_TABLES))
        result = {}
        for table, rows in dump.items():
            if not rows:
                result[table] = {"backup": 0, "same": 0}
                continue
            columns = [c for c in rows[0] if c not in ("update_time",)]
            key = columns[0]
            current = {r[0]: r for r in root_rows(table, columns)}
            same = sum(1 for r in rows if current.get(r[key]) == [r[c] for c in columns])
            result[table] = {"backup": len(rows), "same": same, "currentTotal": len(current)}
        return result
    except Exception as failure:  # noqa: BLE001
        record["notes"].append({"dataIdentityError": str(failure)[:300]})
        return None


# ---------- 主流程 ----------

def main():
    s = e2e_http.secrets()
    admin = login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])["accessToken"]
    checker = None
    try:
        checker = create_checker(admin)
        events = ac02_catalog(admin)
        ac01_catalog(admin, events)
        save()
        ensure_profiles(admin)
        tpl = ensure_templates(admin, checker)
        save()
        activated = activate_pending(admin)
        ac04_after_activation(admin, tpl, activated)
        save()
        scenes = {}
        for name, fn in (("101", lambda: scenario_101(admin, checker, tpl)), ("102", lambda: scenario_102(admin, checker, tpl)),
                         ("103", lambda: scenario_103(admin, checker, tpl)), ("PT101", lambda: scenario_pt101(admin, checker)),
                         ("901", lambda: scenario_901(admin, checker, tpl))):
            try:
                scenes[name] = fn()
            except Stop:
                check("AC-13", f"场景 {name} 写入", False, record["notes"][-1] if record["notes"] else None)
                scenes[name] = None
            save()
        record["ids"]["scenes"] = scenes
        if scenes.get("101"):
            ac04_not_resolvable(admin, tpl, scenes["101"]["agreementId"], CLIENTS["DEMO2-CL-101"][0])
        before101 = price_signature(workspace(admin, scenes["101"]["v1"])) if scenes.get("101") else None
        try:
            template_v2(admin, checker, tpl)
        except Stop:
            check("AC-10", "标准价 v2 建立", False, record["notes"][-1] if record["notes"] else None)
        activated = activate_pending(admin)
        after_activation(admin, scenes.get("101"), scenes.get("102"), scenes.get("103"), scenes.get("PT101"), activated)
        ac10(admin, scenes.get("101"), before101, tpl, activated)
        save()
        try:
            scenario_103_pending(admin, scenes.get("103"))
        except Stop:
            check("AC-09", "DEMO2-CL-103 已开通待定价", False, record["notes"][-1] if record["notes"] else None)
        ac08_exempt(admin)
        ac11_migration(admin)
    except Stop:
        print("[中止] 前置步骤失败")
    finally:
        try:
            if record["ids"].get("checkerAccountId") or record["ids"].get("checkerRoleId"):
                disable_checker(admin)
        except Stop:
            record["notes"].append({"warning": "合成审批人停用失败，需手工处理"})
        checks = record["checks"]
        record["summary"] = {"passed": sum(1 for c in checks if c["ok"] is True), "failed": sum(1 for c in checks if c["ok"] is False),
                             "skipped": sum(1 for c in checks if c["ok"] is None)}
        save()
        print(f"\n{record['summary']}  运行记录 {OUT}")


if __name__ == "__main__":
    main()
