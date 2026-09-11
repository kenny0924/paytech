#!/usr/bin/env python3
"""Offline contract tests for deterministic Risk Route recovery."""

import contextlib
import io
import math
import unittest
from types import SimpleNamespace
from unittest import mock

from gateway_snapshot_wait import expectation_for_stage
import risk_rule_configuration_recovery as subject


ROUTE_PAIRS = (
    ("operations-risk-rule-authoring-context", "BASELINE", "AUTHORING_READ_ACTIVE"),
    ("operations-risk-rule-set-list", "AUTHORING_READ_ACTIVE", "RULE_SET_LIST_READ_ACTIVE"),
    ("operations-risk-rule-set-detail", "RULE_SET_LIST_READ_ACTIVE", "RULE_SET_DETAIL_READ_ACTIVE"),
    ("operations-risk-test-candidate-list", "RULE_SET_DETAIL_READ_ACTIVE", "CANDIDATE_LIST_READ_ACTIVE"),
    ("operations-risk-test-candidate-detail", "CANDIDATE_LIST_READ_ACTIVE", "READ_ACTIVE"),
    ("operations-risk-rule-set-save", "READ_ACTIVE", "SAVE_ACTIVE"),
    ("operations-risk-rule-set-freeze", "SAVE_ACTIVE", "FREEZE_ACTIVE"),
    ("operations-risk-test-candidate-create", "FREEZE_ACTIVE", "CREATE_ACTIVE"),
    ("operations-risk-test-candidate-validate", "CREATE_ACTIVE", "ALL_ROUTES_ACTIVE"),
)


def observation(stage, resources=None):
    if resources is None:
        resources = {"OPERATIONS_SYSTEM": "system"}
    return SimpleNamespace(
        stage=stage,
        persisted={"stage": stage, "resources": resources},
    )


def success_result(stage):
    return SimpleNamespace(
        observation="SUCCESS",
        verified_stage=stage,
        expected_prior_candidate=None,
        refresh_result="SUCCESS",
        failure_reason="NONE",
        last_valid_in_use=False,
        identity_verified=True,
        requires_behavior_probe=False,
        operations_route_count=expectation_for_stage(stage).operations_route_count,
    )


def fallback_result(prior_stage):
    return SimpleNamespace(
        observation="INVALID_FALLBACK_METRICS",
        verified_stage=None,
        expected_prior_candidate=prior_stage,
        refresh_result="FAILURE",
        failure_reason="INVALID_SNAPSHOT",
        last_valid_in_use=True,
        identity_verified=False,
        requires_behavior_probe=True,
        operations_route_count=(
            expectation_for_stage(prior_stage).operations_route_count
        ),
    )


def invalid_route_count_results(factory, stage):
    expected = expectation_for_stage(stage).operations_route_count
    missing = factory(stage)
    del missing.operations_route_count
    return (
        missing,
        SimpleNamespace(**{
            **vars(factory(stage)),
            "operations_route_count": True,
        }),
        SimpleNamespace(**{
            **vars(factory(stage)),
            "operations_route_count": float(expected),
        }),
        SimpleNamespace(**{
            **vars(factory(stage)),
            "operations_route_count": expected + 1,
        }),
    )


class FakeRollout:
    def __init__(self, initial_stage, journal, disable_result=None, disable_error=None):
        self.initial_stage = initial_stage
        self.journal = journal
        self.disable_result = disable_result
        self.disable_error = disable_error

    def inspect_stage(self):
        self.journal.append(("inspect",))
        return observation(self.initial_stage)

    def disable_route(self, route_code):
        self.journal.append(("disable", route_code))
        if self.disable_error is not None:
            raise self.disable_error
        return self.disable_result


class RecoveryStateMachineTest(unittest.TestCase):
    def coordinator(self, route_code, persisted_stage, transition_result=None,
                    disable_result=None, disable_error=None, inspect_override=None):
        journal = []
        prior_stage, next_stage = subject.stage_pair_for_route(route_code)
        rollout = FakeRollout(
            persisted_stage,
            journal,
            disable_result=disable_result or observation(prior_stage),
            disable_error=disable_error,
        )
        if inspect_override is not None:
            rollout.inspect_stage = lambda: inspect_override

        def observe(prior, next_value, timeout_seconds):
            journal.append(("observe", prior, next_value, timeout_seconds))
            return transition_result

        def wait_stage(*, expected_stage, expected_invalid_fallback_stage,
                       timeout_seconds):
            journal.append((
                "wait", expected_stage, expected_invalid_fallback_stage,
                timeout_seconds,
            ))
            return success_result(expected_stage)

        def probe(runtime_stage, expected_persisted_stage):
            journal.append(("probe", runtime_stage, expected_persisted_stage))

        return (
            subject.RecoveryCoordinator(
                rollout=rollout,
                snapshot_transition_observer=observe,
                snapshot_stage_waiter=wait_stage,
                behavior_probe=probe,
            ),
            journal,
            prior_stage,
            next_stage,
        )

    def test_all_nine_routes_use_the_shared_frozen_adjacent_pairs(self):
        self.assertEqual(tuple(code for code, _, _ in ROUTE_PAIRS), subject.ROUTE_ORDER)
        for route_code, prior_stage, next_stage in ROUTE_PAIRS:
            with self.subTest(route_code=route_code):
                self.assertEqual(
                    (prior_stage, next_stage),
                    subject.stage_pair_for_route(route_code),
                )

    def test_prior_stage_waits_and_probes_without_observe_or_patch(self):
        route_code, prior_stage, _ = ROUTE_PAIRS[5]
        coordinator, journal, _, _ = self.coordinator(route_code, prior_stage)

        result = coordinator.recover(route_code, timeout_seconds=17)

        self.assertEqual("ALREADY_AT_PRIOR_STAGE", result)
        self.assertEqual(
            [
                ("inspect",),
                ("wait", prior_stage, None, 17.0),
                ("probe", prior_stage, prior_stage),
            ],
            journal,
        )

    def test_next_success_disables_directly_then_verifies_prior(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[6]
        coordinator, journal, _, _ = self.coordinator(
            route_code,
            next_stage,
            transition_result=success_result(next_stage),
        )

        result = coordinator.recover(route_code, timeout_seconds=23)

        self.assertEqual("RECOVERED", result)
        self.assertEqual(
            [
                ("inspect",),
                ("observe", prior_stage, next_stage, 23.0),
                ("disable", route_code),
                ("wait", prior_stage, None, 23.0),
                ("probe", prior_stage, prior_stage),
            ],
            journal,
        )

    def test_invalid_fallback_probes_prior_with_persisted_next_before_disable(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[7]
        coordinator, journal, _, _ = self.coordinator(
            route_code,
            next_stage,
            transition_result=fallback_result(prior_stage),
        )

        result = coordinator.recover(route_code, timeout_seconds=31)

        self.assertEqual("RECOVERED", result)
        self.assertEqual(
            [
                ("inspect",),
                ("observe", prior_stage, next_stage, 31.0),
                ("probe", prior_stage, next_stage),
                ("disable", route_code),
                ("wait", prior_stage, None, 31.0),
                ("probe", prior_stage, prior_stage),
            ],
            journal,
        )

    def test_failed_fallback_probe_prevents_disable(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[4]
        journal = []
        rollout = FakeRollout(next_stage, journal, observation(prior_stage))

        def probe(runtime_stage, persisted_stage):
            journal.append(("probe", runtime_stage, persisted_stage))
            raise RuntimeError("secret behavior response")

        coordinator = subject.RecoveryCoordinator(
            rollout=rollout,
            snapshot_transition_observer=lambda prior, next_value, timeout_seconds:
                fallback_result(prior_stage),
            snapshot_stage_waiter=lambda **kwargs: success_result(prior_stage),
            behavior_probe=probe,
        )

        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover(route_code, timeout_seconds=60)
        self.assertEqual(
            [("inspect",), ("probe", prior_stage, next_stage)],
            journal,
        )

    def test_unknown_disable_stops_before_post_wait_and_probe(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[8]
        coordinator, journal, _, _ = self.coordinator(
            route_code,
            next_stage,
            transition_result=success_result(next_stage),
            disable_error=SystemExit(route_code + " UNKNOWN_REQUIRES_RECONCILIATION secret"),
        )

        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover(route_code, timeout_seconds=60)
        self.assertEqual(
            [
                ("inspect",),
                ("observe", prior_stage, next_stage, 60.0),
                ("disable", route_code),
            ],
            journal,
        )

    def test_disable_must_return_exact_prior_before_post_verification(self):
        route_code, _, next_stage = ROUTE_PAIRS[2]
        coordinator, journal, prior_stage, _ = self.coordinator(
            route_code,
            next_stage,
            transition_result=success_result(next_stage),
            disable_result=observation(next_stage),
        )

        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover(route_code, timeout_seconds=60)
        self.assertEqual(
            [
                ("inspect",),
                ("observe", prior_stage, next_stage, 60.0),
                ("disable", route_code),
            ],
            journal,
        )

    def test_drifted_or_visible_or_malformed_persisted_state_never_mutates(self):
        route_code, _, next_stage = ROUTE_PAIRS[3]
        cases = (
            observation("ALL_ROUTES_ACTIVE"),
            observation("RESOURCES_EXPOSED", {"OPERATIONS_RISK_MANAGEMENT": "risk"}),
            SimpleNamespace(stage=next_stage, persisted=None),
            SimpleNamespace(
                stage=next_stage,
                persisted={"stage": next_stage, "resources": {"OPERATIONS_RISK_MANAGEMENT": "risk"}},
            ),
            SimpleNamespace(
                stage=next_stage,
                persisted={"stage": "BASELINE", "resources": {}},
            ),
        )
        for invalid in cases:
            with self.subTest(invalid=invalid):
                coordinator, journal, _, _ = self.coordinator(
                    route_code,
                    next_stage,
                    transition_result=success_result(next_stage),
                    inspect_override=invalid,
                )
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(route_code, timeout_seconds=60)
                self.assertNotIn("disable", [item[0] for item in journal])

    def test_unknown_or_malformed_runtime_combinations_never_mutate(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[1]
        malformed = (
            None,
            SimpleNamespace(),
            success_result(prior_stage),
            fallback_result(next_stage),
            SimpleNamespace(**{
                **vars(fallback_result(prior_stage)),
                "last_valid_in_use": False,
            }),
            SimpleNamespace(**{
                **vars(success_result(next_stage)),
                "requires_behavior_probe": True,
            }),
        )
        for runtime in malformed:
            with self.subTest(runtime=runtime):
                coordinator, journal, _, _ = self.coordinator(
                    route_code, next_stage, transition_result=runtime
                )
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(route_code, timeout_seconds=60)
                self.assertNotIn("disable", [item[0] for item in journal])

    def test_success_runtime_route_count_breakers_prevent_disable(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[1]
        for runtime in invalid_route_count_results(success_result, next_stage):
            with self.subTest(runtime=runtime):
                coordinator, journal, _, _ = self.coordinator(
                    route_code, next_stage, transition_result=runtime
                )
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(route_code, timeout_seconds=60)
                self.assertNotIn(("disable", route_code), journal)
                self.assertNotIn(("wait", prior_stage, None, 60.0), journal)

    def test_fallback_runtime_route_count_breakers_prevent_probe_and_disable(self):
        route_code, prior_stage, next_stage = ROUTE_PAIRS[2]
        for runtime in invalid_route_count_results(fallback_result, prior_stage):
            with self.subTest(runtime=runtime):
                coordinator, journal, _, _ = self.coordinator(
                    route_code, next_stage, transition_result=runtime
                )
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(route_code, timeout_seconds=60)
                self.assertNotIn(("probe", prior_stage, next_stage), journal)
                self.assertNotIn(("disable", route_code), journal)

    def test_prior_wait_route_count_breakers_prevent_probe_and_mutation(self):
        route_code, prior_stage, _ = ROUTE_PAIRS[0]
        for wait_result in invalid_route_count_results(success_result, prior_stage):
            journal = []
            rollout = FakeRollout(prior_stage, journal)

            def wait_stage(**kwargs):
                journal.append(("wait",))
                return wait_result

            coordinator = subject.RecoveryCoordinator(
                rollout=rollout,
                snapshot_transition_observer=lambda *args, **kwargs: None,
                snapshot_stage_waiter=wait_stage,
                behavior_probe=lambda *args: journal.append(("probe",)),
            )
            with self.subTest(wait_result=wait_result):
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(route_code, timeout_seconds=60)
                self.assertEqual([("inspect",), ("wait",)], journal)

    def test_malformed_prior_wait_result_prevents_probe_and_mutation(self):
        route_code, prior_stage, _ = ROUTE_PAIRS[0]
        journal = []
        rollout = FakeRollout(prior_stage, journal)

        def wait_stage(**kwargs):
            journal.append(("wait",))
            return fallback_result(prior_stage)

        coordinator = subject.RecoveryCoordinator(
            rollout=rollout,
            snapshot_transition_observer=lambda *args, **kwargs: None,
            snapshot_stage_waiter=wait_stage,
            behavior_probe=lambda *args: journal.append(("probe",)),
        )
        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover(route_code, timeout_seconds=60)
        self.assertEqual([("inspect",), ("wait",)], journal)

    def test_nonadjacent_injected_stage_pair_fails_before_observation(self):
        route_code = ROUTE_PAIRS[0][0]
        journal = []
        coordinator = subject.RecoveryCoordinator(
            rollout=FakeRollout("BASELINE", journal),
            snapshot_transition_observer=lambda *args, **kwargs: None,
            snapshot_stage_waiter=lambda **kwargs: None,
            behavior_probe=lambda *args: None,
            stage_pair_resolver=lambda code: ("BASELINE", "READ_ACTIVE"),
        )
        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover(route_code, timeout_seconds=60)
        self.assertEqual([], journal)

    def test_invalid_route_and_timeout_fail_before_observation(self):
        invalid_timeouts = (True, 0, -1, 60.1, math.inf, math.nan, "60")
        for timeout in invalid_timeouts:
            journal = []
            coordinator = subject.RecoveryCoordinator(
                rollout=FakeRollout("BASELINE", journal),
                snapshot_transition_observer=lambda *args, **kwargs: None,
                snapshot_stage_waiter=lambda **kwargs: None,
                behavior_probe=lambda *args: None,
            )
            with self.subTest(timeout=timeout):
                with self.assertRaises(subject.RecoveryFailure):
                    coordinator.recover(ROUTE_PAIRS[0][0], timeout_seconds=timeout)
                self.assertEqual([], journal)
        coordinator = subject.RecoveryCoordinator(
            rollout=FakeRollout("BASELINE", []),
            snapshot_transition_observer=lambda *args, **kwargs: None,
            snapshot_stage_waiter=lambda **kwargs: None,
            behavior_probe=lambda *args: None,
        )
        with self.assertRaises(subject.RecoveryFailure):
            coordinator.recover("not-approved", timeout_seconds=60)


class RecoveryCliTest(unittest.TestCase):
    def test_no_action_is_noop_and_does_not_construct_live_adapters(self):
        factory = mock.Mock(side_effect=AssertionError("must not construct"))
        self.assertEqual(
            0,
            subject.main(
                ["--base-url", "http://not-loopback.example"],
                dependency_factory=factory,
                stdout=io.StringIO(),
                stderr=io.StringIO(),
            ),
        )
        factory.assert_not_called()

    def test_cli_accepts_only_one_exact_recovery_action_and_bounded_timeout(self):
        parser = subject.build_argument_parser()
        for route_code, _, _ in ROUTE_PAIRS:
            options = parser.parse_args(["--recover-route", route_code])
            self.assertEqual(route_code, options.recover_route)
        for argv in (
            ["--recover-route", "not-approved"],
            ["--recover-route", ROUTE_PAIRS[0][0], "--timeout-seconds", "61"],
            ["--recover-route", ROUTE_PAIRS[0][0], "--timeout-seconds", "nan"],
        ):
            with self.subTest(argv=argv), mock.patch("sys.stderr", io.StringIO()), \
                    self.assertRaises(SystemExit):
                parser.parse_args(argv)

    def test_non_loopback_base_fails_before_factory_or_mutation(self):
        factory = mock.Mock(side_effect=AssertionError("must not construct"))
        stdout = io.StringIO()
        stderr = io.StringIO()
        result = subject.main(
            ["--base-url", "https://example.test", "--recover-route", ROUTE_PAIRS[0][0]],
            dependency_factory=factory,
            stdout=stdout,
            stderr=stderr,
        )
        self.assertEqual(1, result)
        factory.assert_not_called()
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("RISK_RULE_CONFIGURATION_RECOVERY_FAILED\n", stderr.getvalue())

    def test_cli_success_is_fixed_and_closes_dependencies(self):
        runner = mock.Mock()
        runner.recover.return_value = "RECOVERED"
        stdout = io.StringIO()
        stderr = io.StringIO()
        result = subject.main(
            [
                "--base-url", "http://127.0.0.1:28080",
                "--recover-route", ROUTE_PAIRS[-1][0],
                "--timeout-seconds", "12.5",
            ],
            dependency_factory=lambda base_url: runner,
            stdout=stdout,
            stderr=stderr,
        )
        self.assertEqual(0, result)
        runner.recover.assert_called_once_with(ROUTE_PAIRS[-1][0], timeout_seconds=12.5)
        runner.close.assert_called_once_with()
        self.assertEqual("RECOVERED\n", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def test_cli_failure_redacts_exception_body_key_token_and_hash(self):
        secret = "password token idempotency-key {body} " + "a" * 64
        runner = mock.Mock()
        runner.recover.side_effect = SystemExit(secret)
        stdout = io.StringIO()
        stderr = io.StringIO()
        result = subject.main(
            ["--recover-route", ROUTE_PAIRS[0][0]],
            dependency_factory=lambda base_url: runner,
            stdout=stdout,
            stderr=stderr,
        )
        self.assertEqual(1, result)
        runner.close.assert_called_once_with()
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("RISK_RULE_CONFIGURATION_RECOVERY_FAILED\n", stderr.getvalue())
        self.assertNotIn(secret, stderr.getvalue())

    def test_keyboard_interrupt_closes_once_then_rethrows_redacted_type(self):
        secret = "interrupt-secret-must-not-appear"
        runner = mock.Mock()
        runner.recover.side_effect = KeyboardInterrupt(secret)
        stdout = io.StringIO()
        stderr = io.StringIO()
        with self.assertRaises(KeyboardInterrupt) as caught:
            subject.main(
                ["--recover-route", ROUTE_PAIRS[0][0]],
                dependency_factory=lambda base_url: runner,
                stdout=stdout,
                stderr=stderr,
            )
        runner.close.assert_called_once_with()
        self.assertEqual((), caught.exception.args)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def test_generator_exit_closes_once_then_rethrows_redacted_type(self):
        runner = mock.Mock()
        runner.recover.side_effect = GeneratorExit("generator-secret")
        stdout = io.StringIO()
        stderr = io.StringIO()
        with self.assertRaises(GeneratorExit) as caught:
            subject.main(
                ["--recover-route", ROUTE_PAIRS[0][0]],
                dependency_factory=lambda base_url: runner,
                stdout=stdout,
                stderr=stderr,
            )
        runner.close.assert_called_once_with()
        self.assertEqual((), caught.exception.args)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def test_primary_interrupt_wins_over_close_failure_after_exactly_one_close(self):
        runner = mock.Mock()
        runner.recover.side_effect = KeyboardInterrupt("primary-secret")
        runner.close.side_effect = RuntimeError("close-secret")
        stdout = io.StringIO()
        stderr = io.StringIO()
        with self.assertRaises(KeyboardInterrupt) as caught:
            subject.main(
                ["--recover-route", ROUTE_PAIRS[0][0]],
                dependency_factory=lambda base_url: runner,
                stdout=stdout,
                stderr=stderr,
            )
        runner.close.assert_called_once_with()
        self.assertEqual((), caught.exception.args)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def test_normal_primary_failure_wins_over_close_interrupt(self):
        runner = mock.Mock()
        runner.recover.side_effect = RuntimeError("primary-secret")
        runner.close.side_effect = KeyboardInterrupt("close-secret")
        stdout = io.StringIO()
        stderr = io.StringIO()
        result = subject.main(
            ["--recover-route", ROUTE_PAIRS[0][0]],
            dependency_factory=lambda base_url: runner,
            stdout=stdout,
            stderr=stderr,
        )
        runner.close.assert_called_once_with()
        self.assertEqual(1, result)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("RISK_RULE_CONFIGURATION_RECOVERY_FAILED\n", stderr.getvalue())

    def test_close_interrupt_after_success_is_rethrown_redacted(self):
        runner = mock.Mock()
        runner.recover.return_value = "RECOVERED"
        runner.close.side_effect = KeyboardInterrupt("close-secret")
        stdout = io.StringIO()
        stderr = io.StringIO()
        with self.assertRaises(KeyboardInterrupt) as caught:
            subject.main(
                ["--recover-route", ROUTE_PAIRS[0][0]],
                dependency_factory=lambda base_url: runner,
                stdout=stdout,
                stderr=stderr,
            )
        runner.close.assert_called_once_with()
        self.assertEqual((), caught.exception.args)
        self.assertEqual("", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())

    def test_argparse_error_is_redacted(self):
        secret = "token-" + "b" * 64
        injected = io.StringIO()
        process_stderr = io.StringIO()
        with contextlib.redirect_stderr(process_stderr):
            with self.assertRaises(SystemExit):
                subject.main(["--recover-route", secret], stderr=injected)
        self.assertEqual("", process_stderr.getvalue())
        self.assertIn("invalid arguments", injected.getvalue())
        self.assertNotIn(secret, injected.getvalue())


if __name__ == "__main__":
    unittest.main()
