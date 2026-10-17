"""阶段 5 接口验收：经本机 Nginx 运营入口（Gateway → BFF → Platform）验证事件队列配置、拉取状态与发送记录事件字段。

前置：本机 mor_platform_e2e 已执行 ddl.sql 与 acceptance-fixture-local.sql；本机 Platform 以
notification.ses.events.enabled=true、poll-interval=10s、endpoint=http://127.0.0.1:9 启动（不访问真实 AWS）。
只连接本机；不打印 Token 与凭据。新建账号 E2E_SES_EVT_LIVE，结束后执行 acceptance-cleanup-local.sql。
用法：python3 -I local_acceptance.py
"""
import json
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

BASE = "/notification-admin/v1"
LIVE = "E2E_SES_EVT_LIVE"
QUEUE = "https://sqs.us-east-1.amazonaws.com/000000000000/e2e-ses-events"
FAKE_KEY = "AKIAE2ESESEVENTS0001"
FAKE_SECRET = "e2e-fake-secret-not-a-real-key-000000000000"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    """执行调用，返回 (HTTP 状态, 响应体)；成功统一记 200。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def wait_until(probe, seconds=60):
    """轮询直到 probe 返回真值；超时返回 None。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        value = probe()
        if value:
            return value
        time.sleep(2)
    return None


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body: ops.send(method, BASE + path, body, key=True)
    providers = lambda: {p["providerCode"]: p for p in get("/providers?page=1&size=100")["items"]}
    base = {"region": "us-east-1", "fromAddress": "no-reply@e2e.test", "configurationSet": "", "reason": "阶段 5 联调"}

    # AC-08 队列 URL 的格式校验、录入、清空
    code, body = status_of(lambda: send("POST", "/providers", {**base, "providerCode": LIVE, "providerType": "AMAZON_SES",
                                                              "eventQueueUrl": "https://example.com/queue"}))
    fields = [e.get("field") for e in body.get("errors", [])]
    check("AC-08", f"非 SQS 地址被拒（{code}，字段 {fields}）", code in (400, 422) and fields == ["eventQueueUrl"])
    code, body = status_of(lambda: send("POST", "/providers", {**base, "providerCode": LIVE, "providerType": "AMAZON_SES",
                                                              "eventQueueUrl": "   "}))
    check("AC-08", f"只有空格被拒（{code}）", code in (400, 422))
    send("POST", "/providers", {**base, "providerCode": LIVE, "providerType": "AMAZON_SES", "eventQueueUrl": QUEUE})
    live = providers()[LIVE]
    check("AC-08", "新建账号带队列 URL，尚无拉取记录",
          live["eventQueueUrl"] == QUEUE and live["eventPollTime"] is None and live["eventPollError"] is None)

    # AC-06 无密钥不拉取；录入（合成）密钥后走真实链路，终端不可达记为固定错误码，不影响账号版本
    time.sleep(12)
    check("AC-06", "无密钥的账号不被拉取", providers()[LIVE]["eventPollError"] is None)
    version = providers()[LIVE]["version"]
    send("PUT", f"/providers/{LIVE}/credential", {"accessKeyId": FAKE_KEY, "secretAccessKey": FAKE_SECRET,
                                                 "expectedVersion": version, "reason": "阶段 5 联调"})
    version = providers()[LIVE]["version"]
    failed = wait_until(lambda: (lambda p: p if p["eventPollError"] else None)(providers()[LIVE]), 40)
    check("AC-06", f"终端不可达记为拉取失败（{failed and failed['eventPollError']}）",
          failed is not None and failed["eventPollError"] == "SQS_UNAVAILABLE")
    time.sleep(12)
    after = providers()[LIVE]
    check("AC-06", f"多轮拉取后账号版本不变（{version} → {after['version']}）", after["version"] == version)
    text = json.dumps(after, ensure_ascii=False)
    check("AC-09", "账号响应不含密钥", FAKE_SECRET not in text and FAKE_KEY not in text)

    # AC-08 清空后不再拉取，列表显示未配置；再录回供页面截图
    send("PUT", f"/providers/{LIVE}", {**base, "eventQueueUrl": "", "expectedVersion": version})
    cleared = providers()[LIVE]
    check("AC-08", "空串清空队列 URL，版本 +1", cleared["eventQueueUrl"] is None and cleared["version"] == version + 1)
    events = ops.get(f"/audit/v1/events?page=1&size=10&resourceType=NOTIFICATION_PROVIDER&resourceId={LIVE}")["items"]
    latest = ops.get(f"/audit/v1/events/{events[0]['auditEventId']}") if events else {}
    change = next((c for c in latest.get("changes", []) if c["key"] == "EVENT_QUEUE_URL"), None)
    check("AC-08", "清空队列 URL 记入审计（修改前后值）", change == {"key": "EVENT_QUEUE_URL", "label": "EVENT_QUEUE_URL",
                                                         "before": [QUEUE], "after": [""]})
    send("PUT", f"/providers/{LIVE}", {**base, "eventQueueUrl": QUEUE, "expectedVersion": cleared["version"]})

    # AC-12 合成账号的三种静态状态
    all_ = providers()
    ok, wait, none = all_["E2E_SES_EVT_OK"], all_["E2E_SES_EVT_WAIT"], all_["E2E_SES_EVT_NONE"]
    check("AC-12", "正常接收：有最近拉取与最近事件时间", ok["eventPollTime"] and ok["eventReceiveTime"] and not ok["eventPollError"])
    check("AC-12", "等待首次拉取：有队列无拉取时间", wait["eventQueueUrl"] and wait["eventPollTime"] is None)
    check("AC-12", "未配置：无队列 URL", none["eventQueueUrl"] is None)

    # AC-13 发送记录：投递状态筛选与尝试事件字段
    now = datetime.now(timezone.utc)
    window = (f"createdFrom={quote((now - timedelta(days=1)).isoformat().replace('+00:00', 'Z'))}"
              f"&createdTo={quote((now + timedelta(minutes=5)).isoformat().replace('+00:00', 'Z'))}")
    scope = f"page=1&size=20&{window}&sourceSystem=e2e-ses-events"
    listed = get(f"/requests?{scope}")
    item = listed["items"][0] if listed["items"] else {}
    check("AC-13", f"记录状态计数（{item.get('statusCounts')}）",
          item.get("statusCounts") == {"DELIVERED": 2, "FAILED": 1, "SENT": 1})
    check("AC-13", "按投递状态「已送达」可筛出", get(f"/requests?{scope}&deliveryStatus=DELIVERED")["total"] == 1)
    detail = get(f"/requests/{item.get('requestId')}")
    attempts = {a["attemptId"]: a for d in detail["deliveries"] for a in d["attempts"]}
    a = lambda n: attempts.get(str(980000000000002040 + n)) or attempts.get(980000000000002040 + n) or {}
    check("AC-02", "送达尝试显示 DELIVERED 与时间", a(1).get("eventOutcome") == "DELIVERED" and a(1).get("eventOutcomeTime"))
    check("AC-03", "硬退信显示分类", a(2).get("eventOutcome") == "HARD_BOUNCE" and a(2).get("eventOutcomeCode") == "Permanent/General")
    check("AC-03", "软退信与投诉同时显示",
          a(3).get("eventOutcome") == "SOFT_BOUNCE" and a(3).get("complaintCode") == "abuse" and a(3).get("complaintTime"))
    check("AC-04", "结果未知的旧尝试经事件确认：来源 PROVIDER_EVENT",
          a(4).get("resolutionSource") == "PROVIDER_EVENT" and a(4).get("eventOutcome") == "DELIVERED")
    check("AC-04", "当前尝试无事件字段", a(5).get("eventOutcome") is None and a(5).get("complaintCode") is None)
    text = json.dumps([listed, detail], ensure_ascii=False)
    check("AC-09", "记录响应不含地址 Token、参数值与消息标识", not any(x in text for x in ["vlt_e2eses", "E2E-SECRET", "e2e-ses-msg"]))

    failed_items = [r for r in results if not r[2]]
    print(f"\n合计 {len(results)} 项，失败 {len(failed_items)} 项")
    sys.exit(1 if failed_items else 0)


if __name__ == "__main__":
    main()
