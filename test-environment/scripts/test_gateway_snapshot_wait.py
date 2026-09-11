#!/usr/bin/env python3
"""Offline contract tests for the bounded Gateway Snapshot waiter."""

import io
import json
import subprocess
import unittest
from unittest import mock

import gateway_snapshot_wait as subject


SUCCESS_BASELINE_METRICS = """\
gateway_snapshot_valid 1
gateway_snapshot_last_refresh_result{result="NEVER_ATTEMPTED"} 0
gateway_snapshot_last_refresh_result{result="SUCCESS"} 1
gateway_snapshot_last_refresh_result{result="FAILURE"} 0
gateway_snapshot_last_refresh_result{result="SUPPRESSED"} 0
gateway_snapshot_last_failure_reason{reason="NONE"} 1
gateway_snapshot_last_failure_reason{reason="EMPTY_RESPONSE"} 0
gateway_snapshot_last_failure_reason{reason="INVALID_SNAPSHOT"} 0
gateway_snapshot_last_failure_reason{reason="CONTROL_PLANE_UNAVAILABLE"} 0
gateway_snapshot_last_valid_in_use 0
gateway_snapshot_routes{channel="OPERATIONS"} 27
"""

SUCCESS_AUTHORING_METRICS = SUCCESS_BASELINE_METRICS.replace(
    'gateway_snapshot_routes{channel="OPERATIONS"} 27',
    'gateway_snapshot_routes{channel="OPERATIONS"} 28',
)

INVALID_BASELINE_FALLBACK_METRICS = SUCCESS_BASELINE_METRICS.replace(
    'gateway_snapshot_last_refresh_result{result="SUCCESS"} 1',
    'gateway_snapshot_last_refresh_result{result="SUCCESS"} 0',
).replace(
    'gateway_snapshot_last_refresh_result{result="FAILURE"} 0',
    'gateway_snapshot_last_refresh_result{result="FAILURE"} 1',
).replace(
    'gateway_snapshot_last_failure_reason{reason="NONE"} 1',
    'gateway_snapshot_last_failure_reason{reason="NONE"} 0',
).replace(
    'gateway_snapshot_last_failure_reason{reason="INVALID_SNAPSHOT"} 0',
    'gateway_snapshot_last_failure_reason{reason="INVALID_SNAPSHOT"} 1',
).replace(
    "gateway_snapshot_last_valid_in_use 0",
    "gateway_snapshot_last_valid_in_use 1",
)


def route(route_code):
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


def snapshot(route_codes):
    ordered = sorted(route_codes, key=lambda value: value.encode("utf-8"))
    return json.dumps(
        {
            "schemaVersion": 1,
            "targets": [],
            "routes": [route(code) for code in ordered],
        },
        separators=(",", ":"),
    )


BASELINE_SNAPSHOT = snapshot(["operations-auth-login"])
AUTHORING_SNAPSHOT = snapshot(
    ["operations-auth-login", "operations-risk-rule-authoring-context"]
)


class FakeClock:
    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds

    def advance(self, seconds):
        self.now += seconds


class ScriptedFetcher:
    def __init__(self, clock, responses, elapsed=0.0):
        self.clock = clock
        self.responses = {url: list(values) for url, values in responses.items()}
        self.elapsed = elapsed
        self.calls = []

    def __call__(self, url, timeout_seconds):
        self.calls.append((url, timeout_seconds))
        self.clock.advance(min(self.elapsed, timeout_seconds))
        values = self.responses[url]
        if len(values) > 1:
            return values.pop(0)
        return values[0]


class StageMappingTest(unittest.TestCase):
    def test_stage_mapping_uses_activation_prefix_not_binary_route_order(self):
        expectation = subject.expectation_for_stage("READ_ACTIVE")

        self.assertEqual(32, expectation.operations_route_count)
        self.assertEqual(
            frozenset(
                {
                    "operations-risk-rule-authoring-context",
                    "operations-risk-rule-set-list",
                    "operations-risk-rule-set-detail",
                    "operations-risk-test-candidate-list",
                    "operations-risk-test-candidate-detail",
                }
            ),
            expectation.active_risk_route_codes,
        )

    def test_resources_exposed_keeps_all_route_snapshot_coordinates(self):
        expectation = subject.expectation_for_stage("RESOURCES_EXPOSED")

        self.assertEqual(36, expectation.operations_route_count)
        self.assertEqual(9, len(expectation.active_risk_route_codes))

    def test_non_runtime_stage_is_rejected(self):
        with self.assertRaisesRegex(subject.SnapshotWaitError, "stage"):
            subject.expectation_for_stage("LEGACY")


class SnapshotWaitTest(unittest.TestCase):
    def test_success_mode_requires_matching_metrics_and_public_snapshot(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
        )

        result = subject.wait_for_stage(
            expected_stage="AUTHORING_READ_ACTIVE",
            expected_invalid_fallback_stage=None,
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("AUTHORING_READ_ACTIVE", result.verified_stage)
        self.assertIsNone(result.expected_prior_candidate)
        self.assertTrue(result.identity_verified)
        self.assertFalse(result.requires_behavior_probe)
        self.assertEqual("SUCCESS", result.observation)
        self.assertEqual(
            [subject.GATEWAY_METRICS_URL, subject.PLATFORM_SNAPSHOT_URL],
            [url for url, _ in fetcher.calls],
        )

    def test_stale_evidence_converges_across_five_second_refresh_cadence(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [
                    SUCCESS_BASELINE_METRICS,
                    SUCCESS_BASELINE_METRICS,
                    SUCCESS_BASELINE_METRICS,
                    SUCCESS_AUTHORING_METRICS,
                ],
                subject.PLATFORM_SNAPSHOT_URL: [
                    BASELINE_SNAPSHOT,
                    BASELINE_SNAPSHOT,
                    BASELINE_SNAPSHOT,
                    AUTHORING_SNAPSHOT,
                ],
            },
        )

        result = subject.wait_for_stage(
            expected_stage="AUTHORING_READ_ACTIVE",
            expected_invalid_fallback_stage=None,
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("SUCCESS", result.observation)
        self.assertGreaterEqual(clock.now, 6.0)
        self.assertTrue(clock.sleeps)
        self.assertTrue(all(0 < value <= 2.0 for value in clock.sleeps))

    def test_invalid_fallback_mode_uses_metrics_without_claiming_identity(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )

        result = subject.wait_for_stage(
            expected_stage=None,
            expected_invalid_fallback_stage="BASELINE",
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertIsNone(result.verified_stage)
        self.assertEqual("BASELINE", result.expected_prior_candidate)
        self.assertEqual("INVALID_FALLBACK_METRICS", result.observation)
        self.assertEqual("FAILURE", result.refresh_result)
        self.assertEqual("INVALID_SNAPSHOT", result.failure_reason)
        self.assertTrue(result.last_valid_in_use)
        self.assertEqual(27, result.operations_route_count)
        self.assertFalse(result.identity_verified)
        self.assertTrue(result.requires_behavior_probe)
        self.assertEqual(
            [subject.GATEWAY_METRICS_URL], [url for url, _ in fetcher.calls]
        )
        self.assertFalse(hasattr(result, "stage"))

    def test_invalid_fallback_cli_prints_coordinates_not_a_verified_stage(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )
        stdout = io.StringIO()
        stderr = io.StringIO()

        exit_code = subject.main(
            [
                "--expected-invalid-fallback-stage",
                "BASELINE",
                "--timeout-seconds",
                "60",
            ],
            stdout=stdout,
            stderr=stderr,
            fetcher=fetcher,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("", stderr.getvalue())
        self.assertEqual(
            "SNAPSHOT_FALLBACK_METRICS_VERIFIED "
            "expectedPriorCandidate=BASELINE refreshResult=FAILURE "
            "failureReason=INVALID_SNAPSHOT lastValidInUse=true "
            "operationsRouteCount=27 "
            "identity=UNVERIFIED_BEHAVIOR_PROBE_REQUIRED\n",
            stdout.getvalue(),
        )
        self.assertNotIn("SNAPSHOT_STAGE_VERIFIED", stdout.getvalue())
        self.assertNotIn("stage=BASELINE", stdout.getvalue())

    def test_malformed_response_fails_without_echoing_the_body(self):
        secret_body = "malformed access_token=top-secret"
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock, {subject.GATEWAY_METRICS_URL: [secret_body]}
        )

        with self.assertRaises(subject.SnapshotWaitError) as caught:
            subject.wait_for_stage(
                expected_stage=None,
                expected_invalid_fallback_stage="BASELINE",
                timeout_seconds=60,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
            )

        self.assertNotIn(secret_body, str(caught.exception))
        self.assertNotIn("top-secret", str(caught.exception))

    def test_oversized_response_is_rejected_before_an_injected_decoder_runs(self):
        canary = "access_token=oversized-waiter-secret"
        oversized = canary + ("x" * (1_048_577 - len(canary)))
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock, {subject.GATEWAY_METRICS_URL: [oversized]}
        )
        decoder_calls = []

        def decoder(_body):
            decoder_calls.append("called")
            raise AssertionError("decoder must not receive an oversized body")

        with self.assertRaises(subject.SnapshotWaitError) as caught:
            subject.wait_for_stage(
                expected_stage=None,
                expected_invalid_fallback_stage="BASELINE",
                timeout_seconds=60,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                metrics_decoder=decoder,
            )

        self.assertEqual([], decoder_calls)
        self.assertNotIn(canary, str(caught.exception))

    def test_fetcher_exception_is_redacted_at_the_wait_boundary(self):
        def unsafe_fetcher(_url, _timeout_seconds):
            raise subject.SnapshotWaitError("access_token=fetcher-secret")

        with self.assertRaises(subject.SnapshotWaitError) as caught:
            subject.wait_for_stage(
                expected_stage="BASELINE",
                expected_invalid_fallback_stage=None,
                timeout_seconds=60,
                fetcher=unsafe_fetcher,
            )

        self.assertNotIn("fetcher-secret", str(caught.exception))
        self.assertNotIn("access_token", str(caught.exception))

    def test_each_fetch_receives_only_the_remaining_monotonic_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
            elapsed=7.5,
        )

        subject.wait_for_stage(
            expected_stage="AUTHORING_READ_ACTIVE",
            expected_invalid_fallback_stage=None,
            timeout_seconds=20,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual(20.0, fetcher.calls[0][1])
        self.assertEqual(12.5, fetcher.calls[1][1])
        self.assertTrue(all(timeout <= 20 for _, timeout in fetcher.calls))

    def test_slow_metrics_decoder_cannot_return_after_the_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )

        def slow_metrics_decoder(body):
            evidence = subject.decode_prometheus(body)
            clock.advance(10)
            return evidence

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.wait_for_stage(
                expected_stage=None,
                expected_invalid_fallback_stage="BASELINE",
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                metrics_decoder=slow_metrics_decoder,
            )

        self.assertEqual(10.0, clock.now)

    def test_slow_public_snapshot_decoder_cannot_return_after_the_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
        )

        def slow_snapshot_decoder(body):
            evidence = subject.decode_public_snapshot(body)
            clock.advance(10)
            return evidence

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.wait_for_stage(
                expected_stage="AUTHORING_READ_ACTIVE",
                expected_invalid_fallback_stage=None,
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                public_snapshot_decoder=slow_snapshot_decoder,
            )

        self.assertEqual(10.0, clock.now)

    def test_fallback_match_cannot_cross_deadline_before_return(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )

        class MetricsThatCrossDeadlineOnFinalCoordinate:
            valid = True
            refresh_result = "FAILURE"
            failure_reason = "INVALID_SNAPSHOT"
            last_valid_in_use = True

            @property
            def operations_route_count(self):
                clock.advance(10)
                return 27

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.wait_for_stage(
                expected_stage=None,
                expected_invalid_fallback_stage="BASELINE",
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                metrics_decoder=lambda _body: MetricsThatCrossDeadlineOnFinalCoordinate(),
            )

    def test_success_match_cannot_cross_deadline_before_return(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
        )

        class SnapshotThatCrossesDeadlineDuringIdentityComparison:
            @property
            def active_risk_codes(self):
                clock.advance(10)
                return frozenset({"operations-risk-rule-authoring-context"})

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.wait_for_stage(
                expected_stage="AUTHORING_READ_ACTIVE",
                expected_invalid_fallback_stage=None,
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                public_snapshot_decoder=lambda _body: (
                    SnapshotThatCrossesDeadlineDuringIdentityComparison()
                ),
            )

    def test_hard_deadline_stops_after_sixty_seconds(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [SUCCESS_BASELINE_METRICS]},
        )

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.wait_for_stage(
                expected_stage=None,
                expected_invalid_fallback_stage="AUTHORING_READ_ACTIVE",
                timeout_seconds=60,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
            )

        self.assertEqual(60.0, clock.now)
        self.assertTrue(all(timeout <= 60.0 for _, timeout in fetcher.calls))

    def test_timeout_larger_than_hard_bound_is_rejected(self):
        with self.assertRaisesRegex(subject.SnapshotWaitError, "60"):
            subject.wait_for_stage(
                expected_stage="BASELINE",
                expected_invalid_fallback_stage=None,
                timeout_seconds=61,
                fetcher=lambda _url, _timeout: "",
            )

    def test_modes_are_exactly_mutually_exclusive(self):
        for expected, fallback in ((None, None), ("BASELINE", "BASELINE")):
            with self.subTest(expected=expected, fallback=fallback):
                with self.assertRaisesRegex(subject.SnapshotWaitError, "exactly one"):
                    subject.wait_for_stage(
                        expected_stage=expected,
                        expected_invalid_fallback_stage=fallback,
                        timeout_seconds=60,
                        fetcher=lambda _url, _timeout: "",
                    )


class RecoveryTransitionObservationTest(unittest.TestCase):
    def test_next_success_wins_with_exact_public_snapshot_identity(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
        )

        result = subject.observe_recovery_transition(
            "BASELINE",
            "AUTHORING_READ_ACTIVE",
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("SUCCESS", result.observation)
        self.assertEqual("AUTHORING_READ_ACTIVE", result.verified_stage)
        self.assertIsNone(result.expected_prior_candidate)
        self.assertTrue(result.identity_verified)
        self.assertFalse(result.requires_behavior_probe)
        self.assertEqual(
            [subject.GATEWAY_METRICS_URL, subject.PLATFORM_SNAPSHOT_URL],
            [url for url, _timeout in fetcher.calls],
        )

    def test_prior_invalid_fallback_wins_without_fetching_public_snapshot(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )

        result = subject.observe_recovery_transition(
            "BASELINE",
            "AUTHORING_READ_ACTIVE",
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("INVALID_FALLBACK_METRICS", result.observation)
        self.assertIsNone(result.verified_stage)
        self.assertEqual("BASELINE", result.expected_prior_candidate)
        self.assertEqual("FAILURE", result.refresh_result)
        self.assertEqual("INVALID_SNAPSHOT", result.failure_reason)
        self.assertTrue(result.last_valid_in_use)
        self.assertEqual(27, result.operations_route_count)
        self.assertFalse(result.identity_verified)
        self.assertTrue(result.requires_behavior_probe)
        self.assertEqual(
            [subject.GATEWAY_METRICS_URL],
            [url for url, _timeout in fetcher.calls],
        )

    def test_nonmatching_metrics_and_stale_success_snapshot_converge(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [
                    SUCCESS_BASELINE_METRICS,
                    SUCCESS_AUTHORING_METRICS,
                    SUCCESS_AUTHORING_METRICS,
                ],
                subject.PLATFORM_SNAPSHOT_URL: [
                    BASELINE_SNAPSHOT,
                    AUTHORING_SNAPSHOT,
                ],
            },
        )

        result = subject.observe_recovery_transition(
            "BASELINE",
            "AUTHORING_READ_ACTIVE",
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("AUTHORING_READ_ACTIVE", result.verified_stage)
        self.assertEqual([2.0, 2.0], clock.sleeps)
        self.assertEqual(
            [
                subject.GATEWAY_METRICS_URL,
                subject.GATEWAY_METRICS_URL,
                subject.PLATFORM_SNAPSHOT_URL,
                subject.GATEWAY_METRICS_URL,
                subject.PLATFORM_SNAPSHOT_URL,
            ],
            [url for url, _timeout in fetcher.calls],
        )

    def test_late_fallback_competes_with_success_under_one_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: (
                    ([SUCCESS_BASELINE_METRICS] * 29)
                    + [INVALID_BASELINE_FALLBACK_METRICS]
                )
            },
        )

        result = subject.observe_recovery_transition(
            "BASELINE",
            "AUTHORING_READ_ACTIVE",
            timeout_seconds=60,
            fetcher=fetcher,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
        )

        self.assertEqual("INVALID_FALLBACK_METRICS", result.observation)
        self.assertEqual(58.0, clock.now)
        self.assertEqual(30, len(fetcher.calls))
        self.assertTrue(all(timeout <= 60 for _url, timeout in fetcher.calls))

    def test_only_forward_adjacent_route_prefix_stages_are_accepted(self):
        invalid_pairs = (
            ("BASELINE", "BASELINE"),
            ("BASELINE", "RULE_SET_LIST_READ_ACTIVE"),
            ("AUTHORING_READ_ACTIVE", "BASELINE"),
            ("ALL_ROUTES_ACTIVE", "RESOURCES_EXPOSED"),
            ("LEGACY", "BASELINE"),
            ("UNKNOWN", "AUTHORING_READ_ACTIVE"),
        )
        calls = []

        for prior_stage, next_stage in invalid_pairs:
            with self.subTest(prior_stage=prior_stage, next_stage=next_stage):
                with self.assertRaisesRegex(subject.SnapshotWaitError, "adjacent"):
                    subject.observe_recovery_transition(
                        prior_stage,
                        next_stage,
                        fetcher=lambda url, timeout: calls.append((url, timeout)),
                    )

        self.assertEqual([], calls)

    def test_slow_metrics_decoder_cannot_escape_transition_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {subject.GATEWAY_METRICS_URL: [INVALID_BASELINE_FALLBACK_METRICS]},
        )

        def slow_decoder(body):
            evidence = subject.decode_prometheus(body)
            clock.advance(10)
            return evidence

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.observe_recovery_transition(
                "BASELINE",
                "AUTHORING_READ_ACTIVE",
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                metrics_decoder=slow_decoder,
            )

    def test_slow_public_decoder_cannot_escape_transition_deadline(self):
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [AUTHORING_SNAPSHOT],
            },
        )

        def slow_decoder(body):
            evidence = subject.decode_public_snapshot(body)
            clock.advance(10)
            return evidence

        with self.assertRaisesRegex(subject.SnapshotWaitError, "deadline"):
            subject.observe_recovery_transition(
                "BASELINE",
                "AUTHORING_READ_ACTIVE",
                timeout_seconds=10,
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                public_snapshot_decoder=slow_decoder,
            )

    def test_oversized_transition_metrics_fail_before_decoder_and_without_echo(self):
        canary = "access_token=transition-oversized-secret"
        oversized = canary + ("x" * (1_048_577 - len(canary)))
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock, {subject.GATEWAY_METRICS_URL: [oversized]}
        )
        decoder_calls = []

        with self.assertRaises(subject.SnapshotWaitError) as caught:
            subject.observe_recovery_transition(
                "BASELINE",
                "AUTHORING_READ_ACTIVE",
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
                metrics_decoder=lambda body: decoder_calls.append(body),
            )

        self.assertEqual([], decoder_calls)
        self.assertNotIn(canary, str(caught.exception))

    def test_deep_success_snapshot_is_a_redacted_controlled_failure(self):
        canary = "password=transition-deep-secret"
        depth = 2_000
        deep_snapshot = (
            '{"schemaVersion":1,"targets":'
            + ("[" * depth)
            + json.dumps(canary)
            + ("]" * depth)
            + ',"routes":[]}'
        )
        clock = FakeClock()
        fetcher = ScriptedFetcher(
            clock,
            {
                subject.GATEWAY_METRICS_URL: [SUCCESS_AUTHORING_METRICS],
                subject.PLATFORM_SNAPSHOT_URL: [deep_snapshot],
            },
        )

        with self.assertRaises(subject.SnapshotWaitError) as caught:
            subject.observe_recovery_transition(
                "BASELINE",
                "AUTHORING_READ_ACTIVE",
                fetcher=fetcher,
                monotonic=clock.monotonic,
                sleep=clock.sleep,
            )

        self.assertNotIsInstance(caught.exception, RecursionError)
        self.assertNotIn(canary, str(caught.exception))


class ComposeFetcherTest(unittest.TestCase):
    def test_fixed_argument_jre_fetcher_and_redacted_subprocess_failure(self):
        completed = subprocess.CompletedProcess(
            args=[],
            returncode=22,
            stdout=b"access_token=stdout-secret",
            stderr=None,
        )
        with mock.patch.object(subject.subprocess, "run", return_value=completed) as run:
            with self.assertRaises(subject.SnapshotWaitError) as caught:
                subject.ComposeFetcher()(subject.GATEWAY_METRICS_URL, 4.25)

        args, kwargs = run.call_args
        self.assertEqual(
            [
                str(subject.COMPOSE_SCRIPT),
                "exec",
                "-T",
                "api-gateway",
                "java",
                "-cp",
                "/app/health-probe",
                "LocalSnapshotEvidenceFetch",
                subject.GATEWAY_METRICS_URL,
            ],
            args[0],
        )
        self.assertNotIn("shell", kwargs)
        self.assertEqual(subprocess.PIPE, kwargs["stdout"])
        self.assertEqual(subprocess.DEVNULL, kwargs["stderr"])
        self.assertNotIn("text", kwargs)
        self.assertEqual(4.25, kwargs["timeout"])
        self.assertFalse(kwargs["check"])
        self.assertNotIn("stdout-secret", str(caught.exception))
        self.assertNotIn("stderr-secret", str(caught.exception))

    def test_success_is_strict_utf8_and_bounded_before_return(self):
        valid = subprocess.CompletedProcess(
            args=[], returncode=0, stdout=b"gateway_snapshot_valid 1\n", stderr=None
        )
        with mock.patch.object(subject.subprocess, "run", return_value=valid):
            self.assertEqual(
                "gateway_snapshot_valid 1\n",
                subject.ComposeFetcher()(subject.GATEWAY_METRICS_URL, 4.25),
            )

        malformed = (
            b"\xff",
            b"x" * (subject.MAX_RESPONSE_BODY_BYTES + 1),
        )
        for payload in malformed:
            with self.subTest(size=len(payload)), mock.patch.object(
                subject.subprocess,
                "run",
                return_value=subprocess.CompletedProcess(
                    args=[], returncode=0, stdout=payload, stderr=None
                ),
            ), self.assertRaises(subject.SnapshotWaitError) as caught:
                subject.ComposeFetcher()(subject.PLATFORM_SNAPSHOT_URL, 4.25)
            self.assertNotIn("x" * 64, str(caught.exception))

    def test_subprocess_timeout_is_redacted(self):
        timeout = subprocess.TimeoutExpired(
            cmd=["java", "token=secret"], timeout=1, output="secret-output"
        )
        with mock.patch.object(subject.subprocess, "run", side_effect=timeout):
            with self.assertRaises(subject.SnapshotWaitError) as caught:
                subject.ComposeFetcher()(subject.PLATFORM_SNAPSHOT_URL, 1.0)

        self.assertNotIn("secret", str(caught.exception))

    def test_invalid_url_or_timeout_never_starts_a_subprocess(self):
        invalid_urls = (
            "http://127.0.0.1:19080/actuator/health",
            "http://api-gateway:19080/actuator/prometheus",
            "http://127.0.0.1:19080/actuator/prometheus?x=1",
            "HTTP://127.0.0.1:19080/actuator/prometheus",
            "http://platform-support-service:18106/"
            "platform-support/gateway-config/v1/public-snapshot#x",
        )
        for url in invalid_urls:
            with self.subTest(url=url), mock.patch.object(
                subject.subprocess, "run"
            ) as run, self.assertRaises(subject.SnapshotWaitError):
                subject.ComposeFetcher()(url, 1.0)
            run.assert_not_called()

        invalid_timeouts = (True, 0, -1, float("inf"), float("nan"), "1")
        for timeout in invalid_timeouts:
            with self.subTest(timeout=timeout), mock.patch.object(
                subject.subprocess, "run"
            ) as run, self.assertRaises(subject.SnapshotWaitError):
                subject.ComposeFetcher()(subject.GATEWAY_METRICS_URL, timeout)
            run.assert_not_called()

if __name__ == "__main__":
    unittest.main()
