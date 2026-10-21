"""对账切片 1 缺陷 D-1 复测：N 行渠道账单匹配任务耗时（直连私有 reconciliation 18115，不经 Gateway / BFF）。

与 perf_20k.py 的差别只在入口：账单导入直接调用 reconciliation 内部 HTTP（带 Gateway 身份头，MAKER 900001 / CHECKER 900002），
因此不需要 IAM 合成人员与私有 Gateway / BFF / Nginx；事实发布、平台流水登记、匹配任务与计时口径与 perf_20k.py 相同。

  python3 -I perf_direct.py vhost-up              建临时 vhost /mor-recon5 并授权（须在 stack.sh start-recon 之前）
  python3 -I perf_direct.py setup                 经 API 建到账银行账户（Maker 提交、Checker 批准）与清算账户
  python3 -I perf_direct.py run [行数]            发布 N 条 PAYMENT 事实 → 等全部登记 → 分段导入 N 行 → 计匹配任务耗时
  python3 -I perf_direct.py cleanup               删除 mor_reconciliation_v2e2e 中的全部合成行（逐表计数）
  python3 -I perf_direct.py vhost-down            删除临时 vhost
运行记录写 $RECON5_STATE/d1-run.json（会话 scratchpad）。
"""
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

BASE = "http://127.0.0.1:18115/reconciliation/v1"
MAKER, CHECKER = "900001", "900002"
RECORD = c.STATE / "d1-run.json"
TABLES = ["t_bank_match", "t_bank_statement_line", "t_bank_statement", "t_expected_bank_movement", "t_recon_resolution",
          "t_recon_exception", "t_channel_statement_line", "t_channel_statement", "t_platform_txn", "t_clearing_account_mid",
          "t_clearing_account", "t_cash_account", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version"]


def call(actor, method, path, body=None):
    headers = {"Accept": "application/json", "X-Gateway-Actor-Subject": actor, "X-Gateway-Session-Id": "recon-d1-perf",
               "X-Request-Id": uuid.uuid4().hex}
    data = None
    if method != "GET":
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode() if body is not None else b""
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            text = response.read().decode()
            return response.status, json.loads(text) if text else {}
    except urllib.error.HTTPError as failure:
        return failure.code, json.loads(failure.read().decode() or "{}")


def load():
    return json.loads(RECORD.read_text()) if RECORD.exists() else {}


def save(record):
    RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))


def vhost_up():
    c._rmq("PUT", f"/api/vhosts/{c.vhost_path()}", {"description": "recon D-1 perf (temporary)"})
    c._rmq("PUT", f"/api/permissions/{c.vhost_path()}/{c.LOCAL['MOR_RABBITMQ_USER']}",
           {"configure": ".*", "write": ".*", "read": ".*"})
    print("vhost", c.VHOST, "ready")


def vhost_down():
    c._rmq("DELETE", f"/api/vhosts/{c.vhost_path()}")
    print("vhost", c.VHOST, "deleted;", "remaining:", [v["name"] for v in c._rmq("GET", "/api/vhosts")])


def setup():
    record = load()
    suffix = uuid.uuid4().hex[:6].upper()
    status, cash = call(MAKER, "POST", "/cash-accounts", {
        "entityType": "OPERATING_ENTITY", "entityId": "AE001", "accountPurpose": "CHANNEL_SETTLEMENT", "bankCode": "ENBD",
        "accountName": "D1 PERF SETTLEMENT", "currency": "USD", "accountNumber": "AE07033" + suffix + "4321"})
    if status != 201:
        raise SystemExit(f"新建银行账户失败 {status} {cash}")
    cash_id = cash.get("cashAccountId") or cash.get("id")
    status, approved = call(CHECKER, "POST", f"/cash-accounts/{cash_id}/approval",
                            {"decision": "APPROVED", "approvedContentHash": cash["contentHash"]})
    if status != 200:
        raise SystemExit(f"批准银行账户失败 {status} {approved}")
    status, clearing = call(MAKER, "POST", "/clearing-accounts", {
        "clearingAccountCode": f"PMX-AE001-USD-D1{suffix}", "channelCode": "PAYERMAX_MOCK", "entityNo": "AE001",
        "currency": "USD", "settlementCashAccountId": str(cash_id), "unmatchedWaitDays": 3, "costToleranceAmount": "0.10",
        "cashGraceDays": 2})
    if status != 201:
        raise SystemExit(f"新建清算账户失败 {status} {clearing}")
    record["clearingAccount"] = clearing
    record["cashAccountId"] = str(cash_id)
    save(record)
    print("clearing account", clearing["clearingAccountCode"], clearing["clearingAccountId"])


def publish_and_wait(ca_id, run, n, perf):
    facts = [c.fact("PAYMENT", ca_id, "10.00", "NI_ORDER_REF", f"PERF-{run}-{i:05d}", channel_cost="0.05")
             for i in range(1, n + 1)]
    t0 = time.monotonic()
    c.publish_facts(facts, threads=32)
    perf["publishSeconds"] = round(time.monotonic() - t0, 1)

    def consumed():
        return int(c.recon_sql(f"SELECT COUNT(*) FROM t_platform_txn WHERE external_transaction_id LIKE 'PERF-{run}-%'")[0][0])
    while consumed() < n and time.monotonic() - t0 < 1800:
        time.sleep(5)
    perf["consumedCount"] = consumed()
    perf["publishToConsumedSeconds"] = round(time.monotonic() - t0, 1)
    print(f"发布 {n} 条 {perf['publishSeconds']} s，全部登记 {perf['publishToConsumedSeconds']} s", flush=True)


def import_statement(ca, run, n, perf):
    today = date.today().isoformat()
    lines = [{"lineNo": i, "lineType": "PAYMENT", "direction": "CREDIT", "amount": "10.00", "feeAmount": "0.05",
              "referenceType": "NI_ORDER_REF", "externalTransactionId": f"PERF-{run}-{i:05d}", "transactionDate": today,
              "rawAttributes": {"terminal_id": "T01"}} for i in range(1, n + 1)]
    status, started = call(MAKER, "POST", "/channel-statements", {
        "clearingAccountCode": ca["clearingAccountCode"], "channelBatchRef": f"PERF-{run}", "settlementDate": today,
        "currency": "USD", "grossAmount": f"{10 * n:.2f}", "feeAmount": f"{0.05 * n:.2f}", "netAmount": f"{9.95 * n:.2f}",
        "cashDirection": "CREDIT", "fileName": f"perf-{n}.csv", "fileContentHash": c.sha256_text(json.dumps(lines)),
        "declaredLineCount": n})
    if status != 201:
        raise SystemExit(f"开始导入失败 {status} {started}")
    sid = started["statementId"]
    perf["statementId"] = sid
    segment_times = []
    for i in range(0, n, 100):
        ts = time.monotonic()
        s, appended = call(MAKER, "POST", f"/channel-statements/{sid}/lines", {"lines": lines[i:i + 100]})
        segment_times.append(time.monotonic() - ts)
        if s != 200:
            raise SystemExit(f"追加第 {i // 100 + 1} 段失败 {s} {appended}")
    perf["appendSegments"] = len(segment_times)
    perf["appendSegmentMsP50"] = round(statistics.median(segment_times) * 1000)
    return sid


def await_match(sid, perf):
    t = time.monotonic()
    status, done = call(MAKER, "POST", f"/channel-statements/{sid}/completion")
    perf["completionSeconds"] = round(time.monotonic() - t, 2)
    perf["completionStatus"] = done.get("status")
    completed_at, trace, last = time.monotonic(), [], None
    while time.monotonic() - completed_at < 900:
        row = c.recon_sql(f"SELECT status FROM t_channel_statement WHERE id={sid}")[0]
        tasks = c.recon_sql("SELECT execute_status, execute_count, IFNULL(last_error_code,''), "
                            "IFNULL(LEFT(last_error_summary,200),'') FROM t_compensation_event "
                            "WHERE event_type='CHANNEL_STATEMENT_MATCH'")
        if tasks != last:
            trace.append({"t": round(time.monotonic() - completed_at, 2), "statement": row[0], "tasks": tasks})
            print(trace[-1], flush=True)
            last = tasks
        if row[0] != "MATCHING" and not tasks:
            break
        time.sleep(0.25)
    perf["matchSeconds"] = round(time.monotonic() - completed_at, 2)
    perf["matchTrace"] = trace
    perf["finalStatus"] = c.recon_sql(f"SELECT status FROM t_channel_statement WHERE id={sid}")[0][0]
    perf["lineStatus"] = {k: int(v) for k, v in c.recon_sql(
        f"SELECT match_status, COUNT(*) FROM t_channel_statement_line WHERE statement_id={sid} GROUP BY match_status")}
    perf["platformTxnStatus"] = {k: int(v) for k, v in c.recon_sql(
        "SELECT p.status, COUNT(*) FROM t_platform_txn p WHERE p.id IN (SELECT platform_txn_id FROM t_channel_statement_line "
        f"WHERE statement_id={sid}) GROUP BY p.status")}


def run(n):
    record = load()
    ca = record["clearingAccount"]
    perf = {"run": uuid.uuid4().hex[:4].upper(), "lines": n}
    record.setdefault("perf", []).append(perf)
    publish_and_wait(ca["clearingAccountId"], perf["run"], n, perf)
    sid = import_statement(ca, perf["run"], n, perf)
    await_match(sid, perf)
    print(f"{n} 行：匹配任务 → {perf['finalStatus']}（{perf['matchSeconds']} s，行 {perf['lineStatus']}，"
          f"平台流水 {perf['platformTxnStatus']}）", flush=True)
    save(record)


def cleanup():
    before = {t: int(c.recon_sql(f"SELECT COUNT(*) FROM {t}")[0][0]) for t in TABLES}
    for table in TABLES:
        c.recon_sql(f"DELETE FROM {table}")
    after = {t: int(c.recon_sql(f"SELECT COUNT(*) FROM {t}")[0][0]) for t in TABLES}
    record = load()
    record["cleanup"] = {"before": before, "after": after}
    save(record)
    print(json.dumps(record["cleanup"], indent=1))


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "vhost-up":
        vhost_up()
    elif command == "vhost-down":
        vhost_down()
    elif command == "setup":
        setup()
    elif command == "run":
        run(int(sys.argv[2]) if len(sys.argv) > 2 else 20000)
    elif command == "cleanup":
        cleanup()
    else:
        print(__doc__)
        sys.exit(2)
