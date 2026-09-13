from release_support import *
from smoke import Browser
from gateway_snapshot_wait import ComposeFetcher,GATEWAY_METRICS_URL
from gateway_snapshot_metrics import decode_prometheus
from dataclasses import asdict
from urllib.parse import quote
import http.client
initial=json.loads((OUT/'preflight.json').read_text());images=json.loads((OUT/'images-body-limit.json').read_text())
migration=json.loads((OUT/'demo-migration.json').read_text());assert migration['status']=='completed' and images['completedAt']
after=containers();assert len(after)==11 and all(c['health']=='healthy' for c in after)
for service,item in images['images'].items():assert next(c for c in after if c['name']=='mor-test-'+service+'-1')['image']==item['imageId']
unchanged=[]
for old in initial['containers']:
 if old['name'] not in ['mor-test-'+s+'-1' for s in images['images']]:assert next(c for c in after if c['name']==old['name'])==old;unchanged.append(old['name'])
snapshot=decode_prometheus(ComposeFetcher()(GATEWAY_METRICS_URL,12))
assert snapshot.valid and snapshot.refresh_result=='SUCCESS' and snapshot.failure_reason=='NONE' and not snapshot.last_valid_in_use and snapshot.operations_route_count==70
report={'at':str(datetime.datetime.now(datetime.timezone.utc)),'containers':after,'unchangedContainers':unchanged,'snapshot':asdict(snapshot),'checks':[]}
b=Browser();material=json.loads((ROOT/'test-environment/private/operations-material.json').read_text())
b.login(material['administratorEmail'],material['administratorPassword'])
try:
 navigation=b.request('GET','/authorization/v1/navigation');assert any(r['resourceCode']=='OPERATIONS_RISK_VARIABLES' for r in navigation['resources'])
 context=b.request('GET','/decision-variables/v1/authoring-context');assert context['limits']['memberBodyBytes']==65536
 catalog=b.request('GET','/decision-variables/v1/variables?page=1&size=100');assert catalog['total']==17
 search=b.request('GET','/decision-variables/v1/variables?keyword='+quote('实付金额')+'&page=1&size=20');assert any(v['variableCode']=='INPUT_NET_AMOUNT' for v in search['items'])
 oversized='{"expectedVersion":"999999999","members":[]}'
 oversized=(oversized+' '*(65537-len(oversized))).encode()
 problem=b.request_raw('PUT','/decision-variables/v1/variables/RESTRICTED_COUNTRIES/members',oversized,status=413)
 members=b.request('GET','/decision-variables/v1/variables/RESTRICTED_COUNTRIES/members?page=1&size=100');assert members['total']==3 and members['version']=='1'
 rule=b.request('GET','/decision-rule-sets/v1/rule-sets/'+migration['ruleVersion']['ruleSetVersionId'])
 flow=b.request('GET','/decision-flows/v1/flows/'+migration['flowVersion']['flowId'])
 assert rule['versionNo']==2 and flow['versionNo']==2 and rule['operandFormat']=='VARIABLE_REFERENCE'
 for d in [rule,flow]:assert not d['variableIssues']
 values={v['variableCode']:v.get('currentValue') for v in flow['currentVariables']}
 assert values['DEMO_AMOUNT_THRESHOLD']=='8000.00000000' and values['DEMO_STANDARD_CURRENCY']=='USD'
 report['checks']=['administrator navigation contains variable menu','17 variables available through Gateway/BFF/Risk','Chinese variable search succeeds','management context reports 65536 member bytes','65537-byte member request rejected with HTTP 413; original three members unchanged','rule and flow latest versions are 2; variable issues empty','demo constants restored to 8000/USD']
 report['memberOversizeProblemCode']=problem['code'];report['demo']={'ruleVersion':migration['ruleVersion'],'flowVersion':migration['flowVersion'],'currentValues':values}
finally:b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
asset_hashes=images['images']['operations-console-web']['distSha256']
paths=[p for p in asset_hashes if p=='index.html' or p.startswith(('assets/RiskVariables-','assets/RiskDecisionFlows-','assets/index-'))]
report['publicAssets']={}
for path in paths:
 c=http.client.HTTPConnection('localhost',28080,timeout=15)
 try:
  c.request('GET','/' if path=='index.html' else '/'+path);r=c.getresponse();body=r.read();assert r.status==200
  digest=hashlib.sha256(body).hexdigest();assert digest==asset_hashes[path],path;report['publicAssets'][path]=digest
 finally:c.close()
report['database']={'variables':query('mor_risk','SELECT COUNT(*) FROM t_risk_variable')[0][0],'members':query('mor_risk','SELECT COUNT(*) FROM t_risk_variable_member')[0][0],'activeRoutes':query('mor_platform',"SELECT COUNT(*) FROM t_gateway_route WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0],'activeResources':query('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0],'adminVersion':query('mor_platform','SELECT version FROM t_role WHERE role_id=900000000000000510')[0][0]}
assert report['database']=={'variables':17,'members':9,'activeRoutes':70,'activeResources':38,'adminVersion':17}
report['sessionLoggedOut']=True;report['status']='test deployment complete; awaiting user acceptance';save('final-checks.json',report)
print({'status':report['status'],'database':report['database'],'publicAssetsMatched':len(report['publicAssets']),'unchangedContainers':len(unchanged),'demoVersionNo':2})
