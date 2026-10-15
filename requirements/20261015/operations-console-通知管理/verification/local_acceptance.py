"""阶段 5 接口验收：经本机 Nginx 运营入口（Gateway → BFF → Platform）走 AC-01～AC-07。

前置：本机 mor_platform_e2e 已执行索引 DDL、注册 DML、activation-local.sql 与 acceptance-fixture-local.sql。
只连接本机；不打印 Token 与凭据。验收中新建的模板编码以 E2E_NOTIFY_ 开头，结束后执行 acceptance-cleanup-local.sql。
用法：python3 -I local_acceptance.py
"""
import json
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

RUN = uuid.uuid4().hex[:6].upper()
TEMPLATE = f"E2E_NOTIFY_ORDER_{RUN}"
BASE = "/notification-admin/v1"
SENSITIVE = ["vlt_", "E2E-SECRET", "e2e-req-", "e2e-msg-", "10.00"]
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
    """轮询直到 probe 返回真；超时返回假。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return True
        time.sleep(2)
    return False


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body: ops.send(method, BASE + path, body, key=True)
    content = lambda subject, body, version: {"subjectTemplate": subject, "bodyTemplate": body,
                                              "expectedVersion": version, "reason": "阶段 5 联调"}
    status = lambda target, version: {"status": target, "expectedVersion": version, "reason": "阶段 5 联调"}

    # AC-01 新路由经 Gateway 快照生效后，管理员可访问
    check("AC-01", "Gateway 已加载通知管理路由，模板列表可访问",
          wait_until(lambda: status_of(lambda: get("/templates?page=1&size=10"))[0] == 200))
    navigation = ops.get("/authorization/v1/navigation")
    codes = json.dumps(navigation, ensure_ascii=False)
    check("AC-01", "导航含通知管理菜单与两个维护按钮",
          all(c in codes for c in ["OPERATIONS_NOTIFICATION_TEMPLATES", "OPERATIONS_NOTIFICATION_PROVIDERS",
                                    "OPERATIONS_NOTIFICATION_RECORDS", "OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE",
                                    "OPERATIONS_NOTIFICATION_PROVIDER_MANAGE"]))

    # AC-02 / AC-03 新建、内容校验、预览、启用
    created = send("POST", "/templates", {"templateCode": TEMPLATE, "reason": "阶段 5 联调"})
    check("AC-02", "新建模板为版本 1 且停用", created["version"] == 1 and get(f"/templates/{TEMPLATE}")["status"] == "DISABLED")
    check("AC-03", "没有语言内容不能启用（400）", status_of(lambda: send("PATCH", f"/templates/{TEMPLATE}/status", status("ACTIVE", 1)))[0] == 400)
    code, body = status_of(lambda: send("PUT", f"/templates/{TEMPLATE}/contents/zh-CN", content("付款\n成功", "订单 ${orderNo}", 0)))
    check("AC-03", f"标题换行返回 422 并定位字段（{[e.get('field') for e in body.get('errors', [])]}）",
          code == 422 and [e.get("field") for e in body.get("errors", [])] == ["subjectTemplate"])
    saved = send("PUT", f"/templates/{TEMPLATE}/contents/zh-CN", content("付款成功", "订单 ${orderNo} 已付款。", 0))
    check("AC-02", "保存中文内容返回版本 1", saved == {"code": "zh-CN", "version": 1})
    preview = send("POST", "/template-preview", {"subjectTemplate": "付款 ${orderNo}", "bodyTemplate": "金额 ${amount}",
                                                 "parameters": {"orderNo": "O-1"}})
    check("AC-03", "预览替换已提供参数并列出缺失项",
          preview["subject"] == "付款 O-1" and preview["missingParameters"] == ["amount"])
    enabled = send("PATCH", f"/templates/{TEMPLATE}/status", status("ACTIVE", 1))
    check("AC-02", "加内容后启用成功（版本 2）", enabled["version"] == 2 and get(f"/templates/{TEMPLATE}")["status"] == "ACTIVE")

    # AC-04 并发版本
    check("AC-04", "内容版本过期返回 409",
          status_of(lambda: send("PUT", f"/templates/{TEMPLATE}/contents/zh-CN", content("x", "y", 5)))[0] == 409)
    check("AC-04", "模板版本过期返回 409",
          status_of(lambda: send("PATCH", f"/templates/{TEMPLATE}/status", status("DISABLED", 1)))[0] == 409)

    # AC-05 路由规则与账号启停
    route = send("PUT", f"/templates/{TEMPLATE}/route", {"primaryProviderCode": "E2E_NOTIFY_EMAIL", "fallbackProviderCode": None,
                                                        "expectedVersion": 0, "reason": "阶段 5 联调"})
    check("AC-05", "新建路由（启用的邮件账号）成功", route["version"] == 1)
    code, _ = status_of(lambda: send("PUT", f"/templates/{TEMPLATE}/route", {"primaryProviderCode": "E2E_NOTIFY_OFF",
                                                                            "fallbackProviderCode": None, "expectedVersion": 1,
                                                                            "reason": "阶段 5 联调"}))
    check("AC-05", "路由指向停用账号被拒（400）", code == 400)
    code, _ = status_of(lambda: send("PUT", f"/templates/{TEMPLATE}/route", {"primaryProviderCode": "E2E_NOTIFY_EMAIL",
                                                                            "fallbackProviderCode": "E2E_NOTIFY_EMAIL",
                                                                            "expectedVersion": 1, "reason": "阶段 5 联调"}))
    check("AC-05", "主备相同被拒（400）", code == 400)
    providers = get("/providers?page=1&size=100")["items"]
    email = next((p for p in providers if p["providerCode"] == "E2E_NOTIFY_EMAIL"), None)
    check("AC-05", "账号列表显示未配置适配器与本模板的主账号引用",
          email is not None and email["adapterConfigured"] is False
          and {"templateCode": TEMPLATE, "role": "PRIMARY", "routeStatus": "ACTIVE"} in email["references"])
    off = next(p for p in providers if p["providerCode"] == "E2E_NOTIFY_OFF")
    on = send("PATCH", "/providers/E2E_NOTIFY_OFF/status", status("ACTIVE", off["version"]))
    back = send("PATCH", "/providers/E2E_NOTIFY_OFF/status", status("DISABLED", on["version"]))
    check("AC-05", "账号启用后再停用，版本依次递增", back["version"] == off["version"] + 2)
    route_off = send("PATCH", f"/templates/{TEMPLATE}/route/status", status("DISABLED", 1))
    check("AC-05", "停用路由成功", route_off["version"] == 2)

    # AC-06 发送记录
    now = datetime.now(timezone.utc)
    window = f"createdFrom={quote((now - timedelta(days=1)).isoformat().replace('+00:00', 'Z'))}&createdTo={quote((now + timedelta(minutes=5)).isoformat().replace('+00:00', 'Z'))}"
    raw = e2e_http.call(e2e_http.OPERATIONS, f"/api/operations{BASE}/requests?page=1&size=20&{window}&sourceSystem=e2e-notification-admin",
                        token=ops.token)
    item = raw["items"][0] if raw["items"] else {}
    check("AC-06", "按时间与来源查到合成记录，状态计数正确",
          raw["total"] == 1 and item.get("statusCounts") == {"SENT": 1, "FAILED": 1} and item.get("deliveryCount") == 2)
    check("AC-06", "按投递状态 FAILED 可筛出", get(f"/requests?page=1&size=20&{window}&deliveryStatus=FAILED&sourceSystem=e2e-notification-admin")["total"] == 1)
    check("AC-06", "时间跨度超过 31 天被拒（400）",
          status_of(lambda: get(f"/requests?createdFrom=2026-01-01T00:00:00Z&createdTo=2026-10-01T00:00:00Z"))[0] == 400)
    detail = get(f"/requests/{item.get('requestId')}")
    attempts = [len(d["attempts"]) for d in detail["deliveries"]]
    check("AC-06", "详情含两个投递、三次尝试与参数名", attempts == [1, 2] and detail["parameterKeys"] == ["amount", "name"])
    text = json.dumps([raw, detail], ensure_ascii=False)
    check("AC-06", "列表与详情不含 Token、参数值、供应商请求号与消息标识", not any(x in text for x in SENSITIVE))

    # AC-07 审计
    def audited():
        events = ops.get(f"/audit/v1/events?page=1&size=20&resourceType=NOTIFICATION_TEMPLATE&resourceId={TEMPLATE}")
        actions = {e.get("actionCode") for e in events.get("items", [])}
        return {"OPERATIONS_NOTIFICATION_TEMPLATE_CREATE", "OPERATIONS_NOTIFICATION_TEMPLATE_STATUS_CHANGE"} <= actions
    check("AC-07", "新建与启停模板产生审计事件", wait_until(audited, 15))

    failed = [r for r in results if not r[2]]
    print(f"\n共 {len(results)} 项，通过 {len(results) - len(failed)}，失败 {len(failed)}；本次模板 {TEMPLATE}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
