"""阶段 7 mor-test P-1（acceptance-plan 第 3 节）：Payment 终止原因 ORDER_EXPIRED 与按来源查询。

一次性探针容器以 acquiring-service 身份直连 Payment 内部接口；复用 3DS 阶段 7 的 mt.py / accept_3ds.py（import，不复制）。
sourceId 为随机合成正整数（接口要求无前导零的十进制 long，不能用 exp- 前缀），不关联任何订单；运行标识 exp-<run> 只写入结果。
只读 SELECT 核对 Intent 状态与版本。结果写同目录 accept-terminate.json。
子命令：
  p1        全部步骤
  ac02      复用 accept_3ds.ac02（3DS AC-02 回归），结果写同目录 accept-3ds-regression.json
  leak <sinceUTC>  acquiring、payment docker logs 泄漏检索（只计数）：chk_ 令牌形串、收银台主机、测试卡号、Luhn 卡号形串、
            whsec_ / ck- 凭证形串；WARN 按 logger 计数，过期相关 WARN 只记字段键名。写 leak-scan.json
  checks    只读：BIN 生效版本、payment / acquiring 队列消费者与 DLQ、XXL 心跳与任务 4、9、23 最近日志，写 accept-3ds-regression.json
"""
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "payment-3DS全链路/release/acceptance"))
import mt  # noqa: E402
import accept_3ds as a3  # noqa: E402

mt.HERE = HERE  # 结果写本目录，不覆盖 3DS 目录下的文件
OUT = "accept-terminate.json"
OTHER_CLIENT_ID = "236087245667409999"  # 合成的另一个 Client ID（与 Intent 归属不一致）
RUN = "exp-" + str(random.randrange(10 ** 6, 10 ** 7))


def intent_row(intent_id):
    rows = a3.psql(f"SELECT intent_status, version, source_type, source_id FROM t_payment_intent WHERE payment_intent_id={intent_id}")
    return dict(zip(("status", "version", "sourceType", "sourceId"), rows[0])) if rows else None


def create(source_id):
    status, body = a3.pay_api("POST", "/payment-execution/v1/intents", {
        "clientId": a3.CLIENT_ID, "sourceType": "COMMERCE_ORDER", "sourceId": source_id,
        "entityNo": a3.ENTITY_NO, "clientPricingVersionId": "1", "industryCode": "DIGITAL_GOODS", "mccCode": "5999",
        "consumerCountryCode": "AE", "amount": "10.00", "currency": "USD",
        "expiresAt": (datetime.now(timezone.utc) + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%SZ")})
    return status, body


def terminate(intent_id, reason, client_id=a3.CLIENT_ID):
    return a3.pay_api("POST", f"/payment-execution/v1/intents/{intent_id}/terminate", {"clientId": client_id, "reason": reason})


def by_source(source_type, source_id, client_id=a3.CLIENT_ID):
    return a3.pay_api("GET", f"/payment-execution/v1/intents/by-source/{source_type}/{source_id}?clientId={client_id}")


def brief(body):
    keys = ("intentId", "status", "decision", "code", "title", "detail", "errors")
    return {k: body.get(k) for k in keys if isinstance(body, dict) and k in body}


def step(steps, name, expect, ok, http, body, extra=None):
    steps.append({"step": name, "expect": expect, "result": "PASS" if ok else "FAIL", "http": http, "body": brief(body),
                  **(extra or {})})


def p1():
    steps = []
    source_a = str(random.randrange(10 ** 14, 10 ** 15))
    http, body = create(source_a)
    intent_a = body.get("intentId")
    row = intent_row(intent_a) if intent_a else None
    step(steps, "建 Intent A", "201，OPEN", http in (200, 201) and row and row["status"] == "OPEN", http, body, {"db": row})

    http, body = by_source("COMMERCE_ORDER", source_a)
    step(steps, "按来源查询 A", "200，intentId=A", http == 200 and body.get("intentId") == intent_a, http, body)

    http, body = by_source("COMMERCE_ORDER", source_a, OTHER_CLIENT_ID)
    step(steps, "按来源查询，另一个 clientId", "404 NOT_FOUND", http == 404 and body.get("code") == "NOT_FOUND", http, body)

    missing = str(random.randrange(10 ** 14, 10 ** 15))
    http, body = by_source("COMMERCE_ORDER", missing)
    step(steps, "按来源查询，不存在的 sourceId", "404 NOT_FOUND", http == 404 and body.get("code") == "NOT_FOUND", http, body)

    http, body = by_source("commerce_order", source_a)
    step(steps, "按来源查询，小写 sourceType", "400", http == 400, http, body)

    http, body = terminate(intent_a, "ORDER_EXPIRED")
    row = intent_row(intent_a)
    step(steps, "终止 A，ORDER_EXPIRED", "200 TERMINATED / EXPIRED；库内 EXPIRED",
         http == 200 and body.get("decision") == "TERMINATED" and body.get("status") == "EXPIRED" and row["status"] == "EXPIRED",
         http, body, {"db": row})

    http, body = terminate(intent_a, "ORDER_EXPIRED")
    again = intent_row(intent_a)
    step(steps, "重复终止 A", "200 ALREADY_TERMINATED，版本不变",
         http == 200 and body.get("decision") == "ALREADY_TERMINATED" and again["version"] == row["version"], http, body,
         {"db": again, "versionBefore": row["version"]})

    source_b = str(random.randrange(10 ** 14, 10 ** 15))
    http, body = create(source_b)
    intent_b = body.get("intentId")
    step(steps, "建 Intent B", "201，OPEN", http in (200, 201) and bool(intent_b), http, body, {"db": intent_row(intent_b)})

    before = intent_row(intent_b)
    http, body = terminate(intent_b, "ORDER_CANCELLED", OTHER_CLIENT_ID)
    after = intent_row(intent_b)
    step(steps, "另一个 clientId 终止 B", "400 BUSINESS_CHECK_FAILED，Intent 不变",
         http == 400 and body.get("code") == "BUSINESS_CHECK_FAILED" and after == before, http, body, {"db": after})

    http, body = terminate(intent_b, "EXPIRED")
    after = intent_row(intent_b)
    step(steps, "终止 B，reason=EXPIRED（非法）", "422，Intent 不变", http == 422 and after == before, http, body, {"db": after})

    http, body = terminate(intent_b, "ORDER_CANCELLED")
    row = intent_row(intent_b)
    step(steps, "终止 B，ORDER_CANCELLED", "200 TERMINATED / CANCELLED；库内 CANCELLED",
         http == 200 and body.get("decision") == "TERMINATED" and body.get("status") == "CANCELLED" and row["status"] == "CANCELLED",
         http, body, {"db": row})

    verdict = "PASS" if all(s["result"] == "PASS" for s in steps) else "FAIL"
    mt.save(OUT, "P-1-" + mt.now(), {"run": RUN, "clientId": a3.CLIENT_ID, "otherClientId": OTHER_CLIENT_ID,
                                      "intentA": intent_a, "sourceA": source_a, "intentB": intent_b, "sourceB": source_b,
                                      "verdict": verdict, "steps": steps})


def ac02():
    a3.OUT = "accept-3ds-regression.json"
    a3.ac02()


def xxl(statement):
    """XXL 库只读 SELECT（root 口令只经 MYSQL_PWD 传入，与 scripts/snapshot.sh 相同）。"""
    import os
    import subprocess
    if not statement.lstrip().upper().startswith("SELECT"):
        raise SystemExit("只允许只读 SQL")
    env = (HERE.parents[4] / "test-environment/.env.test").read_text().splitlines()
    password = next(line.split("=", 1)[1] for line in env if line.startswith("MOR_MYSQL_ROOT_PASSWORD="))
    result = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD=" + password, "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                             "--default-character-set=utf8mb4", "xxl_job", "-e", statement], capture_output=True, text=True,
                            env={**os.environ})
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip()[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def checks():
    queues = [q for q in mt.h.rabbit_queues("") if q[0].startswith(("payment.channel-connector.", "acquiring.payment."))
              or (("dlq" in q[0].lower() or "dead" in q[0].lower()) and q[0].startswith(("payment.", "acquiring.")))]
    mt.save("accept-3ds-regression.json", "checks-" + mt.now(), {
        "binActive": mt.sql("card", "SELECT version_no, status, IFNULL(active_slot,'') FROM t_card_bin_library_version "
                                    "WHERE active_slot IS NOT NULL OR status='ACTIVE'"),
        "queues": queues,
        "xxlRegistry": xxl("SELECT registry_key, registry_value, update_time, TIMESTAMPDIFF(SECOND, update_time, NOW()) FROM "
                           "xxl_job_registry WHERE registry_key IN ('acquiring-service','payment-service')"),
        "xxlGroups": xxl("SELECT id, app_name, IFNULL(address_list,'') FROM xxl_job_group WHERE id IN (4,9)"),
        "xxlJobs": xxl("SELECT id, job_group, executor_handler, trigger_status FROM xxl_job_info WHERE id IN (4,9,23)"),
        "xxlLogs30m": xxl("SELECT job_id, COUNT(*), SUM(trigger_code=200), SUM(handle_code=200), SUM(handle_code NOT IN (0,200)), "
                          "MAX(trigger_time) FROM xxl_job_log WHERE job_id IN (4,9,23) AND trigger_time >= NOW() - INTERVAL 30 MINUTE "
                          "GROUP BY job_id"),
        "orderExpiryLast": xxl("SELECT trigger_time, handle_code, LEFT(IFNULL(handle_msg,''),300) FROM xxl_job_log WHERE job_id=23 "
                               "ORDER BY id DESC LIMIT 3")})


def leak(since):
    import re
    import subprocess
    from accept_leak_scan import luhn_pans
    out = {}
    for svc in ("acquiring-service", "payment-service"):
        result = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{svc}-1"], capture_output=True, text=True)
        text = result.stdout + result.stderr
        warns = [line for line in text.splitlines() if " WARN " in line]
        by_logger = {}
        for line in warns:
            match = re.search(r"\] ([\w.$]+)\s+:", line)
            key = match.group(1) if match else "?"
            by_logger[key] = by_logger.get(key, 0) + 1
        expiry = [line for line in warns if re.search(r"(?i)expir|过期|到期|pending|reversal", line)]
        out[svc] = {"lines": text.count("\n"),
                    "chkTokenLike": len(re.findall(r"chk_[A-Za-z0-9]{30}", text)),
                    "chkPrefix": text.count("chk_"),
                    "checkoutHost": text.count("localhost:28443"),
                    "testPans": sum(text.count(pan) for pan in a3.CARDS.values()),
                    "luhnCardLikeRuns": luhn_pans(text),
                    "whsecPrefix": text.count("whsec_"),
                    "ckCredentialLike": len(re.findall(r"(?<![\w-])ck-[A-Za-z0-9_-]{16,}", text)),
                    "errorLines": sum(" ERROR " in line for line in text.splitlines()),
                    "warnLines": len(warns), "warnByLogger": by_logger,
                    "expiryWarnLines": len(expiry),
                    "expiryWarnFieldKeys": sorted({k for line in expiry for k in re.findall(r"(\w+)=", line)})}
    mt.save("leak-scan.json", "scan-" + mt.now(), {"since": since, "services": out})


if __name__ == "__main__":
    {"p1": p1, "ac02": ac02, "checks": checks, "leak": lambda: leak(sys.argv[2])}[sys.argv[1]]()
