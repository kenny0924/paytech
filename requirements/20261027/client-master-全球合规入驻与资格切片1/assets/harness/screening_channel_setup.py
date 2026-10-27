"""全球合规入驻切片 1（R8 / S-06）：经 channel-connector 管理接口登记筛查标准 API 与合成筛查服务渠道，可重复执行。

登记内容（全部经管理接口，不写 SQL；已存在且一致则沿用，字段 / 定义 / 流程 / 出站地址不一致时按版本改写）：
  1) 标准 API SCREENING_SEARCH
       请求  subjectType(STRING,必填)、fullName(STRING,必填)、birthYear(STRING "YYYY")、countries[](STRING)、
             screeningTypes[](STRING,必填)、clientRef(STRING,必填)
       响应  runRef(STRING)；hits[](必填)：externalRef、entityType、listTypes[]、score(DECIMAL 0–100)、matchTypes[]、
             riskLevel?、matchName?（父容器逐层声明）；
             errorCode(STRING，FAIL 时)。FAIL 出口 hits 取 Mock 返回的空数组（每个出口须覆盖必填响应字段，表达式不开放 seq.list）
  2) 服务渠道 S_SCREENING_MOCK（SERVICE，无技术账号），出站地址 SCREENING = --mock-url，激活
  3) 原子 API SCREENING_SEARCH_CALL（POST /v1/screening/search，JSON，无凭据、无脚本）
       状态规则：HTTP 200 且 code==0 → SUCCESS；HTTP 200 且 code!=0 → FAIL；其他 → 结果未知（ERROR）
  4) 实现（流程无初始化项）：call → SUCCESS 出口输出 hits；FAIL 出口输出空 hits 与 errorCode；兜底 ERROR
  5) 调用授权 compliance-service × S_SCREENING_MOCK × SCREENING_SEARCH（已停用则再启用）
可选冒烟（--smoke）：以 compliance-service 调用通用入口 5 次（无命中 / PEP / SANCTIONS / FAIL / ERROR）与 1 次未授权调用方。

用法：
  python3 -I screening_channel_setup.py --base-url http://127.0.0.1:18110 --mock-url http://127.0.0.1:18191 \
      --actor-id <Operations 账号 ID> [--smoke] [--out result.json]
  --base-url  channel-connector 私网根地址（无 context path；本机 18110；mor-test 在 backend 网络内执行，见 README）
  --mock-url  合成筛查服务根地址（本机 http://127.0.0.1:18191；mor-test 为 Mock 容器固定私网 IP）
  --actor-id  X-Gateway-Actor-Subject（十进制 Operations 账号 ID，写入 created_by 与配置变更记录）
前提：channel-connector 的 allowed-schemes 含 http、trusted-networks 含 Mock 地址所在 /32（本机 127.0.0.1/32）。
输出不含凭据（本渠道无凭据）；冒烟只用合成姓名。
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

CHANNEL = "S_SCREENING_MOCK"
STD = "SCREENING_SEARCH"
API = "SCREENING_SEARCH_CALL"
ENDPOINT = "SCREENING"
CALLER = "compliance-service"
OTHER_CALLER = "client-master-service"
REASON = "全球合规入驻切片 1：合成筛查服务渠道与筛查标准 API（requirements/20261027/client-master-全球合规入驻与资格切片1）"
CHANNEL_ADMIN = "/channel-connector/v1/channels"
GRANTS = "/channel-connector/v1/channel-call-grants"
EXTERNAL_ADMIN = "/external-api-admin/v1"
SERVICE_PATH = "/channel-connector/v1/service-executions"
RESULTS = {}


class Client:
    def __init__(self, base_url, actor_id):
        self.base = base_url.rstrip("/")
        self.actor = actor_id

    def call(self, method, path, body=None, headers=None, timeout=60):
        data = json.dumps(body).encode() if body is not None else None
        all_headers = {"Accept": "application/json", "X-Gateway-Actor-Subject": self.actor}
        if data is not None:
            all_headers["Content-Type"] = "application/json"
        all_headers.update(headers or {})
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=all_headers)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as failure:
            status, text = failure.code, failure.read().decode(errors="replace")
        try:
            return status, json.loads(text) if text else {}
        except ValueError:
            return status, {"raw": text[:300]}


def record(key, ok, **facts):
    RESULTS[key] = {"ok": bool(ok), **facts}
    print(("PASS " if ok else "FAIL ") + key + " " + json.dumps(facts, ensure_ascii=False)[:400], flush=True)
    return ok


def problem(body):
    if not isinstance(body, dict):
        return body
    out = {k: body.get(k) for k in ("code", "title", "status", "detail") if body.get(k) is not None}
    errors = body.get("errors") or body.get("fieldErrors") or body.get("issues")
    if errors:
        out["errors"] = errors
    return out


def must(label, status, body, expected):
    if status not in expected:
        record(label, False, httpStatus=status, problem=problem(body))
        raise SystemExit(f"{label} 失败：HTTP {status} {json.dumps(problem(body), ensure_ascii=False)[:600]}")
    return body


# ---------- 定义 ----------

def field(path, data_type, required, description=None):
    return {"fieldLocation": "BODY", "fieldPath": path, "fieldName": path, "dataType": data_type, "required": required,
            "description": description}


def request_fields():
    return [field("subjectType", "STRING", True, "PERSON / ORGANIZATION"),
            field("fullName", "STRING", True, "筛查姓名或法定名称（拉丁字母）"),
            field("birthYear", "STRING", False, "出生年份 YYYY（个人可选；组织或未知时省略）"),
            field("countries", "ARRAY", False, "关联国家 ISO 3166-1 alpha-2"),
            field("countries[]", "STRING", False),
            field("screeningTypes", "ARRAY", True, "SANCTIONS / PEP / ADVERSE_MEDIA（compliance 每次只传一个）"),
            field("screeningTypes[]", "STRING", True),
            field("clientRef", "STRING", True, "调用方关联号（筛查运行 ID），不含个人信息")]


def response_fields():
    return [field("runRef", "STRING", False, "筛查服务本次运行编号"),
            field("hits", "ARRAY", True, "命中列表，无命中为空数组"),
            field("hits[]", "OBJECT", True),
            field("hits[].externalRef", "STRING", True, "筛查服务命中编号（同一运行内唯一）"),
            field("hits[].entityType", "STRING", True, "PERSON / ORGANIZATION / VESSEL / OTHER"),
            field("hits[].listTypes", "ARRAY", True, "命中名单类型"),
            field("hits[].listTypes[]", "STRING", True),
            field("hits[].score", "DECIMAL", True, "匹配分 0–100"),
            field("hits[].matchTypes", "ARRAY", True, "匹配方式"),
            field("hits[].matchTypes[]", "STRING", True),
            field("hits[].riskLevel", "STRING", False, "LOW / MEDIUM / HIGH / UNKNOWN"),
            field("hits[].matchName", "STRING", False, "名单中的匹配名称（compliance 不存储）"),
            field("errorCode", "STRING", False, "FAIL 时筛查服务拒绝原因码")]


def standard_api_body():
    return {"standardApiCode": STD, "standardApiName": "主体筛查检索",
            "description": "按姓名 / 法定名称检索 PEP、制裁等名单；SUCCESS 返回命中列表（可为空）。合规入驻案件筛查使用。",
            "requestFields": request_fields(), "responseFields": response_fields(), "reason": REASON}


def api_body():
    definition = {
        "params": [], "fixedValues": {},
        "transport": {"protocol": "HTTP", "method": "POST", "endpointCode": ENDPOINT, "pathTemplate": "/v1/screening/search",
                      "connectTimeoutMs": 2000, "responseTimeoutMs": 10000},
        "requestEncoding": "JSON", "responseDecoding": "JSON", "decimalEncoding": "NUMBER", "scripts": [],
        "statusRules": [
            {"condition": "http.responseStatus == 200 && http.responseBody.code == 0", "executeStatus": "SUCCESS"},
            {"condition": "http.responseStatus == 200 && http.responseBody.code != 0", "executeStatus": "FAIL"}],
        "outputs": {
            "SUCCESS": [{"name": "hits", "dataType": "ARRAY", "required": True,
                         "value": {"kind": "PATH", "path": "http.responseBody.hits"}},
                        {"name": "runRef", "dataType": "STRING", "required": False,
                         "value": {"kind": "PATH", "path": "http.responseBody.runRef"}}],
            "FAIL": [{"name": "hits", "dataType": "ARRAY", "required": True,
                      "value": {"kind": "PATH", "path": "http.responseBody.hits"}},
                     {"name": "errorCode", "dataType": "STRING", "required": False,
                      "value": {"kind": "PATH", "path": "http.responseBody.errorCode"}}]}}
    return {"apiCode": API, "apiName": "合成筛查检索", "description": "S_SCREENING_MOCK 的 POST /v1/screening/search",
            "requestFields": request_fields(), "responseFields": response_fields(),
            "definition": definition, "reason": REASON}


def flow_body():
    def path(p):
        return {"kind": "PATH", "path": p}
    request = {f"BODY:{name}": path(f"input.{name}")
               for name in ("subjectType", "fullName", "birthYear", "countries", "screeningTypes", "clientRef")}
    flow = {
        "timeoutMs": 20000, "initializers": [], "entry": "call",
        "nodes": [{"id": "call", "integrationApi": API, "request": request, "params": {}}],
        "returns": [{"id": "done", "executeStatus": "SUCCESS", "outputs": {"hits": path("steps.call.outputs.hits"),
                                                                     "runRef": path("steps.call.outputs.runRef")}},
                    {"id": "rejected", "executeStatus": "FAIL", "outputs": {
                        "hits": path("steps.call.outputs.hits"),
                        "errorCode": path("steps.call.outputs.errorCode")}},
                    {"id": "unknown", "executeStatus": "ERROR"}],
        "edges": [{"from": "call", "to": "done", "condition": "steps.call.executeStatus == 'SUCCESS'", "priority": 1},
                  {"from": "call", "to": "rejected", "condition": "steps.call.executeStatus == 'FAIL'", "priority": 2},
                  {"from": "call", "to": "unknown", "default": True}]}
    return {"standardApiCode": STD, "flowDefinition": flow, "reason": REASON}


# ---------- 登记 ----------

def find_channel(client):
    status, body = client.call("GET", f"{CHANNEL_ADMIN}?channelType=SERVICE&keyword={CHANNEL}&page=1&size=50")
    must("listChannels", status, body, (200,))
    return next((i for i in body.get("items") or [] if i.get("channelCode") == CHANNEL), None)


def ensure_channel(client, mock_url):
    existing = find_channel(client)
    if existing is None:
        status, body = client.call("POST", CHANNEL_ADMIN, {
            "channelType": "SERVICE", "channelCode": CHANNEL, "channelName": "合成筛查服务",
            "description": "全球合规入驻切片 1 合成筛查服务（仅 local / mor-test，不连真实名单）"})
        created = must("channel.create", status, body, (201,))
        record("channel.create", created.get("channelType") == "SERVICE", httpStatus=status, status=created.get("status"))
        channel_id = created["channelId"]
    else:
        channel_id = existing["channelId"]
        record("channel.create", True, existed=True, status=existing.get("status"))
    status, detail = client.call("GET", f"{CHANNEL_ADMIN}/{channel_id}")
    must("channel.detail", status, detail, (200,))
    wanted = [{"endpointCode": ENDPOINT, "baseUrl": mock_url.rstrip("/"), "sandbox": False}]
    current = [{k: e.get(k) for k in ("endpointCode", "baseUrl", "sandbox")} for e in detail.get("endpoints") or []]
    if current != wanted:
        status, body = client.call("PUT", f"{CHANNEL_ADMIN}/{channel_id}/endpoints",
                                   {"expectedVersion": detail["version"], "endpoints": wanted})
        detail = must("channel.endpoints", status, body, (200,))
        record("channel.endpoints", True, httpStatus=status, before=current, after=wanted, version=detail.get("version"))
    else:
        record("channel.endpoints", True, unchanged=True, endpoints=current)
    if detail.get("status") != "ACTIVE":
        status, body = client.call("POST", f"{CHANNEL_ADMIN}/{channel_id}/status", {"targetStatus": "ACTIVE", "reason": REASON})
        body = must("channel.activate", status, body, (200,))
        record("channel.activate", body.get("status") == "ACTIVE", httpStatus=status, status=body.get("status"))
    else:
        record("channel.activate", True, alreadyActive=True)
    return channel_id


def field_set(fields):
    return sorted((f.get("fieldLocation"), f.get("fieldPath"), f.get("dataType"), bool(f.get("required")))
                  for f in fields or [])


def drift(existing, body):
    """已有配置与本脚本期望值的差异项（字段集合、定义 / 流程；名称与说明不比较）。"""
    out = []
    for key in ("requestFields", "responseFields"):
        if key in body and field_set(existing.get(key)) != field_set(body[key]):
            out.append(key)
    for key in ("definition", "flowDefinition"):
        if key in body and json.dumps(existing.get(key), sort_keys=True) != json.dumps(body[key], sort_keys=True):
            out.append(key)
    return out


def ensure(client, key, get_path, post_path, body, update_keys):
    """不存在则 POST；存在且有差异则按版本 PUT（update_keys 为更新请求体所含键）；否则沿用。"""
    status, existing = client.call("GET", get_path)
    if status != 200:
        status, response = client.call("POST", post_path, body)
        must(key, status, response, (200, 201))
        record(key, True, httpStatus=status, created=True, version=response.get("version") if isinstance(response, dict) else None)
        return response
    changed = drift(existing, body)
    if not changed:
        record(key, True, existed=True, version=existing.get("version"))
        return existing
    update = {k: body[k] for k in update_keys if k in body}
    update["expectedVersion"] = existing["version"]
    status, response = client.call("PUT", get_path, update)
    must(key, status, response, (200,))
    record(key, True, httpStatus=status, updated=changed, versionBefore=existing["version"],
           version=response.get("version") if isinstance(response, dict) else None)
    return response


def ensure_grant(client, channel_id):
    status, body = client.call("GET", f"{GRANTS}?channelId={channel_id}&callerService={CALLER}&standardApiCode={STD}&page=1&size=50")
    must("grant.list", status, body, (200,))
    grant = next(iter(body.get("items") or []), None)
    if grant is None:
        status, body = client.call("POST", GRANTS, {"channelId": str(channel_id), "callerService": CALLER, "standardApiCode": STD})
        grant = must("grant.create", status, body, (201,))
        record("grant.create", grant.get("grantStatus") == "ACTIVE", httpStatus=status, callGrantId=grant.get("callGrantId"))
    elif grant.get("grantStatus") != "ACTIVE":
        status, body = client.call("POST", f"{GRANTS}/{grant['callGrantId']}/status",
                                   {"targetStatus": "ACTIVE", "expectedVersion": grant["version"], "reason": REASON})
        grant = must("grant.enable", status, body, (200,))
        record("grant.create", grant.get("grantStatus") == "ACTIVE", reEnabled=True, callGrantId=grant.get("callGrantId"))
    else:
        record("grant.create", True, existed=True, callGrantId=grant.get("callGrantId"))
    return grant


# ---------- 冒烟 ----------

def execute(client, caller, full_name):
    payload = {"channelCode": CHANNEL, "standardApiCode": STD, "input": {
        "subjectType": "PERSON", "fullName": full_name, "birthYear": "1980", "countries": ["AE"],
        "screeningTypes": ["PEP"], "clientRef": "slice1-smoke-" + datetime.now(timezone.utc).strftime("%H%M%S%f")}}
    return client.call("POST", SERVICE_PATH, payload, headers={"X-Caller-Service": caller})


def smoke(client):
    cases = [("smoke.noHit", "Synthetic Person", "SUCCESS", 0, None),
             ("smoke.pep", "Synthetic TESTHIT Person", "SUCCESS", 1, "PEP"),
             ("smoke.sanctions", "Synthetic TESTSANCTION Person", "SUCCESS", 1, "SANCTIONS"),
             ("smoke.fail", "Synthetic TESTFAIL Person", "FAIL", None, None),
             ("smoke.error", "Synthetic TESTERROR Person", "ERROR", None, None)]
    for key, name, want_status, want_hits, want_list in cases:
        status, body = execute(client, CALLER, name)
        hits = (body.get("outputs") or {}).get("hits")
        ok = status == 200 and body.get("executeStatus") == want_status
        if want_hits is not None:
            ok = ok and isinstance(hits, list) and len(hits) == want_hits
        if want_list:
            ok = ok and hits[0].get("listTypes") == [want_list] and {
                "externalRef", "entityType", "listTypes", "score", "matchTypes"} <= set(hits[0])
        if want_status == "SUCCESS":
            ok = ok and bool((body.get("outputs") or {}).get("runRef"))
        record(key, ok, httpStatus=status, executeStatus=body.get("executeStatus"), outputs=body.get("outputs"),
               problem=None if status == 200 else problem(body))
    status, body = execute(client, OTHER_CALLER, "Synthetic Person")
    record("smoke.otherCallerNotGranted", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED",
           httpStatus=status, problem=problem(body))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--mock-url", required=True)
    parser.add_argument("--actor-id", required=True)
    parser.add_argument("--smoke", action="store_true")
    parser.add_argument("--out")
    args = parser.parse_args()
    if not args.actor_id.isdigit():
        raise SystemExit("--actor-id 必须是十进制 Operations 账号 ID")
    client = Client(args.base_url, args.actor_id)
    started = datetime.now(timezone.utc).isoformat()
    channel_id = ensure_channel(client, args.mock_url)
    ensure(client, "standardApi", f"{EXTERNAL_ADMIN}/standard-apis/{STD}", f"{EXTERNAL_ADMIN}/standard-apis",
           standard_api_body(), ("standardApiName", "description", "requestFields", "responseFields", "reason"))
    ensure(client, "atomicApi", f"{EXTERNAL_ADMIN}/integrations/{CHANNEL}/apis/{API}",
           f"{EXTERNAL_ADMIN}/integrations/{CHANNEL}/apis", api_body(),
           ("apiName", "description", "requestFields", "responseFields", "definition", "reason"))
    ensure(client, "implementation", f"{EXTERNAL_ADMIN}/integrations/{CHANNEL}/implementations/{STD}",
           f"{EXTERNAL_ADMIN}/integrations/{CHANNEL}/implementations", flow_body(), ("flowDefinition", "reason"))
    ensure_grant(client, channel_id)
    if args.smoke:
        smoke(client)
    RESULTS["_meta"] = {"baseUrl": args.base_url, "mockUrl": args.mock_url, "channelId": str(channel_id),
                        "startedAt": started, "finishedAt": datetime.now(timezone.utc).isoformat()}
    if args.out:
        with open(args.out, "w") as handle:
            handle.write(json.dumps({"results": RESULTS}, ensure_ascii=False, indent=1) + "\n")
        print("写入 " + args.out)
    failed = [k for k, v in RESULTS.items() if isinstance(v, dict) and v.get("ok") is False]
    if failed:
        print("失败项：" + ", ".join(failed), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
