"""Alipay+ 沙箱验证：在接入方 ALIPAY_PLUS 下录入验证账号 SBX_MAIN、保存并经渠道沙箱验证运行用例（技术方案 T7）。

账号材料只从仓库外对接包读取（ALIPAY_PACKAGE，默认 ~/Desktop/channels/I_ALIPAY/test-credentials.json 的 materials 与
subMerchants.items[0]），进程内使用，不打印、不写回执。回执只含运行结论、期望核对与渠道 resultStatus/resultCode；收银台 URL 与渠道单号不写入。

用法：
  python3 sandbox_cases.py              录入账号，运行不需付款的 4 个用例
  python3 sandbox_cases.py CASE ...     只运行指定用例
SBX_PAY_PENDING 留下一笔未付款测试单（单号见回执 attemptIds），由 Alipay+ 到期关闭或另行直连关单。
"""
import json, os, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-渠道沙箱验证/assets/harness'))
from common import ADMIN, login, runtime  # noqa: E402
import verify_mock_e2e as harness  # noqa: E402

INTEGRATION = 'ALIPAY_PLUS'
harness.BASE = f'{ADMIN}/integrations/{INTEGRATION}'
ACCOUNT = 'SBX_MAIN'
REASON = 'Alipay+ 沙箱验证'
PACKAGE = Path(os.environ.get('ALIPAY_PACKAGE', Path.home() / 'Desktop/channels/I_ALIPAY/test-credentials.json'))
CHANNEL = '${account.identity}'
NONE = 'APVNONE${random.digits(12)}'


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': CHANNEL, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def refund(attempt_id, value='1.00'):
    return {'refund': {'refundId': 'APR${random.digits(12)}', 'paymentAttemptId': attempt_id,
                       'amount': {'value': value, 'currency': 'USD'}, 'reason': 'Sandbox refund verify'}}


PAY = {'payment': {'attemptId': 'APV${random.digits(12)}', 'amount': {'value': '1.00', 'currency': 'USD'}, 'subject': 'MoR sandbox verify',
                   'payerReference': 'sbx-payer-1', 'returnUrl': 'https://example.com/mor/return', 'country': 'CN',
                   'paymentMethodType': 'ALIPAY_CN'}}
PENDING_ID = '${steps.pay.input.payment.attemptId}'

CASES = [
    ('SBX_PAY_QUERY_NOT_FOUND', '查询不存在的订单', [
        step('query', 'PAY_QUERY', {'payment': {'attemptId': NONE}}, 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'),
             ('failureCode', 'EQUALS', 'ORDER_NOT_EXIST'))]),
    ('SBX_REFUND_NOT_EXIST', '对不存在的订单退款', [
        step('refund', 'REFUND', refund(NONE), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'ORDER_NOT_EXIST'))]),
    ('SBX_REFUND_QUERY_NOT_FOUND', '退款查询：原单不存在', [
        step('query', 'REFUND_QUERY', {'refund': {'refundId': 'APRNONE${random.digits(12)}', 'paymentAttemptId': NONE}}, 'FAIL',
             ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'ORDER_NOT_EXIST'))]),
    ('SBX_PAY_PENDING', '收银台下单 USD 1.00 / ALIPAY_CN：待付款、查询、未付款退款被拒、退款查询无记录', [
        step('pay', 'PAY', PAY, 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'), ('nextAction.type', 'EQUALS', 'REDIRECT'),
             ('nextAction.redirectUrl', 'EXISTS', None), ('channelTransactionRef', 'EXISTS', None)),
        step('query', 'PAY_QUERY', {'payment': {'attemptId': PENDING_ID}}, 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'),
             ('channelTransactionRef', 'EXISTS', None), ('amount.value', 'EQUALS', '1.00'), ('amount.currency', 'EQUALS', 'USD')),
        step('refund', 'REFUND', refund(PENDING_ID), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'),
             ('failureCode', 'EQUALS', 'INVALID_ORDER_STATUS')),
        step('refundQuery', 'REFUND_QUERY', {'refund': {'refundId': '${steps.refund.input.refund.refundId}', 'paymentAttemptId': PENDING_ID}},
             'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'REFUND_NOT_EXIST'))]),
]


def account_body():
    data = json.loads(PACKAGE.read_text())
    material, merchant = data['materials'], data['subMerchants']['items'][0]
    return {'accountName': 'Alipay+ 沙箱测试商户', 'channelCode': INTEGRATION, 'channelAccountId': '900000000000000004', 'entityNo': 'UAT01',
            'parameters': {'clientId': material['channelMerchant.clientId'], 'alipayPublicKey': material['channelMerchant.publicKey'].strip(),
                           'referenceMerchantId': merchant['targetMerchantId'], 'merchantName': merchant['targetMerchantName'],
                           'merchantMcc': merchant['targetMerchantMcc'], 'merchantRegion': merchant['targetMerchantCountry'],
                           'merchantCity': merchant['targetMerchantCity'], 'settlementCurrency': 'USD'},
            'credentials': {'merchantPrivateKey': material['channelMerchant.privateKey'].strip()}, 'reason': REASON}


OMITTED = ('redirectUrl', 'normalUrl', 'schemeUrl', 'channelTransactionRef', 'channelRefundRef')


def scrub(value):
    """回执不写收银台 URL 与渠道单号。"""
    if isinstance(value, dict):
        return {k: ('[omitted]' if k in OMITTED else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


def evidence(detail):
    result = scrub(harness.summary(detail))
    for item in detail['steps']:
        attempt = (((item.get('resolvedInput') or {}).get('payment') or {}).get('attemptId'))
        if item['stepKey'] == 'pay' and attempt:
            result['attemptIds'] = [attempt]
        for node in (item.get('trace') or {}).get('nodes', []):
            response = (node.get('exchange') or {}).get('response') or {}
            try:
                parsed = json.loads((response.get('body') or {}).get('text') or '')
                channel = {'result': (parsed.get('result') or {}).get('resultStatus'), 'code': (parsed.get('result') or {}).get('resultCode'),
                           'paymentResult': (parsed.get('paymentResult') or {}).get('resultStatus')}
            except (ValueError, AttributeError):
                channel = None
            result.setdefault('exchanges', []).append({'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                                       'reason': node.get('reason'), 'http': response.get('status'),
                                                       'error': (node.get('exchange') or {}).get('error'), 'channel': channel})
    return result


if __name__ == '__main__':
    token = login()
    harness.upsert(token, 'verify-accounts', ACCOUNT, account_body())
    selected = [c for c in CASES if len(sys.argv) == 1 or c[0] in sys.argv[1:]]
    out = {}
    for code, name, steps in selected:
        harness.upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps,
                                                    'reason': REASON})
        out[code] = evidence(harness.run(token, code))
    (runtime / 'alipay-sandbox-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'], 'exchanges': r.get('exchanges'),
                             'steps': r['steps']} for code, r in out.items()}, ensure_ascii=False, indent=1))
