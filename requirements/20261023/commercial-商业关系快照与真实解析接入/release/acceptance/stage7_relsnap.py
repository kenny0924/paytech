"""商业关系快照与真实解析接入 · 阶段 7 mor-test 后端验收（release.md 第 8 节 S-0—S-6 与 AC-13）。

写法同 requirements/20261022/acquiring-到期后支付结果确认/release/acceptance/accept_result_after_expiry.py：
- E2E_TARGET=test；私网调用经接入 mor-test-backend 的一次性探针容器（e2e_http.private_call，请求经 stdin，不进命令行）；
- Client 侧：mor-test 没有可用的 Client API 密钥，私网直连 acquiring /client-gateway-api/v1，带 Gateway 注入的可信 Header；
- 商业解析：私网直连 commercial POST /checkout-context/v1/resolutions（X-Caller-Service: acquiring-service）；
- 资格撤销 / 重登（只在 S-3）：经运营入口 http://localhost:28080 → Gateway → ops-bff（块 137 路由），管理员取 test-environment/private/access.md；
- 库只读 SELECT（服务自有账号，口令经 MYSQL_PWD，不打印）；开关值只读两个非机密键（docker inspect 后在内存中取值，其余变量不读取、不输出）。
不打印口令、令牌、Checkout Public Token（chk_…）；结果合并写入同目录 stage7-relsnap-result.json，逐行过程写 stage7-relsnap.txt。

步骤（每步可单独执行；--dry-run 只打印将发送的请求与将执行的 SELECT，不连接 mor-test 任何接口或数据库）：
  preflight  门禁只读核对：两个开关的当前值、7 个容器镜像、PayFac Partner 合同与两个方案的 ACTIVE 版本与价格、三条资格、AE001 主档、DLQ 基线
  S-0 / S-6  MOCK 建单（要求 acquiring=MOCK）：201，AE001 + PARTNER + LP 107，绑定 0 行，生效版本 0
  S-1        直连解析 DEMO-CL-001（不传支付方式）：C1 八个角色、THROUGH_CUSTODIAN、PARTNER + PayFac 两个版本、卖方公开文本、开通项含 CARD / APPLE_PAY
  S-2        REAL 建单（要求 acquiring=REAL、快照开关=false）：绑定 8 行、上下文三列、Partner 字段、t_order_legal.public_* = 卖方披露
  S-3        REAL 失败映射：无 Affiliation 的 Client 建单被拒、不落单；撤销 DEMO-CL-001 资格后建单 CHECKOUT_NOT_ELIGIBLE、不落单；之后按原有效期重登
  S-4        （可选，需先人工完成一笔 APPLE_PAY 支付）--since <UTC>：billing 事实为 AE001 + PayFac Partner，billing DLQ 与 preflight 基线相比无新增
  S-5        快照生效（要求 acquiring=REAL、快照开关=true）：新订单生效版本 1，S-2 的订单仍为 0
  AC-13      只读：acquiring / risk-decision / client-master 中 PLATFORM 来源 0 行，risk 目录行 2
用法：python3 -I stage7_relsnap.py --step <步骤> [--dry-run] [--since <UTC>]；python3 -I stage7_relsnap.py --help
"""
import argparse
import json
import os
import re
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
RELEASE = HERE.parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
sys.path.insert(0, str(MOR_ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))

OUT = HERE / "stage7-relsnap-result.json"
LOG = HERE / "stage7-relsnap.txt"
PROBE_IMAGE = os.environ.get("RELSNAP_PROBE_IMAGE", "sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7")  # 现网 payment，只用其 JDK
CLIENT_API = "/client-gateway-api/v1"
AC11_CLIENT = "237575207592763393"          # DEMO-CL-001（D-3 补丁 23 只补它）
UNAFFILIATED_CLIENT = "239260386824658945"  # ACTIVE、无 commercial 合同、无 AE001 Affiliation
PAYFAC = "900000000001370001"
MOCK_PARTNER = "107"                        # acquiring 新 Mock 的占位 Local Partner
AE001_PARTY = "880000000702"
PLATFORM_PARTY = "880000000701"
SELLER = ("AE001 Demo Operating Entity FZ-LLC", "MORTEST-REG-AE001")  # 21 种子（仅供测试，D-1）
MOR_ROLES = {"CLIENT": ("CLIENT", AC11_CLIENT), "ACQUIRING_OE": ("OPERATING_ENTITY", "AE001"),
             "OE_PROVIDER": ("LOCAL_PARTNER", PAYFAC), "FUNDS_CUSTODIAN": ("OPERATING_ENTITY", "AE001"),
             "PLATFORM_FEE_PAYER": ("LOCAL_PARTNER", PAYFAC), "PLATFORM": ("PLATFORM", None),
             "SELLER": ("OPERATING_ENTITY", "AE001"), "TAX_RESPONSIBLE": ("OPERATING_ENTITY", "AE001")}
SWITCHES = {"acquiring-service": "ACQUIRING_COMMERCIAL_RESOLUTION_MODE", "commercial-service": "COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED"}
DLQ = "billing.acquiring.dlq"
DRY = False
SECRETS = set()


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")[:-4] + "Z"


def scrub(text):
    text = str(text)
    for secret in SECRETS:
        if secret:
            text = text.replace(secret, "<redacted>")
    return re.sub(r"chk_[A-Za-z0-9]{8,}", "chk_<redacted>", text)


def log(line):
    line = scrub(f"{now()} {line}")
    print(line, flush=True)
    if not DRY:
        with LOG.open("a") as handle:
            handle.write(line + "\n")


def load():
    return json.loads(OUT.read_text()) if OUT.exists() else {}


def save(name, data):
    if DRY:
        log(f"[dry-run] {name} 不写结果文件")
        return
    results = load()
    results[name] = data
    text = scrub(json.dumps(results, ensure_ascii=False, indent=1))
    OUT.write_text(text + "\n")
    log(f"{name} saved verdict={json.dumps(data.get('verdict'), ensure_ascii=False)}")


def verdict(checks):
    failed = [name for name, ok in checks.items() if not ok]
    return "PASS" if not failed else "FAIL: " + ", ".join(failed)


def h():
    import e2e_http as module  # 延迟导入：--help / --dry-run 不需要它
    module.PROBE_IMAGE = PROBE_IMAGE
    return module


# ---------- 只读与调用 ----------

def sql(service, statement):
    if not statement.lstrip().upper().startswith("SELECT"):
        raise SystemExit("阶段 7 验收只允许只读 SELECT")
    if DRY:
        log(f"[dry-run] SQL {service}: {statement[:200]}")
        return []
    return h().sql(service, statement)


def call(service, method, path, headers=None, body=None):
    if DRY:
        log(f"[dry-run] {method} {service}{path} headers={sorted((headers or {}).keys())} body={json.dumps(body, ensure_ascii=False)[:300]}")
        return 0, {}
    try:
        status, text = h().private_call(service, method, path, headers=headers, body=body)
    except RuntimeError as failure:
        raise RuntimeError(scrub(failure)) from None
    try:
        return status, (json.loads(text) if text else {})
    except ValueError:
        return status, {"raw": scrub(text[:300])}


def switch(service):
    """只读取一个非机密开关的值（容器 Config.Env 在内存中过滤，其余键不输出）。"""
    if DRY:
        return "<dry-run>"
    out = subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", "{{json .Config.Env}}"],
                         capture_output=True, text=True, check=True).stdout
    key = SWITCHES[service] + "="
    return next((item[len(key):] for item in json.loads(out) if item.startswith(key)), None)


def image(service):
    if DRY:
        return "<dry-run>"
    return subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", "{{.Image}}"],
                          capture_output=True, text=True).stdout.strip()


def require_switches(acquiring, snapshot):
    values = {"acquiring": switch("acquiring-service"), "snapshot": switch("commercial-service")}
    log(f"开关：acquiring={values['acquiring']} snapshot={values['snapshot']}")
    if DRY:
        return values
    if (acquiring and values["acquiring"] != acquiring) or (snapshot and values["snapshot"] != snapshot):
        raise SystemExit(f"开关不符合本步要求（acquiring={acquiring} snapshot={snapshot}），先按 release.md 第 6 节切换")
    return values


def candidate_images():
    images, current = {}, None
    for line in (RELEASE / "compose-candidate.yaml").read_text().splitlines():
        if re.match(r"^  [a-z][a-z-]+:$", line):
            current = line.strip()[:-1]
        match = re.match(r"^    image: (sha256:[0-9a-f]{64})", line)
        if match and current:
            images[current] = match.group(1)
    return images


def payfac_terms():
    """PayFac Partner 合同与两个方案的 ACTIVE 版本及其 USD 价格画像（commercial 只读）。"""
    agreements = sql("commercial", "SELECT agreement_id, status FROM t_agreement WHERE agreement_type='PARTNER' "
                                   f"AND local_partner_id={PAYFAC}")
    versions = sql("commercial", "SELECT p.term_type, v.term_version_id, GROUP_CONCAT(pr.pricing_profile_code ORDER BY pr.priority_no) "
                                 "FROM t_commercial_term_plan p JOIN t_commercial_term_version v ON v.term_plan_id=p.term_plan_id AND v.status='ACTIVE' "
                                 "LEFT JOIN t_commercial_term_price pr ON pr.term_version_id=v.term_version_id AND pr.entity_no='AE001' AND pr.currency='USD' "
                                 f"WHERE p.local_partner_id={PAYFAC} GROUP BY p.term_type, v.term_version_id")
    return agreements, {row[0]: {"versionId": row[1], "profiles": (row[2] or "").split(",")} for row in versions}


def qualification(role_type, role_id):
    rows = sql("compliance", "SELECT qualification_id, qualification_type, status, version, "
                             "IFNULL(DATE_FORMAT(valid_from,'%Y-%m-%dT%H:%i:%s.%fZ'),''), IFNULL(valid_to,'') FROM t_role_qualification "
                             f"WHERE role_type='{role_type}' AND role_id='{role_id}'")
    return [dict(zip(("qualificationId", "type", "status", "version", "validFrom", "validTo"), r)) for r in rows]


def dlq_messages():
    if DRY:
        return None
    return {name: messages for name, messages, _ in h().rabbit_queues("billing.")}.get(DLQ)


# ---------- 建单与核对 ----------

def checkout(label, client_id):
    client_order_id = f"relsnap-s7-{label.lower()}-{uuid.uuid4().hex[:8]}"
    body = {"clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": "US",
            "items": [{"clientItemId": "line-1", "name": "Relsnap stage seven item", "productTypeCode": "SERVICE",
                       "taxCategoryCode": "STANDARD", "quantity": "1", "unitAmount": "12.34"}]}
    headers = {"Accept": "application/json", "X-Request-Id": "req-relsnap-s7-" + uuid.uuid4().hex[:12],
               "X-Gateway-Client-Id": client_id, "X-Gateway-Credential-Id": "1"}
    status, response = call("acquiring", "POST", CLIENT_API + "/checkouts", headers, body)
    token = (response or {}).get("checkoutPublicToken") or (response or {}).get("publicToken")
    if token:
        SECRETS.add(token)
    return client_order_id, status, response


def order_rows(client_id, client_order_id):
    order = sql("acquiring", "SELECT order_id, order_no, entity_no, entity_source_type, IFNULL(local_partner_id,'NULL') FROM t_commerce_order "
                             f"WHERE client_id={client_id} AND client_order_id='{client_order_id}'")
    if not order:
        return None
    oid = order[0][0]
    rows = {"order": dict(zip(("orderId", "orderNo", "entityNo", "sourceType", "localPartnerId"), order[0]))}
    ctx = sql("acquiring", "SELECT IFNULL(fund_flow,'NULL'), effective_relationship_model_version, IFNULL(snapshot_model_version,'NULL'), "
                           "IFNULL(partner_share_version_id,'NULL'), IFNULL(platform_charge_version_id,'NULL'), IFNULL(platform_service_party_id,'NULL') "
                           f"FROM t_order_context WHERE order_id={oid}")
    rows["context"] = dict(zip(("fundFlow", "effectiveVersion", "snapshotVersion", "shareVersionId", "chargeVersionId", "platformPartyId"),
                               ctx[0])) if ctx else None
    rows["bindings"] = {r[0]: (r[1], None if r[2] == "NULL" else r[2], None if r[3] == "NULL" else r[3]) for r in sql(
        "acquiring", "SELECT role_code, subject_type, IFNULL(subject_id,'NULL'), IFNULL(party_id,'NULL') FROM t_order_relationship_binding "
                     f"WHERE order_id={oid}")}
    legal = sql("acquiring", "SELECT l.public_legal_name, l.public_registration_number FROM t_order_legal l "
                             f"JOIN t_order_context c ON c.order_context_id=l.order_context_id WHERE c.order_id={oid}")
    rows["legal"] = list(legal[0]) if legal else None
    return rows


def step_mock(name):
    require_switches("MOCK", None)
    client_order_id, status, response = checkout(name, AC11_CLIENT)
    rows = None if DRY else order_rows(AC11_CLIENT, client_order_id)
    checks = {"http201": status == 201, "orderFrozen": bool(rows)}
    if rows:
        o, c = rows["order"], rows["context"] or {}
        checks.update(source=(o["entityNo"], o["sourceType"], o["localPartnerId"]) == ("AE001", "PARTNER", MOCK_PARTNER),
                      noBindings=not rows["bindings"], effective0=c.get("effectiveVersion") == "0", fundFlowNull=c.get("fundFlow") == "NULL")
    save(name, {"at": now(), "clientOrderId": client_order_id, "http": status, "code": (response or {}).get("code"), "rows": rows,
                "checks": checks, "verdict": verdict(checks)})


def step_resolve():
    switches = require_switches(None, None)
    request = {"clientId": AC11_CLIENT, "orderNo": "RELSNAPS7" + uuid.uuid4().hex[:10].upper(), "consumerCountryCode": "US",
               "currency": "USD", "locale": "en", "contextAsOfTime": now()}
    status, body = call("commercial", "POST", "/checkout-context/v1/resolutions",
                        {"X-Caller-Service": "acquiring-service", "X-Request-Id": "req-relsnap-s7-" + uuid.uuid4().hex[:12]}, request)
    _, terms = payfac_terms()
    snap = (body or {}).get("relationshipSnapshot") or {}
    roles = {b.get("role"): b for b in snap.get("bindings") or []}
    partner = (body or {}).get("partnerTerms") or {}
    seller = ((body or {}).get("legal") or {}).get("seller") or {}
    capabilities = {c.get("capabilityCode") for c in ((body or {}).get("clientTerms") or {}).get("capabilities") or []}
    expected_effective = 1 if switches.get("snapshot") == "true" else 0
    checks = {
        "http200": status == 200,
        "entity": body.get("entityNo") == "AE001" and body.get("sourceType") == "PARTNER",
        "snapshot": snap.get("fundFlow") == "THROUGH_CUSTODIAN" and snap.get("businessMode") == "MOR"
        and snap.get("snapshotModelVersion") == 1 and snap.get("effectiveModelVersion") == expected_effective,
        "roles": set(roles) == set(MOR_ROLES) and all((roles[r].get("subjectType"), roles[r].get("subjectId")) == v for r, v in MOR_ROLES.items()),
        "parties": (roles.get("PLATFORM") or {}).get("partyId") == PLATFORM_PARTY and (roles.get("ACQUIRING_OE") or {}).get("partyId") == AE001_PARTY,
        "partnerTerms": partner.get("localPartnerId") == PAYFAC and partner.get("platformSignatoryPartyId") == PLATFORM_PARTY
        and partner.get("shareVersionId") == (terms.get("PARTNER_SHARE") or {}).get("versionId")
        and partner.get("serviceChargeVersionId") == (terms.get("PLATFORM_SERVICE_CHARGE") or {}).get("versionId"),
        "seller": (seller.get("legalName"), seller.get("registrationNumber")) == SELLER,
        "capabilitiesWithoutPaymentMethod": {"CARD", "APPLE_PAY"} <= capabilities,
    }
    save("S-1", {"at": now(), "request": request, "http": status, "code": (body or {}).get("code"), "switches": switches,
                 "snapshot": snap, "partnerTerms": partner, "seller": seller, "capabilities": sorted(capabilities),
                 "checks": checks, "verdict": verdict(checks)})


def step_real(name, snapshot):
    require_switches("REAL", snapshot)
    _, terms = payfac_terms()
    client_order_id, status, response = checkout(name, AC11_CLIENT)
    rows = None if DRY else order_rows(AC11_CLIENT, client_order_id)
    expected_effective = "1" if snapshot == "true" else "0"
    checks = {"http201": status == 201, "orderFrozen": bool(rows)}
    if rows:
        o, c = rows["order"], rows["context"] or {}
        bindings = rows["bindings"]
        checks.update(
            source=(o["entityNo"], o["sourceType"], o["localPartnerId"]) == ("AE001", "PARTNER", PAYFAC),
            context=(c.get("fundFlow"), c.get("effectiveVersion"), c.get("snapshotVersion")) == ("THROUGH_CUSTODIAN", expected_effective, "1"),
            partnerFields=c.get("shareVersionId") == (terms.get("PARTNER_SHARE") or {}).get("versionId")
            and c.get("chargeVersionId") == (terms.get("PLATFORM_SERVICE_CHARGE") or {}).get("versionId") and c.get("platformPartyId") == PLATFORM_PARTY,
            bindings=set(bindings) == set(MOR_ROLES) and all(bindings[r][:2] == v for r, v in MOR_ROLES.items()),
            legal=tuple(rows["legal"] or ()) == SELLER)
    data = {"at": now(), "clientOrderId": client_order_id, "http": status, "code": (response or {}).get("code"), "rows": rows}
    if name == "S-5" and not DRY:
        earlier = (load().get("S-2") or {}).get("rows") or {}
        if earlier.get("order"):
            again = sql("acquiring", "SELECT effective_relationship_model_version FROM t_order_context "
                                     f"WHERE order_id={earlier['order']['orderId']}")
            checks["s2OrderStill0"] = bool(again) and again[0][0] == "0"
            data["s2Order"] = {"orderId": earlier["order"]["orderId"], "effectiveVersion": again[0][0] if again else None}
        else:
            checks["s2OrderStill0"] = False
    data.update(checks=checks, verdict=verdict(checks))
    save(name, data)


def ops_session():
    module = h()
    secrets = module.secrets()
    SECRETS.update({secrets["E2E_ADMIN_PASSWORD"]})
    return module.Operations(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])


def step_failures():
    require_switches("REAL", "false")
    data = {"at": now()}
    # (a) 无 Affiliation / 资料未定稿的 Client：拒绝且不落单
    client_order_id, status, response = checkout("S-3a", UNAFFILIATED_CLIENT)
    rows = None if DRY else order_rows(UNAFFILIATED_CLIENT, client_order_id)
    data["unaffiliated"] = {"clientOrderId": client_order_id, "http": status, "code": (response or {}).get("code"), "orderRows": rows}
    checks = {"unaffiliatedRejected": status in (409, 422) and (response or {}).get("code") in ("CLIENT_CONFIGURATION_NOT_READY", "CHECKOUT_NOT_ELIGIBLE"),
              "unaffiliatedNoOrder": rows is None}
    # (b) 撤销 DEMO-CL-001 资格 → CHECKOUT_NOT_ELIGIBLE、不落单 → 按原有效期重登（finally 中执行）
    before = [q for q in qualification("CLIENT", AC11_CLIENT) if q["status"] == "APPROVED"]
    data["qualificationBefore"] = before
    if DRY:
        log("[dry-run] 运营入口：POST /qualification-admin/v1/qualifications/{id}/revocation → 建单 → POST .../manual-decisions（原 validFrom）")
        save("S-3", data | {"checks": checks, "verdict": "DRY-RUN"})
        return
    if len(before) != 1:
        raise SystemExit(f"DEMO-CL-001 应恰有一条 APPROVED 资格，实际 {len(before)}；停止")
    q = before[0]
    ops = ops_session()
    reason = {"reasonCode": "MORTEST_STAGE7_ACCEPTANCE"}
    revoked = ops.send("POST", f"/qualification-admin/v1/qualifications/{q['qualificationId']}/revocation",
                       {"expectedVersion": q["version"], **reason, "basisNote": "阶段 7 验收 S-3：临时撤销以验证 CHECKOUT_NOT_ELIGIBLE，随后按原有效期重登"})
    data["revoked"] = {k: (revoked or {}).get(k) for k in ("status", "effectiveStatus", "version")}
    try:
        client_order_id, status, response = checkout("S-3b", AC11_CLIENT)
        rows = order_rows(AC11_CLIENT, client_order_id)
        data["revokedClient"] = {"clientOrderId": client_order_id, "http": status, "code": (response or {}).get("code"), "orderRows": rows}
        checks.update(revokedRejected=status in (409, 422) and (response or {}).get("code") == "CHECKOUT_NOT_ELIGIBLE", revokedNoOrder=rows is None)
    finally:
        restored = ops.send("POST", "/qualification-admin/v1/qualifications/manual-decisions", {
            "roleType": "CLIENT", "roleId": AC11_CLIENT, "qualificationType": q["type"], "validFrom": q["validFrom"], "validTo": None,
            **reason, "basisNote": "阶段 7 验收 S-3：恢复验收前的资格（原有效期）", "expectedVersion": (revoked or {}).get("version")})
        data["restored"] = {k: (restored or {}).get(k) for k in ("status", "effectiveStatus", "version", "validFrom")}
        checks["restored"] = (restored or {}).get("status") == "APPROVED"
        data.update(checks=checks, verdict=verdict(checks))
        save("S-3", data)


def step_billing(since):
    if not since:
        raise SystemExit("S-4 需要 --since <UTC>（人工完成 APPLE_PAY 支付前的时刻）")
    facts = sql("billing", "SELECT id, fact_type, status, entity_no, IFNULL(local_partner_id,'NULL'), currency, gross_amount FROM t_billing_fact "
                           f"WHERE client_id={AC11_CLIENT} AND create_time >= '{since.replace('T', ' ').rstrip('Z')}'")
    baseline = (load().get("preflight") or {}).get("dlqMessages")
    current = dlq_messages()
    checks = {"factsPresent": bool(facts) or DRY,
              "factsPartner": all(f[3] == "AE001" and f[4] == PAYFAC for f in facts),
              "dlqUnchanged": baseline is not None and current == baseline or DRY}
    save("S-4", {"at": now(), "since": since, "facts": facts, "dlqBaseline": baseline, "dlqNow": current,
                 "checks": checks, "verdict": verdict(checks)})


def step_platform_removed():
    counts = {
        "acquiring.orders.PLATFORM": sql("acquiring", "SELECT COUNT(*) FROM t_commerce_order WHERE entity_source_type='PLATFORM'"),
        "risk.snapshots.PLATFORM": sql("risk-decision", "SELECT COUNT(*) FROM t_checkout_input_snapshot WHERE entity_source_type='PLATFORM'"),
        "risk.ENTITY_SOURCE_VALUES": sql("risk-decision", "SELECT GROUP_CONCAT(value_key ORDER BY value_key) FROM t_risk_variable_member "
                                                          "WHERE variable_code='ENTITY_SOURCE_VALUES'"),
        "client-master.sources.PLATFORM": sql("client-master", "SELECT COUNT(*) FROM t_entity_source_relation WHERE source_type='PLATFORM'"),
        "client-master.applications.PLATFORM": sql("client-master", "SELECT COUNT(*) FROM t_operating_entity_application WHERE source_type='PLATFORM'"),
    }
    values = {k: (v[0][0] if v else None) for k, v in counts.items()}
    checks = {k: (values[k] == "CLIENT,PARTNER" if k == "risk.ENTITY_SOURCE_VALUES" else values[k] == "0") for k in values} if not DRY else {}
    save("AC-13", {"at": now(), "values": values, "checks": checks, "verdict": verdict(checks) if checks else "DRY-RUN"})


def step_preflight():
    switches = require_switches(None, None)
    expected = candidate_images()
    images = {service: image(service) for service in expected}
    agreements, terms = payfac_terms()
    qualifications = {"AE001": qualification("OPERATING_ENTITY", "AE001"), "PayFac": qualification("LOCAL_PARTNER", PAYFAC),
                      "DEMO-CL-001": qualification("CLIENT", AC11_CLIENT)}
    master = sql("client-master", "SELECT (SELECT COUNT(*) FROM t_entity_source_relation WHERE entity_no='AE001' AND source_type='PARTNER' "
                                  f"AND source_partner_id={PAYFAC} AND status='ACTIVE'), (SELECT COUNT(*) FROM t_seller_disclosure "
                                  "WHERE entity_no='AE001' AND country_code='US' AND locale='en' AND status='ACTIVE'), "
                                  "(SELECT COUNT(*) FROM t_party_profile WHERE profile_id=237575207592763395 AND draft_application_id IS NULL "
                                  "AND content_hash IS NOT NULL)")
    dlq = dlq_messages()
    approved = lambda rows: any(r["status"] == "APPROVED" for r in rows)  # noqa: E731
    checks = {
        "images": DRY or all(images[s] == expected[s] for s in expected),
        "payfacAgreementActive": DRY or any(a[1] == "ACTIVE" for a in agreements),
        "payfacPrices": DRY or all((terms.get(t) or {}).get("profiles") == ["CARD_STANDARD", "APPLE_PAY_STANDARD"]
                                   for t in ("PARTNER_SHARE", "PLATFORM_SERVICE_CHARGE")),
        "qualifications": DRY or all(approved(rows) for rows in qualifications.values()),
        "masterData": DRY or (bool(master) and master[0] == ["1", "1", "1"]),
    }
    save("preflight", {"at": now(), "switches": switches, "images": images, "payfacAgreements": agreements, "payfacTerms": terms,
                       "qualifications": qualifications, "masterData": master, "dlqMessages": dlq, "checks": checks, "verdict": verdict(checks)})


STEPS = {"preflight": step_preflight, "S-0": lambda a: step_mock("S-0"), "S-1": lambda a: step_resolve(),
         "S-2": lambda a: step_real("S-2", "false"), "S-3": lambda a: step_failures(), "S-4": lambda a: step_billing(a.since),
         "S-5": lambda a: step_real("S-5", "true"), "S-6": lambda a: step_mock("S-6"), "AC-13": lambda a: step_platform_removed()}


def main():
    global DRY
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--step", required=True, choices=sorted(STEPS))
    parser.add_argument("--dry-run", action="store_true", help="只打印将发送的请求与将执行的 SELECT，不连接 mor-test")
    parser.add_argument("--since", help="S-4：支付开始前的 UTC 时刻，例如 2026-10-26T02:10:00Z")
    args = parser.parse_args()
    DRY = args.dry_run
    log(f"== {args.step}{' (dry-run)' if DRY else ''}")
    step = STEPS[args.step]
    step(args) if args.step != "preflight" else step()


if __name__ == "__main__":
    main()
