#!/usr/bin/env python3
"""Wait for one exact Gateway Snapshot runtime observation.

This command observes only the already-running local stack.  It neither starts
Compose nor mutates Platform/Gateway state.  Invalid-refresh fallback proves
only the metrics coordinates; callers must run the separate behavior probe to
establish the Last Valid Snapshot identity.
"""

import argparse
import math
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from gateway_snapshot_metrics import (
    MAX_RESPONSE_BODY_BYTES,
    decode_prometheus,
    decode_public_snapshot,
)
from risk_rule_configuration_stage import (
    LEGACY_ROUTE_CODES,
    RISK_ROUTE_ACTIVATION_ORDER,
    ROUTE_PREFIX_STAGES,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_SCRIPT = REPOSITORY_ROOT / "test-environment" / "compose.sh"
GATEWAY_METRICS_URL = "http://127.0.0.1:19080/actuator/prometheus"
PLATFORM_SNAPSHOT_URL = (
    "http://platform-support-service:18106/"
    "platform-support/gateway-config/v1/public-snapshot"
)
MAX_TIMEOUT_SECONDS = 60.0
MAX_POLL_INTERVAL_SECONDS = 2.0


class SnapshotWaitError(RuntimeError):
    """Snapshot evidence could not be verified safely."""


@dataclass(frozen=True)
class StageExpectation:
    stage: str
    active_risk_route_codes: frozenset
    operations_route_count: int


@dataclass(frozen=True)
class WaitResult:
    observation: str
    verified_stage: Optional[str]
    expected_prior_candidate: Optional[str]
    refresh_result: str
    failure_reason: str
    last_valid_in_use: bool
    operations_route_count: int
    identity_verified: bool
    requires_behavior_probe: bool


def expectation_for_stage(stage):
    """Map a closed rollout stage to its exact runtime Route coordinates."""

    if stage == "RESOURCES_EXPOSED":
        prefix_length = len(RISK_ROUTE_ACTIVATION_ORDER)
    elif stage in ROUTE_PREFIX_STAGES:
        prefix_length = ROUTE_PREFIX_STAGES.index(stage)
    else:
        raise SnapshotWaitError("unknown or non-runtime rollout stage")
    active_codes = frozenset(RISK_ROUTE_ACTIVATION_ORDER[:prefix_length])
    return StageExpectation(
        stage=stage,
        active_risk_route_codes=active_codes,
        operations_route_count=len(LEGACY_ROUTE_CODES) + prefix_length,
    )


class ComposeFetcher:
    """Fetch one fixed internal endpoint through the existing Gateway container."""

    def __call__(self, url, timeout_seconds):
        if url not in {GATEWAY_METRICS_URL, PLATFORM_SNAPSHOT_URL}:
            raise SnapshotWaitError("snapshot evidence endpoint is not approved")
        if (
            isinstance(timeout_seconds, bool)
            or not isinstance(timeout_seconds, (int, float))
            or not math.isfinite(float(timeout_seconds))
            or timeout_seconds <= 0
        ):
            raise SnapshotWaitError("snapshot evidence timeout is invalid")
        command = [
            str(COMPOSE_SCRIPT),
            "exec",
            "-T",
            "api-gateway",
            "java",
            "-cp",
            "/app/health-probe",
            "LocalSnapshotEvidenceFetch",
            url,
        ]
        try:
            completed = subprocess.run(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=float(timeout_seconds),
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            raise SnapshotWaitError("snapshot evidence fetch failed") from None
        if completed.returncode != 0:
            raise SnapshotWaitError("snapshot evidence fetch failed")
        if (
            not isinstance(completed.stdout, bytes)
            or len(completed.stdout) > MAX_RESPONSE_BODY_BYTES
        ):
            raise SnapshotWaitError("snapshot evidence response is malformed")
        try:
            return completed.stdout.decode("utf-8", errors="strict")
        except UnicodeError:
            raise SnapshotWaitError("snapshot evidence response is malformed") from None


def _validated_timeout(timeout_seconds):
    if (
        isinstance(timeout_seconds, bool)
        or not isinstance(timeout_seconds, (int, float))
        or not math.isfinite(float(timeout_seconds))
        or timeout_seconds <= 0
        or timeout_seconds > MAX_TIMEOUT_SECONDS
    ):
        raise SnapshotWaitError("timeout must be greater than zero and at most 60 seconds")
    return float(timeout_seconds)


def _remaining(deadline, monotonic):
    remaining = deadline - monotonic()
    if remaining <= 0:
        raise SnapshotWaitError("snapshot wait deadline expired")
    return remaining


def _fetch(fetcher, url, deadline, monotonic):
    timeout_seconds = _remaining(deadline, monotonic)
    try:
        value = fetcher(url, timeout_seconds)
    except Exception:
        raise SnapshotWaitError("snapshot evidence fetch failed") from None
    if not isinstance(value, str):
        raise SnapshotWaitError("snapshot evidence response is malformed")
    try:
        if len(value) > MAX_RESPONSE_BODY_BYTES:
            raise SnapshotWaitError("snapshot evidence response exceeds maximum byte size")
        byte_size = len(value.encode("utf-8"))
    except SnapshotWaitError:
        raise
    except Exception:
        raise SnapshotWaitError("snapshot evidence response is malformed") from None
    if byte_size > MAX_RESPONSE_BODY_BYTES:
        raise SnapshotWaitError("snapshot evidence response exceeds maximum byte size")
    _remaining(deadline, monotonic)
    return value


def _decode_metrics(text, decoder):
    try:
        return decoder(text)
    except Exception:
        raise SnapshotWaitError("Gateway Snapshot metrics evidence is malformed") from None


def _decode_snapshot(text, decoder):
    try:
        return decoder(text)
    except Exception:
        raise SnapshotWaitError("Platform Public Snapshot evidence is malformed") from None


def _success_metrics_match(metrics, expectation):
    return (
        metrics.valid
        and metrics.refresh_result == "SUCCESS"
        and metrics.failure_reason == "NONE"
        and not metrics.last_valid_in_use
        and metrics.operations_route_count == expectation.operations_route_count
    )


def _invalid_fallback_metrics_match(metrics, expectation):
    return (
        metrics.valid
        and metrics.refresh_result == "FAILURE"
        and metrics.failure_reason == "INVALID_SNAPSHOT"
        and metrics.last_valid_in_use
        and metrics.operations_route_count == expectation.operations_route_count
    )


def wait_for_stage(
    *,
    expected_stage,
    expected_invalid_fallback_stage,
    timeout_seconds=60,
    fetcher=None,
    monotonic=None,
    sleep=None,
    metrics_decoder=None,
    public_snapshot_decoder=None
):
    """Poll until exact success or exact invalid-refresh fallback evidence exists."""

    if (expected_stage is None) == (expected_invalid_fallback_stage is None):
        raise SnapshotWaitError("exactly one Snapshot wait mode is required")
    timeout = _validated_timeout(timeout_seconds)
    stage = (
        expected_stage
        if expected_stage is not None
        else expected_invalid_fallback_stage
    )
    expectation = expectation_for_stage(stage)
    evidence_fetcher = ComposeFetcher() if fetcher is None else fetcher
    now = time.monotonic if monotonic is None else monotonic
    pause = time.sleep if sleep is None else sleep
    decode_metrics = decode_prometheus if metrics_decoder is None else metrics_decoder
    decode_snapshot = (
        decode_public_snapshot
        if public_snapshot_decoder is None
        else public_snapshot_decoder
    )
    deadline = now() + timeout

    while True:
        metrics_text = _fetch(evidence_fetcher, GATEWAY_METRICS_URL, deadline, now)
        metrics = _decode_metrics(metrics_text, decode_metrics)
        _remaining(deadline, now)

        if expected_stage is not None:
            snapshot_text = _fetch(
                evidence_fetcher, PLATFORM_SNAPSHOT_URL, deadline, now
            )
            snapshot = _decode_snapshot(snapshot_text, decode_snapshot)
            _remaining(deadline, now)
            try:
                matched = (
                    _success_metrics_match(metrics, expectation)
                    and snapshot.active_risk_codes
                    == expectation.active_risk_route_codes
                )
            except Exception:
                raise SnapshotWaitError("Snapshot evidence is malformed") from None
            _remaining(deadline, now)
            if matched:
                try:
                    result = WaitResult(
                        observation="SUCCESS",
                        verified_stage=stage,
                        expected_prior_candidate=None,
                        refresh_result=metrics.refresh_result,
                        failure_reason=metrics.failure_reason,
                        last_valid_in_use=metrics.last_valid_in_use,
                        operations_route_count=metrics.operations_route_count,
                        identity_verified=True,
                        requires_behavior_probe=False,
                    )
                except Exception:
                    raise SnapshotWaitError("Snapshot evidence is malformed") from None
                _remaining(deadline, now)
                return result
        else:
            try:
                matched = _invalid_fallback_metrics_match(metrics, expectation)
            except Exception:
                raise SnapshotWaitError("Snapshot evidence is malformed") from None
            _remaining(deadline, now)
            if matched:
                try:
                    result = WaitResult(
                        observation="INVALID_FALLBACK_METRICS",
                        verified_stage=None,
                        expected_prior_candidate=stage,
                        refresh_result=metrics.refresh_result,
                        failure_reason=metrics.failure_reason,
                        last_valid_in_use=metrics.last_valid_in_use,
                        operations_route_count=metrics.operations_route_count,
                        identity_verified=False,
                        requires_behavior_probe=True,
                    )
                except Exception:
                    raise SnapshotWaitError("Snapshot evidence is malformed") from None
                _remaining(deadline, now)
                return result

        remaining = _remaining(deadline, now)
        pause(min(MAX_POLL_INTERVAL_SECONDS, remaining))


def observe_recovery_transition(
    prior_stage,
    next_stage,
    timeout_seconds=60,
    fetcher=None,
    monotonic=None,
    sleep=None,
    metrics_decoder=None,
    public_snapshot_decoder=None,
) -> WaitResult:
    """Race the two legal post-mutation runtime outcomes under one deadline.

    A successful next Snapshot proves its identity with the public Snapshot.
    Invalid-refresh fallback proves only the prior metrics coordinates and
    therefore still requires the caller's separate behavior probe.
    """

    if prior_stage not in ROUTE_PREFIX_STAGES or next_stage not in ROUTE_PREFIX_STAGES:
        raise SnapshotWaitError("recovery stages must be adjacent Route-prefix stages")
    prior_index = ROUTE_PREFIX_STAGES.index(prior_stage)
    if prior_index + 1 >= len(ROUTE_PREFIX_STAGES):
        raise SnapshotWaitError("recovery stages must be adjacent Route-prefix stages")
    if ROUTE_PREFIX_STAGES[prior_index + 1] != next_stage:
        raise SnapshotWaitError("recovery stages must be adjacent Route-prefix stages")

    timeout = _validated_timeout(timeout_seconds)
    prior_expectation = expectation_for_stage(prior_stage)
    next_expectation = expectation_for_stage(next_stage)
    evidence_fetcher = ComposeFetcher() if fetcher is None else fetcher
    now = time.monotonic if monotonic is None else monotonic
    pause = time.sleep if sleep is None else sleep
    decode_metrics = decode_prometheus if metrics_decoder is None else metrics_decoder
    decode_snapshot = (
        decode_public_snapshot
        if public_snapshot_decoder is None
        else public_snapshot_decoder
    )
    deadline = now() + timeout

    while True:
        metrics_text = _fetch(evidence_fetcher, GATEWAY_METRICS_URL, deadline, now)
        metrics = _decode_metrics(metrics_text, decode_metrics)
        _remaining(deadline, now)
        try:
            next_success = _success_metrics_match(metrics, next_expectation)
            prior_fallback = _invalid_fallback_metrics_match(
                metrics, prior_expectation
            )
        except Exception:
            raise SnapshotWaitError("Snapshot evidence is malformed") from None
        _remaining(deadline, now)

        if next_success:
            snapshot_text = _fetch(
                evidence_fetcher, PLATFORM_SNAPSHOT_URL, deadline, now
            )
            snapshot = _decode_snapshot(snapshot_text, decode_snapshot)
            _remaining(deadline, now)
            try:
                snapshot_matches = (
                    snapshot.active_risk_codes
                    == next_expectation.active_risk_route_codes
                )
            except Exception:
                raise SnapshotWaitError("Snapshot evidence is malformed") from None
            _remaining(deadline, now)
            if snapshot_matches:
                try:
                    result = WaitResult(
                        observation="SUCCESS",
                        verified_stage=next_stage,
                        expected_prior_candidate=None,
                        refresh_result=metrics.refresh_result,
                        failure_reason=metrics.failure_reason,
                        last_valid_in_use=metrics.last_valid_in_use,
                        operations_route_count=metrics.operations_route_count,
                        identity_verified=True,
                        requires_behavior_probe=False,
                    )
                except Exception:
                    raise SnapshotWaitError("Snapshot evidence is malformed") from None
                _remaining(deadline, now)
                return result
        elif prior_fallback:
            try:
                result = WaitResult(
                    observation="INVALID_FALLBACK_METRICS",
                    verified_stage=None,
                    expected_prior_candidate=prior_stage,
                    refresh_result=metrics.refresh_result,
                    failure_reason=metrics.failure_reason,
                    last_valid_in_use=metrics.last_valid_in_use,
                    operations_route_count=metrics.operations_route_count,
                    identity_verified=False,
                    requires_behavior_probe=True,
                )
            except Exception:
                raise SnapshotWaitError("Snapshot evidence is malformed") from None
            _remaining(deadline, now)
            return result

        remaining = _remaining(deadline, now)
        pause(min(MAX_POLL_INTERVAL_SECONDS, remaining))


def _argument_parser():
    parser = argparse.ArgumentParser(
        description="Wait for exact local Gateway Snapshot evidence."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--expected-stage")
    mode.add_argument("--expected-invalid-fallback-stage")
    parser.add_argument("--timeout-seconds", type=float, default=60.0)
    return parser


def main(argv=None, *, stdout=None, stderr=None, fetcher=None):
    output_stream = sys.stdout if stdout is None else stdout
    error_stream = sys.stderr if stderr is None else stderr
    args = _argument_parser().parse_args(argv)
    try:
        result = wait_for_stage(
            expected_stage=args.expected_stage,
            expected_invalid_fallback_stage=args.expected_invalid_fallback_stage,
            timeout_seconds=args.timeout_seconds,
            fetcher=fetcher,
        )
    except SnapshotWaitError:
        error_stream.write("SNAPSHOT_WAIT_FAILED\n")
        return 1
    if result.identity_verified:
        output_stream.write(
            "SNAPSHOT_STAGE_VERIFIED stage=%s observation=%s\n"
            % (result.verified_stage, result.observation)
        )
    else:
        output_stream.write(
            "SNAPSHOT_FALLBACK_METRICS_VERIFIED "
            "expectedPriorCandidate=%s refreshResult=%s failureReason=%s "
            "lastValidInUse=%s operationsRouteCount=%d "
            "identity=UNVERIFIED_BEHAVIOR_PROBE_REQUIRED\n"
            % (
                result.expected_prior_candidate,
                result.refresh_result,
                result.failure_reason,
                str(result.last_valid_in_use).lower(),
                result.operations_route_count,
            )
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
