#!/usr/bin/env python3
"""Explicit mor-test role deletion/count acceptance; uses only synthetic staff and roles."""
import json
import secrets
import uuid

from initialize import mysql
from roles_smoke import account, command, nodes, save_role
from smoke import Browser, ROOT


def role_list(browser):
    result = browser.request("GET", "/iam/v1/roles?size=200")
    assert result["total"] <= 200, "Use an isolated small mor-test catalog for this acceptance"
    assert all(isinstance(row["employeeCount"], int) and row["employeeCount"] >= 0
               and row["version"] > 0 for row in result["items"])
    return {row["roleId"]: row for row in result["items"]}


def delete_role(browser, role, key=None, reason="删除验收：删除合成角色", status=204):
    return command(browser, "DELETE", "/iam/v1/roles/" + role["roleId"], {
        "expectedVersion": role["version"], "reason": reason,
    }, key=key, status=status)


def assign(browser, staff, role_id):
    command(browser, "PUT", "/iam/v1/accounts/" + staff["accountId"] + "/roles", {
        "roleIds": [role_id], "expectedMemberVersion": staff["memberVersion"],
        "reason": "删除验收：调整合成人员的角色关联",
    }, status=204)
    return account(browser, staff["accountId"])


def assert_audit(role):
    role_id = int(role["roleId"])
    state = mysql(f"SELECT status,version FROM t_role WHERE role_id={role_id};")
    assert state == f"INACTIVE\t{role['version'] + 1}", "Deletion must retain the inactive versioned role"
    assert mysql(f"SELECT COUNT(*) FROM t_role_permission WHERE role_id={role_id} AND status='ACTIVE';") == "0"
    predicate = f"action_code='OPERATIONS_ROLE_DELETE' AND resource_id='{role_id}' AND result='SUCCEEDED'"
    assert mysql(f"SELECT COUNT(*) FROM t_audit_event WHERE {predicate};") == "1", "Replay must not duplicate audit"
    rows = mysql(f"SELECT attr_key,value_text FROM t_audit_event_attr "
                 f"WHERE audit_event_id=(SELECT audit_event_id FROM t_audit_event WHERE {predicate}) "
                 "AND attr_key IN ('before.status','after.status','before.version','after.version',"
                 "'before.permissionCount','after.permissionCount') ORDER BY attr_key;")
    values = dict(row.split("\t", 1) for row in rows.splitlines())
    assert values == {"before.status": "ACTIVE", "after.status": "INACTIVE",
                      "before.version": str(role["version"]), "after.version": str(role["version"] + 1),
                      "before.permissionCount": str(len(role["permissionIds"])), "after.permissionCount": "0"}


def verify(admin, viewer, state):
    before = role_list(admin)
    assert before["900000000000000510"]["employeeCount"] >= 1
    system_role = before["900000000000000511"]
    assert delete_role(admin, system_role, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    resources = list(nodes(admin.request("GET", "/iam/v1/role-permission-options")["nodes"]))
    by_name = {node["displayName"]: node["permissionId"] for node in resources}
    assert by_name["删除角色"] == "900000000000000531"
    permissions = [by_name["系统管理"], by_name["角色管理"]]
    suffix = uuid.uuid4().hex[:12].upper()
    for kind in ("PRIMARY", "ALTERNATE"):
        role = command(admin, "POST", "/iam/v1/roles", {
            "roleCode": f"QA_DELETE_{kind}_{suffix}", "roleName": f"删除验收 {kind} {suffix}",
            "permissionIds": permissions, "reason": "删除验收：创建合成角色",
        }, status=200)
        state["roles"].append(role)
    primary, alternate = state["roles"]
    initial = role_list(admin)
    assert initial[primary["roleId"]]["employeeCount"] == 0
    assert initial[alternate["roleId"]]["employeeCount"] == 0

    email = "role-delete-" + suffix.lower() + "@example.test"
    initial_password = "Delete check initial " + secrets.token_urlsafe(18) + "!"
    changed_password = "Delete check changed " + secrets.token_urlsafe(18) + "!"
    staff = command(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial_password, "roleIds": [primary["roleId"]],
        "reason": "删除验收：创建合成人员",
    }, status=200)
    state["accountId"] = staff["accountId"]
    assert role_list(admin)[primary["roleId"]]["employeeCount"] == 1
    assert delete_role(admin, primary, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    viewer.login(email, initial_password)
    viewer.change_password(initial_password, changed_password)
    viewer.login(email, changed_password)
    assert delete_role(viewer, alternate, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    assert role_list(viewer)[primary["roleId"]]["employeeCount"] == 1

    staff = account(admin, staff["accountId"])
    command(admin, "POST", "/iam/v1/accounts/" + staff["accountId"] + "/disable", {
        "expectedVersion": staff["version"], "reason": "删除验收：停用合成人员后仍保留角色关联",
    }, status=204)
    viewer.access = viewer.refresh_token = None
    assert role_list(admin)[primary["roleId"]]["employeeCount"] == 1
    assert delete_role(admin, primary, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    staff = assign(admin, staff, alternate["roleId"])
    moved = role_list(admin)
    assert moved[primary["roleId"]]["employeeCount"] == 0
    assert moved[alternate["roleId"]]["employeeCount"] == 1

    updated = save_role(admin, primary, permissions, name=primary["roleName"] + " 已更新")
    state["roles"][0] = updated
    assert delete_role(admin, primary, status=409)["code"] == "CONCURRENT_REQUEST"
    key = str(uuid.uuid4())
    delete_role(admin, updated, key=key)
    state["deleted"].add(updated["roleId"])
    delete_role(admin, updated, key=key)
    conflict = delete_role(admin, updated, key=key, reason="不同的删除原因", status=409)
    assert conflict["code"] == "CONCURRENT_REQUEST" and conflict["requestId"]
    assert delete_role(admin, updated, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    assert updated["roleId"] not in role_list(admin)
    assert admin.request("GET", "/iam/v1/roles/" + updated["roleId"], status=400)["code"] == "BUSINESS_CHECK_FAILED"
    command(admin, "PUT", "/iam/v1/accounts/" + staff["accountId"] + "/roles", {
        "roleIds": [updated["roleId"]], "expectedMemberVersion": staff["memberVersion"],
        "reason": "删除验收：拒绝分配已删除角色",
    }, status=400)
    assert_audit(updated)
    audit = admin.request("GET", "/audit/v1/events?size=200")
    assert any(row["resourceId"] == updated["roleId"] and row["actionCode"] == "OPERATIONS_ROLE_DELETE"
               and row["result"] == "SUCCEEDED" for row in audit["items"])


def cleanup(admin, viewer, state):
    if state.get("accountId"):
        staff = account(admin, state["accountId"])
        if staff["status"] == "ACTIVE":
            command(admin, "POST", "/iam/v1/accounts/" + staff["accountId"] + "/disable", {
                "expectedVersion": staff["version"], "reason": "删除验收结束：停用合成人员",
            }, status=204)
        assign(admin, staff, "900000000000000511")
    for role in state["roles"]:
        if role["roleId"] not in state["deleted"]:
            current = admin.request("GET", "/iam/v1/roles/" + role["roleId"])
            delete_role(admin, current)
            state["deleted"].add(role["roleId"])
    viewer.access = viewer.refresh_token = None
    if admin.refresh_token:
        admin.request("POST", "/auth/v1/logout", {"refreshToken": admin.refresh_token}, status=204)
        admin.access = admin.refresh_token = None


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, viewer = Browser(), Browser()
    state = {"roles": [], "deleted": set()}
    admin.login(material["administratorEmail"], material["administratorPassword"])
    try:
        verify(admin, viewer, state)
    finally:
        cleanup(admin, viewer, state)
    print("PASS: associated employee counts including disabled accounts, independent delete authorization, "
          "system/associated role protection, version conflict, logical deletion, idempotent replay, "
          "deleted role assignment rejection and one complete deletion audit.")
    print("Synthetic account disabled, both synthetic roles deleted through the API, session logged out.")


if __name__ == "__main__":
    main()
