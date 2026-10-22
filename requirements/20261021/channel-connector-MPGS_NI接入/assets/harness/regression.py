"""其他接入方回归（AC5）：PAY 标准 API 追加可选字段后，按原用例重跑。7 个查询类用例与统一出口第一段 mor-test 验收 B 相同；
dLocal SBX_PAY_PENDING_CANCEL 经修改后的 PAY 标准 API 下单并撤销（不需付款，订单终态 CANCELLED）。不修改用例。

用法：MPGS_RUNTIME=<运行目录> python3 regression.py
"""
import json, time

from mortest import ADMIN, call, login, runtime

CASES = [('PAYERMAX', 'UAT_QUERY_NOT_FOUND'), ('PAYERMAX', 'UAT_REFUND_QUERY_NOT_FOUND'), ('PAYERMAX', 'UAT_REFUND_NOT_EXIST'),
         ('PAYBY', 'UAT_PAY_QUERY_NOT_FOUND'), ('DLOCAL', 'SBX_PAY_QUERY_NOT_FOUND'), ('ALIPAY_PLUS', 'SBX_PAY_QUERY_NOT_FOUND'),
         ('TOSS_PAY', 'SBX_PAY_QUERY_NOT_FOUND'), ('DLOCAL', 'SBX_PAY_PENDING_CANCEL')]
REASON = 'MPGS（NI）接入后回归'

if __name__ == '__main__':
    token = login()
    out = []
    for integration, case in CASES:
        base = f'{ADMIN}/integrations/{integration}'
        started = call('run-start', 'POST', f'{base}/verify-cases/{case}/runs', token, {'reason': REASON})[1]
        for _ in range(90):
            detail = call('run-get', 'GET', f"{base}/verify-runs/{started['runCode']}", token)[1]
            if detail['runStatus'] != 'RUNNING':
                break
            time.sleep(2)
        out.append({'integration': integration, 'case': case, 'runCode': detail['runCode'], 'runStatus': detail['runStatus'],
                    'failureSummary': detail.get('failureSummary'),
                    'steps': [[s['stepKey'], s['stepStatus']] for s in detail['steps']]})
    (runtime / 'regression.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
    for item in out:
        print(item['integration'], item['case'], item['runStatus'], item['failureSummary'], item['steps'])
