#!/usr/bin/env python3
"""Offline contract tests for Gateway Snapshot evidence parsing."""

import io
import json
import unittest
from contextlib import redirect_stderr

import gateway_snapshot_metrics as subject


VALID_PROMETHEUS = """\
# HELP gateway_snapshot_valid Whether this instance has loaded a valid snapshot
# TYPE gateway_snapshot_valid gauge
gateway_snapshot_valid 1.0
gateway_snapshot_last_refresh_result{result="NEVER_ATTEMPTED"} 0.0
gateway_snapshot_last_refresh_result{result="SUCCESS"} 1.0
gateway_snapshot_last_refresh_result{result="FAILURE"} 0.0
gateway_snapshot_last_refresh_result{result="SUPPRESSED"} 0.0
gateway_snapshot_last_failure_reason{reason="NONE"} 1.0
gateway_snapshot_last_failure_reason{reason="EMPTY_RESPONSE"} 0.0
gateway_snapshot_last_failure_reason{reason="INVALID_SNAPSHOT"} 0.0
gateway_snapshot_last_failure_reason{reason="CONTROL_PLANE_UNAVAILABLE"} 0.0
gateway_snapshot_last_valid_in_use 0.0
gateway_snapshot_routes{channel="ALL"} 27.0
gateway_snapshot_routes{channel="OPERATIONS"} 27.0
gateway_snapshot_routes{channel="CLIENT"} 0.0
process_uptime_seconds 42.0
"""

INVALID_FALLBACK_PROMETHEUS = """\
gateway_snapshot_valid 1
gateway_snapshot_last_refresh_result{result="NEVER_ATTEMPTED"} 0
gateway_snapshot_last_refresh_result{result="SUCCESS"} 0
gateway_snapshot_last_refresh_result{result="FAILURE"} 1
gateway_snapshot_last_refresh_result{result="SUPPRESSED"} 0
gateway_snapshot_last_failure_reason{reason="NONE"} 0
gateway_snapshot_last_failure_reason{reason="EMPTY_RESPONSE"} 0
gateway_snapshot_last_failure_reason{reason="INVALID_SNAPSHOT"} 1
gateway_snapshot_last_failure_reason{reason="CONTROL_PLANE_UNAVAILABLE"} 0
gateway_snapshot_last_valid_in_use 1
gateway_snapshot_routes{channel="OPERATIONS"} 32
"""


FIRST_FIVE_ACTIVATION_CODES = (
    "operations-risk-rule-authoring-context",
    "operations-risk-rule-set-list",
    "operations-risk-rule-set-detail",
    "operations-risk-test-candidate-list",
    "operations-risk-test-candidate-detail",
)
MAX_RESPONSE_BODY_BYTES = 1_048_576


def ascii_body_at_size(prefix, size):
    remaining = size - len(prefix.encode("utf-8"))
    if remaining < 2:
        raise AssertionError("fixture prefix is too large")
    return prefix + "#" + ("x" * (remaining - 2)) + "\n"


def route(route_code):
    """Return a complete public Route fixture; only routeCode varies."""

    return {
        "routeCode": route_code,
        "channel": "OPERATIONS",
        "httpMethod": "GET",
        "externalPathTemplate": "/fixture",
        "downstreamPathTemplate": "/fixture",
        "targetServiceCode": "operations-console-bff",
        "securityProfileCode": "OPERATIONS_JWT",
        "authorizationMode": "PERMISSION",
        "permissionCode": "OPERATIONS_SYSTEM_READ",
        "requiredScopeCode": None,
        "requestCookieMode": "NONE",
        "responseCookieMode": "NONE",
        "corsRequestHeaderNames": [],
        "forwardedRequestHeaderNames": [],
        "queryParameterNames": [],
        "bodyLimitBytes": 0,
        "bodyReceiveTimeoutMillis": 1000,
        "responseTimeoutMillis": 1000,
        "guardRules": [],
    }


def snapshot(route_codes, schema_version=1):
    return json.dumps(
        {
            "schemaVersion": schema_version,
            "targets": [
                {
                    "targetServiceCode": "operations-console-bff",
                    "channel": "OPERATIONS",
                    "discoveryServiceName": "operations-console-bff",
                }
            ],
            "routes": [route(code) for code in route_codes],
        },
        separators=(",", ":"),
    )


class PrometheusEvidenceTest(unittest.TestCase):
    def test_exact_exported_families_decode_to_one_observation(self):
        evidence = subject.decode_prometheus(VALID_PROMETHEUS)

        self.assertTrue(evidence.valid)
        self.assertEqual("SUCCESS", evidence.refresh_result)
        self.assertEqual("NONE", evidence.failure_reason)
        self.assertFalse(evidence.last_valid_in_use)
        self.assertEqual(27, evidence.operations_route_count)

    def test_expected_success_state_is_verified(self):
        evidence = subject.verify_prometheus(
            VALID_PROMETHEUS,
            expected_refresh_result="SUCCESS",
            expected_failure_reason="NONE",
            expected_last_valid=False,
            expected_operations_route_count=27,
        )

        self.assertEqual("SUCCESS", evidence.refresh_result)

    def test_invalid_refresh_last_valid_is_not_treated_as_snapshot_identity(self):
        evidence = subject.verify_prometheus(
            INVALID_FALLBACK_PROMETHEUS,
            expected_refresh_result="FAILURE",
            expected_failure_reason="INVALID_SNAPSHOT",
            expected_last_valid=True,
            expected_operations_route_count=32,
        )

        self.assertTrue(evidence.last_valid_in_use)
        self.assertFalse(hasattr(evidence, "route_codes"))

    def test_duplicate_selected_series_is_rejected(self):
        duplicate = VALID_PROMETHEUS + "gateway_snapshot_valid 1\n"

        with self.assertRaisesRegex(subject.EvidenceError, "exactly once"):
            subject.decode_prometheus(duplicate)

    def test_missing_one_hot_series_is_rejected(self):
        missing = VALID_PROMETHEUS.replace(
            'gateway_snapshot_last_refresh_result{result="SUPPRESSED"} 0.0\n', ""
        )

        with self.assertRaisesRegex(subject.EvidenceError, "one-hot"):
            subject.decode_prometheus(missing)

    def test_extra_one_hot_label_value_is_rejected(self):
        extra = (
            VALID_PROMETHEUS
            + 'gateway_snapshot_last_failure_reason{reason="OTHER"} 0\n'
        )

        with self.assertRaisesRegex(subject.EvidenceError, "one-hot"):
            subject.decode_prometheus(extra)

    def test_non_finite_selected_value_is_rejected(self):
        non_finite = VALID_PROMETHEUS.replace(
            'gateway_snapshot_routes{channel="OPERATIONS"} 27.0',
            'gateway_snapshot_routes{channel="OPERATIONS"} NaN',
        )

        with self.assertRaisesRegex(subject.EvidenceError, "finite"):
            subject.decode_prometheus(non_finite)

    def test_invalid_numeric_cause_cannot_retain_response_content(self):
        canary = "access_token=numeric-secret"
        invalid = VALID_PROMETHEUS.replace(
            "gateway_snapshot_valid 1.0",
            "gateway_snapshot_valid " + canary,
        )

        with self.assertRaises(subject.EvidenceError) as caught:
            subject.decode_prometheus(invalid)

        self.assertIsNone(caught.exception.__cause__)
        self.assertNotIn(canary, str(caught.exception))

    def test_unexpected_labels_on_selected_scalar_are_rejected(self):
        labelled = VALID_PROMETHEUS.replace(
            "gateway_snapshot_valid 1.0",
            'gateway_snapshot_valid{instance="unexpected"} 1.0',
        )

        with self.assertRaisesRegex(subject.EvidenceError, "labels"):
            subject.decode_prometheus(labelled)

    def test_wrong_expected_value_is_a_nonmatching_observation(self):
        with self.assertRaises(subject.EvidenceMismatch):
            subject.verify_prometheus(
                VALID_PROMETHEUS,
                expected_refresh_result="FAILURE",
                expected_failure_reason="NONE",
                expected_last_valid=False,
                expected_operations_route_count=27,
            )

    def test_exact_maximum_prometheus_body_is_accepted(self):
        body = ascii_body_at_size(VALID_PROMETHEUS, MAX_RESPONSE_BODY_BYTES)

        evidence = subject.decode_prometheus(body)

        self.assertEqual(MAX_RESPONSE_BODY_BYTES, len(body.encode("utf-8")))
        self.assertEqual(27, evidence.operations_route_count)

    def test_oversized_prometheus_body_is_rejected_without_echo(self):
        canary = "access_token=oversized-prometheus-secret"
        prefix = VALID_PROMETHEUS + "# " + canary + "\n"
        body = ascii_body_at_size(prefix, MAX_RESPONSE_BODY_BYTES + 1)

        with self.assertRaisesRegex(subject.EvidenceError, "maximum") as caught:
            subject.decode_prometheus(body)

        self.assertNotIn(canary, str(caught.exception))

    def test_unexpected_body_traversal_exception_is_normalized_and_redacted(self):
        canary = "password=body-traversal-secret"

        class ExplodingBody(str):
            def __len__(self):
                raise RuntimeError(canary)

        with self.assertRaises(subject.EvidenceError) as caught:
            subject.decode_prometheus(ExplodingBody(VALID_PROMETHEUS))

        self.assertNotIsInstance(caught.exception, RuntimeError)
        self.assertNotIn(canary, str(caught.exception))


class PublicSnapshotEvidenceTest(unittest.TestCase):
    def test_binary_wire_order_is_independent_of_activation_prefix_order(self):
        # Lexicographic order puts rule-set-detail before rule-set-list and
        # candidate-detail before candidate-list, unlike activation order.
        wire_codes = sorted(
            (
                "operations-auth-login",
                "client-auth-login",
                *FIRST_FIVE_ACTIVATION_CODES,
            ),
            key=lambda value: value.encode("utf-8"),
        )

        evidence = subject.verify_public_snapshot(
            snapshot(wire_codes), FIRST_FIVE_ACTIVATION_CODES
        )

        self.assertEqual(frozenset(FIRST_FIVE_ACTIVATION_CODES), evidence.active_risk_codes)
        self.assertEqual(tuple(wire_codes), evidence.route_codes)

    def test_duplicate_route_code_is_rejected(self):
        with self.assertRaisesRegex(subject.EvidenceError, "duplicate"):
            subject.decode_public_snapshot(
                snapshot(["operations-auth-login", "operations-auth-login"])
            )

    def test_unknown_expected_risk_code_is_rejected(self):
        with self.assertRaisesRegex(subject.EvidenceError, "unknown"):
            subject.verify_public_snapshot(
                snapshot(["operations-auth-login"]),
                ("operations-risk-not-approved",),
            )

    def test_missing_middle_of_activation_prefix_is_rejected(self):
        non_prefix = (
            "operations-risk-rule-authoring-context",
            "operations-risk-rule-set-detail",
        )

        with self.assertRaisesRegex(subject.EvidenceError, "prefix"):
            subject.verify_public_snapshot(snapshot(sorted(non_prefix)), non_prefix)

    def test_duplicate_expected_prefix_code_is_rejected(self):
        duplicated = (
            "operations-risk-rule-authoring-context",
            "operations-risk-rule-authoring-context",
        )

        with self.assertRaisesRegex(subject.EvidenceError, "duplicate"):
            subject.verify_public_snapshot(snapshot([]), duplicated)

    def test_activation_order_on_wire_is_rejected_when_not_binary_sorted(self):
        with self.assertRaisesRegex(subject.EvidenceError, "binary"):
            subject.decode_public_snapshot(snapshot(FIRST_FIVE_ACTIVATION_CODES))

    def test_unknown_risk_route_in_snapshot_is_rejected(self):
        codes = sorted(
            ["operations-auth-login", "operations-risk-not-approved"],
            key=lambda value: value.encode("utf-8"),
        )

        with self.assertRaisesRegex(subject.EvidenceError, "unknown Risk"):
            subject.decode_public_snapshot(snapshot(codes))

    def test_wrong_schema_version_is_rejected(self):
        with self.assertRaisesRegex(subject.EvidenceError, "schemaVersion"):
            subject.decode_public_snapshot(snapshot([], schema_version=2))

    def test_duplicate_json_key_is_rejected(self):
        body = '{"schemaVersion":1,"schemaVersion":1,"targets":[],"routes":[]}'

        with self.assertRaisesRegex(subject.EvidenceError, "duplicate JSON"):
            subject.decode_public_snapshot(body)

    def test_duplicate_json_key_cause_cannot_retain_response_content(self):
        canary = "password=duplicate-key-secret"
        body = '{"%s":1,"%s":2}' % (canary, canary)

        with self.assertRaises(subject.EvidenceError) as caught:
            subject.decode_public_snapshot(body)

        self.assertIsNone(caught.exception.__cause__)
        self.assertNotIn(canary, str(caught.exception))

    def test_exact_maximum_public_snapshot_body_is_accepted(self):
        prefix = snapshot([])
        body = prefix + (" " * (MAX_RESPONSE_BODY_BYTES - len(prefix)))

        evidence = subject.decode_public_snapshot(body)

        self.assertEqual(MAX_RESPONSE_BODY_BYTES, len(body.encode("utf-8")))
        self.assertEqual((), evidence.route_codes)

    def test_oversized_public_snapshot_body_is_rejected_without_echo(self):
        canary = "password=oversized-snapshot-secret"
        prefix = snapshot([]) + " " + canary
        body = prefix + (" " * (MAX_RESPONSE_BODY_BYTES + 1 - len(prefix)))

        with self.assertRaisesRegex(subject.EvidenceError, "maximum") as caught:
            subject.decode_public_snapshot(body)

        self.assertNotIn(canary, str(caught.exception))

    def test_deep_json_recursion_is_normalized_to_redacted_evidence_error(self):
        canary = "access_token=deep-json-secret"
        depth = 2_000
        body = (
            '{"schemaVersion":1,"targets":'
            + ("[" * depth)
            + json.dumps(canary)
            + ("]" * depth)
            + ',"routes":[]}'
        )

        with self.assertRaises(subject.EvidenceError) as caught:
            subject.decode_public_snapshot(body)

        self.assertNotIsInstance(caught.exception, RecursionError)
        self.assertNotIn(canary, str(caught.exception))


class CommandLineTest(unittest.TestCase):
    def test_prometheus_mode_reads_only_stdin_and_prints_redacted_success(self):
        stdout = io.StringIO()
        stderr = io.StringIO()

        exit_code = subject.main(
            [
                "--prometheus",
                "--expected-refresh-result",
                "SUCCESS",
                "--expected-failure-reason",
                "NONE",
                "--expected-last-valid",
                "false",
                "--expected-operations-route-count",
                "27",
            ],
            stdin=io.StringIO(VALID_PROMETHEUS),
            stdout=stdout,
            stderr=stderr,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("SNAPSHOT_METRICS_VERIFIED\n", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())
        self.assertNotIn(VALID_PROMETHEUS, stdout.getvalue())

    def test_stdin_read_is_bounded_before_parsing(self):
        class RecordingInput(io.StringIO):
            def __init__(self, value):
                super().__init__(value)
                self.read_sizes = []

            def read(self, size=-1):
                self.read_sizes.append(size)
                return super().read(size)

        stdin = RecordingInput(VALID_PROMETHEUS)

        exit_code = subject.main(
            [
                "--prometheus",
                "--expected-refresh-result",
                "SUCCESS",
                "--expected-failure-reason",
                "NONE",
                "--expected-last-valid",
                "false",
                "--expected-operations-route-count",
                "27",
            ],
            stdin=stdin,
            stdout=io.StringIO(),
            stderr=io.StringIO(),
        )

        self.assertEqual(0, exit_code)
        self.assertEqual([1_048_577], stdin.read_sizes)

    def test_modes_are_mutually_exclusive(self):
        with redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                subject.main(
                    ["--prometheus", "--public-snapshot"],
                    stdin=io.StringIO(""),
                    stdout=io.StringIO(),
                    stderr=io.StringIO(),
                )


if __name__ == "__main__":
    unittest.main()
