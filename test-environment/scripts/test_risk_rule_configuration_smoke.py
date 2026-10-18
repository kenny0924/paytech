#!/usr/bin/env python3
"""Offline contract tests for the opt-in Risk rule-configuration smoke."""

import contextlib
import io
import json
import math
import os
import re
import stat
import tempfile
import unittest
from unittest import mock
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

try:
    import risk_rule_configuration_smoke as smoke
except ModuleNotFoundError:
    smoke = None

from risk_rule_configuration_rollout import ROUTE_ORDER
from risk_rule_configuration_stage import LEGAL_STAGES
from smoke import Browser


CODE_PATTERN = re.compile(r"^SMOKE_[0-9]{8}T[0-9]{6}Z_[0-9A-F]{12}$")


def require_smoke():
    if smoke is None:
        raise AssertionError("risk_rule_configuration_smoke module is missing")
    return smoke


class _Headers:
    def get_all(self, _name):
        return []


class _Response:
    status = 200
    headers = _Headers()

    def read(self):
        return b"{}"


class _JournalConnection:
    requests = []

    def __init__(self, host, port, timeout):
        self.address = (host, port, timeout)

    def request(self, method, path, body, headers):
        self.requests.append((method, path, body, dict(headers)))

    def getresponse(self):
        return _Response()

    def close(self):
        pass


class RequestGenerationTest(unittest.TestCase):
    def test_rule_set_code_has_exact_utc_and_random_shape(self):
        module = require_smoke()
        code = module.generate_rule_set_code(
            now=datetime(2026, 9, 7, 1, 2, 3, tzinfo=timezone.utc),
            random_hex="A1B2C3D4E5F6",
        )
        self.assertEqual("SMOKE_20260907T010203Z_A1B2C3D4E5F6", code)
        self.assertRegex(code, CODE_PATTERN)

    def test_code_generation_rejects_non_utc_and_malformed_entropy(self):
        module = require_smoke()
        with self.assertRaises(ValueError):
            module.generate_rule_set_code(
                now=datetime(2026, 9, 7, 1, 2, 3),
                random_hex="A1B2C3D4E5F6",
            )
        with self.assertRaises(ValueError):
            module.generate_rule_set_code(
                now=datetime(2026, 9, 7, 1, 2, 3, tzinfo=timezone.utc),
                random_hex="not-12-hex!",
            )

    def test_exact_boundary_body_is_compact_ascii_and_semantically_valid(self):
        module = require_smoke()
        code = "SMOKE_20260907T010203Z_A1B2C3D4E5F6"
        raw = module.build_save_request_bytes(code)

        self.assertEqual(65_536, len(raw))
        self.assertTrue(raw.isascii())
        self.assertNotIn(b" ", raw)
        self.assertEqual(raw, module.build_save_request_bytes(code))
        body = json.loads(raw)
        self.assertEqual(
            {
                "pointCode",
                "ruleSetCode",
                "ruleSetName",
                "baseRuleSetVersionId",
                "baseContentHash",
                "changeReason",
                "rules",
            },
            set(body),
        )
        self.assertEqual("CHECKOUT_PREPARE", body["pointCode"])
        self.assertEqual(code, body["ruleSetCode"])
        self.assertIsNone(body["baseRuleSetVersionId"])
        self.assertIsNone(body["baseContentHash"])
        self.assertGreaterEqual(len(body["rules"]), 1)
        self.assertLessEqual(len(body["rules"]), 200)

        priorities = []
        rule_codes = []
        node_count = 0
        operand_count = 0
        for rule in body["rules"]:
            self.assertEqual(
                {
                    "ruleCode",
                    "ruleName",
                    "priorityNo",
                    "enabledFlag",
                    "reasonCode",
                    "reasonMessage",
                    "additionalActions",
                    "conditionNodes",
                    "action",
                },
                set(rule),
            )
            priorities.append(rule["priorityNo"])
            rule_codes.append(rule["ruleCode"])
            self.assertTrue(rule["enabledFlag"])
            self.assertIsNone(rule["reasonCode"])
            self.assertEqual(
                {"actionCode": "ALLOW_CHECKOUT", "actionVersionNo": 1},
                rule["action"],
            )
            self.assertGreaterEqual(len(rule["conditionNodes"]), 1)
            self.assertLessEqual(len(rule["conditionNodes"]), 2_000)
            node_count += len(rule["conditionNodes"])
            for node in rule["conditionNodes"]:
                self.assertEqual("PREDICATE", node["nodeType"])
                self.assertIsNone(node["parentNodeKey"])
                self.assertIsNone(node["groupOperator"])
                self.assertEqual("EQ", node["comparisonOperator"])
                self.assertEqual(2, len(node["operands"]))
                operand_count += len(node["operands"])
                left, right = node["operands"]
                self.assertEqual(
                    {
                        "operandRole": "LEFT",
                        "valueSequence": 1,
                        "sourceType": "CONTEXT",
                        "fieldCode": "country_code",
                        "valueType": "STRING",
                        "literalValue": None,
                    },
                    left,
                )
                self.assertEqual("RIGHT", right["operandRole"])
                self.assertEqual(1, right["valueSequence"])
                self.assertEqual("LITERAL", right["sourceType"])
                self.assertIsNone(right["fieldCode"])
                self.assertEqual("STRING", right["valueType"])
                self.assertIsInstance(right["literalValue"], str)
                self.assertGreaterEqual(len(right["literalValue"]), 1)
                self.assertLessEqual(len(right["literalValue"]), 512)

        self.assertEqual(list(range(1, len(priorities) + 1)), priorities)
        self.assertEqual(len(rule_codes), len(set(rule_codes)))
        self.assertLessEqual(node_count, 2_000)
        self.assertLessEqual(operand_count, 10_000)

    def test_exact_boundary_padding_preserves_country_code_literal_domain(self):
        module = require_smoke()
        raw = module.build_save_request_bytes(
            "SMOKE_20260907T010203Z_ABCDEF123456"
        )
        rules = json.loads(raw)["rules"]

        self.assertTrue(any(len(rule["ruleName"]) > 16 for rule in rules))
        for rule in rules:
            self.assertGreaterEqual(len(rule["ruleName"]), 1)
            self.assertLessEqual(len(rule["ruleName"]), 255)
            right = rule["conditionNodes"][0]["operands"][1]
            self.assertEqual("AE", right["literalValue"])

    def test_oversize_variant_is_only_one_json_legal_transport_byte_larger(self):
        module = require_smoke()
        code = "SMOKE_20260907T010203Z_001122AABBCC"
        boundary = module.build_save_request_bytes(code)
        oversize = module.build_oversize_save_request_bytes(code)
        self.assertEqual(65_537, len(oversize))
        self.assertEqual(boundary + b"\x20", oversize)
        self.assertEqual(json.loads(boundary), json.loads(oversize))

    def test_raw_save_uses_task_five_browser_byte_path_without_reencoding(self):
        module = require_smoke()
        _JournalConnection.requests = []
        browser = Browser(connection_factory=_JournalConnection)
        browser.access = "access-token-must-stay-private"
        raw = module.build_save_request_bytes(
            "SMOKE_20260907T010203Z_FFEEDDCCBBAA"
        )

        module.send_raw_save(
            browser,
            raw,
            "idempotency-key-must-stay-private",
            expected_status=200,
        )

        self.assertEqual(1, len(_JournalConnection.requests))
        method, path, actual, headers = _JournalConnection.requests[0]
        self.assertEqual("POST", method)
        self.assertEqual(
            "/api/operations/decision-rule-sets/v1/rule-sets", path
        )
        self.assertIs(raw, actual)
        self.assertEqual("65536", headers["Content-Length"])
        self.assertEqual(
            "idempotency-key-must-stay-private", headers["Idempotency-Key"]
        )


class SmokeCliTest(unittest.TestCase):
    ACTIONS = (
        (["--prepare-authorization-fixture"], "prepare_authorization_fixture", ()),
        (["--grant-authorization-fixture"], "grant_authorization_fixture", ()),
        (
            ["--probe-runtime-stage", "READ_ACTIVE"],
            "probe_runtime_stage",
            ("READ_ACTIVE", "READ_ACTIVE"),
        ),
        (["--read-stage"], "read_stage", ()),
        (["--route-smoke", ROUTE_ORDER[0]], "route_smoke", (ROUTE_ORDER[0],)),
        (["--full"], "full", ()),
        (["--cleanup-authorization-fixture"], "cleanup_authorization_fixture", ()),
    )

    class Runner:
        def __init__(self, journal):
            self.journal = journal

        def __getattr__(self, name):
            def action(*args):
                self.journal.append((name, args))

            return action

    def test_no_action_is_noop_and_does_not_construct_live_boundaries(self):
        module = require_smoke()

        def forbidden_factory():
            raise AssertionError("no-action mode constructed a runner")

        output = io.StringIO()
        error = io.StringIO()
        self.assertEqual(
            0,
            module.main(
                [], runner_factory=forbidden_factory, stdout=output, stderr=error
            ),
        )
        self.assertEqual("", output.getvalue())
        self.assertEqual("", error.getvalue())

    def test_exactly_seven_action_forms_dispatch_and_emit_fixed_summaries(self):
        module = require_smoke()
        for argv, method, expected_args in self.ACTIONS:
            with self.subTest(argv=argv):
                journal = []
                output = io.StringIO()
                error = io.StringIO()
                result = module.main(
                    argv,
                    runner_factory=lambda: self.Runner(journal),
                    stdout=output,
                    stderr=error,
                )
                self.assertEqual(0, result)
                self.assertEqual([(method, expected_args)], journal)
                self.assertRegex(output.getvalue(), r"^[A-Z_]+_OK\n$")
                self.assertEqual("", error.getvalue())

    def test_common_base_url_reaches_every_action_without_becoming_an_action(self):
        module = require_smoke()
        base_url = "http://127.0.0.1:29876"
        constructions = []

        def construct(selected_base_url):
            constructions.append(selected_base_url)
            return self.Runner([])

        with mock.patch.object(
            module, "RiskRuleConfigurationSmoke", side_effect=construct
        ):
            for argv, _method, _arguments in self.ACTIONS:
                with self.subTest(argv=argv):
                    self.assertEqual(
                        0,
                        module.main(
                            ["--base-url", base_url] + argv,
                            stdout=io.StringIO(),
                            stderr=io.StringIO(),
                        ),
                    )
            self.assertEqual([base_url] * len(self.ACTIONS), constructions)

            self.assertEqual(
                0,
                module.main(
                    ["--base-url", base_url],
                    stdout=io.StringIO(),
                    stderr=io.StringIO(),
                ),
            )
            self.assertEqual([base_url] * len(self.ACTIONS), constructions)

    def test_explicit_persisted_stage_is_probe_only_and_defaults_to_runtime(self):
        module = require_smoke()
        journal = []
        self.assertEqual(
            0,
            module.main(
                [
                    "--probe-runtime-stage",
                    "READ_ACTIVE",
                    "--expected-persisted-stage",
                    "CANDIDATE_LIST_READ_ACTIVE",
                ],
                runner_factory=lambda: self.Runner(journal),
                stdout=io.StringIO(),
                stderr=io.StringIO(),
            ),
        )
        self.assertEqual(
            [
                (
                    "probe_runtime_stage",
                    ("READ_ACTIVE", "CANDIDATE_LIST_READ_ACTIVE"),
                )
            ],
            journal,
        )
        for action in (
            ["--read-stage"],
            ["--full"],
            ["--route-smoke", ROUTE_ORDER[0]],
        ):
            with self.subTest(action=action):
                with self.assertRaises(SystemExit):
                    module.main(
                        action
                        + ["--expected-persisted-stage", "BASELINE"],
                        runner_factory=lambda: self.Runner([]),
                        stdout=io.StringIO(),
                        stderr=io.StringIO(),
                    )

    def test_actions_route_codes_and_stage_names_are_strictly_constrained(self):
        module = require_smoke()
        conflicting = ["--full", "--read-stage"]
        invalid_route = ["--route-smoke", "RISK_ALMOST_A_ROUTE"]
        invalid_stage = ["--probe-runtime-stage", "READISH"]
        for argv in (conflicting, invalid_route, invalid_stage):
            with self.subTest(argv=argv):
                with contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        module.main(argv, runner_factory=lambda: self.Runner([]))
        for stage in LEGAL_STAGES:
            with self.subTest(stage=stage):
                journal = []
                self.assertEqual(
                    0,
                    module.main(
                        ["--probe-runtime-stage", stage],
                        runner_factory=lambda: self.Runner(journal),
                        stdout=io.StringIO(),
                        stderr=io.StringIO(),
                    ),
                )
                self.assertEqual(
                    [("probe_runtime_stage", (stage, stage))], journal
                )

    def test_parser_errors_use_injected_stderr_without_echoing_invalid_values(self):
        module = require_smoke()
        invalid_value = "header.payload.private-signature"
        injected = io.StringIO()
        process_stderr = io.StringIO()
        with contextlib.redirect_stderr(process_stderr):
            with self.assertRaises(SystemExit):
                module.main(
                    ["--route-smoke", invalid_value],
                    runner_factory=lambda: self.Runner([]),
                    stderr=injected,
                )
        self.assertEqual("", process_stderr.getvalue())
        self.assertNotIn(invalid_value, injected.getvalue())
        self.assertIn("invalid arguments", injected.getvalue())

    def test_failure_and_success_output_redact_every_sensitive_value(self):
        module = require_smoke()
        password = "Password-Must-Never-Appear-9!"
        token = "header.payload.signature"
        key = "idempotency-key-7f36f50d"
        body = '{"literalValue":"HIGHLY_PRIVATE_LITERAL"}'
        full_hash = "a" * 64
        secrets = (password, token, key, body, full_hash)

        class FailingRunner(self.Runner):
            def full(self):
                raise RuntimeError(" :: ".join(secrets))

        output = io.StringIO()
        error = io.StringIO()
        self.assertEqual(
            1,
            module.main(
                ["--full"],
                runner_factory=lambda: FailingRunner([]),
                stdout=output,
                stderr=error,
            ),
        )
        emitted = output.getvalue() + error.getvalue()
        for secret in secrets:
            self.assertNotIn(secret, emitted)
        self.assertEqual("", output.getvalue())
        self.assertEqual("RISK_RULE_CONFIGURATION_SMOKE_FAILED\n", error.getvalue())


def _problem(code):
    statuses = {
        "BUSINESS_CHECK_FAILED": 400,
        "INVALID_REQUEST": 400,
        "OPERATIONS_AUTHENTICATION_FAILED": 401,
        "OPERATIONS_PERMISSION_DENIED": 403,
        "GATEWAY_ROUTE_NOT_FOUND": 404,
        "RISK_DECISION_CONFIGURATION_NOT_FOUND": 404,
        "CONCURRENT_REQUEST": 409,
        "GATEWAY_PAYLOAD_TOO_LARGE": 413,
        "GATEWAY_RATE_LIMITED": 429,
    }
    titles = {
        400: "Bad Request",
        401: "Unauthorized",
        403: "Forbidden",
        404: "Not Found",
        409: "Conflict",
        413: "Content Too Large",
        429: "Too Many Requests",
    }
    return {
        "type": "about:blank",
        "title": titles[statuses[code]],
        "detail": "Synthetic safe detail",
        "code": code,
        "requestId": "request-20260907_ABC",
        "timestamp": "2026-09-07T01:02:03.123456Z",
    }


class ProblemProfileTest(unittest.TestCase):
    def test_payload_limit_problem_uses_standard_413_reason_phrase(self):
        module = require_smoke()
        body = {
            "type": "about:blank",
            "title": "Content Too Large",
            "detail": "Synthetic safe detail",
            "code": "GATEWAY_PAYLOAD_TOO_LARGE",
            "requestId": "request-20260907_ABC",
            "timestamp": "2026-09-07T01:02:03.123456Z",
        }

        self.assertEqual(
            "GATEWAY_PAYLOAD_TOO_LARGE",
            module._problem(body, "GATEWAY_PAYLOAD_TOO_LARGE", 413)["code"],
        )

    def test_consumer_checks_status_title_and_ignores_unknown_extensions(self):
        module = require_smoke()
        body = {
            "type": "about:blank",
            "title": "Forbidden",
            "detail": "Synthetic safe detail",
            "code": "OPERATIONS_PERMISSION_DENIED",
            "requestId": "request-20260907_ABC",
            "timestamp": "2026-09-07T01:02:03.123456Z",
            "futureDiagnostic": {"safe": True},
        }
        self.assertEqual(
            "OPERATIONS_PERMISSION_DENIED",
            module._problem(body, "OPERATIONS_PERMISSION_DENIED", 403)["code"],
        )
        bad_title = dict(body, title="Synthetic title")
        with self.assertRaises(module.SmokeFailure):
            module._problem(bad_title, "OPERATIONS_PERMISSION_DENIED", 403)


class BoundedPagedReadTest(unittest.TestCase):
    def test_paged_reads_use_bounded_gateway_rate_limit_adapter(self):
        module = require_smoke()
        calls = []

        class Browser:
            def new_deadline(self, timeout_seconds):
                calls.append(("deadline", timeout_seconds))
                return 123.0

            def request_rate_limited_get(
                self, path, max_retry_after_seconds, deadline
            ):
                calls.append(
                    ("get", path, max_retry_after_seconds, deadline)
                )
                return {
                    "items": [{"roleId": "10001"}],
                    "page": 1,
                    "size": 200,
                    "total": 1,
                }

            def request(self, *_args, **_kwargs):
                raise AssertionError("unbounded GET path was used")

        self.assertEqual(
            [{"roleId": "10001"}],
            module._page_items(Browser(), "/iam/v1/roles", "roleId"),
        )
        self.assertEqual(
            [
                ("deadline", 90),
                (
                    "get",
                    "/iam/v1/roles?page=1&size=200",
                    6,
                    123.0,
                ),
            ],
            calls,
        )


class _IamServer:
    """Stateful HTTP-only IAM fake; it never opens network, SQL or private files."""

    ADMIN_EMAIL = "administrator@example.test"
    ADMIN_PASSWORD = "Administrator-Password-9!"

    def __init__(self, filler_count=0):
        self.journal = []
        self.next_role = 10_000
        self.next_account = 20_000
        self.roles = {}
        self.accounts = {}
        self.admin_account = {
            "accountId": "9001",
            "email": self.ADMIN_EMAIL,
            "password": self.ADMIN_PASSWORD,
            "passwordChangeRequired": False,
            "status": "ACTIVE",
            "version": 1,
            "memberId": "9101",
            "memberVersion": 1,
            "roleIds": [],
        }
        self.accounts[self.admin_account["accountId"]] = self.admin_account
        for index in range(filler_count):
            role_id = str(self.next_role)
            self.next_role += 1
            self.roles[role_id] = {
                "roleId": role_id,
                "roleCode": "FILLER_ROLE_{0:03d}".format(index),
                "roleName": "FillerRole{0:03d}".format(index),
                "systemRole": False,
                "status": "ACTIVE",
                "version": 1,
                "permissionIds": ["900000000000000524"],
            }
            account_id = str(self.next_account)
            self.next_account += 1
            self.accounts[account_id] = {
                "accountId": account_id,
                "email": "filler-{0:03d}@example.test".format(index),
                "password": "Unused-Filler-Password-9!",
                "passwordChangeRequired": False,
                "status": "ACTIVE",
                "version": 1,
                "memberId": str(int(account_id) + 100_000),
                "memberVersion": 1,
                "roleIds": [role_id],
            }

    def browser(self):
        return _IamBrowser(self)

    def expected(self, expected_status, actual_status, body):
        allowed = (
            (expected_status,)
            if isinstance(expected_status, int)
            else tuple(expected_status)
        )
        if actual_status not in allowed:
            raise AssertionError(
                "fake IAM status {0} not in expected {1}: {2}".format(
                    actual_status, allowed, body
                )
            )
        return body

    def role_summary(self, role):
        employee_count = sum(
            1
            for account in self.accounts.values()
            if account["status"] != "CLOSED"
            and role["roleId"] in account["roleIds"]
        )
        return {
            key: role[key]
            for key in ("roleId", "roleCode", "roleName", "systemRole", "version")
        } | {"employeeCount": employee_count}

    @staticmethod
    def role_detail(role):
        return {
            key: role[key]
            for key in (
                "roleId",
                "roleCode",
                "roleName",
                "systemRole",
                "version",
                "permissionIds",
            )
        }

    def account_summary(self, account):
        roles = [
            {
                key: self.roles[role_id][key]
                for key in ("roleId", "roleCode", "roleName", "systemRole")
            }
            for role_id in account["roleIds"]
        ]
        return {
            "accountId": account["accountId"],
            "emailMasked": account["email"][0] + "***@example.test",
            "status": account["status"],
            "passwordChangeRequired": account["passwordChangeRequired"],
            "lockedUntil": None,
            "version": account["version"],
            "memberId": account["memberId"],
            "memberVersion": account["memberVersion"],
            "roles": roles,
        }


class _IamBrowser:
    def __init__(self, server):
        self.server = server
        self.identity = None
        self.access = None
        self.refresh_token = None

    def _record(self, method, path, body, status, extra_headers):
        self.server.journal.append(
            {
                "actor": None if self.identity is None else self.identity["accountId"],
                "method": method,
                "path": path,
                "body": body,
                "status": status,
                "headers": dict(extra_headers or {}),
            }
        )

    @staticmethod
    def new_deadline(timeout_seconds):
        return float(timeout_seconds)

    def request_rate_limited_get(
        self, path, max_retry_after_seconds, deadline
    ):
        del max_retry_after_seconds, deadline
        return self.request("GET", path)

    def request_rate_limited_login(
        self,
        identifier,
        password,
        max_retry_after_seconds,
        deadline,
        status=200,
    ):
        del max_retry_after_seconds, deadline
        return self.request(
            "POST",
            "/auth/v1/login",
            {"loginIdentifier": identifier, "password": password},
            authenticated=False,
            status=status,
        )

    def request(
        self,
        method,
        path,
        body=None,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        self._record(method, path, body, status, extra_headers)
        split = urlsplit(path)
        query = parse_qs(split.query)
        clean_path = split.path
        if method == "POST" and clean_path == "/auth/v1/login":
            matches = [
                account
                for account in self.server.accounts.values()
                if account["email"] == body["loginIdentifier"]
                and account["password"] == body["password"]
                and account["status"] == "ACTIVE"
            ]
            if len(matches) != 1:
                return self.server.expected(
                    status, 401, _problem("OPERATIONS_AUTHENTICATION_FAILED")
                )
            self.identity = matches[0]
            self.access = "access-for-" + self.identity["accountId"]
            self.refresh_token = "refresh-for-" + self.identity["accountId"]
            return self.server.expected(
                status,
                200,
                {
                    "accessToken": self.access,
                    "tokenType": "Bearer",
                    "expiresIn": 300,
                    "refreshToken": self.refresh_token,
                    "refreshExpiresIn": 3600,
                    "passwordChangeRequired": self.identity[
                        "passwordChangeRequired"
                    ],
                    "bootstrapAdministratorRequired": False,
                },
            )
        if method == "POST" and clean_path == "/auth/v1/password":
            if self.identity is None or self.identity["password"] != body["currentPassword"]:
                return self.server.expected(status, 401, _problem("OPERATIONS_AUTHENTICATION_FAILED"))
            self.identity["password"] = body["newPassword"]
            self.identity["passwordChangeRequired"] = False
            self.identity["version"] += 1
            self.access = None
            self.refresh_token = None
            return self.server.expected(status, 204, None)
        if method == "GET" and clean_path == "/auth/v1/context":
            if self.identity is None:
                return self.server.expected(status, 401, _problem("OPERATIONS_AUTHENTICATION_FAILED"))
            account = self.identity
            return self.server.expected(
                status,
                200,
                {
                    "accountId": account["accountId"],
                    "sessionId": "session-" + account["accountId"],
                    "accountType": "HUMAN",
                    "emailMasked": account["email"][0] + "***@example.test",
                    "accountStatus": account["status"],
                    "tenantId": "1",
                    "memberId": account["memberId"],
                    "passwordChangeRequired": account["passwordChangeRequired"],
                    "bootstrapAdministratorRequired": False,
                    "integrationMode": "INTERNAL",
                },
            )
        if method == "POST" and clean_path == "/auth/v1/logout":
            self.access = None
            self.refresh_token = None
            return self.server.expected(status, 204, None)
        if method == "GET" and clean_path == "/iam/v1/roles":
            page = int(query.get("page", ["1"])[0])
            size = int(query.get("size", ["200"])[0])
            rows = [
                self.server.role_summary(role)
                for role in self.server.roles.values()
                if role["status"] == "ACTIVE"
            ]
            start = (page - 1) * size
            return self.server.expected(
                status,
                200,
                {
                    "items": rows[start : start + size],
                    "page": page,
                    "size": size,
                    "total": len(rows),
                },
            )
        if method == "GET" and clean_path.startswith("/iam/v1/roles/"):
            role_id = clean_path.rsplit("/", 1)[1]
            return self.server.expected(
                status,
                200,
                dict(self.server.role_detail(self.server.roles[role_id]), nodes=[]),
            )
        if method == "POST" and clean_path == "/iam/v1/roles":
            if any(role["roleCode"] == body["roleCode"] for role in self.server.roles.values()):
                return self.server.expected(status, 400, _problem("BUSINESS_CHECK_FAILED"))
            role_id = str(self.server.next_role)
            self.server.next_role += 1
            role = {
                "roleId": role_id,
                "roleCode": body["roleCode"],
                "roleName": body["roleName"],
                "systemRole": False,
                "status": "ACTIVE",
                "version": 1,
                "permissionIds": list(body["permissionIds"]),
            }
            self.server.roles[role_id] = role
            return self.server.expected(
                status, 200, self.server.role_detail(role)
            )
        if method == "PUT" and clean_path.startswith("/iam/v1/roles/"):
            role_id = clean_path.rsplit("/", 1)[1]
            role = self.server.roles[role_id]
            if body["expectedVersion"] != role["version"]:
                return self.server.expected(status, 409, _problem("CONCURRENT_REQUEST"))
            role["roleName"] = body["roleName"]
            role["permissionIds"] = list(body["permissionIds"])
            role["version"] += 1
            return self.server.expected(
                status, 200, self.server.role_detail(role)
            )
        if method == "GET" and clean_path == "/iam/v1/accounts":
            page = int(query.get("page", ["1"])[0])
            size = int(query.get("size", ["200"])[0])
            active = [
                account
                for account in self.server.accounts.values()
                if account["status"] != "CLOSED"
            ]
            rows = [self.server.account_summary(account) for account in active]
            start = (page - 1) * size
            return self.server.expected(
                status,
                200,
                {
                    "items": rows[start : start + size],
                    "page": page,
                    "size": size,
                    "total": len(rows),
                },
            )
        if method == "POST" and clean_path == "/iam/v1/accounts":
            if any(
                account["email"] == body["email"]
                for account in self.server.accounts.values()
            ):
                return self.server.expected(
                    status, 400, _problem("BUSINESS_CHECK_FAILED")
                )
            account_id = str(self.server.next_account)
            self.server.next_account += 1
            account = {
                "accountId": account_id,
                "email": body["email"],
                "password": body["initialPassword"],
                "passwordChangeRequired": True,
                "status": "ACTIVE",
                "version": 1,
                "memberId": str(int(account_id) + 100_000),
                "memberVersion": 1,
                "roleIds": list(body["roleIds"]),
            }
            self.server.accounts[account_id] = account
            return self.server.expected(
                status, 200, self.server.account_summary(account)
            )
        if method == "DELETE" and clean_path.startswith("/iam/v1/accounts/"):
            account_id = clean_path.rsplit("/", 1)[1]
            account = self.server.accounts[account_id]
            if (
                body["expectedVersion"] != account["version"]
                or body["expectedMemberVersion"] != account["memberVersion"]
            ):
                return self.server.expected(status, 409, _problem("CONCURRENT_REQUEST"))
            account["status"] = "CLOSED"
            account["version"] += 1
            account["memberVersion"] += 1
            account["roleIds"] = []
            return self.server.expected(status, 204, None)
        if method == "DELETE" and clean_path.startswith("/iam/v1/roles/"):
            role_id = clean_path.rsplit("/", 1)[1]
            role = self.server.roles[role_id]
            if body["expectedVersion"] != role["version"]:
                return self.server.expected(status, 409, _problem("CONCURRENT_REQUEST"))
            role["status"] = "INACTIVE"
            role["version"] += 1
            role["permissionIds"] = []
            return self.server.expected(status, 204, None)
        raise AssertionError("Unexpected fake IAM request: {0} {1}".format(method, path))

    def request_raw(
        self,
        method,
        path,
        body,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        self.server.journal.append(
            {
                "actor": None if self.identity is None else self.identity["accountId"],
                "method": method,
                "path": path,
                "body": body,
                "status": status,
                "headers": dict(extra_headers or {}),
                "raw": True,
            }
        )
        decoded = json.loads(body)
        before = len(self.server.journal)
        result = self.request(
            method,
            path,
            decoded,
            authenticated=authenticated,
            status=status,
            extra_headers=extra_headers,
        )
        self.server.journal[before]["internal_from_raw"] = True
        return result

    def login(self, identifier, password):
        result = self.request(
            "POST",
            "/auth/v1/login",
            {"loginIdentifier": identifier, "password": password},
            authenticated=False,
            status=200,
        )
        return result

    def change_password(self, current, new):
        return self.request(
            "POST",
            "/auth/v1/password",
            {"currentPassword": current, "newPassword": new},
            status=(200, 204),
        )


class _RateLimitedIamServer(_IamServer):
    """Five-login token bucket matching the local Gateway guard contract."""

    def __init__(self):
        super().__init__()
        self.login_tokens = 5.0
        self.login_clock = 0.0
        self.login_updated_at = 0.0
        self.login_sleeps = []
        self.login_deadlines = []
        self.last_retry_after = None

    def browser(self):
        return _RateLimitedIamBrowser(self)

    def admit_login(self):
        elapsed = self.login_clock - self.login_updated_at
        self.login_tokens = min(5.0, self.login_tokens + elapsed / 12.0)
        self.login_updated_at = self.login_clock
        if self.login_tokens >= 1.0:
            self.login_tokens -= 1.0
            self.last_retry_after = None
            return True
        self.last_retry_after = int(math.ceil((1.0 - self.login_tokens) * 12.0))
        return False


class _RateLimitedIamBrowser(_IamBrowser):
    def new_deadline(self, timeout_seconds):
        return self.server.login_clock + float(timeout_seconds)

    def remaining_before_deadline(self, deadline):
        remaining = deadline - self.server.login_clock
        if remaining <= 0:
            raise SystemExit("bounded fake login deadline exhausted")
        return remaining

    def request(
        self,
        method,
        path,
        body=None,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        if method == "POST" and path == "/auth/v1/login":
            if not self.server.admit_login():
                self._record(method, path, body, status, extra_headers)
                return self.server.expected(
                    status, 429, _problem("GATEWAY_RATE_LIMITED")
                )
        return super().request(
            method, path, body, authenticated, status, extra_headers
        )

    def request_rate_limited_login(
        self,
        identifier,
        password,
        max_retry_after_seconds,
        deadline,
        status=200,
    ):
        self.server.login_deadlines.append(deadline)
        expected = (status,) if isinstance(status, int) else tuple(status)
        for attempt in range(2):
            result = self.request(
                "POST",
                "/auth/v1/login",
                {"loginIdentifier": identifier, "password": password},
                authenticated=False,
                status=expected + (429,),
            )
            if not (
                isinstance(result, dict)
                and result.get("code") == "GATEWAY_RATE_LIMITED"
            ):
                return self.server.expected(
                    expected, 401 if result.get("code") else 200, result
                )
            if attempt == 1:
                raise SystemExit("bounded fake login remained rate limited")
            retry_after = self.server.last_retry_after
            if retry_after > max_retry_after_seconds:
                raise SystemExit("fake Retry-After exceeds bound")
            wait_seconds = max(retry_after, 6)
            if wait_seconds >= self.remaining_before_deadline(deadline):
                raise SystemExit("bounded fake login deadline exhausted")
            self.server.login_sleeps.append(wait_seconds)
            self.server.login_clock += wait_seconds
        raise AssertionError("unreachable fake rate-limit state")


class CredentialAndFixtureTest(unittest.TestCase):
    def test_prepare_rejects_legacy_before_credentials_or_iam(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: (_ for _ in ()).throw(
                    AssertionError("legacy stage opened administrator material")
                ),
                credential_store=module.FixtureCredentialStore(path),
                password_factory=lambda _label: (_ for _ in ()).throw(
                    AssertionError("legacy stage generated credentials")
                ),
                idempotency_key_factory=lambda: "unused-key",
            )

            with self.assertRaisesRegex(
                module.SmokeFailure, "AUTHORIZATION_PREPARE_STAGE_INVALID"
            ):
                manager.prepare("LEGACY")

            self.assertEqual([], server.journal)
            self.assertFalse(path.exists())
            self.assertFalse(path.parent.exists())

    def test_fixture_material_is_exclusive_and_mode_restricted(self):
        module = require_smoke()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            store = module.FixtureCredentialStore(
                path, generation_factory=lambda: "ABCDEF123456"
            )
            calls = []

            def password(label):
                calls.append(label)
                return "Strong-{0}-Aa9!".format(label)

            first = store.ensure(password)
            second = store.ensure(
                lambda _label: (_ for _ in ()).throw(
                    AssertionError("existing credentials were overwritten")
                )
            )

            self.assertEqual(first, second)
            self.assertEqual(4, len(calls))
            self.assertEqual(
                4,
                len(
                    {
                        first[key]
                        for key in (
                            "authorizedInitialPassword",
                            "authorizedPassword",
                            "deniedInitialPassword",
                            "deniedPassword",
                        )
                    }
                ),
            )
            self.assertEqual(
                {
                    "authorizedInitialPassword",
                    "authorizedPassword",
                    "deniedInitialPassword",
                    "deniedPassword",
                    "fixtureGeneration",
                },
                set(first),
            )
            self.assertEqual("ABCDEF123456", first["fixtureGeneration"])
            self.assertFalse(any("id" in key.lower() for key in first))
            self.assertEqual(0o700, stat.S_IMODE(path.parent.stat().st_mode))
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual(first, json.loads(path.read_text()))

    def test_legacy_fixture_material_is_atomically_upgraded(self):
        module = require_smoke()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            path.parent.mkdir(mode=0o700)
            legacy = {
                key: "Legacy-{0}-Aa9!".format(key)
                for key in (
                    "authorizedInitialPassword",
                    "authorizedPassword",
                    "deniedInitialPassword",
                    "deniedPassword",
                )
            }
            path.write_text(json.dumps(legacy))
            path.chmod(0o600)
            store = module.FixtureCredentialStore(
                path, generation_factory=lambda: "123456ABCDEF"
            )

            upgraded = store.ensure(
                lambda _label: (_ for _ in ()).throw(
                    AssertionError("legacy passwords were regenerated")
                )
            )

            self.assertEqual("123456ABCDEF", upgraded["fixtureGeneration"])
            self.assertEqual(legacy, {key: upgraded[key] for key in legacy})
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual(upgraded, json.loads(path.read_text()))
            self.assertEqual([], list(path.parent.glob(".*.tmp")))

    def test_active_legacy_fixture_remains_resumable_without_upgrade(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            path.parent.mkdir(mode=0o700)
            legacy = {
                key: "Legacy-{0}-Aa9!".format(key)
                for key in (
                    "authorizedInitialPassword",
                    "authorizedPassword",
                    "deniedInitialPassword",
                    "deniedPassword",
                )
            }
            path.write_text(json.dumps(legacy))
            path.chmod(0o600)
            role_definitions = (
                ("10001", module.AUTHORIZED_ROLE_CODE),
                ("10002", module.DENIED_ROLE_CODE),
            )
            for role_id, role_code in role_definitions:
                server.roles[role_id] = {
                    "roleId": role_id,
                    "roleCode": role_code,
                    "roleName": role_code.title().replace("_", ""),
                    "systemRole": False,
                    "status": "ACTIVE",
                    "version": 1,
                    "permissionIds": [module.OPERATIONS_SYSTEM_READ],
                }
            account_definitions = (
                (
                    "20001",
                    module.AUTHORIZED_EMAIL,
                    legacy["authorizedPassword"],
                    "10001",
                ),
                (
                    "20002",
                    module.DENIED_EMAIL,
                    legacy["deniedPassword"],
                    "10002",
                ),
            )
            for account_id, email, password, role_id in account_definitions:
                server.accounts[account_id] = {
                    "accountId": account_id,
                    "email": email,
                    "password": password,
                    "passwordChangeRequired": False,
                    "status": "ACTIVE",
                    "version": 1,
                    "memberId": str(int(account_id) + 100_000),
                    "memberVersion": 1,
                    "roleIds": [role_id],
                }
            store = module.FixtureCredentialStore(
                path,
                generation_factory=lambda: (_ for _ in ()).throw(
                    AssertionError("active legacy fixture was upgraded")
                ),
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda _label: (_ for _ in ()).throw(
                    AssertionError("active legacy fixture regenerated passwords")
                ),
                idempotency_key_factory=lambda: "unused-key",
            )

            actors = manager.prepare("READ_ACTIVE")

            self.assertEqual(
                {module.AUTHORIZED_ROLE_CODE, module.DENIED_ROLE_CODE},
                {
                    actors.authorized.role["roleCode"],
                    actors.denied.role["roleCode"],
                },
            )
            self.assertNotIn("fixtureGeneration", store.read())
            self.assertFalse(
                any(
                    entry["method"] == "POST"
                    and entry["path"] in {"/iam/v1/roles", "/iam/v1/accounts"}
                    for entry in server.journal
                )
            )
            manager.logout_actors(actors)

    def test_credential_parent_symlink_is_rejected_before_target_mode_changes(self):
        module = require_smoke()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "outside-target"
            target.mkdir(mode=0o755)
            target.chmod(0o755)
            link = root / "private"
            link.symlink_to(target, target_is_directory=True)
            store = module.FixtureCredentialStore(
                link / "risk-rule-configuration-smoke.json"
            )

            with self.assertRaisesRegex(
                module.SmokeFailure, "FIXTURE_CREDENTIAL_DIRECTORY_INVALID"
            ):
                store.ensure(lambda label: "Fixture-{0}-Aa9!".format(label))

            self.assertEqual(0o755, stat.S_IMODE(target.stat().st_mode))
            self.assertFalse((target / "risk-rule-configuration-smoke.json").exists())

    def test_prepare_and_new_process_discovery_page_exact_roles_and_account_ids(self):
        module = require_smoke()
        server = _IamServer(filler_count=201)
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "private-idempotency-key",
            )
            prepared = manager.prepare("SAVE_ACTIVE")
            self.assertEqual(
                {module.OPERATIONS_SYSTEM_READ},
                set(prepared.authorized.role["permissionIds"]),
            )
            self.assertEqual(
                {module.OPERATIONS_SYSTEM_READ},
                set(prepared.denied.role["permissionIds"]),
            )

            creates_before = sum(
                entry["method"] == "POST"
                and entry["path"] in {"/iam/v1/roles", "/iam/v1/accounts"}
                for entry in server.journal
            )
            later_process = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda _label: (_ for _ in ()).throw(
                    AssertionError("later process generated passwords")
                ),
                idempotency_key_factory=lambda: "another-private-key",
            )
            admin = later_process.administrator()
            discovered = later_process.discover(admin, "SAVE_ACTIVE")
            creates_after = sum(
                entry["method"] == "POST"
                and entry["path"] in {"/iam/v1/roles", "/iam/v1/accounts"}
                for entry in server.journal
            )
            self.assertEqual(creates_before, creates_after)
            self.assertEqual(
                discovered.authorized.context["accountId"],
                discovered.authorized.account["accountId"],
            )
            self.assertEqual(
                discovered.denied.context["accountId"],
                discovered.denied.account["accountId"],
            )
            self.assertNotEqual(
                module.AUTHORIZED_EMAIL,
                discovered.authorized.account["emailMasked"],
            )
            self.assertTrue(
                any(
                    entry["path"] == "/iam/v1/roles?page=2&size=200"
                    for entry in server.journal
                )
            )
            self.assertTrue(
                any(
                    entry["path"] == "/iam/v1/accounts?page=2&size=200"
                    for entry in server.journal
                )
            )
            stored = json.loads(store.path.read_text())
            self.assertFalse(any("Id" in key or "id" in key for key in stored))

    def test_prepare_resumes_partial_fixture_through_five_login_gateway_limit(self):
        module = require_smoke()
        server = _RateLimitedIamServer()
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json",
                generation_factory=lambda: "ABCDEF123456",
            )
            credentials = store.ensure(
                lambda label: "Fixture-{0}-Aa9!".format(label)
            )
            authorized, denied = module._fixture_definitions(credentials)
            for role_id, definition in (
                ("10001", authorized),
                ("10002", denied),
            ):
                server.roles[role_id] = {
                    "roleId": role_id,
                    "roleCode": definition.role_code,
                    "roleName": definition.role_name,
                    "systemRole": False,
                    "status": "ACTIVE",
                    "version": 1,
                    "permissionIds": [module.OPERATIONS_SYSTEM_READ],
                }
            server.accounts["20001"] = {
                "accountId": "20001",
                "email": authorized.email,
                "password": authorized.password,
                "passwordChangeRequired": False,
                "status": "ACTIVE",
                "version": 1,
                "memberId": "120001",
                "memberVersion": 1,
                "roleIds": ["10001"],
            }
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda _label: (_ for _ in ()).throw(
                    AssertionError("partial fixture regenerated credentials")
                ),
                idempotency_key_factory=lambda: "hidden-resume-key",
            )

            def observe(administrator):
                if administrator.identity is not server.admin_account:
                    raise AssertionError(
                        "runner observation used the wrong administrator"
                    )
                server.login_clock += 2.0
                return SimpleNamespace(
                    stage="RESOURCES_EXPOSED",
                    persisted={"resources": {}},
                )

            runner = module.RiskRuleConfigurationSmoke(
                authorization=manager,
                stage_observer=observe,
            )
            runner.prepare_authorization_fixture()

            self.assertEqual(36.0, server.login_clock)
            self.assertEqual(3, len(server.login_sleeps))
            self.assertEqual({90.0}, set(server.login_deadlines))
            self.assertEqual(
                0,
                sum(
                    entry["method"] == "POST"
                    and entry["path"] == "/iam/v1/roles"
                    for entry in server.journal
                ),
            )
            self.assertEqual(
                1,
                sum(
                    entry["method"] == "POST"
                    and entry["path"] == "/iam/v1/accounts"
                    for entry in server.journal
                ),
            )
            denied_accounts = [
                account
                for account in server.accounts.values()
                if account["email"] == denied.email
            ]
            self.assertEqual(1, len(denied_accounts))
            denied_account = denied_accounts[0]
            self.assertEqual(denied.email, denied_account["email"])
            self.assertEqual(["10002"], denied_account["roleIds"])

    def test_prepare_never_grants_risk_and_grant_is_stage_gated_and_idempotent(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=module.FixtureCredentialStore(
                    Path(directory)
                    / "private"
                    / "risk-rule-configuration-smoke.json"
                ),
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "private-key",
            )
            prepared = manager.prepare("ALL_ROUTES_ACTIVE")
            auth_role = server.roles[prepared.authorized.role["roleId"]]
            self.assertEqual([module.OPERATIONS_SYSTEM_READ], auth_role["permissionIds"])
            with self.assertRaises(module.SmokeFailure):
                manager.grant("READ_ACTIVE")

            granted = manager.grant("ALL_ROUTES_ACTIVE")
            self.assertEqual(
                set(module.AUTHORIZED_PERMISSION_IDS),
                set(granted.authorized.role["permissionIds"]),
            )
            self.assertEqual(
                {module.OPERATIONS_SYSTEM_READ},
                set(granted.denied.role["permissionIds"]),
            )
            updates = [
                entry
                for entry in server.journal
                if entry["method"] == "PUT"
                and entry["path"].startswith("/iam/v1/roles/")
                and entry.get("raw") is True
            ]
            self.assertEqual(1, len(updates))
            self.assertEqual(
                set(module.AUTHORIZED_PERMISSION_IDS),
                set(json.loads(updates[0]["body"])["permissionIds"]),
            )
            manager.grant("RESOURCES_EXPOSED")
            updates = [
                entry
                for entry in server.journal
                if entry["method"] == "PUT"
                and entry["path"].startswith("/iam/v1/roles/")
                and entry.get("raw") is True
            ]
            self.assertEqual(1, len(updates))


class SessionCleanupTest(unittest.TestCase):
    @staticmethod
    def _token(password_change=False, bootstrap=False):
        return {
            "accessToken": "private-access-token",
            "tokenType": "Bearer",
            "expiresIn": 300,
            "refreshToken": "private-refresh-token",
            "refreshExpiresIn": 3600,
            "passwordChangeRequired": password_change,
            "bootstrapAdministratorRequired": bootstrap,
        }

    def test_internal_validation_failures_logout_every_acquired_session(self):
        module = require_smoke()

        class Browser:
            def __init__(self, token, context_error=False):
                self.token = token
                self.context_error = context_error
                self.access = None
                self.refresh_token = None
                self.logout_count = 0

            @staticmethod
            def new_deadline(timeout_seconds):
                return float(timeout_seconds)

            def request_rate_limited_login(
                self,
                _identifier,
                _password,
                max_retry_after_seconds,
                deadline,
                status=200,
            ):
                del max_retry_after_seconds, deadline, status
                self.access = self.token.get("accessToken")
                self.refresh_token = self.token.get("refreshToken")
                return self.token

            def login(self, _email, _password):
                self.access = self.token.get("accessToken")
                self.refresh_token = self.token.get("refreshToken")
                return self.token

            def request(
                self,
                method,
                path,
                body=None,
                authenticated=True,
                status=200,
                extra_headers=None,
            ):
                if method == "POST" and path == "/auth/v1/login":
                    return self.token
                if method == "POST" and path == "/auth/v1/logout":
                    self.logout_count += 1
                    return None
                if method == "GET" and path == "/auth/v1/context":
                    if self.context_error:
                        raise module.SmokeFailure("INJECTED_CONTEXT_FAILURE")
                raise AssertionError("unexpected session test request")

        malformed = Browser({"accessToken": "private", "refreshToken": "refresh"})
        manager = module.AuthorizationFixtureManager(
            browser_factory=lambda: malformed,
            material_loader=lambda: {
                "administratorEmail": "administrator@example.test",
                "administratorPassword": "Administrator-Password-9!",
            },
        )
        with self.assertRaises(module.SmokeFailure):
            manager.administrator()
        self.assertIsNone(malformed.access)
        self.assertIsNone(malformed.refresh_token)
        self.assertEqual(1, malformed.logout_count)

        not_ready = Browser(self._token(password_change=True))
        manager = module.AuthorizationFixtureManager(
            browser_factory=lambda: not_ready
        )
        with self.assertRaisesRegex(module.SmokeFailure, "FIXTURE_LOGIN_NOT_READY"):
            manager._login_required("fixture@example.test", "secret")
        self.assertIsNone(not_ready.access)
        self.assertIsNone(not_ready.refresh_token)
        self.assertEqual(1, not_ready.logout_count)

        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=module.FixtureCredentialStore(
                    Path(directory)
                    / "private"
                    / "risk-rule-configuration-smoke.json"
                ),
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "hidden-session-key",
            )
            actors = manager.prepare("READ_ACTIVE")
            manager.logout_actors(actors)
            administrator = manager.administrator()
            broken = Browser(self._token(), context_error=True)
            broken.access = "private-access-token"
            broken.refresh_token = "private-refresh-token"
            manager._try_login = lambda _email, _password, _deadline=None: (
                broken,
                self._token(),
            )
            with self.assertRaisesRegex(
                module.SmokeFailure, "INJECTED_CONTEXT_FAILURE"
            ):
                manager.cleanup(administrator)
            self.assertIsNone(broken.access)
            self.assertIsNone(broken.refresh_token)
            self.assertEqual(1, broken.logout_count)
            manager.logout(administrator)


class _RiskBrowser:
    def __init__(self, actor, gateway_404_paths=()):
        self.actor = actor
        self.gateway_404_paths = set(gateway_404_paths)
        self.journal = []
        self.access = "private-token-" + actor

    @staticmethod
    def new_deadline(timeout_seconds):
        return float(timeout_seconds)

    def request_rate_limited_get(
        self, path, max_retry_after_seconds, deadline
    ):
        del max_retry_after_seconds, deadline
        return self.request("GET", path)

    def request(
        self,
        method,
        path,
        body=None,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        self.journal.append(
            (method, path, body, authenticated, status, dict(extra_headers or {}))
        )
        expected = (status,) if isinstance(status, int) else tuple(status)
        if 403 in expected:
            return _problem("OPERATIONS_PERMISSION_DENIED")
        if 400 in expected:
            return _problem("INVALID_REQUEST")
        if 413 in expected:
            return _problem("GATEWAY_PAYLOAD_TOO_LARGE")
        if 404 in expected:
            code = (
                "GATEWAY_ROUTE_NOT_FOUND"
                if path in self.gateway_404_paths or path.endswith("/unmatched")
                else "RISK_DECISION_CONFIGURATION_NOT_FOUND"
            )
            return _problem(code)
        if method == "GET" and (
            path.startswith("/decision-rule-sets/v1/rule-sets?")
            or path.startswith("/decision-releases/v1/candidates?")
        ):
            page = int(parse_qs(urlsplit(path).query).get("page", ["1"])[0])
            size = int(parse_qs(urlsplit(path).query).get("size", ["200"])[0])
            return {"items": [], "page": page, "size": size, "total": 0}
        return {}


class ReadAndRuntimeProbeTest(unittest.TestCase):
    def test_grant_proves_fresh_synthetic_authorized_read_and_denied_403(self):
        module = require_smoke()
        server = _RiskWriteServer()
        administrator = server.browser("repository-administrator")
        authorized = server.browser("synthetic-authorized")
        denied = server.browser("synthetic-denied")

        class Authorization:
            def administrator(self):
                return administrator

            def grant(self, stage, admin):
                self.assert_identity(admin, administrator)
                self.assert_equal(stage, "ALL_ROUTES_ACTIVE")
                return module.FixtureActors(
                    module.FixtureIdentity(authorized, {}, {}, {}),
                    module.FixtureIdentity(denied, {}, {}, {}),
                )

            @staticmethod
            def assert_identity(actual, expected):
                if actual is not expected:
                    raise AssertionError("grant used the wrong administrator")

            @staticmethod
            def assert_equal(actual, expected):
                if actual != expected:
                    raise AssertionError("grant used the wrong stage")

            @staticmethod
            def logout(browser):
                browser.access = None
                browser.refresh_token = None

        runner = module.RiskRuleConfigurationSmoke(
            authorization=Authorization(),
            stage_observer=lambda _admin: SimpleNamespace(
                stage="ALL_ROUTES_ACTIVE",
                persisted={"resources": {}},
                http_routes={},
                route_details={},
                navigation={},
            ),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-key"
            ),
        )
        runner.grant_authorization_fixture()

        authoring_path = "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE"
        self.assertIn(
            ("synthetic-authorized", "GET", authoring_path, 200),
            [
                (row["actor"], row["method"], row["path"], row["status"])
                for row in server.journal
            ],
        )
        self.assertIn(
            ("synthetic-denied", "GET", authoring_path, 403),
            [
                (row["actor"], row["method"], row["path"], row["status"])
                for row in server.journal
            ],
        )

    def test_resources_exposed_runtime_probe_uses_synthetic_actor_navigation(self):
        module = require_smoke()
        server = _RiskWriteServer()
        administrator = server.browser("repository-administrator")
        authorized = server.browser("synthetic-authorized")
        denied = server.browser("synthetic-denied")
        actors = module.FixtureActors(
            module.FixtureIdentity(authorized, {}, {}, {}),
            module.FixtureIdentity(denied, {}, {}, {}),
        )

        class Authorization:
            def administrator(self):
                return administrator

            def discover(self, admin, stage):
                if admin is not administrator or stage != "RESOURCES_EXPOSED":
                    raise AssertionError("runtime probe used the wrong IAM authority")
                return actors

            @staticmethod
            def logout(browser):
                browser.access = None
                browser.refresh_token = None

        resources = dict(module.RISK_ACTIVE_RESOURCES)
        runner = module.RiskRuleConfigurationSmoke(
            authorization=Authorization(),
            stage_observer=lambda _admin: SimpleNamespace(
                stage="RESOURCES_EXPOSED",
                persisted={"resources": resources},
                http_routes={},
                route_details={},
                navigation=resources,
            ),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-key"
            ),
        )
        runner.probe_runtime_stage("RESOURCES_EXPOSED", "RESOURCES_EXPOSED")

        self.assertTrue(
            any(
                row["actor"] == "synthetic-authorized"
                and row["method"] == "GET"
                and row["path"] == "/authorization/v1/navigation"
                for row in server.journal
            )
        )
        self.assertFalse(
            any(
                row["actor"] == "repository-administrator"
                and row["path"].startswith(("/decision-rule-sets/", "/decision-releases/"))
                for row in server.journal
            )
        )

    def test_detail_probes_use_exhaustive_absent_id_and_distinguish_owner_gateway(self):
        module = require_smoke()

        class DetailBrowser(_RiskBrowser):
            def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
                if method == "GET" and path.startswith(
                    "/decision-rule-sets/v1/rule-sets?"
                ):
                    self.journal.append((method, path, body, authenticated, status, {}))
                    return {
                        "items": [
                            {"ruleSetVersionId": "1"},
                            {"ruleSetVersionId": "3"},
                        ],
                        "page": 1,
                        "size": 200,
                        "total": 2,
                    }
                return super().request(
                    method, path, body, authenticated, status, extra_headers
                )

        authorized = DetailBrowser("administrator")
        denied = _RiskBrowser("denied")
        probe = module.RiskRouteProbe(idempotency_key_factory=lambda: "hidden-key")
        probe.probe_route(
            "operations-risk-rule-set-detail", authorized, denied
        )

        admin_paths = [entry[1] for entry in authorized.journal]
        self.assertIn(
            "/decision-rule-sets/v1/rule-sets?page=1&size=200", admin_paths
        )
        self.assertIn("/decision-rule-sets/v1/rule-sets/2", admin_paths)
        self.assertIn(
            "/decision-rule-sets/v1/rule-sets/2/unmatched", admin_paths
        )
        self.assertIn(
            ("GET", "/decision-rule-sets/v1/rule-sets/2"),
            [(entry[0], entry[1]) for entry in denied.journal],
        )

    def test_read_stage_runs_all_five_exact_get_checks_and_denials(self):
        module = require_smoke()
        authorized = _RiskBrowser("administrator")
        denied = _RiskBrowser("denied")
        probe = module.RiskRouteProbe(idempotency_key_factory=lambda: "hidden-key")
        probe.probe_read_stage(authorized, denied)

        successful_targets = [
            entry
            for entry in authorized.journal
            if entry[4] == 200
            and not entry[1].endswith("/unmatched")
            and "?" not in entry[1]
        ]
        self.assertTrue(
            any("/authoring-context/CHECKOUT_PREPARE" in row[1] for row in successful_targets)
        )
        self.assertEqual(5, len(denied.journal))
        self.assertTrue(all(row[4] == 403 for row in denied.journal))
        self.assertTrue(all(row[0] == "GET" for row in denied.journal))

    def test_runtime_probe_is_read_only_covers_prefix_and_next_gateway_route(self):
        module = require_smoke()
        next_path = "/decision-rule-sets/v1/rule-sets/1/freeze"
        authorized = _RiskBrowser("administrator", gateway_404_paths={next_path})
        denied = _RiskBrowser("denied")
        probe = module.RiskRouteProbe(idempotency_key_factory=lambda: "hidden-key")
        probe.probe_runtime_stage("SAVE_ACTIVE", authorized, denied)

        self.assertTrue(
            any(
                row[0] == "POST"
                and row[1] == "/decision-rule-sets/v1/rule-sets"
                and row[2] == {}
                and row[4] == 400
                for row in authorized.journal
            )
        )
        self.assertTrue(
            any(row[1] == next_path and row[4] == 404 for row in authorized.journal)
        )
        self.assertFalse(
            any(
                row[0] == "POST"
                and row[4] == 200
                for row in authorized.journal
            )
        )
        self.assertEqual(6, len(denied.journal))
        self.assertTrue(all(row[4] == 403 for row in denied.journal))

    def test_runner_requires_exact_or_adjacent_persisted_stage_and_disabled_resources(self):
        module = require_smoke()
        resources = {}

        def observation(stage, resource_statuses=None):
            return SimpleNamespace(
                stage=stage,
                persisted={
                    "resources": resources
                    if resource_statuses is None
                    else resource_statuses
                },
                http_routes={},
                route_details={},
                navigation={},
            )

        class Iam:
            def __init__(self, admin, denied):
                self.admin = admin
                self.denied = denied
                self.discoveries = []

            def administrator(self):
                return self.admin

            def discover(self, admin, stage):
                self.discoveries.append((admin, stage))
                return SimpleNamespace(
                    denied=SimpleNamespace(browser=self.denied)
                )

            def logout(self, browser):
                browser.access = None

            def logout_actors(self, _actors):
                pass

        prior = "READ_ACTIVE"
        persisted = "SAVE_ACTIVE"
        next_save_path = "/decision-rule-sets/v1/rule-sets"
        admin = _RiskBrowser("administrator", gateway_404_paths={next_save_path})
        denied = _RiskBrowser("denied")
        iam = Iam(admin, denied)
        runner = module.RiskRuleConfigurationSmoke(
            authorization=iam,
            stage_observer=lambda _admin: observation(persisted),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-key"
            ),
        )
        runner.probe_runtime_stage(prior, persisted)
        self.assertEqual([(admin, persisted)], iam.discoveries)

        with self.assertRaises(module.SmokeFailure):
            runner.probe_runtime_stage("SAVE_ACTIVE", "READ_ACTIVE")

        visible = {code: "visible" for code in module.RISK_RESOURCE_CODES}
        runner = module.RiskRuleConfigurationSmoke(
            authorization=Iam(_RiskBrowser("admin"), _RiskBrowser("denied")),
            stage_observer=lambda _admin: observation(persisted, visible),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-key"
            ),
        )
        with self.assertRaises(module.SmokeFailure):
            runner.probe_runtime_stage(prior, persisted)

    def test_baseline_runtime_probe_needs_no_synthetic_identity(self):
        module = require_smoke()
        first_path = "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE"
        admin = _RiskBrowser("administrator", gateway_404_paths={first_path})

        class Iam:
            def administrator(self):
                return admin

            def discover(self, _admin, _stage):
                raise AssertionError("BASELINE must not load synthetic credentials")

            def logout(self, browser):
                browser.access = None

        runner = module.RiskRuleConfigurationSmoke(
            authorization=Iam(),
            stage_observer=lambda _admin: SimpleNamespace(
                stage="BASELINE",
                persisted={
                    "resources": {}
                },
                http_routes={},
                route_details={},
                navigation={},
            ),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-key"
            ),
        )
        runner.probe_runtime_stage("BASELINE", "BASELINE")
        self.assertEqual(1, len(admin.journal))
        self.assertEqual(404, admin.journal[0][4])


VALIDATION_CHECKS = (
    "REFERENCE_INTEGRITY",
    "AST_SHAPE",
    "AST_TYPES",
    "FEATURE_DEPENDENCY",
    "HANDLER_HASH",
    "ACTION_PARAMETERS",
    "TERMINAL_ACTION",
    "SYSTEM_OBLIGATION",
)


class _RiskWriteServer:
    def __init__(self):
        self.rules = {}
        self.candidates = {}
        self.next_rule_id = 100
        self.next_release_id = 500
        self.journal = []

    def browser(self, actor="administrator"):
        return _RiskWriteBrowser(self, actor)

    @staticmethod
    def _page(items, page, size):
        start = (page - 1) * size
        return {
            "items": items[start : start + size],
            "page": page,
            "size": size,
            "total": len(items),
        }

    @staticmethod
    def _rule_summary(rule):
        request = rule["request"]
        return {
            "ruleSetVersionId": rule["ruleSetVersionId"],
            "pointVersionId": rule["pointVersionId"],
            "pointCode": request["pointCode"],
            "ruleSetCode": request["ruleSetCode"],
            "ruleSetName": request["ruleSetName"],
            "versionNo": 1,
            "status": rule["status"],
            "contentHash": rule["contentHash"],
            "createdBy": "fixture-actor",
            "changeReason": request["changeReason"],
            "createTime": "2026-09-07T01:02:03.123456Z",
            "frozenBy": "fixture-actor" if rule["status"] == "FROZEN" else None,
            "frozenTime": "2026-09-07T01:03:03.123456Z"
            if rule["status"] == "FROZEN"
            else None,
        }

    @classmethod
    def _rule_detail(cls, rule):
        response_rules = []
        request_rules = sorted(
            rule["request"]["rules"],
            key=lambda value: (value["priorityNo"], value["ruleCode"]),
        )
        for rule_index, request_rule in enumerate(request_rules):
            node_ids = {
                node["nodeKey"]: str(1_000_000 + rule_index * 2_000 + node_index)
                for node_index, node in enumerate(
                    request_rule["conditionNodes"], start=1
                )
            }
            children = {}
            roots = []
            for node in request_rule["conditionNodes"]:
                parent = node["parentNodeKey"]
                if parent is None:
                    roots.append(node)
                else:
                    children.setdefault(parent, []).append(node)
            for siblings in children.values():
                siblings.sort(key=lambda value: value["sequenceNo"])
            ordered_nodes = []

            def append_preorder(node):
                ordered_nodes.append(node)
                for child in children.get(node["nodeKey"], []):
                    append_preorder(child)

            append_preorder(roots[0])
            response_nodes = []
            for node in ordered_nodes:
                parent = node["parentNodeKey"]
                operands = sorted(
                    node["operands"],
                    key=lambda value: (
                        0 if value["operandRole"] == "LEFT" else 1,
                        value["valueSequence"],
                    ),
                )
                response_nodes.append(
                    dict(
                        node,
                        nodeKey=node_ids[node["nodeKey"]],
                        parentNodeKey=None if parent is None else node_ids[parent],
                        operands=operands,
                    )
                )
            response_rules.append(
                dict(request_rule, conditionNodes=response_nodes)
            )
        return dict(cls._rule_summary(rule), rules=response_rules)

    @staticmethod
    def _candidate_summary(candidate):
        return {
            "releaseId": candidate["releaseId"],
            "releaseNo": 1,
            "decisionPointId": "210001",
            "pointCode": "CHECKOUT_PREPARE",
            "pointVersionId": candidate["pointVersionId"],
            "ruleSetVersionId": candidate["ruleSetVersionId"],
            "environmentCode": "TEST",
            "status": candidate["status"],
            "version": candidate["version"],
            "contentHash": candidate["contentHash"],
            "createdBy": "fixture-actor",
            "createTime": "2026-09-07T01:04:03.123456Z",
            "allStaticChecksPassed": candidate["allStaticChecksPassed"],
        }

    @classmethod
    def _candidate_detail(cls, candidate, rule):
        return dict(
            cls._candidate_summary(candidate),
            waitingForShadow=candidate["waitingForShadow"],
            frozenComposition={
                "decisionPointId": "210001",
                "pointCode": "CHECKOUT_PREPARE",
                "pointVersionId": candidate["pointVersionId"],
                "pointVersionNo": 1,
                "pointVersionContentHash": "b" * 64,
                "ruleSetVersionId": candidate["ruleSetVersionId"],
                "ruleSetCode": rule["request"]["ruleSetCode"],
                "ruleSetVersionNo": 1,
                "ruleSetContentHash": rule["contentHash"],
            },
            stateChanges=[],
            staticChecks=[
                {
                    "auditEventId": str(8_000 + index),
                    "eventTime": "2026-09-07T01:05:03.123456Z",
                    "checkCode": code,
                    "checkStatus": "PASSED",
                    "severity": "ERROR",
                    "errorCode": None,
                    "errorSummary": None,
                }
                for index, code in enumerate(VALIDATION_CHECKS)
            ]
            if candidate["allStaticChecksPassed"] is True
            else [],
        )


class _RiskWriteBrowser:
    def __init__(self, server, actor):
        self.server = server
        self.actor = actor
        self.access = "private-access-" + actor
        self.refresh_token = "private-refresh-" + actor

    @staticmethod
    def new_deadline(timeout_seconds):
        return float(timeout_seconds)

    def request_rate_limited_get(
        self, path, max_retry_after_seconds, deadline
    ):
        del max_retry_after_seconds, deadline
        return self.request("GET", path)

    @staticmethod
    def _expected(status):
        return (status,) if isinstance(status, int) else tuple(status)

    def _record(self, method, path, body, status, extra_headers, raw=False):
        entry = {
            "actor": self.actor,
            "method": method,
            "path": path,
            "body": body,
            "status": status,
            "headers": dict(extra_headers or {}),
            "raw": raw,
        }
        self.server.journal.append(entry)
        return entry

    def request(
        self,
        method,
        path,
        body=None,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        self._record(method, path, body, status, extra_headers)
        expected = self._expected(status)
        if 403 in expected:
            return _problem("OPERATIONS_PERMISSION_DENIED")
        if 400 in expected:
            return _problem("INVALID_REQUEST")
        if 404 in expected:
            return _problem(
                "GATEWAY_ROUTE_NOT_FOUND"
                if path.endswith("/unmatched")
                else "RISK_DECISION_CONFIGURATION_NOT_FOUND"
            )
        split = urlsplit(path)
        query = parse_qs(split.query)
        clean = split.path
        if method == "GET" and clean == "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE":
            return {}
        if method == "GET" and clean == "/authorization/v1/navigation":
            return {
                "resources": [
                    {
                        "resourceId": str(90_000 + index),
                        "resourceCode": code,
                        "resourceType": "MENU" if index < 2 else "BUTTON",
                        "parentResourceId": None,
                        "displayName": label,
                        "frontendRoute": None,
                        "componentKey": None,
                        "sortOrder": index,
                    }
                    for index, (code, label) in enumerate(
                        require_smoke().RISK_ACTIVE_RESOURCES.items()
                    )
                ]
            }
        if method == "GET" and clean == "/decision-rule-sets/v1/rule-sets":
            rows = [
                self.server._rule_summary(rule)
                for rule in self.server.rules.values()
                if not query.get("ruleSetCode")
                or rule["request"]["ruleSetCode"] == query["ruleSetCode"][0]
            ]
            page = int(query.get("page", ["1"])[0])
            size = int(query.get("size", ["200"])[0])
            return self.server._page(rows, page, size)
        if method == "GET" and clean.startswith("/decision-rule-sets/v1/rule-sets/"):
            identifier = clean.rsplit("/", 1)[1]
            return self.server._rule_detail(self.server.rules[identifier])
        if method == "POST" and clean == "/decision-rule-sets/v1/rule-sets":
            return self._save(body)
        if method == "POST" and clean.endswith("/freeze"):
            identifier = clean.split("/")[-2]
            rule = self.server.rules[identifier]
            self.assert_equal(rule["contentHash"], body["expectedContentHash"])
            rule["status"] = "FROZEN"
            summary = self.server._rule_summary(rule)
            return {
                key: summary[key]
                for key in (
                    "ruleSetVersionId",
                    "pointVersionId",
                    "ruleSetCode",
                    "versionNo",
                    "status",
                    "contentHash",
                    "frozenBy",
                    "frozenTime",
                )
            }
        if method == "GET" and clean == "/decision-releases/v1/candidates":
            rows = [
                self.server._candidate_summary(candidate)
                for candidate in self.server.candidates.values()
            ]
            page = int(query.get("page", ["1"])[0])
            size = int(query.get("size", ["200"])[0])
            return self.server._page(rows, page, size)
        if method == "GET" and clean.startswith("/decision-releases/v1/candidates/"):
            identifier = clean.rsplit("/", 1)[1]
            candidate = self.server.candidates[identifier]
            rule = self.server.rules[candidate["ruleSetVersionId"]]
            return self.server._candidate_detail(candidate, rule)
        if method == "POST" and clean == "/decision-releases/v1/candidates":
            rule = self.server.rules[body["ruleSetVersionId"]]
            self.assert_equal("FROZEN", rule["status"])
            self.assert_equal(rule["contentHash"], body["expectedRuleSetContentHash"])
            identifier = str(self.server.next_release_id)
            self.server.next_release_id += 1
            candidate = {
                "releaseId": identifier,
                "pointVersionId": rule["pointVersionId"],
                "ruleSetVersionId": rule["ruleSetVersionId"],
                "status": "DRAFT",
                "version": 1,
                "contentHash": "c" * 63 + str(int(identifier) % 10),
                "allStaticChecksPassed": None,
                "waitingForShadow": False,
            }
            self.server.candidates[identifier] = candidate
            result = self.server._candidate_summary(candidate)
            result.pop("allStaticChecksPassed")
            result["initialStateChange"] = {
                "auditEventId": "7001",
                "eventTime": "2026-09-07T01:04:03.123456Z",
                "fromStatus": None,
                "toStatus": "DRAFT",
                "reasonCode": "CREATED",
                "triggerType": "COMMAND",
                "actorId": "fixture-actor",
                "aggregateVersion": 1,
            }
            return result
        if method == "POST" and clean.endswith("/validate"):
            identifier = clean.split("/")[-2]
            candidate = self.server.candidates[identifier]
            self.assert_equal({"expectedVersion": 1}, body)
            candidate["status"] = "VALIDATING"
            candidate["version"] = 2
            candidate["allStaticChecksPassed"] = True
            candidate["waitingForShadow"] = True
            return {
                "releaseId": identifier,
                "releaseNo": 1,
                "environmentCode": "TEST",
                "status": "VALIDATING",
                "version": 2,
                "contentHash": candidate["contentHash"],
                "allStaticChecksPassed": True,
                "checks": [
                    {
                        "checkCode": code,
                        "checkStatus": "PASSED",
                        "severity": "ERROR",
                        "errorCode": None,
                        "errorSummary": None,
                    }
                    for code in VALIDATION_CHECKS
                ],
            }
        if method == "POST" and clean == "/auth/v1/logout":
            self.access = None
            self.refresh_token = None
            return None
        raise AssertionError("Unexpected Risk write request: {0} {1}".format(method, path))

    @staticmethod
    def assert_equal(expected, actual):
        if expected != actual:
            raise AssertionError("{0!r} != {1!r}".format(expected, actual))

    def _save(self, body):
        code = body["ruleSetCode"]
        if any(rule["request"]["ruleSetCode"] == code for rule in self.server.rules.values()):
            raise AssertionError("test generated a duplicate Rule Set code")
        identifier = str(self.server.next_rule_id)
        self.server.next_rule_id += 1
        rule = {
            "ruleSetVersionId": identifier,
            "pointVersionId": "210002",
            "request": body,
            "status": "DRAFT",
            "contentHash": format(int(identifier), "064x"),
        }
        self.server.rules[identifier] = rule
        summary = self.server._rule_summary(rule)
        return {
            key: summary[key]
            for key in (
                "ruleSetVersionId",
                "pointVersionId",
                "ruleSetCode",
                "versionNo",
                "status",
                "contentHash",
                "createdBy",
                "createTime",
            )
        }

    def request_raw(
        self,
        method,
        path,
        body,
        authenticated=True,
        status=200,
        extra_headers=None,
    ):
        self._record(method, path, body, status, extra_headers, raw=True)
        if len(body) == 65_537:
            self.assert_equal((413,), self._expected(status))
            return _problem("GATEWAY_PAYLOAD_TOO_LARGE")
        self.assert_equal(65_536, len(body))
        self.assert_equal((200,), self._expected(status))
        return self._save(json.loads(body))


class WriteRouteSmokeTest(unittest.TestCase):
    CODES = (
        "SMOKE_20260907T010201Z_000000000001",
        "SMOKE_20260907T010202Z_000000000002",
        "SMOKE_20260907T010203Z_000000000003",
        "SMOKE_20260907T010204Z_000000000004",
    )

    @staticmethod
    def _noncanonical_rekey_contract():
        left_operand = {
            "operandRole": "LEFT",
            "valueSequence": 1,
            "sourceType": "CONTEXT",
            "fieldCode": "country_code",
            "valueType": "STRING",
            "literalValue": None,
        }
        right_operand = {
            "operandRole": "RIGHT",
            "valueSequence": 1,
            "sourceType": "LITERAL",
            "fieldCode": None,
            "valueType": "STRING",
            "literalValue": "AE",
        }
        first_rule = {
            "ruleCode": "SMOKE_RULE_001",
            "ruleName": "SyntheticRule001",
            "priorityNo": 1,
            "enabledFlag": True,
            "reasonCode": None,
            "reasonMessage": None,
            "additionalActions": [],
            "conditionNodes": [
                {
                    "nodeKey": "child",
                    "parentNodeKey": "root",
                    "sequenceNo": 1,
                    "nodeType": "PREDICATE",
                    "groupOperator": None,
                    "comparisonOperator": "EQ",
                    "operands": [right_operand, left_operand],
                },
                {
                    "nodeKey": "root",
                    "parentNodeKey": None,
                    "sequenceNo": 1,
                    "nodeType": "GROUP",
                    "groupOperator": "NOT",
                    "comparisonOperator": None,
                    "operands": [],
                },
            ],
            "action": {
                "actionCode": "ALLOW_CHECKOUT",
                "actionVersionNo": 1,
            },
        }
        second_rule = {
            "ruleCode": "SMOKE_RULE_002",
            "ruleName": "SyntheticRule002",
            "priorityNo": 2,
            "enabledFlag": True,
            "reasonCode": None,
            "reasonMessage": None,
            "additionalActions": [],
            "conditionNodes": [
                {
                    "nodeKey": "root-two",
                    "parentNodeKey": None,
                    "sequenceNo": 1,
                    "nodeType": "PREDICATE",
                    "groupOperator": None,
                    "comparisonOperator": "IS_NULL",
                    "operands": [left_operand],
                }
            ],
            "action": {
                "actionCode": "ALLOW_CHECKOUT",
                "actionVersionNo": 1,
            },
        }
        requested_rules = [second_rule, first_rule]
        canonical_requested_rules = [
            dict(
                first_rule,
                conditionNodes=[
                    first_rule["conditionNodes"][1],
                    dict(
                        first_rule["conditionNodes"][0],
                        operands=[left_operand, right_operand],
                    ),
                ],
            ),
            second_rule,
        ]
        canonical_actual_rules = [
            dict(
                first_rule,
                conditionNodes=[
                    dict(first_rule["conditionNodes"][1], nodeKey="4001"),
                    dict(
                        first_rule["conditionNodes"][0],
                        nodeKey="4002",
                        parentNodeKey="4001",
                        operands=[left_operand, right_operand],
                    ),
                ],
            ),
            dict(
                second_rule,
                conditionNodes=[
                    dict(second_rule["conditionNodes"][0], nodeKey="5001")
                ],
            ),
        ]
        noncanonical_actual_rules = [
            dict(
                second_rule,
                conditionNodes=[
                    dict(second_rule["conditionNodes"][0], nodeKey="5001")
                ],
            ),
            dict(
                first_rule,
                conditionNodes=[
                    dict(
                        first_rule["conditionNodes"][0],
                        nodeKey="4002",
                        parentNodeKey="4001",
                    ),
                    dict(first_rule["conditionNodes"][1], nodeKey="4001"),
                ],
            ),
        ]
        return (
            requested_rules,
            canonical_requested_rules,
            canonical_actual_rules,
            noncanonical_actual_rules,
        )

    def test_rule_detail_accepts_owner_canonical_order_for_noncanonical_request(self):
        module = require_smoke()
        requested, canonical_requested, canonical_actual, _ = (
            self._noncanonical_rekey_contract()
        )
        noncanonical_rule_order = json.loads(json.dumps(canonical_requested))
        noncanonical_rule_order.reverse()
        noncanonical_node_order = json.loads(json.dumps(canonical_requested))
        noncanonical_node_order[0]["conditionNodes"].reverse()
        noncanonical_operand_order = json.loads(json.dumps(canonical_requested))
        noncanonical_operand_order[0]["conditionNodes"][1]["operands"].reverse()

        for order, request_value in (
            ("rules", noncanonical_rule_order),
            ("nodes", noncanonical_node_order),
            ("operands", noncanonical_operand_order),
            ("all", requested),
        ):
            with self.subTest(order=order):
                module.RiskRouteProbe._validate_rekeyed_rules(
                    canonical_actual, request_value
                )

    def test_rule_detail_rejects_noncanonical_owner_order(self):
        module = require_smoke()
        requested, canonical_requested, canonical_actual, noncanonical_actual = (
            self._noncanonical_rekey_contract()
        )
        noncanonical_rule_order = json.loads(json.dumps(canonical_actual))
        noncanonical_rule_order.reverse()
        matching_rule_request = json.loads(json.dumps(canonical_requested))
        matching_rule_request.reverse()
        noncanonical_node_order = json.loads(json.dumps(canonical_actual))
        noncanonical_node_order[0]["conditionNodes"].reverse()
        matching_node_request = json.loads(json.dumps(canonical_requested))
        matching_node_request[0]["conditionNodes"].reverse()
        noncanonical_operand_order = json.loads(json.dumps(canonical_actual))
        noncanonical_operand_order[0]["conditionNodes"][1]["operands"].reverse()
        matching_operand_request = json.loads(json.dumps(canonical_requested))
        matching_operand_request[0]["conditionNodes"][1]["operands"].reverse()

        for order, actual_value, request_value in (
            ("rules", noncanonical_rule_order, matching_rule_request),
            ("nodes", noncanonical_node_order, matching_node_request),
            ("operands", noncanonical_operand_order, matching_operand_request),
            ("all", noncanonical_actual, requested),
        ):
            with self.subTest(order=order):
                with self.assertRaisesRegex(
                    module.SmokeFailure, "RULE_SET_DETAIL_RULES_INVALID"
                ):
                    module.RiskRouteProbe._validate_rekeyed_rules(
                        actual_value, request_value
                    )

    def test_rule_detail_rejects_duplicate_node_id_across_rules(self):
        module = require_smoke()
        _, requested, canonical_actual, _ = self._noncanonical_rekey_contract()
        duplicate_node_id = json.loads(json.dumps(canonical_actual))
        duplicate_node_id[1]["conditionNodes"][0]["nodeKey"] = "4001"

        with self.assertRaisesRegex(
            module.SmokeFailure, "RULE_SET_DETAIL_RULES_INVALID"
        ):
            module.RiskRouteProbe._validate_rekeyed_rules(
                duplicate_node_id, requested
            )

    def test_rule_detail_rekey_preserves_parent_topology_and_requires_ids(self):
        module = require_smoke()
        requested_rules = [
            {
                "ruleCode": "SMOKE_RULE_001",
                "ruleName": "SyntheticRule001",
                "priorityNo": 1,
                "enabledFlag": True,
                "reasonCode": None,
                "reasonMessage": None,
                "additionalActions": [],
                "conditionNodes": [
                    {
                        "nodeKey": "root",
                        "parentNodeKey": None,
                        "sequenceNo": 1,
                        "nodeType": "GROUP",
                        "groupOperator": "NOT",
                        "comparisonOperator": None,
                        "operands": [],
                    },
                    {
                        "nodeKey": "child",
                        "parentNodeKey": "root",
                        "sequenceNo": 1,
                        "nodeType": "PREDICATE",
                        "groupOperator": None,
                        "comparisonOperator": "IS_NULL",
                        "operands": [
                            {
                                "operandRole": "LEFT",
                                "valueSequence": 1,
                                "sourceType": "CONTEXT",
                                "fieldCode": "country_code",
                                "valueType": "STRING",
                                "literalValue": None,
                            }
                        ],
                    },
                ],
                "action": {
                    "actionCode": "ALLOW_CHECKOUT",
                    "actionVersionNo": 1,
                },
            }
        ]
        actual_rules = json.loads(json.dumps(requested_rules))
        actual_rules[0]["conditionNodes"][0]["nodeKey"] = "4001"
        actual_rules[0]["conditionNodes"][1]["nodeKey"] = "4002"
        actual_rules[0]["conditionNodes"][1]["parentNodeKey"] = "4001"

        module.RiskRouteProbe._validate_rekeyed_rules(
            actual_rules, requested_rules
        )

        broken_parent = json.loads(json.dumps(actual_rules))
        broken_parent[0]["conditionNodes"][1]["parentNodeKey"] = "4999"
        with self.assertRaisesRegex(
            module.SmokeFailure, "RULE_SET_DETAIL_RULES_INVALID"
        ):
            module.RiskRouteProbe._validate_rekeyed_rules(
                broken_parent, requested_rules
            )

        local_key_echo = json.loads(json.dumps(actual_rules))
        local_key_echo[0]["conditionNodes"][0]["nodeKey"] = "root"
        with self.assertRaisesRegex(
            module.SmokeFailure, "RULE_SET_DETAIL_NODE_ID_INVALID"
        ):
            module.RiskRouteProbe._validate_rekeyed_rules(
                local_key_echo, requested_rules
            )

    def test_each_write_route_builds_only_its_cumulative_prerequisite_chain(self):
        module = require_smoke()
        routes_and_counts = (
            ("operations-risk-rule-set-save", (1, 0, 0, 0)),
            ("operations-risk-rule-set-freeze", (1, 1, 0, 0)),
            ("operations-risk-test-candidate-create", (1, 1, 1, 0)),
            ("operations-risk-test-candidate-validate", (1, 1, 1, 2)),
        )
        for index, (route, counts) in enumerate(routes_and_counts):
            with self.subTest(route=route):
                server = _RiskWriteServer()
                authorized = server.browser()
                denied = server.browser("denied")
                probe = module.RiskRouteProbe(
                    idempotency_key_factory=lambda: "hidden-general-key",
                    rule_set_code_factory=lambda value=self.CODES[index]: value,
                )
                probe.probe_route_smoke(route, authorized, denied)
                saves = [
                    row
                    for row in server.journal
                    if row["method"] == "POST"
                    and row["path"] == "/decision-rule-sets/v1/rule-sets"
                    and row["status"] == 200
                ]
                freezes = [
                    row
                    for row in server.journal
                    if row["method"] == "POST"
                    and row["path"].endswith("/freeze")
                    and row["status"] == 200
                ]
                creates = [
                    row
                    for row in server.journal
                    if row["method"] == "POST"
                    and row["path"] == "/decision-releases/v1/candidates"
                    and row["status"] == 200
                ]
                validates = [
                    row
                    for row in server.journal
                    if row["method"] == "POST"
                    and row["path"].endswith("/validate")
                    and row["status"] == 200
                ]
                self.assertEqual(counts, (len(saves), len(freezes), len(creates), len(validates)))
                denied_calls = [row for row in server.journal if row["status"] == 403]
                self.assertEqual(1, len(denied_calls))
                expected_fragments = {
                    "operations-risk-rule-set-save": "/decision-rule-sets/v1/rule-sets",
                    "operations-risk-rule-set-freeze": "/freeze",
                    "operations-risk-test-candidate-create": "/decision-releases/v1/candidates",
                    "operations-risk-test-candidate-validate": "/validate",
                }
                self.assertIn(expected_fragments[route], denied_calls[0]["path"])

    def test_validate_requires_exact_checks_detail_waiting_and_identical_replay(self):
        module = require_smoke()
        server = _RiskWriteServer()
        probe = module.RiskRouteProbe(
            idempotency_key_factory=lambda: "hidden-general-key",
            rule_set_code_factory=lambda: self.CODES[-1],
        )
        probe.probe_route_smoke(
            "operations-risk-test-candidate-validate",
            server.browser(),
            server.browser("denied"),
        )
        validates = [
            row
            for row in server.journal
            if row["path"].endswith("/validate") and row["status"] == 200
        ]
        self.assertEqual(2, len(validates))
        self.assertIs(validates[0]["body"], validates[1]["body"])
        self.assertEqual(validates[0]["headers"], validates[1]["headers"])
        release_id = validates[0]["path"].split("/")[-2]
        self.assertEqual(
            "risk-validate-v1-{0}-1".format(release_id),
            validates[0]["headers"]["Idempotency-Key"],
        )
        details = [
            row
            for row in server.journal
            if row["method"] == "GET"
            and row["path"] == "/decision-releases/v1/candidates/" + release_id
        ]
        self.assertGreaterEqual(len(details), 3)


class _FullAuthorization:
    def __init__(self, module, authorized_browser, denied_browser):
        self.module = module
        self.admin = SimpleNamespace(access="admin-access", refresh_token=None)
        self.authorized_browser = authorized_browser
        self.denied_browser = denied_browser
        self.authorized_role = {
            "roleId": "7001",
            "roleCode": module.AUTHORIZED_ROLE_CODE,
            "roleName": "RiskRuleSmokeAuthorized",
            "systemRole": False,
            "version": 1,
            "permissionIds": list(module.AUTHORIZED_PERMISSION_IDS),
            "nodes": [],
        }
        self.denied_role = {
            "roleId": "7002",
            "roleCode": module.DENIED_ROLE_CODE,
            "roleName": "RiskRuleSmokeDenied",
            "systemRole": False,
            "version": 1,
            "permissionIds": [module.OPERATIONS_SYSTEM_READ],
            "nodes": [],
        }
        self.updates = []
        self.discover_count = 0

    def administrator(self):
        return self.admin

    def discover(self, admin, stage):
        if admin is not self.admin or stage != "RESOURCES_EXPOSED":
            raise AssertionError("full used wrong IAM authority or stage")
        self.discover_count += 1
        return self.module.FixtureActors(
            self.module.FixtureIdentity(
                self.authorized_browser, self.authorized_role, {}, {}
            ),
            self.module.FixtureIdentity(
                self.denied_browser, self.denied_role, {}, {}
            ),
        )

    def replace_authorized_permissions(self, admin, role, permissions):
        if admin is not self.admin or role["roleId"] != "7001":
            raise AssertionError("repository administrator or fixture role changed")
        self.updates.append(tuple(permissions))
        self.authorized_role = dict(
            role,
            version=role["version"] + 1,
            permissionIds=list(permissions),
        )
        return self.authorized_role

    def logout(self, browser):
        browser.access = None
        browser.refresh_token = None

    def logout_actors(self, actors):
        self.logout(actors.authorized.browser)
        self.logout(actors.denied.browser)


class FullAndCleanupTest(unittest.TestCase):
    def test_full_proves_live_revoke_restore_size_boundaries_and_exact_nacos(self):
        module = require_smoke()
        server = _RiskWriteServer()
        authorized = server.browser("synthetic-authorized")
        denied = server.browser("synthetic-denied")
        authorization = _FullAuthorization(module, authorized, denied)
        codes = iter(
            (
                "SMOKE_20260907T020201Z_ABCDEF000001",
                "SMOKE_20260907T020202Z_ABCDEF000002",
                "SMOKE_20260907T020203Z_ABCDEF000003",
            )
        )
        nacos_calls = []

        def nacos(namespace, group, service):
            nacos_calls.append((namespace, group, service))
            return [
                {
                    "ip": "172.30.73.25",
                    "port": 18104,
                    "healthy": True,
                    "enabled": True,
                }
            ]

        resources = dict(module.RISK_ACTIVE_RESOURCES)
        runner = module.RiskRuleConfigurationSmoke(
            authorization=authorization,
            stage_observer=lambda admin: SimpleNamespace(
                stage="RESOURCES_EXPOSED",
                persisted={"resources": resources},
                http_routes={},
                route_details={},
                navigation=resources,
            ),
            route_probe=module.RiskRouteProbe(
                idempotency_key_factory=lambda: "hidden-general-key",
                rule_set_code_factory=lambda: next(codes),
            ),
            nacos_probe=nacos,
        )
        runner.full()

        self.assertEqual(2, len(authorization.updates))
        self.assertEqual(
            set(module.AUTHORIZED_PERMISSION_IDS) - {module.RISK_PERMISSION_IDS[0]},
            set(authorization.updates[0]),
        )
        self.assertEqual(
            set(module.AUTHORIZED_PERMISSION_IDS), set(authorization.updates[1])
        )
        self.assertGreaterEqual(authorization.discover_count, 2)
        self.assertTrue(
            any(
                row["actor"] == "synthetic-authorized"
                and row["method"] == "GET"
                and row["path"] == "/authorization/v1/navigation"
                for row in server.journal
            )
        )
        raw = [row for row in server.journal if row["raw"]]
        self.assertEqual([65_536, 65_537], [len(row["body"]) for row in raw])
        self.assertIsInstance(json.loads(raw[0]["body"]), dict)
        self.assertIsInstance(json.loads(raw[1]["body"]), dict)
        self.assertEqual(b" ", raw[1]["body"][-1:])
        successful_chain = [
            row
            for row in server.journal
            if row["actor"] == "synthetic-authorized"
            and row["status"] == 200
            and not row["raw"]
            and row["method"] == "POST"
        ]
        self.assertEqual(
            (1, 1, 1, 2),
            (
                sum(
                    row["path"] == "/decision-rule-sets/v1/rule-sets"
                    for row in successful_chain
                ),
                sum(row["path"].endswith("/freeze") for row in successful_chain),
                sum(
                    row["path"] == "/decision-releases/v1/candidates"
                    for row in successful_chain
                ),
                sum(row["path"].endswith("/validate") for row in successful_chain),
            ),
        )
        denied_write_paths = {
            row["path"]
            for row in server.journal
            if row["actor"] == "synthetic-denied"
            and row["status"] == 403
            and row["method"] == "POST"
        }
        self.assertEqual(4, len(denied_write_paths))
        self.assertIn("/decision-rule-sets/v1/rule-sets", denied_write_paths)
        self.assertTrue(any(path.endswith("/freeze") for path in denied_write_paths))
        self.assertIn("/decision-releases/v1/candidates", denied_write_paths)
        self.assertTrue(any(path.endswith("/validate") for path in denied_write_paths))

        oversize_code = "SMOKE_20260907T020203Z_ABCDEF000003"
        self.assertFalse(
            any(
                rule["request"]["ruleSetCode"] == oversize_code
                for rule in server.rules.values()
            )
        )
        self.assertEqual(
            [("mor-test", "DEFAULT_GROUP", "risk-decision-service")], nacos_calls
        )

    def test_role_update_unknown_replays_once_with_identical_bytes_and_key(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=module.FixtureCredentialStore(
                    Path(directory) / "private" / "risk-rule-configuration-smoke.json"
                ),
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "same-hidden-key",
            )
            actors = manager.prepare("ALL_ROUTES_ACTIVE")
            admin = manager.administrator()
            original_raw = admin.request_raw
            calls = []

            def timeout_once(method, path, body, **kwargs):
                calls.append((body, kwargs["extra_headers"]["Idempotency-Key"]))
                if len(calls) == 1:
                    raise TimeoutError("unknown response")
                return original_raw(method, path, body, **kwargs)

            admin.request_raw = timeout_once
            updated = manager.replace_authorized_permissions(
                admin, actors.authorized.role, module.AUTHORIZED_PERMISSION_IDS
            )
            self.assertEqual(2, len(calls))
            self.assertIs(calls[0][0], calls[1][0])
            self.assertEqual(
                [("same-hidden-key"), ("same-hidden-key")],
                [calls[0][1], calls[1][1]],
            )
            self.assertEqual(
                set(module.AUTHORIZED_PERMISSION_IDS),
                set(updated["permissionIds"]),
            )

    def test_cleanup_removes_only_fixture_and_is_idempotent(self):
        module = require_smoke()
        server = _IamServer(filler_count=2)
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "hidden-cleanup-key",
            )
            actors = manager.prepare("READ_ACTIVE")
            fixture_role_ids = {
                actors.authorized.role["roleId"], actors.denied.role["roleId"]
            }
            manager.logout_actors(actors)
            filler_roles = {
                role_id
                for role_id, role in server.roles.items()
                if role["roleCode"].startswith("FILLER_ROLE_")
            }
            filler_accounts = {
                account_id
                for account_id, account in server.accounts.items()
                if account["email"].startswith("filler-")
            }
            manager.cleanup()
            self.assertFalse(store.path.exists())
            self.assertEqual(
                filler_roles,
                {
                    role_id
                    for role_id, role in server.roles.items()
                    if role["roleCode"].startswith("FILLER_ROLE_")
                },
            )
            self.assertTrue(filler_roles <= set(server.roles))
            self.assertTrue(
                all(server.accounts[item]["status"] == "ACTIVE" for item in filler_accounts)
            )
            self.assertTrue(
                all(
                    server.roles[role_id]["status"] == "INACTIVE"
                    for role_id in fixture_role_ids
                )
            )
            self.assertFalse(
                any(
                    role["status"] == "ACTIVE"
                    and (
                        role["roleCode"].startswith(
                            module.AUTHORIZED_ROLE_CODE + "_"
                        )
                        or role["roleCode"].startswith(
                            module.DENIED_ROLE_CODE + "_"
                        )
                    )
                    for role in server.roles.values()
                )
            )
            manager.cleanup()

    def test_cleanup_allows_a_new_fixture_generation_after_soft_delete(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "hidden-cleanup-key",
            )

            first = manager.prepare("READ_ACTIVE")
            first_role_codes = {
                first.authorized.role["roleCode"], first.denied.role["roleCode"]
            }
            first_account_ids = {
                first.authorized.account["accountId"],
                first.denied.account["accountId"],
            }
            first_emails = {
                server.accounts[account_id]["email"]
                for account_id in first_account_ids
            }
            manager.logout_actors(first)
            manager.cleanup()

            second = manager.prepare("READ_ACTIVE")
            second_role_codes = {
                second.authorized.role["roleCode"], second.denied.role["roleCode"]
            }
            second_account_ids = {
                second.authorized.account["accountId"],
                second.denied.account["accountId"],
            }
            second_emails = {
                server.accounts[account_id]["email"]
                for account_id in second_account_ids
            }

            self.assertTrue(first_role_codes.isdisjoint(second_role_codes))
            self.assertTrue(first_emails.isdisjoint(second_emails))
            self.assertTrue(first_account_ids.isdisjoint(second_account_ids))
            self.assertTrue(
                all(
                    role["status"] == "INACTIVE"
                    for role in server.roles.values()
                    if role["roleCode"] in first_role_codes
                )
            )
            self.assertTrue(
                all(
                    server.accounts[account_id]["status"] == "CLOSED"
                    for account_id in first_account_ids
                )
            )
            manager.logout_actors(second)

    def test_prepare_requires_credentials_when_a_fixture_generation_is_active(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "hidden-prepare-key",
            )
            actors = manager.prepare("READ_ACTIVE")
            active_role_ids = {
                actors.authorized.role["roleId"], actors.denied.role["roleId"]
            }
            manager.logout_actors(actors)
            store.remove()
            writes_before = sum(
                entry["method"] in {"POST", "PUT", "DELETE"}
                and entry["path"].startswith("/iam/v1/")
                for entry in server.journal
            )

            with self.assertRaisesRegex(
                module.SmokeFailure,
                "FIXTURE_CREDENTIALS_REQUIRED_FOR_PREPARE",
            ):
                manager.prepare("READ_ACTIVE")

            writes_after = sum(
                entry["method"] in {"POST", "PUT", "DELETE"}
                and entry["path"].startswith("/iam/v1/")
                for entry in server.journal
            )
            self.assertEqual(writes_before, writes_after)
            self.assertFalse(store.path.exists())
            self.assertEqual(
                active_role_ids,
                {
                    role_id
                    for role_id, role in server.roles.items()
                    if role["status"] == "ACTIVE"
                    and (
                        role["roleCode"].startswith(
                            module.AUTHORIZED_ROLE_CODE + "_"
                        )
                        or role["roleCode"].startswith(
                            module.DENIED_ROLE_CODE + "_"
                        )
                    )
                },
            )

    def test_cleanup_fails_closed_when_role_is_missing_but_account_is_active(self):
        module = require_smoke()
        server = _IamServer()
        with tempfile.TemporaryDirectory() as directory:
            store = module.FixtureCredentialStore(
                Path(directory) / "private" / "risk-rule-configuration-smoke.json"
            )
            manager = module.AuthorizationFixtureManager(
                browser_factory=server.browser,
                material_loader=lambda: {
                    "administratorEmail": server.ADMIN_EMAIL,
                    "administratorPassword": server.ADMIN_PASSWORD,
                },
                credential_store=store,
                password_factory=lambda label: "Fixture-{0}-Aa9!".format(label),
                idempotency_key_factory=lambda: "hidden-cleanup-key",
            )
            actors = manager.prepare("READ_ACTIVE")
            authorized_account_id = actors.authorized.account["accountId"]
            denied_role_code = actors.denied.role["roleCode"]
            manager.logout_actors(actors)
            authorized_account = server.accounts[authorized_account_id]
            authorized_role_id = authorized_account["roleIds"][0]
            authorized_account["roleIds"] = []
            del server.roles[authorized_role_id]

            with self.assertRaisesRegex(
                module.SmokeFailure, "FIXTURE_ACCOUNT_WITHOUT_ROLE"
            ):
                manager.cleanup()

            self.assertTrue(store.path.exists())
            self.assertEqual("ACTIVE", authorized_account["status"])
            self.assertTrue(
                any(
                    role["status"] == "ACTIVE"
                    and role["roleCode"] == denied_role_code
                    for role in server.roles.values()
                )
            )


if __name__ == "__main__":
    unittest.main()
