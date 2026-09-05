#!/usr/bin/env python3
"""Public HTTP smoke for the Operations Gateway control plane."""

import http.client
import json
import time
import uuid

from smoke import Browser, ORIGIN, ROOT


ROUTE_CODE = "operations-gateway-target-detail"
TARGET_DETAIL_PATH = "/gateway-admin/v1/target-services/operations-console-bff"
ROUTE_DETAIL_PATH = f"/gateway-admin/v1/routes/{ROUTE_CODE}"
ROUTE_STATUS_PATH = f"{ROUTE_DETAIL_PATH}/status"


def change_status(browser, status, version, reason, key=None, expected_status=200):
    return browser.request(
        "PATCH",
        ROUTE_STATUS_PATH,
        {"status": status, "expectedVersion": version, "reason": reason},
        status=expected_status,
        extra_headers={"Idempotency-Key": key or str(uuid.uuid4())},
    )


def public_target_detail_status(browser):
    connection = http.client.HTTPConnection("localhost", 28080, timeout=20)
    connection.request(
        "GET",
        "/api/operations" + TARGET_DETAIL_PATH,
        headers={"Accept": "application/json", "Authorization": "Bearer " + browser.access},
    )
    response = connection.getresponse()
    response.read()
    status = response.status
    cookies = response.headers.get_all("Set-Cookie") or []
    connection.close()
    assert not cookies, "JSON Token contract must not issue Set-Cookie."
    return status


def wait_for_public_status(browser, expected_status):
    deadline = time.monotonic() + 15
    last_status = None
    while time.monotonic() < deadline:
        last_status = public_target_detail_status(browser)
        if last_status == expected_status:
            return
        time.sleep(1)
    raise AssertionError(f"Gateway Snapshot 未在期限内切换，最后 HTTP 状态为 {last_status}")


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    browser = Browser()
    changed = False
    original = None
    browser.login(material["administratorEmail"], material["administratorPassword"])
    try:
        unauthorized = browser.request(
            "GET", "/gateway-admin/v1/routes", authenticated=False, status=401
        )
        assert unauthorized["code"]

        original = browser.request("GET", ROUTE_DETAIL_PATH)
        assert original["routeCode"] == ROUTE_CODE and original["status"] == "ACTIVE"

        disable_key = str(uuid.uuid4())
        disabled = change_status(
            browser, "DISABLED", original["version"], "mor-test 部署验收：停用只读详情路由", disable_key
        )
        changed = disabled["status"] == "DISABLED"
        assert changed and disabled["version"] == original["version"] + 1
        assert change_status(
            browser, "DISABLED", original["version"], "mor-test 部署验收：停用只读详情路由", disable_key
        ) == disabled

        stale = change_status(
            browser,
            "ACTIVE",
            original["version"],
            "mor-test 部署验收：验证旧版本拒绝",
            expected_status=409,
        )
        assert stale["code"] == "CONCURRENT_REQUEST"
        wait_for_public_status(browser, 404)

        restored = change_status(
            browser, "ACTIVE", disabled["version"], "mor-test 部署验收：恢复只读详情路由"
        )
        changed = False
        assert restored["version"] == disabled["version"] + 1
        wait_for_public_status(browser, 200)
        assert browser.request("GET", ROUTE_DETAIL_PATH)["status"] == "ACTIVE"
    finally:
        if changed and original is not None:
            current = browser.request("GET", ROUTE_DETAIL_PATH)
            if current["status"] == "DISABLED":
                change_status(browser, "ACTIVE", current["version"], "mor-test 部署验收异常清理：恢复路由")
                wait_for_public_status(browser, 200)
        if browser.refresh_token:
            browser.request(
                "POST", "/auth/v1/logout", {"refreshToken": browser.refresh_token}, status=(200, 204)
            )

    print(
        "PASS: authenticated reads, authorization rejection, status change, idempotent replay, "
        "stale-version conflict, Snapshot removal and restoration."
    )


if __name__ == "__main__":
    main()
