"""任务卡 P2-stage5 E：第二段新能力本机验证（服务渠道、调用授权、入口隔离、出站地址守卫、暂停、执行分舱、导航）。

控制台操作一律经 Nginx 18444 → Gateway → ops-bff → channel-connector（同时验证块 129 路由与 Gateway Snapshot）；
通用入口 POST /channel-connector/v1/service-executions 直接调用 channel-connector 私网端口，调用方经 X-Caller-Service 传入
（本机 internal-auth OFF，按 ServiceChannelExecutionApi 合同信任该头）。回显服务为 stage5p2_echo_server.py（本脚本启动并在结束时关闭），
运行目录 local-environment/e2e/runtime/stage5p2-echo/。合成凭据为本次随机生成，只在进程内存在；输出与流水只含末 4 位。

子命令：
  setup-and-run <out.json>     E2–E6、E8 与已删除接入方 CRUD 探针（写入 <out.json>，并记录服务渠道 ID 等到运行目录 state.json）
  compartment <out.json>       E7：channel-connector 已以 service-max-concurrency=1 重启后执行；回显延迟 3 秒，两次并发通用入口调用 +
                               同时一次支付入口 REFUND；结果并入 <out.json>
用法：python3 -I stage5p2_service.py <子命令> <out.json>
"""
import json
import secrets as pysecrets
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage5_e2e as s5  # noqa: E402
from stage5_e2e import h, ops, login, actor_of, log_offset, log_since  # noqa: E402

ROOT = HERE.parents[3]
RUN = ROOT / "local-environment/e2e/runtime/stage5p2-echo"
CHANNEL_ADMIN = "/channel-admin/v1"
EXTERNAL_ADMIN = "/external-api-admin/v1"
SERVICE_PATH = "/channel-connector/v1/service-executions"
CODE = "S5P2_ECHO"
STD = "S5P2_ECHO_PING"
API = "ECHO_CALL"
CALLER = "counterparty-service"
OTHER_CALLER = "risk-decision-service"
REASON = "统一对外渠道出口第二段阶段 5 服务渠道验证"
SECRET = "s5p2-" + pysecrets.token_urlsafe(24)  # 合成凭据：只在内存，不打印
RESULTS = {}


def record(key, ok, **facts):
    RESULTS[key] = {"ok": bool(ok), **facts}
    print(("PASS " if ok else "FAIL ") + key + " " + json.dumps(facts, ensure_ascii=False)[:300])
    return ok


def problem(body):
    """Problem / 错误体的稳定字段（不含 requestId、时间）。"""
    if not isinstance(body, dict):
        return body
    out = {k: body.get(k) for k in ("code", "title", "status", "detail") if body.get(k) is not None}
    errors = body.get("errors") or body.get("fieldErrors")
    if errors:
        out["errors"] = errors
    return out


# ---------- 回显服务 ----------

def echo_start():
    RUN.mkdir(parents=True, exist_ok=True)
    for name in ("echo-port", "echo-journal.jsonl"):
        (RUN / name).unlink(missing_ok=True)
    proc = subprocess.Popen([sys.executable, "-I", str(HERE / "stage5p2_echo_server.py"), str(RUN)],
                            stdout=subprocess.DEVNULL, stderr=open(RUN / "echo-stderr.log", "w"))
    for _ in range(50):
        if (RUN / "echo-port").exists():
            port = int((RUN / "echo-port").read_text())
            (RUN / "echo.pid").write_text(str(proc.pid))
            return proc, port
        time.sleep(0.1)
    proc.kill()
    raise SystemExit("回显服务未启动")


def echo_stop(proc):
    proc.send_signal(signal.SIGTERM)
    proc.wait(timeout=10)
    (RUN / "echo.pid").unlink(missing_ok=True)


def echo_delay(port, millis):
    request = urllib.request.Request(f"http://127.0.0.1:{port}/__control", data=json.dumps({"delayMs": millis}).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.loads(response.read())


def echo_journal():
    path = RUN / "echo-journal.jsonl"
    return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []


# ---------- 调用 ----------

def service_call(caller, channel_code, standard_api, input_body):
    headers = {"X-Caller-Service": caller} if caller else {}
    status, text = h.private_call("channel-connector", "POST", SERVICE_PATH, headers=headers, timeout=60,
                                  body={"channelCode": channel_code, "standardApiCode": standard_api, "input": input_body})
    try:
        body = json.loads(text) if text else {}
    except ValueError:
        body = {"raw": text[:200]}
    return status, body


def ping_input(tag):
    return {"ping": f"s5p2-{tag}-{pysecrets.token_hex(3)}"}


def call_and_observe(tag, caller=CALLER, channel_code=CODE, standard_api=STD):
    """调用通用入口并返回 (状态, 体, 本次回显流水增量, 本次 channel-connector 访问日志行)。"""
    before = len(echo_journal())
    mark = log_offset("channel-connector")
    status, body = service_call(caller, channel_code, standard_api, ping_input(tag))
    time.sleep(0.5)
    seen = echo_journal()[before:]
    access = [line.split(" : ", 1)[-1] for line in log_since("channel-connector", mark).splitlines()
              if "channel.outbound.access" in line]
    return status, body, seen, access


def result_view(status, body):
    if status == 200:
        return {"httpStatus": status, "executeStatus": body.get("executeStatus"), "outputs": body.get("outputs")}
    return {"httpStatus": status, "problem": problem(body)}


# ---------- 控制台 ----------

def must(label, status, body, expected):
    if status not in expected:
        raise SystemExit(f"{label} 失败：HTTP {status} {json.dumps(problem(body), ensure_ascii=False)[:400]}")
    return body


def channel_list(token, channel_type=None):
    query = "?page=1&size=50" + (f"&channelType={channel_type}" if channel_type else "")
    status, body = ops(token, "GET", CHANNEL_ADMIN + "/channels" + query)
    return status, body


def list_filter(token):
    out = {}
    for kind in (None, "PAYMENT", "SERVICE"):
        status, body = channel_list(token, kind)
        items = body.get("items") or []
        out[kind or "ALL"] = {"httpStatus": status, "total": body.get("total"),
                              "items": [[i.get("channelCode"), i.get("channelType"), i.get("status"), i.get("endpointCount"),
                                         i.get("activeGrantCount")] for i in items]}
    ok = all(v["httpStatus"] == 200 for v in out.values()) \
        and all(i[1] == "PAYMENT" for i in out["PAYMENT"]["items"]) and all(i[1] == "SERVICE" for i in out["SERVICE"]["items"]) \
        and len(out["ALL"]["items"]) == len(out["PAYMENT"]["items"]) + len(out["SERVICE"]["items"])
    return ok, out


def standard_api_body():
    def field(path, data_type, required):
        return {"fieldLocation": "BODY", "fieldPath": path, "fieldName": path, "dataType": data_type, "required": required,
                "description": None}
    return {"standardApiCode": STD, "standardApiName": "阶段 5 回显探测", "description": "第二段阶段 5 本机服务渠道验证用",
            "requestFields": [field("ping", "STRING", True), field("channelAccount", "OBJECT", True),
                              field("channelAccount.channelCode", "STRING", True),
                              field("channelAccount.channelAccountId", "STRING", True),
                              field("channelAccount.accountVaultToken", "STRING", True)],
            "responseFields": [field("echo", "STRING", True)], "reason": REASON}


def api_body():
    def field(path):
        return {"fieldLocation": "BODY", "fieldPath": path, "fieldName": path, "dataType": "STRING", "required": True,
                "description": None}
    definition = {
        "params": [{"name": "apiKey", "dataType": "PROTECTED_HANDLE", "required": True}],
        "fixedValues": {},
        "transport": {"protocol": "HTTP", "method": "POST", "endpointCode": "ECHO", "pathTemplate": "/echo",
                      "connectTimeoutMs": 2000, "responseTimeoutMs": 10000},
        "requestEncoding": "JSON", "responseDecoding": "JSON", "decimalEncoding": "NUMBER",
        "credentialFields": ["BODY:apiKey"], "scripts": [],
        "statusRules": [{"condition": "http.responseStatus == 200 && http.responseBody.code == 0", "executeStatus": "SUCCESS"}],
        "outputs": {"SUCCESS": [{"name": "echo", "dataType": "STRING", "required": True,
                                 "value": {"kind": "PATH", "path": "http.responseBody.echo"}}], "FAIL": []}}
    return {"apiCode": API, "apiName": "回显", "description": None, "requestFields": [field("ping"), field("apiKey")],
            "responseFields": [field("echo")], "definition": definition, "reason": REASON}


def flow_body():
    def path(p):
        return {"kind": "PATH", "path": p}
    flow = {
        "timeoutMs": 20000,
        "initializers": [{"id": "channelAccount", "function": "CHANNEL_ACCOUNT_CONFIG", "required": True, "args": {
            "vaultToken": path("input.channelAccount.accountVaultToken"), "channelCode": path("input.channelAccount.channelCode"),
            "channelAccountId": path("input.channelAccount.channelAccountId")}}],
        "entry": "call",
        "nodes": [{"id": "call", "integrationApi": API,
                   "request": {"BODY:ping": path("input.ping"), "BODY:apiKey": path("resources.channelAccount.credentials.apiKey")},
                   "params": {"apiKey": path("resources.channelAccount.credentials.apiKey")}}],
        "returns": [{"id": "done", "executeStatus": "SUCCESS", "outputs": {"echo": path("steps.call.outputs.echo")}},
                    {"id": "unknown", "executeStatus": "ERROR"}],
        "edges": [{"from": "call", "to": "done", "condition": "steps.call.executeStatus == 'SUCCESS'", "priority": 1},
                  {"from": "call", "to": "unknown", "default": True}]}
    return {"standardApiCode": STD, "flowDefinition": flow, "reason": REASON}


def vault_events(since, callers=("channel-connector-service",)):
    names = ",".join(f"'{c}'" for c in callers)
    rows = h.sql("vault", "SELECT caller_service, action, IFNULL(data_type,''), IFNULL(purpose,''), outcome "
                          f"FROM t_vault_access_event WHERE create_time >= '{since}' AND caller_service IN ({names}) ORDER BY create_time")
    return [dict(zip(("callerService", "action", "dataType", "purpose", "outcome"), r)) for r in rows]


FIRST_ATTEMPT = {
    # 第一次执行（2026-10-22T04:28:44Z）在激活凭据账号时中止（账号激活要求渠道已 ACTIVE，脚本顺序错误，非缺陷）；
    # 以下为该次控制台输出原样，渠道沿用该次创建的 S5P2_ECHO。
    "E2.create": {"httpStatus": 201, "channelType": "SERVICE", "status": "DRAFT", "version": 1, "endpoints": []},
    "E2.activateWithoutEndpoints": {"httpStatus": 400, "problem": {"code": "BUSINESS_CHECK_FAILED", "title": "Bad Request",
                                                                   "detail": "渠道未配置出站地址: S5P2_ECHO"}},
    "E2.endpoints": {"httpStatus": 200, "version": 2, "endpointCount": 1},
    "accountActivationWhileDraft": {"httpStatus": 400, "code": "BUSINESS_CHECK_FAILED",
                                    "detail": "技术账号激活前置不满足（渠道须为 ACTIVE 且账号已写入配置）: 账号 238881240718049280, 渠道状态 DRAFT, 已配置 true"},
}


def find_channel(token):
    _, body = channel_list(token, "SERVICE")
    return next((i for i in body.get("items") or [] if i.get("channelCode") == CODE), None)


def setup_service_channel(token, port):
    state = {}
    existing = find_channel(token)
    if existing is None:
        status, body = ops(token, "POST", CHANNEL_ADMIN + "/channels", {
            "channelType": "SERVICE", "channelCode": CODE, "channelName": "阶段 5 回显服务渠道", "description": "本机回显服务（P2 阶段 5）"})
        channel = must("新建服务渠道", status, body, (201,))
        record("E2.create", channel.get("channelType") == "SERVICE" and channel.get("status") == "DRAFT" and channel.get("version") == 1,
               httpStatus=status, channelType=channel.get("channelType"), status=channel.get("status"), version=channel.get("version"))
        cid = channel["channelId"]
        status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON})
        record("E2.activateWithoutEndpoints", status in (400, 409), httpStatus=status, problem=problem(body))
    else:
        cid = existing["channelId"]
        for key in ("E2.create", "E2.activateWithoutEndpoints"):
            record(key, True, firstAttempt=FIRST_ATTEMPT[key])
        RESULTS["E2.firstAttemptAccountActivationWhileDraft"] = {"ok": True, **FIRST_ATTEMPT["accountActivationWhileDraft"]}
    state["channelId"] = cid
    status, detail = ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    wanted = [{"endpointCode": "ECHO", "baseUrl": f"http://127.0.0.1:{port}", "sandbox": False}]
    status, body = ops(token, "PUT", f"{CHANNEL_ADMIN}/channels/{cid}/endpoints",
                       {"expectedVersion": detail["version"], "endpoints": wanted})
    updated = must("写入出站地址", status, body, (200,))
    record("E2.endpoints", updated.get("version") == detail["version"] + 1 and len(updated.get("endpoints") or []) == 1,
           httpStatus=status, versionBefore=detail["version"], version=updated.get("version"), endpoints=updated.get("endpoints"),
           firstAttempt=FIRST_ATTEMPT["E2.endpoints"] if existing else None)
    if updated.get("status") != "ACTIVE":
        status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON})
        record("E2.activate", status == 200 and body.get("status") == "ACTIVE", httpStatus=status, status=body.get("status"),
               note="激活只要求已配置出站地址：此时尚无凭据、标准 API 实现与授权")
    # 凭据：服务渠道账号不带 OE，真实 Vault Protect（每次运行生成新的合成凭据并重写配置）
    since = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")
    _, listing = ops(token, "GET", f"{CHANNEL_ADMIN}/channel-accounts?channelId={cid}&page=1&size=50")
    account = (listing.get("items") or [None])[0]
    if account is None:
        status, body = ops(token, "POST", CHANNEL_ADMIN + "/channel-accounts", {"channelId": cid, "accountName": "回显凭据", "remark": REASON})
        account = must("新建凭据账号", status, body, (201,))
    state["channelAccountId"] = aid = account["channelAccountId"]
    status, body = ops(token, "PUT", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/configuration", {
        "parameters": {"region": "local"}, "credentials": {"apiKey": SECRET}})
    configured = must("写入凭据", status, body, (200,))
    if account.get("status") != "ACTIVE":
        status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/status", {"targetStatus": "ACTIVE", "reason": REASON})
        must("激活凭据账号", status, body, (200,))
    vault_token, entity_no = h.sql("channel-connector", "SELECT IFNULL(account_vault_token,''), IFNULL(entity_no,'<null>') "
                                                        f"FROM t_channel_account WHERE channel_account_id={int(aid)}")[0]
    state["vaultToken"] = vault_token
    status, listing = ops(token, "GET", f"{CHANNEL_ADMIN}/channel-accounts?channelId={cid}&page=1&size=50")
    listing_text = json.dumps(listing)
    item = (listing.get("items") or [{}])[0]
    record("E2.credential", account.get("entityNo") is None and entity_no == "<null>" and bool(vault_token)
           and vault_token not in listing_text and SECRET not in listing_text and item.get("vaultTokenSuffix") == vault_token[-4:]
           and item.get("status") == "ACTIVE",
           accountEntityNo=account.get("entityNo"), dbEntityNo=entity_no, configureSuffixLen=len(configured.get("vaultTokenSuffix") or ""),
           listSuffixMatchesDbTokenLast4=item.get("vaultTokenSuffix") == vault_token[-4:],
           listContainsFullToken=vault_token in listing_text, listContainsSecret=SECRET in listing_text,
           listItemKeys=sorted(item.keys()), listStatus=item.get("status"), vaultEvents=vault_events(since))
    # 标准 API、原子 API、实现（已存在则沿用）
    for key, get_path, post_path, body in (
            ("E2.standardApi", f"{EXTERNAL_ADMIN}/standard-apis/{STD}", EXTERNAL_ADMIN + "/standard-apis", standard_api_body()),
            ("E2.atomicApi", f"{EXTERNAL_ADMIN}/integrations/{CODE}/apis/{API}", f"{EXTERNAL_ADMIN}/integrations/{CODE}/apis", api_body()),
            ("E2.implementation", f"{EXTERNAL_ADMIN}/integrations/{CODE}/implementations/{STD}",
             f"{EXTERNAL_ADMIN}/integrations/{CODE}/implementations", flow_body())):
        status, _ = ops(token, "GET", get_path)
        if status == 200:
            record(key, True, existed=True)
            continue
        status, response = ops(token, "POST", post_path, body)
        record(key, status in (200, 201), httpStatus=status, problem=None if status in (200, 201) else problem(response))
    return state


def grants(token, state):
    cid = state["channelId"]
    status, body, seen, access = call_and_observe("nogrant")
    record("E3.notGranted", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED" and not seen,
           **result_view(status, body), echoRequests=len(seen), accessLogLines=len(access))
    status, body, seen, _ = call_and_observe("nocaller", caller=None)
    record("E3.missingCaller", status == 400 and not seen, **result_view(status, body), echoRequests=len(seen))
    status, body = ops(token, "POST", CHANNEL_ADMIN + "/channel-call-grants",
                       {"channelId": cid, "callerService": CALLER, "standardApiCode": STD})
    grant = must("新建授权", status, body, (201,))
    state["callGrantId"] = gid = grant["callGrantId"]
    record("E3.createGrant", grant.get("grantStatus") == "ACTIVE" and grant.get("version") == 1, httpStatus=status,
           grant={k: grant.get(k) for k in ("callerService", "standardApiCode", "standardApiName", "grantStatus", "version")})
    since = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")
    status, body, seen, access = call_and_observe("granted")
    entry = seen[0] if seen else {}
    credential = (entry.get("body") or {}).get("apiKey") or {}
    record("E3.grantedSuccess", status == 200 and body.get("executeStatus") == "SUCCESS" and len(seen) == 1
           and credential.get("present") and credential.get("last4") == SECRET[-4:]
           and any(f"callerService={CALLER} channelCode={CODE} channelType=SERVICE standardApiCode={STD} apiCode={API} "
                   "executeStatus=SUCCESS httpStatus=200" in line for line in access),
           **result_view(status, body), echo={k: entry.get(k) for k in ("method", "path", "headerNames")},
           echoBodyKeys=sorted((entry.get("body") or {}).keys()), credentialInjected=credential.get("present"),
           credentialLast4Matches=credential.get("last4") == SECRET[-4:], accessLog=access,
           vaultEvents=vault_events(since))
    status, body, seen, _ = call_and_observe("othercaller", caller=OTHER_CALLER)
    record("E3.otherCallerNotGranted", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED" and not seen,
           **result_view(status, body), echoRequests=len(seen))
    status, body = ops(token, "POST", CHANNEL_ADMIN + "/channel-call-grants",
                       {"channelId": cid, "callerService": CALLER, "standardApiCode": STD})
    record("E3.duplicateGrant", status == 409, httpStatus=status, problem=problem(body))
    status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channel-call-grants/{gid}/status",
                       {"targetStatus": "DISABLED", "expectedVersion": 1, "reason": REASON})
    record("E3.disableGrant", status == 200 and body.get("grantStatus") == "DISABLED" and body.get("version") == 2,
           httpStatus=status, grantStatus=body.get("grantStatus"), version=body.get("version"))
    status, body, seen, _ = call_and_observe("disabled")
    record("E3.disabledRejected", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED" and not seen,
           **result_view(status, body), echoRequests=len(seen))
    status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channel-call-grants/{gid}/status",
                       {"targetStatus": "DISABLED", "expectedVersion": 1, "reason": REASON})
    record("E3.staleGrantVersion", status == 409, httpStatus=status, problem=problem(body))
    status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channel-call-grants/{gid}/status",
                       {"targetStatus": "ACTIVE", "expectedVersion": 2, "reason": REASON})
    record("E3.enableGrant", status == 200 and body.get("grantStatus") == "ACTIVE" and body.get("version") == 3,
           httpStatus=status, grantStatus=body.get("grantStatus"), version=body.get("version"))
    status, body, seen, _ = call_and_observe("reenabled")
    record("E3.reenabledSuccess", status == 200 and body.get("executeStatus") == "SUCCESS" and len(seen) == 1,
           **result_view(status, body), echoRequests=len(seen))
    status, body = ops(token, "GET", f"{CHANNEL_ADMIN}/config-changes?targetType=CHANNEL_CALL_GRANT&targetId={gid}&page=1&size=50")
    changes = s5.normalize(body.get("items") or [])
    record("E3.grantChanges", status == 200 and len(changes) == 3, httpStatus=status, total=body.get("total"), changes=changes)
    for bad in ("Counterparty_Service", "x", "counterparty.service"):
        status, body = ops(token, "POST", CHANNEL_ADMIN + "/channel-call-grants",
                           {"channelId": cid, "callerService": bad, "standardApiCode": STD})
        record(f"E3.invalidCaller[{bad}]", status == 422, httpStatus=status, problem=problem(body))
    status, body = ops(token, "GET", f"{CHANNEL_ADMIN}/channel-call-grants?channelId={cid}&page=1&size=50")
    record("E3.grantList", status == 200 and body.get("total") in (1, "1"), httpStatus=status, total=body.get("total"),
           items=[[g.get("callerService"), g.get("standardApiCode"), g.get("standardApiName"), g.get("grantStatus"), g.get("version")]
                  for g in body.get("items") or []])
    status, body = channel_list(token, "SERVICE")
    item = next((i for i in body.get("items") or [] if i.get("channelCode") == CODE), {})
    record("E3.activeGrantCount", item.get("activeGrantCount") == 1, activeGrantCount=item.get("activeGrantCount"),
           endpointCount=item.get("endpointCount"))


def isolation(state):
    journal_before = len(s5.journal())
    status, body, seen, _ = call_and_observe("paymentchannel", channel_code=s5.CODE, standard_api="REFUND")
    record("E4.paymentChannelViaServiceEntry", status == 400 and not seen and len(s5.journal()) == journal_before,
           **result_view(status, body))
    pmx = json.loads(s5.STATE.read_text())
    before_echo, before_pmx = len(echo_journal()), len(s5.journal())
    result = s5.execute({"channelId": state["channelId"], "channelAccountId": state["channelAccountId"], "midId": pmx["midId"]},
                        "REFUND", "ORIGINAL_TRANSACTION", "5.00",
                        {"refund": {"refundId": "RFDS5P2ISO1", "paymentAttemptId": "1", "amount": {"value": "5.00", "currency": "USD"},
                                    "reason": "isolation"}})
    record("E4.serviceChannelViaPaymentEntry", result["outcome"] == "NOT_SENT" and result["failureCode"] == "INTEGRATION_CONFIG_INVALID"
           and len(echo_journal()) == before_echo and len(s5.journal()) == before_pmx,
           httpStatus=result["httpStatus"], outcome=result["outcome"], failureCode=result["failureCode"],
           echoRequests=len(echo_journal()) - before_echo, payermaxRequests=len(s5.journal()) - before_pmx)


def endpoints(token, state, port):
    cid = state["channelId"]
    status, detail = ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    version = detail.get("version")
    echo = {"endpointCode": "ECHO", "baseUrl": f"http://127.0.0.1:{port}", "sandbox": False}
    cases = [
        ("E5.privateNetwork", [echo, {"endpointCode": "PRIV", "baseUrl": "https://10.1.2.3", "sandbox": False}], version, 422,
         "endpoints[1].baseUrl"),
        ("E5.localhost", [{**echo, "baseUrl": "https://localhost:8443"}], version, 422, "endpoints[0].baseUrl"),
        ("E5.linkLocal", [{**echo, "baseUrl": "https://169.254.169.254"}], version, 422, "endpoints[0].baseUrl"),
        ("E5.referencedEndpointRemoved", [{"endpointCode": "OTHER", "baseUrl": "https://echo.example.test", "sandbox": False}],
         version, 409, None),
        ("E5.staleVersion", [echo], version - 1, 409, None),
    ]
    for key, items, expected_version, want, field in cases:
        status, body = ops(token, "PUT", f"{CHANNEL_ADMIN}/channels/{cid}/endpoints",
                           {"expectedVersion": expected_version, "endpoints": items})
        fields = json.dumps(body.get("errors") or body.get("fieldErrors") or [], ensure_ascii=False)
        ok = status == want and (field is None or field in fields)
        record(key, ok, httpStatus=status, problem=problem(body))
    status, after = ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    record("E5.unchangedAfterRejections", after.get("version") == version and after.get("endpoints") == detail.get("endpoints"),
           version=after.get("version"))
    status, body = ops(token, "GET", f"{CHANNEL_ADMIN}/config-changes?targetType=CHANNEL&targetId={cid}&page=1&size=50")
    rows = s5.normalize(body.get("items") or [])
    record("E5.channelChanges", status == 200, httpStatus=status, changes=rows)


def suspend(token, state):
    cid = state["channelId"]
    status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "SUSPENDED", "reason": REASON})
    record("E6.suspend", status == 200 and body.get("status") == "SUSPENDED", httpStatus=status, status=body.get("status"))
    status, body, seen, _ = call_and_observe("suspended")
    record("E6.suspendedRejected", status == 400 and not seen, **result_view(status, body), echoRequests=len(seen))
    status, body = ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON})
    record("E6.resume", status == 200 and body.get("status") == "ACTIVE", httpStatus=status, status=body.get("status"))
    status, body, seen, _ = call_and_observe("resumed")
    record("E6.resumedSuccess", status == 200 and body.get("executeStatus") == "SUCCESS" and len(seen) == 1,
           **result_view(status, body), echoRequests=len(seen))


def navigation(token):
    status, nav = ops(token, "GET", "/authorization/v1/navigation")
    resources = {r["resourceCode"]: r for r in nav.get("resources", [])}
    channel_page = resources.get("OPERATIONS_CHANNEL_CHANNELS", {})
    button = resources.get("OPERATIONS_CHANNEL_CALL_GRANT_MANAGE", {})
    record("E8.navigation", status == 200 and "OPERATIONS_EXTERNAL_INTEGRATIONS" not in resources
           and "OPERATIONS_EXTERNAL_INTEGRATION_MANAGE" not in resources
           and channel_page.get("displayName") == "渠道" and str(channel_page.get("resourceId")) == "900000000000092302"
           and str(button.get("parentResourceId")) == "900000000000092302" and "OPERATIONS_EXTERNAL_STANDARD_APIS" in resources,
           httpStatus=status, hasIntegrationsPage="OPERATIONS_EXTERNAL_INTEGRATIONS" in resources,
           channelPage={k: channel_page.get(k) for k in ("resourceId", "displayName", "frontendRoute")},
           callGrantButton={k: button.get(k) for k in ("resourceId", "displayName", "resourceType", "parentResourceId")},
           standardApiPage={k: resources.get("OPERATIONS_EXTERNAL_STANDARD_APIS", {}).get(k) for k in ("displayName", "frontendRoute")})


def removed_crud(token):
    """已删除的接入方 5 个 CRUD：经 Gateway 均 404；另直连 ops-bff 与 channel-connector 私网确认处理器已下线。"""
    probes = {}
    for name, method, path, body in (
            ("list", "GET", "/integrations?page=1&size=10", None), ("detail", "GET", f"/integrations/{s5.CODE}", None),
            ("create", "POST", "/integrations", {"integrationCode": "S5P2_GONE", "integrationName": "x", "endpoints": []}),
            ("update", "PUT", f"/integrations/{s5.CODE}", {"integrationName": "x", "expectedVersion": 1}),
            ("delete", "DELETE", f"/integrations/{s5.CODE}", {"expectedVersion": 1, "reason": REASON})):
        status, response = ops(token, method, EXTERNAL_ADMIN + path, body)
        probes[f"gateway.{name}"] = [status, response.get("code") if isinstance(response, dict) else None]
    for name, method, path in (("list", "GET", "/integrations"), ("detail", "GET", f"/integrations/{s5.CODE}"),
                               ("create", "POST", "/integrations"), ("delete", "DELETE", f"/integrations/{s5.CODE}")):
        status, _ = private_status("http://127.0.0.1:18201" + EXTERNAL_ADMIN + path, method)
        probes[f"ops-bff.{name}"] = [status]
        status, _ = h.private_call("channel-connector", method, EXTERNAL_ADMIN + path, body={} if method != "GET" else None)
        probes[f"channel-connector.{name}"] = [status]
    record("D2.removedIntegrationCrud", all(v[0] in (404, 405) for v in probes.values()) and
           all(v[0] == 404 for k, v in probes.items() if k.startswith("gateway.")), probes=probes)


def private_status(url, method):
    request = urllib.request.Request(url, data=(b"{}" if method != "GET" else None), method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, None
    except urllib.error.HTTPError as failure:
        return failure.code, None


# ---------- E7 分舱 ----------

def compartment(port):
    pmx = json.loads(s5.STATE.read_text())
    echo_delay(port, 3000)
    results = {}

    def service(slot):
        started = time.time()
        status, body = service_call(CALLER, CODE, STD, ping_input(f"busy{slot}"))
        results[f"service{slot}"] = {**result_view(status, body), "elapsedMs": int((time.time() - started) * 1000)}

    def refund():
        time.sleep(0.8)
        started = time.time()
        r = s5.execute(pmx, "REFUND", "ORIGINAL_TRANSACTION", "5.00", {"refund": {
            "refundId": f"RFD{pysecrets.randbelow(10**11):011d}2", "paymentAttemptId": str(10**15 + pysecrets.randbelow(10**15)),
            "amount": {"value": "5.00", "currency": "USD"}, "reason": "Stage 5 P2 compartment"}})
        results["paymentRefund"] = {"httpStatus": r["httpStatus"], "outcome": r["outcome"], "failureCode": r["failureCode"],
                                    "channelJournal": r["channelJournal"]["count"], "elapsedMs": int((time.time() - started) * 1000)}

    before = len(echo_journal())
    mark = log_offset("channel-connector")
    threads = [threading.Thread(target=service, args=(1,)), threading.Thread(target=service, args=(2,)),
               threading.Thread(target=refund)]
    threads[0].start()
    time.sleep(0.3)
    threads[1].start()
    threads[2].start()
    for t in threads:
        t.join(60)
    echo_delay(port, 0)
    services = [results["service1"], results["service2"]]
    busy = [s for s in services if s["httpStatus"] == 503 and (s.get("problem") or {}).get("code") == "CHANNEL_EXECUTION_BUSY"]
    done = [s for s in services if s["httpStatus"] == 200 and s.get("executeStatus") == "SUCCESS"]
    access = [line.split(" : ", 1)[-1] for line in log_since("channel-connector", mark).splitlines() if "channel.outbound.access" in line]
    record("E7.compartment", len(busy) == 1 and len(done) == 1 and results["paymentRefund"]["outcome"] == "SUCCESS"
           and results["paymentRefund"]["elapsedMs"] < 3000, results=results, echoRequests=len(echo_journal()) - before,
           accessLog=access)


# ---------- 入口 ----------

def write(out, state):
    pmx = json.loads(s5.STATE.read_text())
    text = json.dumps({"results": RESULTS, "finishedAt": datetime.now(timezone.utc).isoformat()}, ensure_ascii=False, indent=1)
    forbidden = [SECRET, state.get("vaultToken") or "\0"] + s5.forbidden_values(pmx)
    leaked = [i for i, v in enumerate(forbidden) if v and v in text]
    if leaked:
        raise SystemExit(f"输出含私密值（序号 {leaked}），未写入")
    for placeholder, value in s5.identifiers().items():
        text = text.replace(value, placeholder)
    Path(out).write_text(text + "\n")
    print(f"写入 {out}")


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("setup-and-run", "compartment"):
        raise SystemExit(__doc__)
    command, out = sys.argv[1], Path(sys.argv[2])
    token = login()
    proc, port = echo_start()
    try:
        if command == "setup-and-run":
            ok, out_list = list_filter(token)
            record("E2.listFilterBefore", ok, lists=out_list)
            state = setup_service_channel(token, port)
            (RUN / "state.json").write_text(json.dumps({k: v for k, v in state.items() if k != "vaultToken"}))
            ok, out_list = list_filter(token)
            record("E2.listFilterAfter", ok and any(i[0] == CODE for i in out_list["SERVICE"]["items"]), lists=out_list)
            grants(token, state)
            isolation(state)
            endpoints(token, state, port)
            suspend(token, state)
            navigation(token)
            removed_crud(token)
            RESULTS["_meta"] = {"adminActor": actor_of(token), "channelId": state["channelId"], "echoPort": port,
                                "startedAt": datetime.now(timezone.utc).isoformat()}
        else:
            state = json.loads((RUN / "state.json").read_text())
            previous = json.loads(out.read_text())["results"]
            RESULTS.update(previous)
            # 回显服务端口每次不同：先把出站地址指向本次端口（同时验证 PUT 出站地址成功路径的版本递增）
            status, detail = ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{state['channelId']}")
            status, body = ops(token, "PUT", f"{CHANNEL_ADMIN}/channels/{state['channelId']}/endpoints", {
                "expectedVersion": detail["version"],
                "endpoints": [{"endpointCode": "ECHO", "baseUrl": f"http://127.0.0.1:{port}", "sandbox": False}]})
            record("E7.repointEndpoint", status == 200 and body.get("version") == detail["version"] + 1, httpStatus=status,
                   version=body.get("version"))
            status, body, seen, _ = call_and_observe("warmup")
            record("E7.warmup", status == 200 and body.get("executeStatus") == "SUCCESS", **result_view(status, body))
            compartment(port)
        write(out, state)
    finally:
        echo_stop(proc)


if __name__ == "__main__":
    main()
