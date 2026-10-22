"""阶段 7 mor-test 3DS AC-11 / BIN AC-11：由 verification/stage5_leak_scan.py 改写。

检索十个服务 `docker logs --since <发布开始>`、补偿表 Payload、BIN 导入错误明细、动作表与回调表中的跳转主机（参数传入）、`acsSecret=`、
本次测试卡号（完整 PAN）与任意通过 Luhn 校验的 13–19 位卡号形数字串。只输出命中计数，不输出命中内容。
mor-test 不建临时 MQ 捕获队列（阶段 7 不写 RabbitMQ）：MQ 只核对 payment.events 等队列消息计数，正文以日志与表检索代替。
用法：python3 accept_leak_scan.py <sinceUTC> [跳转主机 …]；写 accept-leak-scan.json。
"""
import re
import subprocess
import sys

import mt
from accept_3ds import CARDS
from accept_regression import SERVICES

FIXED_TERMS = ["acsSecret=", "acs_secret"] + list(CARDS.values())


def luhn_pans(text):
    """以 3–6 开头、13–19 位且通过 Luhn 的连续数字串（Snowflake ID 以 2 开头，不计入）。"""
    found = 0
    for run in re.findall(r"(?<!\d)[3-6]\d{12,18}(?!\d)", text):
        digits = [int(c) for c in reversed(run)]
        total = sum(d if i % 2 == 0 else (d * 2 - 9 if d * 2 > 9 else d * 2) for i, d in enumerate(digits))
        found += total % 10 == 0
    return found


def scan_text(text, terms):
    return {term[:6] + "…": text.count(term) for term in terms if text.count(term)}


def logs(since, terms):
    out = {}
    for svc in SERVICES:
        result = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{svc}-1"], capture_output=True, text=True)
        text = result.stdout + result.stderr
        out[svc] = {"lines": text.count("\n"), "hits": scan_text(text, terms), "luhnCardLikeRuns": luhn_pans(text)}
    return out


def tables(terms):
    out = {}
    for service, statement in (("payment", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("channel-connector", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("card", "SELECT IFNULL(payload_json,'') FROM t_compensation_event"),
                               ("card", "SELECT CONCAT_WS('|', IFNULL(error_message,''), IFNULL(value_excerpt,'')) FROM t_card_bin_import_error"),
                               ("payment", "SELECT CONCAT_WS('|', IFNULL(channel_response_code,''), IFNULL(channel_response_msg,'')) FROM t_payment_action"),
                               ("channel-connector", "SELECT CONCAT_WS('|', c.channel_callback_id, IFNULL(i.target_id,''), IFNULL(i.normalized_status,'')) "
                                                     "FROM t_channel_callback c LEFT JOIN t_channel_callback_item i ON i.channel_callback_id=c.channel_callback_id")):
        key = f"{service}: {statement[7:60]}"
        try:
            rows = mt.sql(service, statement)
        except RuntimeError as failure:
            out[key] = {"error": str(failure)[:160]}
            continue
        text = "\n".join(r[0] for r in rows)
        out[key] = {"rows": len(rows), "hits": scan_text(text, terms), "luhnCardLikeRuns": luhn_pans(text)}
    return out


def queues():
    return [q for q in mt.h.rabbit_queues("") if q[0].startswith(("payment.", "card.", "acquiring.", "channel-connector."))]


if __name__ == "__main__":
    since, *hosts = sys.argv[1:]
    terms = FIXED_TERMS + hosts
    mt.save("accept-leak-scan.json", "scan-" + mt.now(), {"since": since, "redirectHostsChecked": len(hosts),
                                                          "logs": logs(since, terms), "tables": tables(terms), "queues": queues()})
