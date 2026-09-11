"""Pure, fail-closed classifier for Risk Operations rollout stages."""

from collections.abc import Mapping
from types import MappingProxyType


LEGACY_ACTIVE_RESOURCES = MappingProxyType(
    {
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
)

RISK_ACTIVE_RESOURCES = MappingProxyType(
    {
        "OPERATIONS_RISK_MANAGEMENT": "风控管理",
        "OPERATIONS_RISK_RULE_CONFIGURATION": "规则配置",
        "OPERATIONS_RISK_RULE_SET_SAVE": "保存为新版本",
        "OPERATIONS_RISK_RULE_SET_FREEZE": "冻结版本",
        "OPERATIONS_RISK_TEST_CANDIDATE_CREATE": "创建 TEST Candidate",
        "OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE": "执行静态校验",
    }
)

LEGACY_ROUTE_CODES = frozenset(
    {
        "operations-auth-login",
        "operations-auth-refresh",
        "operations-auth-logout",
        "operations-auth-context",
        "operations-auth-password",
        "operations-bootstrap-administrator",
        "operations-navigation",
        "operations-account-list",
        "operations-account-create",
        "operations-account-disable",
        "operations-account-roles",
        "operations-role-list",
        "operations-audit-list",
        "operations-role-permission-options",
        "operations-role-detail",
        "operations-role-create",
        "operations-role-update",
        "operations-role-delete",
        "operations-account-delete",
        "operations-account-enable",
        "operations-audit-detail",
        "operations-gateway-target-list",
        "operations-gateway-target-detail",
        "operations-gateway-target-status",
        "operations-gateway-route-list",
        "operations-gateway-route-detail",
        "operations-gateway-route-status",
    }
)

RISK_ROUTE_ID_TO_CODE = MappingProxyType(
    {
        1024: "operations-risk-rule-authoring-context",
        1025: "operations-risk-rule-set-list",
        1026: "operations-risk-rule-set-detail",
        1027: "operations-risk-rule-set-save",
        1028: "operations-risk-rule-set-freeze",
        1029: "operations-risk-test-candidate-list",
        1030: "operations-risk-test-candidate-detail",
        1031: "operations-risk-test-candidate-create",
        1032: "operations-risk-test-candidate-validate",
    }
)

RISK_ROUTE_ACTIVATION_IDS = (
    1024,
    1025,
    1026,
    1029,
    1030,
    1027,
    1028,
    1031,
    1032,
)
RISK_ROUTE_ACTIVATION_ORDER = tuple(
    RISK_ROUTE_ID_TO_CODE[route_id] for route_id in RISK_ROUTE_ACTIVATION_IDS
)

ROUTE_PREFIX_STAGES = (
    "BASELINE",
    "AUTHORING_READ_ACTIVE",
    "RULE_SET_LIST_READ_ACTIVE",
    "RULE_SET_DETAIL_READ_ACTIVE",
    "CANDIDATE_LIST_READ_ACTIVE",
    "READ_ACTIVE",
    "SAVE_ACTIVE",
    "FREEZE_ACTIVE",
    "CREATE_ACTIVE",
    "ALL_ROUTES_ACTIVE",
)

ALL_ROUTE_CODES = LEGACY_ROUTE_CODES | frozenset(RISK_ROUTE_ACTIVATION_ORDER)
ALL_ACTIVE_RESOURCES = MappingProxyType(
    {**dict(LEGACY_ACTIVE_RESOURCES), **dict(RISK_ACTIVE_RESOURCES)}
)
LEGAL_STAGES = frozenset(("LEGACY", *ROUTE_PREFIX_STAGES, "RESOURCES_EXPOSED"))


def _mapping_copy(value, name):
    if not isinstance(value, Mapping):
        raise ValueError(f"{name} must be a mapping")
    result = dict(value)
    if any(
        not isinstance(key, str) or not isinstance(item, str)
        for key, item in result.items()
    ):
        raise ValueError(f"{name} must contain string keys and values")
    return result


def classify_route_stage(route_code_to_status):
    """Classify an exact persisted Operations Route status projection."""

    routes = _mapping_copy(route_code_to_status, "routeCodeToStatus")
    route_codes = frozenset(routes)

    if route_codes == LEGACY_ROUTE_CODES:
        if any(status != "ACTIVE" for status in routes.values()):
            raise ValueError("every legacy Operations Route must be ACTIVE")
        return "LEGACY"

    if route_codes != ALL_ROUTE_CODES:
        raise ValueError("Operations Route catalog is missing, unknown, or partial")
    if any(routes[code] != "ACTIVE" for code in LEGACY_ROUTE_CODES):
        raise ValueError("every legacy Operations Route must remain ACTIVE")

    statuses = [routes[code] for code in RISK_ROUTE_ACTIVATION_ORDER]
    if any(status not in {"ACTIVE", "DISABLED"} for status in statuses):
        raise ValueError("Risk Route status must be exactly ACTIVE or DISABLED")

    active_prefix = 0
    while active_prefix < len(statuses) and statuses[active_prefix] == "ACTIVE":
        active_prefix += 1
    if any(status != "DISABLED" for status in statuses[active_prefix:]):
        raise ValueError("Risk Routes must form the approved activation prefix")
    return ROUTE_PREFIX_STAGES[active_prefix]


def classify_stage(active_resource_code_to_label, route_code_to_status):
    """Classify persisted ACTIVE Resources and all Routes.

    The Resource projection must come from verified persisted rows. An actor's
    permission-filtered Navigation response is not Resource-status authority.
    """

    resources = _mapping_copy(
        active_resource_code_to_label, "activeResourceCodeToLabel"
    )
    route_stage = classify_route_stage(route_code_to_status)

    if resources == dict(LEGACY_ACTIVE_RESOURCES):
        return route_stage
    if resources == dict(ALL_ACTIVE_RESOURCES):
        if route_stage != "ALL_ROUTES_ACTIVE":
            raise ValueError("Risk Resources require every Risk Route to be ACTIVE")
        return "RESOURCES_EXPOSED"
    raise ValueError(
        "ACTIVE Operations Resource projection is missing, unknown, or partial"
    )
