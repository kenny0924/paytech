"""把已登记路由的请求头与最新生成的登记模板对齐（2026-10-14：F01/F02 补 idempotency-key；2026-10-15：请求幂等迁移，idempotency-key 只允许 CORS、不再转发）。

只更新 Header 集合不一致的 CLIENT/OPERATIONS 入驻路由；其余字段按模板原样提交，保持状态不变。
"""
import json
from pathlib import Path

from e2e_http import Operations, secrets

HERE = Path(__file__).resolve().parent
TEMPLATES = json.loads((HERE.parent.parent / "db/management-command-templates.json").read_text())["commands"]
REASON = "Sync route headers: 2026-10-15 idempotency migration, idempotency-key CORS only, not forwarded"

s = secrets()
ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
permission_ids = {}
for command in TEMPLATES:
    if not command["ownerPath"].endswith("/routes"):
        continue
    body = command["body"]
    code = body["routeCode"]
    live = ops.get(f"/gateway-admin/v1/routes/{code}")
    wanted = sorted((h["headerName"], h["headerUse"]) for h in body["headers"])
    current = sorted((h["headerName"], h["headerUse"]) for h in live["headers"])
    if wanted == current:
        continue
    update = {k: body[k] for k in ("httpMethod", "externalPathTemplate", "downstreamPathTemplate", "targetServiceCode",
                                   "securityProfileCode", "authorizationMode", "requiredScopeCode", "requestCookieMode",
                                   "responseCookieMode", "bodyLimitBytes", "bodyReceiveTimeoutMillis",
                                   "responseTimeoutMillis", "headers", "queryParameterNames", "guardRules")}
    update["permissionId"] = live.get("permissionId")
    update["expectedVersion"] = live["version"]
    update["reason"] = REASON
    ops.send("PUT", f"/gateway-admin/v1/routes/{code}", update)
    print("updated", code)
print("done")
