"""商业关系快照与真实解析接入 · 阶段 7 第 ⑨ 步：经运营入口建立 PayFac Partner 合同与两个方案（release.md 第 4 节 D-2 / D-4）。

入口与 ops-console 相同：http://localhost:28080 → Gateway → operations-console-bff → commercial（块 95 路由），复用多语言管理发布包的
i18n7_common（登录、请求、只读 SQL）。写法参照 2026-10-20 商务演示数据（DEMO-PT-001）：管理员为 maker，临时 checker 只用于批准，
结束时在 finally 中停用账号并清空角色权限。数据内容与兜底 SQL 30 一致（30 只在运营接口不可用且用户另行同意时执行）：
  合同  PAYFAC-AE001-PARTNER：PARTNER、localPartnerId 900000000001370001、覆盖 AE001、effectiveFrom = 执行时刻；
        LOCAL_PARTNER 与 OPERATING_ENTITY = 880000000702 / 881000000702（AC-10：两者必须同一 Party），
        PLATFORM_SIGNATORY = 880000000701 / 881000000701；一份 SIGNING_DOCUMENT（合成文件 ID + SHA-256，同现有演示合同）。
        提交签署 → 两个 Party 各登记一次 CLICK_ACCEPTANCE（同一 Party 的全部角色一次登记）→ SIGNED → 激活。
  方案  PAYFAC-AE001-PARTNER-SHARE（PARTNER_SHARE）、PAYFAC-AE001-SERVICE-CHARGE（PLATFORM_SERVICE_CHARGE）：
        版本 1（BLENDED / EXCLUSIVE，冻结上面的合同）→ 价格 AE001 / USD：CARD_STANDARD priority 1、APPLE_PAY_STANDARD priority 2，
        各一个 PERCENTAGE 组件 SALE / GROSS_AMOUNT / RETURN_PRORATA、费率 0，Partner 价格不带结算字段 → 提交（管理员 = maker）
        → 批准（临时 checker，不同于 maker）→ 登记 LOCAL_PARTNER 签署（版本不免签，排期前须 CLIENT_SIGNED；E_SIGNATURE + 合成价格附件）
        → 两个版本排期到同一时刻 T（当前 + 45 秒）→ 到点后手工激活（XXL 作业 commercialTermVersionActivation 每 5 分钟也会激活，
        两者竞争时以重读状态为准）。
  记录：合同生效时刻、条款生效时刻 T；验收订单的业务时间须晚于 T（mor-test-release-card-lessons）。

可重跑：按 localPartnerId + agreementNo / planCode 查到已有数据就从当前状态继续（合同 DRAFT / PENDING_SIGNATURE / SIGNED，版本 DRAFT
— SCHEDULED）；已 ACTIVE 的跳过。上次未清理的临时 checker（状态文件中记录的 ID）在开始前先清理。

缺省为干跑（--dry-run）：只用 i18n7_common.sql() 执行只读 SELECT（主档、画像、计费事件、权限与路由、已有数据与续跑起点），
打印将发送的请求与请求体（ID 与版本号为占位，口令不打印），不登录、不调用任何 mor-test 接口。--apply 才执行（阶段 7，主会话）。
运行记录与状态：I18N7_STATE（缺省 ~/Code-MoR-backups/relsnap-stage7，目录 0700、文件 0600），不含口令与令牌。
用法：python3 -I payfac_agreement.py [--dry-run | --apply]
"""
import argparse
import hashlib
import json
import os
import secrets as pysecrets
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.dont_write_bytecode = True
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/relsnap-stage7"))
os.environ.setdefault("MOR_TEST_ENVIRONMENT_DIR", str(MOR_ROOT / "test-environment"))
sys.path.insert(0, str(MOR_ROOT / "requirements/20261022/platform-support-多语言管理/release/acceptance"))
import i18n7_common as c  # noqa: E402  （登录、请求、只读 SQL；凭据只在内存）

BASE = "/commercial-admin/v1"
OE = "AE001"
CURRENCY = "USD"
PAYFAC = "900000000001370001"
AE001_PARTY = ("880000000702", "881000000702")       # 21 种子：AE001 与 PayFac Partner 的同一 Party
PLATFORM_PARTY = ("880000000701", "881000000701")    # 平台签约方占位（DEMO-PT-001 / DEMO2-PT-101 / ledger PLATFORM_HQ）
AGREEMENT_NO = "PAYFAC-AE001-PARTNER"
AGREEMENT_NAME = "PayFac Partner Agreement · AE001"
SIGNING_FILE = ("990000137001", "mor-test relsnap seed 2026-10-25: PAYFAC-AE001-PARTNER signing document")
PLANS = {  # termType: (planCode, componentCode, 价格附件合成文件)
    "PARTNER_SHARE": ("PAYFAC-AE001-PARTNER-SHARE", "PARTNER_SHARE",
                      ("990000137011", "mor-test relsnap seed 2026-10-25: PAYFAC-AE001-PARTNER-SHARE price annex")),
    "PLATFORM_SERVICE_CHARGE": ("PAYFAC-AE001-SERVICE-CHARGE", "PLATFORM_SERVICE_CHARGE",
                                ("990000137012", "mor-test relsnap seed 2026-10-25: PAYFAC-AE001-SERVICE-CHARGE price annex")),
}
PRICES = (("CARD_STANDARD", 1), ("APPLE_PAY_STANDARD", 2))  # D-4
TRIGGER = "SALE"
# 块 95：临时 checker 只拿读合同、读价格与批准三个权限
PERM_CONTRACT_READ, PERM_PRICING_READ, PERM_PRICING_APPROVE = "900000000000095101", "900000000000095103", "900000000000095105"
SCHEDULE_LEAD_SECONDS = 45
OPEN_VERSION = ("DRAFT", "PENDING_APPROVAL", "APPROVED", "CLIENT_SIGNED", "SCHEDULED")
ROUTES = (("POST", "/agreements"), ("GET", "/agreements"), ("GET", "/agreements/{agreementId}"),
          ("POST", "/agreements/{agreementId}/submit-for-signature"),
          ("POST", "/agreements/{agreementId}/parties/{partyId}/acceptance"), ("POST", "/agreements/{agreementId}/activate"),
          ("GET", "/commercial-terms/plans"), ("POST", "/commercial-terms/plans"),
          ("POST", "/commercial-terms/plans/{planId}/versions"), ("GET", "/commercial-terms/versions"),
          ("PUT", "/commercial-terms/versions/{versionId}/prices"), ("GET", "/commercial-terms/versions/{versionId}/prices"),
          ("POST", "/commercial-terms/versions/{versionId}/submit"), ("POST", "/commercial-terms/versions/{versionId}/approve"),
          ("POST", "/commercial-terms/versions/{versionId}/acceptance"),
          ("POST", "/commercial-terms/versions/{versionId}/schedule"),
          ("POST", "/commercial-terms/versions/{versionId}/activate"))
IAM_ROUTES = (("POST", "/iam/v1/roles"), ("GET", "/iam/v1/roles/{roleId}"), ("PUT", "/iam/v1/roles/{roleId}"),
              ("POST", "/iam/v1/accounts"), ("GET", "/iam/v1/accounts"), ("POST", "/iam/v1/accounts/{accountId}/disable"),
              ("POST", "/auth/v1/login"), ("POST", "/auth/v1/password"))
STATE_FILE = "payfac-agreement-state.json"
STAMP = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


class Stop(Exception):
    pass


def iso(moment):
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def utcnow():
    return datetime.now(timezone.utc).replace(microsecond=0)


def sha256_of(text):
    return hashlib.sha256(text.encode()).hexdigest()


# ---------- 请求体（干跑与执行共用） ----------

def agreement_body(effective_from):
    return {
        "agreementNo": AGREEMENT_NO, "agreementName": AGREEMENT_NAME, "agreementType": "PARTNER",
        "localPartnerId": PAYFAC, "operatingEntityNos": [OE], "effectiveFrom": effective_from,
        "parties": [{"partyId": AE001_PARTY[0], "partyProfileId": AE001_PARTY[1], "partyRole": "LOCAL_PARTNER"},
                    {"partyId": AE001_PARTY[0], "partyProfileId": AE001_PARTY[1], "partyRole": "OPERATING_ENTITY"},
                    {"partyId": PLATFORM_PARTY[0], "partyProfileId": PLATFORM_PARTY[1], "partyRole": "PLATFORM_SIGNATORY"}],
        "documents": [{"documentType": "SIGNING_DOCUMENT", "fileId": SIGNING_FILE[0], "contentHash": sha256_of(SIGNING_FILE[1])}]}


def agreement_acceptance_body(change_version, party_id, accepted_at):
    """CLICK_ACCEPTANCE：证据为合同唯一 SIGNING_DOCUMENT，必须带实际接受 Actor（同现有演示合同取 Party ID），不带执行文件。"""
    return {"expectedChangeVersion": change_version, "acceptanceReference": f"ACC-{AGREEMENT_NO}-{party_id}",
            "acceptanceMethod": "CLICK_ACCEPTANCE", "acceptedByActorId": party_id, "acceptedAt": accepted_at}


def plan_body(term_type, agreement_id):
    return {"planCode": PLANS[term_type][0], "termType": term_type, "agreementId": agreement_id, "localPartnerId": PAYFAC}


def version_body(plan_change_version, agreement_id):
    return {"expectedChangeVersion": str(plan_change_version), "agreementId": agreement_id, "pricingModel": "BLENDED",
            "feeTaxTreatment": "EXCLUSIVE"}


def prices_body(term_type, change_version):
    component = {"componentCode": PLANS[term_type][1], "componentType": "PERCENTAGE", "triggerCode": TRIGGER,
                 "calculationBase": "GROSS_AMOUNT", "refundBehavior": "RETURN_PRORATA", "rate": "0"}
    return {"expectedChangeVersion": str(change_version),
            "prices": [{"entityNo": OE, "currency": CURRENCY, "pricingProfileCode": profile, "priorityNo": priority,
                        "components": [dict(component)]} for profile, priority in PRICES]}


def version_acceptance_body(term_type, change_version, accepted_at):
    plan_code, _, annex = PLANS[term_type]
    return {"expectedChangeVersion": str(change_version), "partyId": AE001_PARTY[0], "acceptanceMethod": "E_SIGNATURE",
            "acceptedAt": accepted_at, "acceptanceReference": f"PA-{plan_code}-V1", "fileId": annex[0],
            "contentHash": sha256_of(annex[1])}


def command_body(change_version, **extra):
    return {"expectedChangeVersion": str(change_version), **extra}


# ---------- 只读核对（干跑与执行共用） ----------

def q(database, statement):
    return c.sql(database, statement)


def preflight():
    """主档、画像、计费事件、权限与路由；任何一项不满足即停止。返回已有数据快照（续跑起点）。"""
    master = q("mor_client_master", "SELECT (SELECT COUNT(*) FROM t_local_partner WHERE local_partner_id=" + PAYFAC
               + f" AND party_id={AE001_PARTY[0]} AND status='ACTIVE'), (SELECT COUNT(*) FROM t_operating_entity WHERE entity_no='{OE}'"
               + f" AND party_id={AE001_PARTY[0]} AND status='ACTIVE'), (SELECT COUNT(*) FROM t_party WHERE party_id IN "
               + f"({AE001_PARTY[0]},{PLATFORM_PARTY[0]}))")
    c.check("准备", "client-master：PayFac Partner 与 AE001 同一 Party、两个 Party 存在（21 / 22 已执行）", master == [["1", "1", "2"]], master)
    profiles = q("mor_commercial", "SELECT pricing_profile_code, payment_method_code, status FROM t_pricing_profile "
                 "WHERE pricing_profile_code IN ('CARD_STANDARD','APPLE_PAY_STANDARD') ORDER BY pricing_profile_code")
    c.check("准备", "commercial：CARD_STANDARD / APPLE_PAY_STANDARD 画像 ACTIVE",
            profiles == [["APPLE_PAY_STANDARD", "APPLE_PAY", "ACTIVE"], ["CARD_STANDARD", "CARD", "ACTIVE"]], profiles)
    events = q("mor_commercial", f"SELECT term_type FROM t_product_fee_event WHERE fee_event_code='{TRIGGER}' "
               "AND term_type IN ('PARTNER_SHARE','PLATFORM_SERVICE_CHARGE') ORDER BY term_type")
    c.check("准备", f"commercial：两种条款类型都登记了计费事件 {TRIGGER}", events == [["PARTNER_SHARE"], ["PLATFORM_SERVICE_CHARGE"]], events)
    perms = q("mor_platform", "SELECT COUNT(*) FROM t_permission WHERE status='ACTIVE' AND permission_id IN "
              f"({PERM_CONTRACT_READ},{PERM_PRICING_READ},{PERM_PRICING_APPROVE})")
    c.check("准备", "Platform：checker 所需三个权限 ACTIVE", perms == [["3"]], perms)
    wanted = [(m, "/api/operations/commercial-admin/v1" + p) for m, p in ROUTES] + [(m, "/api/operations" + p) for m, p in IAM_ROUTES]
    live = {(r[0], r[1]) for r in q("mor_platform", "SELECT http_method, external_path_template FROM t_gateway_route "
                                                    "WHERE channel='OPERATIONS' AND status='ACTIVE'")}
    missing = [f"{m} {p}" for m, p in wanted if (m, p) not in live]
    c.check("准备", f"Gateway：用到的 {len(wanted)} 条运营路由 ACTIVE", not missing, missing)
    return existing()


def existing():
    """已有数据（只读 SQL）：同 Partner 的合同、两个方案及其版本。"""
    agreements = q("mor_commercial", "SELECT agreement_id, agreement_no, status, DATE_FORMAT(effective_from,'%Y-%m-%dT%H:%i:%sZ') "
                   f"FROM t_agreement WHERE local_partner_id={PAYFAC}")
    plans = q("mor_commercial", "SELECT term_plan_id, plan_code, term_type, status, IFNULL(local_partner_id,'') FROM t_commercial_term_plan "
              f"WHERE local_partner_id={PAYFAC} OR plan_code IN ('{PLANS['PARTNER_SHARE'][0]}','{PLANS['PLATFORM_SERVICE_CHARGE'][0]}')")
    versions = q("mor_commercial", "SELECT v.term_plan_id, v.term_version_id, v.version_no, v.status, "
                 "DATE_FORMAT(v.effective_from,'%Y-%m-%dT%H:%i:%sZ'), GROUP_CONCAT(pr.pricing_profile_code ORDER BY pr.priority_no) "
                 "FROM t_commercial_term_version v JOIN t_commercial_term_plan p ON p.term_plan_id=v.term_plan_id "
                 "LEFT JOIN t_commercial_term_price pr ON pr.term_version_id=v.term_version_id "
                 f"WHERE p.local_partner_id={PAYFAC} GROUP BY v.term_plan_id, v.term_version_id, v.version_no, v.status, v.effective_from")
    others = [a for a in agreements if a[1] != AGREEMENT_NO and a[2] in ("DRAFT", "PENDING_SIGNATURE", "SIGNED", "ACTIVE")]
    c.check("准备", "同 Partner 没有其他未终止合同", not others, others)
    codes = {code for code, _, _ in PLANS.values()}
    foreign = [p for p in plans if p[1] in codes and (p[4] != PAYFAC or PLANS.get(p[2], ("",))[0] != p[1])]
    c.check("准备", "方案号未被其他 Owner / 条款类型占用", not foreign, foreign)
    extra = [p for p in plans if p[4] == PAYFAC and p[1] not in codes and p[2] in PLANS]
    c.check("准备", "同 Partner 没有其他 PARTNER_SHARE / PLATFORM_SERVICE_CHARGE 方案", not extra, extra)
    ours = [a for a in agreements if a[1] == AGREEMENT_NO]
    if ours and ours[0][2] in ("EXPIRED", "TERMINATED", "CANCELLED"):
        c.check("准备", f"合同 {AGREEMENT_NO} 不是终止态（合同号不可复用）", False, ours)
    return {"agreements": agreements, "plans": plans, "versions": versions}


def gate_prices():
    """第 ⑩ 步门禁同款：两个方案的 ACTIVE 版本各有 AE001 / USD 的 CARD_STANDARD、APPLE_PAY_STANDARD 价格，组件费率 0。"""
    rows = q("mor_commercial", "SELECT p.term_type, v.term_version_id, GROUP_CONCAT(CONCAT(pr.pricing_profile_code,':',pr.priority_no,':',"
             "c.trigger_code,':',CAST(c.rate AS DOUBLE)) ORDER BY pr.priority_no) FROM t_commercial_term_plan p JOIN t_commercial_term_version v "
             "ON v.term_plan_id=p.term_plan_id AND v.status='ACTIVE' JOIN t_commercial_term_price pr ON pr.term_version_id=v.term_version_id "
             f"AND pr.entity_no='{OE}' AND pr.currency='{CURRENCY}' JOIN t_commercial_term_component c ON c.term_price_id=pr.term_price_id "
             f"WHERE p.local_partner_id={PAYFAC} GROUP BY p.term_type, v.term_version_id ORDER BY p.term_type")
    expected = ",".join(f"{profile}:{priority}:{TRIGGER}:0" for profile, priority in PRICES)
    return rows, [r[0] for r in rows] == ["PARTNER_SHARE", "PLATFORM_SERVICE_CHARGE"] and all(r[2] == expected for r in rows)


# ---------- 干跑 ----------

def dry_run():
    snapshot = preflight()
    print("\n已有数据（只读）：", json.dumps(snapshot, ensure_ascii=False))
    print(f"续跑起点：{resume_hint(snapshot)}")
    t0 = iso(utcnow())
    t1 = iso(utcnow() + timedelta(seconds=SCHEDULE_LEAD_SECONDS))
    hidden = "<生成，不打印>"
    steps = [
        ("A1 管理员登录", "POST", "/auth/v1/login", {"loginIdentifier": "<access.md>", "password": "<access.md>"}),
        ("A2 查已有合同", "GET", f"{BASE}/agreements?localPartnerId={PAYFAC}&agreementType=PARTNER&page=1&size=50", None),
        ("A3 建合同（DRAFT）", "POST", f"{BASE}/agreements", agreement_body(t0)),
        ("A4 提交签署", "POST", f"{BASE}/agreements/<agreementId>/submit-for-signature", {"expectedChangeVersion": "<cv>"}),
        ("A5 AE001 Party 签署（LOCAL_PARTNER + OPERATING_ENTITY）", "POST",
         f"{BASE}/agreements/<agreementId>/parties/{AE001_PARTY[0]}/acceptance", agreement_acceptance_body("<cv>", AE001_PARTY[0], "<now>")),
        ("A6 平台签约方签署 → SIGNED", "POST", f"{BASE}/agreements/<agreementId>/parties/{PLATFORM_PARTY[0]}/acceptance",
         agreement_acceptance_body("<cv>", PLATFORM_PARTY[0], "<now>")),
        ("A7 激活合同", "POST", f"{BASE}/agreements/<agreementId>/activate", {"expectedChangeVersion": "<cv>"}),
        ("K1 IAM 建临时 checker 角色", "POST", "/iam/v1/roles", checker_role_body()),
        ("K2 IAM 建临时 checker", "POST", "/iam/v1/accounts", {**checker_account_body("<roleId>"), "initialPassword": hidden}),
        ("K3 checker 首登并改口令", "POST", "/auth/v1/password", {"currentPassword": hidden, "newPassword": hidden}),
    ]
    for term_type in PLANS:
        label = PLANS[term_type][0]
        steps += [
            (f"P1 {label} 查方案", "GET", f"{BASE}/commercial-terms/plans?localPartnerId={PAYFAC}&termType={term_type}&page=1&size=50", None),
            (f"P2 {label} 建方案", "POST", f"{BASE}/commercial-terms/plans", plan_body(term_type, "<agreementId>")),
            (f"P3 {label} 建版本 1", "POST", f"{BASE}/commercial-terms/plans/<planId>/versions", version_body("<planCv>", "<agreementId>")),
            (f"P4 {label} 替换价格", "PUT", f"{BASE}/commercial-terms/versions/<versionId>/prices", prices_body(term_type, "<cv>")),
            (f"P5 {label} 提交（管理员 = maker）", "POST", f"{BASE}/commercial-terms/versions/<versionId>/submit", command_body("<cv>")),
            (f"P6 {label} 批准（checker）", "POST", f"{BASE}/commercial-terms/versions/<versionId>/approve", command_body("<cv>")),
            (f"P7 {label} 登记 LOCAL_PARTNER 签署", "POST", f"{BASE}/commercial-terms/versions/<versionId>/acceptance",
             version_acceptance_body(term_type, "<cv>", "<now>")),
        ]
    for term_type in PLANS:
        steps.append((f"S1 {PLANS[term_type][0]} 排期", "POST", f"{BASE}/commercial-terms/versions/<versionId>/schedule",
                      command_body("<cv>", effectiveFrom=t1)))
    for term_type in PLANS:
        steps.append((f"S2 {PLANS[term_type][0]} 到点激活（等待至 {t1} 后）", "POST",
                      f"{BASE}/commercial-terms/versions/<versionId>/activate", command_body("<cv>")))
    steps += [
        ("V1 复核价格（每个版本）", "GET", f"{BASE}/commercial-terms/versions/<versionId>/prices?entityNo={OE}&termType=<termType>"
                                   f"&currency={CURRENCY}", None),
        ("K4 停用 checker（finally）", "POST", "/iam/v1/accounts/<accountId>/disable",
         {"expectedVersion": "<version>", "reason": CHECKER_REASON_DONE}),
        ("K5 清空 checker 角色权限（finally）", "PUT", "/iam/v1/roles/<roleId>",
         {"roleName": "<roleName>", "permissionIds": [], "expectedVersion": "<version>", "reason": CHECKER_REASON_DONE}),
    ]
    print("\n计划调用（经 http://localhost:28080/api/operations；已存在 / 已完成的步骤执行时跳过）：")
    for label, method, path, body in steps:
        print(f"  [{label}] {method} {path}")
        if body is not None:
            print("      " + json.dumps(body, ensure_ascii=False))
    rows, ok = gate_prices()
    print(f"\n第 ⑩ 步价目门禁当前：{'满足' if ok else '未满足'} {rows}")
    return c.summary("PayFac Partner 合同（干跑：只读 SELECT，未调用任何接口）")


def resume_hint(snapshot):
    ours = [a for a in snapshot["agreements"] if a[1] == AGREEMENT_NO]
    if not ours:
        return "从头开始（无 PayFac 合同）"
    statuses = {p[2]: [v[3] for v in snapshot["versions"] if v[0] == p[0]] for p in snapshot["plans"]}
    return f"合同 {ours[0][0]} {ours[0][2]}（effectiveFrom {ours[0][3]}），方案版本状态 {statuses}"


# ---------- 执行：请求 ----------

def call(token, label, method, path, body=None, ok=(200, 201)):
    status, payload, _, _ = c.raw(token, method, path, body)
    if status not in ok:
        detail = payload.get("detail") or payload.get("code") or payload
        print(f"[失败] {label}：{method} {path} → {status} {json.dumps(detail, ensure_ascii=False)[:400]}", flush=True)
        record["log"].append({"step": label, "request": f"{method} {path}", "status": status, "detail": detail})
        raise Stop(label)
    print(f"[完成] {label}", flush=True)
    record["log"].append({"step": label, "request": f"{method} {path}", "status": status})
    return payload


def get(token, path):
    status, payload, _, _ = c.raw(token, "GET", path)
    if status != 200:
        print(f"[失败] 查询 {path} → {status} {payload.get('detail') or payload}", flush=True)
        raise Stop(path)
    return payload


def save_state():
    record["state"] = state
    c.save(STATE_FILE, state)
    c.save(f"payfac-agreement-{STAMP}.json", record)


# ---------- 执行：合同 ----------

def ensure_agreement(admin):
    rows = get(admin, f"{BASE}/agreements?localPartnerId={PAYFAC}&agreementType=PARTNER&page=1&size=50")["items"]
    row = next((r for r in rows if r["agreementNo"] == AGREEMENT_NO), None)
    if row is None:
        effective_from = iso(utcnow())
        created = call(admin, "A3 建合同", "POST", f"{BASE}/agreements", agreement_body(effective_from))
        state.update(agreementId=created["agreementId"], agreementEffectiveFrom=effective_from)
        save_state()
    else:
        state.update(agreementId=row["agreementId"], agreementEffectiveFrom=row["effectiveFrom"])
    agreement_id = state["agreementId"]
    detail = get(admin, f"{BASE}/agreements/{agreement_id}")["agreement"]
    if detail["status"] == "DRAFT":
        call(admin, "A4 提交签署", "POST", f"{BASE}/agreements/{agreement_id}/submit-for-signature",
             {"expectedChangeVersion": detail["changeVersion"]})
        detail = get(admin, f"{BASE}/agreements/{agreement_id}")["agreement"]
    if detail["status"] == "PENDING_SIGNATURE":
        for party_id in (AE001_PARTY[0], PLATFORM_PARTY[0]):
            if any(p["partyId"] == party_id and p.get("acceptedAt") for p in detail["parties"]):
                continue
            accepted = call(admin, f"A5 Party {party_id} 签署", "POST", f"{BASE}/agreements/{agreement_id}/parties/{party_id}/acceptance",
                            agreement_acceptance_body(detail["changeVersion"], party_id, iso(utcnow())))
            detail["changeVersion"] = accepted["changeVersion"]
        detail = get(admin, f"{BASE}/agreements/{agreement_id}")["agreement"]
    if detail["status"] == "SIGNED":
        call(admin, "A7 激活合同", "POST", f"{BASE}/agreements/{agreement_id}/activate",
             {"expectedChangeVersion": detail["changeVersion"]})
        detail = get(admin, f"{BASE}/agreements/{agreement_id}")["agreement"]
    state["agreementActivatedTime"] = detail.get("activatedTime")
    save_state()
    if not c.check("D-2", f"合同 {AGREEMENT_NO} ACTIVE（{agreement_id}，effectiveFrom {state['agreementEffectiveFrom']}）",
                   detail["status"] == "ACTIVE", detail["status"]):
        raise Stop("agreement")
    roles = sorted((p["partyRole"], p["partyId"]) for p in detail["parties"])
    expected = sorted([("LOCAL_PARTNER", AE001_PARTY[0]), ("OPERATING_ENTITY", AE001_PARTY[0]),
                       ("PLATFORM_SIGNATORY", PLATFORM_PARTY[0])])
    c.check("AC-10", "签约方：LOCAL_PARTNER = OPERATING_ENTITY = AE001 Party，PLATFORM_SIGNATORY = 平台 Party", roles == expected, roles)
    return agreement_id


# ---------- 执行：临时 checker ----------

CHECKER_REASON = "商业关系快照阶段 7：PayFac Partner 条款版本复核人（完成后停用）"
CHECKER_REASON_DONE = "商业关系快照阶段 7：PayFac Partner 条款批准完成，停用复核人 / 清空复核角色权限"


def checker_role_body():
    return {"roleCode": f"RELSNAP_PAYFAC_CHECKER_{STAMP}", "roleName": f"PayFac 条款复核 {STAMP}",
            "permissionIds": [PERM_CONTRACT_READ, PERM_PRICING_READ, PERM_PRICING_APPROVE], "reason": CHECKER_REASON}


def checker_account_body(role_id):
    return {"email": f"relsnap-payfac-checker-{STAMP.lower()}@example.test", "initialPassword": None, "roleIds": [role_id],
            "reason": CHECKER_REASON}


def create_checker(admin):
    role = call(admin, "K1 IAM 建临时 checker 角色", "POST", "/iam/v1/roles", checker_role_body())
    state.setdefault("checkers", []).append({"roleId": role["roleId"], "accountId": None, "cleaned": False})
    save_state()
    body = checker_account_body(role["roleId"])
    initial = "Relsnap initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Relsnap checker " + pysecrets.token_urlsafe(18) + "!"
    body["initialPassword"] = initial
    created = call(admin, "K2 IAM 建临时 checker", "POST", "/iam/v1/accounts", body)
    state["checkers"][-1]["accountId"] = created["accountId"]
    save_state()
    first = c.login(body["email"], initial)
    status, _, _, _ = c.raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    if status not in (200, 204):
        print(f"[失败] K3 checker 改口令 → {status}", flush=True)
        raise Stop("checker password")
    print("[完成] K3 checker 首登并改口令", flush=True)
    return c.login(body["email"], password)


def find_account(admin, account_id):
    page = 1
    while True:
        result = get(admin, f"/iam/v1/accounts?page={page}&size=200")
        row = next((r for r in result["items"] if r["accountId"] == account_id), None)
        if row or page * 200 >= int(result["total"]):
            return row
        page += 1


def clean_checkers(admin):
    """停用临时账号、清空临时角色权限；失败只告警并保留记录，下次运行或手工处理。"""
    for checker in state.get("checkers", []):
        if checker.get("cleaned"):
            continue
        try:
            if checker.get("accountId"):
                row = find_account(admin, checker["accountId"])
                if row and row["status"] == "ACTIVE":
                    call(admin, f"K4 停用 checker {checker['accountId']}", "POST", f"/iam/v1/accounts/{checker['accountId']}/disable",
                         {"expectedVersion": row["version"], "reason": CHECKER_REASON_DONE}, ok=(200, 201, 204))
            role = get(admin, f"/iam/v1/roles/{checker['roleId']}")
            if role.get("permissionIds"):
                call(admin, f"K5 清空 checker 角色 {checker['roleId']}", "PUT", f"/iam/v1/roles/{checker['roleId']}",
                     {"roleName": role["roleName"], "permissionIds": [], "expectedVersion": role["version"], "reason": CHECKER_REASON_DONE}, ok=(200, 201, 204))
            checker["cleaned"] = True
        except Stop:
            print(f"[警告] 临时 checker 清理失败，需手工处理：{checker}", flush=True)
        save_state()


# ---------- 执行：方案与版本 ----------

def ensure_plan(admin, term_type, agreement_id):
    plan_code = PLANS[term_type][0]
    rows = get(admin, f"{BASE}/commercial-terms/plans?localPartnerId={PAYFAC}&termType={term_type}&page=1&size=50")["items"]
    row = next((r for r in rows if r["planCode"] == plan_code), None)
    if row is None:
        row = call(admin, f"P2 {plan_code} 建方案", "POST", f"{BASE}/commercial-terms/plans", plan_body(term_type, agreement_id))
    state.setdefault("plans", {})[term_type] = {"planId": row["planId"], "planCode": plan_code}
    save_state()
    return row


def versions_of(admin, plan_id):
    return get(admin, f"{BASE}/commercial-terms/versions?planId={plan_id}&page=1&size=50")["items"]


def current_version(admin, plan):
    """ACTIVE 版本已存在则返回它；否则返回最新的未完成版本；都没有时新建版本 1。"""
    rows = versions_of(admin, plan["planId"])
    active = [r for r in rows if r["status"] == "ACTIVE"]
    if active:
        return active[0]
    pending = sorted((r for r in rows if r["status"] in OPEN_VERSION), key=lambda r: int(r["versionNo"]))
    if pending:
        return pending[-1]
    plan_change = next(r for r in get(admin, f"{BASE}/commercial-terms/plans?localPartnerId={PAYFAC}&termType={plan['termType']}"
                                             "&page=1&size=50")["items"] if r["planId"] == plan["planId"])["changeVersion"]
    return call(admin, f"P3 {plan['planCode']} 建版本", "POST", f"{BASE}/commercial-terms/plans/{plan['planId']}/versions",
                version_body(plan_change, state["agreementId"]))


def drive_to_signed(admin, checker_box, term_type, plan):
    """DRAFT → PENDING_APPROVAL → APPROVED → CLIENT_SIGNED；返回版本行（含 changeVersion）。"""
    label = plan["planCode"]
    version = current_version(admin, plan)
    version_id = version["versionId"]
    state["plans"][term_type]["versionId"] = version_id
    save_state()
    cv = version["changeVersion"]
    status = version["status"]
    if status == "DRAFT":
        cv = call(admin, f"P4 {label} 替换价格", "PUT", f"{BASE}/commercial-terms/versions/{version_id}/prices",
                  prices_body(term_type, cv))["changeVersion"]
        cv = call(admin, f"P5 {label} 提交", "POST", f"{BASE}/commercial-terms/versions/{version_id}/submit", command_body(cv))["changeVersion"]
        status = "PENDING_APPROVAL"
    if status == "PENDING_APPROVAL":
        if checker_box.get("token") is None:
            checker_box["token"] = create_checker(admin)
        cv = call(checker_box["token"], f"P6 {label} 批准（checker）", "POST", f"{BASE}/commercial-terms/versions/{version_id}/approve",
                  command_body(cv))["changeVersion"]
        status = "APPROVED"
    if status == "APPROVED":
        cv = call(admin, f"P7 {label} 登记 LOCAL_PARTNER 签署", "POST", f"{BASE}/commercial-terms/versions/{version_id}/acceptance",
                  version_acceptance_body(term_type, cv, iso(utcnow())))["changeVersion"]
        status = "CLIENT_SIGNED"
    return {"versionId": version_id, "status": status, "changeVersion": cv, "effectiveFrom": version.get("effectiveFrom")}


def latest_row(admin, term_type):
    plan = state["plans"][term_type]
    return next(r for r in versions_of(admin, plan["planId"]) if r["versionId"] == plan["versionId"])


def schedule_and_activate(admin, versions):
    """两个版本排期到同一时刻 T（已排期的保留原时刻），到点后激活；与定时作业竞争时以重读状态为准。"""
    to_schedule = [t for t, v in versions.items() if v["status"] == "CLIENT_SIGNED"]
    if to_schedule:
        moment = iso(utcnow() + timedelta(seconds=SCHEDULE_LEAD_SECONDS))
        for term_type in to_schedule:
            v = versions[term_type]
            v["changeVersion"] = call(admin, f"S1 {PLANS[term_type][0]} 排期 {moment}", "POST",
                                      f"{BASE}/commercial-terms/versions/{v['versionId']}/schedule",
                                      command_body(v["changeVersion"], effectiveFrom=moment))["changeVersion"]
            v.update(status="SCHEDULED", effectiveFrom=moment)
    for term_type, v in versions.items():
        state["plans"][term_type]["effectiveFrom"] = v["effectiveFrom"]
    save_state()
    pending = [t for t, v in versions.items() if v["status"] == "SCHEDULED"]
    if pending:
        due = max(datetime.fromisoformat(versions[t]["effectiveFrom"].replace("Z", "+00:00")) for t in pending)
        wait = (due - datetime.now(timezone.utc)).total_seconds() + 3
        if wait > 0:
            print(f"[等待] 到点 {iso(due)}（约 {int(wait)} 秒）", flush=True)
            time.sleep(wait)
    for term_type in pending:
        row = latest_row(admin, term_type)
        if row["status"] == "SCHEDULED":
            try:
                call(admin, f"S2 {PLANS[term_type][0]} 激活", "POST", f"{BASE}/commercial-terms/versions/{row['versionId']}/activate",
                     command_body(row["changeVersion"]))
            except Stop:
                if latest_row(admin, term_type)["status"] != "ACTIVE":  # 409：定时作业已抢先激活时视为完成
                    raise
                print(f"[说明] {PLANS[term_type][0]} 已由定时作业激活", flush=True)


def verify_versions(admin):
    for term_type, plan in state["plans"].items():
        row = latest_row(admin, term_type)
        c.check("D-4", f"{plan['planCode']} 版本 {row['versionNo']} ACTIVE（{row['versionId']}，生效 {row['effectiveFrom']}，"
                f"maker {row.get('makerAccountId')} / checker {row.get('checkerAccountId')}）",
                row["status"] == "ACTIVE" and bool(row.get("checkerAccountId"))
                and row.get("makerAccountId") != row.get("checkerAccountId"), row)
        state["plans"][term_type]["effectiveFrom"] = row["effectiveFrom"]
        prices = get(admin, f"{BASE}/commercial-terms/versions/{row['versionId']}/prices?entityNo={OE}&termType={term_type}"
                            f"&currency={CURRENCY}")["prices"]
        shape = sorted((p["pricingProfileCode"], p["priorityNo"], tuple((x["triggerCode"], float(x["rate"])) for x in p["components"]))
                       for p in prices)
        expected = sorted((profile, priority, ((TRIGGER, 0.0),)) for profile, priority in PRICES)
        c.check("D-4", f"{plan['planCode']} AE001 / USD 价格：CARD_STANDARD #1、APPLE_PAY_STANDARD #2，费率 0", shape == expected, shape)
    rows, ok = gate_prices()
    c.check("⑩", "只读 SQL 门禁：两个方案的 ACTIVE 版本价目就位", ok, rows)
    state["termsEffectiveFrom"] = max(p["effectiveFrom"] for p in state["plans"].values())
    state["acceptanceOrdersAfter"] = max(state["termsEffectiveFrom"], state["agreementEffectiveFrom"])
    save_state()


# ---------- 执行 ----------

def apply():
    snapshot = preflight()
    if c.summary("PayFac Partner 合同 · 准备") != 0:
        return 1
    print(f"续跑起点：{resume_hint(snapshot)}", flush=True)
    admin = c.admin_token()
    checker_box = {}
    try:
        clean_checkers(admin)  # 上次遗留
        agreement_id = ensure_agreement(admin)
        versions = {}
        for term_type in PLANS:
            plan = dict(ensure_plan(admin, term_type, agreement_id), termType=term_type, planCode=PLANS[term_type][0])
            versions[term_type] = drive_to_signed(admin, checker_box, term_type, plan)
        schedule_and_activate(admin, versions)
        verify_versions(admin)
        print(f"\n合同生效 {state['agreementEffectiveFrom']}；条款生效 {state['termsEffectiveFrom']}"
              f" —— 之后的验收订单业务时间须晚于 {state['acceptanceOrdersAfter']}", flush=True)
    except Stop:
        c.check("D-2", "PayFac Partner 合同与方案经运营接口建立", False, record["log"][-1:] or None)
    finally:
        try:
            clean_checkers(admin)
        finally:
            record["finishedAt"] = c.now_iso()
            save_state()
            print(f"运行记录：{c.STATE / f'payfac-agreement-{STAMP}.json'}；状态：{c.STATE / STATE_FILE}", flush=True)
    return c.summary("PayFac Partner 合同")


def load_state():
    path = c.STATE / STATE_FILE
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {}


state = {}
record = {"startedAt": STAMP, "log": []}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="缺省：只读 SELECT 并打印计划调用，不调用任何 mor-test 接口")
    mode.add_argument("--apply", action="store_true", help="阶段 7：经运营入口建立合同与两个方案")
    args = parser.parse_args()
    if not args.apply:
        return dry_run()
    state.update(load_state())
    return apply()


if __name__ == "__main__":
    sys.exit(main())
