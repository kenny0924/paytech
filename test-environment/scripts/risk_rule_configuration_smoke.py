#!/usr/bin/env python3
"""Opt-in Risk rule-configuration smoke with injectable local boundaries."""

import argparse
from collections.abc import Mapping
import contextlib
from dataclasses import dataclass
import fcntl
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import stat
import sys
from datetime import datetime, timezone
import urllib.parse
import uuid

from risk_rule_configuration_rollout import (
    ROUTE_ORDER,
    ROUTE_STAGES,
    RouteRollout,
    stage_pair_for_route,
)
from risk_rule_configuration_stage import (
    LEGAL_STAGES,
    RISK_ACTIVE_RESOURCES,
)
from smoke import Browser, ORIGIN, ROOT


RULE_SET_CODE_PATTERN = re.compile(
    r"^SMOKE_[0-9]{8}T[0-9]{6}Z_[0-9A-F]{12}$"
)
_ENTROPY_PATTERN = re.compile(r"^[0-9A-Fa-f]{12}$")
_FIXTURE_GENERATION_PATTERN = re.compile(r"^[0-9A-F]{12}$")
BOUNDARY_BODY_BYTES = 65_536
OVERSIZE_BODY_BYTES = 65_537
MAX_RETRY_AFTER_SECONDS = 6
PAGED_READ_DEADLINE_SECONDS = 90
LOGIN_MAX_RETRY_AFTER_SECONDS = 60
LOGIN_DEADLINE_SECONDS = 90
OPERATIONS_SYSTEM_READ = "900000000000000524"
RISK_PERMISSION_IDS = (
    "900000000000000536",
    "900000000000000537",
    "900000000000000538",
    "900000000000000539",
    "900000000000000540",
)
AUTHORIZED_PERMISSION_IDS = (OPERATIONS_SYSTEM_READ,) + RISK_PERMISSION_IDS
AUTHORIZED_ROLE_CODE = "RISK_RULE_SMOKE_AUTHORIZED"
DENIED_ROLE_CODE = "RISK_RULE_SMOKE_DENIED"
AUTHORIZED_EMAIL = "risk-rule-authorized@example.test"
DENIED_EMAIL = "risk-rule-denied@example.test"
RISK_RESOURCE_CODES = tuple(RISK_ACTIVE_RESOURCES)
FIXTURE_CREDENTIAL_PATH = ROOT / "private" / "risk-rule-configuration-smoke.json"

_PASSWORD_KEYS = frozenset(
    {
        "authorizedInitialPassword",
        "authorizedPassword",
        "deniedInitialPassword",
        "deniedPassword",
    }
)
_LEGACY_CREDENTIAL_KEYS = _PASSWORD_KEYS
_CREDENTIAL_KEYS = _PASSWORD_KEYS | frozenset({"fixtureGeneration"})
_PAGE_KEYS = frozenset({"items", "page", "size", "total"})
_PROBLEM_KEYS = frozenset(
    {"type", "title", "detail", "code", "requestId", "timestamp"}
)
_PROBLEM_REASON_PHRASES = {
    400: "Bad Request",
    401: "Unauthorized",
    403: "Forbidden",
    404: "Not Found",
    409: "Conflict",
    413: "Content Too Large",
    422: "Unprocessable Content",
}
_NAVIGATION_RESOURCE_KEYS = frozenset(
    {
        "resourceId",
        "resourceCode",
        "resourceType",
        "parentResourceId",
        "displayName",
        "frontendRoute",
        "componentKey",
        "sortOrder",
    }
)
_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
_TIMESTAMP_PATTERN = re.compile(
    r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{6}Z$"
)
_POSITIVE_ID_PATTERN = re.compile(r"^[1-9][0-9]{0,18}$")
_HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")
VALIDATION_CHECK_CODES = (
    "REFERENCE_INTEGRITY",
    "AST_SHAPE",
    "AST_TYPES",
    "FEATURE_DEPENDENCY",
    "HANDLER_HASH",
    "ACTION_PARAMETERS",
    "TERMINAL_ACTION",
    "SYSTEM_OBLIGATION",
)


class SmokeFailure(RuntimeError):
    """Fail closed without carrying sensitive response material to the CLI."""


def generate_rule_set_code(now=None, random_hex=None):
    """Return a unique, uppercase code whose timestamp is explicitly UTC."""
    instant = now if now is not None else datetime.now(timezone.utc)
    if instant.tzinfo is None or instant.utcoffset() is None:
        raise ValueError("Rule Set code time must be timezone-aware UTC.")
    if instant.utcoffset().total_seconds() != 0:
        raise ValueError("Rule Set code time must be UTC.")
    entropy = random_hex if random_hex is not None else secrets.token_hex(6)
    if not isinstance(entropy, str) or not _ENTROPY_PATTERN.fullmatch(entropy):
        raise ValueError("Rule Set code entropy must contain exactly twelve hex digits.")
    return "SMOKE_{0}_{1}".format(
        instant.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
        entropy.upper(),
    )


def _rule(rule_number, literal):
    return {
        "ruleCode": "SMOKE_RULE_{0:03d}".format(rule_number),
        "ruleName": "SyntheticRule{0:03d}".format(rule_number),
        "priorityNo": rule_number,
        "enabledFlag": True,
        "reasonCode": None,
        "conditionNodes": [
            {
                "nodeKey": "root{0:03d}".format(rule_number),
                "parentNodeKey": None,
                "sequenceNo": 1,
                "nodeType": "PREDICATE",
                "groupOperator": None,
                "comparisonOperator": "EQ",
                "operands": [
                    {
                        "operandRole": "LEFT",
                        "valueSequence": 1,
                        "sourceType": "CONTEXT",
                        "fieldCode": "country_code",
                        "valueType": "STRING",
                        "literalValue": None,
                    },
                    {
                        "operandRole": "RIGHT",
                        "valueSequence": 1,
                        "sourceType": "LITERAL",
                        "fieldCode": None,
                        "valueType": "STRING",
                        "literalValue": literal,
                    },
                ],
            }
        ],
        "action": {"actionCode": "ALLOW_CHECKOUT", "actionVersionNo": 1},
    }


def _save_document(rule_set_code, literals):
    return {
        "pointCode": "CHECKOUT_PREPARE",
        "ruleSetCode": rule_set_code,
        "ruleSetName": "SyntheticBoundarySmoke",
        "baseRuleSetVersionId": None,
        "baseContentHash": None,
        "changeReason": "LocalSyntheticBoundaryVerification",
        "rules": [
            _rule(index, literal)
            for index, literal in enumerate(literals, start=1)
        ],
    }


def _compact_json(document):
    return json.dumps(
        document, ensure_ascii=True, separators=(",", ":")
    ).encode("ascii")


def build_save_request_bytes(rule_set_code):
    """Build a valid compact request of exactly the Gateway acceptance limit."""
    if not isinstance(rule_set_code, str) or not RULE_SET_CODE_PATTERN.fullmatch(
        rule_set_code
    ):
        raise ValueError("Invalid synthetic Rule Set code.")

    for rule_count in range(1, 201):
        literals = ["AE"] * rule_count
        document = _save_document(rule_set_code, literals)
        base = _compact_json(document)
        available = sum(
            255 - len(rule["ruleName"])
            for rule in document["rules"]
        )
        missing = BOUNDARY_BODY_BYTES - len(base)
        if 0 <= missing <= available:
            for rule in document["rules"]:
                addition = min(255 - len(rule["ruleName"]), missing)
                rule["ruleName"] += "A" * addition
                missing -= addition
            if missing != 0:
                raise AssertionError("Exact request padding was not exhausted.")
            raw = _compact_json(document)
            if len(raw) != BOUNDARY_BODY_BYTES:
                raise AssertionError("Exact request size changed during serialization.")
            return raw
    raise SmokeFailure("EXACT_BOUNDARY_REQUEST_CANNOT_BE_CONSTRUCTED")


def build_oversize_save_request_bytes(rule_set_code):
    """Append one legal JSON whitespace byte without reconstructing the request."""
    raw = build_save_request_bytes(rule_set_code) + b"\x20"
    if len(raw) != OVERSIZE_BODY_BYTES:
        raise AssertionError("Oversize request is not exactly one byte larger.")
    return raw


def send_raw_save(browser, raw_body, idempotency_key, expected_status=200):
    """Send caller-owned request bytes through Task 5's byte-exact Browser path."""
    return browser.request_raw(
        "POST",
        "/decision-rule-sets/v1/rule-sets",
        raw_body,
        status=expected_status,
        extra_headers={"Idempotency-Key": idempotency_key},
    )


def _mapping(value, context):
    if not isinstance(value, Mapping):
        raise SmokeFailure(context + "_INVALID")
    return value


def _text(value, context):
    if not isinstance(value, str) or not value:
        raise SmokeFailure(context + "_INVALID")
    return value


def _positive_int(value, context):
    if type(value) is not int or value <= 0:
        raise SmokeFailure(context + "_INVALID")
    return value


def _problem(body, code, status):
    value = _mapping(body, "PROBLEM")
    if not _PROBLEM_KEYS.issubset(value):
        raise SmokeFailure("PROBLEM_SHAPE_INVALID")
    if value["type"] != "about:blank":
        raise SmokeFailure("PROBLEM_TYPE_INVALID")
    if value["title"] != _PROBLEM_REASON_PHRASES.get(status):
        raise SmokeFailure("PROBLEM_TITLE_INVALID")
    _text(value["detail"], "PROBLEM_DETAIL")
    if value["code"] != code:
        raise SmokeFailure("PROBLEM_CODE_INVALID")
    request_id = _text(value["requestId"], "PROBLEM_REQUEST_ID")
    if not _REQUEST_ID_PATTERN.fullmatch(request_id):
        raise SmokeFailure("PROBLEM_REQUEST_ID_INVALID")
    timestamp = _text(value["timestamp"], "PROBLEM_TIMESTAMP")
    if not _TIMESTAMP_PATTERN.fullmatch(timestamp):
        raise SmokeFailure("PROBLEM_TIMESTAMP_INVALID")
    try:
        datetime.strptime(timestamp, "%Y-%m-%dT%H:%M:%S.%fZ")
    except ValueError:
        raise SmokeFailure("PROBLEM_TIMESTAMP_INVALID") from None
    if "errors" in value:
        errors = value["errors"]
        if code != "VALIDATION_ERROR" or not isinstance(errors, list) or not errors:
            raise SmokeFailure("PROBLEM_ERRORS_INVALID")
        for raw_error in errors:
            error = _mapping(raw_error, "PROBLEM_FIELD_ERROR")
            if frozenset(error) != frozenset({"field", "message"}):
                raise SmokeFailure("PROBLEM_ERRORS_INVALID")
            _text(error["field"], "PROBLEM_ERROR_FIELD")
            _text(error["message"], "PROBLEM_ERROR_MESSAGE")
    return value


def _page_items(browser, base_path, item_id, decoder=None):
    page = 1
    size = 200
    result = []
    declared_total = None
    identifiers = set()
    deadline = browser.new_deadline(PAGED_READ_DEADLINE_SECONDS)
    while True:
        separator = "&" if "?" in base_path else "?"
        body = browser.request_rate_limited_get(
            "{0}{1}page={2}&size={3}".format(base_path, separator, page, size),
            max_retry_after_seconds=MAX_RETRY_AFTER_SECONDS,
            deadline=deadline,
        )
        page_body = _mapping(body, "PAGE")
        if frozenset(page_body) != _PAGE_KEYS:
            raise SmokeFailure("PAGE_SHAPE_INVALID")
        if page_body["page"] != page or page_body["size"] != size:
            raise SmokeFailure("PAGE_COORDINATES_INVALID")
        total = page_body["total"]
        items = page_body["items"]
        if type(total) is not int or total < 0 or not isinstance(items, list):
            raise SmokeFailure("PAGE_VALUES_INVALID")
        if declared_total is None:
            declared_total = total
        if total != declared_total:
            raise SmokeFailure("PAGE_TOTAL_CHANGED")
        remaining = total - len(result)
        if remaining < 0 or len(items) != min(size, remaining):
            raise SmokeFailure("PAGE_INCOMPLETE")
        for raw in items:
            item = decoder(raw) if decoder is not None else _mapping(raw, "PAGE_ITEM")
            identifier = _text(item.get(item_id), "PAGE_ITEM_ID")
            if identifier in identifiers:
                raise SmokeFailure("PAGE_DUPLICATE_ID")
            identifiers.add(identifier)
            result.append(item)
        if len(result) == total:
            return result
        page += 1


def _default_password(label):
    return "Rr9!{0}-{1}".format(label, secrets.token_urlsafe(24))


def _default_fixture_generation():
    return secrets.token_hex(6).upper()


class FixtureCredentialStore:
    """Private fixture material with exclusive, restricted persistence."""

    def __init__(
        self,
        path=FIXTURE_CREDENTIAL_PATH,
        generation_factory=_default_fixture_generation,
    ):
        self.path = Path(path)
        self.generation_factory = generation_factory

    @staticmethod
    def _validate(value):
        data = _mapping(value, "FIXTURE_CREDENTIALS")
        keys = frozenset(data)
        if keys not in (_LEGACY_CREDENTIAL_KEYS, _CREDENTIAL_KEYS):
            raise SmokeFailure("FIXTURE_CREDENTIAL_SHAPE_INVALID")
        passwords = []
        for key in sorted(_PASSWORD_KEYS):
            password = data[key]
            if not isinstance(password, str) or len(password) < 12:
                raise SmokeFailure("FIXTURE_CREDENTIAL_VALUE_INVALID")
            passwords.append(password)
        if len(set(passwords)) != len(passwords):
            raise SmokeFailure("FIXTURE_CREDENTIALS_NOT_INDEPENDENT")
        generation = data.get("fixtureGeneration")
        if generation is not None and (
            not isinstance(generation, str)
            or not _FIXTURE_GENERATION_PATTERN.fullmatch(generation)
        ):
            raise SmokeFailure("FIXTURE_GENERATION_INVALID")
        return dict(data)

    def _verify_parent(self, create=False):
        try:
            parent_status = self.path.parent.lstat()
        except FileNotFoundError:
            if not create:
                raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_MISSING") from None
            try:
                self.path.parent.mkdir(parents=True, mode=0o700, exist_ok=False)
                parent_status = self.path.parent.lstat()
            except OSError:
                raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_MISSING") from None
        except OSError:
            raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_MISSING") from None
        if not stat.S_ISDIR(parent_status.st_mode) or stat.S_ISLNK(
            parent_status.st_mode
        ):
            raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_INVALID")
        if create:
            os.chmod(str(self.path.parent), 0o700)
            parent_status = self.path.parent.lstat()
            if not stat.S_ISDIR(parent_status.st_mode) or stat.S_ISLNK(
                parent_status.st_mode
            ):
                raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_INVALID")
        if stat.S_IMODE(parent_status.st_mode) != 0o700:
            raise SmokeFailure("FIXTURE_CREDENTIAL_DIRECTORY_MODE_INVALID")

    def _read_descriptor(self, descriptor):
        status = os.fstat(descriptor)
        if not stat.S_ISREG(status.st_mode) or stat.S_IMODE(status.st_mode) != 0o600:
            raise SmokeFailure("FIXTURE_CREDENTIAL_FILE_MODE_INVALID")
        try:
            os.lseek(descriptor, 0, os.SEEK_SET)
            chunks = []
            while True:
                chunk = os.read(descriptor, 4096)
                if not chunk:
                    break
                chunks.append(chunk)
            value = json.loads(b"".join(chunks).decode("utf-8"))
        except (OSError, ValueError, UnicodeError):
            raise SmokeFailure("FIXTURE_CREDENTIAL_FILE_INVALID") from None
        return self._validate(value)

    def read(self):
        self._verify_parent()
        flags = os.O_RDONLY
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        try:
            descriptor = os.open(str(self.path), flags)
        except OSError:
            raise SmokeFailure("FIXTURE_CREDENTIAL_FILE_UNAVAILABLE") from None
        try:
            return self._read_descriptor(descriptor)
        finally:
            os.close(descriptor)

    @staticmethod
    def _write_descriptor(descriptor, values):
        payload = _compact_json(values)
        offset = 0
        while offset < len(payload):
            written = os.write(descriptor, payload[offset:])
            if written <= 0:
                raise OSError("fixture credential write did not progress")
            offset += written
        os.fsync(descriptor)

    def _replace_atomically(self, values):
        temporary = self.path.with_name(
            ".{0}.{1}.tmp".format(self.path.name, secrets.token_hex(8))
        )
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = None
        try:
            descriptor = os.open(str(temporary), flags, 0o600)
            self._write_descriptor(descriptor, values)
            os.close(descriptor)
            descriptor = None
            os.chmod(str(temporary), 0o600)
            os.replace(str(temporary), str(self.path))
        except OSError:
            raise SmokeFailure("FIXTURE_CREDENTIAL_UPGRADE_FAILED") from None
        finally:
            if descriptor is not None:
                os.close(descriptor)
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass

    def _upgrade_legacy(self):
        flags = os.O_RDONLY
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        while True:
            try:
                descriptor = os.open(str(self.path), flags)
            except OSError:
                raise SmokeFailure("FIXTURE_CREDENTIAL_FILE_UNAVAILABLE") from None
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX)
                current = self._read_descriptor(descriptor)
                descriptor_status = os.fstat(descriptor)
                try:
                    path_status = self.path.lstat()
                except OSError:
                    continue
                if (
                    descriptor_status.st_dev != path_status.st_dev
                    or descriptor_status.st_ino != path_status.st_ino
                ):
                    continue
                if "fixtureGeneration" in current:
                    return current
                upgraded = dict(
                    current, fixtureGeneration=self.generation_factory()
                )
                upgraded = self._validate(upgraded)
                self._replace_atomically(upgraded)
                return self.read()
            finally:
                fcntl.flock(descriptor, fcntl.LOCK_UN)
                os.close(descriptor)

    def ensure(self, password_factory=_default_password, upgrade_legacy=True):
        self._verify_parent(create=True)
        try:
            existing = self.read()
        except SmokeFailure as error:
            if str(error) != "FIXTURE_CREDENTIAL_FILE_UNAVAILABLE":
                raise
        else:
            if "fixtureGeneration" not in existing and upgrade_legacy:
                return self._upgrade_legacy()
            return existing
        values = {
            key: password_factory(key)
            for key in sorted(_PASSWORD_KEYS)
        }
        values["fixtureGeneration"] = self.generation_factory()
        values = self._validate(values)
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        try:
            descriptor = os.open(str(self.path), flags, 0o600)
        except FileExistsError:
            return self.ensure(password_factory, upgrade_legacy)
        except OSError:
            raise SmokeFailure("FIXTURE_CREDENTIAL_CREATE_FAILED") from None
        try:
            self._write_descriptor(descriptor, values)
            os.close(descriptor)
            descriptor = None
            os.chmod(str(self.path), 0o600)
        finally:
            if descriptor is not None:
                os.close(descriptor)
        return self.read()

    def remove(self):
        self._verify_parent()
        try:
            status = self.path.lstat()
        except FileNotFoundError:
            return
        if not stat.S_ISREG(status.st_mode) or stat.S_ISLNK(status.st_mode):
            raise SmokeFailure("FIXTURE_CREDENTIAL_FILE_INVALID")
        self.path.unlink()


def _default_material_loader():
    try:
        with (ROOT / "private" / "operations-material.json").open(
            "r", encoding="utf-8"
        ) as stream:
            return json.load(stream)
    except (OSError, ValueError, UnicodeError):
        raise SmokeFailure("ADMINISTRATOR_MATERIAL_UNAVAILABLE") from None


def _default_idempotency_key():
    return str(uuid.uuid4())


def _default_nacos_probe(namespace, group, service):
    from initialize import environment, login, nacos_request, require_success

    values = environment()
    token = login("mor-test-services", values["MOR_NACOS_SERVICE_PASSWORD"])
    query = urllib.parse.urlencode(
        {
            "namespaceId": namespace,
            "groupName": group,
            "serviceName": service,
        }
    )
    response = require_success(
        nacos_request(
            "GET", "/v3/client/ns/instance/list?" + query, token=token
        ),
        "Risk discovery",
    )
    instances = response.get("data")
    if not isinstance(instances, list):
        raise SmokeFailure("NACOS_DISCOVERY_RESPONSE_INVALID")
    return instances


def verify_nacos_instances(instances):
    if not isinstance(instances, list) or len(instances) != 1:
        raise SmokeFailure("NACOS_RISK_INSTANCE_COUNT_INVALID")
    instance = _mapping(instances[0], "NACOS_RISK_INSTANCE")
    if (
        instance.get("ip") != "172.30.73.25"
        or type(instance.get("port")) is not int
        or instance.get("port") != 18104
        or instance.get("healthy") is not True
        or instance.get("enabled") is not True
    ):
        raise SmokeFailure("NACOS_RISK_INSTANCE_INVALID")


def verify_authorized_risk_navigation(browser):
    body = _mapping(
        browser.request("GET", "/authorization/v1/navigation"),
        "AUTHORIZED_NAVIGATION",
    )
    if frozenset(body) != frozenset({"resources"}) or not isinstance(
        body["resources"], list
    ):
        raise SmokeFailure("AUTHORIZED_NAVIGATION_SHAPE_INVALID")
    resources = {}
    for value in body["resources"]:
        row = _mapping(value, "AUTHORIZED_NAVIGATION_RESOURCE")
        if frozenset(row) != _NAVIGATION_RESOURCE_KEYS:
            raise SmokeFailure("AUTHORIZED_NAVIGATION_RESOURCE_SHAPE_INVALID")
        code = _text(row["resourceCode"], "AUTHORIZED_NAVIGATION_RESOURCE_CODE")
        if (
            code in resources
            or not isinstance(row["resourceId"], str)
            or not row["resourceId"].isdigit()
            or row["resourceType"] not in {"MENU", "BUTTON"}
            or (
                row["parentResourceId"] is not None
                and (
                    not isinstance(row["parentResourceId"], str)
                    or not row["parentResourceId"].isdigit()
                )
            )
            or not isinstance(row["displayName"], str)
            or not row["displayName"]
            or (
                row["frontendRoute"] is not None
                and not isinstance(row["frontendRoute"], str)
            )
            or (
                row["componentKey"] is not None
                and not isinstance(row["componentKey"], str)
            )
            or type(row["sortOrder"]) is not int
            or row["sortOrder"] < 0
        ):
            raise SmokeFailure("AUTHORIZED_NAVIGATION_RESOURCE_INVALID")
        resources[code] = row["displayName"]
    if any(
        resources.get(code) != label
        for code, label in RISK_ACTIVE_RESOURCES.items()
    ):
        raise SmokeFailure("AUTHORIZED_RISK_NAVIGATION_INCOMPLETE")
    return resources


def _decode_role_summary(value):
    role = _mapping(value, "ROLE_SUMMARY")
    expected = frozenset(
        {"roleId", "roleCode", "roleName", "systemRole", "version", "employeeCount"}
    )
    if frozenset(role) != expected:
        raise SmokeFailure("ROLE_SUMMARY_SHAPE_INVALID")
    _text(role["roleId"], "ROLE_ID")
    _text(role["roleCode"], "ROLE_CODE")
    _text(role["roleName"], "ROLE_NAME")
    if type(role["systemRole"]) is not bool:
        raise SmokeFailure("ROLE_SYSTEM_FLAG_INVALID")
    _positive_int(role["version"], "ROLE_VERSION")
    if type(role["employeeCount"]) is not int or role["employeeCount"] < 0:
        raise SmokeFailure("ROLE_EMPLOYEE_COUNT_INVALID")
    return dict(role)


def _decode_role_detail(value):
    role = _mapping(value, "ROLE_DETAIL")
    expected = frozenset(
        {
            "roleId",
            "roleCode",
            "roleName",
            "systemRole",
            "version",
            "permissionIds",
            "nodes",
        }
    )
    if frozenset(role) != expected:
        raise SmokeFailure("ROLE_DETAIL_SHAPE_INVALID")
    _text(role["roleId"], "ROLE_ID")
    _text(role["roleCode"], "ROLE_CODE")
    _text(role["roleName"], "ROLE_NAME")
    if type(role["systemRole"]) is not bool:
        raise SmokeFailure("ROLE_SYSTEM_FLAG_INVALID")
    _positive_int(role["version"], "ROLE_VERSION")
    permissions = role["permissionIds"]
    if (
        not isinstance(permissions, list)
        or any(not isinstance(item, str) or not item for item in permissions)
        or len(set(permissions)) != len(permissions)
        or not isinstance(role["nodes"], list)
    ):
        raise SmokeFailure("ROLE_PERMISSION_RELATIONSHIP_INVALID")
    return dict(role, permissionIds=list(permissions))


def _decode_account(value):
    account = _mapping(value, "ACCOUNT")
    expected = frozenset(
        {
            "accountId",
            "emailMasked",
            "status",
            "passwordChangeRequired",
            "lockedUntil",
            "version",
            "memberId",
            "memberVersion",
            "roles",
        }
    )
    if frozenset(account) != expected:
        raise SmokeFailure("ACCOUNT_SHAPE_INVALID")
    for field in ("accountId", "emailMasked", "status", "memberId"):
        _text(account[field], "ACCOUNT_" + field.upper())
    if type(account["passwordChangeRequired"]) is not bool:
        raise SmokeFailure("ACCOUNT_PASSWORD_CHANGE_FLAG_INVALID")
    _positive_int(account["version"], "ACCOUNT_VERSION")
    _positive_int(account["memberVersion"], "ACCOUNT_MEMBER_VERSION")
    if not isinstance(account["roles"], list):
        raise SmokeFailure("ACCOUNT_ROLE_RELATIONSHIP_INVALID")
    roles = []
    role_ids = set()
    for value in account["roles"]:
        role = _mapping(value, "ACCOUNT_ROLE")
        if frozenset(role) != frozenset(
            {"roleId", "roleCode", "roleName", "systemRole"}
        ):
            raise SmokeFailure("ACCOUNT_ROLE_SHAPE_INVALID")
        for field in ("roleId", "roleCode", "roleName"):
            _text(role[field], "ACCOUNT_ROLE_" + field.upper())
        if type(role["systemRole"]) is not bool or role["roleId"] in role_ids:
            raise SmokeFailure("ACCOUNT_ROLE_RELATIONSHIP_INVALID")
        role_ids.add(role["roleId"])
        roles.append(dict(role))
    return dict(account, roles=roles)


def _decode_context(value):
    context = _mapping(value, "AUTH_CONTEXT")
    expected = frozenset(
        {
            "accountId",
            "sessionId",
            "accountType",
            "emailMasked",
            "accountStatus",
            "tenantId",
            "memberId",
            "passwordChangeRequired",
            "bootstrapAdministratorRequired",
            "integrationMode",
        }
    )
    if frozenset(context) != expected:
        raise SmokeFailure("AUTH_CONTEXT_SHAPE_INVALID")
    for field in (
        "accountId",
        "sessionId",
        "accountType",
        "emailMasked",
        "accountStatus",
        "tenantId",
        "memberId",
        "integrationMode",
    ):
        _text(context[field], "AUTH_CONTEXT_" + field.upper())
    if (
        type(context["passwordChangeRequired"]) is not bool
        or type(context["bootstrapAdministratorRequired"]) is not bool
    ):
        raise SmokeFailure("AUTH_CONTEXT_FLAGS_INVALID")
    return dict(context)


def _decode_token(value):
    token = _mapping(value, "TOKEN_GRANT")
    expected = frozenset(
        {
            "accessToken",
            "tokenType",
            "expiresIn",
            "refreshToken",
            "refreshExpiresIn",
            "passwordChangeRequired",
            "bootstrapAdministratorRequired",
        }
    )
    if frozenset(token) != expected:
        raise SmokeFailure("TOKEN_GRANT_SHAPE_INVALID")
    _text(token["accessToken"], "ACCESS_TOKEN")
    _text(token["refreshToken"], "REFRESH_TOKEN")
    if token["tokenType"] != "Bearer":
        raise SmokeFailure("TOKEN_TYPE_INVALID")
    _positive_int(token["expiresIn"], "ACCESS_TOKEN_TTL")
    _positive_int(token["refreshExpiresIn"], "REFRESH_TOKEN_TTL")
    if (
        type(token["passwordChangeRequired"]) is not bool
        or type(token["bootstrapAdministratorRequired"]) is not bool
    ):
        raise SmokeFailure("TOKEN_GRANT_FLAGS_INVALID")
    return token


@dataclass(frozen=True)
class AuthorizationFixtureDefinition:
    role_code: str
    role_name: str
    email: str
    initial_password: str
    password: str
    authorized: bool


def _fixture_role_code(base_code, generation):
    if generation is None:
        return base_code
    return "{0}_{1}".format(base_code, generation)


def _fixture_email(base_email, generation):
    if generation is None:
        return base_email
    local, domain = base_email.split("@", 1)
    return "{0}-{1}@{2}".format(local, generation.lower(), domain)


def _fixture_role_code_matches(role_code, base_code):
    if role_code == base_code:
        return True
    if not isinstance(role_code, str):
        return False
    prefix = base_code + "_"
    return role_code.startswith(prefix) and bool(
        _FIXTURE_GENERATION_PATTERN.fullmatch(role_code[len(prefix) :])
    )


def _fixture_definitions(credentials):
    generation = credentials.get("fixtureGeneration")
    return (
        AuthorizationFixtureDefinition(
            _fixture_role_code(AUTHORIZED_ROLE_CODE, generation),
            "RiskRuleSmokeAuthorized",
            _fixture_email(AUTHORIZED_EMAIL, generation),
            credentials["authorizedInitialPassword"],
            credentials["authorizedPassword"],
            True,
        ),
        AuthorizationFixtureDefinition(
            _fixture_role_code(DENIED_ROLE_CODE, generation),
            "RiskRuleSmokeDenied",
            _fixture_email(DENIED_EMAIL, generation),
            credentials["deniedInitialPassword"],
            credentials["deniedPassword"],
            False,
        ),
    )


@dataclass(frozen=True)
class FixtureIdentity:
    browser: object
    role: Mapping
    account: Mapping
    context: Mapping


@dataclass(frozen=True)
class FixtureActors:
    authorized: FixtureIdentity
    denied: FixtureIdentity


class AuthorizationFixtureManager:
    """Strictly discover/create the two isolated fixture identities through IAM HTTP."""

    _LOGIN_DEADLINE_ATTRIBUTE = "_risk_rule_configuration_login_deadline"

    def __init__(
        self,
        browser_factory=Browser,
        material_loader=_default_material_loader,
        credential_store=None,
        password_factory=_default_password,
        idempotency_key_factory=_default_idempotency_key,
    ):
        self.browser_factory = browser_factory
        self.material_loader = material_loader
        self.credential_store = credential_store or FixtureCredentialStore()
        self.password_factory = password_factory
        self.idempotency_key_factory = idempotency_key_factory

    @classmethod
    def _login_deadline_for(cls, browser):
        deadline = getattr(browser, cls._LOGIN_DEADLINE_ATTRIBUTE, None)
        if deadline is None:
            deadline = browser.new_deadline(LOGIN_DEADLINE_SECONDS)
            setattr(browser, cls._LOGIN_DEADLINE_ATTRIBUTE, deadline)
        return deadline

    def _administrator_with_deadline(self):
        material = _mapping(self.material_loader(), "ADMINISTRATOR_MATERIAL")
        email = _text(material.get("administratorEmail"), "ADMINISTRATOR_EMAIL")
        password = _text(
            material.get("administratorPassword"), "ADMINISTRATOR_PASSWORD"
        )
        browser = self.browser_factory()
        deadline = self._login_deadline_for(browser)
        try:
            token = _decode_token(
                browser.request_rate_limited_login(
                    email,
                    password,
                    max_retry_after_seconds=LOGIN_MAX_RETRY_AFTER_SECONDS,
                    deadline=deadline,
                    status=200,
                )
            )
            browser.access = token["accessToken"]
            browser.refresh_token = token["refreshToken"]
            if token["passwordChangeRequired"] or token[
                "bootstrapAdministratorRequired"
            ]:
                raise SmokeFailure("ADMINISTRATOR_NOT_READY")
        except BaseException:
            self.logout(browser)
            raise
        return browser, deadline

    def administrator(self):
        return self._administrator_with_deadline()[0]

    @staticmethod
    def logout(browser):
        refresh = getattr(browser, "refresh_token", None)
        try:
            if refresh:
                browser.request(
                    "POST",
                    "/auth/v1/logout",
                    {"refreshToken": refresh},
                    status=(200, 204),
                )
        finally:
            browser.access = None
            browser.refresh_token = None

    def logout_actors(self, actors):
        if actors is None:
            return
        try:
            self.logout(actors.authorized.browser)
        finally:
            self.logout(actors.denied.browser)

    @staticmethod
    def _permissions_for_stage(stage, authorized):
        if stage not in LEGAL_STAGES:
            raise SmokeFailure("PERSISTED_STAGE_INVALID")
        if not authorized:
            return (frozenset({OPERATIONS_SYSTEM_READ}),)
        legacy = frozenset({OPERATIONS_SYSTEM_READ})
        if stage in {"ALL_ROUTES_ACTIVE", "RESOURCES_EXPOSED"}:
            return legacy, frozenset(AUTHORIZED_PERMISSION_IDS)
        return (legacy,)

    def _roles(self, admin):
        return _page_items(admin, "/iam/v1/roles", "roleId", _decode_role_summary)

    def _find_role(self, admin, role_code, required=True):
        matches = [role for role in self._roles(admin) if role["roleCode"] == role_code]
        if len(matches) > 1:
            raise SmokeFailure("FIXTURE_ROLE_CODE_NOT_UNIQUE")
        if not matches:
            if required:
                raise SmokeFailure("FIXTURE_ROLE_MISSING")
            return None
        summary = matches[0]
        detail = _decode_role_detail(
            admin.request("GET", "/iam/v1/roles/" + summary["roleId"])
        )
        if (
            detail["roleId"] != summary["roleId"]
            or detail["roleCode"] != role_code
            or detail["systemRole"]
            or detail["version"] != summary["version"]
        ):
            raise SmokeFailure("FIXTURE_ROLE_IDENTITY_INVALID")
        return detail

    @staticmethod
    def _validate_role_permissions(role, stage, authorized):
        allowed = AuthorizationFixtureManager._permissions_for_stage(
            stage, authorized
        )
        actual = frozenset(role["permissionIds"])
        if actual not in allowed:
            raise SmokeFailure("FIXTURE_ROLE_PERMISSIONS_INVALID")

    def _try_login(self, email, password, login_deadline=None):
        browser = self.browser_factory()
        deadline = (
            browser.new_deadline(LOGIN_DEADLINE_SECONDS)
            if login_deadline is None
            else login_deadline
        )
        result = browser.request_rate_limited_login(
            email,
            password,
            max_retry_after_seconds=LOGIN_MAX_RETRY_AFTER_SECONDS,
            deadline=deadline,
            status=(200, 401),
        )
        if isinstance(result, Mapping) and result.get("code") is not None:
            _problem(result, "OPERATIONS_AUTHENTICATION_FAILED", 401)
            return None
        token = _decode_token(result)
        browser.access = token["accessToken"]
        browser.refresh_token = token["refreshToken"]
        return browser, token

    def _login_required(self, email, password, login_deadline=None):
        result = self._try_login(email, password, login_deadline)
        if result is None:
            raise SmokeFailure("FIXTURE_LOGIN_FAILED")
        browser, token = result
        if token["passwordChangeRequired"] or token["bootstrapAdministratorRequired"]:
            self.logout(browser)
            raise SmokeFailure("FIXTURE_LOGIN_NOT_READY")
        return browser

    def _find_account(self, admin, account_id):
        rows = _page_items(admin, "/iam/v1/accounts", "accountId", _decode_account)
        matches = [row for row in rows if row["accountId"] == account_id]
        if len(matches) != 1:
            raise SmokeFailure("FIXTURE_ACCOUNT_ID_NOT_UNIQUE")
        return matches[0]

    def _identity(self, admin, role, email, password, login_deadline):
        browser = self._login_required(email, password, login_deadline)
        try:
            context = _decode_context(browser.request("GET", "/auth/v1/context"))
            account = self._find_account(admin, context["accountId"])
            if (
                context["accountId"] != account["accountId"]
                or context["memberId"] != account["memberId"]
                or context["accountStatus"] != "ACTIVE"
                or context["passwordChangeRequired"]
                or context["bootstrapAdministratorRequired"]
                or account["status"] != "ACTIVE"
                or account["passwordChangeRequired"]
                or len(account["roles"]) != 1
                or account["roles"][0]["roleId"] != role["roleId"]
                or account["roles"][0]["roleCode"] != role["roleCode"]
            ):
                raise SmokeFailure("FIXTURE_ACCOUNT_RELATIONSHIP_INVALID")
        except BaseException:
            self.logout(browser)
            raise
        return FixtureIdentity(browser, role, account, context)

    def discover(self, admin, stage, login_deadline=None):
        deadline = (
            self._login_deadline_for(admin)
            if login_deadline is None
            else login_deadline
        )
        credentials = self.credential_store.read()
        authorized_definition, denied_definition = _fixture_definitions(credentials)
        authorized_role = self._find_role(
            admin, authorized_definition.role_code
        )
        denied_role = self._find_role(admin, denied_definition.role_code)
        self._validate_role_permissions(authorized_role, stage, True)
        self._validate_role_permissions(denied_role, stage, False)
        authorized = self._identity(
            admin,
            authorized_role,
            authorized_definition.email,
            authorized_definition.password,
            deadline,
        )
        try:
            denied = self._identity(
                admin,
                denied_role,
                denied_definition.email,
                denied_definition.password,
                deadline,
            )
        except BaseException:
            self.logout(authorized.browser)
            raise
        return FixtureActors(authorized, denied)

    def _create_role(self, admin, code, name):
        response = admin.request(
            "POST",
            "/iam/v1/roles",
            {
                "roleCode": code,
                "roleName": name,
                "permissionIds": [OPERATIONS_SYSTEM_READ],
                "reason": "Risk rule configuration synthetic smoke fixture",
            },
            status=200,
            extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
        )
        role = _mapping(response, "ROLE_CREATE")
        if frozenset(role) != frozenset(
            {"roleId", "roleCode", "roleName", "systemRole", "version", "permissionIds"}
        ):
            raise SmokeFailure("ROLE_CREATE_SHAPE_INVALID")
        return self._find_role(admin, code)

    def _login_or_create_account(
        self,
        admin,
        role,
        email,
        initial_password,
        final_password,
        login_deadline,
    ):
        final_login = self._try_login(email, final_password, login_deadline)
        if final_login is not None:
            if final_login[1]["passwordChangeRequired"]:
                self.logout(final_login[0])
                raise SmokeFailure("FIXTURE_FINAL_PASSWORD_REQUIRES_CHANGE")
            self.logout(final_login[0])
            return
        initial_login = self._try_login(email, initial_password, login_deadline)
        if initial_login is None:
            admin.request(
                "POST",
                "/iam/v1/accounts",
                {
                    "email": email,
                    "initialPassword": initial_password,
                    "roleIds": [role["roleId"]],
                    "reason": "Risk rule configuration synthetic smoke fixture",
                },
                status=200,
                extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
            )
            initial_login = self._try_login(
                email, initial_password, login_deadline
            )
            if initial_login is None:
                raise SmokeFailure("FIXTURE_ACCOUNT_CREATE_NOT_SETTLED")
        browser, token = initial_login
        if not token["passwordChangeRequired"]:
            self.logout(browser)
            raise SmokeFailure("FIXTURE_INITIAL_PASSWORD_STATE_INVALID")
        try:
            browser.change_password(initial_password, final_password)
        finally:
            if getattr(browser, "refresh_token", None):
                self.logout(browser)
        verified = self._login_required(email, final_password, login_deadline)
        self.logout(verified)

    def prepare(self, stage, admin=None):
        if stage not in LEGAL_STAGES or stage == "LEGACY":
            raise SmokeFailure("AUTHORIZATION_PREPARE_STAGE_INVALID")
        if admin is None:
            administrator, login_deadline = self._administrator_with_deadline()
        else:
            administrator = admin
            login_deadline = self._login_deadline_for(admin)
        active_fixture_roles = [
            role
            for role in self._roles(administrator)
            if _fixture_role_code_matches(
                role["roleCode"], AUTHORIZED_ROLE_CODE
            )
            or _fixture_role_code_matches(
                role["roleCode"], DENIED_ROLE_CODE
            )
        ]
        try:
            credentials = self.credential_store.read()
        except SmokeFailure as error:
            if str(error) not in {
                "FIXTURE_CREDENTIAL_DIRECTORY_MISSING",
                "FIXTURE_CREDENTIAL_FILE_UNAVAILABLE",
            }:
                raise
            if active_fixture_roles:
                raise SmokeFailure(
                    "FIXTURE_CREDENTIALS_REQUIRED_FOR_PREPARE"
                ) from None
            credentials = self.credential_store.ensure(self.password_factory)
        else:
            if "fixtureGeneration" not in credentials and not active_fixture_roles:
                credentials = self.credential_store.ensure(self.password_factory)
        authorized_definition, denied_definition = _fixture_definitions(credentials)
        expected_role_codes = {
            authorized_definition.role_code,
            denied_definition.role_code,
        }
        if {
            role["roleCode"] for role in active_fixture_roles
        } - expected_role_codes:
            raise SmokeFailure("FIXTURE_GENERATION_AMBIGUOUS")
        authorized_role = self._find_role(
            administrator, authorized_definition.role_code, required=False
        )
        if authorized_role is None:
            authorized_role = self._create_role(
                administrator,
                authorized_definition.role_code,
                authorized_definition.role_name,
            )
        self._validate_role_permissions(authorized_role, stage, True)
        denied_role = self._find_role(
            administrator, denied_definition.role_code, required=False
        )
        if denied_role is None:
            denied_role = self._create_role(
                administrator,
                denied_definition.role_code,
                denied_definition.role_name,
            )
        self._validate_role_permissions(denied_role, stage, False)
        self._login_or_create_account(
            administrator,
            authorized_role,
            authorized_definition.email,
            authorized_definition.initial_password,
            authorized_definition.password,
            login_deadline,
        )
        self._login_or_create_account(
            administrator,
            denied_role,
            denied_definition.email,
            denied_definition.initial_password,
            denied_definition.password,
            login_deadline,
        )
        return self.discover(administrator, stage, login_deadline)

    def grant(self, stage, admin=None):
        if stage not in {"ALL_ROUTES_ACTIVE", "RESOURCES_EXPOSED"}:
            raise SmokeFailure("AUTHORIZATION_GRANT_STAGE_INVALID")
        if admin is None:
            administrator, login_deadline = self._administrator_with_deadline()
        else:
            administrator = admin
            login_deadline = self._login_deadline_for(admin)
        actors = self.discover(administrator, stage, login_deadline)
        role = actors.authorized.role
        self.logout_actors(actors)
        if frozenset(role["permissionIds"]) == frozenset({OPERATIONS_SYSTEM_READ}):
            self.replace_authorized_permissions(
                administrator, role, AUTHORIZED_PERMISSION_IDS
            )
        return self.discover(administrator, stage, login_deadline)

    def replace_authorized_permissions(self, administrator, role, permissions):
        target = tuple(permissions)
        permitted_targets = {
            frozenset(AUTHORIZED_PERMISSION_IDS),
            frozenset(AUTHORIZED_PERMISSION_IDS)
            - frozenset({RISK_PERMISSION_IDS[0]}),
        }
        if (
            not isinstance(role, Mapping)
            or not _fixture_role_code_matches(
                role.get("roleCode"), AUTHORIZED_ROLE_CODE
            )
            or role.get("systemRole") is not False
            or frozenset(target) not in permitted_targets
            or len(target) != len(set(target))
        ):
            raise SmokeFailure("AUTHORIZATION_UPDATE_SCOPE_INVALID")
        body = {
            "roleName": role["roleName"],
            "permissionIds": list(target),
            "expectedVersion": role["version"],
            "reason": "Risk rule configuration synthetic smoke authorization proof",
        }
        encoded = _compact_json(body)
        key = self.idempotency_key_factory()
        if not isinstance(key, str) or not key:
            raise SmokeFailure("IDEMPOTENCY_KEY_INVALID")
        response = None
        for attempt in range(2):
            try:
                response = administrator.request_raw(
                    "PUT",
                    "/iam/v1/roles/" + role["roleId"],
                    encoded,
                    status=200,
                    extra_headers={"Idempotency-Key": key},
                )
                break
            except (TimeoutError, ConnectionError, OSError, http.client.HTTPException):
                if attempt == 1:
                    raise SmokeFailure(
                        "AUTHORIZATION_UPDATE_UNKNOWN_REQUIRES_RECONCILIATION"
                    ) from None
        if not isinstance(response, Mapping):
            raise SmokeFailure("AUTHORIZATION_UPDATE_RESPONSE_INVALID")
        updated = self._find_role(administrator, role["roleCode"])
        if (
            updated["roleId"] != role["roleId"]
            or updated["version"] != role["version"] + 1
            or frozenset(updated["permissionIds"]) != frozenset(target)
        ):
            raise SmokeFailure("AUTHORIZATION_UPDATE_POST_READ_INVALID")
        return updated

    def cleanup(self, admin=None):
        if admin is None:
            administrator, login_deadline = self._administrator_with_deadline()
        else:
            administrator = admin
            login_deadline = self._login_deadline_for(admin)
        selected_roles = [
            role
            for role in self._roles(administrator)
            if _fixture_role_code_matches(
                role["roleCode"], AUTHORIZED_ROLE_CODE
            )
            or _fixture_role_code_matches(
                role["roleCode"], DENIED_ROLE_CODE
            )
        ]
        try:
            credentials = self.credential_store.read()
        except SmokeFailure as error:
            if str(error) == "FIXTURE_CREDENTIAL_FILE_UNAVAILABLE" and not selected_roles:
                return
            raise SmokeFailure("FIXTURE_CREDENTIALS_REQUIRED_FOR_CLEANUP") from None

        definitions = _fixture_definitions(credentials)
        expected_role_codes = {
            definition.role_code for definition in definitions
        }
        unexpected_role_codes = {
            role["roleCode"] for role in selected_roles
        } - expected_role_codes
        if unexpected_role_codes:
            raise SmokeFailure("FIXTURE_GENERATION_AMBIGUOUS")
        if any(
            sum(role["roleCode"] == code for role in selected_roles) > 1
            for code in expected_role_codes
        ):
            raise SmokeFailure("FIXTURE_ROLE_CODE_NOT_UNIQUE")
        role_summaries = {role["roleCode"]: role for role in selected_roles}

        for definition in definitions:
            role_code = definition.role_code
            if role_code in role_summaries:
                continue
            login = self._try_login(
                definition.email, definition.password, login_deadline
            )
            if login is not None:
                self.logout(login[0])
                raise SmokeFailure("FIXTURE_ACCOUNT_WITHOUT_ROLE")

        for definition in definitions:
            role_code = definition.role_code
            summary = role_summaries.get(role_code)
            if summary is None:
                continue
            role = self._find_role(administrator, role_code)
            login = self._try_login(
                definition.email, definition.password, login_deadline
            )
            if login is None:
                if summary["employeeCount"] != 0:
                    raise SmokeFailure("FIXTURE_ACCOUNT_CANNOT_BE_DISCOVERED")
                continue
            browser, token = login
            try:
                if token["passwordChangeRequired"] or token[
                    "bootstrapAdministratorRequired"
                ]:
                    raise SmokeFailure("FIXTURE_ACCOUNT_NOT_READY_FOR_CLEANUP")
                context = _decode_context(browser.request("GET", "/auth/v1/context"))
                account = self._find_account(administrator, context["accountId"])
                if (
                    account["memberId"] != context["memberId"]
                    or len(account["roles"]) != 1
                    or account["roles"][0]["roleId"] != role["roleId"]
                    or account["roles"][0]["roleCode"] != role_code
                ):
                    raise SmokeFailure("FIXTURE_CLEANUP_RELATIONSHIP_INVALID")
            finally:
                self.logout(browser)
            administrator.request(
                "DELETE",
                "/iam/v1/accounts/" + account["accountId"],
                {
                    "expectedVersion": account["version"],
                    "expectedMemberVersion": account["memberVersion"],
                    "reason": "Risk rule configuration synthetic smoke cleanup",
                },
                status=204,
                extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
            )
            remaining_accounts = _page_items(
                administrator,
                "/iam/v1/accounts",
                "accountId",
                _decode_account,
            )
            if any(
                item["accountId"] == account["accountId"]
                for item in remaining_accounts
            ):
                raise SmokeFailure("FIXTURE_ACCOUNT_DELETE_POST_READ_INVALID")

        for definition in definitions:
            role_code = definition.role_code
            summaries = [
                role
                for role in self._roles(administrator)
                if role["roleCode"] == role_code
            ]
            if len(summaries) > 1:
                raise SmokeFailure("FIXTURE_ROLE_CODE_NOT_UNIQUE")
            if not summaries:
                continue
            summary = summaries[0]
            if summary["employeeCount"] != 0:
                raise SmokeFailure("FIXTURE_ROLE_STILL_ASSIGNED")
            role = self._find_role(administrator, role_code)
            administrator.request(
                "DELETE",
                "/iam/v1/roles/" + role["roleId"],
                {
                    "expectedVersion": role["version"],
                    "reason": "Risk rule configuration synthetic smoke cleanup",
                },
                status=204,
                extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
            )
        remaining_roles = {
            role["roleCode"] for role in self._roles(administrator)
        }
        if remaining_roles.intersection(expected_role_codes):
            raise SmokeFailure("FIXTURE_ROLE_DELETE_POST_READ_INVALID")
        self.credential_store.remove()


class RiskRouteProbe:
    """Behavior probes through Browser; all authority and identity stay external."""

    AUTHORING = "operations-risk-rule-authoring-context"
    RULE_LIST = "operations-risk-rule-set-list"
    RULE_DETAIL = "operations-risk-rule-set-detail"
    CANDIDATE_LIST = "operations-risk-test-candidate-list"
    CANDIDATE_DETAIL = "operations-risk-test-candidate-detail"
    SAVE = "operations-risk-rule-set-save"
    FREEZE = "operations-risk-rule-set-freeze"
    CREATE = "operations-risk-test-candidate-create"
    VALIDATE = "operations-risk-test-candidate-validate"

    def __init__(
        self,
        idempotency_key_factory=_default_idempotency_key,
        rule_set_code_factory=generate_rule_set_code,
    ):
        self.idempotency_key_factory = idempotency_key_factory
        self.rule_set_code_factory = rule_set_code_factory

    @staticmethod
    def _expect_problem(browser, method, path, body, status, code, key=None):
        headers = None if key is None else {"Idempotency-Key": key}
        response = browser.request(
            method,
            path,
            body,
            status=status,
            extra_headers=headers,
        )
        return _problem(response, code, status)

    def _denied(self, browser, method, path, body=None, key=None):
        self._expect_problem(
            browser,
            method,
            path,
            body,
            403,
            "OPERATIONS_PERMISSION_DENIED",
            key,
        )

    @staticmethod
    def _rule_rows(browser):
        return _page_items(
            browser,
            "/decision-rule-sets/v1/rule-sets",
            "ruleSetVersionId",
        )

    @staticmethod
    def _candidate_rows(browser):
        return _page_items(
            browser,
            "/decision-releases/v1/candidates",
            "releaseId",
        )

    @staticmethod
    def _absent_id(rows, field):
        present = set()
        for row in rows:
            identifier = _text(row.get(field), "RISK_LIST_IDENTIFIER")
            if not _POSITIVE_ID_PATTERN.fullmatch(identifier):
                raise SmokeFailure("RISK_LIST_IDENTIFIER_INVALID")
            present.add(identifier)
        for candidate in range(1, 11):
            value = str(candidate)
            if value not in present:
                return value
        raise SmokeFailure("NO_PROVED_ABSENT_IDENTIFIER")

    def _rule_absent_id(self, browser):
        return self._absent_id(self._rule_rows(browser), "ruleSetVersionId")

    def _candidate_absent_id(self, browser):
        return self._absent_id(self._candidate_rows(browser), "releaseId")

    def _probe_authoring(self, authorized, denied):
        path = "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE"
        authorized.request("GET", path)
        self._denied(denied, "GET", path)

    def _probe_rule_list(self, authorized, denied):
        self._rule_rows(authorized)
        self._denied(
            denied,
            "GET",
            "/decision-rule-sets/v1/rule-sets?page=1&size=200",
        )

    def _probe_rule_detail(self, authorized, denied):
        identifier = self._rule_absent_id(authorized)
        path = "/decision-rule-sets/v1/rule-sets/" + identifier
        self._expect_problem(
            authorized,
            "GET",
            path,
            None,
            404,
            "RISK_DECISION_CONFIGURATION_NOT_FOUND",
        )
        self._expect_problem(
            authorized,
            "GET",
            path + "/unmatched",
            None,
            404,
            "GATEWAY_ROUTE_NOT_FOUND",
        )
        self._denied(denied, "GET", path)

    def _probe_candidate_list(self, authorized, denied):
        self._candidate_rows(authorized)
        self._denied(
            denied,
            "GET",
            "/decision-releases/v1/candidates?page=1&size=200",
        )

    def _probe_candidate_detail(self, authorized, denied):
        identifier = self._candidate_absent_id(authorized)
        path = "/decision-releases/v1/candidates/" + identifier
        self._expect_problem(
            authorized,
            "GET",
            path,
            None,
            404,
            "RISK_DECISION_CONFIGURATION_NOT_FOUND",
        )
        self._expect_problem(
            authorized,
            "GET",
            path + "/unmatched",
            None,
            404,
            "GATEWAY_ROUTE_NOT_FOUND",
        )
        self._denied(denied, "GET", path)

    def _probe_save_shape(self, authorized, denied):
        path = "/decision-rule-sets/v1/rule-sets"
        key = self.idempotency_key_factory()
        self._expect_problem(
            authorized, "POST", path, {}, 400, "INVALID_REQUEST", key
        )
        self._denied(denied, "POST", path, {}, key)

    def _probe_freeze_missing(self, authorized, denied):
        identifier = self._rule_absent_id(authorized)
        path = "/decision-rule-sets/v1/rule-sets/{0}/freeze".format(identifier)
        body = {"expectedContentHash": "0" * 64}
        key = self.idempotency_key_factory()
        self._expect_problem(
            authorized,
            "POST",
            path,
            body,
            404,
            "RISK_DECISION_CONFIGURATION_NOT_FOUND",
            key,
        )
        self._denied(denied, "POST", path, body, key)

    def _probe_create_missing(self, authorized, denied):
        identifier = self._rule_absent_id(authorized)
        path = "/decision-releases/v1/candidates"
        body = {
            "ruleSetVersionId": identifier,
            "expectedRuleSetContentHash": "0" * 64,
        }
        key = self.idempotency_key_factory()
        self._expect_problem(
            authorized,
            "POST",
            path,
            body,
            404,
            "RISK_DECISION_CONFIGURATION_NOT_FOUND",
            key,
        )
        self._denied(denied, "POST", path, body, key)

    def _probe_validate_missing(self, authorized, denied):
        identifier = self._candidate_absent_id(authorized)
        path = "/decision-releases/v1/candidates/{0}/validate".format(identifier)
        body = {"expectedVersion": 1}
        key = "risk-validate-v1-{0}-1".format(identifier)
        self._expect_problem(
            authorized,
            "POST",
            path,
            body,
            404,
            "RISK_DECISION_CONFIGURATION_NOT_FOUND",
            key,
        )
        self._denied(denied, "POST", path, body, key)

    def probe_route(self, route_code, authorized, denied):
        actions = {
            self.AUTHORING: self._probe_authoring,
            self.RULE_LIST: self._probe_rule_list,
            self.RULE_DETAIL: self._probe_rule_detail,
            self.CANDIDATE_LIST: self._probe_candidate_list,
            self.CANDIDATE_DETAIL: self._probe_candidate_detail,
            self.SAVE: self._probe_save_shape,
            self.FREEZE: self._probe_freeze_missing,
            self.CREATE: self._probe_create_missing,
            self.VALIDATE: self._probe_validate_missing,
        }
        try:
            action = actions[route_code]
        except KeyError:
            raise SmokeFailure("ROUTE_CODE_INVALID") from None
        action(authorized, denied)

    @staticmethod
    def _exact(value, keys, context):
        result = _mapping(value, context)
        if frozenset(result) != frozenset(keys):
            raise SmokeFailure(context + "_SHAPE_INVALID")
        return result

    @staticmethod
    def _positive_identifier(value, context):
        identifier = _text(value, context)
        if not _POSITIVE_ID_PATTERN.fullmatch(identifier):
            raise SmokeFailure(context + "_INVALID")
        return identifier

    @staticmethod
    def _content_hash(value, context):
        content_hash = _text(value, context)
        if not _HASH_PATTERN.fullmatch(content_hash):
            raise SmokeFailure(context + "_INVALID")
        return content_hash

    @staticmethod
    def _minimal_save_request(rule_set_code):
        if not isinstance(rule_set_code, str) or not RULE_SET_CODE_PATTERN.fullmatch(
            rule_set_code
        ):
            raise SmokeFailure("GENERATED_RULE_SET_CODE_INVALID")
        return _save_document(rule_set_code, ["AE"])

    def _validate_rule_create(self, response, request):
        value = self._exact(
            response,
            {
                "ruleSetVersionId",
                "pointVersionId",
                "ruleSetCode",
                "versionNo",
                "status",
                "contentHash",
                "createdBy",
                "createTime",
            },
            "RULE_SET_CREATE",
        )
        self._positive_identifier(value["ruleSetVersionId"], "RULE_SET_VERSION_ID")
        self._positive_identifier(value["pointVersionId"], "POINT_VERSION_ID")
        if (
            value["ruleSetCode"] != request["ruleSetCode"]
            or type(value["versionNo"]) is not int
            or value["versionNo"] != 1
            or value["status"] != "DRAFT"
        ):
            raise SmokeFailure("RULE_SET_CREATE_COORDINATES_INVALID")
        self._content_hash(value["contentHash"], "RULE_SET_CONTENT_HASH")
        _text(value["createdBy"], "RULE_SET_CREATED_BY")
        _text(value["createTime"], "RULE_SET_CREATE_TIME")
        return dict(value)

    @classmethod
    def _canonical_requested_operands(cls, operands):
        if not isinstance(operands, list):
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        operand_keys = {
            "operandRole",
            "valueSequence",
            "sourceType",
            "fieldCode",
            "valueType",
            "literalValue",
        }
        sequences = {"LEFT": [], "RIGHT": []}
        values = []
        for operand in operands:
            value = cls._exact(
                operand, operand_keys, "RULE_SET_REQUEST_OPERAND"
            )
            role = _text(value["operandRole"], "RULE_SET_REQUEST_OPERAND_ROLE")
            if role not in sequences:
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
            sequence = _positive_int(
                value["valueSequence"], "RULE_SET_REQUEST_OPERAND_SEQUENCE"
            )
            sequences[role].append(sequence)
            values.append(value)
        for role_sequences in sequences.values():
            if sorted(role_sequences) != list(
                range(1, len(role_sequences) + 1)
            ):
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        return sorted(
            values,
            key=lambda value: (
                0 if value["operandRole"] == "LEFT" else 1,
                value["valueSequence"],
            ),
        )

    @classmethod
    def _canonical_requested_nodes(cls, nodes, node_keys):
        if not isinstance(nodes, list) or not nodes:
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        by_key = {}
        children = {}
        roots = []
        for node in nodes:
            value = cls._exact(node, node_keys, "RULE_SET_REQUEST_NODE")
            node_key = _text(value["nodeKey"], "RULE_SET_REQUEST_NODE_KEY")
            if node_key in by_key:
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
            _positive_int(
                value["sequenceNo"], "RULE_SET_REQUEST_NODE_SEQUENCE"
            )
            parent_key = value["parentNodeKey"]
            if parent_key is None:
                roots.append(value)
            else:
                parent_key = _text(
                    parent_key, "RULE_SET_REQUEST_PARENT_NODE_KEY"
                )
                children.setdefault(parent_key, []).append(value)
            by_key[node_key] = value
        if len(roots) != 1 or roots[0]["sequenceNo"] != 1:
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        if any(parent_key not in by_key for parent_key in children):
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        for siblings in children.values():
            siblings.sort(key=lambda value: value["sequenceNo"])
            if [value["sequenceNo"] for value in siblings] != list(
                range(1, len(siblings) + 1)
            ):
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")

        ordered = []
        visiting = set()
        visited = set()

        def append_preorder(node, depth):
            node_key = node["nodeKey"]
            if depth > 12 or node_key in visiting or node_key in visited:
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
            visiting.add(node_key)
            ordered.append(node)
            for child in children.get(node_key, []):
                append_preorder(child, depth + 1)
            visiting.remove(node_key)
            visited.add(node_key)

        append_preorder(roots[0], 1)
        if len(visited) != len(nodes):
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        return ordered

    @classmethod
    def _validate_rekeyed_rules(cls, actual_rules, requested_rules):
        if (
            not isinstance(actual_rules, list)
            or not isinstance(requested_rules, list)
            or len(actual_rules) != len(requested_rules)
        ):
            raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
        rule_keys = {
            "ruleCode",
            "ruleName",
            "priorityNo",
            "enabledFlag",
            "reasonCode",
            "conditionNodes",
            "action",
        }
        node_keys = {
            "nodeKey",
            "parentNodeKey",
            "sequenceNo",
            "nodeType",
            "groupOperator",
            "comparisonOperator",
            "operands",
        }
        canonical_requests = []
        rule_codes = set()
        priorities = set()
        for requested_rule in requested_rules:
            requested = cls._exact(
                requested_rule, rule_keys, "RULE_SET_REQUEST_RULE"
            )
            rule_code = _text(
                requested["ruleCode"], "RULE_SET_REQUEST_RULE_CODE"
            )
            priority = _positive_int(
                requested["priorityNo"], "RULE_SET_REQUEST_RULE_PRIORITY"
            )
            if rule_code in rule_codes or priority in priorities:
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
            rule_codes.add(rule_code)
            priorities.add(priority)
            canonical_requests.append(
                (
                    requested,
                    cls._canonical_requested_nodes(
                        requested["conditionNodes"], node_keys
                    ),
                )
            )
        canonical_requests.sort(
            key=lambda value: (value[0]["priorityNo"], value[0]["ruleCode"])
        )

        actual_node_ids = set()
        for actual_rule, (requested, requested_nodes) in zip(
            actual_rules, canonical_requests
        ):
            actual = cls._exact(actual_rule, rule_keys, "RULE_SET_DETAIL_RULE")
            if any(
                actual[key] != requested[key]
                for key in rule_keys - {"conditionNodes"}
            ):
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
            actual_nodes = actual["conditionNodes"]
            if (
                not isinstance(actual_nodes, list)
                or len(actual_nodes) != len(requested_nodes)
            ):
                raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")

            node_id_by_request_key = {}
            paired_nodes = []
            for actual_node, requested_node in zip(
                actual_nodes, requested_nodes
            ):
                actual_value = cls._exact(
                    actual_node, node_keys, "RULE_SET_DETAIL_NODE"
                )
                request_key = requested_node["nodeKey"]
                node_id = cls._positive_identifier(
                    actual_value["nodeKey"], "RULE_SET_DETAIL_NODE_ID"
                )
                if node_id in actual_node_ids:
                    raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
                node_id_by_request_key[request_key] = node_id
                actual_node_ids.add(node_id)
                if any(
                    actual_value[key] != requested_node[key]
                    for key in node_keys
                    - {"nodeKey", "parentNodeKey", "operands"}
                ):
                    raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
                canonical_operands = cls._canonical_requested_operands(
                    requested_node["operands"]
                )
                actual_operands = actual_value["operands"]
                if (
                    not isinstance(actual_operands, list)
                    or len(actual_operands) != len(canonical_operands)
                ):
                    raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
                for actual_operand, requested_operand in zip(
                    actual_operands, canonical_operands
                ):
                    if cls._exact(
                        actual_operand,
                        requested_operand.keys(),
                        "RULE_SET_DETAIL_OPERAND",
                    ) != requested_operand:
                        raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
                paired_nodes.append((actual_value, requested_node))

            for actual_node, requested_node in paired_nodes:
                requested_parent = requested_node["parentNodeKey"]
                actual_parent = actual_node["parentNodeKey"]
                if requested_parent is None:
                    if actual_parent is not None:
                        raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")
                    continue
                mapped_parent = node_id_by_request_key.get(requested_parent)
                if mapped_parent is None or actual_parent != mapped_parent:
                    raise SmokeFailure("RULE_SET_DETAIL_RULES_INVALID")

    def _validate_rule_detail(self, response, created, request, status):
        value = self._exact(
            response,
            {
                "ruleSetVersionId",
                "pointVersionId",
                "pointCode",
                "ruleSetCode",
                "ruleSetName",
                "versionNo",
                "status",
                "contentHash",
                "createdBy",
                "changeReason",
                "createTime",
                "frozenBy",
                "frozenTime",
                "rules",
            },
            "RULE_SET_DETAIL",
        )
        if (
            value["ruleSetVersionId"] != created["ruleSetVersionId"]
            or value["pointVersionId"] != created["pointVersionId"]
            or value["pointCode"] != request["pointCode"]
            or value["ruleSetCode"] != request["ruleSetCode"]
            or value["ruleSetName"] != request["ruleSetName"]
            or value["versionNo"] != 1
            or type(value["versionNo"]) is not int
            or value["status"] != status
            or value["contentHash"] != created["contentHash"]
            or value["changeReason"] != request["changeReason"]
        ):
            raise SmokeFailure("RULE_SET_DETAIL_COORDINATES_INVALID")
        self._validate_rekeyed_rules(value["rules"], request["rules"])
        if status == "DRAFT":
            if value["frozenBy"] is not None or value["frozenTime"] is not None:
                raise SmokeFailure("RULE_SET_DRAFT_FREEZE_FIELDS_INVALID")
        elif status == "FROZEN":
            _text(value["frozenBy"], "RULE_SET_FROZEN_BY")
            _text(value["frozenTime"], "RULE_SET_FROZEN_TIME")
        else:
            raise SmokeFailure("RULE_SET_EXPECTED_STATUS_INVALID")
        return dict(value)

    def _create_rule(self, browser):
        request = self._minimal_save_request(self.rule_set_code_factory())
        key = self.idempotency_key_factory()
        response = browser.request(
            "POST",
            "/decision-rule-sets/v1/rule-sets",
            request,
            status=200,
            extra_headers={"Idempotency-Key": key},
        )
        created = self._validate_rule_create(response, request)
        detail = browser.request(
            "GET",
            "/decision-rule-sets/v1/rule-sets/" + created["ruleSetVersionId"],
        )
        self._validate_rule_detail(detail, created, request, "DRAFT")
        return request, created

    def _freeze_rule(self, browser):
        request, created = self._create_rule(browser)
        body = {"expectedContentHash": created["contentHash"]}
        response = browser.request(
            "POST",
            "/decision-rule-sets/v1/rule-sets/{0}/freeze".format(
                created["ruleSetVersionId"]
            ),
            body,
            status=200,
            extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
        )
        frozen = self._exact(
            response,
            {
                "ruleSetVersionId",
                "pointVersionId",
                "ruleSetCode",
                "versionNo",
                "status",
                "contentHash",
                "frozenBy",
                "frozenTime",
            },
            "RULE_SET_FREEZE",
        )
        if (
            frozen["ruleSetVersionId"] != created["ruleSetVersionId"]
            or frozen["pointVersionId"] != created["pointVersionId"]
            or frozen["ruleSetCode"] != request["ruleSetCode"]
            or frozen["versionNo"] != 1
            or type(frozen["versionNo"]) is not int
            or frozen["status"] != "FROZEN"
            or frozen["contentHash"] != created["contentHash"]
        ):
            raise SmokeFailure("RULE_SET_FREEZE_COORDINATES_INVALID")
        _text(frozen["frozenBy"], "RULE_SET_FROZEN_BY")
        _text(frozen["frozenTime"], "RULE_SET_FROZEN_TIME")
        detail = browser.request(
            "GET",
            "/decision-rule-sets/v1/rule-sets/" + created["ruleSetVersionId"],
        )
        self._validate_rule_detail(detail, created, request, "FROZEN")
        return request, created

    def _validate_candidate_create(self, response, rule):
        value = self._exact(
            response,
            {
                "releaseId",
                "releaseNo",
                "decisionPointId",
                "pointCode",
                "pointVersionId",
                "ruleSetVersionId",
                "environmentCode",
                "status",
                "version",
                "contentHash",
                "createdBy",
                "createTime",
                "initialStateChange",
            },
            "CANDIDATE_CREATE",
        )
        self._positive_identifier(value["releaseId"], "RELEASE_ID")
        self._positive_identifier(value["decisionPointId"], "DECISION_POINT_ID")
        if (
            type(value["releaseNo"]) is not int
            or value["releaseNo"] <= 0
            or value["pointCode"] != "CHECKOUT_PREPARE"
            or value["pointVersionId"] != rule["pointVersionId"]
            or value["ruleSetVersionId"] != rule["ruleSetVersionId"]
            or value["environmentCode"] != "TEST"
            or value["status"] != "DRAFT"
            or type(value["version"]) is not int
            or value["version"] != 1
        ):
            raise SmokeFailure("CANDIDATE_CREATE_COORDINATES_INVALID")
        self._content_hash(value["contentHash"], "CANDIDATE_CONTENT_HASH")
        _mapping(value["initialStateChange"], "CANDIDATE_INITIAL_STATE")
        return dict(value)

    def _validate_candidate_detail(self, response, candidate, waiting, validated):
        value = self._exact(
            response,
            {
                "releaseId",
                "releaseNo",
                "decisionPointId",
                "pointCode",
                "pointVersionId",
                "ruleSetVersionId",
                "environmentCode",
                "status",
                "version",
                "contentHash",
                "createdBy",
                "createTime",
                "allStaticChecksPassed",
                "waitingForShadow",
                "frozenComposition",
                "stateChanges",
                "staticChecks",
            },
            "CANDIDATE_DETAIL",
        )
        expected_status = "VALIDATING" if validated else "DRAFT"
        expected_version = 2 if validated else 1
        expected_passed = True if validated else None
        if (
            value["releaseId"] != candidate["releaseId"]
            or value["pointVersionId"] != candidate["pointVersionId"]
            or value["ruleSetVersionId"] != candidate["ruleSetVersionId"]
            or value["environmentCode"] != "TEST"
            or value["status"] != expected_status
            or type(value["version"]) is not int
            or value["version"] != expected_version
            or value["contentHash"] != candidate["contentHash"]
            or value["allStaticChecksPassed"] is not expected_passed
            or value["waitingForShadow"] is not waiting
            or not isinstance(value["stateChanges"], list)
            or not isinstance(value["staticChecks"], list)
        ):
            raise SmokeFailure("CANDIDATE_DETAIL_COORDINATES_INVALID")
        _mapping(value["frozenComposition"], "FROZEN_COMPOSITION")
        return dict(value)

    def _create_candidate(self, browser):
        request, rule = self._freeze_rule(browser)
        body = {
            "ruleSetVersionId": rule["ruleSetVersionId"],
            "expectedRuleSetContentHash": rule["contentHash"],
        }
        response = browser.request(
            "POST",
            "/decision-releases/v1/candidates",
            body,
            status=200,
            extra_headers={"Idempotency-Key": self.idempotency_key_factory()},
        )
        candidate = self._validate_candidate_create(response, rule)
        detail = browser.request(
            "GET",
            "/decision-releases/v1/candidates/" + candidate["releaseId"],
        )
        self._validate_candidate_detail(detail, candidate, False, False)
        return request, rule, candidate

    def _validate_result(self, response, candidate):
        value = self._exact(
            response,
            {
                "releaseId",
                "releaseNo",
                "environmentCode",
                "status",
                "version",
                "contentHash",
                "allStaticChecksPassed",
                "checks",
            },
            "CANDIDATE_VALIDATE",
        )
        if (
            value["releaseId"] != candidate["releaseId"]
            or value["releaseNo"] != candidate["releaseNo"]
            or type(value["releaseNo"]) is not int
            or value["environmentCode"] != "TEST"
            or value["status"] != "VALIDATING"
            or type(value["version"]) is not int
            or value["version"] != 2
            or value["contentHash"] != candidate["contentHash"]
            or value["allStaticChecksPassed"] is not True
            or not isinstance(value["checks"], list)
            or len(value["checks"]) != len(VALIDATION_CHECK_CODES)
        ):
            raise SmokeFailure("CANDIDATE_VALIDATE_COORDINATES_INVALID")
        actual_codes = []
        for check in value["checks"]:
            item = self._exact(
                check,
                {
                    "checkCode",
                    "checkStatus",
                    "severity",
                    "errorCode",
                    "errorSummary",
                },
                "VALIDATION_CHECK",
            )
            if item["checkStatus"] != "PASSED":
                raise SmokeFailure("VALIDATION_CHECK_NOT_PASSED")
            actual_codes.append(item["checkCode"])
        if tuple(actual_codes) != VALIDATION_CHECK_CODES:
            raise SmokeFailure("VALIDATION_CHECK_ORDER_INVALID")
        return dict(value)

    def _validate_candidate(self, browser):
        request, rule, candidate = self._create_candidate(browser)
        body = {"expectedVersion": 1}
        key = "risk-validate-v1-{0}-1".format(candidate["releaseId"])
        path = "/decision-releases/v1/candidates/{0}/validate".format(
            candidate["releaseId"]
        )
        first = browser.request(
            "POST",
            path,
            body,
            status=200,
            extra_headers={"Idempotency-Key": key},
        )
        validated = self._validate_result(first, candidate)
        detail = browser.request(
            "GET",
            "/decision-releases/v1/candidates/" + candidate["releaseId"],
        )
        self._validate_candidate_detail(detail, candidate, True, True)
        replay = browser.request(
            "POST",
            path,
            body,
            status=200,
            extra_headers={"Idempotency-Key": key},
        )
        replay_validated = self._validate_result(replay, candidate)
        if replay_validated != validated:
            raise SmokeFailure("CANDIDATE_VALIDATE_REPLAY_CHANGED")
        replay_detail = browser.request(
            "GET",
            "/decision-releases/v1/candidates/" + candidate["releaseId"],
        )
        self._validate_candidate_detail(replay_detail, candidate, True, True)
        return request, rule, candidate, body, key, path

    def probe_route_smoke(self, route_code, authorized, denied):
        if route_code in ROUTE_ORDER[:5]:
            self.probe_route(route_code, authorized, denied)
            return
        if route_code == self.SAVE:
            request, _created = self._create_rule(authorized)
            self._denied(
                denied,
                "POST",
                "/decision-rule-sets/v1/rule-sets",
                request,
                self.idempotency_key_factory(),
            )
            return
        if route_code == self.FREEZE:
            _request, rule = self._freeze_rule(authorized)
            body = {"expectedContentHash": rule["contentHash"]}
            self._denied(
                denied,
                "POST",
                "/decision-rule-sets/v1/rule-sets/{0}/freeze".format(
                    rule["ruleSetVersionId"]
                ),
                body,
                self.idempotency_key_factory(),
            )
            return
        if route_code == self.CREATE:
            _request, rule, _candidate = self._create_candidate(authorized)
            body = {
                "ruleSetVersionId": rule["ruleSetVersionId"],
                "expectedRuleSetContentHash": rule["contentHash"],
            }
            self._denied(
                denied,
                "POST",
                "/decision-releases/v1/candidates",
                body,
                self.idempotency_key_factory(),
            )
            return
        if route_code == self.VALIDATE:
            _request, _rule, _candidate, body, _key, path = (
                self._validate_candidate(authorized)
            )
            self._denied(
                denied,
                "POST",
                path,
                body,
                self.idempotency_key_factory(),
            )
            return
        raise SmokeFailure("ROUTE_CODE_INVALID")

    def probe_full_write_chain(self, authorized, denied):
        request, rule, _candidate, validate_body, _validate_key, validate_path = (
            self._validate_candidate(authorized)
        )
        self._denied(
            denied,
            "POST",
            "/decision-rule-sets/v1/rule-sets",
            request,
            self.idempotency_key_factory(),
        )
        self._denied(
            denied,
            "POST",
            "/decision-rule-sets/v1/rule-sets/{0}/freeze".format(
                rule["ruleSetVersionId"]
            ),
            {"expectedContentHash": rule["contentHash"]},
            self.idempotency_key_factory(),
        )
        self._denied(
            denied,
            "POST",
            "/decision-releases/v1/candidates",
            {
                "ruleSetVersionId": rule["ruleSetVersionId"],
                "expectedRuleSetContentHash": rule["contentHash"],
            },
            self.idempotency_key_factory(),
        )
        self._denied(
            denied,
            "POST",
            validate_path,
            validate_body,
            self.idempotency_key_factory(),
        )

    def probe_read_permission_denied(self, browser):
        self._expect_problem(
            browser,
            "GET",
            "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE",
            None,
            403,
            "OPERATIONS_PERMISSION_DENIED",
        )

    @staticmethod
    def _filtered_rule_rows(browser, rule_set_code):
        rows = _page_items(
            browser,
            "/decision-rule-sets/v1/rule-sets?ruleSetCode="
            + urllib.parse.quote(rule_set_code, safe=""),
            "ruleSetVersionId",
        )
        if any(row.get("ruleSetCode") != rule_set_code for row in rows):
            raise SmokeFailure("RULE_SET_FILTER_RESPONSE_INVALID")
        return rows

    def probe_full_size_boundaries(self, browser):
        accepted_code = self.rule_set_code_factory()
        accepted_request = self._minimal_save_request(accepted_code)
        if self._filtered_rule_rows(browser, accepted_code):
            raise SmokeFailure("BOUNDARY_RULE_SET_CODE_ALREADY_EXISTS")
        accepted_raw = build_save_request_bytes(accepted_code)
        accepted_response = send_raw_save(
            browser,
            accepted_raw,
            self.idempotency_key_factory(),
            expected_status=200,
        )
        created = self._validate_rule_create(accepted_response, accepted_request)
        submitted = json.loads(accepted_raw)
        detail = browser.request(
            "GET",
            "/decision-rule-sets/v1/rule-sets/" + created["ruleSetVersionId"],
        )
        self._validate_rule_detail(detail, created, submitted, "DRAFT")
        after = self._filtered_rule_rows(browser, accepted_code)
        if (
            len(after) != 1
            or after[0].get("ruleSetVersionId") != created["ruleSetVersionId"]
            or after[0].get("pointVersionId") != created["pointVersionId"]
            or after[0].get("contentHash") != created["contentHash"]
            or after[0].get("status") != "DRAFT"
            or after[0].get("versionNo") != 1
        ):
            raise SmokeFailure("BOUNDARY_RULE_SET_POST_LIST_INVALID")

        rejected_code = self.rule_set_code_factory()
        if rejected_code == accepted_code:
            raise SmokeFailure("BOUNDARY_RULE_SET_CODES_NOT_UNIQUE")
        self._minimal_save_request(rejected_code)
        if self._filtered_rule_rows(browser, rejected_code):
            raise SmokeFailure("OVERSIZE_RULE_SET_CODE_ALREADY_EXISTS")
        rejected_raw = build_oversize_save_request_bytes(rejected_code)
        rejected = send_raw_save(
            browser,
            rejected_raw,
            self.idempotency_key_factory(),
            expected_status=413,
        )
        _problem(rejected, "GATEWAY_PAYLOAD_TOO_LARGE", 413)
        if self._filtered_rule_rows(browser, rejected_code):
            raise SmokeFailure("OVERSIZE_RULE_SET_WAS_FORWARDED")

    def probe_read_stage(self, authorized, denied):
        for route_code in ROUTE_ORDER[:5]:
            self.probe_route(route_code, authorized, denied)

    def _probe_inactive(self, route_code, browser):
        if route_code == self.AUTHORING:
            method = "GET"
            path = "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE"
            body = None
            key = None
        elif route_code == self.RULE_LIST:
            method = "GET"
            path = "/decision-rule-sets/v1/rule-sets?page=1&size=200"
            body = None
            key = None
        elif route_code == self.RULE_DETAIL:
            method = "GET"
            path = "/decision-rule-sets/v1/rule-sets/1"
            body = None
            key = None
        elif route_code == self.CANDIDATE_LIST:
            method = "GET"
            path = "/decision-releases/v1/candidates?page=1&size=200"
            body = None
            key = None
        elif route_code == self.CANDIDATE_DETAIL:
            method = "GET"
            path = "/decision-releases/v1/candidates/1"
            body = None
            key = None
        elif route_code == self.SAVE:
            method = "POST"
            path = "/decision-rule-sets/v1/rule-sets"
            body = {}
            key = self.idempotency_key_factory()
        elif route_code == self.FREEZE:
            method = "POST"
            path = "/decision-rule-sets/v1/rule-sets/1/freeze"
            body = {"expectedContentHash": "0" * 64}
            key = self.idempotency_key_factory()
        elif route_code == self.CREATE:
            method = "POST"
            path = "/decision-releases/v1/candidates"
            body = {
                "ruleSetVersionId": "1",
                "expectedRuleSetContentHash": "0" * 64,
            }
            key = self.idempotency_key_factory()
        elif route_code == self.VALIDATE:
            method = "POST"
            path = "/decision-releases/v1/candidates/1/validate"
            body = {"expectedVersion": 1}
            key = "risk-validate-v1-1-1"
        else:
            raise SmokeFailure("ROUTE_CODE_INVALID")
        self._expect_problem(
            browser,
            method,
            path,
            body,
            404,
            "GATEWAY_ROUTE_NOT_FOUND",
            key,
        )

    def probe_runtime_stage(self, runtime_stage, authorized, denied=None):
        if runtime_stage in {"LEGACY", "BASELINE"}:
            active_count = 0
        elif runtime_stage == "RESOURCES_EXPOSED":
            active_count = len(ROUTE_ORDER)
        elif runtime_stage in ROUTE_STAGES:
            active_count = ROUTE_STAGES.index(runtime_stage)
        else:
            raise SmokeFailure("RUNTIME_STAGE_INVALID")
        if active_count and denied is None:
            raise SmokeFailure("DENIED_FIXTURE_REQUIRED")
        for route_code in ROUTE_ORDER[:active_count]:
            self.probe_route(route_code, authorized, denied)
        if active_count < len(ROUTE_ORDER):
            self._probe_inactive(ROUTE_ORDER[active_count], authorized)

class _RedactedArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        self.print_usage(sys.stderr)
        self.exit(2, "{0}: error: invalid arguments\n".format(self.prog))


def build_argument_parser():
    parser = _RedactedArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=ORIGIN)
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--prepare-authorization-fixture", action="store_true")
    actions.add_argument("--grant-authorization-fixture", action="store_true")
    actions.add_argument(
        "--probe-runtime-stage", choices=LEGAL_STAGES, metavar="EXPECTED_RUNTIME_STAGE"
    )
    actions.add_argument("--read-stage", action="store_true")
    actions.add_argument("--route-smoke", choices=ROUTE_ORDER, metavar="ROUTE_CODE")
    actions.add_argument("--full", action="store_true")
    actions.add_argument("--cleanup-authorization-fixture", action="store_true")
    parser.add_argument(
        "--expected-persisted-stage",
        choices=LEGAL_STAGES,
        metavar="EXPECTED_PERSISTED_STAGE",
    )
    return parser


class RiskRuleConfigurationSmoke:
    """Compose authoritative stage, strict IAM and behavior-probe adapters."""

    def __init__(
        self,
        base_url=ORIGIN,
        authorization=None,
        stage_observer=None,
        route_probe=None,
        nacos_probe=_default_nacos_probe,
    ):
        if authorization is None:
            Browser(base_url)
            authorization = AuthorizationFixtureManager(
                browser_factory=lambda: Browser(base_url)
            )
        self.authorization = authorization
        self.stage_observer = stage_observer or (
            lambda browser: RouteRollout(browser).inspect_stage()
        )
        self.route_probe = route_probe or RiskRouteProbe()
        self.nacos_probe = nacos_probe

    def _observe(self, administrator):
        observation = self.stage_observer(administrator)
        stage = getattr(observation, "stage", None)
        persisted = getattr(observation, "persisted", None)
        if stage not in LEGAL_STAGES or not isinstance(persisted, Mapping):
            raise SmokeFailure("ROLLOUT_OBSERVATION_INVALID")
        resources = persisted.get("resources")
        if not isinstance(resources, Mapping):
            raise SmokeFailure("PERSISTED_RESOURCE_AUTHORITY_INVALID")
        return observation

    def _close_sessions(self, administrator, *actor_groups):
        browsers = []
        for actors in actor_groups:
            if actors is None:
                continue
            for name in ("authorized", "denied"):
                identity = getattr(actors, name, None)
                browser = getattr(identity, "browser", None)
                if browser is not None:
                    browsers.append(browser)
        browsers.append(administrator)
        failure = None
        seen = set()
        for browser in browsers:
            if id(browser) in seen:
                continue
            seen.add(id(browser))
            try:
                self.authorization.logout(browser)
            except BaseException as error:
                if failure is None:
                    failure = error
        if failure is not None:
            raise failure

    @staticmethod
    def _risk_resources_hidden(observation):
        resources = observation.persisted["resources"]
        return not frozenset(resources).intersection(RISK_RESOURCE_CODES)

    def prepare_authorization_fixture(self):
        administrator = self.authorization.administrator()
        actors = None
        try:
            observation = self._observe(administrator)
            actors = self.authorization.prepare(observation.stage, administrator)
        finally:
            self._close_sessions(administrator, actors)

    def grant_authorization_fixture(self):
        administrator = self.authorization.administrator()
        actors = None
        try:
            observation = self._observe(administrator)
            actors = self.authorization.grant(observation.stage, administrator)
            self.route_probe.probe_route(
                RiskRouteProbe.AUTHORING,
                actors.authorized.browser,
                actors.denied.browser,
            )
        finally:
            self._close_sessions(administrator, actors)

    def probe_runtime_stage(self, runtime_stage, persisted_stage):
        administrator = self.authorization.administrator()
        actors = None
        try:
            observation = self._observe(administrator)
            if observation.stage != persisted_stage:
                raise SmokeFailure("PERSISTED_STAGE_MISMATCH")
            if runtime_stage != persisted_stage:
                if (
                    runtime_stage not in ROUTE_STAGES
                    or persisted_stage not in ROUTE_STAGES
                    or ROUTE_STAGES.index(persisted_stage)
                    != ROUTE_STAGES.index(runtime_stage) + 1
                    or not self._risk_resources_hidden(observation)
                ):
                    raise SmokeFailure("RUNTIME_PERSISTED_STAGE_PAIR_INVALID")
            if runtime_stage in {"LEGACY", "BASELINE"}:
                authorized = administrator
                denied = None
            else:
                actors = self.authorization.discover(
                    administrator, observation.stage
                )
                authorized = administrator
                denied = actors.denied.browser
                if runtime_stage == "RESOURCES_EXPOSED":
                    authorized = actors.authorized.browser
                    verify_authorized_risk_navigation(authorized)
            self.route_probe.probe_runtime_stage(
                runtime_stage, authorized, denied
            )
        finally:
            self._close_sessions(administrator, actors)

    def read_stage(self):
        administrator = self.authorization.administrator()
        actors = None
        try:
            observation = self._observe(administrator)
            if observation.stage == "RESOURCES_EXPOSED":
                allowed = True
            elif observation.stage in ROUTE_STAGES:
                allowed = ROUTE_STAGES.index(
                    observation.stage
                ) >= ROUTE_STAGES.index("READ_ACTIVE")
            else:
                allowed = False
            if not allowed:
                raise SmokeFailure("READ_STAGE_PREREQUISITE_NOT_MET")
            actors = self.authorization.discover(
                administrator, observation.stage
            )
            self.route_probe.probe_read_stage(
                administrator, actors.denied.browser
            )
        finally:
            self._close_sessions(administrator, actors)

    def route_smoke(self, route_code):
        try:
            _prior, required_stage = stage_pair_for_route(route_code)
        except ValueError:
            raise SmokeFailure("ROUTE_CODE_INVALID") from None
        administrator = self.authorization.administrator()
        actors = None
        try:
            observation = self._observe(administrator)
            if observation.stage != required_stage:
                raise SmokeFailure("ROUTE_SMOKE_STAGE_MISMATCH")
            actors = self.authorization.discover(
                administrator, observation.stage
            )
            self.route_probe.probe_route_smoke(
                route_code, administrator, actors.denied.browser
            )
        finally:
            self._close_sessions(administrator, actors)

    def full(self):
        administrator = self.authorization.administrator()
        actors = None
        restored_actors = None
        try:
            observation = self._observe(administrator)
            if observation.stage != "RESOURCES_EXPOSED":
                raise SmokeFailure("FULL_REQUIRES_RESOURCES_EXPOSED")
            navigation = getattr(observation, "navigation", None)
            if not isinstance(navigation, Mapping) or any(
                navigation.get(code) != label
                for code, label in RISK_ACTIVE_RESOURCES.items()
            ):
                raise SmokeFailure("FULL_RISK_NAVIGATION_INVALID")
            actors = self.authorization.discover(
                administrator, observation.stage
            )
            if (
                frozenset(actors.authorized.role["permissionIds"])
                != frozenset(AUTHORIZED_PERMISSION_IDS)
                or frozenset(actors.denied.role["permissionIds"])
                != frozenset({OPERATIONS_SYSTEM_READ})
            ):
                raise SmokeFailure("FULL_AUTHORIZATION_FIXTURE_INVALID")

            verify_authorized_risk_navigation(actors.authorized.browser)
            self.route_probe.probe_read_stage(
                actors.authorized.browser, actors.denied.browser
            )
            without_read = tuple(
                permission
                for permission in AUTHORIZED_PERMISSION_IDS
                if permission != RISK_PERMISSION_IDS[0]
            )
            revoked = self.authorization.replace_authorized_permissions(
                administrator, actors.authorized.role, without_read
            )
            try:
                self.route_probe.probe_read_permission_denied(
                    actors.authorized.browser
                )
            finally:
                self.authorization.replace_authorized_permissions(
                    administrator, revoked, AUTHORIZED_PERMISSION_IDS
                )

            restored_actors = self.authorization.discover(
                administrator, observation.stage
            )
            if frozenset(
                restored_actors.authorized.role["permissionIds"]
            ) != frozenset(AUTHORIZED_PERMISSION_IDS):
                raise SmokeFailure("FULL_AUTHORIZATION_RESTORE_INVALID")
            self.route_probe.probe_route(
                RiskRouteProbe.AUTHORING,
                restored_actors.authorized.browser,
                restored_actors.denied.browser,
            )
            self.route_probe.probe_full_write_chain(
                restored_actors.authorized.browser,
                restored_actors.denied.browser,
            )
            self.route_probe.probe_full_size_boundaries(
                restored_actors.authorized.browser
            )
            verify_nacos_instances(
                self.nacos_probe("mor-test", "DEFAULT_GROUP", "risk-service")
            )
        finally:
            self._close_sessions(administrator, actors, restored_actors)

    def cleanup_authorization_fixture(self):
        administrator = self.authorization.administrator()
        try:
            self.authorization.cleanup(administrator)
        finally:
            self._close_sessions(administrator)


def _action(options):
    if options.prepare_authorization_fixture:
        return "prepare_authorization_fixture", (), "PREPARE_AUTHORIZATION_FIXTURE_OK"
    if options.grant_authorization_fixture:
        return "grant_authorization_fixture", (), "GRANT_AUTHORIZATION_FIXTURE_OK"
    if options.probe_runtime_stage is not None:
        persisted = options.expected_persisted_stage or options.probe_runtime_stage
        return (
            "probe_runtime_stage",
            (options.probe_runtime_stage, persisted),
            "PROBE_RUNTIME_STAGE_OK",
        )
    if options.read_stage:
        return "read_stage", (), "READ_STAGE_OK"
    if options.route_smoke is not None:
        return "route_smoke", (options.route_smoke,), "ROUTE_SMOKE_OK"
    if options.full:
        return "full", (), "FULL_OK"
    if options.cleanup_authorization_fixture:
        return "cleanup_authorization_fixture", (), "CLEANUP_AUTHORIZATION_FIXTURE_OK"
    return None


def main(argv=None, runner_factory=None, stdout=None, stderr=None):
    output = stdout if stdout is not None else sys.stdout
    error = stderr if stderr is not None else sys.stderr
    parser = build_argument_parser()
    with contextlib.redirect_stderr(error):
        options = parser.parse_args(argv)
        selected = _action(options)
        if (
            options.expected_persisted_stage is not None
            and options.probe_runtime_stage is None
        ):
            parser.error("--expected-persisted-stage requires --probe-runtime-stage")
    if selected is None:
        return 0

    try:
        method_name, arguments, summary = selected
        if runner_factory is None:
            runner = RiskRuleConfigurationSmoke(options.base_url)
        else:
            runner = runner_factory()
        getattr(runner, method_name)(*arguments)
    except (Exception, SystemExit):
        error.write("RISK_RULE_CONFIGURATION_SMOKE_FAILED\n")
        return 1
    output.write(summary + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
