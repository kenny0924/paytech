#!/usr/bin/env python3
"""明细账运维 · mor-test 阶段 7 验收（runbook 第 4 节 V-0—V-8 的 API / SQL 部分；浏览器部分见 plops_browser.mortest.mjs）。
由阶段 5 verification/stage5/t6lib.py、t6_readonly.py、t6_rebuild.py 改写：入口 http://localhost:28080（E2E_TARGET=test），
库只用 mor-test-mysql-1 的 root READ ONLY 会话（库名 mor_*），口令只经 MYSQL_PWD 传入、不打印。
S-03：mor-test 不制造不一致；写入只有三项（合成只读角色 + 账号；OE.AE001.USD.0 空追平一次；AE001 · USD 同值重建一次），
后两项由浏览器在 zh-CN-1440（追平）与 en-390（重建）各执行一次，本脚本只做前后读数。
凭据：~/Code-MoR-backups/plops-stage7/accounts.json（0600），不打印。结果写本目录 result-<子命令>-<UTC>.json（不含口令、令牌）。

子命令：
  plan       只读干跑：V-0 健康、V-1 / V-2 进度、V-3 批次浏览与 SQL 对照、语言包 #30、权限 ID、只读角色是否已存在。
  accounts   写（--yes）：IAM 建合成只读角色（只含 FINANCE_ACCOUNTING_READ）与账号 plops-mt-readonly-<时间>@example.test，首登改密。
  readonly   只读 + 403 探测：V-6 只读账号导航、B1 / B2 200、B3 / B4 403（403 不产生写入）。
  snapshot   只读：AE001 · USD 全部 Owner 账户余额与桶金额、ledger 已消费序号、account 流最大序号（--tag before|after）。
  compare    只读：比较 snapshot before / after（逐行）。
  catchup    写（--yes）：V-4 对已一致的 OE.AE001.USD.0 经运营入口（管理员）调用一次 B3。页面在流已一致时不提供"追平"按钮，故走接口。
  cleanup    写（--yes）：经 IAM 停用合成账号；删除合成角色（被拒时改为清空其权限），不删审计。
用法：python3 -I plops_mt.py <plan|accounts|readonly|snapshot|compare|catchup|cleanup> [--yes] [--tag before|after]
"""
import argparse
import base64
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
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as E  # noqa: E402

STATE_DIR = Path.home() / "Code-MoR-backups/plops-stage7"
ACCOUNTS = STATE_DIR / "accounts.json"
OE_BOOK, FE_BOOK = "238137396670066688", "238324787049492480"
STREAM = "OE.AE001.USD.0"
FINANCE_ACCOUNTING_READ = "900000000000097101"
SUBLEDGER_MANAGE = "900000000000148101"
CANDIDATE_ENTRY = "assets/index-CqHRNw-B.js"   # 发布树 dist/index.html 的入口（evidence/ops-web-dist-candidate.sha256）
CONTAINERS = ["mor-test-ledger-service-1", "mor-test-account-service-1", "mor-test-platform-support-service-1",
              "mor-test-operations-console-bff-1", "mor-test-operations-console-web-1"]
RESULTS = []


def now():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def record(v, step, ok, detail=None):
    RESULTS.append({"v": v, "step": step, "ok": bool(ok), "detail": detail})
    print(("PASS " if ok else "FAIL ") + v + " " + step + ("" if detail is None else " " + json.dumps(detail, ensure_ascii=False)[:600]))


def q(sql):
    """root READ ONLY 会话，只允许只读语句。"""
    head = sql.lstrip().split(None, 1)[0].upper()
    if head not in ("SELECT", "SHOW", "WITH"):
        raise SystemExit("q 只允许只读语句")
    pw = E.read_env(MAIN / "test-environment/.env.test")["MOR_MYSQL_ROOT_PASSWORD"]
    out = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                          "--default-character-set=utf8mb4", "--init-command=SET SESSION TRANSACTION READ ONLY", "-e", sql],
                         env={**os.environ, "MYSQL_PWD": pw}, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("SQL 失败：" + out.stderr.strip()[:300])
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def login(email=None, password=None):
    if email is None:
        s = E.secrets()
        email, password = s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]
    return E.Operations(email, password).token


def ops(method, path, token, body=None, key=True, timeout=60):
    """经运营入口（mor-test Nginx 28080 → Gateway → ops-bff）；返回 (状态, JSON, 秒)。429 按 Retry-After 重试。"""
    headers = {"Origin": E.OPERATIONS, "Accept": "application/json", "Authorization": "Bearer " + token}
    data = json.dumps(body).encode() if body is not None else None
    if method != "GET":
        headers["Content-Type"] = "application/json"
        data = data if data is not None else b""
        if key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
    for attempt in range(12):
        req = urllib.request.Request(E.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        t0 = time.monotonic()
        try:
            with urllib.request.urlopen(req, context=E.TLS, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None), time.monotonic() - t0
        except urllib.error.HTTPError as f:
            raw = f.read().decode()
            if f.code == 429 and attempt < 11:
                time.sleep(int(f.headers.get("Retry-After") or 10))
                continue
            try:
                return f.code, json.loads(raw), time.monotonic() - t0
            except ValueError:
                return f.code, raw[:300], time.monotonic() - t0


def nav_codes(token):
    _, nav, _ = ops("GET", "/authorization/v1/navigation", token)
    return sorted(set(re.findall(r"OPERATIONS_FINANCE_POSTING_OPS[A-Z_]*", json.dumps(nav))))


def sql_stream():
    acct = q("SELECT shard_no, last_sequence FROM mor_account.t_account_posting_stream "
             "WHERE entity_type='OPERATING_ENTITY' AND entity_id='AE001' AND currency='USD'")
    led = q(f"SELECT last_version FROM mor_ledger.t_consumed_aggregate_version WHERE aggregate_type='ACCOUNT_POSTING' AND aggregate_id='{STREAM}'")
    return {"accountStreams": acct, "ledgerConsumed": led[0][0] if led else None}


def save(name, extra=None):
    out = HERE / f"result-{name}-{now()}.json"
    out.write_text(json.dumps({"results": RESULTS, **(extra or {})}, ensure_ascii=False, indent=2))
    print("→", out.name, "PASS", sum(r["ok"] for r in RESULTS), "FAIL", sum(not r["ok"] for r in RESULTS))


# ---------------------------------------------------------------- V-0
def v0():
    for c in CONTAINERS:
        out = subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}} {{.Image}}", c], capture_output=True, text=True).stdout.strip()
        record("V-0", f"{c} healthy", out.startswith("healthy"), {"inspect": out[:30]})
    # mor-test Gateway 容器自 13:10Z 重启后 docker logs 无输出（日志不经 stdout），改以 Platform 的 ACTIVE 路由数 + 块 148 路由可达判定
    active = q("SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE status='ACTIVE'")[0][0]
    b148 = q("SELECT route_id, status FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000148401 AND 900000000000148404 ORDER BY route_id")
    log = subprocess.run(["docker", "logs", "--since", "15m", "mor-test-api-gateway-1"], capture_output=True, text=True)
    counts = re.findall(r"routeCount=(\d+)", log.stdout + log.stderr)
    record("V-0", "ACTIVE 路由 675，块 148 四条路由 ACTIVE（Gateway 日志 routeCount 可见时一并记录）",
           active == "675" and len(b148) == 4 and all(r[1] == "ACTIVE" for r in b148),
           {"activeRoutes": active, "block148": b148, "gatewayLogRouteCount": counts[-1:] if counts else "无日志输出"})
    html = urllib.request.urlopen("http://localhost:28080/", timeout=10).read().decode()
    entry = re.findall(r"assets/index-[^\"]*\.js", html)
    record("V-0", "Web 入口为新 dist", entry == [CANDIDATE_ENTRY], {"entry": entry})


# ---------------------------------------------------------------- V-1 / V-2 / V-3
def v123(tok):
    sql = sql_stream()
    acct_max = sql["accountStreams"][0][1] if sql["accountStreams"] else None
    s, b, dt = ops("GET", f"/finance/posting-progress/v1/ledger-books/{OE_BOOK}", tok)
    streams = (b or {}).get("streamSegment", {}).get("streams", []) if s == 200 else []
    oe = next((x for x in streams if x["streamId"] == STREAM), {})
    record("V-1", "B1 OE 账簿 200，三段 CONSISTENT，流 account 最大 = ledger 已消费 = SQL，stuckPosting null",
           s == 200 and b["factSegment"]["status"] == "CONSISTENT" and b["streamSegment"]["status"] == "CONSISTENT"
           and oe.get("status") == "CONSISTENT" and oe.get("accountMaxSequence") == acct_max == sql["ledgerConsumed"] == oe.get("ledgerConsumedSequence")
           and oe.get("stuckPosting") is None,
           {"status": s, "seconds": round(dt, 3), "fact": (b or {}).get("factSegment"), "streams": streams, "sql": sql})
    s2, b2, dt2 = ops("GET", f"/finance/posting-progress/v1/ledger-books/{FE_BOOK}", tok)
    fe_streams = (b2 or {}).get("streamSegment", {}).get("streams", []) if s2 == 200 else []
    fe_sql = q("SELECT DISTINCT entity_no FROM mor_account.t_balance_applied_fact WHERE finance_entity_id=238324786164494336 AND currency='USD'") \
        if s2 == 200 else []
    record("V-2", "B1 FE 账簿 200，流清单 = 服务费所在 OE 流（account 同口径），全部 CONSISTENT",
           s2 == 200 and b2["factSegment"]["status"] == "CONSISTENT" and b2["streamSegment"]["status"] == "CONSISTENT"
           and all(x["status"] == "CONSISTENT" for x in fe_streams),
           {"status": s2, "seconds": round(dt2, 3), "fact": (b2 or {}).get("factSegment"),
            "streams": [{k: x.get(k) for k in ("streamId", "accountMaxSequence", "ledgerConsumedSequence", "status")} for x in fe_streams],
            "sqlServiceChargeEntityNos": fe_sql})
    s3, b3, dt3 = ops("GET", f"/finance/posting-streams/v1/{STREAM}/postings?fromSequence=1&limit=20", tok)
    items = (b3 or {}).get("items", []) if s3 == 200 else []
    db = q("SELECT id, posting_sequence, IFNULL(fact_id,''), source_type FROM mor_account.t_account_posting "
           "WHERE entity_type='OPERATING_ENTITY' AND entity_id='AE001' AND currency='USD' AND shard_no=0 ORDER BY posting_sequence") \
        if s3 == 200 else []
    api_rows = [[x["postingId"], x["postingSequence"], x.get("factId") or "", x["sourceType"]] for x in items]
    record("V-3", "B2 limit=20：条数与 mor_account 一致、逐行相同、nextFromSequence null",
           s3 == 200 and api_rows == db and b3.get("nextFromSequence") is None,
           {"status": s3, "seconds": round(dt3, 3), "count": len(items), "sqlCount": len(db), "next": (b3 or {}).get("nextFromSequence")})
    if items:
        s4, b4, _ = ops("GET", f"/finance/account-postings/v1/{items[0]['postingId']}", tok)
        record("V-3", "批次详情（已有路由）200，流水行存在", s4 == 200 and bool((b4 or {}).get("entries")),
               {"status": s4, "entries": len((b4 or {}).get("entries") or [])})


def i18n_check(tok):
    """与前端相同：按语言请求 manifest（?locale=），匿名；finance 命名空间须含本需求新键。"""
    ok, detail = True, {}
    for code in ("en", "zh-CN"):
        req = urllib.request.Request(f"{E.OPERATIONS}/api/operations/i18n/v1/manifest?locale={code}", headers={"Accept": "application/json"})
        m = json.loads(urllib.request.urlopen(req, context=E.TLS, timeout=10).read().decode())
        url = m.get("bundles", {}).get(code, {}).get("finance", {}).get("url")
        body = json.loads(urllib.request.urlopen(url, context=E.TLS, timeout=10).read().decode()) if url else {}
        keys = sum(1 for k in body if k.startswith("finance.postingOps."))
        detail[code] = {"releaseNo": m.get("releaseNo"), "postingOpsKeys": keys, "rebuildButton": body.get("finance.postingOps.rebuild.button"),
                        "byAt": body.get("finance.postingOps.result.byAt")}
        ok = ok and str(m.get("releaseNo")) == "30" and keys > 0 and "finance.postingOps.result.byAt" in body
    record("V-7", "语言包 #30：en / zh-CN finance 含明细账运维文案", ok, detail)


# ---------------------------------------------------------------- 子命令
def cmd_plan():
    v0()
    tok = login()
    record("V-6", "管理员导航含页面与两个按钮", nav_codes(tok) == ["OPERATIONS_FINANCE_POSTING_OPS", "OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP",
                                                            "OPERATIONS_FINANCE_POSTING_OPS_REBUILD"], {"codes": nav_codes(tok)})
    v123(tok)
    i18n_check(tok)
    perm = q(f"SELECT permission_id, permission_code, status FROM mor_platform.t_permission WHERE permission_id IN ({FINANCE_ACCOUNTING_READ},{SUBLEDGER_MANAGE})")
    record("V-6", "权限 FINANCE_ACCOUNTING_READ / FINANCE_SUBLEDGER_MANAGE 存在且 ACTIVE", len(perm) == 2 and all(p[2] == "ACTIVE" for p in perm),
           {"rows": perm})
    record("V-6", "合成只读账号状态文件", True, {"exists": ACCOUNTS.exists()})
    save("plan")


def cmd_accounts(yes):
    if not yes:
        raise SystemExit("写操作需 --yes")
    if ACCOUNTS.exists():
        raise SystemExit("accounts.json 已存在，不重复创建")
    STATE_DIR.mkdir(mode=0o700, exist_ok=True)
    admin = login()
    stamp = time.strftime("%m%d%H%M%S")
    s, role, _ = ops("POST", "/iam/v1/roles", admin, {"roleCode": f"PLOPS_MT_READONLY_{stamp}", "roleName": f"明细账运维只读 {stamp}",
                                                     "permissionIds": [FINANCE_ACCOUNTING_READ], "reason": "明细账运维阶段 7 只读验收（合成）"})
    record("V-6", "建合成只读角色", s in (200, 201), {"status": s, "roleId": (role or {}).get("roleId"), "permissionIds": (role or {}).get("permissionIds")})
    if s not in (200, 201):
        return save("accounts")
    email = f"plops-mt-readonly-{stamp}@example.test"
    p1, p2 = "Pmt!" + pysecrets.token_urlsafe(18), "Pmt!" + pysecrets.token_urlsafe(18)
    s, acct, _ = ops("POST", "/iam/v1/accounts", admin, {"email": email, "initialPassword": p1, "roleIds": [role["roleId"]],
                                                         "reason": "明细账运维阶段 7 只读验收（合成）"})
    record("V-6", "建合成只读账号", s in (200, 201), {"status": s, "accountId": (acct or {}).get("accountId"), "emailMasked": (acct or {}).get("emailMasked")})
    state = {"email": email, "accountId": acct.get("accountId"), "roleId": role["roleId"], "roleCode": role["roleCode"], "created": now()}
    ACCOUNTS.write_text(json.dumps({**state, "password": p1}, ensure_ascii=False))
    os.chmod(ACCOUNTS, 0o600)
    s, _, _ = ops("POST", "/auth/v1/password", login(email, p1), {"currentPassword": p1, "newPassword": p2}, key=False)
    record("V-6", "首登改密", s == 204, {"status": s})
    if s == 204:
        ACCOUNTS.write_text(json.dumps({**state, "password": p2}, ensure_ascii=False))
        os.chmod(ACCOUNTS, 0o600)
    save("accounts", {"account": state})


def cmd_readonly():
    st = json.loads(ACCOUNTS.read_text())
    ro = login(st["email"], st["password"])
    record("V-6", "只读账号导航只有页面、无执行按钮", nav_codes(ro) == ["OPERATIONS_FINANCE_POSTING_OPS"], {"codes": nav_codes(ro)})
    s1 = ops("GET", f"/finance/posting-progress/v1/ledger-books/{OE_BOOK}", ro)[0]
    s2 = ops("GET", f"/finance/posting-streams/v1/{STREAM}/postings?fromSequence=1&limit=20", ro)[0]
    record("V-6", "只读账号 B1 / B2 200", s1 == 200 and s2 == 200, {"B1": s1, "B2": s2})
    before = sql_stream()
    s3, b3, _ = ops("POST", f"/finance/posting-streams/v1/{STREAM}/catch-up", ro)
    s4, b4, _ = ops("POST", "/finance/posting-rebuilds/v1", ro, {"entityType": "OPERATING_ENTITY", "entityId": "AE001", "currency": "USD"})
    record("V-6", "只读账号 B3 / B4 403", s3 == 403 and s4 == 403,
           {"B3": [s3, (b3 or {}).get("code") if isinstance(b3, dict) else b3], "B4": [s4, (b4 or {}).get("code") if isinstance(b4, dict) else b4]})
    record("V-6", "403 未产生写入（ledger 已消费与 account 流不变）", sql_stream() == before, {"sql": before})
    save("readonly")


def snapshot_rows():
    rows = q("SELECT id, account_no, account_code, owner_type, owner_id, balance_bucket, debit_total, credit_total, net_balance "
             "FROM mor_account.t_owner_account WHERE entity_type='OPERATING_ENTITY' AND entity_id='AE001' AND currency='USD' ORDER BY id")
    meta = q("SELECT id, balance_version, update_time FROM mor_account.t_owner_account "
             "WHERE entity_type='OPERATING_ENTITY' AND entity_id='AE001' AND currency='USD' ORDER BY id")
    entries = q("SELECT a.id, SUM(CASE e.entry_side WHEN 'DEBIT' THEN e.amount ELSE 0 END), SUM(CASE e.entry_side WHEN 'CREDIT' THEN e.amount ELSE 0 END) "
                "FROM mor_account.t_owner_account a JOIN mor_account.t_account_entry e ON e.account_id = a.id "
                "WHERE a.entity_type='OPERATING_ENTITY' AND a.entity_id='AE001' AND a.currency='USD' GROUP BY a.id ORDER BY a.id")
    return {"rows": rows, "meta": meta, "entrySums": entries, "stream": sql_stream(), "at": now()}


def cmd_snapshot(tag):
    STATE_DIR.mkdir(mode=0o700, exist_ok=True)
    snap = snapshot_rows()
    (HERE / f"snapshot-{tag}.json").write_text(json.dumps(snap, ensure_ascii=False, indent=2))
    print(tag, len(snap["rows"]), "accounts; stream", snap["stream"])


def cmd_compare():
    b = json.loads((HERE / "snapshot-before.json").read_text())
    a = json.loads((HERE / "snapshot-after.json").read_text())
    record("V-5", f"重建前后 {len(b['rows'])} 个账户余额 / 桶金额逐行相同", b["rows"] == a["rows"] and len(b["rows"]) > 0,
           {"count": len(b["rows"]), "diff": [[x, y] for x, y in zip(b["rows"], a["rows"]) if x != y]})
    sums_ok = all(r[6] == s[1] and r[7] == s[2] for r, s in zip(a["rows"], a["entrySums"])) if len(a["rows"]) == len(a["entrySums"]) else False
    record("V-5", "重建后余额 = 流水合计（借 / 贷逐账户）", sums_ok, {"accounts": len(a["rows"]), "withEntries": len(a["entrySums"])})
    record("V-5", "版本 / 更新时间变化（仅元数据）", True, {"before": b["meta"], "after": a["meta"]})
    record("V-4", "追平前后 ledger 已消费序号与 account 流最大序号不变", b["stream"] == a["stream"], {"before": b["stream"], "after": a["stream"]})
    save("compare")


def cmd_catchup(yes):
    if not yes:
        raise SystemExit("写操作需 --yes")
    marker = HERE / "catchup-done.json"
    if marker.exists():
        raise SystemExit("已执行过一次追平（catchup-done.json），不重复")
    before = sql_stream()
    tok = login()
    s, b, dt = ops("POST", f"/finance/posting-streams/v1/{STREAM}/catch-up", tok)
    marker.write_text(json.dumps({"status": s, "body": b, "seconds": round(dt, 3), "at": now()}, ensure_ascii=False))
    after = sql_stream()
    record("V-4", "B3 已一致的流：200、processedCount 0、caughtUp true", s == 200 and b.get("processedCount") == "0" and b.get("caughtUp") is True,
           {"status": s, "body": b, "seconds": round(dt, 3)})
    record("V-4", "ledger 已消费序号与 account 流最大序号不变", before == after, {"before": before, "after": after})
    jl = q("SELECT COUNT(*) FROM mor_ledger.t_journal_entry WHERE create_time > UTC_TIMESTAMP(6) - INTERVAL 2 MINUTE")
    record("V-4", "近 2 分钟 mor_ledger 无新分录", jl[0][0] == "0", {"journals": jl[0][0]})
    save("catchup")


def cmd_cleanup(yes):
    if not yes:
        raise SystemExit("写操作需 --yes")
    st = json.loads(ACCOUNTS.read_text())
    admin = login()
    s, page, _ = ops("GET", "/iam/v1/accounts?page=1&size=200", admin)
    acct = next((x for x in (page or {}).get("items", []) if x["accountId"] == st["accountId"]), None)
    if acct and acct["status"] != "DISABLED":
        s, b, _ = ops("POST", f"/iam/v1/accounts/{st['accountId']}/disable", admin,
                      {"expectedVersion": acct["version"], "reason": "明细账运维阶段 7 验收结束，停用合成账号"})
        record("清理", "停用合成只读账号", s == 204, {"status": s, "body": b})
    else:
        record("清理", "停用合成只读账号（已停用或未找到）", acct is not None, {"status": (acct or {}).get("status")})
    s, role, _ = ops("GET", f"/iam/v1/roles/{st['roleId']}", admin)
    version = (role or {}).get("version")
    s, b, _ = ops("DELETE", f"/iam/v1/roles/{st['roleId']}", admin, {"expectedVersion": version, "reason": "明细账运维阶段 7 验收结束，撤销合成角色"})
    if s == 204:
        record("清理", "删除合成角色", True, {"status": s})
    else:
        record("清理", "删除合成角色被拒，改为清空权限", True, {"status": s, "code": (b or {}).get("code") if isinstance(b, dict) else b})
        s2, b2, _ = ops("PUT", f"/iam/v1/roles/{st['roleId']}", admin, {"roleName": role["roleName"], "permissionIds": [],
                                                                       "expectedVersion": version, "reason": "明细账运维阶段 7 验收结束，撤销合成角色权限"})
        record("清理", "合成角色权限清空", s2 == 200 and (b2 or {}).get("permissionIds") == [], {"status": s2, "body": b2 if s2 != 200 else None})
    rows = q(f"SELECT status FROM mor_platform.t_user_account WHERE account_id={int(st['accountId'])}")
    record("清理", "SQL 核对账号状态", bool(rows) and rows[0][0] == "DISABLED", {"rows": rows})
    save("cleanup", {"account": {k: v for k, v in st.items() if k != "password"}})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd")
    ap.add_argument("--yes", action="store_true")
    ap.add_argument("--tag", default="before")
    a = ap.parse_args()
    {"plan": cmd_plan, "readonly": cmd_readonly, "compare": cmd_compare}.get(a.cmd, lambda: None)()
    if a.cmd == "accounts":
        cmd_accounts(a.yes)
    elif a.cmd == "snapshot":
        cmd_snapshot(a.tag)
    elif a.cmd == "catchup":
        cmd_catchup(a.yes)
    elif a.cmd == "cleanup":
        cmd_cleanup(a.yes)


if __name__ == "__main__":
    main()
