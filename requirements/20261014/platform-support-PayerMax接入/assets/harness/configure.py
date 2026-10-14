"""PayerMax 接入阶段 3：经真实管理接口保存（存在则按版本修改）标准 API PAY、PAY_QUERY 与一个 PayerMax 接入方的
出站地址、签名/验签脚本、ORDER_AND_PAY、ORDER_QUERY 原子 API 及两条流程实现。配置内容见技术方案第 4 节。

用法：python3 configure.py <接入方编码> <出站 base URL>
  本机合成：python3 configure.py PAYERMAX_MOCK http://127.0.0.1:<端口>/aggregate-pay/api/gateway
  UAT：     python3 configure.py PAYERMAX https://pay-gate-uat.payermax.com/aggregate-pay/api/gateway
"""
import json, sys
from common import ADMIN, call, login, results, runtime

REASON = 'PayerMax 接入（requirements/20261014）'
ENDPOINT = 'PAYERMAX_GATEWAY'
SIGN, VERIFY = 'PAYERMAX_SIGN', 'PAYERMAX_VERIFY'
ORDER_AND_PAY, ORDER_QUERY = 'ORDER_AND_PAY', 'ORDER_QUERY'
# 明确未受理的下单错误码（技术方案第 4.4 节）；ORDER_REPEAT、SYSTEM_ERROR 等与未知码归 ERROR。
REJECT_CODES = ['PARAMS_INVALID', 'REQUEST_PARAM_INVALID', 'AMOUNT_INVALID', 'COUNTRY_INVALID', 'CURRENCY_INVALID',
                'CONTRACT_INVALID', 'MERCHANT_INVALID', 'MERCHANT_APP_INVALID', 'SIGN_VERIFY_FAILED', 'PAYMENT_METHOD_NOT_EXIST',
                'PAYMENT_METHOD_SUSPEND', 'AMOUNT_LIMIT', 'AMOUNT_LIMIT_MINIMUM', 'AMOUNT_LIMIT_MAXIMUM', 'ONBOARD_ERROR']


def field(path, data_type='STRING', required=True, location='BODY'):
    return {'fieldLocation': location, 'fieldPath': path, 'fieldName': path, 'dataType': data_type, 'required': required, 'description': None}


def path(value):
    return {'kind': 'PATH', 'path': value}


def expr(value):
    return {'kind': 'EXPRESSION', 'expression': value}


def const(text):
    return expr(f"'{text}'")


def output(name, value, data_type='STRING', required=True):
    return {'name': name, 'dataType': data_type, 'required': required, 'value': value}


CHANNEL_ACCOUNT = [field('channelAccount', 'OBJECT'), field('channelAccount.channelCode'), field('channelAccount.channelAccountId'),
                   field('channelAccount.entityNo'), field('channelAccount.accountVaultToken')]

PAY_REQUEST = CHANNEL_ACCOUNT + [
    field('payment', 'OBJECT'), field('payment.attemptId'), field('payment.amount', 'OBJECT'), field('payment.amount.value', 'DECIMAL'),
    field('payment.amount.currency'), field('payment.subject'), field('payment.payerReference'), field('payment.returnUrl'),
    field('payment.country', required=False), field('payment.language', required=False), field('payment.notifyUrl', required=False),
    field('payment.expireSeconds', required=False)]
PAY_RESPONSE = [field('paymentStatus'), field('nextAction', 'OBJECT', False), field('nextAction.type', required=False),
                field('nextAction.redirectUrl', required=False), field('channelTransactionRef', required=False),
                field('channelStatus', required=False), field('failureCode', required=False), field('failureMessage', required=False)]
QUERY_REQUEST = CHANNEL_ACCOUNT + [field('payment', 'OBJECT'), field('payment.attemptId')]
QUERY_RESPONSE = [field('paymentStatus'), field('channelTransactionRef', required=False), field('channelStatus', required=False),
                  field('amount', 'OBJECT', False), field('amount.value', 'DECIMAL', False), field('amount.currency', required=False),
                  field('paymentMethodType', required=False), field('failureCode', required=False), field('failureMessage', required=False)]

SIGN_SOURCE = """json.set(http.requestBody, 'requestTime', nowRfc3339());
http.requestHeaders.sign = rsaSha256Sign(params.privateKey, sealJsonBody(http));"""
VERIFY_SOURCE = """if (http.responseStatus == 200) {
  requireRsaSha256Signature(params.publicKey, http.responseRaw, http.responseHeaders.sign);
}"""

ENVELOPE = [field('version'), field('keyVersion'), field('requestTime', required=False), field('appId'), field('merchantNo'),
            field('data', 'OBJECT', False)]
ORDER_AND_PAY_REQUEST = ENVELOPE + [
    field('data.outTradeNo'), field('data.integrate'), field('data.subject'), field('data.totalAmount', 'DECIMAL'), field('data.currency'),
    field('data.userId'), field('data.frontCallbackUrl'), field('data.country', required=False), field('data.language', required=False),
    field('data.reference', required=False), field('data.notifyUrl', required=False), field('data.expireTime', required=False)]
ORDER_AND_PAY_RESPONSE = [field('code'), field('msg', required=False), field('data', 'OBJECT', False), field('data.redirectUrl', required=False),
                          field('data.outTradeNo', required=False), field('data.tradeToken', required=False), field('data.status', required=False)]
ORDER_QUERY_REQUEST = ENVELOPE + [field('data.outTradeNo')]
ORDER_QUERY_RESPONSE = [field('code'), field('msg', required=False), field('data', 'OBJECT', False), field('data.outTradeNo', required=False),
                        field('data.tradeToken', required=False), field('data.totalAmount', 'DECIMAL', False),
                        field('data.currency', required=False), field('data.status', required=False),
                        field('data.resultCode', required=False), field('data.resultMsg', required=False)]


def definition(path_template, status_rules, outputs, fixed):
    return {
        'params': [{'name': 'merchantPrivateKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                   {'name': 'payermaxPublicKey', 'dataType': 'STRING', 'required': True}],
        'fixedValues': {'BODY:version': const('1.5'), **fixed},
        'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': ENDPOINT, 'pathTemplate': path_template,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON', 'responseDecoding': 'JSON', 'decimalEncoding': 'NUMBER',
        'scripts': [{'id': 'sign', 'scriptCode': SIGN, 'stage': 'REQUEST', 'params': {'privateKey': path('params.merchantPrivateKey')}},
                    {'id': 'verify', 'scriptCode': VERIFY, 'stage': 'RESPONSE', 'params': {'publicKey': path('params.payermaxPublicKey')}}],
        'statusRules': status_rules,
        'outputs': outputs,
    }


OK = "http.responseStatus == 200 && http.responseBody.code == 'APPLY_SUCCESS'"
FAIL_OUTPUTS = [output('errorCode', path('http.responseBody.code')), output('errorMessage', path('http.responseBody.msg'), required=False)]
ORDER_AND_PAY_DEFINITION = definition('/orderAndPay', [
    {'condition': OK + " && http.responseBody.data.status == 'PENDING' && http.responseBody.data.redirectUrl != nil", 'executeStatus': 'SUCCESS'},
    {'condition': 'http.responseStatus == 200 && (' + ' || '.join(f"http.responseBody.code == '{code}'" for code in REJECT_CODES) + ')',
     'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('redirectUrl', path('http.responseBody.data.redirectUrl')), output('tradeToken', path('http.responseBody.data.tradeToken')),
                 output('channelStatus', path('http.responseBody.data.status'))],
     'FAIL': FAIL_OUTPUTS},
    {'BODY:data.integrate': const('Hosted_Checkout')})
DETAILS = 'http.responseBody.data.paymentDetails'
ORDER_QUERY_DEFINITION = definition('/orderQuery', [
    {'condition': OK + " && (" + ' || '.join(f"http.responseBody.data.status == '{s}'" for s in ('SUCCESS', 'PENDING', 'FAILED', 'CLOSED')) + ')',
     'executeStatus': 'SUCCESS'},
    {'condition': "http.responseStatus == 200 && http.responseBody.code == 'ORDER_NOT_EXIST'", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('channelStatus', path('http.responseBody.data.status')),
                 output('tradeToken', path('http.responseBody.data.tradeToken'), required=False),
                 output('amount', path('http.responseBody.data.totalAmount'), 'DECIMAL', False),
                 output('currency', path('http.responseBody.data.currency'), required=False),
                 output('resultCode', path('http.responseBody.data.resultCode'), required=False),
                 output('resultMsg', path('http.responseBody.data.resultMsg'), required=False),
                 output('paymentMethodType', expr(f'{DETAILS} == nil || count({DETAILS}) == 0 ? nil : #{DETAILS}[0].paymentMethodType'),
                        required=False)],
     'FAIL': FAIL_OUTPUTS},
    {})

INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
ACCOUNT = 'resources.channelAccount.parameters.'
ENVELOPE_VALUES = {'BODY:keyVersion': path(ACCOUNT + 'keyVersion'), 'BODY:appId': path(ACCOUNT + 'appId'),
                   'BODY:merchantNo': path(ACCOUNT + 'merchantNo')}
NODE_PARAMS = {'merchantPrivateKey': path('resources.channelAccount.credentials.merchantPrivateKey'),
               'payermaxPublicKey': path(ACCOUNT + 'payermaxPublicKey')}


def status(node, value):
    return f"steps.{node}.executeStatus == '{value}'"


PAY_FLOW = {
    'timeoutMs': 25000, 'initializers': INITIALIZER, 'entry': 'createOrder',
    'nodes': [{'id': 'createOrder', 'integrationApi': ORDER_AND_PAY, 'params': NODE_PARAMS, 'request': {
        **ENVELOPE_VALUES,
        'BODY:data.outTradeNo': path('input.payment.attemptId'), 'BODY:data.subject': path('input.payment.subject'),
        'BODY:data.totalAmount': path('input.payment.amount.value'), 'BODY:data.currency': path('input.payment.amount.currency'),
        'BODY:data.userId': path('input.payment.payerReference'), 'BODY:data.frontCallbackUrl': path('input.payment.returnUrl'),
        'BODY:data.country': path('input.payment.country'), 'BODY:data.language': path('input.payment.language'),
        'BODY:data.notifyUrl': path('input.payment.notifyUrl'), 'BODY:data.expireTime': path('input.payment.expireSeconds')}}],
    'returns': [
        {'id': 'created', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': const('PENDING'), 'nextAction.type': const('REDIRECT'),
            'nextAction.redirectUrl': path('steps.createOrder.outputs.redirectUrl'),
            'channelTransactionRef': path('steps.createOrder.outputs.tradeToken'), 'channelStatus': path('steps.createOrder.outputs.channelStatus')}},
        {'id': 'rejected', 'executeStatus': 'FAIL', 'outputs': {
            'paymentStatus': const('FAILED'), 'failureCode': path('steps.createOrder.outputs.errorCode'),
            'failureMessage': path('steps.createOrder.outputs.errorMessage')}},
        {'id': 'unknown', 'executeStatus': 'ERROR'}],
    'edges': [{'from': 'createOrder', 'to': 'created', 'condition': status('createOrder', 'SUCCESS'), 'priority': 1},
              {'from': 'createOrder', 'to': 'rejected', 'condition': status('createOrder', 'FAIL'), 'priority': 2},
              {'from': 'createOrder', 'to': 'unknown', 'default': True}],
}
Q = 'steps.queryOrder.outputs.'
QUERY_FLOW = {
    'timeoutMs': 15000, 'initializers': INITIALIZER, 'entry': 'queryOrder',
    'nodes': [{'id': 'queryOrder', 'integrationApi': ORDER_QUERY, 'params': NODE_PARAMS,
               'request': {**ENVELOPE_VALUES, 'BODY:data.outTradeNo': path('input.payment.attemptId')}}],
    'returns': [
        {'id': 'found', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': expr(f"{Q}channelStatus == 'SUCCESS' ? 'SUCCEEDED' : {Q}channelStatus"),
            'channelTransactionRef': path(Q + 'tradeToken'), 'channelStatus': path(Q + 'channelStatus'),
            'amount.value': path(Q + 'amount'), 'amount.currency': path(Q + 'currency'),
            'paymentMethodType': path(Q + 'paymentMethodType'), 'failureCode': path(Q + 'resultCode'),
            'failureMessage': expr(f"{Q}resultMsg == '' ? nil : {Q}resultMsg")}},
        {'id': 'notFound', 'executeStatus': 'FAIL', 'outputs': {
            'paymentStatus': const('NOT_FOUND'), 'failureCode': path(Q + 'errorCode'), 'failureMessage': path(Q + 'errorMessage')}},
        {'id': 'unknown', 'executeStatus': 'ERROR'}],
    'edges': [{'from': 'queryOrder', 'to': 'found', 'condition': status('queryOrder', 'SUCCESS'), 'priority': 1},
              {'from': 'queryOrder', 'to': 'notFound', 'condition': status('queryOrder', 'FAIL'), 'priority': 2},
              {'from': 'queryOrder', 'to': 'unknown', 'default': True}],
}


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token,
                {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def configure(integration, base_url):
    token = login()
    for code, label, request, response in (('PAY', '支付', PAY_REQUEST, PAY_RESPONSE),
                                           ('PAY_QUERY', '支付查询', QUERY_REQUEST, QUERY_RESPONSE)):
        body = {'standardApiName': label, 'description': '渠道无关标准 API（草案，PayerMax 接入）', 'requestFields': request,
                'responseFields': response}
        upsert(token, 'standard-api-' + code, f'{ADMIN}/standard-apis/{code}', f'{ADMIN}/standard-apis',
               {'standardApiCode': code, **body}, body)
    body = {'integrationName': f'PayerMax（{integration}）', 'description': 'PayerMax 收银台下单与交易查询',
            'endpoints': [{'endpointCode': ENDPOINT, 'baseUrl': base_url}]}
    upsert(token, 'integration', f'{ADMIN}/integrations/{integration}', f'{ADMIN}/integrations',
           {'integrationCode': integration, **body}, body)
    base = f'{ADMIN}/integrations/{integration}'
    for code, name, stage, param, source in ((SIGN, '请求签名（SHA256withRSA）', 'REQUEST', ('privateKey', 'PROTECTED_HANDLE'), SIGN_SOURCE),
                                             (VERIFY, '响应验签（SHA256withRSA）', 'RESPONSE', ('publicKey', 'STRING'), VERIFY_SOURCE)):
        body = {'scriptName': name, 'executionStage': stage, 'parameterDefinition': [{'name': param[0], 'dataType': param[1], 'required': True}],
                'sourceCode': source, 'description': None}
        upsert(token, 'script-' + code, f'{base}/scripts/{code}', f'{base}/scripts', {'scriptCode': code, **body}, body)
    for code, name, request, response, definition_json in (
            (ORDER_AND_PAY, '收银台下单 orderAndPay', ORDER_AND_PAY_REQUEST, ORDER_AND_PAY_RESPONSE, ORDER_AND_PAY_DEFINITION),
            (ORDER_QUERY, '交易查询 orderQuery', ORDER_QUERY_REQUEST, ORDER_QUERY_RESPONSE, ORDER_QUERY_DEFINITION)):
        body = {'apiName': name, 'description': None, 'requestFields': request, 'responseFields': response, 'definition': definition_json}
        upsert(token, 'api-' + code, f'{base}/apis/{code}', f'{base}/apis', {'apiCode': code, **body}, body)
    for code, flow in (('PAY', PAY_FLOW), ('PAY_QUERY', QUERY_FLOW)):
        found, current = call('implementation-get-' + code, 'GET', f'{base}/implementations/{code}', token, expected=(200, 404))
        check = call('implementation-validate-' + code, 'POST', f'{base}/implementations/validate', token,
                     {'standardApiCode': code, 'flowDefinition': flow, 'expectedVersion': current['version'] if found == 200 else None})[1]
        assert check['valid'], check
        upsert(token, 'implementation-' + code, f'{base}/implementations/{code}', f'{base}/implementations',
               {'standardApiCode': code, 'flowDefinition': flow}, {'flowDefinition': flow})


if __name__ == '__main__':
    integration_code, endpoint = sys.argv[1], sys.argv[2]
    configure(integration_code, endpoint)
    (runtime / f'configure-{integration_code}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integration': integration_code, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update'))]}, ensure_ascii=False))
