"""Manual lightweight checks of the approved local test release; no business writes."""
import datetime
import hashlib
import json
from pathlib import Path
import re
import sys
import urllib.request

root = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(root / 'test-environment/scripts'))
from smoke import Browser, check_page

out = Path(__file__).parent
manifest = json.loads((out.parent / 'stage6/release-manifest.json').read_text())
checks = []
client = Browser()
access = (root / 'test-environment/private/access.md').read_text()
identifier = re.search(r'^账号：(.+)$', access, re.M)[1].strip()
password = re.search(r'^密码：(.+)$', access, re.M)[1].strip()
try:
    client.login(identifier, password)
    checks.append({'name': 'login', 'status': 200})
    access = password = None
    navigation = client.request('GET', '/authorization/v1/navigation')
    codes = {item['resourceCode'] for item in navigation['resources']}
    expected = {'OPERATIONS_TENANT_ACCESS', 'OPERATIONS_TENANT_ACCESS_MANAGE', 'OPERATIONS_TENANT_MEMBER_READ'}
    assert expected <= codes
    names = {item['resourceCode']: item['displayName'] for item in navigation['resources']}
    assert names['OPERATIONS_TENANT_ACCESS'] == '租户访问'
    assert names['OPERATIONS_TENANT_ACCESS_MANAGE'] == '安全暂停与恢复'
    assert names['OPERATIONS_TENANT_MEMBER_READ'] == '查看成员权限'
    checks.append({'name': 'navigation-new-resources', 'status': 200, 'resources': sorted(expected)})
    base = '/tenant-access-admin/v1/tenants'
    for suffix in ['', '?tenantType=CLIENT', '?tenantType=PARTNER']:
        page = client.request('GET', base + suffix)
        check_page(page)
        assert page['items'] == [] and page['total'] == 0
        checks.append({'name': 'tenant-list' + suffix, 'status': 200, 'total': 0})
    for tenant_id in ['999999999999999998', '900000000000000500']:
        client.request_problem('GET', base + '/' + tenant_id, 'NOT_FOUND', 404)
        checks.append({'name': 'tenant-not-found-' + ('internal' if tenant_id.endswith('500') else 'unknown'), 'status': 404, 'problemAndRequestIdVerified': True})
    for path in ['/iam/v1/accounts', '/iam/v1/roles', '/gateway-admin/v1/routes', '/audit/v1/events', '/decision-rule-sets/v1/rule-sets']:
        body = client.request('GET', path)
        check_page(body)
        checks.append({'name': 'existing-query', 'path': path, 'status': 200, 'total': body['total']})
finally:
    if client.access:
        client.request('POST', '/auth/v1/logout', {'refreshToken': client.refresh_token}, status=(200, 204))
        checks.append({'name': 'logout', 'status': '200-or-204'})
        client.access = client.refresh_token = None
for path, sha in manifest['webFiles'].items():
    with urllib.request.urlopen('http://localhost:28080/' + path, timeout=15) as response:
        assert response.status == 200
        assert hashlib.sha256(response.read()).hexdigest() == sha, path
result = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'status': 'PASS', 'checks': checks, 'publicStaticFilesMatched': len(manifest['webFiles']), 'businessWrites': 0, 'tokensPersisted': False, 'limitations': 'No CLIENT/PARTNER tenant or member fixtures in test; full business flow evidence remains in stage 5.'}
(out / 'public-checks.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result))
