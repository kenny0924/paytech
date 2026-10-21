"""对账切片 1 阶段 5：2 万行渠道账单性能（I-14：CHANNEL_STATEMENT_MATCH 单次尝试超时 100 秒、补偿 Lease 默认 2 分钟）。

1. 以正式发布器发布 N 条 PAYMENT BillingFactRecorded（每条 10.00、预估费 0.05，外部交易号 PERF-<run>-<序号>），计时并等待全部登记为平台流水；
2. 以 MAKER 身份经运营入口（Nginx → Gateway → BFF → reconciliation）分段导入 N 行账单（每段 100 行），分别计开始、追加（逐段）、完成导入耗时；
3. 轮询账单状态直到 RECONCILED，匹配任务耗时 = 对平时间 − 完成导入响应时间；同时读 t_compensation_event 的尝试次数与错误。
用法：python3 -I perf_20k.py <run-*.json> [行数，默认 20000]
"""
import json
import statistics
import sys
import time
import uuid
from datetime import date, datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

R = "/finance/reconciliation/v1"


def main():
    record_path = Path(sys.argv[1])
    record = json.loads(record_path.read_text())
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 20000
    run = uuid.uuid4().hex[:4].upper()
    ca = record["clearingAccount"]
    today = date.today().isoformat()
    perf = {"run": run, "lines": n}
    record.setdefault("perf", []).append(perf)

    facts = [c.fact("PAYMENT", ca["clearingAccountId"], "10.00", "NI_ORDER_REF", f"PERF-{run}-{i:05d}", channel_cost="0.05")
             for i in range(1, n + 1)]
    t0 = time.monotonic()
    c.publish_facts(facts, threads=32)
    perf["publishSeconds"] = round(time.monotonic() - t0, 1)
    print(f"发布 {n} 条事实 {perf['publishSeconds']} s", flush=True)

    def consumed():
        return int(c.recon_sql(f"SELECT COUNT(*) FROM t_platform_txn WHERE external_transaction_id LIKE 'PERF-{run}-%'")[0][0])
    while consumed() < n and time.monotonic() - t0 < 1800:
        time.sleep(5)
    perf["consumedCount"] = consumed()
    perf["publishToConsumedSeconds"] = round(time.monotonic() - t0, 1)
    print(f"平台流水 {perf['consumedCount']} 条，发布到全部登记 {perf['publishToConsumedSeconds']} s", flush=True)
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))

    people = record["people"]
    maker = c.login(people["MAKER"]["email"], people["MAKER"]["password"])
    lines = [{"lineNo": i, "lineType": "PAYMENT", "direction": "CREDIT", "amount": "10.00", "feeAmount": "0.05",
              "referenceType": "NI_ORDER_REF", "externalTransactionId": f"PERF-{run}-{i:05d}", "transactionDate": today,
              "rawAttributes": {"terminal_id": "T01"}} for i in range(1, n + 1)]
    gross, fee = f"{10 * n:.2f}", f"{0.05 * n:.2f}"
    net = f"{10 * n - 0.05 * n:.2f}"
    t = time.monotonic()
    status, started = c.raw(maker, "POST", R + "/channel-statements", {
        "clearingAccountCode": ca["clearingAccountCode"], "channelBatchRef": f"PERF-{run}", "settlementDate": today, "currency": "USD",
        "grossAmount": gross, "feeAmount": fee, "netAmount": net, "cashDirection": "CREDIT", "fileName": f"perf-{n}.csv",
        "fileContentHash": c.sha256_text(json.dumps(lines)), "declaredLineCount": n})
    perf["startSeconds"] = round(time.monotonic() - t, 3)
    if status != 201:
        raise SystemExit(f"开始导入失败 {status} {started}")
    sid = started["statementId"]
    perf["statementId"] = sid
    record.setdefault("statements", []).append(sid)
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    segment_times, sizes = [], []
    t_append = time.monotonic()
    for i in range(0, n, 100):
        body = {"lines": lines[i:i + 100]}
        sizes.append(len(json.dumps(body).encode()))
        ts = time.monotonic()
        s, appended = c.raw(maker, "POST", f"{R}/channel-statements/{sid}/lines", body)
        segment_times.append(time.monotonic() - ts)
        if s != 200:
            raise SystemExit(f"追加第 {i // 100 + 1} 段失败 {s} {appended}")
    perf["appendTotalSeconds"] = round(time.monotonic() - t_append, 1)
    perf["appendSegments"] = len(segment_times)
    perf["appendSegmentMs"] = {"p50": round(statistics.median(segment_times) * 1000), "max": round(max(segment_times) * 1000),
                               "mean": round(statistics.mean(segment_times) * 1000)}
    perf["segmentBodyBytesMax"] = max(sizes)
    print(f"追加 {len(segment_times)} 段 {perf['appendTotalSeconds']} s（每段 {perf['appendSegmentMs']} ms，最大正文 {max(sizes)} B）", flush=True)
    t = time.monotonic()
    status, done = c.raw(maker, "POST", f"{R}/channel-statements/{sid}/completion")
    perf["completionSeconds"] = round(time.monotonic() - t, 2)
    completed_at = time.monotonic()
    perf["completionStatus"] = done.get("status")
    print(f"完成导入 {status} {done.get('status')} {perf['completionSeconds']} s", flush=True)
    events = []
    last = None
    while time.monotonic() - completed_at < 900:
        row = c.recon_sql(f"SELECT status, reconciled_time FROM t_channel_statement WHERE id={sid}")[0]
        tasks = c.recon_sql("SELECT event_type, execute_status, execute_count, IFNULL(last_error_code,''), IFNULL(LEFT(last_error_summary,200),'') FROM t_compensation_event "
                            f"WHERE event_type='CHANNEL_STATEMENT_MATCH'")
        if tasks != last:
            events.append({"t": round(time.monotonic() - completed_at, 1), "statement": row[0], "tasks": tasks})
            print(events[-1], flush=True)
            last = tasks
        if row[0] != "MATCHING" and not tasks:
            break
        time.sleep(1)
    perf["matchSeconds"] = round(time.monotonic() - completed_at, 1)
    perf["matchTrace"] = events
    row = c.recon_sql(f"SELECT status, reconciled_time, update_time FROM t_channel_statement WHERE id={sid}")[0]
    perf["finalStatus"] = row[0]
    counts = c.recon_sql(f"SELECT match_status, COUNT(*) FROM t_channel_statement_line WHERE statement_id={sid} GROUP BY match_status")
    perf["lineStatus"] = {k: int(v) for k, v in counts}
    print(f"匹配任务 → {row[0]}（{perf['matchSeconds']} s，行 {perf['lineStatus']}）", flush=True)
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
