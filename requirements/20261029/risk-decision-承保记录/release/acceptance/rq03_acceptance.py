#!/usr/bin/env python3
"""REQ-03 risk-decision 承保记录 · mor-test 阶段 7 验收（API 级，E2E_TARGET=test；运营入口 http://localhost:28080 → Gateway 块 143
→ operations-console-bff → client-master / compliance / Platform → risk-decision）。阶段 5 本机脚本 verification/stage5/stage5_e2e.py 的 mor-test 子集。

数据（阶段 6 只读选定，2026-10-29；都是测试 / 验收合成数据，不碰 DEMO-CL-* 与名称像真实企业的 Client）：
  L 240686705999454209 "Acceptance Trading 1027040243 LLC"：切片 1 mor-test 验收停在四眼前的 AE 机构案件 240686862329565184
    （CRR LOW、推荐 APPROVE、责任 Partner 900000000001370001、MCC 5999 已确认）→ prepare 由合成复核人批准 → 自动承保 → 修订 → 人工批准
  H 241049681294106625 "Refdata AE 1028040509 LLC"：参考数据发布的冒烟机构案件 241049829650845696（SUBMITTED）→ prepare 审完、分类 MCC 5967
    （卡组织高风险）、CRR HIGH、推荐、合成复核人批准 → 承保员提交 → 负责人拒绝 → 负责人重建并提交 → 本人复核 409 → 管理员批准（四眼）
  M 239260386824658945 "Accept Tester"（LEGACY 迁移 Client，CRR 为空）→ 按 MEDIUM 处理：自动承保 409、建草稿、非 HIGH 直接拒绝
子命令（按 runbook ⑪；结果写本目录 result-<子命令>-<UTC>.json，不含口令与令牌）：
  accounts  写：IAM 建 3 个合成角色与账号（uw = READ + MANAGE，lead = + APPROVE，ro = READ；都含 RISK_DECISION_CONFIG_READ 以显示"风控"父菜单），
            凭据 ~/Code-MoR-backups/rq03-stage7/accounts.json（0600）。需 --yes。可重跑（已建则复用）。
  prepare   写：经运营 API 审完并批准 L、H 两个入驻案件（四眼：管理员推荐、切片 1 合成复核人批准；无复核人时由切片 1 脚本新建）。需 --yes。
  api       写：承保场景 P / A / C / E / L / Q（见各函数）。需 --yes。可重跑（已完成的场景跳过）。
  check     只读：工作台、列表、统计、模板只读试跑、库计数核对与日志检查；不写业务数据。
不提供删除：承保记录是审计依据（回滚 SQL 有记录时拒绝）；验收数据清单写在 result，回滚时按 runbook 回滚节人工处理。
用法：E2E_TARGET=test python3 -I rq03_acceptance.py <accounts|prepare|api|check> [--yes]
"""
import argparse
import json
import os
import secrets as ps
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
WORKTREE = HERE.parents[4]
ACCEPTANCE = WORKTREE / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = WORKTREE / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path.home() / "Code-MoR-backups/rq03-stage7"
TEMPLATE = REQ / "db/data-query-templates.sql"
TEST_ENV = Path("/Users/kenny/Desktop/Workspace/Code-MoR/test-environment")
os.environ.setdefault("ONB_STATE", str(Path.home() / "Code-MoR-backups/onboarding-slice1-acceptance"))
sys.path.insert(0, str(V2_E2E))
sys.path.insert(0, str(ACCEPTANCE))
import e2e_http as E  # noqa: E402
import acceptance_flow as V2  # noqa: E402
import onboarding_acceptance as OA  # noqa: E402

OA.E, OA.V2 = E, V2
check, require, record, skip, problem = OA.check, OA.require, OA.record, OA.skip, OA.problem
UW = "/risk-underwriting/v1"
CLIENTS = {"L": "240686705999454209", "H": "241049681294106625", "M": "239260386824658945"}
CASES = {"L": ("240686862329565184", "LOW", None, "900000000001370001"), "H": ("241049829650845696", "HIGH", "5967", None)}
PERMISSIONS = {"READ": "900000000000143101", "MANAGE": "900000000000143102", "APPROVE": "900000000000143103",
               "RISK_MENU": "900000000000000536"}
ROLES = {"uw": ("READ", "MANAGE", "RISK_MENU"), "lead": ("READ", "MANAGE", "APPROVE", "RISK_MENU"), "ro": ("READ", "RISK_MENU")}
CHECK_ORDER = ["RULE_CONFIGURED", "CRR_LOW", "VOLUME_PROVIDED", "CURRENCY_CONFIGURED", "VOLUME_WITHIN_THRESHOLD",
               "MCC_NOT_NETWORK_HIGH_RISK", "MCC_NOT_EXCLUDED", "CLASSIFICATION_CONFIRMED"]
IDS = {}


def root_ro(database, statement):
    """mor-test root 只读会话（SET SESSION TRANSACTION READ ONLY），返回行字典列表。"""
    pw = next(line.split("=", 1)[1] for line in (TEST_ENV / ".env.test").read_text().splitlines()
              if line.startswith("MOR_MYSQL_ROOT_PASSWORD="))
    out = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", "mor-test-mysql-1", "mysql", "-uroot", "-B",
                          "--default-character-set=utf8mb4", database],
                         input="SET SESSION TRANSACTION READ ONLY;\n" + statement, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:300])
    lines = [line for line in out.stdout.splitlines() if line]
    head = lines[0].split("\t") if lines else []
    return [dict(zip(head, line.split("\t"))) for line in lines[1:]]


def template(client_id):
    text = TEMPLATE.read_text().split("-- template ", 1)[1].split("\n", 1)[1]
    return root_ro("mor_risk_decision", text.replace("#{clientId}", str(int(client_id))))[0]


def decision(**overrides):
    body = {"observationDays": 30, "observationTransactions": 100, "requireDeposit": False, "depositAmount": None,
            "depositCurrency": None, "depositBlocksCollection": False, "suggestedSettlementDays": None, "highRiskMcc": False,
            "highRiskMccReason": None, "limitNote": None, "underwritingNote": "REQ-03 mor-test 验收", "handoverNote": None}
    body.update(overrides)
    return body


def code(body):
    return body.get("code") if isinstance(body, dict) else None


def ws(session, client_id):
    status, body = session.get(f"{UW}/clients/{client_id}/workspace")
    require("WS", f"工作台 {client_id}", status == 200, f"HTTP {status} {problem(body) if status != 200 else body.get('inputStatus')}")
    return body


def admin():
    s = E.secrets()
    return OA.ops_login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def saved_accounts():
    target = STATE / "accounts.json"
    return json.loads(target.read_text()) if target.exists() else {}


def login_accounts(ctx):
    saved = saved_accounts()
    for label in ROLES:
        if label not in saved:
            raise OA.Stop(f"缺少账号 {label}：先执行 accounts --yes")
        ctx[label] = OA.ops_login(saved[label]["email"], saved[label]["password"])
        ctx[label].account_id = ctx[label].account_id or saved[label]["accountId"]


# ---------------------------------------------------------------------------------------------------------------- accounts

def cmd_accounts(ctx):
    saved, stamp = saved_accounts(), time.strftime("%m%d%H%M%S")
    for label, grants in ROLES.items():
        if label in saved:
            record("S", f"{label} 已存在，复用", "INFO", saved[label]["accountId"])
            continue
        status, role = ctx["admin"].call("POST", "/iam/v1/roles", {
            "roleCode": f"RQ03_ACCEPTANCE_{label.upper()}_{stamp}", "roleName": f"REQ-03 承保验收 {label} {stamp}（合成）",
            "permissionIds": [PERMISSIONS[g] for g in grants], "reason": "REQ-03 mor-test 承保验收：合成账号"})
        require("S", f"IAM 建角色 {label}", status in (200, 201), f"HTTP {status} {problem(role)}")
        role_id = role.get("roleId") or role.get("objectId")
        email = f"rq03-acceptance-{label}-{stamp}@example.test"
        p1, p2 = "Rq!" + ps.token_urlsafe(18), "Rq!" + ps.token_urlsafe(18)
        status, account = ctx["admin"].call("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                                         "reason": "REQ-03 mor-test 承保验收：合成账号"})
        require("S", f"IAM 建账号 {label}", status in (200, 201), f"HTTP {status} {problem(account)}")
        first = OA.ops_login(email, p1)
        status, _ = first.call("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        require("S", f"{label} 首登改密", status in (200, 204), f"HTTP {status}")
        saved[label] = {"email": email, "password": p2, "accountId": account.get("accountId"), "roleId": role_id,
                        "permissions": list(grants)}
        STATE.mkdir(parents=True, exist_ok=True)
        os.chmod(STATE, 0o700)
        (STATE / "accounts.json").write_text(json.dumps(saved, indent=1) + "\n")
        os.chmod(STATE / "accounts.json", 0o600)
        record("S", f"{label} 已建", "PASS", {"accountId": saved[label]["accountId"], "roleId": role_id})


# ---------------------------------------------------------------------------------------------------------------- prepare

def classify(ops, case_id, mcc):
    detail = OA.case_detail(ops, case_id)
    child = next(c for category in detail.get("industries") or [] for c in category.get("children") or [])
    status, reply = OA.command(ops, "PUT", f"/cases/{case_id}/classifications",
                               {"items": [{"industryCode": child["industryCode"], "mccCode": mcc or child.get("defaultMccCode") or "5999",
                                           "primary": True}]})
    check("PREP", f"{case_id} 设置分类 MCC {mcc}", status == 200, f"HTTP {status} {problem(reply)}")
    status, reply = OA.command(ops, "POST", f"/cases/{case_id}/classification-confirmation", None)
    check("PREP", f"{case_id} 确认分类", status == 200, f"HTTP {status} {problem(reply)}")


def cmd_prepare(ctx):
    ops = ctx["admin"]
    reviewer = OA.reviewer({"opsA": ops, "run": time.strftime("%m%d%H%M%S"),
                            "args": argparse.Namespace(env="mor-test", create_reviewer=True)})
    for label, (case_id, rating, mcc, partner) in CASES.items():
        review = OA.review_of(OA.case_detail(ops, case_id))
        if review.get("status") == "APPROVED":
            record("PREP", f"{label} 案件已批准", "INFO", case_id)
            continue
        OA.wait_screening(ops, case_id)
        OA.accept_and_pass(ops, "PREP", case_id, partner, label)
        if mcc or not root_ro("mor_compliance", f"SELECT case_id FROM t_dd_case_classification WHERE case_id={int(case_id)} "
                                                "AND confirmed_time IS NOT NULL"):
            classify(ops, case_id, mcc)
            time.sleep(3)  # 确认分类后立刻评 CRR 偶发 409 CONCURRENT_REQUEST（compliance 既有行为）
        if review.get("riskRating") != rating:
            OA.rate(ops, case_id, partner, label, rating)
        if review.get("recommendation") != "APPROVE":
            status, reply = OA.command(ops, "POST", f"/cases/{case_id}/recommendation",
                                       {"recommendation": "APPROVE", "note": "REQ-03 mor-test 验收", "onBehalfOfPartnerId": partner})
            check("PREP", f"{label} 推荐 APPROVE", status == 200, f"HTTP {status} {problem(reply)}")
        blockers = OA.review_of(OA.case_detail(reviewer, case_id)).get("approvalBlockers") or []
        status, reply = OA.command(reviewer, "POST", f"/cases/{case_id}/approval",
                                   {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "REQ-03 mor-test 验收四眼", "onBehalfOfPartnerId": partner})
        require("PREP", f"{label} 合成复核人批准", status == 200, f"HTTP {status} {problem(reply)} blockers={blockers}")


# ---------------------------------------------------------------------------------------------------------------- api

def s_permissions(ctx):
    ro, uw, client = ctx["ro"], ctx["uw"], CLIENTS["M"]
    status, _ = ro.get(f"{UW}/underwritings?page=1&size=10")
    check("AC-11", "只读：列表 200", status == 200, f"HTTP {status}")
    body = ws(ro, client)
    check("AC-11", "只读：工作台 canManage=false", body.get("canManage") is False, body.get("canManage"))
    status, body = ro.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    check("AC-11", "只读：建草稿 403", status == 403, f"HTTP {status} {problem(body)}")
    status, body = uw.call("POST", f"{UW}/underwritings/1/review", {"expectedVersion": 0, "decision": "APPROVE", "comment": None})
    check("AC-11", "承保员（无 APPROVE）：复核 403", status == 403, f"HTTP {status} {problem(body)}")


def s_low_auto_revision(ctx):
    uw, lead, client = ctx["uw"], ctx["lead"], CLIENTS["L"]
    current = ws(uw, client)
    preview = current.get("preview") or {}
    record("AC-02", "L 工作台输入与预览", "INFO", {"inputStatus": current.get("inputStatus"), "sources": current.get("sources"),
                                               "autoEligible": preview.get("autoEligible"),
                                               "failed": [c["code"] for c in preview.get("checks") or [] if not c["passed"]],
                                               "threshold": [preview.get("thresholdAmount"), preview.get("thresholdCurrency")]})
    check("AC-02", "预览 8 项固定顺序、规则 v1", [c["code"] for c in preview.get("checks") or []] == CHECK_ORDER
          and preview.get("ruleVersionNo") == 1, preview.get("ruleVersionNo"))
    effective = (current.get("current") or {}).get("effective")
    if effective and effective.get("versionNo") == 2:
        skip("AC-07", "L 自动承保与修订", f"已完成（v2 {effective['underwritingId']}）")
        return
    if not preview.get("autoEligible"):
        skip("AC-02", "L 一键自动承保", "不符合自动承保（见上方未通过项）；改走人工 LOW 提交")
        status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
        require("AC-03", "L 建草稿", status == 201, f"HTTP {status} {problem(draft)}")
        status, done = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                               {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                                "decision": decision(suggestedSettlementDays=7)})
        require("AC-03", "L 提交即批准", status == 200 and done.get("status") == "APPROVED", f"HTTP {status} {problem(done)}")
        auto = done
    else:
        status, auto = uw.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
        require("AC-02", "L 一键自动承保 201：AUTO_RULE、规则 v1、无提交人", status == 201 and auto.get("decisionSource") == "AUTO_RULE"
                and auto.get("submittedBy") is None, f"HTTP {status} {problem(auto)}")
    IDS["L_v1"] = auto["underwritingId"]
    status, draft = uw.call("POST", f"{UW}/underwritings/{auto['underwritingId']}/revision",
                            {"expectedVersion": auto["version"], "revisionReason": "REQ-03 mor-test 验收：补充 T+N"})
    require("AC-07", "L 修订 → DRAFT v2", status == 201 and draft.get("versionNo") == 2, f"HTTP {status} {problem(draft)}")
    status, done = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                           {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                            "decision": decision(suggestedSettlementDays=7)})
    require("AC-07", "L v2 批准，supersedes = v1", status == 200 and done.get("status") == "APPROVED"
            and done.get("supersedesUnderwritingId") == auto["underwritingId"], f"HTTP {status} {problem(done)}")
    IDS["L_v2"] = done["underwritingId"]
    status, history = lead.get(f"{UW}/clients/{client}/underwritings")
    check("AC-07", "L 历史 v2 APPROVED / v1 SUPERSEDED",
          [(i.get("versionNo"), i.get("status")) for i in history.get("items") or []] == [(2, "APPROVED"), (1, "SUPERSEDED")],
          [(i.get("versionNo"), i.get("status")) for i in history.get("items") or []])


def s_high_four_eyes(ctx):
    uw, lead, admin_, client = ctx["uw"], ctx["lead"], ctx["admin"], CLIENTS["H"]
    current = ws(uw, client)
    inp = current.get("input") or {}
    check("AC-06", "H 工作台：HIGH、MCC 5967 卡组织高风险", (current.get("preview") or {}).get("reviewLevel") == "HIGH"
          and inp.get("networkHighRiskMcc") is True, {"reviewLevel": (current.get("preview") or {}).get("reviewLevel"),
                                                      "mcc": inp.get("mccCode"), "inputStatus": current.get("inputStatus")})
    if (current.get("current") or {}).get("effective"):
        skip("AC-08", "H 四眼", "已有有效版本")
        return
    open_ = (current.get("current") or {}).get("open")
    if not open_:
        status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
        require("AC-06", "H 建草稿", status == 201, f"HTTP {status} {problem(draft)}")
        status, body = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                               {"expectedVersion": draft["version"], "outcome": "DECLINE", "declineReason": "x",
                                "decision": decision(highRiskMcc=True)})
        check("AC-06", "H 提交 DECLINE 409 DECLINE_REQUIRES_REVIEW", status == 409
              and code(body) == "RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW", f"HTTP {status} {problem(body)}")
        status, sub = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                              {"expectedVersion": draft["version"], "outcome": "APPROVE", "declineReason": None,
                               "decision": decision(highRiskMcc=True, requireDeposit=True, depositAmount="20000", depositCurrency="USD",
                                                    suggestedSettlementDays=14)})
        require("AC-06", "H 承保员提交 → SUBMITTED", status == 200 and sub.get("status") == "SUBMITTED", f"HTTP {status} {problem(sub)}")
        check("AC-10", "模板：待复核", template(client).get("missing") == "UNDERWRITING_PENDING_REVIEW", template(client))
        status, declined = lead.call("POST", f"{UW}/underwritings/{sub['underwritingId']}/review",
                                     {"expectedVersion": sub["version"], "decision": "DECLINE", "comment": "REQ-03 验收：拒绝路径"})
        require("AC-06", "H 负责人拒绝 → DECLINED", status == 200 and declined.get("status") == "DECLINED", f"HTTP {status} {problem(declined)}")
        IDS["H_v1"] = declined["underwritingId"]
        status, again = lead.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": decision(highRiskMcc=True)})
        require("AC-06", "H 负责人重建 v2", status == 201 and again.get("versionNo") == 2, f"HTTP {status} {problem(again)}")
        status, open_ = lead.call("POST", f"{UW}/underwritings/{again['underwritingId']}/submission",
                                  {"expectedVersion": again["version"], "outcome": "APPROVE", "declineReason": None,
                                   "decision": decision(highRiskMcc=True, requireDeposit=True, depositAmount="20000",
                                                        depositCurrency="USD", depositBlocksCollection=True, suggestedSettlementDays=14)})
        require("AC-06", "H 负责人本人提交 → SUBMITTED", status == 200 and open_.get("status") == "SUBMITTED", f"HTTP {status} {problem(open_)}")
    status, body = lead.call("POST", f"{UW}/underwritings/{open_['underwritingId']}/review",
                             {"expectedVersion": open_["version"], "decision": "APPROVE", "comment": None})
    check("AC-08", "H 提交人本人复核 409 SELF_REVIEW", status == 409 and code(body) == "RISK_UNDERWRITING_SELF_REVIEW", f"HTTP {status} {problem(body)}")
    status, approved = admin_.call("POST", f"{UW}/underwritings/{open_['underwritingId']}/review",
                                   {"expectedVersion": open_["version"], "decision": "APPROVE", "comment": "REQ-03 验收：四眼批准"})
    require("AC-08", "H 管理员复核批准 → APPROVED", status == 200 and approved.get("status") == "APPROVED"
            and approved.get("decidedBy") != approved.get("submittedBy"), f"HTTP {status} {problem(approved)}")
    IDS["H_v2"] = approved["underwritingId"]
    row = template(client)
    check("AC-10", "模板：H 要求保证金且阻断收款", row.get("satisfied") == "1" and row.get("out_requireDeposit") == "1"
          and row.get("out_depositBlocksCollection") == "1" and row.get("out_highRiskMcc") == "1", row)


def s_crr_missing(ctx):
    uw, client = ctx["uw"], CLIENTS["M"]
    current = ws(uw, client)
    record("AC-09", "M 工作台（LEGACY 迁移 Client）", "INFO", {"inputStatus": current.get("inputStatus"), "sources": current.get("sources"),
                                                         "crr": (current.get("input") or {}).get("crr"),
                                                         "reviewLevel": (current.get("preview") or {}).get("reviewLevel")})
    if current.get("inputStatus") != "READY":
        skip("AC-09", "M CRR 为空路径", f"inputStatus={current.get('inputStatus')}：遗留 Client 输入不全，只记录现状")
        return
    if (current.get("current") or {}).get("latest"):
        skip("AC-09", "M CRR 为空路径", "已有记录")
        return
    status, body = uw.call("POST", f"{UW}/clients/{client}/auto-underwritings", None, json_empty=True)
    check("AC-09", "M 自动承保 409 AUTO_NOT_ELIGIBLE", status == 409 and code(body) == "RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE",
          f"HTTP {status} {problem(body)}")
    status, draft = uw.call("POST", f"{UW}/clients/{client}/underwritings", {"decision": None})
    require("AC-09", "M 建草稿：crrMissing、reviewLevel MEDIUM", status == 201 and (draft.get("input") or {}).get("crrMissing") is True
            and draft.get("reviewLevel") == "MEDIUM", f"HTTP {status} {problem(draft)}")
    status, declined = uw.call("POST", f"{UW}/underwritings/{draft['underwritingId']}/submission",
                               {"expectedVersion": draft["version"], "outcome": "DECLINE", "declineReason": "REQ-03 验收：尽调未评 CRR",
                                "decision": decision()})
    require("AC-09", "M 非 HIGH 直接拒绝 → DECLINED", status == 200 and declined.get("status") == "DECLINED", f"HTTP {status} {problem(declined)}")
    IDS["M_v1"] = declined["underwritingId"]


def cmd_api(ctx):
    login_accounts(ctx)
    for name, fn in (("P", s_permissions), ("A", s_low_auto_revision), ("C", s_high_four_eyes), ("E", s_crr_missing)):
        print(f"\n==== {name} {fn.__name__}", flush=True)
        try:
            fn(ctx)
        except OA.Stop as stop:
            record(name, "场景中止", "FAIL", str(stop))
    cmd_check(ctx)


# ---------------------------------------------------------------------------------------------------------------- check

def cmd_check(ctx):
    ops = ctx["admin"]
    db = {r["status"]: int(r["n"]) for r in root_ro("mor_risk_decision", "SELECT status, COUNT(*) n FROM t_client_underwriting GROUP BY status")}
    effective = int(root_ro("mor_risk_decision", "SELECT COUNT(*) n FROM t_client_underwriting WHERE effective_slot=1")[0]["n"])
    latest = int(root_ro("mor_risk_decision", "SELECT COUNT(DISTINCT client_id) n FROM t_client_underwriting")[0]["n"])
    status, stats = ops.get(f"{UW}/underwriting-statistics")
    check("AC-12", "统计与库一致", status == 200 and stats.get("effective") == effective and stats.get("draft") == db.get("DRAFT", 0)
          and stats.get("submitted") == db.get("SUBMITTED", 0), {"api": stats, "db": db, "effective": effective})
    status, page = ops.get(f"{UW}/underwritings?page=1&size=50")
    check("AC-12", "全部 = 每个 Client 最新版本", status == 200 and page.get("total") == latest, {"total": page.get("total"), "clients": latest})
    for st in ("SUPERSEDED", "DECLINED"):
        status, page = ops.get(f"{UW}/underwritings?status={st}&page=1&size=50")
        check("AC-12", f"{st} 页签 = 库中该状态行数", status == 200 and page.get("total") == db.get(st, 0), {"api": page.get("total"), "db": db.get(st, 0)})
    for label, client in CLIENTS.items():
        record("AC-10", f"模板只读试跑 {label}", "INFO", template(client))
    rule = root_ro("mor_risk_decision", "SELECT rule_version_id, version_no, status, active_slot FROM t_underwriting_rule_version")
    check("AC-01", "规则 v1 ACTIVE", rule == [{"rule_version_id": "290001", "version_no": "1", "status": "ACTIVE", "active_slot": "1"}], rule)
    started = ctx.get("since") or "30m"
    for svc in ("risk-decision-service", "operations-console-bff"):
        logs = subprocess.run(["docker", "logs", "--since", started, f"mor-test-{svc}-1"], capture_output=True, text=True)
        text = logs.stdout + logs.stderr
        names = ["Acceptance Trading", "Refdata AE", "Accept Tester"]
        check("AC-12", f"{svc} 日志不含 Client 名称（承保只记元数据）", not any(n in text for n in names), [n for n in names if n in text])
        errors = [line[:200] for line in text.splitlines() if " ERROR " in line]
        record("AC-12", f"{svc} 最近日志 ERROR 行", "PASS" if not errors else "INFO", errors[:3])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("accounts", "prepare", "api", "check"))
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--since", help="check 读容器日志的起点（docker logs --since），缺省 30m")
    args = parser.parse_args()
    if E.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）；本机见 verification/stage5/stage5_e2e.py")
    if args.command != "check" and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test，需 --yes（且须已获用户阶段 7 授权）")
    started = datetime.now(timezone.utc)
    ctx = {"admin": admin(), "since": args.since}
    try:
        {"accounts": cmd_accounts, "prepare": cmd_prepare, "api": cmd_api, "check": cmd_check}[args.command](ctx)
    except OA.Stop as stop:
        record(args.command, "中止", "FAIL", str(stop))
    summary = {v: sum(1 for r in OA.RESULTS if r.get("verdict") == v) for v in ("PASS", "FAIL", "SKIP", "INFO")}
    out = HERE / f"result-{args.command}-{started.strftime('%Y-%m-%dT%H%M%SZ')}.json"
    out.write_text(json.dumps({"startedAt": started.isoformat(), "clients": CLIENTS, "ids": IDS, "summary": summary,
                               "results": OA.RESULTS}, ensure_ascii=False, indent=1) + "\n")
    print("\nSUMMARY", summary, out.name)
    return 1 if summary["FAIL"] else 0


sys.exit(main())
