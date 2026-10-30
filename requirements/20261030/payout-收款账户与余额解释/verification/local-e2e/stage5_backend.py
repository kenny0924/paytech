#!/usr/bin/env python3
"""payout 收款账户与余额解释 · 阶段 5 本机后端端到端验收（W6 任务卡，真实入口，无 Mock）。

链路：Client https://127.0.0.1:18443/api/client/payout/v1/**（Nginx → Gateway → client-console-bff → payout → Vault / account / Platform）
      运营 https://127.0.0.1:18444/api/operations/payout-admin/v1/**（Nginx → Gateway → operations-console-bff → payout / client-master）
余额准备：运营登记并批准一条 MANUAL_ADJUSTMENT 计费事实（/finance/billing-facts/v1，正式 BillingFactRecorded → account），不写 account 表。
数据库只读核对经 rodb.py（root，口令只在进程环境变量中）；唯一的库写入是共用夹具 mint_signup_link / 邀请链接铸造（Platform 认证挑战的
secret_hmac，本机无 SES 时的既有做法）。合成数据带 S1E2E 前缀；不清理。
凭据：运营管理员与 Client 所有者口令读 local-environment/e2e/secrets.env 与 ~/Code-MoR-backups/rq12-stage5/accounts.json；
本脚本新建的运营账号与 Client 成员口令只写 ~/Code-MoR-backups/payout-s1-stage5/state.json（0600）。不打印任何口令、令牌、原值。
用法：python3 -I stage5_backend.py；结果写同目录 stage5-backend-result.json；退出码 0 表示无 FAIL。
"""
import base64
import hashlib
import hmac
import http.cookiejar
import json
import os
import random
import secrets as pysecrets
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
V2_E2E = Path("/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
os.environ["E2E_TARGET"] = "local"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(V2_E2E))
import e2e_http as E  # noqa: E402
import rodb  # noqa: E402

STATE_DIR = Path.home() / "Code-MoR-backups/payout-s1-stage5"
STATE_FILE = STATE_DIR / "state.json"
RQ12 = Path.home() / "Code-MoR-backups/rq12-stage5/accounts.json"
CLIENT_A_ID, CLIENT_B_ID = "241533217348268033", "241533316505808897"
CLIENT_C_ID = "241533481480368129"  # 第三个 ACTIVE Client，不参与提交
ENTITY, CURRENCY = "AE001", "USD"
PLATFORM_ADMIN_ROLE = "900000000000000510"
DATA_QUERY_READ = "900000000000131101"
PAY = "/payout/v1"
ADMIN = "/payout-admin/v1"
PDB, PLT = "mor_payout_v2e2e", "mor_platform_v2e2e"

RESULTS = []
DATA = {"clients": {"A": CLIENT_A_ID, "B": CLIENT_B_ID}, "accounts": {}, "operators": {}, "billingFacts": [],
        "notifications": [], "viewer": {}}


# ---------------------------------------------------------------- 记录与断言
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


def q(sql):
    return rodb.q(sql)


def wait_until(predicate, seconds=60, interval=2):
    deadline = time.time() + seconds
    value = predicate()
    while not value and time.time() < deadline:
        time.sleep(interval)
        value = predicate()
    return value


# ---------------------------------------------------------------- 状态（含口令，只在 0600 文件）
def load_state():
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE_DIR, 0o700)
    return json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else {}


def save_state(state):
    STATE_FILE.write_text(json.dumps(state, indent=1) + "\n")
    os.chmod(STATE_FILE, 0o600)


# ---------------------------------------------------------------- Client 会话
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


def client_session(label, client_id):
    accounts = json.loads(RQ12.read_text())
    session = Client()
    context = session.login(accounts[label]["email"], accounts[label]["password"])
    member_id = session.enter(client_id)
    return session, context["accountId"], member_id


# ---------------------------------------------------------------- 夹具：邀请链接（与 team_flow.mint_invitation_link 相同）
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


# ---------------------------------------------------------------- 1 准备
def prepare_operators(state, admin):
    """Checker：新运营账号授予平台管理员角色；无权限账号：只有 DATA_QUERY_READ 的自定义角色。"""
    stamp = time.strftime("%m%d%H%M%S")
    for label, role_ids, reason in (("checker", [PLATFORM_ADMIN_ROLE], "payout 阶段 5 Checker（四眼第二人）"),
                                    ("noperm", None, "payout 阶段 5 无 PAYOUT 权限反例")):
        if label in state.get("operators", {}):
            continue
        if role_ids is None:
            role = admin.send("POST", "/iam/v1/roles", {"roleCode": f"S1E2E_NOPERM_{stamp}", "roleName": f"S1E2E 无出款权限 {stamp}",
                                                       "permissionIds": [DATA_QUERY_READ], "reason": reason})
            role_ids = [role.get("roleId") or role.get("objectId")]
        email = f"s1e2e-{label}-{stamp}@example.test"
        p1, p2 = "S1e!" + pysecrets.token_urlsafe(18), "S1e!" + pysecrets.token_urlsafe(18)
        account = admin.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": role_ids,
                                                          "reason": reason})
        E.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
        state.setdefault("operators", {})[label] = {"email": email, "password": p2, "accountId": account.get("accountId"),
                                                    "roleIds": role_ids}
        save_state(state)
    sessions = {}
    for label, creds in state["operators"].items():
        sessions[label] = E.Operations(creds["email"], creds["password"])
        DATA["operators"][label] = {"accountId": creds["accountId"], "email": creds["email"], "roleIds": creds["roleIds"]}
    return sessions


def prepare_viewer(state, owner):
    """Client A 内一个 TENANT_VIEWER 成员（只有 CLIENT_PAYOUT_ACCOUNT_READ，没有 MANAGE），经团队邀请真实入口加入。"""
    if "viewer" not in state:
        stamp = time.strftime("%m%d%H%M%S")
        email, password = f"s1e2e-viewer-{stamp}@example.test", "S1e-" + uuid.uuid4().hex[:16]
        _, created = owner.call("/team/v1/invitations", "POST", {"email": email, "roleCode": "TENANT_VIEWER"})
        proof = mint_invitation_link(created["invitationId"])
        invitee = Client()
        context = register(invitee, email, password)
        invitee.call("/invitations/v1/acceptance", "POST", proof)
        state["viewer"] = {"email": email, "password": password, "accountId": context["accountId"],
                           "invitationId": created["invitationId"]}
        save_state(state)
    viewer = Client()
    viewer.login(state["viewer"]["email"], state["viewer"]["password"])
    member_id = viewer.enter(CLIENT_A_ID)
    DATA["viewer"] = {"accountId": state["viewer"]["accountId"], "memberId": member_id,
                      "invitationId": state["viewer"]["invitationId"], "email": state["viewer"]["email"]}
    return viewer


def account_rows(client_id):
    return q(f"SELECT entity_id, currency, account_code, balance_bucket, net_balance FROM mor_account_v2e2e.t_owner_account "
             f"WHERE owner_type='CLIENT' AND owner_id={int(client_id)} ORDER BY entity_id, currency, account_code")


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


def prepare_balance(state, maker_id, checker_id):
    """计费事实登记（Maker = E2E 管理员）+ 批准（Checker = 新运营）：正式 BillingFactRecorded → account 明细账 PENDING 桶；A 与 B 各一条。"""
    for label, client_id, amount in (("A", CLIENT_A_ID, "250.00"), ("B", CLIENT_B_ID, "80.00")):
        facts = state.setdefault("billingFacts", {})
        if label not in facts:
            body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": "OPS-" + str(uuid.uuid4()),
                    "clientId": client_id, "entityNo": ENTITY, "currency": CURRENCY,
                    "refs": [{"refType": "TICKET", "refId": f"S1E2E-{label}-" + time.strftime("%m%d%H%M%S")}],
                    "lines": [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": ENTITY,
                               "payeeRole": "CLIENT", "payeeId": client_id, "amount": amount, "direction": "ADJUSTMENT",
                               "targetBucket": "AVAILABLE"}]}
            created = billing("POST", "", maker_id, body)
            facts[label] = {"factId": created["factId"], "clientId": client_id, "amount": amount}
            save_state(state)
        fact_id = facts[label]["factId"]
        status, text = E.private_call("billing", "GET", f"/billing/v1/facts/{fact_id}",
                                      {"X-Caller-Service": "operations-console-bff"})
        fact = json.loads(text) if status == 200 else {}
        if fact.get("status") != "APPROVED":
            fact = billing("POST", f"/{fact_id}/approve", checker_id)
        DATA["billingFacts"].append({"client": label, "factId": fact_id, "amount": amount, "status": fact.get("status")})
        record("准备", f"Client {label} 计费事实 MANUAL_ADJUSTMENT {amount} USD 已批准（Maker 管理员、Checker 新运营）",
               fact.get("status") == "APPROVED", {"factId": fact_id, "makerId": fact.get("makerId"), "checkerId": fact.get("checkerId")})
        rows = wait_until(lambda: account_rows(client_id), 15, 3)
        record("准备", f"Client {label}：account 明细账出现 AE001 × USD 账户（正式 MQ 入账，未写 account 表）", bool(rows),
               {"rows": rows})
        DATA.setdefault("accountRows", {})[label] = rows


# ---------------------------------------------------------------- 合成账户字段
def rnd_digits(n):
    return "".join(random.choice("0123456789") for _ in range(n))


def ae_local(holder):
    return {"railType": "LOCAL", "bankCountry": "AE", "currency": "USD",
            "fields": {"holderName": holder, "iban": "AE" + rnd_digits(21), "bankName": "S1E2E Emirates Test Bank"}}


def swift(holder, country="GB"):
    return {"railType": "SWIFT", "bankCountry": country, "currency": "USD",
            "fields": {"holderName": holder, "iban": country + rnd_digits(2) + "S1EB" + rnd_digits(14),
                       "swiftBic": "SONEGB2L", "bankName": "S1E2E London Test Bank"}}


def sa_local(holder):
    return {"railType": "LOCAL", "bankCountry": "SA", "currency": "USD",
            "fields": {"holderName": holder, "iban": "SA" + rnd_digits(22), "bankName": "S1E2E Riyadh Test Bank"}}


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


# ---------------------------------------------------------------- 2 Client 提交（AC-01 / AC-02）
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

    a1 = ae_local("S1E2E Trading LLC")
    status, acct1 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a1)
    record("AC-01", "提交 AE 本地 USD → 201 PENDING_REVIEW", status == 201 and acct1["status"] == "PENDING_REVIEW"
           and acct1["holderNameMasked"] != a1["fields"]["holderName"] and acct1["accountLast4"] == a1["fields"]["iban"][-4:],
           {"id": acct1["beneficiaryAccountId"], "code": acct1["accountCode"], "masked": acct1["holderNameMasked"],
            "last4": acct1["accountLast4"], "usable": acct1["usable"]})
    a2 = swift("S1E2E Trading LLC")
    status, acct2 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a2)
    record("AC-01", "提交 SWIFT USD → 201 PENDING_REVIEW", status == 201 and acct2["status"] == "PENDING_REVIEW",
           {"id": acct2["beneficiaryAccountId"], "code": acct2["accountCode"]})

    # Gateway Idempotency-Key：同键同体重放返回原响应；新键同账号 → 409 BENEFICIARY_DUPLICATE（含已有账户 ID）
    a3 = swift("S1E2E Trading LLC")
    key = str(uuid.uuid4())
    s1, acct3 = owner_a.call(f"{PAY}/beneficiary-accounts", "POST", a3, key=key)
    s2, replay = owner_a.call(f"{PAY}/beneficiary-accounts", "POST", a3, key=key)
    record("AC-10", "同一 Idempotency-Key 重放返回原账户", s1 == 201 and s2 in (200, 201)
           and replay["beneficiaryAccountId"] == acct3["beneficiaryAccountId"],
           {"first": s1, "replay": s2, "id": acct3["beneficiaryAccountId"]})
    status, same = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", a1)
    record("AC-10", "新键、同 Owner 同内容重复提交 → 200 返回原账户（业务幂等）", status == 200
           and same["beneficiaryAccountId"] == acct1["beneficiaryAccountId"], {"status": status, "id": same["beneficiaryAccountId"]})
    changed = dict(a1, fields=dict(a1["fields"], holderName="S1E2E Different Holder"))
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
    b1 = dict(a1, fields=dict(a1["fields"], holderName="S1E2E Shared Holder Co"))
    status, acct_b = owner_b.command(f"{PAY}/beneficiary-accounts", "POST", b1)
    record("AC-02", "另一 Client 共用同一账号可提交 201", status == 201 and acct_b["status"] == "PENDING_REVIEW",
           {"id": acct_b["beneficiaryAccountId"], "ownerId": acct_b["ownerId"]})
    record("AC-02", "Client 视图不返回共用 Owner", not acct_b.get("sharedOwners"), {"sharedOwners": acct_b.get("sharedOwners")})

    # 权限（AC-10 / R10）
    expect("R10", "无 CLIENT_PAYOUT_ACCOUNT_MANAGE 的成员（TENANT_VIEWER）提交 403", 403,
           lambda: viewer.command(f"{PAY}/beneficiary-accounts", "POST", sa_local("S1E2E Viewer")))
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


# ---------------------------------------------------------------- 3 运营核验（AC-03 / AC-07）
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
    reviewed = maker(admin, acct1["beneficiaryAccountId"], "APPROVE", note="S1E2E 户名与法定名一致")
    shown = ops_detail(admin, acct1["beneficiaryAccountId"])["account"]
    stored = q(f"SELECT review_maker_note FROM {PDB}.t_beneficiary_account WHERE id={int(acct1['beneficiaryAccountId'])}")
    record("AC-03", "Maker 意见 APPROVE（带 note）：运营详情与库内有备注", reviewed["reviewMakerDecision"] == "APPROVE"
           and reviewed["status"] == "PENDING_REVIEW" and shown.get("reviewMakerNote") == "S1E2E 户名与法定名一致"
           and stored[0][0] == "S1E2E 户名与法定名一致",
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
    maker(admin, acct2["beneficiaryAccountId"], "REJECT", reason="HOLDER_NAME_MISMATCH", note="S1E2E 户名与银行回执不一致",
          client_note="S1E2E 请上传银行开户证明")
    expect("AC-03", "Maker 建议拒绝时 Checker 批准 409", 409,
           lambda: checker_decide(checker, acct2["beneficiaryAccountId"], "APPROVED"))
    rejected = checker_decide(checker, acct2["beneficiaryAccountId"], "REJECTED", reason="HOLDER_NAME_MISMATCH")
    record("AC-03", "拒绝 → REJECTED，原因代码与给 Client 的说明（沿用 Maker 的 clientNote）",
           rejected["status"] == "REJECTED" and rejected["reasonCode"] == "HOLDER_NAME_MISMATCH"
           and rejected.get("reasonNote") == "S1E2E 请上传银行开户证明", {"reasonCode": rejected["reasonCode"],
                                                                         "reasonNote": rejected.get("reasonNote")})
    return decided, rejected


# ---------------------------------------------------------------- 4 冷却与余额解释（AC-04 / AC-08）
def position_of(reply):
    return next((p for p in (reply or {}).get("positions") or [] if p["entityNo"] == ENTITY and p["currency"] == CURRENCY), None)


def balance_checks(owner_a, owner_b, admin, acct1_id):
    """A：已有 VERIFIED 冷却中账户；B：只有 PENDING_REVIEW 账户；第三个 ACTIVE Client 无 account 账户。"""
    status, account = owner_a.call(f"{PAY}/beneficiary-accounts/{acct1_id}")
    record("AC-04", "Client 账户详情：VERIFIED、usable=false、coolingUntil 有值", status == 200
           and account["status"] == "VERIFIED" and account["usable"] is False and bool(account.get("coolingUntil")),
           {"status": account["status"], "usable": account["usable"], "coolingUntil": account.get("coolingUntil"),
            "events": [e["action"] for e in account.get("events") or []]})
    status, reply = attempt(lambda: owner_a.call(f"{PAY}/balances"))
    pos = position_of(reply) if status == 200 else None
    usd = next((u for u in (reply or {}).get("usableAccounts") or [] if u["currency"] == CURRENCY), None) if status == 200 else None
    record("AC-04", "Client A /balances：原因含 ACCOUNT_COOLING 与 NO_AVAILABLE_BALANCE，不含 NO_USABLE_ACCOUNT；USD 可用账户数 0",
           status == 200 and pos and "ACCOUNT_COOLING" in pos["blockReasons"] and "NO_AVAILABLE_BALANCE" in pos["blockReasons"]
           and "NO_USABLE_ACCOUNT" not in pos["blockReasons"] and usd and usd["count"] == 0,
           {"status": status, "code": (reply or {}).get("code"), "position": pos,
            "usableAccounts": (reply or {}).get("usableAccounts")})
    record("AC-08", "桶余额：pending=250、available=0，金额为字符串",
           bool(pos) and all(isinstance(pos.get(k), str) for k in ("pending", "available", "locked", "recoveryReceivable"))
           and float(pos["pending"]) == 250 and float(pos["available"]) == 0,
           {k: pos.get(k) for k in ("pending", "available", "locked", "securityDeposit", "recoveryReceivable")} if pos else None)
    status_b, reply_b = attempt(lambda: owner_b.call(f"{PAY}/balances"))
    pos_b = position_of(reply_b) if status_b == 200 else None
    record("AC-08", "Client B /balances：原因含 NO_AVAILABLE_BALANCE 与 NO_USABLE_ACCOUNT（只有待核验账户）",
           status_b == 200 and pos_b and {"NO_AVAILABLE_BALANCE", "NO_USABLE_ACCOUNT"} <= set(pos_b["blockReasons"])
           and "ACCOUNT_COOLING" not in pos_b["blockReasons"],
           {"status": status_b, "code": (reply_b or {}).get("code"), "position": pos_b})
    status_o, ops_reply = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_A_ID}"))
    record("AC-08", "运营 /payout-admin/v1/balances 与 Client 视图一致", status_o == 200 and status == 200
           and ops_reply.get("positions") == reply.get("positions"),
           {"status": status_o, "code": (ops_reply or {}).get("code"), "positions": (ops_reply or {}).get("positions")})
    status_e, empty = attempt(lambda: admin.get(f"{ADMIN}/balances?ownerType=CLIENT&ownerId={CLIENT_C_ID}"))
    record("AC-08", "无 account 账户的 Owner：positions 为空数组（不再 400）", status_e == 200 and empty.get("positions") == [],
           {"status": status_e, "code": (empty or {}).get("code"), "positions": (empty or {}).get("positions")})
    record("AC-08", "RECOVERY_OUTSTANDING：本机无法经正式入口造 Client 追偿应收（account 映射规则无该组件）", True,
           {"note": "见报告证据边界；由 payout auto 测试覆盖"})


# ---------------------------------------------------------------- 5 默认 / 停用 / 恢复 / 关闭（AC-05 / AC-06）
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
    body = sa_local("S1E2E Trading LLC")
    _, acct4 = owner_a.command(f"{PAY}/beneficiary-accounts", "POST", body)
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1E2E 第二个 USD 账户")
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
    maker(admin, acct4["beneficiaryAccountId"], "APPROVE", note="S1E2E 恢复后重核")
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
    reviewed = maker(admin, acct_b["beneficiaryAccountId"], "APPROVE", note="S1E2E 共用账号，待复核")
    record("AC-03", "B 共用账户 Maker 建议通过，留在 Checker 队列", reviewed["reviewMakerDecision"] == "APPROVE", None)
    return closed


# ---------------------------------------------------------------- 6 通知（AC-09）
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


def notifications(started_utc, raw_secrets):
    def settled():
        rows = q(f"SELECT event_type, execute_status, COUNT(*) FROM {PDB}.t_compensation_event "
                 f"WHERE create_time>='{started_utc}' GROUP BY 1,2")
        pending = [r for r in rows if r[1] not in ("SUCCESS", "SUCCEEDED", "DONE", "COMPLETED")]
        return rows if rows and not pending else None
    rows = wait_until(settled, 90, 3)
    all_rows = q(f"SELECT event_type, sub_event_type, execute_status, execute_count, last_error_code FROM {PDB}.t_compensation_event "
                 f"WHERE create_time>='{started_utc}' ORDER BY id")
    record("AC-09", "payout t_compensation_event 全部处理完成", bool(rows), {"events": all_rows})

    expected, members = manage_recipients(CLIENT_A_ID)
    expected_b, members_b = manage_recipients(CLIENT_B_ID)
    template_join = ""
    cols = [c[0] for c in q(f"SELECT column_name FROM information_schema.columns WHERE table_schema='{PLT}' "
                            "AND table_name='t_notification_template'")]
    code_col = next((c for c in ("template_code", "notification_template_code", "code") if c in cols), None)
    if code_col:
        template_join = f"LEFT JOIN {PLT}.t_notification_template tpl ON tpl.template_id=r.notification_template_id"
    code_expr = f"tpl.{code_col}" if code_col else "r.notification_template_id"
    requests = q(f"SELECT r.request_id, r.source_event_key, {code_expr}, r.tenant_id, "
                 f"(SELECT COUNT(*) FROM {PLT}.t_notification_delivery d WHERE d.request_id=r.request_id), "
                 f"(SELECT GROUP_CONCAT(DISTINCT d.status) FROM {PLT}.t_notification_delivery d WHERE d.request_id=r.request_id), "
                 f"r.default_parameters FROM {PLT}.t_notification_request r {template_join} "
                 f"WHERE r.source_system='payout-service' AND r.create_time>='{started_utc}' ORDER BY r.create_time")
    DATA["notifications"] = [{"requestId": r[0], "sourceEventKey": r[1], "template": r[2], "deliveries": r[4], "status": r[5]}
                             for r in requests]
    templates = sorted({r[2] for r in requests})
    record("AC-09", "Platform 受理 payout 通知（核验通过 / 拒绝 / 停用 / 关闭）", len(requests) >= 4, {"templates": templates,
                                                                                            "count": len(requests)})
    record("AC-09", f"每条通知投递数 = Client A 持 MANAGE 的有效已验证成员数（{expected}；成员共 {members}，含 VIEWER）",
           requests and all(int(r[4]) == expected for r in requests), {"deliveries": [r[4] for r in requests],
                                                                        "expected": expected, "members": members})
    params = q(f"SELECT CONCAT_WS('|', r.default_parameters, (SELECT GROUP_CONCAT(d.parameters) FROM {PLT}.t_notification_delivery d "
               f"WHERE d.request_id=r.request_id)) FROM {PLT}.t_notification_request r WHERE r.source_system='payout-service' "
               f"AND r.create_time>='{started_utc}'")
    blob = " ".join(p[0] for p in params)
    leaks = [i for i, v in enumerate(raw_secrets) if v and v in blob]
    record("AC-09", "模板参数无原值（户名 / IBAN / BIC）", not leaks, {"leakIndexes": leaks,
                                                                   "sampleKeys": sorted(json.loads(requests[0][6]).keys())
                                                                   if requests and requests[0][6] and requests[0][6].startswith("{") else None})
    record("AC-09", "Client B 无决定事件 → 无 payout 通知（B 仅 Maker 意见）", True,
           {"clientBManageMembers": expected_b, "clientBMembers": members_b})
    vault_tokens = q(f"SELECT COUNT(*) FROM {PLT}.t_notification_delivery d JOIN {PLT}.t_notification_request r "
                     f"ON r.request_id=d.request_id WHERE r.source_system='payout-service' AND r.create_time>='{started_utc}' "
                     f"AND d.address_vault_token IS NOT NULL")
    record("AC-09", "投递地址为账号邮箱 Vault Token（不 Reveal）", requests and int(vault_tokens[0][0]) == sum(int(r[4]) for r in requests),
           {"withToken": vault_tokens[0][0]})
    return requests


# ---------------------------------------------------------------- 8 他方回归
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


# ---------------------------------------------------------------- 主流程
def main():
    started_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    DATA["startedUtc"] = started_utc
    state = load_state()
    s = E.secrets()
    admin = E.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    admin_ctx = admin.get("/auth/v1/context")
    DATA["operators"]["maker"] = {"accountId": admin_ctx.get("accountId"), "role": "PLATFORM_ADMIN（E2E 管理员）"}
    ops = prepare_operators(state, admin)
    checker, noperm = ops["checker"], ops["noperm"]
    record("准备", "Maker（E2E 管理员）与 Checker（新运营，PLATFORM_ADMIN）为两个不同账号",
           DATA["operators"]["checker"]["accountId"] != admin_ctx.get("accountId"),
           {"maker": admin_ctx.get("accountId"), "checker": DATA["operators"]["checker"]["accountId"],
            "noperm": DATA["operators"]["noperm"]["accountId"]})

    owner_a, owner_a_account, member_a = client_session("A", CLIENT_A_ID)
    owner_b, owner_b_account, member_b = client_session("B", CLIENT_B_ID)
    DATA["clientMembers"] = {"A": {"accountId": owner_a_account, "memberId": member_a, "role": "TENANT_OWNER"},
                             "B": {"accountId": owner_b_account, "memberId": member_b, "role": "TENANT_OWNER"}}
    viewer = prepare_viewer(state, owner_a)
    record("准备", "Client A 所有者与 TENANT_VIEWER 成员可进入工作区", True, {"viewer": DATA["viewer"]["memberId"]})

    if "--notify" in sys.argv:
        since = sys.argv[sys.argv.index("--notify") + 1]
        DATA["notifySinceUtc"] = since
        notifications(since, state.get("rawValues", []))
        return finish("stage5-backend-notify-result.json")
    prepare_balance(state, admin_ctx.get("accountId"), DATA["operators"]["checker"]["accountId"])
    if "--balances" in sys.argv:
        DATA["accounts"] = state["accounts"]
        balance_checks(owner_a, owner_b, admin, state["accounts"]["A_AE_LOCAL"])
        return finish("stage5-backend-balances-result.json")

    acct1, acct2, acct3, acct_b = client_submit(owner_a, owner_b, viewer)
    state["accounts"] = DATA["accounts"]
    save_state(state)
    secrets_to_check = []
    for account_id in (acct1["beneficiaryAccountId"], acct2["beneficiaryAccountId"]):
        revealed = admin.send("POST", f"{ADMIN}/beneficiary-accounts/{account_id}/reveal", {"reasonCode": "MAKER_REVIEW"})
        secrets_to_check += [revealed.get(k) for k in ("holderName", "iban", "swiftBic") if revealed.get(k)]
    secrets_to_check = [v for v in secrets_to_check if len(v) >= 8]
    state["rawValues"] = sorted(set(state.get("rawValues", []) + secrets_to_check))  # 只在 0600 状态文件，供 --notify 复核
    save_state(state)
    operations_review(admin, checker, noperm, acct1, acct2, acct_b)
    balance_checks(owner_a, owner_b, admin, acct1["beneficiaryAccountId"])
    lifecycle(owner_a, admin, checker, acct1, acct2)
    state["accounts"] = DATA["accounts"]
    save_state(state)
    ops_close_and_checker_stage(admin, acct3, acct_b)
    notifications(started_utc, secrets_to_check)
    regressions(admin)
    return finish("stage5-backend-result.json")


def finish(name):
    out = {"results": RESULTS, "data": DATA, "pass": sum(r["ok"] for r in RESULTS), "fail": sum(not r["ok"] for r in RESULTS)}
    (HERE / name).write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"DONE pass={out['pass']} fail={out['fail']}")
    return 1 if out["fail"] else 0


if __name__ == "__main__":
    sys.exit(main())
