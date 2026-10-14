"""PayBy UAT：在接入方 PAYBY 下录入验证账号 UAT_MAIN、保存并经渠道沙箱验证运行用例（技术方案第 6 节）。

账号材料只从仓库外对接包读取（PAYBY_PACKAGE，默认 ~/Desktop/channels/I_PAYBY/test-credentials.json 的“入款通用”记录），
进程内使用，不打印、不写回执。回执只含运行结论、期望核对与渠道 head.code/applyStatus/业务状态。

用法：
  python3 uat_cases.py                     录入账号，运行不依赖已有订单的用例
  python3 uat_cases.py --unpaid <单号>      另运行 UAT_UNPAID_QUERY_CANCEL：对一笔 CREATED 订单查询 → 退款被拒 → 关单 ×2 → 查询
"""
import json, os, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-渠道沙箱验证/assets/harness'))
from common import ADMIN, login, runtime  # noqa: E402
import verify_mock_e2e as harness  # noqa: E402

INTEGRATION = 'PAYBY'
harness.BASE = f'{ADMIN}/integrations/{INTEGRATION}'
ACCOUNT = 'UAT_MAIN'
REASON = 'PayBy UAT 沙箱验证'
PACKAGE = Path(os.environ.get('PAYBY_PACKAGE', Path.home() / 'Desktop/channels/I_PAYBY/test-credentials.json'))
CHANNEL = '${account.identity}'


def outputs(*items):
    return [{'path': p, 'match': m, **({'value': v} if v is not None else {})} for p, m, v in items]


def step(step_id, api, payload, execute_status, *checks):
    return {'id': step_id, 'standardApiCode': api, 'input': {'channelAccount': CHANNEL, **payload},
            'expect': {'executeStatus': execute_status, 'outputs': outputs(*checks)}}


def order(attempt_id):
    return {'payment': {'attemptId': attempt_id}}


CASES = [
    ('UAT_PAY_QUERY_NOT_FOUND', '查询不存在的订单', [
        step('query', 'PAY_QUERY', order('PBYVNONE${random.digits(12)}'), 'FAIL', ('paymentStatus', 'EQUALS', 'NOT_FOUND'),
             ('failureCode', 'EQUALS', '62004'))]),
    ('UAT_CANCEL_NOT_FOUND', '关闭不存在的订单', [
        step('cancel', 'PAY_CANCEL', order('PBYVNONE${random.digits(12)}'), 'FAIL', ('cancelStatus', 'EQUALS', 'NOT_FOUND'),
             ('failureCode', 'EQUALS', '62004'))]),
    ('UAT_REFUND_NOT_EXIST', '对不存在的订单退款', [
        step('refund', 'REFUND', {'refund': {'refundId': 'PBYR${random.digits(12)}', 'paymentAttemptId': 'PBYVNONE${random.digits(12)}',
                                             'amount': {'value': '1.00', 'currency': 'AED'}, 'reason': 'Sandbox refund verify'}},
             'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', '62004'))]),
    ('UAT_REFUND_QUERY_NOT_FOUND', '查询不存在的退款', [
        step('query', 'REFUND_QUERY', {'refund': {'refundId': 'PBYRNONE${random.digits(12)}'}}, 'FAIL',
             ('refundStatus', 'EQUALS', 'NOT_FOUND'), ('failureCode', 'EQUALS', '62007'))]),
    ('UAT_BALANCE_AED', '查询 AED 可用余额', [
        step('balance', 'BALANCE_QUERY', {'currency': 'AED'}, 'SUCCESS', ('available.value', 'EXISTS', None),
             ('available.currency', 'EQUALS', 'AED'))]),
]


def unpaid_case(attempt_id):
    return ('UAT_UNPAID_QUERY_CANCEL', '未付款订单：查询、退款被拒、关单两次、再查询', [
        step('query1', 'PAY_QUERY', order(attempt_id), 'SUCCESS', ('paymentStatus', 'EQUALS', 'PENDING'),
             ('channelTransactionRef', 'EXISTS', None), ('amount.value', 'EQUALS', '1.0')),  # PayBy 返回不补小数位
        step('refund', 'REFUND', {'refund': {'refundId': 'PBYR${random.digits(12)}', 'paymentAttemptId': attempt_id,
                                             'amount': {'value': '1.00', 'currency': 'AED'}}},
             'FAIL', ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', '62015')),
        step('cancel1', 'PAY_CANCEL', order(attempt_id), 'SUCCESS', ('cancelStatus', 'EQUALS', 'CLOSED')),
        step('cancel2', 'PAY_CANCEL', order(attempt_id), 'SUCCESS', ('cancelStatus', 'EQUALS', 'CLOSED')),
        step('query2', 'PAY_QUERY', order(attempt_id), 'SUCCESS', ('paymentStatus', 'EQUALS', 'FAILED'),
             ('channelStatus', 'EQUALS', 'FAILURE'))])


def account_body():
    record = next(r for r in json.loads(PACKAGE.read_text())['records'] if r['channelCode'] == 'I_PAYBY_P01')['account']
    return {'accountName': 'PayBy UAT 测试商户（入款通用）', 'channelCode': 'PAYBY', 'channelAccountId': '900000000000000003', 'entityNo': 'UAT01',
            'parameters': {'partnerId': record['partnerId'], 'paybyPublicKey': record['channelPublicKey_bak']['decrypted'].strip()},
            'credentials': {'merchantPrivateKey': record['privateKey']['decrypted'].strip()}, 'reason': REASON}


def evidence(detail):
    result = harness.summary(detail)
    for item in detail['steps']:
        for node in (item.get('trace') or {}).get('nodes', []):
            response = (node.get('exchange') or {}).get('response') or {}
            try:
                parsed = json.loads((response.get('body') or {}).get('text') or '')
                body = parsed.get('body') or {}
                business = body.get('acquireOrder') or body.get('refundOrder') or {}
                channel = {'applyStatus': parsed['head'].get('applyStatus'), 'code': parsed['head'].get('code'),
                           'msg': parsed['head'].get('msg'), 'status': business.get('status')}
            except (ValueError, KeyError, AttributeError):
                channel = None
            result.setdefault('exchanges', []).append({'step': item['stepKey'], 'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                                       'reason': node.get('reason'), 'http': response.get('status'),
                                                       'error': (node.get('exchange') or {}).get('error'), 'channel': channel})
    return result


if __name__ == '__main__':
    token = login()
    harness.upsert(token, 'verify-accounts', ACCOUNT, account_body())
    cases = CASES if len(sys.argv) == 1 else [unpaid_case(sys.argv[2])]
    out = {}
    for code, name, steps in cases:
        harness.upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps,
                                                    'reason': REASON})
        out[code] = evidence(harness.run(token, code))
    (runtime / f"uat-cases-{'base' if len(sys.argv) == 1 else 'unpaid'}.json").write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: {'runStatus': r['runStatus'], 'failureSummary': r['failureSummary'], 'exchanges': r.get('exchanges'),
                             'steps': r['steps']} for code, r in out.items()}, ensure_ascii=False, indent=1))
