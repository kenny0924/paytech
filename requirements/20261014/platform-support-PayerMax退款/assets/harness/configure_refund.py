"""PayerMax 退款阶段 2：经真实管理接口保存（存在则按版本修改）标准 API REFUND、REFUND_QUERY 与一个 PayerMax 接入方下的
REFUND、REFUND_QUERY 原子 API 及两条流程实现。配置内容见技术方案第 3、4 节。

前提：接入方、出站地址、签名/验签脚本已由 PayerMax 接入 harness 的 configure.py 保存；本脚本复用其字段与外层定义，不修改它们。

用法：python3 configure_refund.py <接入方编码>（PAYERMAX_MOCK 或 PAYERMAX）
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'platform-support-PayerMax接入/assets/harness'))
from common import ADMIN, call, login, results, runtime  # noqa: E402
import configure  # noqa: E402
from configure import (CHANNEL_ACCOUNT, ENVELOPE, ENVELOPE_VALUES, FAIL_OUTPUTS, INITIALIZER, NODE_PARAMS, OK, const, definition,  # noqa: E402
                       expr, field, output, path, status, upsert)

REASON = 'PayerMax 退款（requirements/20261014）'
# 明确未创建退款的错误码（技术方案第 4.1 节，评审点 R2）；SYSTEM_ERROR、REQUEST_TIMEOUT、UNEXPECTED_ERROR 与未知码归 ERROR。
REJECT_CODES = ['PARAMS_INVALID', 'REQUEST_PARAM_INVALID', 'AMOUNT_INVALID', 'CURRENCY_INVALID', 'SIGN_VERIFY_FAILED', 'MERCHANT_INVALID',
                'MERCHANT_APP_INVALID', 'ORDER_NOT_EXIST', 'REFUND_FAILED', 'REFUND_NO_INVALID', 'REFUND_INSUFFICIENT_BALANCE',
                'RDR_REFUNDED_NOT_SUPPORT_RETRY']
REFUND_STATUSES = ('REFUND_PENDING', 'REFUND_SUCCESS', 'REFUND_FAILED')
REFUND_VERSION = '1.1'  # 评审点 R1：退款文档当前版本

REFUND_REQUEST = CHANNEL_ACCOUNT + [
    field('refund', 'OBJECT'), field('refund.refundId'), field('refund.paymentAttemptId'), field('refund.amount', 'OBJECT'),
    field('refund.amount.value', 'DECIMAL'), field('refund.amount.currency'), field('refund.reason', required=False),
    field('refund.notifyUrl', required=False)]
REFUND_RESPONSE = [field('refundStatus'), field('channelRefundRef', required=False), field('channelStatus', required=False),
                   field('failureCode', required=False), field('failureMessage', required=False)]
QUERY_REQUEST = CHANNEL_ACCOUNT + [field('refund', 'OBJECT'), field('refund.refundId')]
QUERY_RESPONSE = REFUND_RESPONSE + [field('amount', 'OBJECT', False), field('amount.value', 'DECIMAL', False),
                                    field('amount.currency', required=False)]

API_REFUND_REQUEST = ENVELOPE + [
    field('data.outRefundNo'), field('data.refundAmount', 'DECIMAL'), field('data.refundCurrency'), field('data.outTradeNo'),
    field('data.comments', required=False), field('data.refundNotifyUrl', required=False)]
API_REFUND_RESPONSE = [field('code'), field('msg', required=False), field('data', 'OBJECT', False), field('data.outRefundNo', required=False),
                       field('data.refundTradeNo', required=False), field('data.tradeOrderNo', required=False),
                       field('data.status', required=False)]
API_QUERY_REQUEST = ENVELOPE + [field('data.outRefundNo')]
API_QUERY_RESPONSE = [field('code'), field('msg', required=False), field('data', 'OBJECT', False), field('data.outRefundNo', required=False),
                      field('data.refundTradeNo', required=False), field('data.refundAmount', 'DECIMAL', False),
                      field('data.refundCurrency', required=False), field('data.outTradeNo', required=False),
                      field('data.status', required=False), field('data.resultMsg', required=False)]

KNOWN_STATUS = '(' + ' || '.join(f"http.responseBody.data.status == '{s}'" for s in REFUND_STATUSES) + ')'


def refund_definition(path_template, status_rules, outputs):
    value = definition(path_template, status_rules, outputs, {})
    value['fixedValues'] = {'BODY:version': const(REFUND_VERSION)}
    return value


API_REFUND_DEFINITION = refund_definition('/refund', [
    {'condition': f'{OK} && {KNOWN_STATUS} && http.responseBody.data.refundTradeNo != nil', 'executeStatus': 'SUCCESS'},
    {'condition': 'http.responseStatus == 200 && (' + ' || '.join(f"http.responseBody.code == '{code}'" for code in REJECT_CODES) + ')',
     'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('refundTradeNo', path('http.responseBody.data.refundTradeNo')),
                 output('channelStatus', path('http.responseBody.data.status'))],
     'FAIL': FAIL_OUTPUTS})
API_QUERY_DEFINITION = refund_definition('/refundQuery', [
    {'condition': f'{OK} && {KNOWN_STATUS}', 'executeStatus': 'SUCCESS'},
    {'condition': "http.responseStatus == 200 && http.responseBody.code == 'ORDER_NOT_EXIST'", 'executeStatus': 'FAIL'}],
    {'SUCCESS': [output('channelStatus', path('http.responseBody.data.status')),
                 output('refundTradeNo', path('http.responseBody.data.refundTradeNo'), required=False),
                 output('amount', path('http.responseBody.data.refundAmount'), 'DECIMAL', False),
                 output('currency', path('http.responseBody.data.refundCurrency'), required=False),
                 output('resultMsg', path('http.responseBody.data.resultMsg'), required=False)],
     'FAIL': FAIL_OUTPUTS})


def refund_status(channel_status):
    """REFUND_SUCCESS → SUCCEEDED、REFUND_FAILED → FAILED，其余已知状态（REFUND_PENDING）→ PENDING。"""
    return expr(f"{channel_status} == 'REFUND_SUCCESS' ? 'SUCCEEDED' : ({channel_status} == 'REFUND_FAILED' ? 'FAILED' : 'PENDING')")


def flow(node, api, request, success_outputs, fail_status, timeout_ms):
    out = f'steps.{node}.outputs.'
    return {
        'timeoutMs': timeout_ms, 'initializers': INITIALIZER, 'entry': node,
        'nodes': [{'id': node, 'integrationApi': api, 'params': NODE_PARAMS, 'request': {**ENVELOPE_VALUES, **request}}],
        'returns': [
            {'id': 'accepted' if api == 'REFUND' else 'found', 'executeStatus': 'SUCCESS', 'outputs': success_outputs(out)},
            {'id': 'rejected' if api == 'REFUND' else 'notFound', 'executeStatus': 'FAIL', 'outputs': {
                'refundStatus': const(fail_status), 'failureCode': path(out + 'errorCode'), 'failureMessage': path(out + 'errorMessage')}},
            {'id': 'unknown', 'executeStatus': 'ERROR'}],
        'edges': [{'from': node, 'to': 'accepted' if api == 'REFUND' else 'found', 'condition': status(node, 'SUCCESS'), 'priority': 1},
                  {'from': node, 'to': 'rejected' if api == 'REFUND' else 'notFound', 'condition': status(node, 'FAIL'), 'priority': 2},
                  {'from': node, 'to': 'unknown', 'default': True}],
    }


REFUND_FLOW = flow('applyRefund', 'REFUND', {
    'BODY:data.outRefundNo': path('input.refund.refundId'), 'BODY:data.outTradeNo': path('input.refund.paymentAttemptId'),
    'BODY:data.refundAmount': path('input.refund.amount.value'), 'BODY:data.refundCurrency': path('input.refund.amount.currency'),
    'BODY:data.comments': path('input.refund.reason'), 'BODY:data.refundNotifyUrl': path('input.refund.notifyUrl')},
    lambda out: {'refundStatus': refund_status(out + 'channelStatus'), 'channelRefundRef': path(out + 'refundTradeNo'),
                 'channelStatus': path(out + 'channelStatus')},
    'FAILED', 25000)
QUERY_FLOW = flow('queryRefund', 'REFUND_QUERY', {'BODY:data.outRefundNo': path('input.refund.refundId')},
                  lambda out: {'refundStatus': refund_status(out + 'channelStatus'), 'channelRefundRef': path(out + 'refundTradeNo'),
                               'channelStatus': path(out + 'channelStatus'), 'amount.value': path(out + 'amount'),
                               'amount.currency': path(out + 'currency'),
                               'failureMessage': expr(f"{out}resultMsg == '' ? nil : {out}resultMsg")},
                  'NOT_FOUND', 15000)


configure.REASON = REASON  # upsert 写入的修改原因


def save(token, name, detail_path, create_path, create_body, update_body):
    return upsert(token, name, detail_path, create_path, create_body, update_body)


def configure_refund(integration):
    token = login()
    for code, label, request, response in (('REFUND', '退款', REFUND_REQUEST, REFUND_RESPONSE),
                                           ('REFUND_QUERY', '退款查询', QUERY_REQUEST, QUERY_RESPONSE)):
        body = {'standardApiName': label, 'description': '渠道无关标准 API（草案，PayerMax 退款）', 'requestFields': request,
                'responseFields': response}
        save(token, 'standard-api-' + code, f'{ADMIN}/standard-apis/{code}', f'{ADMIN}/standard-apis', {'standardApiCode': code, **body}, body)
    base = f'{ADMIN}/integrations/{integration}'
    for code, name, request, response, definition_json in (
            ('REFUND', '退款申请 refund', API_REFUND_REQUEST, API_REFUND_RESPONSE, API_REFUND_DEFINITION),
            ('REFUND_QUERY', '退款查询 refundQuery', API_QUERY_REQUEST, API_QUERY_RESPONSE, API_QUERY_DEFINITION)):
        body = {'apiName': name, 'description': None, 'requestFields': request, 'responseFields': response, 'definition': definition_json}
        save(token, 'api-' + code, f'{base}/apis/{code}', f'{base}/apis', {'apiCode': code, **body}, body)
    for code, flow_definition in (('REFUND', REFUND_FLOW), ('REFUND_QUERY', QUERY_FLOW)):
        found, current = call('implementation-get-' + code, 'GET', f'{base}/implementations/{code}', token, expected=(200, 404))
        check = call('implementation-validate-' + code, 'POST', f'{base}/implementations/validate', token,
                     {'standardApiCode': code, 'flowDefinition': flow_definition,
                      'expectedVersion': current['version'] if found == 200 else None})[1]
        assert check['valid'], check
        save(token, 'implementation-' + code, f'{base}/implementations/{code}', f'{base}/implementations',
             {'standardApiCode': code, 'flowDefinition': flow_definition}, {'flowDefinition': flow_definition})


if __name__ == '__main__':
    integration_code = sys.argv[1]
    configure_refund(integration_code)
    (runtime / f'configure-refund-{integration_code}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integration': integration_code, 'calls': len(results),
                      'saved': [r['name'] for r in results if r['name'].endswith(('-create', '-update'))]}, ensure_ascii=False))
