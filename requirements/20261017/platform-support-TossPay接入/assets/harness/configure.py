"""Toss Pay 接入阶段 3：经真实管理接口保存（存在则按版本修改）接入方 TOSS_PAY 的出站地址、退款匹配脚本、
4 个原子 API 及 PAY、PAY_QUERY、REFUND、REFUND_QUERY 四条流程（技术方案第 4 节）。不改标准 API。

apiKey 写在 JSON 请求体，原子 API 用 credentialFields 声明（技术方案 T1）。

用法：E2E_RUNTIME=<运行目录> python3 configure.py <接入方编码> <出站 base URL>
  mor-test：python3 configure.py TOSS_PAY https://pay.toss.im
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

REASON = 'Toss Pay 接入（requirements/20261017）'
ENDPOINT = 'TOSS_API'
PICK = 'TOSS_PICK_REFUND'


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


def krw_units(amount_path, currency_path):
    """只收 KRW（技术方案 D2）：币种不是 KRW 时 currency 为空，必填参数缺失使节点 ERROR、不发请求；有小数时函数报错同样 ERROR。"""
    return {'kind': 'FUNCTION', 'function': 'MONEY_MINOR_UNITS', 'result': 'result',
            'args': {'amount': expr(f'str({amount_path})'), 'currency': expr(f"{currency_path} == 'KRW' ? 'KRW' : nil")}}


# 在 /api/v2/status 的 transactions[] 中按我方退款单号取退款记录。
PICK_SOURCE = """if (http.responseBody != nil && http.responseBody.transactions != nil) {
  for item in http.responseBody.transactions {
    if (item.stepType == 'REFUND' && item.refundNo == params.refundId) {
      http.responseBody.refundRecord = item;
    }
  }
}"""
SCRIPTS = ((PICK, '退款查询：按退款单号匹配 transactions[]', 'RESPONSE', [('refundId', 'STRING')], PICK_SOURCE),)

# ---- 原子 API ----
BODY = 'http.responseBody'
OK = f'http.responseStatus == 200 && {BODY} != nil'
DECLINED = f'{OK} && {BODY}.code == -1 && {BODY}.errorCode != nil'
# 查不存在的单与 apiKey 错误的 errorCode 相同，只有 msg 以 orderNo 结尾才判不存在（技术方案 D3）。
NOT_FOUND = (f"{DECLINED} && {BODY}.errorCode == 'COMMON_INVALID_PARAMETER' && {BODY}.msg != nil"
             f" && string.endsWith({BODY}.msg, 'orderNo')")
ERROR_FIELDS = [field('code', 'INTEGER'), field('errorCode', required=False), field('msg', required=False)]
FAIL_OUTPUTS = [output('errorCode', path(BODY + '.errorCode')), output('errorMessage', path(BODY + '.msg'), required=False)]
KEY_PARAM = {'BODY:apiKey': path('resources.channelAccount.credentials.apiKey')}


def definition(path_template, status_rules, outputs, params=(), scripts=(), fixed=None):
    return {
        'params': list(params),
        'fixedValues': fixed or {},
        'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': ENDPOINT, 'pathTemplate': path_template,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 10000},
        'requestEncoding': 'JSON', 'responseDecoding': 'JSON', 'decimalEncoding': 'NUMBER',
        'credentialFields': ['BODY:apiKey'],
        'scripts': list(scripts),
        'statusRules': status_rules,
        'outputs': outputs,
    }


PAY_REQUEST = [field('apiKey'), field('orderNo'), field('amount', 'INTEGER'), field('amountTaxFree', 'INTEGER'), field('productDesc'),
               field('retUrl'), field('retCancelUrl'), field('autoExecute', 'BOOLEAN'), field('resultCallback'), field('callbackVersion')]
PAY_RESPONSE = ERROR_FIELDS + [field('payToken', required=False), field('checkoutPage', required=False)]
CREATE_PAYMENT = definition('/api/v2/payments', [
    {'condition': f"{OK} && {BODY}.code == 0 && {BODY}.payToken != nil && {BODY}.checkoutPage != nil", 'executeStatus': 'SUCCESS'},
    {'condition': DECLINED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('payToken', path(BODY + '.payToken')), output('checkoutPage', path(BODY + '.checkoutPage'))],
     'FAIL': FAIL_OUTPUTS},
    fixed={'BODY:amountTaxFree': expr('0'), 'BODY:autoExecute': expr('true'), 'BODY:callbackVersion': const('V2')})

STATUS_REQUEST = [field('apiKey'), field('orderNo')]
STATUS_RESPONSE = ERROR_FIELDS + [field('payToken', required=False), field('payStatus', required=False), field('amount', 'DECIMAL', False),
                                  field('transactions', 'ARRAY', False), field('transactions[].stepType', required=False),
                                  field('transactions[].refundNo', required=False)]
GET_STATUS = definition('/api/v2/status', [
    {'condition': f"{OK} && {BODY}.code == 0 && {BODY}.payStatus != nil", 'executeStatus': 'SUCCESS'},
    {'condition': NOT_FOUND, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('payStatus', path(BODY + '.payStatus')), output('payToken', path(BODY + '.payToken'), required=False),
                 output('amount', path(BODY + '.amount'), 'DECIMAL', False)],
     'FAIL': FAIL_OUTPUTS})

RECORD = BODY + '.refundRecord'
GET_STATUS_REFUND = definition('/api/v2/status', [
    {'condition': f"{OK} && {BODY}.code == 0 && {RECORD} != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"{OK} && {BODY}.code == 0 && {BODY}.payStatus != nil", 'executeStatus': 'FAIL'},
    {'condition': NOT_FOUND, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('refundNo', path(RECORD + '.refundNo'))],
     'FAIL': [output('errorCode', expr(f"{BODY}.code == 0 ? 'REFUND_NOT_EXIST' : {BODY}.errorCode")),
              output('errorMessage', expr(f"{BODY}.code == 0 ? 'Refund not found' : {BODY}.msg"), required=False)]},
    params=[{'name': 'refundId', 'dataType': 'STRING', 'required': True}],
    scripts=[{'id': 'pickRefund', 'scriptCode': PICK, 'stage': 'RESPONSE', 'params': {'refundId': path('params.refundId')}}])

REFUND_REQUEST = [field('apiKey'), field('payToken'), field('refundNo'), field('amount', 'INTEGER'), field('amountTaxFree', 'INTEGER'),
                  field('reason', required=False)]
REFUND_RESPONSE = ERROR_FIELDS + [field('refundNo', required=False), field('transactionId', required=False)]
CREATE_REFUND = definition('/api/v2/refunds', [
    {'condition': f"{OK} && {BODY}.code == 0", 'executeStatus': 'SUCCESS'},
    {'condition': DECLINED, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('transactionId', path(BODY + '.transactionId'), required=False)], 'FAIL': FAIL_OUTPUTS},
    fixed={'BODY:amountTaxFree': expr('0')})

APIS = (
    ('CREATE_PAYMENT', '建单 POST /api/v2/payments', PAY_REQUEST, PAY_RESPONSE, CREATE_PAYMENT),
    ('GET_STATUS', '按我方单号查单 POST /api/v2/status', STATUS_REQUEST, STATUS_RESPONSE, GET_STATUS),
    ('GET_STATUS_REFUND', '退款查询 POST /api/v2/status（按退款单号匹配 transactions）', STATUS_REQUEST, STATUS_RESPONSE, GET_STATUS_REFUND),
    ('CREATE_REFUND', '退款 POST /api/v2/refunds', REFUND_REQUEST, REFUND_RESPONSE, CREATE_REFUND),
)

# ---- 流程 ----
INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]


def status(node, value):
    return f"steps.{node}.executeStatus == '{value}'"


def node(node_id, api, request, params=None):
    return {'id': node_id, 'integrationApi': api, 'params': params or {}, 'request': {**KEY_PARAM, **request}}


def flow(timeout_ms, nodes, branches):
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
    return {'timeoutMs': timeout_ms, 'initializers': INITIALIZER, 'entry': nodes[0]['id'], 'nodes': nodes, 'returns': returns, 'edges': edges}


def failure(out):
    return {'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage')}


def status_node(attempt_path):
    return node('getStatus', 'GET_STATUS', {'BODY:orderNo': path(attempt_path)})


def one_of(value, *items):
    return '(' + ' || '.join(f"{value} == '{item}'" for item in items) + ')'


P = 'steps.createPayment.outputs.'
RETURN = 'input.payment.returnUrl'
PAY_FLOW = flow(20000, [node('createPayment', 'CREATE_PAYMENT', {
    'BODY:orderNo': path('input.payment.attemptId'),
    'BODY:amount': krw_units('input.payment.amount.value', 'input.payment.amount.currency'),
    'BODY:productDesc': expr("input.payment.subject != nil ? input.payment.subject : 'MoR payment'"),
    'BODY:retUrl': path(RETURN), 'BODY:retCancelUrl': path(RETURN),
    # 本期不接回调，但 Toss 要求必填：有 notifyUrl 用 notifyUrl，否则用 returnUrl。
    'BODY:resultCallback': expr(f"input.payment.notifyUrl != nil ? input.payment.notifyUrl : {RETURN}")})], [
    ('createPayment', ('redirect', 'SUCCESS', {'paymentStatus': const('PENDING'), 'nextAction.type': const('REDIRECT'),
                                               'nextAction.redirectUrl': path(P + 'checkoutPage'), 'channelTransactionRef': path(P + 'payToken')}),
     status('createPayment', 'SUCCESS')),
    ('createPayment', ('rejected', 'FAIL', {'paymentStatus': const('FAILED'), **failure(P)}), status('createPayment', 'FAIL'))])

O = 'steps.getStatus.outputs.'
OS = O + 'payStatus'
PENDING = one_of(OS, 'PAY_STANDBY', 'PAY_APPROVED', 'PAY_PROGRESS')
# 退款相关状态的付款本身已成功，查单按 SUCCEEDED 返回，退款结果由退款查询给出（技术方案 D4）。
SUCCEEDED = one_of(OS, 'PAY_COMPLETE', 'SETTLEMENT_COMPLETE', 'REFUND_PROGRESS', 'REFUND_SUCCESS', 'SETTLEMENT_REFUND_COMPLETE')
PAY_QUERY_FLOW = flow(15000, [status_node('input.payment.attemptId')], [
    ('getStatus', ('found', 'SUCCESS', {
        'paymentStatus': expr(f"{SUCCEEDED} ? 'SUCCEEDED' : ({OS} == 'PAY_CANCEL' ? 'FAILED' : 'PENDING')"),
        'channelTransactionRef': path(O + 'payToken'), 'channelStatus': path(OS),
        'amount.value': path(O + 'amount'), 'amount.currency': const('KRW'),
        'failureCode': expr(f"{OS} == 'PAY_CANCEL' ? {OS} : nil")}),
     status('getStatus', 'SUCCESS') + f" && ({PENDING} || {SUCCEEDED} || {OS} == 'PAY_CANCEL')"),
    ('getStatus', ('notFound', 'FAIL', {'paymentStatus': const('NOT_FOUND'), **failure(O)}), status('getStatus', 'FAIL'))])

R = 'steps.createRefund.outputs.'
REFUND_FLOW = flow(25000, [status_node('input.refund.paymentAttemptId'), node('createRefund', 'CREATE_REFUND', {
    'BODY:payToken': path(O + 'payToken'), 'BODY:refundNo': path('input.refund.refundId'),
    'BODY:amount': krw_units('input.refund.amount.value', 'input.refund.amount.currency'),
    'BODY:reason': path('input.refund.reason')})], [
    ('getStatus', ('paymentNotFound', 'FAIL', {'refundStatus': const('FAILED'), **failure(O)}), status('getStatus', 'FAIL')),
    ('getStatus', 'createRefund', status('getStatus', 'SUCCESS')),
    # Toss 同步完成退款，code=0 即成功（技术方案 D5）。
    ('createRefund', ('refunded', 'SUCCESS', {'refundStatus': const('SUCCEEDED'), 'channelRefundRef': path(R + 'transactionId')}),
     status('createRefund', 'SUCCESS')),
    ('createRefund', ('rejected', 'FAIL', {'refundStatus': const('FAILED'), **failure(R)}), status('createRefund', 'FAIL'))])

RQ = 'steps.getRefund.outputs.'
REFUND_QUERY_FLOW = flow(15000, [node('getRefund', 'GET_STATUS_REFUND', {'BODY:orderNo': path('input.refund.paymentAttemptId')},
                                      {'refundId': path('input.refund.refundId')})], [
    ('getRefund', ('found', 'SUCCESS', {'refundStatus': const('SUCCEEDED'), 'channelStatus': const('REFUND')}), status('getRefund', 'SUCCESS')),
    ('getRefund', ('notFound', 'FAIL', {'refundStatus': const('NOT_FOUND'), **failure(RQ)}), status('getRefund', 'FAIL'))])

FLOWS = (('PAY', PAY_FLOW), ('PAY_QUERY', PAY_QUERY_FLOW), ('REFUND', REFUND_FLOW), ('REFUND_QUERY', REFUND_QUERY_FLOW))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


def configure(integration, base_url):
    token = login()
    body = {'integrationName': f'Toss Pay（{integration}）', 'description': 'Toss Pay 一般结算：建单、查单、退款、退款查询（KRW）',
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
