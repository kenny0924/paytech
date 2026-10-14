"""PayBy 接入阶段 2：经真实管理接口保存（存在则按版本修改）标准 API PAY_CANCEL、BALANCE_QUERY，与接入方 PAYBY 的出站地址、
签名/验签脚本、GET_ORDER、CANCEL_ORDER、REFUND、REFUND_QUERY、GET_BALANCE 原子 API 及五条流程实现（技术方案第 4、5 节）。
PAY（DIRECTPAY 卡直付）依赖 Platform 卡能力，另行配置；已有标准 API PAY_QUERY、REFUND、REFUND_QUERY 只读复用，不修改。

用法：E2E_RUNTIME=<运行目录> python3 configure.py <接入方编码> <出站 base URL>
  mor-test：python3 configure.py PAYBY https://uat.test2pay.com/sgs/api
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

REASON = 'PayBy 接入（requirements/20261014）'
ENDPOINT = 'PAYBY_SGS'
SIGN, VERIFY = 'PAYBY_SIGN', 'PAYBY_VERIFY'


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


def any_of(target, values):
    return '(' + ' || '.join(f"{target} == '{value}'" for value in values) + ')'


CHANNEL_ACCOUNT = [field('channelAccount', 'OBJECT'), field('channelAccount.channelCode'), field('channelAccount.channelAccountId'),
                   field('channelAccount.entityNo'), field('channelAccount.accountVaultToken')]
CANCEL_REQUEST = CHANNEL_ACCOUNT + [field('payment', 'OBJECT'), field('payment.attemptId')]
CANCEL_RESPONSE = [field('cancelStatus'), field('channelStatus', required=False), field('failureCode', required=False),
                   field('failureMessage', required=False)]
BALANCE_REQUEST = CHANNEL_ACCOUNT + [field('currency')]
BALANCE_RESPONSE = [field('available', 'OBJECT', False), field('available.value', 'DECIMAL', False),
                    field('available.currency', required=False), field('failureCode', required=False), field('failureMessage', required=False)]

# TODO by CLAUDE: 引擎没有毫秒时间戳函数，暂用 nowRfc3339() 按 days-from-civil 换算；新增注册函数后替换本段。
SIGN_SOURCE = """let s = nowRfc3339();
let y = long(string.substring(s, 0, 4));
let m = long(string.substring(s, 5, 7));
let d = long(string.substring(s, 8, 10));
let hh = long(string.substring(s, 11, 13));
let mi = long(string.substring(s, 14, 16));
let ss = long(string.substring(s, 17, 19));
let ms = long(string.substring(s, 20, 23));
let yy = m <= 2 ? y - 1 : y;
let era = yy / 400;
let yoe = yy - era * 400;
let mp = m > 2 ? m - 3 : m + 9;
let doy = (153 * mp + 2) / 5 + d - 1;
let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
let days = era * 146097 + doe - 719468;
json.set(http.requestBody, 'requestTime', ((days * 24 + hh) * 60 + mi) * 60000 + ss * 1000 + ms);
http.requestHeaders.sign = rsaSha256Sign(params.privateKey, sealJsonBody(http));"""
VERIFY_SOURCE = """if (http.responseStatus == 200 && http.responseBody != nil && http.responseBody.head.applyStatus == 'SUCCESS') {
  requireRsaSha256Signature(params.publicKey, http.responseRaw, http.responseHeaders.sign);
}"""

ENVELOPE = [field('Partner-Id', location='HEADER'), field('Content-Language', location='HEADER'), field('requestTime', 'INTEGER', False), field('bizContent', 'OBJECT', False)]
HEAD = [field('head', 'OBJECT', False), field('head.applyStatus', required=False), field('head.code', required=False),
        field('head.msg', required=False)]
ACQUIRE = 'http.responseBody.body.acquireOrder'
REFUND_ORDER = 'http.responseBody.body.refundOrder'
OK = "http.responseStatus == 200 && http.responseBody.head.applyStatus == 'SUCCESS' && http.responseBody.head.code == '0'"


def signed(*codes):
    """带签名的业务错误码：applyStatus=SUCCESS 且 code 为给定值之一。"""
    return "http.responseStatus == 200 && http.responseBody.head.applyStatus == 'SUCCESS' && " + any_of('http.responseBody.head.code', codes)


FAIL_OUTPUTS = [output('errorCode', path('http.responseBody.head.code')), output('errorMessage', path('http.responseBody.head.msg'), required=False)]
ORDER_STATUSES = ('CREATED', 'PAID_SUCCESS', 'SETTLED', 'FAILURE')
REFUND_STATUSES = ('CREATED', 'REFUNDED_SETTLED', 'SUCCESS', 'FAILURE')
# 明确未创建退款的码（技术方案第 4.3 节 T3）；62017、400、5xx 与未知码归 ERROR。
REFUND_REJECT_CODES = ('601', '62002', '62004', '62006', '62015', '62035', '62038', '62040', '62045', '62083', '62084')


def definition(path_template, status_rules, outputs):
    return {
        'params': [{'name': 'merchantPrivateKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                   {'name': 'paybyPublicKey', 'dataType': 'STRING', 'required': True}],
        'fixedValues': {'HEADER:Content-Language': const('en')},
        'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': ENDPOINT, 'pathTemplate': path_template,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON', 'responseDecoding': 'JSON', 'decimalEncoding': 'NUMBER',
        'scripts': [{'id': 'sign', 'scriptCode': SIGN, 'stage': 'REQUEST', 'params': {'privateKey': path('params.merchantPrivateKey')}},
                    {'id': 'verify', 'scriptCode': VERIFY, 'stage': 'RESPONSE', 'params': {'publicKey': path('params.paybyPublicKey')}}],
        'statusRules': status_rules,
        'outputs': outputs,
    }


ACQUIRE_RESPONSE = HEAD + [field('body', 'OBJECT', False), field('body.acquireOrder', 'OBJECT', False),
                           field('body.acquireOrder.orderNo', required=False), field('body.acquireOrder.status', required=False),
                           field('body.acquireOrder.totalAmount', 'OBJECT', False),
                           field('body.acquireOrder.totalAmount.amount', 'DECIMAL', False),
                           field('body.acquireOrder.totalAmount.currency', required=False),
                           field('body.acquireOrder.failCode', required=False), field('body.acquireOrder.failDes', required=False)]
ORDER_NO_REQUEST = ENVELOPE + [field('bizContent.merchantOrderNo')]
ORDER_OUTPUTS = [output('channelStatus', path(ACQUIRE + '.status')), output('orderNo', path(ACQUIRE + '.orderNo'), required=False),
                 output('amount', path(ACQUIRE + '.totalAmount.amount'), 'DECIMAL', False),
                 output('currency', path(ACQUIRE + '.totalAmount.currency'), required=False),
                 output('payChannel', path(ACQUIRE + '.paymentInfo.payChannel'), required=False),
                 output('failCode', path(ACQUIRE + '.failCode'), required=False),
                 output('failDes', path(ACQUIRE + '.failDes'), required=False)]
GET_ORDER = definition('/acquire2/getOrder', [
    {'condition': f'{OK} && {any_of(ACQUIRE + ".status", ORDER_STATUSES)}', 'executeStatus': 'SUCCESS'},
    {'condition': signed('62004'), 'executeStatus': 'FAIL'}],
    {'SUCCESS': ORDER_OUTPUTS, 'FAIL': FAIL_OUTPUTS})
CANCEL_ORDER = definition('/acquire2/cancelOrder', [
    {'condition': f"{OK} && {ACQUIRE}.status == 'FAILURE'", 'executeStatus': 'SUCCESS'},
    {'condition': signed('62002'), 'executeStatus': 'SUCCESS'},
    {'condition': signed('62001', '62003', '62004'), 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('channelStatus', expr(f"http.responseBody.head.code == '62002' ? 'FAILURE' : {ACQUIRE}.status"))],
     'FAIL': FAIL_OUTPUTS})

REFUND_REQUEST_FIELDS = ENVELOPE + [field('bizContent.refundMerchantOrderNo'), field('bizContent.originMerchantOrderNo'),
                                    field('bizContent.amount', 'OBJECT', False), field('bizContent.amount.amount', 'DECIMAL'),
                                    field('bizContent.amount.currency'), field('bizContent.reason', required=False),
                                    field('bizContent.notifyUrl', required=False)]
REFUND_RESPONSE_FIELDS = HEAD + [field('body', 'OBJECT', False), field('body.refundOrder', 'OBJECT', False),
                                 field('body.refundOrder.orderNo', required=False), field('body.refundOrder.status', required=False),
                                 field('body.refundOrder.amount', 'OBJECT', False), field('body.refundOrder.amount.amount', 'DECIMAL', False),
                                 field('body.refundOrder.amount.currency', required=False),
                                 field('body.refundOrder.failCode', required=False), field('body.refundOrder.failDes', required=False)]
REFUND_OUTPUTS = [output('channelStatus', path(REFUND_ORDER + '.status')), output('refundOrderNo', path(REFUND_ORDER + '.orderNo')),
                  output('amount', path(REFUND_ORDER + '.amount.amount'), 'DECIMAL', False),
                  output('currency', path(REFUND_ORDER + '.amount.currency'), required=False),
                  output('failCode', path(REFUND_ORDER + '.failCode'), required=False),
                  output('failDes', path(REFUND_ORDER + '.failDes'), required=False)]
KNOWN_REFUND = any_of(REFUND_ORDER + '.status', REFUND_STATUSES)
REFUND = definition('/acquire2/refund/placeOrder', [
    {'condition': f'{OK} && {KNOWN_REFUND} && {REFUND_ORDER}.orderNo != nil', 'executeStatus': 'SUCCESS'},
    {'condition': signed(*REFUND_REJECT_CODES), 'executeStatus': 'FAIL'}],
    {'SUCCESS': REFUND_OUTPUTS, 'FAIL': FAIL_OUTPUTS})
REFUND_QUERY = definition('/acquire2/refund/getOrder', [
    {'condition': f'{OK} && {KNOWN_REFUND} && {REFUND_ORDER}.orderNo != nil', 'executeStatus': 'SUCCESS'},
    {'condition': signed('62007'), 'executeStatus': 'FAIL'}],
    {'SUCCESS': REFUND_OUTPUTS, 'FAIL': FAIL_OUTPUTS})

BALANCES = 'http.responseBody.body.balanceList'
BALANCE_RESPONSE_FIELDS = HEAD + [field('body', 'OBJECT', False), field('body.balanceList', 'ARRAY', False),
                                  field('body.balanceList[].availableBalance', 'OBJECT', False),
                                  field('body.balanceList[].availableBalance.amount', 'DECIMAL', False),
                                  field('body.balanceList[].availableBalance.currency', required=False)]
GET_BALANCE = definition('/member/getBalance', [
    {'condition': f'{OK} && {BALANCES} != nil && count({BALANCES}) > 0', 'executeStatus': 'SUCCESS'}],
    {'SUCCESS': [output('amount', expr(f'#{BALANCES}[0].availableBalance.amount'), 'DECIMAL'),
                 output('currency', expr(f'#{BALANCES}[0].availableBalance.currency'))], 'FAIL': FAIL_OUTPUTS})

APIS = (
    ('GET_ORDER', '交易查询 getOrder', ORDER_NO_REQUEST, ACQUIRE_RESPONSE, GET_ORDER),
    ('CANCEL_ORDER', '关闭未支付订单 cancelOrder', ORDER_NO_REQUEST, ACQUIRE_RESPONSE, CANCEL_ORDER),
    ('REFUND', '退款 refund/placeOrder', REFUND_REQUEST_FIELDS, REFUND_RESPONSE_FIELDS, REFUND),
    ('REFUND_QUERY', '退款查询 refund/getOrder', ENVELOPE + [field('bizContent.refundMerchantOrderNo')], REFUND_RESPONSE_FIELDS, REFUND_QUERY),
    ('GET_BALANCE', '余额查询 member/getBalance', ENVELOPE + [field('bizContent.currencyCode')], BALANCE_RESPONSE_FIELDS, GET_BALANCE),
)

INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
ACCOUNT = 'resources.channelAccount.parameters.'
NODE_PARAMS = {'merchantPrivateKey': path('resources.channelAccount.credentials.merchantPrivateKey'),
               'paybyPublicKey': path(ACCOUNT + 'paybyPublicKey')}
PARTNER = {'HEADER:Partner-Id': path(ACCOUNT + 'partnerId')}


def status(node, value):
    return f"steps.{node}.executeStatus == '{value}'"


def flow(node, api, request, timeout_ms, success, fail=None, extra_success=None):
    """单节点流程：SUCCESS → 第一个出口（可附加按条件的其他出口），FAIL → fail 出口，默认 → unknown（ERROR）。"""
    returns, edges, priority = [], [], 1
    for return_id, execute_status, outputs, condition in [success] + (extra_success or []) + ([fail] if fail else []):
        returns.append({'id': return_id, 'executeStatus': execute_status, 'outputs': outputs})
        edges.append({'from': node, 'to': return_id, 'condition': condition, 'priority': priority})
        priority += 1
    returns.append({'id': 'unknown', 'executeStatus': 'ERROR'})
    edges.append({'from': node, 'to': 'unknown', 'default': True})
    return {'timeoutMs': timeout_ms, 'initializers': INITIALIZER, 'entry': node,
            'nodes': [{'id': node, 'integrationApi': api, 'params': NODE_PARAMS, 'request': {**PARTNER, **request}}],
            'returns': returns, 'edges': edges}


def failure(out):
    return {'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage')}


Q = 'steps.queryOrder.outputs.'
PAY_STATUS = (f"{Q}channelStatus == 'CREATED' ? 'PENDING' : "
              f"(({Q}channelStatus == 'PAID_SUCCESS' || {Q}channelStatus == 'SETTLED') ? 'SUCCEEDED' : 'FAILED')")
PAY_QUERY_FLOW = flow('queryOrder', 'GET_ORDER', {'BODY:bizContent.merchantOrderNo': path('input.payment.attemptId')}, 15000,
                      ('found', 'SUCCESS', {'paymentStatus': expr(PAY_STATUS), 'channelTransactionRef': path(Q + 'orderNo'),
                                            'channelStatus': path(Q + 'channelStatus'), 'amount.value': path(Q + 'amount'),
                                            'amount.currency': path(Q + 'currency'), 'paymentMethodType': path(Q + 'payChannel'),
                                            'failureCode': path(Q + 'failCode'), 'failureMessage': path(Q + 'failDes')},
                       status('queryOrder', 'SUCCESS')),
                      ('notFound', 'FAIL', {'paymentStatus': const('NOT_FOUND'), **failure(Q)}, status('queryOrder', 'FAIL')))
C = 'steps.cancelOrder.outputs.'
PAY_CANCEL_FLOW = flow('cancelOrder', 'CANCEL_ORDER', {'BODY:bizContent.merchantOrderNo': path('input.payment.attemptId')}, 15000,
                       ('closed', 'SUCCESS', {'cancelStatus': const('CLOSED'), 'channelStatus': path(C + 'channelStatus')},
                        status('cancelOrder', 'SUCCESS')),
                       ('rejected', 'FAIL', {'cancelStatus': expr(f"{C}errorCode == '62004' ? 'NOT_FOUND' : 'ALREADY_PAID'"), **failure(C)},
                        status('cancelOrder', 'FAIL')))


def refund_status(out):
    """CREATED → PENDING，REFUNDED_SETTLED/SUCCESS → SUCCEEDED，FAILURE → FAILED。"""
    s = out + 'channelStatus'
    return expr(f"{s} == 'CREATED' ? 'PENDING' : (({s} == 'REFUNDED_SETTLED' || {s} == 'SUCCESS') ? 'SUCCEEDED' : 'FAILED')")


R = 'steps.applyRefund.outputs.'
REFUND_FLOW = flow('applyRefund', 'REFUND', {
    'BODY:bizContent.refundMerchantOrderNo': path('input.refund.refundId'),
    'BODY:bizContent.originMerchantOrderNo': path('input.refund.paymentAttemptId'),
    'BODY:bizContent.amount.amount': path('input.refund.amount.value'), 'BODY:bizContent.amount.currency': path('input.refund.amount.currency'),
    'BODY:bizContent.reason': path('input.refund.reason'), 'BODY:bizContent.notifyUrl': path('input.refund.notifyUrl')}, 25000,
    ('accepted', 'SUCCESS', {'refundStatus': refund_status(R), 'channelRefundRef': path(R + 'refundOrderNo'),
                             'channelStatus': path(R + 'channelStatus'), 'failureCode': path(R + 'failCode'),
                             'failureMessage': path(R + 'failDes')}, status('applyRefund', 'SUCCESS')),
    ('rejected', 'FAIL', {'refundStatus': const('FAILED'), **failure(R)}, status('applyRefund', 'FAIL')))
RQ = 'steps.queryRefund.outputs.'
REFUND_QUERY_FLOW = flow('queryRefund', 'REFUND_QUERY', {'BODY:bizContent.refundMerchantOrderNo': path('input.refund.refundId')}, 15000,
                         ('found', 'SUCCESS', {'refundStatus': refund_status(RQ), 'channelRefundRef': path(RQ + 'refundOrderNo'),
                                               'channelStatus': path(RQ + 'channelStatus'), 'amount.value': path(RQ + 'amount'),
                                               'amount.currency': path(RQ + 'currency'), 'failureCode': path(RQ + 'failCode'),
                                               'failureMessage': path(RQ + 'failDes')}, status('queryRefund', 'SUCCESS')),
                         ('notFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **failure(RQ)}, status('queryRefund', 'FAIL')))
B = 'steps.getBalance.outputs.'
BALANCE_FLOW = flow('getBalance', 'GET_BALANCE', {'BODY:bizContent.currencyCode': path('input.currency')}, 15000,
                    ('found', 'SUCCESS', {'available.value': path(B + 'amount'), 'available.currency': path(B + 'currency')},
                     status('getBalance', 'SUCCESS') + f' && {B}currency == input.currency'),
                    extra_success=[('currencyNotFound', 'FAIL', {'failureCode': const('CURRENCY_NOT_FOUND'),
                                                                  'failureMessage': const('渠道余额列表不含请求币种')},
                                    status('getBalance', 'SUCCESS'))])

FLOWS = (('PAY_QUERY', PAY_QUERY_FLOW), ('PAY_CANCEL', PAY_CANCEL_FLOW), ('REFUND', REFUND_FLOW), ('REFUND_QUERY', REFUND_QUERY_FLOW),
         ('BALANCE_QUERY', BALANCE_FLOW))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def configure(integration, base_url):
    token = login()
    for code, label, request, response in (('PAY_CANCEL', '关闭未支付订单', CANCEL_REQUEST, CANCEL_RESPONSE),
                                           ('BALANCE_QUERY', '渠道余额查询', BALANCE_REQUEST, BALANCE_RESPONSE)):
        body = {'standardApiName': label, 'description': '渠道无关标准 API（草案，PayBy 接入）', 'requestFields': request,
                'responseFields': response}
        upsert(token, 'standard-api-' + code, f'{ADMIN}/standard-apis/{code}', f'{ADMIN}/standard-apis', {'standardApiCode': code, **body}, body)
    body = {'integrationName': f'PayBy（{integration}）', 'description': 'PayBy 阿联酋收款：查询、关单、退款、余额；卡直付另行配置',
            'endpoints': [{'endpointCode': ENDPOINT, 'baseUrl': base_url, 'sandbox': True}]}
    upsert(token, 'integration', f'{ADMIN}/integrations/{integration}', f'{ADMIN}/integrations', {'integrationCode': integration, **body}, body)
    base = f'{ADMIN}/integrations/{integration}'
    for code, name, stage, param, source in ((SIGN, '请求签名（毫秒 requestTime + SHA256withRSA）', 'REQUEST', ('privateKey', 'PROTECTED_HANDLE'),
                                              SIGN_SOURCE),
                                             (VERIFY, '响应验签（applyStatus=SUCCESS 时 SHA256withRSA）', 'RESPONSE', ('publicKey', 'STRING'),
                                              VERIFY_SOURCE)):
        body = {'scriptName': name, 'executionStage': stage, 'parameterDefinition': [{'name': param[0], 'dataType': param[1], 'required': True}],
                'sourceCode': source, 'description': None}
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
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update'))]}, ensure_ascii=False))
