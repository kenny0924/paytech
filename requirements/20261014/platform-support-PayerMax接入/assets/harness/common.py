"""PayerMax 接入 harness 公共函数：读取运行文件、经真实入口（Web 代理 → Gateway → BFF → Platform）调用管理接口、直连 Platform 内部执行接口。
凭据只在 E2E_RUNTIME 私有目录；记录的结果只含名称、方法、路径与状态码。"""
import json, os, time, uuid, urllib.error, urllib.request
from pathlib import Path

runtime = Path(os.environ['E2E_RUNTIME'])
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
internal = f"http://127.0.0.1:{platform['port']}/platform-support"
ADMIN = '/external-api-admin/v1'
results = []


def send(name, url, method='GET', headers=None, body=None, expected=(200,)):
    expected = (expected,) if isinstance(expected, int) else expected
    request = urllib.request.Request(url, method=method, headers={'Content-Type': 'application/json', **(headers or {})},
                                     data=None if body is None else json.dumps(body).encode())
    for _ in range(15):
        try:
            response = urllib.request.urlopen(request, timeout=70)
        except urllib.error.HTTPError as error:
            response = error
        if response.status != 429:
            break
        delay = min(30, max(1, int(response.headers.get('Retry-After', '6'))))
        response.read()
        time.sleep(delay)
    data = response.read()
    value = json.loads(data) if data and 'json' in response.headers.get('Content-Type', '') else None
    assert response.status in expected, (name, response.status, value)
    results.append({'name': name, 'method': method, 'path': url.split('?')[0].replace(origin, '').replace(internal, '[internal]'),
                    'status': response.status})
    return response.status, value


def login():
    return call('login-admin', 'POST', '/auth/v1/login', body={'loginIdentifier': platform['admin'], 'password': platform['password']})[1]['accessToken']


def call(name, method, path, token=None, body=None, expected=(200,)):
    headers = {'Origin': origin}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None and method != 'GET' and not path.endswith('/validate'):
        headers['Idempotency-Key'] = uuid.uuid4().hex
    return send(name, origin + '/api/operations' + path, method, headers, body, expected)


def execute(name, standard_api, integration, payload, expected=200):
    return send(name, f'{internal}/standard-apis/v1/{standard_api}/execute', 'POST', None,
                {'integrationCode': integration, 'input': payload}, expected)[1]
