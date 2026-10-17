"""阶段 7 mor-test 真实链路验收（D2 A）：SES → SNS → SQS → Platform 拉取 → 发送记录回写。

前置：mor-test 已部署本需求镜像并开启 NOTIFICATION_SES_EVENTS_ENABLED；SES_PRIMARY 已填队列 URL 且拉取正常；
Platform 叠加 release/compose-acceptance.yaml（临时调用方 e2esesevents → 模板 E2E_SES_EVT_TEST）。
经运营入口新建模板与路由，从 test 内网提交一条通知发给 3 个 SES 模拟地址，等待事件回写后核对。
只连接 mor-test；不打印 Token 与凭据。结束后执行 acceptance-cleanup-test.sql。
用法：E2E_TARGET=test E2E_PLATFORM_IMAGE=<本次 Platform 镜像> python3 -I test_acceptance.py
"""
import json
import sys
import time
import uuid
from pathlib import Path

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

assert e2e_http.TARGET == "test", "只用于 mor-test：设置 E2E_TARGET=test"
BASE = "/notification-admin/v1"
TEMPLATE = "E2E_SES_EVT_TEST"
CALLER = "e2esesevents"
PROVIDER = "SES_PRIMARY"
SIMULATOR = ["success@simulator.amazonses.com", "bounce@simulator.amazonses.com", "complaint@simulator.amazonses.com"]
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def wait_until(probe, seconds, interval=10):
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        value = probe()
        if value:
            return value
        time.sleep(interval)
    return None


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body: ops.send(method, BASE + path, body, key=True)
    provider = lambda: next(p for p in get("/providers?page=1&size=10")["items"] if p["providerCode"] == PROVIDER)

    before = provider()
    check("AC-06", f"拉取正常（队列已配置，最近拉取 {before['eventPollTime']}，错误 {before['eventPollError']}）",
          before["eventQueueUrl"] and before["eventPollTime"] and not before["eventPollError"])

    # 模板、语言内容、路由
    send("POST", "/templates", {"templateCode": TEMPLATE, "reason": "SES 投递事件 test 验收"})
    send("PUT", f"/templates/{TEMPLATE}/contents/zh-CN", {"subjectTemplate": "Code-MoR 投递事件验收 ${run}",
         "bodyTemplate": "这是一封发往 SES 模拟地址的验收邮件（${run}）。", "expectedVersion": 0, "reason": "SES 投递事件 test 验收"})
    send("PATCH", f"/templates/{TEMPLATE}/status", {"status": "ACTIVE", "expectedVersion": 1, "reason": "SES 投递事件 test 验收"})
    route = send("PUT", f"/templates/{TEMPLATE}/route", {"primaryProviderCode": PROVIDER, "fallbackProviderCode": None,
                                                        "expectedVersion": 0, "reason": "SES 投递事件 test 验收"})
    check("AC-11", "验收模板启用、路由到 SES_PRIMARY", route["version"] >= 1)

    # 从 test 内网提交
    run = uuid.uuid4().hex[:8]
    body = {"sourceEventKey": f"ses-events-acceptance:{run}", "templateCode": TEMPLATE, "defaultLocale": "zh-CN",
            "defaultParameters": {"run": run}, "recipients": [{"email": e} for e in SIMULATOR]}
    status, text = e2e_http.platform_call("POST", "/notifications/v1/requests",
                                          {"Content-Type": "application/json", "X-Caller-Service": CALLER}, json.dumps(body))
    accepted = json.loads(text) if status < 300 else {}
    check("AC-11", f"内部提交受理（{status}，{accepted.get('acceptedCount')} 个收件人）", status < 300 and accepted.get("acceptedCount") == 3)
    request_id = accepted.get("requestId")

    # 等待发送与事件回写（投诉事件通常晚于送达数十秒）
    def settled():
        detail = get(f"/requests/{request_id}")
        attempts = [a for d in detail["deliveries"] for a in d["attempts"]]
        outcomes = sorted(a.get("eventOutcome") or "-" for a in attempts)
        complaints = [a for a in attempts if a.get("complaintCode")]
        return detail if outcomes == ["DELIVERED", "DELIVERED", "HARD_BOUNCE"] and complaints else None
    detail = wait_until(settled, 600) if request_id else None
    if detail is None and request_id:
        detail = get(f"/requests/{request_id}")
        print("最终状态:", json.dumps([[d["status"], [{k: a.get(k) for k in ("status", "eventOutcome", "eventOutcomeCode",
              "complaintCode")} for a in d["attempts"]]] for d in detail["deliveries"]], ensure_ascii=False))
    deliveries = detail["deliveries"] if detail else []
    by_outcome = {}
    for d in deliveries:
        for a in d["attempts"]:
            by_outcome.setdefault(a.get("eventOutcome"), []).append((d, a))
    delivered = [(d, a) for d, a in by_outcome.get("DELIVERED", []) if not a.get("complaintCode")]
    bounced = by_outcome.get("HARD_BOUNCE", [])
    complained = [(d, a) for d, a in by_outcome.get("DELIVERED", []) if a.get("complaintCode")]
    check("AC-02", "success@ 送达 → 投递已送达", len(delivered) == 1 and delivered[0][0]["status"] == "DELIVERED")
    check("AC-03", f"bounce@ 硬退信 → 投递失败（{bounced and bounced[0][1].get('eventOutcomeCode')}）",
          len(bounced) == 1 and bounced[0][0]["status"] == "FAILED" and bounced[0][1].get("eventOutcomeCode"))
    check("AC-03", f"complaint@ 送达并记下投诉（{complained and complained[0][1].get('complaintCode')}）",
          len(complained) == 1 and complained[0][0]["status"] == "DELIVERED" and complained[0][1].get("complaintTime"))

    after = provider()
    check("AC-06", f"账号记下最近事件时间、版本不变（{before['version']} → {after['version']}）",
          after["eventReceiveTime"] and not after["eventPollError"] and after["version"] == before["version"])
    text = json.dumps(detail, ensure_ascii=False)
    check("AC-09", "记录响应不含收件地址", "simulator.amazonses.com" not in text)
    print("requestId:", request_id)

    failed = [r for r in results if not r[2]]
    print(f"\n合计 {len(results)} 项，失败 {len(failed)} 项")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
