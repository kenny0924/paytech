#!/usr/bin/env python3
"""Strict, stdin-only verification of Gateway Snapshot evidence.

The module deliberately separates decoding from expectation matching.  The
bounded waiter can therefore distinguish a well-formed stale observation from
malformed evidence without treating ``last_valid_in_use`` as an identity
signal.
"""

import argparse
import json
import math
import re
import sys
from dataclasses import dataclass

from risk_rule_configuration_stage import RISK_ROUTE_ACTIVATION_ORDER


REFRESH_RESULTS = (
    "NEVER_ATTEMPTED",
    "SUCCESS",
    "FAILURE",
    "SUPPRESSED",
)
FAILURE_REASONS = (
    "NONE",
    "EMPTY_RESPONSE",
    "INVALID_SNAPSHOT",
    "CONTROL_PLANE_UNAVAILABLE",
)
MAX_RESPONSE_BODY_BYTES = 1_048_576

_SELECTED_METRICS = frozenset(
    {
        "gateway_snapshot_valid",
        "gateway_snapshot_last_refresh_result",
        "gateway_snapshot_last_failure_reason",
        "gateway_snapshot_last_valid_in_use",
        "gateway_snapshot_routes",
    }
)
_SAMPLE_RE = re.compile(
    r"^(?P<name>[A-Za-z_:][A-Za-z0-9_:]*)"
    r"(?:\{(?P<labels>.*)\})?"
    r"[ \t]+(?P<value>[^ \t]+)"
    r"(?:[ \t]+(?P<timestamp>[0-9]+))?$"
)
_LABEL_RE = re.compile(
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)[ \t]*=[ \t]*'
    r'"(?P<value>(?:\\.|[^"\\])*)"'
)


class EvidenceError(ValueError):
    """The supplied evidence is malformed or violates a closed contract."""


class EvidenceMismatch(EvidenceError):
    """Well-formed evidence does not yet match the requested observation."""


@dataclass(frozen=True)
class PrometheusEvidence:
    valid: bool
    refresh_result: str
    failure_reason: str
    last_valid_in_use: bool
    operations_route_count: int


@dataclass(frozen=True)
class PublicSnapshotEvidence:
    route_codes: tuple
    active_risk_codes: frozenset


def _require_bounded_text_body(text, evidence_name):
    if not isinstance(text, str):
        raise EvidenceError("%s response body must be text" % evidence_name)
    if len(text) > MAX_RESPONSE_BODY_BYTES:
        raise EvidenceError("%s response body exceeds maximum byte size" % evidence_name)
    try:
        byte_size = len(text.encode("utf-8"))
    except Exception:
        raise EvidenceError("%s response body is not valid UTF-8" % evidence_name) from None
    if byte_size > MAX_RESPONSE_BODY_BYTES:
        raise EvidenceError("%s response body exceeds maximum byte size" % evidence_name)


def _decode_label_value(value):
    result = []
    index = 0
    while index < len(value):
        character = value[index]
        if character != "\\":
            result.append(character)
            index += 1
            continue
        if index + 1 >= len(value):
            raise EvidenceError("invalid Prometheus label escape")
        escaped = value[index + 1]
        replacements = {"\\": "\\", '"': '"', "n": "\n"}
        if escaped not in replacements:
            raise EvidenceError("invalid Prometheus label escape")
        result.append(replacements[escaped])
        index += 2
    return "".join(result)


def _parse_labels(raw_labels):
    if raw_labels is None:
        return {}
    if raw_labels == "":
        return {}

    labels = {}
    position = 0
    while position < len(raw_labels):
        match = _LABEL_RE.match(raw_labels, position)
        if match is None:
            raise EvidenceError("invalid Prometheus labels")
        name = match.group("name")
        if name in labels:
            raise EvidenceError("duplicate Prometheus label")
        labels[name] = _decode_label_value(match.group("value"))
        position = match.end()
        if position == len(raw_labels):
            break
        if raw_labels[position] != ",":
            raise EvidenceError("invalid Prometheus labels")
        position += 1
        if position == len(raw_labels):
            raise EvidenceError("invalid Prometheus labels")
    return labels


def _parse_selected_samples(text):
    if not isinstance(text, str):
        raise EvidenceError("Prometheus evidence must be text")

    samples = []
    seen = set()
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        head = re.match(r"^([A-Za-z_:][A-Za-z0-9_:]*)", line)
        if head is None or head.group(1) not in _SELECTED_METRICS:
            continue
        match = _SAMPLE_RE.fullmatch(line)
        if match is None:
            raise EvidenceError("invalid selected Prometheus sample")
        labels = _parse_labels(match.group("labels"))
        try:
            value = float(match.group("value"))
        except ValueError:
            raise EvidenceError("selected Prometheus value must be numeric") from None
        if not math.isfinite(value):
            raise EvidenceError("selected Prometheus value must be finite")
        identity = (match.group("name"), tuple(sorted(labels.items())))
        if identity in seen:
            raise EvidenceError("each selected Prometheus series must appear exactly once")
        seen.add(identity)
        samples.append((match.group("name"), labels, value))
    return samples


def _scalar(samples, name):
    family = [(labels, value) for metric, labels, value in samples if metric == name]
    if len(family) != 1:
        raise EvidenceError("each selected Prometheus series must appear exactly once")
    labels, value = family[0]
    if labels:
        raise EvidenceError("selected scalar Prometheus metric must not have labels")
    return value


def _boolean_gauge(value, name):
    if value not in (0.0, 1.0):
        raise EvidenceError("%s must be exactly zero or one" % name)
    return value == 1.0


def _one_hot(samples, name, label_name, allowed_values):
    family = [(labels, value) for metric, labels, value in samples if metric == name]
    observed = {}
    for labels, value in family:
        if set(labels) != {label_name}:
            raise EvidenceError("%s one-hot series has invalid labels" % name)
        label_value = labels[label_name]
        if label_value not in allowed_values or label_value in observed:
            raise EvidenceError("%s one-hot series is missing or extra" % name)
        if value not in (0.0, 1.0):
            raise EvidenceError("%s one-hot value must be zero or one" % name)
        observed[label_value] = value
    if set(observed) != set(allowed_values) or sum(observed.values()) != 1.0:
        raise EvidenceError("%s one-hot series is missing or extra" % name)
    return next(value for value in allowed_values if observed[value] == 1.0)


def _operations_route_count(samples):
    family = [
        (labels, value)
        for metric, labels, value in samples
        if metric == "gateway_snapshot_routes"
    ]
    operations_values = []
    seen_channels = set()
    for labels, value in family:
        if set(labels) != {"channel"}:
            raise EvidenceError("gateway_snapshot_routes has invalid labels")
        channel = labels["channel"]
        if channel in seen_channels:
            raise EvidenceError("each selected Prometheus series must appear exactly once")
        seen_channels.add(channel)
        if value < 0 or not value.is_integer():
            raise EvidenceError("gateway_snapshot_routes must be a non-negative integer")
        if channel == "OPERATIONS":
            operations_values.append(int(value))
    if len(operations_values) != 1:
        raise EvidenceError("Operations route-count series must appear exactly once")
    return operations_values[0]


def decode_prometheus(text):
    """Decode the exact Gateway Snapshot metric families from Prometheus text."""

    try:
        _require_bounded_text_body(text, "Prometheus evidence")
        return _decode_prometheus(text)
    except EvidenceError:
        raise
    except Exception:
        raise EvidenceError("invalid Prometheus evidence") from None


def _decode_prometheus(text):
    samples = _parse_selected_samples(text)
    valid = _boolean_gauge(
        _scalar(samples, "gateway_snapshot_valid"), "gateway_snapshot_valid"
    )
    refresh_result = _one_hot(
        samples,
        "gateway_snapshot_last_refresh_result",
        "result",
        REFRESH_RESULTS,
    )
    failure_reason = _one_hot(
        samples,
        "gateway_snapshot_last_failure_reason",
        "reason",
        FAILURE_REASONS,
    )
    last_valid = _boolean_gauge(
        _scalar(samples, "gateway_snapshot_last_valid_in_use"),
        "gateway_snapshot_last_valid_in_use",
    )
    route_count = _operations_route_count(samples)
    return PrometheusEvidence(
        valid=valid,
        refresh_result=refresh_result,
        failure_reason=failure_reason,
        last_valid_in_use=last_valid,
        operations_route_count=route_count,
    )


def verify_prometheus(
    text,
    *,
    expected_refresh_result,
    expected_failure_reason,
    expected_last_valid,
    expected_operations_route_count
):
    """Require one exact, healthy runtime observation."""

    if expected_refresh_result not in REFRESH_RESULTS:
        raise EvidenceError("unknown expected refresh result")
    if expected_failure_reason not in FAILURE_REASONS:
        raise EvidenceError("unknown expected failure reason")
    if not isinstance(expected_last_valid, bool):
        raise EvidenceError("expected last-valid flag must be boolean")
    if (
        isinstance(expected_operations_route_count, bool)
        or not isinstance(expected_operations_route_count, int)
        or expected_operations_route_count < 0
    ):
        raise EvidenceError("expected Operations route count must be non-negative")

    evidence = decode_prometheus(text)
    expected = (
        True,
        expected_refresh_result,
        expected_failure_reason,
        expected_last_valid,
        expected_operations_route_count,
    )
    actual = (
        evidence.valid,
        evidence.refresh_result,
        evidence.failure_reason,
        evidence.last_valid_in_use,
        evidence.operations_route_count,
    )
    if actual != expected:
        raise EvidenceMismatch("Gateway Snapshot metrics do not match expectation")
    return evidence


class _DuplicateJsonKey(ValueError):
    pass


def _unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateJsonKey(key)
        result[key] = value
    return result


def _reject_json_constant(_value):
    raise ValueError("non-finite JSON number")


def decode_public_snapshot(text):
    """Decode route identity from one complete, canonically ordered Snapshot."""

    try:
        _require_bounded_text_body(text, "Public Snapshot evidence")
        return _decode_public_snapshot(text)
    except EvidenceError:
        raise
    except Exception:
        raise EvidenceError("invalid Public Snapshot evidence") from None


def _decode_public_snapshot(text):
    try:
        snapshot = json.loads(
            text,
            object_pairs_hook=_unique_json_object,
            parse_constant=_reject_json_constant,
        )
    except _DuplicateJsonKey:
        raise EvidenceError("duplicate JSON object key") from None
    except (json.JSONDecodeError, ValueError):
        raise EvidenceError("invalid Public Snapshot JSON") from None

    if not isinstance(snapshot, dict) or set(snapshot) != {
        "schemaVersion",
        "targets",
        "routes",
    }:
        raise EvidenceError("Public Snapshot root contract is invalid")
    if type(snapshot["schemaVersion"]) is not int or snapshot["schemaVersion"] != 1:
        raise EvidenceError("Public Snapshot schemaVersion must be exactly 1")
    if not isinstance(snapshot["targets"], list) or not isinstance(snapshot["routes"], list):
        raise EvidenceError("Public Snapshot targets and routes must be arrays")

    route_codes = []
    for route in snapshot["routes"]:
        if not isinstance(route, dict):
            raise EvidenceError("Public Snapshot Route must be an object")
        route_code = route.get("routeCode")
        if not isinstance(route_code, str) or not route_code:
            raise EvidenceError("Public Snapshot routeCode must be a non-empty string")
        route_codes.append(route_code)
    if len(route_codes) != len(set(route_codes)):
        raise EvidenceError("Public Snapshot contains duplicate routeCode")
    try:
        binary_order = sorted(route_codes, key=lambda value: value.encode("utf-8"))
    except UnicodeEncodeError:
        raise EvidenceError("Public Snapshot routeCode is not valid UTF-8") from None
    if route_codes != binary_order:
        raise EvidenceError("Public Snapshot Routes are not in canonical binary order")

    known_risk_codes = frozenset(RISK_ROUTE_ACTIVATION_ORDER)
    unknown_risk_codes = {
        code
        for code in route_codes
        if code.startswith("operations-risk-") and code not in known_risk_codes
    }
    if unknown_risk_codes:
        raise EvidenceError("Public Snapshot contains an unknown Risk routeCode")
    active_risk_codes = frozenset(code for code in route_codes if code in known_risk_codes)
    return PublicSnapshotEvidence(
        route_codes=tuple(route_codes), active_risk_codes=active_risk_codes
    )


def normalize_activation_prefix(expected_codes):
    try:
        codes = tuple(expected_codes)
    except TypeError:
        raise EvidenceError("expected Risk activation prefix must be iterable") from None
    if any(not isinstance(code, str) or not code for code in codes):
        raise EvidenceError("expected Risk activation prefix contains an invalid code")
    if len(codes) != len(set(codes)):
        raise EvidenceError("expected Risk activation prefix contains a duplicate code")

    known = frozenset(RISK_ROUTE_ACTIVATION_ORDER)
    if any(code not in known for code in codes):
        raise EvidenceError("expected Risk activation prefix contains an unknown code")
    expected_set = frozenset(codes)
    legal_prefix_sets = {
        frozenset(RISK_ROUTE_ACTIVATION_ORDER[:length])
        for length in range(len(RISK_ROUTE_ACTIVATION_ORDER) + 1)
    }
    if expected_set not in legal_prefix_sets:
        raise EvidenceError("expected Risk codes do not form an activation prefix")
    return expected_set


def verify_public_snapshot(text, expected_risk_route_codes):
    expected_set = normalize_activation_prefix(expected_risk_route_codes)
    evidence = decode_public_snapshot(text)
    if evidence.active_risk_codes != expected_set:
        raise EvidenceMismatch("Public Snapshot active Risk set does not match expectation")
    return evidence


def _argument_parser():
    parser = argparse.ArgumentParser(
        description="Verify Gateway Snapshot evidence read from standard input."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--prometheus", action="store_true")
    mode.add_argument("--public-snapshot", action="store_true")
    parser.add_argument("--expected-refresh-result", choices=REFRESH_RESULTS)
    parser.add_argument("--expected-failure-reason", choices=FAILURE_REASONS)
    parser.add_argument("--expected-last-valid", choices=("true", "false"))
    parser.add_argument("--expected-operations-route-count", type=int)
    parser.add_argument("--expected-risk-route-prefix")
    return parser


def main(argv=None, *, stdin=None, stdout=None, stderr=None):
    input_stream = sys.stdin if stdin is None else stdin
    output_stream = sys.stdout if stdout is None else stdout
    error_stream = sys.stderr if stderr is None else stderr
    parser = _argument_parser()
    args = parser.parse_args(argv)

    prometheus_options = (
        args.expected_refresh_result,
        args.expected_failure_reason,
        args.expected_last_valid,
        args.expected_operations_route_count,
    )
    if args.prometheus:
        if any(value is None for value in prometheus_options):
            parser.error("Prometheus mode requires all expected metric values")
        if args.expected_risk_route_prefix is not None:
            parser.error("Public Snapshot expectation is invalid in Prometheus mode")
    else:
        if any(value is not None for value in prometheus_options):
            parser.error("Prometheus expectations are invalid in Public Snapshot mode")
        if args.expected_risk_route_prefix is None:
            parser.error("Public Snapshot mode requires the expected Risk prefix")

    evidence_text = input_stream.read(MAX_RESPONSE_BODY_BYTES + 1)
    try:
        if args.prometheus:
            verify_prometheus(
                evidence_text,
                expected_refresh_result=args.expected_refresh_result,
                expected_failure_reason=args.expected_failure_reason,
                expected_last_valid=args.expected_last_valid == "true",
                expected_operations_route_count=args.expected_operations_route_count,
            )
            output_stream.write("SNAPSHOT_METRICS_VERIFIED\n")
        else:
            prefix = (
                ()
                if args.expected_risk_route_prefix == ""
                else tuple(args.expected_risk_route_prefix.split(","))
            )
            verify_public_snapshot(evidence_text, prefix)
            output_stream.write("PUBLIC_SNAPSHOT_VERIFIED\n")
    except EvidenceError:
        error_stream.write("SNAPSHOT_EVIDENCE_INVALID\n")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
