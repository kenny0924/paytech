"""Manual real ingress checks. Read private synthetic credentials; emit safe summaries only."""
import json
import time
import uuid
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

runtime = Path('/tmp/plt-tenant-access-stage5')
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
results = []


def call(name, method, path, token=None, body=None, expected=200, key=None):
    headers = {'Origin': origin, 'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        headers['Idempotency-Key'] = key or uuid.uuid4().hex
    req = urllib.request.Request(origin + '/api/operations' + path, method=method, headers=headers,
                                 data=None if body is None else json.dumps(body).encode())
    for attempt in range(15):
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        if response.status != 429:
            break
        delay = min(30, max(1, int(response.headers.get('Retry-After', '6'))))
        response.read()
        time.sleep(delay)
    raw = response.read()
    value = json.loads(raw) if raw else None
    assert response.status == expected, (name, response.status, value.get('code') if isinstance(value, dict) else None)
    if expected >= 400:
        assert value['requestId'] == response.headers['X-Request-Id']
        assert 'items' not in value
    assert platform['credential'] not in raw.decode()
    results.append({'name': name, 'method': method, 'path': path, 'status': response.status})
    return value


def credential_status(expected):
    req = urllib.request.Request(
        f"http://127.0.0.1:{platform['port']}/platform-support/api-credentials/v1/validate",
        method='POST', headers={'X-Platform-Api-Credential': platform['credential'],
                                'X-Request-Id': platform['marker'] + '-i03-' + uuid.uuid4().hex[:4]})
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    response.read()
    assert response.status == expected, ('PLT-I03', response.status)
    results.append({'name': 'real-PLT-I03', 'status': response.status})


def fault(mode):
    req = urllib.request.Request(f"http://127.0.0.1:{platform['controlPort']}/fixture-state?{mode}", method='POST')
    with urllib.request.urlopen(req, timeout=5) as response:
        assert response.status == 204


def login(kind):
    return call('login-' + kind, 'POST', '/auth/v1/login',
                body={'loginIdentifier': platform[kind], 'password': platform['password']})['accessToken']


if __name__ == '__main__':
    admin, reader, member_reader, denied = [login(k) for k in ['admin', 'reader', 'memberReader', 'denied']]
    tenants = '/tenant-access-admin/v1/tenants'
    detail = tenants + '/' + platform['tenantId']
    member = detail + '/members/' + platform['memberId']
    page = call('tenant-page-one', 'GET', tenants + '?page=1&size=10', admin)
    assert page['total'] == 13 and len(page['items']) == 10
    page2 = call('tenant-page-two', 'GET', tenants + '?page=2&size=10', admin)
    assert len(page2['items']) == 3
    assert len({x['tenantId'] for x in page['items'] + page2['items']}) == 13
    assert call('client-filter', 'GET', tenants + '?tenantType=CLIENT', admin)['total'] == 7
    assert call('partner-filter', 'GET', tenants + '?tenantType=PARTNER', admin)['total'] == 6
    name = urllib.parse.quote('验收客户_100%')
    assert call('literal-name-filter', 'GET', tenants + '?tenantName=' + name, admin)['total'] == 1
    assert call('reader-detail', 'GET', detail, reader)['tenantId'] == platform['tenantId']
    call('denied-list', 'GET', tenants, denied, expected=403)
    call('internal-detail', 'GET', tenants + '/900000000000000500', admin, expected=404)
    call('reader-members-denied', 'GET', detail + '/members', reader, expected=403)
    members = call('member-reader-list', 'GET', detail + '/members', member_reader)
    assert members['total'] == 1
    value = call('member-detail', 'GET', member, member_reader)
    assert value['memberId'] == platform['memberId'] and 'emailVaultToken' not in value and 'email' not in value
    call('cross-tenant-member', 'GET', tenants + '/' + platform['partnerId'] + '/members/' + platform['memberId'], admin, expected=404)
    assert call('role-intervals', 'GET', member + '/roles', admin)['total'] == 4
    permissions = call('permission-page-one', 'GET', member + '/permissions?page=1&size=10', admin)
    assert permissions['total'] == 13 and len(permissions['items']) == 10
    assert len(call('permission-page-two', 'GET', member + '/permissions?page=2&size=10', admin)['items']) == 3
    sources = call('permission-sources', 'GET', member + '/permissions/' + platform['permissionId'] + '/sources', admin)
    assert sources['total'] == 4 and sum(x['effective'] for x in sources['items']) == 1
    assert call('empty-members', 'GET', tenants + '/' + platform['emptyTenantId'] + '/members', admin)['total'] == 0
    partner_member = tenants + '/' + platform['partnerId'] + '/members/' + platform['otherMemberId']
    assert call('empty-roles', 'GET', partner_member + '/roles', admin)['total'] == 0
    assert call('empty-permissions', 'GET', partner_member + '/permissions', admin)['total'] == 0
    call('unknown-query', 'GET', tenants + '?unknown=1', admin, expected=400)
    path = detail + '/security-status'
    prior_audit = call('audit-before-command', 'GET', '/audit/v1/events?resourceType=ACCESS_TENANT&resourceId=' + platform['tenantId'], admin)
    prior_successes = sum(x['result'] == 'SUCCEEDED' for x in prior_audit['items'])
    initial = call('current-before-command', 'GET', detail, admin)
    paused = {'securityStatus': 'SUSPENDED', 'expectedVersion': initial['version'], 'reason': '本机整链路验收暂停'}
    call('reader-pause-denied', 'POST', path, reader, paused, expected=403)
    credential_status(200)
    key = uuid.uuid4().hex
    response = call('pause', 'POST', path, admin, paused, key=key)
    credential_status(401)
    restored = call('restore', 'POST', path, admin, {'securityStatus': 'ACTIVE', 'expectedVersion': response['version'], 'reason': '本机整链路验收恢复'})
    credential_status(200)
    assert response == call('old-pause-replay', 'POST', path, admin, paused, key=key)
    current = call('current-after-replay', 'GET', detail, admin)
    assert current['securityStatus'] == 'ACTIVE' and current['version'] == restored['version']
    call('stale-version', 'POST', path, admin, paused, expected=409)
    call('same-status-noop', 'POST', path, admin, {'securityStatus': 'ACTIVE', 'expectedVersion': current['version'], 'reason': '本机同状态验收'})
    audit = call('security-audit', 'GET', '/audit/v1/events?resourceType=ACCESS_TENANT&resourceId=' + platform['tenantId'], admin)
    assert sum(x['result'] == 'SUCCEEDED' for x in audit['items']) == prior_successes + 2
    blocked = tenants + '/' + platform['blockedTenantId']
    value = call('blocked-current', 'GET', blocked, admin)
    call('restore-owner-blocked', 'POST', blocked + '/security-status', admin,
         {'securityStatus': 'ACTIVE', 'expectedVersion': value['version'], 'reason': '来源停用时仅解除安全暂停'})
    value = call('owner-restriction-remains', 'GET', blocked, admin)
    assert value['securityStatus'] == 'ACTIVE' and not value['tenantAccessAllowed']
    try:
        fault('foreign-role')
        # Owner 500 is mapped by the existing BFF dependency error contract.
        call('corrupt-source-fails-closed', 'GET', member + '/permissions', admin, expected=503)
    finally:
        fault('restore-role')
    call('source-recovered', 'GET', member + '/permissions', admin)
    (runtime / 'integration.json').write_text(json.dumps({'checks': len(results), 'status': 'PASS', 'results': results}, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'checks': len(results), 'status': 'PASS'}))
