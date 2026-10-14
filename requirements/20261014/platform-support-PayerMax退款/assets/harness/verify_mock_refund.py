"""PayerMax 退款阶段 2：对本机合成 PayerMax（接入方 PAYERMAX_MOCK）直连 Platform 内部执行接口，验证退款受理（PENDING / 同步成功 /
同步失败）、明确拒绝（REFUND_FAILED、ORDER_NOT_EXIST）、SYSTEM_ERROR 与响应验签失败 → ERROR，退款查询三种状态与不存在，
以及前置 Problem（输入缺字段 422、账号身份不一致 503）不发出渠道请求。合成渠道独立验证请求签名、外层字段（version 1.1）、
requestTime 时效、refundAmount 为 JSON 数字与必填字段。回执只含场景、执行状态、归一化输出与渠道检查结果。"""
import json, random, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
from common import execute, results, runtime  # noqa: E402

INTEGRATION = 'PAYERMAX_MOCK'
account = json.loads((runtime / 'platform.json').read_text())['accounts']['mock']
journal = runtime / 'payermax-journal.jsonl'
prefix = f'RFD{random.randrange(10**12):012d}'


def traffic():
    return [json.loads(line) for line in journal.read_text().splitlines()] if journal.exists() else []


def refund(outcome, **extra):
    value = {'refundId': prefix + outcome, 'paymentAttemptId': 'PMX' + prefix, 'amount': {'value': '5.00', 'currency': 'USD'},
             'reason': 'Local refund test', **extra}
    return {'channelAccount': account, 'refund': value}


def query(outcome):
    return {'channelAccount': account, 'refund': {'refundId': prefix + outcome}}


def check(name, standard_api, payload, expected):
    actual = execute(name, standard_api, INTEGRATION, payload)
    assert actual == expected, (name, actual)
    return actual


if __name__ == '__main__':
    before = len(traffic())
    invalid = refund('1')
    del invalid['refund']['paymentAttemptId']
    execute('refund-invalid-input-422', 'REFUND', INTEGRATION, invalid, 422)
    wrong_account = refund('1')
    wrong_account['channelAccount'] = {**account, 'channelAccountId': '900000000000000099'}
    execute('refund-account-mismatch-503', 'REFUND', INTEGRATION, wrong_account, 503)
    assert len(traffic()) == before, '前置 Problem 不应发出渠道请求'

    outcomes = {}
    for outcome, refund_status, channel_status in (('1', 'PENDING', 'REFUND_PENDING'), ('2', 'SUCCEEDED', 'REFUND_SUCCESS'),
                                                   ('3', 'FAILED', 'REFUND_FAILED')):
        name = f'refund-accepted-{refund_status.lower()}'
        outcomes[name] = check(name, 'REFUND', refund(outcome), {'executeStatus': 'SUCCESS', 'outputs': {
            'refundStatus': refund_status, 'channelRefundRef': f'R{prefix}{outcome}', 'channelStatus': channel_status}})
    outcomes['refund-rejected-not-supported'] = check('refund-rejected-not-supported', 'REFUND', refund('4'), {
        'executeStatus': 'FAIL', 'outputs': {'refundStatus': 'FAILED', 'failureCode': 'REFUND_FAILED',
                                             'failureMessage': 'The payment method does not support partial refund.'}})
    outcomes['refund-rejected-order-not-exist'] = check('refund-rejected-order-not-exist', 'REFUND', refund('5'), {
        'executeStatus': 'FAIL', 'outputs': {'refundStatus': 'FAILED', 'failureCode': 'ORDER_NOT_EXIST',
                                             'failureMessage': 'The orginal order does not exist.'}})
    for name, outcome in (('refund-system-error', '6'), ('refund-bad-response-signature', '7')):
        outcomes[name] = check(name, 'REFUND', refund(outcome), {'executeStatus': 'ERROR', 'outputs': {}})
    # 可选字段省略时仍受理（comments 不出现在请求中，合成渠道只检查必填字段）
    minimal = refund('1')
    del minimal['refund']['reason']
    outcomes['refund-without-reason'] = check('refund-without-reason', 'REFUND', minimal, outcomes['refund-accepted-pending'])

    for outcome, refund_status, channel_status, extra in (
            ('1', 'PENDING', 'REFUND_PENDING', {}), ('2', 'SUCCEEDED', 'REFUND_SUCCESS', {}),
            ('3', 'FAILED', 'REFUND_FAILED', {'failureMessage': 'Refund rejected by issuer.'})):
        name = f'refund-query-{refund_status.lower()}'
        outcomes[name] = check(name, 'REFUND_QUERY', query(outcome), {'executeStatus': 'SUCCESS', 'outputs': {
            'refundStatus': refund_status, 'channelRefundRef': f'R{prefix}{outcome}', 'channelStatus': channel_status,
            'amount': {'value': '5', 'currency': 'USD'}, **extra}})
    outcomes['refund-query-not-found'] = check('refund-query-not-found', 'REFUND_QUERY', query('9'), {
        'executeStatus': 'FAIL', 'outputs': {'refundStatus': 'NOT_FOUND', 'failureCode': 'ORDER_NOT_EXIST',
                                             'failureMessage': 'The order does not exist.'}})

    seen = traffic()[before:]
    assert len(seen) == 12, seen
    assert all(t['signatureValid'] and t['envelopeValid'] and t['requestTimeValid'] and t['amountValid'] for t in seen), seen
    assert {t['operation'] for t in seen} == {'refund', 'refundQuery'}, seen

    receipt = {'date': '2026-10-14', 'integration': INTEGRATION, 'status': 'PASS', 'checks': len(results),
               'outcomes': outcomes, 'channelChecks': seen, 'results': results}
    (runtime / 'verify-mock-refund.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': 'PASS', 'checks': len(results), 'channelRequests': len(seen)}, ensure_ascii=False))
