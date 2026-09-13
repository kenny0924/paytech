"""Representative real Web-origin -> Gateway -> BFF -> Platform acceptance, with disposable data."""
import json, time, uuid, urllib.request, urllib.error
from pathlib import Path

runtime = Path('/tmp/plt-config-stage5')
platform = json.loads((runtime/'platform.json').read_text())
web = json.loads((runtime/'web.json').read_text())
origin = web['origin']
results = []
def call(name, method, path, token=None, body=None, expected=200, key=None):
    headers = {'Origin': origin, 'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if body is not None: headers['Idempotency-Key'] = key or uuid.uuid4().hex
    request = urllib.request.Request(origin+'/api/operations'+path, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
    try: response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error: response = error
    data = response.read()
    value = json.loads(data) if data else None
    assert response.status == expected, (name, response.status, value.get('code') if isinstance(value, dict) else None)
    if expected >= 400:
        assert value['requestId'], name
        assert value['requestId'] == response.headers['X-Request-ID'], name
    results.append({'name':name, 'method':method, 'path':path, 'status':response.status})
    return value

def fixture(kind):
    with urllib.request.urlopen(f"http://127.0.0.1:{platform['controlPort']}/fixture?{kind}") as r:
        return json.load(r)['code']

def login(identity):
    return call('login-'+identity, 'POST', '/auth/v1/login', body={
        'loginIdentifier':platform[identity], 'password':platform['password']})['accessToken']

admin, reader, denied = (login(identity) for identity in ('admin','reader','denied'))
for route in ('/console-admin/v1/resources', '/console-admin/v1/resource-capabilities',
              '/gateway-admin/v1/capabilities', '/permission-admin/v1/permissions',
              '/permission-admin/v1/permission-options?usage=CONSOLE_RESOURCE',
              '/permission-admin/v1/permission-options?usage=GATEWAY_ROUTE'):
    call('metadata-'+route, 'GET', route, admin)
call('authorized-navigation', 'GET', '/authorization/v1/navigation', admin)
call('reader-resources', 'GET', '/console-admin/v1/resources', reader)
call('reader-permissions', 'GET', '/permission-admin/v1/permissions', reader)
call('no-permission-resources', 'GET', '/console-admin/v1/resources', denied, expected=403)

permission = fixture('PERMISSION')
create = {'permissionCode':permission, 'resourceType':'STAGE5_VALIDATION', 'actionCode':'READ', 'riskLevel':'LOW', 'reason':'本机综合验收'}
call('reader-cannot-write', 'POST', '/permission-admin/v1/permissions', reader, create, expected=403)
created = call('create-permission', 'POST', '/permission-admin/v1/permissions', admin, create)
assert created['status']=='INACTIVE'
call('activate-permission', 'PATCH', f'/permission-admin/v1/permissions/{permission}/status', admin,
     {'status':'ACTIVE', 'expectedVersion':1, 'reason':'本机综合验收'})
call('edit-permission', 'PUT', f'/permission-admin/v1/permissions/{permission}', admin,
     {'riskLevel':'HIGH', 'expectedVersion':2, 'reason':'调整风险级别'})
call('read-permission', 'GET', f'/permission-admin/v1/permissions/{permission}', admin)
call('permission-filter', 'GET', f'/permission-admin/v1/permissions?code={permission}', admin)
call('missing-permission-is-404', 'GET', '/permission-admin/v1/permissions/MISSING_STAGE5_PERMISSION', admin, expected=404)

resource = fixture('RESOURCE')
resource_body = {'resourceCode':resource, 'resourceType':'MENU', 'displayName':'综合验收目录', 'parentResourceId':None,
                 'componentKey':None, 'permissionId':created['objectId'], 'sortOrder':10, 'reason':'本机综合验收'}
call('create-resource', 'POST', '/console-admin/v1/resources', admin, resource_body)
edit = {key:value for key,value in resource_body.items() if key not in ('resourceCode','resourceType')}
edit.update(displayName='综合验收目录已编辑', expectedVersion=1)
key = uuid.uuid4().hex
updated = call('edit-resource', 'PUT', f'/console-admin/v1/resources/{resource}', admin, edit, key=key)
assert updated['version']==2
assert updated==call('resource-replay', 'PUT', f'/console-admin/v1/resources/{resource}', admin, edit, key=key)
call('resource-conflict', 'PUT', f'/console-admin/v1/resources/{resource}', admin, edit, expected=409)
call('activate-resource', 'PATCH', f'/console-admin/v1/resources/{resource}/status', admin,
     {'status':'ACTIVE','expectedVersion':2,'reason':'本机综合验收'})
call('resource-detail', 'GET', f'/console-admin/v1/resources/{resource}', admin)
references = call('permission-resource-references', 'GET', f'/permission-admin/v1/permissions/{permission}/references?kind=RESOURCE', admin)
assert references['total']==1
role = fixture('ROLE')
call('explicit-first-grant', 'POST', '/iam/v1/roles', admin,
     {'roleCode':role,'roleName':'综合验收授权角色','permissionIds':[created['objectId']],'reason':'显式首次授权'})

target, route = fixture('TARGET'), fixture('ROUTE')
call('create-target', 'POST', '/gateway-admin/v1/target-services', admin,
     {'targetServiceCode':target,'channel':'OPERATIONS','discoveryServiceName':'stage5-unused-service','reason':'本机综合验收'})
call('edit-target', 'PUT', f'/gateway-admin/v1/target-services/{target}', admin,
     {'discoveryServiceName':web['dynamicService'],'expectedVersion':1,'reason':'本机综合验收'})
call('activate-target', 'PATCH', f'/gateway-admin/v1/target-services/{target}/status', admin,
     {'status':'ACTIVE','expectedVersion':2,'reason':'本机综合验收'})
dynamic_path='/stage5/'+route
route_body={'routeCode':route,'channel':'OPERATIONS','httpMethod':'GET','externalPathTemplate':'/api/operations'+dynamic_path,
    'downstreamPathTemplate':'/stage5-observed-owner','targetServiceCode':target,'securityProfileCode':'PUBLIC',
    'authorizationMode':'PUBLIC','permissionId':None,'requiredScopeCode':None,'requestCookieMode':'DROP','responseCookieMode':'NONE',
    'bodyLimitBytes':0,'bodyReceiveTimeoutMillis':5000,'responseTimeoutMillis':10000,
    'headers':[{'headerName':'origin','headerUse':'FORWARD_REQUEST'}],'queryParameterNames':[],
    'guardRules':[{'ruleType':'RATE_LIMIT','dimension':'SHARED_ROUTE','capacity':300,'refillTokens':300,'refillPeriodMillis':60000,'concurrencyLimit':None},
                  {'ruleType':'CONCURRENCY','dimension':'SHARED_ROUTE','capacity':None,'refillTokens':None,'refillPeriodMillis':None,'concurrencyLimit':20}],
    'reason':'本机综合验收'}
call('create-route', 'POST', '/gateway-admin/v1/routes', admin, route_body)
route_edit={k:v for k,v in route_body.items() if k not in ('routeCode','channel')}
route_edit.update(expectedVersion=1,queryParameterNames=['detail'])
call('edit-route', 'PUT', f'/gateway-admin/v1/routes/{route}', admin, route_edit)
call('activate-route', 'PATCH', f'/gateway-admin/v1/routes/{route}/status', admin,
     {'status':'ACTIVE','expectedVersion':2,'reason':'本机综合验收'})
time.sleep(2)
observed=call('database-snapshot-dynamic-forwarding','GET',dynamic_path+'?detail=full')
assert observed['owner']=='stage5-dynamic-target'
call('disable-route','PATCH',f'/gateway-admin/v1/routes/{route}/status',admin,
     {'status':'DISABLED','expectedVersion':3,'reason':'本机综合验收'})
time.sleep(2)
call('disabled-route-unavailable','GET',dynamic_path,expected=404)

# 登记的新权限经显式角色分配后，现有人员的新会话可见资源；停用与恢复沿用同一关联。
before = call('new-resource-hidden-before-grant', 'GET', '/authorization/v1/navigation', reader)
assert resource not in json.dumps(before)
reader_id = call('reader-identity', 'GET', '/auth/v1/context', reader)['accountId']
account = next(a for a in call('accounts-for-explicit-grant', 'GET', '/iam/v1/accounts', admin)['items'] if a['accountId']==reader_id)
role_id = next(r['roleId'] for r in call('roles-for-explicit-grant', 'GET', '/iam/v1/roles', admin)['items'] if r['roleCode']==role)
call('assign-created-role-to-person', 'PUT', f'/iam/v1/accounts/{reader_id}/roles', admin,
     {'expectedMemberVersion':account['memberVersion'], 'roleIds':[r['roleId'] for r in account['roles']]+[role_id], 'reason':'本机首次授权闭环'}, expected=204)
reader = login('reader')
assert resource in json.dumps(call('new-resource-visible-after-grant', 'GET', '/authorization/v1/navigation', reader))
call('disable-created-permission', 'PATCH', f'/permission-admin/v1/permissions/{permission}/status', admin,
     {'status':'INACTIVE','expectedVersion':3,'reason':'本机停用验证'})
assert resource not in json.dumps(call('resource-hidden-after-permission-disabled', 'GET', '/authorization/v1/navigation', reader))
call('restore-created-permission', 'PATCH', f'/permission-admin/v1/permissions/{permission}/status', admin,
     {'status':'ACTIVE','expectedVersion':4,'reason':'本机恢复验证'})
assert resource in json.dumps(call('resource-restored-with-original-grant', 'GET', '/authorization/v1/navigation', reader))
summary={'boundary':'real Web ingress/Gateway/BFF/Platform/MySQL/Redis; dynamic Target ends at synthetic HTTP probe; local discovery',
         'results':results,'fixtures':{'permission':permission,'resource':resource,'role':role,'target':target,'route':route}}
(runtime/'integration.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps({'checks':len(results),'passed':True},ensure_ascii=False))
