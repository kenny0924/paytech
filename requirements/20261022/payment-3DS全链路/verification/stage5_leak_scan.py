"""3DS AC-11 / BIN AC-11：日志、MQ（临时捕获队列 s5-card3ds-capture）、补偿表 Payload、导入错误明细中检索跳转地址敏感参数与完整卡号。

检索词：合成挑战地址主机 3ds.local.invalid、敏感参数 acsSecret= / S5ACS、本次使用的全部测试卡号（完整 PAN），以及任意 12–19 位连续数字中
能通过 Luhn 校验且前缀属于本次测试卡的串。只输出命中计数与位置（文件名、行号），不输出命中内容。
用法：python3 -I stage5_leak_scan.py <since ISO-UTC>；写 stage5-leak-scan.json。
"""
import base64
import json
import re
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage5_3ds as t  # noqa: E402

TERMS = ["3ds.local.invalid", "acsSecret=", "S5ACS"] + list(t.CARDS.values()) + ["4444333322221111"]
LOG_FILES = ["payment", "card", "channel-connector", "risk-decision", "gateway", "platform", "ops-bff", "payermax-mock"]


def luhn_pans(text):
    """以 3–6 开头、13–19 位且通过 Luhn 的连续数字串（Snowflake ID 以 2 开头，不计入）。"""
    found = 0
    for run in re.findall(r"(?<!\d)[3-6]\d{12,18}(?!\d)", text):
        digits = [int(c) for c in reversed(run)]
        total = sum(d if i % 2 == 0 else (d * 2 - 9 if d * 2 > 9 else d * 2) for i, d in enumerate(digits))
        found += total % 10 == 0
    return found


def scan_text(text):
    return {term[:6] + ("…" if len(term) > 6 else ""): text.count(term) for term in TERMS if text.count(term)}


def logs(since):
    out = {}
    for name in LOG_FILES:
        path = t.LOGS / f"{name}.log"
        lines = [line for line in path.read_text(errors="replace").splitlines() if line[:27] >= since or not line[:4].isdigit()]
        hits = {}
        for number, line in enumerate(lines, 1):
            for term in TERMS:
                if term in line:
                    hits.setdefault(term[:6] + "…", []).append(number)
        out[name] = {"linesScanned": len(lines), "hits": {k: len(v) for k, v in hits.items()},
                     "luhnCardLikeRuns": luhn_pans("\n".join(lines))}
    return out


def mq():
    env = t.h.LOCAL
    auth = base64.b64encode(f"{env['MOR_RABBITMQ_USER']}:{env['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    request = urllib.request.Request("http://127.0.0.1:15672/api/queues/%2Fmor-e2e/s5-card3ds-capture/get", method="POST",
                                     data=json.dumps({"count": 1000, "ackmode": "ack_requeue_true", "encoding": "auto"}).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        messages = json.loads(response.read())
    text = "\n".join(m["payload"] for m in messages)
    return {"messages": len(messages), "hits": scan_text(text), "redirectUrlKey": text.count("redirectUrl"),
            "luhnCardLikeRuns": luhn_pans(text)}


def tables():
    out = {}
    for service, statement in (("payment", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("channel-connector", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("card", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("card", "SELECT CONCAT_WS('|', IFNULL(error_message,''), IFNULL(value_excerpt,'')) FROM t_card_bin_import_error"),
                               ("payment", "SELECT CONCAT_WS('|', IFNULL(channel_response_code,''), IFNULL(channel_response_msg,'')) FROM t_payment_action"),
                               ("channel-connector", "SELECT CONCAT_WS('|', c.channel_callback_id, IFNULL(i.target_id,''), IFNULL(i.normalized_status,'')) "
                                                     "FROM t_channel_callback c LEFT JOIN t_channel_callback_item i ON i.channel_callback_id=c.channel_callback_id")):
        try:
            rows = t.h.sql(service, statement)
        except RuntimeError as failure:
            out[f"{service}: {statement[7:60]}"] = {"error": str(failure)[:160]}
            continue
        text = "\n".join(r[0] for r in rows)
        out[f"{service}: {statement[7:60]}"] = {"rows": len(rows), "hits": scan_text(text),
                                               "luhnCardLikeRuns": luhn_pans(text)}
    return out


def main():
    since = sys.argv[1]
    result = {"since": since, "terms": ["3ds.local.invalid", "acsSecret=", "S5ACS", "测试卡完整卡号 ×6"], "logs": logs(since),
              "mq": mq(), "tables": tables()}
    (HERE / "stage5-leak-scan.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
