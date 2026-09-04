#!/usr/bin/env python3
"""Explicit mor-test role workflow; leaves its synthetic account disabled and role without permissions."""
import json
import secrets
import uuid

from smoke import Browser, ROOT


def command(browser, method, path, body, key=None, status=(200, 201, 204)):
    return browser.request(method, path, body, status=status,
                           extra_headers={"Idempotency-Key": key or str(uuid.uuid4())})


def nodes(tree):
    for node in tree:
        yield node
        yield from nodes(node["children"])


def account(browser, account_id):
    page = 1
    while True:
        result = browser.request("GET", f"/iam/v1/accounts?page={page}&size=200")
        for row in result["items"]:
            if row["accountId"] == account_id:
                return row
        assert page * 200 < result["total"], "Synthetic account must remain visible to its administrator"
        page += 1


def save_role(browser, role, permissions, name=None, status=200):
    return command(browser, "PUT", "/iam/v1/roles/" + role["roleId"], {
        "roleName": name or role["roleName"], "permissionIds": permissions,
        "expectedVersion": role["version"], "reason": "角色功能验收：调整合成角色权限",
    }, status=status)


def check_workflow(admin, viewer, state):
    options = admin.request("GET", "/iam/v1/role-permission-options")
    resources = list(nodes(options["nodes"]))
    assert len({node["nodeId"] for node in resources}) == len(resources)
    by_name = {node["displayName"]: node for node in resources}
    system_id = by_name["系统管理"]["permissionId"]
    role_read_id = by_name["角色管理"]["permissionId"]
    assert system_id and role_read_id and system_id != role_read_id
    permissions = [system_id, role_read_id]
    suffix = uuid.uuid4().hex[:12].upper()
    request = {"roleCode": "QA_ROLE_" + suffix, "roleName": "验收角色 " + suffix,
               "permissionIds": permissions, "reason": "角色功能验收：创建合成角色"}
    key = str(uuid.uuid4())
    role = command(admin, "POST", "/iam/v1/roles", request, key)
    state["role"] = role
    assert not role["systemRole"] and set(role["permissionIds"]) == set(permissions)
    replay = command(admin, "POST", "/iam/v1/roles", request, key)
    assert replay["roleId"] == role["roleId"] and replay["version"] == role["version"]
    conflict = command(admin, "POST", "/iam/v1/roles", {**request, "roleName": "不同输入"}, key, status=409)
    assert conflict["code"] == "CONCURRENT_REQUEST" and conflict["requestId"]
    detail = admin.request("GET", "/iam/v1/roles/" + role["roleId"])
    assert detail["nodes"] and detail["roleCode"] == request["roleCode"]
    updated = save_role(admin, role, permissions, name=role["roleName"] + " 已编辑")
    state["role"] = updated
    assert updated["version"] == role["version"] + 1
    assert updated["roleCode"] == role["roleCode"]
    assert save_role(admin, role, [], status=409)["code"] == "CONCURRENT_REQUEST"
    assert save_role(admin, updated, ["1"], status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    system_role = admin.request("GET", "/iam/v1/roles/900000000000000510")
    assert save_role(admin, system_role, [], status=400)["code"] == "BUSINESS_CHECK_FAILED"

    initial_password = "Role check initial " + secrets.token_urlsafe(18) + "!"
    password = "Role check changed " + secrets.token_urlsafe(18) + "!"
    email = "role-check-" + suffix.lower() + "@example.test"
    created = command(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial_password, "roleIds": [role["roleId"]],
        "reason": "角色功能验收：创建合成人员",
    })
    state["accountId"] = created["accountId"]
    assert viewer.login(email, initial_password)["passwordChangeRequired"]
    viewer.change_password(initial_password, password)
    viewer.login(email, password)
    visible = viewer.request("GET", "/authorization/v1/navigation")["resources"]
    assert {row["resourceCode"] for row in visible} == {"OPERATIONS_SYSTEM", "OPERATIONS_ROLES"}
    viewer.request("GET", "/iam/v1/roles")
    viewer.request("GET", "/iam/v1/accounts", status=403)
    viewer.request("GET", "/iam/v1/role-permission-options", status=403)
    command(viewer, "POST", "/iam/v1/roles", request, status=403)

    unchanged_token = viewer.access
    reduced = save_role(admin, updated, [system_id])
    state["role"] = reduced
    viewer.request("GET", "/iam/v1/roles", status=403)
    assert "OPERATIONS_ROLES" not in {
        row["resourceCode"] for row in viewer.request("GET", "/authorization/v1/navigation")["resources"]}
    assert viewer.access == unchanged_token, "Revocation must be checked without a token refresh"

    restored = save_role(admin, reduced, permissions)
    state["role"] = restored
    viewer.request("GET", "/iam/v1/roles")

    limited_permissions = permissions + [by_name[name]["permissionId"] for name in ("创建角色", "创建人员", "分配角色")]
    manager = save_role(admin, restored, limited_permissions)
    state["role"] = manager
    offered = list(nodes(viewer.request("GET", "/iam/v1/role-permission-options")["nodes"]))
    assert {node["permissionId"] for node in offered if node["permissionId"]} == set(limited_permissions)
    command(viewer, "POST", "/iam/v1/roles", {
        **request, "roleCode": "DENIED_" + suffix, "permissionIds": [by_name["审计日志"]["permissionId"]],
    }, status=403)
    command(viewer, "PUT", "/iam/v1/accounts/" + created["accountId"] + "/roles", {
        "roleIds": ["900000000000000510"], "expectedMemberVersion": created["memberVersion"],
        "reason": "角色功能验收：拒绝超范围授予管理员",
    }, status=403)
    viewer.request("GET", "/iam/v1/roles/900000000000000510", status=403)
    state["role"] = save_role(admin, manager, permissions)
    assert viewer.access == unchanged_token
    audit = admin.request("GET", "/audit/v1/events?size=200")
    actions = {row["actionCode"] for row in audit["items"] if row["resourceId"] == role["roleId"]}
    assert {"OPERATIONS_ROLE_CREATE", "OPERATIONS_ROLE_UPDATE"} <= actions


def cleanup(admin, viewer, state):
    if state.get("accountId"):
        row = account(admin, state["accountId"])
        if row["status"] == "ACTIVE":
            command(admin, "POST", "/iam/v1/accounts/" + row["accountId"] + "/disable", {
                "expectedVersion": row["version"], "reason": "角色功能验收结束：停用合成人员",
            })
    if state.get("role"):
        current = admin.request("GET", "/iam/v1/roles/" + state["role"]["roleId"])
        save_role(admin, current, [])
    viewer.access = viewer.refresh_token = None
    if admin.refresh_token:
        admin.request("POST", "/auth/v1/logout", {"refreshToken": admin.refresh_token}, status=(200, 204))
        admin.access = admin.refresh_token = None


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, viewer, state = Browser(), Browser(), {}
    admin.login(material["administratorEmail"], material["administratorPassword"])
    try:
        check_workflow(admin, viewer, state)
    finally:
        cleanup(admin, viewer, state)
    print("PASS: role create/edit, atomic grants, idempotency, version conflicts, system-role protection, "
          "assigned member navigation, denied mutations, live permission revoke/regrant and audit.")
    print("Synthetic account disabled and synthetic role permissions removed; no credentials or tokens persisted.")


if __name__ == "__main__":
    main()
