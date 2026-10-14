"""PayerMax 退款 UAT：在接入方 PAYERMAX 下保存退款验证用例并经渠道沙箱验证运行（技术方案第 5.2 节）。

前提：接入方 PAYERMAX 的出站地址已标记沙箱、验证账号 UAT_MAIN 已录入（本脚本不读取、不修改账号材料）。
用法：
  python3 uat_refund_cases.py                       保存并运行 UAT_REFUND_NOT_EXIST、UAT_REFUND_QUERY_NOT_FOUND
  python3 uat_refund_cases.py <已付款 attemptId> <退款金额>   另保存并运行 UAT_REFUND_THEN_QUERY（对该订单部分退款后查询）
输出只含运行结论、期望核对与渠道响应状态码/错误码，不含请求头与账号材料。
"""
import json, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-渠道沙箱验证/assets/harness'))
from common import ADMIN, call, login, runtime  # noqa: E402
import verify_mock_e2e as harness  # noqa: E402

harness.BASE = f'{ADMIN}/integrations/PAYERMAX'
REASON = 'PayerMax 退款 UAT 沙箱验证'
ACCOUNT = 'UAT_MAIN'


def refund_step(attempt_id, amount, expect):
    return {'id': 'refund', 'standardApiCode': 'REFUND', 'input': {'channelAccount': '${account.identity}', 'refund': {
        'refundId': 'RFDV${random.digits(12)}', 'paymentAttemptId': attempt_id, 'amount': {'value': amount, 'currency': 'USD'},
        'reason': 'Sandbox refund verify'}}, 'expect': expect}


def outputs(*items):
    return [{'path': path, 'match': match, **({'value': value} if value is not None else {})} for path, match, value in items]


NOT_EXIST = [refund_step('PMXVNONE${random.digits(12)}', '1.00', {'executeStatus': 'FAIL', 'outputs': outputs(
    ('refundStatus', 'EQUALS', 'FAILED'), ('failureCode', 'EQUALS', 'ORDER_NOT_EXIST'))})]
QUERY_NOT_FOUND = [{'id': 'query', 'standardApiCode': 'REFUND_QUERY', 'input': {'channelAccount': '${account.identity}', 'refund': {
    'refundId': 'RFDVNONE${random.digits(12)}'}}, 'expect': {'executeStatus': 'FAIL', 'outputs': outputs(('refundStatus', 'EQUALS', 'NOT_FOUND'))}}]


def then_query(attempt_id, amount):
    query = {'id': 'query', 'standardApiCode': 'REFUND_QUERY', 'input': {'channelAccount': '${account.identity}', 'refund': {
        'refundId': '${steps.refund.input.refund.refundId}'}}, 'expect': {'executeStatus': 'SUCCESS', 'outputs': outputs(
            ('channelRefundRef', 'EXISTS', None))}}
    return [refund_step(attempt_id, amount, {'executeStatus': 'SUCCESS', 'outputs': outputs(('channelRefundRef', 'EXISTS', None))}), query]


def run(token, case_code):
    started = call('run-start', 'POST', f'{harness.BASE}/verify-cases/{case_code}/runs', token, {'reason': REASON})[1]
    for _ in range(60):
        detail = call('run-get', 'GET', f"{harness.BASE}/verify-runs/{started['runCode']}", token)[1]
        if detail['runStatus'] != 'RUNNING':
            return detail
        time.sleep(2)
    raise AssertionError('运行超时')


def evidence(detail):
    result = harness.summary(detail)
    for step in detail['steps']:
        for node in (step.get('trace') or {}).get('nodes', []):
            exchange = node.get('exchange') or {}
            response = exchange.get('response') or {}
            body = (response.get('body') or {}).get('text') or ''
            try:
                parsed = json.loads(body)
                channel = {'code': parsed.get('code'), 'msg': parsed.get('msg'), 'status': (parsed.get('data') or {}).get('status')}
            except ValueError:
                channel = None
            result.setdefault('exchanges', []).append({'node': node.get('id'), 'executeStatus': node.get('executeStatus'),
                                                       'reason': node.get('reason'), 'http': response.get('status'),
                                                       'error': exchange.get('error'), 'channel': channel})
    return result


if __name__ == '__main__':
    token = login()
    cases = [('UAT_REFUND_NOT_EXIST', '对不存在的订单退款', NOT_EXIST), ('UAT_REFUND_QUERY_NOT_FOUND', '查询不存在的退款', QUERY_NOT_FOUND)]
    if len(sys.argv) == 3:
        cases = [('UAT_REFUND_THEN_QUERY', '已付款订单部分退款后查询', then_query(sys.argv[1], sys.argv[2]))]
    out = {}
    for code, name, steps in cases:
        harness.upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps,
                                                    'reason': REASON})
        out[code] = evidence(run(token, code))
    (runtime / 'uat-refund-cases.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(out, ensure_ascii=False, indent=1))
