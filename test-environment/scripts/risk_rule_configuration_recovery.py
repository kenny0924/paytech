#!/usr/bin/env python3
"""Deterministically recover one adjacent Risk Operations Route stage."""

import argparse
from collections.abc import Mapping
import contextlib
import math
import sys

from gateway_snapshot_wait import (
    expectation_for_stage,
    observe_recovery_transition,
    wait_for_stage,
)
from risk_rule_configuration_rollout import (
    ORIGIN,
    ROUTE_ORDER,
    RouteRollout,
    stage_pair_for_route,
)
from risk_rule_configuration_smoke import RiskRuleConfigurationSmoke
from risk_rule_configuration_stage import RISK_ACTIVE_RESOURCES
from smoke import Browser


MAX_TIMEOUT_SECONDS = 60.0
ALREADY_AT_PRIOR_STAGE = "ALREADY_AT_PRIOR_STAGE"
RECOVERED = "RECOVERED"


class RecoveryFailure(RuntimeError):
    """Recovery could not establish one of the closed, safe outcomes."""


def _validated_timeout(timeout_seconds):
    if (
        isinstance(timeout_seconds, bool)
        or not isinstance(timeout_seconds, (int, float))
        or not math.isfinite(float(timeout_seconds))
        or timeout_seconds <= 0
        or timeout_seconds > MAX_TIMEOUT_SECONDS
    ):
        raise RecoveryFailure("recovery timeout is invalid")
    return float(timeout_seconds)


def _validated_observation(value, expected_stage=None):
    stage = getattr(value, "stage", None)
    persisted = getattr(value, "persisted", None)
    if not isinstance(stage, str) or not isinstance(persisted, Mapping):
        raise RecoveryFailure("persisted rollout observation is malformed")
    if persisted.get("stage") != stage:
        raise RecoveryFailure("persisted rollout observation is inconsistent")
    resources = persisted.get("resources")
    if not isinstance(resources, Mapping):
        raise RecoveryFailure("persisted Resource authority is malformed")
    if frozenset(resources).intersection(RISK_ACTIVE_RESOURCES):
        raise RecoveryFailure("Risk Resources must remain disabled during recovery")
    if expected_stage is not None and stage != expected_stage:
        raise RecoveryFailure("persisted rollout did not reach the required stage")
    return stage


def _exact_success(result, expected_stage):
    return (
        getattr(result, "observation", None) == "SUCCESS"
        and getattr(result, "verified_stage", None) == expected_stage
        and getattr(result, "expected_prior_candidate", None) is None
        and getattr(result, "refresh_result", None) == "SUCCESS"
        and getattr(result, "failure_reason", None) == "NONE"
        and getattr(result, "last_valid_in_use", None) is False
        and getattr(result, "identity_verified", None) is True
        and getattr(result, "requires_behavior_probe", None) is False
        and _exact_operations_route_count(result, expected_stage)
    )


def _exact_prior_fallback(result, prior_stage):
    return (
        getattr(result, "observation", None) == "INVALID_FALLBACK_METRICS"
        and getattr(result, "verified_stage", None) is None
        and getattr(result, "expected_prior_candidate", None) == prior_stage
        and getattr(result, "refresh_result", None) == "FAILURE"
        and getattr(result, "failure_reason", None) == "INVALID_SNAPSHOT"
        and getattr(result, "last_valid_in_use", None) is True
        and getattr(result, "identity_verified", None) is False
        and getattr(result, "requires_behavior_probe", None) is True
        and _exact_operations_route_count(result, prior_stage)
    )


def _exact_operations_route_count(result, stage):
    try:
        expected = expectation_for_stage(stage).operations_route_count
        actual = getattr(result, "operations_route_count")
    except (Exception, SystemExit):
        return False
    return type(actual) is int and actual == expected


class RecoveryCoordinator:
    """Coordinate one fail-closed rollback through injected stable adapters."""

    def __init__(
        self,
        *,
        rollout,
        snapshot_transition_observer=observe_recovery_transition,
        snapshot_stage_waiter=wait_for_stage,
        behavior_probe,
        stage_pair_resolver=stage_pair_for_route,
    ):
        self.rollout = rollout
        self.snapshot_transition_observer = snapshot_transition_observer
        self.snapshot_stage_waiter = snapshot_stage_waiter
        self.behavior_probe = behavior_probe
        self.stage_pair_resolver = stage_pair_resolver

    def close(self):
        close = getattr(self.rollout, "close", None)
        if close is not None:
            close()

    def _stage_pair(self, route_code):
        if route_code not in ROUTE_ORDER:
            raise RecoveryFailure("Route is not in the approved recovery set")
        try:
            authoritative_pair = stage_pair_for_route(route_code)
            resolved_pair = self.stage_pair_resolver(route_code)
        except (Exception, SystemExit):
            raise RecoveryFailure("Route transition could not be resolved") from None
        if resolved_pair != authoritative_pair:
            raise RecoveryFailure("Route transition is not the exact adjacent edge")
        return authoritative_pair

    def _wait_for_success(self, stage, timeout_seconds):
        try:
            result = self.snapshot_stage_waiter(
                expected_stage=stage,
                expected_invalid_fallback_stage=None,
                timeout_seconds=timeout_seconds,
            )
        except (Exception, SystemExit):
            raise RecoveryFailure("Snapshot stage verification failed") from None
        if not _exact_success(result, stage):
            raise RecoveryFailure("Snapshot stage verification was not exact")

    def _probe(self, runtime_stage, persisted_stage):
        try:
            self.behavior_probe(runtime_stage, persisted_stage)
        except (Exception, SystemExit):
            raise RecoveryFailure("runtime behavior verification failed") from None

    def recover(self, route_code, timeout_seconds=60):
        timeout = _validated_timeout(timeout_seconds)
        prior_stage, next_stage = self._stage_pair(route_code)
        try:
            observed = self.rollout.inspect_stage()
        except (Exception, SystemExit):
            raise RecoveryFailure("persisted rollout observation failed") from None
        persisted_stage = _validated_observation(observed)

        if persisted_stage == prior_stage:
            self._wait_for_success(prior_stage, timeout)
            self._probe(prior_stage, prior_stage)
            return ALREADY_AT_PRIOR_STAGE
        if persisted_stage != next_stage:
            raise RecoveryFailure("persisted rollout is outside the adjacent edge")

        try:
            runtime = self.snapshot_transition_observer(
                prior_stage,
                next_stage,
                timeout_seconds=timeout,
            )
        except (Exception, SystemExit):
            raise RecoveryFailure("runtime transition observation failed") from None

        if _exact_success(runtime, next_stage):
            pass
        elif _exact_prior_fallback(runtime, prior_stage):
            self._probe(prior_stage, next_stage)
        else:
            raise RecoveryFailure("runtime transition observation was not a safe outcome")

        try:
            disabled = self.rollout.disable_route(route_code)
        except (Exception, SystemExit):
            raise RecoveryFailure("Route disable result is unknown or failed") from None
        _validated_observation(disabled, prior_stage)

        self._wait_for_success(prior_stage, timeout)
        self._probe(prior_stage, prior_stage)
        return RECOVERED


class _RedactedArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        self.print_usage(sys.stderr)
        self.exit(2, "{0}: error: invalid arguments\n".format(self.prog))


def _timeout_argument(value):
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        raise argparse.ArgumentTypeError("invalid timeout") from None
    try:
        return _validated_timeout(parsed)
    except RecoveryFailure:
        raise argparse.ArgumentTypeError("invalid timeout") from None


def build_argument_parser():
    parser = _RedactedArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=ORIGIN)
    parser.add_argument("--recover-route", choices=ROUTE_ORDER, metavar="ROUTE_CODE")
    parser.add_argument(
        "--timeout-seconds",
        type=_timeout_argument,
        default=MAX_TIMEOUT_SECONDS,
    )
    return parser


def _default_dependency_factory(base_url):
    smoke = RiskRuleConfigurationSmoke(base_url)
    administrator = smoke.authorization.administrator()
    return RecoveryCoordinator(
        rollout=RouteRollout(administrator),
        behavior_probe=smoke.probe_runtime_stage,
    )


def main(argv=None, *, dependency_factory=None, stdout=None, stderr=None):
    output = sys.stdout if stdout is None else stdout
    error = sys.stderr if stderr is None else stderr
    parser = build_argument_parser()
    with contextlib.redirect_stderr(error):
        options = parser.parse_args(argv)
    if options.recover_route is None:
        return 0

    runner = None
    primary_error = None
    result = None
    try:
        Browser(options.base_url)
        runner = (dependency_factory or _default_dependency_factory)(options.base_url)
    except BaseException as caught:
        primary_error = caught
    if runner is not None and primary_error is None:
        try:
            result = runner.recover(
                options.recover_route,
                timeout_seconds=options.timeout_seconds,
            )
            if result not in {ALREADY_AT_PRIOR_STAGE, RECOVERED}:
                raise RecoveryFailure("recovery returned an invalid result")
        except BaseException as caught:
            primary_error = caught
        finally:
            try:
                runner.close()
            except BaseException as caught:
                if primary_error is None:
                    primary_error = caught
    if primary_error is not None:
        if not isinstance(primary_error, (Exception, SystemExit)):
            try:
                redacted_interrupt = type(primary_error)()
            except BaseException:
                redacted_interrupt = BaseException()
            raise redacted_interrupt from None
        error.write("RISK_RULE_CONFIGURATION_RECOVERY_FAILED\n")
        return 1
    output.write(result + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
