"""把已登记路由与最新生成的登记模板对齐（2026-10-14：F01/F02 补 idempotency-key；2026-10-15：请求幂等迁移，idempotency-key 只允许 CORS、不再转发；2026-10-16：租户与工作区，C02 下游改认证路径、A05 允许清 Cookie）。

只更新 Header 集合、下游路径或 Cookie 模式与模板不一致的 CLIENT/OPERATIONS 入驻路由；其余字段按模板原样提交，保持状态不变。
尚未登记的路由（如 W01、W02）跳过，由 register_onboarding.py 创建并启用。
"""
import json
from pathlib import Path

from e2e_http import Operations, secrets

HERE = Path(__file__).resolve().parent
TEMPLATES = json.loads((HERE.parent.parent / "db/management-command-templates.json").read_text())["commands"]
REASON = "Sync routes with registration plan: 2026-10-16 workspace context (C02 downstream, A05 ISSUE_OR_CLEAR)"

s = secrets()
ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
permission_ids = {}
for command in TEMPLATES:
    if not command["ownerPath"].endswith("/routes"):
        continue
    body = command["body"]
    code = body["routeCode"]
    try:
        live = ops.get(f"/gateway-admin/v1/routes/{code}")
    except Exception as missing:  # 未登记：交给 register_onboarding.py
        if getattr(missing, "status", None) != 404:
            raise
        print("missing", code)
        continue
    wanted = sorted((h["headerName"], h["headerUse"]) for h in body["headers"])
    current = sorted((h["headerName"], h["headerUse"]) for h in live["headers"])
    same = all(body[k] == live.get(k) for k in ("downstreamPathTemplate", "requestCookieMode", "responseCookieMode"))
    if wanted == current and same:
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
