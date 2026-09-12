"""User-requested sample configuration through the real test Operations API."""
from pathlib import Path
import copy
import datetime
import json
import sys
import time
import uuid

ROOT = Path('/Users/kenny/Desktop/Workspace/Code-MoR')
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'test-environment/scripts'))
from smoke import Browser

OUTPUT = HERE / 'demo-data.json'
assert not OUTPUT.exists(), 'Inspect existing sample records before attempting another creation.'
report = {'createdAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'environment': 'http://localhost:28080', 'purpose': 'User-requested editable demo configurations',
          'writes': [], 'ruleSets': [], 'activated': False}
browser = Browser()

def persist():
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')

def write(path, body, command=False):
    headers = {'Idempotency-Key': str(uuid.uuid4())} if command else None
    result = browser.request('POST', path, body, extra_headers=headers)
    report['writes'].append({'path': path, 'body': body, 'result': result})
    persist()
    time.sleep(1)
    return result

def predicate(field, operator, values):
    kind = fields[field]['valueType']
    operands = [{'operandRole': 'LEFT', 'valueSequence': 1, 'sourceType': 'CONTEXT',
                 'fieldCode': field, 'valueType': kind, 'literalValue': None}]
    operands += [{'operandRole': 'RIGHT', 'valueSequence': i + 1, 'sourceType': 'LITERAL',
                  'fieldCode': None, 'valueType': kind, 'literalValue': value} for i, value in enumerate(values)]
    return [{'nodeKey': '1', 'parentNodeKey': None, 'sequenceNo': 1, 'nodeType': 'PREDICATE',
             'groupOperator': None, 'comparisonOperator': operator, 'operands': operands}]

def rule(code, name, priority, field, operator, values, terminal, reason=None, tag=None):
    additional = []
    if tag:
        additional = [{'sequenceNo': 1, 'actionCode': 'ADD_RISK_TAG',
                       'actionVersionNo': actions['ADD_RISK_TAG']['versionNo'], 'targetBindingCode': binding,
                       'parameters': [{'parameterCode': 'TAG', 'valueSequence': 1, 'sourceType': 'LITERAL',
                                       'fieldCode': None, 'valueType': 'STRING', 'literalValue': tag}]}]
    return {'ruleCode': code, 'ruleName': name, 'priorityNo': priority, 'enabledFlag': True,
            'reasonCode': reason, 'reasonMessage': reasons[reason]['defaultMessage'] if reason else None,
            'additionalActions': additional, 'conditionNodes': predicate(field, operator, values),
            'action': {'actionCode': terminal, 'actionVersionNo': actions[terminal]['versionNo']}}

material = json.loads((ROOT / 'test-environment/private/operations-material.json').read_text())
browser.login(material['administratorEmail'], material['administratorPassword'])
try:
    context = browser.request('GET', '/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE')
    fields = {x['fieldCode']: x for x in context['fields']}
    actions = {x['actionCode']: x for x in context['actions']}
    binding = next(x['bindingCode'] for x in context['targetBindings'] if x['subjectType'] == 'CLIENT')
    reasons = {code: {'reasonCode': code, 'name': name, 'description': 'DEMO 示例数据，供页面配置和人工验证。',
                      'defaultMessage': message} for code, name, message in [
        ('DEMO_AMOUNT_LIMIT', '示例：订单金额超限', '示例订单金额超过当前配置阈值，请人工确认。'),
        ('DEMO_REGION_RESTRICTED', '示例：地区准入限制', '示例地区代码不在当前支持范围内。'),
        ('DEMO_DISCOUNT_ANOMALY', '示例：折扣金额异常', '示例折扣金额超过当前配置阈值，请人工确认。')]}
    for body in reasons.values():
        write('/decision-dictionaries/v1/rejection-reasons', body)
    for code, name in [('DEMO_HIGH_VALUE', '示例：大额订单关注'),
                       ('DEMO_REGION_REVIEW', '示例：地区准入关注'),
                       ('DEMO_DISCOUNT_REVIEW', '示例：折扣人工核查')]:
        write('/decision-dictionaries/v1/subject-tags', {'tagCode': code, 'name': name,
              'description': 'DEMO 标签定义；规则示例配置目标为当前 Client，仅供人工验证。'})
    specs = [
        ('DEMO_AMOUNT_GUARD', '示例：大额订单与折扣检查', [
            rule('HIGH_AMOUNT', '订单金额超过阈值时拒绝', 10, 'net_amount', 'GT', ['10000'], 'DENY_CHECKOUT', 'DEMO_AMOUNT_LIMIT', 'DEMO_HIGH_VALUE'),
            rule('DISCOUNT_REVIEW', '折扣金额较高时人工复核', 20, 'discount_amount', 'GT', ['500'], 'REVIEW_CHECKOUT', tag='DEMO_DISCOUNT_REVIEW')]),
        ('DEMO_REGION_GUARD', '示例：地区准入检查', [
            rule('REGION_DENY', '演示地区代码命中时拒绝', 10, 'country_code', 'IN', ['ZZ'], 'DENY_CHECKOUT', 'DEMO_REGION_RESTRICTED', 'DEMO_REGION_REVIEW')]),
        ('DEMO_USD_ALLOW', '示例：美元订单放行', [
            rule('USD_ALLOW', '美元订单允许结账', 10, 'currency', 'EQ', ['USD'], 'ALLOW_CHECKOUT')])]
    for code, name, rules in specs:
        existing = browser.request('GET', '/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode=' + code)
        assert existing['total'] == 0, 'Demo rule set already exists; do not overwrite it.'
        body = {'pointCode': 'CHECKOUT_PREPARE', 'ruleSetCode': code, 'ruleSetName': name,
                'baseRuleSetVersionId': None, 'baseContentHash': None,
                'changeReason': '创建 DEMO 初始配置，供人工查看详情与编辑。', 'rules': rules}
        first = write('/decision-rule-sets/v1/rule-sets', body, True)
        if code == 'DEMO_USD_ALLOW':
            write('/decision-rule-sets/v1/rule-sets/' + first['ruleSetVersionId'] + '/freeze',
                  {'expectedContentHash': first['contentHash']}, True)
            report['draftRelease'] = write('/decision-releases/v1/candidates',
                {'ruleSetVersionId': first['ruleSetVersionId'], 'expectedRuleSetContentHash': first['contentHash']}, True)
        second_body = copy.deepcopy(body)
        second_body.update(baseRuleSetVersionId=first['ruleSetVersionId'], baseContentHash=first['contentHash'])
        if code == 'DEMO_AMOUNT_GUARD':
            second_body['rules'][0]['conditionNodes'] = predicate('net_amount', 'GT', ['8000'])
            second_body['changeReason'] = 'DEMO 历史示例：金额拒绝阈值由 10000 调整为 8000。'
        elif code == 'DEMO_REGION_GUARD':
            second_body['rules'][0]['conditionNodes'] = predicate('country_code', 'IN', ['ZZ', 'XX'])
            second_body['changeReason'] = 'DEMO 历史示例：新增演示地区代码 XX；ZZ/XX 均为演示占位值。'
        else:
            second_body['rules'][0]['priorityNo'] = 20
            second_body['rules'].insert(0, rule('DISCOUNT_DENY', '折扣异常先拒绝', 10, 'discount_amount', 'GT', ['1000'], 'DENY_CHECKOUT', 'DEMO_DISCOUNT_ANOMALY'))
            second_body['changeReason'] = 'DEMO 历史示例：新增高优先级折扣异常拒绝；保留旧冻结版本的草稿发布引用。'
        second = write('/decision-rule-sets/v1/rule-sets', second_body, True)
        rows = browser.request('GET', '/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode=' + code)
        assert rows['total'] == 1
        root = rows['items'][0]
        history = browser.request('GET', '/decision-rule-sets/v1/rule-sets/' + root['ruleSetId'] + '/versions?page=1&size=10&maxVersionNo=2')
        assert history['total'] == 2
        report['ruleSets'].append({'code': code, 'name': name, 'ruleSetId': root['ruleSetId'],
                                  'versionIds': [first['ruleSetVersionId'], second['ruleSetVersionId']],
                                  'currentVersion': root['versionNo'], 'currentStatus': root['status']})
        persist()
        print(code + ': 2 versions created', flush=True)
    report['status'] = 'CREATED_AND_RETAINED_FOR_USER'
    persist()
finally:
    browser.request('POST', '/auth/v1/logout', {'refreshToken': browser.refresh_token}, status=(200, 204))
