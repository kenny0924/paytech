"""MPGS（NI）沙箱验证：在接入方 I_MPGS_NI 下录入验证账号 SBX_APPROVED / SBX_DECLINED，保存并经渠道沙箱验证运行技术方案第 5 节的用例。

账号材料只从仓库外对接包读取（MPGS_PACKAGE，默认 ~/Desktop/channels/I_MPGS_NI/test-credentials.json 中记录 MPGS_TEST200900000049，
商户 JOLLYMAX_AED），进程内使用，不打印、不写回执。测试卡为 MPGS 公布的测试卡（有效期决定沙箱结果）。
运行详情原文写到 MPGS_RUNTIME（仓库外）；回执只含运行结论、断言与渠道 HTTP 状态 / result / gatewayCode，不含凭据、卡号与渠道交易引用。

用法：MPGS_RUNTIME=<运行目录> python3 sandbox_cases.py [CASE ...]
"""
import json, os, sys, time
from pathlib import Path

from mortest import ADMIN, call, login, runtime

INTEGRATION = 'I_MPGS_NI'
BASE = f'{ADMIN}/integrations/{INTEGRATION}'
REASON = 'MPGS（NI）沙箱验证'
PACKAGE = Path(os.environ.get('MPGS_PACKAGE', Path.home() / 'Desktop/channels/I_MPGS_NI/test-credentials.json'))
RECORD = 'MPGS_TEST200900000049'
CHANNEL = '${account.identity}'
NONE = 'PTTESTNONE${random.digits(10)}'
# MPGS 公布的测试卡：有效期 01/39 批准，05/39 被拒（DECLINED）。
TEST_CARD = {'pan': '5123450000000008', 'cvv': '100', 'cardholderName': 'PT TEST', 'expiryYear': '2039'}
ACCOUNTS = {'SBX_APPROVED': ('01', '900000000000000021'), 'SBX_DECLINED': ('05', '900000000000000022')}


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': CHANNEL, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def pay(value='10.00'):
    return {'card': {'token': 'VERIFY-TEST-CARD'},
            'payment': {'attemptId': 'PTTEST${random.digits(12)}', 'amount': {'value': value, 'currency': 'USD'},
                        'subject': 'MoR sandbox verify', 'payerReference': 'sbx-payer-1', 'returnUrl': 'https://example.com/mor/return',
                        'clientIp': '203.0.113.10', 'userAgent': 'MoR-Sandbox-Verify/1.0'}}


def attempt(attempt_id):
    return {'payment': {'attemptId': attempt_id}}


def refund(attempt_id, value, refund_id='PTTESTR${random.digits(12)}'):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id, 'amount': {'value': value, 'currency': 'USD'},
                       'reason': 'Sandbox refund verify'}}


def refund_query(attempt_id, refund_id):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id}}


def capture(attempt_id, value, capture_id='PTTESTC${random.digits(12)}'):
    return {'capture': {'captureId': capture_id, 'paymentAttemptId': attempt_id, 'amount': {'value': value, 'currency': 'USD'}}}


PAID = '${steps.pay.input.payment.attemptId}'
AUTHORIZED = '${steps.auth.input.payment.attemptId}'
REFUND_ID = '${steps.refund.input.refund.refundId}'
CASES = [
    ('SBX_PAY_REFUND_CHAIN', 'SBX_APPROVED', '卡支付 USD 10.00 → 查询 → 部分退款 4.00 → 退款查询 → 用支付交易号查退款 → 退款后支付查询', [
        step('pay', 'PAY', pay(), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'), ('channelStatus', 'EQUALS', 'APPROVED'),
             ('channelTransactionRef', 'EXISTS', None)),
        step('query', 'PAY_QUERY', attempt(PAID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'),
             ('amount.value', 'EQUALS', '10.00'), ('amount.currency', 'EQUALS', 'USD'), ('channelTransactionRef', 'EXISTS', None)),
        step('refund', 'REFUND', refund(PAID, '4.00'), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'),
             ('channelStatus', 'EQUALS', 'APPROVED'), ('channelRefundRef', 'EXISTS', None)),
        step('refundQuery', 'REFUND_QUERY', refund_query(PAID, REFUND_ID), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'),
             ('amount.value', 'EQUALS', '4.00'), ('amount.currency', 'EQUALS', 'USD'), ('channelRefundRef', 'EXISTS', None)),
        step('refundQueryPayment', 'REFUND_QUERY', refund_query(PAID, PAID), 'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'),
             ('failureCode', 'EQUALS', 'NOT_A_REFUND')),
        step('queryAfterRefund', 'PAY_QUERY', attempt(PAID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'))]),
    ('SBX_PAY_DECLINED', 'SBX_DECLINED', '卡被拒（有效期 05/39）→ 查询失败', [
        step('pay', 'PAY', pay(), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'DECLINED'),
             ('failureMessage', 'EXISTS', None), ('channelTransactionRef', 'EXISTS', None)),
        step('query', 'PAY_QUERY', attempt(PAID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'DECLINED'))]),
    ('SBX_PAY_QUERY_NOT_FOUND', 'SBX_APPROVED', '查询不存在的订单', [
        step('query', 'PAY_QUERY', attempt(NONE), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'INVALID_REQUEST'))]),
    ('SBX_REFUND_NOT_EXIST', 'SBX_APPROVED', '对不存在的原单退款', [
        step('refund', 'REFUND', refund(NONE, '1.00'), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'INVALID_REQUEST'))]),
    ('SBX_REFUND_QUERY_NOT_FOUND', 'SBX_APPROVED', '查询不存在的退款', [
        step('query', 'REFUND_QUERY', refund_query(NONE, 'PTTESTRNONE${random.digits(10)}'), 'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'),
             ('failureCode', 'EQUALS', 'INVALID_REQUEST'))]),
    # ---- 追加：预授权与请款（技术方案 7.4）----
    ('SBX_AUTH_CAPTURE_CHAIN', 'SBX_APPROVED', '预授权 10.00 → 请款 6.00 → 超额请款 5.00 → 请款 4.00 → 重发第一笔请款 → 退款 2.00', [
        step('auth', 'AUTHORIZE', pay(), 'SUCCESS', ('authorizationStatus', 'EQUALS', 'SUCCEEDED'), ('channelStatus', 'EQUALS', 'APPROVED'),
             ('channelTransactionRef', 'EXISTS', None)),
        step('capture1', 'CAPTURE', capture(AUTHORIZED, '6.00'), 'SUCCESS', ('captureStatus', 'EQUALS', 'SUCCEEDED'),
             ('channelStatus', 'EQUALS', 'APPROVED'), ('channelCaptureRef', 'EXISTS', None)),
        step('captureOver', 'CAPTURE', capture(AUTHORIZED, '5.00'), 'FAIL', ('captureStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_REQUEST')),
        step('captureRest', 'CAPTURE', capture(AUTHORIZED, '4.00'), 'SUCCESS', ('captureStatus', 'EQUALS', 'SUCCEEDED')),
        step('captureReplay', 'CAPTURE', capture(AUTHORIZED, '6.00', '${steps.capture1.input.capture.captureId}'), 'SUCCESS',
             ('captureStatus', 'EQUALS', 'SUCCEEDED'), ('channelCaptureRef', 'EXISTS', None)),
        step('refund', 'REFUND', refund(AUTHORIZED, '2.00'), 'SUCCESS', ('refundStatus', 'EQUALS', 'SUCCEEDED'))]),
    ('SBX_AUTH_DECLINED', 'SBX_DECLINED', '预授权被拒（有效期 05/39）→ 请款被拒', [
        step('auth', 'AUTHORIZE', pay(), 'SUCCESS', ('authorizationStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'DECLINED'),
             ('failureMessage', 'EXISTS', None)),
        step('capture', 'CAPTURE', capture(AUTHORIZED, '10.00'), 'FAIL', ('captureStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_REQUEST'))]),
    ('SBX_CAPTURE_NOT_EXIST', 'SBX_APPROVED', '对不存在的订单请款', [
        step('capture', 'CAPTURE', capture(NONE, '1.00'), 'FAIL', ('captureStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_REQUEST'))]),
]


def material():
    return next(r for r in json.loads(PACKAGE.read_text())['records'] if r['channelMerchantCode'] == RECORD)['account']


def account_body(month, channel_account_id):
    m = material()
    return {'accountName': f'MPGS 沙箱 JOLLYMAX_AED（测试卡 {month}/39）', 'channelCode': INTEGRATION, 'channelAccountId': channel_account_id,
            'entityNo': 'UAT01', 'parameters': {'merchantId': m['merchantId']},
            'credentials': {'apiUser': 'merchant.' + m['merchantId'], 'apiPassword': m['apiPassword'],
                            **{'testCard.' + k: v for k, v in TEST_CARD.items()}, 'testCard.expiryMonth': month},
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
    """回执不写渠道交易引用（RRN）。"""
    if isinstance(value, dict):
        return {k: ('[omitted]' if k in OMITTED else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


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
                parsed = json.loads((response.get('body') or {}).get('text') or '')
                channel = {'result': parsed.get('result'), 'gatewayCode': (parsed.get('response') or {}).get('gatewayCode'),
                           'transactionType': (parsed.get('transaction') or {}).get('type'),
                           'errorCause': (parsed.get('error') or {}).get('cause')}
            except (ValueError, AttributeError):
                channel = None
            result['exchanges'].append({'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                        'reason': node.get('reason'), 'http': response.get('status'),
                                        'error': (node.get('exchange') or {}).get('error'), 'channel': channel})
    return result


if __name__ == '__main__':
    token = login()
    for code, (month, channel_account_id) in ACCOUNTS.items():
        upsert(token, 'verify-accounts', code, account_body(month, channel_account_id))
    out, raw = {}, {}
    for code, account, name, steps in CASES:
        if len(sys.argv) > 1 and code not in sys.argv[1:]:
            continue
        upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': account, 'steps': steps, 'reason': REASON})
        detail = run(token, code)
        raw[code] = detail
        out[code] = evidence(detail)
    (runtime / 'mpgs-sandbox-runs-raw.json').write_text(json.dumps(raw, ensure_ascii=False, indent=2) + '\n')
    (runtime / 'mpgs-sandbox-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'],
                             'steps': [[s['stepKey'], s['stepStatus'], [a for a in s['assertions'] if not a[1]]] for s in r['steps']],
                             'exchanges': [[e['step'], e['http'], e['channel'], e['reason']] for e in r['exchanges']]}
                      for code, r in out.items()}, ensure_ascii=False, indent=1))
