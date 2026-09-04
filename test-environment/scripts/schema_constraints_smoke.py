#!/usr/bin/env python3
"""Explicit mor-test HTTP/MySQL check: corrupt only this run's synthetic identity, then restore it."""
import json
import secrets
import uuid

from initialize import mysql
from roles_smoke import account, command
from smoke import Browser, ROOT


def number(value):
    value = str(value)
    assert value.isascii() and value.isdigit() and int(value) > 0
    return value


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, actor = Browser(), Browser()
    admin.login(material["administratorEmail"], material["administratorPassword"])
    role = created = context = None
    try:
        suffix = uuid.uuid4().hex[:12].upper()
        role = command(admin, "POST", "/iam/v1/roles", {
            "roleCode": "QA_SCHEMA_" + suffix, "roleName": "无外键边界验收 " + suffix,
            "permissionIds": ["900000000000000524", "900000000000000522"],
            "reason": "Schema约束改为代码校验验收"})
        initial, password = "Initial " + secrets.token_urlsafe(24) + "!", "Changed " + secrets.token_urlsafe(24) + "!"
        email = "schema-" + suffix.lower() + "@example.test"
        created = command(admin, "POST", "/iam/v1/accounts", {
            "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": "合成身份验收"})
        actor.login(email, initial)
        actor.change_password(initial, password)
        actor.login(email, password)
        context = actor.request("GET", "/auth/v1/context")
        member, tenant, session, role_id = map(number, [context["memberId"], context["tenantId"], context["sessionId"], role["roleId"]])
        actor.request("GET", "/iam/v1/roles")
        assert {row["resourceCode"] for row in actor.request("GET", "/authorization/v1/navigation")["resources"]} == {"OPERATIONS_SYSTEM", "OPERATIONS_ROLES"}
        try:
            mysql(f"UPDATE t_tenant_member_role SET tenant_id={tenant}+1 WHERE tenant_member_id={member};")
            actor.request("GET", "/iam/v1/roles", status=403)
            assert actor.request("GET", "/authorization/v1/navigation")["resources"] == []
        finally:
            mysql(f"UPDATE t_tenant_member_role SET tenant_id={tenant} WHERE tenant_member_id={member};")
        try:
            mysql(f"UPDATE t_role SET tenant_id={tenant}+1 WHERE role_id={role_id};")
            actor.request("GET", "/iam/v1/roles", status=403)
        finally:
            mysql(f"UPDATE t_role SET tenant_id={tenant} WHERE role_id={role_id};")
        for field, invalid, valid in [("channel", "CLIENT", "OPERATIONS"), ("authentication_method", "GOOGLE", "PASSWORD")]:
            try:
                mysql(f"UPDATE t_auth_session SET {field}='{invalid}' WHERE session_id={session};")
                actor.request("GET", "/auth/v1/context", status=401)
            finally:
                mysql(f"UPDATE t_auth_session SET {field}='{valid}' WHERE session_id={session};")
        actor.request("GET", "/iam/v1/roles")
        print("PASS: real HTTP/MySQL rejects mismatched Member/Tenant, Role Scope, Channel and authentication method; restored synthetic rows.")
    finally:
        if created:
            row = account(admin, created["accountId"])
            command(admin, "DELETE", "/iam/v1/accounts/" + created["accountId"], {
                "expectedVersion": row["version"], "expectedMemberVersion": row["memberVersion"], "reason": "关闭本次合成人员"})
        if role:
            command(admin, "DELETE", "/iam/v1/roles/" + role["roleId"], {
                "expectedVersion": role["version"], "reason": "关闭本次合成角色"})
        admin.request("POST", "/auth/v1/logout", {"refreshToken": admin.refresh_token}, status=(200, 204))


if __name__ == "__main__":
    main()
