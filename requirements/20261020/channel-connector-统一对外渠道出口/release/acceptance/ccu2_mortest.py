"""统一对外渠道出口第二段 · mor-test 验收脚本（phase2-release.md 第 4、5 节；阶段 7 由主会话按步骤执行）。

入口：运营 http://localhost:28080（Nginx → Gateway → ops-bff → Owner）；私网调用经一次性探针容器（docker run --rm，接入
mor-test-backend，镜像固定为第二段回滚目标 platform 镜像 985d00ea…，不用 :development）。HTTP / 数据库 / 写盘扫描复用第一段
ccu_mortest.py（其内再复用 20261017 e2e_http.py，E2E_TARGET=test），第一段文件不改。数据库只做 SELECT（服务自有账号）。
输出写本目录 results/phase2-*.json；写盘前扫描 .env.test 密钥类值与管理员口令，命中即中止。不打印 Token 或口令。

版本判定：控制台 GET /external-api-admin/v1/integrations 为 200 → 第一段版本（before）；为 404 且 GET
/channel-admin/v1/channel-call-grants 为 200 → 第二段版本（after）；其他组合中止。

子命令（<tag> 取 before / after 等）：
  snapshot <tag>    只读：外部 API 管理与渠道管理全部 GET（按版本选路径）+ t_channel_config_change 行（SELECT）。
                    第一段版本：接入方编码取自接入方列表，渠道取自渠道列表；第二段版本：接入方列表 / 详情记录状态码（应 404），
                    编码取自渠道列表，另记类型过滤列表与调用授权列表（v2Only）。parameters / credentials / trace / resolvedInput
                    只留 SHA-256 前 16 位；时间字段去掉，ID 保留。约 330 次调用，受 Gateway 每路由 30 次/分钟限制，约 10 分钟。
  compare <a> <b>   比较两份同类结果（snapshot / chain / rerun）；参数可为文件名、路径或 tag（按 snapshot 解析）。
                    snapshot 允许差异：ID、时间、请求 ID；接入方名改为渠道名；渠道新增字段 channelType / description / endpoints /
                    version（列表另有 endpointCount / activeGrantCount）；配置变更目标类型 INTEGRATION_* → CHANNEL_*（含迁移改写的
                    INTEGRATION → CHANNEL 历史行）；接入方列表与详情在新版本 404。其余差异全部列出（带提示，不自动放行）。
  chain <tag>       C1 只读：DEMO_NI 冻结坐标经私网 POST /channel-connector/v1/operations 发 REFUND（无实现）与 QUERY（标准 API
                    不存在），记录 outcome / failureCode / Vault 事件。不读第一段的 payermax-state.json，不发 C2 / C3。
  rerun <tag>       B 写：第一段 7 个非变更型沙箱用例各运行一次，记录结论、每步结果与期间 Vault 访问事件（按调用方汇总）。
  nav <tag>         只读：运营导航页面编码与渠道 / 调用授权资源。
  service           只在第二段版本运行（写）：渠道列表三个分段计数（并发、不重试 429）；新建服务渠道 S7P2_SVC（出站地址
                    https://s7p2-acceptance.example.invalid，保留域名，不产生外部流量）；私网出站地址 422；测试标准 API
                    S7P2_PING + 原子 API + 实现；counterparty-service 经私网调用通用入口：未授权 403 → 授权后只记录结果 → 停用后 403；
                    DEMO_NI 走通用入口被拒；导航；接入方 5 个增删改经控制台 404（写探针带不可能的版本 / 非法体，不会写入）。
                    可重复执行：已存在的对象沿用。状态写 results/phase2-service-state.json。建议与 snapshot 间隔 ≥ 60 秒（计数不重试 429）。
  cleanup           第二段版本（写）：S7P2_SVC 的授权全部 DISABLED，渠道置 CLOSED（ACTIVE 不能直接关闭时先 SUSPENDED）；列出残留。
  smoke             只读冒烟：第二段导航断言、外部 API / 渠道 / 调用授权、数据查询（data-sources / templates）、文件存储
                    （instances / routes）、币种、对账、商务；Platform 私网 /external-api-admin 404。失败时退出码 1。
用法：python3 -I ccu2_mortest.py <子命令> [参数]
"""
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True  # 不在 acceptance/ 与 20261017 目录生成 __pycache__
os.environ.setdefault("E2E_TARGET", "test")
# 探针镜像固定为第二段回滚目标 platform 镜像（JRE 25；数据查询 dataquery-20261022b）。须在导入 ccu_mortest / e2e_http 前设置。
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:985d00ea2868adcfdd16ba7e88e59f53da9695138eae1eaac68524493276356a")
sys.path.insert(0, str(HERE))
import ccu_mortest as m  # noqa: E402

h = m.h
RESULTS = m.RESULTS
EXTERNAL_ADMIN = m.EXTERNAL_ADMIN
CHANNEL_ADMIN = m.CHANNEL_ADMIN
SERVICE_PATH = "/channel-connector/v1/service-executions"
REASON = "统一对外渠道出口第二段 mor-test 阶段 7 验收"
SERVICE_CODE = "S7P2_SVC"
SERVICE_STD = "S7P2_PING"
SERVICE_API = "S7P2_PING_CALL"
SERVICE_ENDPOINT = {"endpointCode": "PING", "baseUrl": "https://s7p2-acceptance.example.invalid", "sandbox": False}
PRIVATE_ENDPOINT = {"endpointCode": "PRIV", "baseUrl": "https://10.1.2.3", "sandbox": False}
CALLER = "counterparty-service"
SERVICE_STATE = RESULTS / "phase2-service-state.json"
TARGET_TYPE_RENAMES = {"INTEGRATION_API": "CHANNEL_API", "INTEGRATION_SCRIPT": "CHANNEL_SCRIPT",
                       "INTEGRATION_IMPLEMENTATION": "CHANNEL_IMPLEMENTATION", "INTEGRATION": "CHANNEL"}
NEW_CHANNEL_FIELDS = {"channelType", "description", "endpoints", "version"}
NEW_CHANNEL_LIST_FIELDS = {"endpointCount", "activeGrantCount"}
LIST_KEYS = ("changeId", "callGrantId", "runCode", "caseCode", "accountCode", "apiCode", "scriptCode", "standardApiCode",
             "integrationCode", "channelCode", "channelAccountId", "midId", "midCapabilityId", "planCode", "costVer",
             "capabilityCode", "endpointCode")


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def problem(body):
    """Problem / 错误体的稳定字段（不含 requestId、时间）。"""
    if not isinstance(body, dict):
        return body
    out = {k: body.get(k) for k in ("code", "title", "status", "detail") if body.get(k) is not None}
    errors = body.get("errors") or body.get("fieldErrors")
    if errors:
        out["errors"] = errors
    return out


def ops_once(token, path):
    """控制台 GET 一次，不重试 429（用于"没有 429"判定）。"""
    request = urllib.request.Request(h.OPERATIONS + "/api/operations" + path, method="GET", headers={
        "Origin": h.OPERATIONS, "Accept": "application/json", "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(request, context=h.TLS, timeout=60) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else {})
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        try:
            return failure.code, json.loads(text)
        except ValueError:
            return failure.code, {"raw": text[:300]}


def detect_version(token):
    status, _ = m.ops(token, "GET", EXTERNAL_ADMIN + "/integrations?page=1&size=10")
    if status == 200:
        return 1
    grants, _ = m.ops(token, "GET", CHANNEL_ADMIN + "/channel-call-grants?page=1&size=10")
    if status == 404 and grants == 200:
        return 2
    raise SystemExit(f"无法判定版本：接入方列表 HTTP {status}，调用授权列表 HTTP {grants}")


def table_exists(service, table):
    return bool(h.sql(service, "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() "
                               f"AND table_name='{table}'"))


def vault_summary(since):
    """since 之后 Vault 访问事件按调用方 / 动作 / 数据类型 / 用途 / 结果汇总（只读；同期其他会话的事件也会计入）。"""
    rows = h.sql("vault", "SELECT caller_service, action, IFNULL(data_type,''), IFNULL(purpose,''), outcome, COUNT(*) "
                          f"FROM t_vault_access_event WHERE create_time >= '{since}' "
                          "GROUP BY caller_service, action, data_type, purpose, outcome ORDER BY 1, 2, 3")
    return [dict(zip(("callerService", "action", "dataType", "purpose", "outcome", "count"), r)) for r in rows]


# ---------- snapshot ----------

def snapshot(token, version):
    snap, calls, v2_only, removed = {}, [], {}, {}

    def get(name, path, store=snap):
        status, body = m.ops(token, "GET", path)
        calls.append([name, status])
        store[name] = {"status": status, "body": m.normalize(body)}
        return status, body

    def get_paged(name, path, store=snap):
        status, body, items = m.paged(token, path)
        calls.append([name, status])
        store[name] = {"status": status, "total": body.get("total") if status == 200 else None,
                       "items": m.normalize(items) if status == 200 else m.normalize(body)}
        return items

    def changes(target, target_id, label):
        get(f"config-changes/{target}/{label}",
            f"{CHANNEL_ADMIN}/config-changes?targetType={target}&targetId={target_id}&page=1&size=50")

    # 外部 API 管理：编辑能力与标准 API
    get("editor-capabilities", EXTERNAL_ADMIN + "/editor-capabilities")
    standards = get_paged("standard-apis", EXTERNAL_ADMIN + "/standard-apis")
    for item in standards:
        get(f"standard-apis/{item['standardApiCode']}", f"{EXTERNAL_ADMIN}/standard-apis/{item['standardApiCode']}")

    # 渠道管理
    get("mid-capabilities/definitions", CHANNEL_ADMIN + "/mid-capabilities/definitions")
    channels = get_paged("channels", CHANNEL_ADMIN + "/channels")
    if version == 2:
        for kind in ("PAYMENT", "SERVICE"):
            get_paged(f"channels?channelType={kind}", f"{CHANNEL_ADMIN}/channels?channelType={kind}", v2_only)
        get_paged("channel-call-grants", CHANNEL_ADMIN + "/channel-call-grants", v2_only)
    for channel in channels:
        cid, code = channel["channelId"], channel["channelCode"]
        get(f"channels/{code}", f"{CHANNEL_ADMIN}/channels/{cid}")
        changes("CHANNEL", cid, code)
        for account in get_paged(f"channel-accounts?channel={code}", f"{CHANNEL_ADMIN}/channel-accounts?channelId={cid}"):
            aid = account["channelAccountId"]
            get(f"channel-accounts/{aid}", f"{CHANNEL_ADMIN}/channel-accounts/{aid}")
            changes("CHANNEL_ACCOUNT", aid, aid)
        for mid in get_paged(f"mids?channel={code}", f"{CHANNEL_ADMIN}/mids?channelId={cid}"):
            _, detail = get(f"mids/{mid['midId']}", f"{CHANNEL_ADMIN}/mids/{mid['midId']}")
            changes("CHANNEL_MID", mid["midId"], mid["midId"])
            for capability in (detail.get("capabilities") or []) if isinstance(detail, dict) else []:
                changes("MID_CAPABILITY", capability["midCapabilityId"], capability["midCapabilityId"])
    for plan in get_paged("channel-cost-plans", CHANNEL_ADMIN + "/channel-cost-plans"):
        code = plan["planCode"]
        _, detail = get(f"channel-cost-plans/{code}", f"{CHANNEL_ADMIN}/channel-cost-plans/{code}")
        for ver in (detail.get("versions") or []) if isinstance(detail, dict) else []:
            if not ver.get("costVer"):
                continue
            get(f"channel-cost-plans/{code}/versions/{ver['costVer']}",
                f"{CHANNEL_ADMIN}/channel-cost-plans/{code}/versions/{ver['costVer']}")
            if ver.get("costVerId"):
                changes("CHANNEL_COST_VERSION", ver["costVerId"], f"{code}/{ver['costVer']}")

    # 外部 API 管理：按接入方（第一段）/ 渠道（第二段）编码读取
    if version == 1:
        codes = [i["integrationCode"] for i in get_paged("integrations", EXTERNAL_ADMIN + "/integrations")]
        for code in codes:
            get(f"integrations/{code}", f"{EXTERNAL_ADMIN}/integrations/{code}")
    else:
        for name, path in (("integrations", EXTERNAL_ADMIN + "/integrations?page=1&size=50"),
                           ("integrations/DEMO_NI", EXTERNAL_ADMIN + "/integrations/DEMO_NI")):
            status, body = m.ops(token, "GET", path)
            calls.append([name + " (removed)", status])
            removed[name] = {"status": status, "code": body.get("code") if isinstance(body, dict) else None}
        codes = sorted(c["channelCode"] for c in channels)
    for code in codes:
        base = f"{EXTERNAL_ADMIN}/integrations/{code}"
        for item in get_paged(f"integrations/{code}/apis", base + "/apis"):
            get(f"integrations/{code}/apis/{item['apiCode']}", f"{base}/apis/{item['apiCode']}")
        for item in get_paged(f"integrations/{code}/scripts", base + "/scripts"):
            get(f"integrations/{code}/scripts/{item['scriptCode']}", f"{base}/scripts/{item['scriptCode']}")
        _, impls = get(f"integrations/{code}/implementations", base + "/implementations")
        for item in m.listed(impls, "items", "implementations"):
            get(f"integrations/{code}/implementations/{item['standardApiCode']}", f"{base}/implementations/{item['standardApiCode']}")
        for std in standards:
            get(f"integrations/{code}/flow-editor-context?{std['standardApiCode']}",
                f"{base}/flow-editor-context?standardApiCode={std['standardApiCode']}")
        _, accounts = get(f"integrations/{code}/verify-accounts", base + "/verify-accounts")
        for item in m.listed(accounts, "items", "accounts"):
            get(f"integrations/{code}/verify-accounts/{item['accountCode']}", f"{base}/verify-accounts/{item['accountCode']}")
        for item in get_paged(f"integrations/{code}/verify-cases", base + "/verify-cases"):
            get(f"integrations/{code}/verify-cases/{item['caseCode']}", f"{base}/verify-cases/{item['caseCode']}")
        for item in get_paged(f"integrations/{code}/verify-runs", base + "/verify-runs"):
            get(f"integrations/{code}/verify-runs/{item['runCode']}", f"{base}/verify-runs/{item['runCode']}")

    rows = h.sql("channel-connector", "SELECT id, target_type, target_id, change_type FROM t_channel_config_change ORDER BY id")
    statuses = {}
    for _, status in calls:
        statuses[str(status)] = statuses.get(str(status), 0) + 1
    return {"version": version, "calls": len(calls), "statuses": statuses,
            "non200": [c for c in calls if c[1] != 200 and not c[0].endswith("(removed)")],
            "responses": snap, "v2Only": v2_only, "removedIntegrationCrud": removed,
            "configChangeRows": [dict(zip(("changeId", "targetType", "targetId", "changeType"), r)) for r in rows]}


# ---------- compare ----------

def keyed(items):
    """列表元素都是含同一唯一键的对象时，按该键转为映射（顺序变化不算差异）。"""
    if not items or not all(isinstance(i, dict) for i in items):
        return None
    for key in LIST_KEYS:
        values = [i.get(key) for i in items]
        if all(v is not None for v in values) and len({str(v) for v in values}) == len(values):
            return key, {str(v): i for v, i in zip(values, items)}
    return None


def diff(x, y, path=""):
    if type(x) != type(y):
        return [[path, "type", str(x)[:160], str(y)[:160]]]
    out = []
    if isinstance(x, dict):
        for k in sorted(set(x) | set(y)):
            if k not in x or k not in y:
                out.append([f"{path}/{k}", "missing", k in x, k in y])
            else:
                out += diff(x[k], y[k], f"{path}/{k}")
    elif isinstance(x, list):
        kx, ky = keyed(x), keyed(y)
        if (kx or not x) and (ky or not y) and (kx or ky) and (not kx or not ky or kx[0] == ky[0]):
            key = (kx or ky)[0]
            mx, my = (kx or (key, {}))[1], (ky or (key, {}))[1]
            for v in sorted(set(mx) | set(my)):
                if v not in mx or v not in my:
                    out.append([f"{path}[{key}={v}]", "missing", v in mx, v in my])
                else:
                    out += diff(mx[v], my[v], f"{path}[{key}={v}]")
            return out
        if len(x) != len(y):
            out.append([path, "len", len(x), len(y)])
        for i, (p, q) in enumerate(zip(x, y)):
            out += diff(p, q, f"{path}[{i}]")
    elif x != y:
        out.append([path, "value", str(x)[:160], str(y)[:160]])
    return out


def leaf(path):
    return re.sub(r"\[[^\]]*\]$", "", path.rsplit("/", 1)[-1])


def element(path):
    found = re.search(r"\[(\w+)=([^\]]*)\]$", path)
    return (found.group(1), found.group(2)) if found else (None, None)


def classify(key, item, ctx):
    """返回允许类别；None 表示需列出。"""
    path, kind = item[0], item[1]
    name = leaf(path)
    if kind == "value":
        if name in ("requestId", "traceId"):
            return "请求 ID"
        if name == "id" or name.endswith("Id") or name.endswith("Ids"):
            return "ID"
        if name.endswith("Time") or name.endswith("At") or name == "timestamp":
            return "时间"
        if name == "integrationName" and item[3] in ctx["channelNamesB"]:
            return "接入方名改为渠道名"
        if name == "targetType" and TARGET_TYPE_RENAMES.get(item[2]) == item[3]:
            return "配置变更目标类型 INTEGRATION_* → CHANNEL_*"
    if kind == "missing" and item[2] is False and item[3] is True and key.startswith("channels"):
        field, _ = element(path)
        if field is None and name in NEW_CHANNEL_FIELDS:
            return "渠道新增字段（channelType / description / endpoints / version）"
        if field is None and name in NEW_CHANNEL_LIST_FIELDS:
            return "渠道列表新增统计字段（endpointCount / activeGrantCount，P2 合同第 6 节）"
    if kind == "missing" and item[2] is False and item[3] is True and key.startswith("config-changes/"):
        field, value = element(path)
        if field == "changeId" and value in ctx["migratedChangeIds"]:
            return "配置变更目标类型 INTEGRATION_* → CHANNEL_*（迁移改写的历史行）"
    return None


def extra_hint(key, ctx):
    code = key.split("/")[1].split("?")[0] if key.startswith("integrations/") else None
    channel_code = re.sub(r"^channels/", "", key) if key.startswith("channels/") else None
    if "/verify-runs/" in key:
        return "新运行（rerun 等验收写入）"
    if channel_code and channel_code in ctx["integrationCodesA"]:
        return "接入方迁入为渠道（第 3 节 INTEGRATION_ONLY → DRAFT 支付渠道）"
    if code and code not in ctx["integrationCodesA"]:
        return "仅渠道（迁移前无接入方）的外部 API 读取"
    if key.startswith(("config-changes/", "channel-accounts", "mids")):
        return "新出现的渠道对象读取"
    return None


def crosswalk(a, b, ctx):
    """旧接入方详情 vs 新渠道详情：说明、出站地址、版本应一致；名称差异按"接入方名改为渠道名"放行。"""
    out = {"allowed": [], "listed": []}
    for code in sorted(ctx["integrationCodesA"]):
        before = (a.get(f"integrations/{code}") or {}).get("body") or {}
        after = (b.get(f"channels/{code}") or {}).get("body")
        if after is None:
            out["listed"].append([code, "新版本没有对应渠道详情"])
            continue
        endpoints = [{k: e.get(k) for k in ("endpointCode", "baseUrl", "sandbox")} for e in before.get("endpoints") or []]
        new_endpoints = [{k: e.get(k) for k in ("endpointCode", "baseUrl", "sandbox")} for e in after.get("endpoints") or []]
        for field, x, y in (("description", before.get("description"), after.get("description")),
                            ("endpoints", endpoints, new_endpoints), ("version", before.get("version"), after.get("version"))):
            if x != y:
                out["listed"].append([code, field, x, y])
        if before.get("integrationName") != after.get("channelName"):
            out["allowed"].append([code, "接入方名改为渠道名", before.get("integrationName"), after.get("channelName")])
    return out


def compare_snapshots(a_doc, b_doc):
    a, b = a_doc["snapshot"], b_doc["snapshot"]
    ra, rb = a["responses"], b["responses"]
    channels_b = (rb.get("channels") or {}).get("items") or []
    ctx = {"channelNamesB": {c.get("channelName") for c in channels_b},
           "integrationCodesA": {i["integrationCode"] for i in (ra.get("integrations") or {}).get("items") or []},
           "migratedChangeIds": {str(r["changeId"]) for r in a.get("configChangeRows") or []
                                 if str(r["targetType"]).startswith("INTEGRATION")}}
    allowed, listed, extra, missing = {}, [], [], []
    for key in sorted(set(ra) | set(rb)):
        if key not in rb:
            if key == "integrations" or (key.startswith("integrations/") and key.count("/") == 1):
                status = (b.get("removedIntegrationCrud") or {}).get(key if key == "integrations" else "integrations/DEMO_NI",
                                                                     {}).get("status")
                if status == 404:
                    allowed.setdefault("接入方列表与详情已下线（新版本 404）", []).append([key])
                    continue
            missing.append(key)
            continue
        if key not in ra:
            extra.append([key, rb[key].get("status"), extra_hint(key, ctx)])
            continue
        items = diff(ra[key], rb[key])
        kept = []
        for item in items:
            category = classify(key, item, ctx)
            if category:
                allowed.setdefault(category, []).append([key] + item)
            else:
                kept.append(item)
        # 配置变更列表 total 的变化恰好等于迁移改写行的个数时一并放行
        migrated = sum(1 for i in items if "迁移改写" in (classify(key, i, ctx) or ""))
        for item in list(kept):
            if key.startswith("config-changes/") and item[0] in ("/total", "/body/total") and migrated \
                    and str(item[2]).isdigit() and str(item[3]).isdigit() and int(item[3]) - int(item[2]) == migrated:
                allowed.setdefault("配置变更目标类型 INTEGRATION_* → CHANNEL_*（迁移改写的历史行）", []).append([key] + item)
                kept.remove(item)
        listed += [[key] + item for item in kept]
    # 配置变更行（数据库）：同一 changeId 的目标类型按改名表映射
    rows_a = {str(r["changeId"]): r for r in a.get("configChangeRows") or []}
    rows_b = {str(r["changeId"]): r for r in b.get("configChangeRows") or []}
    change_rows = {"renamed": 0, "unchanged": 0, "mismatch": [], "missingInB": sorted(set(rows_a) - set(rows_b)),
                   "extraInB": [rows_b[i] for i in sorted(set(rows_b) - set(rows_a))]}
    for change_id in sorted(set(rows_a) & set(rows_b)):
        x, y = rows_a[change_id], rows_b[change_id]
        expected = TARGET_TYPE_RENAMES.get(x["targetType"], x["targetType"])
        if y["targetType"] != expected or y["changeType"] != x["changeType"]:
            change_rows["mismatch"].append([x, y])
        elif y["targetType"] != x["targetType"]:
            change_rows["renamed"] += 1
        else:
            change_rows["unchanged"] += 1
    return {"versions": [a["version"], b["version"]], "calls": [a["calls"], b["calls"]],
            "statuses": [a["statuses"], b["statuses"]], "non200": [a["non200"], b["non200"]],
            "removedIntegrationCrud": b.get("removedIntegrationCrud"),
            "v2Only": {k: {"status": v.get("status"), "total": v.get("total")} for k, v in (b.get("v2Only") or {}).items()},
            "allowed": allowed, "listed": listed, "missingInB": missing, "extraInB": extra,
            "integrationVsChannel": crosswalk(ra, rb, ctx), "configChangeRows": change_rows}


def compare_chain(a, b):
    out = {}
    for key in sorted(set(a.get("demoNi") or {}) | set(b.get("demoNi") or {})):
        x, y = (a.get("demoNi") or {}).get(key), (b.get("demoNi") or {}).get(key)
        out[key] = {"equal": x == y, "diff": diff(x, y) if x is not None and y is not None else "missing"}
    return out


def compare_rerun(a, b):
    def view(run):
        return {k: run.get(k) for k in ("startStatus", "code", "runStatus", "failureSummary", "steps")}
    runs_a = {f"{r['integration']}/{r['case']}": view(r) for r in a["runs"]}
    runs_b = {f"{r['integration']}/{r['case']}": view(r) for r in b["runs"]}
    out = {k: {"equal": runs_a.get(k) == runs_b.get(k), "diff": diff(runs_a.get(k), runs_b.get(k))}
           for k in sorted(set(runs_a) | set(runs_b))}
    out["_vaultCallers"] = [sorted({e["callerService"] for e in a.get("vaultEvents") or []}),
                            sorted({e["callerService"] for e in b.get("vaultEvents") or []})]
    return out


def resolve(arg):
    for candidate in (Path(arg), RESULTS / arg, RESULTS / f"phase2-snapshot-{arg}.json"):
        if candidate.is_file():
            return candidate
    raise SystemExit(f"找不到结果文件：{arg}")


def compare(a_arg, b_arg):
    a_path, b_path = resolve(a_arg), resolve(b_arg)
    a, b = json.loads(a_path.read_text()), json.loads(b_path.read_text())
    if a.get("kind") != b.get("kind"):
        raise SystemExit(f"结果类型不同：{a.get('kind')} / {b.get('kind')}")
    handlers = {"snapshot": compare_snapshots, "chain": compare_chain, "rerun": compare_rerun}
    if a.get("kind") not in handlers:
        raise SystemExit(f"不支持比较的结果类型：{a.get('kind')}")
    result = {"kind": a["kind"], "a": a_path.name, "b": b_path.name, "comparedAt": now_iso(), **handlers[a["kind"]](a, b)}
    m.write(f"phase2-compare-{a_path.stem.removeprefix('phase2-')}-vs-{b_path.stem.removeprefix('phase2-')}.json", result)
    if a["kind"] == "snapshot":
        summary = {"versions": result["versions"], "calls": result["calls"], "non200": [len(x) for x in result["non200"]],
                   "allowed": {k: len(v) for k, v in result["allowed"].items()}, "listed": len(result["listed"]),
                   "missingInB": len(result["missingInB"]), "extraInB": len(result["extraInB"]),
                   "integrationVsChannelListed": len(result["integrationVsChannel"]["listed"]),
                   "configChangeRows": {k: (v if isinstance(v, int) else len(v)) for k, v in result["configChangeRows"].items()}}
    else:
        summary = {k: (v.get("equal") if isinstance(v, dict) else v) for k, v in result.items()
                   if k not in ("kind", "a", "b", "comparedAt")}
    print(json.dumps(summary, ensure_ascii=False, indent=1))


# ---------- chain（C1） ----------

def demo_ni_target():
    table = "t_channel" if table_exists("channel-connector", "t_channel") else "t_payment_channel"
    rows = h.sql("channel-connector", f"SELECT c.channel_id, a.channel_account_id, a.entity_no, m.mid_id FROM {table} c "
                                      "JOIN t_channel_account a ON a.channel_id=c.channel_id JOIN t_channel_mid m "
                                      "ON m.channel_account_id=a.channel_account_id WHERE c.channel_code='DEMO_NI' "
                                      "AND m.mid_status='ACTIVE' ORDER BY m.mid_id LIMIT 1")
    if not rows:
        return None, table
    channel_id, account_id, entity_no, mid_id = rows[0]
    return {"channelId": channel_id, "channelAccountId": account_id, "entityNo": entity_no, "midId": mid_id,
            "mccCode": "5999"}, table


def chain(tag):
    result = {"kind": "chain", "label": tag, "startedAt": now_iso()}
    target, table = demo_ni_target()
    result["channelTable"] = table
    result["demoNi"] = target and {
        "refundImplementationMissing": m.execute(target, "REFUND", "ORIGINAL_TRANSACTION", "5.00", m.refund_payload()),
        "queryStandardApiMissing": m.execute(target, "QUERY", "ORIGINAL_TRANSACTION", None,
                                             {"payment": {"attemptId": str(random.randrange(10**15, 10**16))}})}
    result["finishedAt"] = now_iso()
    m.write(f"phase2-chain-{tag}.json", result)
    print(json.dumps({k: (v["outcome"], v["failureCode"], len(v["vaultEvents"])) for k, v in (result["demoNi"] or {}).items()},
                     ensure_ascii=False))


# ---------- rerun（B） ----------

def run_case(token, code, case, run_table):
    base = f"{EXTERNAL_ADMIN}/integrations/{code}"
    status, started = m.ops(token, "POST", f"{base}/verify-cases/{case}/runs", {"reason": REASON})
    out = {"integration": code, "case": case, "startStatus": status, "runCode": started.get("runCode"), "code": started.get("code")}
    detail = {}
    for _ in range(60):
        if status not in (200, 201):
            break
        _, detail = m.ops(token, "GET", f"{base}/verify-runs/{started['runCode']}")
        if detail.get("runStatus") != "RUNNING":
            break
        time.sleep(2)
    out["runStatus"] = detail.get("runStatus")
    out["failureSummary"] = detail.get("failureSummary")
    out["steps"] = [{"stepNo": s.get("stepNo"), "stepStatus": s.get("stepStatus"),
                     "executeStatus": (s.get("result") or {}).get("executeStatus"), "returnId": (s.get("result") or {}).get("returnId"),
                     "assertions": [[x.get("target"), x.get("passed")] for x in s.get("assertions") or []]}
                    for s in detail.get("steps") or []]
    run_id = str(started.get("runCode") or "")
    if run_id.isdigit():
        out["rowIn"] = {run_table: bool(h.sql("channel-connector", f"SELECT 1 FROM {run_table} WHERE verify_run_id={run_id}"))}
    return out


def rerun(tag):
    token = m.login()
    run_table = "t_channel_verify_run" if table_exists("channel-connector", "t_channel_verify_run") else "t_integration_verify_run"
    since = m.now_sql()
    runs = [run_case(token, code, case, run_table) for code, case in m.RERUN_CASES]
    time.sleep(1.5)
    result = {"kind": "rerun", "label": tag, "version": detect_version(token), "takenAt": now_iso(), "runs": runs,
              "vaultEvents": vault_summary(since)}
    m.write(f"phase2-rerun-{tag}.json", result)
    print(json.dumps([[r["integration"], r["case"], r["runStatus"]] for r in runs], ensure_ascii=False))


# ---------- nav ----------

def navigation(token):
    status, nav = m.ops(token, "GET", "/authorization/v1/navigation")
    resources = {r.get("resourceCode"): r for r in (nav.get("resources") or [])} if isinstance(nav, dict) else {}
    pages = set(resources) or codes(nav)
    channel_page = resources.get("OPERATIONS_CHANNEL_CHANNELS", {})
    button = resources.get("OPERATIONS_CHANNEL_CALL_GRANT_MANAGE", {})
    return {"httpStatus": status, "codes": sorted(pages),
            "hasIntegrationsPage": "OPERATIONS_EXTERNAL_INTEGRATIONS" in pages,
            "hasStandardApisPage": "OPERATIONS_EXTERNAL_STANDARD_APIS" in pages,
            "hasCallGrantButton": "OPERATIONS_CHANNEL_CALL_GRANT_MANAGE" in pages,
            "channelPage": {k: channel_page.get(k) for k in ("resourceId", "displayName", "frontendRoute")},
            "callGrantButton": {k: button.get(k) for k in ("resourceId", "displayName", "resourceType", "parentResourceId")}}


def codes(node):
    """递归收集导航树中的页面编码（导航体没有 resources 列表时使用）。"""
    found = set()
    if isinstance(node, dict):
        for value in node.values():
            if isinstance(value, str) and value.startswith("OPERATIONS_"):
                found.add(value)
            found |= codes(value)
    elif isinstance(node, list):
        for item in node:
            found |= codes(item)
    return found


def nav(tag):
    token = m.login()
    result = {"kind": "nav", "label": tag, "takenAt": now_iso(), "version": detect_version(token), **navigation(token)}
    m.write(f"phase2-nav-{tag}.json", result)
    print(json.dumps({k: v for k, v in result.items() if k != "codes"}, ensure_ascii=False))


# ---------- service ----------

class Recorder:
    def __init__(self):
        self.results = {}

    def __call__(self, key, ok, **facts):
        self.results[key] = {"ok": bool(ok), **facts}
        print(("PASS " if ok else "FAIL ") + key + " " + json.dumps(facts, ensure_ascii=False)[:300], flush=True)
        return ok


def service_call(caller, channel_code, standard_api, payload):
    headers = {"X-Caller-Service": caller} if caller else {}
    status, text = h.private_call("channel-connector", "POST", SERVICE_PATH, headers=headers, timeout=60,
                                  body={"channelCode": channel_code, "standardApiCode": standard_api, "input": payload})
    try:
        body = json.loads(text) if text else {}
    except ValueError:
        body = {"raw": text[:200]}
    if status == 200:
        return status, body, {"httpStatus": status, "executeStatus": body.get("executeStatus"), "outputs": body.get("outputs")}
    return status, body, {"httpStatus": status, "problem": problem(body)}


def ping(tag):
    return {"ping": f"s7p2-{tag}-{random.randrange(16**6):06x}"}


def field(path, data_type="STRING", required=True):
    return {"fieldLocation": "BODY", "fieldPath": path, "fieldName": path, "dataType": data_type, "required": required,
            "description": None}


def standard_api_body():
    return {"standardApiCode": SERVICE_STD, "standardApiName": "第二段 mor-test 验收探测",
            "description": "统一对外渠道出口第二段 mor-test 验收专用（服务渠道 S7P2_SVC），不得被业务调用",
            "requestFields": [field("ping")], "responseFields": [field("echo")], "reason": REASON}


def atomic_api_body():
    """无凭据：不配置技术账号，不写 Vault；出站地址为保留域名，执行结果只记录。"""
    definition = {
        "params": [], "fixedValues": {},
        "transport": {"protocol": "HTTP", "method": "POST", "endpointCode": SERVICE_ENDPOINT["endpointCode"], "pathTemplate": "/ping",
                      "connectTimeoutMs": 2000, "responseTimeoutMs": 5000},
        "requestEncoding": "JSON", "responseDecoding": "JSON", "decimalEncoding": "NUMBER", "scripts": [],
        "statusRules": [{"condition": "http.responseStatus == 200 && http.responseBody.code == 0", "executeStatus": "SUCCESS"}],
        "outputs": {"SUCCESS": [{"name": "echo", "dataType": "STRING", "required": True,
                                 "value": {"kind": "PATH", "path": "http.responseBody.echo"}}], "FAIL": []}}
    return {"apiCode": SERVICE_API, "apiName": "验收探测", "description": None, "requestFields": [field("ping")],
            "responseFields": [field("echo")], "definition": definition, "reason": REASON}


def implementation_body():
    def path(p):
        return {"kind": "PATH", "path": p}
    flow = {
        "timeoutMs": 10000, "initializers": [], "entry": "call",
        "nodes": [{"id": "call", "integrationApi": SERVICE_API, "request": {"BODY:ping": path("input.ping")}}],
        "returns": [{"id": "done", "executeStatus": "SUCCESS", "outputs": {"echo": path("steps.call.outputs.echo")}},
                    {"id": "unknown", "executeStatus": "ERROR"}],
        "edges": [{"from": "call", "to": "done", "condition": "steps.call.executeStatus == 'SUCCESS'", "priority": 1},
                  {"from": "call", "to": "unknown", "default": True}]}
    return {"standardApiCode": SERVICE_STD, "flowDefinition": flow, "reason": REASON}


def find_service_channel(token):
    _, _, items = m.paged(token, CHANNEL_ADMIN + "/channels?channelType=SERVICE")
    return next((i for i in items if i.get("channelCode") == SERVICE_CODE), None)


def channel_counts(token, record):
    """三个分段依次各请求一次，不重试 429（与页面一致：计数串行，见 channelWorkspace.typeCountsSequentially）。"""
    paths = {"ALL": CHANNEL_ADMIN + "/channels?page=1&size=50",
             "PAYMENT": CHANNEL_ADMIN + "/channels?channelType=PAYMENT&page=1&size=50",
             "SERVICE": CHANNEL_ADMIN + "/channels?channelType=SERVICE&page=1&size=50"}
    answers = {k: ops_once(token, p) for k, p in paths.items()}
    lists = {k: {"httpStatus": s, "total": b.get("total") if isinstance(b, dict) else None,
                 "types": sorted({i.get("channelType") for i in (b.get("items") or [])}) if isinstance(b, dict) else None}
             for k, (s, b) in answers.items()}
    ok = all(v["httpStatus"] == 200 for v in lists.values()) and lists["PAYMENT"]["types"] in ([], ["PAYMENT"]) \
        and lists["SERVICE"]["types"] in ([], ["SERVICE"]) \
        and int(lists["ALL"]["total"] or 0) == int(lists["PAYMENT"]["total"] or 0) + int(lists["SERVICE"]["total"] or 0)
    return record("counts", ok, lists=lists)


def ensure_channel(token, record):
    existing = find_service_channel(token)
    if existing is None:
        status, body = m.ops(token, "POST", CHANNEL_ADMIN + "/channels", {
            "channelType": "SERVICE", "channelCode": SERVICE_CODE, "channelName": "第二段 mor-test 验收服务渠道",
            "description": "统一对外渠道出口第二段 mor-test 验收专用；出站地址为保留域名，不可达"})
        created = m.must("新建服务渠道", status, body, (201,))
        record("createServiceChannel", created.get("channelType") == "SERVICE" and created.get("status") == "DRAFT",
               httpStatus=status, channelType=created.get("channelType"), status=created.get("status"), version=created.get("version"))
        return created["channelId"]
    if existing.get("status") == "CLOSED":
        raise SystemExit(f"{SERVICE_CODE} 已为 CLOSED（上次 cleanup 后），不复用；如需重跑请改用新编码")
    record("createServiceChannel", True, reused=True, status=existing.get("status"))
    return existing["channelId"]


def configure_endpoints(token, cid, record):
    _, detail = m.ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    version = detail.get("version")
    status, body = m.ops(token, "PUT", f"{CHANNEL_ADMIN}/channels/{cid}/endpoints",
                         {"expectedVersion": version, "endpoints": [SERVICE_ENDPOINT, PRIVATE_ENDPOINT]})
    fields = json.dumps(body.get("errors") or body.get("fieldErrors") or [], ensure_ascii=False) if isinstance(body, dict) else ""
    _, after = m.ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    record("privateEndpointRejected", status == 422 and "endpoints[1].baseUrl" in fields and after.get("version") == version,
           httpStatus=status, problem=problem(body), versionBefore=version, versionAfter=after.get("version"))
    current = [{k: e.get(k) for k in ("endpointCode", "baseUrl", "sandbox")} for e in after.get("endpoints") or []]
    if current != [SERVICE_ENDPOINT]:
        status, body = m.ops(token, "PUT", f"{CHANNEL_ADMIN}/channels/{cid}/endpoints",
                             {"expectedVersion": after.get("version"), "endpoints": [SERVICE_ENDPOINT]})
        updated = m.must("写入出站地址", status, body, (200,))
        record("publicEndpointSaved", updated.get("version") == after.get("version") + 1, httpStatus=status,
               version=updated.get("version"), endpoints=updated.get("endpoints"))
    if after.get("status") != "ACTIVE":
        status, body = m.ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON})
        record("activate", status == 200 and body.get("status") == "ACTIVE", httpStatus=status, status=body.get("status"),
               problem=None if status == 200 else problem(body))


def ensure_protocol(token, record):
    """测试标准 API S7P2_PING + S7P2_SVC 上的原子 API 与实现；已存在则沿用。"""
    base = f"{EXTERNAL_ADMIN}/integrations/{SERVICE_CODE}"
    for key, get_path, post_path, body in (
            ("standardApi", f"{EXTERNAL_ADMIN}/standard-apis/{SERVICE_STD}", EXTERNAL_ADMIN + "/standard-apis", standard_api_body()),
            ("atomicApi", f"{base}/apis/{SERVICE_API}", f"{base}/apis", atomic_api_body()),
            ("implementation", f"{base}/implementations/{SERVICE_STD}", f"{base}/implementations", implementation_body())):
        status, _ = m.ops(token, "GET", get_path)
        if status == 200:
            record(key, True, existed=True)
            continue
        status, response = m.ops(token, "POST", post_path, body)
        record(key, status in (200, 201), httpStatus=status, problem=None if status in (200, 201) else problem(response))
        if status not in (200, 201):
            raise SystemExit(f"{key} 创建失败，停止（之前已建对象保留，可重跑沿用）")


def find_grant(token, cid):
    _, _, items = m.paged(token, f"{CHANNEL_ADMIN}/channel-call-grants?channelId={cid}")
    return next((g for g in items if g.get("callerService") == CALLER and g.get("standardApiCode") == SERVICE_STD), None)


def set_grant(token, grant, target):
    return m.ops(token, "POST", f"{CHANNEL_ADMIN}/channel-call-grants/{grant['callGrantId']}/status",
                 {"targetStatus": target, "expectedVersion": grant["version"], "reason": REASON})


def grants(token, cid, record):
    grant = find_grant(token, cid)
    if grant and grant.get("grantStatus") == "ACTIVE":  # 重跑：先停用，保证"未授权"场景成立
        status, body = set_grant(token, grant, "DISABLED")
        grant = m.must("停用既有授权", status, body, (200,))
    status, body, view = service_call(CALLER, SERVICE_CODE, SERVICE_STD, ping("nogrant"))
    record("notGranted", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED", **view,
           priorGrant=grant and grant.get("grantStatus"))
    if grant is None:
        status, body = m.ops(token, "POST", CHANNEL_ADMIN + "/channel-call-grants",
                             {"channelId": cid, "callerService": CALLER, "standardApiCode": SERVICE_STD})
        grant = m.must("新建授权", status, body, (201,))
        record("createGrant", grant.get("grantStatus") == "ACTIVE", httpStatus=status,
               grant={k: grant.get(k) for k in ("callGrantId", "callerService", "standardApiCode", "grantStatus", "version")})
    else:
        status, body = set_grant(token, grant, "ACTIVE")
        grant = m.must("启用授权", status, body, (200,))
        record("enableGrant", grant.get("grantStatus") == "ACTIVE", httpStatus=status, version=grant.get("version"))
    since = m.now_sql()
    status, body, view = service_call(CALLER, SERVICE_CODE, SERVICE_STD, ping("granted"))
    # 只记录：保留域名不可达，结果为 ERROR 或未发出类 Problem 都属预期；只要求不是 403。
    record("grantedResultRecorded", status != 403, **view, vaultEvents=vault_summary(since),
           note="授权后到达执行阶段；结果只记录、不判成功")
    status, body = set_grant(token, grant, "DISABLED")
    grant = m.must("停用授权", status, body, (200,))
    record("disableGrant", grant.get("grantStatus") == "DISABLED", httpStatus=status, version=grant.get("version"))
    status, body, view = service_call(CALLER, SERVICE_CODE, SERVICE_STD, ping("disabled"))
    record("disabledRejected", status == 403 and body.get("code") == "CHANNEL_CALL_NOT_GRANTED", **view)
    status, body = m.ops(token, "GET", f"{CHANNEL_ADMIN}/config-changes?targetType=CHANNEL_CALL_GRANT"
                                       f"&targetId={grant['callGrantId']}&page=1&size=50")
    record("grantChanges", status == 200, httpStatus=status, total=body.get("total") if isinstance(body, dict) else None,
           changes=[[c.get("changeType"), c.get("fromStatus"), c.get("toStatus")] for c in (body.get("items") or [])]
           if isinstance(body, dict) else None)
    return grant


def payment_channel_via_service_entry(record):
    status, body, view = service_call(CALLER, "DEMO_NI", "REFUND", ping("paymentchannel"))
    record("paymentChannelRejected", status == 400, **view, note="期望 400（渠道须为 SERVICE 且 ACTIVE）")


def removed_crud(token, record):
    """接入方 5 个增删改经控制台：均应 404。写探针用非法体或不可能的版本，即使路由仍在也不会写入（旧处理器返回 400 / 409）。"""
    probes = {}
    for name, method, path, body in (
            ("list", "GET", "/integrations?page=1&size=10", None),
            ("detail", "GET", "/integrations/DEMO_NI", None),
            ("create", "POST", "/integrations", {"integrationCode": "s7p2 invalid"}),
            ("update", "PUT", "/integrations/DEMO_NI", {"integrationName": "", "expectedVersion": 999999}),
            ("delete", "DELETE", "/integrations/DEMO_NI", {"expectedVersion": 999999, "reason": REASON})):
        status, response = m.ops(token, method, EXTERNAL_ADMIN + path, body)
        probes[name] = [status, response.get("code") if isinstance(response, dict) else None]
    record("removedIntegrationCrud", all(v[0] == 404 for v in probes.values()), probes=probes)


def service():
    token = m.login()
    if detect_version(token) != 2:
        raise SystemExit("service 只在第二段版本运行")
    record = Recorder()
    started = now_iso()
    channel_counts(token, record)
    cid = ensure_channel(token, record)
    m.write(SERVICE_STATE.name, {"channelId": str(cid), "channelCode": SERVICE_CODE, "standardApiCode": SERVICE_STD,
                                 "apiCode": SERVICE_API, "callerService": CALLER, "createdAt": started})
    configure_endpoints(token, cid, record)
    ensure_protocol(token, record)
    grant = grants(token, cid, record)
    m.write(SERVICE_STATE.name, {**json.loads(SERVICE_STATE.read_text()), "callGrantId": str(grant["callGrantId"])})
    payment_channel_via_service_entry(record)
    view = navigation(token)
    record("navigation", view["httpStatus"] == 200 and not view["hasIntegrationsPage"] and view["hasCallGrantButton"]
           and view["hasStandardApisPage"], **{k: v for k, v in view.items() if k != "codes"})
    removed_crud(token, record)
    channel = find_service_channel(token) or {}
    record("serviceChannelListed", channel.get("status") == "ACTIVE",
           item={k: channel.get(k) for k in ("channelCode", "channelType", "status", "endpointCount", "activeGrantCount")})
    m.write("phase2-service.json", {"kind": "service", "startedAt": started, "finishedAt": now_iso(), "results": record.results})
    failed = [k for k, v in record.results.items() if not v["ok"]]
    print(f"service {len(record.results) - len(failed)}/{len(record.results)} 通过" + (f"；未通过：{failed}" if failed else ""))


# ---------- cleanup ----------

def cleanup():
    token = m.login()
    if detect_version(token) != 2:
        raise SystemExit("cleanup 只在第二段版本运行")
    channel = find_service_channel(token)
    if channel is None:
        raise SystemExit(f"没有 {SERVICE_CODE}，无需清理")
    cid, steps = channel["channelId"], []
    _, _, items = m.paged(token, f"{CHANNEL_ADMIN}/channel-call-grants?channelId={cid}")
    for grant in items:
        if grant.get("grantStatus") == "ACTIVE":
            status, body = set_grant(token, grant, "DISABLED")
            steps.append(["grant DISABLED", grant["callGrantId"], status, problem(body) if status != 200 else body.get("grantStatus")])
    if channel.get("status") != "CLOSED":
        status, body = m.ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "CLOSED", "reason": REASON})
        steps.append(["channel CLOSED", cid, status, problem(body) if status != 200 else body.get("status")])
        if status != 200 and channel.get("status") == "ACTIVE":
            status, body = m.ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status",
                                 {"targetStatus": "SUSPENDED", "reason": REASON})
            steps.append(["channel SUSPENDED", cid, status, problem(body) if status != 200 else body.get("status")])
            status, body = m.ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status",
                                 {"targetStatus": "CLOSED", "reason": REASON})
            steps.append(["channel CLOSED", cid, status, problem(body) if status != 200 else body.get("status")])
    _, detail = m.ops(token, "GET", f"{CHANNEL_ADMIN}/channels/{cid}")
    _, _, grants_after = m.paged(token, f"{CHANNEL_ADMIN}/channel-call-grants?channelId={cid}")
    residue = {
        "channel": {"channelId": str(cid), "channelCode": SERVICE_CODE, "status": detail.get("status"),
                    "reason": "渠道不提供删除；置 CLOSED 保留"},
        "grants": [{k: g.get(k) for k in ("callGrantId", "callerService", "standardApiCode", "grantStatus", "version")}
                   for g in grants_after],
        "grantsNote": "授权只停用、不删除",
        "protocol": {"standardApi": SERVICE_STD, "atomicApi": f"{SERVICE_CODE}/{SERVICE_API}",
                     "implementation": f"{SERVICE_CODE}/{SERVICE_STD}",
                     "reason": "保留：调用授权行按 standard_api_id 引用 S7P2_PING，删除标准 API 会使授权引用悬空；"
                               "实现与原子 API 留作审计，渠道已 CLOSED 不会被执行"},
        "configChanges": "S7P2_SVC 渠道、授权、标准 API、原子 API、实现的 t_channel_config_change 记录只增不改，保留",
        "accessLog": "channel.outbound.access 日志行保留"}
    ok = detail.get("status") == "CLOSED" and all(g.get("grantStatus") == "DISABLED" for g in grants_after)
    m.write("phase2-cleanup.json", {"kind": "cleanup", "finishedAt": now_iso(), "ok": ok, "steps": steps, "residue": residue})
    print(json.dumps({"ok": ok, "steps": steps}, ensure_ascii=False))


# ---------- smoke ----------

def smoke():
    results = []

    def check(name, ok, evidence=""):
        results.append([name, bool(ok), evidence])
        print(f"[{'通过' if ok else '失败'}] {name} {evidence}", flush=True)

    token = m.login()
    check("运营登录", bool(token))
    view = navigation(token)
    pages = set(view["codes"])
    for page in ("OPERATIONS_EXTERNAL_STANDARD_APIS", "OPERATIONS_CHANNEL_CHANNELS", "OPERATIONS_CHANNEL_CALL_GRANT_MANAGE",
                 "OPERATIONS_FINANCE_RECON_CHANNEL_LINES", "OPERATIONS_DATA_SOURCES", "OPERATIONS_QUERY_TEMPLATES",
                 "OPERATIONS_FILE_STORAGE"):
        check(f"导航含 {page}", view["httpStatus"] == 200 and page in pages, f"HTTP {view['httpStatus']}")
    check("导航不含 OPERATIONS_EXTERNAL_INTEGRATIONS", view["httpStatus"] == 200 and "OPERATIONS_EXTERNAL_INTEGRATIONS" not in pages)
    check("渠道页名称为「渠道」", view["channelPage"].get("displayName") == "渠道", str(view["channelPage"].get("displayName")))

    for name, path, want in (("控制台外部 API 标准 API 列表", EXTERNAL_ADMIN + "/standard-apis", 200),
                             ("控制台接入方列表已下线", EXTERNAL_ADMIN + "/integrations?page=1&size=10", 404),
                             ("渠道列表（全部）", CHANNEL_ADMIN + "/channels", 200),
                             ("渠道列表（服务渠道）", CHANNEL_ADMIN + "/channels?channelType=SERVICE", 200),
                             ("调用授权列表", CHANNEL_ADMIN + "/channel-call-grants", 200)):
        status, _ = m.ops(token, "GET", path)
        check(name, status == want, f"HTTP {status}")
    status, _ = h.private_call("platform", "GET", "/external-api-admin/v1/standard-apis")
    check("Platform 私网 /external-api-admin 已下线", status == 404, f"HTTP {status}")

    # 数据查询
    status, body = m.ops(token, "GET", "/data-query-admin/v1/data-sources")
    check("数据查询 GET /data-query-admin/v1/data-sources", status == 200, f"HTTP {status}")
    items = body.get("items") or [] if isinstance(body, dict) else []
    if items and items[0].get("dataSourceId"):
        status, _ = m.ops(token, "GET", f"/data-query-admin/v1/data-sources/{items[0]['dataSourceId']}")
        check("数据源详情", status == 200, f"HTTP {status}")
    status, body = m.ops(token, "GET", "/data-query-admin/v1/templates")
    check("数据查询 GET /data-query-admin/v1/templates", status == 200, f"HTTP {status}")
    items = body.get("items") or [] if isinstance(body, dict) else []
    if items and items[0].get("templateId"):
        template_id = items[0]["templateId"]
        status, _ = m.ops(token, "GET", f"/data-query-admin/v1/templates/{template_id}")
        check("查询模板详情", status == 200, f"HTTP {status}")
        status, _ = m.ops(token, "GET", f"/data-query-admin/v1/templates/{template_id}/versions")
        check("查询模板版本列表", status == 200, f"HTTP {status}")

    # 文件存储
    for path in ("/storage-admin/v1/instances", "/storage-admin/v1/routes"):
        status, _ = m.ops(token, "GET", path)
        check(f"文件存储 GET {path}", status == 200, f"HTTP {status}")

    # 同期服务（沿用第一段冒烟）
    status, body = m.ops(token, "GET", "/currency-admin/v1/currency-specs")
    check("币种列表含 USD", status == 200 and "\"USD\"" in json.dumps(body), f"HTTP {status}")
    for path in ("/finance/reconciliation/v1/channel-statement-lines", "/finance/reconciliation/v1/channel-statements",
                 "/finance/reconciliation/v1/recon-exceptions"):
        status, _ = m.ops(token, "GET", path)
        check(f"对账 GET {path}", status == 200, f"HTTP {status}")
    status, body = m.ops(token, "GET", "/commercial-admin/v1/agreements")
    check("商务合同列表", status == 200, f"HTTP {status}")
    items = (body.get("items") or body.get("records") or body.get("content") or []) if isinstance(body, dict) else []
    if items:
        agreement_id = items[0].get("agreementId") or items[0].get("id")
        status, _ = m.ops(token, "GET", f"/commercial-admin/v1/agreements/{agreement_id}")
        check("商务合同详情", status == 200, f"HTTP {status}")
        if items[0].get("clientId"):
            status, _ = m.ops(token, "GET", f"/commercial-admin/v1/rate-cards?clientId={items[0]['clientId']}")
            check("商务费率预览", status == 200, f"HTTP {status}")
    status, _ = m.ops(token, "GET", "/commercial-admin/v1/dashboard")
    check("商务看板", status == 200, f"HTTP {status}")

    failed = [name for name, ok, _ in results if not ok]
    m.write("phase2-smoke.json", {"kind": "smoke", "finishedAt": now_iso(), "results": results, "failed": failed})
    print(f"冒烟 {len(results) - len(failed)}/{len(results)} 通过" + (f"；失败：{failed}" if failed else ""))
    sys.exit(1 if failed else 0)


def main():
    command, args = (sys.argv[1] if len(sys.argv) > 1 else ""), sys.argv[2:]
    if command == "snapshot" and len(args) == 1:
        token = m.login()
        data = {"kind": "snapshot", "label": args[0], "takenAt": now_iso(), "snapshot": snapshot(token, detect_version(token))}
        data["finishedAt"] = now_iso()
        m.write(f"phase2-snapshot-{args[0]}.json", data)
        s = data["snapshot"]
        print(json.dumps({"version": s["version"], "calls": s["calls"], "statuses": s["statuses"], "non200": s["non200"],
                          "removedIntegrationCrud": s["removedIntegrationCrud"], "configChangeRows": len(s["configChangeRows"])},
                         ensure_ascii=False))
        return None
    if command == "compare" and len(args) == 2:
        return compare(*args)
    if command == "chain" and len(args) == 1:
        return chain(args[0])
    if command == "rerun" and len(args) == 1:
        return rerun(args[0])
    if command == "nav" and len(args) == 1:
        return nav(args[0])
    if command == "service" and not args:
        return service()
    if command == "cleanup" and not args:
        return cleanup()
    if command == "smoke" and not args:
        return smoke()
    raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
