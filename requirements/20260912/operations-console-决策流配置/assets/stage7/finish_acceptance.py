"""Validate the existing two-version fixture, or clean up only its exact recorded business IDs."""
from deploy import ROOT, HERE, rows
from smoke import Browser
import argparse, json, uuid

parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['verify','cleanup']);args=parser.parse_args()
fixture=json.loads((HERE/'acceptance-fixture.json').read_text());first=fixture['flowVersion'];code=fixture['fixtureCode']
output=HERE/('http-acceptance.json' if args.phase=='verify' else 'cleanup.json')
assert not output.exists(),'Inspect recorded result before repeating a phase'
report={'phase':args.phase,'flowId':first['flowId'],'ruleSetId':fixture['ruleRoot']['ruleSetId'],'checks':[]}
def record(name,**values):
    report['checks'].append({'check':name,**values});output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(name,flush=True)
b=Browser();credentials=json.loads((ROOT/'test-environment/private/operations-material.json').read_text());b.login(credentials['administratorEmail'],credentials['administratorPassword'])
try:
    current=b.request('GET','/decision-flows/v1/flows/'+first['flowId'])
    assert current['flowCode']==code and current['versionNo']==2 and current['version']=='2'
    assert current['flowName']=='决策流部署验收第二版'
    assert len(current['graph']['nodes'])==4 and all(n['name']!='未连接结束验收' for n in current['graph']['nodes'])
    if args.phase=='verify':
        history=b.request('GET','/decision-flows/v1/flows/'+first['flowId']+'/versions?maxVersionNo=2&page=1&size=10')
        assert history['total']==2;record('Exactly two versions saved; failed graph added no version',history=history)
        old=b.request('GET','/decision-flows/v1/flows/'+first['flowId']+'/versions/'+first['flowVersionId'])
        assert old['flowName']=='决策流部署验收' and old['versionNo']==1;record('Version one remains unchanged')
        body={**fixture['flowBody'],'baseFlowVersionId':first['flowVersionId'],'expectedVersion':'1','changeReason':'RCO17 test 过期版本冲突检查'}
        conflict=b.request('POST','/decision-flows/v1/flows',body,extra_headers={'Idempotency-Key':str(uuid.uuid4())},status=409)
        record('One stale-version save rejected with HTTP 409',problem=conflict)
        anonymous=Browser();problem=anonymous.request('GET','/decision-flows/v1/flows?pointCode=CHECKOUT_PREPARE&page=1&size=10',authenticated=False,status=401)
        record('Anonymous flow list rejected with HTTP 401',code=problem.get('code'))
        rule=b.request('GET','/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode='+code)
        assert rule['total']==1 and rule['items'][0]['pointVersionId'] is None
        record('One row per new rule root; no synthetic legacy Point ID')
    else:
        # Flow DELETE has a zero-byte body contract; Browser.request would synthesize {}.
        deleted=b.request_raw('DELETE','/decision-flows/v1/flows/'+first['flowId']+'?expectedVersion=2',None,extra_headers={'Idempotency-Key':str(uuid.uuid4())})
        assert deleted['status']=='DELETED';record('Synthetic flow deleted through API',receipt=deleted)
        rule=b.request('GET','/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode='+code)
        assert rule['total']==1 and rule['items'][0]['ruleSetId']==report['ruleSetId']
        deleted_rule=b.request('DELETE','/decision-rule-sets/v1/rule-sets/'+report['ruleSetId'],{'expectedVersion':rule['items'][0]['managementVersion']},extra_headers={'Idempotency-Key':str(uuid.uuid4())})
        assert deleted_rule['status']=='DELETED';record('Synthetic rule set deleted through API',receipt=deleted_rule)
        page=b.request('GET','/decision-flows/v1/flows?pointCode=CHECKOUT_PREPARE&flowCode='+code+'&page=1&size=10')
        assert page['total']==0
        page=b.request('GET','/decision-rule-sets/v1/rule-sets?pointCode=CHECKOUT_PREPARE&ruleSetCode='+code+'&page=1&size=10')
        assert page['total']==0;record('Both active lists exclude the synthetic configuration')
        historical=b.request('GET','/decision-flows/v1/flows/'+first['flowId']+'/versions/'+first['flowVersionId'])
        assert historical['flowStatus']=='DELETED' and historical['versionNo']==1
        reference=b.request('GET','/decision-flows/v1/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId='+first['flowVersionId']+'&nodeKey=clientCheck')
        assert reference['ruleSetStatus']=='DELETED';record('Accurate deleted-flow history and deleted-rule reference preserved')
finally:
    b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
    b.access=b.refresh_token=None
