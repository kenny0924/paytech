"""Toss Pay 沙箱验证：在接入方 TOSS_PAY 下录入验证账号 SBX_MAIN、保存并经渠道沙箱验证运行不需付款的用例（技术方案第 5 节）。

apiKey 只从仓库外对接包读取（TOSS_PACKAGE，默认 ~/Desktop/channels/I_TOSS/test-credentials.json 的 records[0]），必须是 sk_test_
测试 key；进程内使用，不打印、不写回执。回执只含运行结论、期望核对与渠道 HTTP 状态 / code / errorCode；付款页 URL 与 payToken 不写入。

全部用例约 9 次渠道调用，避免触发 Toss 的自动封 IP。

用法：
  python3 sandbox_cases.py              录入账号，运行全部用例
  python3 sandbox_cases.py CASE ...     只运行指定用例
"""
import json, os, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-渠道沙箱验证/assets/harness'))
from common import ADMIN, login, runtime  # noqa: E402
import verify_mock_e2e as harness  # noqa: E402

INTEGRATION = 'TOSS_PAY'
harness.BASE = f'{ADMIN}/integrations/{INTEGRATION}'
ACCOUNT = 'SBX_MAIN'
REASON = 'Toss Pay 沙箱验证'
PACKAGE = Path(os.environ.get('TOSS_PACKAGE', Path.home() / 'Desktop/channels/I_TOSS/test-credentials.json'))
CHANNEL = '${account.identity}'
NONE = 'TPVNONE${random.digits(12)}'
INVALID = 'COMMON_INVALID_PARAMETER'


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': CHANNEL, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def refund(attempt_id, value='1000', refund_id='TPR${random.digits(12)}'):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id,
                       'amount': {'value': value, 'currency': 'KRW'}, 'reason': 'Sandbox refund verify'}}


def pay(currency='KRW', value='1000'):
    return {'payment': {'attemptId': 'TPV${random.digits(12)}', 'amount': {'value': value, 'currency': currency},
                        'subject': 'MoR sandbox verify', 'payerReference': 'sbx-payer-1',
                        'returnUrl': 'https://example.com/mor/return', 'country': 'KR'}}


def attempt(attempt_id):
    return {'payment': {'attemptId': attempt_id}}


PENDING_ID = '${steps.pay.input.payment.attemptId}'

CASES = [
    ('SBX_PAY_QUERY_NOT_FOUND', '查询不存在的订单', [
        step('query', 'PAY_QUERY', attempt(NONE), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', INVALID))]),
    ('SBX_REFUND_NOT_EXIST', '对不存在的订单退款', [
        step('refund', 'REFUND', refund(NONE), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', INVALID))]),
    ('SBX_REFUND_QUERY_NOT_FOUND', '退款查询：原单不存在', [
        step('query', 'REFUND_QUERY', {'refund': {'refundId': 'TPRNONE${random.digits(12)}', 'paymentAttemptId': NONE}}, 'FAIL',
             ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', INVALID))]),
    ('SBX_PAY_PENDING', '建单 KRW 1000：待付款、查询、未付款退款被拒、退款查询无记录', [
        step('pay', 'PAY', pay(), 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'), ('nextAction.type', 'EQUALS', 'REDIRECT'),
             ('nextAction.redirectUrl', 'EXISTS', None), ('channelTransactionRef', 'EXISTS', None)),
        step('query', 'PAY_QUERY', attempt(PENDING_ID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'),
             ('channelStatus', 'EQUALS', 'PAY_STANDBY'), ('channelTransactionRef', 'EXISTS', None),
             ('amount.value', 'EQUALS', '1000'), ('amount.currency', 'EQUALS', 'KRW')),
        step('refund', 'REFUND', refund(PENDING_ID), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'COMMON_REFUND_ERROR')),
        step('refundQuery', 'REFUND_QUERY', {'refund': {'refundId': '${steps.refund.input.refund.refundId}', 'paymentAttemptId': PENDING_ID}},
             'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'REFUND_NOT_EXIST'))]),
    ('SBX_PAY_NOT_KRW', '非 KRW 下单：ERROR，不发请求', [
        step('pay', 'PAY', pay('USD', '1.00'), 'ERROR')]),
]


def account_body():
    key = json.loads(PACKAGE.read_text())['records'][0]['account']['apiKey']['decrypted']
    assert key.startswith('sk_test_'), '只允许测试 key'
    return {'accountName': 'Toss Pay 测试商户', 'channelCode': INTEGRATION, 'channelAccountId': '900000000000000006', 'entityNo': 'UAT01',
            'parameters': {}, 'credentials': {'apiKey': key}, 'reason': REASON}


OMITTED = ('redirectUrl', 'channelTransactionRef', 'channelRefundRef')


def scrub(value):
    """回执不写付款页 URL 与渠道单号。"""
    if isinstance(value, dict):
        return {k: ('[omitted]' if k in OMITTED else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


def evidence(detail):
    result = scrub(harness.summary(detail))
    for item in detail['steps']:
        for node in (item.get('trace') or {}).get('nodes', []):
            exchange = node.get('exchange') or {}
            response = exchange.get('response') or {}
            try:
                parsed = json.loads((response.get('body') or {}).get('text') or '')
                channel = {'code': parsed.get('code'), 'errorCode': parsed.get('errorCode'), 'payStatus': parsed.get('payStatus')}
            except (ValueError, AttributeError):
                channel = None
            request_text = ((exchange.get('request') or {}).get('body') or {}).get('text') or ''
            result.setdefault('exchanges', []).append({
                'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'), 'reason': node.get('reason'),
                'http': response.get('status'), 'error': exchange.get('error'), 'channel': channel,
                'apiKeyMasked': '"apiKey":"[REDACTED]"' in request_text if request_text else None})
    return result


def run_case(token, out, case):
    code, name, steps = case
    harness.upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps, 'reason': REASON})
    detail = harness.run(token, code)
    out[code] = evidence(detail)
    return detail


if __name__ == '__main__':
    token = login()
    harness.upsert(token, 'verify-accounts', ACCOUNT, account_body())
    out = {}
    for case in CASES:
        if len(sys.argv) == 1 or case[0] in sys.argv[1:]:
            run_case(token, out, case)
    (runtime / 'toss-sandbox-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'], 'exchanges': r.get('exchanges'),
                             'steps': r['steps']} for code, r in out.items()}, ensure_ascii=False, indent=1))
