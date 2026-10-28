#!/usr/bin/env python3
"""参考数据客户端组件 · mor-test 验收（runbook 第 3 节；rel-dev 2026-10-28 准备，未运行；只由主会话在阶段 7 执行）。

子命令（每个都可单独重跑）：
  capture-finance <目录>      发布 ops-bff 之前：保存运营财务页币种下拉 4 种查询（不带 / ACTIVE / INACTIVE / DRAFT）的原始响应字节，
                              另记非法 status 的状态码。只读（会登录运营管理员，产生登录会话）。
  compare-finance <目录>      发布 ops-bff 之后：同样 4 种查询与 capture 逐字节比较；非法 status 仍 400。
  probe --since <UTC>         只读证据（不改配置看不到健康明细时的替代证据）：
                                1) ledger / billing / card / client-master 的 readiness（经只读 sidecar：目标镜像的 JRE + 容器网络命名空间）。
                                   readiness 组含 referenceData，UP 即"已开启的种类全部至少成功加载一次"（未加载为 OUT_OF_SERVICE）；
                                2) 各服务 --since 之后日志中 "Reference data refresh failed" 计数（客户端刷新失败时的 WARN）与 ERROR 计数；
                                3) Platform 日志中 --since 之后出现的 X-Caller-Service 调用方（DUAL 回退 WARN 带调用方名）；
                                4) Platform 私网 /platform/v1/currency-specs、/country-regions → 期望 Platform 发布前 200、发布后 404；
                                   /reference-data/{currencies,countries,country-subdivisions,industries,mccs} 的条数（客户端快照的来源）；
                                5) Nacos 解析：platform-support-service 在 namespace mor-test 有健康实例（card / client-master 经服务发现访问它）。
  ledger                      ledger 经运营接口：列账簿 → 选 AE001 的账簿与 OPEN 期间 → 读科目余额与分录（读路径用客户端币种精度换算）
                              → 执行一次试算（写入一条试算记录，不改分录 / 余额；运营日常操作，可重复）。
  billing                     与阶段 5 相同的 billing 私网请求（verification/local-e2e/stage5-requests.json；经接入 mor-test-backend 的一次性探针容器）：
                              超精度 USD 1.005 → 400 FACT_LINE_INVALID；Platform 未登记的币种（JPY，若已登记则顺延 CHF / GBP / CNY）→ 400 BUSINESS_CHECK_FAILED；
                              合法 USD 1.25 → 201 DRAFT → 立即作废（VOIDED，不审批、不发布、不产生分录）。OE / Client 为合成编码（ZZ9xx / 9<run>），不触及 AE001。
  client-master               复用参考数据需求的 mor-test 验收（release/acceptance/refdata_mortest.py，已去掉全部改库步骤）：F、A、K。
不做：card BIN 上传（会留下无法删除的 INVALID 版本；card 证据用 readiness、Platform 调用方日志、Nacos 解析与阶段 5 本机导入证据，runbook 3.4）；
  ledger "查不到 → 可重投"（mor-test 没有安全做法，沿用 auto 测试证据，runbook 3.2）。
结果：verification/acceptance-mor-test-<子命令>-<UTC>.json。凭据只在内存中使用，不打印。
"""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
REFDATA_ACCEPTANCE = Path("/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/platform-support-参考数据目录与client-master迁移/release/acceptance")
PROBE = HERE / "probe"
CONSUMERS = {"ledger-service": 19105, "billing-service": 19113, "card-service": 19111, "client-master-service": 19101}
FINANCE_QUERIES = {"all": "", "ACTIVE": "?status=ACTIVE", "INACTIVE": "?status=INACTIVE", "DRAFT": "?status=DRAFT"}
FINANCE_PATH = "/api/operations/finance/currencies/v1"
RESULTS = []


def record(ac, name, ok, evidence=None):
    RESULTS.append({"ac": ac, "check": name, "verdict": "PASS" if ok else "FAIL", "evidence": evidence})
    print(f"[{'PASS' if ok else 'FAIL'}] {ac} {name}" + ("" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False)[:300]}"),
          flush=True)
    return ok


def info(ac, name, evidence=None):
    RESULTS.append({"ac": ac, "check": name, "verdict": "INFO", "evidence": evidence})
    print(f"[INFO] {ac} {name}" + ("" if evidence is None else f" | {json.dumps(evidence, ensure_ascii=False)[:300]}"), flush=True)


def save(label):
    out = REQ / "verification" / f"acceptance-mor-test-{label}-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
    summary = {v: sum(1 for r in RESULTS if r["verdict"] == v) for v in ("PASS", "FAIL", "INFO")}
    out.write_text(json.dumps({"env": "mor-test", "summary": summary, "results": RESULTS}, ensure_ascii=False, indent=1) + "\n")
    print(f"==== {summary} → {out}")
    return 1 if summary["FAIL"] else 0


def helpers():
    """运营登录、私网探针：沿用参考数据需求的 mor-test 封装（E2E_TARGET=test）。"""
    sys.path.insert(0, str(REFDATA_ACCEPTANCE))
    import refdata_mortest  # noqa: E402
    return refdata_mortest.load()


def ops_session(module):
    secrets = module.E.secrets()
    return module.OA.ops_login(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])


def raw_get(module, session, path):
    """带运营 Bearer 的原始 GET，返回 (status, 字节)。"""
    origin = module.E.OPERATIONS
    request = urllib.request.Request(origin + path, headers={"Origin": origin, "Accept": "application/json",
                                                             "Authorization": "Bearer " + session.token})
    try:
        with urllib.request.urlopen(request, context=module.E.TLS, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read()


def raw_bodyless_post(module, session, path):
    """无请求体运营命令：与运营前端相同，Content-Type JSON、正文为空、带 Idempotency-Key（Gateway Body 上限 0）。"""
    origin = module.E.OPERATIONS
    request = urllib.request.Request(origin + path, data=b"", method="POST",
                                     headers={"Origin": origin, "Accept": "application/json", "Content-Type": "application/json",
                                              "Idempotency-Key": str(uuid.uuid4()), "Authorization": "Bearer " + session.token})
    try:
        with urllib.request.urlopen(request, context=module.E.TLS, timeout=20) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode("utf-8", "replace")


# ------------------------------------------------------------------------------------------- finance dropdown

def capture_finance(target):
    module = helpers(); session = ops_session(module)
    target.mkdir(parents=True, exist_ok=True)
    for name, query in FINANCE_QUERIES.items():
        status, body = raw_get(module, session, FINANCE_PATH + query)
        (target / f"finance-currencies-{name}.json").write_bytes(body)
        record("AC-10", f"capture {FINANCE_PATH}{query} HTTP {status}（{len(body)} 字节）", status == 200)
    status, _ = raw_get(module, session, FINANCE_PATH + "?status=BOGUS")
    (target / "invalid-status.txt").write_text(str(status) + "\n")
    record("AC-10", f"capture 非法 status → {status}", status == 400)
    return save("capture-finance")


def compare_finance(target):
    module = helpers(); session = ops_session(module)
    for name, query in FINANCE_QUERIES.items():
        status, body = raw_get(module, session, FINANCE_PATH + query)
        before = (target / f"finance-currencies-{name}.json").read_bytes()
        record("AC-10", f"财务下拉 {query or '（不带参数）'} 与发布前逐字节相同（{len(body)} 字节）", status == 200 and body == before,
               {"status": status, "before": before[:200].decode(errors="replace"), "after": body[:200].decode(errors="replace")})
    status, _ = raw_get(module, session, FINANCE_PATH + "?status=BOGUS")
    record("AC-10", f"非法 status 仍 {status}", status == int((target / "invalid-status.txt").read_text().strip()))
    return save("compare-finance")


# ------------------------------------------------------------------------------------------- read-only probe

def ensure_probe():
    PROBE.mkdir(exist_ok=True)
    if not (PROBE / "HttpGet.class").exists():
        subprocess.run(["/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javac", "--release", "25", "-d", str(PROBE),
                        str(HERE / "HttpGet.java")], check=True)
        os.chmod(PROBE, 0o755)


def sidecar_get(service, port, path):
    image = subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", "{{.Image}}"], capture_output=True, text=True).stdout.strip()
    out = subprocess.run(["docker", "run", "--rm", "--network", f"container:mor-test-{service}-1", "-v", f"{PROBE}:/probe:ro",
                          "--entrypoint", "java", "--user", "10001:10001", image, "-cp", "/probe", "HttpGet", f"http://127.0.0.1:{port}{path}"],
                         capture_output=True, text=True, timeout=60)
    line = next((l for l in out.stdout.splitlines() if re.match(r"^\d{3} ", l)), "000 " + out.stderr[-200:])
    return int(line[:3]), line[4:]


def logs_since(service, since):
    out = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{service}-1"], capture_output=True, text=True)
    return out.stdout + out.stderr


def probe(since, expect_old):
    ensure_probe()
    for service, port in CONSUMERS.items():
        status, body = sidecar_get(service, port, "/actuator/health/readiness")
        record("AC-11", f"{service} readiness（含 referenceData）{status} {body[:60]}", status == 200 and '"UP"' in body)
        text = logs_since(service, since)
        failures = text.count("Reference data refresh failed")
        errors = sum(1 for line in text.splitlines() if " ERROR " in line)
        record("AC-11", f"{service} {since} 之后：刷新失败 WARN {failures}、ERROR {errors}", failures == 0 and errors == 0)
    platform = logs_since("platform-support-service", since)
    callers = sorted(set(re.findall(r"X-Caller-Service 明文头（DUAL 模式回退）: ([a-z0-9-]+)", platform)))
    info("AC-11", "Platform 识别到的 X-Caller-Service 调用方（DUAL 回退日志）", callers)
    module = helpers()
    for path in ("/platform/v1/currency-specs", "/platform/v1/country-regions"):
        status, _ = module.E.private_call("platform", "GET", path, {"Accept": "application/json"})
        want = 200 if expect_old else 404
        record("AC-10", f"旧接口 {path} → {status}（期望 {want}）", status == want)
    counts = {}
    for kind, path in (("CURRENCY", "/reference-data/currencies"), ("COUNTRY", "/reference-data/countries"),
                       ("SUBDIVISION", "/reference-data/country-subdivisions"), ("INDUSTRY", "/reference-data/industries"),
                       ("MCC", "/reference-data/mccs?page=1&size=10")):
        status, text = module.E.private_call("platform", "GET", "/platform/v1" + path, {"Accept": "application/json"})
        body = json.loads(text) if status == 200 else {}
        counts[kind] = body.get("total", len(body.get("items") or [])) if status == 200 else f"HTTP {status}"
    info("AC-11", "Platform 参考数据条数（各消费方快照的来源；全部状态）", counts)
    hosts = nacos_instances("platform-support-service")
    record("AC-11", f"Nacos 解析 platform-support-service（mor-test / DEFAULT_GROUP）健康实例 {len([h for h in hosts if h[2]])}", any(h[2] for h in hosts), hosts)
    return save("probe")


def nacos_instances(service):
    """Nacos 只读查询（服务账号 mor-test-services 登录取临时 token）：返回 [(ip, port, healthy)]。"""
    env = dict(line.split("=", 1) for line in Path("/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/.env.test").read_text().splitlines()
               if "=" in line and not line.startswith("#"))
    data = urllib.parse.urlencode({"username": "mor-test-services", "password": env["MOR_NACOS_SERVICE_PASSWORD"]}).encode()
    with urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:18848/nacos/v1/auth/login", data=data, method="POST"), timeout=10) as r:
        token = json.loads(r.read())["accessToken"]
    query = urllib.parse.urlencode({"serviceName": service, "namespaceId": "mor-test", "groupName": "DEFAULT_GROUP", "accessToken": token})
    with urllib.request.urlopen(f"http://127.0.0.1:18848/nacos/v1/ns/instance/list?{query}", timeout=10) as r:
        return [(h["ip"], h["port"], h["healthy"]) for h in json.loads(r.read()).get("hosts", [])]


# ------------------------------------------------------------------------------------------- ledger / billing

def ledger():
    module = helpers(); session = ops_session(module)
    status, books = session.get("/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId=AE001&page=1&size=50")
    ae001 = (books or {}).get("items") or []
    if not record("AC-07", f"AE001 账簿 {status}：{len(ae001)} 个", status == 200 and ae001,
                  [{k: b.get(k) for k in ("ledgerBookId", "currency", "bookStatus")} for b in ae001][:10]):
        return save("ledger")
    status, calendars = session.get("/finance/fiscal-calendars/v1?page=1&size=50")
    periods = []
    for calendar in (calendars or {}).get("items") or []:
        st, page = session.get(f"/finance/fiscal-calendars/v1/{calendar['fiscalCalendarId']}/periods?page=1&size=50")
        periods += [p for p in (page or {}).get("items") or [] if p.get("status") == "OPEN"]
    chosen = None
    for book in ae001:   # 先用只读的科目余额找到"账簿 × OPEN 期间"组合（读路径按客户端币种精度换算金额）
        for period in periods:
            st, balances = session.get(f"/finance/ledger-books/v1/{book['ledgerBookId']}/periods/{period['accountingPeriodId']}/account-balances")
            if st == 200:
                chosen = (book, period, balances)
                break
        if chosen:
            break
    if not record("AC-07", f"科目余额读取（账簿 × OPEN 期间，候选期间 {len(periods)}）", chosen is not None,
                  None if chosen else {"periods": [p.get("accountingPeriodId") for p in periods][:10]}):
        return save("ledger")
    book, period, balances = chosen
    info("AC-07", f"账簿 {book['ledgerBookId']}（{book.get('currency')}）期间 {period['accountingPeriodId']} 余额样例", str(balances)[:300])
    status, run = raw_bodyless_post(module, session, f"/api/operations/finance/ledger-books/v1/{book['ledgerBookId']}/periods/{period['accountingPeriodId']}/trial-balances")
    record("AC-07", f"执行试算 {status}（只写试算记录，不改分录 / 余额）", status in (200, 201), str(run)[:300])
    return save("ledger")


def billing():
    module = helpers()
    template = json.loads((HERE / "payloads/billing-fact.json").read_text())
    if template.get("_status") != "READY":
        raise SystemExit("payloads/billing-fact.json 未就绪（_status 不是 READY），停止")
    body = {k: v for k, v in template.items() if not k.startswith("_")}
    run = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
    oe = "ZZ9" + run[-2:]
    actor = os.environ.get("REFCLIENT_ACTOR_ID", "221546852372267008")   # mor-test 运营管理员（只作操作人记录）
    headers = {"Accept": "application/json", "X-Gateway-Actor-Subject": actor, "X-Gateway-Session-Id": "refclient-mortest-" + run}
    status, text = module.E.private_call("platform", "GET", "/platform/v1/reference-data/currencies", {"Accept": "application/json"})
    registered = {i["currencyCode"] for i in json.loads(text).get("items", [])} if status == 200 else set()
    unknown = next(c for c in ("JPY", "CHF", "GBP", "CNY", "KWD") if c not in registered)
    info("AC-07", f"Platform 已登记币种 {sorted(registered)}；未登记样本 {unknown}；合成 OE {oe}")

    def submit(amount, currency, ref):
        text = json.dumps(body).replace("__RUN__", run).replace("__OE__", oe).replace("__AMOUNT__", amount) \
            .replace("__CURRENCY__", currency).replace("__REF__", ref)
        status, reply = module.E.private_call("billing", "POST", "/billing/v1/facts", headers, json.loads(text))
        return status, (json.loads(reply) if reply.strip().startswith("{") else {"raw": reply[:200]})

    status, reply = submit("1.005", "USD", "USD-1.005")
    record("AC-07", f"USD 1.005 → {status} {reply.get('code')}", status == 400 and reply.get("code") == "FACT_LINE_INVALID", reply)
    status, reply = submit("100", unknown, f"{unknown}-100")
    record("AC-07", f"{unknown} → {status} {reply.get('code')}", status == 400 and reply.get("code") == "BUSINESS_CHECK_FAILED", reply)
    status, reply = submit("1.25", "USD", "USD-1.25")
    if record("AC-07", f"USD 1.25 → {status} {reply.get('status')}", status == 201 and reply.get("status") == "DRAFT", reply):
        status, text = module.E.private_call("billing", "POST", f"/billing/v1/facts/{reply['factId']}/void", headers)
        voided = json.loads(text) if text.strip().startswith("{") else {}
        record("AC-07", f"作废验收草稿 {reply['factId']} → {status} {voided.get('status')}", status == 200 and voided.get("status") == "VOIDED", text[:200])
    return save("billing")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("capture-finance").add_argument("dir")
    sub.add_parser("compare-finance").add_argument("dir")
    p = sub.add_parser("probe")
    p.add_argument("--since", required=True)
    p.add_argument("--expect-old-endpoints", action="store_true", help="Platform 尚未发布（旧接口仍应 200）")
    sub.add_parser("ledger")
    sub.add_parser("billing")
    sub.add_parser("client-master")
    args = parser.parse_args()
    if args.command == "capture-finance":
        return capture_finance(Path(args.dir))
    if args.command == "compare-finance":
        return compare_finance(Path(args.dir))
    if args.command == "probe":
        return probe(args.since, args.expect_old_endpoints)
    if args.command == "ledger":
        return ledger()
    if args.command == "billing":
        return billing()
    return subprocess.run([sys.executable, "-I", str(REFDATA_ACCEPTANCE / "refdata_mortest.py"), "--since", "1970-01-01T00:00:00Z",
                           "--only", "F,A,K"]).returncode


if __name__ == "__main__":
    sys.exit(main())
