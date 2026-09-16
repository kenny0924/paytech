"""Build review-only configuration payloads; never calls management APIs."""
from pathlib import Path
import json

HERE = Path(__file__).resolve().parent
plan_path = HERE / 'registration-plan.json'
plan = json.loads(plan_path.read_text())
reason = 'Client onboarding initial registration; keep routes disabled pending acceptance'
idempotent = {'C04', *('C%02d' % n for n in range(6, 15)),
              *('O%02d' % n for n in range(3, 10))}
queries = {'C03': ['clientType', 'countryCode'], 'C15': ['cursor', 'limit'],
           'O01': ['cursor', 'limit', 'status', 'applicationNo']}

def pair(dimension, capacity, concurrency):
    return [dict(ruleType='RATE_LIMIT', dimension=dimension, capacity=capacity,
                 refillTokens=capacity, refillPeriodMillis=60000, concurrencyLimit=None),
            dict(ruleType='CONCURRENCY', dimension=dimension, capacity=None,
                 refillTokens=None, refillPeriodMillis=None, concurrencyLimit=concurrency)]

for route in plan['routes']:
    op = route['operationId']
    if op == 'C02':
        route['externalPathTemplate'] = '/api/client/auth/v1/workspace-context'
    route['responseTimeoutMillis'] = 10000
    headers = []
    if route['securityProfileCode'] != 'PUBLIC':
        headers.append(dict(headerName='authorization', headerUse='CORS_REQUEST_ALLOWED'))
    if route['httpMethod'] not in {'GET', 'HEAD'}:
        headers += [dict(headerName='content-type', headerUse=use)
                    for use in ['CORS_REQUEST_ALLOWED', 'FORWARD_REQUEST']]
        headers.append(dict(headerName='origin', headerUse='FORWARD_REQUEST'))
    if op in idempotent:
        headers += [dict(headerName='idempotency-key', headerUse=use)
                    for use in ['CORS_REQUEST_ALLOWED', 'FORWARD_REQUEST']]
    route['headers'] = headers
    route['queryParameterNames'] = queries.get(op, [])
    public = route['securityProfileCode'] == 'PUBLIC'
    route['guardRules'] = pair('SHARED_ROUTE', 120 if public else 600, 20 if public else 50)
    route['guardRules'] += pair('CLIENT_IP', (3 if op in {'A01', 'A02', 'A07'} else 12)
                               if public else 120, 3 if public else 10)
    if not public:
        route['guardRules'] += pair('AUTHENTICATED_PRINCIPAL', 60, 5)

permission_specs = [
    ('CLIENT_ONBOARDING_REVIEW_READ', 'READ', 'LOW'),
    ('CLIENT_ONBOARDING_CLASSIFICATION_EDIT', 'CLASSIFY', 'MEDIUM'),
    ('CLIENT_ONBOARDING_EVIDENCE_REVIEW', 'REVIEW_EVIDENCE', 'MEDIUM'),
    ('CLIENT_ONBOARDING_RETURN', 'RETURN', 'MEDIUM'),
    ('CLIENT_ONBOARDING_DECIDE', 'DECIDE', 'HIGH'),
]
plan['permissions'] = [dict(code=code, resourceType='CLIENT_ONBOARDING', actionCode=action,
                            riskLevel=risk, initialStatus='INACTIVE',
                            targetStatus='ACTIVE', grantToRealUsers=False)
                       for code, action, risk in permission_specs]
menu = 'CLIENT_ONBOARDING_REVIEW'
component = 'OPERATIONS_CLIENT_ONBOARDING_REVIEW'
plan['consoleResources'] = [dict(code=menu, channel='OPERATIONS', resourceType='MENU',
    displayName='客户入驻审核', frontendRoute='/client-onboarding/reviews',
    componentKey=component, parentCode=None, permissionCode=permission_specs[0][0],
    sortOrder=300, status='DISABLED')]
actions = [
    ('CLIENT_ONBOARDING_CLASSIFY', '确认行业与 MCC', permission_specs[1][0]),
    ('CLIENT_ONBOARDING_REVIEW_EVIDENCE', '核验材料与检查', permission_specs[2][0]),
    ('CLIENT_ONBOARDING_RETURN', '退回修改', permission_specs[3][0]),
    ('CLIENT_ONBOARDING_DECIDE', '批准或最终拒绝', permission_specs[4][0]),
]
for index, (code, label, permission) in enumerate(actions, 1):
    plan['consoleResources'].append(dict(code=code, channel='OPERATIONS', resourceType='BUTTON',
        displayName=label, frontendRoute=None, componentKey=None, parentCode=menu,
        permissionCode=permission, sortOrder=index * 10, status='DISABLED'))
plan['clientNavigation'] = dict(mode='STATIC_ACCOUNT_CONTEXT',
    routes=['/onboarding', '/onboarding/progress'],
    authorization='CCT application ownership + allowedActions; no pre-Tenant IAM menu grants')
plan['requiredComponentCapability'] = dict(componentKey=component,
    displayName='客户入驻审核', frontendRoute='/client-onboarding/reviews',
    actions=[dict(resourceCode=c, displayName=n) for c, n, _ in actions])
plan['status'] = 'Review design complete; no configuration API or database write executed'
plan['unresolved'] = []
plan['releaseBindings'] = [
    'Resolve generated permissionId/resourceId and current expectedVersion by management GET',
    'Confirm CLIENT business listener discovery name and deployed security/cookie capabilities',
    'Confirm exact environment, reviewer roles and explicit grants before activation',
]
plan['sqlDmlRowsGenerated'] = 0

commands = []
def command(key, path, body, bindings=None):
    commands.append(dict(key=key, method='POST', ownerPath=path, body=body,
                         bindings=bindings or {}, execute=False))

command('client-target', '/gateway-admin/v1/target-services', dict(
    targetServiceCode='client-console-bff', channel='CLIENT',
    discoveryServiceName='client-console-bff', reason=reason))
for p in plan['permissions']:
    command('permission:' + p['code'], '/permission-admin/v1/permissions',
        dict(permissionCode=p['code'], resourceType=p['resourceType'],
             actionCode=p['actionCode'], riskLevel=p['riskLevel'], reason=reason))
for r in plan['routes']:
    body = {k: r[k] for k in ['routeCode', 'channel', 'httpMethod', 'externalPathTemplate',
            'downstreamPathTemplate', 'securityProfileCode', 'authorizationMode',
            'requestCookieMode', 'responseCookieMode', 'bodyLimitBytes',
            'bodyReceiveTimeoutMillis', 'responseTimeoutMillis', 'headers',
            'queryParameterNames', 'guardRules']}
    body.update(targetServiceCode=r['targetCode'], permissionId=None, requiredScopeCode=None, reason=reason)
    bindings = {'/permissionId': 'permission:' + r['permissionCode']} if r['permissionCode'] else {}
    command(r['operationId'], '/gateway-admin/v1/routes', body, bindings)
for r in plan['consoleResources']:
    body = {k: r[k] for k in ['resourceType', 'displayName', 'componentKey', 'sortOrder']}
    body.update(resourceCode=r['code'], parentResourceId=None, permissionId=None, reason=reason)
    bindings = {'/permissionId': 'permission:' + r['permissionCode']}
    if r['parentCode']:
        bindings['/parentResourceId'] = 'resource:' + r['parentCode']
    command('resource:' + r['code'], '/console-admin/v1/resources', body, bindings)

plan_path.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n')
(HERE / 'management-command-templates.json').write_text(json.dumps(dict(
    status='REVIEW_ONLY_UNBOUND_TEMPLATES',
    instructions='Resolve every binding before sending; null binding values are not valid requests. '
                 'Internal Owner paths require authenticated Operations BFF transport. '
                 'Use unique management Idempotency-Key per command, stable on retries. '
                 'Create only when exact code is absent; no automatic update or activation.',
    commands=commands), ensure_ascii=False, indent=2) + '\n')
print(json.dumps(dict(routes=len(plan['routes']), permissions=len(plan['permissions']),
                      resources=len(plan['consoleResources']), templates=len(commands))))
