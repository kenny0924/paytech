"""PayerMax 卡直付阶段 2：经渠道沙箱验证（真实入口 Web 代理 → Gateway → BFF → Platform）运行卡直付用例。

测试卡来自验证账号凭据 testCard.*（PayerMax 公布的 UAT 测试卡，合成数据），沙箱运行不调用 Payment。按 PayerMax UAT 规则用 subject
控制结果：SUCCESS / FAILED / PENDING / 3DS。合成渠道另按订单号末位 2、3、4 返回参数错误、系统繁忙与错误签名。
每次运行检查证据：请求体卡号只有前 6 后 4、其余卡字段为 [CARD]，全部证据文本中没有卡号与 CVV 原值。

用法：
  python3 verify_card.py mock   对 PAYERMAX_MOCK（合成渠道密钥），验证账号 MOCK_CARD
  python3 verify_card.py uat    对 PAYERMAX（UAT），验证账号 UAT_MAIN（运行前已录入测试卡）
回执只含用例、运行结论、核对结论与证据中的卡号遮蔽文本。
"""
import json, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

TEST_CARD = {'pan': '4444333322221111', 'expiryMonth': '03', 'expiryYear': '2030', 'cardholderName': 'James Smith', 'cvv': '123'}
MASKED_PAN = '444433******1111'
REASON = 'PayerMax 卡直付沙箱验证'


def step(subject, suffix, expect):
    return [{'id': 'pay', 'standardApiCode': 'PAY', 'input': {
        'channelAccount': '${account.identity}', 'card': {'token': 'sandbox-test-card'},
        'payment': {'attemptId': 'PMXC${random.digits(11)}' + suffix, 'amount': {'value': '10.00', 'currency': 'USD'},
                    'country': 'US', 'returnUrl': 'https://merchant.local.invalid/return', 'subject': subject,
                    'payerReference': 'payer-ref-verify', 'clientIp': '203.0.113.10',
                    'userAgent': 'Mozilla/5.0 (sandbox verification)'}},
        'expect': expect}]


def outputs(**pairs):
    return [{'path': key.replace('__', '.'), 'match': match, **({'value': value} if value is not None else {})}
            for key, (match, value) in pairs.items()]


CASES = {
    'CARD_SUCCESS': ('卡直付成功', step('SUCCESS', '1', {'executeStatus': 'SUCCESS', 'returnId': 'succeeded', 'outputs': outputs(
        paymentStatus=('EQUALS', 'SUCCEEDED'), channelTransactionRef=('EXISTS', None), nextAction=('ABSENT', None))})),
    'CARD_3DS': ('卡直付需 3DS', step('3DS', '1', {'executeStatus': 'SUCCESS', 'returnId': 'challenge', 'outputs': outputs(
        paymentStatus=('EQUALS', 'PENDING'), nextAction__type=('EQUALS', 'THREE_DS_CHALLENGE'),
        nextAction__redirectUrl=('EXISTS', None))})),
    'CARD_PENDING': ('卡直付处理中', step('PENDING', '1', {'executeStatus': 'SUCCESS', 'returnId': 'pending', 'outputs': outputs(
        paymentStatus=('EQUALS', 'PENDING'), nextAction=('ABSENT', None))})),
    'CARD_DECLINED': ('卡直付被拒', step('FAILED', '1', {'executeStatus': 'SUCCESS', 'returnId': 'declined', 'outputs': outputs(
        paymentStatus=('EQUALS', 'FAILED'), channelTransactionRef=('EXISTS', None), failureCode=('EXISTS', None))})),
}
MOCK_ONLY = {
    'CARD_REJECTED': ('参数错误', step('SUCCESS', '2', {'executeStatus': 'FAIL', 'outputs': outputs(
        paymentStatus=('EQUALS', 'FAILED'), failureCode=('EQUALS', 'PARAMS_INVALID'))})),
    'CARD_SYSTEM_ERROR': ('系统繁忙', step('SUCCESS', '3', {'executeStatus': 'ERROR'})),
    'CARD_BAD_SIGNATURE': ('响应签名错误', step('SUCCESS', '4', {'executeStatus': 'ERROR'})),
}


def upsert(token, base, kind, code, body):
    status, existing = call(f'{kind}-get', 'GET', f'{base}/{kind}/{code}', token, expected=(200, 404))
    if status == 404:
        key = 'accountCode' if kind == 'verify-accounts' else 'caseCode'
        call(f'{kind}-create', 'POST', f'{base}/{kind}', token, {key: code, **body})
    else:
        call(f'{kind}-update', 'PUT', f'{base}/{kind}/{code}', token, {**body, 'expectedVersion': existing['version']})


def run(token, base, code):
    started = call('run-start', 'POST', f'{base}/verify-cases/{code}/runs', token, {'reason': REASON})[1]
    for _ in range(60):
        detail = call('run-get', 'GET', f"{base}/verify-runs/{started['runCode']}", token)[1]
        if detail['runStatus'] != 'RUNNING':
            return detail
        time.sleep(2)
    raise AssertionError('运行超时')


def evidence(detail):
    """请求体证据中的卡号遮蔽文本，并确认全部证据文本没有卡号与 CVV 原值。"""
    text = json.dumps(detail, ensure_ascii=False)
    assert TEST_CARD['pan'] not in text and '"cvv":"123"' not in text.replace('\\"', '"'), '证据包含卡值'
    for step_detail in detail['steps']:
        for node in (step_detail.get('trace') or {}).get('nodes', []):
            body = ((node.get('exchange') or {}).get('request') or {}).get('body', {}).get('text', '')
            card = json.loads(body).get('data', {}).get('paymentDetail', {}).get('cardInfo', {}) if body else {}
            return card
    return {}


def add_test_card(token, base, account):
    """已录入的 UAT 验证账号补充测试卡（保留其余参数与凭据，账号材料不输出）。"""
    current = call('verify-accounts-get', 'GET', f'{base}/verify-accounts/{account}', token)[1]
    credentials = {**current['credentials'], **{'testCard.' + k: v for k, v in TEST_CARD.items()}}
    if credentials == current['credentials']:
        return
    call('verify-accounts-update', 'PUT', f'{base}/verify-accounts/{account}', token, {
        'accountName': current['accountName'], 'channelCode': current['channelCode'], 'channelAccountId': current['channelAccountId'],
        'entityNo': current['entityNo'], 'parameters': current['parameters'], 'credentials': credentials,
        'expectedVersion': current['version'], 'reason': REASON + '：录入 UAT 测试卡'})


def main(mode):
    token = login()
    if mode == 'mock':
        integration, account, cases = 'PAYERMAX_MOCK', 'MOCK_CARD', {**CASES, **MOCK_ONLY}
        keys = json.loads((runtime / 'payermax-mock-keys.json').read_text())
        identity = json.loads((runtime / 'platform.json').read_text())['accounts']['mock']
        base = f'{ADMIN}/integrations/{integration}'
        upsert(token, base, 'verify-accounts', account, {
            'accountName': '本机合成 PayerMax（测试卡）', 'channelCode': identity['channelCode'],
            'channelAccountId': identity['channelAccountId'], 'entityNo': identity['entityNo'],
            'parameters': {k: keys[k] for k in ('appId', 'merchantNo', 'keyVersion', 'payermaxPublicKey')},
            'credentials': {'merchantPrivateKey': keys['merchantPrivateKey'], **{'testCard.' + k: v for k, v in TEST_CARD.items()}},
            'reason': REASON})
    else:
        integration, account, cases = 'PAYERMAX', 'UAT_MAIN', CASES
        base = f'{ADMIN}/integrations/{integration}'
        add_test_card(token, base, account)
    receipt = {}
    for code, (name, steps) in cases.items():
        upsert(token, base, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': account, 'steps': steps,
                                                   'reason': REASON})
        detail = run(token, base, code)
        card = evidence(detail)
        step_detail = detail['steps'][0]
        receipt[code] = {'runCode': detail['runCode'], 'runStatus': detail['runStatus'], 'failureSummary': detail.get('failureSummary'),
                         'executeStatus': (step_detail.get('result') or {}).get('executeStatus'),
                         'outputs': {k: ('[transient]' if k == 'nextAction' else v)
                                     for k, v in ((step_detail.get('result') or {}).get('outputs') or {}).items()},
                         'assertions': [[a['target'], a['passed']] for a in step_detail.get('assertions') or []],
                         'evidenceCardNumber': card.get('cardIdentifierNo'), 'evidenceCvv': card.get('cvv')}
    out = {'date': '2026-10-14', 'mode': mode, 'integration': integration, 'account': account, 'cases': receipt,
           'status': 'PASS' if all(r['runStatus'] == 'PASSED' and r['evidenceCardNumber'] in (MASKED_PAN, None) for r in receipt.values())
           else 'FAIL', 'calls': len(results)}
    (runtime / f'verify-card-{mode}.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({code: [r['runStatus'], r['executeStatus'], r['evidenceCardNumber'], r['failureSummary']]
                      for code, r in receipt.items()}, ensure_ascii=False, indent=1))
    print(out['status'])


if __name__ == '__main__':
    main(sys.argv[1])
