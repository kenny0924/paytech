"""PayerMax 卡直付阶段 2：把一个 PayerMax 接入方的 PAY 从收银台下单改为卡直付（技术方案第 4 节），经真实管理接口保存。

顺序：已有 PAY 实现先按版本删除（新流程引用的 input.card.token 在旧标准 API 中不存在，旧流程又会被新标准 API 判为失效）→
修改标准 API PAY → 修改原子 API ORDER_AND_PAY（integrate=Direct_Payment、五个卡字段）→ 校验并创建新流程。
PAY_QUERY、出站地址、签名/验签脚本与账号不变。

前提：接入方已由 PayerMax 接入 harness 的 configure.py 保存。
用法：python3 configure_card.py <接入方编码>...（同一库中实现 PAY 的全部接入方，例如 PAYERMAX_MOCK PAYERMAX）
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
import configure  # noqa: E402
from common import ADMIN, call, login, results, runtime  # noqa: E402
from configure import (CHANNEL_ACCOUNT, ENVELOPE, ENVELOPE_VALUES, FAIL_OUTPUTS, INITIALIZER, NODE_PARAMS, OK, ORDER_AND_PAY,  # noqa: E402
                       REJECT_CODES, const, definition, expr, field, output, path, status, upsert)

REASON = 'PayerMax 卡直付（requirements/20261014/payment-PayerMax卡直付对接）'
configure.REASON = REASON

PAY_REQUEST = CHANNEL_ACCOUNT + [
    field('card', 'OBJECT'), field('card.token'),
    field('payment', 'OBJECT'), field('payment.attemptId'), field('payment.amount', 'OBJECT'), field('payment.amount.value', 'DECIMAL'),
    field('payment.amount.currency'), field('payment.country'), field('payment.returnUrl'),
    field('payment.payerReference'), field('payment.subject', required=False),
    field('payment.notifyUrl', required=False), field('payment.clientIp', required=False),
    field('payment.userAgent', required=False)]
PAY_RESPONSE = [field('paymentStatus'), field('nextAction', 'OBJECT', False), field('nextAction.type', required=False),
                field('nextAction.redirectUrl', required=False), field('channelTransactionRef', required=False),
                field('channelStatus', required=False), field('failureCode', required=False), field('failureMessage', required=False)]

CARD_INFO = 'data.paymentDetail.cardInfo.'
CARD_FIELDS = {'cardIdentifierNo': 'pan', 'cardExpirationMonth': 'expiryMonth', 'cardExpirationYear': 'expiryYearShort',
               'cvv': 'cvv', 'cardHolderFullName': 'cardholderName'}
ORDER_REQUEST = ENVELOPE + [
    field('data.outTradeNo'), field('data.integrate'), field('data.subject'), field('data.totalAmount', 'DECIMAL'), field('data.currency'),
    field('data.country'), field('data.terminalType'), field('data.frontCallbackUrl'), field('data.notifyUrl', required=False),
    field('data.userId'), field('data.paymentDetail', 'OBJECT', False), field('data.paymentDetail.paymentMethodType'),
    field('data.paymentDetail.cardInfo', 'OBJECT', False), field('data.paymentDetail.buyerInfo', 'OBJECT', False),
    field('data.paymentDetail.buyerInfo.clientIp', required=False), field('data.paymentDetail.buyerInfo.userAgent', required=False)]\
    + [field(CARD_INFO + name) for name in CARD_FIELDS]
ORDER_RESPONSE = [field('code'), field('msg', required=False), field('data', 'OBJECT', False), field('data.outTradeNo', required=False),
                  field('data.tradeToken', required=False), field('data.status', required=False),
                  field('data.redirectUrl', required=False)]

STATUS = 'http.responseBody.data.status'
ORDER_DEFINITION = definition('/orderAndPay', [
    # 已创建交易（有 tradeToken）时以 data.status 为准：被拒的外层 code 不是 APPLY_SUCCESS（UAT 实测 ISSUER_PAYMENT_REJECTED）。
    {'condition': f"http.responseStatus == 200 && http.responseBody.data != nil && http.responseBody.data.tradeToken != nil"
                  f" && ({STATUS} == 'FAILED'"
                  f" || (http.responseBody.code == 'APPLY_SUCCESS' && ({STATUS} == 'SUCCESS' || {STATUS} == 'PENDING')))",
     'executeStatus': 'SUCCESS'},
    {'condition': 'http.responseStatus == 200 && (' + ' || '.join(f"http.responseBody.code == '{code}'" for code in REJECT_CODES) + ')',
     'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('tradeToken', path('http.responseBody.data.tradeToken')), output('channelStatus', path(STATUS)),
                 output('redirectUrl', path('http.responseBody.data.redirectUrl'), required=False),
                 output('resultCode', path('http.responseBody.code')),
                 output('resultMessage', path('http.responseBody.msg'), required=False)],
     'FAIL': FAIL_OUTPUTS},
    {'BODY:data.integrate': const('Direct_Payment'), 'BODY:data.terminalType': const('WEB'),
     'BODY:data.paymentDetail.paymentMethodType': const('CARD')})
ORDER_DEFINITION['cardFields'] = ['BODY:' + CARD_INFO + name for name in CARD_FIELDS]

CARD_INITIALIZER = {'id': 'card', 'function': 'PAYMENT_CARD', 'required': True, 'args': {
    'paymentInstrumentToken': path('input.card.token'), 'paymentAttemptId': path('input.payment.attemptId')}}
O = 'steps.createOrder.outputs.'


def succeeded(channel_status, challenge=None):
    condition = f"{status('createOrder', 'SUCCESS')} && {O}channelStatus == '{channel_status}'"
    if challenge is not None:
        condition += f" && {O}redirectUrl {'!=' if challenge else '=='} nil"
    return condition


PAY_FLOW = {
    'timeoutMs': 25000, 'initializers': INITIALIZER + [CARD_INITIALIZER], 'entry': 'createOrder',
    'nodes': [{'id': 'createOrder', 'integrationApi': ORDER_AND_PAY, 'params': NODE_PARAMS, 'request': {
        **ENVELOPE_VALUES,
        'BODY:data.outTradeNo': path('input.payment.attemptId'),
        'BODY:data.subject': expr("input.payment.subject == nil ? 'Order ' + input.payment.attemptId : input.payment.subject"),
        'BODY:data.totalAmount': path('input.payment.amount.value'), 'BODY:data.currency': path('input.payment.amount.currency'),
        'BODY:data.country': path('input.payment.country'), 'BODY:data.frontCallbackUrl': path('input.payment.returnUrl'),
        'BODY:data.notifyUrl': path('input.payment.notifyUrl'), 'BODY:data.userId': path('input.payment.payerReference'),
        'BODY:data.paymentDetail.buyerInfo.clientIp': path('input.payment.clientIp'),
        'BODY:data.paymentDetail.buyerInfo.userAgent': path('input.payment.userAgent'),
        **{'BODY:' + CARD_INFO + name: path('resources.card.' + value) for name, value in CARD_FIELDS.items()}}}],
    'returns': [
        {'id': 'succeeded', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': const('SUCCEEDED'), 'channelTransactionRef': path(O + 'tradeToken'), 'channelStatus': path(O + 'channelStatus')}},
        {'id': 'challenge', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': const('PENDING'), 'nextAction.type': const('THREE_DS_CHALLENGE'),
            'nextAction.redirectUrl': path(O + 'redirectUrl'), 'channelTransactionRef': path(O + 'tradeToken'),
            'channelStatus': path(O + 'channelStatus')}},
        {'id': 'pending', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': const('PENDING'), 'channelTransactionRef': path(O + 'tradeToken'), 'channelStatus': path(O + 'channelStatus')}},
        {'id': 'declined', 'executeStatus': 'SUCCESS', 'outputs': {
            'paymentStatus': const('FAILED'), 'channelTransactionRef': path(O + 'tradeToken'), 'channelStatus': path(O + 'channelStatus'),
            'failureCode': path(O + 'resultCode'), 'failureMessage': path(O + 'resultMessage')}},
        {'id': 'rejected', 'executeStatus': 'FAIL', 'outputs': {
            'paymentStatus': const('FAILED'), 'failureCode': path(O + 'errorCode'), 'failureMessage': path(O + 'errorMessage')}},
        {'id': 'unknown', 'executeStatus': 'ERROR'}],
    'edges': [{'from': 'createOrder', 'to': 'succeeded', 'condition': succeeded('SUCCESS'), 'priority': 1},
              {'from': 'createOrder', 'to': 'challenge', 'condition': succeeded('PENDING', True), 'priority': 2},
              {'from': 'createOrder', 'to': 'pending', 'condition': succeeded('PENDING', False), 'priority': 3},
              {'from': 'createOrder', 'to': 'declined', 'condition': succeeded('FAILED'), 'priority': 4},
              {'from': 'createOrder', 'to': 'rejected', 'condition': status('createOrder', 'FAIL'), 'priority': 5},
              {'from': 'createOrder', 'to': 'unknown', 'default': True}],
}


def drop_hosted_flow(token, integration):
    """收银台版 PAY 流程按版本删除：它与新标准 API 不兼容，而标准 API 修改会先复核全部接入方的流程。"""
    base = f'{ADMIN}/integrations/{integration}'
    found, current = call('implementation-get-PAY', 'GET', f'{base}/implementations/PAY', token, expected=(200, 404))
    initializers = current['flowDefinition'].get('initializers', []) if found == 200 else []
    if found == 200 and not any(item.get('function') == 'PAYMENT_CARD' for item in initializers):
        call('implementation-delete-PAY', 'DELETE', f'{base}/implementations/PAY', token,
             {'expectedVersion': current['version'], 'reason': REASON + '：替换收银台流程'})


def configure_card(token, integration):
    base = f'{ADMIN}/integrations/{integration}'
    body = {'apiName': '卡直付下单 orderAndPay', 'description': None, 'requestFields': ORDER_REQUEST, 'responseFields': ORDER_RESPONSE,
            'definition': ORDER_DEFINITION}
    upsert(token, 'api-' + ORDER_AND_PAY, f'{base}/apis/{ORDER_AND_PAY}', f'{base}/apis', {'apiCode': ORDER_AND_PAY, **body}, body)
    found, current = call('implementation-get-PAY', 'GET', f'{base}/implementations/PAY', token, expected=(200, 404))
    check = call('implementation-validate-PAY', 'POST', f'{base}/implementations/validate', token,
                 {'standardApiCode': 'PAY', 'flowDefinition': PAY_FLOW, 'expectedVersion': current['version'] if found == 200 else None})[1]
    assert check['valid'], check
    upsert(token, 'implementation-PAY', f'{base}/implementations/PAY', f'{base}/implementations',
           {'standardApiCode': 'PAY', 'flowDefinition': PAY_FLOW}, {'flowDefinition': PAY_FLOW})


def configure_all(integrations):
    token = login()
    for integration in integrations:
        drop_hosted_flow(token, integration)
    body = {'standardApiName': '支付', 'description': '渠道无关标准 API（草案）：卡直付，卡以支付工具 Token 传递', 'requestFields': PAY_REQUEST,
            'responseFields': PAY_RESPONSE}
    upsert(token, 'standard-api-PAY', f'{ADMIN}/standard-apis/PAY', f'{ADMIN}/standard-apis', {'standardApiCode': 'PAY', **body}, body)
    for integration in integrations:
        configure_card(token, integration)


if __name__ == '__main__':
    codes = sys.argv[1:]
    configure_all(codes)
    (runtime / f'configure-card-{"-".join(codes)}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integrations': codes, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update', '-delete'))]}, ensure_ascii=False))
