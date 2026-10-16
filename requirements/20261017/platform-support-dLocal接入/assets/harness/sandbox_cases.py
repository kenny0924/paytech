"""dLocal 沙箱验证：在接入方 DLOCAL 下录入验证账号 SBX_MAIN、保存并经渠道沙箱验证运行用例（技术方案第 5 节）。

账号材料只从仓库外对接包读取（DLOCAL_PACKAGE，默认 ~/Desktop/channels/I_DLOCAL/test-credentials.json 的 records[6].account），
进程内使用，不打印、不写回执。回执只含运行结论、期望核对与渠道 HTTP 状态 / status / code；跳转 URL 与渠道单号不写入。

已付款链路不需要人工付款：harness 直连 dLocal 沙箱工具（不经 Platform）把订单强制为 PAID、把退款强制为 SUCCESS，
再继续经 Platform 运行后续用例。

用法：
  python3 sandbox_cases.py              录入账号，运行全部用例（含已付款链路）
  python3 sandbox_cases.py CASE ...     只运行指定的不需付款用例
"""
import hashlib, hmac, json, os, sys, time, urllib.error, urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-渠道沙箱验证/assets/harness'))
from common import ADMIN, login, runtime  # noqa: E402
import verify_mock_e2e as harness  # noqa: E402

INTEGRATION = 'DLOCAL'
harness.BASE = f'{ADMIN}/integrations/{INTEGRATION}'
ACCOUNT = 'SBX_MAIN'
REASON = 'dLocal 沙箱验证'
PACKAGE = Path(os.environ.get('DLOCAL_PACKAGE', Path.home() / 'Desktop/channels/I_DLOCAL/test-credentials.json'))
SANDBOX = 'https://sandbox.dlocal.com'
CHANNEL = '${account.identity}'
NONE = 'DLVNONE${random.digits(12)}'


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': CHANNEL, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def refund(attempt_id, value='1.00', refund_id='DLR${random.digits(12)}'):
    return {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id,
                       'amount': {'value': value, 'currency': 'USD'}, 'reason': 'Sandbox refund verify'}}


def pay(method=None):
    payment = {'attemptId': 'DLV${random.digits(12)}', 'amount': {'value': '1.00', 'currency': 'USD'}, 'subject': 'MoR sandbox verify',
               'payerReference': 'sbx-payer-1', 'returnUrl': 'https://example.com/mor/return', 'country': 'BR'}
    if method:
        payment['paymentMethodType'] = method
    return {'payment': payment}


def attempt(step_id):
    return {'payment': {'attemptId': step_id}}


PENDING_ID = '${steps.pay.input.payment.attemptId}'
PAY_PENDING = ('pay', 'PAY', 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'), ('nextAction.type', 'EQUALS', 'REDIRECT'),
               ('nextAction.redirectUrl', 'EXISTS', None), ('channelTransactionRef', 'EXISTS', None))

CASES = [
    ('SBX_PAY_QUERY_NOT_FOUND', '查询不存在的订单', [
        step('query', 'PAY_QUERY', attempt(NONE), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', '4000'))]),
    ('SBX_REFUND_NOT_EXIST', '对不存在的订单退款', [
        step('refund', 'REFUND', refund(NONE), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', '4000'))]),
    ('SBX_REFUND_QUERY_NOT_FOUND', '退款查询：原单不存在', [
        step('query', 'REFUND_QUERY', {'refund': {'refundId': 'DLRNONE${random.digits(12)}', 'paymentAttemptId': NONE}}, 'FAIL',
             ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', '4000'))]),
    ('SBX_PAY_PENDING_CANCEL', '托管跳转下单 USD 1.00 / BR：待付款、查询、未付款退款被拒、退款查询无记录、撤销、重复撤销、撤销后查询', [
        step(PAY_PENDING[0], PAY_PENDING[1], pay(), *PAY_PENDING[2:]),
        step('query', 'PAY_QUERY', attempt(PENDING_ID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'),
             ('channelTransactionRef', 'EXISTS', None), ('amount.value', 'EQUALS', '1.00'), ('amount.currency', 'EQUALS', 'USD')),
        step('refund', 'REFUND', refund(PENDING_ID), 'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', '5002')),
        step('refundQuery', 'REFUND_QUERY', {'refund': {'refundId': '${steps.refund.input.refund.refundId}', 'paymentAttemptId': PENDING_ID}},
             'FAIL', ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', 'REFUND_NOT_EXIST')),
        step('cancel', 'PAY_CANCEL', attempt(PENDING_ID), 'SUCCESS', ('cancelStatus', 'EQUALS', 'CLOSED'), ('channelStatus', 'EQUALS', 'CANCELLED')),
        step('cancelAgain', 'PAY_CANCEL', attempt(PENDING_ID), 'SUCCESS', ('cancelStatus', 'EQUALS', 'CLOSED')),
        step('queryClosed', 'PAY_QUERY', attempt(PENDING_ID), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'),
             ('channelStatus', 'EQUALS', 'CANCELLED'))]),
]


def paid_order_case():
    return 'SBX_PAID_ORDER', '已付款链路 1：下单 USD 1.00 / BR / PQ', [step(PAY_PENDING[0], PAY_PENDING[1], pay('PQ'), *PAY_PENDING[2:])]


def paid_refund_case(attempt_id, refund_id):
    return 'SBX_PAID_REFUND', '已付款链路 2：查询成功、撤销被拒、部分退款 0.50', [
        step('query', 'PAY_QUERY', attempt(attempt_id), 'SUCCESS', ('paymentStatus', 'EQUALS', 'SUCCEEDED'),
             ('amount.value', 'EQUALS', '1.00'), ('amount.currency', 'EQUALS', 'USD')),
        step('cancel', 'PAY_CANCEL', attempt(attempt_id), 'FAIL', ('cancelStatus', 'EQUALS', 'ALREADY_PAID')),
        step('refund', 'REFUND', refund(attempt_id, '0.50', refund_id), 'SUCCESS', ('refundStatus', 'EQUALS', 'PROCESSING'),
             ('channelRefundRef', 'EXISTS', None))]


def paid_refund_query_case(attempt_id, refund_id):
    return 'SBX_PAID_REFUND_QUERY', '已付款链路 3：退款成功后查询', [
        step('refundQuery', 'REFUND_QUERY', {'refund': {'refundId': refund_id, 'paymentAttemptId': attempt_id}}, 'SUCCESS',
             ('refundStatus', 'EQUALS', 'SUCCEEDED'), ('channelStatus', 'EQUALS', 'SUCCESS'), ('channelRefundRef', 'EXISTS', None))]


def credentials():
    return json.loads(PACKAGE.read_text())['records'][6]['account']


def account_body():
    material = credentials()
    return {'accountName': 'dLocal 沙箱测试商户', 'channelCode': INTEGRATION, 'channelAccountId': '900000000000000005', 'entityNo': 'UAT01',
            'parameters': {'xLogin': material['xLogin']},
            'credentials': {'xTransKey': material['xTransKey'], 'secretKey': material['secretKey']}, 'reason': REASON}


def sandbox(method, api_path, body=None):
    """直连 dLocal 沙箱（只用于沙箱工具与等待状态生效）；返回 (HTTP 状态, JSON)。"""
    material = credentials()
    text = json.dumps(body, separators=(',', ':')) if body is not None else ''
    now = datetime.now(timezone.utc)
    date = now.strftime('%Y-%m-%dT%H:%M:%S.') + f'{now.microsecond // 1000:03d}Z'
    signature = hmac.new(material['secretKey'].encode(), (material['xLogin'] + date + text).encode(), hashlib.sha256).hexdigest()
    headers = {'X-Login': material['xLogin'], 'X-Trans-Key': material['xTransKey'], 'X-Version': '2.1', 'User-Agent': 'MoR-Harness',
               'Content-Type': 'application/json', 'X-Date': date, 'Authorization': 'V2-HMAC-SHA256, Signature: ' + signature}
    request = urllib.request.Request(SANDBOX + api_path, data=text.encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            status, raw = response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        status, raw = failure.code, failure.read().decode()
    try:
        return status, json.loads(raw) if raw else None
    except ValueError:
        return status, None


def wait_status(api_path, expected):
    for _ in range(30):
        status, body = sandbox('GET', api_path)
        if status == 200 and body and body.get('status') == expected:
            return
        time.sleep(2)
    raise AssertionError(f'沙箱状态未变为 {expected}')


OMITTED = ('redirectUrl', 'channelTransactionRef', 'channelRefundRef')


def scrub(value):
    """回执不写跳转 URL 与渠道单号。"""
    if isinstance(value, dict):
        return {k: ('[omitted]' if k in OMITTED else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


def evidence(detail):
    result = scrub(harness.summary(detail))
    for item in detail['steps']:
        resolved = item.get('resolvedInput') or {}
        attempt_id = (resolved.get('payment') or {}).get('attemptId') or (resolved.get('refund') or {}).get('paymentAttemptId')
        if attempt_id and attempt_id not in result.setdefault('attemptIds', []):
            result['attemptIds'].append(attempt_id)
        for node in (item.get('trace') or {}).get('nodes', []):
            response = (node.get('exchange') or {}).get('response') or {}
            try:
                parsed = json.loads((response.get('body') or {}).get('text') or '')
                channel = {'status': parsed.get('status'), 'code': parsed.get('code')}
            except (ValueError, AttributeError):
                channel = None
            result.setdefault('exchanges', []).append({'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                                       'reason': node.get('reason'), 'http': response.get('status'),
                                                       'error': (node.get('exchange') or {}).get('error'), 'channel': channel})
    return result


def step_output(detail, step_key, name):
    item = next(s for s in detail['steps'] if s['stepKey'] == step_key)
    return ((item.get('result') or {}).get('outputs') or {}).get(name), item.get('resolvedInput') or {}


def run_case(token, out, case):
    code, name, steps = case
    harness.upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps, 'reason': REASON})
    detail = harness.run(token, code)
    out[code] = evidence(detail)
    return detail


def run_paid(token, out):
    detail = run_case(token, out, paid_order_case())
    payment_id, resolved = step_output(detail, 'pay', 'channelTransactionRef')
    if detail['runStatus'] != 'PASSED' or not payment_id:
        return
    attempt_id = resolved['payment']['attemptId']
    assert sandbox('POST', '/sandbox-tools/payments', {'payment_id': payment_id, 'status': 'PAID'})[0] == 200
    wait_status(f'/orders/{attempt_id}', 'PAID')
    refund_id = 'DLR' + datetime.now(timezone.utc).strftime('%y%m%d%H%M%S')
    detail = run_case(token, out, paid_refund_case(attempt_id, refund_id))
    channel_refund, _ = step_output(detail, 'refund', 'channelRefundRef')
    if detail['runStatus'] != 'PASSED' or not channel_refund:
        return
    assert sandbox('PATCH', '/sandbox-tools/refunds/update-status', {'id_refund': channel_refund, 'status': 'SUCCESS'})[0] == 200
    wait_status(f'/refunds/{channel_refund}', 'SUCCESS')
    run_case(token, out, paid_refund_query_case(attempt_id, refund_id))


if __name__ == '__main__':
    token = login()
    harness.upsert(token, 'verify-accounts', ACCOUNT, account_body())
    out = {}
    for case in CASES:
        if len(sys.argv) == 1 or case[0] in sys.argv[1:]:
            run_case(token, out, case)
    if len(sys.argv) == 1:
        run_paid(token, out)
    (runtime / 'dlocal-sandbox-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'], 'exchanges': r.get('exchanges'),
                             'steps': r['steps']} for code, r in out.items()}, ensure_ascii=False, indent=1))
