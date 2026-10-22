"""Cybersource（NI）沙箱验证：在渠道 I_CYBS_NI 下录入验证账号 SBX_APPROVED / SBX_DECLINED，保存并经渠道沙箱验证运行技术方案第 5 节的用例。

账号材料只从仓库外对接包读取（CYBS_PACKAGE，默认 ~/Desktop/channels/I_CYBS_NI/test-credentials.json 中记录 PMAE01_CYBS_USD），
进程内使用，不打印、不写回执。测试卡为 Cybersource 公布的 Visa 测试卡；SBX_DECLINED 用过期有效期触发 EXPIRED_CARD。
运行详情原文写到 CYBS_RUNTIME（仓库外）；回执只含运行结论、断言与渠道 HTTP 状态 / status / reason，不含凭据、卡号与渠道交易 id。

用法：CYBS_RUNTIME=<运行目录> python3 sandbox_cases.py
先跑写用例，全部通过后等待 READ_DELAY 秒再跑读用例（F1：Cybersource 读接口最终一致）。
"""
import json, os, sys, time
from pathlib import Path

from mortest import ADMIN, call, login, runtime

CHANNEL = 'I_CYBS_NI'
BASE = f'{ADMIN}/integrations/{CHANNEL}'
REASON = 'Cybersource（NI）沙箱验证'
PACKAGE = Path(os.environ.get('CYBS_PACKAGE', Path.home() / 'Desktop/channels/I_CYBS_NI/test-credentials.json'))
RECORD = 'PMAE01_CYBS_USD'
HOST = 'apitest.cybersource.com'
ACCOUNT = '${account.identity}'
NONE = 'PTTESTNONE${random.digits(10)}'
TEST_CARD = {'pan': '4111111111111111', 'cvv': '123', 'cardholderName': 'PT TEST'}
# 有效期决定沙箱结果：12/2031 批准，01/2020 过期被拒（EXPIRED_CARD）。
ACCOUNTS = {'SBX_APPROVED': ('12', '2031', '900000000000000031'), 'SBX_DECLINED': ('01', '2020', '900000000000000032')}


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': ACCOUNT, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def pay(value='10.00', attempt_id='PTTEST${random.digits(12)}'):
    return {'card': {'token': 'VERIFY-TEST-CARD'},
            'payment': {'attemptId': attempt_id, 'amount': {'value': value, 'currency': 'USD'},
                        'subject': 'MoR sandbox verify', 'payerReference': 'sbx-payer-1', 'returnUrl': 'https://example.com/mor/return',
                        'clientIp': '203.0.113.10', 'userAgent': 'MoR-Sandbox-Verify/1.0'}}


def attempt(attempt_id):
    return {'payment': {'attemptId': attempt_id}}


def refund(attempt_id, value, refund_id='PTTESTR${random.digits(12)}'):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id, 'amount': {'value': value, 'currency': 'USD'},
                       'reason': 'Sandbox refund verify'}}


def refund_query(attempt_id, refund_id):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id}}


def capture(attempt_id, value, capture_id='PTTESTC${random.digits(12)}', ref=None):
    return {'capture': {'captureId': capture_id, 'paymentAttemptId': attempt_id, 'amount': {'value': value, 'currency': 'USD'},
                        **({'paymentChannelRef': ref} if ref else {})}}


def with_ref(payload, ref):
    """F1：退款 / 请款带下单渠道引用（Payment 保存的 channelTransactionRef）。"""
    return {key: {**value, 'paymentChannelRef': ref} for key, value in payload.items()}


PAID = '${steps.pay.input.payment.attemptId}'
PAID_REF = '${steps.pay.outputs.channelTransactionRef}'
AUTHORIZED = '${steps.auth.input.payment.attemptId}'
AUTHORIZED_REF = '${steps.auth.outputs.channelTransactionRef}'
BOGUS_REF = '7000000000000000000000'
# 写用例：只做写操作（按 id 立即可用）。读用例：Cybersource 读接口最终一致（F1），harness 在写用例结束后等待 READ_DELAY 秒，
# 把写用例实际使用的单号与渠道引用写成字面值再运行。
READ_DELAY = 10
WRITE_CASES = [
    ('SBX_PAY_REFUND', 'SBX_APPROVED', '卡支付 USD 10.00 → 按渠道引用部分退款 4.00', [
        step('pay', 'PAY', pay(), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'), ('channelStatus', 'EQUALS', 'AUTHORIZED'),
             ('channelTransactionRef', 'EXISTS', None)),
        step('refund', 'REFUND', with_ref(refund(PAID, '4.00'), PAID_REF), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'),
             ('channelStatus', 'EQUALS', 'PENDING'), ('channelRefundRef', 'EXISTS', None))]),
    ('SBX_PAY_DECLINED', 'SBX_DECLINED', '过期卡（01/2020）被拒', [
        step('pay', 'PAY', pay(), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'EXPIRED_CARD'),
             ('failureMessage', 'EXISTS', None), ('channelTransactionRef', 'EXISTS', None))]),
    ('SBX_AUTH_CAPTURE', 'SBX_APPROVED', '预授权 10.00 → 请款 6.00 → 第二次请款被拒 → 按授权引用退款 2.00', [
        step('auth', 'AUTHORIZE', pay(), 'SUCCESS', ('authorizationStatus', 'EQUALS', 'SUCCEEDED'),
             ('channelStatus', 'EQUALS', 'AUTHORIZED'), ('channelTransactionRef', 'EXISTS', None)),
        step('capture1', 'CAPTURE', capture(AUTHORIZED, '6.00', ref=AUTHORIZED_REF), 'SUCCESS', ('captureStatus', 'EQUALS', 'SUCCEEDED'),
             ('channelCaptureRef', 'EXISTS', None)),
        step('capture2', 'CAPTURE', capture(AUTHORIZED, '4.00', ref=AUTHORIZED_REF), 'FAIL', ('captureStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_DATA')),
        step('refund', 'REFUND', with_ref(refund(AUTHORIZED, '2.00'), AUTHORIZED_REF), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'))]),
    ('SBX_NOT_FOUND', 'SBX_APPROVED', '不存在的单：查询、退款查询 NOT_FOUND；按不存在的渠道引用退款、请款被拒', [
        step('query', 'PAY_QUERY', attempt(NONE), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'NOT_FOUND')),
        step('refundQuery', 'REFUND_QUERY', refund_query(NONE, 'PTTESTRNONE${random.digits(10)}'), 'FAIL',
             ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'NOT_FOUND')),
        step('refund', 'REFUND', with_ref(refund(NONE, '1.00'), BOGUS_REF), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_DATA')),
        step('capture', 'CAPTURE', capture(NONE, '1.00', ref=BOGUS_REF), 'FAIL', ('captureStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_DATA'))]),
]


def read_cases(written):
    """读用例：单号与渠道引用取自写用例运行的 resolvedInput / 输出。"""
    def used(case, step_key):
        item = next(s for s in written[case]['steps'] if s['stepKey'] == step_key)
        return item['resolvedInput'], (item.get('result') or {}).get('outputs') or {}
    pay_in, pay_out = used('SBX_PAY_REFUND', 'pay')
    refund_in, _ = used('SBX_PAY_REFUND', 'refund')
    declined_in, _ = used('SBX_PAY_DECLINED', 'pay')
    auth_in, auth_out = used('SBX_AUTH_CAPTURE', 'auth')
    capture_in, _ = used('SBX_AUTH_CAPTURE', 'capture1')
    paid, refund_id = pay_in['payment']['attemptId'], refund_in['refund']['refundId']
    authorized = auth_in['payment']['attemptId']
    return [
        ('SBX_PAY_REFUND_READ', 'SBX_APPROVED', '读：支付查询 → 退款查询 → 重发退款（回查）→ 用支付单号查退款 → 重发支付（回查）', [
            step('query', 'PAY_QUERY', attempt(paid), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'),
                 ('amount.value', 'EQUALS', '10.00'), ('amount.currency', 'EQUALS', 'USD'), ('channelTransactionRef', 'EXISTS', None)),
            step('refundQuery', 'REFUND_QUERY', refund_query(paid, refund_id), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'),
                 ('amount.value', 'EQUALS', '4.00'), ('amount.currency', 'EQUALS', 'USD')),
            step('refundReplay', 'REFUND', with_ref(refund(paid, '4.00', refund_id), pay_out['channelTransactionRef']), 'SUCCESS',
                 ('refundStatus', 'EQUALS', 'SUCCEEDED'), ('channelRefundRef', 'EXISTS', None)),
            step('refundQueryPayment', 'REFUND_QUERY', refund_query(paid, paid), 'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'),
                 ('failureCode', 'EQUALS', 'NOT_A_REFUND')),
            step('payAgain', 'PAY', pay(attempt_id=paid), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'),
                 ('channelTransactionRef', 'EXISTS', None))]),
        ('SBX_PAY_DECLINED_READ', 'SBX_APPROVED', '读：被拒单查询', [
            step('query', 'PAY_QUERY', attempt(declined_in['payment']['attemptId']), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'),
                 ('failureCode', 'EXISTS', None))]),
        ('SBX_AUTH_CAPTURE_READ', 'SBX_APPROVED', '读：预授权单查询（非支付）→ 重发第一笔请款（回查）', [
            step('query', 'PAY_QUERY', attempt(authorized), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'),
                 ('failureCode', 'EQUALS', 'NOT_A_PAYMENT')),
            step('captureReplay', 'CAPTURE', capture(authorized, '6.00', capture_in['capture']['captureId'], auth_out['channelTransactionRef']),
                 'SUCCESS', ('captureStatus', 'EQUALS', 'SUCCEEDED'), ('channelCaptureRef', 'EXISTS', None))]),
    ]


def material():
    return next(r for r in json.loads(PACKAGE.read_text())['records'] if r['channelMerchantCode'] == RECORD)['account']


def account_body(month, year, channel_account_id):
    m = material()
    return {'accountName': f'Cybersource 沙箱 {RECORD}（测试卡 {month}/{year}）', 'channelCode': CHANNEL,
            'channelAccountId': channel_account_id, 'entityNo': 'UAT01',
            'parameters': {'merchantId': m['merchantId'], 'keyId': m['merchantKeyId'], 'host': HOST},
            'credentials': {'secretKey': m['merchantSecretKey'], **{'testCard.' + k: v for k, v in TEST_CARD.items()},
                            'testCard.expiryMonth': month, 'testCard.expiryYear': year},
            'reason': REASON}


def upsert(token, kind, code, body):
    status, existing = call(f'{kind}-get', 'GET', f'{BASE}/{kind}/{code}', token, expected=(200, 404))
    if status == 404:
        call(f'{kind}-create', 'POST', f'{BASE}/{kind}', token, {f"{'account' if kind == 'verify-accounts' else 'case'}Code": code, **body})
    else:
        call(f'{kind}-update', 'PUT', f'{BASE}/{kind}/{code}', token, {**body, 'expectedVersion': existing['version']})


def run(token, case_code):
    started = call('run-start', 'POST', f'{BASE}/verify-cases/{case_code}/runs', token, {'reason': REASON})[1]
    for _ in range(90):
        detail = call('run-get', 'GET', f"{BASE}/verify-runs/{started['runCode']}", token)[1]
        if detail['runStatus'] != 'RUNNING':
            return detail
        time.sleep(2)
    raise AssertionError('运行超时')


OMITTED = ('channelTransactionRef', 'channelRefundRef', 'channelCaptureRef')


def scrub(value):
    """回执不写渠道交易 id。"""
    if isinstance(value, dict):
        return {k: ('[omitted]' if k in OMITTED else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


def channel_brief(parsed):
    if not isinstance(parsed, dict):
        return None
    summaries = (parsed.get('_embedded') or {}).get('transactionSummaries')
    if summaries is not None:
        return {'totalCount': parsed.get('totalCount'),
                'records': [((s.get('applicationInformation') or {}).get('rFlag'),
                             [(a.get('name'), a.get('rCode')) for a in (s.get('applicationInformation') or {}).get('applications') or []])
                            for s in summaries]}
    return {'status': parsed.get('status'), 'reason': parsed.get('reason') or (parsed.get('errorInformation') or {}).get('reason')}


def evidence(detail):
    result = {'runCode': detail['runCode'], 'runStatus': detail['runStatus'], 'failureSummary': detail.get('failureSummary'),
              'sandboxEndpoints': [e['sandbox'] for e in detail['endpoints']],
              'steps': [{'stepKey': s['stepKey'], 'stepStatus': s['stepStatus'], 'result': scrub(s.get('result')),
                         'assertions': [[a['target'], a['passed']] for a in s.get('assertions') or []]} for s in detail['steps']],
              'exchanges': []}
    for item in detail['steps']:
        for node in (item.get('trace') or {}).get('nodes', []):
            response = (node.get('exchange') or {}).get('response') or {}
            try:
                channel = channel_brief(json.loads((response.get('body') or {}).get('text') or ''))
            except ValueError:
                channel = None
            result['exchanges'].append({'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                        'reason': node.get('reason'), 'http': response.get('status'),
                                        'error': (node.get('exchange') or {}).get('error'), 'channel': channel})
    return result


def run_cases(token, cases, raw, out):
    for code, account, name, steps in cases:
        upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': account, 'steps': steps, 'reason': REASON})
        detail = run(token, code)
        raw[code] = detail
        out[code] = evidence(detail)


if __name__ == '__main__':
    token = login()
    for code, (month, year, channel_account_id) in ACCOUNTS.items():
        upsert(token, 'verify-accounts', code, account_body(month, year, channel_account_id))
    out, raw = {}, {}
    run_cases(token, WRITE_CASES, raw, out)
    if all(raw[code]['runStatus'] == 'PASSED' for code, *_ in WRITE_CASES):
        time.sleep(READ_DELAY)
        run_cases(token, read_cases(raw), raw, out)
    (runtime / 'cybs-sandbox-runs-raw.json').write_text(json.dumps(raw, ensure_ascii=False, indent=2) + '\n')
    (runtime / 'cybs-sandbox-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'],
                             'steps': [[s['stepKey'], s['stepStatus'], [a for a in s['assertions'] if not a[1]]] for s in r['steps']],
                             'exchanges': [[e['step'], e['node'], e['http'], e['channel'], e['reason']] for e in r['exchanges']]}
                      for code, r in out.items()}, ensure_ascii=False, indent=1))
