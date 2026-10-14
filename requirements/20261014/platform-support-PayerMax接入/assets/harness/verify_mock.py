"""PayerMax 接入阶段 3：对本机合成 PayerMax（接入方 PAYERMAX_MOCK）直连 Platform 内部执行接口，验证下单受理、明确拒绝、
结果未知、响应验签失败，查询四种状态与订单不存在，以及前置 Problem 不发出渠道请求。合成渠道独立验证请求签名、外层字段、
requestTime 时效与金额为 JSON 数字。回执只含场景、执行状态、归一化输出（合成跳转地址除外）与渠道检查结果。"""
import json, random
from common import execute, results, runtime, send, internal

INTEGRATION = 'PAYERMAX_MOCK'
account = json.loads((runtime / 'platform.json').read_text())['accounts']['mock']
journal = runtime / 'payermax-journal.jsonl'
prefix = f'PMX{random.randrange(10**12):012d}'


def traffic():
    return [json.loads(line) for line in journal.read_text().splitlines()] if journal.exists() else []


def pay(outcome, **payment):
    value = {'attemptId': prefix + outcome, 'amount': {'value': '10.00', 'currency': 'USD'}, 'subject': 'Local test order',
             'payerReference': 'payer-ref-001', 'returnUrl': 'https://merchant.local.invalid/return', 'country': 'US', **payment}
    return {'channelAccount': account, 'payment': value}


def query(outcome):
    return {'channelAccount': account, 'payment': {'attemptId': prefix + outcome}}


if __name__ == '__main__':
    before = len(traffic())
    send('pay-unknown-integration-404', f'{internal}/standard-apis/v1/PAY/execute', 'POST', None,
         {'integrationCode': 'PAYERMAX_NOPE', 'input': pay('1')}, 404)
    invalid = pay('1')
    del invalid['payment']['payerReference']
    execute('pay-invalid-input-422', 'PAY', INTEGRATION, invalid, 422)
    wrong_token = pay('1')
    wrong_token['channelAccount'] = {**account, 'channelAccountId': '900000000000000099'}
    execute('pay-account-mismatch-503', 'PAY', INTEGRATION, wrong_token, 503)
    assert len(traffic()) == before, '前置 Problem 不应发出渠道请求'

    outcomes = {}
    outcomes['pay-accepted'] = execute('pay-accepted', 'PAY', INTEGRATION, pay('1'))
    assert outcomes['pay-accepted'] == {'executeStatus': 'SUCCESS', 'outputs': {
        'paymentStatus': 'PENDING', 'nextAction': {'type': 'REDIRECT', 'redirectUrl': f'https://cashier.local.invalid/checkout?token=mock-{prefix}1'},
        'channelTransactionRef': f'T{prefix}1', 'channelStatus': 'PENDING'}}, outcomes['pay-accepted']
    outcomes['pay-rejected'] = execute('pay-rejected', 'PAY', INTEGRATION, pay('2'))
    assert outcomes['pay-rejected'] == {'executeStatus': 'FAIL', 'outputs': {
        'paymentStatus': 'FAILED', 'failureCode': 'PARAMS_INVALID', 'failureMessage': 'subject is invalid'}}, outcomes['pay-rejected']
    for name, outcome in (('pay-system-error', '3'), ('pay-bad-response-signature', '4')):
        outcomes[name] = execute(name, 'PAY', INTEGRATION, pay(outcome))
        assert outcomes[name] == {'executeStatus': 'ERROR', 'outputs': {}}, (name, outcomes[name])

    expected_query = {
        '1': {'paymentStatus': 'SUCCEEDED', 'channelStatus': 'SUCCESS', 'paymentMethodType': 'CARD'},
        '5': {'paymentStatus': 'PENDING', 'channelStatus': 'PENDING'},
        '6': {'paymentStatus': 'FAILED', 'channelStatus': 'FAILED', 'failureCode': 'PAYMENT_FAILED', 'failureMessage': 'Transaction declined.'},
        '7': {'paymentStatus': 'CLOSED', 'channelStatus': 'CLOSED'},
    }
    for outcome, extra in expected_query.items():
        name = f'query-{extra["channelStatus"].lower()}'
        outcomes[name] = execute(name, 'PAY_QUERY', INTEGRATION, query(outcome))
        assert outcomes[name] == {'executeStatus': 'SUCCESS', 'outputs': {
            'channelTransactionRef': f'T{prefix}{outcome}', 'amount': {'value': '10', 'currency': 'USD'}, **extra}}, (name, outcomes[name])
    outcomes['query-not-found'] = execute('query-not-found', 'PAY_QUERY', INTEGRATION, query('9'))
    assert outcomes['query-not-found'] == {'executeStatus': 'FAIL', 'outputs': {
        'paymentStatus': 'NOT_FOUND', 'failureCode': 'ORDER_NOT_EXIST', 'failureMessage': 'The order does not exist.'}}, outcomes['query-not-found']

    seen = traffic()[before:]
    assert len(seen) == 9, seen
    assert all(t['signatureValid'] and t['envelopeValid'] and t['requestTimeValid'] and t['amountValid'] for t in seen), seen

    for value in outcomes.values():
        redirect = value['outputs'].get('nextAction', {})
        if 'redirectUrl' in redirect:
            redirect['redirectUrl'] = '[synthetic]'
    receipt = {'date': '2026-10-14', 'integration': INTEGRATION, 'status': 'PASS', 'checks': len(results),
               'outcomes': outcomes, 'channelChecks': seen, 'results': results}
    (runtime / 'verify-mock.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': 'PASS', 'checks': len(results), 'channelRequests': len(seen)}, ensure_ascii=False))
