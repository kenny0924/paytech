"""Closed-stage contract for the Risk Operations route rollout."""

import importlib.util
from pathlib import Path
import unittest
import contextlib
import io
import json
import os
import tempfile
import fcntl
from unittest import mock
from urllib.parse import parse_qs, urlsplit

from test_risk_rule_configuration_catalog import fixture


STAGE_MODULE = Path(__file__).with_name("risk_rule_configuration_stage.py")

LEGACY_RESOURCES = {
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

RISK_RESOURCES = {
    "OPERATIONS_RISK_MANAGEMENT": "风控管理",
    "OPERATIONS_RISK_RULE_CONFIGURATION": "规则配置",
    "OPERATIONS_RISK_RULE_SET_SAVE": "保存为新版本",
    "OPERATIONS_RISK_RULE_SET_FREEZE": "冻结版本",
    "OPERATIONS_RISK_TEST_CANDIDATE_CREATE": "创建 TEST Candidate",
    "OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE": "执行静态校验",
}

LEGACY_ROUTE_CODES = (
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
)

RISK_ROUTE_ACTIVATION_ORDER = (
    "operations-risk-rule-authoring-context",
    "operations-risk-rule-set-list",
    "operations-risk-rule-set-detail",
    "operations-risk-test-candidate-list",
    "operations-risk-test-candidate-detail",
    "operations-risk-rule-set-save",
    "operations-risk-rule-set-freeze",
    "operations-risk-test-candidate-create",
    "operations-risk-test-candidate-validate",
)

PREFIX_STAGES = (
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


def problem(code, status, detail):
    titles = {
        400: "Bad Request",
        401: "Unauthorized",
        409: "Conflict",
        429: "Too Many Requests",
    }
    return {
        "type": "about:blank",
        "title": titles[status],
        "detail": detail,
        "code": code,
        "requestId": "request-20260908_ABC",
        "timestamp": "2026-09-08T01:02:03.123456Z",
    }


def problem_headers(request_id='request-20260908_ABC', content_type='application/problem+json', retry_after=()):
    return {
        'content-type': (content_type,),
        'x-request-id': (request_id,),
        'date': ('Tue, 08 Sep 2026 01:02:03 GMT',),
        'x-timestamp': (),
        'retry-after': tuple(retry_after),
    }


def load_stage_module():
    if not STAGE_MODULE.is_file():
        raise AssertionError(f"missing stage classifier: {STAGE_MODULE.name}")
    spec = importlib.util.spec_from_file_location(
        "risk_rule_configuration_stage", STAGE_MODULE
    )
    if spec is None or spec.loader is None:
        raise AssertionError("stage classifier could not be loaded")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def legacy_routes():
    return {code: "ACTIVE" for code in LEGACY_ROUTE_CODES}


def prepared_routes(active_prefix):
    result = legacy_routes()
    result.update(
        {
            code: "ACTIVE" if index < active_prefix else "DISABLED"
            for index, code in enumerate(RISK_ROUTE_ACTIVATION_ORDER)
        }
    )
    return result


class RiskRuleConfigurationStageTest(unittest.TestCase):
    def setUp(self):
        self.stage = load_stage_module()

    def test_accepts_exactly_the_twelve_legal_persisted_stages(self):
        legal_cases = [("LEGACY", LEGACY_RESOURCES, legacy_routes())]
        legal_cases.extend(
            (stage_name, LEGACY_RESOURCES, prepared_routes(prefix))
            for prefix, stage_name in enumerate(PREFIX_STAGES)
        )
        legal_cases.append(
            (
                "RESOURCES_EXPOSED",
                {**LEGACY_RESOURCES, **RISK_RESOURCES},
                prepared_routes(9),
            )
        )

        self.assertEqual(12, len(legal_cases))
        for expected, resources, routes in legal_cases:
            with self.subTest(stage=expected):
                self.assertEqual(
                    expected,
                    self.stage.classify_stage(dict(resources), dict(routes)),
                )

    def test_route_only_classifier_uses_the_frozen_non_numeric_activation_order(self):
        self.assertEqual("LEGACY", self.stage.classify_route_stage(legacy_routes()))
        for prefix, expected in enumerate(PREFIX_STAGES):
            with self.subTest(prefix=prefix, stage=expected):
                self.assertEqual(
                    expected,
                    self.stage.classify_route_stage(prepared_routes(prefix)),
                )

    def test_rejects_partial_active_resource_projections(self):
        partial_legacy = dict(LEGACY_RESOURCES)
        partial_legacy.pop("OPERATIONS_ACCOUNT_ENABLE")
        partial_risk = {**LEGACY_RESOURCES, **RISK_RESOURCES}
        partial_risk.pop("OPERATIONS_RISK_RULE_SET_FREEZE")

        for resources, routes in (
            (partial_legacy, prepared_routes(0)),
            (partial_risk, prepared_routes(9)),
            (
                {**LEGACY_RESOURCES, "OPERATIONS_RISK_MANAGEMENT": "风控管理"},
                prepared_routes(9),
            ),
        ):
            with self.subTest(resources=len(resources)):
                with self.assertRaises(ValueError):
                    self.stage.classify_stage(resources, routes)

    def test_rejects_resources_exposed_before_all_routes_are_active(self):
        with self.assertRaises(ValueError):
            self.stage.classify_stage(
                {**LEGACY_RESOURCES, **RISK_RESOURCES}, prepared_routes(8)
            )

    def test_rejects_a_non_prefix_risk_route_state(self):
        routes = prepared_routes(1)
        routes[RISK_ROUTE_ACTIVATION_ORDER[0]] = "DISABLED"
        routes[RISK_ROUTE_ACTIVATION_ORDER[1]] = "ACTIVE"

        with self.assertRaises(ValueError):
            self.stage.classify_route_stage(routes)

    def test_rejects_unknown_and_missing_routes(self):
        unknown = prepared_routes(0)
        unknown["operations-risk-unknown"] = "DISABLED"
        missing_new = prepared_routes(0)
        missing_new.pop(RISK_ROUTE_ACTIVATION_ORDER[-1])
        missing_legacy = legacy_routes()
        missing_legacy.pop(LEGACY_ROUTE_CODES[-1])

        for routes in (unknown, missing_new, missing_legacy):
            with self.subTest(routes=len(routes)):
                with self.assertRaises(ValueError):
                    self.stage.classify_route_stage(routes)

    def test_rejects_status_case_drift_and_any_inactive_legacy_route(self):
        wrong_case = prepared_routes(1)
        wrong_case[RISK_ROUTE_ACTIVATION_ORDER[0]] = "active"
        inactive_legacy = prepared_routes(1)
        inactive_legacy[LEGACY_ROUTE_CODES[0]] = "DISABLED"

        for routes in (wrong_case, inactive_legacy):
            with self.subTest():
                with self.assertRaises(ValueError):
                    self.stage.classify_route_stage(routes)

    def test_rejects_wrong_legacy_resource_codes_or_labels(self):
        wrong_label = dict(LEGACY_RESOURCES)
        wrong_label["OPERATIONS_SYSTEM"] = "System"
        missing = dict(LEGACY_RESOURCES)
        missing.pop("OPERATIONS_AUDIT")
        unknown = dict(LEGACY_RESOURCES)
        unknown["OPERATIONS_UNKNOWN"] = "未知"

        for resources in (wrong_label, missing, unknown):
            with self.subTest(resources=resources):
                with self.assertRaises(ValueError):
                    self.stage.classify_stage(resources, legacy_routes())

    def test_rejects_risk_resources_against_the_legacy_route_catalog(self):
        with self.assertRaises(ValueError):
            self.stage.classify_stage(
                {**LEGACY_RESOURCES, **RISK_RESOURCES}, legacy_routes()
            )

    def test_does_not_mutate_persisted_projection_inputs(self):
        resources = dict(LEGACY_RESOURCES)
        routes = prepared_routes(5)
        resources_before = dict(resources)
        routes_before = dict(routes)

        self.assertEqual("READ_ACTIVE", self.stage.classify_stage(resources, routes))
        self.assertEqual(resources_before, resources)
        self.assertEqual(routes_before, routes)


def load_smoke():
    spec = importlib.util.spec_from_file_location('smoke_under_test', STAGE_MODULE.with_name('smoke.py'))
    module = importlib.util.module_from_spec(spec)
    with mock.patch.object(os, 'umask', side_effect=AssertionError('import changed umask')):
        spec.loader.exec_module(module)
    return module


class FakeBrowser:
    def __init__(self, stage, navigation_stage=None, paging=None):
        self.stage = stage
        self.navigation_stage = navigation_stage or stage
        self.paging = paging
        self.refresh_token = 'fake-refresh'
        self.access = 'fake-access'
        self.calls = []
        self.problem_calls = []
        self.logged_out = False

    def login(self, identifier, password):
        return {'passwordChangeRequired': False, 'bootstrapAdministratorRequired': False}

    def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
        self.calls.append((method, path))
        split = urlsplit(path)
        args = parse_qs(split.query)
        page, size = int(args.get('page', [1])[0]), int(args.get('size', [10])[0])
        if '/auth/v1/context' == path:
            return {'tenantId': 'fake-tenant', 'memberId': 'fake-member'}
        if '/authorization/v1/navigation' == path:
            mapping = {'resource_id': 'resourceId', 'resource_code': 'resourceCode', 'resource_type': 'resourceType',
                       'parent_resource_id': 'parentResourceId', 'display_name': 'displayName',
                       'frontend_route': 'frontendRoute', 'component_key': 'componentKey', 'sort_order': 'sortOrder'}
            return {'resources': [{alias: str(row[key]) if key.endswith('_id') and row[key] is not None else row[key]
                                   for key, alias in mapping.items()}
                                  for row in fixture(self.navigation_stage)['t_console_resource'] if row['status'] == 'ACTIVE']}
        if method == 'POST':
            if path.endswith('/password'):
                return problem(
                    'BUSINESS_CHECK_FAILED', 400, '业务校验未通过')
            if path.endswith('/refresh'):
                if self.logged_out:
                    return problem(
                        'OPERATIONS_SESSION_INVALID', 401, '登录已失效，请重新登录')
                return {'accessToken': 'new-fake-access', 'refreshToken': 'new-fake-refresh', 'refreshExpiresIn': 300}
            if path.endswith('/logout'):
                self.logged_out = True
                return None
            raise AssertionError('unexpected mutation: ' + path)
        if page == 0 or size == 11:
            return problem('INVALID_REQUEST', 400, 'Invalid paging')
        if '/gateway-admin/v1/target-services/' in path:
            return {'targetServiceCode': 'operations-console-bff', 'status': 'ACTIVE'}
        if '/gateway-admin/v1/routes/' in path:
            return {'routeCode': 'operations-gateway-route-list', 'targetServiceCode': 'operations-console-bff'}
        if split.path == '/gateway-admin/v1/routes':
            items = [{'routeCode': row['route_code'], 'status': row['status']}
                     for row in fixture(self.stage)['t_gateway_route']]
            if 'routeCode' in args:
                items = [item for item in items if item['routeCode'] == args['routeCode'][0]]
        elif split.path == '/gateway-admin/v1/target-services':
            items = [{'targetServiceCode': 'operations-console-bff', 'status': 'ACTIVE'}]
        elif split.path == '/iam/v1/roles':
            items = [{'roleId': '510', 'roleCode': 'PLATFORM_ADMIN', 'roleName': '平台管理员', 'version': 1, 'employeeCount': 1},
                     {'roleId': '511', 'roleCode': 'OPERATIONS_VIEWER', 'roleName': '运营只读人员', 'version': 1, 'employeeCount': 0}]
        elif split.path == '/iam/v1/accounts':
            items = [{'accountId': 'fake-account'}]
        elif split.path == '/audit/v1/events':
            items = [{'auditEventId': str(i)} for i in range(12)]
        else:
            raise AssertionError('unexpected HTTP path: ' + path)
        result = {'items': items[(page-1)*size:page*size], 'total': len(items), 'page': page, 'size': size}
        if split.path == '/gateway-admin/v1/routes' and 'routeCode' not in args and self.paging:
            self.paging(result)
        return result

    def request_problem(self, method, path, code, status, body=None, authenticated=True, extra_headers=None):
        self.problem_calls.append((method, path, code, status))
        result = self.request(method, path, body, authenticated, status, extra_headers)
        if result['code'] != code:
            raise AssertionError('fake Problem code differs from the expected contract')
        return result


class SmokeWiringTest(unittest.TestCase):
    def setUp(self):
        self.smoke = load_smoke()

    def test_browser_requires_canonical_origin_and_preserves_explicit_zero_port(self):
        for origin in ('http://localhost:28080?', 'http://localhost:28080#',
                       'http://local\thost:28080', 'http://localhost:28080\n', ' http://localhost:28080',
                       'HTTP://localhost:28080', 'http://LOCALHOST:28080', 'http://localhost:028080',
                       'http://localhost:28080/', 'http://localhost:28080\\', 'http://localhost:28080 ',
                       'http://localhost:28080\x00', 'http://localhost:', 'http://[0:0:0:0:0:0:0:1]:28080',
                       'http://[::1%25lo0]:28080'):
            with self.subTest(origin=repr(origin)), self.assertRaises(ValueError):
                self.smoke.Browser(base_url=origin)
        for origin, host, port in [('http://localhost:0', 'localhost', 0), ('http://localhost', 'localhost', 80),
                                   ('http://127.0.0.1:28080', '127.0.0.1', 28080), ('http://[::1]:28080', '::1', 28080)]:
            with self.subTest(origin=origin):
                browser = self.smoke.Browser(base_url=origin)
                self.assertEqual((origin, host, port), (browser.origin, browser.host, browser.port))

    def test_default_smoke_accepts_only_the_current_problem_profile(self):
        valid = problem('INVALID_REQUEST', 400, 'Invalid paging')
        self.assertEqual(
            'INVALID_REQUEST',
            self.smoke.check_problem(valid, 'INVALID_REQUEST', 400, problem_headers())['code'],
        )
        extended = dict(valid, futureDiagnostic='ignored by consumers')
        self.assertEqual(
            'INVALID_REQUEST',
            self.smoke.check_problem(extended, 'INVALID_REQUEST', 400, problem_headers())['code'],
        )
        valid_validation = dict(
            problem('VALIDATION_ERROR', 400, 'Invalid body'),
            errors=[{'field': 'items[0].value', 'message': 'must be present'}],
        )
        self.assertEqual(
            'VALIDATION_ERROR',
            self.smoke.check_problem(
                valid_validation, 'VALIDATION_ERROR', 400, problem_headers()
            )['code'],
        )
        mutations = (
            {'code': 'INVALID_REQUEST', 'message': 'legacy'},
            dict(valid, type='https://example.invalid/problem'),
            dict(valid, title='Invalid Request'),
            dict(valid, detail=''),
            dict(valid, detail='   '),
            dict(valid, code='BUSINESS_CHECK_FAILED'),
            dict(valid, requestId='invalid request id'),
            dict(valid, timestamp='2026-09-08T01:02:03Z'),
            dict(valid, timestamp='2026-02-30T01:02:03.123456Z'),
            dict(valid, errors=[{'field': 'size', 'message': 'invalid'}]),
            dict(valid, message='legacy member'),
            dict(valid, status=400),
            dict(valid, instance='/private/resource'),
        )
        for body in mutations:
            with self.subTest(body=body), self.assertRaises(AssertionError):
                self.smoke.check_problem(body, 'INVALID_REQUEST', 400, problem_headers())

        invalid_request_id = 'invalid request id'
        with self.assertRaises(AssertionError):
            self.smoke.check_problem(
                dict(valid, requestId=invalid_request_id),
                'INVALID_REQUEST', 400,
                problem_headers(request_id=invalid_request_id),
            )

        for errors in (
            [{'field': ' items', 'message': 'invalid'}],
            [{'field': 'items[0].value', 'message': ' '}],
            [{'field': 'items[0].value', 'message': 'invalid', 'code': 'SECOND_LAYER'}],
            [],
            None,
        ):
            with self.subTest(errors=errors), self.assertRaises(AssertionError):
                self.smoke.check_problem(
                    dict(problem('VALIDATION_ERROR', 400, 'Invalid body'), errors=errors),
                    'VALIDATION_ERROR', 400, problem_headers(),
                )

        header_mutations = (
            problem_headers(content_type='application/json'),
            dict(problem_headers(), **{'content-type': ()}),
            dict(problem_headers(), **{'content-type': ('application/problem+json', 'application/problem+json')}),
            dict(problem_headers(), **{'x-request-id': ()}),
            dict(problem_headers(), **{'x-request-id': ('different-request',)}),
            dict(problem_headers(), **{'x-request-id': ('request-20260908_ABC', 'request-duplicate')}),
            dict(problem_headers(), **{'date': ()}),
            dict(problem_headers(), **{'date': ('not-an-http-date',)}),
            dict(problem_headers(), **{'date': ('Tuesday, 08-Sep-26 01:02:03 GMT',)}),
            dict(problem_headers(), **{'date': ('Tue, 08 Sep 2026 09:02:03 +0800',)}),
            dict(problem_headers(), **{'x-timestamp': ('2026-09-08T01:02:03Z',)}),
        )
        for headers in header_mutations:
            with self.subTest(headers=headers), self.assertRaises(AssertionError):
                self.smoke.check_problem(valid, 'INVALID_REQUEST', 400, headers)

    def run_smoke(self, stage, navigation_stage=None, paging=None):
        rows = fixture(stage)
        persisted = {'stage': stage,
                     'resources': {r['resource_code']: r['display_name'] for r in rows['t_console_resource'] if r['status'] == 'ACTIVE'},
                     'routes': {r['route_code']: r['status'] for r in rows['t_gateway_route']},
                     'resource_rows': rows['t_console_resource'], 'route_rows': rows['t_gateway_route']}
        reader = mock.Mock(return_value=persisted)
        browser = FakeBrowser(stage, navigation_stage, paging)
        output = io.StringIO()
        real_write, real_chmod = Path.write_text, Path.chmod
        def write(path, *args, **kwargs):
            self.assertEqual(access, path, 'main attempted to write outside the injected output')
            return real_write(path, *args, **kwargs)
        def chmod(path, *args, **kwargs):
            self.assertEqual(access, path, 'main attempted to chmod outside the injected output')
            return real_chmod(path, *args, **kwargs)
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(output), \
             mock.patch.object(Path, 'read_text', side_effect=AssertionError('main read real secrets')), \
             mock.patch.object(Path, 'write_text', new=write), mock.patch.object(Path, 'chmod', new=chmod), \
             mock.patch.object(self.smoke.http.client, 'HTTPConnection', side_effect=AssertionError('main opened a socket')):
            # Build fake replies before the secret-read sentinel: fake HTTP's own
            # independent SQL fixtures are cached and never read private material.
            access = Path(directory) / 'access.md'
            self.smoke.main([], persisted_reader=reader, browser_factory=lambda: browser,
                            material_loader=lambda: {'administratorEmail': 'fake@example.invalid', 'administratorPassword': 'fake-password'},
                            bootstrap_ready=lambda: True, output_path=access)
            self.assertEqual(0o600, access.stat().st_mode & 0o777)
        reader.assert_called_once_with()
        self.assertIn(stage, output.getvalue())
        self.assertFalse(any('/decision-' in path for _, path in browser.calls))
        self.assertEqual(
            [
                ('GET', '/iam/v1/roles?size=11', 'INVALID_REQUEST', 400),
                ('GET', '/iam/v1/accounts?page=0&size=20', 'INVALID_REQUEST', 400),
                ('POST', '/auth/v1/password', 'BUSINESS_CHECK_FAILED', 400),
                ('POST', '/auth/v1/refresh', 'OPERATIONS_SESSION_INVALID', 401),
            ],
            browser.problem_calls,
        )
        return browser

    def test_real_main_uses_persisted_authority_for_legacy_prepared_and_exposed(self):
        for stage in ('LEGACY', 'BASELINE', 'ALL_ROUTES_ACTIVE', 'RESOURCES_EXPOSED'):
            with self.subTest(stage=stage):
                browser = self.run_smoke(stage)
                pages = [path for _, path in browser.calls if path.startswith('/gateway-admin/v1/routes?page=')]
                self.assertEqual(3 if stage == 'LEGACY' else 4, len(pages))

    def test_real_main_rejects_both_navigation_authority_mismatches(self):
        for db, navigation in [('RESOURCES_EXPOSED', 'BASELINE'), ('BASELINE', 'RESOURCES_EXPOSED')]:
            with self.subTest(db=db, navigation=navigation), self.assertRaises((AssertionError, SystemExit)):
                self.run_smoke(db, navigation)

    def test_real_main_rejects_incomplete_duplicate_unstable_and_wrong_status_routes(self):
        def incomplete(page):
            if page['page'] == 2:
                page['items'].pop()
        def duplicate(page):
            if page['page'] == 2:
                page['items'][0]['routeCode'] = 'operations-auth-login'
        def unstable(page):
            if page['page'] == 2:
                page['total'] += 1
        def wrong_status(page):
            page['items'][0]['status'] = 'DISABLED'
        for mutation in (incomplete, duplicate, unstable, wrong_status):
            with self.subTest(mutation=mutation.__name__), self.assertRaises((AssertionError, SystemExit)):
                self.run_smoke('BASELINE', paging=mutation)

    def test_browser_preserves_exact_raw_bytes_port_path_and_normal_object_api(self):
        journal = []
        class Connection:
            def __init__(self, host, port, timeout):
                journal.append(('connect', host, port, timeout))
            def request(self, method, path, body, headers):
                journal.append((method, path, body, headers))
            def getresponse(self):
                return mock.Mock(status=200, read=lambda: b'{}', headers=mock.Mock(get_all=lambda _: []))
            def close(self):
                pass
        browser = self.smoke.Browser(base_url='http://127.0.0.1:29876', connection_factory=Connection)
        browser.access = 'fake-token'
        for raw in (b'{"x":1}', b'{"x":1} '):
            browser.request_raw('POST', '/decision-rule-sets/v1/rule-sets', raw)
            self.assertEqual(('connect', '127.0.0.1', 29876, 20), journal[-2])
            method, path, body, headers = journal[-1]
            self.assertEqual('/api/operations/decision-rule-sets/v1/rule-sets', path)
            self.assertEqual(raw, body)
            self.assertEqual(str(len(raw)), headers['Content-Length'])
            self.assertEqual('http://127.0.0.1:29876', headers['Origin'])
            self.assertEqual('Bearer fake-token', headers['Authorization'])
        browser.request('POST', '/auth/v1/logout', {'refreshToken': 'fake'})
        self.assertEqual({'refreshToken': 'fake'}, json.loads(journal[-1][2]))
        for base in ('https://example.com', 'http://192.168.1.1:80', 'http://localhost:80/path',
                     'http://user@localhost:80', 'http://localhost:80?x=1'):
            with self.subTest(base=base), self.assertRaises((ValueError, SystemExit)):
                self.smoke.Browser(base_url=base, connection_factory=Connection)

    def test_browser_problem_request_preserves_and_validates_transport_contract(self):
        valid = problem('INVALID_REQUEST', 400, 'Invalid paging')
        response_body = {'value': json.dumps(valid).encode()}
        response_headers = {
            'Content-Type': ['application/problem+json'],
            'X-Request-Id': ['request-20260908_ABC'],
            'Date': ['Tue, 08 Sep 2026 01:02:03 GMT'],
            'X-Timestamp': [],
            'Set-Cookie': [],
        }

        class Headers:
            def get_all(self, name):
                return response_headers.get(name, [])

        class Connection:
            def __init__(self, host, port, timeout):
                pass
            def request(self, method, path, body, headers):
                pass
            def getresponse(self):
                return mock.Mock(status=400, read=lambda: response_body['value'], headers=Headers())
            def close(self):
                pass

        browser = self.smoke.Browser(connection_factory=Connection)
        self.assertEqual(
            'INVALID_REQUEST',
            browser.request_problem('GET', '/iam/v1/roles?size=11', 'INVALID_REQUEST', 400)['code'],
        )
        response_headers['Content-Type'] = ['application/json']
        with self.assertRaises(AssertionError):
            browser.request_problem('GET', '/iam/v1/roles?size=11', 'INVALID_REQUEST', 400)
        response_headers['Content-Type'] = ['application/problem+json']
        response_body['value'] = (
            b'{"type":"about:blank","title":"Bad Request","detail":"Invalid paging",'
            b'"code":"INVALID_REQUEST","requestId":"request-20260908_ABC",'
            b'"timestamp":"2026-09-08T01:02:03.123456Z","futureDiagnostic":NaN}'
        )
        with self.assertRaises(AssertionError):
            browser.request_problem('GET', '/iam/v1/roles?size=11', 'INVALID_REQUEST', 400)
        response_body['value'] = (
            b'{"type":"about:blank","title":"Bad Request","detail":"first",'
            b'"detail":"second","code":"INVALID_REQUEST",'
            b'"requestId":"request-20260908_ABC",'
            b'"timestamp":"2026-09-08T01:02:03.123456Z"}'
        )
        with self.assertRaises(AssertionError):
            browser.request_problem('GET', '/iam/v1/roles?size=11', 'INVALID_REQUEST', 400)

    def test_raw_response_api_preserves_the_real_http_status_and_problem_headers(self):
        conflict = problem('CONCURRENT_REQUEST', 409, 'Concurrent change')
        headers = problem_headers()

        class Headers:
            def get_all(self, name):
                return headers.get(name.lower(), ())

        class Connection:
            def __init__(self, host, port, timeout):
                pass
            def request(self, method, path, body, request_headers):
                self.request_tuple = (method, path, body, request_headers)
            def getresponse(self):
                return mock.Mock(
                    status=409,
                    read=lambda: json.dumps(conflict).encode(),
                    headers=Headers(),
                )
            def close(self):
                pass

        browser = self.smoke.Browser(connection_factory=Connection)
        body, response_headers, status = browser.request_raw_response(
            'PATCH',
            '/gateway-admin/v1/routes/operations-risk-rule-set-detail/status',
            b'{"status":"ACTIVE"}',
            status=(200, 409),
            extra_headers={'Idempotency-Key': 'hidden-test-key'},
        )

        self.assertEqual(409, status)
        self.assertEqual('CONCURRENT_REQUEST', body['code'])
        self.assertEqual(('application/problem+json',), response_headers['content-type'])
        self.assertNotIn('status', body)

    def test_unexpected_status_diagnostic_never_discloses_remote_body_material(self):
        canary = 'CANARY_SECRET\nforged-line'
        response_body = {'value': json.dumps({'code': canary}).encode()}

        class Headers:
            def get_all(self, name):
                return []

        class Connection:
            def __init__(self, host, port, timeout):
                pass
            def request(self, method, path, body, headers):
                pass
            def getresponse(self):
                return mock.Mock(
                    status=418,
                    read=lambda: response_body['value'],
                    headers=Headers(),
                )
            def close(self):
                pass

        browser = self.smoke.Browser(connection_factory=Connection)
        for payload in ({'code': canary}, [canary], canary, None):
            with self.subTest(payload=payload):
                response_body['value'] = json.dumps(payload).encode()
                with self.assertRaises(SystemExit) as failure:
                    browser.request('GET', '/iam/v1/roles')
                diagnostic = str(failure.exception)
                self.assertIn('HTTP 418', diagnostic)
                self.assertNotIn('CANARY_SECRET', diagnostic)
                self.assertNotIn('forged-line', diagnostic)
                self.assertEqual(0, diagnostic.count('\n'))

    def test_rate_limited_get_honors_one_strict_bounded_retry_after(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')
        response_queue = [
            (429, json.dumps(rate_limited).encode(), problem_headers(retry_after=('6',))),
            (200, b'{"routeCode":"operations-risk-rule-set-detail"}', {}),
        ]
        sleeps = []
        timeouts = []
        wire = []
        now = [100.0]

        def clock():
            return now[0]

        def sleep(seconds):
            sleeps.append(seconds)
            now[0] += seconds

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        class Connection:
            def __init__(self, host, port, timeout):
                timeouts.append(timeout)
            def request(self, method, path, body, headers):
                wire.append((method, path, body, dict(headers)))
            def getresponse(self):
                status, body, headers = response_queue.pop(0)
                return mock.Mock(status=status, read=lambda: body, headers=Headers(headers))
            def close(self):
                pass

        browser = self.smoke.Browser(
            connection_factory=Connection, sleeper=sleep, monotonic=clock
        )
        deadline = browser.new_deadline(10)
        result = browser.request_rate_limited_get(
            '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
            max_retry_after_seconds=6,
            deadline=deadline,
        )

        self.assertEqual('operations-risk-rule-set-detail', result['routeCode'])
        self.assertEqual([6], sleeps)
        self.assertEqual([10, 4], timeouts)
        self.assertEqual(2, len(wire))
        self.assertTrue(all(method == 'GET' for method, _, _, _ in wire))
        self.assertTrue(all(body is None for _, _, body, _ in wire))
        self.assertTrue(all('Idempotency-Key' not in headers for _, _, _, headers in wire))
        self.assertEqual(
            {
                '/api/operations/gateway-admin/v1/routes/'
                'operations-risk-rule-set-detail'
            },
            {path for _, path, _, _ in wire},
        )
        self.assertEqual([], response_queue)

    def test_rate_limited_login_replays_only_after_strict_gateway_429(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')
        token = {
            'accessToken': 'private-access-token',
            'refreshToken': 'private-refresh-token',
        }
        response_queue = [
            (
                429,
                json.dumps(rate_limited).encode(),
                problem_headers(retry_after=('12',)),
            ),
            (200, json.dumps(token).encode(), {}),
        ]
        sleeps = []
        timeouts = []
        wire = []
        now = [100.0]

        def clock():
            return now[0]

        def sleep(seconds):
            sleeps.append(seconds)
            now[0] += seconds

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        class Connection:
            def __init__(self, host, port, timeout):
                timeouts.append(timeout)
            def request(self, method, path, body, headers):
                wire.append((method, path, body, dict(headers)))
            def getresponse(self):
                status, body, headers = response_queue.pop(0)
                return mock.Mock(
                    status=status, read=lambda: body, headers=Headers(headers)
                )
            def close(self):
                pass

        browser = self.smoke.Browser(
            connection_factory=Connection, sleeper=sleep, monotonic=clock
        )
        result = browser.request_rate_limited_login(
            'fixture@example.test',
            'Password-Must-Stay-Private-9!',
            max_retry_after_seconds=60,
            deadline=browser.new_deadline(90),
            status=(200, 401),
        )

        self.assertEqual(token, result)
        self.assertEqual([12], sleeps)
        self.assertEqual([20.0, 20.0], timeouts)
        self.assertEqual(2, len(wire))
        self.assertEqual(wire[0][2], wire[1][2])
        self.assertEqual(
            {'POST'}, {method for method, _path, _body, _headers in wire}
        )
        self.assertEqual(
            {'/api/operations/auth/v1/login'},
            {path for _method, path, _body, _headers in wire},
        )
        self.assertTrue(
            all('Authorization' not in headers for _m, _p, _b, headers in wire)
        )
        self.assertEqual([], response_queue)

    def test_rate_limited_login_does_not_retry_401_or_other_429(self):
        unauthorized = problem(
            'OPERATIONS_AUTHENTICATION_FAILED', 401, 'Invalid credentials'
        )
        concurrency_limited = problem(
            'GATEWAY_CONCURRENCY_LIMITED', 429, 'Too many concurrent requests'
        )

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        def browser_for(status, body, headers):
            attempts = []
            sleeps = []

            class Connection:
                def __init__(self, host, port, timeout):
                    pass
                def request(self, method, path, request_body, request_headers):
                    attempts.append((method, path, request_body))
                def getresponse(self):
                    return mock.Mock(
                        status=status,
                        read=lambda: json.dumps(body).encode(),
                        headers=Headers(headers),
                    )
                def close(self):
                    pass

            return (
                self.smoke.Browser(
                    connection_factory=Connection, sleeper=sleeps.append
                ),
                attempts,
                sleeps,
            )

        browser, attempts, sleeps = browser_for(401, unauthorized, {})
        result = browser.request_rate_limited_login(
            'fixture@example.test',
            'private-password',
            max_retry_after_seconds=60,
            deadline=browser.new_deadline(90),
            status=(200, 401),
        )
        self.assertEqual('OPERATIONS_AUTHENTICATION_FAILED', result['code'])
        self.assertEqual(1, len(attempts))
        self.assertEqual([], sleeps)

        browser, attempts, sleeps = browser_for(
            429,
            concurrency_limited,
            problem_headers(retry_after=('1',)),
        )
        with self.assertRaises(AssertionError):
            browser.request_rate_limited_login(
                'fixture@example.test',
                'private-password',
                max_retry_after_seconds=60,
                deadline=browser.new_deadline(90),
                status=(200, 401),
            )
        self.assertEqual(1, len(attempts))
        self.assertEqual([], sleeps)

    def test_rate_limited_login_does_not_retry_unknown_transport(self):
        attempts = []
        sleeps = []

        class Connection:
            def __init__(self, host, port, timeout):
                pass
            def request(self, method, path, body, headers):
                attempts.append((method, path, body))
            def getresponse(self):
                raise TimeoutError("unknown login result")
            def close(self):
                pass

        browser = self.smoke.Browser(
            connection_factory=Connection, sleeper=sleeps.append
        )
        with self.assertRaises(TimeoutError):
            browser.request_rate_limited_login(
                'fixture@example.test',
                'private-password',
                max_retry_after_seconds=60,
                deadline=browser.new_deadline(90),
                status=(200, 401),
            )
        self.assertEqual(1, len(attempts))
        self.assertEqual([], sleeps)

    def test_rate_limited_login_rejects_second_429_and_deadline_exhaustion(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        def browser_for(queue):
            attempts = []
            sleeps = []
            now = [0.0]

            class Connection:
                def __init__(self, host, port, timeout):
                    pass
                def request(self, method, path, body, headers):
                    attempts.append((method, path, body))
                def getresponse(self):
                    status, body, headers = queue.pop(0)
                    return mock.Mock(
                        status=status,
                        read=lambda: body,
                        headers=Headers(headers),
                    )
                def close(self):
                    pass

            def sleep(seconds):
                sleeps.append(seconds)
                now[0] += seconds

            return (
                self.smoke.Browser(
                    connection_factory=Connection,
                    sleeper=sleep,
                    monotonic=lambda: now[0],
                ),
                attempts,
                sleeps,
            )

        response = (
            429,
            json.dumps(rate_limited).encode(),
            problem_headers(retry_after=('1',)),
        )
        browser, attempts, sleeps = browser_for([response, response])
        with self.assertRaisesRegex(SystemExit, 'remained rate limited'):
            browser.request_rate_limited_login(
                'fixture@example.test',
                'private-password',
                max_retry_after_seconds=60,
                deadline=browser.new_deadline(90),
                status=(200, 401),
            )
        self.assertEqual(2, len(attempts))
        self.assertEqual([6], sleeps)

        deadline_response = (
            429,
            json.dumps(rate_limited).encode(),
            problem_headers(retry_after=('6',)),
        )
        browser, attempts, sleeps = browser_for([deadline_response])
        with self.assertRaisesRegex(SystemExit, 'deadline'):
            browser.request_rate_limited_login(
                'fixture@example.test',
                'private-password',
                max_retry_after_seconds=60,
                deadline=browser.new_deadline(5),
                status=(200, 401),
            )
        self.assertEqual(1, len(attempts))
        self.assertEqual([], sleeps)

    def test_rate_limited_get_rejects_bad_retry_after_and_a_second_429(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        def browser_for(retry_values, queue_length=1):
            queue = [
                (429, json.dumps(rate_limited).encode(), problem_headers(retry_after=retry_values))
                for _ in range(queue_length)
            ]
            sleeps = []
            class Connection:
                def __init__(self, host, port, timeout):
                    pass
                def request(self, method, path, body, headers):
                    pass
                def getresponse(self):
                    status, body, headers = queue.pop(0)
                    return mock.Mock(status=status, read=lambda: body, headers=Headers(headers))
                def close(self):
                    pass
            return self.smoke.Browser(connection_factory=Connection, sleeper=sleeps.append), sleeps

        for retry_values in (
            (),
            ('0',),
            ('06',),
            ('7',),
            (' 6',),
            ('+6',),
            ('Tue, 08 Sep 2026 01:02:09 GMT',),
            ('6', '7'),
        ):
            with self.subTest(retry_values=retry_values):
                browser, sleeps = browser_for(retry_values)
                with self.assertRaises(SystemExit):
                    browser.request_rate_limited_get(
                        '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                        max_retry_after_seconds=6,
                        deadline=browser.new_deadline(90),
                    )
                self.assertEqual([], sleeps)

        self.assertEqual(
            1,
            self.smoke._retry_after_seconds(
                problem_headers(retry_after=('1',)), 6
            ),
        )

        for maximum in (True, False, 0, 61, 6.0, '6'):
            with self.subTest(maximum=maximum):
                browser, sleeps = browser_for(('1',))
                with self.assertRaises(ValueError):
                    browser.request_rate_limited_get(
                        '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                        max_retry_after_seconds=maximum,
                        deadline=browser.new_deadline(90),
                    )
                self.assertEqual([], sleeps)

        browser, sleeps = browser_for(('6',), queue_length=2)
        with self.assertRaises(SystemExit) as failure:
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(90),
            )
        self.assertEqual([6], sleeps)
        self.assertNotIn('Too frequent', str(failure.exception))

    def test_rate_limited_get_extends_short_retry_after_for_layered_guard_refill(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')
        attempts = [0]
        sleeps = []
        timeouts = []
        now = [0.0]

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        class Connection:
            def __init__(self, host, port, timeout):
                timeouts.append(timeout)
            def request(self, method, path, body, headers):
                pass
            def getresponse(self):
                attempts[0] += 1
                if attempts[0] == 1 or now[0] < 6:
                    status = 429
                    body = json.dumps(rate_limited).encode()
                    headers = problem_headers(retry_after=('1',))
                else:
                    status, body, headers = 200, b'{"ok":true}', {}
                return mock.Mock(
                    status=status, read=lambda: body, headers=Headers(headers)
                )
            def close(self):
                pass

        def sleep(seconds):
            sleeps.append(seconds)
            now[0] += seconds

        browser = self.smoke.Browser(
            connection_factory=Connection,
            sleeper=sleep,
            monotonic=lambda: now[0],
        )
        self.assertEqual(
            {'ok': True},
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(90),
            ),
        )
        self.assertEqual([6], sleeps)
        self.assertEqual([20.0, 20.0], timeouts)
        self.assertEqual(2, attempts[0])

    def test_rate_limited_get_caps_each_connection_and_checks_deadline_after_exchange(self):
        timeouts = []
        now = [0.0]
        consume_deadline = [False]

        class Headers:
            def get_all(self, name):
                return ()

        class Connection:
            def __init__(self, host, port, timeout):
                timeouts.append(timeout)
            def request(self, method, path, body, headers):
                pass
            def getresponse(self):
                def read():
                    if consume_deadline[0]:
                        now[0] += 90.0
                    return b'{}'
                return mock.Mock(status=200, read=read, headers=Headers())
            def close(self):
                pass

        browser = self.smoke.Browser(
            connection_factory=Connection, monotonic=lambda: now[0]
        )
        for budget in (90, 180, 720):
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(budget),
            )
        self.assertEqual([20.0, 20.0, 20.0], timeouts)

        consume_deadline[0] = True
        with self.assertRaisesRegex(SystemExit, 'deadline'):
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(90),
            )
        self.assertEqual(20.0, timeouts[-1])

    def test_rate_limited_get_has_one_monotonic_deadline_and_never_accepts_other_429(self):
        rate_limited = problem('GATEWAY_RATE_LIMITED', 429, 'Too frequent')
        concurrency_limited = problem(
            'GATEWAY_CONCURRENCY_LIMITED', 429, 'Too many concurrent requests'
        )
        now = [10.0]
        sleeps = []
        timeouts = []
        queue = []

        def clock():
            return now[0]

        def sleep(seconds):
            sleeps.append(seconds)
            now[0] += seconds

        class Headers:
            def __init__(self, values):
                self.values = values
            def get_all(self, name):
                return self.values.get(name.lower(), ())

        class Connection:
            def __init__(self, host, port, timeout):
                timeouts.append(timeout)
            def request(self, method, path, body, headers):
                self.request_tuple = (method, path, body)
            def getresponse(self):
                status, body, headers = queue.pop(0)
                return mock.Mock(status=status, read=lambda: body, headers=Headers(headers))
            def close(self):
                pass

        browser = self.smoke.Browser(
            connection_factory=Connection, sleeper=sleep, monotonic=clock
        )
        queue.append((
            429,
            json.dumps(rate_limited).encode(),
            problem_headers(retry_after=('6',)),
        ))
        with self.assertRaisesRegex(SystemExit, 'deadline') as failure:
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes?page=1&secret=CANARY_SECRET',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(5),
            )
        self.assertNotIn('CANARY_SECRET', str(failure.exception))
        self.assertEqual([], sleeps)
        self.assertEqual([5], timeouts)

        queue.append((
            429,
            json.dumps(rate_limited).encode(),
            problem_headers(retry_after=('1',)),
        ))
        with self.assertRaisesRegex(SystemExit, 'deadline'):
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(1),
            )
        self.assertEqual([], sleeps)
        self.assertEqual([5, 1], timeouts)

        queue.append((
            429,
            json.dumps(rate_limited).encode(),
            problem_headers(retry_after=('1',)),
        ))
        with self.assertRaisesRegex(SystemExit, 'deadline'):
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(1),
            )
        self.assertEqual([], sleeps)

        queue.append((
            429,
            json.dumps(concurrency_limited).encode(),
            problem_headers(retry_after=('1',)),
        ))
        with self.assertRaises(AssertionError):
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes/operations-risk-rule-set-detail',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(90),
            )
        self.assertEqual([], sleeps)

        queue.append((503, b'{"detail":"BODY_CANARY"}', {}))
        with self.assertRaises(SystemExit) as failure:
            browser.request_rate_limited_get(
                '/gateway-admin/v1/routes?page=1&query=QUERY_CANARY',
                max_retry_after_seconds=6,
                deadline=browser.new_deadline(90),
            )
        diagnostic = str(failure.exception)
        self.assertNotIn('QUERY_CANARY', diagnostic)
        self.assertNotIn('BODY_CANARY', diagnostic)
        self.assertEqual([], sleeps)

        for timeout in (True, 0, -1, float('inf'), float('nan'), '90'):
            with self.subTest(timeout=timeout), self.assertRaises(ValueError):
                browser.new_deadline(timeout)


class GatewayRolloutLockTest(unittest.TestCase):
    def run_gateway(self, fail_after_disable=False, contend=False, fail_disable_response=False):
        import initialize
        spec = importlib.util.spec_from_file_location('gateway_smoke_under_test', STAGE_MODULE.with_name('gateway_control_plane_smoke.py'))
        module = importlib.util.module_from_spec(spec)
        with mock.patch.object(os, 'umask', side_effect=AssertionError('import changed umask')), \
             mock.patch.object(Path, 'read_text', side_effect=AssertionError('import read secrets')):
            spec.loader.exec_module(module)
        self.assertIs(initialize.risk_operations_rollout_lock, module.risk_operations_rollout_lock)
        journal, replay = [], {}
        current = {'routeCode': module.ROUTE_CODE, 'status': 'ACTIVE', 'version': 3}
        ownership = {'held': False}
        class Browser:
            refresh_token = 'fake-refresh'
            access = 'fake-access'
            def login(self, identifier, password):
                journal.append('login')
            def request(self, method, path, body=None, authenticated=True, status=200, extra_headers=None):
                if method == 'GET' and path == module.ROUTE_DETAIL_PATH or method == 'PATCH':
                    if not contend:
                        self_case.assertTrue(ownership['held'], 'authoritative GET/PATCH escaped rollout lock')
                    journal.append(method)
                if not authenticated:
                    return {'code': 'UNAUTHORIZED'}
                if method == 'GET':
                    return dict(current)
                if method == 'POST':
                    journal.append('logout')
                    return None
                key = extra_headers['Idempotency-Key']
                if key in replay:
                    return dict(replay[key])
                if body['expectedVersion'] != current['version']:
                    return {'code': 'CONCURRENT_REQUEST'}
                current.update(status=body['status'], version=current['version'] + 1)
                replay[key] = dict(current)
                if fail_disable_response and current['status'] == 'DISABLED':
                    raise RuntimeError('synthetic committed PATCH response failure')
                return dict(current)
        self_case = self
        def wait(browser, status):
            self.assertTrue(ownership['held'], 'Snapshot wait escaped rollout lock')
            journal.append('wait-' + str(status))
            if status == 404 and fail_after_disable:
                raise RuntimeError('synthetic Snapshot failure')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'lock'
            @contextlib.contextmanager
            def shared_lock():
                with initialize.risk_operations_rollout_lock(path) as descriptor:
                    ownership['held'] = True
                    journal.append('lock')
                    try:
                        yield descriptor
                    finally:
                        journal.append('unlock')
                        ownership['held'] = False
            with mock.patch.object(module, 'Browser', Browser), \
                 mock.patch.object(module, 'risk_operations_rollout_lock', shared_lock, create=True), \
                 mock.patch.object(module, 'wait_for_public_status', wait), \
                 mock.patch.object(module.http.client, 'HTTPConnection', side_effect=AssertionError('real network forbidden')), \
                 mock.patch.object(Path, 'read_text', return_value=json.dumps({'administratorEmail': 'fake@example.invalid', 'administratorPassword': 'fake'})), \
                 contextlib.redirect_stdout(io.StringIO()):
                if contend:
                    with initialize.risk_operations_rollout_lock(path), self.assertRaises(SystemExit):
                        module.main()
                    self.assertNotIn('PATCH', journal)
                elif fail_after_disable:
                    with self.assertRaisesRegex(RuntimeError, 'synthetic Snapshot failure'):
                        module.main()
                elif fail_disable_response:
                    with self.assertRaisesRegex(RuntimeError, 'synthetic committed PATCH response failure'):
                        module.main()
                else:
                    module.main()
        if not contend:
            self.assertEqual('ACTIVE', current['status'])
            scoped = journal[journal.index('lock') + 1:journal.index('unlock')]
            self.assertEqual('GET', scoped[0])
            self.assertEqual('GET', scoped[-1], 'restoration must end with an authoritative post-read')
            self.assertGreaterEqual(scoped.count('PATCH'), 2 if fail_disable_response else 4)
        return journal

    def test_existing_route_smoke_holds_lock_through_patch_restore_and_postread(self):
        self.run_gateway()

    def test_existing_route_smoke_restores_under_lock_when_snapshot_check_fails(self):
        self.run_gateway(fail_after_disable=True)

    def test_existing_route_smoke_cannot_patch_when_rollout_lock_is_contended(self):
        self.run_gateway(contend=True)

    def test_existing_route_smoke_restores_after_a_committed_patch_response_is_lost(self):
        self.run_gateway(fail_disable_response=True)


if __name__ == "__main__":
    unittest.main()
