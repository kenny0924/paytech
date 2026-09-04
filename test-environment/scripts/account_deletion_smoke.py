#!/usr/bin/env python3
"""Explicit mor-test employee deletion acceptance using only synthetic employees and roles."""
import json
import secrets
import time
import uuid

from initialize import mysql
from role_deletion_smoke import assign, delete_role, role_list
from roles_smoke import account, command, nodes
from smoke import Browser, ROOT


def delete_employee(browser, row, key=None, status=204, **overrides):
    body = {"expectedVersion": row["version"], "expectedMemberVersion": row["memberVersion"],
            "reason": "员工删除验收：关闭合成账号并解除授权", **overrides}
    return command(browser, "DELETE", "/iam/v1/accounts/" + row["accountId"], body, key=key, status=status)


def create_employee(admin, state, role, suffix, label):
    email = f"account-delete-{label}-{suffix.lower()}@example.test"
    password = "Employee check initial " + secrets.token_urlsafe(18) + "!"
    row = command(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": password, "roleIds": [role["roleId"]],
        "reason": "员工删除验收：创建合成人员",
    }, status=200)
    state["accounts"].append(row["accountId"])
    return row, email, password


def ordinary_login(browser, email, initial_password):
    changed = "Employee check changed " + secrets.token_urlsafe(18) + "!"
    browser.login(email, initial_password)
    browser.change_password(initial_password, changed)
    browser.login(email, changed)
    return changed


def assert_closed(before, epoch):
    account_id = int(before["accountId"])
    member_id = int(before["memberId"])
    actual = mysql(f"SELECT a.status,a.version,a.auth_epoch,m.status,m.version,p.status "
                   f"FROM t_user_account a JOIN t_tenant_member m ON m.account_id=a.account_id "
                   f"JOIN t_password_credential p ON p.account_id=a.account_id WHERE a.account_id={account_id};")
    assert actual == f"CLOSED\t{before['version'] + 1}\t{epoch + 1}\tREVOKED\t{before['memberVersion'] + 1}\tREVOKED"
    assert mysql(f"SELECT COUNT(*) FROM t_tenant_member_role WHERE tenant_member_id={member_id} AND status='ACTIVE';") == "0"
    assert mysql(f"SELECT COUNT(*) FROM t_auth_session WHERE account_id={account_id} AND status='ACTIVE';") == "0"
    assert mysql(f"SELECT COUNT(*) FROM t_refresh_token_generation g JOIN t_auth_session s ON s.session_id=g.session_id "
                 f"WHERE s.account_id={account_id} AND g.status='ISSUED';") == "0"
    predicate = f"action_code='OPERATIONS_ACCOUNT_DELETE' AND resource_id='{account_id}' AND result='SUCCEEDED'"
    assert mysql(f"SELECT COUNT(*) FROM t_audit_event WHERE {predicate};") == "1", "Deletion replay must not duplicate audit"
    attributes = mysql(f"SELECT attr_key,value_text FROM t_audit_event_attr WHERE audit_event_id="
                       f"(SELECT audit_event_id FROM t_audit_event WHERE {predicate}) ORDER BY attr_no;")
    values = dict(line.split("\t", 1) for line in attributes.splitlines())
    assert values["before.accountStatus"] == before["status"] and values["after.accountStatus"] == "CLOSED"
    assert values["before.accountVersion"] == str(before["version"])
    assert values["after.accountVersion"] == str(before["version"] + 1)
    assert values["before.memberStatus"] == "ACTIVE" and values["after.memberStatus"] == "REVOKED"
    assert values["before.memberVersion"] == str(before["memberVersion"])
    assert values["after.memberVersion"] == str(before["memberVersion"] + 1)
    assert values["before.roleCount"] == str(len(before["roles"])) and values["after.roleCount"] == "0"
    before_roles = {value for key, value in (line.split("\t", 1) for line in attributes.splitlines())
                    if key == "before.roleId"}
    assert before_roles == {role["roleId"] for role in before["roles"]}


def check(admin, actor, target, state):
    options = list(nodes(admin.request("GET", "/iam/v1/role-permission-options")["nodes"]))
    permissions = {node["displayName"]: node["permissionId"] for node in options}
    assert permissions["删除人员"] == "900000000000000532"
    base = [permissions[name] for name in ("系统管理", "人员管理", "角色管理")]
    suffix = uuid.uuid4().hex[:12].upper()
    for label, grants in (("ACTOR", base + [permissions["删除人员"]]), ("TARGET", base)):
        state["roles"].append(command(admin, "POST", "/iam/v1/roles", {
            "roleCode": f"QA_ACCOUNT_DELETE_{label}_{suffix}", "roleName": f"员工删除验收 {label} {suffix}",
            "permissionIds": grants, "reason": "员工删除验收：建立独立权限范围",
        }, status=200))
    actor_role, target_role = state["roles"]
    staff_actor, actor_email, actor_password = create_employee(admin, state, actor_role, suffix, "actor")
    active, email, initial = create_employee(admin, state, target_role, suffix, "active")
    disabled, _, _ = create_employee(admin, state, target_role, suffix, "disabled")
    ordinary_login(actor, actor_email, actor_password)
    changed_password = ordinary_login(target, email, initial)
    actor_row = account(admin, staff_actor["accountId"])
    assert delete_employee(actor, actor_row, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    assert delete_employee(target, disabled, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    for method, path in (("POST", "/iam/v1/accounts"),
                         ("POST", f"/iam/v1/accounts/{disabled['accountId']}/disable"),
                         ("PUT", f"/iam/v1/accounts/{disabled['accountId']}/roles"),
                         ("DELETE", f"/iam/v1/roles/{target_role['roleId']}")):
        assert command(actor, method, path, {}, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    navigation = actor.request("GET", "/authorization/v1/navigation")["resources"]
    assert {row["resourceCode"] for row in navigation if row["resourceType"] == "BUTTON"} == {"OPERATIONS_ACCOUNT_DELETE"}
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 2

    # Login advances Account version; assignment advances Member version independently.
    current = account(admin, active["accountId"])
    assert current["version"] > active["version"]
    assert delete_employee(actor, active, status=409)["code"] == "CONCURRENT_REQUEST"
    updated = assign(admin, current, target_role["roleId"])
    assert delete_employee(actor, current, status=409)["code"] == "CONCURRENT_REQUEST"
    epoch = int(mysql(f"SELECT auth_epoch FROM t_user_account WHERE account_id={int(active['accountId'])};"))
    key = str(uuid.uuid4())
    delete_employee(actor, updated, key=key)
    state["closed"].add(updated["accountId"])
    delete_employee(actor, updated, key=key)
    assert delete_employee(actor, updated, key=key, reason="不同的原因", status=409)["code"] == "CONCURRENT_REQUEST"
    assert delete_employee(actor, updated, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    assert_closed(updated, epoch)
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 1
    target.request("GET", "/iam/v1/accounts", status=401)
    target.request("POST", "/auth/v1/refresh", {"refreshToken": target.refresh_token}, authenticated=False, status=401)
    target.access = target.refresh_token = None
    # This acceptance deliberately exceeds one IP's initial five-login burst; allow one refill.
    time.sleep(13)
    target.request("POST", "/auth/v1/login", {"loginIdentifier": email, "password": changed_password},
                   authenticated=False, status=401)

    command(admin, "POST", f"/iam/v1/accounts/{disabled['accountId']}/disable", {
        "expectedVersion": disabled["version"], "reason": "员工删除验收：停用后删除",
    }, status=204)
    disabled_row = account(admin, disabled["accountId"])
    assert disabled_row["status"] == "DISABLED"
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 1
    epoch = int(mysql(f"SELECT auth_epoch FROM t_user_account WHERE account_id={int(disabled['accountId'])};"))
    delete_employee(actor, disabled_row)
    state["closed"].add(disabled_row["accountId"])
    assert_closed(disabled_row, epoch)
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 0
    remaining = admin.request("GET", "/iam/v1/accounts?size=200")
    assert not state["closed"].intersection(row["accountId"] for row in remaining["items"])
    assert all(row["status"] != "CLOSED" for row in remaining["items"])
    expected_total = mysql("SELECT COUNT(*) FROM t_user_account WHERE realm='OPERATIONS' "
                           "AND account_type='HUMAN' AND status<>'CLOSED';")
    assert remaining["total"] == int(expected_total), "Paging total must also exclude deleted employees"
    for method, path, body in (
        ("POST", f"/iam/v1/accounts/{active['accountId']}/disable", {"expectedVersion": updated["version"] + 1, "reason": "拒绝操作已删除员工"}),
        ("PUT", f"/iam/v1/accounts/{active['accountId']}/roles", {"expectedMemberVersion": updated["memberVersion"] + 1, "roleIds": [target_role["roleId"]], "reason": "拒绝重新授权已删除员工"}),
        ("POST", "/iam/v1/accounts", {"email": email, "initialPassword": initial, "roleIds": [target_role["roleId"]], "reason": "保留历史邮箱唯一性"}),
    ):
        assert command(admin, method, path, body, status=400)["code"] == "BUSINESS_CHECK_FAILED"


def cleanup(admin, actor, target, state):
    for account_id in state["accounts"]:
        if account_id not in state["closed"]:
            delete_employee(admin, account(admin, account_id))
            state["closed"].add(account_id)
    for role in state["roles"]:
        delete_role(admin, role, reason="员工删除验收结束：删除已无员工关联的合成角色")
    actor.access = actor.refresh_token = target.access = target.refresh_token = None
    if admin.refresh_token:
        admin.request("POST", "/auth/v1/logout", {"refreshToken": admin.refresh_token}, status=204)
        admin.access = admin.refresh_token = None


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, actor, target = Browser(), Browser(), Browser()
    state = {"roles": [], "accounts": [], "closed": set()}
    admin.login(material["administratorEmail"], material["administratorPassword"])
    try:
        check(admin, actor, target, state)
    finally:
        cleanup(admin, actor, target, state)
    print("PASS: independent employee deletion permission, self-deletion rejection, both version conflicts, "
          "active/disabled deletion, idempotency, revoked access/refresh/password, member/role revocation, "
          "employee count and list removal, immutable history and one complete deletion audit.")
    print("Only synthetic employees and roles were deleted; administrator session logged out.")


if __name__ == "__main__":
    main()
