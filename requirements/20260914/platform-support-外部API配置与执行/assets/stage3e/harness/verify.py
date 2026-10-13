"""外部 API 3e：经真实入口（Web 代理 → Gateway → BFF → Platform）保存带补查分支的 PAY 实现，
再直连 Platform 内部执行接口走通成功、拒绝、补查三条路径。凭据只在 E2E_RUNTIME 私有目录，回执不含令牌与密钥。"""
import json, os, time, uuid, urllib.error, urllib.request
from pathlib import Path

runtime = Path(os.environ['E2E_RUNTIME'])
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
channel_port = json.loads((runtime / 'channel.json').read_text())['port']
journal = runtime / 'channel-journal.jsonl'
internal = f"http://127.0.0.1:{platform['port']}/platform-support"
results = []
suffix = uuid.uuid4().hex[:6].upper()
STD, INTEGRATION, SCRIPT = f'E3_PAY_{suffix}', f'E3_DEMO_{suffix}', f'E3_SIGN_{suffix}'
CREATE, QUERY = 'CREATE_ORDER', 'QUERY_ORDER'
B = '/external-api-admin/v1'


def send(name, url, method='GET', headers=None, body=None, expected=200):
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
        response.read(); time.sleep(delay)
    data = response.read()
    value = json.loads(data) if data and response.headers.get('Content-Type', '').find('json') >= 0 else None
    assert response.status == expected, (name, response.status, value)
    results.append({'name': name, 'method': method, 'path': url.split('?')[0].replace(origin, '').replace(internal, '[internal]'),
                    'status': response.status})
    return value


def call(name, method, path, token=None, body=None, expected=200):
    headers = {'Origin': origin}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None and method != 'GET' and not path.endswith('/validate'):
        headers['Idempotency-Key'] = uuid.uuid4().hex
    return send(name, origin + '/api/operations' + path, method, headers, body, expected)


def execute(name, integration, account, expected=200, input_value=None):
    payload = input_value or {'paymentContext': {'attemptId': account['attemptId'], 'amount': '10.00',
                                                 'vaultToken': account['vaultToken']}}
    return send(name, f'{internal}/standard-apis/v1/{STD}/execute', 'POST', None,
                {'integrationCode': integration, 'input': payload}, expected)


def field(location, path, data_type='STRING', required=True):
    return {'fieldLocation': location, 'fieldPath': path, 'fieldName': path, 'dataType': data_type, 'required': required, 'description': None}


def binding(path):
    return {'kind': 'PATH', 'path': path}


def constant(text):
    return {'kind': 'EXPRESSION', 'expression': f"'{text}'"}


standard_request = [field('BODY', 'paymentContext', 'OBJECT'), field('BODY', 'paymentContext.attemptId'),
                    field('BODY', 'paymentContext.amount'), field('BODY', 'paymentContext.vaultToken')]
standard_response = [field('BODY', 'paymentStatus'), field('BODY', 'transactionRef', required=False)]
create_definition = {
    'params': [{'name': 'signingKey', 'dataType': 'PROTECTED_HANDLE', 'required': True}],
    'fixedValues': {'HEADER:X-Api-Version': constant('2')},
    'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': 'DEMO_GATEWAY', 'pathTemplate': '/outlets/{outletRef}/orders',
                  'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
    'requestEncoding': 'JSON', 'responseDecoding': 'JSON',
    'scripts': [{'id': 'signRequest', 'scriptCode': SCRIPT, 'stage': 'REQUEST', 'params': {'key': binding('params.signingKey')}}],
    'statusRules': [{'condition': "http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'", 'executeStatus': 'SUCCESS'},
                    {'condition': "http.responseStatus == 201 && http.responseBody.state == 'DECLINED'", 'executeStatus': 'FAIL'}],
    'outputs': {'SUCCESS': [{'name': 'transactionRef', 'dataType': 'STRING', 'required': True, 'value': binding('http.responseBody.reference')}],
                'FAIL': [{'name': 'errorCode', 'dataType': 'STRING', 'required': False, 'value': binding('http.responseBody.errorCode')}]},
}
query_definition = {
    'params': [], 'fixedValues': {},
    'transport': {'protocol': 'HTTP', 'method': 'GET', 'endpointCode': 'DEMO_GATEWAY', 'pathTemplate': '/orders/{orderRef}',
                  'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
    'requestEncoding': 'NONE', 'responseDecoding': 'JSON', 'scripts': [],
    'statusRules': [{'condition': "http.responseStatus == 200 && http.responseBody.state == 'CAPTURED'", 'executeStatus': 'SUCCESS'}],
    'outputs': {'SUCCESS': [{'name': 'transactionRef', 'dataType': 'STRING', 'required': True, 'value': binding('http.responseBody.reference')}],
                'FAIL': []},
}
status = lambda node, value: f"steps.{node}.executeStatus == '{value}'"
flow = {
    'timeoutMs': 20000,
    'initializers': [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
        'vaultToken': binding('input.paymentContext.vaultToken'), 'channelCode': constant('DEMO'),
        'channelAccountId': binding('input.paymentContext.attemptId'), 'entityNo': binding('input.paymentContext.attemptId')}}],
    'entry': 'createOrder',
    'nodes': [{'id': 'createOrder', 'integrationApi': CREATE, 'request': {
                   'PATH:outletRef': binding('resources.channelAccount.parameters.outletId'),
                   'BODY:orderRef': binding('input.paymentContext.attemptId'), 'BODY:amount': binding('input.paymentContext.amount')},
               'params': {'signingKey': binding('resources.channelAccount.credentials.apiKey')}},
              {'id': 'queryOrder', 'integrationApi': QUERY, 'request': {'PATH:orderRef': binding('input.paymentContext.attemptId')}}],
    'returns': [{'id': 'paid', 'executeStatus': 'SUCCESS', 'outputs': {'paymentStatus': constant('PAID'),
                                                                       'transactionRef': binding('steps.createOrder.outputs.transactionRef')}},
                {'id': 'declined', 'executeStatus': 'FAIL', 'outputs': {'paymentStatus': constant('DECLINED')}},
                {'id': 'paidAfterQuery', 'executeStatus': 'SUCCESS', 'outputs': {'paymentStatus': constant('PAID'),
                                                                                 'transactionRef': binding('steps.queryOrder.outputs.transactionRef')}},
                {'id': 'unknown', 'executeStatus': 'ERROR'}],
    'edges': [{'from': 'createOrder', 'to': 'paid', 'condition': status('createOrder', 'SUCCESS'), 'priority': 1},
              {'from': 'createOrder', 'to': 'declined', 'condition': status('createOrder', 'FAIL'), 'priority': 2},
              {'from': 'createOrder', 'to': 'queryOrder', 'condition': status('createOrder', 'ERROR'), 'priority': 3},
              {'from': 'createOrder', 'to': 'unknown', 'default': True},
              {'from': 'queryOrder', 'to': 'paidAfterQuery', 'condition': status('queryOrder', 'SUCCESS'), 'priority': 1},
              {'from': 'queryOrder', 'to': 'unknown', 'default': True}],
}


def journal_lines():
    return [json.loads(line) for line in journal.read_text().splitlines()] if journal.exists() else []


if __name__ == '__main__':
    admin = call('login-admin', 'POST', '/auth/v1/login', body={'loginIdentifier': platform['admin'], 'password': platform['password']})['accessToken']
    call('standard-api-create', 'POST', B + '/standard-apis', admin,
         {'standardApiCode': STD, 'standardApiName': '支付（3e 端到端）', 'description': None, 'requestFields': standard_request,
          'responseFields': standard_response, 'reason': '3e 端到端'})
    call('integration-create', 'POST', B + '/integrations', admin,
         {'integrationCode': INTEGRATION, 'integrationName': 'DEMO 渠道（3e）', 'description': None,
          'endpoints': [{'endpointCode': 'DEMO_GATEWAY', 'baseUrl': f'http://127.0.0.1:{channel_port}/api'}], 'reason': '3e 端到端'})
    I = f'{B}/integrations/{INTEGRATION}'
    call('script-create', 'POST', I + '/scripts', admin,
         {'scriptCode': SCRIPT, 'scriptName': '请求签名', 'executionStage': 'REQUEST',
          'parameterDefinition': [{'name': 'key', 'dataType': 'PROTECTED_HANDLE', 'required': True}],
          'sourceCode': 'http.requestHeaders.signature = hmacSha256Hex(params.key, http.requestBody.orderRef)',
          'description': None, 'reason': '3e 端到端'})
    rejected = call('script-unsafe-422', 'POST', I + '/scripts', admin,
                    {'scriptCode': SCRIPT + '_X', 'scriptName': '越权', 'executionStage': 'REQUEST', 'parameterDefinition': [],
                     'sourceCode': 'eval("1")', 'description': None, 'reason': 'x'}, expected=422)
    assert [e['field'] for e in rejected['errors']] == ['sourceCode'], rejected
    call('api-create-order', 'POST', I + '/apis', admin,
         {'apiCode': CREATE, 'apiName': '下单', 'description': None,
          'requestFields': [field('PATH', 'outletRef'), field('HEADER', 'X-Api-Version'), field('BODY', 'orderRef'), field('BODY', 'amount')],
          'responseFields': [field('BODY', 'reference', required=False), field('BODY', 'state')],
          'definition': create_definition, 'reason': '3e 端到端'})
    call('api-query-order', 'POST', I + '/apis', admin,
         {'apiCode': QUERY, 'apiName': '查询订单', 'description': None, 'requestFields': [field('PATH', 'orderRef')],
          'responseFields': [field('BODY', 'reference'), field('BODY', 'state')], 'definition': query_definition, 'reason': '3e 端到端'})
    check = call('implementation-validate', 'POST', I + '/implementations/validate', admin,
                 {'standardApiCode': STD, 'flowDefinition': flow, 'expectedVersion': None})
    assert check['valid'], check
    call('implementation-create', 'POST', I + '/implementations', admin, {'standardApiCode': STD, 'flowDefinition': flow, 'reason': '3e 端到端'})
    send('gateway-does-not-route-execute', origin + f'/api/operations/standard-apis/v1/{STD}/execute', 'POST',
         {'Origin': origin, 'Authorization': 'Bearer ' + admin}, {'integrationCode': INTEGRATION, 'input': {}}, expected=404)

    paid_account, declined_account, unknown_account = platform['accounts']
    before = len(journal_lines())
    send('execute-unknown-integration-404', f'{internal}/standard-apis/v1/{STD}/execute', 'POST', None,
         {'integrationCode': 'E3_NOPE', 'input': {}}, expected=404)
    execute('execute-invalid-input-422', INTEGRATION, paid_account, expected=422,
            input_value={'paymentContext': {'attemptId': '1', 'amount': '10.00'}})
    assert len(journal_lines()) == before, '前置 Problem 不应发出渠道请求'

    paid = execute('execute-success', INTEGRATION, paid_account)
    assert paid == {'executeStatus': 'SUCCESS', 'outputs': {'paymentStatus': 'PAID',
                                                           'transactionRef': 'ref-' + paid_account['attemptId']}}, paid
    declined = execute('execute-declined', INTEGRATION, declined_account)
    assert declined == {'executeStatus': 'FAIL', 'outputs': {'paymentStatus': 'DECLINED'}}, declined
    recovered = execute('execute-recovered-by-query', INTEGRATION, unknown_account)
    assert recovered == {'executeStatus': 'SUCCESS', 'outputs': {'paymentStatus': 'PAID',
                                                                'transactionRef': 'ref-' + unknown_account['attemptId']}}, recovered

    traffic = journal_lines()[before:]
    assert [(t['method'], t['status']) for t in traffic] == [('POST', 201), ('POST', 201), ('POST', 504), ('GET', 200)], traffic
    assert all(t['signatureValid'] for t in traffic if t['method'] == 'POST'), traffic

    summary = {'checks': len(results), 'status': 'PASS', 'standardApi': STD, 'integration': INTEGRATION,
               'channelTraffic': [{'method': t['method'], 'status': t['status'], 'signatureValid': t['signatureValid']} for t in traffic],
               'results': results}
    (runtime / 'verification-3e.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: summary[k] for k in ('checks', 'status', 'standardApi', 'integration', 'channelTraffic')}, ensure_ascii=False))
