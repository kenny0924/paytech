"""步骤 3.2（A9）：用管理员会话按 db/management-command-templates.json 登记入驻目标、权限、路由、页面，再逐项启用并授予管理员角色。

可重复执行：已存在的编码改为读取详情，不重复创建；已启用的不再修改。每步输出编码与结果，不输出令牌或密码。
"""
import json
import sys
from pathlib import Path

from e2e_http import TARGET, Failure, Operations, secrets

HERE = Path(__file__).resolve().parent
TEMPLATES = json.loads((HERE.parent.parent / "db/management-command-templates.json").read_text())["commands"]
ADMIN_ROLE = "900000000000000510"
REASON = f"Client onboarding {TARGET} registration (authorized 2026-10-14)"

s = secrets()
ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
ids = {}


def detail(command):
    body = command["body"]
    path = command["ownerPath"]
    if path.endswith("/target-services"):
        return ops.get(f"/gateway-admin/v1/target-services/{body['targetServiceCode']}")
    if path.endswith("/permissions"):
        return ops.get(f"/permission-admin/v1/permissions/{body['permissionCode']}")
    if path.endswith("/routes"):
        return ops.get(f"/gateway-admin/v1/routes/{body['routeCode']}")
    return ops.get(f"/console-admin/v1/resources/{body['resourceCode']}")


def object_id(found):
    for key in ("permissionId", "resourceId", "objectId", "routeId", "targetId"):
        if found.get(key):
            return found[key]
    return None


def create(command):
    try:
        ids[command["key"]] = object_id(detail(command))
        print("exists ", command["key"])
        return
    except Failure as failure:
        if failure.status != 404:
            print("FAILED read", command["key"], failure)
            sys.exit(1)
    body = json.loads(json.dumps(command["body"]))
    for pointer, reference in command.get("bindings", {}).items():
        body[pointer.lstrip("/")] = ids[reference]
    body["reason"] = REASON
    try:
        result = ops.send("POST", command["ownerPath"], body)
    except Failure as failure:
        print("FAILED", command["key"], failure)
        sys.exit(1)
    ids[command["key"]] = result["objectId"]
    print("created", command["key"], result["status"])


def activate(kind, code, path):
    found = ops.get(path)
    if found.get("status") == "ACTIVE":
        return
    try:
        ops.send("PATCH", path + "/status", {"status": "ACTIVE", "expectedVersion": found["version"], "reason": REASON})
        print("active ", kind, code)
    except Failure as failure:
        print("FAILED activate", kind, code, failure)
        sys.exit(1)


# 路由与页面只能绑定已启用的权限：先建并启用目标与权限，再建其余对象。
permissions = [c for c in TEMPLATES if c["ownerPath"].endswith("/permissions")]
for command in TEMPLATES:
    if command["ownerPath"].endswith(("/target-services", "/permissions")):
        create(command)
activate("target", "client-console-bff", "/gateway-admin/v1/target-services/client-console-bff")
for command in permissions:
    code = command["body"]["permissionCode"]
    activate("permission", code, f"/permission-admin/v1/permissions/{code}")
for command in TEMPLATES:
    if not command["ownerPath"].endswith(("/target-services", "/permissions")):
        create(command)

# PLATFORM_ADMIN 是系统角色，管理接口拒绝修改；授予入驻权限需单独授权的 SQL（见执行记录），本脚本不处理。
role = ops.get(f"/iam/v1/roles/{ADMIN_ROLE}")
wanted = [ids[c["key"]] for c in permissions]
print("PLATFORM_ADMIN missing onboarding permissions:", len([p for p in wanted if p not in role["permissionIds"]]))

for command in TEMPLATES:
    if command["ownerPath"].endswith("/routes"):
        code = command["body"]["routeCode"]
        activate("route", code, f"/gateway-admin/v1/routes/{code}")
for command in TEMPLATES:
    if command["ownerPath"].endswith("/resources"):
        code = command["body"]["resourceCode"]
        activate("resource", code, f"/console-admin/v1/resources/{code}")
print("done")
