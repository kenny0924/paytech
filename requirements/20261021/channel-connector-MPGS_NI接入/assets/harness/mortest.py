"""mor-test 运营管理接口调用：Nginx → Gateway → ops BFF → channel-connector（/api/operations/external-api-admin/v1/**）。

登录与 TLS 复用 20261017 e2e_http.py（E2E_TARGET=test），管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。
运行输出写到 MPGS_RUNTIME（仓库外目录，默认当前会话不提供时报错）；记录只含名称、方法、路径与状态码。
"""
import json, os, sys, time, urllib.error, urllib.request, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
sys.dont_write_bytecode = True
os.environ.setdefault('E2E_TARGET', 'test')
sys.path.insert(0, str(ROOT / 'requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e'))
import e2e_http as h  # noqa: E402

ADMIN = '/external-api-admin/v1'
runtime = Path(os.environ['MPGS_RUNTIME'])
results = []


def login():
    s = h.secrets()
    return h.call(h.OPERATIONS, '/api/operations/auth/v1/login', 'POST',
                  {'loginIdentifier': s['E2E_ADMIN_EMAIL'], 'password': s['E2E_ADMIN_PASSWORD']})['accessToken']


def call(name, method, path, token, body=None, expected=(200,)):
    expected = (expected,) if isinstance(expected, int) else expected
    headers = {'Origin': h.OPERATIONS, 'Accept': 'application/json', 'Authorization': 'Bearer ' + token}
    if method != 'GET':
        headers['Content-Type'] = 'application/json'
        if not path.endswith('/validate'):
            headers['Idempotency-Key'] = str(uuid.uuid4())
    data = json.dumps(body).encode() if body is not None else (b'' if method != 'GET' else None)
    for attempt in range(20):
        request = urllib.request.Request(h.OPERATIONS + '/api/operations' + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=h.TLS, timeout=90) as response:
                status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as failure:
            status, text = failure.code, failure.read().decode()
            if status == 429 and attempt < 19:
                time.sleep(min(30, int(failure.headers.get('Retry-After') or 6)))
                continue
        break
    value = json.loads(text) if text else None
    results.append({'name': name, 'method': method, 'path': path, 'status': status})
    assert status in expected, (name, status, value)
    return status, value
