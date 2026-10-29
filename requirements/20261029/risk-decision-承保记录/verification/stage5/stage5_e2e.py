#!/usr/bin/env python3
"""REQ-03 承保记录 · 阶段 5 本机联调（API 级，经真实外部入口）。

链路：运营控制台 https://127.0.0.1:18444/api/operations/risk-underwriting/v1/...（Nginx → Gateway 块 143 路由 → operations-console-bff
      → client-master / compliance / Platform 参考数据 → risk-decision）。复用 common.py（e2e_http.py + onboarding_acceptance.py）。

账号：管理员（全部权限）与本脚本经 IAM API 新建的三个运营账号：uw（承保员 READ + MANAGE）、lead（负责人 READ + MANAGE + APPROVE）、
     ro（只读 READ）；凭据写 ~/Code-MoR-backups/rq03-stage5/accounts.json（0600），不打印。

演示 Client（prepare_cases.py 经运营 API 审完批准；E 的 CRR 置空为 10-no-crr-test-data.sql 测试数据）：
  A 240674874987655169 LOW 5999            自动承保 → 修订 → 人工批准（v2 生效、v1 已作废）
  B 241532599334350849 MEDIUM PRE_ORDER 14 人工：草稿 / 校验 / 并发版本 / 保证金 + T+N / 批准
  C 241533316505808897 HIGH MCC 5967 卡组织高风险  提交待复核 → 无 APPROVE 403 → 负责人拒绝；再提交（负责人本人）→ 本人复核 409，留作待复核演示
  D 241533481480368129 HIGH 5651           承保员提交 → 负责人批准
  E 240674482170114049 CRR 为空            按 MEDIUM 处理、自动承保不符合、拒绝；再建草稿留作草稿演示
  未触碰：241533217348268033（REQ-12 A，LOW INSTANT，符合自动承保）留给浏览器走查"新建 · 符合自动承保"
场景：S 账号、P 权限、A、B、C、D、E、L 列表与统计、Q data-query 模板只读试跑、U 输入来源不可用（短暂停本机 compliance 后按原 argv / 环境恢复）、
     G 日志（承保路由只记元数据、无 ERROR）。
用法：python3 -I stage5_e2e.py [--only S,P,A,B,C,D,E,L,Q,U,G]；结果写 stage5-e2e-result.json；退出码 0 表示无 FAIL。
"""
import argparse
import json
import secrets as ps
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as C  # noqa: E402

OA, E, UW = C.OA, C.E, C.UW
check, require, record, skip, problem = OA.check, OA.require, OA.record, OA.skip, OA.problem
HERE = Path(__file__).resolve().parent
TEMPLATE = HERE.parents[1] / "db/data-query-templates.sql"
LOGS = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/logs")
CLIENTS = {"A": "240674874987655169", "B": "241532599334350849", "C": "241533316505808897", "D": "241533481480368129",
           "E": "240674482170114049", "AUTO_DEMO": "241533217348268033"}
PERMISSIONS = {"READ": "900000000000143101", "MANAGE": "900000000000143102", "APPROVE": "900000000000143103"}
ROLES = {"uw": ("READ", "MANAGE"), "lead": ("READ", "MANAGE", "APPROVE"), "ro": ("READ",)}
CHECK_ORDER = ["RULE_CONFIGURED", "CRR_LOW", "VOLUME_PROVIDED", "CURRENCY_CONFIGURED", "VOLUME_WITHIN_THRESHOLD",
               "MCC_NOT_NETWORK_HIGH_RISK", "MCC_NOT_EXCLUDED", "CLASSIFICATION_CONFIRMED"]
IDS = {}


def decision(**overrides):
    body = {"observationDays": 30, "observationTransactions": 100, "requireDeposit": False, "depositAmount": None,
            "depositCurrency": None, "depositBlocksCollection": False, "suggestedSettlementDays": None, "highRiskMcc": False,
            "highRiskMccReason": None, "limitNote": None, "underwritingNote": "rq03 stage5", "handoverNote": None}
    body.update(overrides)
    return body


def code(body):
    return body.get("code") if isinstance(body, dict) else None


def fields(body):
    return sorted({e.get("field") for e in (body.get("errors") or [])}) if isinstance(body, dict) else []


def ws(session, client_id):
    status, body = session.get(f"{UW}/clients/{client_id}/workspace")
    require("WS", f"工作台 {client_id}", status == 200, f"HTTP {status} {problem(body) if status != 200 else body.get('inputStatus')}")
    return body


def template(client_id):
    """data-query 模板正文（分隔线之后）在 mor_risk_decision_v2e2e 以只读事务试跑，参数替换为整数。"""
    text = TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1]
    return C.root_sql(text.replace("#{clientId}", str(int(client_id))), "mor_risk_decision_v2e2e")[0]


# ---------------------------------------------------------------------------------------------------------------- S 账号

def s_accounts(ctx):
    admin = ctx["admin"]
    saved = C.accounts()
    stamp = time.strftime("%H%M%S")
    for label, grants in ROLES.items():
        if label not in saved:
            status, role = admin.call("POST", "/iam/v1/roles", {"roleCode": f"RQ03_{label.upper()}_{stamp}",
                                                                "roleName": f"REQ-03 阶段 5 {label} {stamp}",
                                                                "permissionIds": [PERMISSIONS[g] for g in grants],
                                                                "reason": "REQ-03 阶段 5 承保权限验证"})
            require("S", f"IAM 建角色 {label} {grants}", status == 200, f"HTTP {status} {problem(role)}")
            role_id = role.get("roleId") or role.get("objectId")
            email = f"rq03-{label}-{stamp}@example.test"
            p1, p2 = "Rq!" + ps.token_urlsafe(18), "Rq!" + ps.token_urlsafe(18)
            status, account = admin.call("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                                      "reason": "REQ-03 阶段 5 承保权限验证"})
            require("S", f"IAM 建账号 {label}", status == 200, f"HTTP {status} {problem(account)}")
            first = OA.ops_login(email, p1)
            status, _ = first.call("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
            require("S", f"{label} 首登改密", status in (200, 204), f"HTTP {status}")
            saved[label] = {"email": email, "password": p2, "accountId": account.get("accountId"), "roleId": role_id,
                            "permissions": list(grants)}
            C.save_accounts(saved)
        ctx[label] = OA.ops_login(saved[label]["email"], saved[label]["password"])
        ctx[label].account_id = ctx[label].account_id or saved[label]["accountId"]
        record("S", f"{label} 登录", "PASS", f"accountId={saved[label]['accountId']} permissions={saved[label]['permissions']}")


# ---------------------------------------------------------------------------------------------------------------- P 权限

def p_permissions(ctx):
    ro, uw, client = ctx["ro"], ctx["uw"], CLIENTS["AUTO_DEMO"]
    status, _ = ro.get(f"{UW}/underwritings?page=1&size=10")
    check("AC-11", "只读：列表 200", status == 200, f"HTTP {status}")
    status, _ = ro.get(f"{UW}/underwriting-statistics")
    check("AC-11", "只读：统计 200", status == 200, f"HTTP {status}")
    body = ws(ro, client)
    check("AC-11", "只读：工作台 canManage=false、canSubmit=false", body.get("canManage") is False and body.get("canSubmit") is False,
          f"canManage={body.get('canManage')} canSubmit={body.get('canSubmit')}")
    status, body = ro.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    check("AC-11", "只读：建草稿 403", status == 403, f"HTTP {status} {problem(body)}")
    status, body = ro.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
    check("AC-11", "只读：自动承保 403", status == 403, f"HTTP {status} {problem(body)}")
    status, body = ro.call("POST", f"{UW}/previews", {"clientId": client, "suggestedSettlementDays": None})
    check("AC-11", "只读：预览 403（MANAGE）", status == 403, f"HTTP {status} {problem(body)}")
    status, body = uw.call("POST", f"{UW}/underwritings/1/review", {"expectedVersion": 0, "decision": "APPROVE", "comment": None})
    check("AC-11", "承保员（无 APPROVE）：复核 403", status == 403, f"HTTP {status} {problem(body)}")
    body = ws(uw, client)
    check("AC-11", "承保员：工作台 canManage=true", body.get("canManage") is True, f"canManage={body.get('canManage')}")
    status, body = uw.get(f"{UW}/underwritings?page=1&size=10&foo=1")
    check("AC-11", "未声明查询参数被拒（Gateway 白名单 / BFF 严格参数）", status in (400, 422), f"HTTP {status} {problem(body)}")


# ---------------------------------------------------------------------------------------------------------------- A 自动承保 → 修订

def auto_then_revise(uw, client):
    status, auto = uw.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
    require("AC-02", "一键自动承保 201", status == 201, f"HTTP {status} {problem(auto)}")
    check("AC-02", "AUTO_RULE：APPROVED、规则 v1、创建人 = 承保员、无提交人 / 决定人、标准模板结论",
          auto.get("status") == "APPROVED" and auto.get("decisionSource") == "AUTO_RULE" and auto.get("ruleVersionNo") == 1
          and auto.get("createdBy") == str(uw.account_id) and auto.get("submittedBy") is None and auto.get("decidedBy") is None
          and auto.get("createdByLabel") and (auto.get("decision") or {}).get("observationDays") == 30
          and (auto.get("decision") or {}).get("requireDeposit") is False,
          {k: auto.get(k) for k in ("underwritingId", "status", "decisionSource", "ruleVersionNo", "createdBy", "createdByLabel",
                                    "submittedBy", "decidedBy", "versionNo")})
    check("AC-10", "模板：自动承保后 satisfied=1", template(client).get("satisfied") == "1", template(client))
    status, body = uw.call("POST", f"{UW}/underwritings/{auto['underwritingId']}/revision",
                           {"expectedVersion": auto["version"], "revisionReason": ""})
    check("AC-07", "修订原因为空 422", status == 422 and code(body) == "VALIDATION_ERROR", f"HTTP {status} {problem(body)}")
    status, draft = uw.call("POST", f"{UW}/underwritings/{auto['underwritingId']}/revision",
                            {"expectedVersion": auto["version"], "revisionReason": "rq03 stage5：补充 T+N 与保证金"})
    require("AC-07", "修订 201：新 DRAFT v2，复制结论，记修订原因（替代关系在批准时写入）", status == 201 and draft.get("status") == "DRAFT"
            and draft.get("versionNo") == 2 and draft.get("supersedesUnderwritingId") is None
            and draft.get("revisionReason") and (draft.get("decision") or {}).get("observationTransactions") == 100,
            f"HTTP {status} {problem(draft) if status != 201 else {k: draft.get(k) for k in ('underwritingId', 'versionNo', 'status', 'revisionReason')}}")
    return auto, draft


def a_auto_and_revision(ctx):
    uw, lead, client = ctx["uw"], ctx["lead"], CLIENTS["A"]
    current = ws(uw, client)
    preview = current.get("preview") or {}
    check("AC-02", "工作台预览：符合自动承保，8 项固定顺序全部通过，阈值 50000 USD",
          preview.get("autoEligible") is True and [c["code"] for c in preview.get("checks") or []] == CHECK_ORDER
          and all(c["passed"] for c in preview["checks"]) and preview.get("thresholdAmount") in ("50000", "50000.00")
          and preview.get("thresholdCurrency") == "USD" and preview.get("ruleVersionNo") == 1,
          {k: preview.get(k) for k in ("autoEligible", "ruleVersionNo", "thresholdAmount", "thresholdCurrency", "reviewLevel")})
    check("AC-02", "无履约方式时敞口不可算（FULFILLMENT_TYPE_MISSING）",
          preview.get("estimatedExposure") is None and preview.get("exposureUnavailableReason") == "FULFILLMENT_TYPE_MISSING",
          preview.get("exposureUnavailableReason"))
    effective = (current.get("current") or {}).get("effective")
    if effective and effective.get("decisionSource") == "MANUAL" and effective.get("versionNo") == 2:
        skip("AC-02", "自动承保与修订", f"已完成（v2 {effective['underwritingId']} 生效），重跑跳过")
        IDS["A_v2"] = effective["underwritingId"]
        return
    open_ = (current.get("current") or {}).get("open")
    if effective and effective.get("decisionSource") == "AUTO_RULE" and open_ and open_.get("versionNo") == 2:
        auto, draft = effective, open_
        record("AC-02", "续跑：v1 自动承保与 v2 修订草稿已存在", "INFO", f"v1={auto['underwritingId']} v2={draft['underwritingId']}")
    else:
        auto, draft = auto_then_revise(uw, client)
    IDS["A_v1"], IDS["A_v2"] = auto["underwritingId"], draft["underwritingId"]
    status, body = uw.call("POST", f"{UW}/underwritings/{auto['underwritingId']}/revision",
                           {"expectedVersion": auto["version"], "revisionReason": "again"})
    check("AC-07", "已有进行中记录时再修订 409 OPEN_EXISTS", status == 409 and code(body) == "RISK_UNDERWRITING_OPEN_EXISTS",
          f"HTTP {status} {problem(body)}")
    check("AC-07", "修订草稿期间旧版本仍生效（模板 satisfied=1）", template(client).get("satisfied") == "1", template(client))
    status, done = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                           {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                            "decision": decision(suggestedSettlementDays=7, limitNote="首月观察")})
    require("AC-07", "修订版提交批准（LOW → APPROVED，MANUAL，supersedes = v1）", status == 200 and done.get("status") == "APPROVED"
            and done.get("decisionSource") == "MANUAL" and done.get("decidedBy") == str(uw.account_id)
            and done.get("supersedesUnderwritingId") == auto["underwritingId"],
            f"HTTP {status} {problem(done) if status != 200 else done.get('status')}")
    status, old = lead.get(f"{UW}/underwritings/{auto['underwritingId']}")
    check("AC-07", "v1 被替代：SUPERSEDED + supersededAt", status == 200 and old.get("status") == "SUPERSEDED" and old.get("supersededAt"),
          f"HTTP {status} {old.get('status')} {old.get('supersededAt')}")
    status, history = lead.get(f"{UW}/clients/{client}/underwritings")
    items = history.get("items") or []
    check("AC-07", "版本历史 v2 APPROVED、v1 SUPERSEDED（版本倒序）",
          [(i.get("versionNo"), i.get("status")) for i in items] == [(2, "APPROVED"), (1, "SUPERSEDED")],
          [(i.get("versionNo"), i.get("status"), i.get("decisionSource")) for i in items])
    row = template(client)
    check("AC-10", "模板：修订批准后 satisfied=1、T+N 7", row.get("satisfied") == "1" and row.get("out_suggestedSettlementDays") == "7", row)


# ---------------------------------------------------------------------------------------------------------------- B MEDIUM 人工

def b_manual_medium(ctx):
    uw, lead, client = ctx["uw"], ctx["lead"], CLIENTS["B"]
    current = ws(uw, client)
    if (current.get("current") or {}).get("effective"):
        skip("AC-03", "MEDIUM 人工承保", "已有有效版本，重跑跳过")
        IDS["B"] = current["current"]["effective"]["underwritingId"]
        return
    check("AC-03", "工作台：MEDIUM、不符合自动承保（仅 CRR_LOW 不通过）、canSubmit",
          current["preview"].get("reviewLevel") == "MEDIUM" and current["preview"].get("autoEligible") is False
          and [c["code"] for c in current["preview"]["checks"] if not c["passed"]] == ["CRR_LOW"] and current.get("canSubmit") is True,
          {"reviewLevel": current["preview"].get("reviewLevel"), "canSubmit": current.get("canSubmit")})
    status, body = uw.call("POST", f"{UW}/previews", {"clientId": client, "suggestedSettlementDays": 7})
    exposure = body.get("estimatedExposure") or {}
    check("AC-05", "预览敞口：PRE_ORDER 14 + T+7 = 21 天，50000 × 21 / 30 = 35000.00 USD",
          status == 200 and exposure.get("amount") == "35000.00" and exposure.get("currency") == "USD" and exposure.get("riskDays") == 21
          and exposure.get("settlementDaysAssumed") is False, f"HTTP {status} {exposure}")
    status, body = uw.call("POST", f"{UW}/previews", {"clientId": client, "suggestedSettlementDays": None})
    exposure = body.get("estimatedExposure") or {}
    check("AC-05", "预览敞口：T+N 为空按 0 并标注 settlementDaysAssumed", status == 200 and exposure.get("riskDays") == 14
          and exposure.get("settlementDaysAssumed") is True and exposure.get("amount") == "23333.33", f"HTTP {status} {exposure}")
    status, body = uw.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
    check("AC-02", "MEDIUM 自动承保 409 AUTO_NOT_ELIGIBLE（原因由预览展示）", status == 409
          and code(body) == "RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE", f"HTTP {status} {problem(body)}")
    status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    require("AC-03", "建草稿 201 DRAFT v1（输入由 BFF 组装冻结）", status == 201 and draft.get("status") == "DRAFT"
            and draft.get("versionNo") == 1 and (draft.get("input") or {}).get("crr") == "MEDIUM"
            and (draft.get("input") or {}).get("fulfillmentDays") == 14 and (draft.get("input") or {}).get("sourceCaseId"),
            f"HTTP {status} {problem(draft) if status != 201 else draft.get('underwritingId')}")
    IDS["B"] = draft["underwritingId"]
    status, body = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    check("AC-03", "再建草稿 409 OPEN_EXISTS", status == 409 and code(body) == "RISK_UNDERWRITING_OPEN_EXISTS", f"HTTP {status} {problem(body)}")
    path = f"{UW}/underwritings/{draft['underwritingId']}"
    status, body = uw.call("PUT", path, {"expectedVersion": draft["version"] + 5, "decision": decision()})
    check("AC-03", "过期 expectedVersion 409 VERSION_CONFLICT", status == 409 and code(body) == "RISK_UNDERWRITING_VERSION_CONFLICT",
          f"HTTP {status} {problem(body)}")
    status, body = uw.call("PUT", path, {"expectedVersion": draft["version"], "decision": decision(depositAmount="100", depositCurrency="USD")})
    check("AC-04", "不要求保证金却给金额 422", status == 422 and "decision.depositAmount" in str(fields(body)), f"HTTP {status} {fields(body)}")
    status, body = uw.call("PUT", path, {"expectedVersion": draft["version"], "decision": decision(depositBlocksCollection=True)})
    check("AC-04", "不要求保证金却阻断收款 422", status == 422, f"HTTP {status} {fields(body)}")
    status, body = uw.call("PUT", path, {"expectedVersion": draft["version"], "decision": decision(observationDays=0, suggestedSettlementDays=91)})
    check("AC-04", "观察期 0 天、T+91 越界 422（字段级）", status == 422 and {"decision.observationDays", "decision.suggestedSettlementDays"}
          <= set(fields(body)), f"HTTP {status} {fields(body)}")
    deposit = decision(requireDeposit=True, depositAmount="5000", depositCurrency="USD", depositBlocksCollection=True,
                       suggestedSettlementDays=7, handoverNote="收到保证金后开通收款")
    status, saved = uw.call("PUT", path, {"expectedVersion": draft["version"], "decision": deposit})
    exposure = saved.get("estimatedExposure") or {}
    require("AC-04", "保存草稿 200：保证金 5000 USD 阻断收款、T+7，敞口随保存冻结 35000.00", status == 200
            and (saved.get("decision") or {}).get("depositBlocksCollection") is True and exposure.get("amount") == "35000.00",
            f"HTTP {status} {problem(saved) if status != 200 else exposure}")
    status, body = uw.call("POST", f"{path}/submission", {"expectedVersion": saved["version"], "decision": deposit,
                                                          "outcome": "DECLINE", "declineReason": None})
    check("AC-03", "拒绝不填原因 422", status == 422 and "declineReason" in str(fields(body)), f"HTTP {status} {fields(body)}")
    status, done = uw.call("POST", f"{path}/submission", {"expectedVersion": saved["version"], "decision": deposit,
                                                          "outcome": "APPROVE", "declineReason": None})
    require("AC-03", "MEDIUM 提交即批准（无需复核）：APPROVED，提交人 = 决定人 = 承保员", status == 200 and done.get("status") == "APPROVED"
            and done.get("submittedBy") == done.get("decidedBy") == str(uw.account_id) and done.get("decidedByLabel"),
            f"HTTP {status} {problem(done) if status != 200 else (done.get('status'), done.get('decidedByLabel'))}")
    status, body = uw.call("PUT", path, {"expectedVersion": done["version"], "decision": deposit})
    check("AC-03", "对 APPROVED 保存 409 STATE_INVALID", status == 409 and code(body) == "RISK_UNDERWRITING_STATE_INVALID", f"HTTP {status} {problem(body)}")
    status, body = lead.call("POST", f"{path}/review", {"expectedVersion": done["version"], "decision": "APPROVE", "comment": None})
    check("AC-03", "对 APPROVED 复核 409 STATE_INVALID", status == 409 and code(body) == "RISK_UNDERWRITING_STATE_INVALID", f"HTTP {status} {problem(body)}")
    row = template(client)
    check("AC-10", "模板：要求保证金且阻断收款、T+7", row.get("satisfied") == "1" and row.get("out_requireDeposit") == "1"
          and row.get("out_depositBlocksCollection") == "1" and row.get("out_suggestedSettlementDays") == "7", row)


# ---------------------------------------------------------------------------------------------------------------- C HIGH 拒绝 / 本人复核

def c_high_decline_and_self_review(ctx):
    uw, lead, client = ctx["uw"], ctx["lead"], CLIENTS["C"]
    current = ws(uw, client)
    inp = current.get("input") or {}
    check("AC-06", "工作台：HIGH、MCC 5967 卡组织高风险", current["preview"].get("reviewLevel") == "HIGH" and inp.get("mccCode") == "5967"
          and inp.get("networkHighRiskMcc") is True, {"reviewLevel": current["preview"].get("reviewLevel"), "mcc": inp.get("mccCode")})
    if (current.get("current") or {}).get("open", {}) and current["current"]["open"].get("status") == "SUBMITTED" \
            and current["current"]["open"].get("submittedBy") == str(lead.account_id):
        skip("AC-06", "HIGH 拒绝与本人复核", "已留作待复核演示，重跑跳过")
        IDS["C_pending"] = current["current"]["open"]["underwritingId"]
        return
    status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    require("AC-06", "HIGH 建草稿", status == 201, f"HTTP {status} {problem(draft)}")
    path = f"{UW}/underwritings/{draft['underwritingId']}"
    status, body = uw.call("POST", f"{path}/submission", {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                                          "decision": decision(highRiskMcc=False)})
    check("AC-06", "改判非高风险 MCC 不填原因 422 highRiskMccReason", status == 422 and "decision.highRiskMccReason" in fields(body),
          f"HTTP {status} {fields(body)}")
    status, body = uw.call("POST", f"{path}/submission", {"expectedVersion": draft["version"], "outcome": "DECLINE",
                                                          "declineReason": "x", "decision": decision(highRiskMcc=True)})
    check("AC-06", "HIGH 提交 DECLINE 409 DECLINE_REQUIRES_REVIEW", status == 409 and code(body) == "RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW",
          f"HTTP {status} {problem(body)}")
    status, submitted = uw.call("POST", f"{path}/submission", {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                                               "decision": decision(highRiskMcc=True, requireDeposit=True, depositAmount="20000",
                                                                                    depositCurrency="USD", suggestedSettlementDays=14)})
    require("AC-06", "HIGH 提交 → SUBMITTED，提交人 = 承保员", status == 200 and submitted.get("status") == "SUBMITTED"
            and submitted.get("submittedBy") == str(uw.account_id), f"HTTP {status} {problem(submitted) if status != 200 else submitted.get('status')}")
    row = template(client)
    check("AC-10", "模板：待复核 → UNDERWRITING_PENDING_REVIEW", row.get("satisfied") == "0" and row.get("missing") == "UNDERWRITING_PENDING_REVIEW", row)
    status, page = lead.get(f"{UW}/underwritings?pendingReviewForMe=true&page=1&size=100")
    check("AC-08", "负责人「待我复核」包含承保员提交的记录", status == 200 and any(i.get("underwritingId") == submitted["underwritingId"]
                                                                for i in page.get("items") or []), f"HTTP {status} total={page.get('total')}")
    status, body = lead.call("POST", f"{path}/review", {"expectedVersion": submitted["version"], "decision": "DECLINE", "comment": None})
    check("AC-06", "复核拒绝不填意见 422", status == 422 and "comment" in str(fields(body)), f"HTTP {status} {fields(body)}")
    status, declined = lead.call("POST", f"{path}/review", {"expectedVersion": submitted["version"], "decision": "DECLINE",
                                                            "comment": "卡组织高风险 MCC 且预售周期长，暂不承保"})
    require("AC-06", "负责人复核拒绝 → DECLINED，拒绝原因 = 复核意见", status == 200 and declined.get("status") == "DECLINED"
            and declined.get("declineReason") and declined.get("decidedBy") == str(lead.account_id),
            f"HTTP {status} {problem(declined) if status != 200 else declined.get('status')}")
    row = template(client)
    check("AC-10", "模板：拒绝 → UNDERWRITING_DECLINED", row.get("missing") == "UNDERWRITING_DECLINED", row)
    status, again = lead.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": decision(highRiskMcc=True, suggestedSettlementDays=14)})
    require("AC-06", "拒绝后可重新建草稿（v2）", status == 201 and again.get("versionNo") == 2, f"HTTP {status} {problem(again)}")
    status, pending = lead.call("POST", f"{UW}/underwritings/{again['underwritingId']}/submission",
                                {"expectedVersion": again["version"], "outcome": "APPROVE", "declineReason": None,
                                 "decision": decision(highRiskMcc=True, requireDeposit=True, depositAmount="20000", depositCurrency="USD",
                                                      depositBlocksCollection=True, suggestedSettlementDays=14,
                                                      underwritingNote="预售 14 天 + T+14，拒付暴露约 28 天流水")})
    require("AC-06", "负责人本人提交 → SUBMITTED", status == 200 and pending.get("status") == "SUBMITTED", f"HTTP {status} {problem(pending)}")
    IDS["C_pending"] = pending["underwritingId"]
    status, body = lead.call("POST", f"{UW}/underwritings/{pending['underwritingId']}/review",
                             {"expectedVersion": pending["version"], "decision": "APPROVE", "comment": None})
    check("AC-08", "四眼：提交人本人复核 409 SELF_REVIEW", status == 409 and code(body) == "RISK_UNDERWRITING_SELF_REVIEW", f"HTTP {status} {problem(body)}")
    status, page = lead.get(f"{UW}/underwritings?pendingReviewForMe=true&page=1&size=100")
    check("AC-08", "负责人「待我复核」不含本人提交的记录", status == 200 and all(i.get("underwritingId") != pending["underwritingId"]
                                                              for i in page.get("items") or []), f"HTTP {status}")


# ---------------------------------------------------------------------------------------------------------------- D HIGH 批准

def d_high_approve(ctx):
    uw, lead, client = ctx["uw"], ctx["lead"], CLIENTS["D"]
    current = ws(uw, client)
    if (current.get("current") or {}).get("effective"):
        skip("AC-08", "HIGH 复核批准", "已有有效版本，重跑跳过")
        IDS["D"] = current["current"]["effective"]["underwritingId"]
        return
    status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": decision(suggestedSettlementDays=3)})
    require("AC-08", "HIGH 建草稿（带初始结论）", status == 201 and (draft.get("decision") or {}).get("suggestedSettlementDays") == 3,
            f"HTTP {status} {problem(draft)}")
    status, submitted = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                                {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                 "decision": decision(suggestedSettlementDays=3, observationDays=60, observationTransactions=300)})
    require("AC-08", "承保员提交 → SUBMITTED", status == 200 and submitted.get("status") == "SUBMITTED", f"HTTP {status} {problem(submitted)}")
    status, body = lead.call("POST", f"{UW}/underwritings/{submitted['underwritingId']}/review",
                             {"expectedVersion": submitted["version"] + 1, "decision": "APPROVE", "comment": None})
    check("AC-08", "复核 expectedVersion 过期 409 VERSION_CONFLICT", status == 409 and code(body) == "RISK_UNDERWRITING_VERSION_CONFLICT",
          f"HTTP {status} {problem(body)}")
    status, approved = lead.call("POST", f"{UW}/underwritings/{submitted['underwritingId']}/review",
                                 {"expectedVersion": submitted["version"], "decision": "APPROVE", "comment": "同意，观察期 60 天"})
    require("AC-08", "负责人复核批准 → APPROVED，决定人 = 负责人，复核意见", status == 200 and approved.get("status") == "APPROVED"
            and approved.get("decidedBy") == str(lead.account_id) and approved.get("reviewComment") == "同意，观察期 60 天"
            and approved.get("submittedByLabel") and approved.get("decidedByLabel"),
            f"HTTP {status} {problem(approved) if status != 200 else (approved.get('submittedByLabel'), approved.get('decidedByLabel'))}")
    IDS["D"] = approved["underwritingId"]
    check("AC-10", "模板：HIGH 复核批准后 satisfied=1", template(client).get("satisfied") == "1", template(client))


# ---------------------------------------------------------------------------------------------------------------- E CRR 为空

def e_crr_missing(ctx):
    uw, client = ctx["uw"], CLIENTS["E"]
    current = ws(uw, client)
    check("AC-09", "工作台：CRR 为空 → reviewLevel MEDIUM、不符合自动承保", (current.get("input") or {}).get("crr") is None
          and current["preview"].get("reviewLevel") == "MEDIUM" and current["preview"].get("autoEligible") is False,
          {"crr": (current.get("input") or {}).get("crr"), "reviewLevel": current["preview"].get("reviewLevel")})
    if (current.get("current") or {}).get("open"):
        skip("AC-09", "CRR 为空的拒绝与草稿", "已留作草稿演示，重跑跳过")
        IDS["E_draft"] = current["current"]["open"]["underwritingId"]
        return
    status, body = uw.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
    check("AC-09", "CRR 为空自动承保 409 AUTO_NOT_ELIGIBLE", status == 409 and code(body) == "RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE",
          f"HTTP {status} {problem(body)}")
    status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    require("AC-09", "建草稿：input.crrMissing=true、reviewLevel MEDIUM", status == 201 and (draft.get("input") or {}).get("crrMissing") is True
            and draft.get("reviewLevel") == "MEDIUM", f"HTTP {status} {problem(draft) if status != 201 else draft.get('input', {}).get('crrMissing')}")
    status, declined = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                               {"expectedVersion": draft["version"], "outcome": "DECLINE", "declineReason": "尽调未评 CRR，退回补评后再承保",
                                "decision": decision()})
    require("AC-09", "非 HIGH 直接拒绝 → DECLINED", status == 200 and declined.get("status") == "DECLINED", f"HTTP {status} {problem(declined)}")
    status, draft2 = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": decision(suggestedSettlementDays=5)})
    require("AC-09", "再建草稿 v2（留作草稿演示）", status == 201 and draft2.get("versionNo") == 2, f"HTTP {status} {problem(draft2)}")
    IDS["E_draft"] = draft2["underwritingId"]
    row = template(client)
    check("AC-10", "模板：最新为 DRAFT → UNDERWRITING_NOT_STARTED", row.get("missing") == "UNDERWRITING_NOT_STARTED", row)


# ---------------------------------------------------------------------------------------------------------------- L 列表与统计

def l_list_and_statistics(ctx):
    lead = ctx["lead"]
    db = C.root_sql("SELECT status, COUNT(*) n FROM t_client_underwriting GROUP BY status", "mor_risk_decision_v2e2e")
    counts = {r["status"]: int(r["n"]) for r in db}
    effective = int(C.root_sql("SELECT COUNT(*) n FROM t_client_underwriting WHERE effective_slot=1", "mor_risk_decision_v2e2e")[0]["n"])
    status, stats = lead.get(f"{UW}/underwriting-statistics")
    check("AC-12", "统计与库一致（生效中 / 草稿 / 待复核）", status == 200 and stats.get("effective") == effective
          and stats.get("draft") == counts.get("DRAFT", 0) and stats.get("submitted") == counts.get("SUBMITTED", 0)
          and stats.get("approvedThisMonth") >= 1 and stats.get("declinedThisMonth") >= 1, {"api": stats, "db": counts, "effective": effective})
    status, page = lead.get(f"{UW}/underwritings?page=1&size=100")
    items = page.get("items") or []
    check("AC-12", "全部列表数 = 库总数，按更新时间倒序", status == 200 and page.get("total") == sum(counts.values())
          and [i.get("updatedAt") for i in items] == sorted([i.get("updatedAt") for i in items], reverse=True),
          f"HTTP {status} total={page.get('total')} db={sum(counts.values())}")
    for name, query, predicate in (
            ("status=SUPERSEDED", "status=SUPERSEDED", lambda i: i.get("status") == "SUPERSEDED"),
            ("crr=HIGH", "crr=HIGH", lambda i: i.get("crr") == "HIGH"),
            ("decisionSource=AUTO_RULE", "decisionSource=AUTO_RULE", lambda i: i.get("decisionSource") == "AUTO_RULE"),
            ("clientId=C", f"clientId={CLIENTS['C']}", lambda i: i.get("clientId") == CLIENTS["C"]),
            ("keyword=Layla", "keyword=Layla", lambda i: "Layla" in (i.get("clientDisplayName") or ""))):
        status, page = lead.get(f"{UW}/underwritings?{query}&page=1&size=100")
        found = page.get("items") or []
        check("AC-12", f"筛选 {name}", status == 200 and found and all(predicate(i) for i in found), f"HTTP {status} n={len(found)}")
    status, page = lead.get(f"{UW}/underwritings?keyword={CLIENTS['B']}&page=1&size=100")
    check("AC-12", "关键字按 clientId 精确匹配", status == 200 and {i.get("clientId") for i in page.get("items") or []} == {CLIENTS["B"]},
          f"HTTP {status} n={len(page.get('items') or [])}")
    sample = next((i for i in items if i.get("status") == "APPROVED" and i.get("decisionSource") == "MANUAL"), {})
    check("AC-12", "列表行含快照列与显示名（编号 · 辖区 · 类型、敞口、提交人 / 复核人显示名）",
          sample.get("clientNo") and sample.get("jurisdictionCode") and sample.get("clientType") and sample.get("decidedByLabel")
          and "estimatedExposure" in sample, {k: sample.get(k) for k in ("clientNo", "jurisdictionCode", "clientType", "decidedByLabel")})
    status, body = lead.get(f"{UW}/underwritings?status=BOGUS&page=1&size=10")
    check("AC-12", "非法状态筛选 400 INVALID_REQUEST（查询参数错误按系统约定为 400）", status == 400 and code(body) == "INVALID_REQUEST",
          f"HTTP {status} {problem(body)}")


# ---------------------------------------------------------------------------------------------------------------- Q 模板只读试跑

def q_template(ctx):
    expected = {"A": ("1", None), "B": ("1", None), "C": ("0", "UNDERWRITING_PENDING_REVIEW"), "D": ("1", None),
                "E": ("0", "UNDERWRITING_NOT_STARTED"), "AUTO_DEMO": ("0", "UNDERWRITING_NOT_STARTED")}
    for label, (satisfied, missing) in expected.items():
        row = template(CLIENTS[label])
        check("AC-10", f"模板只读试跑 {label}：satisfied={satisfied} missing={missing}",
              row.get("satisfied") == satisfied and (row.get("missing") == missing or (missing is None and row.get("missing") == "NULL")), row)
    rows = C.root_sql("SELECT COUNT(*) n FROM t_client_underwriting WHERE client_id=1", "mor_risk_decision_v2e2e")
    check("AC-10", "不存在的 Client：单行 NOT_STARTED", template("1").get("missing") == "UNDERWRITING_NOT_STARTED" and rows[0]["n"] == "0",
          template("1"))


# ---------------------------------------------------------------------------------------------------------------- U 输入来源不可用

def u_source_unavailable(ctx):
    uw, client = ctx["uw"], CLIENTS["E"]
    draft_id = IDS.get("E_draft")
    if not draft_id:
        skip("AC-05", "来源不可用", "缺少 E 的草稿")
        return
    status, draft = uw.get(f"{UW}/underwritings/{draft_id}")
    pid = (Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e") / "compliance.pid").read_text().strip()
    swap = [sys.executable, "-I", str(HERE / "swap.py")]
    subprocess.run(swap + ["capture", "compliance", pid], check=True)
    subprocess.run(["kill", pid], check=True)
    for _ in range(40):
        if subprocess.run(["kill", "-0", pid], capture_output=True).returncode != 0:
            break
        time.sleep(1)
    record("U", "停本机 compliance", "INFO", f"pid {pid}")
    try:
        status, body = uw.get(f"{UW}/clients/{client}/workspace")
        check("AC-05", "工作台：compliance 不可用 → 200 SOURCE_UNAVAILABLE、canSubmit=false", status == 200
              and body.get("inputStatus") == "SOURCE_UNAVAILABLE" and body.get("canSubmit") is False
              and (body.get("sources") or {}).get("dueDiligenceCase") == "UNAVAILABLE",
              f"HTTP {status} {body.get('inputStatus')} {body.get('sources')}")
        status, body = uw.call("PUT", f"{UW}/underwritings/{draft_id}", {"expectedVersion": draft["version"], "decision": decision()})
        check("AC-05", "保存草稿：来源不可用 503，不写库", status == 503, f"HTTP {status} {problem(body)}")
        status, after = uw.get(f"{UW}/underwritings/{draft_id}")
        check("AC-05", "草稿版本未变", after.get("version") == draft["version"], f"{draft['version']} → {after.get('version')}")
        status, body = uw.get(f"{UW}/underwritings/{draft_id}")
        check("AC-05", "详情不依赖 compliance（200）", status == 200, f"HTTP {status}")
    finally:
        out = subprocess.run(swap + ["restart", "compliance"], capture_output=True, text=True)
        record("U", "按原 argv / 环境恢复 compliance", "PASS" if out.returncode == 0 else "FAIL", out.stdout.strip()[-200:] + out.stderr.strip()[-200:])
    body = ws(uw, client)
    check("AC-05", "恢复后工作台 READY", body.get("inputStatus") == "READY", body.get("inputStatus"))


# ---------------------------------------------------------------------------------------------------------------- G 日志

def g_logs(ctx):
    names = ["Layla Bizinfo", "Acceptance Trading", "Bizinfo Trading"]
    for service in ("risk-decision", "ops-bff"):
        text = (LOGS / f"{service}.log").read_text(errors="replace")
        text = text[text.rfind("rq03-stage5 restart"):]
        check("AC-12", f"{service} 日志：承保请求只记元数据（不含 Client 名称）", not any(n in text for n in names),
              [n for n in names if n in text])
        lines = text.splitlines()
        errors = [line for line in lines if " ERROR " in line]
        related, unrelated = [], []
        for line in errors:
            request_id = line.split("requestId=", 1)[1].split(",", 1)[0] if "requestId=" in line else None
            uris = [l.split('uri="', 1)[1].split('"', 1)[0] for l in lines if request_id and request_id in l and 'uri="' in l]
            (unrelated if uris and not any("risk-underwriting" in u for u in uris) else related).append(
                {"line": line[:160], "uris": uris})
        check("AC-12", f"{service} 日志：替换后无承保相关 ERROR", not related, related[:3])
        if unrelated:
            record("AC-12", f"{service} 日志：与承保无关的 ERROR（另列）", "INFO", unrelated[:3])


# ---------------------------------------------------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only")
    args = parser.parse_args()
    started = datetime.now(timezone.utc)
    selected = (args.only or "S,P,A,B,C,D,E,L,Q,U,G").split(",")
    if "S" not in selected:
        selected.insert(0, "S")
    ctx = {"admin": C.admin()}
    plan = [("S", s_accounts), ("P", p_permissions), ("A", a_auto_and_revision), ("B", b_manual_medium),
            ("C", c_high_decline_and_self_review), ("D", d_high_approve), ("E", e_crr_missing), ("L", l_list_and_statistics),
            ("Q", q_template), ("U", u_source_unavailable), ("G", g_logs)]
    for name, scenario in plan:
        if name not in selected:
            continue
        print(f"\n==== {name} {scenario.__name__}", flush=True)
        try:
            scenario(ctx)
        except OA.Stop as stop:
            record(name, "场景中止", "FAIL", str(stop))
        except Exception as failure:  # noqa: BLE001 — 记录并继续其余场景
            record(name, "场景异常", "FAIL", f"{type(failure).__name__}: {failure}")
    fails = [r for r in OA.RESULTS if r.get("verdict") == "FAIL"]
    result = {"startedAt": started.isoformat(), "finishedAt": datetime.now(timezone.utc).isoformat(), "clients": CLIENTS, "ids": IDS,
              "accounts": {k: {"accountId": v["accountId"], "permissions": v["permissions"], "email": v["email"][:12] + "***@example.test"}
                           for k, v in C.accounts().items()},
              "summary": {v: sum(1 for r in OA.RESULTS if r.get("verdict") == v) for v in ("PASS", "FAIL", "SKIP", "INFO")},
              "results": OA.RESULTS}
    (HERE / "stage5-e2e-result.json").write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n")
    print("\nSUMMARY", result["summary"])
    return 1 if fails else 0


sys.exit(main())
