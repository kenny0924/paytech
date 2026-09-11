#!/usr/bin/env python3
"""Explicit, adjacent-only Risk Operations Route rollout and reconciliation."""
import argparse
from collections.abc import Mapping
from dataclasses import dataclass
import http.client
import json
from pathlib import Path
from types import MappingProxyType
import uuid

from initialize import read_risk_operations_stage, risk_operations_rollout_lock
from risk_rule_configuration_stage import (
    RISK_ACTIVE_RESOURCES,
    RISK_ROUTE_ACTIVATION_ORDER,
    ROUTE_PREFIX_STAGES,
    classify_stage,
)
from smoke import Browser, check_problem


ROOT = Path(__file__).resolve().parents[1]
ORIGIN = "http://localhost:28080"
ROUTE_ORDER = RISK_ROUTE_ACTIVATION_ORDER
ROUTE_STAGES = ROUTE_PREFIX_STAGES
EXPECTED_ROUTE_COUNT = 36
MAX_RETRY_AFTER_SECONDS = 6
INSPECT_DEADLINE_SECONDS = 90
ADJACENT_DEADLINE_SECONDS = 180
RECONCILE_DEADLINE_SECONDS = 720

_READ_HEADERS = (
    MappingProxyType({"headerName": "authorization", "headerUse": "CORS_REQUEST_ALLOWED"}),
    MappingProxyType({"headerName": "origin", "headerUse": "FORWARD_REQUEST"}),
)
_WRITE_HEADERS = (
    MappingProxyType({"headerName": "authorization", "headerUse": "CORS_REQUEST_ALLOWED"}),
    MappingProxyType({"headerName": "content-type", "headerUse": "CORS_REQUEST_ALLOWED"}),
    MappingProxyType({"headerName": "idempotency-key", "headerUse": "CORS_REQUEST_ALLOWED"}),
    MappingProxyType({"headerName": "content-type", "headerUse": "FORWARD_REQUEST"}),
    MappingProxyType({"headerName": "idempotency-key", "headerUse": "FORWARD_REQUEST"}),
    MappingProxyType({"headerName": "origin", "headerUse": "FORWARD_REQUEST"}),
)
_GUARDS = (
    MappingProxyType({"ruleType": "CONCURRENCY", "dimension": "AUTHENTICATED_PRINCIPAL",
                      "capacity": None, "refillTokens": None, "refillPeriodMillis": None,
                      "concurrencyLimit": 2}),
    MappingProxyType({"ruleType": "CONCURRENCY", "dimension": "CLIENT_IP",
                      "capacity": None, "refillTokens": None, "refillPeriodMillis": None,
                      "concurrencyLimit": 3}),
    MappingProxyType({"ruleType": "CONCURRENCY", "dimension": "SHARED_ROUTE",
                      "capacity": None, "refillTokens": None, "refillPeriodMillis": None,
                      "concurrencyLimit": 50}),
    MappingProxyType({"ruleType": "RATE_LIMIT", "dimension": "AUTHENTICATED_PRINCIPAL",
                      "capacity": 10, "refillTokens": 10, "refillPeriodMillis": 60000,
                      "concurrencyLimit": None}),
    MappingProxyType({"ruleType": "RATE_LIMIT", "dimension": "CLIENT_IP",
                      "capacity": 10, "refillTokens": 30, "refillPeriodMillis": 60000,
                      "concurrencyLimit": None}),
    MappingProxyType({"ruleType": "RATE_LIMIT", "dimension": "SHARED_ROUTE",
                      "capacity": 300, "refillTokens": 300, "refillPeriodMillis": 60000,
                      "concurrencyLimit": None}),
)


def _route(method, external_path, downstream_path, permission_id, permission_code,
           body_limit, headers, query_parameters):
    return MappingProxyType({
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
        "headers": headers,
        "queryParameterNames": tuple(query_parameters),
        "guardRules": _GUARDS,
        "createdBy": "1",
    })


ROUTE_MATRIX = MappingProxyType({
    "operations-risk-rule-authoring-context": _route(
        "GET", "/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}",
        "/decision-rule-sets/v1/authoring-context/{pointCode}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, _READ_HEADERS, ()),
    "operations-risk-rule-set-list": _route(
        "GET", "/api/operations/decision-rule-sets/v1/rule-sets",
        "/decision-rule-sets/v1/rule-sets", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, _READ_HEADERS,
        ("page", "pointCode", "ruleSetCode", "size", "status")),
    "operations-risk-rule-set-detail": _route(
        "GET", "/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}",
        "/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, _READ_HEADERS, ()),
    "operations-risk-test-candidate-list": _route(
        "GET", "/api/operations/decision-releases/v1/candidates",
        "/decision-releases/v1/candidates", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, _READ_HEADERS,
        ("page", "pointCode", "size", "status")),
    "operations-risk-test-candidate-detail": _route(
        "GET", "/api/operations/decision-releases/v1/candidates/{releaseId}",
        "/decision-releases/v1/candidates/{releaseId}", "900000000000000536",
        "RISK_DECISION_CONFIG_READ", 0, _READ_HEADERS, ()),
    "operations-risk-rule-set-save": _route(
        "POST", "/api/operations/decision-rule-sets/v1/rule-sets",
        "/decision-rule-sets/v1/rule-sets", "900000000000000537",
        "RISK_RULE_SET_SAVE", 65536, _WRITE_HEADERS, ()),
    "operations-risk-rule-set-freeze": _route(
        "POST", "/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze",
        "/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze", "900000000000000538",
        "RISK_RULE_SET_FREEZE", 16384, _WRITE_HEADERS, ()),
    "operations-risk-test-candidate-create": _route(
        "POST", "/api/operations/decision-releases/v1/candidates",
        "/decision-releases/v1/candidates", "900000000000000539",
        "RISK_TEST_CANDIDATE_CREATE", 16384, _WRITE_HEADERS, ()),
    "operations-risk-test-candidate-validate": _route(
        "POST", "/api/operations/decision-releases/v1/candidates/{releaseId}/validate",
        "/decision-releases/v1/candidates/{releaseId}/validate", "900000000000000540",
        "RISK_TEST_CANDIDATE_VALIDATE", 16384, _WRITE_HEADERS, ()),
})

if tuple(ROUTE_MATRIX) != ROUTE_ORDER:
    raise RuntimeError("Risk Route matrix must use the shared activation order")

FORWARD_TRANSITIONS = MappingProxyType({
    ROUTE_STAGES[index]: (ROUTE_ORDER[index], ROUTE_STAGES[index + 1])
    for index in range(len(ROUTE_ORDER))
})
REVERSE_TRANSITIONS = MappingProxyType({
    ROUTE_STAGES[index + 1]: (ROUTE_ORDER[index], ROUTE_STAGES[index])
    for index in range(len(ROUTE_ORDER))
})

_PAGE_KEYS = frozenset({"items", "page", "size", "total"})
_SUMMARY_KEYS = frozenset({
    "routeCode", "channel", "httpMethod", "externalPathTemplate", "targetServiceCode",
    "targetStatus", "securityProfileCode", "authorizationMode", "status", "version",
})
_DETAIL_KEYS = frozenset({
    "routeCode", *next(iter(ROUTE_MATRIX.values())).keys(), "status", "version",
    "updatedBy", "createTime", "updateTime",
})
_NAVIGATION_KEYS = frozenset({
    "resourceId", "resourceCode", "resourceType", "parentResourceId", "displayName",
    "frontendRoute", "componentKey", "sortOrder",
})
_UNKNOWN_TRANSPORT_ERRORS = (
    TimeoutError,
    ConnectionError,
    OSError,
    http.client.HTTPException,
)


@dataclass(frozen=True)
class Observation:
    stage: str
    persisted: Mapping
    http_routes: Mapping
    route_details: Mapping
    navigation: Mapping


def _fail(message):
    raise SystemExit(message)


def _exact_mapping(value, keys, context):
    if not isinstance(value, Mapping) or frozenset(value) != keys:
        _fail(context + " has an unexpected response shape")
    return value


def _positive_int(value, context):
    if type(value) is not int or value <= 0:
        _fail(context + " must be a positive integer")
    return value


def _plain(value):
    if isinstance(value, Mapping):
        return {key: _plain(item) for key, item in value.items()}
    if isinstance(value, (tuple, list)):
        return [_plain(item) for item in value]
    return value


def _same_json_type_and_value(actual, expected):
    """Compare decoded JSON recursively without Python's bool/int/float coercion."""
    if type(actual) is not type(expected):
        return False
    if type(expected) is dict:
        return (actual.keys() == expected.keys() and
                all(_same_json_type_and_value(actual[key], value)
                    for key, value in expected.items()))
    if type(expected) is list:
        return (len(actual) == len(expected) and
                all(_same_json_type_and_value(actual[index], value)
                    for index, value in enumerate(expected)))
    return actual == expected


def _route_versions(persisted):
    rows = persisted.get("route_rows")
    if not isinstance(rows, (list, tuple)) or len(rows) != EXPECTED_ROUTE_COUNT:
        _fail("Persisted Route authority is missing the complete 36-row catalog")
    versions = {}
    statuses = {}
    for row in rows:
        if not isinstance(row, Mapping):
            _fail("Persisted Route authority has an invalid row")
        code = row.get("route_code")
        if not isinstance(code, str) or code in versions:
            _fail("Persisted Route authority contains a duplicate or invalid code")
        status = row.get("status")
        if status not in {"ACTIVE", "DISABLED"}:
            _fail("Persisted Route authority has an invalid status")
        statuses[code] = status
        versions[code] = _positive_int(row.get("version"), "Persisted Route version")
    if statuses != dict(persisted["routes"]):
        _fail("Persisted Route rows disagree with the complete Route status map")
    return versions


def _decode_persisted(reader):
    persisted = reader()
    if not isinstance(persisted, Mapping):
        _fail("Persisted rollout authority returned an invalid result")
    for key in ("stage", "resources", "routes", "resource_rows", "route_rows"):
        if key not in persisted:
            _fail("Persisted rollout authority omitted " + key)
    if not isinstance(persisted["resources"], Mapping) or not isinstance(persisted["routes"], Mapping):
        _fail("Persisted rollout projections must be mappings")
    if len(persisted["routes"]) != EXPECTED_ROUTE_COUNT:
        _fail("Persisted rollout requires the complete 36-route catalog; legacy or partial state is refused")
    try:
        stage = classify_stage(persisted["resources"], persisted["routes"])
    except ValueError as error:
        _fail("Persisted rollout is not a closed prefix stage: " + str(error))
    if (stage != persisted["stage"] or
            stage not in frozenset(ROUTE_STAGES).union({"RESOURCES_EXPOSED"})):
        _fail("Persisted stage disagrees with its complete authoritative projections")
    versions = _route_versions(persisted)
    return persisted, stage, versions


def _decode_summary(item):
    _exact_mapping(item, _SUMMARY_KEYS, "Route summary")
    if not isinstance(item["routeCode"], str) or not item["routeCode"]:
        _fail("Route summary code is invalid")
    if item["status"] not in {"ACTIVE", "DISABLED"}:
        _fail("Route summary status is invalid")
    _positive_int(item["version"], "Route summary version")
    for key in _SUMMARY_KEYS - {"version"}:
        if key != "status" and (not isinstance(item[key], str) or not item[key]):
            _fail("Route summary field " + key + " is invalid")
    return item["routeCode"], item["status"], item["version"]


def _read_http_routes(browser, deadline):
    page = 1
    size = 10
    routes = {}
    versions = {}
    while len(routes) < EXPECTED_ROUTE_COUNT:
        body = browser.request_rate_limited_get(
            "/gateway-admin/v1/routes?page=%d&size=%d&channel=OPERATIONS" % (page, size),
            max_retry_after_seconds=MAX_RETRY_AFTER_SECONDS,
            deadline=deadline,
        )
        _exact_mapping(body, _PAGE_KEYS, "Route page")
        if (type(body["page"]) is not int or body["page"] != page or
                type(body["size"]) is not int or body["size"] != size or
                type(body["total"]) is not int or body["total"] != EXPECTED_ROUTE_COUNT or
                not isinstance(body["items"], list)):
            _fail("Route page metadata or total is invalid")
        expected_size = min(size, EXPECTED_ROUTE_COUNT - len(routes))
        if len(body["items"]) != expected_size:
            _fail("Route page is incomplete or catalog changed during pagination")
        for item in body["items"]:
            code, status, version = _decode_summary(item)
            if code in routes:
                _fail("Route catalog contains a duplicate across pages")
            routes[code] = status
            versions[code] = version
        page += 1
    if len(routes) != EXPECTED_ROUTE_COUNT:
        _fail("Route catalog did not settle to exactly 36 rows")
    return routes, versions


def _decode_detail(code, body, expected_status, expected_version):
    _exact_mapping(body, _DETAIL_KEYS, "Risk Route detail")
    expected = {"routeCode": code, **_plain(ROUTE_MATRIX[code])}
    actual = {key: body[key] for key in expected}
    if not _same_json_type_and_value(actual, expected):
        _fail("Risk Route detail immutable matrix differs for " + code)
    if (not _same_json_type_and_value(body["status"], expected_status) or
            not _same_json_type_and_value(body["version"], expected_version)):
        _fail("Risk Route detail status/version disagrees with its catalog row")
    _positive_int(body["version"], "Risk Route detail version")
    for key in ("createdBy", "updatedBy"):
        if not isinstance(body[key], str) or not body[key].isdigit() or int(body[key]) <= 0:
            _fail("Risk Route detail audit actor is invalid")
    for key in ("createTime", "updateTime"):
        if not isinstance(body[key], str) or not body[key]:
            _fail("Risk Route detail audit time is invalid")
    return dict(body)


def _read_details(browser, routes, versions, deadline):
    details = {}
    for code in ROUTE_ORDER:
        details[code] = _decode_detail(
            code,
            browser.request_rate_limited_get(
                "/gateway-admin/v1/routes/" + code,
                max_retry_after_seconds=MAX_RETRY_AFTER_SECONDS,
                deadline=deadline,
            ),
            routes[code],
            versions[code],
        )
    return details


def _read_navigation(browser, expected_resources, deadline):
    body = browser.request_rate_limited_get(
        "/authorization/v1/navigation",
        max_retry_after_seconds=MAX_RETRY_AFTER_SECONDS,
        deadline=deadline,
    )
    _exact_mapping(body, frozenset({"resources"}), "Operations Navigation")
    if not isinstance(body["resources"], list):
        _fail("Operations Navigation resources must be a list")
    resources = {}
    for row in body["resources"]:
        _exact_mapping(row, _NAVIGATION_KEYS, "Operations Navigation Resource")
        code = row["resourceCode"]
        if not isinstance(code, str) or not code or code in resources:
            _fail("Operations Navigation contains an invalid or duplicate Resource")
        if (not isinstance(row["resourceId"], str) or not row["resourceId"].isdigit() or
                row["resourceType"] not in {"MENU", "BUTTON"} or
                (row["parentResourceId"] is not None and
                 (not isinstance(row["parentResourceId"], str) or not row["parentResourceId"].isdigit())) or
                not isinstance(row["displayName"], str) or not row["displayName"] or
                (row["frontendRoute"] is not None and not isinstance(row["frontendRoute"], str)) or
                (row["componentKey"] is not None and not isinstance(row["componentKey"], str)) or
                type(row["sortOrder"]) is not int or row["sortOrder"] < 0):
            _fail("Operations Navigation Resource has invalid field types")
        resources[code] = row["displayName"]
    if resources != dict(expected_resources):
        _fail("Operations Navigation disagrees with persisted ACTIVE Resource authority")
    return resources


def _risk_resources_are_disabled(persisted):
    rows = persisted.get("resource_rows")
    if not isinstance(rows, (list, tuple)):
        return False
    statuses = {}
    for row in rows:
        if not isinstance(row, Mapping):
            return False
        code = row.get("resource_code")
        if code in RISK_ACTIVE_RESOURCES:
            if code in statuses:
                return False
            statuses[code] = row.get("status")
    return frozenset(statuses) == frozenset(RISK_ACTIVE_RESOURCES) and all(
        status == "DISABLED" for status in statuses.values())


def stage_pair_for_route(route_code):
    if route_code not in ROUTE_ORDER:
        raise ValueError("Route code is not in the approved Risk rollout")
    index = ROUTE_ORDER.index(route_code)
    return ROUTE_STAGES[index], ROUTE_STAGES[index + 1]


class RouteRollout:
    """Read and mutate the closed Route prefix through injected local authorities."""

    def __init__(self, browser, persisted_reader=read_risk_operations_stage,
                 lock_factory=risk_operations_rollout_lock,
                 idempotency_key_factory=None):
        self.browser = browser
        self.persisted_reader = persisted_reader
        self.lock_factory = lock_factory
        self.idempotency_key_factory = idempotency_key_factory or (lambda: str(uuid.uuid4()))

    def close(self):
        logout_failed = False
        try:
            refresh_token = self.browser.refresh_token
            if not isinstance(refresh_token, str) or not refresh_token:
                raise ValueError("authenticated Browser has no refresh token")
            self.browser.request(
                "POST",
                "/auth/v1/logout",
                {"refreshToken": refresh_token},
                status=(200, 204),
            )
        except (Exception, SystemExit):
            logout_failed = True
        finally:
            self.browser.access = None
            self.browser.refresh_token = None
        if logout_failed:
            _fail("Operations logout failed; credentials and response omitted")

    def inspect_stage(self):
        return self._inspect_stage(
            self.browser.new_deadline(INSPECT_DEADLINE_SECONDS)
        )

    def _inspect_stage(self, deadline):
        persisted, stage, persisted_versions = self._read_persisted(deadline)
        return self._observe_persisted(
            persisted, stage, persisted_versions, deadline
        )

    def _read_persisted(self, deadline):
        remaining = self.browser.remaining_before_deadline(deadline)
        persisted, stage, persisted_versions = _decode_persisted(
            lambda: self.persisted_reader(timeout_seconds=remaining)
        )
        self.browser.remaining_before_deadline(deadline)
        return persisted, stage, persisted_versions

    def _observe_persisted(self, persisted, stage, persisted_versions, deadline):
        http_routes, http_versions = _read_http_routes(self.browser, deadline)
        if http_routes != dict(persisted["routes"]):
            _fail("HTTP Route status catalog disagrees with persisted authority")
        if http_versions != persisted_versions:
            _fail("HTTP Route versions disagree with persisted authority")
        details = _read_details(
            self.browser, http_routes, http_versions, deadline
        )
        navigation = _read_navigation(
            self.browser, persisted["resources"], deadline
        )
        return Observation(stage, persisted, MappingProxyType(http_routes),
                           MappingProxyType(details), MappingProxyType(navigation))

    def activate_route(self, route_code):
        previous_stage, next_stage = stage_pair_for_route(route_code)
        deadline = self.browser.new_deadline(ADJACENT_DEADLINE_SECONDS)
        with self.lock_factory():
            persisted, stage, versions = self._read_persisted(deadline)
            if stage == "RESOURCES_EXPOSED":
                _fail("RESOURCES_MUST_BE_DISABLED_FIRST")
            observed = self._observe_persisted(
                persisted, stage, versions, deadline
            )
            if observed.stage == next_stage:
                return observed
            if observed.stage != previous_stage:
                _fail("Route activation changed before the adjacent edge was locked")
            return self._change_locked(
                observed, route_code, "ACTIVE", next_stage, deadline
            )

    def disable_route(self, route_code):
        previous_stage, next_stage = stage_pair_for_route(route_code)
        deadline = self.browser.new_deadline(ADJACENT_DEADLINE_SECONDS)
        with self.lock_factory():
            persisted, stage, versions = self._read_persisted(deadline)
            if stage == "RESOURCES_EXPOSED" or not _risk_resources_are_disabled(persisted):
                _fail("RESOURCES_MUST_BE_DISABLED_FIRST")
            observed = self._observe_persisted(
                persisted, stage, versions, deadline
            )
            if observed.stage == previous_stage:
                return observed
            if observed.stage != next_stage:
                _fail("Route disable changed before the adjacent edge was locked")
            return self._change_locked(
                observed, route_code, "DISABLED", previous_stage, deadline
            )

    def reconcile_baseline(self):
        deadline = self.browser.new_deadline(RECONCILE_DEADLINE_SECONDS)
        with self.lock_factory():
            persisted, stage, versions = self._read_persisted(deadline)
            if stage == "RESOURCES_EXPOSED" or not _risk_resources_are_disabled(persisted):
                _fail("RESOURCES_MUST_BE_DISABLED_FIRST")
            observed = self._observe_persisted(
                persisted, stage, versions, deadline
            )
            while observed.stage != "BASELINE":
                transition = REVERSE_TRANSITIONS.get(observed.stage)
                if transition is None:
                    _fail("Persisted Route catalog is not an approved rollback prefix")
                route_code, previous_stage = transition
                observed = self._change_locked(
                    observed, route_code, "DISABLED", previous_stage, deadline)
            return observed

    def _change_locked(self, observed, route_code, target_status, target_stage,
                       deadline):
        if target_status == "DISABLED" and not _risk_resources_are_disabled(observed.persisted):
            _fail("RESOURCES_MUST_BE_DISABLED_FIRST")
        detail = observed.route_details[route_code]
        expected_version = detail["version"]
        body = {
            "status": target_status,
            "expectedVersion": expected_version,
            "reason": ("Risk 规则配置分阶段启用" if target_status == "ACTIVE"
                       else "Risk 规则配置分阶段回滚"),
        }
        encoded = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        key = self.idempotency_key_factory()
        if not isinstance(key, str) or not key:
            _fail("Idempotency key generator returned an invalid key")
        response = None
        response_headers = None
        response_status = None
        for attempt in range(2):
            try:
                remaining = self.browser.remaining_before_deadline(deadline)
            except SystemExit:
                if attempt == 1:
                    _fail(route_code + " UNKNOWN_REQUIRES_RECONCILIATION")
                raise
            try:
                response, response_headers, response_status = (
                    self.browser.request_raw_response(
                        "PATCH",
                        "/gateway-admin/v1/routes/%s/status" % route_code,
                        encoded,
                        status=(200, 409),
                        extra_headers={"Idempotency-Key": key},
                        timeout_seconds=min(20.0, remaining),
                    )
                )
                break
            except _UNKNOWN_TRANSPORT_ERRORS:
                if attempt == 1:
                    _fail(route_code + " UNKNOWN_REQUIRES_RECONCILIATION")
        self.browser.remaining_before_deadline(deadline)
        if type(response_status) is not int or response_status not in {200, 409}:
            _fail("Route mutation returned an invalid HTTP status")
        conflict = response_status == 409
        if conflict:
            check_problem(
                response,
                "CONCURRENT_REQUEST",
                409,
                response_headers,
            )
        post = self._inspect_stage(deadline)
        if post.stage != target_stage or post.http_routes[route_code] != target_status:
            if conflict:
                _fail("Route command encountered concurrent drift; inspect before retrying")
            _fail("Route post-read did not reach the exact adjacent target stage")
        post_version = post.route_details[route_code]["version"]
        if post_version != expected_version + 1:
            _fail("Route post-read version did not advance exactly once")
        if conflict:
            return post
        expected_response = {
            "routeCode": route_code,
            "status": target_status,
            "version": post_version,
        }
        if not _same_json_type_and_value(response, expected_response):
            _fail("Route mutation response did not match the authoritative post-read")
        return post


def _load_operator(base_url):
    material_path = ROOT / "private/operations-material.json"
    try:
        material = json.loads(material_path.read_text(encoding="utf-8"))
        identifier = material["administratorEmail"]
        password = material["administratorPassword"]
    except (OSError, ValueError, KeyError, TypeError):
        _fail("Prepared local Operations administrator material is unavailable; secrets omitted")
    browser = Browser(base_url)
    browser.login(identifier, password)
    return RouteRollout(browser)


def _validated_loopback_origin(base_url):
    try:
        Browser(base_url)
    except (TypeError, ValueError):
        _fail("Route rollout base URL must be a loopback HTTP origin")


def main(argv=None, *, dependency_factory=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=ORIGIN)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--inspect-stage", action="store_true")
    actions.add_argument("--activate-route", choices=ROUTE_ORDER)
    actions.add_argument("--disable-route", choices=ROUTE_ORDER)
    actions.add_argument("--reconcile-baseline", action="store_true")
    options = parser.parse_args(argv)
    if not any((options.inspect_stage, options.activate_route,
                options.disable_route, options.reconcile_baseline)):
        return 0
    _validated_loopback_origin(options.base_url)
    operator = (dependency_factory or _load_operator)(options.base_url)
    primary_error = None
    try:
        if options.inspect_stage:
            observed = operator.inspect_stage()
        elif options.activate_route:
            observed = operator.activate_route(options.activate_route)
        elif options.disable_route:
            observed = operator.disable_route(options.disable_route)
        else:
            observed = operator.reconcile_baseline()
    except BaseException as error:
        primary_error = error
    try:
        operator.close()
    except BaseException:
        if primary_error is None:
            raise
    if primary_error is not None:
        raise primary_error from None
    print(observed.stage)
    return 0


if __name__ == "__main__":
    main()
