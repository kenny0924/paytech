#!/usr/bin/env python3
"""Offline contract tests for the Risk Operations Route rollout adapter."""
import contextlib
import copy
import io
import json
from collections.abc import Mapping
from pathlib import Path
import sys
import unittest
from unittest import mock

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import risk_rule_configuration_rollout as rollout
from risk_rule_configuration_stage import (
    LEGACY_ACTIVE_RESOURCES,
    LEGACY_ROUTE_CODES,
    RISK_ACTIVE_RESOURCES,
)


ROUTE_ORDER = (
    "operations-risk-rule-authoring-context",
    "operations-risk-rule-set-list",
    "operations-risk-rule-set-detail",
    "operations-risk-test-candidate-list",
    "operations-risk-test-candidate-detail",
    "operations-risk-rule-set-save",
    "operations-risk-rule-set-freeze",
    "operations-risk-test-candidate-create",
    "operations-risk-test-candidate-validate",
)
STAGES = (
    "BASELINE",
    "AUTHORING_READ_ACTIVE",
    "RULE_SET_LIST_READ_ACTIVE",
    "RULE_SET_DETAIL_READ_ACTIVE",
    "CANDIDATE_LIST_READ_ACTIVE",
    "READ_ACTIVE",
    "SAVE_ACTIVE",
    "FREEZE_ACTIVE",
    "CREATE_ACTIVE",
    "ALL_ROUTES_ACTIVE",
)

READ_HEADERS = (
    {"headerName": "authorization", "headerUse": "CORS_REQUEST_ALLOWED"},
    {"headerName": "origin", "headerUse": "FORWARD_REQUEST"},
)
WRITE_HEADERS = (
    {"headerName": "authorization", "headerUse": "CORS_REQUEST_ALLOWED"},
    {"headerName": "content-type", "headerUse": "CORS_REQUEST_ALLOWED"},
    {"headerName": "idempotency-key", "headerUse": "CORS_REQUEST_ALLOWED"},
    {"headerName": "content-type", "headerUse": "FORWARD_REQUEST"},
    {"headerName": "idempotency-key", "headerUse": "FORWARD_REQUEST"},
    {"headerName": "origin", "headerUse": "FORWARD_REQUEST"},
)
GUARDS = (
    {"ruleType": "CONCURRENCY", "dimension": "AUTHENTICATED_PRINCIPAL", "capacity": None,
     "refillTokens": None, "refillPeriodMillis": None, "concurrencyLimit": 2},
    {"ruleType": "CONCURRENCY", "dimension": "CLIENT_IP", "capacity": None,
     "refillTokens": None, "refillPeriodMillis": None, "concurrencyLimit": 3},
    {"ruleType": "CONCURRENCY", "dimension": "SHARED_ROUTE", "capacity": None,
     "refillTokens": None, "refillPeriodMillis": None, "concurrencyLimit": 50},
    {"ruleType": "RATE_LIMIT", "dimension": "AUTHENTICATED_PRINCIPAL", "capacity": 10,
     "refillTokens": 10, "refillPeriodMillis": 60000, "concurrencyLimit": None},
    {"ruleType": "RATE_LIMIT", "dimension": "CLIENT_IP", "capacity": 10,
     "refillTokens": 30, "refillPeriodMillis": 60000, "concurrencyLimit": None},
    {"ruleType": "RATE_LIMIT", "dimension": "SHARED_ROUTE", "capacity": 300,
     "refillTokens": 300, "refillPeriodMillis": 60000, "concurrencyLimit": None},
)


def route_spec(method, external_path, downstream_path, permission_id, permission_code,
               body_limit, headers, query_parameters):
    return {
        "channel": "OPERATIONS",
        "httpMethod": method,
        "externalPathTemplate": external_path,
        "downstreamPathTemplate": downstream_path,
        "targetServiceCode": "operations-console-bff",
        "targetStatus": "ACTIVE",
        "securityProfileCode": "OPERATIONS_JWT",
        "authorizationMode": "HUMAN_PERMISSION",
        "permissionId": permission_id,
        "permissionCode": permission_code,
        "requiredScopeCode": None,
        "requestCookieMode": "DROP",
        "responseCookieMode": "NONE",
        "bodyLimitBytes": body_limit,
        "bodyReceiveTimeoutMillis": 5000,
        "responseTimeoutMillis": 10000,
        "headers": list(headers),
        "queryParameterNames": list(query_parameters),
        "guardRules": list(GUARDS),
        "createdBy": "1",
    }


ROUTE_SPECS = {
    "operations-risk-rule-authoring-context": route_spec(
        "GET", "/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}",
        "/decision-rule-sets/v1/authoring-context/{pointCode}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, READ_HEADERS, ()),
    "operations-risk-rule-set-list": route_spec(
        "GET", "/api/operations/decision-rule-sets/v1/rule-sets",
        "/decision-rule-sets/v1/rule-sets", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, READ_HEADERS,
        ("page", "pointCode", "ruleSetCode", "size", "status")),
    "operations-risk-rule-set-detail": route_spec(
        "GET", "/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}",
        "/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, READ_HEADERS, ()),
    "operations-risk-test-candidate-list": route_spec(
        "GET", "/api/operations/decision-releases/v1/candidates",
        "/decision-releases/v1/candidates", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, READ_HEADERS,
        ("page", "pointCode", "size", "status")),
    "operations-risk-test-candidate-detail": route_spec(
        "GET", "/api/operations/decision-releases/v1/candidates/{releaseId}",
        "/decision-releases/v1/candidates/{releaseId}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, READ_HEADERS, ()),
    "operations-risk-rule-set-save": route_spec(
        "POST", "/api/operations/decision-rule-sets/v1/rule-sets",
        "/decision-rule-sets/v1/rule-sets", "900000000000000537",
        "RISK_RULE_SET_SAVE", 65536, WRITE_HEADERS, ()),
    "operations-risk-rule-set-freeze": route_spec(
        "POST", "/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze",
        "/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze", "900000000000000538",
        "RISK_RULE_SET_FREEZE", 16384, WRITE_HEADERS, ()),
    "operations-risk-test-candidate-create": route_spec(
        "POST", "/api/operations/decision-releases/v1/candidates",
        "/decision-releases/v1/candidates", "900000000000000539",
        "RISK_TEST_CANDIDATE_CREATE", 16384, WRITE_HEADERS, ()),
    "operations-risk-test-candidate-validate": route_spec(
        "POST", "/api/operations/decision-releases/v1/candidates/{releaseId}/validate",
        "/decision-releases/v1/candidates/{releaseId}/validate", "900000000000000540",
        "RISK_TEST_CANDIDATE_VALIDATE", 16384, WRITE_HEADERS, ()),
}


def prefix_for(stage):
    if stage == "RESOURCES_EXPOSED":
        return len(ROUTE_ORDER)
    return STAGES.index(stage)


def plain(value):
    if isinstance(value, Mapping):
        return {key: plain(item) for key, item in value.items()}
    if isinstance(value, tuple):
        return [plain(item) for item in value]
    return value


class FakeAuthority:
    def __init__(self, stage="BASELINE"):
        self.stage = stage
        self.versions = {code: 1 for code in ROUTE_ORDER}
        self.reads = 0
        self.read_timeouts = []

    def set_stage(self, stage):
        self.stage = stage

    def routes(self):
        prefix = prefix_for(self.stage)
        result = {code: "ACTIVE" for code in LEGACY_ROUTE_CODES}
        result.update({code: "ACTIVE" if index < prefix else "DISABLED"
                       for index, code in enumerate(ROUTE_ORDER)})
        return result

    def resources(self):
        result = dict(LEGACY_ACTIVE_RESOURCES)
        if self.stage == "RESOURCES_EXPOSED":
            result.update(RISK_ACTIVE_RESOURCES)
        return result

    def read(self, timeout_seconds=None):
        self.reads += 1
        self.read_timeouts.append(timeout_seconds)
        resources = self.resources()
        risk_active = self.stage == "RESOURCES_EXPOSED"
        resource_rows = [
            {"resource_code": code, "display_name": label, "status": "ACTIVE", "version": 1}
            for code, label in LEGACY_ACTIVE_RESOURCES.items()
        ]
        resource_rows.extend(
            {"resource_code": code, "display_name": label,
             "status": "ACTIVE" if risk_active else "DISABLED", "version": 1}
            for code, label in RISK_ACTIVE_RESOURCES.items()
        )
        routes = self.routes()
        return {
            "stage": self.stage,
            "resources": resources,
            "routes": routes,
            "resource_rows": resource_rows,
            "route_rows": [
                {"route_code": code, "status": status,
                 "version": self.versions.get(code, 1)}
                for code, status in routes.items()
            ],
        }


class FakeLock:
    def __init__(self, journal, fail=False):
        self.journal = journal
        self.fail = fail

    def __call__(self):
        if self.fail:
            raise SystemExit("lock busy")
        return self

    def __enter__(self):
        self.journal.append(("lock", "enter"))
        return 1

    def __exit__(self, exc_type, exc, traceback):
        self.journal.append(("lock", "exit"))


class FakeBrowser:
    def __init__(self, authority, *, unknown_count=0, conflict=None,
                 short_page=False, route_drift=None, navigation_override=None):
        self.authority = authority
        self.unknown_count = unknown_count
        self.conflict = conflict
        self.short_page = short_page
        self.route_drift = route_drift
        self.navigation_override = navigation_override
        self.calls = []
        self.rate_limited_get_calls = []
        self.raw_calls = []
        self.access = "fake-access"
        self.refresh_token = "fake-refresh"
        self.login_calls = []
        self.deadline_calls = []
        self.deadline_checks = []
        self.raw_timeouts = []
        self._commands = {}

    def login(self, identifier, password):
        self.login_calls.append((identifier, password))
        self.access = "fake-access"
        self.refresh_token = "fake-refresh"
        return {"accessToken": self.access, "refreshToken": "fake-refresh",
                "refreshExpiresIn": 100, "passwordChangeRequired": False,
                "bootstrapAdministratorRequired": False}

    def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
        self.calls.append((method, path, copy.deepcopy(body), copy.deepcopy(extra_headers), status))
        if method == "POST" and path == "/auth/v1/logout":
            self.assertEqual(body, {"refreshToken": self.refresh_token})
            self.assertEqual(status, (200, 204))
            return None
        if method == "GET" and path.startswith("/gateway-admin/v1/routes?"):
            query = dict(part.split("=", 1) for part in path.split("?", 1)[1].split("&"))
            self.assertEqual(query.get("channel"), "OPERATIONS")
            page, size = int(query["page"]), int(query["size"])
            rows = []
            for code, route_status in sorted(self.authority.routes().items()):
                if self.route_drift and code == self.route_drift[0]:
                    route_status = self.route_drift[1]
                rows.append(route_summary(code, route_status, self.authority.versions.get(code, 1)))
            start = (page - 1) * size
            items = rows[start:start + size]
            if self.short_page and page == 2:
                items = items[:-1]
            return {"items": items, "page": page, "size": size, "total": 36}
        if method == "GET" and path.startswith("/gateway-admin/v1/routes/"):
            code = path.rsplit("/", 1)[1]
            return route_detail(code, self.authority.routes()[code], self.authority.versions[code])
        if method == "GET" and path == "/authorization/v1/navigation":
            resources = self.navigation_override
            if resources is None:
                resources = [navigation_row(code, label, index)
                             for index, (code, label) in enumerate(self.authority.resources().items(), 1)]
            return {"resources": copy.deepcopy(resources)}
        raise AssertionError("unexpected request: " + method + " " + path)

    def new_deadline(self, timeout_seconds):
        self.deadline_calls.append(timeout_seconds)
        return 1000.0 + timeout_seconds

    def remaining_before_deadline(self, deadline):
        self.deadline_checks.append(deadline)
        return deadline - 1000.0

    def request_rate_limited_get(self, path, max_retry_after_seconds, deadline):
        self.remaining_before_deadline(deadline)
        self.rate_limited_get_calls.append(
            (path, max_retry_after_seconds, deadline)
        )
        return self.request("GET", path)

    def request_raw(self, method, path, body, authenticated=True, status=200,
                    extra_headers=None, timeout_seconds=20):
        self.raw_timeouts.append(timeout_seconds)
        self.raw_calls.append((method, path, body, copy.deepcopy(extra_headers), status))
        if method != "PATCH" or not path.startswith("/gateway-admin/v1/routes/") or not path.endswith("/status"):
            raise AssertionError("unexpected raw request")
        code = path.split("/")[-2]
        key = (extra_headers or {}).get("Idempotency-Key")
        payload = json.loads(body)
        self.calls.append((method, path, payload, copy.deepcopy(extra_headers)))
        if key in self._commands:
            previous_body, previous_response = self._commands[key]
            if body != previous_body:
                raise AssertionError("idempotency replay changed bytes")
            if self.unknown_count:
                self.unknown_count -= 1
                raise TimeoutError("still unknown")
            return copy.deepcopy(previous_response)
        if self.conflict:
            if self.conflict == "target":
                self._apply(code, payload["status"])
            elif self.conflict == "other":
                other = ROUTE_ORDER[min(ROUTE_ORDER.index(code) + 1, len(ROUTE_ORDER) - 1)]
                self.authority.versions[other] += 1
            return {"type": "about:blank", "title": "Conflict",
                    "detail": "concurrent",
                    "code": "CONCURRENT_REQUEST", "requestId": "0123456789abcdef",
                    "timestamp": "2026-09-07T00:00:00.123456Z"}
        response = self._apply(code, payload["status"])
        self._commands[key] = (body, response)
        if self.unknown_count:
            self.unknown_count -= 1
            raise TimeoutError("unknown")
        return copy.deepcopy(response)

    def request_raw_response(self, method, path, body, authenticated=True,
                             status=200, extra_headers=None, timeout_seconds=20):
        response = self.request_raw(
            method,
            path,
            body,
            authenticated=authenticated,
            status=status,
            extra_headers=extra_headers,
            timeout_seconds=timeout_seconds,
        )
        response_status = 409 if self.conflict else 200
        response_headers = {
            "content-type": ("application/problem+json",),
            "x-request-id": ("0123456789abcdef",),
            "date": ("Mon, 07 Sep 2026 00:00:00 GMT",),
            "x-timestamp": (),
            "retry-after": (),
        } if response_status == 409 else {}
        return response, response_headers, response_status

    def _apply(self, code, target_status):
        statuses = self.authority.routes()
        old_status = statuses[code]
        index = ROUTE_ORDER.index(code)
        if old_status != target_status:
            self.authority.versions[code] += 1
            prefix = prefix_for(self.authority.stage)
            self.authority.stage = STAGES[prefix + (1 if target_status == "ACTIVE" else -1)]
        return {"routeCode": code, "status": target_status,
                "version": self.authority.versions[code]}

    def assertEqual(self, actual, expected):
        if actual != expected:
            raise AssertionError("expected %r, got %r" % (expected, actual))


def route_summary(code, status, version):
    if code in ROUTE_SPECS:
        spec = ROUTE_SPECS[code]
        return {
            "routeCode": code, "channel": "OPERATIONS", "httpMethod": spec["httpMethod"],
            "externalPathTemplate": spec["externalPathTemplate"],
            "targetServiceCode": "operations-console-bff", "targetStatus": "ACTIVE",
            "securityProfileCode": "OPERATIONS_JWT", "authorizationMode": "HUMAN_PERMISSION",
            "status": status, "version": version,
        }
    return {
        "routeCode": code, "channel": "OPERATIONS", "httpMethod": "GET",
        "externalPathTemplate": "/api/operations/legacy/" + code,
        "targetServiceCode": "operations-console-bff", "targetStatus": "ACTIVE",
        "securityProfileCode": "OPERATIONS_JWT", "authorizationMode": "HUMAN_PERMISSION",
        "status": status, "version": version,
    }


def route_detail(code, status, version):
    result = {"routeCode": code}
    result.update(copy.deepcopy(ROUTE_SPECS[code]))
    result.update({
        "status": status,
        "version": version,
        "updatedBy": "1",
        "createTime": "2026-09-07T00:00:00Z",
        "updateTime": "2026-09-07T00:00:00Z",
    })
    return result


def navigation_row(code, label, index):
    return {
        "resourceId": str(900000000000000000 + index),
        "resourceCode": code,
        "resourceType": "MENU",
        "parentResourceId": None,
        "displayName": label,
        "frontendRoute": None,
        "componentKey": None,
        "sortOrder": index,
    }


class FakeCliObservation:
    def __init__(self, stage):
        self.stage = stage


class FakeCliOperator:
    def __init__(self, journal, failure=None):
        self.journal = journal
        self.failure = failure

    def _run(self, action, route_code=None):
        self.journal.append((action, route_code))
        if self.failure is not None:
            raise SystemExit(self.failure)
        return FakeCliObservation("ALL_ROUTES_ACTIVE")

    def inspect_stage(self):
        return self._run("inspect")

    def activate_route(self, route_code):
        return self._run("activate", route_code)

    def disable_route(self, route_code):
        return self._run("disable", route_code)

    def reconcile_baseline(self):
        return self._run("reconcile")

    def close(self):
        self.journal.append(("close", None))


class RolloutTest(unittest.TestCase):
    def operator(self, authority, **browser_options):
        journal = []
        browser = FakeBrowser(authority, **browser_options)
        keys = iter("key-%02d" % index for index in range(100))
        operator = rollout.RouteRollout(
            browser=browser,
            persisted_reader=authority.read,
            lock_factory=FakeLock(journal),
            idempotency_key_factory=lambda: next(keys),
        )
        return operator, browser, journal

    def test_constants_use_the_shared_activation_order_and_exact_frozen_matrix(self):
        self.assertEqual(ROUTE_ORDER, rollout.ROUTE_ORDER)
        self.assertEqual(STAGES, rollout.ROUTE_STAGES)
        self.assertEqual(ROUTE_SPECS, plain(rollout.ROUTE_MATRIX))

    def test_inspect_reads_complete_authority_paging_details_and_navigation(self):
        authority = FakeAuthority("READ_ACTIVE")
        operator, browser, journal = self.operator(authority)

        observation = operator.inspect_stage()

        self.assertEqual("READ_ACTIVE", observation.stage)
        pages = [call for call in browser.calls if call[1].startswith("/gateway-admin/v1/routes?")]
        self.assertEqual(4, len(pages))
        self.assertEqual(9, len([call for call in browser.calls
                                if call[1].startswith("/gateway-admin/v1/routes/")]))
        self.assertEqual(14, len(browser.rate_limited_get_calls))
        self.assertTrue(all(limit == 6 for _, limit, _ in browser.rate_limited_get_calls))
        self.assertEqual({1090.0}, {deadline for _, _, deadline in browser.rate_limited_get_calls})
        self.assertEqual([90], browser.deadline_calls)
        self.assertEqual([90.0], authority.read_timeouts)
        self.assertEqual(1, len([call for call in browser.calls
                                if call[1] == "/authorization/v1/navigation"]))
        self.assertEqual([], journal)

    def test_every_exact_adjacent_forward_and_reverse_edge(self):
        for index, code in enumerate(ROUTE_ORDER):
            with self.subTest(direction="forward", code=code):
                authority = FakeAuthority(STAGES[index])
                operator, browser, journal = self.operator(authority)
                self.assertEqual(STAGES[index + 1], operator.activate_route(code).stage)
                self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)
                self.assertEqual("/gateway-admin/v1/routes/%s/status" % code,
                                 browser.raw_calls[0][1])
                self.assertEqual("/api/operations/gateway-admin/v1/routes/%s/status" % code,
                                 "/api/operations" + browser.raw_calls[0][1])
                payload = json.loads(browser.raw_calls[0][2])
                self.assertEqual({"status": "ACTIVE", "expectedVersion": 1,
                                  "reason": "Risk 规则配置分阶段启用"}, payload)
                self.assertEqual({"Idempotency-Key": "key-00"}, browser.raw_calls[0][3])
                self.assertEqual([180], browser.deadline_calls)
                self.assertEqual([180.0, 180.0], authority.read_timeouts)
                self.assertEqual([20.0], browser.raw_timeouts)
                self.assertEqual(28, len(browser.rate_limited_get_calls))
                self.assertEqual(
                    {1180.0},
                    {deadline for _, _, deadline in browser.rate_limited_get_calls},
                )
            with self.subTest(direction="reverse", code=code):
                authority = FakeAuthority(STAGES[index + 1])
                operator, browser, journal = self.operator(authority)
                self.assertEqual(STAGES[index], operator.disable_route(code).stage)
                self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)
                payload = json.loads(browser.raw_calls[0][2])
                self.assertEqual({"status": "DISABLED", "expectedVersion": 1,
                                  "reason": "Risk 规则配置分阶段回滚"}, payload)
                self.assertEqual([180], browser.deadline_calls)
                self.assertEqual([180.0, 180.0], authority.read_timeouts)
                self.assertEqual([20.0], browser.raw_timeouts)
                self.assertEqual(28, len(browser.rate_limited_get_calls))
                self.assertEqual(
                    {1180.0},
                    {deadline for _, _, deadline in browser.rate_limited_get_calls},
                )

    def test_already_complete_exact_target_is_a_read_only_noop(self):
        authority = FakeAuthority("AUTHORING_READ_ACTIVE")
        operator, browser, journal = self.operator(authority)
        self.assertEqual("AUTHORING_READ_ACTIVE", operator.activate_route(ROUTE_ORDER[0]).stage)
        self.assertEqual([], browser.raw_calls)
        self.assertEqual([180.0], authority.read_timeouts)
        self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)

        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        self.assertEqual("BASELINE", operator.disable_route(ROUTE_ORDER[0]).stage)
        self.assertEqual([], browser.raw_calls)
        self.assertEqual([180.0], authority.read_timeouts)
        self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)

    def test_deadline_exhaustion_before_patch_does_not_mutate_and_after_patch_stops(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        deadline_checks = [0]

        def expire_before_patch(deadline):
            deadline_checks[0] += 1
            if deadline_checks[0] == 17:
                raise SystemExit("deadline exhausted")
            return 100.0

        browser.remaining_before_deadline = expire_before_patch
        with self.assertRaisesRegex(SystemExit, "deadline"):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual([], browser.raw_calls)
        self.assertEqual("BASELINE", authority.stage)
        self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)

        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        deadline_checks = [0]

        def expire_on_post_read(deadline):
            deadline_checks[0] += 1
            if deadline_checks[0] == 18:
                raise SystemExit("deadline exhausted")
            return 100.0

        browser.remaining_before_deadline = expire_on_post_read
        with self.assertRaisesRegex(SystemExit, "deadline"):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual(1, len(browser.raw_calls))
        self.assertEqual(1, authority.reads)
        self.assertEqual("AUTHORING_READ_ACTIVE", authority.stage)
        self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)

    def test_reconcile_deadline_after_first_committed_patch_never_touches_second_route(self):
        authority = FakeAuthority("RULE_SET_LIST_READ_ACTIVE")
        operator, browser, journal = self.operator(authority)
        deadline_checks = [0]

        def expire_before_second_patch(deadline):
            deadline_checks[0] += 1
            if deadline_checks[0] == 35:
                raise SystemExit("deadline exhausted")
            return 100.0

        browser.remaining_before_deadline = expire_before_second_patch
        with self.assertRaisesRegex(SystemExit, "deadline"):
            operator.reconcile_baseline()

        self.assertEqual(1, len(browser.raw_calls))
        self.assertEqual(
            ROUTE_ORDER[1], browser.raw_calls[0][1].split("/")[-2]
        )
        self.assertEqual(2, authority.reads)
        self.assertEqual("AUTHORING_READ_ACTIVE", authority.stage)
        self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)

    def test_rate_limit_wait_and_all_observation_gets_remain_inside_one_lock(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        original_get = browser.request_rate_limited_get
        original_patch = browser.request_raw
        first_get = [True]

        def tracked_get(*args, **kwargs):
            journal.append(("get", args[0]))
            if first_get[0]:
                journal.append(("retry-after-wait", 6))
                first_get[0] = False
            return original_get(*args, **kwargs)

        def tracked_patch(*args, **kwargs):
            journal.append(("patch", args[1]))
            return original_patch(*args, **kwargs)

        browser.request_rate_limited_get = tracked_get
        browser.request_raw = tracked_patch
        self.assertEqual(
            "AUTHORING_READ_ACTIVE",
            operator.activate_route(ROUTE_ORDER[0]).stage,
        )

        self.assertEqual(("lock", "enter"), journal[0])
        self.assertEqual(("lock", "exit"), journal[-1])
        self.assertEqual(1, journal.count(("retry-after-wait", 6)))
        patch_index = next(
            index for index, event in enumerate(journal) if event[0] == "patch"
        )
        self.assertTrue(all(event[0] == "get" for event in journal[1:patch_index]
                            if event[0] != "retry-after-wait"))
        self.assertTrue(any(event[0] == "get" for event in journal[patch_index + 1:-1]))

    def test_skipped_forward_reverse_and_individually_matching_route_are_rejected(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "adjacent"):
            operator.activate_route(ROUTE_ORDER[1])
        self.assertEqual([], browser.raw_calls)

        authority = FakeAuthority("READ_ACTIVE")
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "adjacent"):
            operator.disable_route(ROUTE_ORDER[2])
        self.assertEqual([], browser.raw_calls)

        authority = FakeAuthority("RULE_SET_LIST_READ_ACTIVE")
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "adjacent"):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual([], browser.raw_calls)

    def test_reconcile_walks_every_hidden_prefix_back_to_baseline(self):
        for index, stage in enumerate(STAGES[1:], 1):
            with self.subTest(stage=stage):
                authority = FakeAuthority(stage)
                operator, browser, journal = self.operator(authority)
                self.assertEqual("BASELINE", operator.reconcile_baseline().stage)
                self.assertEqual(list(reversed(ROUTE_ORDER[:index])),
                                 [call[1].split("/")[-2] for call in browser.raw_calls])
                self.assertEqual([("lock", "enter"), ("lock", "exit")], journal)
                self.assertEqual([720], browser.deadline_calls)
                self.assertEqual([720.0] * (index + 1), authority.read_timeouts)
                self.assertEqual([20.0] * index, browser.raw_timeouts)
                self.assertEqual(14 * (index + 1), len(browser.rate_limited_get_calls))
                self.assertEqual(
                    {1720.0},
                    {deadline for _, _, deadline in browser.rate_limited_get_calls},
                )

    def test_all_five_read_prefixes_are_durable_and_inspectable(self):
        for stage in STAGES[1:6]:
            with self.subTest(stage=stage):
                authority = FakeAuthority(stage)
                operator, browser, journal = self.operator(authority)
                self.assertEqual(stage, operator.inspect_stage().stage)
                self.assertEqual([], browser.raw_calls)

    def test_rejects_incomplete_paging_immutable_detail_drift_and_database_http_disagreement(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority, short_page=True)
        with self.assertRaisesRegex(SystemExit, "page|catalog"):
            operator.inspect_stage()

        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        original = browser.request
        def drifted_request(method, path, *args, **kwargs):
            body = original(method, path, *args, **kwargs)
            if path.endswith("/operations-risk-rule-set-save"):
                body["bodyLimitBytes"] = 65535
            return body
        browser.request = drifted_request
        with self.assertRaisesRegex(SystemExit, "detail"):
            operator.inspect_stage()

        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(
            authority, route_drift=(ROUTE_ORDER[0], "ACTIVE"))
        with self.assertRaisesRegex(SystemExit, "HTTP|authority"):
            operator.inspect_stage()

    def test_route_page_numeric_metadata_requires_json_integers_not_equal_bool_or_float(self):
        replacements = (
            ("page", True),
            ("page", 1.0),
            ("size", True),
            ("size", 10.0),
            ("total", True),
            ("total", 36.0),
        )
        for field, replacement in replacements:
            with self.subTest(field=field, replacement=repr(replacement)):
                authority = FakeAuthority("BASELINE")
                operator, browser, journal = self.operator(authority)
                original = browser.request
                def wrong_numeric_type(method, path, *args, **kwargs):
                    body = original(method, path, *args, **kwargs)
                    if path.startswith("/gateway-admin/v1/routes?"):
                        body[field] = replacement
                    return body
                browser.request = wrong_numeric_type
                with self.assertRaisesRegex(SystemExit, "page metadata"):
                    operator.inspect_stage()
                page_calls = [call for call in browser.calls
                              if call[1].startswith("/gateway-admin/v1/routes?")]
                self.assertEqual(1, len(page_calls), "wrong JSON type must fail on its first page")

    def test_route_detail_matrix_compares_recursive_json_types_and_values(self):
        mutations = (
            ("body-limit-bool", lambda body: body.__setitem__("bodyLimitBytes", False)),
            ("body-limit-float", lambda body: body.__setitem__("bodyLimitBytes", 0.0)),
            ("guard-number-float", lambda body: body["guardRules"][0].__setitem__(
                "concurrencyLimit", 2.0)),
            ("guard-null-bool", lambda body: body["guardRules"][0].__setitem__(
                "capacity", False)),
            ("headers-list-tuple", lambda body: body.__setitem__(
                "headers", tuple(body["headers"]))),
        )
        code = ROUTE_ORDER[0]
        for name, mutate in mutations:
            with self.subTest(mutation=name):
                authority = FakeAuthority("BASELINE")
                operator, browser, journal = self.operator(authority)
                original = browser.request
                def wrong_json_type(method, path, *args, **kwargs):
                    body = original(method, path, *args, **kwargs)
                    if path.endswith("/" + code):
                        mutate(body)
                    return body
                browser.request = wrong_json_type
                with self.assertRaisesRegex(SystemExit, "immutable matrix"):
                    operator.inspect_stage()

    def test_route_mutation_response_rejects_equal_float_version(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        original = browser.request_raw
        def float_version(*args, **kwargs):
            response = original(*args, **kwargs)
            response["version"] = float(response["version"])
            return response
        browser.request_raw = float_version
        with self.assertRaisesRegex(SystemExit, "response"):
            operator.activate_route(ROUTE_ORDER[0])

    def test_rejects_non_prefix_and_legacy_catalog_without_synthesizing_rows(self):
        authority = FakeAuthority("BASELINE")
        original = authority.routes
        def non_prefix():
            result = original()
            result[ROUTE_ORDER[1]] = "ACTIVE"
            return result
        authority.routes = non_prefix
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "closed|prefix"):
            operator.inspect_stage()

        authority = FakeAuthority("BASELINE")
        original_read = authority.read
        def legacy_read(timeout_seconds=None):
            result = original_read()
            result["stage"] = "LEGACY"
            result["routes"] = {code: "ACTIVE" for code in LEGACY_ROUTE_CODES}
            result["route_rows"] = [row for row in result["route_rows"]
                                    if row["route_code"] in LEGACY_ROUTE_CODES]
            return result
        authority.read = legacy_read
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "36|legacy|catalog"):
            operator.inspect_stage()

    def test_persisted_row_statuses_must_match_the_classified_status_map(self):
        authority = FakeAuthority("BASELINE")
        original_read = authority.read
        def mismatched_row_read(timeout_seconds=None):
            result = original_read()
            row = next(item for item in result["route_rows"]
                       if item["route_code"] == ROUTE_ORDER[0])
            row["status"] = "ACTIVE"
            return result
        authority.read = mismatched_row_read
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "rows disagree"):
            operator.inspect_stage()

    def test_route_disable_uses_persisted_resource_rows_not_navigation_absence(self):
        authority = FakeAuthority("RESOURCES_EXPOSED")
        navigation = [navigation_row(code, label, index)
                      for index, (code, label) in enumerate(LEGACY_ACTIVE_RESOURCES.items(), 1)]
        operator, browser, journal = self.operator(authority, navigation_override=navigation)
        with self.assertRaisesRegex(SystemExit, "RESOURCES_MUST_BE_DISABLED_FIRST"):
            operator.disable_route(ROUTE_ORDER[-1])
        self.assertEqual([], browser.raw_calls)

    def test_visible_resources_refuse_forward_route_action_with_the_same_stop_code(self):
        authority = FakeAuthority("RESOURCES_EXPOSED")
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "RESOURCES_MUST_BE_DISABLED_FIRST"):
            operator.activate_route(ROUTE_ORDER[-1])
        self.assertEqual([], browser.raw_calls)

        authority = FakeAuthority("ALL_ROUTES_ACTIVE")
        original_read = authority.read
        def visible_row_read(timeout_seconds=None):
            result = original_read(timeout_seconds=timeout_seconds)
            result["resource_rows"][-1]["status"] = "ACTIVE"
            return result
        authority.read = visible_row_read
        operator, browser, journal = self.operator(authority)
        with self.assertRaisesRegex(SystemExit, "RESOURCES_MUST_BE_DISABLED_FIRST"):
            operator.disable_route(ROUTE_ORDER[-1])
        self.assertEqual([], browser.raw_calls)

    def test_lock_contention_prevents_pre_read_patch_and_post_read(self):
        authority = FakeAuthority("BASELINE")
        browser = FakeBrowser(authority)
        operator = rollout.RouteRollout(
            browser=browser, persisted_reader=authority.read,
            lock_factory=FakeLock([], fail=True), idempotency_key_factory=lambda: "never")
        with self.assertRaisesRegex(SystemExit, "lock"):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual(0, authority.reads)
        self.assertEqual([], browser.calls)

    def test_unknown_replays_once_with_identical_bytes_and_key_then_settles(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority, unknown_count=1)

        self.assertEqual("AUTHORING_READ_ACTIVE", operator.activate_route(ROUTE_ORDER[0]).stage)

        self.assertEqual(2, len(browser.raw_calls))
        self.assertIs(browser.raw_calls[0][2], browser.raw_calls[1][2])
        self.assertEqual(browser.raw_calls[0][2], browser.raw_calls[1][2])
        self.assertEqual(browser.raw_calls[0][3], browser.raw_calls[1][3])

    def test_two_unknown_results_stop_without_touching_another_route(self):
        authority = FakeAuthority("RULE_SET_LIST_READ_ACTIVE")
        operator, browser, journal = self.operator(authority, unknown_count=2)
        with self.assertRaisesRegex(
                SystemExit,
                "^%s UNKNOWN_REQUIRES_RECONCILIATION$" % ROUTE_ORDER[1]):
            operator.reconcile_baseline()
        self.assertEqual([ROUTE_ORDER[1], ROUTE_ORDER[1]],
                         [call[1].split("/")[-2] for call in browser.raw_calls])

    def test_deadline_before_settlement_replay_keeps_the_unknown_route_diagnostic(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority, unknown_count=1)
        deadline_checks = [0]

        def expire_before_replay(deadline):
            deadline_checks[0] += 1
            if deadline_checks[0] == 18:
                raise SystemExit("generic deadline")
            return 100.0

        browser.remaining_before_deadline = expire_before_replay
        with self.assertRaisesRegex(
            SystemExit,
            "^%s UNKNOWN_REQUIRES_RECONCILIATION$" % ROUTE_ORDER[0],
        ):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual(1, len(browser.raw_calls))
        self.assertEqual("AUTHORING_READ_ACTIVE", authority.stage)

    def test_409_is_settled_only_by_complete_authoritative_target_observation(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority, conflict="target")
        self.assertEqual("AUTHORING_READ_ACTIVE", operator.activate_route(ROUTE_ORDER[0]).stage)
        self.assertEqual(1, len(browser.raw_calls))

        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority, conflict="other")
        with self.assertRaisesRegex(SystemExit, "concurrent"):
            operator.activate_route(ROUTE_ORDER[0])

    def test_409_requires_the_strict_current_problem_contract(self):
        for corruption in ("media-type", "forbidden-status"):
            with self.subTest(corruption=corruption):
                authority = FakeAuthority("BASELINE")
                operator, browser, journal = self.operator(
                    authority, conflict="target"
                )
                original = browser.request_raw_response
                def invalid_problem(*args, **kwargs):
                    body, headers, status = original(*args, **kwargs)
                    if corruption == "media-type":
                        headers["content-type"] = ("application/json",)
                    else:
                        body["status"] = 409
                    return body, headers, status
                browser.request_raw_response = invalid_problem
                with self.assertRaises(AssertionError):
                    operator.activate_route(ROUTE_ORDER[0])
                self.assertEqual(1, len(browser.raw_calls))

    def test_problem_shaped_200_is_never_misclassified_as_a_409(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        original = browser.request_raw_response

        def misleading_success(*args, **kwargs):
            response, headers, status = original(*args, **kwargs)
            return {
                "type": "about:blank",
                "title": "Conflict",
                "detail": "misleading body",
                "code": "CONCURRENT_REQUEST",
                "requestId": "0123456789abcdef",
                "timestamp": "2026-09-07T00:00:00.123456Z",
            }, headers, status

        browser.request_raw_response = misleading_success
        with self.assertRaisesRegex(SystemExit, "response"):
            operator.activate_route(ROUTE_ORDER[0])
        self.assertEqual("AUTHORING_READ_ACTIVE", authority.stage)
        self.assertEqual(1, len(browser.raw_calls))

    def test_success_response_and_post_read_must_match_exact_target(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        original = browser.request_raw
        def wrong_success(*args, **kwargs):
            result = original(*args, **kwargs)
            result["routeCode"] = ROUTE_ORDER[1]
            return result
        browser.request_raw = wrong_success
        with self.assertRaisesRegex(SystemExit, "response"):
            operator.activate_route(ROUTE_ORDER[0])

    def test_cli_has_four_mutually_exclusive_actions_and_no_action_is_inert(self):
        dependencies = mock.Mock()
        self.assertEqual(0, rollout.main([], dependency_factory=dependencies))
        dependencies.assert_not_called()

        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                rollout.main(["--inspect-stage", "--reconcile-baseline"],
                             dependency_factory=dependencies)
            with self.assertRaises(SystemExit):
                rollout.main(["--activate-route", "not-approved"],
                             dependency_factory=dependencies)
        dependencies.assert_not_called()

    def test_cli_rejects_non_loopback_before_loading_material_or_reading_database(self):
        dependencies = mock.Mock(side_effect=AssertionError("must not load dependencies"))
        with self.assertRaisesRegex(SystemExit, "loopback"):
            rollout.main(["--base-url", "https://example.test", "--inspect-stage"],
                         dependency_factory=dependencies)
        dependencies.assert_not_called()

    def test_cli_closes_authenticated_operator_for_every_action_and_outcome(self):
        actions = (
            (["--inspect-stage"], ("inspect", None)),
            (["--activate-route", ROUTE_ORDER[0]], ("activate", ROUTE_ORDER[0])),
            (["--disable-route", ROUTE_ORDER[0]], ("disable", ROUTE_ORDER[0])),
            (["--reconcile-baseline"], ("reconcile", None)),
        )
        outcomes = (
            None,
            "BUSINESS_REJECTED",
            "Route command encountered concurrent drift; inspect before retrying",
            ROUTE_ORDER[0] + " UNKNOWN_REQUIRES_RECONCILIATION",
        )
        for argv, action_entry in actions:
            for failure in outcomes:
                with self.subTest(argv=argv, outcome=failure or "success"):
                    journal = []
                    operator = FakeCliOperator(journal, failure=failure)
                    dependencies = mock.Mock(return_value=operator)
                    output = io.StringIO()
                    if failure is None:
                        with contextlib.redirect_stdout(output):
                            self.assertEqual(
                                0, rollout.main(argv, dependency_factory=dependencies))
                        self.assertEqual("ALL_ROUTES_ACTIVE\n", output.getvalue())
                    else:
                        with self.assertRaises(SystemExit) as caught, \
                                contextlib.redirect_stdout(output):
                            rollout.main(argv, dependency_factory=dependencies)
                        self.assertEqual(failure, str(caught.exception))
                        self.assertEqual("", output.getvalue())
                    self.assertEqual([action_entry, ("close", None)], journal)
                    dependencies.assert_called_once_with(rollout.ORIGIN)

    def test_close_logs_out_through_the_same_browser_and_clears_tokens(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        browser.access = "secret-access"
        browser.refresh_token = "secret-refresh"

        operator.close()

        self.assertIs(operator.browser, browser)
        self.assertEqual(
            ("POST", "/auth/v1/logout", {"refreshToken": "secret-refresh"},
             None, (200, 204)),
            browser.calls[-1],
        )
        self.assertIsNone(browser.access)
        self.assertIsNone(browser.refresh_token)

    def test_logout_failure_is_redacted_clears_tokens_and_suppresses_success_stage(self):
        authority = FakeAuthority("BASELINE")
        operator, browser, journal = self.operator(authority)
        browser.access = "secret-access"
        browser.refresh_token = "secret-refresh"
        logout_calls = []

        def failing_logout(method, path, body=None, authenticated=True, status=200,
                           extra_headers=None):
            logout_calls.append((method, path, copy.deepcopy(body), status))
            raise RuntimeError("transport leaked secret-access and secret-refresh")

        browser.request = failing_logout
        operator.inspect_stage = lambda: FakeCliObservation("BASELINE")
        output = io.StringIO()
        with self.assertRaises(SystemExit) as caught, contextlib.redirect_stdout(output):
            rollout.main(["--inspect-stage"], dependency_factory=lambda _: operator)

        self.assertEqual(
            "Operations logout failed; credentials and response omitted",
            str(caught.exception),
        )
        self.assertNotIn("secret-access", str(caught.exception))
        self.assertNotIn("secret-refresh", str(caught.exception))
        self.assertEqual("", output.getvalue())
        self.assertEqual(
            [("POST", "/auth/v1/logout", {"refreshToken": "secret-refresh"},
              (200, 204))],
            logout_calls,
        )
        self.assertIsNone(browser.access)
        self.assertIsNone(browser.refresh_token)

    def test_action_error_remains_primary_when_logout_also_fails(self):
        primary_errors = (
            ROUTE_ORDER[0] + " UNKNOWN_REQUIRES_RECONCILIATION",
            "Route command encountered concurrent drift; inspect before retrying",
        )
        for primary_error in primary_errors:
            with self.subTest(primary_error=primary_error):
                authority = FakeAuthority("BASELINE")
                operator, browser, journal = self.operator(authority)
                browser.access = "secret-access"
                browser.refresh_token = "secret-refresh"
                logout_calls = []

                def fail_action(route_code):
                    self.assertEqual(ROUTE_ORDER[0], route_code)
                    raise SystemExit(primary_error)

                def fail_logout(method, path, body=None, authenticated=True, status=200,
                                extra_headers=None):
                    logout_calls.append((method, path, copy.deepcopy(body), status))
                    raise RuntimeError("transport leaked secret-access and secret-refresh")

                operator.activate_route = fail_action
                browser.request = fail_logout
                output = io.StringIO()
                with self.assertRaises(SystemExit) as caught, \
                        contextlib.redirect_stdout(output):
                    rollout.main(
                        ["--activate-route", ROUTE_ORDER[0]],
                        dependency_factory=lambda _: operator,
                    )

                self.assertEqual(primary_error, str(caught.exception))
                self.assertNotIn("secret-access", str(caught.exception))
                self.assertNotIn("secret-refresh", str(caught.exception))
                self.assertEqual("", output.getvalue())
                self.assertEqual(
                    [("POST", "/auth/v1/logout", {"refreshToken": "secret-refresh"},
                      (200, 204))],
                    logout_calls,
                )
                self.assertIsNone(browser.access)
                self.assertIsNone(browser.refresh_token)

    def test_import_and_help_do_not_read_database_open_network_or_create_private_files(self):
        script = SCRIPT_DIR / "risk_rule_configuration_rollout.py"
        source = script.read_text(encoding="utf-8")
        self.assertIn('if __name__ == "__main__":', source)
        with mock.patch("initialize.mysql", side_effect=AssertionError("SQL on help")), \
                mock.patch("smoke.http.client.HTTPConnection", side_effect=AssertionError("network on help")):
            with self.assertRaises(SystemExit) as caught, contextlib.redirect_stdout(io.StringIO()):
                rollout.main(["--help"])
        self.assertEqual(0, caught.exception.code)


if __name__ == "__main__":
    unittest.main()
