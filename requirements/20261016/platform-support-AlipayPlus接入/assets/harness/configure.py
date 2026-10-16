"""Alipay+ 接入阶段 3：经真实管理接口保存（存在则按版本修改）标准 API PAY、REFUND_QUERY 的新增可选字段（技术方案 T2、T3），
与接入方 ALIPAY_PLUS 的出站地址、签名/验签/退款匹配脚本、4 个原子 API 及 4 条流程（技术方案第 5 节）。

用法：E2E_RUNTIME=<运行目录> python3 configure.py <接入方编码> <出站 base URL>
  mor-test：python3 configure.py ALIPAY_PLUS https://open-sea.alipayplus.com
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

REASON = 'Alipay+ 接入（requirements/20261016）'
ENDPOINT = 'ALIPAY_PLUS_OPEN'
SIGN, VERIFY, PICK = 'ALIPAY_PLUS_SIGN', 'ALIPAY_PLUS_VERIFY', 'ALIPAY_PLUS_PICK_REFUND'
PREFIX = '/aps/api/v1/payments'


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


def major_units(amount_path):
    """最小单位字符串 → 主单位十进制（MONEY_MAJOR_UNITS）。"""
    return {'kind': 'FUNCTION', 'function': 'MONEY_MAJOR_UNITS', 'result': 'result',
            'args': {'amount': path(amount_path + '.value'), 'currency': path(amount_path + '.currency')}}


def minor_units(amount_path):
    """主单位十进制 → 最小单位字符串；标准 API 的 DECIMAL 输入是数值，先 str()。"""
    return {'kind': 'FUNCTION', 'function': 'MONEY_MINOR_UNITS', 'result': 'str(result)',
            'args': {'amount': expr(f'str({amount_path}.value)'), 'currency': path(amount_path + '.currency')}}


# ---- 标准 API 调整（只加可选字段，其余按现行版本原样保留） ----
STANDARD_ADDITIONS = {'PAY': field('payment.paymentMethodType', required=False),
                      'REFUND_QUERY': field('refund.paymentAttemptId', required=False)}

# ---- 公共脚本 ----
SIGN_SOURCE = """let requestTime = nowRfc3339();
seq.put(http.requestHeaders, 'Client-Id', params.clientId);
seq.put(http.requestHeaders, 'Request-Time', requestTime);
let content = 'POST ' + params.path + '\\n' + params.clientId + '.' + requestTime + '.' + sealJsonBody(http);
http.requestHeaders.Signature = 'algorithm=RSA256, keyVersion=0, signature=' + urlEncode(rsaSha256Sign(params.privateKey, content));"""
# 只有带签名的 200 应答才验签并打标记；状态规则要求标记，未签名的应答一律落到 ERROR（技术方案 T6）。
VERIFY_SOURCE = """let header = seq.get(http.responseHeaders, 'signature');
if (http.responseStatus == 200 && http.responseBody != nil && header != nil) {
  let start = string.indexOf(header, 'signature=');
  let signature = urlDecode(string.substring(header, start + 10));
  let content = 'POST ' + params.path + '\\n' + seq.get(http.responseHeaders, 'client-id') + '.'
    + seq.get(http.responseHeaders, 'response-time') + '.' + http.responseRaw;
  requireRsaSha256Signature(params.publicKey, content, signature);
  http.responseBody.verified = true;
}"""
# 沙箱没有 inquiryRefund：在 inquiryPayment 的 transactions[] 中按退款单号取 REFUND 记录（技术方案第 5.4 节）。
PICK_SOURCE = """if (http.responseBody != nil && http.responseBody.verified == true && http.responseBody.transactions != nil) {
  for item in http.responseBody.transactions {
    if (item.transactionType == 'REFUND' && item.transactionRequestId == params.refundId) {
      http.responseBody.refundTransaction = item;
    }
  }
}"""
SCRIPTS = (
    (SIGN, '请求签名（SHA256withRSA，签名值 URL 编码）', 'REQUEST',
     [('privateKey', 'PROTECTED_HANDLE'), ('clientId', 'STRING'), ('path', 'STRING')], SIGN_SOURCE),
    (VERIFY, '响应验签（带签名的 200 应答，通过后标记 verified）', 'RESPONSE', [('publicKey', 'STRING'), ('path', 'STRING')], VERIFY_SOURCE),
    (PICK, '退款查询：按退款单号匹配 transactions[]', 'RESPONSE', [('refundId', 'STRING')], PICK_SOURCE),
)

# ---- 原子 API ----
BODY = 'http.responseBody'
VERIFIED = f'http.responseStatus == 200 && {BODY}.verified == true'
RESULT = f'{BODY}.result.resultStatus'
RESULT_FIELDS = [field('result', 'OBJECT', False), field('result.resultStatus', required=False), field('result.resultCode', required=False),
                 field('result.resultMessage', required=False)]
FAIL_OUTPUTS = [output('errorCode', path(BODY + '.result.resultCode')), output('errorMessage', path(BODY + '.result.resultMessage'), required=False)]
ACCOUNT_PARAMS = [{'name': 'merchantPrivateKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                  {'name': 'clientId', 'dataType': 'STRING', 'required': True},
                  {'name': 'alipayPublicKey', 'dataType': 'STRING', 'required': True}]


def definition(api_path, status_rules, outputs, fixed=None, extra_params=(), extra_scripts=()):
    full = const(PREFIX + api_path)
    return {
        'params': ACCOUNT_PARAMS + list(extra_params),
        'fixedValues': fixed or {},
        'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': ENDPOINT, 'pathTemplate': PREFIX + api_path,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON', 'responseDecoding': 'JSON',
        'scripts': [{'id': 'sign', 'scriptCode': SIGN, 'stage': 'REQUEST',
                     'params': {'privateKey': path('params.merchantPrivateKey'), 'clientId': path('params.clientId'), 'path': full}},
                    {'id': 'verify', 'scriptCode': VERIFY, 'stage': 'RESPONSE',
                     'params': {'publicKey': path('params.alipayPublicKey'), 'path': full}}] + list(extra_scripts),
        'statusRules': status_rules,
        'outputs': outputs,
    }


def amount_fields(prefix, required=True):
    return [field(prefix, 'OBJECT', False), field(prefix + '.currency', required=required), field(prefix + '.value', required=required)]


PAY_REQUEST = ([field('paymentRequestId')] + amount_fields('paymentAmount')
               + [field('order', 'OBJECT', False), field('order.referenceOrderId'), field('order.orderDescription')]
               + amount_fields('order.orderAmount')
               + [field('order.merchant', 'OBJECT', False), field('order.merchant.referenceMerchantId'), field('order.merchant.merchantName'),
                  field('order.merchant.merchantMCC'), field('order.merchant.merchantAddress', 'OBJECT', False),
                  field('order.merchant.merchantAddress.region'), field('order.merchant.merchantAddress.city'),
                  field('order.env', 'OBJECT', False), field('order.env.terminalType'),
                  field('paymentMethod', 'OBJECT', False), field('paymentMethod.paymentMethodType'),
                  field('paymentFactor', 'OBJECT', False), field('paymentFactor.isCashierPayment'), field('paymentFactor.isInStorePayment'),
                  field('settlementStrategy', 'OBJECT', False), field('settlementStrategy.settlementCurrency'),
                  field('paymentRedirectUrl'), field('paymentNotifyUrl', required=False), field('userRegion', required=False)])
PAY_RESPONSE = RESULT_FIELDS + [field('paymentId', required=False), field('normalUrl', required=False)]
PAY_CASHIER = definition('/pay', [
    {'condition': f"{VERIFIED} && {RESULT} == 'U' && {BODY}.paymentId != nil && {BODY}.normalUrl != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{VERIFIED} && {RESULT} == 'S' && {BODY}.paymentId != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{VERIFIED} && {RESULT} == 'F'", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('resultStatus', path(RESULT)), output('paymentId', path(BODY + '.paymentId')),
                 output('normalUrl', path(BODY + '.normalUrl'), required=False)],
     'FAIL': FAIL_OUTPUTS},
    fixed={'BODY:order.env.terminalType': const('WEB'), 'BODY:paymentFactor.isCashierPayment': const('true'),
           'BODY:paymentFactor.isInStorePayment': const('false')})

INQUIRY_REQUEST = [field('paymentRequestId')]
INQUIRY_RESPONSE = (RESULT_FIELDS + [field('paymentResult', 'OBJECT', False), field('paymentResult.resultStatus', required=False),
                                     field('paymentResult.resultCode', required=False), field('paymentResult.resultMessage', required=False),
                                     field('paymentId', required=False)]
                    + amount_fields('paymentAmount', False)
                    + [field('transactions', 'ARRAY', False), field('transactions[].transactionType', required=False),
                       field('transactions[].transactionRequestId', required=False), field('transactions[].transactionId', required=False),
                       field('transactions[].transactionStatus', required=False)])
PAYMENT_RESULT = f'{BODY}.paymentResult.resultStatus'
NOT_EXIST = f"{VERIFIED} && {RESULT} == 'F' && {BODY}.result.resultCode == 'ORDER_NOT_EXIST'"
INQUIRY_PAYMENT = definition('/inquiryPayment', [
    {'condition': f"{VERIFIED} && {RESULT} == 'S' && ({PAYMENT_RESULT} == 'S' || {PAYMENT_RESULT} == 'F' || {PAYMENT_RESULT} == 'U')",
     'executeStatus': 'SUCCESS'},
    {'condition': NOT_EXIST, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('paymentResultStatus', path(PAYMENT_RESULT)), output('paymentResultCode', path(BODY + '.paymentResult.resultCode'), required=False),
                 output('paymentResultMessage', path(BODY + '.paymentResult.resultMessage'), required=False),
                 output('paymentId', path(BODY + '.paymentId'), required=False),
                 output('amount', major_units(BODY + '.paymentAmount'), 'DECIMAL', False),
                 output('currency', path(BODY + '.paymentAmount.currency'), required=False)],
     'FAIL': FAIL_OUTPUTS})

REFUND_TX = f'{BODY}.refundTransaction'
INQUIRY_REFUND = definition('/inquiryPayment', [
    {'condition': f"{VERIFIED} && {RESULT} == 'S' && {REFUND_TX} != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{VERIFIED} && {RESULT} == 'S'", 'executeStatus': 'FAIL'},
    {'condition': NOT_EXIST, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('transactionStatus', path(REFUND_TX + '.transactionStatus')),
                 output('transactionId', path(REFUND_TX + '.transactionId'), required=False),
                 output('transactionResultCode', path(REFUND_TX + '.transactionResult.resultCode'), required=False),
                 output('transactionResultMessage', path(REFUND_TX + '.transactionResult.resultMessage'), required=False),
                 output('amount', major_units(REFUND_TX + '.transactionAmount'), 'DECIMAL', False),
                 output('currency', path(REFUND_TX + '.transactionAmount.currency'), required=False)],
     'FAIL': [output('errorCode', expr(f"{RESULT} == 'S' ? 'REFUND_NOT_EXIST' : {BODY}.result.resultCode")),
              output('errorMessage', expr(f"{RESULT} == 'S' ? 'Refund transaction not found' : {BODY}.result.resultMessage"), required=False)]},
    extra_params=[{'name': 'refundId', 'dataType': 'STRING', 'required': True}],
    extra_scripts=[{'id': 'pickRefund', 'scriptCode': PICK, 'stage': 'RESPONSE', 'params': {'refundId': path('params.refundId')}}])

REFUND_REQUEST = [field('paymentRequestId'), field('refundRequestId')] + amount_fields('refundAmount') + [field('refundReason', required=False)]
REFUND_RESPONSE = RESULT_FIELDS + [field('refundId', required=False)]
REFUND = definition('/refund', [
    {'condition': f"{VERIFIED} && ({RESULT} == 'S' || {RESULT} == 'U')", 'executeStatus': 'SUCCESS'},
    {'condition': f"{VERIFIED} && {RESULT} == 'F'", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('resultStatus', path(RESULT)), output('refundId', path(BODY + '.refundId'), required=False)],
     'FAIL': FAIL_OUTPUTS})

APIS = (
    ('PAY_CASHIER', '收银台下单 payments/pay', PAY_REQUEST, PAY_RESPONSE, PAY_CASHIER),
    ('INQUIRY_PAYMENT', '支付查询 payments/inquiryPayment', INQUIRY_REQUEST, INQUIRY_RESPONSE, INQUIRY_PAYMENT),
    ('INQUIRY_REFUND', '退款查询 payments/inquiryPayment（按退款单号匹配 transactions）', INQUIRY_REQUEST, INQUIRY_RESPONSE, INQUIRY_REFUND),
    ('REFUND', '退款 payments/refund', REFUND_REQUEST, REFUND_RESPONSE, REFUND),
)

# ---- 流程 ----
INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
ACCOUNT = 'resources.channelAccount.parameters.'
NODE_PARAMS = {'merchantPrivateKey': path('resources.channelAccount.credentials.merchantPrivateKey'),
               'clientId': path(ACCOUNT + 'clientId'), 'alipayPublicKey': path(ACCOUNT + 'alipayPublicKey')}


def status(node, value):
    return f"steps.{node}.executeStatus == '{value}'"


def flow(node, api, request, timeout_ms, branches, params=None):
    """单节点流程：按顺序匹配 branches（id, executeStatus, outputs, condition），其余 → unknown（ERROR）。"""
    returns, edges = [], []
    for priority, (return_id, execute_status, outputs, condition) in enumerate(branches, start=1):
        returns.append({'id': return_id, 'executeStatus': execute_status, 'outputs': outputs})
        edges.append({'from': node, 'to': return_id, 'condition': condition, 'priority': priority})
    returns.append({'id': 'unknown', 'executeStatus': 'ERROR'})
    edges.append({'from': node, 'to': 'unknown', 'default': True})
    return {'timeoutMs': timeout_ms, 'initializers': INITIALIZER, 'entry': node,
            'nodes': [{'id': node, 'integrationApi': api, 'params': {**NODE_PARAMS, **(params or {})}, 'request': request}],
            'returns': returns, 'edges': edges}


def failure(out):
    return {'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage')}


P = 'steps.createPayment.outputs.'
PAY_FLOW = flow('createPayment', 'PAY_CASHIER', {
    'BODY:paymentRequestId': path('input.payment.attemptId'),
    'BODY:paymentAmount.currency': path('input.payment.amount.currency'), 'BODY:paymentAmount.value': minor_units('input.payment.amount'),
    'BODY:order.referenceOrderId': path('input.payment.attemptId'), 'BODY:order.orderDescription': path('input.payment.subject'),
    'BODY:order.orderAmount.currency': path('input.payment.amount.currency'), 'BODY:order.orderAmount.value': minor_units('input.payment.amount'),
    'BODY:order.merchant.referenceMerchantId': path(ACCOUNT + 'referenceMerchantId'),
    'BODY:order.merchant.merchantName': path(ACCOUNT + 'merchantName'), 'BODY:order.merchant.merchantMCC': path(ACCOUNT + 'merchantMcc'),
    'BODY:order.merchant.merchantAddress.region': path(ACCOUNT + 'merchantRegion'),
    'BODY:order.merchant.merchantAddress.city': path(ACCOUNT + 'merchantCity'),
    'BODY:paymentMethod.paymentMethodType': path('input.payment.paymentMethodType'),
    'BODY:settlementStrategy.settlementCurrency': path(ACCOUNT + 'settlementCurrency'),
    'BODY:paymentRedirectUrl': path('input.payment.returnUrl'),
    # 本期不接通知（需求第 6 节）；未提供 notifyUrl 时沿用 returnUrl。
    'BODY:paymentNotifyUrl': expr('input.payment.notifyUrl != nil ? input.payment.notifyUrl : input.payment.returnUrl'),
    'BODY:userRegion': path('input.payment.country')}, 20000, [
    ('redirect', 'SUCCESS', {'paymentStatus': const('PENDING'), 'nextAction.type': const('REDIRECT'), 'nextAction.redirectUrl': path(P + 'normalUrl'),
                             'channelTransactionRef': path(P + 'paymentId'), 'channelStatus': path(P + 'resultStatus')},
     status('createPayment', 'SUCCESS') + f" && {P}resultStatus == 'U'"),
    ('paid', 'SUCCESS', {'paymentStatus': const('SUCCEEDED'), 'channelTransactionRef': path(P + 'paymentId'),
                         'channelStatus': path(P + 'resultStatus')}, status('createPayment', 'SUCCESS')),
    ('rejected', 'FAIL', {'paymentStatus': const('FAILED'), **failure(P)}, status('createPayment', 'FAIL'))])

Q = 'steps.queryPayment.outputs.'
QS = Q + 'paymentResultStatus'
PAY_QUERY_FLOW = flow('queryPayment', 'INQUIRY_PAYMENT', {'BODY:paymentRequestId': path('input.payment.attemptId')}, 15000, [
    ('found', 'SUCCESS', {'paymentStatus': expr(f"{QS} == 'S' ? 'SUCCEEDED' : ({QS} == 'F' ? 'FAILED' : 'PENDING')"),
                          'channelTransactionRef': path(Q + 'paymentId'), 'channelStatus': path(QS),
                          'amount.value': path(Q + 'amount'), 'amount.currency': path(Q + 'currency'),
                          'failureCode': expr(f"{QS} == 'F' ? {Q}paymentResultCode : nil"),
                          'failureMessage': expr(f"{QS} == 'F' ? {Q}paymentResultMessage : nil")},
     status('queryPayment', 'SUCCESS')),
    ('notFound', 'FAIL', {'paymentStatus': const('NOT_FOUND'), **failure(Q)}, status('queryPayment', 'FAIL'))])

R = 'steps.applyRefund.outputs.'
REFUND_FLOW = flow('applyRefund', 'REFUND', {
    'BODY:paymentRequestId': path('input.refund.paymentAttemptId'), 'BODY:refundRequestId': path('input.refund.refundId'),
    'BODY:refundAmount.currency': path('input.refund.amount.currency'), 'BODY:refundAmount.value': minor_units('input.refund.amount'),
    'BODY:refundReason': path('input.refund.reason')}, 20000, [
    ('accepted', 'SUCCESS', {'refundStatus': expr(f"{R}resultStatus == 'S' ? 'SUCCEEDED' : 'PROCESSING'"),
                             'channelRefundRef': path(R + 'refundId'), 'channelStatus': path(R + 'resultStatus')},
     status('applyRefund', 'SUCCESS')),
    ('rejected', 'FAIL', {'refundStatus': const('FAILED'), **failure(R)}, status('applyRefund', 'FAIL'))])

RQ = 'steps.queryRefund.outputs.'
RS = RQ + 'transactionStatus'
REFUND_QUERY_FLOW = flow('queryRefund', 'INQUIRY_REFUND', {'BODY:paymentRequestId': path('input.refund.paymentAttemptId')}, 15000, [
    ('found', 'SUCCESS', {'refundStatus': expr(f"{RS} == 'SUCCESS' ? 'SUCCEEDED' : ({RS} == 'FAIL' ? 'FAILED' : 'PROCESSING')"),
                          'channelRefundRef': path(RQ + 'transactionId'), 'channelStatus': path(RS),
                          'amount.value': path(RQ + 'amount'), 'amount.currency': path(RQ + 'currency'),
                          'failureCode': expr(f"{RS} == 'FAIL' ? {RQ}transactionResultCode : nil"),
                          'failureMessage': expr(f"{RS} == 'FAIL' ? {RQ}transactionResultMessage : nil")},
     status('queryRefund', 'SUCCESS')),
    ('notFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **failure(RQ)}, status('queryRefund', 'FAIL'))],
    params={'refundId': path('input.refund.refundId')})

FLOWS = (('PAY', PAY_FLOW), ('PAY_QUERY', PAY_QUERY_FLOW), ('REFUND', REFUND_FLOW), ('REFUND_QUERY', REFUND_QUERY_FLOW))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def extend_standard_api(token, code, addition):
    """在现行字段末尾追加一个可选请求字段；已存在则不改。"""
    current = call('standard-api-get-' + code, 'GET', f'{ADMIN}/standard-apis/{code}', token)[1]
    if any(f['fieldPath'] == addition['fieldPath'] for f in current['requestFields']):
        return
    keys = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')
    plain = lambda fields: [{k: f.get(k) for k in keys} for f in fields]  # noqa: E731
    body = {'standardApiName': current['standardApiName'], 'description': current['description'],
            'requestFields': plain(current['requestFields']) + [addition], 'responseFields': plain(current['responseFields']),
            'expectedVersion': current['version'], 'reason': REASON}
    call('standard-api-update-' + code, 'PUT', f'{ADMIN}/standard-apis/{code}', token, body)


def configure(integration, base_url):
    token = login()
    for code, addition in STANDARD_ADDITIONS.items():
        extend_standard_api(token, code, addition)
    body = {'integrationName': f'Alipay+（{integration}）', 'description': 'Alipay+ 跨境钱包收银台：下单、查询、退款、退款查询',
            'endpoints': [{'endpointCode': ENDPOINT, 'baseUrl': base_url, 'sandbox': True}]}
    upsert(token, 'integration', f'{ADMIN}/integrations/{integration}', f'{ADMIN}/integrations', {'integrationCode': integration, **body}, body)
    base = f'{ADMIN}/integrations/{integration}'
    for code, name, stage, params, source in SCRIPTS:
        body = {'scriptName': name, 'executionStage': stage, 'sourceCode': source, 'description': None,
                'parameterDefinition': [{'name': n, 'dataType': t, 'required': True} for n, t in params]}
        upsert(token, 'script-' + code, f'{base}/scripts/{code}', f'{base}/scripts', {'scriptCode': code, **body}, body)
    for code, name, request, response, definition_json in APIS:
        body = {'apiName': name, 'description': None, 'requestFields': request, 'responseFields': response, 'definition': definition_json}
        upsert(token, 'api-' + code, f'{base}/apis/{code}', f'{base}/apis', {'apiCode': code, **body}, body)
    for code, flow_definition in FLOWS:
        found, current = call('implementation-get-' + code, 'GET', f'{base}/implementations/{code}', token, expected=(200, 404))
        check = call('implementation-validate-' + code, 'POST', f'{base}/implementations/validate', token,
                     {'standardApiCode': code, 'flowDefinition': flow_definition, 'expectedVersion': current['version'] if found == 200 else None})[1]
        assert check['valid'], (code, check)
        upsert(token, 'implementation-' + code, f'{base}/implementations/{code}', f'{base}/implementations',
               {'standardApiCode': code, 'flowDefinition': flow_definition}, {'flowDefinition': flow_definition})


if __name__ == '__main__':
    integration_code, endpoint = sys.argv[1], sys.argv[2]
    configure(integration_code, endpoint)
    (runtime / f'configure-{integration_code}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integration': integration_code, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update')) or '-update-' in r['name']]},
                     ensure_ascii=False))
