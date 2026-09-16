"""Run the complete local HTTPS protocol chain; retain only status/contract evidence."""
import http.client
from http.cookies import SimpleCookie
import json
from pathlib import Path
import re
import ssl
from urllib.parse import urlsplit

state = json.loads(Path('/tmp/client-https-current.json').read_text())
runtime = Path(state['runtime'])
origin = state['origin']
address = urlsplit(origin)
# Trust only this run's exact certificate, without disabling TLS/hostname verification.
tls = ssl.create_default_context(cafile=str(runtime / 'localhost.crt'))
email = state['protocolEmail']
password = 'Seven planets drift across quiet seas.'
replacement = 'Clouds cross a different silent valley.'
records = []
checks = []


def call(path, expected, body=None, token=None, cookie=None, request_origin=origin, extra=None):
    headers = {'Accept': 'application/json'}
    if request_origin is not None:
        headers['Origin'] = request_origin
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if cookie:
        headers['Cookie'] = cookie
    headers.update(extra or {})
    connection = http.client.HTTPSConnection(address.hostname, address.port, context=tls, timeout=15)
    connection.request('GET' if body is None else 'POST', '/api/client/auth/v1/' + path,
                       body=None if body is None else json.dumps(body), headers=headers)
    response = connection.getresponse()
    raw = response.read()
    value = json.loads(raw) if raw else None
    received = {key.lower(): val for key, val in response.getheaders()}
    record = {'path': path, 'status': response.status, 'expected': expected,
              'requestId': received.get('x-request-id', ''), 'passed': response.status == expected}
    records.append(record)
    (runtime / 'protocol-results.json').write_text(json.dumps({'requests': records, 'checks': checks}, indent=2))
    assert response.status == expected, f'{path}: expected {expected}, got {response.status}; requestId={record["requestId"]}'
    assert record['requestId'], 'Gateway request ID missing'
    if response.status >= 400:
        assert received.get('content-type', '').startswith('application/problem+json')
        assert value['requestId'] == record['requestId']
        assert value.get('type') and value.get('title') and value.get('code') and value.get('timestamp')
    connection.close()
    return value, received


def refresh_cookie(headers, cleared=False):
    value = headers['set-cookie']
    parsed = SimpleCookie()
    parsed.load(value)
    assert list(parsed) == ['__Secure-client-refresh']
    cookie = parsed['__Secure-client-refresh']
    assert cookie['secure'] and cookie['httponly'] and cookie['samesite'] == 'Lax'
    assert cookie['path'] == '/api/client/auth/v1' and not cookie['domain']
    if cleared:
        assert cookie.value == '' and cookie['max-age'] == '0'
    else:
        assert 0 < int(cookie['max-age']) <= 604800
        assert re.fullmatch(r'[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{43}', cookie.value)
    return cookie.key + '=' + cookie.value


def mail():
    connection = http.client.HTTPConnection('127.0.0.1', state['mailboxPort'], timeout=5)
    connection.request('GET', '/mail')
    response = connection.getresponse()
    assert response.status == 200, 'Expected local SMTP delivery absent'
    message = json.loads(response.read())
    connection.close()
    assert message['recipient'] == email
    match = re.search(r'challengeNo=([0-9a-f-]{36})&secret=([A-Za-z0-9_-]{43})', message['body'])
    assert match and origin in message['body']
    return {'challengeNo': match[1], 'secret': match[2]}


def login(secret=password):
    grant, headers = call('login', 200, {'email': email, 'password': secret})
    assert set(grant) == {'context', 'accessToken', 'expiresAt'}
    context = grant['context']
    assert context['contextType'] == 'ACCOUNT' and context['emailVerified'] is True
    assert context['tenantId'] is None and context['memberId'] is None
    assert context['emailMasked'] != email
    return grant, refresh_cookie(headers)


call('context', 401)
call('context', 401, extra={'X-Gateway-Realm': 'CUSTOMER', 'X-Gateway-Actor-Subject': '123'})
call('register', 403, {'email': email, 'password': password}, request_origin='https://untrusted.example.test')
call('register', 403, {'email': email, 'password': password}, request_origin=None)
call('register', 202, {'email': email, 'password': password})
link = mail()
call('login', 401, {'email': email, 'password': password})
_, verified = call('email-verifications', 200, link)
assert 'set-cookie' not in verified
call('context', 401)
call('email-verifications', 422, link)
checks.append('Email verification uses actual SMTP link once and does not log in')
first, first_cookie = login()
context, _ = call('context', 200, token=first['accessToken'])
assert context == first['context']
call('refresh', 403, {}, cookie=first_cookie, request_origin='https://untrusted.example.test')
second, headers = call('refresh', 200, {}, cookie=first_cookie)
second_cookie = refresh_cookie(headers)
assert second_cookie != first_cookie and second['context']['sessionId'] == first['context']['sessionId']
checks.append('Refresh rotates the Secure HttpOnly host cookie under the same session')
_, headers = call('refresh', 401, {}, cookie=first_cookie)
refresh_cookie(headers, cleared=True)
call('context', 401, token=second['accessToken'])
call('refresh', 401, {}, cookie=second_cookie)
checks.append('Reusing the consumed refresh token revokes its session across Gateway and Platform')
third, third_cookie = login()
call('password-reset-requests', 202, {'email': email})
reset = mail()
call('password-resets', 204, dict(reset, newPassword=replacement))
call('login', 401, {'email': email, 'password': password})
call('context', 401, token=third['accessToken'])
call('refresh', 401, {}, cookie=third_cookie)
call('password-resets', 422, dict(reset, newPassword=replacement))
checks.append('Password reset consumes its SMTP proof once and revokes old password/access/refresh sessions')
fourth, fourth_cookie = login(replacement)
_, headers = call('logout', 204, {}, token=fourth['accessToken'], cookie=fourth_cookie)
refresh_cookie(headers, cleared=True)
call('context', 401, token=fourth['accessToken'])
_, headers = call('logout', 204, {}, token=fourth['accessToken'], cookie=fourth_cookie)
refresh_cookie(headers, cleared=True)
call('refresh', 401, {}, cookie=fourth_cookie)
checks.append('Logout is idempotent, clears the exact cookie scope and invalidates the session')
checks.append('401/403/422 preserve RFC 9457 status and the Gateway request ID')
checks.append('Every HTTPS request validates the temporary certificate and localhost hostname')
result = {'runId': state['runId'], 'requests': records, 'checks': checks,
          'allPassed': all(record['passed'] for record in records)}
(runtime / 'protocol-results.json').write_text(json.dumps(result, indent=2))
print(json.dumps({'requests': len(records), 'checks': checks, 'allPassed': result['allPassed']}))
