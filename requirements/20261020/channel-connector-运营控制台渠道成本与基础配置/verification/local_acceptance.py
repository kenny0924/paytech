"""阶段 5 接口验收：经运营入口（Nginx 18444 → Gateway → Operations BFF → channel-connector / Platform / Vault）覆盖块 92 的 31 条路由。

前置（本机）：
  - run-service.sh 已起 platform、channel-connector、ops-bff、gateway、nginx、payermax-mock（新 JAR）；
  - mor_platform_v2e2e 已执行块 92 registration-dml.sql 与 activation-local.sql；mor_vault 已有 channel-connector PROTECT 规则；
    mor_channel_connector_v2e2e 已有 t_channel_config_change；
  - Platform 已有与渠道编码相同的接入方（渠道激活前置）：缺省用本机已登记的 PAYERMAX；E2E_CHANNEL_CODE 可改为
    platform_payermax_setup.py 登记的 PAYERMAX_MOCK。

覆盖：渠道 5、账号 6、MID 5、能力 4、变更记录 1、成本 10 条路由的正向与关键拒绝；Maker-Checker 由 IAM 新建的合成复核人完成；
未授权（只有审计只读权限）的合成人员看不到「渠道」菜单、调用路由 403。账号参数与凭据取合成 PayerMax 的密钥文件，
经真实 Vault Protect 落库为 Token；不打印 Token、口令、密钥或凭据。

用法：
  python3 -I local_acceptance.py              验收；运行记录写 local-environment/e2e/runtime/channel-acceptance-<ts>.json
  python3 -I local_acceptance.py --cleanup    按最新运行记录执行 acceptance-cleanup-local.sql 的 channel-connector 段与 Platform 段
"""
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
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

RUNTIME = ROOT / "local-environment/e2e/runtime"
LOGS = ROOT / "local-environment/logs"
KEYS = RUNTIME / "payermax/payermax-mock-keys.json"
CLEANUP_SQL = HERE / "acceptance-cleanup-local.sql"
BASE = "/channel-admin/v1"
RUN = uuid.uuid4().hex[:6].upper()
# = 本机 Platform 已登记的接入方编码（mor_platform_v2e2e 只有 PAYERMAX；若已按 platform_payermax_setup.py 登记 PAYERMAX_MOCK，设 E2E_CHANNEL_CODE）
MAIN_CODE = os.environ.get("E2E_CHANNEL_CODE", "PAYERMAX")
ENTITY_NO, OTHER_ENTITY = "AE001", "AE002"
CHINESE = re.compile(r"[一-鿿]")
PERM = {"CONFIG_READ": "900000000000092101", "CONFIG_MANAGE": "900000000000092102", "CREDENTIAL": "900000000000092103",
        "COST_READ": "900000000000092104", "COST_MANAGE": "900000000000092105", "COST_APPROVE": "900000000000092106",
        "CHANGE_READ": "900000000000092107", "AUDIT_READ": "900000000000000523"}
results = []
record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat(), "ids": {}}
TEST = e2e_http.TARGET == "test"
RECORD_PREFIX = "channel-acceptance-test-" if TEST else "channel-acceptance-2"
RECORD_PATH = RUNTIME / f"channel-acceptance-{'test-' if TEST else ''}{datetime.now().strftime('%Y%m%d%H%M%S')}.json"


def save():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    RECORD_PATH.write_text(json.dumps(record, ensure_ascii=False, indent=2))


def remember(kind, value):
    if value:
        record["ids"].setdefault(kind, []).append(str(value))
        save()


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:400]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def raw(token, method, path, body=None, extra=None, prefix="/api/operations" + BASE):
    """请求运营入口，返回 (状态, 响应体)。写命令带 Idempotency-Key（Gateway 规则），429 按 Retry-After 重试。"""
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json", **(extra or {})}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(12):
        request = urllib.request.Request(e2e_http.OPERATIONS + prefix + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=e2e_http.TLS, timeout=30) as response:
                text = response.read().decode()
                return response.status, json.loads(text) if text else {}
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 11:
                time.sleep(int(failure.headers.get("Retry-After") or 10))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def rejected(status, body, detail_part=None, code="BUSINESS_CHECK_FAILED", http=400):
    """业务拒绝：HTTP 状态、code、中文 detail（含指定片段）。"""
    detail = body.get("detail") or ""
    return (status == http and (code is None or body.get("code") == code) and bool(CHINESE.search(detail))
            and (detail_part is None or detail_part in detail))


def jwt_claims(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def login(identifier, password):
    return e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                         {"loginIdentifier": identifier, "password": password})["accessToken"]


def iam(token, method, path, body=None):
    return raw(token, method, path, body, prefix="/api/operations")


def synthetic_person(admin, label, permission_ids):
    """经 IAM 新建合成角色与人员，首登改口令后返回 (token, accountId)。"""
    status, role = iam(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_CHANNEL_{label}_{RUN}", "roleName": f"渠道控制台验收 {label} {RUN}",
        "permissionIds": permission_ids, "reason": f"channel-connector 运营控制台阶段 5 验收：合成角色 {label}"})
    if not check("准备", f"IAM 新建合成角色 {label}（{status}）", status in (200, 201) and role.get("roleId"), role):
        return None, None
    remember("platformRoles", role["roleId"])
    email = f"channel-ver-{label.lower()}-{RUN.lower()}@example.test"
    initial = "Channel initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Channel ver " + pysecrets.token_urlsafe(18) + "!"
    status, created = iam(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": f"channel-connector 运营控制台阶段 5 验收：合成人员 {label}"})
    if not check("准备", f"IAM 新建合成人员 {label}（{status}）", status in (200, 201) and created.get("accountId"), created):
        return None, None
    remember("platformAccounts", created["accountId"])
    first = login(email, initial)
    e2e_http.call(e2e_http.OPERATIONS, "/api/operations/auth/v1/password", "POST",
                  {"currentPassword": initial, "newPassword": password}, first)
    return login(email, password), created["accountId"]


def navigation_codes(token):
    _, nav = iam(token, "GET", "/authorization/v1/navigation")
    return {r["resourceCode"]: r for r in nav.get("resources", [])}


CONTAINERS = {"ops-bff": "mor-test-operations-console-bff-1", "channel-connector": "mor-test-channel-connector-service-1",
              "platform": "mor-test-platform-support-service-1"}


def log_offset(name):
    """读日志起点：本机为日志文件字节数；mor-test 为 UTC 时间（回拨 5 秒容忍时钟偏差）。"""
    if TEST:
        return (datetime.now(timezone.utc) - timedelta(seconds=5)).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    path = LOGS / f"{name}.log"
    return path.stat().st_size if path.exists() else 0


def log_since(name, offset):
    if TEST:
        result = subprocess.run(["docker", "logs", "--since", offset, CONTAINERS[name]], capture_output=True, timeout=60)
        return (result.stdout + b"\n" + result.stderr).decode(errors="ignore")
    path = LOGS / f"{name}.log"
    with path.open("rb") as handle:
        handle.seek(offset)
        return handle.read().decode(errors="ignore")


def account_material():
    """本机取合成 PayerMax 密钥文件；mor-test 取每次随机生成的合成值（非真实渠道凭据）。"""
    if not TEST:
        return json.loads(KEYS.read_text())
    return {"appId": "synthetic-app-" + pysecrets.token_hex(6), "merchantNo": "SYN" + pysecrets.token_hex(5).upper(),
            "keyVersion": "1", "payermaxPublicKey": "synthetic-public-" + pysecrets.token_urlsafe(24),
            "merchantPrivateKey": "synthetic-private-" + pysecrets.token_urlsafe(32)}


def cc_sql(statement):
    return e2e_http.sql("channel-connector", statement)


def candidates(currency="USD", country="US"):
    status, text = e2e_http.private_call("channel-connector", "POST", "/channel-connector/v1/mid-candidates", body={
        "entityNo": ENTITY_NO, "criteria": {"paymentMethodCode": "CARD", "schemeCode": None, "countryCode": country,
                                            "currency": currency, "mccCode": "5999"}})
    body = json.loads(text) if text.startswith(("[", "{")) else {}
    items = body if isinstance(body, list) else body.get("items") or body.get("candidates") or []
    return status, items


def capability(mid_id, currency="USD", details=None, country="*"):
    return {"midId": mid_id, "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": country, "currency": currency,
            "mccCode": "5999", "details": details or [
                {"capabilityCode": "ACTION.PAY", "booleanValue": True},
                {"capabilityCode": "ACTION.REFUND", "booleanValue": True},
                {"capabilityCode": "AMOUNT.MIN", "decimalValue": "0.01"},
                {"capabilityCode": "AMOUNT.MAX", "decimalValue": "100000"},
                {"capabilityCode": "SECURITY.3DS_MODE", "codeValue": "SUPPORTED"}]}


def scope(scope_no, mid_id, rate="0.029", fixed="0.30", action="SALE", extra=None):
    components = [{"componentCode": "PROCESSING", "componentType": "PROCESSING_FEE", "calculationBase": "TRANSACTION_AMOUNT",
                   "rateValue": rate, "fixedAmount": fixed, "currency": "USD" if fixed else None, "roundingMode": "HALF_UP"}] + (extra or [])
    return {"scopeNo": scope_no, "midId": mid_id, "entityNo": ENTITY_NO, "channelAgreementRef": f"PMX-AGR-{RUN}",
            "countryCode": "US", "currency": "USD", "paymentMethodCode": "CARD", "actionType": action, "components": components}


def iso(moment):
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


# ---------------------------------------------------------------------------------------------------------------------

def channels(admin, actor):
    ids = {}
    status, main = raw(admin, "POST", "/channels", {"channelCode": MAIN_CODE, "channelName": f"本机合成 PayerMax {RUN}"})
    ids["main"] = main.get("channelId")
    remember("channels", ids["main"])
    check("AC-B01", f"R03 新建渠道 {MAIN_CODE} → 201 DRAFT（{status}）", status == 201 and main.get("status") == "DRAFT", main)
    status, body = raw(admin, "POST", "/channels", {"channelCode": MAIN_CODE, "channelName": "重复"})
    check("AC-B01", f"R03 重复渠道编码 → 409（{status}）", status == 409, body)
    status, body = raw(admin, "POST", "/channels", {"channelCode": "bad-code", "channelName": "非法编码"})
    check("AC-V02", f"R03 非法渠道编码 → 422 字段错误（{status}）", status == 422 and body.get("errors"), body)
    orphan_code = f"NOINTEG_{RUN}"
    status, orphan = raw(admin, "POST", "/channels", {"channelCode": orphan_code, "channelName": f"无接入方渠道 {RUN}"})
    ids["orphan"] = orphan.get("channelId")
    remember("channels", ids["orphan"])
    check("AC-B01", f"R03 新建无接入方渠道 {orphan_code} → 201（{status}）", status == 201, orphan)
    status, body = raw(admin, "POST", f"/channels/{ids['orphan']}/status", {"targetStatus": "ACTIVE", "reason": "验收：无接入方激活"})
    check("AC-B01", f"R05 无接入方激活被拒，detail「Platform 不存在同编码接入方」（{body.get('detail')}）",
          rejected(status, body, f"Platform 不存在同编码接入方: {orphan_code}"), body)
    status, body = raw(admin, "POST", f"/channels/{ids['orphan']}/status", {"targetStatus": "SUSPENDED"})
    check("AC-V04", f"R05 DRAFT → SUSPENDED 非法流转被拒（{status}）", rejected(status, body, "状态不允许变更"), body)
    status, active = raw(admin, "POST", f"/channels/{ids['main']}/status", {"targetStatus": "ACTIVE", "reason": "验收：激活"})
    check("AC-B01", f"R05 有接入方激活 → ACTIVE（{status}）", status == 200 and active.get("status") == "ACTIVE", active)
    status, renamed = raw(admin, "PUT", f"/channels/{ids['main']}", {"channelName": f"本机合成 PayerMax（改名）{RUN}"})
    check("B1", f"R04 改名 → 200（{status}）", status == 200 and "改名" in (renamed.get("channelName") or ""), renamed)
    status, listed = raw(admin, "GET", f"/channels?status=ACTIVE&keyword=PAYERMAX&page=1&size=20")
    row = next((i for i in listed.get("items", []) if i["channelId"] == ids["main"]), None)
    check("B1", f"R01 列表按状态 + 关键词筛选命中（total={listed.get('total')}）", status == 200 and row is not None, listed)
    status, listed = raw(admin, "GET", f"/channels?status=CLOSED&keyword=PAYERMAX&page=1&size=20")
    check("B1", "R01 状态筛选排除 ACTIVE 渠道", status == 200 and all(i["channelId"] != ids["main"] for i in listed.get("items", [])))
    status, body = raw(admin, "GET", "/channels?foo=1")
    check("B1", f"R01 未登记 Query → 400（{status}）", status == 400, body)
    status, detail = raw(admin, "GET", f"/channels/{ids['main']}")
    check("B1", f"R02 详情 → 200、编码不可变（{detail.get('channelCode')}）", status == 200 and detail.get("channelCode") == MAIN_CODE, detail)
    status, body = raw(admin, "PUT", f"/channels/{ids['main']}", {"channelName": "x", "channelCode": "OTHER"})
    check("B1", f"R04 改名请求携带 channelCode → 400（编码不可改，{status}）", status == 400, body)
    status, closed = raw(admin, "POST", f"/channels/{ids['orphan']}/status", {"targetStatus": "CLOSED", "reason": "验收：关闭无接入方渠道"})
    check("AC-V04", f"R05 DRAFT → CLOSED（{status}）", status == 200 and closed.get("status") == "CLOSED", closed)
    status, body = raw(admin, "PUT", f"/channels/{ids['orphan']}", {"channelName": "关闭后改名"})
    check("AC-V04", f"R04 CLOSED 渠道改名被拒（{body.get('detail')}）", rejected(status, body, "渠道已关闭"), body)
    status, body = raw(admin, "POST", f"/channels/{ids['orphan']}/status", {"targetStatus": "ACTIVE"})
    check("AC-V04", "R05 CLOSED 终态不能再激活", rejected(status, body), body)
    other_code = f"OTHERCH_{RUN}"
    status, other = raw(admin, "POST", "/channels", {"channelCode": other_code, "channelName": f"跨渠道改绑用 {RUN}"})
    ids["other"] = other.get("channelId")
    remember("channels", ids["other"])
    check("准备", f"R03 新建第三个渠道 {other_code}（跨渠道改绑用，{status}）", status == 201, other)
    return ids


def accounts(admin, ch):
    ids, keys = {}, account_material()
    status, acct = raw(admin, "POST", "/channel-accounts", {"channelId": ch["main"], "entityNo": ENTITY_NO,
                                                            "accountName": f"PayerMax 主账号 {RUN}", "remark": "阶段 5 验收"})
    ids["main"] = acct.get("channelAccountId")
    remember("accounts", ids["main"])
    check("AC-B02", f"R08 新建账号 → 201 DRAFT 未配置（{status}）",
          status == 201 and acct.get("status") == "DRAFT" and acct.get("configured") is False, acct)
    status, body = raw(admin, "POST", "/channel-accounts", {"channelId": ch["main"], "entityNo": "ae1", "accountName": "x"})
    check("AC-V02", f"R08 OE 格式非法 → 422（{status}）", status == 422 and body.get("errors"), body)
    status, body = raw(admin, "POST", "/channel-accounts", {"channelId": ch["orphan"], "entityNo": ENTITY_NO, "accountName": "x"})
    check("B2", f"R08 关闭渠道下新建账号被拒（{body.get('detail')}）", rejected(status, body), body)
    status, body = raw(admin, "POST", f"/channel-accounts/{ids['main']}/status", {"targetStatus": "ACTIVE"})
    check("AC-B02", f"R11 无 Token 账号激活被拒（{body.get('detail')}）", rejected(status, body, "技术账号激活前置不满足"), body)

    secret_values = [keys["merchantPrivateKey"], keys["payermaxPublicKey"], keys["appId"], keys["merchantNo"]]
    bff_mark, cc_mark = log_offset("ops-bff"), log_offset("channel-connector")
    configuration = {"parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
                     "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"]}}
    status, configured = raw(admin, "PUT", f"/channel-accounts/{ids['main']}/configuration", configuration)
    text = json.dumps(configured)
    check("AC-B02", f"R10 写入参数与凭据 → 200 configured、Token 末 4 位（{status} suffix={configured.get('vaultTokenSuffix')}）",
          status == 200 and configured.get("configured") is True and re.fullmatch(r".{4}", configured.get("vaultTokenSuffix") or ""), configured)
    check("AC-B02", "R10 响应不回显任何参数或凭据值", not any(v in text for v in secret_values))
    token_row = cc_sql(f"SELECT IFNULL(account_vault_token,''), account_status FROM t_channel_account WHERE channel_account_id={int(ids['main'])}")[0]
    vault_token = token_row[0]
    record["vaultTokenSuffix"] = vault_token[-4:]
    save()
    check("AC-B02", f"库中只有 Vault Token（长度 {len(vault_token)}，末 4 位与响应一致，不含明文）",
          vault_token and vault_token[-4:] == configured.get("vaultTokenSuffix") and not any(v in vault_token for v in secret_values))
    row_text = "\t".join(cc_sql(f"SELECT * FROM t_channel_account WHERE channel_account_id={int(ids['main'])}")[0])
    check("AC-B02", "账号整行不含任何明文参数或凭据", not any(v in row_text for v in secret_values))
    vault = e2e_http.sql("vault", f"SELECT data_type, token_mode FROM t_vault_record WHERE token='{vault_token}'")
    check("AC-B02", f"Vault 记录存在（{vault}）：真实 Protect，数据类型 CHANNEL_ACCOUNT_CONFIG_V1",
          vault and vault[0][0] == "CHANNEL_ACCOUNT_CONFIG_V1")
    remember("vaultTokens", vault_token[-4:])
    time.sleep(1)
    leaked = [n for n in ("ops-bff", "channel-connector") if any(v in log_since(n, bff_mark if n == "ops-bff" else cc_mark) for v in secret_values)]
    check("AC-B02", f"BFF 与 channel-connector 日志不含凭据或参数值（泄漏={leaked}）", not leaked)
    status, again = raw(admin, "PUT", f"/channel-accounts/{ids['main']}/configuration", configuration)
    token_again = cc_sql(f"SELECT account_vault_token FROM t_channel_account WHERE channel_account_id={int(ids['main'])}")[0][0]
    check("B2", f"R10 同内容重放复用同一 Token（{status}）", status == 200 and token_again == vault_token, again)
    status, detail = raw(admin, "GET", f"/channel-accounts/{ids['main']}")
    check("AC-V03", "R07 详情只有 configured 与末 4 位，无参数 / 凭据字段",
          status == 200 and detail.get("configured") is True and detail.get("vaultTokenSuffix") == vault_token[-4:]
          and not ({"parameters", "credentials", "accountVaultToken"} & set(detail)) and not any(v in json.dumps(detail) for v in secret_values), detail)
    status, updated = raw(admin, "PUT", f"/channel-accounts/{ids['main']}", {"accountName": f"PayerMax 主账号（改）{RUN}", "remark": "已更新备注"})
    check("B2", f"R09 编辑名称与备注 → 200（{status}）", status == 200 and updated.get("remark") == "已更新备注", updated)
    status, body = raw(admin, "PUT", f"/channel-accounts/{ids['main']}", {"accountName": "x", "entityNo": OTHER_ENTITY})
    check("AC-B03", f"R09 携带 entityNo → 400（OE 不可编辑，{status}）", status == 400, body)
    status, active = raw(admin, "POST", f"/channel-accounts/{ids['main']}/status", {"targetStatus": "ACTIVE", "reason": "验收：激活账号"})
    check("AC-B02", f"R11 已配置账号激活 → ACTIVE（{status}）", status == 200 and active.get("status") == "ACTIVE", active)
    status, listed = raw(admin, "GET", f"/channel-accounts?channelId={ch['main']}&entityNo={ENTITY_NO}&status=ACTIVE&page=1&size=20")
    check("B2", f"R06 列表按渠道 / OE / 状态筛选命中（total={listed.get('total')}）",
          status == 200 and any(i["channelAccountId"] == ids["main"] and i.get("vaultTokenSuffix") == vault_token[-4:] for i in listed.get("items", [])), listed)
    for key, channel, entity in (("sameOe", ch["main"], ENTITY_NO), ("otherOe", ch["main"], OTHER_ENTITY), ("otherChannel", ch["other"], ENTITY_NO)):
        status, body = raw(admin, "POST", "/channel-accounts", {"channelId": channel, "entityNo": entity, "accountName": f"{key} {RUN}"})
        ids[key] = body.get("channelAccountId")
        remember("accounts", ids[key])
        check("准备", f"R08 新建辅助账号 {key}（{entity}，{status}）", status == 201, body)
    return ids, vault_token


def mids(admin, ch, acct):
    ids, code = {}, f"PMX-E2E-{RUN}"
    status, mid = raw(admin, "POST", "/mids", {"channelId": ch["main"], "channelAccountId": acct["main"], "externalMidCode": code, "entityNo": ENTITY_NO})
    ids["main"] = mid.get("midId")
    remember("mids", ids["main"])
    check("AC-B03", f"R14 新建 MID → 201 DRAFT（{status}）", status == 201 and mid.get("status") == "DRAFT", mid)
    status, body = raw(admin, "POST", "/mids", {"channelId": ch["main"], "channelAccountId": acct["main"], "externalMidCode": code, "entityNo": ENTITY_NO})
    check("AC-B03", f"R14 同渠道外部 MID 重复 → 409（{status}）", status == 409, body)
    status, body = raw(admin, "POST", "/mids", {"channelId": ch["main"], "channelAccountId": acct["main"], "externalMidCode": code + "-X", "entityNo": OTHER_ENTITY})
    check("AC-B03", f"R14 账号与 MID 的 OE 不一致被拒（{body.get('detail')}）", rejected(status, body, "技术账号不属于该渠道、已关闭或 OE 不一致"), body)
    status, body = raw(admin, "PUT", f"/mids/{ids['main']}/account", {"channelAccountId": acct["otherOe"]})
    check("AC-B03", f"R15 跨 OE 改绑被拒（{body.get('detail')}）", rejected(status, body, "目标技术账号不属于 MID 所在渠道、OE 不一致或已关闭"), body)
    status, body = raw(admin, "PUT", f"/mids/{ids['main']}/account", {"channelAccountId": acct["otherChannel"]})
    check("AC-B03", f"R15 跨渠道改绑被拒（{body.get('detail')}）", rejected(status, body, "目标技术账号不属于 MID 所在渠道"), body)
    status, body = raw(admin, "PUT", f"/mids/{ids['main']}/account", {"channelAccountId": acct["sameOe"], "entityNo": OTHER_ENTITY})
    check("AC-B03", f"R15 携带 entityNo → 400（OE 不可编辑，{status}）", status == 400, body)
    status, rebound = raw(admin, "PUT", f"/mids/{ids['main']}/account", {"channelAccountId": acct["sameOe"]})
    check("AC-B03", f"R15 同渠道同 OE 改绑 → 200（{status}）", status == 200 and rebound.get("channelAccountId") == acct["sameOe"], rebound)
    status, body = raw(admin, "POST", f"/mids/{ids['main']}/status", {"targetStatus": "ACTIVE"})
    check("B6", f"R16 当前账号未激活时 MID 激活被拒（{body.get('detail')}）", rejected(status, body, "MID 激活前置不满足"), body)
    status, back = raw(admin, "PUT", f"/mids/{ids['main']}/account", {"channelAccountId": acct["main"]})
    check("AC-B03", f"R15 改绑回主账号 → 200（{status}）", status == 200 and back.get("channelAccountId") == acct["main"], back)
    status, active = raw(admin, "POST", f"/mids/{ids['main']}/status", {"targetStatus": "ACTIVE", "reason": "验收：激活 MID"})
    check("AC-B03", f"R16 MID 激活 → ACTIVE、activatedAt（{status}）", status == 200 and active.get("status") == "ACTIVE" and active.get("activatedAt"), active)
    status, listed = raw(admin, "GET", f"/mids?channelId={ch['main']}&status=ACTIVE&externalMidCode=PMX-E2E-&page=1&size=20")
    row = next((i for i in listed.get("items", []) if i["midId"] == ids["main"]), {})
    check("A3", f"R12 列表 channelId + ACTIVE + 外部 MID 前缀命中（total={listed.get('total')}）",
          status == 200 and row.get("externalMidCode") == code and row.get("channelCode") == MAIN_CODE, listed)
    status, listed = raw(admin, "GET", f"/mids?channelId={ch['main']}&externalMidCode=NOPE-{RUN}&page=1&size=20")
    check("A3", "R12 外部 MID 前缀不匹配 → 0 条", status == 200 and listed.get("total") == 0, listed)
    status, listed = raw(admin, "GET", f"/mids?channelId={ch['other']}&page=1&size=20")
    check("AC-A02", "R12 按渠道筛选不含其他渠道的 MID（成本编制下拉的数据源）", status == 200 and all(i["channelId"] == ch["other"] for i in listed.get("items", [])))
    return ids


def capabilities(admin, mid):
    ids = {}
    status, catalog = raw(admin, "GET", "/mid-capabilities/definitions")
    types = {d["capabilityCode"]: d["valueType"] for d in catalog.get("definitions", [])}
    check("B4", f"R17 能力目录（{len(types)} 项，ACTION.PAY={types.get('ACTION.PAY')}，AMOUNT.MIN={types.get('AMOUNT.MIN')}）",
          status == 200 and types.get("ACTION.PAY") == "BOOLEAN" and types.get("AMOUNT.MIN") == "DECIMAL", catalog)
    main_group = capability(mid["main"])
    for code in ("ACTION.AUTHORIZE", "ACTION.CAPTURE"):
        if types.get(code) == "BOOLEAN":
            main_group["details"].insert(1, {"capabilityCode": code, "booleanValue": True})
    status, group = raw(admin, "POST", "/mid-capabilities", main_group)
    ids["usd"] = group.get("midCapabilityId")
    remember("capabilities", ids["usd"])
    codes = [d["capabilityCode"] for d in main_group["details"]]
    check("AC-B04", f"R18 新建 USD 能力组（{len(codes)} 条明细 {codes}）→ 201 ENABLED（{status}）",
          status == 201 and group.get("status") == "ENABLED" and len(group.get("details") or []) == len(codes), group)
    status, body = raw(admin, "POST", "/mid-capabilities", capability(mid["main"]))
    check("AC-B04", f"R18 六维条件重复 → 409（{status}）", status == 409, body)
    status, body = raw(admin, "POST", "/mid-capabilities", capability(mid["main"], "GBP", [{"capabilityCode": "ACTION.PAY", "decimalValue": "1"}]))
    check("AC-B04", f"R18 明细类型与目录不一致被拒（{body.get('detail')}）", rejected(status, body, "能力值与目录类型不一致"), body)
    status, body = raw(admin, "POST", "/mid-capabilities", capability(mid["main"], "GBP", [{"capabilityCode": "NO.SUCH", "booleanValue": True}]))
    check("AC-B04", f"R18 未注册能力代码被拒（{body.get('detail')}）", rejected(status, body, "能力代码未注册"), body)
    bad = capability(mid["main"], "GBP")
    bad["mccCode"] = "59"
    status, body = raw(admin, "POST", "/mid-capabilities", bad)
    check("AC-V02", f"R18 MCC 非四位数字 → 422（{status}）", status == 422 and body.get("errors"), body)
    status, eur = raw(admin, "POST", "/mid-capabilities", capability(mid["main"], "EUR", [{"capabilityCode": "ACTION.PAY", "booleanValue": True}]))
    ids["eur"] = eur.get("midCapabilityId")
    remember("capabilities", ids["eur"])
    check("AC-B04", f"R18 新建 EUR 能力组 → 201（{status}）", status == 201, eur)
    status, replaced = raw(admin, "PUT", f"/mid-capabilities/{ids['eur']}/details", {"details": [
        {"capabilityCode": "ACTION.PAY", "booleanValue": True}, {"capabilityCode": "AMOUNT.MAX", "decimalValue": "500"}]})
    check("B4", f"R19 整组替换明细 → 200、2 条（{status}）", status == 200 and len(replaced.get("details") or []) == 2, replaced)
    _, before = candidates("EUR")
    check("AC-B04", "停用前：EUR 候选查询返回该 MID", any(str(c.get("midId")) == mid["main"] for c in before), before)
    status, disabled = raw(admin, "POST", f"/mid-capabilities/{ids['eur']}/status", {"targetStatus": "DISABLED"})
    check("AC-B04", f"R20 整组停用 → DISABLED（{status}）", status == 200 and disabled.get("status") == "DISABLED", disabled)
    _, after = candidates("EUR")
    check("AC-B04", "停用后：EUR 候选查询不再返回该 MID", all(str(c.get("midId")) != mid["main"] for c in after), after)
    status, body = raw(admin, "POST", f"/mid-capabilities/{ids['eur']}/status", {"targetStatus": "DISABLED"})
    check("B4", f"R20 重复停用被拒（{body.get('detail')}）", rejected(status, body, "能力组已是目标状态"), body)
    status, enabled = raw(admin, "POST", f"/mid-capabilities/{ids['eur']}/status", {"targetStatus": "ENABLED"})
    _, again = candidates("EUR")
    check("AC-B04", f"R20 重新启用 → ENABLED，EUR 候选查询恢复返回该 MID（{status}）",
          status == 200 and enabled.get("status") == "ENABLED" and any(str(c.get("midId")) == mid["main"] for c in again), enabled)
    status, disabled = raw(admin, "POST", f"/mid-capabilities/{ids['eur']}/status", {"targetStatus": "DISABLED"})
    check("B4", f"R20 再次停用（保持页面验收的 DISABLED 前置，{status}）", status == 200 and disabled.get("status") == "DISABLED", disabled)
    status, detail = raw(admin, "GET", f"/mids/{mid['main']}")
    groups = {g["midCapabilityId"]: g["status"] for g in detail.get("capabilities", [])}
    check("B4", f"R13 MID 详情带能力组（{groups}）", status == 200 and groups.get(ids["usd"]) == "ENABLED" and groups.get(ids["eur"]) == "DISABLED", detail)
    _, usd = candidates("USD")
    mine = [c for c in usd if str(c.get("midId")) == mid["main"]]
    check("AC-B05", f"USD 候选查询返回控制台登记的 MID 且支持 PAY（{mine[0].get('supportedOperations') if mine else None}）",
          mine and "PAY" in (mine[0].get("supportedOperations") or []), usd)
    return ids


def changes(admin, actor, ch, acct, mid):
    status, body = raw(admin, "GET", f"/config-changes?targetType=CHANNEL&targetId={ch['main']}&page=1&size=50")
    items = body.get("items", [])
    kinds = [(i["changeType"], i.get("fromStatus"), i.get("toStatus")) for i in items]
    check("AC-B06", f"R21 渠道变更记录 CREATE / STATUS DRAFT→ACTIVE / UPDATE（{kinds}）",
          status == 200 and ("CREATE", None, None) in kinds and ("STATUS", "DRAFT", "ACTIVE") in kinds and any(k[0] == "UPDATE" for k in kinds), body)
    check("AC-B06", f"R21 操作者 = 会话 Account ID（{actor}）且有时间",
          items and all(i["operatorId"] == actor and i.get("createTime") for i in items), items[:2])
    status, body = raw(admin, "GET", f"/config-changes?targetType=CHANNEL_ACCOUNT&targetId={acct['main']}&page=1&size=50")
    kinds = [i["changeType"] for i in body.get("items", [])]
    check("AC-B06", f"R21 账号变更记录含 CONFIGURATION 与 STATUS（{kinds}）", "CONFIGURATION" in kinds and "STATUS" in kinds, body)
    reasons = [i.get("reason") for i in body.get("items", [])]
    check("AC-B02", "R21 账号变更记录原因中不含凭据", not any(r and "PRIVATE" in r.upper() for r in reasons))
    status, body = raw(admin, "GET", f"/config-changes?targetType=CHANNEL_MID&targetId={mid['main']}&page=1&size=50")
    kinds = [i["changeType"] for i in body.get("items", [])]
    check("AC-B06", f"R21 MID 变更记录含 REBIND ×2 与 STATUS（{kinds}）", kinds.count("REBIND") == 2 and "STATUS" in kinds, body)
    status, body = raw(admin, "GET", "/config-changes?targetType=CHANNEL&page=1&size=50")
    check("B7", f"R21 缺 targetId → 400（{status}）", status == 400, body)


def costs(admin, checker, checker_id, actor, ch, mid):
    ids, plan_code = {}, f"PMX_E2E_{RUN}"
    status, body = raw(admin, "POST", "/channel-cost-plans", {"channelId": "987654321987654", "planCode": plan_code + "_X"})
    check("AC-A06", f"R25 渠道不存在时建方案被拒（{body.get('detail')}）", rejected(status, body, "渠道不存在"), body)
    status, plan = raw(admin, "POST", "/channel-cost-plans", {"channelId": ch["main"], "planCode": plan_code})
    remember("costPlans", plan_code)
    check("A1", f"R25 新建成本方案 → 201（{status}）", status == 201 and plan.get("planCode") == plan_code, plan)
    path = f"/channel-cost-plans/{plan_code}"
    start = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(seconds=5)
    status, v1 = raw(admin, "POST", path + "/versions", {"effectiveFrom": iso(start), "scopes": [scope(1, mid["main"])]})
    ids["v1"] = v1.get("costVer")
    remember("costVerIds", v1.get("costVerId"))
    check("AC-A02", f"R26 编制 V1 → 201 DRAFT、编制人 = 会话账号（{status} maker={v1.get('makerId')}）",
          status == 201 and v1.get("status") == "DRAFT" and v1.get("makerId") == actor, v1)
    status, submitted = raw(admin, "POST", f"{path}/versions/{ids['v1']}/submit")
    check("AC-A02", f"R27 提交 → PENDING_APPROVAL（{status}）", status == 200 and submitted.get("status") == "PENDING_APPROVAL", submitted)
    status, listed = raw(admin, "GET", f"/channel-cost-plans?channelId={ch['main']}&page=1&size=20")
    row = next((i for i in listed.get("items", []) if i["planCode"] == plan_code), {})
    check("AC-A01", f"R22 方案列表按渠道筛选、待审批 1、渠道名联查（{row.get('pendingApprovalCount')} {row.get('channelCode')}）",
          status == 200 and row.get("pendingApprovalCount") == 1 and row.get("channelCode") == MAIN_CODE and row.get("channelName"), listed)
    status, listed = raw(admin, "GET", f"/channel-cost-plans?status=INACTIVE&page=1&size=200")
    check("AC-A01", "R22 状态筛选排除本方案", status == 200 and all(i["planCode"] != plan_code for i in listed.get("items", [])), listed.get("total"))
    content_hash = submitted.get("contentHash") or v1.get("contentHash")
    status, body = raw(admin, "POST", f"{path}/versions/{ids['v1']}/approve", {"approvedContentHash": content_hash})
    check("AC-A03", f"R28 编制人自批被拒（{body.get('detail')}）", rejected(status, body, "复核人不能与编制人相同"), body)
    status, body = raw(checker, "POST", f"{path}/versions/{ids['v1']}/approve", {"approvedContentHash": "0" * 64})
    check("A4", f"R28 内容摘要不一致被拒（{body.get('detail')}）", rejected(status, body, "复核确认的内容摘要与版本不一致"), body)
    status, body = raw(checker, "POST", path + "/versions", {"effectiveFrom": iso(start), "scopes": [scope(1, mid["main"])]})
    check("A4", f"复核人（无 COST_MANAGE）编制版本 → 403（{status}）", status == 403, body)
    while datetime.now(timezone.utc) < start + timedelta(seconds=1):
        time.sleep(1)
    status, approved = raw(checker, "POST", f"{path}/versions/{ids['v1']}/approve", {"approvedContentHash": content_hash})
    check("AC-A03", f"R28 另一账号批准、已到生效时间 → ACTIVE（{status} checker={approved.get('checkerId')}）",
          status == 200 and approved.get("status") == "ACTIVE" and approved.get("checkerId") == checker_id, approved)
    status, body = raw(admin, "POST", f"{path}/versions/{ids['v1']}/cancel", {"reason": "验收：ACTIVE 不可作废"})
    check("AC-A04", f"R30 ACTIVE 版本作废被拒（{body.get('detail')}）", rejected(status, body, "只能作废已批准且未到生效时间"), body)

    future = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(days=2)
    fee = {"componentCode": "CROSS_BORDER", "componentType": "CROSS_BORDER_FEE", "calculationBase": "TRANSACTION_AMOUNT",
           "rateValue": "0.01", "roundingMode": "HALF_UP"}
    status, v2 = raw(admin, "POST", path + "/versions", {"effectiveFrom": iso(future),
                                                         "scopes": [scope(1, mid["main"], rate="0.025", extra=[fee]),
                                                                    scope(2, mid["main"], rate="0.01", fixed=None, action="REFUND")]})
    ids["v2"] = v2.get("costVer")
    remember("costVerIds", v2.get("costVerId"))
    check("AC-A02", f"R26 编制 V2（2 个范围，组成增删）→ 201（{status}）", status == 201, v2)
    status, submitted = raw(admin, "POST", f"{path}/versions/{ids['v2']}/submit")
    status, approved = raw(checker, "POST", f"{path}/versions/{ids['v2']}/approve", {"approvedContentHash": submitted.get("contentHash")})
    check("AC-A03", f"R28 未到生效时间批准 → APPROVED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPROVED", approved)
    status, diff = raw(admin, "GET", f"{path}/versions/{ids['v1']}/diff/{ids['v2']}")
    changed = diff.get("changed") or []
    added_components = [c["componentCode"] for s in changed for c in s.get("addedComponents") or []]
    fields = [d["field"] for s in changed for c in s.get("changedComponents") or [] for d in c.get("differences") or []]
    check("AC-A05", f"R31 对比：新增范围 {len(diff.get('added') or [])}、修改 {len(changed)}、组成新增 {added_components}、字段 {fields}",
          status == 200 and len(diff.get("added") or []) == 1 and "CROSS_BORDER" in added_components and "rateValue" in fields, diff)
    status, reverse = raw(admin, "GET", f"{path}/versions/{ids['v2']}/diff/{ids['v1']}")
    check("AC-A05", "R31 反向对比：删除范围 1", status == 200 and len(reverse.get("removed") or []) == 1, reverse)
    status, cancelled = raw(admin, "POST", f"{path}/versions/{ids['v2']}/cancel", {"reason": f"验收：作废未到期版本 {RUN}"})
    check("AC-A04", f"R30 未到期 APPROVED 作废 → INACTIVE（{status}）", status == 200 and cancelled.get("status") == "INACTIVE", cancelled)
    due = cc_sql(f"SELECT status, effective_from = effective_to FROM t_channel_cost_plan_ver WHERE id={int(v2.get('costVerId'))}")[0]
    check("AC-A04", f"库中 V2 为 INACTIVE（到期 Job 只选 APPROVED，不再切换）{due}", due[0] == "INACTIVE")
    status, body = raw(admin, "GET", f"/config-changes?targetType=CHANNEL_COST_VERSION&targetId={v2.get('costVerId')}&page=1&size=20")
    cancel = next((i for i in body.get("items", []) if i["changeType"] == "CANCEL"), {})
    check("AC-A04", f"R21 作废原因写入变更记录（{cancel.get('reason')}）", cancel.get("reason") == f"验收：作废未到期版本 {RUN}" and cancel.get("operatorId") == actor, body)

    status, v3 = raw(admin, "POST", path + "/versions", {"effectiveFrom": iso(future + timedelta(days=1)), "scopes": [scope(1, mid["main"], rate="0.02")]})
    ids["v3"] = v3.get("costVer")
    remember("costVerIds", v3.get("costVerId"))
    status, submitted = raw(admin, "POST", f"{path}/versions/{ids['v3']}/submit")
    status, rejected_v3 = raw(checker, "POST", f"{path}/versions/{ids['v3']}/reject", {"approvedContentHash": submitted.get("contentHash")})
    check("A4", f"R29 复核人驳回 → REJECTED（{status} {rejected_v3.get('status')}）", status == 200 and rejected_v3.get("status") == "REJECTED", rejected_v3)
    status, detail = raw(admin, "GET", path)
    statuses = {v["costVer"]: v["status"] for v in detail.get("versions", [])}
    counts = {v["costVer"]: (v.get("scopeCount"), v.get("componentCount")) for v in detail.get("versions", [])}
    check("AC-A01", f"R23 方案详情版本列表 {statuses} 计数 {counts}",
          status == 200 and statuses == {ids["v1"]: "ACTIVE", ids["v2"]: "INACTIVE", ids["v3"]: "REJECTED"}
          and counts.get(ids["v2"]) == (2, 3), detail)
    status, version = raw(admin, "GET", f"{path}/versions/{ids['v2']}")
    check("A6", f"R24 版本详情按范围展示组成（{len(version.get('scopes') or [])} 个范围）",
          status == 200 and len(version.get("scopes") or []) == 2 and version.get("costVerId") == v2.get("costVerId"), version)
    return ids


def authorization(admin, reader):
    nav = navigation_codes(admin)
    wanted = ["OPERATIONS_CHANNEL", "OPERATIONS_CHANNEL_CHANNELS", "OPERATIONS_CHANNEL_MIDS", "OPERATIONS_CHANNEL_COST_PLANS",
              "OPERATIONS_CHANNEL_CHANNEL_MANAGE", "OPERATIONS_CHANNEL_ACCOUNT_CREDENTIAL", "OPERATIONS_CHANNEL_MID_MANAGE",
              "OPERATIONS_CHANNEL_COST_MANAGE", "OPERATIONS_CHANNEL_COST_APPROVE"]
    check("AC-C01", "管理员导航含「渠道」目录、三页与 5 个按钮", all(c in nav for c in wanted), [c for c in wanted if c not in nav])
    menus = sorted((r for r in nav.values() if r.get("parentResourceId") == nav.get("OPERATIONS_CHANNEL", {}).get("resourceId")
                    and r["resourceType"] == "MENU"), key=lambda r: r["sortOrder"])
    check("AC-V01", f"渠道子菜单顺序 {[r['displayName'] for r in menus]}", [r["resourceCode"] for r in menus] ==
          ["OPERATIONS_CHANNEL_CHANNELS", "OPERATIONS_CHANNEL_MIDS", "OPERATIONS_CHANNEL_COST_PLANS"])
    if reader:
        reader_nav = navigation_codes(reader)
        check("AC-C01", "未授权账号导航不含任何 OPERATIONS_CHANNEL*", not any(c.startswith("OPERATIONS_CHANNEL") for c in reader_nav))
        for path in ("/channels", "/mids", "/channel-cost-plans", "/mid-capabilities/definitions"):
            status, body = raw(reader, "GET", path)
            check("AC-C01", f"未授权账号 GET {path} → 403（{status}）", status == 403, body)
        status, body = raw(reader, "POST", "/channels", {"channelCode": f"DENY_{RUN}", "channelName": "x"})
        check("AC-C01", f"未授权账号 POST /channels → 403（{status}）", status == 403, body)


def wait_routes(admin):
    """Gateway 每 5 秒刷新 Snapshot；激活后等渠道路由可用。"""
    for _ in range(30):
        status, _ = raw(admin, "GET", "/channels?page=1&size=10")
        if status == 200:
            return True
        time.sleep(2)
    return False


def main():
    s = e2e_http.secrets()
    admin = login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    actor = jwt_claims(admin)["sub"]
    record["adminAccountId"] = actor
    save()
    if not check("AC-C01", "Gateway 已加载块 92 路由（列表 200）", wait_routes(admin)):
        return finish()
    checker, checker_id = synthetic_person(admin, "CHECKER", [PERM["CONFIG_READ"], PERM["COST_READ"], PERM["COST_APPROVE"], PERM["CHANGE_READ"]])
    reader, _ = synthetic_person(admin, "READER", [PERM["AUDIT_READ"]])
    ch = channels(admin, actor)
    record["channelIds"] = ch
    acct, _ = accounts(admin, ch)
    record["accountIds"] = acct
    mid = mids(admin, ch, acct)
    record["midIds"] = mid
    cap = capabilities(admin, mid)
    record["capabilityIds"] = cap
    save()
    changes(admin, actor, ch, acct, mid)
    if checker:
        record["costVersions"] = costs(admin, checker, checker_id, actor, ch, mid)
    authorization(admin, reader)
    return finish()


def finish():
    passed = sum(1 for r in results if r[2])
    record.update(passed=passed, total=len(results), failed=[f"{r[0]} {r[1]}" for r in results if not r[2]])
    save()
    print(f"\n接口验收：{passed} / {len(results)} 通过；运行记录 {RECORD_PATH}")
    return 0 if passed == len(results) else 1


# ---------------------------------------------------------------------------------------------------------------------

def latest_record():
    paths = sorted(RUNTIME.glob(RECORD_PREFIX + "*.json"))
    if not paths:
        raise SystemExit("没有运行记录")
    return paths[-1], json.loads(paths[-1].read_text())


def cleanup():
    return cleanup_test() if TEST else cleanup_local()


def cleanup_local():
    path, rec = latest_record()
    numeric = lambda values: ",".join(str(int(v)) for v in values if re.fullmatch(r"[1-9][0-9]{0,18}", str(v))) or "NULL"
    plans = ",".join(f"'{p}'" for p in rec["ids"].get("costPlans", []) if re.fullmatch(r"[A-Z][A-Z0-9_]{1,63}", p)) or "NULL"
    text = CLEANUP_SQL.read_text()
    cc_part, platform_part = text.split("-- @platform", 1)
    cc_part = cc_part.replace("/*CHANNELS*/", numeric(rec["ids"].get("channels", []))).replace("/*PLANS*/", plans)
    e2e_http.sql("channel-connector", cc_part)
    platform_part = (platform_part.replace("/*ACCOUNTS*/", numeric(rec["ids"].get("platformAccounts", [])))
                     .replace("/*ROLES*/", numeric(rec["ids"].get("platformRoles", []))))
    e2e_http.sql("platform", platform_part)
    left = e2e_http.sql("channel-connector", f"SELECT COUNT(*) FROM t_payment_channel WHERE channel_id IN ({numeric(rec['ids'].get('channels', []))})")
    print(f"清理 {path.name}：渠道 {rec['ids'].get('channels')}、方案 {rec['ids'].get('costPlans')}、合成人员 "
          f"{rec['ids'].get('platformAccounts')}、角色 {rec['ids'].get('platformRoles')}；剩余渠道 {left[0][0]}；"
          f"Vault 侧保留 Token {len(set(rec['ids'].get('vaultTokens', [])))} 个（不删）")
    return 0 if left[0][0] == "0" else 1


def cleanup_test():
    """mor-test：channel-connector 按 acceptance-cleanup-test.sql（先核对后删除）；Platform 合成人员停用、合成角色清空权限（IAM 接口）。"""
    path, rec = latest_record()
    numeric = lambda values: ",".join(str(int(v)) for v in values if re.fullmatch(r"[1-9][0-9]{0,18}", str(v))) or "NULL"
    plans = ",".join(f"'{p}'" for p in rec["ids"].get("costPlans", []) if re.fullmatch(r"[A-Z][A-Z0-9_]{1,63}", p)) or "NULL"
    text = (HERE / "acceptance-cleanup-test.sql").read_text()
    text = text.replace("/*CHANNELS*/", numeric(rec["ids"].get("channels", []))).replace("/*PLANS*/", plans)
    rows = e2e_http.sql("channel-connector", text)
    print("channel-connector：" + "；".join(f"{r[0]}={r[1]}" for r in rows))
    s = e2e_http.secrets()
    admin = login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    platform = []
    for account_id in rec["ids"].get("platformAccounts", []):
        page, row = 1, None
        while True:
            _, result = iam(admin, "GET", f"/iam/v1/accounts?page={page}&size=200")
            row = next((r for r in result.get("items", []) if r["accountId"] == account_id), None)
            if row or page * 200 >= result.get("total", 0):
                break
            page += 1
        if row and row["status"] == "ACTIVE":
            status, _ = iam(admin, "POST", f"/iam/v1/accounts/{account_id}/disable",
                            {"expectedVersion": row["version"], "reason": "channel-connector 运营控制台阶段 7 验收结束：停用合成人员"})
            platform.append(f"停用人员 {account_id} → {status}")
        else:
            platform.append(f"人员 {account_id} 状态 {row and row['status']}")
    for role_id in rec["ids"].get("platformRoles", []):
        _, current = iam(admin, "GET", f"/iam/v1/roles/{role_id}")
        if not current.get("permissionIds") and current.get("version"):
            platform.append(f"角色 {role_id} 已无权限")
            continue
        status, _ = iam(admin, "PUT", f"/iam/v1/roles/{role_id}", {
            "roleName": current["roleName"], "permissionIds": [], "expectedVersion": current["version"],
            "reason": "channel-connector 运营控制台阶段 7 验收结束：清空合成角色权限"})
        platform.append(f"清空角色 {role_id} → {status}")
    print("Platform：" + "；".join(platform))
    left = {r[0]: r[1] for r in rows if r[0].startswith("left_")}
    print(f"Vault 侧保留 Token {len(set(rec['ids'].get('vaultTokens', [])))} 个（不删）；剩余 {left}")
    rec["cleanup"] = {"channelConnector": {r[0]: r[1] for r in rows}, "platform": platform, "at": datetime.now(timezone.utc).isoformat()}
    path.write_text(json.dumps(rec, ensure_ascii=False, indent=2))
    ok = all(v == "0" for v in left.values()) and all("→ 200" in x or "→ 204" in x or "已无权限" in x or "DISABLED" in x for x in platform)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(cleanup() if "--cleanup" in sys.argv else main())
