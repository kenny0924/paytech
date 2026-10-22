"""Cybersource（Network International）接入阶段 5：经 mor-test 真实管理接口保存技术方案第 3、5 节的配置（存在则按版本修改）。

1. 支付渠道 I_CYBS_NI（运营渠道接口，统一出口第二段后接入方即渠道）与出站端点 CYBS_API（标记沙箱）。
2. 请求签名脚本 CYBS_HTTP_SIGNATURE（C2）与 Search 结果挑选脚本 CYBS_PICK（C5）。
3. 原子 API PAYMENT、REFUND、CAPTURE、SEARCH。
4. 标准 API REFUND / CAPTURE 各追加可选字段 paymentChannelRef（F1；已存在则跳过，服务端复核全部接入方流程）。
5. PAY、AUTHORIZE、PAY_QUERY、REFUND、REFUND_QUERY、CAPTURE 六条流程，每条先校验再保存。

用法：CYBS_RUNTIME=<仓库外运行目录> python3 configure.py [base URL，默认 https://apitest.cybersource.com]
"""
import json, sys

from mortest import ADMIN, call, login, results, runtime

CHANNEL = 'I_CYBS_NI'
REASON = 'Cybersource（NI）接入（requirements/20261022/channel-connector-CYBS_NI接入）'
ENDPOINT = 'CYBS_API'
SIGN = 'CYBS_HTTP_SIGNATURE'
PICK = 'CYBS_PICK'
B = 'http.responseBody'


def field(path_, data_type='STRING', required=True, location='BODY'):
    return {'fieldLocation': location, 'fieldPath': path_, 'fieldName': path_, 'dataType': data_type, 'required': required, 'description': None}


def path(value):
    return {'kind': 'PATH', 'path': value}


def expr(value):
    return {'kind': 'EXPRESSION', 'expression': value}


def const(text):
    return expr(f"'{text}'")


def output(name, value, required=True, data_type='STRING'):
    return {'name': name, 'dataType': data_type, 'required': required, 'value': value}


# ---- 公共脚本 ----
# C2：Digest 覆盖锁定后实际发送的字节；签名串 5 行；密钥在句柄内 Base64 解码。keyId 是账号参数（C3）。
SIGN_SOURCE = """let digest = 'SHA-256=' + sha256Base64(sealJsonBody(http));
let date = nowRfc1123();
let content = 'host: ' + params.host + '\\ndate: ' + date + '\\nrequest-target: ' + params.requestTarget
  + '\\ndigest: ' + digest + '\\nv-c-merchant-id: ' + params.merchantId;
seq.put(http.requestHeaders, 'Date', date);
seq.put(http.requestHeaders, 'Digest', digest);
seq.put(http.requestHeaders, 'v-c-merchant-id', params.merchantId);
seq.put(http.requestHeaders, 'Signature', 'keyid="' + params.keyId
  + '", algorithm="HmacSHA256", headers="host date request-target digest v-c-merchant-id", signature="'
  + hmacSha256Base64DecodedKey(params.secretKey, content) + '"');
"""
SIGN_PARAMS = [{'name': 'secretKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
               {'name': 'keyId', 'dataType': 'STRING', 'required': True},
               {'name': 'merchantId', 'dataType': 'STRING', 'required': True},
               {'name': 'host', 'dataType': 'STRING', 'required': True},
               {'name': 'requestTarget', 'dataType': 'STRING', 'required': True}]

# C5：取第一条非重复记录（跳过 rFlag=DDUPLICATE 与顶层 104/105），并取出 ics_auth / ics_bill / ics_credit 的 rCode（有界 for）。
PICK_SOURCE = """let summaries = json.get(http.responseBody, '_embedded.transactionSummaries');
if (http.responseStatus == 201 && summaries != nil) {
  for item in summaries {
    let info = item.applicationInformation;
    if (http.responseBody.picked == nil && info != nil && info.rFlag != 'DDUPLICATE' && info.reasonCode != '104'
        && info.reasonCode != '105') {
      http.responseBody.picked = item;
      if (info.applications != nil) {
        for app in info.applications {
          if (app.name == 'ics_auth') { http.responseBody.pickedAuth = app.rCode; }
          if (app.name == 'ics_bill') { http.responseBody.pickedBill = app.rCode; }
          if (app.name == 'ics_credit') { http.responseBody.pickedCredit = app.rCode; }
        }
      }
    }
  }
}
"""

# ---- 原子 API ----
ACCOUNT_PARAMS = [{'name': 'secretKey', 'dataType': 'PROTECTED_HANDLE', 'required': True},
                  {'name': 'keyId', 'dataType': 'STRING', 'required': True},
                  {'name': 'merchantId', 'dataType': 'STRING', 'required': True},
                  {'name': 'host', 'dataType': 'STRING', 'required': True}]
PAYMENT_ID_PARAM = [{'name': 'paymentId', 'dataType': 'STRING', 'required': True}]


def sign_script(request_target):
    return {'id': 'sign', 'scriptCode': SIGN, 'stage': 'REQUEST', 'params': {
        'secretKey': path('params.secretKey'), 'keyId': path('params.keyId'), 'merchantId': path('params.merchantId'),
        'host': path('params.host'), 'requestTarget': expr(request_target)}}


def definition(path_template, request_target, status_rules, outputs, fixed=None, params=(), scripts=()):
    return {
        'params': ACCOUNT_PARAMS + list(params),
        'fixedValues': fixed or {},
        'transport': {'protocol': 'HTTP', 'method': 'POST', 'endpointCode': ENDPOINT, 'pathTemplate': path_template,
                      'connectTimeoutMs': 2000, 'responseTimeoutMs': 15000},
        'requestEncoding': 'JSON', 'responseDecoding': 'JSON', 'decimalEncoding': 'STRING',
        'scripts': [sign_script(request_target), *scripts],
        'statusRules': status_rules,
        'outputs': outputs,
    }


RESPONSE_FIELDS = [field('id', required=False), field('status', required=False), field('reason', required=False),
                   field('message', required=False), field('errorInformation', 'OBJECT', False),
                   field('errorInformation.reason', required=False), field('errorInformation.message', required=False),
                   field('processorInformation', 'OBJECT', False), field('processorInformation.retrievalReferenceNumber', required=False)]
INVALID = f"http.responseStatus == 400 && {B}.status == 'INVALID_REQUEST'"
FAIL_OUTPUTS = [output('errorCode', expr(f"{B}.reason != nil ? {B}.reason : {B}.status")),
                output('errorMessage', path(B + '.message'), required=False)]
CARD = 'paymentInformation.card.'
CARD_FIELDS = {'number': 'pan', 'expirationMonth': 'expiryMonth', 'expirationYear': 'expiryYear', 'securityCode': 'cvv'}
BILL_TO = {'firstName': 'NOREAL', 'lastName': 'NAME', 'country': 'US', 'address1': '1295 Charleston Road', 'postalCode': '94043',
           'locality': 'Mountain View', 'administrativeArea': 'CA', 'email': 'null@cybersource.com'}
PAYMENT_REQUEST = [
    field('clientReferenceInformation', 'OBJECT', False), field('clientReferenceInformation.code'),
    field('clientReferenceInformation.transactionId'), field('processingInformation', 'OBJECT', False),
    field('processingInformation.commerceIndicator'), field('processingInformation.capture', 'BOOLEAN'),
    field('orderInformation', 'OBJECT', False), field('orderInformation.billTo', 'OBJECT', False)] + [
    field('orderInformation.billTo.' + name) for name in BILL_TO] + [
    field('orderInformation.amountDetails', 'OBJECT', False), field('orderInformation.amountDetails.totalAmount'),
    field('orderInformation.amountDetails.currency'), field('paymentInformation', 'OBJECT', False),
    field(CARD[:-1], 'OBJECT', False)] + [field(CARD + name) for name in CARD_FIELDS]
# C6：201 且为已知终态或待审核 → SUCCESS，由流程按 status 映射；400 INVALID_REQUEST（含 DUPLICATE_REQUEST）→ FAIL；其余 ERROR。
PAYMENT_STATUSES = ('AUTHORIZED', 'DECLINED', 'AUTHORIZED_RISK_DECLINED', 'AUTHORIZED_PENDING_REVIEW', 'PENDING_REVIEW')
PAYMENT_DEFINITION = definition('/pts/v2/payments', "'post /pts/v2/payments'", [
    {'condition': f"http.responseStatus == 201 && ({' || '.join(f'{B}.status == {chr(39)}{s}{chr(39)}' for s in PAYMENT_STATUSES)})",
     'executeStatus': 'SUCCESS'},
    {'condition': INVALID, 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('status', path(B + '.status')), output('id', path(B + '.id'), required=False),
                 output('reason', path(B + '.errorInformation.reason'), required=False),
                 output('message', path(B + '.errorInformation.message'), required=False)],
     'FAIL': FAIL_OUTPUTS},
    {'BODY:processingInformation.commerceIndicator': const('internet'),
     **{'BODY:orderInformation.billTo.' + name: const(value) for name, value in BILL_TO.items()}})
PAYMENT_DEFINITION['cardFields'] = ['BODY:' + CARD + name for name in CARD_FIELDS]

FOLLOW_REQUEST = [field('paymentId', location='PATH'), field('clientReferenceInformation', 'OBJECT', False),
                  field('clientReferenceInformation.code'), field('clientReferenceInformation.transactionId'),
                  field('orderInformation', 'OBJECT', False), field('orderInformation.amountDetails', 'OBJECT', False),
                  field('orderInformation.amountDetails.totalAmount'), field('orderInformation.amountDetails.currency')]


def follow_definition(kind):
    """退款与请款：路径挂原单 id；同步 PENDING 即受理成功（C6）。"""
    return definition('/pts/v2/payments/{paymentId}/' + kind, f"'post /pts/v2/payments/' + params.paymentId + '/{kind}'", [
        {'condition': f"http.responseStatus == 201 && ({B}.status == 'PENDING' || {B}.status == 'TRANSMITTED')",
         'executeStatus': 'SUCCESS'},
        {'condition': INVALID, 'executeStatus': 'FAIL'}],
        {'SUCCESS': [output('status', path(B + '.status')), output('id', path(B + '.id'))], 'FAIL': FAIL_OUTPUTS},
        params=PAYMENT_ID_PARAM)


SEARCH_REQUEST = [field('query'), field('save', 'BOOLEAN'), field('limit', 'INTEGER'), field('sort')]
SEARCH_RESPONSE = [field('totalCount', 'INTEGER', False), field('_embedded', 'OBJECT', False),
                   field('_embedded.transactionSummaries', 'ARRAY', False)]
P = B + '.picked.'
SEARCH_DEFINITION = definition('/tss/v2/searches', "'post /tss/v2/searches'", [
    {'condition': f"http.responseStatus == 201 && {B}.picked != nil", 'executeStatus': 'SUCCESS'},
    {'condition': f"http.responseStatus == 201 && {B}.picked == nil", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('id', path(P + 'id')), output('rFlag', path(P + 'applicationInformation.rFlag'), required=False),
                 output('auth', path(B + '.pickedAuth'), required=False), output('bill', path(B + '.pickedBill'), required=False),
                 output('credit', path(B + '.pickedCredit'), required=False),
                 # 拒付记录 amountDetails 为空；函数参数必填，缺失时以 0 USD 占位（流程只在成功分支输出金额）。
                 output('amountMinor', {'kind': 'FUNCTION', 'function': 'MONEY_MINOR_UNITS', 'result': 'str(result)', 'args': {
                     'amount': expr(f"{P}orderInformation.amountDetails.totalAmount != nil ? {P}orderInformation.amountDetails.totalAmount : '0'"),
                     'currency': expr(f"{P}orderInformation.amountDetails.currency != nil ? {P}orderInformation.amountDetails.currency : 'USD'")}},
                        required=False),
                 output('currency', path(P + 'orderInformation.amountDetails.currency'), required=False)],
     'FAIL': [output('errorCode', const('NOT_FOUND')), output('errorMessage', const('No transaction for this code'), required=False)]},
    {'BODY:save': expr('false'), 'BODY:limit': expr('20'), 'BODY:sort': const('submitTimeUtc:desc')},
    scripts=[{'id': 'pick', 'scriptCode': PICK, 'stage': 'RESPONSE', 'params': {}}])

APIS = (
    ('PAYMENT', '下单 / 预授权 POST /pts/v2/payments（capture 由流程给出）', PAYMENT_REQUEST, RESPONSE_FIELDS, PAYMENT_DEFINITION),
    ('REFUND', '退款 POST /pts/v2/payments/{id}/refunds', FOLLOW_REQUEST, RESPONSE_FIELDS, follow_definition('refunds')),
    ('CAPTURE', '请款 POST /pts/v2/payments/{id}/captures', FOLLOW_REQUEST, RESPONSE_FIELDS, follow_definition('captures')),
    ('SEARCH', '交易检索 POST /tss/v2/searches（按 clientReferenceInformation.code）', SEARCH_REQUEST, SEARCH_RESPONSE, SEARCH_DEFINITION),
)

# ---- 流程 ----
INITIALIZER = [{'id': 'channelAccount', 'function': 'CHANNEL_ACCOUNT_CONFIG', 'required': True, 'args': {
    'vaultToken': path('input.channelAccount.accountVaultToken'), 'channelCode': path('input.channelAccount.channelCode'),
    'channelAccountId': path('input.channelAccount.channelAccountId'), 'entityNo': path('input.channelAccount.entityNo')}}]
CARD_INITIALIZER = {'id': 'card', 'function': 'PAYMENT_CARD', 'required': True, 'args': {
    'paymentInstrumentToken': path('input.card.token'), 'paymentAttemptId': path('input.payment.attemptId')}}
NODE_PARAMS = {'secretKey': path('resources.channelAccount.credentials.secretKey'),
               'keyId': path('resources.channelAccount.parameters.keyId'),
               'merchantId': path('resources.channelAccount.parameters.merchantId'),
               'host': path('resources.channelAccount.parameters.host')}


def status(node_id, value):
    return f"steps.{node_id}.executeStatus == '{value}'"


def node(node_id, api, request, params=None):
    return {'id': node_id, 'integrationApi': api, 'params': {**NODE_PARAMS, **(params or {})}, 'request': request}


def search(node_id, code_path):
    return node(node_id, 'SEARCH', {'BODY:query': expr(f"'clientReferenceInformation.code:' + {code_path}")})


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


def rejected(out, status_field):
    return {status_field: const('FAILED'), 'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage'),
            'channelStatus': const('INVALID_REQUEST')}


DUPLICATE = "outputs.errorCode == 'DUPLICATE_REQUEST'"


SEARCH_ATTEMPTS = 4


def chain(prefix):
    """Search 有 1～3 秒索引延迟（阶段 5 实测）：同一检索最多连发 SEARCH_ATTEMPTS 次（mor-test 每次约 0.4 秒），首个命中即停。
    流程校验只允许读取必经节点，所以每个检索节点有自己的返回出口（返回出口上限 16）。"""
    return [f'{prefix}{i}' for i in range(1, SEARCH_ATTEMPTS + 1)]


def chain_nodes(prefix, code_path):
    return [search(node_id, code_path) for node_id in chain(prefix)]


def chain_branches(prefix, found):
    """found(q) → [(return_id, executeStatus, outputs, condition)]；按节点生成返回出口，未命中沿链重试。"""
    ids, branches = chain(prefix), []
    for index, node_id in enumerate(ids, start=1):
        for return_id, execute_status, outputs, condition in found(f'steps.{node_id}.outputs.'):
            branches.append((node_id, (f'{return_id}{index}', execute_status, outputs), f"{status(node_id, 'SUCCESS')} && {condition}"))
        if index < len(ids):
            branches.append((node_id, ids[index], status(node_id, 'FAIL')))
    return branches


def last(prefix):
    return chain(prefix)[-1]


def amount_of(q):
    return {'amount.value': {'kind': 'FUNCTION', 'function': 'MONEY_MAJOR_UNITS', 'result': 'result',
                             'args': {'amount': path(q + 'amountMinor'), 'currency': path(q + 'currency')}},
            'amount.currency': path(q + 'currency')}


def query_flow(code_path, status_field, found):
    """C10 / C11：查无记录时沿检索链重试，最后一次仍无记录才返回 NOT_FOUND。"""
    q = f"steps.{last('query')}.outputs."
    return flow(20000, chain_nodes('query', code_path), chain_branches('query', found) + [
        (last('query'), ('notFound', 'FAIL', {status_field: const('NOT_FOUND'), 'failureCode': path(q + 'errorCode'),
                                              'failureMessage': path(q + 'errorMessage')}), status(last('query'), 'FAIL'))])


# C10
PAY_QUERY_FLOW = query_flow('input.payment.attemptId', 'paymentStatus', lambda q: [
    ('succeeded', 'SUCCESS', {'paymentStatus': const('SUCCEEDED'), 'channelTransactionRef': path(q + 'id'),
                              'channelStatus': path(q + 'rFlag'), **amount_of(q)}, f"{q}auth == '1' && {q}bill == '1'"),
    ('failed', 'SUCCESS', {'paymentStatus': const('FAILED'), 'channelTransactionRef': path(q + 'id'), 'channelStatus': path(q + 'rFlag'),
                           'failureCode': path(q + 'rFlag')}, f"{q}auth == '0'"),
    ('notPayment', 'FAIL', {'paymentStatus': const('NOT_FOUND'), 'failureCode': const('NOT_A_PAYMENT'), 'channelStatus': path(q + 'rFlag')},
     f"{q}auth == '1' && {q}bill == nil")])

# C11
REFUND_QUERY_FLOW = query_flow('input.refund.refundId', 'refundStatus', lambda q: [
    ('succeeded', 'SUCCESS', {'refundStatus': const('SUCCEEDED'), 'channelRefundRef': path(q + 'id'), 'channelStatus': path(q + 'rFlag'),
                              **amount_of(q)}, f"{q}credit == '1'"),
    ('failed', 'SUCCESS', {'refundStatus': const('FAILED'), 'channelRefundRef': path(q + 'id'), 'channelStatus': path(q + 'rFlag'),
                           'failureCode': path(q + 'rFlag')}, f"{q}credit == '0'"),
    ('notRefund', 'FAIL', {'refundStatus': const('NOT_FOUND'), 'failureCode': const('NOT_A_REFUND'), 'channelStatus': path(q + 'rFlag')},
     f"{q}credit == nil")])


def payment_flow(capture, status_field):
    """PAY / AUTHORIZE：C6 状态；DUPLICATE_REQUEST 时按 attemptId 沿检索链回查（C7），回查不到 → ERROR（结果未知）。"""
    o = 'steps.payment.outputs.'
    known = {'channelTransactionRef': path(o + 'id'), 'channelStatus': path(o + 'status')}
    request = {'BODY:clientReferenceInformation.code': path('input.payment.attemptId'),
               'BODY:clientReferenceInformation.transactionId': path('input.payment.attemptId'),
               'BODY:processingInformation.capture': expr('true' if capture else 'false'),
               'BODY:orderInformation.amountDetails.totalAmount': expr('str(input.payment.amount.value)'),
               'BODY:orderInformation.amountDetails.currency': path('input.payment.amount.currency'),
               **{'BODY:' + CARD + name: path('resources.card.' + value) for name, value in CARD_FIELDS.items()}}
    authorized = (lambda r: f"{r}auth == '1' && {r}bill == '1'") if capture else (lambda r: f"{r}auth == '1' && {r}bill == nil")
    return flow(25000, [node('payment', 'PAYMENT', request), *chain_nodes('recheck', 'input.payment.attemptId')], [
        ('payment', ('succeeded', 'SUCCESS', {status_field: const('SUCCEEDED'), **known}),
         f"{status('payment', 'SUCCESS')} && {o}status == 'AUTHORIZED'"),
        ('payment', ('declined', 'SUCCESS', {status_field: const('FAILED'), **known, 'failureCode': path(o + 'reason'),
                                             'failureMessage': path(o + 'message')}),
         f"{status('payment', 'SUCCESS')} && ({o}status == 'DECLINED' || {o}status == 'AUTHORIZED_RISK_DECLINED')"),
        ('payment', ('pending', 'SUCCESS', {status_field: const('PENDING'), **known}),
         f"{status('payment', 'SUCCESS')} && ({o}status == 'AUTHORIZED_PENDING_REVIEW' || {o}status == 'PENDING_REVIEW')"),
        ('payment', 'recheck1', f"{status('payment', 'FAIL')} && steps.payment.{DUPLICATE}"),
        ('payment', ('rejected', 'FAIL', rejected(o, status_field)), status('payment', 'FAIL'))] + chain_branches('recheck', lambda r: [
        ('succeededOnRecheck', 'SUCCESS', {status_field: const('SUCCEEDED'), 'channelTransactionRef': path(r + 'id'),
                                           'channelStatus': const('AUTHORIZED')}, authorized(r)),
        ('declinedOnRecheck', 'SUCCESS', {status_field: const('FAILED'), 'channelTransactionRef': path(r + 'id'),
                                          'channelStatus': path(r + 'rFlag'), 'failureCode': path(r + 'rFlag')}, f"{r}auth == '0'")]),
        INITIALIZER + [CARD_INITIALIZER])


def follow_flow(api, root, code_field, status_field, ref_field, application):
    """REFUND / CAPTURE（F1）：Cybersource 读接口最终一致、写接口按 id 立即可用，原单 id 取标准 API 可选字段 paymentChannelRef
    （Payment 保存的下单渠道引用），不再检索。缺失时 PATH 无法构建，按引擎规则未发出（NOT_SENT）。
    DUPLICATE_REQUEST 时按本单号沿检索链回查（C7），查不到 → FAIL。"""
    action, o = api.lower(), f'steps.{api.lower()}.outputs.'
    code, amount, ref = f'input.{root}.{code_field}', f'input.{root}.amount', path(f'input.{root}.paymentChannelRef')
    action_node = node(action, api, {
        'PATH:paymentId': ref, 'BODY:clientReferenceInformation.code': path(code),
        'BODY:clientReferenceInformation.transactionId': path(code),
        'BODY:orderInformation.amountDetails.totalAmount': expr(f'str({amount}.value)'),
        'BODY:orderInformation.amountDetails.currency': path(amount + '.currency')}, {'paymentId': ref})
    duplicate_not_found = ('duplicateNotFound', 'FAIL', rejected(o, status_field))
    return flow(25000, [action_node, *chain_nodes('recheck', code)], [
        (action, ('succeeded', 'SUCCESS', {status_field: const('SUCCEEDED'), ref_field: path(o + 'id'),
                                           'channelStatus': path(o + 'status')}), status(action, 'SUCCESS')),
        (action, 'recheck1', f"{status(action, 'FAIL')} && steps.{action}.{DUPLICATE}"),
        (action, ('rejected', 'FAIL', rejected(o, status_field)), status(action, 'FAIL'))] + chain_branches('recheck', lambda r: [
        ('succeededOnRecheck', 'SUCCESS', {status_field: const('SUCCEEDED'), ref_field: path(r + 'id'),
                                           'channelStatus': const('PENDING')}, f"{r}{application} == '1'")]) + [
        (node_id, duplicate_not_found, f"{status(node_id, 'SUCCESS')} && steps.{node_id}.outputs.{application} != '1'")
        for node_id in chain('recheck')] + [
        (last('recheck'), duplicate_not_found, status(last('recheck'), 'FAIL'))])


FLOWS = (('PAY', payment_flow(True, 'paymentStatus')), ('AUTHORIZE', payment_flow(False, 'authorizationStatus')),
         ('PAY_QUERY', PAY_QUERY_FLOW), ('REFUND', follow_flow('REFUND', 'refund', 'refundId', 'refundStatus', 'channelRefundRef', 'credit')),
         ('REFUND_QUERY', REFUND_QUERY_FLOW),
         ('CAPTURE', follow_flow('CAPTURE', 'capture', 'captureId', 'captureStatus', 'channelCaptureRef', 'bill')))


def upsert(token, name, detail_path, create_path, create_body, update_body):
    """存在则按当前版本修改，否则创建。"""
    status_code, current = call(name + '-get', 'GET', detail_path, token, expected=(200, 404))
    if status_code == 404:
        return call(name + '-create', 'POST', create_path, token, {**create_body, 'reason': REASON})[1]
    return call(name + '-update', 'PUT', detail_path, token, {**update_body, 'expectedVersion': current['version'], 'reason': REASON})[1]


FIELD_KEYS = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')


def extend_standard_api(token, code, added):
    """F1：只追加缺少的可选字段；已存在时不修改。"""
    current = call(f'standard-api-{code}-get', 'GET', f'{ADMIN}/standard-apis/{code}', token)[1]
    request_fields = [{k: f.get(k) for k in FIELD_KEYS} for f in current['requestFields']]
    missing = [f for f in added if f['fieldPath'] not in {item['fieldPath'] for item in request_fields}]
    if not missing:
        return []
    call(f'standard-api-{code}-update', 'PUT', f'{ADMIN}/standard-apis/{code}', token, {
        'standardApiName': current['standardApiName'], 'description': current.get('description'),
        'requestFields': request_fields + missing, 'responseFields': [{k: f.get(k) for k in FIELD_KEYS} for f in current['responseFields']],
        'expectedVersion': current['version'], 'reason': REASON + '：' + code + ' 增加可选 paymentChannelRef'})
    return [code + ':' + f['fieldPath'] for f in missing]


def ensure_channel(token, base_url):
    """C1：支付渠道 + 沙箱端点；渠道保持 DRAFT（与迁移后的其他接入方相同，沙箱验证不要求 ACTIVE）。"""
    found = call('channel-list', 'GET', f'/channel-admin/v1/channels?keyword={CHANNEL}', token)[1]
    channel = next((item for item in found['items'] if item['channelCode'] == CHANNEL), None)
    if channel is None:
        channel = call('channel-create', 'POST', '/channel-admin/v1/channels', token, {
            'channelCode': CHANNEL, 'channelName': 'Cybersource（Network International）', 'channelType': 'PAYMENT',
            'description': 'Cybersource REST 卡直付：下单、预授权、请款、退款、Search 查询（不含 3DS），HMAC HTTP Signature'},
            expected=201)[1]
    channel_id = channel.get('channelId') or channel.get('id')
    detail = call('channel-get', 'GET', f'/channel-admin/v1/channels/{channel_id}', token)[1]
    endpoints = [{'endpointCode': ENDPOINT, 'baseUrl': base_url, 'sandbox': True}]
    if [{k: e.get(k) for k in ('endpointCode', 'baseUrl', 'sandbox')} for e in detail.get('endpoints') or []] != endpoints:
        call('channel-endpoints', 'PUT', f'/channel-admin/v1/channels/{channel_id}/endpoints', token,
             {'expectedVersion': detail['version'], 'endpoints': endpoints})
    return channel_id


def configure(base_url):
    token = login()
    extended = extend_standard_api(token, 'REFUND', [field('refund.paymentChannelRef', required=False)])
    extended += extend_standard_api(token, 'CAPTURE', [field('capture.paymentChannelRef', required=False)])
    print(json.dumps({'standardApiChanges': extended}, ensure_ascii=False))
    channel_id = ensure_channel(token, base_url)
    base = f'{ADMIN}/integrations/{CHANNEL}'
    for code, name, stage, source, params in ((SIGN, 'Cybersource HTTP Signature（HmacSHA256，Digest / Date / Signature）', 'REQUEST',
                                               SIGN_SOURCE, SIGN_PARAMS),
                                              (PICK, 'Search 结果挑选：首条非重复记录与 ics_auth / ics_bill / ics_credit', 'RESPONSE',
                                               PICK_SOURCE, [])):
        body = {'scriptName': name, 'executionStage': stage, 'sourceCode': source, 'description': None, 'parameterDefinition': params}
        upsert(token, 'script-' + code, f'{base}/scripts/{code}', f'{base}/scripts', {'scriptCode': code, **body}, body)
    for code, name, request, response, definition_json in APIS:
        body = {'apiName': name, 'description': None, 'requestFields': request, 'responseFields': response, 'definition': definition_json}
        upsert(token, 'api-' + code, f'{base}/apis/{code}', f'{base}/apis', {'apiCode': code, **body}, body)
    for code, flow_definition in FLOWS:
        found, current = call('implementation-get-' + code, 'GET', f'{base}/implementations/{code}', token, expected=(200, 404))
        check = call('implementation-validate-' + code, 'POST', f'{base}/implementations/validate', token,
                     {'standardApiCode': code, 'flowDefinition': flow_definition,
                      'expectedVersion': current['version'] if found == 200 else None})[1]
        assert check['valid'], (code, check)
        upsert(token, 'implementation-' + code, f'{base}/implementations/{code}', f'{base}/implementations',
               {'standardApiCode': code, 'flowDefinition': flow_definition}, {'flowDefinition': flow_definition})
    return channel_id


if __name__ == '__main__':
    endpoint = sys.argv[1] if len(sys.argv) > 1 else 'https://apitest.cybersource.com'
    created_channel = configure(endpoint)
    (runtime / f'configure-{CHANNEL}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'channel': CHANNEL, 'channelId': created_channel, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update', '-endpoints'))]},
                     ensure_ascii=False))
