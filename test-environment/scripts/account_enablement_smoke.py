#!/usr/bin/env python3
"""Explicit mor-test enablement acceptance; modifies only employees created by this run."""
import json
import time
import uuid

from account_deletion_smoke import cleanup, create_employee, delete_employee, ordinary_login
from initialize import mysql
from role_deletion_smoke import assign, role_list
from roles_smoke import account, command, nodes
from smoke import Browser, ROOT


def enable_employee(browser, row, key=None, status=204, **overrides):
    body = {"expectedVersion": row["version"], "reason": "员工启用验收：恢复合成人员", **overrides}
    return command(browser, "POST", f"/iam/v1/accounts/{row['accountId']}/enable", body, key=key, status=status)


def disable_employee(admin, row):
    command(admin, "POST", f"/iam/v1/accounts/{row['accountId']}/disable", {
        "expectedVersion": row["version"], "reason": "员工启用验收：停用合成人员",
    }, status=204)
    return account(admin, row["accountId"])


def retained_state(row):
    account_id, member_id = int(row["accountId"]), int(row["memberId"])
    return mysql(f"SELECT a.credential_version,a.consecutive_password_failures,a.locked_until,"
                 f"a.last_password_failure_time,p.status,p.credential_version,p.force_change_flag,p.version,"
                 f"p.password_changed_time,SHA2(p.password_verifier_phc,256) FROM t_user_account a "
                 f"JOIN t_password_credential p ON p.account_id=a.account_id WHERE a.account_id={account_id};"
                 f"SELECT status,version FROM t_tenant_member WHERE member_id={member_id};"
                 f"SELECT role_id,status,version,effective_from,effective_to FROM t_tenant_member_role "
                 f"WHERE tenant_member_id={member_id} ORDER BY role_id;")


def assert_enabled(before, epoch, retained):
    account_id = int(before["accountId"])
    assert mysql(f"SELECT status,version,auth_epoch FROM t_user_account WHERE account_id={account_id};") == (
        f"ACTIVE\t{before['version'] + 1}\t{epoch + 1}")
    assert retained_state(before) == retained, "Enable must preserve credentials, lockout, member and role grants"
    assert mysql(f"SELECT COUNT(*) FROM t_auth_session WHERE account_id={account_id} AND status='ACTIVE';") == "0"
    assert mysql(f"SELECT COUNT(*) FROM t_refresh_token_generation g JOIN t_auth_session s ON s.session_id=g.session_id "
                 f"WHERE s.account_id={account_id} AND g.status='ISSUED';") == "0"
    predicate = f"action_code='OPERATIONS_ACCOUNT_ENABLE' AND resource_id='{account_id}' AND result='SUCCEEDED'"
    assert mysql(f"SELECT COUNT(*) FROM t_audit_event WHERE {predicate};") == "1"
    rows = mysql(f"SELECT attr_key,value_text FROM t_audit_event_attr WHERE audit_event_id="
                 f"(SELECT audit_event_id FROM t_audit_event WHERE {predicate}) ORDER BY attr_no;")
    values = dict(row.split("\t", 1) for row in rows.splitlines())
    expected = {"before.accountStatus": "DISABLED", "after.accountStatus": "ACTIVE",
                "before.accountVersion": str(before["version"]), "after.accountVersion": str(before["version"] + 1),
                "before.authEpoch": str(epoch), "after.authEpoch": str(epoch + 1),
                "memberId": before["memberId"], "reason": "员工启用验收：恢复合成人员"}
    assert values == expected, "Enable audit must retain exact before/after state once"


def check(admin, actor, target, state):
    options = list(nodes(admin.request("GET", "/iam/v1/role-permission-options")["nodes"]))
    permissions = {node["displayName"]: node["permissionId"] for node in options}
    assert permissions["启用人员"] == "900000000000000533"
    base = [permissions[name] for name in ("系统管理", "人员管理", "角色管理")]
    suffix = uuid.uuid4().hex[:12].upper()
    for label, grants in (("ACTOR", base + [permissions["启用人员"]]), ("TARGET", base)):
        state["roles"].append(command(admin, "POST", "/iam/v1/roles", {
            "roleCode": f"QA_ACCOUNT_ENABLE_{label}_{suffix}", "roleName": f"员工启用验收 {label} {suffix}",
            "permissionIds": grants, "reason": "员工启用验收：建立独立权限范围",
        }, status=200))
    actor_role, target_role = state["roles"]
    actor_row, actor_email, actor_password = create_employee(admin, state, actor_role, suffix, "enable-actor")
    staff, email, initial = create_employee(admin, state, target_role, suffix, "enable-target")
    high, _, _ = create_employee(admin, state, target_role, suffix, "enable-admin")
    ordinary_login(actor, actor_email, actor_password)
    changed_password = ordinary_login(target, email, initial)
    assert enable_employee(target, actor_row, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    actor_row = account(admin, actor_row["accountId"])
    assert enable_employee(actor, actor_row, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    navigation = actor.request("GET", "/authorization/v1/navigation")["resources"]
    assert {r["resourceCode"] for r in navigation if r["resourceType"] == "BUTTON"} == {"OPERATIONS_ACCOUNT_ENABLE"}
    for method, path in (("POST", "/iam/v1/accounts"),
                         ("POST", f"/iam/v1/accounts/{staff['accountId']}/disable"),
                         ("PUT", f"/iam/v1/accounts/{staff['accountId']}/roles"),
                         ("DELETE", f"/iam/v1/accounts/{staff['accountId']}")):
        assert command(actor, method, path, {}, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    current = account(admin, staff["accountId"])
    disabled = disable_employee(admin, current)
    disabled_high = disable_employee(admin, high)
    disabled_high = assign(admin, disabled_high, "900000000000000510")
    assert enable_employee(actor, disabled_high, status=403)["code"] == "OPERATIONS_PERMISSION_DENIED"
    assert account(admin, high["accountId"])["status"] == "DISABLED"
    assert enable_employee(actor, current, status=409)["code"] == "CONCURRENT_REQUEST"
    assert enable_employee(actor, disabled, reason="", status=400)["code"] == "INVALID_REQUEST"
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 1
    target.request("GET", "/iam/v1/accounts", status=401)
    target.request("POST", "/auth/v1/refresh", {"refreshToken": target.refresh_token}, authenticated=False, status=401)
    retained = retained_state(disabled)
    epoch = int(mysql(f"SELECT auth_epoch FROM t_user_account WHERE account_id={int(staff['accountId'])};"))
    key = str(uuid.uuid4())
    enable_employee(actor, disabled, key=key)
    enable_employee(actor, disabled, key=key)
    assert enable_employee(actor, disabled, key=key, reason="不同的原因", status=409)["code"] == "CONCURRENT_REQUEST"
    assert enable_employee(actor, disabled, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    assert_enabled(disabled, epoch, retained)
    enabled = account(admin, staff["accountId"])
    assert enabled["status"] == "ACTIVE" and enabled["roles"] == disabled["roles"]
    assert not enabled["passwordChangeRequired"]
    assert role_list(admin)[target_role["roleId"]]["employeeCount"] == 1
    target.request("GET", "/iam/v1/accounts", status=401)
    target.request("POST", "/auth/v1/refresh", {"refreshToken": target.refresh_token}, authenticated=False, status=401)
    target.access = target.refresh_token = None
    # Admin + two initial-password handoffs use the five-login burst; allow one refill for fresh login.
    time.sleep(13)
    tokens = target.login(email, changed_password)
    assert not tokens["passwordChangeRequired"]
    target.request("GET", "/iam/v1/accounts")
    delete_employee(admin, account(admin, staff["accountId"]))
    state["closed"].add(staff["accountId"])
    assert enable_employee(admin, enabled, status=400)["code"] == "BUSINESS_CHECK_FAILED"
    bootstrap = mysql("SELECT account_id,version FROM t_user_account WHERE realm='OPERATIONS' AND account_type='BOOTSTRAP_ADMIN';")
    bootstrap_id, bootstrap_version = bootstrap.split("\t")
    assert enable_employee(admin, {"accountId": bootstrap_id, "version": int(bootstrap_version)}, status=400)["code"] == "BUSINESS_CHECK_FAILED"


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, actor, target = Browser(), Browser(), Browser()
    state = {"roles": [], "accounts": [], "closed": set()}
    admin.login(material["administratorEmail"], material["administratorPassword"])
    try:
        check(admin, actor, target, state)
    finally:
        cleanup(admin, actor, target, state)
    print("PASS: independent enable permission and grant ceiling, disabled-to-active transition, version/idempotency, "
          "retained credentials/roles/member/lockout, old session rejection, fresh login, CLOSED/Bootstrap rejection, complete audit.")
    print("Only this run's synthetic employees and roles were changed and deleted; administrator logged out.")


if __name__ == "__main__":
    main()
