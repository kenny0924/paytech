#!/usr/bin/env python3
"""Local HTTP deployment check. --complete-bootstrap explicitly completes the new test administrator setup."""
import argparse
import http.client
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = "http://localhost:28080"
os.umask(0o077)


class Browser:
    def __init__(self):
        self.access = None
        self.refresh_token = None

    def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
        headers = {"Accept": "application/json"}
        if method != "GET":
            headers.update({"Origin": ORIGIN, "Content-Type": "application/json"})
        if authenticated and self.access:
            headers["Authorization"] = "Bearer " + self.access
        headers.update(extra_headers or {})
        connection = http.client.HTTPConnection("localhost", 28080, timeout=20)
        connection.request(method, "/api/operations" + path,
                           json.dumps(body or {}) if method != "GET" else None, headers)
        response = connection.getresponse()
        raw = response.read()
        code = response.status
        cookies = response.headers.get_all("Set-Cookie") or []
        connection.close()
        expected = (status,) if isinstance(status, int) else status
        if code not in expected:
            try:
                error = json.loads(raw).get("code", "UNKNOWN")
            except (ValueError, UnicodeDecodeError):
                error = "NON_JSON"
            raise SystemExit(f"{method} {path}: HTTP {code}, code {error}; credentials and response omitted.")
        if cookies:
            raise SystemExit("JSON Token contract must not issue Set-Cookie.")
        return json.loads(raw) if raw else None

    def login(self, identifier, password):
        data = self.request("POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, False)
        assert data["accessToken"] and data["refreshToken"] and data["refreshExpiresIn"] > 0
        self.access = data["accessToken"]
        self.refresh_token = data["refreshToken"]
        return data

    def change_password(self, current, new):
        self.request("POST", "/auth/v1/password", {"currentPassword": current, "newPassword": new}, status=(200, 204))
        self.access = None
        self.refresh_token = None


def checkpoint(name):
    path = ROOT / "private" / name
    path.write_text("Completed through the test Gateway/BFF/Platform chain.\n")
    path.chmod(0o600)


def check_page(body, page=1, size=10):
    assert set(body) == {"items", "page", "size", "total"}, "Unexpected paging response contract."
    assert body["page"] == page and body["size"] == size
    assert isinstance(body["items"], list) and len(body["items"]) <= size
    assert isinstance(body["total"], int) and body["total"] >= len(body["items"])


def complete_bootstrap(browser, material):
    if not (ROOT / "private/administrator-created").exists():
        if not (ROOT / "private/bootstrap-password-changed").exists():
            tokens = browser.login("admin", material["bootstrapPassword"])
            assert tokens["passwordChangeRequired"]
            browser.change_password(material["bootstrapPassword"], material["changedBootstrapPassword"])
            checkpoint("bootstrap-password-changed")
        tokens = browser.login("admin", material["changedBootstrapPassword"])
        assert tokens["bootstrapAdministratorRequired"] and not tokens["passwordChangeRequired"]
        browser.request("POST", "/auth/v1/bootstrap/administrator", {
            "email": material["administratorEmail"], "initialPassword": material["administratorInitialPassword"]})
        browser.access = None
        browser.refresh_token = None
        checkpoint("administrator-created")
    if not (ROOT / "private/administrator-password-changed").exists():
        tokens = browser.login(material["administratorEmail"], material["administratorInitialPassword"])
        assert tokens["passwordChangeRequired"]
        browser.change_password(material["administratorInitialPassword"], material["administratorPassword"])
        checkpoint("administrator-password-changed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--complete-bootstrap", action="store_true")
    options = parser.parse_args()
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    browser = Browser()
    if options.complete_bootstrap:
        complete_bootstrap(browser, material)
    elif not (ROOT / "private/administrator-password-changed").exists():
        raise SystemExit("Test administrator has not been configured; explicitly run --complete-bootstrap once.")
    tokens = browser.login(material["administratorEmail"], material["administratorPassword"])
    assert not tokens["passwordChangeRequired"] and not tokens["bootstrapAdministratorRequired"]
    context = browser.request("GET", "/auth/v1/context")
    assert context["tenantId"] and context["memberId"]
    navigation = browser.request("GET", "/authorization/v1/navigation")
    expected_resources = {
        "OPERATIONS_SYSTEM": "系统管理",
        "OPERATIONS_ACCOUNTS": "人员管理",
        "OPERATIONS_ROLES": "角色管理",
        "OPERATIONS_AUDIT": "审计日志",
        "OPERATIONS_ACCOUNT_CREATE": "创建人员",
        "OPERATIONS_ACCOUNT_DISABLE": "禁用人员",
        "OPERATIONS_ACCOUNT_ASSIGN_ROLES": "分配角色",
        "OPERATIONS_ACCOUNT_DELETE": "删除人员",
        "OPERATIONS_ACCOUNT_ENABLE": "启用人员",
        "OPERATIONS_ROLE_CREATE": "创建角色",
        "OPERATIONS_ROLE_EDIT": "编辑角色",
        "OPERATIONS_ROLE_DELETE": "删除角色",
        "OPERATIONS_PLATFORM_CONFIGURATION": "平台配置",
        "OPERATIONS_GATEWAY_CONFIGURATION": "API 路由管理",
        "OPERATIONS_GATEWAY_CONFIGURATION_MANAGE": "管理配置",
    }
    assert {r["resourceCode"]: r["displayName"] for r in navigation["resources"]} == expected_resources, \
        "Operations navigation labels do not match the UTF-8 catalog."
    resources = {r["resourceCode"]: r for r in navigation["resources"]}
    system = resources["OPERATIONS_SYSTEM"]
    assert system["resourceType"] == "MENU" and system["parentResourceId"] is None
    assert system["frontendRoute"] is None and system["componentKey"] is None
    for code, route, order in [("OPERATIONS_ACCOUNTS", "/accounts", 10),
                               ("OPERATIONS_ROLES", "/roles", 20),
                               ("OPERATIONS_AUDIT", "/audit", 30)]:
        resource = resources[code]
        assert resource["resourceType"] == "MENU" and resource["parentResourceId"] == system["resourceId"]
        assert resource["frontendRoute"] == route and resource["componentKey"] == code
        assert resource["sortOrder"] == order
    platform_configuration = resources["OPERATIONS_PLATFORM_CONFIGURATION"]
    gateway_configuration = resources["OPERATIONS_GATEWAY_CONFIGURATION"]
    assert platform_configuration["resourceType"] == "MENU" and platform_configuration["parentResourceId"] is None
    assert gateway_configuration["resourceType"] == "MENU"
    assert gateway_configuration["parentResourceId"] == platform_configuration["resourceId"]
    assert gateway_configuration["frontendRoute"] == "/gateway-configuration"
    assert gateway_configuration["componentKey"] == "OPERATIONS_GATEWAY_CONFIGURATION"
    for resource in resources.values():
        if resource["resourceType"] == "BUTTON":
            if resource["resourceCode"] == "OPERATIONS_GATEWAY_CONFIGURATION_MANAGE":
                parent = "OPERATIONS_GATEWAY_CONFIGURATION"
            elif resource["resourceCode"].startswith("OPERATIONS_ROLE_"):
                parent = "OPERATIONS_ROLES"
            else:
                parent = "OPERATIONS_ACCOUNTS"
            assert resource["parentResourceId"] == resources[parent]["resourceId"]
    accounts = browser.request("GET", "/iam/v1/accounts")
    roles = browser.request("GET", "/iam/v1/roles")
    assert all(isinstance(role["employeeCount"], int) and role["employeeCount"] >= 0
               and role["version"] > 0 for role in roles["items"])
    audit = browser.request("GET", "/audit/v1/events")
    for page in (accounts, roles, audit):
        check_page(page)
    assert accounts["total"] >= 1 and roles["total"] >= 2 and audit["total"] > 0
    expected_roles = {"PLATFORM_ADMIN": "平台管理员", "OPERATIONS_VIEWER": "运营只读人员"}
    assert expected_roles.items() <= {r["roleCode"]: r["roleName"] for r in roles["items"]}.items(), \
        "Operations role labels do not match the UTF-8 catalog."
    targets = browser.request("GET", "/gateway-admin/v1/target-services")
    routes = browser.request("GET", "/gateway-admin/v1/routes?page=1&size=200")
    check_page(targets)
    check_page(routes, size=200)
    assert targets["total"] == 1 and routes["total"] == 27
    target = targets["items"][0]
    assert target["targetServiceCode"] == "operations-console-bff" and target["status"] == "ACTIVE"
    target_detail = browser.request("GET", "/gateway-admin/v1/target-services/operations-console-bff")
    assert target_detail["targetServiceCode"] == target["targetServiceCode"]
    expected_gateway_routes = {
        "operations-gateway-target-list",
        "operations-gateway-target-detail",
        "operations-gateway-target-status",
        "operations-gateway-route-list",
        "operations-gateway-route-detail",
        "operations-gateway-route-status",
    }
    gateway_routes = {route["routeCode"]: route for route in routes["items"]}
    assert expected_gateway_routes <= gateway_routes.keys()
    assert all(gateway_routes[code]["status"] == "ACTIVE" for code in expected_gateway_routes)
    route_detail = browser.request("GET", "/gateway-admin/v1/routes/operations-gateway-route-list")
    assert route_detail["targetServiceCode"] == "operations-console-bff"
    filtered_routes = browser.request(
        "GET", "/gateway-admin/v1/routes?routeCode=operations-gateway-route-list&channel=OPERATIONS"
        "&status=ACTIVE&targetServiceCode=operations-console-bff"
    )
    check_page(filtered_routes)
    assert filtered_routes["total"] == 1 and filtered_routes["items"][0]["routeCode"] == route_detail["routeCode"]
    for path in ("/iam/v1/accounts", "/iam/v1/roles", "/audit/v1/events"):
        for size in (20, 50, 100, 200):
            check_page(browser.request("GET", f"{path}?page=1&size={size}"), size=size)
    second_roles = browser.request("GET", "/iam/v1/roles?page=2&size=10")
    check_page(second_roles, page=2)
    assert second_roles["total"] == roles["total"]
    assert {r["roleId"] for r in roles["items"]}.isdisjoint(r["roleId"] for r in second_roles["items"])
    second_audit = browser.request("GET", "/audit/v1/events?page=2&size=10")
    check_page(second_audit, page=2)
    assert {row["auditEventId"] for row in audit["items"]}.isdisjoint(
        row["auditEventId"] for row in second_audit["items"])
    invalid_size = browser.request("GET", "/iam/v1/roles?size=11", status=400)
    assert invalid_size["code"] == "INVALID_REQUEST" and invalid_size["message"]
    invalid_page = browser.request("GET", "/iam/v1/accounts?page=0&size=20", status=400)
    assert invalid_page["code"] == "INVALID_REQUEST" and invalid_page["message"]
    same_password = browser.request("POST", "/auth/v1/password", {
        "currentPassword": material["administratorPassword"],
        "newPassword": material["administratorPassword"],
    }, status=400)
    assert same_password["code"] == "BUSINESS_CHECK_FAILED"
    assert same_password["message"] == "新密码不能与当前密码相同"
    original_token = browser.refresh_token
    refreshed = browser.request("POST", "/auth/v1/refresh", {"refreshToken": original_token}, authenticated=False)
    assert refreshed["refreshToken"] and refreshed["refreshToken"] != original_token
    assert refreshed["refreshExpiresIn"] > 0
    browser.access = refreshed["accessToken"]
    browser.refresh_token = refreshed["refreshToken"]
    browser.request("GET", "/auth/v1/context")
    browser.request("POST", "/auth/v1/logout", {"refreshToken": browser.refresh_token}, status=(200, 204))
    browser.request("POST", "/auth/v1/refresh", {"refreshToken": browser.refresh_token}, authenticated=False, status=401)
    browser.access = None
    browser.refresh_token = None
    access_file = ROOT / "private/access.md"
    access_file.write_text(f"# Code-MoR test 环境\n\n入口：{ORIGIN}\n\n账号：{material['administratorEmail']}\n\n"
                          f"密码：{material['administratorPassword']}\n\n仅用于本机 mor-test；禁止复用到生产。\n")
    access_file.chmod(0o600)
    print("PASS: JSON Token login, common paging defaults/sizes/database slices, navigation and role labels, "
          "Gateway control-plane reads, parameter/business messages, Refresh rotation, logout and revoked Refresh "
          "rejection; no cookies.")
    print("Test login details saved to test-environment/private/access.md (0600); no tokens persisted.")


if __name__ == "__main__":
    main()
