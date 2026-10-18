"""阶段 5 本机全链路（任务卡 14）：登记 Client API Target 与 9 条路由（由 20261017 commerce-ClientAPI测试环境联通/verification 同名脚本改写）。

V2 差异：Target 发现名改为 acquiring-service（第 03 步 Commerce 拆分改名；context path 仍为 /commerce，控制器前缀
/client-gateway-api/v1 未变）；3 条写路由一开始就是 idempotencyMode=REQUIRED、不转发 idempotency-key（Gateway 处理请求幂等）；
Snapshot 路由数读本机 Gateway 管理端口 http://127.0.0.1:19080/actuator/prometheus。
流程：Target、路由先建（新建即 DISABLED）→ 启用 Target → 逐条启用路由，每条启用后等待 CLIENT_API 路由数加 1；可重复执行。
用法：python3 register_client_api.py [--dry-run]；不输出令牌或密码。
"""
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from e2e_http import Failure, Operations, secrets  # noqa: E402

REASON = "Client API 本机全链路（阶段 5 任务卡 14）"
TARGET_CODE = "commerce-client-api"
DISCOVERY = "acquiring-service"
ROOT = "/commerce/client-gateway-api/v1"
GATEWAY_METRICS = "http://127.0.0.1:19080/actuator/prometheus"

# (routeCode, method, external path, scope, query allowlist, body bytes)
ROUTES = [
    ("client-api-commerce-checkout-create", "POST", "/checkouts", "checkouts.write", [], 16384),
    ("client-api-commerce-order-read", "GET", "/orders/{orderNo}", "orders.read", [], 0),
    ("client-api-commerce-order-read-client-id", "GET", "/orders", "orders.read", ["clientOrderId"], 0),
    ("client-api-commerce-order-cancel", "POST", "/orders/{orderNo}/cancel", "orders.cancel", [], 0),
    ("client-api-commerce-refundable-read", "GET", "/orders/{orderNo}/refundable-amount", "refunds.read", [], 0),
    ("client-api-commerce-refund-create", "POST", "/orders/{orderNo}/refunds", "refunds.write", [], 4096),
    ("client-api-commerce-refund-read", "GET", "/refunds/{refundRequestNo}", "refunds.read", [], 0),
    ("client-api-commerce-refund-read-client-id", "GET", "/refunds", "refunds.read", ["clientRefundId"], 0),
    ("client-api-commerce-order-refunds-list", "GET", "/orders/{orderNo}/refunds", "refunds.read", ["limit", "cursor"], 0),
]


def rate(dimension, capacity):
    return {"ruleType": "RATE_LIMIT", "dimension": dimension, "capacity": capacity, "refillTokens": capacity,
            "refillPeriodMillis": 60000, "concurrencyLimit": None}


def concurrency(dimension, limit):
    return {"ruleType": "CONCURRENCY", "dimension": dimension, "capacity": None, "refillTokens": None,
            "refillPeriodMillis": None, "concurrencyLimit": limit}


GUARDS = [rate("SHARED_ROUTE", 600), concurrency("SHARED_ROUTE", 50),
          rate("CLIENT_IP", 120), concurrency("CLIENT_IP", 20),
          rate("AUTHENTICATED_PRINCIPAL", 120), concurrency("AUTHENTICATED_PRINCIPAL", 10),
          rate("TENANT", 300), concurrency("TENANT", 20)]


def route_body(code, method, path, scope, query, body_bytes):
    forwarded = ["accept", "content-type"] if method == "POST" else ["accept"]
    body = {
        "routeCode": code, "channel": "CLIENT_API", "httpMethod": method,
        "externalPathTemplate": "/api/v1" + path, "downstreamPathTemplate": ROOT + path,
        "securityProfileCode": "CLIENT_API_CREDENTIAL", "authorizationMode": "API_SCOPE",
        "requestCookieMode": "DROP", "responseCookieMode": "NONE",
        "bodyLimitBytes": body_bytes, "bodyReceiveTimeoutMillis": 5000, "responseTimeoutMillis": 10000,
        "headers": [{"headerName": name, "headerUse": "FORWARD_REQUEST"} for name in forwarded],
        "queryParameterNames": query, "guardRules": GUARDS,
        "targetServiceCode": TARGET_CODE, "permissionId": None, "requiredScopeCode": scope, "reason": REASON,
    }
    if method == "POST":
        body["idempotencyMode"] = "REQUIRED"
    return body


def client_api_route_count():
    try:
        text = urllib.request.urlopen(GATEWAY_METRICS, timeout=5).read().decode()
    except OSError:
        return None
    match = re.search(r'^gateway_snapshot_routes\{channel="CLIENT_API"\} ([0-9.]+)$', text, re.M)
    return int(float(match.group(1))) if match else 0


def wait_route_count(expected, timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        count = client_api_route_count()
        if count is None or count >= expected:
            return count
        time.sleep(3)
    raise SystemExit(f"Gateway CLIENT_API 路由数未达到 {expected}：停止（Snapshot 刷新间隔 5s，检查 Gateway 日志）")


def ensure(ops, kind, path, create_path, body):
    try:
        found = ops.get(path)
        print("exists ", kind, path.rsplit("/", 1)[-1], found.get("status"), found.get("idempotencyMode", ""))
        return found
    except Failure as failure:
        if failure.status != 404:
            raise
    result = ops.send("POST", create_path, body)
    print("created", kind, path.rsplit("/", 1)[-1], result.get("status"))
    return ops.get(path)


def activate(ops, kind, path):
    found = ops.get(path)
    if found.get("status") == "ACTIVE":
        return False
    ops.send("PATCH", path + "/status", {"status": "ACTIVE", "expectedVersion": found["version"], "reason": REASON})
    print("active ", kind, path.rsplit("/", 1)[-1])
    return True


def main():
    if "--dry-run" in sys.argv:
        print(json.dumps([route_body(*route) for route in ROUTES], ensure_ascii=False, indent=1))
        return
    s = secrets()
    ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    target_path = f"/gateway-admin/v1/target-services/{TARGET_CODE}"
    target = ensure(ops, "target", target_path, "/gateway-admin/v1/target-services",
                    {"targetServiceCode": TARGET_CODE, "channel": "CLIENT_API", "discoveryServiceName": DISCOVERY, "reason": REASON})
    if target.get("discoveryServiceName") not in (None, DISCOVERY):
        raise SystemExit(f"已有 Target 发现名为 {target.get('discoveryServiceName')}，不是 {DISCOVERY}：停止，人工核对")
    for route in ROUTES:
        found = ensure(ops, "route", f"/gateway-admin/v1/routes/{route[0]}", "/gateway-admin/v1/routes", route_body(*route))
        if route[1] == "POST" and found.get("idempotencyMode") not in (None, "REQUIRED"):
            raise SystemExit(f"路由 {route[0]} 幂等模式为 {found.get('idempotencyMode')}：停止，人工核对")
    activate(ops, "target", target_path)
    count = client_api_route_count()
    print("CLIENT_API routes before:", count)
    for route in ROUTES:
        if activate(ops, "route", f"/gateway-admin/v1/routes/{route[0]}") and count is not None:
            count = wait_route_count(count + 1)
            print("CLIENT_API routes:", count)
    print("done")


if __name__ == "__main__":
    main()
