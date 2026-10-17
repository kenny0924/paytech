"""Build review-only configuration payloads; never calls management APIs."""
from pathlib import Path
import json

HERE = Path(__file__).resolve().parent
plan_path = HERE / 'registration-plan.json'
plan = json.loads(plan_path.read_text())
reason = 'Client onboarding initial registration; keep routes disabled pending acceptance'
# C14 按申请状态防重，不转发 Idempotency-Key（2026-10-13 C14 修订）。
# F01/F02 同样要求 Idempotency-Key（api-contract 第 1 节）；2026-10-14 本机验收发现漏登记后补齐。
idempotent = {'C04', *('C%02d' % n for n in range(6, 14)), 'F01', 'F02',
              *('O%02d' % n for n in range(3, 10))}
queries = {'C03': ['clientType'], 'C15': ['cursor', 'limit'],
           'O01': ['page', 'size', 'status', 'qualificationType', 'applicationId'],
           'D01': ['status'], 'D13': ['endpointId', 'eventType', 'status', 'from', 'to', 'page', 'size']}

# 2026-10-16 接入自助：工作区“开发者”接口（API 凭证 6、Webhook 9），租户上下文与权限由 BFF 调 Platform 判断。
developer = [
    ('D01', 'GET', '/credentials', 0), ('D02', 'GET', '/credentials/{credentialId}', 0),
    ('D03', 'GET', '/scopes', 0), ('D04', 'POST', '/credentials', 4096),
    ('D05', 'POST', '/credentials/{credentialId}/rotation', 1024),
    ('D06', 'POST', '/credentials/{credentialId}/revocation', 1024),
    ('D07', 'GET', '/webhook-endpoints', 0), ('D08', 'POST', '/webhook-endpoints', 8192),
    ('D09', 'PUT', '/webhook-endpoints/{endpointId}', 8192),
    ('D10', 'POST', '/webhook-endpoints/{endpointId}/status', 1024),
    ('D11', 'POST', '/webhook-endpoints/{endpointId}/secret-rotation', 1024),
    ('D12', 'POST', '/webhook-endpoints/{endpointId}/test-events', 1024),
    ('D13', 'GET', '/webhook-deliveries', 0), ('D14', 'GET', '/webhook-deliveries/{deliveryId}', 0),
    ('D15', 'POST', '/webhook-deliveries/{deliveryId}/replay', 1024),
]
idempotent |= {op for op, method, _, _ in developer if method != 'GET'}

# 2026-10-17 团队成员与邀请（requirements/20261017/client-console-团队成员与邀请 第 8 节）：
# 团队 T01—T07 租户上下文、权限由 BFF 调 Platform 判断；邀请 I01 预览公开，I02 详情、I03 接受为账号上下文。
team = [
    ('T01', 'GET', '/members', 0), ('T02', 'GET', '/invitations', 0), ('T03', 'POST', '/invitations', 4096),
    ('T04', 'POST', '/invitations/{invitationId}/resend', 1024),
    ('T05', 'POST', '/invitations/{invitationId}/revocation', 1024),
    ('T06', 'POST', '/members/{memberId}/role', 1024), ('T07', 'POST', '/members/{memberId}/removal', 1024),
]
invitation = [('I01', 'A03', '/preview'), ('I02', 'C01', '/details'), ('I03', 'C01', '/acceptance')]
idempotent |= {op for op, method, _, _ in team if method != 'GET'} | {'I03'}
queries.update(T01=['page', 'size'], T02=['status', 'page', 'size'])

def pair(dimension, capacity, concurrency):
    return [dict(ruleType='RATE_LIMIT', dimension=dimension, capacity=capacity,
                 refillTokens=capacity, refillPeriodMillis=60000, concurrencyLimit=None),
            dict(ruleType='CONCURRENCY', dimension=dimension, capacity=None,
                 refillTokens=None, refillPeriodMillis=None, concurrencyLimit=concurrency)]

# C16 提交前核对（2026-10-13 C14 修订）：在 C05 之后插入一次，重复运行保持幂等。
if not any(r['operationId'] == 'C16' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'C05')
    c16 = dict(base, operationId='C16', routeCode='client-onboarding-c16',
               externalPathTemplate='/api/client/onboarding/v1/applications/{applicationId}/submission-check',
               downstreamPathTemplate='/client-onboarding/v1/applications/{applicationId}/submission-check')
    plan['routes'].insert(plan['routes'].index(base) + 1, c16)

# O10 审核材料下载（2026-10-14 文件存储确认稿）：在 O09 之后插入一次，重复运行保持幂等。
if not any(r['operationId'] == 'O10' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'O02')
    o10 = dict(base, operationId='O10', routeCode='client-onboarding-o10', httpMethod='POST', bodyLimitBytes=1024,
               externalPathTemplate='/api/operations/client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download',
               downstreamPathTemplate='/client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download')
    o09 = next(r for r in plan['routes'] if r['operationId'] == 'O09')
    plan['routes'].insert(plan['routes'].index(o09) + 1, o10)

# 租户与工作区（2026-10-16，requirements/20261016/client-console-租户与工作区 第 8 节）：
# C02 下游改到认证路径；A05 刷新失败允许清 Cookie；新增 W01 返回账号、W02 工作区首页，重复运行保持幂等。
for route in plan['routes']:
    if route['operationId'] == 'C02':
        route['downstreamPathTemplate'] = '/client-auth/v1/workspace-context'
    if route['operationId'] == 'A05':
        route['responseCookieMode'] = 'ISSUE_OR_CLEAR'
if not any(r['operationId'] == 'W01' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'A06')
    w01 = dict(base, operationId='W01', routeCode='client-onboarding-w01', responseCookieMode='ISSUE_ONLY',
               externalPathTemplate='/api/client/auth/v1/account-context',
               downstreamPathTemplate='/client-auth/v1/account-context')
    c02 = next(r for r in plan['routes'] if r['operationId'] == 'C02')
    plan['routes'].insert(plan['routes'].index(c02) + 1, w01)
if not any(r['operationId'] == 'W02' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'C01')
    w02 = dict(base, operationId='W02', routeCode='client-onboarding-w02',
               externalPathTemplate='/api/client/workspace/v1/home',
               downstreamPathTemplate='/client-workspace/v1/home')
    w01 = next(r for r in plan['routes'] if r['operationId'] == 'W01')
    plan['routes'].insert(plan['routes'].index(w01) + 1, w02)

if not any(r['operationId'] == 'D01' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'C01')
    anchor = next(r for r in plan['routes'] if r['operationId'] == 'W02')
    at = plan['routes'].index(anchor) + 1
    for offset, (op, method, path, limit) in enumerate(developer):
        plan['routes'].insert(at + offset, dict(base, operationId=op, routeCode='client-developer-' + op.lower(),
            httpMethod=method, bodyLimitBytes=limit or base['bodyLimitBytes'],
            externalPathTemplate='/api/client/developer/v1' + path,
            downstreamPathTemplate='/client-developer/v1' + path))

if not any(r['operationId'] == 'T01' for r in plan['routes']):
    base = next(r for r in plan['routes'] if r['operationId'] == 'C01')
    at = plan['routes'].index(next(r for r in plan['routes'] if r['operationId'] == 'D15')) + 1
    for offset, (op, method, path, limit) in enumerate(team):
        plan['routes'].insert(at + offset, dict(base, operationId=op, routeCode='client-team-' + op.lower(),
            httpMethod=method, bodyLimitBytes=limit or base['bodyLimitBytes'],
            externalPathTemplate='/api/client/team/v1' + path, downstreamPathTemplate='/client-team/v1' + path))
if not any(r['operationId'] == 'I01' for r in plan['routes']):
    at = plan['routes'].index(next(r for r in plan['routes'] if r['operationId'] == 'T07')) + 1
    for offset, (op, base_op, path) in enumerate(invitation):
        base = next(r for r in plan['routes'] if r['operationId'] == base_op)
        plan['routes'].insert(at + offset, dict(base, operationId=op, routeCode='client-invitation-' + op.lower(),
            httpMethod='POST', bodyLimitBytes=1024,
            externalPathTemplate='/api/client/invitations/v1' + path,
            downstreamPathTemplate='/client-invitations/v1' + path))

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
        # 2026-10-15 请求幂等迁移：Key 由 Gateway 处理，只允许浏览器携带，不再转发给 BFF 和下游。
        headers.append(dict(headerName='idempotency-key', headerUse='CORS_REQUEST_ALLOWED'))
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
