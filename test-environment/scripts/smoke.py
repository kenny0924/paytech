#!/usr/bin/env python3
"""Local HTTP deployment check. --complete-bootstrap explicitly completes the new test administrator setup."""
import argparse
from collections.abc import Mapping
from datetime import datetime
from email.utils import format_datetime, parsedate_to_datetime
import http.client
import ipaddress
import json
import math
import os
from pathlib import Path
import re
import time
from urllib.parse import urlsplit

from initialize import read_risk_operations_stage, _restricted_file_creation
from risk_rule_configuration_stage import classify_stage

ROOT = Path(__file__).resolve().parents[1]
ORIGIN = "http://localhost:28080"
PROBLEM_REQUIRED_KEYS = frozenset(
    {"type", "title", "detail", "code", "requestId", "timestamp"}
)
PROBLEM_FORBIDDEN_KEYS = frozenset({"status", "instance", "message"})
PROBLEM_REASON_PHRASES = {
    400: "Bad Request",
    401: "Unauthorized",
    403: "Forbidden",
    404: "Not Found",
    409: "Conflict",
    413: "Payload Too Large",
    422: "Unprocessable Content",
    429: "Too Many Requests",
}
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
TIMESTAMP_PATTERN = re.compile(
    r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{6}Z$"
)
FIELD_PATTERN = re.compile(
    r"^[A-Za-z][A-Za-z0-9]*(?:(?:\.[A-Za-z][A-Za-z0-9]*)|(?:\[[0-9]+\]))*$"
)
PROBLEM_HEADER_NAMES = (
    "Content-Type", "X-Request-Id", "Date", "X-Timestamp", "Retry-After"
)
LAYERED_GUARD_REFILL_FLOOR_SECONDS = 6


class Browser:
    def __init__(self, base_url=ORIGIN, connection_factory=None, sleeper=None,
                 monotonic=None):
        if not isinstance(base_url, str) or any(character.isspace() or ord(character) < 32 or ord(character) == 127 for character in base_url):
            raise ValueError('Smoke base URL must be a canonical loopback HTTP origin.')
        try:
            parsed = urlsplit(base_url)
            address = None if parsed.hostname == 'localhost' else ipaddress.ip_address(parsed.hostname)
            loopback = address is None or address.is_loopback
            host = 'localhost' if address is None else address.compressed
            canonical_host = '[' + host + ']' if address is not None and address.version == 6 else host
            port = parsed.port if parsed.port is not None else 80
            canonical_origin = 'http://' + canonical_host + (':' + str(port) if parsed.port is not None else '')
        except (ValueError, TypeError):
            raise ValueError('Smoke base URL must use a loopback HTTP host and valid port.') from None
        if not loopback or '%' in canonical_host or base_url != canonical_origin:
            raise ValueError('Smoke base URL must be a loopback HTTP origin.')
        self.host, self.port = parsed.hostname, port
        self.origin = canonical_origin
        self.connection_factory = connection_factory or http.client.HTTPConnection
        self.sleeper = sleeper or time.sleep
        if not callable(self.sleeper):
            raise TypeError("Smoke sleeper must be callable.")
        self.monotonic = monotonic or time.monotonic
        if not callable(self.monotonic):
            raise TypeError("Smoke monotonic clock must be callable.")
        self.access = None
        self.refresh_token = None

    def new_deadline(self, timeout_seconds):
        if (
            isinstance(timeout_seconds, bool)
            or not isinstance(timeout_seconds, (int, float))
            or not math.isfinite(float(timeout_seconds))
            or timeout_seconds <= 0
            or timeout_seconds > 720
        ):
            raise ValueError("Read deadline must be between 0 and 720 seconds.")
        return self.monotonic() + float(timeout_seconds)

    def remaining_before_deadline(self, deadline):
        if (
            isinstance(deadline, bool)
            or not isinstance(deadline, (int, float))
            or not math.isfinite(float(deadline))
        ):
            raise ValueError("Read deadline must be a finite monotonic value.")
        remaining = float(deadline) - self.monotonic()
        if remaining <= 0:
            raise SystemExit(
                "Bounded local read deadline was exhausted; credentials and response omitted."
            )
        return remaining

    def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
        raw = json.dumps(body if body is not None else {}).encode('utf-8') if method != 'GET' else None
        return self.request_raw(method, path, raw, authenticated, status, extra_headers)

    def request_raw(self, method, path, body, authenticated=True, status=200,
                    extra_headers=None, timeout_seconds=20):
        """Send byte-exact JSON, including caller-owned whitespace for boundary probes."""
        decoded, _, _ = self.request_raw_response(
            method, path, body, authenticated, status, extra_headers,
            timeout_seconds=timeout_seconds,
        )
        return decoded

    def request_raw_response(self, method, path, body, authenticated=True,
                             status=200, extra_headers=None, timeout_seconds=20):
        """Send byte-exact JSON and retain authoritative HTTP status/headers."""
        raw, response_headers, response_status = self._exchange_raw(
            method, path, body, authenticated, status, extra_headers,
            timeout_seconds=timeout_seconds,
        )
        return _decode_json(raw), response_headers, response_status

    def request_problem(self, method, path, code, status, body=None, authenticated=True, extra_headers=None):
        raw = json.dumps(body if body is not None else {}).encode('utf-8') if method != 'GET' else None
        response_body, response_headers, _ = self._exchange_raw(
            method, path, raw, authenticated, status, extra_headers
        )
        try:
            decoded = _decode_json(response_body)
        except (UnicodeDecodeError, ValueError, TypeError):
            raise AssertionError("Problem response must be one strict JSON value.") from None
        return check_problem(decoded, code, status, response_headers)

    def request_rate_limited_get(self, path, max_retry_after_seconds, deadline):
        """Retry one read-only GET only for a strict, bounded Gateway rate-limit response."""
        if type(max_retry_after_seconds) is not int or not 1 <= max_retry_after_seconds <= 60:
            raise ValueError("Rate-limit retry ceiling must be an integer from 1 to 60 seconds.")
        for attempt in range(2):
            remaining = self.remaining_before_deadline(deadline)
            try:
                response_body, response_headers, response_status = self._exchange_raw(
                    "GET", path, None, True, (200, 429), None,
                    timeout_seconds=min(20.0, remaining),
                )
            except SystemExit:
                raise SystemExit(
                    "Bounded local GET returned an unexpected transport response; "
                    "credentials, query and response omitted."
                ) from None
            self.remaining_before_deadline(deadline)
            try:
                decoded = _decode_json(response_body)
            except (UnicodeDecodeError, ValueError, TypeError):
                raise AssertionError("HTTP response must be one strict JSON value.") from None
            if response_status == 200:
                return decoded
            check_problem(
                decoded, "GATEWAY_RATE_LIMITED", 429, response_headers
            )
            retry_after = _retry_after_seconds(
                response_headers, max_retry_after_seconds
            )
            if attempt == 1:
                raise SystemExit(
                    "Bounded local GET remained rate limited after one retry; "
                    "credentials and response omitted."
                )
            retry_wait_seconds = max(
                retry_after, LAYERED_GUARD_REFILL_FLOOR_SECONDS
            )
            if retry_wait_seconds >= self.remaining_before_deadline(deadline):
                raise SystemExit(
                    "Bounded local read deadline cannot admit Retry-After; "
                    "credentials and response omitted."
                )
            self.sleeper(retry_wait_seconds)
        raise AssertionError("unreachable rate-limit retry state")

    def request_rate_limited_login(
        self,
        identifier,
        password,
        max_retry_after_seconds,
        deadline,
        status=200,
    ):
        """Retry one login only after a strict, bounded Gateway rate-limit response."""
        if type(max_retry_after_seconds) is not int or not 1 <= max_retry_after_seconds <= 60:
            raise ValueError("Rate-limit retry ceiling must be an integer from 1 to 60 seconds.")
        expected = (status,) if type(status) is int else tuple(status)
        if (
            not expected
            or any(type(code) is not int or code not in {200, 401} for code in expected)
            or len(expected) != len(set(expected))
        ):
            raise ValueError("Login status must contain only unique HTTP 200 and 401 values.")
        raw_body = json.dumps(
            {"loginIdentifier": identifier, "password": password}
        ).encode("utf-8")
        for attempt in range(2):
            remaining = self.remaining_before_deadline(deadline)
            try:
                response_body, response_headers, response_status = self._exchange_raw(
                    "POST",
                    "/auth/v1/login",
                    raw_body,
                    False,
                    expected + (429,),
                    None,
                    timeout_seconds=min(20.0, remaining),
                )
            except SystemExit:
                raise SystemExit(
                    "Bounded local login returned an unexpected transport response; "
                    "credentials and response omitted."
                ) from None
            self.remaining_before_deadline(deadline)
            try:
                decoded = _decode_json(response_body)
            except (UnicodeDecodeError, ValueError, TypeError):
                raise AssertionError("HTTP response must be one strict JSON value.") from None
            if response_status in expected:
                if response_status == 200 and isinstance(decoded, Mapping):
                    self.access = decoded.get("accessToken")
                    self.refresh_token = decoded.get("refreshToken")
                return decoded
            check_problem(
                decoded, "GATEWAY_RATE_LIMITED", 429, response_headers
            )
            retry_after = _retry_after_seconds(
                response_headers, max_retry_after_seconds
            )
            if attempt == 1:
                raise SystemExit(
                    "Bounded local login remained rate limited after one retry; "
                    "credentials and response omitted."
                )
            retry_wait_seconds = max(
                retry_after, LAYERED_GUARD_REFILL_FLOOR_SECONDS
            )
            if retry_wait_seconds >= self.remaining_before_deadline(deadline):
                raise SystemExit(
                    "Bounded local login deadline cannot admit Retry-After; "
                    "credentials and response omitted."
                )
            self.sleeper(retry_wait_seconds)
        raise AssertionError("unreachable login rate-limit retry state")

    def _exchange_raw(self, method, path, body, authenticated=True, status=200,
                      extra_headers=None, timeout_seconds=20):
        if body is not None and not isinstance(body, bytes):
            raise TypeError('Raw JSON body must be bytes.')
        if (
            isinstance(timeout_seconds, bool)
            or not isinstance(timeout_seconds, (int, float))
            or not math.isfinite(float(timeout_seconds))
            or timeout_seconds <= 0
            or timeout_seconds > 20
        ):
            raise ValueError("HTTP timeout must be between 0 and 20 seconds.")
        if not path.startswith('/') or path.startswith('//') or '\r' in path or '\n' in path:
            raise ValueError('Operations request path must be relative to the API prefix.')
        headers = {"Accept": "application/json"}
        if method != "GET":
            headers.update({"Origin": self.origin, "Content-Type": "application/json"})
        if authenticated and self.access:
            headers["Authorization"] = "Bearer " + self.access
        headers.update(extra_headers or {})
        if body is not None:
            for key in list(headers):
                if key.lower() in {'content-length', 'transfer-encoding'}:
                    del headers[key]
            headers['Content-Length'] = str(len(body))
        connection = self.connection_factory(
            self.host, self.port, timeout=float(timeout_seconds)
        )
        try:
            connection.request(method, "/api/operations" + path, body, headers)
            response = connection.getresponse()
            raw = response.read()
            code = response.status
            cookies = response.headers.get_all("Set-Cookie") or []
            response_headers = {
                name.lower(): tuple(response.headers.get_all(name) or ())
                for name in PROBLEM_HEADER_NAMES
            }
        finally:
            connection.close()
        expected = (status,) if isinstance(status, int) else status
        if code not in expected:
            raise SystemExit(f"{method} {path}: HTTP {code}; credentials and response omitted.")
        if cookies:
            raise SystemExit("JSON Token contract must not issue Set-Cookie.")
        return raw, response_headers, code

    def login(self, identifier, password):
        data = self.request("POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, False)
        assert data["accessToken"] and data["refreshToken"] and data["refreshExpiresIn"] > 0
        self.access = data["accessToken"]
        self.refresh_token = data["refreshToken"]
        return data

    def change_password(self, current, new):
        self.request("POST", "/auth/v1/password", {"currentPassword": current, "newPassword": new}, status=(200, 204))
        self.access = None
        self.refresh_token = None


def checkpoint(name):
    path = ROOT / "private" / name
    with _restricted_file_creation():
        path.write_text("Completed through the test Gateway/BFF/Platform chain.\n")
        path.chmod(0o600)


def _require(condition, message):
    if not condition:
        raise AssertionError(message)


def check_page(body, page=1, size=10):
    _require(isinstance(body, Mapping), "Paging response must be an object.")
    _require(
        set(body) == {"items", "page", "size", "total"},
        "Unexpected paging response contract.",
    )
    _require(
        type(body["page"]) is int
        and body["page"] == page
        and type(body["size"]) is int
        and body["size"] == size,
        "Paging coordinates are invalid.",
    )
    _require(
        isinstance(body["items"], list) and len(body["items"]) <= size,
        "Paging items are invalid.",
    )
    _require(
        type(body["total"]) is int and body["total"] >= len(body["items"]),
        "Paging total is invalid.",
    )


def _unique_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("JSON object contains a duplicate member.")
        value[key] = item
    return value


def _decode_json(raw):
    def reject_non_finite_number(value):
        raise ValueError("JSON contains a non-finite number: " + value)

    return json.loads(
        raw,
        object_pairs_hook=_unique_object,
        parse_constant=reject_non_finite_number,
    ) if raw else None


def _retry_after_seconds(headers, maximum):
    values = headers.get("retry-after") if isinstance(headers, Mapping) else None
    if not isinstance(values, tuple) or len(values) != 1:
        raise SystemExit(
            "Rate-limit Retry-After must be one canonical delta; response omitted."
        )
    value = values[0]
    if not isinstance(value, str) or re.fullmatch(r"[1-9][0-9]*", value) is None:
        raise SystemExit(
            "Rate-limit Retry-After must be one canonical delta; response omitted."
        )
    seconds = int(value)
    if seconds > maximum:
        raise SystemExit(
            "Rate-limit Retry-After exceeds the approved local bound; response omitted."
        )
    return seconds


def check_problem(body, code, status, headers):
    """Validate the current RFC 9457-based Code-MoR Problem projection."""
    def require(condition, message):
        if not condition:
            raise AssertionError(message)

    require(isinstance(headers, Mapping), "Problem response headers are unavailable.")
    require(
        headers.get("content-type") == ("application/problem+json",),
        "Problem Content-Type must be one application/problem+json value.",
    )
    request_ids = headers.get("x-request-id")
    require(
        isinstance(request_ids, tuple) and len(request_ids) == 1,
        "Problem X-Request-Id must have one value.",
    )
    header_request_id = request_ids[0]
    require(
        isinstance(header_request_id, str)
        and REQUEST_ID_PATTERN.fullmatch(header_request_id) is not None,
        "Problem X-Request-Id is invalid.",
    )
    dates = headers.get("date")
    require(
        isinstance(dates, tuple) and len(dates) == 1,
        "Problem Date must have one value.",
    )
    try:
        parsed_date = parsedate_to_datetime(dates[0])
    except (TypeError, ValueError, OverflowError):
        raise AssertionError("Problem Date header is invalid.") from None
    require(
        parsed_date.tzinfo is not None,
        "Problem Date header must include a timezone.",
    )
    try:
        canonical_date = format_datetime(parsed_date, usegmt=True)
    except ValueError:
        raise AssertionError("Problem Date header must use IMF-fixdate in GMT.") from None
    require(
        canonical_date == dates[0],
        "Problem Date header must use IMF-fixdate in GMT.",
    )
    require(
        headers.get("x-timestamp", ()) == (),
        "Problem must not use X-Timestamp.",
    )
    require(isinstance(body, Mapping), "Problem response must be an object.")
    require(PROBLEM_REQUIRED_KEYS.issubset(body), "Problem response is incomplete.")
    require(PROBLEM_FORBIDDEN_KEYS.isdisjoint(body), "Problem contains a retired member.")
    require(body["type"] == "about:blank", "Problem type is invalid.")
    require(
        body["title"] == PROBLEM_REASON_PHRASES.get(status),
        "Problem title is invalid.",
    )
    require(
        isinstance(body["detail"], str) and bool(body["detail"].strip()),
        "Problem detail is invalid.",
    )
    require(body["code"] == code, "Problem code is invalid.")
    require(
        body["requestId"] == header_request_id,
        "Problem Request ID does not match its header.",
    )
    require(
        isinstance(body["timestamp"], str)
        and TIMESTAMP_PATTERN.fullmatch(body["timestamp"]) is not None,
        "Problem timestamp shape is invalid.",
    )
    try:
        datetime.strptime(body["timestamp"], "%Y-%m-%dT%H:%M:%S.%fZ")
    except ValueError:
        raise AssertionError("Problem timestamp is invalid.") from None
    if "errors" in body:
        errors = body["errors"]
        require(
            code == "VALIDATION_ERROR" and isinstance(errors, list) and bool(errors),
            "Problem field errors are invalid.",
        )
        for error in errors:
            require(
                isinstance(error, Mapping)
                and set(error) == {"field", "message"},
                "Problem field error shape is invalid.",
            )
            require(
                isinstance(error["field"], str)
                and FIELD_PATTERN.fullmatch(error["field"]) is not None,
                "Problem field path is invalid.",
            )
            require(
                isinstance(error["message"], str)
                and bool(error["message"].strip()),
                "Problem field message is invalid.",
            )
    return body


def complete_bootstrap(browser, material):
    if not (ROOT / "private/administrator-created").exists():
        if not (ROOT / "private/bootstrap-password-changed").exists():
            tokens = browser.login("admin", material["bootstrapPassword"])
            assert tokens["passwordChangeRequired"]
            browser.change_password(material["bootstrapPassword"], material["changedBootstrapPassword"])
            checkpoint("bootstrap-password-changed")
        tokens = browser.login("admin", material["changedBootstrapPassword"])
        assert tokens["bootstrapAdministratorRequired"] and not tokens["passwordChangeRequired"]
        browser.request("POST", "/auth/v1/bootstrap/administrator", {
            "email": material["administratorEmail"], "initialPassword": material["administratorInitialPassword"]})
        browser.access = None
        browser.refresh_token = None
        checkpoint("administrator-created")
    if not (ROOT / "private/administrator-password-changed").exists():
        tokens = browser.login(material["administratorEmail"], material["administratorInitialPassword"])
        assert tokens["passwordChangeRequired"]
        browser.change_password(material["administratorInitialPassword"], material["administratorPassword"])
        checkpoint("administrator-password-changed")


def read_http_routes(browser, expected_count):
    routes = {}
    declared_total = None
    page, size = 1, 10
    while True:
        result = browser.request('GET', f'/gateway-admin/v1/routes?page={page}&size={size}')
        check_page(result, page=page, size=size)
        if declared_total is None:
            declared_total = result['total']
        _require(
            result['total'] == declared_total == expected_count,
            'Route paging total changed or catalog is incomplete.',
        )
        expected_page_size = min(size, declared_total - len(routes))
        _require(expected_page_size > 0, 'Route paging exceeded its declared total.')
        _require(
            len(result['items']) == expected_page_size,
            'Route page is incomplete.',
        )
        for row in result['items']:
            _require(isinstance(row, Mapping), 'Route summary must be an object.')
            _require(
                isinstance(row.get('routeCode'), str) and bool(row['routeCode']),
                'Route summary code is invalid.',
            )
            _require(
                row['routeCode'] not in routes,
                'Duplicate Route across HTTP pages.',
            )
            routes[row['routeCode']] = row['status']
        if len(routes) == declared_total:
            return routes
        page += 1


def main(argv=None, *, persisted_reader=None, browser_factory=None, material_loader=None,
         output_path=None, bootstrap_ready=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--complete-bootstrap", action="store_true")
    options = parser.parse_args(argv)
    persisted = (persisted_reader or read_risk_operations_stage)()
    stage = classify_stage(persisted['resources'], persisted['routes'])
    _require(
        stage == persisted['stage'],
        'Persisted stage disagrees with its verified projections.',
    )
    material = (material_loader or (lambda: json.loads((ROOT / 'private/operations-material.json').read_text())))()
    browser = (browser_factory or Browser)()
    if options.complete_bootstrap:
        complete_bootstrap(browser, material)
    elif not (bootstrap_ready or (lambda: (ROOT / 'private/administrator-password-changed').exists()))():
        raise SystemExit("Test administrator has not been configured; explicitly run --complete-bootstrap once.")
    tokens = browser.login(material["administratorEmail"], material["administratorPassword"])
    _require(
        not tokens["passwordChangeRequired"]
        and not tokens["bootstrapAdministratorRequired"],
        'Operations administrator is not ready for smoke verification.',
    )
    context = browser.request("GET", "/auth/v1/context")
    _require(
        context["tenantId"] and context["memberId"],
        'Operations authentication context is incomplete.',
    )
    navigation = browser.request("GET", "/authorization/v1/navigation")
    expected_resources = {
        "OPERATIONS_SYSTEM": "系统管理",
        "OPERATIONS_ACCOUNTS": "人员管理",
        "OPERATIONS_ROLES": "角色管理",
        "OPERATIONS_AUDIT": "审计日志",
        "OPERATIONS_ACCOUNT_CREATE": "创建人员",
        "OPERATIONS_ACCOUNT_DISABLE": "禁用人员",
        "OPERATIONS_ACCOUNT_ASSIGN_ROLES": "分配角色",
        "OPERATIONS_ACCOUNT_DELETE": "删除人员",
        "OPERATIONS_ACCOUNT_ENABLE": "启用人员",
        "OPERATIONS_ROLE_CREATE": "创建角色",
        "OPERATIONS_ROLE_EDIT": "编辑角色",
        "OPERATIONS_ROLE_DELETE": "删除角色",
        "OPERATIONS_PLATFORM_CONFIGURATION": "平台配置",
        "OPERATIONS_GATEWAY_CONFIGURATION": "API 路由管理",
        "OPERATIONS_GATEWAY_CONFIGURATION_MANAGE": "管理配置",
    }
    if stage == 'RESOURCES_EXPOSED':
        expected_resources.update({
            'OPERATIONS_RISK_MANAGEMENT': '风控管理', 'OPERATIONS_RISK_RULE_CONFIGURATION': '规则配置',
            'OPERATIONS_RISK_RULE_SET_SAVE': '保存为新版本', 'OPERATIONS_RISK_RULE_SET_FREEZE': '冻结版本',
            'OPERATIONS_RISK_TEST_CANDIDATE_CREATE': '创建 TEST Candidate',
            'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE': '执行静态校验'})
    _require(
        expected_resources == persisted['resources'],
        'Persisted ACTIVE Resource projection differs.',
    )
    _require(
        isinstance(navigation, Mapping)
        and isinstance(navigation.get('resources'), list)
        and len(navigation['resources']) == len(expected_resources),
        'Navigation contains duplicate or missing Resources.',
    )
    _require(
        {r["resourceCode"]: r["displayName"] for r in navigation["resources"]}
        == persisted['resources'],
        "Operations navigation labels do not match the UTF-8 catalog.",
    )
    resources = {r["resourceCode"]: r for r in navigation["resources"]}
    system = resources["OPERATIONS_SYSTEM"]
    assert system["resourceType"] == "MENU" and system["parentResourceId"] is None
    assert system["frontendRoute"] is None and system["componentKey"] is None
    for code, route, order in [("OPERATIONS_ACCOUNTS", "/accounts", 10),
                               ("OPERATIONS_ROLES", "/roles", 20),
                               ("OPERATIONS_AUDIT", "/audit", 30)]:
        resource = resources[code]
        assert resource["resourceType"] == "MENU" and resource["parentResourceId"] == system["resourceId"]
        assert resource["frontendRoute"] == route and resource["componentKey"] == code
        assert resource["sortOrder"] == order
    platform_configuration = resources["OPERATIONS_PLATFORM_CONFIGURATION"]
    gateway_configuration = resources["OPERATIONS_GATEWAY_CONFIGURATION"]
    assert platform_configuration["resourceType"] == "MENU" and platform_configuration["parentResourceId"] is None
    assert gateway_configuration["resourceType"] == "MENU"
    assert gateway_configuration["parentResourceId"] == platform_configuration["resourceId"]
    assert gateway_configuration["frontendRoute"] == "/gateway-configuration"
    assert gateway_configuration["componentKey"] == "OPERATIONS_GATEWAY_CONFIGURATION"
    for resource in resources.values():
        if resource["resourceType"] == "BUTTON":
            if resource["resourceCode"] == "OPERATIONS_GATEWAY_CONFIGURATION_MANAGE":
                parent = "OPERATIONS_GATEWAY_CONFIGURATION"
            elif resource["resourceCode"].startswith("OPERATIONS_ROLE_"):
                parent = "OPERATIONS_ROLES"
            elif resource['resourceCode'] in {'OPERATIONS_ACCOUNT_CREATE', 'OPERATIONS_ACCOUNT_DISABLE',
                                             'OPERATIONS_ACCOUNT_ASSIGN_ROLES', 'OPERATIONS_ACCOUNT_DELETE',
                                             'OPERATIONS_ACCOUNT_ENABLE'}:
                parent = "OPERATIONS_ACCOUNTS"
            elif stage == 'RESOURCES_EXPOSED' and resource['resourceCode'] in {
                    'OPERATIONS_RISK_RULE_SET_SAVE', 'OPERATIONS_RISK_RULE_SET_FREEZE',
                    'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE'}:
                parent = 'OPERATIONS_RISK_RULE_CONFIGURATION'
            else:
                raise AssertionError('Unknown BUTTON parent contract.')
            assert resource["parentResourceId"] == resources[parent]["resourceId"]
    if stage == 'RESOURCES_EXPOSED':
        for row in persisted['resource_rows']:
            if row['resource_code'].startswith('OPERATIONS_RISK_'):
                resource = resources[row['resource_code']]
                for database, api in [('resource_id', 'resourceId'), ('resource_type', 'resourceType'),
                                      ('parent_resource_id', 'parentResourceId'), ('display_name', 'displayName'),
                                      ('frontend_route', 'frontendRoute'), ('component_key', 'componentKey'),
                                      ('sort_order', 'sortOrder')]:
                    value = row[database]
                    if database.endswith('_id') and value is not None:
                        value = str(value)
                    assert resource[api] == value, 'Risk Resource field differs: ' + api
    accounts = browser.request("GET", "/iam/v1/accounts")
    roles = browser.request("GET", "/iam/v1/roles")
    assert all(isinstance(role["employeeCount"], int) and role["employeeCount"] >= 0
               and role["version"] > 0 for role in roles["items"])
    audit = browser.request("GET", "/audit/v1/events")
    for page in (accounts, roles, audit):
        check_page(page)
    assert accounts["total"] >= 1 and roles["total"] >= 2 and audit["total"] > 0
    expected_roles = {"PLATFORM_ADMIN": "平台管理员", "OPERATIONS_VIEWER": "运营只读人员"}
    assert expected_roles.items() <= {r["roleCode"]: r["roleName"] for r in roles["items"]}.items(), \
        "Operations role labels do not match the UTF-8 catalog."
    targets = browser.request("GET", "/gateway-admin/v1/target-services")
    route_statuses = read_http_routes(browser, 27 if stage == 'LEGACY' else 36)
    check_page(targets)
    assert targets["total"] == 1
    _require(
        route_statuses == persisted['routes'],
        'HTTP Route status map differs from persisted authority.',
    )
    target = targets["items"][0]
    assert target["targetServiceCode"] == "operations-console-bff" and target["status"] == "ACTIVE"
    target_detail = browser.request("GET", "/gateway-admin/v1/target-services/operations-console-bff")
    assert target_detail["targetServiceCode"] == target["targetServiceCode"]
    expected_gateway_routes = {
        "operations-gateway-target-list",
        "operations-gateway-target-detail",
        "operations-gateway-target-status",
        "operations-gateway-route-list",
        "operations-gateway-route-detail",
        "operations-gateway-route-status",
    }
    gateway_routes = route_statuses
    assert expected_gateway_routes <= gateway_routes.keys()
    assert all(gateway_routes[code] == "ACTIVE" for code in expected_gateway_routes)
    route_detail = browser.request("GET", "/gateway-admin/v1/routes/operations-gateway-route-list")
    assert route_detail["targetServiceCode"] == "operations-console-bff"
    filtered_routes = browser.request(
        "GET", "/gateway-admin/v1/routes?routeCode=operations-gateway-route-list&channel=OPERATIONS"
        "&status=ACTIVE&targetServiceCode=operations-console-bff"
    )
    check_page(filtered_routes)
    assert filtered_routes["total"] == 1 and filtered_routes["items"][0]["routeCode"] == route_detail["routeCode"]
    for path in ("/iam/v1/accounts", "/iam/v1/roles", "/audit/v1/events"):
        for size in (20, 50, 100, 200):
            check_page(browser.request("GET", f"{path}?page=1&size={size}"), size=size)
    second_roles = browser.request("GET", "/iam/v1/roles?page=2&size=10")
    check_page(second_roles, page=2)
    assert second_roles["total"] == roles["total"]
    assert {r["roleId"] for r in roles["items"]}.isdisjoint(r["roleId"] for r in second_roles["items"])
    second_audit = browser.request("GET", "/audit/v1/events?page=2&size=10")
    check_page(second_audit, page=2)
    assert {row["auditEventId"] for row in audit["items"]}.isdisjoint(
        row["auditEventId"] for row in second_audit["items"])
    browser.request_problem("GET", "/iam/v1/roles?size=11", "INVALID_REQUEST", 400)
    browser.request_problem("GET", "/iam/v1/accounts?page=0&size=20", "INVALID_REQUEST", 400)
    same_password = browser.request_problem("POST", "/auth/v1/password", "BUSINESS_CHECK_FAILED", 400, {
        "currentPassword": material["administratorPassword"],
        "newPassword": material["administratorPassword"],
    })
    assert same_password["detail"] == "业务校验未通过"
    original_token = browser.refresh_token
    refreshed = browser.request("POST", "/auth/v1/refresh", {"refreshToken": original_token}, authenticated=False)
    assert refreshed["refreshToken"] and refreshed["refreshToken"] != original_token
    assert refreshed["refreshExpiresIn"] > 0
    browser.access = refreshed["accessToken"]
    browser.refresh_token = refreshed["refreshToken"]
    browser.request("GET", "/auth/v1/context")
    browser.request("POST", "/auth/v1/logout", {"refreshToken": browser.refresh_token}, status=(200, 204))
    browser.request_problem("POST", "/auth/v1/refresh", "OPERATIONS_SESSION_INVALID", 401,
                            {"refreshToken": browser.refresh_token}, authenticated=False)
    browser.access = None
    browser.refresh_token = None
    access_file = Path(output_path) if output_path is not None else ROOT / "private/access.md"
    with _restricted_file_creation():
        access_file.write_text(f"# Code-MoR test 环境\n\n入口：{ORIGIN}\n\n账号：{material['administratorEmail']}\n\n"
                          f"密码：{material['administratorPassword']}\n\n仅用于本机 mor-test；禁止复用到生产。\n")
        access_file.chmod(0o600)
    print('Operations rollout stage: ' + stage)
    print("PASS: JSON Token login, common paging defaults/sizes/database slices, navigation and role labels, "
          "Gateway control-plane reads, parameter/business messages, Refresh rotation, logout and revoked Refresh "
          "rejection; no cookies.")
    print("Test login details saved to test-environment/private/access.md (0600); no tokens persisted.")


if __name__ == "__main__":
    main()
