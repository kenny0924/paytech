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

    def request(self, method, path, body=None, authenticated=True, status=200):
        headers = {"Accept": "application/json"}
        if method != "GET":
            headers.update({"Origin": ORIGIN, "Content-Type": "application/json"})
        if authenticated and self.access:
            headers["Authorization"] = "Bearer " + self.access
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
    assert {"OPERATIONS_ACCOUNTS", "OPERATIONS_AUDIT"}.issubset({r["resourceCode"] for r in navigation["resources"]})
    accounts = browser.request("GET", "/iam/v1/accounts?page=1&size=20")
    roles = browser.request("GET", "/iam/v1/roles")
    audit = browser.request("GET", "/audit/v1/events?page=1&size=20")
    assert accounts["total"] >= 1 and len(roles) == 2 and audit["total"] > 0
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
    print("PASS: JSON Token login, authorized queries, parameter/business messages, Refresh rotation, logout and revoked Refresh rejection; no cookies.")
    print("Test login details saved to test-environment/private/access.md (0600); no tokens persisted.")


if __name__ == "__main__":
    main()
