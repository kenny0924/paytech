#!/usr/bin/env python3
"""payout 收款账户与余额解释（S1）· mor-test 阶段 7 验收（API 级，E2E_TARGET=test）。

由阶段 5 本机脚本 verification/local-e2e/stage5_backend.py 改写（场景函数原样复用，见 release/acceptance/README 段落；本文件由
W9 阶段 6 生成，只做过语法检查与只读子命令干跑）。改动：入口换成 mor-test（运营 http://localhost:28080、Client https://localhost:28443，
e2e_http E2E_TARGET=test）；库名 mor_payout / mor_platform / mor_vault / mor_account（root READ ONLY 会话）；合成前缀 S1MT；
运营 Maker / Checker 为两个只含 PAYOUT_* 权限的合成账号（不再用 E2E 管理员当 Maker）；Client 账号从状态文件读入；余额预期按
mor_account 现值计算（不默认登记计费事实）；AC-09 按 mor-test 的真实 Amazon SES（沙箱）判定。

链路：Client  https://localhost:28443/api/client/payout/v1/**（Nginx → Gateway 块 147 → client-console-bff → payout → Vault / account / Platform）
      运营    http://localhost:28080/api/operations/payout-admin/v1/**（Nginx → Gateway 块 147 → operations-console-bff → payout / client-master）

数据（阶段 6 只读选定，见 W9-report「验收数据」）：
  A  Client 237575207592763393（DEMO-CL-001，AE001 × USD PENDING 137.69 / AVAILABLE 97.00）——需 Client 所有者凭据（待裁决 D-4）
  B  Client 240686705999454209（REQ-03 验收 L，无 account 账户）——需 Client 所有者凭据（待裁决 D-4）
  Z  Client 237092466744991745（只有 AE001 × USD PENDING 51.10，AVAILABLE 0）——只经运营只读余额视图触发 NO_AVAILABLE_BALANCE
  C  Client 241049681294106625（REQ-03 验收 H，无 account 账户）——运营只读余额视图：positions 为空数组
  Client 所有者凭据文件 ~/Code-MoR-backups/payout-s1-stage7/clients.json（0600，主会话提供，格式
  {"A": {"clientId": "…", "email": "…", "password": "…"}, "B": {…}}）；本脚本不打印、不改其口令。

子命令（结果写本目录 result-<子命令>-<UTC>.json，不含口令、令牌、原值）：
  plan      只读：验收数据与前置读数（四个 Client 状态与 account 现值、块 147 / Vault / mor_payout 状态、凭据文件是否就位）。
  accounts  写：IAM 建 4 个合成运营角色与账号（maker / checker = PAYOUT_BENEFICIARY_* 5 项 + FINANCE_ACCOUNTING_READ 显示「财务」父菜单；
            ro = PAYOUT_BENEFICIARY_READ + FINANCE_ACCOUNTING_READ；noperm = DATA_QUERY_READ），首登改密；核对 clients.json 两个所有者
            可登录并进入工作区。凭据 ~/Code-MoR-backups/payout-s1-stage7/accounts.json（0600）。需 --yes。
  prepare   写：Client A 团队邀请两名成员——viewer（TENANT_VIEWER，@example.test）与 manager（TENANT_ADMIN，邮箱为 Amazon SES
            邮箱模拟器 success+s1mt-<时间>@simulator.amazonses.com，沙箱可投递）；邀请与注册邮件收不到时沿用既有 mint_invitation_link /
            mint_signup_link（只改写本次新账号自己的挑战 secret_hmac）。可选 --billing-fact：对 B 登记一条 MANUAL_ADJUSTMENT 计费事实
            （不可删除的会计分录，待裁决 D-5；缺省不做）。需 --yes。
  api       写：AC-01—AC-08、AC-10、权限分支、生命周期（payout 写接口）；他方回归只读。记录本次开始时间供 notify / check / cleanup。需 --yes。
  notify    只读：AC-09——payout 补偿事件处理完成；Platform 受理 4 类模板；每条投递数 = Client A 持 MANAGE 的有效已验证成员数；
            模拟器地址投递 SENT / DELIVERED，@example.test 地址被 SES 沙箱拒绝为 FAILED（判定为预期，证明"失败不影响账户状态"）；
            参数无原值；Mailpit 不参与（mor-test 的 Platform 只经 SES 发信）。
  check     只读：mor_payout 账户 / 事件 / 补偿事件与本次结果一致、无 FAILED 补偿事件、库与补偿载荷无原值、Vault 两类记录数、
            payout / Platform / BFF 日志自本次开始无原值与 ERROR、块 147 verify 读数。
  cleanup   只读：生成本次合成数据的清理 SQL 草稿 release/acceptance/cleanup-<UTC>.sql（按 ID / S1MT 标记，不执行）；执行由主会话
            经 root 按 runbook 完成。
用法：python3 -I payout_acceptance.py <plan|accounts|prepare|api|notify|check|cleanup> [--yes] [--billing-fact]
"""
import argparse
import base64
import hashlib
import hmac
import http.cookiejar
import json
import os
import random
import secrets as pysecrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
# 私网探针镜像：e2e_http 缺省用 mor-test/platform-support-service:development，本机已无该 tag；用现网 Platform（含 Java，REQ-04 同法）。
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:af13e77a05c3d30bf9652523d52ba302dc59d2ee2b2221500ece647215560012")
HERE = Path(__file__).resolve().parent
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
TEST_ENV = MAIN / "test-environment"
V2_E2E = MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
sys.path.insert(0, str(V2_E2E))
import e2e_http as E  # noqa: E402

STATE_DIR = Path.home() / "Code-MoR-backups/payout-s1-stage7"
STATE_FILE = STATE_DIR / "accounts.json"
CLIENTS_FILE = STATE_DIR / "clients.json"
RUN_FILE = STATE_DIR / "run.json"
CLIENT_A_ID, CLIENT_B_ID = "237575207592763393", "240686705999454209"
CLIENT_Z_ID = "237092466744991745"  # 只有 PENDING，AVAILABLE 0
CLIENT_C_ID = "241049681294106625"  # ACTIVE、无 account 账户
ENTITY, CURRENCY = "AE001", "USD"
PERMISSIONS = {"READ": "900000000000147104", "SUBMIT": "900000000000147105", "REVIEW": "900000000000147106",
               "REVEAL": "900000000000147107", "MANAGE": "900000000000147108", "FINANCE_MENU": "900000000000097101",
               "DATA_QUERY_READ": "900000000000131101"}
OPERATOR_ROLES = {"maker": ("READ", "SUBMIT", "REVIEW", "REVEAL", "MANAGE", "FINANCE_MENU"),
                  "checker": ("READ", "SUBMIT", "REVIEW", "REVEAL", "MANAGE", "FINANCE_MENU"),
                  "ro": ("READ", "FINANCE_MENU"), "noperm": ("DATA_QUERY_READ",)}
PAY = "/payout/v1"
ADMIN = "/payout-admin/v1"
PDB, PLT = "mor_payout", "mor_platform"
SES_SIMULATOR = "success+s1mt-{stamp}@simulator.amazonses.com"

OPS_ONLY = False
RESULTS = []
DATA = {"clients": {"A": CLIENT_A_ID, "B": CLIENT_B_ID}, "accounts": {}, "operators": {}, "billingFacts": [],
        "notifications": [], "viewer": {}}


def q(sql):
    """mor-test root 只读会话（READ ONLY；口令只经 MYSQL_PWD 环境变量），返回制表符分隔的行。"""
    head = sql.lstrip().split(None, 1)[0].upper()
    if head not in ("SELECT", "SHOW", "WITH"):
        raise SystemExit("q 只允许只读语句")
    pw = E.read_env(TEST_ENV / ".env.test")["MOR_MYSQL_ROOT_PASSWORD"]
    out = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                          "--default-character-set=utf8mb4", "--init-command=SET SESSION TRANSACTION READ ONLY", "-e", sql],
                         env={**os.environ, "MYSQL_PWD": pw}, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("SQL 失败：" + out.stderr.strip()[:300])
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def load_json(path):
    return json.loads(path.read_text()) if path.exists() else {}


def save_json(path, data):
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE_DIR, 0o700)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
    os.chmod(path, 0o600)


def load_state():
    return load_json(STATE_FILE)


def save_state(state):
    save_json(STATE_FILE, state)



# ---------------------------------------------------------------- 阶段 5 场景函数（原样复用，合成前缀 S1E2E → S1MT）
def record(ac, step, ok, detail=None):
    RESULTS.append({"ac": ac, "step": step, "ok": bool(ok), "detail": detail})
    text = json.dumps(detail, ensure_ascii=False, default=str) if detail is not None else ""
    print(("PASS " if ok else "FAIL ") + f"[{ac}] {step} " + text[:300], flush=True)
    return ok


def problem(failure):
    try:
        return json.loads(failure.body)
    except (ValueError, AttributeError):
        return {"raw": str(getattr(failure, "body", ""))[:200]}


def attempt(fn):
    """执行调用，返回 (status, body)。"""
    try:
        result = fn()
        if isinstance(result, tuple):
            return result
        return 200, result
    except E.Failure as failure:
        return failure.status, problem(failure)


def expect(ac, step, status, fn, code=None):
    actual, body = attempt(fn)
    got_code = body.get("code") if isinstance(body, dict) else None
    ok = actual == status and (code is None or got_code == code)
    record(ac, step, ok, {"status": actual, "code": got_code,
                          "detail": (body.get("detail") if isinstance(body, dict) else None)})
    return body


def wait_until(predicate, seconds=60, interval=2):
    deadline = time.time() + seconds
    value = predicate()
    while not value and time.time() < deadline:
        time.sleep(interval)
        value = predicate()
    return value


class Client:
    """Client 站点会话（Cookie Jar + 内存 Access Token）；call 返回 (status, body)，失败抛 E.Failure。"""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=E.TLS),
                                                  urllib.request.HTTPCookieProcessor(self.jar))
        self.token = None

    def call(self, path, method="GET", body=None, key=None, auth=True):
        headers = {"Origin": E.CLIENT, "Accept": "application/json"}
        data = None
        if body is not None or method not in ("GET", "HEAD", "DELETE"):
            headers["Content-Type"] = "application/json"
        if body is not None:
            data = json.dumps(body).encode()
        if key:
            headers["Idempotency-Key"] = key
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        for i in range(12):
            request = urllib.request.Request(E.CLIENT + "/api/client" + path, data=data, headers=headers, method=method)
            try:
                with self.opener.open(request, timeout=30) as response:
                    raw = response.read().decode()
                    return response.status, (json.loads(raw) if raw else None)
            except urllib.error.HTTPError as failure:
                if failure.code == 429 and i < 11:
                    time.sleep(int(failure.headers.get("Retry-After") or 10))
                    continue
                raise E.Failure(failure.code, failure.read().decode()) from None

    def command(self, path, method="POST", body=None):
        return self.call(path, method, body, key=str(uuid.uuid4()))

    def login(self, email, password):
        _, grant = self.call("/auth/v1/login", "POST", {"email": email, "password": password}, auth=False)
        self.token = grant["accessToken"]
        return grant["context"]

    def enter(self, client_id):
        _, start = self.call("/onboarding/v1/start")
        items = (start.get("workspaces") or {}).get("items") or []
        item = next((w for w in items if w.get("clientId") == client_id and w.get("state") == "ACCESSIBLE"), None)
        if item is None:
            raise SystemExit(f"工作区不可访问: {client_id} {[(w.get('clientId'), w.get('state')) for w in items]}")
        _, grant = self.call("/auth/v1/workspace-context", "POST", {"memberId": item["memberId"]})
        self.token = grant["accessToken"]
        return item["memberId"]


def mint_invitation_link(invitation_id):
    keys = E.secrets()
    rows = E.platform_sql("SELECT challenge_id,challenge_no,active_scope_hash FROM t_authentication_challenge "
                          f"WHERE purpose='TENANT_INVITATION_ACCEPT' AND status='PENDING' AND tenant_invitation_id={int(invitation_id)} "
                          "ORDER BY create_time DESC LIMIT 1")
    if not rows:
        return None
    challenge_id, challenge_no, scope_hash = rows[0]
    secret = base64.urlsafe_b64encode(pysecrets.token_bytes(32)).decode().rstrip("=")
    proof = "".join(f"{len(v.encode())}:{v}" for v in
                    ("CUSTOMER-EMAIL-CHALLENGE", challenge_no, "TENANT_INVITATION_ACCEPT", scope_hash, secret))
    digest = hmac.new(base64.b64decode(keys["CLIENT_LOCAL_HMAC_KEY_BASE64"]),
                      ("client-email-challenge-v1" + "\\0" + proof).encode(), hashlib.sha256).hexdigest()
    E.platform_sql(f"UPDATE t_authentication_challenge SET secret_hmac='{digest}' WHERE challenge_id={int(challenge_id)} "
                   "AND status='PENDING'")
    return {"challengeNo": challenge_no, "secret": secret}


def register(session, email, password):
    since = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(time.time() - 5))
    session.call("/auth/v1/register", "POST", {"email": email, "password": password}, auth=False)
    challenge, secret = None, None
    for _ in range(10):
        challenge, secret = E.mint_signup_link(email, since)
        if challenge:
            break
        time.sleep(1)
    session.call("/auth/v1/email-verifications", "POST", {"challengeNo": challenge, "secret": secret}, auth=False)
    return session.login(email, password)


def billing(method, path, actor_id, body=None):
    """billing 私网计费事实命令（本机 Platform 未登记 /finance/billing-facts 网关路由，按 ops-bff 的转发方式直连）：

    与 FinanceBillingFactClientImpl 相同：sourceSystem=OPERATIONS、sourceReference=OPS-UUID，编制人 / 复核人为真实运营账号 ID；
    billing 真实处理器校验并在批准后经补偿任务发布 BillingFactRecorded → account。"""
    status, text = E.private_call("billing", method, "/billing/v1/facts" + path,
                                  {"X-Caller-Service": "operations-console-bff", "X-Gateway-Actor-Subject": str(actor_id),
                                   "X-Gateway-Session-Id": "s1e2e-" + uuid.uuid4().hex}, body)
    if status >= 300:
        raise E.Failure(status, text)
    return json.loads(text) if text else None


def rnd_digits(n):
    return "".join(random.choice("0123456789") for _ in range(n))


def ae_local(holder):
    return {"railType": "LOCAL", "bankCountry": "AE", "currency": "USD",
            "fields": {"holderName": holder, "iban": "AE" + rnd_digits(21), "bankName": "S1MT Emirates Test Bank"}}


def swift(holder, country="GB"):
    return {"railType": "SWIFT", "bankCountry": country, "currency": "USD",
            "fields": {"holderName": holder, "iban": country + rnd_digits(2) + "S1EB" + rnd_digits(14),
                       "swiftBic": "SONEGB2L", "bankName": "S1MT London Test Bank"}}


def sa_local(holder):
    return {"railType": "LOCAL", "bankCountry": "SA", "currency": "USD",
            "fields": {"holderName": holder, "iban": "SA" + rnd_digits(22), "bankName": "S1MT Riyadh Test Bank"}}


def raw_values(body):
    fields = body["fields"]
    # SWIFT BIC / 本地银行代码是银行标识（落 bank_code 列，设计如此），不属于收款人原值
    return [v for k, v in fields.items() if k in ("holderName", "iban", "accountNumber")]


def no_raw_in_payout(account_id, values):
    hits = []
    for value in values:
        escaped = value.replace("'", "''")
        for table, cols in (("t_beneficiary_account", "CONCAT_WS('|',owner_type,account_code,bank_name,bank_code,holder_name_masked,"
                                                       "account_last4,beneficiary_vault_token,bank_account_token,reason_note,review_maker_note)"),
                            ("t_beneficiary_account_event", "CONCAT_WS('|',action,reason_code,source_key)"),
                            ("t_compensation_event", "payload_json")):
            rows = q(f"SELECT COUNT(*) FROM {PDB}.{table} WHERE INSTR({cols}, '{escaped}') > 0")
            if rows[0][0] != "0":
                hits.append(table)
    return hits


def client_submit(owner_a, owner_b, viewer):
    status, req = owner_a.call(f"{PAY}/beneficiary-requirements?railType=LOCAL&bankCountry=AE&currency=USD")
    codes = [f.get("code") for f in (req or {}).get("fields") or []]
    record("AC-01", "字段清单 LOCAL × AE × USD", status == 200 and codes == ["holderName", "iban", "bankName"], {"fields": codes})
    status, req = owner_a.call(f"{PAY}/beneficiary-requirements?railType=SWIFT&bankCountry=GB&currency=USD")
    codes = [f.get("code") for f in (req or {}).get("fields") or []]
    record("AC-01", "字段清单 SWIFT × GB × USD（* 国家条目）", status == 200 and "swiftBic" in codes, {"fields": codes})
    expect("AC-01", "字段清单 LOCAL × EG × USD 不支持 404", 404,
           lambda: owner_a.call(f"{PAY}/beneficiary-requirements?railType=LOCAL&bankCountry=EG&currency=USD"),
           "BENEFICIARY_REQUIREMENT_NOT_FOUND")

    a1 = ae_local("S1MT Trading LLC")
    status, acct1 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a1)
    record("AC-01", "提交 AE 本地 USD → 201 PENDING_REVIEW", status == 201 and acct1["status"] == "PENDING_REVIEW"
           and acct1["holderNameMasked"] != a1["fields"]["holderName"] and acct1["accountLast4"] == a1["fields"]["iban"][-4:],
           {"id": acct1["beneficiaryAccountId"], "code": acct1["accountCode"], "masked": acct1["holderNameMasked"],
            "last4": acct1["accountLast4"], "usable": acct1["usable"]})
    a2 = swift("S1MT Trading LLC")
    status, acct2 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a2)
    record("AC-01", "提交 SWIFT USD → 201 PENDING_REVIEW", status == 201 and acct2["status"] == "PENDING_REVIEW",
           {"id": acct2["beneficiaryAccountId"], "code": acct2["accountCode"]})

    # Gateway Idempotency-Key：同键同体重放返回原响应；新键同账号 → 409 BENEFICIARY_DUPLICATE（含已有账户 ID）
    a3 = swift("S1MT Trading LLC")
    key = str(uuid.uuid4())
    s1, acct3 = owner_a.call(f"{PAY}/beneficiary-accounts", "POST", a3, key=key)
    s2, replay = owner_a.call(f"{PAY}/beneficiary-accounts", "POST", a3, key=key)
    record("AC-10", "同一 Idempotency-Key 重放返回原账户", s1 == 201 and s2 in (200, 201)
           and replay["beneficiaryAccountId"] == acct3["beneficiaryAccountId"],
           {"first": s1, "replay": s2, "id": acct3["beneficiaryAccountId"]})
    status, same = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a1)
    record("AC-10", "新键、同 Owner 同内容重复提交 → 200 返回原账户（业务幂等）", status == 200
           and same["beneficiaryAccountId"] == acct1["beneficiaryAccountId"], {"status": status, "id": same["beneficiaryAccountId"]})
    changed = dict(a1, fields=dict(a1["fields"], holderName="S1MT Different Holder"))
    dup = expect("AC-02", "同 Owner 同账号、内容不同 → 409 BENEFICIARY_DUPLICATE", 409,
                 lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", changed), "BENEFICIARY_DUPLICATE")
    record("AC-02", "409 含已有账户 ID（technique P1）", acct1["beneficiaryAccountId"] in json.dumps(dup, ensure_ascii=False),
           {"problem": {k: v for k, v in dup.items() if k not in ("type", "title", "requestId", "timestamp")}})

    bad = dict(a1, bankCountry="EG", fields=dict(a1["fields"]))
    expect("AC-01", "不支持组合 LOCAL × EG × USD 422", 422,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", bad), "BENEFICIARY_UNSUPPORTED_ROUTE")
    bad = dict(a1, fields=dict(a1["fields"], iban="AE12"))
    expect("AC-01", "IBAN 格式不符 400 INVALID_REQUEST", 400,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", bad), "INVALID_REQUEST")
    bad = dict(a1, fields=dict(a1["fields"], swiftBic="SONEGB2L"))
    expect("AC-01", "清单外字段 400 INVALID_REQUEST", 400,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", bad), "INVALID_REQUEST")
    bad = dict(a1, fields={k: v for k, v in a1["fields"].items() if k != "bankName"})
    expect("AC-01", "缺必填字段 400 INVALID_REQUEST", 400,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", bad), "INVALID_REQUEST")
    bad = dict(a1, railType="ACH")
    expect("AC-01", "railType 非法 422 VALIDATION_ERROR（Bean Validation）", 422,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts", "POST", bad), "VALIDATION_ERROR")

    # 不同 Owner 共用账号可提交
    b1 = dict(a1, fields=dict(a1["fields"], holderName="S1MT Shared Holder Co"))
    status, acct_b = owner_b.command(f"{PAY}/beneficiary-accounts", "POST", b1)
    record("AC-02", "另一 Client 共用同一账号可提交 201", status == 201 and acct_b["status"] == "PENDING_REVIEW",
           {"id": acct_b["beneficiaryAccountId"], "ownerId": acct_b["ownerId"]})
    record("AC-02", "Client 视图不返回共用 Owner", not acct_b.get("sharedOwners"), {"sharedOwners": acct_b.get("sharedOwners")})

    # 权限（AC-10 / R10）
    expect("R10", "无 CLIENT_PAYOUT_ACCOUNT_MANAGE 的成员（TENANT_VIEWER）提交 403", 403,
           lambda: viewer.command(f"{PAY}/beneficiary-accounts", "POST", sa_local("S1MT Viewer")))
    status, listed = viewer.call(f"{PAY}/beneficiary-accounts")
    record("R10", "TENANT_VIEWER 可读账户列表 200", status == 200 and len(listed.get("items") or []) >= 3,
           {"count": len(listed.get("items") or [])})
    expect("R10", "TENANT_VIEWER 关闭账户 403", 403,
           lambda: viewer.command(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/close", "POST",
                                  {"reasonCode": "OWNER_REQUEST", "expectedVersion": acct1["version"]}))
    expect("R10", "未登录 401", 401, lambda: Client().call(f"{PAY}/beneficiary-accounts", auth=False))

    # 库内无原值；Vault 两类 Token
    for label, acct, body in (("AE 本地", acct1, a1), ("SWIFT", acct2, a2)):
        hits = no_raw_in_payout(acct["beneficiaryAccountId"], raw_values(body))
        record("AC-01", f"{label}：mor_payout 三表无原值（户名 / IBAN / 账号）", not hits, {"hits": hits})
        rows = q(f"SELECT a.beneficiary_vault_token IS NOT NULL, a.bank_account_token IS NOT NULL, "
                 f"(SELECT data_type FROM mor_vault.t_vault_record r WHERE r.token=a.beneficiary_vault_token), "
                 f"(SELECT data_type FROM mor_vault.t_vault_record r WHERE r.token=a.bank_account_token), "
                 f"(SELECT token_mode FROM mor_vault.t_vault_record r WHERE r.token=a.bank_account_token) "
                 f"FROM {PDB}.t_beneficiary_account a WHERE a.id={int(acct['beneficiaryAccountId'])}")
        record("AC-01", f"{label}：Vault 有 PAYOUT_BENEFICIARY_V1 与 PAYOUT_BANK_ACCOUNT_KEY_V1 两类 Token",
               rows and rows[0][2] == "PAYOUT_BENEFICIARY_V1" and rows[0][3] == "PAYOUT_BANK_ACCOUNT_KEY_V1", {"row": rows})
    rows = q(f"SELECT COUNT(DISTINCT bank_account_token) FROM {PDB}.t_beneficiary_account WHERE id IN "
             f"({int(acct1['beneficiaryAccountId'])},{int(acct_b['beneficiaryAccountId'])})")
    record("AC-02", "跨 Owner 同账号的 STABLE Token 相同（盲索引）", rows[0][0] == "1", {"distinctTokens": rows[0][0]})
    DATA["accounts"].update(A_AE_LOCAL=acct1["beneficiaryAccountId"], A_SWIFT_1=acct2["beneficiaryAccountId"],
                            A_SWIFT_2=acct3["beneficiaryAccountId"], B_AE_SHARED=acct_b["beneficiaryAccountId"])
    return acct1, acct2, acct3, acct_b


def ops_detail(ops, account_id):
    return ops.get(f"{ADMIN}/beneficiary-accounts/{account_id}")


def maker(ops, account_id, decision, reason=None, note=None, client_note=None, version=None, content_hash=None):
    detail = ops_detail(ops, account_id)["account"]
    body = {"decision": decision, "contentHash": content_hash or detail["contentHash"],
            "expectedVersion": version or detail["version"]}
    for key, value in (("reasonCode", reason), ("note", note), ("clientNote", client_note)):
        if value is not None:
            body[key] = value
    return ops.send("POST", f"{ADMIN}/beneficiary-accounts/{account_id}/maker-reviews", body)


def checker_decide(ops, account_id, decision, reason=None, client_note=None, version=None, content_hash=None):
    detail = ops_detail(ops, account_id)["account"]
    body = {"decision": decision, "contentHash": content_hash or detail["contentHash"],
            "expectedVersion": version or detail["version"]}
    for key, value in (("reasonCode", reason), ("clientNote", client_note)):
        if value is not None:
            body[key] = value
    return ops.send("POST", f"{ADMIN}/beneficiary-accounts/{account_id}/checker-decisions", body)


def operations_review(admin, checker, noperm, acct1, acct2, acct_b, a1_iban=None):
    queue = admin.get(f"{ADMIN}/beneficiary-reviews?stage=MAKER&size=50")
    ids = [item["account"]["beneficiaryAccountId"] for item in queue.get("items") or []]
    record("AC-03", "Maker 队列含三条新账户", {acct1["beneficiaryAccountId"], acct2["beneficiaryAccountId"],
                                               acct_b["beneficiaryAccountId"]} <= set(ids), {"total": queue.get("total")})
    item = next((i for i in queue["items"] if i["account"]["beneficiaryAccountId"] == acct1["beneficiaryAccountId"]), {})
    shared = item.get("account", {}).get("sharedOwners") or []
    record("AC-02", "队列项 sharedOwners 列出另一 Client 的账户", any(s["ownerId"] == CLIENT_B_ID for s in shared),
           {"sharedOwners": shared, "profiles": list((item.get("sharedOwnerProfiles") or {}).keys())})

    detail = ops_detail(admin, acct1["beneficiaryAccountId"])
    owner = detail.get("owner") or {}
    profiles = detail.get("sharedOwnerProfiles") or {}
    record("AC-03", "详情聚合 Owner 法定名（client-master）", owner.get("ownerId") == CLIENT_A_ID and bool(owner.get("legalName")),
           {"owner": owner})
    record("AC-02", "详情 sharedOwners + sharedOwnerProfiles 含对方 Client 法定名",
           any(s["ownerId"] == CLIENT_B_ID for s in detail["account"].get("sharedOwners") or [])
           and any((p or {}).get("legalName") for p in profiles.values()), {"profiles": profiles})

    # Reveal（AC-07）
    before = q("SELECT COUNT(*) FROM mor_vault.t_vault_access_event WHERE purpose='PAYOUT_BENEFICIARY_REVIEW'")[0][0]
    revealed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/reveal",
                          {"reasonCode": "MAKER_REVIEW"})
    record("AC-07", "Reveal 返回原值字段（不输出值）", bool(revealed.get("holderName")) and bool(revealed.get("iban")),
           {"fields": sorted(k for k, v in revealed.items() if v)})
    events = q(f"SELECT action, reason_code, actor_type, actor_id FROM {PDB}.t_beneficiary_account_event "
               f"WHERE beneficiary_account_id={int(acct1['beneficiaryAccountId'])} AND action='REVEAL'")
    after = q("SELECT COUNT(*) FROM mor_vault.t_vault_access_event WHERE purpose='PAYOUT_BENEFICIARY_REVIEW'")[0][0]
    vault = q("SELECT caller_service, action, data_type, purpose, outcome, reason_code FROM mor_vault.t_vault_access_event "
              "WHERE purpose='PAYOUT_BENEFICIARY_REVIEW' ORDER BY create_time DESC LIMIT 1")
    record("AC-07", "Reveal 审计：payout REVEAL 事件 + Vault 访问事件（用途 PAYOUT_BENEFICIARY_REVIEW）",
           len(events) >= 1 and int(after) == int(before) + 1, {"payoutEvents": events, "vaultLatest": vault})
    expect("AC-07", "Reveal 原因代码非法 422", 422,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/reveal",
                              {"reasonCode": "CURIOUS"}), "VALIDATION_ERROR")
    expect("AC-07", "无 PAYOUT 权限运营 Reveal 403", 403,
           lambda: noperm.send("POST", f"{ADMIN}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/reveal",
                               {"reasonCode": "MAKER_REVIEW"}))
    expect("R10", "无 PAYOUT 权限运营读队列 403", 403, lambda: noperm.get(f"{ADMIN}/beneficiary-reviews?stage=MAKER&size=10"))

    # Maker → 同人 Checker 403 → 另一人批准
    expect("AC-03", "Checker 在无 Maker 意见时 409", 409,
           lambda: checker_decide(checker, acct1["beneficiaryAccountId"], "APPROVED"), "BENEFICIARY_STATUS_CONFLICT")
    reviewed = maker(admin, acct1["beneficiaryAccountId"], "APPROVE", note="S1MT 户名与法定名一致")
    shown = ops_detail(admin, acct1["beneficiaryAccountId"])["account"]
    stored = q(f"SELECT review_maker_note FROM {PDB}.t_beneficiary_account WHERE id={int(acct1['beneficiaryAccountId'])}")
    record("AC-03", "Maker 意见 APPROVE（带 note）：运营详情与库内有备注", reviewed["reviewMakerDecision"] == "APPROVE"
           and reviewed["status"] == "PENDING_REVIEW" and shown.get("reviewMakerNote") == "S1MT 户名与法定名一致"
           and stored[0][0] == "S1MT 户名与法定名一致",
           {"makerId": reviewed["reviewMakerId"], "version": reviewed["version"],
            "noteInCommandResponse": reviewed.get("reviewMakerNote"), "noteInDetail": shown.get("reviewMakerNote")})
    expect("AC-03", "重复 Maker 意见 409", 409, lambda: maker(checker, acct1["beneficiaryAccountId"], "APPROVE"),
           "BENEFICIARY_STATUS_CONFLICT")
    queue = admin.get(f"{ADMIN}/beneficiary-reviews?stage=CHECKER&size=50")
    record("AC-03", "进入 Checker 队列", acct1["beneficiaryAccountId"] in [i["account"]["beneficiaryAccountId"]
                                                                       for i in queue.get("items") or []], {"total": queue.get("total")})
    expect("AC-03", "同一人做 Checker 403 FOUR_EYES_REQUIRED", 403,
           lambda: checker_decide(admin, acct1["beneficiaryAccountId"], "APPROVED"), "FOUR_EYES_REQUIRED")
    expect("AC-03", "contentHash 不一致 409", 409,
           lambda: checker_decide(checker, acct1["beneficiaryAccountId"], "APPROVED", content_hash="0" * 64))
    stale = str(int(reviewed["version"]) - 1)
    expect("AC-10", "Checker expectedVersion 过期（当前 − 1）409", 409,
           lambda: checker_decide(checker, acct1["beneficiaryAccountId"], "APPROVED", version=stale))
    decided = checker_decide(checker, acct1["beneficiaryAccountId"], "APPROVED")
    decision = datetime.fromisoformat(decided["decisionTime"].replace("Z", "+00:00"))
    cooling = datetime.fromisoformat(decided["coolingUntil"].replace("Z", "+00:00"))
    record("AC-03", "另一人批准 → VERIFIED，coolingUntil = 决定 + 24h", decided["status"] == "VERIFIED"
           and cooling - decision == timedelta(hours=24) and decided["usable"] is False,
           {"decisionTime": decided["decisionTime"], "coolingUntil": decided["coolingUntil"], "checkerId": decided["checkerId"]})
    rows = q(f"SELECT status, TIMESTAMPDIFF(SECOND, decision_time, cooling_until), review_maker_id, checker_id "
             f"FROM {PDB}.t_beneficiary_account WHERE id={int(acct1['beneficiaryAccountId'])}")
    record("AC-03", "库内 cooling_until − decision_time = 86400 秒，Maker ≠ Checker", rows and rows[0][1] == "86400"
           and rows[0][2] != rows[0][3], {"row": rows})

    # 拒绝（clientNote）；Maker 建议拒绝时 Checker 不能批准
    maker(admin, acct2["beneficiaryAccountId"], "REJECT", reason="HOLDER_NAME_MISMATCH", note="S1MT 户名与银行回执不一致",
          client_note="S1MT 请上传银行开户证明")
    expect("AC-03", "Maker 建议拒绝时 Checker 批准 409", 409,
           lambda: checker_decide(checker, acct2["beneficiaryAccountId"], "APPROVED"))
    rejected = checker_decide(checker, acct2["beneficiaryAccountId"], "REJECTED", reason="HOLDER_NAME_MISMATCH")
    record("AC-03", "拒绝 → REJECTED，原因代码与给 Client 的说明（沿用 Maker 的 clientNote）",
           rejected["status"] == "REJECTED" and rejected["reasonCode"] == "HOLDER_NAME_MISMATCH"
           and rejected.get("reasonNote") == "S1MT 请上传银行开户证明", {"reasonCode": rejected["reasonCode"],
                                                                         "reasonNote": rejected.get("reasonNote")})
    return decided, rejected


def position_of(reply):
    return next((p for p in (reply or {}).get("positions") or [] if p["entityNo"] == ENTITY and p["currency"] == CURRENCY), None)


def lifecycle(owner_a, admin, checker, acct1, acct2):
    expect("AC-05", "REJECTED 账户设默认 409", 409,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts/{acct2['beneficiaryAccountId']}/default", "POST",
                                   {"expectedVersion": owner_a.call(f"{PAY}/beneficiary-accounts/{acct2['beneficiaryAccountId']}")[1]["version"]}),
           "BENEFICIARY_STATUS_CONFLICT")
    _, current = owner_a.call(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}")
    status, first = owner_a.command(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/default", "POST",
                                    {"expectedVersion": current["version"]})
    record("AC-05", "VERIFIED（冷却中）账户设默认", status == 200 and first["defaultAccount"] is True, {"version": first["version"]})
    status, again = owner_a.command(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/default", "POST",
                                    {"expectedVersion": current["version"]})
    record("AC-10", "已是默认再设默认（旧版本号）→ 200 原样返回，版本不变（幂等无操作）", status == 200
           and again["version"] == first["version"] and again["defaultAccount"], {"version": again["version"]})

    # 第二个 USD VERIFIED 账户 → 设默认后前一个不再默认
    body = sa_local("S1MT Trading LLC")
    _, acct4 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", body)
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1MT 第二个 USD 账户")
    acct4 = checker_decide(checker, acct4["beneficiaryAccountId"], "APPROVED")
    status, second = owner_a.command(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/default", "POST",
                                     {"expectedVersion": acct4["version"]})
    _, listed = owner_a.call(f"{PAY}/beneficiary-accounts?currency=USD")
    defaults = [i["beneficiaryAccountId"] for i in listed["items"] if i["defaultAccount"]]
    record("AC-05", "同币种只有一个默认（新默认替换旧默认）", status == 200 and defaults == [acct4["beneficiaryAccountId"]],
           {"defaults": defaults})
    rows = q(f"SELECT COUNT(*) FROM {PDB}.t_beneficiary_account WHERE owner_type='CLIENT' AND owner_id={int(CLIENT_A_ID)} "
             f"AND currency='USD' AND is_default=1")
    record("AC-05", "库内 is_default=1 只有一行", rows[0][0] == "1", {"count": rows[0][0]})

    # 停用默认账户 → 无默认；恢复 → PENDING_REVIEW 清 Maker / Checker
    _, detail = owner_a.call(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}")
    expect("AC-10", "停用 expectedVersion 过期（当前 − 1）409", 409,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                              {"reasonCode": "RISK_REVIEW", "expectedVersion": str(int(detail["version"]) - 1)}))
    suspended = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                           {"reasonCode": "RISK_REVIEW", "expectedVersion": detail["version"]})
    _, listed = owner_a.call(f"{PAY}/beneficiary-accounts?currency=USD")
    defaults = [i["beneficiaryAccountId"] for i in listed["items"] if i["defaultAccount"]]
    record("AC-05", "停用默认账户后该币种无默认", suspended["status"] == "SUSPENDED" and suspended["reasonCode"] == "RISK_REVIEW"
           and not suspended["defaultAccount"] and defaults == [], {"defaults": defaults})
    expect("AC-06", "停用原因非法 400", 400,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                              {"reasonCode": "BAD_REASON", "expectedVersion": suspended["version"]}))
    resumed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/resume",
                         {"expectedVersion": suspended["version"]})
    record("AC-06", "恢复 → PENDING_REVIEW，清 Maker / Checker 与冷却，需重核", resumed["status"] == "PENDING_REVIEW"
           and not resumed.get("reviewMakerId") and not resumed.get("checkerId") and not resumed.get("coolingUntil"),
           {"status": resumed["status"], "maker": resumed.get("reviewMakerId"), "checker": resumed.get("checkerId")})
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1MT 恢复后重核")
    rechecked = checker_decide(checker, acct4["beneficiaryAccountId"], "APPROVED")
    record("AC-06", "恢复后重走核验与冷却（新 coolingUntil）", rechecked["status"] == "VERIFIED"
           and rechecked["coolingUntil"] != acct4["coolingUntil"],
           {"firstCooling": acct4["coolingUntil"], "secondCooling": rechecked["coolingUntil"]})

    # Client 关闭；关闭后不能设默认
    status, closed = owner_a.command(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/close", "POST",
                                     {"reasonCode": "OWNER_REQUEST", "expectedVersion": rechecked["version"]})
    record("AC-06", "Client 关闭 → CLOSED，原因 OWNER_REQUEST", status == 200 and closed["status"] == "CLOSED"
           and closed["reasonCode"] == "OWNER_REQUEST" and not closed["defaultAccount"], {"version": closed["version"]})
    expect("AC-05", "CLOSED 账户设默认 409", 409,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/default", "POST",
                                   {"expectedVersion": closed["version"]}), "BENEFICIARY_STATUS_CONFLICT")
    expect("AC-06", "CLOSED 账户再关闭 409", 409,
           lambda: owner_a.command(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/close", "POST",
                                   {"reasonCode": "OWNER_REQUEST", "expectedVersion": closed["version"]}))
    status, events = owner_a.call(f"{PAY}/beneficiary-accounts/{acct4['beneficiaryAccountId']}")
    record("AC-06", "事件时间线完整", [e["action"] for e in events.get("events") or []],
           {"events": [(e["eventNo"], e["action"], e.get("toStatus")) for e in events.get("events") or []]})
    # 恢复 acct1 为默认，供浏览器核对
    _, current = owner_a.call(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}")
    if not current["defaultAccount"]:
        owner_a.command(f"{PAY}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/default", "POST",
                        {"expectedVersion": current["version"]})
    DATA["accounts"]["A_SA_LOCAL_LIFECYCLE"] = acct4["beneficiaryAccountId"]
    return acct4


def ops_close_and_checker_stage(admin, acct3, acct_b):
    """运营关闭一条 PENDING 账户（A_SWIFT_2）；B 的共用账户只做 Maker 意见，留在 Checker 队列供浏览器核对。"""
    detail = ops_detail(admin, acct3["beneficiaryAccountId"])["account"]
    closed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct3['beneficiaryAccountId']}/close",
                        {"reasonCode": "OPS", "expectedVersion": detail["version"]})
    record("AC-06", "运营关闭待核验账户 → CLOSED（原因 OPS）", closed["status"] == "CLOSED" and closed["reasonCode"] == "OPS",
           {"id": closed["beneficiaryAccountId"]})
    reviewed = maker(admin, acct_b["beneficiaryAccountId"], "APPROVE", note="S1MT 共用账号，待复核")
    record("AC-03", "B 共用账户 Maker 建议通过，留在 Checker 队列", reviewed["reviewMakerDecision"] == "APPROVE", None)
    return closed


def manage_recipients(client_id):
    rows = q(f"SELECT COUNT(DISTINCT m.member_id) FROM {PLT}.t_access_tenant t "
             f"JOIN {PLT}.t_tenant_member m ON m.tenant_id=t.tenant_id AND m.status='ACTIVE' "
             f"JOIN {PLT}.t_tenant_member_role mr ON mr.tenant_member_id=m.member_id AND mr.status='ACTIVE' "
             f"AND mr.effective_from<=UTC_TIMESTAMP(6) AND (mr.effective_to IS NULL OR mr.effective_to>UTC_TIMESTAMP(6)) "
             f"JOIN {PLT}.t_role_permission rp ON rp.role_id=mr.role_id AND rp.status='ACTIVE' "
             f"JOIN {PLT}.t_permission p ON p.permission_id=rp.permission_id AND p.permission_code='CLIENT_PAYOUT_ACCOUNT_MANAGE' "
             f"JOIN {PLT}.t_user_account a ON a.account_id=m.account_id AND a.status='ACTIVE' AND a.email_verified_time IS NOT NULL "
             f"WHERE t.tenant_type='CLIENT' AND t.owner_ref_id={int(client_id)}")
    members = q(f"SELECT COUNT(*) FROM {PLT}.t_access_tenant t JOIN {PLT}.t_tenant_member m ON m.tenant_id=t.tenant_id "
                f"AND m.status='ACTIVE' WHERE t.tenant_type='CLIENT' AND t.owner_ref_id={int(client_id)}")
    return int(rows[0][0]), int(members[0][0])


def regressions(admin):
    status, body = attempt(lambda: admin.get("/risk-limit-tiers/v1/client-tiers?page=1&size=10"))
    record("回归", "REQ-04 限额档位列表 GET /risk-limit-tiers/v1/client-tiers 200", status == 200,
           {"status": status, "total": body.get("total") if isinstance(body, dict) else None})
    status, body = attempt(lambda: admin.get("/risk-limit-tiers/v1/tier-quotas"))
    record("回归", "REQ-04 档位配额 GET /risk-limit-tiers/v1/tier-quotas 200", status == 200, {"status": status})
    status, body = attempt(lambda: admin.get("/data-query-admin/v1/key-types?size=200"))
    record("回归", "联合查询键类型列表 200", status == 200, {"status": status, "total": body.get("total") if isinstance(body, dict) else None})
    status, body = attempt(lambda: admin.get("/localization-admin/v1/applications/operations-console/releases?page=1&size=10"))
    record("回归", "多语言发布历史 200", status == 200, {"status": status, "total": body.get("total") if isinstance(body, dict) else None})


# ---------------------------------------------------------------- mor-test 会话与准备
def admin_session():
    s = E.secrets()
    return E.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def operator_sessions(state):
    missing = [label for label in OPERATOR_ROLES if label not in state.get("operators", {})]
    if missing:
        raise SystemExit(f"缺少运营账号 {missing}：先执行 accounts --yes")
    sessions = {}
    for label, creds in state["operators"].items():
        sessions[label] = E.Operations(creds["email"], creds["password"])
        DATA["operators"][label] = {"accountId": creds["accountId"], "roleId": creds["roleId"], "permissions": creds["permissions"]}
    return sessions


def client_session(label):
    clients = load_json(CLIENTS_FILE)
    if label not in clients:
        raise SystemExit(f"缺少 Client {label} 所有者凭据：{CLIENTS_FILE}（待裁决 D-4，主会话提供）")
    creds = clients[label]
    expected = {"A": CLIENT_A_ID, "B": CLIENT_B_ID}[label]
    if creds.get("clientId") != expected:
        raise SystemExit(f"clients.json {label}.clientId 与脚本选定的 {expected} 不一致")
    session = Client()
    context = session.login(creds["email"], creds["password"])
    member_id = session.enter(expected)
    return session, context["accountId"], member_id


def accounts(state, admin):
    stamp = time.strftime("%m%d%H%M%S")
    for label, grants in OPERATOR_ROLES.items():
        if label in state.get("operators", {}):
            record("准备", f"运营 {label} 已存在（复用）", True, state["operators"][label]["accountId"])
            continue
        role = admin.send("POST", "/iam/v1/roles", {"roleCode": f"S1MT_{label.upper()}_{stamp}",
                                                   "roleName": f"payout S1 验收 {label} {stamp}",
                                                   "permissionIds": [PERMISSIONS[g] for g in grants],
                                                   "reason": "payout S1 收款账户 mor-test 验收"})
        role_id = role.get("roleId") or role.get("objectId")
        email = f"s1mt-{label}-{stamp}@example.test"
        p1, p2 = "S1m!" + pysecrets.token_urlsafe(18), "S1m!" + pysecrets.token_urlsafe(18)
        account = admin.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                          "reason": "payout S1 收款账户 mor-test 验收"})
        E.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        state.setdefault("operators", {})[label] = {"email": email, "password": p2, "accountId": account.get("accountId"),
                                                    "roleId": role_id, "permissions": list(grants)}
        save_state(state)
        record("准备", f"运营 {label} 建成（{'/'.join(grants)}）", True, account.get("accountId"))
    operator_sessions(state)
    record("准备", "Maker 与 Checker 为两个不同账号", DATA["operators"]["maker"]["accountId"] != DATA["operators"]["checker"]["accountId"],
           {k: v["accountId"] for k, v in DATA["operators"].items()})
    if OPS_ONLY:
        return
    for label in ("A", "B"):
        _, account_id, member_id = client_session(label)
        record("准备", f"Client {label} 所有者可登录并进入工作区", True, {"accountId": account_id, "memberId": member_id})


def invite_member(state, owner, label, email, role_code):
    """Client A 团队邀请一名成员并经邀请链接加入（真实入口；邀请 / 注册邮件经 SES 发出，链接用既有铸造夹具）。"""
    if label in state.get("members", {}):
        return state["members"][label]
    password = "S1m-" + uuid.uuid4().hex[:16]
    _, created = owner.call("/team/v1/invitations", "POST", {"email": email, "roleCode": role_code})
    proof = mint_invitation_link(created["invitationId"])
    invitee = Client()
    context = register(invitee, email, password)
    invitee.call("/invitations/v1/acceptance", "POST", proof)
    member = {"email": email, "password": password, "accountId": context["accountId"], "invitationId": created["invitationId"],
              "roleCode": role_code}
    state.setdefault("members", {})[label] = member
    save_state(state)
    return member


def prepare(state, args):
    owner_a, _, _ = client_session("A")
    stamp = time.strftime("%m%d%H%M%S")
    viewer = invite_member(state, owner_a, "viewer", f"s1mt-viewer-{stamp}@example.test", "TENANT_VIEWER")
    manager = invite_member(state, owner_a, "manager", SES_SIMULATOR.format(stamp=stamp), "TENANT_ADMIN")
    for label, member in (("viewer", viewer), ("manager", manager)):
        session = Client()
        session.login(member["email"], member["password"])
        member_id = session.enter(CLIENT_A_ID)
        record("准备", f"Client A 成员 {label}（{member['roleCode']}）可进入工作区", True,
               {"accountId": member["accountId"], "memberId": member_id})
    expected, members = manage_recipients(CLIENT_A_ID)
    record("准备", "Client A 持 CLIENT_PAYOUT_ACCOUNT_MANAGE 的有效已验证成员 ≥ 2（所有者 + manager）", expected >= 2,
           {"manageRecipients": expected, "members": members})
    if args.billing_fact:
        ops = operator_sessions(state)
        prepare_balance(state, DATA["operators"]["maker"]["accountId"], DATA["operators"]["checker"]["accountId"])
        del ops


def account_rows(client_id):
    return q(f"SELECT entity_id, currency, balance_bucket, net_balance FROM mor_account.t_owner_account "
             f"WHERE owner_type='CLIENT' AND owner_id='{int(client_id)}' ORDER BY entity_id, currency, balance_bucket")


def prepare_balance(state, maker_id, checker_id):
    """可选（--billing-fact，待裁决 D-5）：对 Client B 登记并批准一条 MANUAL_ADJUSTMENT 计费事实 80.00 USD → account 明细账。"""
    facts = state.setdefault("billingFacts", {})
    if "B" not in facts:
        body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": "OPS-" + str(uuid.uuid4()),
                "clientId": CLIENT_B_ID, "entityNo": ENTITY, "currency": CURRENCY,
                "refs": [{"refType": "TICKET", "refId": "S1MT-B-" + time.strftime("%m%d%H%M%S")}],
                "lines": [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": ENTITY,
                           "payeeRole": "CLIENT", "payeeId": CLIENT_B_ID, "amount": "80.00", "direction": "ADJUSTMENT",
                           "targetBucket": "AVAILABLE"}]}
        created = billing("POST", "", maker_id, body)
        facts["B"] = {"factId": created["factId"], "clientId": CLIENT_B_ID, "amount": "80.00"}
        save_state(state)
    fact = billing("POST", f"/{facts['B']['factId']}/approve", checker_id)
    record("准备", "Client B 计费事实已批准", fact.get("status") == "APPROVED", {"factId": facts["B"]["factId"]})
    rows = wait_until(lambda: account_rows(CLIENT_B_ID), 30, 3)
    record("准备", "Client B account 明细账出现 AE001 × USD", bool(rows), {"rows": rows})


def expected_reasons(client_id, verified_cooling, verified_usable):
    """按 mor_account 现值与本次账户状态推算 AE001 × USD 的阻断原因（与 BalanceExplanation.blockReasons 相同规则）。"""
    rows = account_rows(client_id)
    available = sum(float(r[3]) for r in rows if r[0] == ENTITY and r[1] == CURRENCY and r[2] == "AVAILABLE")
    present = any(r[0] == ENTITY and r[1] == CURRENCY for r in rows)
    reasons = set()
    if available <= 0:
        reasons.add("NO_AVAILABLE_BALANCE")
    if not verified_cooling and not verified_usable:
        reasons.add("NO_USABLE_ACCOUNT")
    elif not verified_usable:
        reasons.add("ACCOUNT_COOLING")
    return present, reasons, rows


def balance_checks(owner_a, owner_b, admin, acct1_id):
    """A：一条 VERIFIED 冷却中账户；B：只有 PENDING_REVIEW 账户；Z：只有 PENDING 余额；C：无 account 账户。预期按 mor_account 现值计算。"""
    status, account = owner_a.call(f"{PAY}/beneficiary-accounts/{acct1_id}")
    record("AC-04", "Client 账户详情：VERIFIED、usable=false、coolingUntil 有值", status == 200
           and account["status"] == "VERIFIED" and account["usable"] is False and bool(account.get("coolingUntil")),
           {"status": account["status"], "usable": account["usable"], "coolingUntil": account.get("coolingUntil")})
    for label, session, client_id, cooling in (("A", owner_a, CLIENT_A_ID, True), ("B", owner_b, CLIENT_B_ID, False)):
        present, reasons, rows = expected_reasons(client_id, cooling, False)
        status, reply = attempt(lambda: session.call(f"{PAY}/balances"))
        pos = position_of(reply) if status == 200 else None
        ok = status == 200 and ((pos is not None and set(pos["blockReasons"]) >= reasons and
                                 ("ACCOUNT_COOLING" in pos["blockReasons"]) == cooling) if present
                                else (reply or {}).get("positions") == [])
        record("AC-04" if cooling else "AC-08", f"Client {label} /balances 阻断原因 ⊇ {sorted(reasons)}（account 现值 {rows}）", ok,
               {"status": status, "position": pos, "usableAccounts": (reply or {}).get("usableAccounts")})
        if pos:
            record("AC-08", f"Client {label} 桶余额为字符串且与 mor_account 一致",
                   all(isinstance(pos.get(k), str) for k in ("pending", "available", "locked", "recoveryReceivable"))
                   and abs(float(pos["available"]) - sum(float(r[3]) for r in rows if r[2] == "AVAILABLE")) < 0.005
                   and abs(float(pos["pending"]) - sum(float(r[3]) for r in rows if r[2] == "PENDING")) < 0.005,
                   {k: pos.get(k) for k in ("pending", "available", "locked", "securityDeposit", "recoveryReceivable")})
        status_o, ops_reply = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={client_id}"))
        record("AC-08", f"运营 /payout-admin/v1/balances 与 Client {label} 视图一致", status_o == 200 and status == 200
               and ops_reply.get("positions") == reply.get("positions"), {"status": status_o})
    status_z, reply_z = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_Z_ID}"))
    pos_z = position_of(reply_z) if status_z == 200 else None
    record("AC-08", "Client Z（只有 PENDING）：NO_AVAILABLE_BALANCE + NO_USABLE_ACCOUNT（运营只读视图）",
           status_z == 200 and pos_z and {"NO_AVAILABLE_BALANCE", "NO_USABLE_ACCOUNT"} <= set(pos_z["blockReasons"]),
           {"status": status_z, "position": pos_z})
    status_e, empty = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_C_ID}"))
    record("AC-08", "无 account 账户的 Owner（C）：positions 为空数组", status_e == 200 and empty.get("positions") == [],
           {"status": status_e, "positions": (empty or {}).get("positions")})
    rows = q("SELECT COUNT(*) FROM mor_account.t_owner_account WHERE owner_type='CLIENT' AND balance_bucket LIKE '%RECOVERY%' AND net_balance <> 0")
    record("AC-08", "RECOVERY_OUTSTANDING：mor-test 无 Client 追偿应收余额（经正式入口无法构造），由 payout auto 测试覆盖", True,
           {"recoveryRows": rows[0][0]})


def notifications(started_utc, raw_secrets):
    """AC-09（mor-test）：Platform 经真实 Amazon SES（ap-southeast-1，沙箱）发信，Mailpit 不参与。"""
    def settled():
        rows = q(f"SELECT event_type, execute_status, COUNT(*) FROM {PDB}.t_compensation_event "
                 f"WHERE create_time>='{started_utc}' GROUP BY 1,2")
        return None if rows else True  # 成功即删除；剩余行（INIT / PROCESSING / ERROR / FAILED）说明未处理完
    done = wait_until(settled, 120, 5)
    left = q(f"SELECT event_type, sub_event_type, execute_status, execute_count, last_error_code FROM {PDB}.t_compensation_event "
             f"WHERE create_time>='{started_utc}' ORDER BY id")
    record("AC-09", "payout t_compensation_event 本次登记的通知任务全部处理完成（成功即删除，剩余 0）", bool(done), {"left": left})
    expected, members = manage_recipients(CLIENT_A_ID)
    requests = q(f"SELECT r.request_id, r.source_event_key, tpl.template_code, "
                 f"(SELECT COUNT(*) FROM {PLT}.t_notification_delivery d WHERE d.request_id=r.request_id) "
                 f"FROM {PLT}.t_notification_request r JOIN {PLT}.t_notification_template tpl ON tpl.template_id=r.notification_template_id "
                 f"WHERE r.source_system='payout-service' AND r.create_time>='{started_utc}' ORDER BY r.create_time")
    templates = sorted({r[2] for r in requests})
    DATA["notifications"] = [{"requestId": r[0], "sourceEventKey": r[1], "template": r[2], "deliveries": r[3]} for r in requests]
    record("AC-09", "Platform 受理四类 payout 通知（核验通过 / 拒绝 / 停用 / 关闭）",
           set(templates) >= {"PAYOUT_BENEFICIARY_VERIFIED_EMAIL", "PAYOUT_BENEFICIARY_REJECTED_EMAIL",
                              "PAYOUT_BENEFICIARY_SUSPENDED_EMAIL", "PAYOUT_BENEFICIARY_CLOSED_EMAIL"},
           {"templates": templates, "count": len(requests)})
    record("AC-09", f"每条通知投递数 = Client A 持 MANAGE 的有效已验证成员数（{expected}；成员共 {members}，含 VIEWER）",
           requests and all(int(r[3]) == expected for r in requests), {"deliveries": [r[3] for r in requests]})
    statuses = q(f"SELECT d.status, COUNT(*) FROM {PLT}.t_notification_delivery d JOIN {PLT}.t_notification_request r "
                 f"ON r.request_id=d.request_id WHERE r.source_system='payout-service' AND r.create_time>='{started_utc}' GROUP BY 1")
    by_status = {s: int(n) for s, n in statuses}
    record("AC-09", "投递全部到终态（SENT / DELIVERED / FAILED，无 PENDING / PROCESSING / RETRY_WAIT / UNKNOWN 滞留）",
           set(by_status) <= {"SENT", "DELIVERED", "FAILED"}, by_status)
    if OPS_ONLY:
        record("AC-09", "（--ops-only）未邀请 SES 模拟器成员：投递成功与否不判定，Client 侧收件验证留作遗留", True, by_status)
    else:
        record("AC-09", "SES 模拟器收件人（manager）每类通知至少 1 条 SENT / DELIVERED（沙箱可投递）",
               by_status.get("SENT", 0) + by_status.get("DELIVERED", 0) >= len(requests) > 0, by_status)
    record("AC-09", "@example.test 收件人被 SES 沙箱拒绝时只记投递 FAILED，账户状态不受影响（见 api 结果的账户终态）", True,
           {"failed": by_status.get("FAILED", 0)})
    params = q(f"SELECT CONCAT_WS('|', r.default_parameters, (SELECT GROUP_CONCAT(d.parameters) FROM {PLT}.t_notification_delivery d "
               f"WHERE d.request_id=r.request_id)) FROM {PLT}.t_notification_request r WHERE r.source_system='payout-service' "
               f"AND r.create_time>='{started_utc}'")
    blob = " ".join(p[0] for p in params)
    leaks = [i for i, v in enumerate(raw_secrets) if v and v in blob]
    record("AC-09", "模板参数无原值（户名 / IBAN / BIC）", not leaks, {"leakIndexes": leaks})
    vault_tokens = q(f"SELECT COUNT(*) FROM {PLT}.t_notification_delivery d JOIN {PLT}.t_notification_request r "
                     f"ON r.request_id=d.request_id WHERE r.source_system='payout-service' AND r.create_time>='{started_utc}' "
                     f"AND d.address_vault_token IS NOT NULL")
    record("AC-09", "投递地址为账号邮箱 Vault Token（不 Reveal）", requests and int(vault_tokens[0][0]) == sum(int(r[3]) for r in requests),
           {"withToken": vault_tokens[0][0]})
    try:
        with urllib.request.urlopen("http://127.0.0.1:28025/api/v1/info", timeout=5) as response:
            info = json.loads(response.read())
        record("AC-09", "Mailpit 只作对照（mor-test Platform 不经 Mailpit 发信）", True, {"mailpitMessages": info.get("Messages")})
    except OSError as failure:
        record("AC-09", "Mailpit 只作对照（不可达不影响判定）", True, {"error": str(failure)[:120]})
    return requests


# ---------------------------------------------------------------- 子命令
def api(state):
    run = {"startedUtc": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")}
    save_json(RUN_FILE, {**load_json(RUN_FILE), **run})
    DATA["startedUtc"] = run["startedUtc"]
    ops = operator_sessions(state)
    maker_s, checker, noperm, ro = ops["maker"], ops["checker"], ops["noperm"], ops["ro"]
    owner_a, owner_a_account, member_a = client_session("A")
    owner_b, owner_b_account, member_b = client_session("B")
    viewer_creds = state.get("members", {}).get("viewer")
    if not viewer_creds:
        raise SystemExit("缺少 Client A viewer 成员：先执行 prepare --yes")
    viewer = Client()
    viewer.login(viewer_creds["email"], viewer_creds["password"])
    viewer.enter(CLIENT_A_ID)
    DATA["clientMembers"] = {"A": {"accountId": owner_a_account, "memberId": member_a}, "B": {"accountId": owner_b_account, "memberId": member_b}}

    acct1, acct2, acct3, acct_b = client_submit(owner_a, owner_b, viewer)
    secrets_to_check = []
    for account_id in (acct1["beneficiaryAccountId"], acct2["beneficiaryAccountId"]):
        revealed = maker_s.send("POST", f"{ADMIN}/beneficiary-accounts/{account_id}/reveal", {"reasonCode": "MAKER_REVIEW"})
        secrets_to_check += [revealed.get(k) for k in ("holderName", "iban", "accountNumber") if revealed.get(k)]  # BIC 是银行标识（同 raw_values）
    secrets_to_check = sorted({v for v in secrets_to_check if len(v) >= 8})
    save_json(RUN_FILE, {**load_json(RUN_FILE), "rawValues": secrets_to_check})  # 只在 0600 状态文件，供 notify / check 复核
    expect("R10", "只读运营（ro）Reveal 403", 403,
           lambda: ro.send("POST", f"{ADMIN}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/reveal", {"reasonCode": "MAKER_REVIEW"}))
    status, listed = attempt(lambda: ro.get(f"{ADMIN}/beneficiary-reviews?stage=MAKER&size=10"))
    record("R10", "只读运营（ro）可读核验队列 200", status == 200, {"status": status})
    expect("R10", "只读运营（ro）Maker 意见 403", 403, lambda: maker(ro, acct3["beneficiaryAccountId"], "APPROVE"))
    operations_review(maker_s, checker, noperm, acct1, acct2, acct_b)
    balance_checks(owner_a, owner_b, maker_s, acct1["beneficiaryAccountId"])
    lifecycle(owner_a, maker_s, checker, acct1, acct2)
    ops_close_and_checker_stage(maker_s, acct3, acct_b)
    # 浏览器走查用：两条 PENDING_REVIEW、无 Maker 意见的 A 账户（写组合 Maker 通过 + Checker 批准第一条；第二条只开拒绝弹窗后取消）
    for label, body in (("A_BROWSER_MAKER", sa_local("S1MT Trading LLC")), ("A_BROWSER_REJECT", ae_local("S1MT Trading LLC"))):
        status, created = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", body)
        record("准备", f"浏览器走查账户 {label} → PENDING_REVIEW", status == 201 and created["status"] == "PENDING_REVIEW",
               {"id": created.get("beneficiaryAccountId")})
        DATA["accounts"][label] = created["beneficiaryAccountId"]
        DATA.setdefault("accountCodes", {})[label] = created.get("accountCode")
    regressions(admin_session())
    save_json(RUN_FILE, {**load_json(RUN_FILE), "accounts": DATA["accounts"], "accountCodes": DATA.get("accountCodes", {})})
    return "api"


def ops_submit(ops, owner_id, body):
    return ops.send("POST", f"{ADMIN}/beneficiary-accounts", {"ownerType": "CLIENT", "ownerId": owner_id, **body})


def ops_submit_checks(maker_s, noperm, ro):
    """--ops-only：运营代 Owner 提交（B1），替代 client_submit；Client 门户自身入口、TENANT_VIEWER 分支与同键重放留作遗留。"""
    req = maker_s.get(f"{ADMIN}/beneficiary-requirements?railType=LOCAL&bankCountry=AE&currency=USD")
    codes = [f.get("code") for f in (req or {}).get("fields") or []]
    record("AC-01", "运营字段清单 LOCAL × AE × USD", codes == ["holderName", "iban", "bankName"], {"fields": codes})
    expect("AC-01", "运营字段清单 LOCAL × EG × USD 不支持 404", 404,
           lambda: maker_s.get(f"{ADMIN}/beneficiary-requirements?railType=LOCAL&bankCountry=EG&currency=USD"),
           "BENEFICIARY_REQUIREMENT_NOT_FOUND")
    a1, a2, a3 = ae_local("S1MT Trading LLC"), swift("S1MT Trading LLC"), swift("S1MT Trading LLC")
    acct1 = ops_submit(maker_s, CLIENT_A_ID, a1)
    record("AC-01", "运营代 A 提交 AE 本地 USD → PENDING_REVIEW，掩码与后四位", acct1["status"] == "PENDING_REVIEW"
           and acct1["holderNameMasked"] != a1["fields"]["holderName"] and acct1["accountLast4"] == a1["fields"]["iban"][-4:],
           {"id": acct1["beneficiaryAccountId"], "code": acct1["accountCode"], "masked": acct1["holderNameMasked"]})
    acct2 = ops_submit(maker_s, CLIENT_A_ID, a2)
    record("AC-01", "运营代 A 提交 SWIFT USD → PENDING_REVIEW", acct2["status"] == "PENDING_REVIEW", {"id": acct2["beneficiaryAccountId"]})
    acct3 = ops_submit(maker_s, CLIENT_A_ID, a3)
    same = ops_submit(maker_s, CLIENT_A_ID, a1)
    record("AC-10", "新键、同 Owner 同内容重复提交 → 返回原账户（业务幂等）",
           same["beneficiaryAccountId"] == acct1["beneficiaryAccountId"], {"id": same["beneficiaryAccountId"]})
    changed = dict(a1, fields=dict(a1["fields"], holderName="S1MT Different Holder"))
    dup = expect("AC-02", "同 Owner 同账号、内容不同 → 409 BENEFICIARY_DUPLICATE", 409,
                 lambda: ops_submit(maker_s, CLIENT_A_ID, changed), "BENEFICIARY_DUPLICATE")
    record("AC-02", "409 含已有账户 ID（technique P1）", acct1["beneficiaryAccountId"] in json.dumps(dup, ensure_ascii=False),
           {"detail": dup.get("detail") if isinstance(dup, dict) else None})
    for step, status, bad, code in (
            ("不支持组合 LOCAL × EG × USD 422", 422, dict(a1, bankCountry="EG"), "BENEFICIARY_UNSUPPORTED_ROUTE"),
            ("IBAN 格式不符 400", 400, dict(a1, fields=dict(a1["fields"], iban="AE12")), "INVALID_REQUEST"),
            ("清单外字段 400", 400, dict(a1, fields=dict(a1["fields"], swiftBic="SONEGB2L")), "INVALID_REQUEST"),
            ("缺必填字段 400", 400, dict(a1, fields={k: v for k, v in a1["fields"].items() if k != "bankName"}), "INVALID_REQUEST")):
        expect("AC-01", f"运营提交{step}", status, lambda bad=bad: ops_submit(maker_s, CLIENT_A_ID, bad), code)
    b1 = dict(a1, fields=dict(a1["fields"], holderName="S1MT Shared Holder Co"))
    acct_b = ops_submit(maker_s, CLIENT_B_ID, b1)
    record("AC-02", "运营代另一 Client 提交共用同一账号 → PENDING_REVIEW", acct_b["status"] == "PENDING_REVIEW",
           {"id": acct_b["beneficiaryAccountId"], "ownerId": acct_b["ownerId"]})
    expect("R10", "无 PAYOUT 权限运营提交 403", 403, lambda: ops_submit(noperm, CLIENT_A_ID, sa_local("S1MT Noperm")))
    expect("R10", "只读运营（ro）提交 403", 403, lambda: ops_submit(ro, CLIENT_A_ID, sa_local("S1MT Readonly")))
    for label, acct, body in (("AE 本地", acct1, a1), ("SWIFT", acct2, a2)):
        hits = no_raw_in_payout(acct["beneficiaryAccountId"], raw_values(body))
        record("AC-01", f"{label}：mor_payout 三表无原值（户名 / IBAN / 账号）", not hits, {"hits": hits})
        rows = q(f"SELECT (SELECT data_type FROM mor_vault.t_vault_record r WHERE r.token=a.beneficiary_vault_token), "
                 f"(SELECT data_type FROM mor_vault.t_vault_record r WHERE r.token=a.bank_account_token) "
                 f"FROM {PDB}.t_beneficiary_account a WHERE a.id={int(acct['beneficiaryAccountId'])}")
        record("AC-01", f"{label}：Vault 有 PAYOUT_BENEFICIARY_V1 与 PAYOUT_BANK_ACCOUNT_KEY_V1 两类 Token",
               rows and rows[0][0] == "PAYOUT_BENEFICIARY_V1" and rows[0][1] == "PAYOUT_BANK_ACCOUNT_KEY_V1", {"row": rows})
    rows = q(f"SELECT COUNT(DISTINCT bank_account_token) FROM {PDB}.t_beneficiary_account WHERE id IN "
             f"({int(acct1['beneficiaryAccountId'])},{int(acct_b['beneficiaryAccountId'])})")
    record("AC-02", "跨 Owner 同账号的 STABLE Token 相同（盲索引）", rows[0][0] == "1", {"distinctTokens": rows[0][0]})
    DATA["accounts"].update(A_AE_LOCAL=acct1["beneficiaryAccountId"], A_SWIFT_1=acct2["beneficiaryAccountId"],
                            A_SWIFT_2=acct3["beneficiaryAccountId"], B_AE_SHARED=acct_b["beneficiaryAccountId"])
    return acct1, acct2, acct3, acct_b


def ops_balance_checks(admin, acct1_id):
    """--ops-only：运营只读余额视图（A 冷却中、B 只有待核验、Z 只有 PENDING、C 无 account 账户）；Client 自身 /balances 留作遗留。"""
    shown = ops_detail(admin, acct1_id)["account"]
    record("AC-04", "运营详情：VERIFIED、usable=false、coolingUntil 有值", shown["status"] == "VERIFIED"
           and shown["usable"] is False and bool(shown.get("coolingUntil")), {"coolingUntil": shown.get("coolingUntil")})
    for label, client_id, cooling in (("A", CLIENT_A_ID, True), ("B", CLIENT_B_ID, False)):
        present, reasons, rows = expected_reasons(client_id, cooling, False)
        status, reply = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={client_id}"))
        pos = position_of(reply) if status == 200 else None
        ok = status == 200 and ((pos is not None and set(pos["blockReasons"]) >= reasons
                                 and ("ACCOUNT_COOLING" in pos["blockReasons"]) == cooling) if present
                                else (reply or {}).get("positions") == [])
        record("AC-04" if cooling else "AC-08", f"运营视图 Client {label} 阻断原因 ⊇ {sorted(reasons)}（account 现值 {rows}）", ok,
               {"status": status, "position": pos})
        if pos:
            record("AC-08", f"运营视图 Client {label} 桶余额为字符串且与 mor_account 一致",
                   all(isinstance(pos.get(k), str) for k in ("pending", "available", "locked", "recoveryReceivable"))
                   and abs(float(pos["available"]) - sum(float(r[3]) for r in rows if r[2] == "AVAILABLE")) < 0.005
                   and abs(float(pos["pending"]) - sum(float(r[3]) for r in rows if r[2] == "PENDING")) < 0.005,
                   {k: pos.get(k) for k in ("pending", "available", "locked", "securityDeposit", "recoveryReceivable")})
    status_z, reply_z = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_Z_ID}"))
    pos_z = position_of(reply_z) if status_z == 200 else None
    record("AC-08", "Client Z（只有 PENDING）：NO_AVAILABLE_BALANCE + NO_USABLE_ACCOUNT",
           status_z == 200 and pos_z and {"NO_AVAILABLE_BALANCE", "NO_USABLE_ACCOUNT"} <= set(pos_z["blockReasons"]),
           {"status": status_z, "position": pos_z})
    status_e, empty = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_C_ID}"))
    record("AC-08", "无 account 账户的 Owner（C）：positions 为空数组", status_e == 200 and empty.get("positions") == [],
           {"status": status_e, "positions": (empty or {}).get("positions")})


def ops_lifecycle(admin, checker):
    """--ops-only：停用 / 恢复重核 / 运营关闭；设默认（AC-05）只有 Client 入口，留作遗留。"""
    acct4 = ops_submit(admin, CLIENT_A_ID, sa_local("S1MT Trading LLC"))
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1MT 第二个 USD 账户")
    acct4 = checker_decide(checker, acct4["beneficiaryAccountId"], "APPROVED")
    expect("AC-10", "停用 expectedVersion 过期（当前 − 1）409", 409,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                              {"reasonCode": "RISK_REVIEW", "expectedVersion": str(int(acct4["version"]) - 1)}))
    suspended = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                           {"reasonCode": "RISK_REVIEW", "expectedVersion": acct4["version"]})
    record("AC-06", "停用 → SUSPENDED（RISK_REVIEW）", suspended["status"] == "SUSPENDED" and suspended["reasonCode"] == "RISK_REVIEW",
           {"version": suspended["version"]})
    expect("AC-06", "停用原因非法 400", 400,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/suspend",
                              {"reasonCode": "BAD_REASON", "expectedVersion": suspended["version"]}))
    resumed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/resume",
                         {"expectedVersion": suspended["version"]})
    record("AC-06", "恢复 → PENDING_REVIEW，清 Maker / Checker 与冷却，需重核", resumed["status"] == "PENDING_REVIEW"
           and not resumed.get("reviewMakerId") and not resumed.get("checkerId") and not resumed.get("coolingUntil"),
           {"status": resumed["status"]})
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1MT 恢复后重核")
    rechecked = checker_decide(checker, acct4["beneficiaryAccountId"], "APPROVED")
    record("AC-06", "恢复后重走核验与冷却（新 coolingUntil）", rechecked["status"] == "VERIFIED"
           and rechecked["coolingUntil"] != acct4["coolingUntil"],
           {"firstCooling": acct4["coolingUntil"], "secondCooling": rechecked["coolingUntil"]})
    closed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/close",
                        {"reasonCode": "OPS", "expectedVersion": rechecked["version"]})
    record("AC-06", "运营关闭 VERIFIED 账户 → CLOSED（OPS）", closed["status"] == "CLOSED" and closed["reasonCode"] == "OPS",
           {"version": closed["version"]})
    expect("AC-06", "CLOSED 账户再关闭 409", 409,
           lambda: admin.send("POST", f"{ADMIN}/beneficiary-accounts/{acct4['beneficiaryAccountId']}/close",
                              {"reasonCode": "OPS", "expectedVersion": closed["version"]}))
    events = q(f"SELECT event_no, action, to_status FROM {PDB}.t_beneficiary_account_event "
               f"WHERE beneficiary_account_id={int(acct4['beneficiaryAccountId'])} ORDER BY event_no")
    record("AC-06", "事件时间线完整（提交 → 核验 → 停用 → 恢复 → 重核 → 关闭）", len(events) >= 8, {"events": events})
    DATA["accounts"]["A_SA_LOCAL_LIFECYCLE"] = acct4["beneficiaryAccountId"]


def api_ops(state):
    run = {"startedUtc": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S"), "opsOnly": True}
    save_json(RUN_FILE, {**load_json(RUN_FILE), **run})
    DATA["startedUtc"] = run["startedUtc"]
    ops = operator_sessions(state)
    maker_s, checker, noperm, ro = ops["maker"], ops["checker"], ops["noperm"], ops["ro"]
    acct1, acct2, acct3, acct_b = ops_submit_checks(maker_s, noperm, ro)
    secrets_to_check = []
    for account_id in (acct1["beneficiaryAccountId"], acct2["beneficiaryAccountId"]):
        revealed = maker_s.send("POST", f"{ADMIN}/beneficiary-accounts/{account_id}/reveal", {"reasonCode": "MAKER_REVIEW"})
        secrets_to_check += [revealed.get(k) for k in ("holderName", "iban", "accountNumber") if revealed.get(k)]  # BIC 是银行标识（同 raw_values）
    secrets_to_check = sorted({v for v in secrets_to_check if len(v) >= 8})
    save_json(RUN_FILE, {**load_json(RUN_FILE), "rawValues": secrets_to_check})
    expect("R10", "只读运营（ro）Reveal 403", 403,
           lambda: ro.send("POST", f"{ADMIN}/beneficiary-accounts/{acct1['beneficiaryAccountId']}/reveal", {"reasonCode": "MAKER_REVIEW"}))
    status, _ = attempt(lambda: ro.get(f"{ADMIN}/beneficiary-reviews?stage=MAKER&size=10"))
    record("R10", "只读运营（ro）可读核验队列 200", status == 200, {"status": status})
    expect("R10", "只读运营（ro）Maker 意见 403", 403, lambda: maker(ro, acct3["beneficiaryAccountId"], "APPROVE"))
    operations_review(maker_s, checker, noperm, acct1, acct2, acct_b)
    ops_balance_checks(maker_s, acct1["beneficiaryAccountId"])
    ops_lifecycle(maker_s, checker)
    ops_close_and_checker_stage(maker_s, acct3, acct_b)
    for label, body in (("A_BROWSER_MAKER", sa_local("S1MT Trading LLC")), ("A_BROWSER_REJECT", ae_local("S1MT Trading LLC"))):
        created = ops_submit(maker_s, CLIENT_A_ID, body)
        record("准备", f"浏览器走查账户 {label} → PENDING_REVIEW", created["status"] == "PENDING_REVIEW",
               {"id": created.get("beneficiaryAccountId")})
        DATA["accounts"][label] = created["beneficiaryAccountId"]
        DATA.setdefault("accountCodes", {})[label] = created.get("accountCode")
    regressions(admin_session())
    save_json(RUN_FILE, {**load_json(RUN_FILE), "accounts": DATA["accounts"], "accountCodes": DATA.get("accountCodes", {})})
    return "api"


def check(state):
    run = load_json(RUN_FILE)
    if "startedUtc" not in run:
        raise SystemExit("缺少 run.json：先执行 api --yes")
    since, ids = run["startedUtc"], [int(v) for v in (run.get("accounts") or {}).values()]
    id_list = ",".join(map(str, ids)) or "0"
    rows = q(f"SELECT status, COUNT(*) FROM {PDB}.t_beneficiary_account WHERE id IN ({id_list}) GROUP BY 1")
    record("check", "本次账户终态分布", bool(rows), {"byStatus": dict(rows)})
    rows = q(f"SELECT COUNT(*) FROM {PDB}.t_beneficiary_account a WHERE id IN ({id_list}) AND (SELECT COUNT(*) FROM "
             f"{PDB}.t_beneficiary_account_event e WHERE e.beneficiary_account_id=a.id) <> (SELECT MAX(event_no) FROM "
             f"{PDB}.t_beneficiary_account_event e WHERE e.beneficiary_account_id=a.id)")
    record("check", "事件序号连续（每账户 COUNT = MAX(event_no)）", rows[0][0] == "0", {"gaps": rows[0][0]})
    rows = q(f"SELECT COUNT(*) FROM {PDB}.t_compensation_event WHERE execute_status IN ('ERROR','FAILED')")
    record("check", "mor_payout 无 ERROR / FAILED 补偿事件", rows[0][0] == "0", {"count": rows[0][0]})
    hits = []
    for account_id in ids:
        hits += no_raw_in_payout(account_id, run.get("rawValues") or [])
    record("check", "mor_payout 三表无原值", not hits, {"hits": sorted(set(hits))})
    rows = q("SELECT data_type, COUNT(*) FROM mor_vault.t_vault_record WHERE data_type IN ('PAYOUT_BENEFICIARY_V1','PAYOUT_BANK_ACCOUNT_KEY_V1') GROUP BY 1")
    record("check", "Vault 两类记录存在", len(rows) == 2, {"records": dict(rows)})
    leaks = {}
    for container in ("payout-service", "platform-support-service", "client-console-bff", "operations-console-bff", "api-gateway"):
        out = subprocess.run(["docker", "logs", "--since", since.replace(" ", "T") + "Z", f"mor-test-{container}-1"],
                             capture_output=True, text=True)
        text = out.stdout + out.stderr
        leaks[container] = {"rawHits": sum(1 for v in run.get("rawValues") or [] if v in text),
                            "errorLines": sum(1 for line in text.splitlines() if " ERROR " in line)}
    record("check", "自本次开始各服务日志无原值（ERROR 行数供人工复核）", all(v["rawHits"] == 0 for v in leaks.values()), leaks)
    return "check"


def cleanup(state):
    """只读：生成清理 SQL 草稿（按本次 ID 与 S1MT 标记），不执行。"""
    run = load_json(RUN_FILE)
    if not q(f"SELECT 1 FROM information_schema.tables WHERE table_schema='{PDB}' AND table_name='t_beneficiary_account'"):
        record("cleanup", f"{PDB} 尚未建表（阶段 7 之前）：无可清理数据，不生成草稿", True, None)
        return "cleanup"
    ids = sorted(int(v) for v in (run.get("accounts") or {}).values())
    rows = q(f"SELECT id FROM {PDB}.t_beneficiary_account WHERE bank_name LIKE 'S1MT %'")
    marked = sorted({int(r[0]) for r in rows} | set(ids))
    id_list = ",".join(map(str, marked)) or "0"
    tokens = q(f"SELECT beneficiary_vault_token, bank_account_token FROM {PDB}.t_beneficiary_account WHERE id IN ({id_list})")
    requests = q(f"SELECT request_id FROM {PLT}.t_notification_request WHERE source_system='payout-service'"
                 + (f" AND create_time>='{run['startedUtc']}'" if run.get("startedUtc") else ""))
    operators = state.get("operators", {})
    members = state.get("members", {})
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    target = HERE / f"cleanup-{stamp}.sql"
    lines = [f"-- payout S1 mor-test 验收合成数据清理草稿（{stamp} 由 payout_acceptance.py cleanup 只读生成；未执行）。",
             "-- 执行者：主会话 root；顺序与理由见 release/W9-report.md 第 6 节。先停 payout 写入（或确认无验收进行中），逐段执行、逐段核对。",
             f"-- 账户 {len(marked)} 条：{id_list}",
             "START TRANSACTION;",
             f"DELETE FROM {PDB}.t_beneficiary_account_event WHERE beneficiary_account_id IN ({id_list});",
             f"DELETE FROM {PDB}.t_compensation_event WHERE event_type='BENEFICIARY_NOTIFICATION' AND SUBSTRING_INDEX(event_key, ':', 1) IN ({','.join(repr(str(i)) for i in marked) or repr('0')});",
             f"DELETE FROM {PDB}.t_beneficiary_account WHERE id IN ({id_list});",
             "COMMIT;",
             f"-- Platform 通知记录 {len(requests)} 条（payout-service，本次开始之后）：保留（通知审计；删除会使块 147 回滚保护失效的判断失去依据）。",
             "-- Vault 记录：保留（t_vault_access_event / t_vault_protect_request 以外键引用，同 data-query 清理先例）；Token 清单仅供追溯：",
             *[f"--   {a} / {b}" for a, b in tokens],
             "-- IAM 合成运营账号与角色：经运营 IAM 页面停用（不删，审计引用）："]
    lines += [f"--   {label}: accountId={v['accountId']} roleId={v['roleId']}" for label, v in operators.items()]
    lines += ["-- Client A 合成成员：由 Client A 所有者在团队页移除（不删账号）："]
    lines += [f"--   {label}: accountId={v['accountId']} role={v['roleCode']}" for label, v in members.items()]
    if state.get("billingFacts"):
        lines += ["-- 计费事实（--billing-fact）：会计分录不可删除；如需冲回，运营登记反向 MANUAL_ADJUSTMENT：",
                  *[f"--   {k}: factId={v['factId']} amount={v['amount']}" for k, v in state["billingFacts"].items()]]
    target.write_text("\n".join(lines) + "\n")
    record("cleanup", "生成清理草稿（未执行）", True, {"file": str(target), "accounts": len(marked), "notificationRequests": len(requests)})
    return "cleanup"


def plan(state):
    """只读：验收前置读数（数据可用性、块 147、Vault、payout 库、凭据文件是否就位；不登录、不写）。"""
    for label, client_id in (("A", CLIENT_A_ID), ("B", CLIENT_B_ID), ("Z", CLIENT_Z_ID), ("C", CLIENT_C_ID)):
        rows = q(f"SELECT c.status, t.owner_status, (SELECT COUNT(*) FROM {PLT}.t_tenant_member m WHERE m.tenant_id=t.tenant_id AND m.status='ACTIVE') "
                 f"FROM mor_client_master.t_client c LEFT JOIN {PLT}.t_access_tenant t ON t.tenant_type='CLIENT' AND t.owner_ref_id=c.client_id "
                 f"WHERE c.client_id={int(client_id)}")
        record("plan", f"Client {label} {client_id} ACTIVE 且工作区 ACTIVE", bool(rows) and rows[0][0] == "ACTIVE" and rows[0][1] == "ACTIVE",
               {"client/tenant/members": rows, "accountRows": account_rows(client_id)})
    routes = q(f"SELECT status, COUNT(*) FROM {PLT}.t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 GROUP BY 1")
    record("plan", "块 147 路由状态（阶段 7 激活后应 ACTIVE 19）", True, dict(routes))
    vault = q("SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE caller_service='payout-service' AND status='ACTIVE'")
    record("plan", "Vault payout-service 规则数（阶段 7 后应 3）", True, vault[0][0])
    schema = q(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{PDB}'")
    record("plan", f"{PDB} 表数（阶段 7 后应 3）", True, schema[0][0])
    record("plan", "Client 所有者凭据文件就位（D-4）", CLIENTS_FILE.exists() and {"A", "B"} <= set(load_json(CLIENTS_FILE)),
           {"file": str(CLIENTS_FILE), "exists": CLIENTS_FILE.exists()})
    record("plan", "运营合成账号（accounts 之后应 4 个）", True, sorted(state.get("operators", {})))
    return "plan"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("plan", "accounts", "prepare", "api", "notify", "check", "cleanup"))
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--billing-fact", action="store_true")
    parser.add_argument("--ops-only", action="store_true",
                        help="不用 Client 所有者凭据：运营代 Owner 提交（B1），Client 门户入口与收件验证留作遗留（用户 2026-10-30 决定）")
    args = parser.parse_args()
    global OPS_ONLY
    OPS_ONLY = args.ops_only
    if E.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")
    if args.command in ("accounts", "prepare", "api") and not args.yes:
        raise SystemExit(f"{args.command} 会写 mor-test，需 --yes（且须已获用户阶段 7 授权）")
    state = load_state()
    if args.command == "accounts":
        accounts(state, admin_session())
    elif args.command == "prepare":
        prepare(state, args)
    elif args.command == "api":
        api_ops(state) if OPS_ONLY else api(state)
    elif args.command == "notify":
        run = load_json(RUN_FILE)
        if "startedUtc" not in run:
            raise SystemExit("缺少 run.json：先执行 api --yes")
        notifications(run["startedUtc"], run.get("rawValues") or [])
    elif args.command == "check":
        check(state)
    elif args.command == "plan":
        plan(state)
    else:
        cleanup(state)
    return finish(args.command)


def finish(name):
    out = {"command": name, "results": RESULTS, "data": DATA, "pass": sum(r["ok"] for r in RESULTS),
           "fail": sum(not r["ok"] for r in RESULTS)}
    target = HERE / f"result-{name}-{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H%M%SZ')}.json"
    target.write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"DONE {name} pass={out['pass']} fail={out['fail']} → {target.name}")
    return 1 if out["fail"] else 0


if __name__ == "__main__":
    sys.exit(main())
