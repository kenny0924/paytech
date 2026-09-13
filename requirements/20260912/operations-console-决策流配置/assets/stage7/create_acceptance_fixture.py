"""Authorized bounded test configuration: exactly one new rule set and one flow first version."""
from deploy import ROOT, HERE, REQ
from smoke import Browser
import datetime, json, uuid

path=HERE/'acceptance-fixture.json'
assert not path.exists(),'Inspect saved fixture before any additional write'
report={'environment':'http://localhost:28080','events':[],'fixtureCode':'RCO17_TEST_'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S')}
def record(name,value):
    report['events'].append({'step':name,'value':value})
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(name,flush=True)
b=Browser();credentials=json.loads((ROOT/'test-environment/private/operations-material.json').read_text())
b.login(credentials['administratorEmail'],credentials['administratorPassword'])
try:
    context=b.request('GET','/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE')
    action=next(a for a in context['actions'] if a['actionCode']=='ALLOW_CHECKOUT')
    field=next(f for f in context['fields'] if f['fieldCode']=='currency')
    code=report['fixtureCode']
    body={'pointCode':'CHECKOUT_PREPARE','ruleSetCode':code,'ruleSetName':'决策流部署验收规则','baseRuleSetVersionId':None,'baseContentHash':None,'changeReason':'RCO17 test 发布验收，结束后删除合成配置','rules':[{'ruleCode':'CURRENCY_CHECK','ruleName':'美元订单条件示例','priorityNo':10,'enabledFlag':True,'reasonCode':None,'reasonMessage':None,'additionalActions':[],'conditionNodes':[{'nodeKey':'currency','parentNodeKey':None,'sequenceNo':1,'nodeType':'PREDICATE','groupOperator':None,'comparisonOperator':'EQ','operands':[{'operandRole':'LEFT','valueSequence':1,'sourceType':'CONTEXT','fieldCode':'currency','valueType':field['valueType'],'literalValue':None},{'operandRole':'RIGHT','valueSequence':1,'sourceType':'LITERAL','fieldCode':None,'valueType':field['valueType'],'literalValue':'USD'}]}],'action':{'actionCode':'ALLOW_CHECKOUT','actionVersionNo':action['versionNo']}}]}
    assert b.request('GET','/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode='+code)['total']==0
    result=b.request('POST','/decision-rule-sets/v1/rule-sets',body,extra_headers={'Idempotency-Key':str(uuid.uuid4())})
    report['ruleBody']=body;report['ruleVersion']=result;record('Created one rule version',result)
    root=b.request('GET','/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode='+code)
    assert root['total']==1 and root['items'][0]['pointVersionId'] is None
    report['ruleRoot']=root['items'][0]
    example=json.loads((REQ/'contracts/save-flow.example.json').read_text())
    nodes=[n for n in example['graph']['nodes'] if n['nodeKey'] in ['start','clientCheck','review','allow']]
    for node in nodes:
        if node['nodeKey']=='clientCheck':node.update(name='币种检查',ruleSetVersionId=result['ruleSetVersionId'],position={'x':'300','y':'100'})
        if node['nodeKey']=='start':node.update(name='开始',position={'x':'0','y':'100'})
        if node['nodeKey']=='review':node['position']={'x':'600','y':'0'}
        if node['nodeKey']=='allow':node['position']={'x':'600','y':'250'}
    edges=[e for e in example['graph']['edges'] if e['edgeKey'] in ['startCheck','clientMatched','clientElse']]
    for edge in edges:
        if edge['edgeKey']=='clientMatched':edge.update(targetNodeKey='review',name='命中转人工复核')
        if edge['edgeKey']=='clientElse':edge['targetNodeKey']='allow'
    flow={'pointCode':'CHECKOUT_PREPARE','flowCode':code,'flowName':'决策流部署验收','baseFlowVersionId':None,'expectedVersion':None,'changeReason':'合成配置：条件分支与提前结束验收','graph':{'schemaVersion':1,'nodes':nodes,'edges':edges}}
    validation=b.request('POST','/decision-flows/v1/flows/validate',flow)
    assert validation['valid'];record('Static validation passed',validation)
    key=str(uuid.uuid4());saved=b.request('POST','/decision-flows/v1/flows',flow,extra_headers={'Idempotency-Key':key})
    report['flowBody']=flow;report['flowVersion']=saved;record('Created one flow version',saved)
    replay=b.request('POST','/decision-flows/v1/flows',flow,extra_headers={'Idempotency-Key':key})
    assert replay==saved;record('Same-key replay preserved first version',replay)
    detail=b.request('GET','/decision-flows/v1/flows/'+saved['flowId'])
    # Persistence sorts identities; business branch order is the explicit edge priority.
    assert detail['graph']['schemaVersion']==flow['graph']['schemaVersion']
    for collection,key_name in [('nodes','nodeKey'),('edges','edgeKey')]:
        assert {x[key_name]:x for x in detail['graph'][collection]}=={x[key_name]:x for x in flow['graph'][collection]}
    record('Current configuration matches submitted graph',{'flowId':saved['flowId'],'version':detail['version']})
    reference=b.request('GET','/decision-flows/v1/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId='+saved['flowVersionId']+'&nodeKey=clientCheck')
    assert reference['ruleSet']['ruleSetVersionId']==result['ruleSetVersionId'];record('Exact rule reference readable',{'ruleSetVersionId':result['ruleSetVersionId']})
    record('Fixture ready for browser verification',{'editPath':'/risk/decision-flows/'+saved['flowId']+'/edit'})
finally:
    b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
    b.access=b.refresh_token=None
