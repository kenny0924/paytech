"""阶段 5 本机全链路（任务卡 14）：让 Payment 的卡支付 Attempt 能路由到本机合成 PayerMax（PAYERMAX_MOCK），可重复执行。

配置链（每一步可重复执行，已满足即跳过）：
  前置（主会话执行的评审 SQL）：sql/50-channel-connector-payermax-mock.sql（渠道 / 账号 DRAFT / MID / 能力）、
                              sql/51-platform-external-api-routes.sql（外部 API 与渠道沙箱运营路由）；
                              run-service.sh 已起 payermax-mock（127.0.0.1:18190，密钥写入 local-environment/e2e/runtime/payermax）。
  1. Platform 外部 API（真实运营入口 https://127.0.0.1:18444/api/operations/external-api-admin/v1）：
     沿用 PayerMax 接入 / 卡直付 / 退款三个 harness 的配置定义（configure.py、configure_card.py、configure_refund.py，原文件不改）：
     标准 API PAY（卡直付合同）、PAY_QUERY、REFUND、REFUND_QUERY；接入方 PAYERMAX_MOCK 的出站地址（标记沙箱）、签名/验签脚本、
     ORDER_AND_PAY（Direct_Payment）、ORDER_QUERY、REFUND、REFUND_QUERY 原子 API 与四条流程。
     三个 harness 的 common.py 读测试装配 Platform 的运行文件，这里以同名模块（sys.modules['common']）替换为真实入口。
  2. 渠道账号配置（CHANNEL_ACCOUNT_CONFIG_V1，SECRET 类，人工加密已开启）：经运营入口 /vault-admin/v1/manual/secret-protect 加密
     {schemaVersion, channelCode, channelAccountId, entityNo, parameters{appId, merchantNo, keyVersion, payermaxPublicKey},
     credentials{merchantPrivateKey}}，得到 REFERENCE Token。Platform（platform-support-service）与 channel-connector-service
     以 CHANNEL_EXECUTION 用途 Reveal（sql/30-vault-v2-callers.sql 登记后者）。
  3. 夹具写入（已标注）：channel-connector 没有账号管理接口，把 Token 写入 t_channel_account 并改为 ACTIVE；
     前置条件 account_status='DRAFT' 且 Token 为空，否则不写。
  4. 核对：channel-connector 候选查询（私网 POST /channel-connector/v1/mid-candidates）对 AE001 / CARD / US / USD / 5999 返回本 MID 且含 PAY。
用法：python3 channel_setup.py；不打印密钥、Token 或口令。
"""
import json
import sys
import time
import types
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import e2e_http as h  # noqa: E402

RUNTIME = h.ROOT / "local-environment/e2e/runtime/payermax"
HARNESS = h.ROOT / "requirements/20261014"
INTEGRATION = "PAYERMAX_MOCK"
CHANNEL_ID = 9500000000000001
ACCOUNT_ID = "9500000000000101"
MID_ID = 9500000000000201
ENTITY_NO = "AE001"
PAYMENT_METHOD = "CARD"
MCC = "5999"
MOCK_URL = "http://127.0.0.1:18190/aggregate-pay/api/gateway"
ADMIN = "/external-api-admin/v1"
REASON = "阶段 5 本机全链路（任务卡 14）"
results = []
_token = {"value": None}


# ---------- 替换三个 harness 的 common 模块：真实运营入口 + 私网 Platform 执行 ----------

def send(name, url, method="GET", headers=None, body=None, expected=(200,)):
    expected = (expected,) if isinstance(expected, int) else expected
    request = urllib.request.Request(url, method=method, headers={"Content-Type": "application/json", **(headers or {})},
                                     data=None if body is None else json.dumps(body).encode())
    context = h.TLS if url.startswith("https://") else None
    for _ in range(15):
        try:
            response = urllib.request.urlopen(request, timeout=70, context=context)
        except urllib.error.HTTPError as error:
            response = error
        if response.status != 429:
            break
        delay = min(30, max(1, int(response.headers.get("Retry-After", "6"))))
        response.read()
        time.sleep(delay)
    data = response.read()
    value = json.loads(data) if data and "json" in (response.headers.get("Content-Type") or "") else None
    assert response.status in expected, (name, response.status, value)
    results.append({"name": name, "method": method, "path": url.split("?")[0].replace(h.OPERATIONS, "")
                    .replace(h.PRIVATE["platform"], "[internal]"), "status": response.status})
    return response.status, value


def call(name, method, path, token=None, body=None, expected=(200,)):
    headers = {"Origin": h.OPERATIONS}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None and method != "GET" and not path.endswith("/validate"):
        headers["Idempotency-Key"] = uuid.uuid4().hex
    return send(name, h.OPERATIONS + "/api/operations" + path, method, headers, body, expected)


def login():
    s = h.secrets()
    _token["value"] = call("login-admin", "POST", "/auth/v1/login",
                           body={"loginIdentifier": s["E2E_ADMIN_EMAIL"], "password": s["E2E_ADMIN_PASSWORD"]})[1]["accessToken"]
    return _token["value"]


def execute(name, standard_api, integration, payload, expected=200):
    return send(name, f"{h.PRIVATE['platform']}/standard-apis/v1/{standard_api}/execute", "POST", None,
                {"integrationCode": integration, "input": payload}, expected)[1]


def install_harness():
    """以真实入口替换 harness 的 common，并导入三个配置模块（原文件不改）。"""
    shim = types.ModuleType("common")
    shim.ADMIN, shim.call, shim.login, shim.results, shim.runtime = ADMIN, call, login, results, RUNTIME
    shim.execute, shim.send, shim.origin, shim.internal = execute, send, h.OPERATIONS, h.PRIVATE["platform"]
    sys.modules["common"] = shim
    for folder in ("platform-support-PayerMax接入", "payment-PayerMax卡直付对接", "platform-support-PayerMax退款"):
        sys.path.insert(0, str(HARNESS / folder / "assets/harness"))
    import configure  # noqa: F401
    import configure_card  # noqa: F401
    import configure_refund  # noqa: F401
    for module in (configure, configure_card):
        module.REASON = REASON
    return configure, configure_card, configure_refund


def mock_keys():
    keys = RUNTIME / "payermax-mock-keys.json"
    if not keys.exists():
        raise SystemExit(f"缺少 {keys}：先 run-service.sh start payermax-mock")
    return json.loads(keys.read_text())


def identity(token=None):
    """渠道账号身份（Platform PAY 输入的 channelAccount；accountVaultToken 由 channel-connector 按冻结账号补齐）。"""
    value = {"channelCode": INTEGRATION, "channelAccountId": ACCOUNT_ID, "entityNo": ENTITY_NO}
    if token:
        value["accountVaultToken"] = token
    return value


# ---------- 步骤 ----------

def reset_stale_catalog(token):
    """mor_platform_v2e2e 复制自 mor_platform_e2e，其中留有旧会话不完整的外部 API 配置：标准 API PAY / PAY_QUERY 没有任何字段
    （t_api_field 0 行），接入方 PAYERMAX 的流程与原子 API 引用它们。任何对 PAY 的保存都因引用不完整被拒（BUSINESS_CHECK_FAILED）。
    只在检测到“PAY 存在且无请求字段”时，经真实运营入口按依赖倒序删除：实现 → 原子 API → 脚本 → 接入方 → 标准 API。"""
    found, pay = call("standard-api-get-PAY", "GET", f"{ADMIN}/standard-apis/PAY", token, expected=(200, 404))
    if found != 200 or pay.get("requestFields"):
        return
    print("stale   标准 API PAY 无字段：清理旧会话残留的外部 API 配置")
    reason = {"reason": REASON + "：清理复制库中不完整的旧配置"}

    def delete(name, path):
        status, current = call(name + "-get", "GET", path, token, expected=(200, 404))
        if status == 200:
            call(name + "-delete", "DELETE", path, token, {"expectedVersion": current["version"], **reason})
            print(f"deleted {name}")
    integrations = {i["integrationCode"] for code in ("PAY", "PAY_QUERY")
                    for i in (call("standard-api-get-" + code, "GET", f"{ADMIN}/standard-apis/{code}", token,
                                   expected=(200, 404))[1] or {}).get("implementations") or []}
    for integration in sorted(integrations):
        base = f"{ADMIN}/integrations/{integration}"
        for code in ("PAY", "PAY_QUERY", "REFUND", "REFUND_QUERY"):
            delete(f"implementation-{integration}-{code}", f"{base}/implementations/{code}")
        for (code,) in h.platform_sql("SELECT a.api_code FROM t_integration_api a JOIN t_integration i "
                                      f"ON i.integration_id=a.integration_id WHERE i.integration_code='{integration}'"):
            delete(f"api-{integration}-{code}", f"{base}/apis/{code}")
        for (code,) in h.platform_sql("SELECT s.script_code FROM t_integration_script s JOIN t_integration i "
                                      f"ON i.integration_id=s.integration_id WHERE i.integration_code='{integration}'"):
            delete(f"script-{integration}-{code}", f"{base}/scripts/{code}")
        delete(f"integration-{integration}", base)
    for code in ("PAY", "PAY_QUERY"):
        delete(f"standard-api-{code}", f"{ADMIN}/standard-apis/{code}")


def configure_platform(configure, configure_card, configure_refund):
    token = login()
    reset_stale_catalog(token)
    base = f"{ADMIN}/integrations/{INTEGRATION}"
    found, current = call("implementation-get-PAY", "GET", f"{base}/implementations/PAY", token, expected=(200, 404))
    card_ready = found == 200 and any(i.get("function") == "PAYMENT_CARD"
                                      for i in (current.get("flowDefinition") or {}).get("initializers", []))
    if card_ready:
        print("exists  Platform PAY 卡直付流程（跳过收银台基础配置，避免把 PAY 标准 API 改回收银台合同）")
    else:
        configure.configure(INTEGRATION, MOCK_URL)
        print("saved   Platform 基础配置（标准 API PAY / PAY_QUERY、接入方、出站地址、签名脚本、收银台流程）")
    mark_sandbox(token)
    configure_card.configure_all([INTEGRATION])
    print("saved   Platform 卡直付 PAY（Direct_Payment、PAYMENT_CARD 读卡初始化）")
    configure_refund.configure_refund(INTEGRATION)
    print("saved   Platform 退款 REFUND / REFUND_QUERY")


def mark_sandbox(token):
    """合成渠道的出站地址标记沙箱：渠道沙箱验证（card_smoke.py）要求流程用到的出站地址全部为沙箱；正式执行不受影响。"""
    path = f"{ADMIN}/integrations/{INTEGRATION}"
    current = call("integration-get", "GET", path, token)[1]
    endpoints = current.get("endpoints") or []
    if endpoints and all(e.get("sandbox") for e in endpoints):
        return
    body = {"integrationName": current["integrationName"], "description": current.get("description"),
            "endpoints": [{"endpointCode": e["endpointCode"], "baseUrl": e["baseUrl"], "sandbox": True} for e in endpoints],
            "expectedVersion": current["version"], "reason": REASON + "：合成渠道出站地址标记沙箱"}
    call("integration-update-sandbox", "PUT", path, token, body)
    print("saved   出站地址标记沙箱")


def account_row():
    rows = h.sql("channel-connector", "SELECT account_status, IFNULL(account_vault_token,'') FROM t_channel_account "
                                      f"WHERE channel_account_id={ACCOUNT_ID} AND channel_id={CHANNEL_ID}")
    if not rows:
        raise SystemExit("mor_channel_connector_v2e2e 缺少渠道账号：先执行 sql/50-channel-connector-payermax-mock.sql")
    return rows[0]


def protect_account():
    status, token = account_row()
    if status == "ACTIVE" and len(token) == 43:
        print("exists  渠道账号 ACTIVE，已有 Vault Token")
        return token
    if status != "DRAFT" or token:
        raise SystemExit(f"渠道账号状态不符合夹具前置条件：status={status} hasToken={bool(token)}")
    keys = mock_keys()
    config = {"schemaVersion": 1, **identity(),
              "parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
              "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"]}}
    protected = call("vault-secret-protect", "POST", "/vault-admin/v1/manual/secret-protect", _token["value"] or login(), {
        "submissionId": "v2e2e-" + uuid.uuid4().hex[:24], "dataTypeCode": "CHANNEL_ACCOUNT_CONFIG_V1",
        "value": json.dumps(config, separators=(",", ":")), "reasonCode": "CHANNEL_CONFIGURATION",
        "ticketRef": "STAGE5-LOCAL-E2E"})[1]
    vault_token = protected["token"]
    # 夹具写入：channel-connector 无账号管理接口；只在 DRAFT 且无 Token 时写入。
    rows = h.sql("channel-connector",
                 f"UPDATE t_channel_account SET account_vault_token='{vault_token}', account_status='ACTIVE', "
                 f"update_time=UTC_TIMESTAMP(6) WHERE channel_account_id={ACCOUNT_ID} AND account_status='DRAFT' "
                 "AND account_vault_token IS NULL; SELECT ROW_COUNT();")
    if rows[-1][0] != "1":
        raise SystemExit("渠道账号 Token 写入失败（前置条件不满足）")
    print("saved   Vault CHANNEL_ACCOUNT_CONFIG_V1（REFERENCE Token 已写入渠道账号，账号 ACTIVE）")
    return vault_token


def check_candidates():
    status, text = h.private_call("channel-connector", "POST", "/channel-connector/v1/mid-candidates", body={
        "entityNo": ENTITY_NO, "criteria": {"paymentMethodCode": PAYMENT_METHOD, "schemeCode": None, "countryCode": "US",
                                            "currency": "USD", "mccCode": MCC}})
    body = json.loads(text) if text.startswith(("[", "{")) else {}
    items = body if isinstance(body, list) else body.get("items") or body.get("candidates") or []
    mine = [c for c in items if str(c.get("midId")) == str(MID_ID)]
    ok = status == 200 and mine and "PAY" in (mine[0].get("supportedOperations") or [])
    print(("PASS" if ok else "FAIL"), "候选查询返回合成 PayerMax MID 且支持 PAY",
          f"HTTP {status} candidates={len(items)} ops={(mine[0].get('supportedOperations') if mine else None)}")
    return bool(ok)


def main():
    configure, configure_card, configure_refund = install_harness()
    mock_keys()
    configure_platform(configure, configure_card, configure_refund)
    protect_account()
    ok = check_candidates()
    (RUNTIME / "channel-setup.json").write_text(json.dumps(results, ensure_ascii=False, indent=1) + "\n")
    print(f"管理调用 {len(results)} 次；记录 {RUNTIME / 'channel-setup.json'}")
    raise SystemExit(0 if ok else 1)


if __name__ == "__main__":
    main()
