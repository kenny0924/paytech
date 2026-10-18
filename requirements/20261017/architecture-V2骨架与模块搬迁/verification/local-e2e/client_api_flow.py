"""阶段 5 本机全链路（任务卡 14）：Client API 下单 / 取消 / 真实支付 / 退款 + Webhook（由 20261017 commerce-ClientAPI测试环境联通 改写）。

链路：入驻批准的验收 Client → Client Console 自助凭证（ck-）与 Webhook 端点（本机接收端 127.0.0.1）
→ https://127.0.0.1:18445/api/v1/*（Nginx CLIENT_API → Gateway 凭证 / Scope / Idempotency-Key → acquiring-service）
→ acquiring 补偿任务 → Platform 受理（调用方 acquiring-service）→ 签名投递 → 本机接收端。
  AC-01 拒绝    无凭证 / 错凭证 / 只读凭证缺 Scope / POST 缺 Idempotency-Key（Gateway）
  AC-01/02     order.paid：真实卡支付（webhook_flow.paid_order：Hosted Checkout Customer API → Payment → 合成 PayerMax），
               PaymentSucceeded v2 真实到 acquiring 与 card；已支付订单再次支付被拒、不产生第二个事件
  AC-03        order.cancelled（含重复取消）
  AC-04        refund.created（含同一 clientRefundId 重放）及退款查询；RefundSucceeded 为已知缺口（GAP，见 webhook_flow.py）
  幂等         Gateway 请求幂等默认验证（非法 Key、同 Key 重放、换 Body / 换接口复用、并发同 Key、凭证轮换后重放、错误响应重放）
与 mor-test 版的差异：去掉 --outage（本机无 XXL-Job 调度中心，补偿扫描不会运行；Platform 是宿主机进程，不暂停）；
  去掉 --idempotency 开关（路由一开始即 REQUIRED）；接收端为本机线程，不读 test 落盘目录；库为 mor_acquiring_v2e2e。
前置：run-service.sh start all；sql/50、sql/51 已执行；依次 risk_setup.py、channel_setup.py、register_client_api.py。
不打印凭证、签名密钥、卡号或口令。用法：python3 client_api_flow.py
"""
import concurrent.futures
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import developer_flow as dev  # noqa: E402
import webhook_flow as wf  # noqa: E402
import workspace_flow as ws_flow  # noqa: E402
from acceptance_flow import RESULTS, Stop, problem  # noqa: E402
from e2e_http import CLIENT_API, TLS, Failure  # noqa: E402

API = CLIENT_API + "/api/v1"
SCOPES = ["checkouts.write", "orders.read", "orders.cancel", "refunds.read", "refunds.write"]
record = wf.record
STATE = {"credential": None}


def client_api(method, path, client_id=None, body=None, credential="default", idempotency=True, key=None, raw=None):
    """经 Client API 正式入口调用；client_id 仅为与 webhook_flow 签名兼容，身份来自凭证。
    key：指定 Idempotency-Key（默认每次随机）；raw：传入 dict 时写入原始响应文本与响应头，供重放逐字比对。"""
    headers = {"Accept": "application/json", "X-Request-Id": "req-" + uuid.uuid4().hex[:16]}
    secret = STATE["credential"] if credential == "default" else credential
    if secret:
        headers["Authorization"] = "Bearer " + secret
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if method == "POST":
        if idempotency:
            headers["Idempotency-Key"] = key or ("idem-" + uuid.uuid4().hex)
        data = data if data is not None else b""
    request = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30, context=TLS) as response:
            text = response.read().decode()
            if raw is not None:
                raw.update(text=text, headers=dict(response.headers), status=response.status)
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        if raw is not None:
            raw.update(text=text, headers=dict(failure.headers), status=failure.code)
        try:
            return failure.code, json.loads(text)
        except ValueError:
            return failure.code, {"raw": text[:300]}


_compare = wf.compare


def compare(label, obj, expected):
    """refund.created 冻结在创建时（PENDING、version 1）；退款提交任务若已把退款推进到 PROCESSING（version 2），
    把这三个字段还原为创建时的值再逐字比对，与 acquiring auto 测试一致。"""
    if label.startswith("AC-04 refund.created"):
        current = {field: actual for field, actual, _ in expected}
        if current.get("refundStatus") == "PROCESSING" and str(current.get("version")) == "2":
            record("AC-04 refund.created 冻结创建时状态（PENDING、version 1）",
                   obj.get("refundStatus") == "PENDING" and obj.get("version") == 1,
                   f"event={obj.get('refundStatus')}/v{obj.get('version')} current=PROCESSING/v2")
            expected = [(f, a, k) for f, a, k in expected if f not in ("refundStatus", "version", "updatedAt")]
            expected.append(("updatedAt", obj.get("createdAt"), "instant"))
    return _compare(label, obj, expected)


wf.commerce = client_api
wf.compare = compare


# ---------- 验收步骤 ----------

def create_credentials(client):
    status, _, created = dev.raw(client, dev.DEV + "/credentials", "POST",
                                 {"name": "Client API acceptance", "scopeCodes": SCOPES, "validityDays": 30})
    secret = (created or {}).get("secret") or ""
    record("自助创建凭证（5 个 Scope）", status == 201 and secret.startswith("ck-"),
           f"HTTP {status} keyPrefix={((created or {}).get('credential') or {}).get('keyPrefix')} code={(created or {}).get('code')}")
    status, _, limited = dev.raw(client, dev.DEV + "/credentials", "POST",
                                 {"name": "Client API read-only", "scopeCodes": ["orders.read"], "validityDays": 30})
    record("自助创建只读凭证（orders.read）", status == 201, f"HTTP {status}")
    if not secret:
        raise Stop("凭证未创建")
    STATE["credential"] = secret
    return (limited or {}).get("secret") or ""


def rejections(read_only, order_no):
    status, body = client_api("GET", f"/orders/{order_no}", credential=None)
    record("AC-01 无凭证被 Gateway 拒绝", status == 401, f"HTTP {status} code={(body or {}).get('code')}")
    wrong = STATE["credential"][:-2] + ("AA" if not STATE["credential"].endswith("AA") else "BB")
    status, body = client_api("GET", f"/orders/{order_no}", credential=wrong)
    record("AC-01 错凭证被拒绝", status == 401, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("GET", f"/orders/{order_no}", credential=read_only)
    record("AC-01 只读凭证可查单", status == 200, f"HTTP {status}")
    status, body = client_api("POST", f"/orders/{order_no}/cancel", credential=read_only)
    record("AC-01 缺 Scope（orders.cancel）被拒绝", status == 403, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("POST", f"/orders/{order_no}/cancel", idempotency=False)
    record("AC-01 幂等：POST 缺 Idempotency-Key 由 Gateway 拒绝",
           status == 400 and (body or {}).get("code") == "IDEMPOTENCY_KEY_REQUIRED", f"HTTP {status} code={(body or {}).get('code')}")


def queries(order_no, client_order_id):
    status, order = client_api("GET", f"/orders/{order_no}")
    record("AC-02 查单", status == 200 and order.get("orderNo") == order_no, f"HTTP {status} status={(order or {}).get('orderStatus')}")
    status, found = client_api("GET", f"/orders?clientOrderId={urllib.request.quote(client_order_id)}")
    record("AC-02 按 clientOrderId 查单", status == 200 and (found or {}).get("orderNo") == order_no, f"HTTP {status}")
    status, body = client_api("GET", "/orders/ord_doesnotexist0000000000000000")
    record("AC-02 不存在 / 他人订单 404", status == 404, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("GET", f"/orders/{order_no}?unknown=1")
    record("Gateway Query 白名单：未知参数被拒", 400 <= status < 500, f"HTTP {status} code={(body or {}).get('code')}")


def checkout_body(label):
    return {"clientOrderId": f"idem-{label}-{uuid.uuid4().hex[:8]}", "currency": "USD", "consumerCountryCode": "US",
            "items": [{"clientItemId": "line-1", "name": "Premium subscription", "productTypeCode": "SERVICE",
                       "taxCategoryCode": "STANDARD", "quantity": "1", "unitAmount": "10.00"}]}


def orders_with_client_order_id(client_order_id):
    return int(wf.acquiring_sql(f"SELECT COUNT(*) FROM t_commerce_order WHERE client_order_id='{client_order_id}'")[0][0])


def idempotency_flow(client):
    """Gateway 处理 Idempotency-Key（20261017 gateway-请求幂等 AC-01—AC-07）。"""
    status, body = client_api("POST", "/checkouts", body=checkout_body("bad"), key="-bad key")
    record("幂等 AC-01 非法 Key 400 INVALID_IDEMPOTENCY_KEY", status == 400 and (body or {}).get("code") == "INVALID_IDEMPOTENCY_KEY",
           f"HTTP {status} code={(body or {}).get('code')}")
    key, request = "idem-" + uuid.uuid4().hex, checkout_body("replay")
    first, second = {}, {}
    s1, b1 = client_api("POST", "/checkouts", body=request, key=key, raw=first)
    s2, _ = client_api("POST", "/checkouts", body=request, key=key, raw=second)
    record("幂等 AC-02 同 Key 同请求重放：状态、响应体、Location 逐字一致，新 Request ID",
           s1 == 201 and s2 == 201 and first["text"] == second["text"]
           and first["headers"].get("Location") == second["headers"].get("Location")
           and first["headers"].get("X-Request-Id") != second["headers"].get("X-Request-Id"),
           f"HTTP {s1}/{s2} sameBody={first.get('text') == second.get('text')} "
           f"sameLocation={first['headers'].get('Location') == second['headers'].get('Location')}")
    record("幂等 AC-02 acquiring 只建 1 单", orders_with_client_order_id(request["clientOrderId"]) == 1,
           f"orders={orders_with_client_order_id(request['clientOrderId'])}")
    changed = dict(request, items=[dict(request["items"][0], unitAmount="11.00")])
    status, body = client_api("POST", "/checkouts", body=changed, key=key)
    record("幂等 AC-03 同 Key 换 Body 409 IDEMPOTENCY_KEY_REUSED", status == 409 and (body or {}).get("code") == "IDEMPOTENCY_KEY_REUSED",
           f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("POST", f"/orders/{(b1 or {}).get('orderNo')}/cancel", key=key)
    record("幂等 AC-03 同 Key 换接口 409 IDEMPOTENCY_KEY_REUSED", status == 409 and (body or {}).get("code") == "IDEMPOTENCY_KEY_REUSED",
           f"HTTP {status} code={(body or {}).get('code')}")
    key2, request2 = "idem-" + uuid.uuid4().hex, checkout_body("parallel")
    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        results = list(pool.map(lambda _: (lambda r: (client_api("POST", "/checkouts", body=request2, key=key2, raw=r), r))({}),
                                range(4)))
    statuses = [r[0][0] for r in results]
    in_progress = [r for r in results if r[0][0] == 409]
    record("幂等 AC-04 并发同 Key：只建 1 单；其余为执行中（Retry-After: 3）或重放",
           orders_with_client_order_id(request2["clientOrderId"]) == 1 and set(statuses) <= {201, 409}
           and all((r[0][1] or {}).get("code") == "IDEMPOTENCY_REQUEST_IN_PROGRESS" and r[1]["headers"].get("Retry-After") == "3"
                   for r in in_progress),
           f"statuses={statuses} orders={orders_with_client_order_id(request2['clientOrderId'])}")
    status, _, created = dev.raw(client, dev.DEV + "/credentials", "POST",
                                 {"name": "Client API rotation", "scopeCodes": SCOPES, "validityDays": 30})
    other = (created or {}).get("secret")
    third = {}
    s3, _ = client_api("POST", "/checkouts", body=request, key=key, credential=other, raw=third)
    record("幂等 AC-06 另一凭证（同 Client）同 Key 重放", status == 201 and s3 == 201 and third.get("text") == first["text"],
           f"HTTP {s3} sameBody={third.get('text') == first['text']}")
    key3, e1, e2 = "idem-" + uuid.uuid4().hex, {}, {}
    sa, pa = client_api("POST", "/orders/ord_doesnotexist0000000000000000/cancel", key=key3, raw=e1)
    sb, pb = client_api("POST", "/orders/ord_doesnotexist0000000000000000/cancel", key=key3, raw=e2)
    record("幂等 AC-05 下游 404 Problem 重放：code、timestamp 不变，requestId = 本次响应头",
           sa == sb == 404 and (pa or {}).get("code") == (pb or {}).get("code")
           and (pa or {}).get("timestamp") == (pb or {}).get("timestamp")
           and (pb or {}).get("requestId") == e2["headers"].get("X-Request-Id") != e1["headers"].get("X-Request-Id"),
           f"HTTP {sa}/{sb} code={(pb or {}).get('code')} sameTimestamp={(pa or {}).get('timestamp') == (pb or {}).get('timestamp')}")


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = ws_flow.approved_client(stamp)
        client_id = app["clientId"]
        context = dev.enter(client, client_id)
        record("进入工作区", context.get("contextType") == "TENANT", f"tenantId={context.get('tenantId')} clientId={client_id}")
        read_only = create_credentials(client)
        receiver = dev.Receiver()
        status, _, created = dev.raw(client, dev.DEV + "/webhook-endpoints", "POST",
                                     {"url": receiver.url, "eventTypes": wf.EVENT_TYPES})
        secret = (created or {}).get("signingSecret") or ""
        record("端点：本机接收端、订阅 3 种事件", status == 201 and secret.startswith("whsec_"),
               f"HTTP {status} endpointId={(created or {}).get('endpointId')} code={(created or {}).get('code')}")
        if status != 201:
            raise Stop("端点未创建")
        data.update(clientId=client_id, endpointId=created.get("endpointId"), tenantId=context.get("tenantId"))

        order_no, _ = wf.create_checkout(client_id, "query")
        client_order_id = client_api("GET", f"/orders/{order_no}")[1]["clientOrderId"]
        queries(order_no, client_order_id)
        rejections(read_only, order_no)
        idempotency_flow(client)

        wf.flow_cancel(receiver, secret, client_id)
        paid_order = wf.flow_paid(receiver, secret, client_id)
        data["orderNoPaid"] = paid_order
        data["refundRequestNo"] = wf.flow_refund(receiver, secret, client_id, paid_order)
        for path in (f"/orders/{paid_order}/refundable-amount", f"/orders/{paid_order}/refunds?limit=10"):
            status, body = client_api("GET", path)
            record(f"退款查询 {path.split('/')[-1].split('?')[0]}", status == 200, f"HTTP {status} code={(body or {}).get('code')}")
        types = [json.loads(r["body"]).get("eventType") for r in receiver.requests]
        record("接收端总数 = 3（无多余投递）", len(types) == 3, f"eventTypes={types}")
        receiver.server.shutdown()
    except Stop as stop:
        print(f"中止于：{stop}")
        RESULTS.append(("中止", "FAIL", str(stop)))
    except Failure as failure:
        RESULTS.append(("中止（HTTP）", "FAIL", problem(failure)))
        print(f"FAIL 中止（HTTP） | {problem(failure)}")
    raise SystemExit(0 if wf.summary(data) else 1)


if __name__ == "__main__":
    main()
