#!/usr/bin/env python3
"""Explicit mor-test audit acceptance; only this run's synthetic role/employee are changed."""
import json
import secrets
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib.parse import urlencode

from account_deletion_smoke import cleanup, ordinary_login
from initialize import mysql
from roles_smoke import command, save_role
from smoke import Browser, ROOT, check_page


class AuditReader:
    def __init__(self, browser):
        self.browser = browser
        self.last_request = {}

    def get(self, event_id=None, status=200, **filters):
        route = "detail" if event_id else "list"
        # Respect the deployed per-principal 10/minute audit read budget.
        wait = self.last_request.get(route, 0) + 6.1 - time.monotonic()
        if wait > 0:
            time.sleep(wait)
        self.last_request[route] = time.monotonic()
        path = "/audit/v1/events" + ("/" + event_id if event_id else "")
        if filters:
            path += "?" + urlencode(filters)
        return self.browser.request("GET", path, status=status)


def prepare(admin, viewer, state):
    suffix = uuid.uuid4().hex[:12].upper()
    permissions = ["900000000000000524", "900000000000000522"]
    role = command(admin, "POST", "/iam/v1/roles", {
        "roleCode": "QA_AUDIT_" + suffix, "roleName": "审计验收 " + suffix,
        "permissionIds": permissions, "reason": "审计验收：建立无审计权限的合成角色",
    }, status=200)
    state["roles"].append(role)
    updated = save_role(admin, role, permissions, name=role["roleName"] + " 已更新")
    state["roles"][0] = updated
    email = "audit-check-" + suffix.lower() + "@example.test"
    initial = "Audit check initial " + secrets.token_urlsafe(18) + "!"
    staff = command(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": "审计验收：创建无审计权限的合成人员",
    }, status=200)
    state["accounts"].append(staff["accountId"])
    ordinary_login(viewer, email, initial)
    return role, updated


def event_for_role(role_id):
    event_id = mysql("SELECT audit_event_id FROM t_audit_event "
                     "WHERE action_code='OPERATIONS_ROLE_UPDATE' AND result='SUCCEEDED' "
                     f"AND resource_id='{int(role_id)}' ORDER BY occurred_time DESC,audit_event_id DESC LIMIT 1;")
    assert event_id.isdigit(), "The synthetic update must have one audit event"
    return event_id


def assert_detail(reader, event_id, before, after):
    detail = reader.get(event_id)
    assert set(detail) == {"event", "changes", "attributes"}
    event = detail["event"]
    assert event["auditEventId"] == event_id and event["resourceId"] == after["roleId"]
    changes = {row["key"]: row for row in detail["changes"]}
    assert changes["roleName"]["before"] == [before["roleName"]]
    assert changes["roleName"]["after"] == [after["roleName"]]
    assert changes["version"]["before"] == [str(before["version"])]
    assert changes["version"]["after"] == [str(after["version"])]
    assert set(changes["permissionId"]["before"]) == set(before["permissionIds"])
    assert set(changes["permissionId"]["after"]) == set(after["permissionIds"])
    assert len(changes["permissionId"]["before"]) == 2, "Repeated attribute keys must not collapse"
    assert all(row["label"] for row in detail["changes"])
    return event


def assert_filters(reader, event):
    expected = event["auditEventId"]
    for key in ("actorId", "actionCode", "result", "requestId"):
        page = reader.get(size=200, **{key: event[key]})
        check_page(page, size=200)
        assert page["items"] and all(row[key] == event[key] for row in page["items"])
        if key in ("actionCode", "requestId"):
            assert expected in {row["auditEventId"] for row in page["items"]}
    instant = datetime.fromisoformat(event["occurredTime"].replace("Z", "+00:00"))
    after = (instant + timedelta(microseconds=1)).isoformat()
    exact = {key: event[key] for key in ("actorId", "actionCode", "result", "requestId")}
    page = reader.get(occurredFrom=instant.isoformat(), occurredTo=after, **exact)
    assert page["total"] == 1 and page["items"][0]["auditEventId"] == expected
    empty = reader.get(occurredTo=instant.isoformat(), **exact)
    assert empty["total"] == 0 and not empty["items"], "Upper time bound is exclusive"
    one_nanosecond_later = instant.strftime("%Y-%m-%dT%H:%M:%S.") + f"{instant.microsecond * 1000 + 1:09d}Z"
    included = reader.get(occurredFrom=instant.isoformat(), occurredTo=one_nanosecond_later, **exact)
    assert included["total"] == 1, "Submicrosecond upper bound must include the exact stored instant"
    excluded = reader.get(occurredFrom=one_nanosecond_later, occurredTo=after, **exact)
    assert excluded["total"] == 0, "Submicrosecond lower bound must exclude the earlier stored instant"
    empty = reader.get(requestId="no-such-audit-" + uuid.uuid4().hex)
    assert empty["total"] == 0 and not empty["items"]


def assert_paging(reader):
    cutoff = datetime.now(timezone.utc).isoformat()
    first = reader.get(occurredTo=cutoff, page=1, size=10)
    second = reader.get(occurredTo=cutoff, page=2, size=10)
    check_page(first)
    check_page(second, page=2)
    assert first["total"] == second["total"] and first["total"] > 10
    assert {row["auditEventId"] for row in first["items"]}.isdisjoint(
        row["auditEventId"] for row in second["items"])
    order = [(datetime.fromisoformat(row["occurredTime"].replace("Z", "+00:00")),
              int(row["auditEventId"])) for row in first["items"]]
    assert order == sorted(order, reverse=True)


def assert_errors(reader, viewer, event_id):
    for filters in ({"occurredFrom": "invalid"}, {"result": "UNKNOWN"},
                    {"actorId": "a" * 129}, {"occurredFrom": "2026-09-05T00:00:00Z",
                                           "occurredTo": "2026-09-04T00:00:00Z"}):
        error = reader.get(status=400, **filters)
        assert error["code"] == "INVALID_REQUEST" and error["requestId"] and error["message"]
    error = reader.get("1", status=400)
    assert error["code"] == "BUSINESS_CHECK_FAILED" and error["requestId"]
    for path in ("/audit/v1/events", "/audit/v1/events/" + event_id):
        error = viewer.request("GET", path, status=403)
        assert error["code"] == "OPERATIONS_PERMISSION_DENIED"
        assert Browser().request("GET", path, status=401)["requestId"]


def assert_historical(reader):
    # Older simple events have a reason only; never fabricate a state comparison.
    rows = reader.get(actionCode="OPERATIONS_LOGIN", result="SUCCEEDED")
    event = rows["items"][0]
    detail = reader.get(event["auditEventId"])
    assert detail["event"] == event and not detail["changes"]


def main():
    material = json.loads((ROOT / "private/operations-material.json").read_text())
    admin, viewer = Browser(), Browser()
    state = {"roles": [], "accounts": [], "closed": set()}
    admin.login(material["administratorEmail"], material["administratorPassword"])
    try:
        before, after = prepare(admin, viewer, state)
        reader = AuditReader(admin)
        event_id = event_for_role(after["roleId"])
        event = assert_detail(reader, event_id, before, after)
        print("PASS: immutable detail and repeated before/after permission attributes", flush=True)
        assert_filters(reader, event)
        assert_paging(reader)
        print("PASS: individual/combined filters, UTC bounds and stable filtered paging", flush=True)
        assert_errors(reader, viewer, event_id)
        assert_historical(reader)
        print("PASS: shared errors, permission/anonymous denial and historical events", flush=True)
    finally:
        cleanup(admin, viewer, Browser(), state)
    print("PASS: audit acceptance completed; synthetic employee/role deleted and administrator logged out.")


if __name__ == "__main__":
    main()
