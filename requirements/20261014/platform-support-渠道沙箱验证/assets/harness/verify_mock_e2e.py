"""渠道沙箱验证阶段 5：经真实入口（Web 代理 → Gateway → BFF → Platform）对本机合成 PayerMax（接入方 PAYERMAX_MOCK）跑通验证流程。
沿用 PayerMax harness 的 common.py 与运行目录（E2E_RUNTIME）。顺序：出站地址未标沙箱时运行在发请求前失败 → 标记沙箱 →
登记验证账号（合成密钥）→ 两个用例运行通过。合成渠道按订单号末位决定结果：下单末位 1 受理（PENDING），
查询同一订单返回 SUCCESS，末位 9 查询为不存在；UAT 用例在页面上按真实渠道期望（查询 PENDING）编写。回执只含名称、状态与核对结论，不含密钥与报文。"""
import json, sys, time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402

INTEGRATION = 'PAYERMAX_MOCK'
ACCOUNT = 'MOCK_LOCAL'
BASE = f'{ADMIN}/integrations/{INTEGRATION}'
PAY_THEN_QUERY = [
    {'id': 'pay', 'standardApiCode': 'PAY',
     'input': {'channelAccount': '${account.identity}',
               'payment': {'attemptId': 'PMXV${random.digits(12)}1', 'amount': {'value': '10.00', 'currency': 'USD'},
                           'subject': 'Sandbox verify order', 'payerReference': 'payer-ref-verify',
                           'returnUrl': 'https://merchant.local.invalid/return', 'country': 'US'}},
     'expect': {'executeStatus': 'SUCCESS', 'outputs': [
         {'path': 'paymentStatus', 'match': 'EQUALS', 'value': 'PENDING'},
         {'path': 'nextAction.redirectUrl', 'match': 'EXISTS'},
         {'path': 'channelTransactionRef', 'match': 'EXISTS'}]}},
    {'id': 'query', 'standardApiCode': 'PAY_QUERY',
     'input': {'channelAccount': '${account.identity}', 'payment': {'attemptId': '${steps.pay.input.payment.attemptId}'}},
     'expect': {'executeStatus': 'SUCCESS', 'outputs': [
         {'path': 'paymentStatus', 'match': 'EQUALS', 'value': 'SUCCEEDED'},
         {'path': 'channelTransactionRef', 'match': 'EXISTS'}]}},
]
QUERY_NOT_FOUND = [
    {'id': 'query', 'standardApiCode': 'PAY_QUERY',
     'input': {'channelAccount': '${account.identity}', 'payment': {'attemptId': 'PMXV${random.digits(12)}9'}},
     'expect': {'executeStatus': 'FAIL', 'outputs': [
         {'path': 'paymentStatus', 'match': 'EQUALS', 'value': 'NOT_FOUND'},
         {'path': 'failureCode', 'match': 'EQUALS', 'value': 'ORDER_NOT_EXIST'}]}},
]


def set_sandbox(token, sandbox):
    detail = call('integration-get', 'GET', BASE, token)[1]
    endpoints = [{'endpointCode': e['endpointCode'], 'baseUrl': e['baseUrl'], **({'sandbox': True} if sandbox else {})}
                 for e in detail['endpoints']]
    call(f'integration-sandbox-{str(sandbox).lower()}', 'PUT', BASE, token, {
        'integrationName': detail['integrationName'], 'description': detail.get('description'), 'endpoints': endpoints,
        'expectedVersion': detail['version'], 'reason': '沙箱验证本机端到端'})


def upsert(token, kind, code, body):
    status, existing = call(f'{kind}-get', 'GET', f'{BASE}/{kind}/{code}', token, expected=(200, 404))
    if status == 404:
        call(f'{kind}-create', 'POST', f'{BASE}/{kind}', token, {f"{'account' if kind == 'verify-accounts' else 'case'}Code": code, **body})
    else:
        call(f'{kind}-update', 'PUT', f'{BASE}/{kind}/{code}', token, {**body, 'expectedVersion': existing['version']})


def run(token, case_code):
    started = call('run-start', 'POST', f'{BASE}/verify-cases/{case_code}/runs', token, {'reason': '本机合成 PayerMax 回归'})[1]
    for _ in range(60):
        detail = call('run-get', 'GET', f"{BASE}/verify-runs/{started['runCode']}", token)[1]
        if detail['runStatus'] != 'RUNNING':
            return detail
        time.sleep(2)
    raise AssertionError('运行超时')


def summary(detail):
    return {'runCode': detail['runCode'], 'runStatus': detail['runStatus'], 'failureSummary': detail.get('failureSummary'),
            'sandboxEndpoints': [e['sandbox'] for e in detail['endpoints']],
            'steps': [{'stepKey': s['stepKey'], 'stepStatus': s['stepStatus'], 'result': s.get('result'),
                       'assertions': [[a['target'], a['passed']] for a in s.get('assertions') or []]} for s in detail['steps']]}


if __name__ == '__main__':
    token = login()
    keys = json.loads((runtime / 'payermax-mock-keys.json').read_text())
    identity = json.loads((runtime / 'platform.json').read_text())['accounts']['mock']
    upsert(token, 'verify-accounts', ACCOUNT, {
        'accountName': '本机合成 PayerMax', 'channelCode': identity['channelCode'], 'channelAccountId': identity['channelAccountId'],
        'entityNo': identity['entityNo'],
        'parameters': {k: keys[k] for k in ('appId', 'merchantNo', 'keyVersion', 'payermaxPublicKey')},
        'credentials': {'merchantPrivateKey': keys['merchantPrivateKey']}, 'reason': '沙箱验证本机端到端'})
    for code, name, steps in (('PAY_THEN_QUERY', '下单受理后查询（合成渠道返回成功）', PAY_THEN_QUERY),
                              ('QUERY_NOT_FOUND', '查询不存在订单', QUERY_NOT_FOUND)):
        upsert(token, 'verify-cases', code, {'caseName': name, 'description': None, 'accountCode': ACCOUNT, 'steps': steps,
                                             'reason': '沙箱验证本机端到端'})
    receipt = {'date': '2026-10-14', 'integration': INTEGRATION}
    journal = runtime / 'payermax-journal.jsonl'
    traffic = lambda: len(journal.read_text().splitlines()) if journal.exists() else 0
    set_sandbox(token, False)
    before = traffic()
    blocked = run(token, 'QUERY_NOT_FOUND')
    assert blocked['runStatus'] == 'FAILED' and traffic() == before, summary(blocked)
    receipt['notSandbox'] = summary(blocked)
    set_sandbox(token, True)
    for code in ('PAY_THEN_QUERY', 'QUERY_NOT_FOUND'):
        detail = run(token, code)
        assert detail['runStatus'] == 'PASSED', summary(detail)
        receipt[code] = summary(detail)
    cases = call('case-list', 'GET', f'{BASE}/verify-cases?page=1&size=10', token)[1]
    receipt['caseStatus'] = {c['caseCode']: c['verifyStatus'] for c in cases['items']}
    receipt['calls'] = len(results)
    print(json.dumps(receipt, ensure_ascii=False, indent=1))
