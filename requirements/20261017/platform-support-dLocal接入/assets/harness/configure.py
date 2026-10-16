"""dLocal 接入阶段 2：经真实管理接口保存（存在则按版本修改）接入方 DLOCAL 的出站地址、签名/付款人/退款匹配脚本、
5 个原子 API 及 PAY、PAY_QUERY、PAY_CANCEL、REFUND、REFUND_QUERY 五条流程（技术方案第 4 节）。不改标准 API。

用法：E2E_RUNTIME=<运行目录> python3 configure.py <接入方编码> <出站 base URL>
  mor-test：python3 configure.py DLOCAL https://sandbox.dlocal.com
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

REASON = 'dLocal 接入（requirements/20261017）'
ENDPOINT = 'DLOCAL_API'
SIGN_JSON, SIGN_EMPTY, PAYER, PICK = 'DLOCAL_SIGN_JSON', 'DLOCAL_SIGN_EMPTY', 'DLOCAL_EMPTY_PAYER', 'DLOCAL_PICK_REFUND'


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


def minor_units(amount_path, currency_path):
    """渠道主单位数字（如 1.0）→ 最小单位字符串；流程再用 major_units 转回，按币种精度补齐（1.00）。函数参数不能嵌套函数，所以分两步。"""
    return {'kind': 'FUNCTION', 'function': 'MONEY_MINOR_UNITS', 'result': 'str(result)',
            'args': {'amount': expr(f'str({amount_path})'), 'currency': path(currency_path)}}


def major_units(amount_path, currency_path):
    return {'kind': 'FUNCTION', 'function': 'MONEY_MAJOR_UNITS', 'result': 'result',
            'args': {'amount': path(amount_path), 'currency': path(currency_path)}}


# ---- 公共脚本 ----
HEADERS = """let date = nowRfc3339();
seq.put(http.requestHeaders, 'X-Login', params.xLogin);
seq.put(http.requestHeaders, 'X-Trans-Key', params.xTransKey);
seq.put(http.requestHeaders, 'X-Version', '2.1');
seq.put(http.requestHeaders, 'User-Agent', 'MoR-Platform');
seq.put(http.requestHeaders, 'X-Date', date);
"""
SIGN_JSON_SOURCE = HEADERS + """seq.put(http.requestHeaders, 'X-Idempotency-Key', params.idempotencyKey);
seq.put(http.requestHeaders, 'Authorization',
  'V2-HMAC-SHA256, Signature: ' + hmacSha256Hex(params.secretKey, params.xLogin + date + sealJsonBody(http)));"""
# GET 与无 body 的 POST：签名原文中 body 为空串。
SIGN_EMPTY_SOURCE = HEADERS + """seq.put(http.requestHeaders, 'Authorization',
  'V2-HMAC-SHA256, Signature: ' + hmacSha256Hex(params.secretKey, params.xLogin + date));"""
# 托管页收集付款人信息，请求只发空 payer（技术方案 D6）。
PAYER_SOURCE = "seq.put(http.requestBody, 'payer', seq.map());"
# 在 GET /payments/{id} 的 refunds[] 中按我方退款单号取记录（技术方案 D3）。
PICK_SOURCE = """if (http.responseStatus == 200 && http.responseBody != nil && http.responseBody.refunds != nil) {
  for item in http.responseBody.refunds {
    if (item.order_refund_id == params.refundId) {
      http.responseBody.refundRecord = item;
    }
  }
}"""
KEYS = [('xLogin', 'STRING'), ('xTransKey', 'PROTECTED_HANDLE'), ('secretKey', 'PROTECTED_HANDLE')]
SCRIPTS = (
    (SIGN_JSON, '请求签名（JSON 请求体，带幂等键）', 'REQUEST', KEYS + [('idempotencyKey', 'STRING')], SIGN_JSON_SOURCE),
    (SIGN_EMPTY, '请求签名（无请求体）', 'REQUEST', KEYS, SIGN_EMPTY_SOURCE),
    (PAYER, '下单写入空 payer', 'REQUEST', [], PAYER_SOURCE),
    (PICK, '退款查询：按退款单号匹配 refunds[]', 'RESPONSE', [('refundId', 'STRING')], PICK_SOURCE),
)

# ---- 原子 API ----
BODY = 'http.responseBody'
OK = 'http.responseStatus == 200'
REJECTED_4XX = f'http.responseStatus >= 400 && http.responseStatus < 500 && {BODY}.code != nil'
ERROR_FIELDS = [field('code', 'INTEGER', False), field('message', required=False)]
# 4xx 带数字 code，或 200 带拒绝状态：code 转字符串（修正旧系统 ClassCastException）。
FAIL_OUTPUTS = [output('errorCode', expr(f"{BODY}.code != nil ? str({BODY}.code) : {BODY}.status")),
                output('errorMessage', expr(f"{BODY}.code != nil ? {BODY}.message : {BODY}.status_detail"), required=False)]
ACCOUNT_PARAMS = [{'name': 'xLogin', 'dataType': 'STRING', 'required': True},
                  {'name': 'xTransKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                  {'name': 'secretKey', 'dataType': 'PROTECTED_HANDLE', 'required': True}]
KEY_BINDINGS = {'xLogin': path('params.xLogin'), 'xTransKey': path('params.xTransKey'), 'secretKey': path('params.secretKey')}


def definition(method, path_template, status_rules, outputs, idempotency=None, extra_params=(), before=(), after=()):
    """idempotency 为幂等键参数名时按 JSON 请求体签名，否则按空 body 签名。"""
    sign = ({'id': 'sign', 'scriptCode': SIGN_JSON, 'stage': 'REQUEST', 'params': {**KEY_BINDINGS, 'idempotencyKey': path('params.' + idempotency)}}
            if idempotency else {'id': 'sign', 'scriptCode': SIGN_EMPTY, 'stage': 'REQUEST', 'params': KEY_BINDINGS})
    result = {
        'params': ACCOUNT_PARAMS + list(extra_params),
        'fixedValues': {},
        'transport': {'protocol': 'HTTP', 'method': method, 'endpointCode': ENDPOINT, 'pathTemplate': path_template,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON' if idempotency else 'NONE', 'responseDecoding': 'JSON',
        'scripts': list(before) + [sign] + list(after),
        'statusRules': status_rules,
        'outputs': outputs,
    }
    if idempotency:
        result['decimalEncoding'] = 'NUMBER'
    return result


def status_in(*values):
    return '(' + ' || '.join(f"{BODY}.status == '{v}'" for v in values) + ')'


PAY_REQUEST = [field('order_id'), field('amount', 'DECIMAL'), field('currency'), field('country'), field('payment_method_flow'),
               field('payment_method_id', required=False), field('payer', 'OBJECT', False), field('description', required=False),
               field('callback_url'), field('notification_url', required=False)]
PAY_RESPONSE = ERROR_FIELDS + [field('id', required=False), field('status', required=False), field('redirect_url', required=False),
                               field('status_detail', required=False)]
CREATE_PAYMENT = definition('POST', '/payments', [
    {'condition': f"{OK} && {BODY}.status == 'PENDING' && {BODY}.id != nil && {BODY}.redirect_url != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{OK} && {BODY}.status == 'PAID' && {BODY}.id != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{OK} && {status_in('REJECTED', 'CANCELLED', 'EXPIRED')}", 'executeStatus': 'FAIL'},
    {'condition': REJECTED_4XX, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(BODY + '.status')), output('paymentId', path(BODY + '.id')),
                 output('redirectUrl', path(BODY + '.redirect_url'), required=False)],
     'FAIL': FAIL_OUTPUTS},
    idempotency='orderId', extra_params=[{'name': 'orderId', 'dataType': 'STRING', 'required': True}],
    before=[{'id': 'payer', 'scriptCode': PAYER, 'stage': 'REQUEST', 'params': {}}])

ORDER_REQUEST = [field('orderId', location='PATH')]
ORDER_RESPONSE = ERROR_FIELDS + [field('payment_id', required=False), field('status', required=False), field('amount', 'DECIMAL', False),
                                 field('currency', required=False), field('status_detail', required=False)]
GET_ORDER = definition('GET', '/orders/{orderId}', [
    {'condition': f"{OK} && {BODY}.status != nil && {BODY}.payment_id != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"http.responseStatus == 404 && {BODY}.code == 4000", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(BODY + '.status')), output('paymentId', path(BODY + '.payment_id')),
                 output('amountMinor', minor_units(BODY + '.amount', BODY + '.currency'), required=False),
                 output('currency', path(BODY + '.currency'), required=False),
                 output('statusDetail', path(BODY + '.status_detail'), required=False)],
     'FAIL': FAIL_OUTPUTS})

CANCEL_REQUEST = [field('paymentId', location='PATH')]
CANCEL_RESPONSE = ERROR_FIELDS + [field('status', required=False)]
CANCEL_PAYMENT = definition('POST', '/payments/{paymentId}/cancel', [
    {'condition': f"{OK} && {BODY}.status == 'CANCELLED'", 'executeStatus': 'SUCCESS'},
    {'condition': f"http.responseStatus == 400 && {BODY}.code == 5013", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(BODY + '.status'))], 'FAIL': FAIL_OUTPUTS})

REFUND_REQUEST = [field('payment_id'), field('order_refund_id'), field('amount', 'DECIMAL'), field('currency'),
                  field('description', required=False), field('notification_url', required=False)]
REFUND_RESPONSE = ERROR_FIELDS + [field('id', required=False), field('status', required=False), field('status_detail', required=False)]
CREATE_REFUND = definition('POST', '/refunds', [
    {'condition': f"{OK} && {status_in('PENDING', 'SUCCESS')} && {BODY}.id != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{OK} && {status_in('REJECTED', 'CANCELLED')}", 'executeStatus': 'FAIL'},
    {'condition': REJECTED_4XX, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(BODY + '.status')), output('refundId', path(BODY + '.id'))], 'FAIL': FAIL_OUTPUTS},
    idempotency='refundId', extra_params=[{'name': 'refundId', 'dataType': 'STRING', 'required': True}])

PAYMENT_REQUEST = [field('paymentId', location='PATH')]
PAYMENT_RESPONSE = ERROR_FIELDS + [field('id', required=False), field('refunds', 'ARRAY', False),
                                   field('refunds[].order_refund_id', required=False), field('refunds[].status', required=False)]
RECORD = BODY + '.refundRecord'
GET_PAYMENT_REFUNDS = definition('GET', '/payments/{paymentId}', [
    {'condition': f"{OK} && {RECORD} != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{OK} && {BODY}.id != nil", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(RECORD + '.status')), output('refundId', path(RECORD + '.id'), required=False),
                 output('statusDetail', path(RECORD + '.status_detail'), required=False)],
     'FAIL': [output('errorCode', const('REFUND_NOT_EXIST')), output('errorMessage', const('Refund not found'), required=False)]},
    extra_params=[{'name': 'refundId', 'dataType': 'STRING', 'required': True}],
    after=[{'id': 'pickRefund', 'scriptCode': PICK, 'stage': 'RESPONSE', 'params': {'refundId': path('params.refundId')}}])

APIS = (
    ('CREATE_PAYMENT', '下单 POST /payments（REDIRECT）', PAY_REQUEST, PAY_RESPONSE, CREATE_PAYMENT),
    ('GET_ORDER', '按我方单号查询 GET /orders/{order_id}', ORDER_REQUEST, ORDER_RESPONSE, GET_ORDER),
    ('CANCEL_PAYMENT', '撤销待付款 POST /payments/{id}/cancel', CANCEL_REQUEST, CANCEL_RESPONSE, CANCEL_PAYMENT),
    ('CREATE_REFUND', '退款 POST /refunds', REFUND_REQUEST, REFUND_RESPONSE, CREATE_REFUND),
    ('GET_PAYMENT_REFUNDS', '退款查询 GET /payments/{id}（按退款单号匹配 refunds）', PAYMENT_REQUEST, PAYMENT_RESPONSE, GET_PAYMENT_REFUNDS),
)

# ---- 流程 ----
INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
NODE_PARAMS = {'xLogin': path('resources.channelAccount.parameters.xLogin'),
               'xTransKey': path('resources.channelAccount.credentials.xTransKey'),
               'secretKey': path('resources.channelAccount.credentials.secretKey')}


def status(node, value):
    return f"steps.{node}.executeStatus == '{value}'"


def node(node_id, api, request, params=None):
    return {'id': node_id, 'integrationApi': api, 'params': {**NODE_PARAMS, **(params or {})}, 'request': request}


def flow(timeout_ms, nodes, branches):
    """branches：(from, to, condition)；to 为 (return_id, executeStatus, outputs) 或下一节点 id。每个节点缺省 → unknown（ERROR）。"""
    returns, edges, seen = [], [], set()
    priorities = {}
    for source, target, condition in branches:
        priorities[source] = priorities.get(source, 0) + 1
        if isinstance(target, tuple):
            return_id, execute_status, outputs = target
            if return_id not in seen:
                returns.append({'id': return_id, 'executeStatus': execute_status, 'outputs': outputs})
                seen.add(return_id)
            target = return_id
        edges.append({'from': source, 'to': target, 'condition': condition, 'priority': priorities[source]})
    returns.append({'id': 'unknown', 'executeStatus': 'ERROR'})
    for item in nodes:
        edges.append({'from': item['id'], 'to': 'unknown', 'default': True})
    return {'timeoutMs': timeout_ms, 'initializers': INITIALIZER, 'entry': nodes[0]['id'], 'nodes': nodes, 'returns': returns, 'edges': edges}


def failure(out):
    return {'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage')}


def order_node(attempt_path):
    return node('getOrder', 'GET_ORDER', {'PATH:orderId': path(attempt_path)})


O = 'steps.getOrder.outputs.'
OS = O + 'status'

P = 'steps.createPayment.outputs.'
PAY_FLOW = flow(20000, [node('createPayment', 'CREATE_PAYMENT', {
    'BODY:order_id': path('input.payment.attemptId'), 'BODY:amount': path('input.payment.amount.value'),
    'BODY:currency': path('input.payment.amount.currency'), 'BODY:country': path('input.payment.country'),
    'BODY:payment_method_flow': const('REDIRECT'), 'BODY:payment_method_id': path('input.payment.paymentMethodType'),
    'BODY:description': path('input.payment.subject'), 'BODY:callback_url': path('input.payment.returnUrl'),
    'BODY:notification_url': path('input.payment.notifyUrl')}, {'orderId': path('input.payment.attemptId')})], [
    ('createPayment', ('redirect', 'SUCCESS', {'paymentStatus': const('PENDING'), 'nextAction.type': const('REDIRECT'),
                                               'nextAction.redirectUrl': path(P + 'redirectUrl'), 'channelTransactionRef': path(P + 'paymentId'),
                                               'channelStatus': path(P + 'status')}),
     status('createPayment', 'SUCCESS') + f" && {P}status == 'PENDING'"),
    ('createPayment', ('paid', 'SUCCESS', {'paymentStatus': const('SUCCEEDED'), 'channelTransactionRef': path(P + 'paymentId'),
                                           'channelStatus': path(P + 'status')}), status('createPayment', 'SUCCESS')),
    ('createPayment', ('rejected', 'FAIL', {'paymentStatus': const('FAILED'), **failure(P)}), status('createPayment', 'FAIL'))])

TERMINAL_FAILED = f"({OS} == 'REJECTED' || {OS} == 'CANCELLED' || {OS} == 'EXPIRED')"
PAY_QUERY_FLOW = flow(15000, [order_node('input.payment.attemptId')], [
    ('getOrder', ('found', 'SUCCESS', {
        'paymentStatus': expr(f"{OS} == 'PAID' ? 'SUCCEEDED' : ({TERMINAL_FAILED} ? 'FAILED' : 'PENDING')"),
        'channelTransactionRef': path(O + 'paymentId'), 'channelStatus': path(OS),
        'amount.value': major_units(O + 'amountMinor', O + 'currency'), 'amount.currency': path(O + 'currency'),
        'failureCode': expr(f"{TERMINAL_FAILED} ? {OS} : nil"), 'failureMessage': expr(f"{TERMINAL_FAILED} ? {O}statusDetail : nil")}),
     status('getOrder', 'SUCCESS') + f" && ({OS} == 'PAID' || {TERMINAL_FAILED} || {OS} == 'PENDING' || {OS} == 'AUTHORIZED')"),
    ('getOrder', ('notFound', 'FAIL', {'paymentStatus': const('NOT_FOUND'), **failure(O)}), status('getOrder', 'FAIL'))])

C = 'steps.cancelPayment.outputs.'
PAY_CANCEL_FLOW = flow(25000, [order_node('input.payment.attemptId'),
                               node('cancelPayment', 'CANCEL_PAYMENT', {'PATH:paymentId': path(O + 'paymentId')})], [
    ('getOrder', ('notFound', 'FAIL', {'cancelStatus': const('NOT_FOUND'), **failure(O)}), status('getOrder', 'FAIL')),
    ('getOrder', ('alreadyClosed', 'SUCCESS', {'cancelStatus': const('CLOSED'), 'channelStatus': path(OS)}),
     status('getOrder', 'SUCCESS') + ' && ' + TERMINAL_FAILED),
    ('getOrder', ('alreadyPaid', 'FAIL', {'cancelStatus': const('ALREADY_PAID'), 'channelStatus': path(OS), 'failureCode': path(OS)}),
     status('getOrder', 'SUCCESS') + f" && {OS} == 'PAID'"),
    ('getOrder', 'cancelPayment', status('getOrder', 'SUCCESS') + f" && {OS} == 'PENDING'"),
    ('cancelPayment', ('closed', 'SUCCESS', {'cancelStatus': const('CLOSED'), 'channelStatus': path(C + 'status')}),
     status('cancelPayment', 'SUCCESS')),
    ('cancelPayment', ('paidDuringCancel', 'FAIL', {'cancelStatus': const('ALREADY_PAID'), **failure(C)}), status('cancelPayment', 'FAIL'))])

R = 'steps.createRefund.outputs.'
REFUND_FLOW = flow(25000, [order_node('input.refund.paymentAttemptId'), node('createRefund', 'CREATE_REFUND', {
    'BODY:payment_id': path(O + 'paymentId'), 'BODY:order_refund_id': path('input.refund.refundId'),
    'BODY:amount': path('input.refund.amount.value'), 'BODY:currency': path('input.refund.amount.currency'),
    'BODY:description': path('input.refund.reason'), 'BODY:notification_url': path('input.refund.notifyUrl')},
    {'refundId': path('input.refund.refundId')})], [
    ('getOrder', ('paymentNotFound', 'FAIL', {'refundStatus': const('FAILED'), **failure(O)}), status('getOrder', 'FAIL')),
    ('getOrder', 'createRefund', status('getOrder', 'SUCCESS')),
    ('createRefund', ('accepted', 'SUCCESS', {'refundStatus': expr(f"{R}status == 'SUCCESS' ? 'SUCCEEDED' : 'PROCESSING'"),
                                              'channelRefundRef': path(R + 'refundId'), 'channelStatus': path(R + 'status')}),
     status('createRefund', 'SUCCESS')),
    ('createRefund', ('rejected', 'FAIL', {'refundStatus': const('FAILED'), **failure(R)}), status('createRefund', 'FAIL'))])

RQ = 'steps.getRefunds.outputs.'
RS = RQ + 'status'
REFUND_QUERY_FLOW = flow(25000, [order_node('input.refund.paymentAttemptId'),
                                 node('getRefunds', 'GET_PAYMENT_REFUNDS', {'PATH:paymentId': path(O + 'paymentId')},
                                      {'refundId': path('input.refund.refundId')})], [
    ('getOrder', ('paymentNotFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **failure(O)}), status('getOrder', 'FAIL')),
    ('getOrder', 'getRefunds', status('getOrder', 'SUCCESS')),
    ('getRefunds', ('found', 'SUCCESS', {
        'refundStatus': expr(f"{RS} == 'SUCCESS' ? 'SUCCEEDED' : (({RS} == 'REJECTED' || {RS} == 'CANCELLED') ? 'FAILED' : 'PROCESSING')"),
        'channelRefundRef': path(RQ + 'refundId'), 'channelStatus': path(RS),
        'failureCode': expr(f"({RS} == 'REJECTED' || {RS} == 'CANCELLED') ? {RS} : nil"),
        'failureMessage': expr(f"({RS} == 'REJECTED' || {RS} == 'CANCELLED') ? {RQ}statusDetail : nil")}),
     status('getRefunds', 'SUCCESS')),
    ('getRefunds', ('refundNotFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **failure(RQ)}), status('getRefunds', 'FAIL'))])

FLOWS = (('PAY', PAY_FLOW), ('PAY_QUERY', PAY_QUERY_FLOW), ('PAY_CANCEL', PAY_CANCEL_FLOW), ('REFUND', REFUND_FLOW),
         ('REFUND_QUERY', REFUND_QUERY_FLOW))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def configure(integration, base_url):
    token = login()
    body = {'integrationName': f'dLocal（{integration}）', 'description': 'dLocal 托管跳转收款：下单、查询、撤销、退款、退款查询',
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
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update'))]}, ensure_ascii=False))
