#!/usr/bin/env python3
"""REQ-04 限额档位 · 阶段 5 本机联调（API 级）。

链路：
  运营：https://127.0.0.1:18444/api/operations/risk-limit-tiers/v1/...（Nginx → Gateway 块 145 路由 → ops-bff → compliance / commercial /
        risk-decision）；承保步骤沿用 REQ-03 路由与 REQ-03 阶段 5 的 uw / lead 账号。
  评估：risk-decision 统一评估入口 POST /risk-decisions/v1/evaluations（私网，Payment / acquiring 调用的同一入口），用于额度矩阵；
        支付全链路：以 acquiring 身份直调 Payment 内部接口（建 Intent → 卡工具 → pay），Payment 真实调用 risk-decision。
  已激活发布见 stage5-risk-setup.json（档位分支流程，额度为本机 TEST 值）。
账号：管理员 + 本脚本经 IAM 新建 req（READ+REQUEST）、appr（READ+APPROVE）、elev（READ+APPROVE_ELEVATED）、ro（READ），均加
     RISK_DECISION_CONFIG_READ（风控目录）；凭据写 ~/Code-MoR-backups/rq04-stage5/accounts.json（0600），不打印。
Client：
  AUTO   241533217348268033 LOW，无承保 → 自动承保 → VERIFICATION；额度矩阵（USD / AED / SAR、重放）；VERIFICATION ↔ RESTRICTED（LOW 普通审批、本人审批拒绝）
  B      241532599334350849 MEDIUM，承保已批准（REQ-04 之前、无档位、无开通项）→ 修订批准：不初始化档位（发现 F-1）
  E      240674482170114049 无 CRR（按 MEDIUM）、资格 APPROVED、有 ENABLED 开通项，迁移为 STANDARD → 批准其承保草稿（档位不变）→
         RESTRICTED → VERIFICATION（近 30 日额度：10-e-yesterday.sql 种一行昨日汇总）→ 上线复核 → OBSERVATION（高级审批）→ 观察期到期 → 延长 → 驳回 → STANDARD（还原）
  D      241533481480368129 HIGH，承保已批准、无档位：无档位拒绝
  FIX    880000000901（Payment 内部夹具，迁移为 STANDARD）：STANDARD 额度、未配置币种放行；Payment 全链路 STANDARD 放行 / RESTRICTED 拒绝后还原
用法：python3 -I stage5_e2e.py [--only S,P,U,Q,M,N,G,F,T,L,X]；结果写 stage5-e2e-result.json；退出码 0 表示无 FAIL。
"""
import argparse
import json
import random
import secrets as ps
import subprocess
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as C  # noqa: E402

OA, E, UW, LT = C.OA, C.E, C.UW, C.LT
check, require, record, skip, problem = OA.check, OA.require, OA.record, OA.skip, OA.problem
HERE = Path(__file__).resolve().parent
TEMPLATE = HERE.parents[1] / "db/data-query-templates.sql"
LOGS = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/logs")
CLIENTS = {"AUTO": "241533217348268033", "B": "241532599334350849", "E": "240674482170114049", "D": "241533481480368129",
           "FIX": "880000000901"}
PERMISSIONS = {"READ": "900000000000145101", "REQUEST": "900000000000145102", "APPROVE": "900000000000145103",
               "APPROVE_ELEVATED": "900000000000145104", "CONFIG_READ": "900000000000000536"}
ROLES = {"req": ("READ", "REQUEST"), "appr": ("READ", "APPROVE"), "elev": ("READ", "APPROVE_ELEVATED"), "ro": ("READ",)}
CHECKLIST = {"channelRegistrationConfirmed": True, "integrationConfirmed": True, "productionVerified": True, "openIssuesCleared": True}
RQ03_ACCOUNTS = Path.home() / "Code-MoR-backups/rq03-stage5/accounts.json"
STARTED = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def code(body):
    return body.get("code") if isinstance(body, dict) else None


def rid():
    return str(random.randrange(10 ** 14, 9 * 10 ** 14))


# ---------------------------------------------------------------------------------------------------------------- 评估入口

def evaluate(body):
    status, text = E.private_call("risk-decision", "POST", "/risk-decisions/v1/evaluations",
                                  headers={"X-Request-Id": f"rq04-s5-{uuid.uuid4()}"}, body=body)
    reply = json.loads(text) if text.strip() else {}
    if status != 200:
        return {"http": status, "problem": problem(reply)}
    return {"http": status, "result": reply.get("result"), "evaluationId": reply.get("evaluationId"),
            "path": [r["node_key"] for r in C.root_sql(
                f"SELECT node_key FROM t_evaluation_flow_trace WHERE evaluation_id = {reply['evaluationId']} ORDER BY 1", C.RD)]}


def pay_body(client, amount, currency, key=None):
    return {"pointCode": "PAYMENT_ATTEMPT_RISK", "contractVersion": 1, "businessKey": key or rid(), "inputs": {
        "paymentIntentId": rid(), "riskActionId": rid(), "orderId": rid(), "clientId": client, "entityNo": "AE001",
        "paymentMethodCode": "CARD_VISA", "captureMode": "AUTOMATIC", "amount": amount, "currency": currency,
        "industryCode": "DIGITAL_GOODS", "mccCode": "5816", "consumerCountryCode": "AE", "consumerReference": "rq04-s5-payer",
        "clientIpAddress": "203.0.113.10", "userAgent": "Mozilla/5.0 rq04-stage5", "cardFingerprint": "fp_rq04_" + rid(),
        "cardBin": "411111", "issuerCountryCode": "AE", "cardBrand": "VISA", "fundingType": "CREDIT"}}


def pay(client, amount, currency):
    """本机负载高时偶发评估超时（> 决策点 500 ms 预算，见报告 F-4）：记 INFO 后以新业务键重试（至多 3 次）。"""
    outcome = evaluate(pay_body(client, amount, currency))
    for _ in range(3):
        if outcome.get("result") != "TIMED_OUT":
            break
        record("F-4", f"评估超时后重试 {client} {amount} {currency}", "INFO", outcome.get("evaluationId"))
        time.sleep(1)
        outcome = evaluate(pay_body(client, amount, currency))
    return outcome


def checkout(client, currency, net="100.00"):
    return evaluate({"pointCode": "CHECKOUT_PREPARE", "contractVersion": 5, "businessKey": rid(), "inputs": {
        "clientId": client, "entityNo": "AE001", "entitySourceType": "CLIENT", "consumerCountryCode": "AE", "currency": currency,
        "merchandiseAmount": net, "discountAmount": "0", "netAmount": net, "industryCode": "DIGITAL_GOODS", "mccCode": "5816",
        "lineCount": "1"}})


def expect(ac, step, outcome, result, node=None):
    ok = outcome.get("result") == result and (node is None or node in (outcome.get("path") or []))
    return check(ac, step, ok, {k: outcome.get(k) for k in ("http", "result", "path", "problem")})


def daily(client):
    rows = C.root_sql("SELECT event_code, currency, event_date, amount, event_count FROM t_client_allowed_daily_total "
                      f"WHERE client_id = {client} ORDER BY event_code, currency, event_date", C.RD)
    return {(r["event_code"], r["currency"], r["event_date"]): (f"{Decimal(r['amount']):.2f}", r["event_count"]) for r in rows}


def today():
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


# ---------------------------------------------------------------------------------------------------------------- 档位接口

def ws(session, client):
    status, body = session.get(f"{LT}/clients/{client}/workspace")
    require("WS", f"档位工作台 {client}", status == 200, f"HTTP {status} {problem(body) if status != 200 else ''}")
    return body


def tier_of(session, client):
    return ws(session, client).get("tier") or {}


def request_change(session, client, to, reason, checklist=None, note=None, days=None, txns=None, version=None):
    tier = tier_of(session, client)
    body = {"expectedTierVersion": tier.get("version") if version is None else version, "toTier": to, "reason": reason,
            "goLiveNote": note, "observationTargetDays": days, "observationTargetTransactions": txns, "checklist": checklist}
    return session.call("POST", f"{LT}/clients/{client}/tier-changes", body)


def decide(session, change, action, comment="rq04 stage5"):
    body = {"expectedVersion": change["version"]} if action == "withdrawal" else {"expectedVersion": change["version"],
                                                                                   "comment": comment}
    return session.call("POST", f"{LT}/tier-changes/{change['changeId']}/{action}", body)


def lists(client):
    """当前生效的档位名单成员（任何时刻至多一张）。"""
    return [r["list_code"] for r in C.root_sql(
        "SELECT l.list_code FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id WHERE l.list_purpose = 'TIER' "
        f"AND e.status = 'ACTIVE' AND e.subject_type = 'CLIENT' AND e.subject_key = '{client}'", C.RD)]


def consistent(ac, client, tier):
    found = lists(client)
    return check(ac, f"{client} 档位 {tier} 与名单一致（只在 TIER_{tier}）", found == [f"TIER_{tier}"], found)


def template(client, target):
    text = TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1]
    sql = text.replace("#{clientId}", str(int(client))).replace("#{targetTier}", f"'{target}'")
    return C.root_sql(sql, C.RD)[0]


# ---------------------------------------------------------------------------------------------------------------- S 账号

def s_accounts(ctx):
    admin = ctx["admin"]
    saved = C.accounts()
    stamp = time.strftime("%H%M%S")
    for label, grants in ROLES.items():
        if label not in saved:
            ids = [PERMISSIONS[g] for g in grants] + [PERMISSIONS["CONFIG_READ"]]
            status, role = admin.call("POST", "/iam/v1/roles", {"roleCode": f"RQ04_{label.upper()}_{stamp}",
                                                                "roleName": f"REQ-04 阶段 5 {label} {stamp}", "permissionIds": ids,
                                                                "reason": "REQ-04 阶段 5 限额档位权限验证"})
            require("S", f"IAM 建角色 {label} {grants}", status == 200, f"HTTP {status} {problem(role)}")
            role_id = role.get("roleId") or role.get("objectId")
            email = f"rq04-{label}-{stamp}@example.test"
            p1, p2 = "Rq!" + ps.token_urlsafe(18), "Rq!" + ps.token_urlsafe(18)
            status, account = admin.call("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                                      "reason": "REQ-04 阶段 5 限额档位权限验证"})
            require("S", f"IAM 建账号 {label}", status == 200, f"HTTP {status} {problem(account)}")
            first = OA.ops_login(email, p1)
            status, _ = first.call("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
            require("S", f"{label} 首登改密", status in (200, 204), f"HTTP {status}")
            saved[label] = {"email": email, "password": p2, "accountId": account.get("accountId"), "roleId": role_id,
                            "permissions": list(grants) + ["RISK_DECISION_CONFIG_READ"]}
            C.save_accounts(saved)
        ctx[label] = OA.ops_login(saved[label]["email"], saved[label]["password"])
        ctx[label].account_id = ctx[label].account_id or saved[label]["accountId"]
        record("S", f"{label} 登录", "PASS", f"accountId={saved[label]['accountId']} permissions={saved[label]['permissions']}")
    rq03 = json.loads(RQ03_ACCOUNTS.read_text())
    for label in ("uw", "lead"):
        ctx[label] = OA.ops_login(rq03[label]["email"], rq03[label]["password"])
        ctx[label].account_id = ctx[label].account_id or rq03[label]["accountId"]
        record("S", f"REQ-03 {label} 登录（承保步骤）", "PASS", f"accountId={rq03[label]['accountId']}")


# ---------------------------------------------------------------------------------------------------------------- P 权限

def p_permissions(ctx):
    ro, req, appr, admin = ctx["ro"], ctx["req"], ctx["appr"], ctx["admin"]
    for path in ("/client-tiers?page=1&size=10", "/tier-statistics", "/tier-quotas"):
        status, _ = ro.get(LT + path)
        check("AC-10", f"只读：GET {path.split('?')[0]} 200", status == 200, f"HTTP {status}")
    body = ws(ro, CLIENTS["FIX"])
    check("AC-10", "只读：工作台 canRequest=false、requestableTargets 空", body.get("canRequest") is False
          and not body.get("requestableTargets"), {k: body.get(k) for k in ("canRequest", "requestableTargets")})
    body = ws(req, CLIENTS["FIX"])
    check("AC-10", "申请人：STANDARD 工作台 canRequest=true，可申请 RESTRICTED", body.get("canRequest") is True
          and body.get("requestableTargets") == ["RESTRICTED"], {k: body.get(k) for k in ("canRequest", "requestableTargets")})
    status, body = request_change(ro, CLIENTS["FIX"], "RESTRICTED", "ro 不应能申请")
    check("AC-02", "只读：申请 403", status == 403, f"HTTP {status} {problem(body)}")
    fake = {"changeId": "1", "version": 0}
    status, body = decide(req, fake, "approval")
    check("AC-02", "申请人（无 APPROVE）：审批 403", status == 403, f"HTTP {status} {problem(body)}")
    status, body = decide(appr, fake, "elevated-approval")
    check("AC-02", "普通审批人（无 APPROVE_ELEVATED）：高级审批 403", status == 403, f"HTTP {status} {problem(body)}")
    status, body = req.get(f"{LT}/client-tiers?page=1&size=10&foo=1")
    check("AC-10", "未声明查询参数被拒", status in (400, 422), f"HTTP {status} {problem(body)}")
    status, body = admin.call("POST", "/risk-lists/v1/lists/TIER_STANDARD/entries", {"subjectValue": CLIENTS["D"], "effectiveFrom": None, "effectiveTo": None, "reason": "manual"})
    check("AC-08", "名单页：TIER 名单手工加成员被拒（业务校验）", status in (400, 409, 422) and code(body) not in ("INVALID_REQUEST", "VALIDATION_ERROR"),
          f"HTTP {status} {problem(body)}")
    entry = C.root_sql("SELECT e.entry_id, e.version FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id "
                       f"WHERE l.list_code = 'TIER_STANDARD' AND e.status = 'ACTIVE' AND e.subject_key = '{CLIENTS['FIX']}'", C.RD)[0]
    status, body = admin.call("POST", f"/risk-lists/v1/lists/TIER_STANDARD/entries/{entry['entry_id']}/deactivate",
                              {"reason": "manual"})
    check("AC-08", "名单页：TIER 名单手工停用成员被拒（业务校验）", status in (400, 409, 422)
          and code(body) not in ("INVALID_REQUEST", "VALIDATION_ERROR", "GATEWAY_ROUTE_NOT_FOUND"), f"HTTP {status} {problem(body)}")
    check("AC-08", "停用被拒后成员仍生效", lists(CLIENTS["FIX"]) == ["TIER_STANDARD"], lists(CLIENTS["FIX"]))
    status, body = admin.call("POST", f"{LT}/internal/tier-migrations", {"executedBy": "1", "reason": "x", "clients": []})
    check("AC-07", "迁移接口不经 Gateway 暴露（外部入口 404）", status == 404, f"HTTP {status} {problem(body)}")


# ---------------------------------------------------------------------------------------------------------------- U 承保初始化

def u_underwriting(ctx):
    uw, lead, ro = ctx["uw"], ctx["lead"], ctx["ro"]
    auto = CLIENTS["AUTO"]
    if not tier_of(ro, auto):
        check("AC-01", "AUTO 承保前无档位：工作台 tier=null、名单无成员", lists(auto) == [], lists(auto))
        status, done = uw.call("POST", f"{UW}/clients/{auto}/auto-underwritings", None, json_empty=True)
        require("AC-01", "AUTO 一键自动承保 201 APPROVED", status == 201 and done.get("status") == "APPROVED",
                f"HTTP {status} {problem(done) if status != 201 else done.get('underwritingId')}")
        ctx["AUTO_v1"] = done
    tier = tier_of(ro, auto)
    status, changes = ro.get(f"{LT}/clients/{auto}/tier-changes")
    items = changes.get("items") or []
    inits = [i for i in items if i.get("changeType") == "UNDERWRITING_INIT"]
    check("AC-01", "AUTO 自动承保批准即建档：最早一条为 UNDERWRITING_INIT → VERIFICATION、APPROVED、记来源承保（重跑时其后另有人工变更）",
          len(inits) == 1 and items[-1] is inits[0] and inits[0].get("toTier") == "VERIFICATION" and inits[0].get("status") == "APPROVED"
          and inits[0].get("underwritingId") and (len(items) > 1 or tier.get("sourceType") == "UNDERWRITING_INIT"),
          [(i.get("changeType"), i.get("status"), i.get("toTier"), i.get("underwritingId")) for i in items])
    consistent("AC-03", auto, tier.get("tier"))
    effective = (ws(ro, auto).get("underwriting") or {})
    if effective.get("versionNo") == 1:
        status, current = uw.get(f"{UW}/underwritings/{effective['underwritingId']}")
        status, draft = uw.call("POST", f"{UW}/underwritings/{effective['underwritingId']}/revision",
                                {"expectedVersion": current["version"], "revisionReason": "rq04 stage5：修订批准不改档位"})
        require("AC-01", "AUTO 修订草稿 201", status == 201, f"HTTP {status} {problem(draft)}")
        status, done = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                               {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                "decision": draft.get("decision")})
        require("AC-01", "AUTO 修订版提交批准（LOW 直接 APPROVED）", status == 200 and done.get("status") == "APPROVED",
                f"HTTP {status} {problem(done) if status != 200 else done.get('status')}")
    after = tier_of(ro, auto)
    status, changes = ro.get(f"{LT}/clients/{auto}/tier-changes")
    check("AC-01", "AUTO 修订批准后档位不变（档位版本不变，未新增 UNDERWRITING_INIT 记录）", after.get("tier") == tier.get("tier")
          and after.get("version") == tier.get("version") and len(changes.get("items") or []) == len(items)
          and sum(i.get("changeType") == "UNDERWRITING_INIT" for i in changes.get("items") or []) == 1,
          {"version": [tier.get("version"), after.get("version")], "changes": [len(items), len(changes.get("items") or [])]})
    # B：REQ-04 之前已批准、无档位 → 修订批准是首个 REQ-04 下的批准 → VERIFICATION
    b = CLIENTS["B"]
    if not tier_of(ro, b) and ((ws(ro, b).get("underwriting") or {}).get("versionNo") or 1) < 2:
        effective = ws(ro, b).get("underwriting") or {}
        status, current = uw.get(f"{UW}/underwritings/{effective['underwritingId']}")
        status, draft = uw.call("POST", f"{UW}/underwritings/{effective['underwritingId']}/revision",
                                {"expectedVersion": current["version"], "revisionReason": "rq04 stage5：MEDIUM 修订"})
        require("AC-01", "B 修订草稿 201", status == 201, f"HTTP {status} {problem(draft)}")
        status, submitted = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                                    {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                     "decision": draft.get("decision")})
        require("AC-01", "B 提交批准（MEDIUM 由承保员直接批准；HIGH 才需复核）", status == 200
                and submitted.get("status") in ("APPROVED", "SUBMITTED"), f"HTTP {status} {problem(submitted) if status != 200 else submitted.get('status')}")
        if submitted.get("status") == "SUBMITTED":
            status, done = lead.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/review",
                                     {"expectedVersion": submitted["version"], "decision": "APPROVE", "comment": "rq04 stage5"})
            require("AC-01", "B 负责人复核批准", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    tier = tier_of(ro, b)
    record("F-1", "B（REQ-04 之前已批准、无开通项、不在迁移名单）修订批准后仍无档位：修订批准不初始化（B2 字面），"
           "此类 Client 没有获得档位的路径", "FIND", {"tier": tier.get("tier"), "lists": lists(b)})
    # E：已有档位（迁移 STANDARD）时批准承保不改档位
    e = CLIENTS["E"]
    before = tier_of(ro, e)
    effective = ws(ro, e).get("underwriting") or {}
    if not effective.get("underwritingId"):
        status, draft = uw.get(f"{UW}/underwritings/241601375522275328")
        status, submitted = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                                    {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                     "decision": draft.get("decision")})
        require("AC-01", "E 草稿提交批准（无 CRR 按 MEDIUM）", status == 200 and submitted.get("status") in ("APPROVED", "SUBMITTED"),
                f"HTTP {status} {problem(submitted) if status != 200 else submitted.get('status')}")
        if submitted.get("status") == "SUBMITTED":
            status, done = lead.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/review",
                                     {"expectedVersion": submitted["version"], "decision": "APPROVE", "comment": "rq04 stage5"})
            require("AC-01", "E 复核批准", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    after = tier_of(ro, e)
    check("AC-01", "E 已有档位：承保批准不改档位（仍 STANDARD / MIGRATION）", after.get("tier") == "STANDARD"
          and after.get("sourceType") == before.get("sourceType") and after.get("version") == before.get("version"),
          {"before": [before.get("tier"), before.get("sourceType"), before.get("version")],
           "after": [after.get("tier"), after.get("sourceType"), after.get("version")]})
    status, body = ro.get(f"{LT}/clients/{CLIENTS['D']}/workspace")
    check("AC-01", "D（REQ-04 之前已批准、无新批准）无档位：工作台 tier=null", status == 200 and body.get("tier") is None,
          f"HTTP {status} tier={body.get('tier')}")


# ---------------------------------------------------------------------------------------------------------------- Q VERIFICATION 额度矩阵

def q_quotas(ctx):
    client = CLIENTS["AUTO"]
    if tier_of(ctx["ro"], client).get("tier") != "VERIFICATION":
        skip("AC-06", "VERIFICATION 额度矩阵", "AUTO 不是 VERIFICATION")
        return
    before = daily(client)
    if before:
        skip("AC-06", "VERIFICATION 额度矩阵", f"AUTO 已有放行汇总 {before}，重跑跳过")
        return
    expect("AC-06", "AUTO USD 60 > 单笔 50 拒绝（额度规则集）", pay(client, "60.00", "USD"), "DENY", "deny_limit")
    for n in range(5):
        expect("AC-06", f"AUTO USD 50 第 {n + 1} 笔放行", pay(client, "50.00", "USD"), "ALLOW", "allow")
    expect("AC-06", "AUTO USD 10 拒绝（当日 260 > 250）", pay(client, "10.00", "USD"), "DENY", "deny_limit")
    body = pay_body(client, "50.00", "USD", key=rid())
    first, second = evaluate(body), evaluate(body)
    check("AC-06", "同一业务键重放返回同一评估（拒绝，不重复累计）", first.get("evaluationId") == second.get("evaluationId")
          and first.get("result") == "DENY", [first.get("evaluationId"), second.get("evaluationId"), first.get("result")])
    expect("AC-06", "AUTO AED 250 > 单笔 200 拒绝", pay(client, "250.00", "AED"), "DENY", "deny_limit")
    for n in range(5):
        expect("AC-06", f"AUTO AED 200 第 {n + 1} 笔放行（USD 已到日上限，AED 单独累计）", pay(client, "200.00", "AED"), "ALLOW")
    expect("AC-06", "AUTO AED 10 拒绝（当日 1010 > 1000）", pay(client, "10.00", "AED"), "DENY", "deny_limit")
    expect("AC-06", "AUTO SAR 1 拒绝（VERIFICATION 未配置 SAR）", pay(client, "1.00", "SAR"), "DENY", "deny_limit")
    expect("AC-06", "AUTO 结账 USD 放行（结账点不做金额额度）", checkout(client, "USD", "999.00"), "ALLOW", "allow")
    expect("AC-06", "AUTO 结账 SAR 拒绝（币种集合）", checkout(client, "SAR"), "DENY", "deny_limit")
    totals = daily(client)
    d = today()
    check("AC-06", "日放行汇总：USD 250.00 / 5 笔、AED 1000.00 / 5 笔（被拒与重放不计）、结账 USD 999.00 / 1 笔",
          totals.get(("PAYMENT_ATTEMPT_ALLOWED", "USD", d)) == ("250.00", "5")
          and totals.get(("PAYMENT_ATTEMPT_ALLOWED", "AED", d)) == ("1000.00", "5")
          and totals.get(("CHECKOUT_ALLOWED", "USD", d)) == ("999.00", "1") and len(totals) == 3,
          {"|".join(k): v for k, v in totals.items()})
    quotas = {q["currency"]: q for q in tier_of(ctx["ro"], client).get("quotas") or []}
    check("AC-11", "工作台额度：USD 50 / 250 / 400，已用 250.00 / 250.00；AED 已用 1000.00",
          quotas.get("USD", {}).get("single") == "50.00" and quotas["USD"].get("usedToday") == "250.00"
          and quotas["USD"].get("usedLast30Days") == "250.00" and quotas.get("AED", {}).get("usedToday") == "1000.00",
          quotas)


def m_thirty_days(ctx):
    """E（VERIFICATION 期间）：昨日放行 300 USD（测试数据 10-e-yesterday.sql）计入近 30 日、不计入当日。"""
    client = CLIENTS["E"]
    if tier_of(ctx["ro"], client).get("tier") != "VERIFICATION":
        skip("AC-06", "近 30 日额度", "E 不是 VERIFICATION")
        return
    yesterday = (datetime.now(timezone.utc) - timedelta(days=1)).strftime("%Y-%m-%d")
    totals = daily(client)
    if ("PAYMENT_ATTEMPT_ALLOWED", "USD", yesterday) not in totals:
        out = subprocess.run([str(C.ROOT_SQL), C.RD], input=(HERE / "10-e-yesterday.sql").read_text(), capture_output=True, text=True)
        require("AC-06", "种昨日汇总 E USD 300（10-e-yesterday.sql）", out.returncode == 0, out.stderr[:300])
    elif ("PAYMENT_ATTEMPT_ALLOWED", "USD", today()) in totals:
        skip("AC-06", "近 30 日额度", f"E 今日已有 USD 汇总 {totals}，重跑跳过")
        return
    expect("AC-06", "E USD 50 放行（当日 50 / 30 日 350）", pay(client, "50.00", "USD"), "ALLOW")
    expect("AC-06", "E USD 50 放行（30 日 400，等于上限）", pay(client, "50.00", "USD"), "ALLOW")
    expect("AC-06", "E USD 50 拒绝（30 日 450 > 400，当日 150 ≤ 250）", pay(client, "50.00", "USD"), "DENY", "deny_limit")
    quotas = {q["currency"]: q for q in tier_of(ctx["ro"], client).get("quotas") or []}
    check("AC-11", "E 工作台：USD 已用当日 100.00、近 30 日 400.00", quotas.get("USD", {}).get("usedToday") == "100.00"
          and quotas["USD"].get("usedLast30Days") == "400.00", quotas.get("USD"))


def n_no_tier_and_standard(ctx):
    d, fix = CLIENTS["D"], CLIENTS["FIX"]
    expect("AC-06", "D 无档位：支付尝试拒绝（无档位分支）", pay(d, "1.00", "USD"), "DENY", "deny_no_tier")
    expect("AC-06", "D 无档位：结账拒绝", checkout(d, "USD"), "DENY", "deny_no_tier")
    expect("AC-07", "迁移 STANDARD（FIX）：USD 9000 放行（激活后交易不受影响）", pay(fix, "9000.00", "USD"), "ALLOW", "allow")
    expect("AC-06", "STANDARD：USD 10001 > 单笔 10000 拒绝", pay(fix, "10001.00", "USD"), "DENY", "deny_limit")
    expect("AC-06", "STANDARD：AED 36001 > 单笔 36000 拒绝", pay(fix, "36001.00", "AED"), "DENY", "deny_limit")
    expect("AC-06", "STANDARD：SAR 未配置放行", pay(fix, "5000.00", "SAR"), "ALLOW", "allow")
    expect("AC-06", "STANDARD：结账 SAR 放行", checkout(fix, "SAR"), "ALLOW", "allow")
    for other in ("237161687952666625", "900000000001380201"):
        expect("AC-07", f"迁移 STANDARD {other}：USD 10 放行", pay(other, "10.00", "USD"), "ALLOW")


# ---------------------------------------------------------------------------------------------------------------- G 变更流程

def g_restricted_low(ctx):
    """AUTO（LOW）：VERIFICATION → RESTRICTED → VERIFICATION；普通审批人可批；本人申请本人审批被拒。"""
    admin, appr, req, ro, client = ctx["admin"], ctx["appr"], ctx["req"], ctx["ro"], CLIENTS["AUTO"]
    if tier_of(ro, client).get("tier") != "VERIFICATION":
        skip("AC-02", "AUTO 受限往返", "AUTO 不是 VERIFICATION")
        return
    status, body = request_change(req, client, "STANDARD", "越级")
    check("AC-04", "VERIFICATION → STANDARD 被拒 TRANSITION_INVALID", code(body) == "RISK_LIMIT_TIER_TRANSITION_INVALID",
          f"HTTP {status} {problem(body)}")
    status, change = request_change(admin, client, "RESTRICTED", "rq04 stage5：受限演练")
    require("AC-02", "管理员申请 → RESTRICTED 201 PENDING、LOW 为普通审批", status == 201 and change.get("status") == "PENDING"
            and change.get("approvalLevel") == "STANDARD" and change.get("crrSnapshot") == "LOW",
            f"HTTP {status} {problem(change) if status != 201 else {k: change.get(k) for k in ('changeId', 'approvalLevel', 'crrSnapshot')}}")
    status, body = request_change(req, client, "RESTRICTED", "重复")
    check("AC-04", "已有待审批申请：再申请 409 PENDING_EXISTS", code(body) == "RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS",
          f"HTTP {status} {problem(body)}")
    status, body = decide(admin, change, "approval")
    check("AC-02", "申请人本人审批被拒 SELF_APPROVAL", code(body) == "RISK_LIMIT_TIER_SELF_APPROVAL", f"HTTP {status} {problem(body)}")
    status, body = decide(req, change, "withdrawal")
    check("AC-02", "非申请人撤回被拒 NOT_REQUESTER", code(body) == "RISK_LIMIT_TIER_NOT_REQUESTER", f"HTTP {status} {problem(body)}")
    status, done = decide(appr, change, "approval")
    require("AC-02", "普通审批人批准（LOW）→ RESTRICTED 生效", status == 200 and done.get("status") == "APPROVED",
            f"HTTP {status} {problem(done)}")
    check("AC-03", "批准即生效：工作台 RESTRICTED、previousTier=VERIFICATION", tier_of(ro, client).get("tier") == "RESTRICTED"
          and tier_of(ro, client).get("previousTier") == "VERIFICATION", tier_of(ro, client).get("tier"))
    consistent("AC-03", client, "RESTRICTED")
    expect("AC-06", "AUTO RESTRICTED：支付尝试受限拒绝（不进额度规则集）", pay(client, "1.00", "USD"), "DENY", "deny_restricted")
    expect("AC-06", "AUTO RESTRICTED：结账拒绝", checkout(client, "USD"), "DENY", "deny_restricted")
    row = template(client, "OBSERVATION")
    check("AC-09", "模板：RESTRICTED → satisfied=0、LIMIT_TIER_RESTRICTED", row.get("satisfied") == "0"
          and row.get("missing") == "LIMIT_TIER_RESTRICTED", row)
    body = ws(req, client)
    check("AC-04", "RESTRICTED 可申请目标：先前档位或更低（VERIFICATION）", body.get("requestableTargets") == ["VERIFICATION"],
          body.get("requestableTargets"))
    status, body = request_change(req, client, "OBSERVATION", "高于先前档位")
    check("AC-04", "RESTRICTED → OBSERVATION（高于先前档位）被拒", code(body) == "RISK_LIMIT_TIER_TRANSITION_INVALID",
          f"HTTP {status} {problem(body)}")
    status, change = request_change(req, client, "VERIFICATION", "rq04 stage5：解除受限")
    require("AC-02", "申请 RESTRICTED → VERIFICATION", status == 201, f"HTTP {status} {problem(change)}")
    status, done = decide(appr, change, "rejection", "先驳回一次")
    check("AC-02", "普通审批人驳回 → REJECTED，档位不变", status == 200 and done.get("status") == "REJECTED"
          and tier_of(ro, client).get("tier") == "RESTRICTED", f"HTTP {status} {done.get('status')}")
    status, change = request_change(req, client, "VERIFICATION", "rq04 stage5：解除受限")
    status, done = decide(appr, change, "approval")
    require("AC-02", "再申请并批准 → VERIFICATION", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    consistent("AC-03", client, "VERIFICATION")
    expect("AC-06", "AUTO 回到 VERIFICATION：当日 USD 仍已满（同日汇总延续）", pay(client, "10.00", "USD"), "DENY", "deny_limit")
    row = template(client, "OBSERVATION")
    check("AC-09", "模板：VERIFICATION 对 OBSERVATION → satisfied=0、LIMIT_TIER_BELOW_TARGET", row.get("satisfied") == "0"
          and row.get("missing") == "LIMIT_TIER_BELOW_TARGET" and row.get("out_tier") == "VERIFICATION", row)


def g_golive_elevated(ctx):
    """E（无 CRR，按高级审批）：STANDARD → RESTRICTED → VERIFICATION（近 30 日额度：10-e-yesterday.sql 种一行昨日汇总）→ 上线复核 OBSERVATION → 到期 → 延长 → 驳回 → STANDARD。"""
    req, appr, elev, ro, admin, client = ctx["req"], ctx["appr"], ctx["elev"], ctx["ro"], ctx["admin"], CLIENTS["E"]
    current = tier_of(ro, client).get("tier")
    if current == "STANDARD" and not ctx.get("E_done"):
        status, change = request_change(req, client, "RESTRICTED", "rq04 stage5：为上线复核演练降级")
        require("AC-02", "E 申请 STANDARD → RESTRICTED（无 CRR → ELEVATED）", status == 201 and change.get("approvalLevel") == "ELEVATED",
                f"HTTP {status} {problem(change) if status != 201 else change.get('approvalLevel')}")
        status, body = decide(appr, change, "approval")
        check("AC-02", "普通审批人批准 ELEVATED 申请被拒 ELEVATION_REQUIRED", code(body) == "RISK_LIMIT_TIER_ELEVATION_REQUIRED",
              f"HTTP {status} {problem(body)}")
        status, done = decide(elev, change, "elevated-approval")
        require("AC-02", "高级审批人批准 → RESTRICTED", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
        status, change = request_change(req, client, "VERIFICATION", "rq04 stage5：降至验证档")
        status, done = decide(elev, change, "elevated-approval")
        require("AC-02", "RESTRICTED → VERIFICATION（低于先前档位 STANDARD）批准", status == 200 and done.get("status") == "APPROVED",
                f"HTTP {status} {problem(done)}")
        consistent("AC-03", client, "VERIFICATION")
        current = "VERIFICATION"
    if current == "VERIFICATION":
        m_thirty_days(ctx)
        body = ws(req, client)
        items = {i["code"]: i["status"] for i in (body.get("checklist") or {}).get("items") or []}
        facts = (body.get("checklist") or {}).get("facts") or {}
        check("AC-02", "复核清单系统项：资格 APPROVED、有效承保、合同 ACTIVE、有 ENABLED 开通项（只读带出）",
              facts.get("qualificationStatus") == "APPROVED" and facts.get("underwritingId") and facts.get("agreementStatus") == "ACTIVE"
              and facts.get("enabledPaymentCapabilities"), {"items": items, "facts": facts})
        status, body = request_change(req, client, "OBSERVATION", "上线复核", checklist={**CHECKLIST, "productionVerified": False},
                                      note="生产验证未完成")
        check("AC-02", "人工项未全部勾选 → CHECKLIST_NOT_SATISFIED", code(body) == "RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED",
              f"HTTP {status} {problem(body)}")
        status, body = request_change(req, client, "OBSERVATION", "上线复核", checklist=CHECKLIST, note=None)
        check("AC-02", "缺上线说明 → 被拒", status in (400, 409, 422), f"HTTP {status} {problem(body)}")
        status, change = request_change(req, client, "OBSERVATION", "rq04 stage5：上线复核", checklist=CHECKLIST,
                                        note="渠道报备、接入、生产验证完成；无未结事项", days=30, txns=2)
        require("AC-02", "上线复核申请 201：清单快照、ELEVATED", status == 201 and change.get("approvalLevel") == "ELEVATED"
                and (change.get("checklist") or {}).get("qualificationStatus") == "APPROVED"
                and (change.get("checklist") or {}).get("productionVerified") is True,
                f"HTTP {status} {problem(change) if status != 201 else change.get('checklist')}")
        status, done = decide(req, change, "withdrawal")
        check("AC-02", "申请人撤回 → WITHDRAWN", status == 200 and done.get("status") == "WITHDRAWN", f"HTTP {status} {problem(done)}")
        status, change = request_change(req, client, "OBSERVATION", "rq04 stage5：上线复核", checklist=CHECKLIST,
                                        note="渠道报备、接入、生产验证完成；无未结事项", days=30, txns=2)
        status, listed = ro.get(f"{LT}/client-tiers?pendingApproval=true&page=1&size=50")
        check("AC-10", "列表筛选待审批包含 E", any(i.get("clientId") == client for i in listed.get("items") or []),
              [i.get("clientId") for i in listed.get("items") or []])
        status, body = decide(appr, change, "approval")
        check("AC-02", "普通审批人批准上线复核（ELEVATED）被拒", code(body) == "RISK_LIMIT_TIER_ELEVATION_REQUIRED",
              f"HTTP {status} {problem(body)}")
        status, done = decide(elev, change, "elevated-approval")
        require("AC-02", "高级审批人批准 → OBSERVATION", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
        consistent("AC-03", client, "OBSERVATION")
        current = "OBSERVATION"
    if current != "OBSERVATION":
        skip("AC-05", "E 观察期", f"E 当前 {current}")
        return
    tier = tier_of(ro, client)
    obs = tier.get("observation") or {}
    check("AC-05", "观察期：目标 30 天 / 2 笔，放行 0，未到期", obs.get("targetDays") == 30 and obs.get("targetTransactions") == 2
          and obs.get("allowedTransactions") == 0 and obs.get("due") is False, obs)
    expect("AC-06", "E OBSERVATION：USD 2500 > 单笔 2000 拒绝", pay(client, "2500.00", "USD"), "DENY", "deny_limit")
    expect("AC-06", "E OBSERVATION：SAR 未配置拒绝", pay(client, "1.00", "SAR"), "DENY", "deny_limit")
    expect("AC-05", "E OBSERVATION：USD 1500 放行 1", pay(client, "1500.00", "USD"), "ALLOW")
    expect("AC-05", "E OBSERVATION：AED 100 放行 2", pay(client, "100.00", "AED"), "ALLOW")
    obs = (tier_of(ro, client).get("observation") or {})
    check("AC-05", "放行 2 笔 → 到期待确认（due=true），不自动升档", obs.get("allowedTransactions") == 2 and obs.get("due") is True
          and tier_of(ro, client).get("tier") == "OBSERVATION", obs)
    status, listed = ro.get(f"{LT}/client-tiers?observationDue=true&page=1&size=50")
    check("AC-10", "列表“到期待确认”筛选只按天数（S-10，可索引）：E 按笔数到期、天数未到 → 不在筛选结果",
          status == 200 and not any(i.get("clientId") == client for i in listed.get("items") or []),
          [i.get("clientId") for i in listed.get("items") or []])
    status, stats = ro.get(f"{LT}/tier-statistics")
    expected = int(C.root_sql("SELECT COUNT(*) n FROM t_client_limit_tier WHERE tier = 'OBSERVATION' AND "
                              "DATE_ADD(observation_started_time, INTERVAL observation_target_days DAY) <= UTC_TIMESTAMP(6)", C.RD)[0]["n"])
    check("AC-10", "统计 observationDue 与按天数口径的库内计数一致", status == 200 and stats.get("observationDue") == expected,
          {"api": stats.get("observationDue"), "sql": expected})
    status, change = request_change(req, client, "OBSERVATION", "rq04 stage5：延长观察", days=30, txns=5)
    require("AC-05", "延长观察申请（OBSERVATION → OBSERVATION）", status == 201, f"HTTP {status} {problem(change)}")
    status, done = decide(elev, change, "elevated-approval")
    obs = (tier_of(ro, client).get("observation") or {})
    check("AC-05", "延长批准：观察重新开始（放行 0、目标 5、未到期）", status == 200 and obs.get("allowedTransactions") == 0
          and obs.get("targetTransactions") == 5 and obs.get("due") is False, obs)
    status, change = request_change(req, client, "STANDARD", "rq04 stage5：观察期满确认")
    status, done = decide(elev, change, "elevated-rejection", "先驳回")
    check("AC-02", "高级审批人驳回升 STANDARD → 仍 OBSERVATION", status == 200 and done.get("status") == "REJECTED"
          and tier_of(ro, client).get("tier") == "OBSERVATION", f"HTTP {status} {done.get('status')}")
    row = template(client, "STANDARD")
    check("AC-09", "模板：OBSERVATION 对 STANDARD → satisfied=0、BELOW_TARGET；对 OBSERVATION → 1",
          row.get("satisfied") == "0" and row.get("missing") == "LIMIT_TIER_BELOW_TARGET"
          and template(client, "OBSERVATION").get("satisfied") == "1", row)
    status, change = request_change(req, client, "STANDARD", "rq04 stage5：观察期满确认，还原 STANDARD")
    status, done = decide(elev, change, "elevated-approval")
    require("AC-02", "OBSERVATION → STANDARD 批准（E 还原为 STANDARD）", status == 200 and done.get("status") == "APPROVED",
            f"HTTP {status} {problem(done)}")
    consistent("AC-03", client, "STANDARD")
    row = template(client, "STANDARD")
    check("AC-09", "模板：STANDARD 对 STANDARD → satisfied=1、missing 空", row.get("satisfied") == "1" and row.get("missing") == "NULL", row)
    status, changes = ro.get(f"{LT}/clients/{client}/tier-changes")
    check("AC-10", "E 变更历史（时间倒序）含 MIGRATION 与各次申请", [(i.get("changeType"), i.get("toTier"), i.get("status"))
                                                            for i in changes.get("items") or []][-1] == ("MIGRATION", "STANDARD", "APPROVED"),
          [(i.get("changeType"), i.get("fromTier"), i.get("toTier"), i.get("status")) for i in changes.get("items") or []])
    ctx["E_done"] = True


# ---------------------------------------------------------------------------------------------------------------- F Payment 全链路

PAY_CLIENT, PAY_ENTITY = CLIENTS["FIX"], "AE001"


def pay_api(method, path, body=None, headers=None):
    status, text = E.private_call("payment", method, path, headers=headers, body=body)
    try:
        return status, json.loads(text) if text else {}
    except ValueError:
        return status, {"raw": text[:300]}


def payment_chain(amount="12.00"):
    """以 acquiring 身份直调 Payment：建 Intent → 卡工具（公开测试卡 4111…）→ pay；返回 pay 回执与该 Attempt 的风控评估结论。"""
    source = rid()
    status, intent = pay_api("POST", "/payment-execution/v1/intents", {
        "clientId": PAY_CLIENT, "sourceType": "COMMERCE_ORDER", "sourceId": source, "entityNo": PAY_ENTITY, "clientPricingVersionId": "1",
        "industryCode": "DIGITAL_GOODS", "mccCode": "5999", "consumerCountryCode": "AE", "amount": amount, "currency": "USD",
        "expiresAt": (datetime.now(timezone.utc) + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%SZ")})
    if status not in (200, 201):
        return {"intentHttp": status, "problem": problem(intent)}
    submission = rid()
    status, token = pay_api("POST", "/payment-instrument/v1/card-tokens", {
        "clientId": PAY_CLIENT, "intentId": intent["intentId"], "sourceSubmissionId": submission, "paymentMethodCode": "CARD",
        "card": {"pan": "4111111111111111", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}})
    if status not in (200, 201):
        return {"tokenHttp": status, "problem": problem(token)}
    status, paid = pay_api("POST", f"/payment-execution/v1/intents/{intent['intentId']}/pay", {
        "clientId": PAY_CLIENT, "sourceSubmissionId": submission, "captureMode": "AUTOMATIC",
        "payMethod": {"paymentMethodCode": "CARD", "paymentInstrumentToken": token["paymentInstrumentToken"]},
        "returnUrl": "https://checkout.local.invalid/return", "consumerReference": "rq04-s5-payer",
        "consumerIpAddress": "203.0.113.10", "consumerUserAgent": "Mozilla/5.0 (rq04 stage5)"}, {"Idempotency-Key": "submission:" + submission})
    attempt = paid.get("attemptId")
    evaluation = C.root_sql("SELECT e.business_decision, e.technical_status, e.evaluation_id FROM t_transaction_risk_assessment a "
                            "JOIN t_decision_evaluation e "
                            f"ON e.evaluation_id = a.evaluation_id WHERE a.payment_attempt_id = {attempt}", C.RD) if attempt else []
    path = [r["node_key"] for r in C.root_sql(f"SELECT node_key FROM t_evaluation_flow_trace WHERE evaluation_id = "
                                              f"{evaluation[0]['evaluation_id']} ORDER BY 1", C.RD)] if evaluation else []
    attempt_row = E.sql("payment", f"SELECT attempt_status FROM t_payment_attempt WHERE payment_attempt_id = {attempt}") if attempt else []
    return {"payHttp": status, "payStatus": paid.get("status"), "attemptId": attempt, "problem": None if status in (200, 201) else problem(paid),
            "risk": evaluation[0]["business_decision"] if evaluation else None, "path": path,
            "riskTechnicalStatus": evaluation[0]["technical_status"] if evaluation else None,
            "attemptStatus": attempt_row[0][0] if attempt_row else None}


def settled_chain():
    """本机负载下 Payment 建 Intent 偶发 503、风控偶发超时（F-4）：记 INFO 后重试，至多 3 次。"""
    outcome = payment_chain()
    for _ in range(3):
        if outcome.get("riskTechnicalStatus") == "SUCCEEDED":
            break
        record("F-4", "Payment 全链路重试", "INFO", {k: outcome.get(k) for k in ("payHttp", "problem", "riskTechnicalStatus", "attemptId")})
        time.sleep(2)
        outcome = payment_chain()
    return outcome


def f_payment_chain(ctx):
    req, elev, ro = ctx["req"], ctx["elev"], ctx["ro"]
    if tier_of(ro, PAY_CLIENT).get("tier") != "STANDARD":
        skip("AC-06", "Payment 全链路", "FIX 不是 STANDARD")
        return
    allowed = settled_chain()
    check("AC-07", "Payment 全链路：STANDARD（迁移）支付风控放行、尝试继续", allowed.get("risk") == "ALLOW"
          and "allow" in allowed.get("path", []), allowed)
    status, change = request_change(req, PAY_CLIENT, "RESTRICTED", "rq04 stage5：全链路受限")
    status, done = decide(elev, change, "elevated-approval")
    require("AC-06", "FIX → RESTRICTED（无 CRR → 高级审批）", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    try:
        denied = settled_chain()
        check("AC-06", "Payment 全链路：RESTRICTED 支付风控拒绝、尝试不继续", denied.get("risk") == "DENY"
              and "deny_restricted" in denied.get("path", []), denied)
        row = template(PAY_CLIENT, "STANDARD")
        check("AC-09", "模板：RESTRICTED → LIMIT_TIER_RESTRICTED", row.get("missing") == "LIMIT_TIER_RESTRICTED", row)
    finally:
        status, change = request_change(req, PAY_CLIENT, "STANDARD", "rq04 stage5：还原先前档位")
        status, done = decide(elev, change, "elevated-approval")
        require("AC-04", "RESTRICTED → 先前档位 STANDARD 还原", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    consistent("AC-03", PAY_CLIENT, "STANDARD")
    again = settled_chain()
    check("AC-07", "Payment 全链路：还原后再次放行", again.get("risk") == "ALLOW", again)


# ---------------------------------------------------------------------------------------------------------------- T 模板、L 列表、X 日志

def t_template(ctx):
    row = template(CLIENTS["D"], "OBSERVATION")
    check("AC-09", "模板：无档位 → satisfied=0、LIMIT_TIER_NOT_SET、out_tier 空", row.get("satisfied") == "0"
          and row.get("missing") == "LIMIT_TIER_NOT_SET" and row.get("out_tier") == "NULL", row)
    row = template(CLIENTS["FIX"], "STANDARD")
    check("AC-09", "模板：迁移 STANDARD → satisfied=1", row.get("satisfied") == "1", row)


def l_lists(ctx):
    ro = ctx["ro"]
    status, stats = ro.get(f"{LT}/tier-statistics")
    sql = {r["tier"]: int(r["n"]) for r in C.root_sql("SELECT tier, COUNT(*) n FROM t_client_limit_tier GROUP BY tier", C.RD)}
    check("AC-10", "统计与库一致", status == 200 and all((stats.get(t.lower()) or 0) == sql.get(t, 0)
                                                     for t in ("VERIFICATION", "OBSERVATION", "STANDARD", "RESTRICTED")),
          {"api": stats, "sql": sql})
    status, listed = ro.get(f"{LT}/client-tiers?tier=VERIFICATION&page=1&size=50")
    ids = {i.get("clientId") for i in listed.get("items") or []}
    check("AC-10", "列表按档位筛选 VERIFICATION 含 AUTO（B 无档位，见 F-1）", CLIENTS["AUTO"] in ids and CLIENTS["B"] not in ids, sorted(ids))
    status, listed = ro.get(f"{LT}/client-tiers?keyword=RELSNAP&page=1&size=50")
    check("AC-10", "列表关键字（编号）筛选", status == 200 and len(listed.get("items") or []) == 2,
          [i.get("clientNo") for i in listed.get("items") or []])
    status, quotas = ro.get(f"{LT}/tier-quotas")
    check("AC-11", "额度页：四档位、无配置问题、VERIFICATION 未配置币种 DENY、STANDARD ALLOW", status == 200
          and quotas.get("configurationIssues") == [] and {t["tier"]: t["unconfiguredCurrencyPolicy"] for t in quotas["tiers"]}
          == {"VERIFICATION": "DENY", "OBSERVATION": "DENY", "STANDARD": "ALLOW", "RESTRICTED": "DENY"}, quotas.get("configurationIssues"))
    for client in CLIENTS.values():
        tier = (C.root_sql(f"SELECT tier FROM t_client_limit_tier WHERE client_id = {client}", C.RD) or [{}])[0].get("tier")
        found = lists(client)
        check("AC-03", f"{client} 终态：档位与名单一致", found == ([f"TIER_{tier}"] if tier else []), {"tier": tier, "lists": found})


def x_logs(ctx):
    since = STARTED.replace("Z", "")
    for name in ("risk-decision", "ops-bff"):
        lines = [line for line in (LOGS / f"{name}.log").read_text(errors="replace").splitlines() if line[:19] >= since[:19]]
        errors = [line[:200] for line in lines if " ERROR " in line]
        check("G", f"{name} 日志本次运行无 ERROR", not errors, errors[:5])
    lines = [line for line in (LOGS / "ops-bff.log").read_text(errors="replace").splitlines()
             if line[:19] >= since[:19] and "risk-limit-tiers" in line and "HTTP request completed" in line]
    check("G", "ops-bff 档位路由访问日志只记元数据（无 requestBody / responseBody）", lines and not any("requestBody=" in line for line in lines),
          f"{len(lines)} 行")


SCENARIOS = {"S": s_accounts, "P": p_permissions, "U": u_underwriting, "Q": q_quotas, "M": m_thirty_days, "N": n_no_tier_and_standard,
             "G": g_restricted_low, "O": g_golive_elevated, "F": f_payment_chain, "T": t_template, "L": l_lists, "X": x_logs}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default=",".join(SCENARIOS))
    only = parser.parse_args().only.split(",")
    ctx = {"admin": C.admin()}
    for key in only:
        if key != "S" and "ro" not in ctx:
            s_accounts(ctx)
        try:
            SCENARIOS[key](ctx)
        except OA.Stop as stop:
            record(key, "场景中止", "FAIL", str(stop))
    out = HERE / "stage5-e2e-result.json"
    previous = json.loads(out.read_text()) if out.exists() else {"runs": []}
    previous["runs"].append({"startedAt": STARTED, "only": only, "results": OA.RESULTS})
    out.write_text(json.dumps(previous, ensure_ascii=False, indent=1) + "\n")
    failed = [r for r in OA.RESULTS if r["verdict"] == "FAIL"]
    print(f"PASS {sum(r['verdict'] == 'PASS' for r in OA.RESULTS)} FAIL {len(failed)} SKIP {sum(r['verdict'] == 'SKIP' for r in OA.RESULTS)}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
