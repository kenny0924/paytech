"""MPGS（Network International）接入阶段 4：经 mor-test 真实管理接口保存（存在则按版本修改）技术方案第 4 节的配置。

1. 标准 API PAY：读取现行定义，只追加可选字段 card、card.token、payment.clientIp、payment.userAgent（M10），已有字段保持不变；
   服务端用新字段复核全部接入方的流程。
2. 接入方 I_MPGS_NI（出站端点 MPGS_API，标记沙箱；统一出口第二段后为渠道，需先在运营控制台建好）、Basic 认证脚本、3 个原子 API（PAY、RETRIEVE、REFUND）。
3. PAY、PAY_QUERY、REFUND、REFUND_QUERY 四条流程，每条先校验再保存。
4. 追加（技术方案第 7 节）：新增标准 API AUTHORIZE（请求字段同 PAY）、CAPTURE；原子 API AUTHORIZE、CAPTURE；两条流程，
   渠道拒绝时回查本笔交易（A4），查到同类型交易按查到的结果返回。

用法：MPGS_RUNTIME=<仓库外运行目录> python3 configure.py [base URL，默认 https://test-gateway.mastercard.com]
"""
import json, sys

from mortest import ADMIN, call, login, results, runtime

INTEGRATION = 'I_MPGS_NI'
REASON = 'MPGS（NI）接入（requirements/20261021/channel-connector-MPGS_NI接入）'
ENDPOINT = 'MPGS_API'
AUTH = 'MPGS_BASIC_AUTH'
PATH_TEMPLATE = '/api/rest/version/100/merchant/{merchantId}/order/{orderId}/transaction/{transactionId}'
B = 'http.responseBody'


def field(path_, data_type='STRING', required=True, location='BODY'):
    return {'fieldLocation': location, 'fieldPath': path_, 'fieldName': path_, 'dataType': data_type, 'required': required, 'description': None}


def path(value):
    return {'kind': 'PATH', 'path': value}


def expr(value):
    return {'kind': 'EXPRESSION', 'expression': value}


def const(text):
    return expr(f"'{text}'")


def output(name, value, required=True):
    return {'name': name, 'dataType': 'STRING', 'required': required, 'value': value}


def minor_units(amount_path, currency_path):
    """渠道主单位数字 → 最小单位字符串；流程再用 MONEY_MAJOR_UNITS 按币种精度补齐（函数参数不能嵌套函数，分两步）。"""
    return {'kind': 'FUNCTION', 'function': 'MONEY_MINOR_UNITS', 'result': 'str(result)',
            'args': {'amount': expr(f'str({amount_path})'), 'currency': path(currency_path)}}


def major_units(amount_path, currency_path):
    return {'kind': 'FUNCTION', 'function': 'MONEY_MAJOR_UNITS', 'result': 'result',
            'args': {'amount': path(amount_path), 'currency': path(currency_path)}}


# ---- 标准 API PAY：追加的可选字段（M10）----
PAY_ADDED = [field('card', 'OBJECT', False), field('card.token', required=False),
             field('payment.clientIp', required=False), field('payment.userAgent', required=False)]

# ---- 公共脚本：Basic 认证（M3），两个参数都是受保护句柄，凭据不进日志 ----
AUTH_SOURCE = "seq.put(http.requestHeaders, 'Authorization', basicAuth(params.apiUser, params.apiPassword));"

# ---- 原子 API ----
# 容器对象字段声明为可选（与 PayerMax 卡直付相同），叶子字段决定是否必填。
PATH_FIELDS = [field('merchantId', location='PATH'), field('orderId', location='PATH'), field('transactionId', location='PATH')]
RESPONSE_FIELDS = [field('result', required=False), field('error', 'OBJECT', False), field('error.cause', required=False),
                   field('error.explanation', required=False), field('response', 'OBJECT', False),
                   field('response.gatewayCode', required=False), field('response.acquirerCode', required=False),
                   field('response.acquirerMessage', required=False), field('transaction', 'OBJECT', False),
                   field('transaction.id', required=False), field('transaction.type', required=False),
                   field('transaction.receipt', required=False), field('transaction.amount', 'DECIMAL', False),
                   field('transaction.currency', required=False)]
CREATED = '(http.responseStatus == 200 || http.responseStatus == 201)'
RESULTS = f"({B}.result == 'SUCCESS' || {B}.result == 'FAILURE' || {B}.result == 'PENDING')"
REJECTED = (f"(http.responseStatus == 400 || http.responseStatus == 401) && {B}.result == 'ERROR' && {B}.error != nil"
            f" && ({B}.error.cause == 'INVALID_REQUEST' || {B}.error.cause == 'REQUEST_REJECTED')")
TRANSACTION_OUTPUTS = [
    output('result', path(B + '.result')), output('transactionType', path(B + '.transaction.type')),
    output('gatewayCode', path(B + '.response.gatewayCode'), required=False),
    output('acquirerCode', path(B + '.response.acquirerCode'), required=False),
    output('acquirerMessage', path(B + '.response.acquirerMessage'), required=False),
    # M7：收单 RRN，缺失时取交易号
    output('reference', expr(f"{B}.transaction.receipt != nil ? {B}.transaction.receipt : {B}.transaction.id"), required=False)]
FAIL_OUTPUTS = [output('errorCode', path(B + '.error.cause')), output('errorMessage', path(B + '.error.explanation'), required=False)]
ACCOUNT_PARAMS = [{'name': 'apiUser', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                  {'name': 'apiPassword', 'dataType': 'PROTECTED_HANDLE', 'required': True}]


def definition(method, status_rules, outputs, fixed=None, encoded=True):
    result = {
        'params': ACCOUNT_PARAMS,
        'fixedValues': fixed or {},
        'transport': {'protocol': 'HTTP', 'method': method, 'endpointCode': ENDPOINT, 'pathTemplate': PATH_TEMPLATE,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON' if encoded else 'NONE', 'responseDecoding': 'JSON',
        'scripts': [{'id': 'auth', 'scriptCode': AUTH, 'stage': 'REQUEST',
                     'params': {'apiUser': path('params.apiUser'), 'apiPassword': path('params.apiPassword')}}],
        'statusRules': status_rules,
        'outputs': outputs,
    }
    if encoded:
        result['decimalEncoding'] = 'NUMBER'
    return result


CARD = 'sourceOfFunds.provided.card.'
CARD_FIELDS = {'number': 'pan', 'expiry.month': 'expiryMonth', 'expiry.year': 'expiryYearShort', 'securityCode': 'cvv',
               'nameOnCard': 'cardholderName'}
PAY_REQUEST = PATH_FIELDS + [
    field('apiOperation'), field('sourceOfFunds', 'OBJECT', False), field('sourceOfFunds.type'), field('sourceOfFunds.provided', 'OBJECT', False),
    field(CARD[:-1], 'OBJECT', False), field(CARD + 'expiry', 'OBJECT', False)] + [field(CARD + name) for name in CARD_FIELDS] + [
    field('order', 'OBJECT', False), field('order.amount', 'DECIMAL'), field('order.currency'), field('order.reference', required=False),
    field('device', 'OBJECT', False), field('device.ipAddress', required=False), field('device.browser', required=False)]
PAY_DEFINITION = definition('PUT', [
    {'condition': f"{CREATED} && {B}.transaction != nil && {B}.transaction.type == 'PAYMENT' && {RESULTS}", 'executeStatus': 'SUCCESS'},
    {'condition': REJECTED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': TRANSACTION_OUTPUTS, 'FAIL': FAIL_OUTPUTS},
    {'BODY:apiOperation': const('PAY'), 'BODY:sourceOfFunds.type': const('CARD')})
PAY_DEFINITION['cardFields'] = ['BODY:' + CARD + name for name in CARD_FIELDS]

RETRIEVE_DEFINITION = definition('GET', [
    {'condition': f"http.responseStatus == 200 && {B}.transaction != nil && {B}.transaction.type != nil && {RESULTS}",
     'executeStatus': 'SUCCESS'},
    {'condition': f"http.responseStatus == 400 && {B}.result == 'ERROR' && {B}.error != nil && {B}.error.cause == 'INVALID_REQUEST'",
     'executeStatus': 'FAIL'}],
    {'SUCCESS': TRANSACTION_OUTPUTS + [
        output('amountMinor', minor_units(B + '.transaction.amount', B + '.transaction.currency'), required=False),
        output('currency', path(B + '.transaction.currency'), required=False)],
     'FAIL': FAIL_OUTPUTS}, encoded=False)

REFUND_REQUEST = PATH_FIELDS + [field('apiOperation'), field('transaction', 'OBJECT', False), field('transaction.amount', 'DECIMAL'),
                                field('transaction.currency')]
REFUND_DEFINITION = definition('PUT', [
    {'condition': f"{CREATED} && {B}.transaction != nil && {B}.transaction.type == 'REFUND' && {RESULTS}", 'executeStatus': 'SUCCESS'},
    {'condition': REJECTED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': TRANSACTION_OUTPUTS, 'FAIL': FAIL_OUTPUTS},
    {'BODY:apiOperation': const('REFUND')})

# ---- 追加：预授权与请款（技术方案 7.3）：与 PAY / REFUND 只差 apiOperation 和交易类型 ----
AUTHORIZE_DEFINITION = definition('PUT', [
    {'condition': f"{CREATED} && {B}.transaction != nil && {B}.transaction.type == 'AUTHORIZATION' && {RESULTS}", 'executeStatus': 'SUCCESS'},
    {'condition': REJECTED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': TRANSACTION_OUTPUTS, 'FAIL': FAIL_OUTPUTS},
    {'BODY:apiOperation': const('AUTHORIZE'), 'BODY:sourceOfFunds.type': const('CARD')})
AUTHORIZE_DEFINITION['cardFields'] = PAY_DEFINITION['cardFields']
CAPTURE_DEFINITION = definition('PUT', [
    {'condition': f"{CREATED} && {B}.transaction != nil && {B}.transaction.type == 'CAPTURE' && {RESULTS}", 'executeStatus': 'SUCCESS'},
    {'condition': REJECTED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': TRANSACTION_OUTPUTS, 'FAIL': FAIL_OUTPUTS},
    {'BODY:apiOperation': const('CAPTURE')})

APIS = (
    ('PAY', '卡直付下单 PUT …/order/{orderId}/transaction/{transactionId}（apiOperation=PAY）', PAY_REQUEST, PAY_DEFINITION),
    ('RETRIEVE', '查询交易 GET …/order/{orderId}/transaction/{transactionId}', PATH_FIELDS, RETRIEVE_DEFINITION),
    ('REFUND', '退款 PUT …/order/{orderId}/transaction/{refundId}（apiOperation=REFUND）', REFUND_REQUEST, REFUND_DEFINITION),
    ('AUTHORIZE', '卡预授权 PUT …/order/{orderId}/transaction/{transactionId}（apiOperation=AUTHORIZE）', PAY_REQUEST, AUTHORIZE_DEFINITION),
    ('CAPTURE', '请款 PUT …/order/{orderId}/transaction/{captureId}（apiOperation=CAPTURE）', REFUND_REQUEST, CAPTURE_DEFINITION),
)

# ---- 流程 ----
INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
CARD_INITIALIZER = {'id': 'card', 'function': 'PAYMENT_CARD', 'required': True, 'args': {
    'paymentInstrumentToken': path('input.card.token'), 'paymentAttemptId': path('input.payment.attemptId')}}
NODE_PARAMS = {'apiUser': path('resources.channelAccount.credentials.apiUser'),
               'apiPassword': path('resources.channelAccount.credentials.apiPassword')}
MERCHANT = path('resources.channelAccount.parameters.merchantId')


def status(node_id, value):
    return f"steps.{node_id}.executeStatus == '{value}'"


def node(node_id, api, order, transaction, request=None):
    return {'id': node_id, 'integrationApi': api, 'params': NODE_PARAMS, 'request': {
        'PATH:merchantId': MERCHANT, 'PATH:orderId': path(order), 'PATH:transactionId': path(transaction), **(request or {})}}


def flow(timeout_ms, nodes, branches, initializers=INITIALIZER):
    """branches：(from, to, condition)；to 为 (return_id, executeStatus, outputs) 或下一节点 id。每个节点缺省 → unknown（ERROR）。"""
    returns, edges, seen, priorities = [], [], set(), {}
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
    return {'timeoutMs': timeout_ms, 'initializers': initializers, 'entry': nodes[0]['id'], 'nodes': nodes, 'returns': returns,
            'edges': edges}


def acquirer_message(out):
    """M8：收单原始码与说明拼为失败说明。"""
    code, message = out + 'acquirerCode', out + 'acquirerMessage'
    return expr(f"{code} == nil ? {message} : ({message} == nil ? {code} : {code} + ' ' + {message})")


def rejected(out):
    return {'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage'), 'channelStatus': path(out + 'errorCode')}


def approved(out):
    return f"{out}result == 'SUCCESS' && {out}gatewayCode == 'APPROVED'"


P = 'steps.pay.outputs.'
PAY_FLOW = flow(25000, [node('pay', 'PAY', 'input.payment.attemptId', 'input.payment.attemptId', {
    'BODY:order.amount': path('input.payment.amount.value'), 'BODY:order.currency': path('input.payment.amount.currency'),
    'BODY:order.reference': path('input.payment.attemptId'),
    'BODY:device.ipAddress': path('input.payment.clientIp'), 'BODY:device.browser': path('input.payment.userAgent'),
    **{'BODY:' + CARD + name: path('resources.card.' + value) for name, value in CARD_FIELDS.items()}})], [
    ('pay', ('succeeded', 'SUCCESS', {'paymentStatus': const('SUCCEEDED'), 'channelTransactionRef': path(P + 'reference'),
                                      'channelStatus': path(P + 'gatewayCode')}),
     f"{status('pay', 'SUCCESS')} && {approved(P)}"),
    ('pay', ('declined', 'SUCCESS', {'paymentStatus': const('FAILED'), 'channelTransactionRef': path(P + 'reference'),
                                     'channelStatus': path(P + 'gatewayCode'), 'failureCode': path(P + 'gatewayCode'),
                                     'failureMessage': acquirer_message(P)}),
     f"{status('pay', 'SUCCESS')} && {P}result == 'FAILURE'"),
    ('pay', ('pending', 'SUCCESS', {'paymentStatus': const('PENDING'), 'channelTransactionRef': path(P + 'reference'),
                                    'channelStatus': path(P + 'gatewayCode')}),
     f"{status('pay', 'SUCCESS')} && {P}result == 'PENDING'"),
    ('pay', ('rejected', 'FAIL', {'paymentStatus': const('FAILED'), **rejected(P)}), status('pay', 'FAIL'))],
    INITIALIZER + [CARD_INITIALIZER])

Q = 'steps.retrieve.outputs.'
QUERY_KNOWN = f"({approved(Q)} || {Q}result == 'FAILURE' || {Q}result == 'PENDING')"
PAY_QUERY_FLOW = flow(15000, [node('retrieve', 'RETRIEVE', 'input.payment.attemptId', 'input.payment.attemptId')], [
    ('retrieve', ('found', 'SUCCESS', {
        'paymentStatus': expr(f"{approved(Q)} ? 'SUCCEEDED' : ({Q}result == 'FAILURE' ? 'FAILED' : 'PENDING')"),
        'channelTransactionRef': path(Q + 'reference'), 'channelStatus': path(Q + 'gatewayCode'),
        'amount.value': major_units(Q + 'amountMinor', Q + 'currency'), 'amount.currency': path(Q + 'currency'),
        'failureCode': expr(f"{Q}result == 'FAILURE' ? {Q}gatewayCode : nil"),
        'failureMessage': expr(f"{Q}result == 'FAILURE' ? {Q}acquirerMessage : nil")}),
     f"{status('retrieve', 'SUCCESS')} && {Q}transactionType == 'PAYMENT' && {QUERY_KNOWN}"),
    ('retrieve', ('notFound', 'FAIL', {'paymentStatus': const('NOT_FOUND'), **rejected(Q)}), status('retrieve', 'FAIL'))])

R = 'steps.refund.outputs.'
REFUND_FLOW = flow(15000, [node('refund', 'REFUND', 'input.refund.paymentAttemptId', 'input.refund.refundId', {
    'BODY:transaction.amount': path('input.refund.amount.value'), 'BODY:transaction.currency': path('input.refund.amount.currency')})], [
    ('refund', ('succeeded', 'SUCCESS', {'refundStatus': const('SUCCEEDED'), 'channelRefundRef': path(R + 'reference'),
                                         'channelStatus': path(R + 'gatewayCode')}),
     f"{status('refund', 'SUCCESS')} && {approved(R)}"),
    ('refund', ('processing', 'SUCCESS', {'refundStatus': const('PROCESSING'), 'channelRefundRef': path(R + 'reference'),
                                          'channelStatus': path(R + 'gatewayCode')}),
     f"{status('refund', 'SUCCESS')} && {R}result == 'PENDING'"),
    ('refund', ('declined', 'FAIL', {'refundStatus': const('FAILED'), 'channelRefundRef': path(R + 'reference'),
                                     'channelStatus': path(R + 'gatewayCode'), 'failureCode': path(R + 'gatewayCode'),
                                     'failureMessage': acquirer_message(R)}),
     f"{status('refund', 'SUCCESS')} && {R}result == 'FAILURE'"),
    ('refund', ('rejected', 'FAIL', {'refundStatus': const('FAILED'), **rejected(R)}), status('refund', 'FAIL'))])

REFUND_QUERY_FLOW = flow(15000, [node('retrieve', 'RETRIEVE', 'input.refund.paymentAttemptId', 'input.refund.refundId')], [
    ('retrieve', ('found', 'SUCCESS', {
        'refundStatus': expr(f"{approved(Q)} ? 'SUCCEEDED' : ({Q}result == 'FAILURE' ? 'FAILED' : 'PROCESSING')"),
        'channelRefundRef': path(Q + 'reference'), 'channelStatus': path(Q + 'gatewayCode'),
        'amount.value': major_units(Q + 'amountMinor', Q + 'currency'), 'amount.currency': path(Q + 'currency'),
        'failureCode': expr(f"{Q}result == 'FAILURE' ? {Q}gatewayCode : nil"),
        'failureMessage': expr(f"{Q}result == 'FAILURE' ? {Q}acquirerMessage : nil")}),
     f"{status('retrieve', 'SUCCESS')} && {Q}transactionType == 'REFUND' && {QUERY_KNOWN}"),
    ('retrieve', ('notRefund', 'FAIL', {'refundStatus': const('NOT_FOUND'), 'failureCode': const('NOT_A_REFUND'),
                                        'channelStatus': path(Q + 'transactionType')}),
     f"{status('retrieve', 'SUCCESS')} && {Q}transactionType != 'REFUND'"),
    ('retrieve', ('notFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **rejected(Q)}), status('retrieve', 'FAIL'))])

# ---- 追加：AUTHORIZE / CAPTURE 流程。渠道拒绝（FAIL）时回查本笔交易（A4）：同一交易号重发且已不是订单最后一笔时 MPGS 返回 400，
# 回查到同类型交易就按查到的结果返回，否则返回原拒绝原因。----
A, AQ = 'steps.authorize.outputs.', 'steps.recheck.outputs.'


def authorization(out, suffix):
    known = {'channelTransactionRef': path(out + 'reference'), 'channelStatus': path(out + 'gatewayCode')}
    return [
        (('succeeded' + suffix, 'SUCCESS', {'authorizationStatus': const('SUCCEEDED'), **known}), approved(out)),
        (('declined' + suffix, 'SUCCESS', {'authorizationStatus': const('FAILED'), **known, 'failureCode': path(out + 'gatewayCode'),
                                           'failureMessage': acquirer_message(out)}), f"{out}result == 'FAILURE'"),
        (('pending' + suffix, 'SUCCESS', {'authorizationStatus': const('PENDING'), **known}), f"{out}result == 'PENDING'")]


AUTHORIZE_FLOW = flow(25000, [
    node('authorize', 'AUTHORIZE', 'input.payment.attemptId', 'input.payment.attemptId', PAY_FLOW['nodes'][0]['request']),
    node('recheck', 'RETRIEVE', 'input.payment.attemptId', 'input.payment.attemptId')],
    [('authorize', target, f"{status('authorize', 'SUCCESS')} && {condition}") for target, condition in authorization(A, '')] + [
        ('authorize', 'recheck', status('authorize', 'FAIL'))] + [
        ('recheck', target, f"{status('recheck', 'SUCCESS')} && {AQ}transactionType == 'AUTHORIZATION' && {condition}")
        for target, condition in authorization(AQ, 'OnRecheck')] + [
        ('recheck', ('rejected', 'FAIL', {'authorizationStatus': const('FAILED'), **rejected(A)}),
         f"{status('recheck', 'FAIL')} || ({status('recheck', 'SUCCESS')} && {AQ}transactionType != 'AUTHORIZATION')")],
    INITIALIZER + [CARD_INITIALIZER])

C, CQ = 'steps.capture.outputs.', 'steps.recheck.outputs.'


def captured(out, suffix):
    known = {'channelCaptureRef': path(out + 'reference'), 'channelStatus': path(out + 'gatewayCode')}
    return [
        (('succeeded' + suffix, 'SUCCESS', {'captureStatus': const('SUCCEEDED'), **known}), approved(out)),
        (('processing' + suffix, 'SUCCESS', {'captureStatus': const('PROCESSING'), **known}), f"{out}result == 'PENDING'"),
        (('declined' + suffix, 'FAIL', {'captureStatus': const('FAILED'), **known, 'failureCode': path(out + 'gatewayCode'),
                                        'failureMessage': acquirer_message(out)}), f"{out}result == 'FAILURE'")]


CAPTURE_FLOW = flow(25000, [
    node('capture', 'CAPTURE', 'input.capture.paymentAttemptId', 'input.capture.captureId', {
        'BODY:transaction.amount': path('input.capture.amount.value'), 'BODY:transaction.currency': path('input.capture.amount.currency')}),
    node('recheck', 'RETRIEVE', 'input.capture.paymentAttemptId', 'input.capture.captureId')],
    [('capture', target, f"{status('capture', 'SUCCESS')} && {condition}") for target, condition in captured(C, '')] + [
        ('capture', 'recheck', status('capture', 'FAIL'))] + [
        ('recheck', target, f"{status('recheck', 'SUCCESS')} && {CQ}transactionType == 'CAPTURE' && {condition}")
        for target, condition in captured(CQ, 'OnRecheck')] + [
        ('recheck', ('rejected', 'FAIL', {'captureStatus': const('FAILED'), **rejected(C)}),
         f"{status('recheck', 'FAIL')} || ({status('recheck', 'SUCCESS')} && {CQ}transactionType != 'CAPTURE')")])

FLOWS = (('PAY', PAY_FLOW), ('PAY_QUERY', PAY_QUERY_FLOW), ('REFUND', REFUND_FLOW), ('REFUND_QUERY', REFUND_QUERY_FLOW),
         ('AUTHORIZE', AUTHORIZE_FLOW), ('CAPTURE', CAPTURE_FLOW))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def extend_pay(token):
    """M10：只追加缺少的可选字段；已全部存在时不修改。"""
    current = call('standard-api-PAY-get', 'GET', f'{ADMIN}/standard-apis/PAY', token)[1]
    keys = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')
    request_fields = [{k: f.get(k) for k in keys} for f in current['requestFields']]
    existing = {f['fieldPath'] for f in request_fields}
    added = [f for f in PAY_ADDED if f['fieldPath'] not in existing]
    if not added:
        return []
    call('standard-api-PAY-update', 'PUT', f'{ADMIN}/standard-apis/PAY', token, {
        'standardApiName': current['standardApiName'], 'description': current.get('description'),
        'requestFields': request_fields + added, 'responseFields': [{k: f.get(k) for k in keys} for f in current['responseFields']],
        'expectedVersion': current['version'], 'reason': REASON + '：PAY 增加可选卡字段'})
    return [f['fieldPath'] for f in added]


# ---- 追加：标准 API AUTHORIZE（A1，请求字段与 PAY 逐项相同）、CAPTURE（A2，与 REFUND 对称）----
FIELD_KEYS = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')
ACCOUNT_FIELDS = [field('channelAccount', 'OBJECT')] + [field('channelAccount.' + name) for name in
                                                        ('channelCode', 'channelAccountId', 'entityNo', 'accountVaultToken')]
AUTHORIZE_RESPONSE = [field('authorizationStatus'), field('nextAction', 'OBJECT', False), field('nextAction.type', required=False),
                      field('nextAction.redirectUrl', required=False), field('channelTransactionRef', required=False),
                      field('channelStatus', required=False), field('failureCode', required=False), field('failureMessage', required=False)]
CAPTURE_REQUEST = ACCOUNT_FIELDS + [field('capture', 'OBJECT'), field('capture.captureId'), field('capture.paymentAttemptId'),
                                    field('capture.amount', 'OBJECT'), field('capture.amount.value', 'DECIMAL'), field('capture.amount.currency')]
CAPTURE_RESPONSE = [field('captureStatus'), field('channelCaptureRef', required=False), field('channelStatus', required=False),
                    field('failureCode', required=False), field('failureMessage', required=False)]


def ensure_standard_apis(token):
    """只新增：已存在则不修改（标准 API 修改会复核全部实现，这里不需要）。"""
    pay = call('standard-api-PAY-get', 'GET', f'{ADMIN}/standard-apis/PAY', token)[1]
    pay_fields = [{k: f.get(k) for k in FIELD_KEYS} for f in pay['requestFields']]
    created = []
    for code, name, description, request, response in (
            ('AUTHORIZE', '预授权', '渠道无关标准 API：卡预授权，冻结额度不扣款（不含 3DS）；请求字段与 PAY 相同', pay_fields, AUTHORIZE_RESPONSE),
            ('CAPTURE', '请款', '渠道无关标准 API：对已成功的预授权请款，可部分、可多次', CAPTURE_REQUEST, CAPTURE_RESPONSE)):
        found = call(f'standard-api-{code}-get', 'GET', f'{ADMIN}/standard-apis/{code}', token, expected=(200, 404))[0]
        if found == 404:
            call(f'standard-api-{code}-create', 'POST', f'{ADMIN}/standard-apis', token, {
                'standardApiCode': code, 'standardApiName': name, 'description': description, 'requestFields': request,
                'responseFields': response, 'reason': REASON + '：新增 ' + code})
            created.append(code)
    return created


def configure(base_url):
    token = login()
    added = extend_pay(token) + ensure_standard_apis(token)
    base = f'{ADMIN}/integrations/{INTEGRATION}'
    status_code = call('integration-apis', 'GET', f'{base}/apis', token, expected=(200, 404))[0]
    if status_code == 404:
        # 统一出口第二段（2026-10-22 上 mor-test）把接入方并入渠道，旧 /integrations 维护接口已删除；
        # 新环境须先在运营「渠道」建支付渠道 I_MPGS_NI 并配置出站端点 MPGS_API（base_url，标记沙箱）。
        raise SystemExit(f'渠道 {INTEGRATION} 不存在，请先在运营控制台建渠道并配置端点 {ENDPOINT} = {base_url}（沙箱）')
    body = {'scriptName': 'Basic 认证（merchant.<id>:apiPassword）', 'executionStage': 'REQUEST', 'sourceCode': AUTH_SOURCE, 'description': None,
            'parameterDefinition': [{'name': 'apiUser', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                                    {'name': 'apiPassword', 'dataType': 'PROTECTED_HANDLE', 'required': True}]}
    upsert(token, 'script-' + AUTH, f'{base}/scripts/{AUTH}', f'{base}/scripts', {'scriptCode': AUTH, **body}, body)
    for code, name, request, definition_json in APIS:
        body = {'apiName': name, 'description': None, 'requestFields': request, 'responseFields': RESPONSE_FIELDS, 'definition': definition_json}
        upsert(token, 'api-' + code, f'{base}/apis/{code}', f'{base}/apis', {'apiCode': code, **body}, body)
    for code, flow_definition in FLOWS:
        found, current = call('implementation-get-' + code, 'GET', f'{base}/implementations/{code}', token, expected=(200, 404))
        check = call('implementation-validate-' + code, 'POST', f'{base}/implementations/validate', token,
                     {'standardApiCode': code, 'flowDefinition': flow_definition,
                      'expectedVersion': current['version'] if found == 200 else None})[1]
        assert check['valid'], (code, check)
        upsert(token, 'implementation-' + code, f'{base}/implementations/{code}', f'{base}/implementations',
               {'standardApiCode': code, 'flowDefinition': flow_definition}, {'flowDefinition': flow_definition})
    return added


if __name__ == '__main__':
    endpoint = sys.argv[1] if len(sys.argv) > 1 else 'https://test-gateway.mastercard.com'
    added_fields = configure(endpoint)
    (runtime / f'configure-{INTEGRATION}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integration': INTEGRATION, 'standardApiChanges': added_fields, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update'))]}, ensure_ascii=False))
