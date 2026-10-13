"""外部 API 2e：经真实入口代理 → Gateway → BFF → Platform 的整链路检查。凭据只在 E2E_RUNTIME 私有目录，结果摘要不含令牌。"""
import json, os, time, uuid, urllib.error, urllib.request
from pathlib import Path

runtime = Path(os.environ['E2E_RUNTIME'])
platform = json.loads((runtime / 'platform.json').read_text())
origin = json.loads((runtime / 'web.json').read_text())['origin']
results = []
suffix = uuid.uuid4().hex[:6].upper()
STD, INTEGRATION, SCRIPT, API = f'E2E_PAY_{suffix}', f'E2E_DEMO_{suffix}', f'E2E_SIGN_{suffix}', 'CREATE_ORDER'
B = '/external-api-admin/v1'


def call(name, method, path, token=None, body=None, expected=200):
    headers = {'Origin': origin, 'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None and method != 'GET' and not path.endswith('/validate'):
        headers['Idempotency-Key'] = uuid.uuid4().hex
    request = urllib.request.Request(origin + '/api/operations' + path, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
    for _ in range(15):
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        if response.status != 429:
            break
        delay = min(30, max(1, int(response.headers.get('Retry-After', '6'))))
        response.read(); time.sleep(delay)
    data = response.read(); value = json.loads(data) if data else None
    assert response.status == expected, (name, response.status, value)
    if expected >= 400:
        assert value['requestId'] == response.headers['X-Request-Id'], name
    results.append({'name': name, 'method': method, 'path': path.split('?')[0], 'status': response.status})
    return value


def login(kind):
    return call('login-' + kind, 'POST', '/auth/v1/login', body={'loginIdentifier': platform[kind], 'password': platform['password']})['accessToken']


def field(location, path, data_type='STRING', required=True):
    return {'fieldLocation': location, 'fieldPath': path, 'fieldName': path, 'dataType': data_type, 'required': required, 'description': None}


standard_request = [field('BODY', 'paymentContext', 'OBJECT'), field('BODY', 'paymentContext.attemptId'),
                    field('BODY', 'paymentContext.amount'), field('BODY', 'paymentContext.vaultToken')]
standard_response = [field('BODY', 'paymentStatus'), field('BODY', 'transactionRef', required=False)]
api_request = [field('PATH', 'outletRef'), field('HEADER', 'X-Api-Version'), field('BODY', 'orderRef'), field('BODY', 'amount')]
api_response = [field('BODY', 'reference'), field('BODY', 'state')]
definition = {
    'params': [{'name': 'signingKey', 'dataType': 'PROTECTED_HANDLE', 'required': True}],
    'fixedValues': {'HEADER:X-Api-Version': {'kind': 'EXPRESSION', 'expression': "'2'"}},
    'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': 'DEMO_GATEWAY', 'pathTemplate': '/outlets/{outletRef}/orders',
                  'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
    'requestEncoding': 'JSON', 'responseDecoding': 'JSON',
    'scripts': [{'id': 'signRequest', 'scriptCode': SCRIPT, 'stage': 'REQUEST', 'params': {'key': {'kind': 'PATH', 'path': 'params.signingKey'}}}],
    'statusRules': [{'condition': "http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'", 'executeStatus': 'SUCCESS'},
                    {'condition': "http.responseStatus == 201 && http.responseBody.state == 'DECLINED'", 'executeStatus': 'FAIL'}],
    'outputs': {'SUCCESS': [{'name': 'transactionRef', 'dataType': 'STRING', 'required': True, 'value': {'kind': 'PATH', 'path': 'http.responseBody.reference'}}],
                'FAIL': [{'name': 'errorCode', 'dataType': 'STRING', 'required': False, 'value': {'kind': 'PATH', 'path': 'http.responseBody.errorCode'}}]},
}
flow = {
    'timeoutMs': 20000,
    'initializers': [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
        'vaultToken': {'kind': 'PATH', 'path': 'input.paymentContext.vaultToken'}, 'channelCode': {'kind': 'EXPRESSION', 'expression': "'DEMO'"},
        'channelAccountId': {'kind': 'PATH', 'path': 'input.paymentContext.attemptId'}, 'entityNo': {'kind': 'PATH', 'path': 'input.paymentContext.attemptId'}}}],
    'entry': 'createOrder',
    'nodes': [{'id': 'createOrder', 'integrationApi': API, 'request': {
        'PATH:outletRef': {'kind': 'PATH', 'path': 'resources.channelAccount.parameters.outletId'},
        'BODY:orderRef': {'kind': 'PATH', 'path': 'input.paymentContext.attemptId'},
        'BODY:amount': {'kind': 'PATH', 'path': 'input.paymentContext.amount'}},
        'params': {'signingKey': {'kind': 'PATH', 'path': 'resources.channelAccount.credentials.apiKey'}}}],
    'returns': [{'id': 'paid', 'executeStatus': 'SUCCESS', 'outputs': {'paymentStatus': {'kind': 'EXPRESSION', 'expression': "'PAID'"},
                                                                       'transactionRef': {'kind': 'PATH', 'path': 'steps.createOrder.outputs.transactionRef'}}},
                {'id': 'declined', 'executeStatus': 'FAIL', 'outputs': {'paymentStatus': {'kind': 'EXPRESSION', 'expression': "'DECLINED'"}}},
                {'id': 'unknown', 'executeStatus': 'ERROR'}],
    'edges': [{'from': 'createOrder', 'to': 'paid', 'condition': "steps.createOrder.executeStatus == 'SUCCESS'", 'priority': 1},
              {'from': 'createOrder', 'to': 'declined', 'condition': "steps.createOrder.executeStatus == 'FAIL'", 'priority': 2},
              {'from': 'createOrder', 'to': 'unknown', 'default': True}],
}

if __name__ == '__main__':
    admin, reader = login('admin'), login('reader')
    codes = lambda resources: {r['resourceCode'] for r in resources}
    admin_nav = codes(call('admin-navigation', 'GET', '/authorization/v1/navigation', admin)['resources'])
    reader_nav = codes(call('reader-navigation', 'GET', '/authorization/v1/navigation', reader)['resources'])
    assert {'OPERATIONS_EXTERNAL_API', 'OPERATIONS_EXTERNAL_STANDARD_APIS', 'OPERATIONS_EXTERNAL_INTEGRATIONS',
            'OPERATIONS_EXTERNAL_STANDARD_API_MANAGE', 'OPERATIONS_EXTERNAL_INTEGRATION_MANAGE'} <= admin_nav, admin_nav
    assert 'OPERATIONS_EXTERNAL_STANDARD_APIS' in reader_nav and 'OPERATIONS_EXTERNAL_STANDARD_API_MANAGE' not in reader_nav, reader_nav

    capabilities = call('capabilities', 'GET', B + '/editor-capabilities', reader)
    assert any(f['functionCode'] == 'CHANNEL_ACCOUNT_CONFIG' for f in capabilities['functions'])

    call('reader-create-denied', 'POST', B + '/standard-apis', reader,
         {'standardApiCode': STD, 'standardApiName': '支付', 'description': None, 'requestFields': [], 'responseFields': [], 'reason': 'x'}, expected=403)
    call('standard-api-create', 'POST', B + '/standard-apis', admin,
         {'standardApiCode': STD, 'standardApiName': '支付（端到端）', 'description': None, 'requestFields': standard_request,
          'responseFields': standard_response, 'reason': '2e 端到端'})
    bad = call('standard-api-invalid-422', 'POST', B + '/standard-apis', admin,
               {'standardApiCode': STD + '_BAD', 'standardApiName': '坏', 'description': None,
                'requestFields': [field('BODY', 'payment.amount')], 'responseFields': [], 'reason': 'x'}, expected=422)
    assert [e['field'] for e in bad['errors']] == ['requestFields[0].fieldPath'], bad
    call('integration-create', 'POST', B + '/integrations', admin,
         {'integrationCode': INTEGRATION, 'integrationName': 'DEMO 渠道（端到端）', 'description': None,
          'endpoints': [{'endpointCode': 'DEMO_GATEWAY', 'baseUrl': 'https://gateway.example.test/api'}], 'reason': '2e 端到端'})
    I = f'{B}/integrations/{INTEGRATION}'
    call('script-create', 'POST', I + '/scripts', admin,
         {'scriptCode': SCRIPT, 'scriptName': '请求签名', 'executionStage': 'REQUEST',
          'parameterDefinition': [{'name': 'key', 'dataType': 'PROTECTED_HANDLE', 'required': True}],
          'sourceCode': 'http.requestHeaders.signature = hmacSha256Hex(params.key, http.requestBody.orderRef)', 'description': None, 'reason': '2e 端到端'})
    broken = dict(definition, transport=dict(definition['transport'], pathTemplate='/outlets/{storeRef}/orders'))
    issues = call('api-validate-invalid', 'POST', I + '/apis/validate', admin,
                  {'apiCode': API, 'apiName': '下单', 'description': None, 'requestFields': api_request, 'responseFields': api_response,
                   'definition': broken, 'expectedVersion': None})
    assert not issues['valid'] and any(i['code'] == 'PATH_VARIABLE_MISMATCH' for i in issues['issues']), issues
    projected = call('api-create-422-paths', 'POST', I + '/apis', admin,
                     {'apiCode': API, 'apiName': '下单', 'description': None, 'requestFields': api_request, 'responseFields': api_response,
                      'definition': broken, 'reason': 'x'}, expected=422)
    assert 'definition.transport.pathTemplate' in [e['field'] for e in projected['errors']], projected
    call('api-create', 'POST', I + '/apis', admin,
         {'apiCode': API, 'apiName': '下单', 'description': None, 'requestFields': api_request, 'responseFields': api_response,
          'definition': definition, 'reason': '2e 端到端'})
    context = call('flow-editor-context', 'GET', f'{I}/flow-editor-context?standardApiCode={STD}', reader)
    assert [a['apiCode'] for a in context['apis']] == [API]
    assert [f['fixed'] for f in context['apis'][0]['requestFields']] == [False, True, False, False]
    check = call('implementation-validate', 'POST', I + '/implementations/validate', admin,
                 {'standardApiCode': STD, 'flowDefinition': flow, 'expectedVersion': None})
    assert check['valid'], check
    call('implementation-create', 'POST', I + '/implementations', admin, {'standardApiCode': STD, 'flowDefinition': flow, 'reason': '2e 端到端'})
    call('implementation-duplicate', 'POST', I + '/implementations', admin, {'standardApiCode': STD, 'flowDefinition': flow, 'reason': 'x'}, expected=400)

    assert call('standard-api-list', 'GET', f'{B}/standard-apis?keyword={STD}', reader)['items'][0]['implementedBy'][0]['integrationCode'] == INTEGRATION
    assert call('integration-list', 'GET', f'{B}/integrations?keyword={INTEGRATION}', reader)['items'][0]['apiCount'] == 1
    assert call('api-list', 'GET', I + '/apis', reader)['items'][0]['referencedBy'] == [STD]
    assert call('script-list', 'GET', I + '/scripts?executionStage=REQUEST', reader)['items'][0]['referencedApiCodes'] == [API]
    assert call('implementation-list', 'GET', I + '/implementations', reader)['items'][0]['nodeCount'] == 1
    assert call('implementation-detail', 'GET', f'{I}/implementations/{STD}', reader)['flowDefinition'] == flow

    breaking = [field('BODY', 'paymentStatus'), field('BODY', 'transactionRef')]
    call('standard-api-breaking-update', 'PUT', f'{B}/standard-apis/{STD}', admin,
         {'standardApiName': '支付', 'description': None, 'requestFields': standard_request, 'responseFields': breaking, 'expectedVersion': 1, 'reason': 'x'}, expected=400)
    call('standard-api-update', 'PUT', f'{B}/standard-apis/{STD}', admin,
         {'standardApiName': '支付（端到端，已修改）', 'description': '2e', 'requestFields': standard_request, 'responseFields': standard_response,
          'expectedVersion': 1, 'reason': '2e 端到端'})
    call('standard-api-stale', 'PUT', f'{B}/standard-apis/{STD}', admin,
         {'standardApiName': '旧', 'description': None, 'requestFields': standard_request, 'responseFields': standard_response, 'expectedVersion': 1, 'reason': 'x'}, expected=409)
    call('standard-api-delete-referenced', 'DELETE', f'{B}/standard-apis/{STD}', admin, {'expectedVersion': 2, 'reason': 'x'}, expected=400)
    call('script-delete-referenced', 'DELETE', f'{I}/scripts/{SCRIPT}', admin, {'expectedVersion': 1, 'reason': 'x'}, expected=400)
    call('reader-update-denied', 'PUT', f'{I}/implementations/{STD}', reader, {'flowDefinition': flow, 'expectedVersion': 1, 'reason': 'x'}, expected=403)
    call('unknown-query', 'GET', B + '/standard-apis?unknown=1', admin, expected=400)

    summary = {'checks': len(results), 'status': 'PASS', 'standardApi': STD, 'integration': INTEGRATION, 'results': results}
    (runtime / 'verification.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'checks': len(results), 'status': 'PASS', 'standardApi': STD, 'integration': INTEGRATION}, ensure_ascii=False))
