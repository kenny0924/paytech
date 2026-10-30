#!/usr/bin/env python3
"""REQ-04 限额档位 · mor-test 阶段 7 验收（API 级，E2E_TARGET=test；运营入口 http://localhost:28080 → Gateway 块 145 → ops-bff →
compliance / commercial / risk-decision；评估经私网探针调用 risk-decision 统一评估入口）。阶段 5 本机脚本 verification/stage5/stage5_e2e.py
的 mor-test 子集；在 runbook ⑭（档位发布激活、迁移 Client 冒烟）之后执行。

数据（阶段 6 只读选定，都是验收合成数据）：
  L 240686705999454209 "Acceptance Trading 1027040243 LLC"（REQ-03 验收：承保 v2 APPROVED、CRR LOW、无开通项、无档位）
  H 241049681294106625 "Refdata AE 1028040509 LLC"（REQ-03 验收：承保 v2 APPROVED、CRR HIGH、无开通项、无档位）
  迁移名单（DEMO 4 个，STANDARD）只做冒烟（rq04_migrate.py smoke），不做档位变更。
子命令（结果写本目录 result-<子命令>-<UTC>.json，不含口令与令牌）：
  accounts  写：IAM 建 4 个合成角色与账号（req = READ + REQUEST，appr = READ + APPROVE，elev = READ + APPROVE_ELEVATED，ro = READ；
            都含 RISK_DECISION_CONFIG_READ 以显示"风控"父菜单），凭据 ~/Code-MoR-backups/rq04-stage7/accounts.json（0600）。需 --yes。
  prepare   写：REQ-03 验收账号 uw / lead（~/Code-MoR-backups/rq03-stage7/accounts.json）对 L、H 各做一次承保修订并批准
            （L：LOW 承保员直接批准；H：HIGH 提交 → 负责人复核）→ F-1：无档位 Client 修订批准即建 VERIFICATION。需 --yes。
  api       写：P 权限、L 的 VERIFICATION 额度评估与 RESTRICTED 往返（LOW 普通审批、本人审批拒绝、非申请人撤回拒绝）、
            L 上线复核因无 ENABLED 开通项被拒、H 的高级审批（普通审批人 ELEVATION_REQUIRED）、越级 / 重复申请拒绝、TIER 名单手工维护拒绝。需 --yes。
  check     只读：档位与名单一致、统计与库一致、日放行汇总与放行评估一致、激活后 TIMED_OUT 计数、模板只读试跑、近期日志只记元数据。
不提供删除：档位与变更记录是审计依据（回滚 SQL 有数据时拒绝）。
用法：E2E_TARGET=test python3 -I rq04_acceptance.py <accounts|prepare|api|check> [--yes]
"""
import argparse
import json
import random
import secrets as ps
import subprocess
import sys
import time
import uuid
from decimal import Decimal
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import rq04_release_common as C  # noqa: E402

OA = C.OA
check, require, record, skip, problem = OA.check, OA.require, OA.record, OA.skip, OA.problem
UW, LT = C.UW, C.LT
CLIENTS = {"L": "240686705999454209", "H": "241049681294106625"}
PERMISSIONS = {"READ": "900000000000145101", "REQUEST": "900000000000145102", "APPROVE": "900000000000145103",
               "APPROVE_ELEVATED": "900000000000145104", "RISK_MENU": "900000000000000536"}
ROLES = {"req": ("READ", "REQUEST"), "appr": ("READ", "APPROVE"), "elev": ("READ", "APPROVE_ELEVATED"), "ro": ("READ",)}
CHECKLIST = {"channelRegistrationConfirmed": True, "integrationConfirmed": True, "productionVerified": True, "openIssuesCleared": True}
RQ03_ACCOUNTS = Path.home() / "Code-MoR-backups/rq03-stage7/accounts.json"
TEMPLATE = C.REQ / "db/data-query-templates.sql"


def code(body):
    return body.get("code") if isinstance(body, dict) else None


def rid():
    return str(random.randrange(10 ** 14, 9 * 10 ** 14))


# ---------------------------------------------------------------------------------------------------------------- 会话与档位接口

def sessions(ctx, with_rq03=False):
    saved = C.load_state("accounts.json")
    for label in ROLES:
        if label not in saved:
            raise SystemExit(f"缺少账号 {label}：先执行 accounts --yes")
        ctx[label] = OA.ops_login(saved[label]["email"], saved[label]["password"])
        ctx[label].account_id = ctx[label].account_id or saved[label]["accountId"]
    if with_rq03:
        rq03 = json.loads(RQ03_ACCOUNTS.read_text())
        for label in ("uw", "lead"):
            ctx[label] = OA.ops_login(rq03[label]["email"], rq03[label]["password"])
            ctx[label].account_id = ctx[label].account_id or rq03[label]["accountId"]


def ws(session, client):
    status, body = session.get(f"{LT}/clients/{client}/workspace")
    require("WS", f"档位工作台 {client}", status == 200, f"HTTP {status} {problem(body) if status != 200 else ''}")
    return body


def tier_of(session, client):
    return ws(session, client).get("tier") or {}


def request_change(session, client, to, reason, checklist=None, note=None, days=None, txns=None):
    tier = tier_of(session, client)
    return session.call("POST", f"{LT}/clients/{client}/tier-changes", {
        "expectedTierVersion": tier.get("version"), "toTier": to, "reason": reason, "goLiveNote": note,
        "observationTargetDays": days, "observationTargetTransactions": txns, "checklist": checklist})


def decide(session, change, action, comment="rq04 acceptance"):
    body = {"expectedVersion": change["version"]} if action == "withdrawal" else {"expectedVersion": change["version"], "comment": comment}
    return session.call("POST", f"{LT}/tier-changes/{change['changeId']}/{action}", body)


def lists(client):
    return [r["list_code"] for r in C.root_ro("mor_risk_decision", (
        "SELECT l.list_code FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id WHERE l.list_purpose = 'TIER' "
        f"AND e.status = 'ACTIVE' AND e.subject_type = 'CLIENT' AND e.subject_key = '{client}'"))]


def consistent(ac, client, tier):
    found = lists(client)
    return check(ac, f"{client} 档位 {tier} 与名单一致", found == [f"TIER_{tier}"], found)


def evaluate(client, amount, currency):
    body = {"pointCode": "PAYMENT_ATTEMPT_RISK", "contractVersion": 1, "businessKey": rid(), "inputs": {
        "paymentIntentId": rid(), "riskActionId": rid(), "orderId": rid(), "clientId": client, "entityNo": "AE001",
        "paymentMethodCode": "CARD_VISA", "captureMode": "AUTOMATIC", "amount": amount, "currency": currency,
        "industryCode": "DIGITAL_GOODS", "mccCode": "5816", "consumerCountryCode": "AE", "consumerReference": "rq04-acceptance",
        "clientIpAddress": "203.0.113.10", "userAgent": "Mozilla/5.0 rq04-acceptance", "cardFingerprint": "fp_rq04_" + rid(),
        "cardBin": "411111", "issuerCountryCode": "AE", "cardBrand": "VISA", "fundingType": "CREDIT"}}
    status, reply = C.private("risk-decision", "POST", "/risk-decisions/v1/evaluations", body=body,
                              headers={"X-Request-Id": f"rq04-acc-{uuid.uuid4()}"})
    if status != 200:
        return {"http": status, "problem": problem(reply)}
    path = [r["node_key"] for r in C.root_ro("mor_risk_decision", "SELECT node_key FROM t_evaluation_flow_trace WHERE evaluation_id = "
                                             f"{reply['evaluationId']} ORDER BY 1")]
    return {"http": status, "result": reply.get("result"), "evaluationId": reply.get("evaluationId"), "path": path}


def expect(ac, step, outcome, result, node=None):
    return check(ac, step, outcome.get("result") == result and (node is None or node in (outcome.get("path") or [])),
                 {k: outcome.get(k) for k in ("http", "result", "path", "problem")})


def template(client, target):
    text = TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1]
    return C.root_ro("mor_risk_decision", text.replace("#{clientId}", str(int(client))).replace("#{targetTier}", f"'{target}'"))[0]


# ---------------------------------------------------------------------------------------------------------------- accounts

def accounts(ctx):
    admin = ctx["admin"]
    saved = C.load_state("accounts.json")
    stamp = time.strftime("%H%M%S")
    for label, grants in ROLES.items():
        if label in saved:
            record("S", f"{label} 已存在（复用）", "PASS", saved[label]["accountId"])
            continue
        ids = [PERMISSIONS[g] for g in grants] + [PERMISSIONS["RISK_MENU"]]
        status, role = admin.call("POST", "/iam/v1/roles", {"roleCode": f"RQ04_ACCEPTANCE_{label.upper()}_{stamp}",
                                                            "roleName": f"REQ-04 验收 {label} {stamp}", "permissionIds": ids,
                                                            "reason": "REQ-04 限额档位 mor-test 验收"})
        require("S", f"IAM 建角色 {label} {grants}", status == 200, f"HTTP {status} {problem(role)}")
        role_id = role.get("roleId") or role.get("objectId")
        email = f"rq04-acceptance-{label}-{stamp}@example.test"
        p1, p2 = "Rq!" + ps.token_urlsafe(18), "Rq!" + ps.token_urlsafe(18)
        status, account = admin.call("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                                  "reason": "REQ-04 限额档位 mor-test 验收"})
        require("S", f"IAM 建账号 {label}", status == 200, f"HTTP {status} {problem(account)}")
        first = OA.ops_login(email, p1)
        status, _ = first.call("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        require("S", f"{label} 首登改密", status in (200, 204), f"HTTP {status}")
        saved[label] = {"email": email, "password": p2, "accountId": account.get("accountId"), "roleId": role_id,
                        "permissions": list(grants) + ["RISK_DECISION_CONFIG_READ"]}
        C.save_state("accounts.json", saved)
        record("S", f"{label} 建成", "PASS", f"accountId={account.get('accountId')}")


# ---------------------------------------------------------------------------------------------------------------- prepare（F-1）

def revise_and_approve(ctx, client, label):
    uw, lead, ro = ctx["uw"], ctx["lead"], ctx["ro"]
    if tier_of(ro, client):
        skip("AC-01", f"{label} 修订批准建档", f"已有档位 {tier_of(ro, client).get('tier')}，跳过")
        return
    check("AC-01", f"{label} 修订前无档位、无 TIER 名单成员", not lists(client), lists(client))
    effective = ws(ro, client).get("underwriting") or {}
    require("AC-01", f"{label} 有有效承保", effective.get("underwritingId"), effective)
    status, current = uw.get(f"{UW}/underwritings/{effective['underwritingId']}")
    status, draft = uw.call("POST", f"{UW}/underwritings/{effective['underwritingId']}/revision",
                            {"expectedVersion": current["version"], "revisionReason": "REQ-04 mor-test 验收：修订批准建档（F-1）"})
    require("AC-01", f"{label} 修订草稿 201", status == 201, f"HTTP {status} {problem(draft)}")
    status, submitted = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                                {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                 "decision": draft.get("decision")})
    require("AC-01", f"{label} 提交", status == 200 and submitted.get("status") in ("APPROVED", "SUBMITTED"), f"HTTP {status} {problem(submitted)}")
    if submitted.get("status") == "SUBMITTED":
        status, done = lead.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/review",
                                 {"expectedVersion": submitted["version"], "decision": "APPROVE", "comment": "REQ-04 验收"})
        require("AC-01", f"{label} 负责人复核批准", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
    tier = tier_of(ro, client)
    status, changes = ro.get(f"{LT}/clients/{client}/tier-changes")
    items = changes.get("items") or []
    check("AC-01", f"{label} 修订批准后 VERIFICATION（UNDERWRITING_INIT，记本次修订版本）", tier.get("tier") == "VERIFICATION"
          and tier.get("sourceType") == "UNDERWRITING_INIT" and len(items) == 1 and items[0].get("underwritingId") == draft["underwritingId"],
          {"tier": tier.get("tier"), "source": tier.get("sourceType"), "changes": [(i.get("changeType"), i.get("underwritingId")) for i in items]})
    consistent("AC-03", client, "VERIFICATION")


def prepare(ctx):
    revise_and_approve(ctx, CLIENTS["L"], "L（LOW）")
    revise_and_approve(ctx, CLIENTS["H"], "H（HIGH）")


# ---------------------------------------------------------------------------------------------------------------- api

def api(ctx):
    admin, req, appr, elev, ro = ctx["admin"], ctx["req"], ctx["appr"], ctx["elev"], ctx["ro"]
    lc, hc = CLIENTS["L"], CLIENTS["H"]
    # P 权限与只读
    body = ws(ro, lc)
    check("AC-10", "只读：工作台 canRequest=false", body.get("canRequest") is False, body.get("canRequest"))
    status, body = request_change(ro, lc, "RESTRICTED", "ro")
    check("AC-02", "只读：申请 403", status == 403, f"HTTP {status} {problem(body)}")
    status, quotas = ro.get(f"{LT}/tier-quotas")
    check("AC-11", "额度页：无配置问题、VERIFICATION 有 USD / AED / SAR / EGP", status == 200 and quotas.get("configurationIssues") == []
          and sorted(c["currency"] for t in quotas["tiers"] if t["tier"] == "VERIFICATION" for c in t["currencies"]) == ["AED", "EGP", "SAR", "USD"],
          quotas.get("configurationIssues"))
    status, body = admin.call("POST", "/risk-lists/v1/lists/TIER_STANDARD/entries",
                              {"subjectValue": lc, "effectiveFrom": None, "effectiveTo": None, "reason": "manual"})
    check("AC-08", "名单页：TIER 名单手工加成员被拒（业务校验）", status == 400 and code(body) == "BUSINESS_CHECK_FAILED", f"HTTP {status} {problem(body)}")
    status, body = admin.call("POST", f"{LT}/internal/tier-migrations", {"executedBy": "1", "reason": "x", "clients": []})
    check("AC-07", "迁移接口不经 Gateway 暴露（外部入口 404）", status == 404, f"HTTP {status} {problem(body)}")
    if tier_of(ro, lc).get("tier") != "VERIFICATION":
        skip("AC-06", "L 额度与往返", f"L 当前 {tier_of(ro, lc).get('tier')}")
    else:
        # VERIFICATION 额度（单笔 USD 500，见 risk-config.json）
        expect("AC-06", "L VERIFICATION：USD 600 > 单笔拒绝", evaluate(lc, "600.00", "USD"), "DENY", "deny_limit")
        expect("AC-06", "L VERIFICATION：USD 100 放行", evaluate(lc, "100.00", "USD"), "ALLOW", "tier_verification")
        expect("AC-06", "L VERIFICATION：AED 100 放行（AED 单独累计）", evaluate(lc, "100.00", "AED"), "ALLOW", "tier_verification")
        expect("AC-06", "L VERIFICATION：GBP 未配置拒绝", evaluate(lc, "1.00", "GBP"), "DENY", "deny_limit")
        status, body = request_change(req, lc, "STANDARD", "越级")
        check("AC-04", "VERIFICATION → STANDARD 被拒", code(body) == "RISK_LIMIT_TIER_TRANSITION_INVALID", f"HTTP {status} {problem(body)}")
        status, body = request_change(req, lc, "OBSERVATION", "上线复核", checklist=CHECKLIST, note="mor-test 验收")
        check("AC-02", "L 上线复核：无 ENABLED 开通项 → CHECKLIST_NOT_SATISFIED（系统项由 BFF 带出）",
              code(body) == "RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED", f"HTTP {status} {problem(body)}")
        status, change = request_change(admin, lc, "RESTRICTED", "REQ-04 验收：受限往返")
        require("AC-02", "管理员申请 → RESTRICTED（LOW 普通审批）", status == 201 and change.get("approvalLevel") == "STANDARD",
                f"HTTP {status} {problem(change) if status != 201 else change.get('approvalLevel')}")
        status, body = request_change(req, lc, "RESTRICTED", "重复")
        check("AC-04", "再申请 409 PENDING_EXISTS", code(body) == "RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS", f"HTTP {status} {problem(body)}")
        status, body = decide(admin, change, "approval")
        check("AC-02", "本人审批被拒 SELF_APPROVAL", code(body) == "RISK_LIMIT_TIER_SELF_APPROVAL", f"HTTP {status} {problem(body)}")
        status, body = decide(req, change, "withdrawal")
        check("AC-02", "非申请人撤回被拒 NOT_REQUESTER", code(body) == "RISK_LIMIT_TIER_NOT_REQUESTER", f"HTTP {status} {problem(body)}")
        status, done = decide(appr, change, "approval")
        require("AC-02", "普通审批人批准 → RESTRICTED", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
        consistent("AC-03", lc, "RESTRICTED")
        expect("AC-06", "L RESTRICTED：受限拒绝", evaluate(lc, "1.00", "USD"), "DENY", "deny_restricted")
        row = template(lc, "OBSERVATION")
        check("AC-09", "模板：RESTRICTED → LIMIT_TIER_RESTRICTED", row.get("missing") == "LIMIT_TIER_RESTRICTED", row)
        status, change = request_change(req, lc, "VERIFICATION", "REQ-04 验收：解除受限")
        status, done = decide(appr, change, "approval")
        require("AC-02", "RESTRICTED → VERIFICATION 批准", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
        consistent("AC-03", lc, "VERIFICATION")
    if tier_of(ro, hc).get("tier") != "VERIFICATION":
        skip("AC-02", "H 高级审批", f"H 当前 {tier_of(ro, hc).get('tier')}")
        return
    status, change = request_change(req, hc, "RESTRICTED", "REQ-04 验收：HIGH 高级审批")
    require("AC-02", "H 申请 → RESTRICTED（HIGH → ELEVATED）", status == 201 and change.get("approvalLevel") == "ELEVATED",
            f"HTTP {status} {problem(change) if status != 201 else change.get('approvalLevel')}")
    status, body = decide(appr, change, "approval")
    check("AC-02", "普通审批人批准 ELEVATED 被拒", code(body) == "RISK_LIMIT_TIER_ELEVATION_REQUIRED", f"HTTP {status} {problem(body)}")
    status, done = decide(elev, change, "elevated-rejection", "REQ-04 验收：驳回")
    check("AC-02", "高级审批人驳回 → REJECTED，档位不变", status == 200 and done.get("status") == "REJECTED"
          and tier_of(ro, hc).get("tier") == "VERIFICATION", f"HTTP {status} {done.get('status')}")
    status, change = request_change(req, hc, "RESTRICTED", "REQ-04 验收：HIGH 高级审批")
    status, done = decide(req, change, "withdrawal")
    check("AC-02", "申请人撤回 → WITHDRAWN", status == 200 and done.get("status") == "WITHDRAWN", f"HTTP {status} {problem(done)}")
    consistent("AC-03", hc, "VERIFICATION")
    row = template(hc, "OBSERVATION")
    check("AC-09", "模板：VERIFICATION 对 OBSERVATION → BELOW_TARGET", row.get("missing") == "LIMIT_TIER_BELOW_TARGET", row)


# ---------------------------------------------------------------------------------------------------------------- check（只读）

def checks(ctx):
    ro = ctx["ro"]
    rows = C.root_ro("mor_risk_decision", (
        "SELECT t.client_id, t.tier, (SELECT GROUP_CONCAT(l.list_code) FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id "
        "WHERE l.list_purpose = 'TIER' AND e.status = 'ACTIVE' AND e.subject_type = 'CLIENT' "
        "AND e.subject_key = CAST(t.client_id AS CHAR) COLLATE utf8mb4_general_ci) lists FROM t_client_limit_tier t"))
    bad = [r for r in rows if r["lists"] != f"TIER_{r['tier']}"]
    check("AC-03", f"全部 {len(rows)} 个档位与名单一致", not bad, bad)
    orphan = C.root_ro("mor_risk_decision", (
        "SELECT e.subject_key FROM t_risk_list_entry e JOIN t_risk_list l ON l.list_id = e.list_id WHERE l.list_purpose = 'TIER' "
        "AND e.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM t_client_limit_tier t WHERE CAST(t.client_id AS CHAR) COLLATE utf8mb4_general_ci = e.subject_key)"))
    check("AC-03", "没有无档位行的 TIER 名单成员", not orphan, orphan)
    status, stats = ro.get(f"{LT}/tier-statistics")
    sql = {r["tier"]: int(r["n"]) for r in C.root_ro("mor_risk_decision", "SELECT tier, COUNT(*) n FROM t_client_limit_tier GROUP BY tier")}
    check("AC-10", "统计与库一致", status == 200 and all((stats.get(t.lower()) or 0) == sql.get(t, 0)
                                                     for t in ("VERIFICATION", "OBSERVATION", "STANDARD", "RESTRICTED")), {"api": stats, "sql": sql})
    totals = C.root_ro("mor_risk_decision", (
        "SELECT x.client_id, x.currency, x.n, x.amt, d.event_count, d.amount FROM (SELECT a.client_id, a.currency, COUNT(*) n, SUM(a.amount) amt "
        "FROM t_transaction_risk_assessment a JOIN t_decision_evaluation e ON e.evaluation_id = a.evaluation_id "
        "WHERE e.business_decision = 'ALLOW' AND DATE(e.evaluation_time) = UTC_DATE() GROUP BY 1, 2) x LEFT JOIN t_client_allowed_daily_total d "
        "ON d.client_id = x.client_id AND d.currency = x.currency AND d.event_code = 'PAYMENT_ATTEMPT_ALLOWED' AND d.event_date = UTC_DATE()"))
    mismatch = [t for t in totals if t["event_count"] == "NULL" or int(t["n"]) != int(t["event_count"]) or Decimal(t["amt"]) != Decimal(t["amount"])]
    check("AC-06", f"今日放行评估与日放行汇总一致（{len(totals)} 组；激活前的旧镜像放行不写汇总时会列为不一致，见报告）", not mismatch, mismatch)
    state = C.load_state("risk-config.json")
    since = (state.get("candidate") or {}).get("PAYMENT_ATTEMPT_RISK", {}).get("activatedAt", "")[:19].replace("T", " ")
    if since:
        timing = C.root_ro("mor_risk_decision", (
            "SELECT p.point_code, e.technical_status, COUNT(*) n, ROUND(AVG(e.elapsed_millis)) avg_ms, MAX(e.elapsed_millis) max_ms "
            "FROM t_decision_evaluation e JOIN t_decision_point p ON p.decision_point_id = e.decision_point_id "
            f"WHERE e.evaluation_time >= '{since}' AND e.evaluation_mode = 'PRIMARY' GROUP BY 1, 2"))
        timed_out = sum(int(t["n"]) for t in timing if t["technical_status"] == "TIMED_OUT")
        check("F-4", f"激活后评估耗时（TIMED_OUT {timed_out}）", timed_out == 0, timing)
    for label, client in CLIENTS.items():
        for target in ("OBSERVATION", "STANDARD"):
            row = template(client, target)
            check("AC-09", f"模板只读试跑 {label} → {target}", set(row) == {"satisfied", "out_tier", "out_observationDue", "missing"}, row)
    for service in ("risk-decision-service", "operations-console-bff"):
        logs = subprocess.run(["docker", "logs", "--since", "60m", f"mor-test-{service}-1"], capture_output=True, text=True)
        text = logs.stdout + logs.stderr
        tier_lines = [l for l in text.splitlines() if "risk-limit-tiers" in l and "HTTP request completed" in l]
        check("G", f"{service} 档位路由日志只记元数据（{len(tier_lines)} 行）", not any("requestBody=" in l and "{" in l.split("requestBody=")[1][:3]
                                                                               for l in tier_lines), len(tier_lines))
        errors = [l[:200] for l in text.splitlines() if " ERROR " in l]
        check("G", f"{service} 近 60 分钟无 ERROR", not errors, errors[:3])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("accounts", "prepare", "api", "check"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    C.require_test()
    if args.command != "check" and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test，需 --yes（且须已获用户阶段 7 授权）")
    ctx = {"admin": C.admin()}
    try:
        if args.command == "accounts":
            accounts(ctx)
        else:
            sessions(ctx, with_rq03=args.command == "prepare")
            {"prepare": prepare, "api": api, "check": checks}[args.command](ctx)
    except OA.Stop as stop:
        record(args.command, "中止", "FAIL", str(stop))
    out = C.write_result(args.command, OA.RESULTS)
    failed = [r for r in OA.RESULTS if r["verdict"] == "FAIL"]
    print(f"PASS {sum(r['verdict'] == 'PASS' for r in OA.RESULTS)} FAIL {len(failed)} SKIP {sum(r['verdict'] == 'SKIP' for r in OA.RESULTS)} → {out.name}")
    return 1 if failed else 0


sys.exit(main())
