#!/usr/bin/env python3
"""REQ-02 渠道身份就绪查询 · mor-test 阶段 7 验收（API 级，E2E_TARGET=test；W3 阶段 6 生成，只做过语法检查与只读子命令干跑）。

链路：运营 http://localhost:28080/api/operations/commercial-admin/v1/**（Nginx → Gateway → ops-bff → commercial → client-master / channel-connector）
      就绪查询 / 经营分类为服务间接口（不经 Gateway）：经接入 mor-test-backend 的一次性探针容器私网直连（e2e_http.private_call，
      E2E_PROBE_IMAGE 缺省用现网 commercial 镜像 806043a3，含 JDK），请求头 X-Caller-Service: commercial-service（与真实调用方相同）。
数据：只用现有 AE001 DEMO 合同（DEMO-CL-001 / DEMO2-CL-101 / DEMO2-CL-103 等 7 个 Client，主 MCC 均为 5999）与 AE001 的 MID；
      预期以 sql/01-precheck-capabilities-to-suspend.sql 的预测为准（脚本内同一查询现场重算，不读旧文件）。
合成数据（只在 prepare 中创建，cleanup 生成清理草稿，不删除不可删的业务记录）：
  * 运营账号 r02mt-nomid-<时间>@example.test：角色只含 COMMERCIAL_CONTRACT_READ（无 CHANNEL_CONFIG_READ → 无 MID 菜单），供浏览器 AC-V2。
  不改任何渠道 / MID / 能力配置（主会话裁决：不为验收虚构渠道能力；AC-07 不在 mor-test 演练）。
预期暂停（主会话裁决，用户审批时决定）：DEMO2-CL-101 CARD（只有 AED 价格，AED 无 MID）、DEMO-CL-001 APPLE_PAY（AE001 无该能力）——
  check 对这 2 条按"预期暂停"判定：commercial 仍为现网镜像时 ENABLED 且预测 UNSATISFIED 即通过；候选上线后须为 SUSPENDED_BY_PREREQUISITE。

子命令（结果写本目录 result-<子命令>-<UTC>.json，不含口令与令牌）：
  plan      只读：现网四个镜像（发布前 = 回滚 ID，发布后 = 候选 ID）；巡检预测（同 sql/01）；AE001 MID 能力行；GOLIVE_MID_READY 模板状态；
            合成账号 / 能力行状态文件。发布前就绪查询 / 经营分类接口应为 404（只记录，不判失败）。
  prepare   写（--yes）：建无 MID 菜单运营账号（首登改密）。
  api       写（--yes）：
            R  就绪查询矩阵（只读接口）：AE001 CARD×USD×5999 ready；APPLE_PAY → PAYMENT_METHOD_NOT_SUPPORTED；CARD×AED×5999 →
               CURRENCY_NOT_SUPPORTED；CARD×USD×1234 → MCC_NOT_COVERED；CARD×USD×5816 →
               PAYMENT_ACTION_NOT_SUPPORTED；SA001 → MID_NOT_FOUND；同组重复同结论；非法入参 422 VALIDATION_ERROR。
            C  经营分类：7 个 DEMO Client confirmed + 5999；不存在 404；ID 非法 400。
            G  AC-06 / AC-08 / AC-12：对每份有支付方式开通项的 AE001 合同 POST …/prerequisite-checks（与"重新检查前置"按钮相同），
               ④ 结果 / 原因 / 参数（entityNo、paymentMethodCode、mccCode、currencies）与现场预测一致，执行状态与逐项结论一致；
               partial 场景（Q-4 修订）：DEMO-CL-001 / DEMO2-CL-103 的 CARD（AED + USD，只有 USD 有 MID）为 SATISFIED +
               CHANNEL_IDENTITY_PARTIAL、currencies=AED 且保持 ENABLED；DEMO2-CL-101 CARD（只有 AED）与 DEMO-CL-001 APPLE_PAY 暂停。
            Q  AC-11（仅模板已登记）：GOLIVE_MID_READY 经 /data-query/v1/templates/GOLIVE_MID_READY/executions 执行 5 组，
               satisfied / out_missing_methods / missing 与 R 的就绪结论一致；不传 currencies 时结论与空串相同（防 NULL 误判，见 W3 报告）。
  check     只读：开通项执行状态 = 现场预测；commercial 自本次 api 开始无 ERROR / "discovery is unavailable"；commercial 补偿事件无 FAILED。
  cleanup   prepare 的反操作：生成运营账号停用 SQL 草稿（不执行）。
用法：python3 -I req02_acceptance.py <plan|prepare|api|check|cleanup> [--yes]
"""
import argparse
import json
import os
import secrets as pysecrets
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728")
HERE = Path(__file__).resolve().parent
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
TEST_ENV = MAIN / "test-environment"
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as E  # noqa: E402

STATE_DIR = Path.home() / "Code-MoR-backups/req02-stage7"
STATE_FILE = STATE_DIR / "state.json"
PRECHECK_SQL = HERE.parent / "sql/01-precheck-capabilities-to-suspend.sql"
ENTITY, MCC = "AE001", "5999"
EXPECTED_SUSPENSIONS = {("DEMO2-CL-101", "CARD"), ("DEMO-CL-001", "APPLE_PAY")}  # 主会话裁决：预期暂停，用户审批时决定
READY_PATH = "/channel-connector/v1/channel-identities/readiness"
CLASSIFICATION_PATH = "/client-master/v1/clients/{}/business-classification"
CALLER = {"X-Caller-Service": "commercial-service"}
PERM_CONTRACT_READ = "900000000000095101"
LIVE = {"channel-connector-service": ("sha256:23679d451fe44dfce08426777b5ec7cbc73a60253c604f149dfc48291c3890aa",
                                      "sha256:f330c4ae93bfee95438ed9df924bbf7a8905d57fa3139b8043454c02463d47e1"),
        "client-master-service": ("sha256:2b12c2e2184bb9e926c5fb6a74822c103ba5a659a53dcbacb9a4d6bb60c414bc",
                                  "sha256:df13cc47d936b51abaa766bafa30df8ca8f1776677f46b831656b87a54d7ef1f"),
        "commercial-service": ("sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728",
                               "sha256:2be844e7a163ed47dc5ecce840a9cee263958e1938c568878ea49da22342431f"),
        "operations-console-web": ("sha256:5784fa817c471ebce707821dfd4db0727dd28cbe073776e620fb48a9f5d800b4",
                                   "sha256:bbc03d33854e65dadbea4e5754e4fb7ef4aea3e49d4297d23fa1493b7310d486")}
RESULTS, DATA = [], {}


def expected(pred):
    """预测的 (结果, 原因)；Q-4 修订：任一已覆盖币种就绪即 SATISFIED，部分缺口为 CHANNEL_IDENTITY_PARTIAL。"""
    return pred["predictedResult"], pred["predictedReason"]


def now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def record(ac, step, ok, detail=None):
    RESULTS.append({"ac": ac, "step": step, "ok": bool(ok), "detail": detail})
    text = json.dumps(detail, ensure_ascii=False, default=str) if detail is not None else ""
    print(("PASS " if ok else "FAIL ") + f"[{ac}] {step} " + text[:300], flush=True)
    return ok


def root_sql(statement, database=""):
    """mor-test root 只读会话（READ ONLY；口令只经 MYSQL_PWD 环境变量），返回制表符分隔的行；可含多条语句。"""
    pw = E.read_env(TEST_ENV / ".env.test")["MOR_MYSQL_ROOT_PASSWORD"]
    args = ["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
            "--default-character-set=utf8mb4", "--init-command=SET SESSION TRANSACTION READ ONLY"] + ([database] if database else [])
    out = subprocess.run(args, input=statement, env={**os.environ, "MYSQL_PWD": pw}, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("SQL 失败：" + out.stderr.strip()[:300])
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def load_state():
    return json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else {}


def save_state(state):
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE_DIR, 0o700)
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=1) + "\n")
    os.chmod(STATE_FILE, 0o600)


def admin():
    s = E.secrets()
    return E.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def attempt(fn):
    try:
        return 200, fn()
    except E.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except (ValueError, TypeError):
            return failure.status, {"raw": str(failure.body)[:200]}


def predictions():
    """现场重算 sql/01 的第一段（逐开通项预测）：{agreement_capability_id: row}。"""
    first = PRECHECK_SQL.read_text().split("-- 汇总", 1)[0]
    cols = ["agreementNo", "id", "clientId", "entityNo", "capabilityCode", "paymentMethodCode", "executionStatus", "mccCode",
            "coveredCurrencies", "predictedResult", "predictedReason", "notReadyCurrencies", "impact"]
    rows = [dict(zip(cols, [None if v == "NULL" else v for v in r])) for r in root_sql(first)]
    return {r["id"]: r for r in rows}


def readiness(entity, items):
    status, text = E.private_call("channel-connector", "POST", READY_PATH, CALLER, {"entityNo": entity, "items": items})
    try:
        return status, json.loads(text)
    except ValueError:
        return status, {"raw": text[:200]}


# ---------------------------------------------------------------- plan
def plan(state):
    for svc, (rollback, candidate) in LIVE.items():
        live = subprocess.run(["docker", "inspect", f"mor-test-{svc}-1", "--format", "{{.Image}}"], capture_output=True, text=True).stdout.strip()
        stage = "发布前" if live == rollback else "发布后" if live == candidate else "未知"
        record("plan", f"{svc} 现网镜像 = 回滚或候选 ID（{stage}）", stage != "未知", live[7:19])
    preds = predictions()
    DATA["predictions"] = list(preds.values())
    record("plan", "巡检预测（同 sql/01）", True, {p["agreementNo"] + "/" + p["capabilityCode"]: p["impact"] for p in preds.values()})
    caps = root_sql("SELECT m.mid_id, m.mid_status, c.mid_capability_id, c.payment_method_code, c.currency, c.mcc_code, c.capability_status "
                    "FROM mor_channel_connector.t_channel_mid m LEFT JOIN mor_channel_connector.t_mid_capability c ON c.mid_id = m.mid_id "
                    f"WHERE m.entity_no = '{ENTITY}' ORDER BY 1, 3")
    DATA["midCapabilities"] = caps
    record("plan", f"{ENTITY} MID 能力行", True, caps)
    template = root_sql("SELECT status, current_version_no FROM mor_platform.t_query_template WHERE template_code = 'GOLIVE_MID_READY'")
    record("plan", "GOLIVE_MID_READY 模板（阶段 7 登记后应 ACTIVE / 1）", True, template or "未登记")
    status, body = readiness(ENTITY, [{"paymentMethodCode": "CARD", "currency": "USD", "mccCode": MCC}])
    record("plan", "就绪查询接口（发布前 404 / 发布后 200）", True, {"status": status, "body": body})
    status, _ = E.private_call("client-master", "GET", CLASSIFICATION_PATH.format("237575207592763393"), CALLER)
    record("plan", "经营分类接口（发布前 404 / 发布后 200）", True, {"status": status})
    record("plan", "合成数据状态文件", True, {"file": str(STATE_FILE), "keys": sorted(state)})


# ---------------------------------------------------------------- prepare
def prepare(state, args):
    ops = admin()
    if "nomid" not in state:
        stamp = time.strftime("%m%d%H%M%S")
        role = ops.send("POST", "/iam/v1/roles", {"roleCode": f"R02MT_NOMID_{stamp}", "roleName": f"REQ-02 验收 无 MID 菜单 {stamp}",
                                                  "permissionIds": [PERM_CONTRACT_READ], "reason": "REQ-02 渠道身份就绪 mor-test 验收"})
        role_id = role.get("roleId") or role.get("objectId")
        email = f"r02mt-nomid-{stamp}@example.test"
        p1, p2 = "R02!" + pysecrets.token_urlsafe(18), "R02!" + pysecrets.token_urlsafe(18)
        account = ops.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                        "reason": "REQ-02 渠道身份就绪 mor-test 验收"})
        E.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        state["nomid"] = {"email": email, "password": p2, "accountId": account.get("accountId"), "roleId": role_id}
        save_state(state)
    record("prepare", "无 MID 菜单运营账号就绪（只含 COMMERCIAL_CONTRACT_READ）", True, {"accountId": state["nomid"]["accountId"]})


# ---------------------------------------------------------------- api
def readiness_matrix(state):
    cases = [("CARD", "USD", MCC, True, None), ("APPLE_PAY", "USD", MCC, False, "PAYMENT_METHOD_NOT_SUPPORTED"),
             ("CARD", "AED", MCC, False, "CURRENCY_NOT_SUPPORTED"),
             ("CARD", "USD", "1234", False, "MCC_NOT_COVERED"), ("CARD", "USD", "5816", False, "PAYMENT_ACTION_NOT_SUPPORTED"),
             ("CARD", "USD", MCC, True, None)]
    status, body = readiness(ENTITY, [{"paymentMethodCode": p, "currency": c, "mccCode": m} for p, c, m, _, _ in cases])
    items = body.get("items") or []
    record("R", "AE001 批量 6 组 200、逐组回显", status == 200 and len(items) == len(cases), {"status": status})
    for (p, c, m, ready, reason), item in zip(cases, items):
        record("R", f"{p} × {c} × {m} → ready={ready} reason={reason}", (item.get("ready"), item.get("reasonCode")) == (ready, reason), item)
    DATA["readiness"] = items
    status, body = readiness("SA001", [{"paymentMethodCode": "CARD", "currency": "USD", "mccCode": MCC}])
    record("R", "SA001 → MID_NOT_FOUND", status == 200 and (body.get("items") or [{}])[0].get("reasonCode") == "MID_NOT_FOUND", body)
    status, body = readiness(ENTITY, [{"paymentMethodCode": "CARD", "currency": "*", "mccCode": MCC}])
    record("R", "币种 * 非法 → 422 VALIDATION_ERROR", status == 422 and body.get("code") == "VALIDATION_ERROR", {"status": status, "code": body.get("code")})


def classification(preds):
    for client_id in sorted({p["clientId"] for p in preds.values()}):
        status, text = E.private_call("client-master", "GET", CLASSIFICATION_PATH.format(client_id), CALLER)
        body = json.loads(text) if status == 200 else {}
        record("C", f"Client {client_id} 经营分类 confirmed + {MCC}", status == 200 and body.get("confirmed") is True
               and body.get("mccCode") == MCC, {"status": status, "mcc": body.get("mccCode"), "confirmed": body.get("confirmed")})
    status, _ = E.private_call("client-master", "GET", CLASSIFICATION_PATH.format("999999999999999999"), CALLER)
    record("C", "不存在的 Client → 404", status == 404, status)
    status, _ = E.private_call("client-master", "GET", CLASSIFICATION_PATH.format("not-a-number"), CALLER)
    record("C", "Client ID 非法 → 400", status == 400, status)


def gate_checks(ops, preds, label="G"):
    agreements = root_sql("SELECT DISTINCT ac.agreement_id, a.agreement_no FROM mor_commercial.t_agreement_capability ac "
                          "JOIN mor_commercial.t_agreement a ON a.agreement_id = ac.agreement_id "
                          f"WHERE ac.agreement_capability_id IN ({','.join(preds) or '0'}) ORDER BY 2")
    for agreement_id, agreement_no in agreements:
        status, result = attempt(lambda: ops.send("POST", f"/commercial-admin/v1/agreements/{agreement_id}/prerequisite-checks", None))
        if not record(label, f"{agreement_no} 检查前置 200", status == 200, {"status": status}):
            continue
        for cap in result.get("capabilities") or []:
            pred = preds.get(str(cap.get("agreementCapabilityId")))
            if pred is None:
                continue
            item = next((i for i in cap.get("checks") or [] if i.get("code") == "CHANNEL_IDENTITY"), {})
            params = item.get("params") or {}
            want = expected(pred)
            ok = (item.get("result"), item.get("reasonCode")) == want
            if ok and want[1] not in (None, "CLIENT_MCC_MISSING", "AWAITING_PRICE"):
                ok = (params.get("entityNo"), params.get("paymentMethodCode"), params.get("mccCode"), params.get("currencies")) == (
                    pred["entityNo"], pred["paymentMethodCode"], pred["mccCode"], pred["notReadyCurrencies"])
            if want[1] == "CHANNEL_IDENTITY_PARTIAL":
                record(label, f"{agreement_no} {pred['capabilityCode']} partial：满足但提醒缺 MID 币种 {pred['notReadyCurrencies']}，执行状态保持 ENABLED",
                       item.get("result") == "SATISFIED" and params.get("currencies") == pred["notReadyCurrencies"]
                       and cap.get("executionStatus") == "ENABLED", {"item": item, "executionStatus": cap.get("executionStatus")})
            record(label, f"{agreement_no} {pred['capabilityCode']} ④ = 预测{want[0]}/{want[1]}"
                          f"（{pred['notReadyCurrencies']}）", ok, item)
            all_ok = all(i.get("result") in ("SATISFIED", "SKIPPED") for i in cap.get("checks") or [])
            record(label, f"{agreement_no} {pred['capabilityCode']} 执行状态与逐项结论一致（{cap.get('executionStatus')}）",
                   cap.get("executionStatus") == ("ENABLED" if all_ok else ("SUSPENDED_BY_PREREQUISITE" if pred["executionStatus"]
                                                                          in ("ENABLED", "SUSPENDED_BY_PREREQUISITE") else "PENDING_PREREQUISITES")),
                   {"executionStatus": cap.get("executionStatus")})


def golive(ops):
    if not root_sql("SELECT 1 FROM mor_platform.t_query_template WHERE template_code = 'GOLIVE_MID_READY' AND status = 'ACTIVE'"):
        record("Q", "GOLIVE_MID_READY 未登记，跳过 AC-11（登记后重跑 api）", True)
        return
    cases = [({"entityNo": ENTITY, "paymentMethods": "CARD,APPLE_PAY", "mccCode": MCC, "currencies": "USD"}, ("0", "APPLE_PAY", "PAYMENT_METHOD_NOT_READY")),
             ({"entityNo": ENTITY, "paymentMethods": "CARD", "mccCode": MCC, "currencies": ""}, ("1", "", None)),
             ({"entityNo": ENTITY, "paymentMethods": "CARD", "mccCode": MCC}, ("1", "", None)),
             ({"entityNo": ENTITY, "paymentMethods": "CARD,APPLE_PAY", "mccCode": MCC}, ("0", "APPLE_PAY", "PAYMENT_METHOD_NOT_READY")),
             ({"entityNo": ENTITY, "paymentMethods": "CARD", "mccCode": MCC, "currencies": "USD,AED"}, ("1", "", None)),
             ({"entityNo": "SA001", "paymentMethods": "CARD", "mccCode": MCC, "currencies": ""}, ("0", "CARD", "MID_NOT_FOUND"))]
    for params, (sat, missing_methods, missing) in cases:
        status, body = attempt(lambda: ops.send("POST", "/data-query/v1/templates/GOLIVE_MID_READY/executions",
                                                {"params": params, "withTotal": False}))
        row = ((body.get("rows") or [{}])[0]) if isinstance(body, dict) else {}
        if isinstance(row, list):  # data-query 执行结果按列顺序返回行数组
            row = dict(zip(("satisfied", "out_missing_methods", "missing"), row))
        got = (str(row.get("satisfied")), row.get("out_missing_methods") or "", row.get("missing"))
        record("Q", f"GOLIVE_MID_READY {params} → {sat}/{missing_methods or '∅'}/{missing}", status == 200 and got == (sat, missing_methods, missing),
               {"status": status, "row": row})


def api(state, args):
    ops = admin()
    preds = predictions()
    DATA["startedUtc"] = now()
    state["apiStartedUtc"] = DATA["startedUtc"]
    save_state(state)
    readiness_matrix(state)
    classification(preds)
    gate_checks(ops, preds)
    golive(ops)


# ---------------------------------------------------------------- check / cleanup
def check(state):
    preds = predictions()
    live = subprocess.run(["docker", "inspect", "mor-test-commercial-service-1", "--format", "{{.Image}}"], capture_output=True, text=True).stdout.strip()
    released = live == LIVE["commercial-service"][1]
    predicted = {(p["agreementNo"], p["capabilityCode"]) for p in preds.values()
                 if p["executionStatus"] in ("ENABLED", "SUSPENDED_BY_PREREQUISITE") and p["predictedResult"] == "UNSATISFIED"}
    record("check", f"预测暂停集合 = 预期 {sorted(EXPECTED_SUSPENSIONS)}", predicted == EXPECTED_SUSPENSIONS, sorted(predicted))
    for p in preds.values():
        key = (p["agreementNo"], p["capabilityCode"])
        if key in EXPECTED_SUSPENSIONS:
            want = "SUSPENDED_BY_PREREQUISITE" if released else "ENABLED"
            record("check", f"{key[0]} {key[1]} 预期暂停（{p['predictedReason']}，{'候选已上线' if released else '发布前'} → {want}）",
                   p["executionStatus"] == want and p["predictedResult"] == "UNSATISFIED", p)
        elif p["executionStatus"] == "ENABLED":
            record("check", f"{p['agreementNo']} {p['capabilityCode']} 仍 ENABLED 且预测满足（{p['predictedReason'] or '无缺口'}）",
                   expected(p)[0] == "SATISFIED", p)
        else:
            record("check", f"{p['agreementNo']} {p['capabilityCode']} {p['executionStatus']}（④ 预测 {p['predictedResult']}/{p['predictedReason']}）", True)
    since = state.get("apiStartedUtc")
    logs = subprocess.run(["docker", "logs", "--since", since or "30m", "mor-test-commercial-service-1"], capture_output=True, text=True)
    text = logs.stdout + logs.stderr
    bad = [line[:200] for line in text.splitlines() if " ERROR " in line or "discovery is unavailable" in line]
    record("check", f"commercial 自 {since or '30m'} 起无 ERROR / 发现失败", not bad, bad[:5])
    failed = root_sql("SELECT COUNT(*) FROM mor_commercial.t_compensation_event WHERE execute_status = 'FAILED'")
    record("check", "commercial 补偿事件 FAILED = 0", failed and failed[0][0] == "0", failed)


def cleanup(state, args):
    if "nomid" in state:
        draft = HERE / f"cleanup-{now().replace(':', '')}.sql"
        draft.write_text("-- REQ-02 验收合成运营账号停用草稿（不自动执行；主会话审阅后经运营控制台停用或按 runbook 执行）\n"
                         f"-- 账号 {state['nomid']['email']}（accountId {state['nomid']['accountId']}）、角色 {state['nomid']['roleId']}\n"
                         f"SELECT account_id, status FROM mor_platform.t_user_account WHERE account_id = {int(state['nomid']['accountId'])};\n")
        record("cleanup", "运营账号停用草稿已生成（未执行）", True, draft.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("plan", "prepare", "api", "check", "cleanup"))
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    if E.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")
    if args.command in ("prepare", "api"):
        if not args.yes:
            raise SystemExit(f"{args.command} 会写 mor-test，需 --yes（且须已获用户阶段 7 授权）")
    state = load_state()
    {"plan": lambda: plan(state), "prepare": lambda: prepare(state, args), "api": lambda: api(state, args),
     "check": lambda: check(state), "cleanup": lambda: cleanup(state, args)}[args.command]()
    out = {"command": args.command, "at": now(), "results": RESULTS, "data": DATA,
           "pass": sum(r["ok"] for r in RESULTS), "fail": sum(not r["ok"] for r in RESULTS)}
    target = HERE / f"result-{args.command}-{out['at'].replace(':', '')}.json"
    target.write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"DONE {args.command} pass={out['pass']} fail={out['fail']} → {target.name}")
    return 1 if out["fail"] else 0


if __name__ == "__main__":
    sys.exit(main())
