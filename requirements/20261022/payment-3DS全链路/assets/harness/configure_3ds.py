"""3DS 全链路阶段 4：经真实管理接口（Web 代理 → Gateway → Operations BFF → channel-connector）为 PayerMax 渠道打开动态 3DS。
配置内容见技术方案第 3.3.7 节；只做增量读改写，已有字段与流程其余部分保持不变，已是目标状态时不发起修改。

1. 标准 API PAY：追加可选请求字段 payment.threeDsRequired（BOOLEAN）。修改标准 API 会复核全部接入方的 PAY 流程，
   并使已有沙箱用例变为“需重新验证”（阶段 5 重跑）。
2. 每个给定渠道的原子 API ORDER_AND_PAY（卡直付版）：追加可选请求字段 data.paymentDetail.cardInfo.dynamic3DS。
3. 每个给定渠道的 PAY 流程：createOrder 节点请求增加
   BODY:data.paymentDetail.cardInfo.dynamic3DS = input.payment.threeDsRequired == true ? 'do3DS' : nil
   （要求 3DS 时传 do3DS，否则不传，由 PayerMax 按自身 3DS 服务决定）；先校验再保存。notifyUrl 映射已存在，不改。
4. 每个给定渠道的 PAY_QUERY 流程：只核对已输出 CLOSED（渠道状态透传）与 NOT_FOUND（ORDER_NOT_EXIST）；
   缺少时按 PayerMax 接入 harness configure.py 的 QUERY_FLOW 校验后保存。

前提：渠道已由 PayerMax 接入 harness 的 configure.py 与卡直付 harness 的 configure_card.py 配好（PAY 为卡直付流程）。
用法：E2E_RUNTIME=<运行目录> python3 configure_3ds.py PAYERMAX_MOCK PAYERMAX
本文件只是配置脚本，编写时未对任何环境执行。
"""
import json, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[4] / '20261014/platform-support-PayerMax接入/assets/harness'))
import configure  # noqa: E402
from common import ADMIN, call, login, results, runtime  # noqa: E402
from configure import ORDER_AND_PAY, QUERY_FLOW, expr, field  # noqa: E402

REASON = '3DS 全链路（requirements/20261022/payment-3DS全链路）'
configure.REASON = REASON
FIELD_KEYS = ('fieldLocation', 'fieldPath', 'fieldName', 'dataType', 'required', 'description')

THREE_DS_REQUIRED = field('payment.threeDsRequired', 'BOOLEAN', False)
DYNAMIC_3DS = 'data.paymentDetail.cardInfo.dynamic3DS'
DYNAMIC_3DS_FIELD = field(DYNAMIC_3DS, required=False)
DYNAMIC_3DS_VALUE = expr("input.payment.threeDsRequired == true ? 'do3DS' : nil")


def fields(items):
    return [{k: item.get(k) for k in FIELD_KEYS} for item in items]


def append_field(items, added):
    """返回追加后的字段列表；已存在同一路径时返回 None 表示无需修改。"""
    current = fields(items)
    if any(item['fieldPath'] == added['fieldPath'] for item in current):
        return None
    return current + [added]


def extend_standard_pay(token):
    current = call('standard-api-PAY-get', 'GET', f'{ADMIN}/standard-apis/PAY', token)[1]
    request_fields = append_field(current['requestFields'], THREE_DS_REQUIRED)
    if request_fields is None:
        return False
    call('standard-api-PAY-update', 'PUT', f'{ADMIN}/standard-apis/PAY', token, {
        'standardApiName': current['standardApiName'], 'description': current.get('description'),
        'requestFields': request_fields, 'responseFields': fields(current['responseFields']),
        'expectedVersion': current['version'], 'reason': REASON + '：PAY 增加 payment.threeDsRequired'})
    return True


def extend_order_and_pay(token, integration):
    detail = f'{ADMIN}/integrations/{integration}/apis/{ORDER_AND_PAY}'
    current = call(f'api-{ORDER_AND_PAY}-get', 'GET', detail, token)[1]
    request_fields = append_field(current['requestFields'], DYNAMIC_3DS_FIELD)
    if request_fields is None:
        return False
    call(f'api-{ORDER_AND_PAY}-update', 'PUT', detail, token, {
        'apiName': current['apiName'], 'description': current.get('description'), 'requestFields': request_fields,
        'responseFields': fields(current['responseFields']), 'definition': current['definition'],
        'expectedVersion': current['version'], 'reason': REASON + '：orderAndPay 增加 dynamic3DS'})
    return True


def save_flow(token, integration, code, current, flow, reason):
    """先校验再按当前版本保存流程。"""
    base = f'{ADMIN}/integrations/{integration}/implementations'
    check = call(f'implementation-validate-{code}', 'POST', f'{base}/validate', token,
                 {'standardApiCode': code, 'flowDefinition': flow, 'expectedVersion': current['version']})[1]
    assert check['valid'], (integration, code, check)
    call(f'implementation-{code}-update', 'PUT', f'{base}/{code}', token,
         {'flowDefinition': flow, 'expectedVersion': current['version'], 'reason': reason})


def extend_pay_flow(token, integration):
    current = call('implementation-get-PAY', 'GET', f'{ADMIN}/integrations/{integration}/implementations/PAY', token)[1]
    flow = current['flowDefinition']
    nodes = [node for node in flow['nodes'] if node.get('integrationApi') == ORDER_AND_PAY]
    assert len(nodes) == 1, (integration, '卡直付 PAY 流程应恰有一个 ORDER_AND_PAY 节点', len(nodes))
    key = 'BODY:' + DYNAMIC_3DS
    if nodes[0]['request'].get(key) == DYNAMIC_3DS_VALUE:
        return False
    nodes[0]['request'][key] = DYNAMIC_3DS_VALUE
    save_flow(token, integration, 'PAY', current, flow, REASON + '：要求 3DS 时传 dynamic3DS=do3DS')
    return True


def query_outputs_closed_and_not_found(flow):
    """found 分支透传渠道状态（含 CLOSED），FAIL 分支固定输出 NOT_FOUND。"""
    returns = {item['id']: item for item in flow.get('returns', [])}
    found = returns.get('found', {}).get('outputs', {}).get('paymentStatus', {})
    not_found = returns.get('notFound', {})
    return ('channelStatus' in found.get('expression', '') and not_found.get('executeStatus') == 'FAIL'
            and not_found.get('outputs', {}).get('paymentStatus') == expr("'NOT_FOUND'"))


def ensure_pay_query_flow(token, integration):
    current = call('implementation-get-PAY_QUERY', 'GET', f'{ADMIN}/integrations/{integration}/implementations/PAY_QUERY',
                   token)[1]
    if query_outputs_closed_and_not_found(current['flowDefinition']):
        return False
    save_flow(token, integration, 'PAY_QUERY', current, QUERY_FLOW, REASON + '：PAY_QUERY 输出 CLOSED / NOT_FOUND')
    return True


def configure_all(integrations):
    token = login()
    changes = {'standardApiPay': extend_standard_pay(token)}
    for integration in integrations:
        changes[integration] = {'orderAndPay': extend_order_and_pay(token, integration),
                                'payFlow': extend_pay_flow(token, integration),
                                'payQueryFlow': ensure_pay_query_flow(token, integration)}
    return changes


if __name__ == '__main__':
    codes = sys.argv[1:]
    if not codes:
        raise SystemExit('用法：python3 configure_3ds.py <渠道编码>...（例如 PAYERMAX_MOCK PAYERMAX）')
    changed = configure_all(codes)
    (runtime / f'configure-3ds-{"-".join(codes)}.json').write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'integrations': codes, 'changed': changed, 'calls': len(results)}, ensure_ascii=False))
