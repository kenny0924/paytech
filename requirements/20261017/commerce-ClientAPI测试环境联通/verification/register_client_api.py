"""Client API 联通：登记 Commerce CLIENT_API Target 与 9 条路由（设计第 4 节冻结清单，结账查询未实现不登记）。

流程：Target、路由先建（新建即 DISABLED）→ 启用 Target → 逐条启用路由，每条启用后等待 Gateway Snapshot 的
CLIENT_API 路由数加 1；超时即停止并提示按发布记录回退。可重复执行：已存在的对象读取详情，已启用的跳过。
用法：E2E_TARGET=test python3 register_client_api.py [--dry-run] [--enable-idempotency]
  --enable-idempotency：把已登记的 3 条写路由更新为 idempotencyMode=REQUIRED 并不再转发 idempotency-key
  （2026-10-17 Gateway 处理 Idempotency-Key；须在 Platform、Gateway 新版本部署之后执行）。
不输出令牌或密码。
"""
import json
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCAL_E2E = HERE.parents[2] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(LOCAL_E2E))
from e2e_http import TARGET, Failure, Operations, secrets  # noqa: E402

REASON = f"Client API {TARGET} activation (requirement 20261017 commerce-ClientAPI测试环境联通)"
TARGET_CODE = "commerce-client-api"
ROOT = "/commerce/client-gateway-api/v1"
GATEWAY_CONTAINER = {"test": "mor-test-api-gateway-1"}.get(TARGET)

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


def route_body(code, method, path, scope, query, body_bytes, idempotent=False):
    """idempotent=True：写路由由 Gateway 处理 Idempotency-Key，不再转发给 Commerce。"""
    if method == "POST":
        forwarded = ["accept", "content-type"] if idempotent else ["accept", "content-type", "idempotency-key"]
    else:
        forwarded = ["accept"]
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
    if idempotent:
        body["idempotencyMode"] = "REQUIRED"
    return body


def enable_idempotency(ops):
    """更新 3 条写路由：全量 PUT（Platform 更新为全量替换），带上当前版本号。"""
    for route in ROUTES:
        if route[1] != "POST":
            continue
        path = f"/gateway-admin/v1/routes/{route[0]}"
        live = ops.get(path)
        if live.get("idempotencyMode") == "REQUIRED":
            print("exists ", "idempotency", route[0])
            continue
        body = route_body(*route, idempotent=True)
        for key in ("routeCode", "channel"):
            body.pop(key)
        body["expectedVersion"] = live["version"]
        ops.send("PUT", path, body)
        print("updated", "idempotency", route[0], ops.get(path).get("idempotencyMode"))


def client_api_route_count():
    """读 Gateway 管理端口（容器内 127.0.0.1:19080）的 gateway_snapshot_routes{channel="CLIENT_API"}。"""
    if GATEWAY_CONTAINER is None:
        return None
    script = ("import urllib.request,re;t=urllib.request.urlopen('http://127.0.0.1:19080/actuator/prometheus',timeout=5)"
              ".read().decode();m=re.search(r'^gateway_snapshot_routes\\{channel=\"CLIENT_API\"\\} ([0-9.]+)$',t,re.M);"
              "print(int(float(m.group(1))) if m else 0)")
    out = subprocess.run(["docker", "run", "--rm", "--network", f"container:{GATEWAY_CONTAINER}",
                          "python:3.13-alpine", "python", "-I", "-c", script],
                         capture_output=True, text=True, timeout=60, check=True)
    return int(out.stdout.strip())


def wait_route_count(expected, timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        count = client_api_route_count()
        if count is None or count >= expected:
            return count
        time.sleep(5)
    raise SystemExit(f"Gateway CLIENT_API 路由数未达到 {expected}：停止，按发布记录回退（撤销验收凭证 → 停用路由与 Target）")


def ensure(ops, kind, path, create_path, body):
    try:
        found = ops.get(path)
        print("exists ", kind, path.rsplit("/", 1)[-1], found.get("status"))
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
    if "--enable-idempotency" in sys.argv:
        enable_idempotency(ops)
        print("done")
        return
    target_path = f"/gateway-admin/v1/target-services/{TARGET_CODE}"
    ensure(ops, "target", target_path, "/gateway-admin/v1/target-services",
           {"targetServiceCode": TARGET_CODE, "channel": "CLIENT_API", "discoveryServiceName": "commerce-service",
            "reason": REASON})
    for route in ROUTES:
        ensure(ops, "route", f"/gateway-admin/v1/routes/{route[0]}", "/gateway-admin/v1/routes", route_body(*route))
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
