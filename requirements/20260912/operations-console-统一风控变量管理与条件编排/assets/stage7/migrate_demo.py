"""Authorized two-demo migration through public management APIs; never rewrite history."""
from release_support import *
from smoke import Browser
import copy,uuid
from urllib.parse import urlencode

before=json.loads((OUT/'before-demo.json').read_text())
template=json.loads((REQ/'contracts/test-demo-template.json').read_text())
RULE='/decision-rule-sets/v1/rule-sets';FLOW='/decision-flows/v1/flows';VAR='/decision-variables/v1/variables'
report={'startedAt':str(datetime.datetime.now(datetime.timezone.utc)),'events':[],'ruleIdempotencyKey':str(uuid.uuid4()),'flowIdempotencyKey':str(uuid.uuid4())}
def record(step,value):
 report['events'].append({'step':step,'value':value});save('demo-migration.json',report);print(step,flush=True)
def norm(g):return {'schemaVersion':g['schemaVersion'],'nodes':sorted(g['nodes'],key=lambda n:n['nodeKey']),'edges':sorted(g['edges'],key=lambda e:e['edgeKey'])}
def update_body(d,value):return {k:d[k] for k in ['variableName','description','status']}|{'expectedVersion':d['version'],'currentValue':value}
def values(detail):return {v['variableCode']:v.get('currentValue') for v in detail['currentVariables']}
def legacy(v):
 if isinstance(v,list):return [legacy(x) for x in v]
 if isinstance(v,dict):return {k:legacy(x) for k,x in v.items() if not (k in ['variableCode','numericUnit','currencyCode','currencyVariableCode'] and x is None)}
 return v
b=Browser();material=json.loads((ROOT/'test-environment/private/operations-material.json').read_text())
with risk_operations_rollout_lock():
 assert not (OUT/'demo-migration.json').exists(),'Inspect partial migration receipts before retrying'
 b.login(material['administratorEmail'],material['administratorPassword'])
 changed=[]
 try:
  rule=b.request('GET',RULE+'/'+template['originalRuleVersionId'])
  roots=b.request('GET',RULE+'?pointCode=CHECKOUT_PREPARE&ruleSetCode=DEMO_FLOW_AMOUNT_CHECK&page=1&size=10')
  assert roots['total']==1 and roots['items'][0]['ruleSetVersionId']==template['originalRuleVersionId']
  flow=b.request('GET',FLOW+'/'+template['originalFlowId'])
  assert rule['contentHash']==before['rule']['contentHash'] and legacy(rule['rules'])==legacy(before['rule']['rules'])
  assert flow['version']=='1' and norm(flow['graph'])==norm(before['flow']['graph'])
  constants={code:b.request('GET',VAR+'/'+code) for code in ['DEMO_AMOUNT_THRESHOLD','DEMO_STANDARD_CURRENCY']}
  assert constants['DEMO_AMOUNT_THRESHOLD']['currentValue']=='8000.00000000' and constants['DEMO_STANDARD_CURRENCY']['currentValue']=='USD'
  assert all(v['version']=='1' for v in constants.values())
  record('Current baselines confirmed',{'ruleHash':rule['contentHash'],'flowVersion':flow['version']})
  rb=copy.deepcopy(template['ruleRequestTemplate']);rb['baseRuleSetVersionId']=rule['ruleSetVersionId'];rb['baseContentHash']=rule['contentHash']
  report['ruleRequest']=rb;save('demo-migration.json',report)
  saved_rule=b.request('POST',RULE,rb,extra_headers={'Idempotency-Key':report['ruleIdempotencyKey']})
  report['ruleVersion']=saved_rule;record('Saved rule variable-reference version',saved_rule)
  current_rule=b.request('GET',RULE+'/'+saved_rule['ruleSetVersionId'])
  assert current_rule['operandFormat']=='VARIABLE_REFERENCE' and not current_rule['variableIssues']
  assert current_rule['rules'][0]['action'] is None
  flow=b.request('GET',FLOW+'/'+template['originalFlowId']);assert flow['version']=='1' and norm(flow['graph'])==norm(before['flow']['graph'])
  fb=copy.deepcopy(template['flowRequestTemplate']);fb['baseFlowVersionId']=flow['flowVersionId'];fb['expectedVersion']=flow['version']
  for n in fb['graph']['nodes']:
   if n['type']=='RULE_SET':n['ruleSetVersionId']=saved_rule['ruleSetVersionId']
  report['flowRequest']=fb;save('demo-migration.json',report)
  validation=b.request('POST',FLOW+'/validate',fb);assert validation['valid'] and not validation['issues'];record('Flow static validation passed',validation)
  saved_flow=b.request('POST',FLOW,fb,extra_headers={'Idempotency-Key':report['flowIdempotencyKey']})
  report['flowVersion']=saved_flow;record('Saved flow variable-reference version',saved_flow)
  current_flow=b.request('GET',FLOW+'/'+saved_flow['flowId'])
  assert norm(current_flow['graph'])==norm(fb['graph']) and not current_flow['variableIssues']
  assert len(current_flow['graph']['nodes'])==5 and len(current_flow['graph']['edges'])==4
  old_rule=b.request('GET',RULE+'/'+template['originalRuleVersionId'])
  old_flow=b.request('GET',FLOW+'/'+template['originalFlowId']+'/versions/'+template['originalFlowVersionId'])
  assert old_rule['contentHash']==before['rule']['contentHash'] and legacy(old_rule['rules'])==legacy(before['rule']['rules'])
  assert norm(old_flow['graph'])==norm(before['flow']['graph'])
  references={code:b.request('GET',VAR+'/'+code+'/references?scope=ALL') for code in constants}
  assert any(r['consumerType']=='RULE_SET' and r['versionId']==saved_rule['ruleSetVersionId'] for r in references['DEMO_AMOUNT_THRESHOLD']['items'])
  assert any(r['consumerType']=='DECISION_FLOW' and r['versionId']==saved_flow['flowVersionId'] for r in references['DEMO_STANDARD_CURRENCY']['items'])
  record('New graphs, references and original history verified',{'ruleHash':current_rule['contentHash'],'flowGraphSha256':hashlib.sha256(json.dumps(norm(current_flow['graph']),sort_keys=True).encode()).hexdigest(),'references':references})
  for code,new_value in [('DEMO_AMOUNT_THRESHOLD','10000.00000000'),('DEMO_STANDARD_CURRENCY','EUR')]:
   changed_detail=b.request('PUT',VAR+'/'+code,update_body(constants[code],new_value))
   changed.append((code,changed_detail));record('Temporarily changed dedicated demo constant',{'code':code,'value':new_value,'version':changed_detail['version']})
  reread_rule=b.request('GET',RULE+'/'+saved_rule['ruleSetVersionId']);reread_flow=b.request('GET',FLOW+'/'+saved_flow['flowId'])
  assert reread_rule['contentHash']==current_rule['contentHash'] and reread_rule['rules']==current_rule['rules']
  assert reread_flow['flowVersionId']==saved_flow['flowVersionId'] and norm(reread_flow['graph'])==norm(current_flow['graph'])
  assert values(reread_rule)['DEMO_AMOUNT_THRESHOLD']=='10000.00000000'
  assert values(reread_flow)['DEMO_AMOUNT_THRESHOLD']=='10000.00000000' and values(reread_flow)['DEMO_STANDARD_CURRENCY']=='EUR'
  record('Latest values projected without saving rule or flow',{'ruleValues':values(reread_rule),'flowValues':values(reread_flow),'ruleHashUnchanged':True,'flowVersionAndGraphUnchanged':True})
 finally:
  try:
   for code,changed_detail in reversed(changed):
    restored=b.request('PUT',VAR+'/'+code,update_body(changed_detail,constants[code]['currentValue']))
    assert restored['currentValue']==constants[code]['currentValue']
    record('Restored demo constant with own exact CAS',{'code':code,'value':restored['currentValue'],'version':restored['version']})
  finally:b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
 report['completedAt']=str(datetime.datetime.now(datetime.timezone.utc));report['sessionLoggedOut']=True;report['status']='completed';save('demo-migration.json',report)
