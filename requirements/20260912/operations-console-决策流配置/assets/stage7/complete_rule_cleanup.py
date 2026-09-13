"""Continue only after the recorded synthetic flow deletion; no flow deletion replay."""
from deploy import ROOT, HERE
from smoke import Browser
import json, uuid

path=HERE/'cleanup.json';report=json.loads(path.read_text());assert len(report['checks'])==1
assert report['checks'][0]['receipt']['status']=='DELETED'
fixture=json.loads((HERE/'acceptance-fixture.json').read_text());first=fixture['flowVersion'];code=fixture['fixtureCode']
def record(name,**values):
    report['checks'].append({'check':name,**values});path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(name,flush=True)
b=Browser();credentials=json.loads((ROOT/'test-environment/private/operations-material.json').read_text());b.login(credentials['administratorEmail'],credentials['administratorPassword'])
try:
    rule=b.request('GET','/decision-rule-sets/v1/rule-sets?page=1&size=10&pointCode=CHECKOUT_PREPARE&ruleSetCode='+code)
    assert rule['total']==1 and rule['items'][0]['ruleSetId']==report['ruleSetId'] and rule['items'][0]['managementVersion']=='1'
    deleted=b.request('DELETE','/decision-rule-sets/v1/rule-sets/'+report['ruleSetId'],{'expectedVersion':'1'},extra_headers={'Idempotency-Key':str(uuid.uuid4())})
    assert deleted['status']=='DELETED';record('Synthetic rule set deleted through API',receipt=deleted)
    assert b.request('GET','/decision-flows/v1/flows?pointCode=CHECKOUT_PREPARE&flowCode='+code+'&page=1&size=10')['total']==0
    assert b.request('GET','/decision-rule-sets/v1/rule-sets?pointCode=CHECKOUT_PREPARE&ruleSetCode='+code+'&page=1&size=10')['total']==0
    record('Both active lists exclude the synthetic configuration')
    historical=b.request('GET','/decision-flows/v1/flows/'+first['flowId']+'/versions/'+first['flowVersionId'])
    assert historical['flowStatus']=='DELETED' and historical['versionNo']==1
    reference=b.request('GET','/decision-flows/v1/rule-set-reference?sourceType=FLOW_VERSION&sourceVersionId='+first['flowVersionId']+'&nodeKey=clientCheck')
    assert reference['ruleSetStatus']=='DELETED';record('Accurate deleted-flow history and deleted-rule reference preserved')
finally:
    b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
    b.access=b.refresh_token=None
