from pathlib import Path
import json, subprocess, sys, hashlib, re, datetime, sqlite3
root=Path('/Users/kenny/Desktop/Workspace/Code-MoR'); work=Path('/tmp/rco16-stage6-final'); req=root/'requirements/20260911/operations-console-风控规则集历史与字典管理'
sys.path.insert(0,str(root/'test-environment/scripts'))
from initialize import mysql

def query(db,sql):
 return [line.split('\t') for line in mysql('START TRANSACTION READ ONLY;\n'+sql+'\nCOMMIT;',database=db,timeout_seconds=30).splitlines()]

def registration():
 connection=sqlite3.connect(':memory:');connection.row_factory=sqlite3.Row; result={}
 source=(req/'db/platform-support-service/dml.sql').read_text()
 for m in re.finditer(r'INSERT INTO (t_\w+)\s*\((.*?)\)\s*VALUES\s*(.*?);',source,re.S):
  table,cols,values=m.groups(); names=[c.strip() for c in cols.split(',')]
  connection.execute('CREATE TABLE '+table+' ('+','.join(names)+')')
  connection.execute('INSERT INTO '+table+' ('+cols+') VALUES '+values,{'rco16_registration_time':'2026-09-11 00:00:00.000000'})
  result[table]=[dict(r) for r in connection.execute('SELECT * FROM '+table)]
 assert sum(map(len,result.values()))==182
 return result

report={'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'dockerContext':subprocess.check_output(['docker','context','show'],text=True).strip(),'containers':[]}
assert report['dockerContext']=='desktop-linux'
endpoint=subprocess.check_output(['docker','context','inspect','--format','{{.Endpoints.docker.Host}}',report['dockerContext']],text=True).strip();assert endpoint.startswith('unix://')
for service in ['mysql','redis','nacos','risk-service','operations-console-bff','operations-console-web','platform-support-service','api-gateway','nginx','vault-service','rabbitmq']:
 c=json.loads(subprocess.check_output(['docker','inspect','mor-test-'+service+'-1'],text=True))[0];labels=c['Config']['Labels'];assert labels['com.docker.compose.project']=='mor-test' and labels['com.docker.compose.service']==service
 report['containers'].append({'service':service,'containerId':c['Id'],'imageId':c['Image'],'imageTag':c['Config']['Image'],'health':c['State'].get('Health',{}).get('Status'),'startedAt':c['State']['StartedAt'],'networks':list(c['NetworkSettings']['Networks']),'ports':c['NetworkSettings']['Ports']})
report['risk']={
 'tableCounts':query('mor_risk',"SELECT COUNT(*), SUM(table_name IN ('t_rule_reject_reason','t_rule_subject_tag')) FROM information_schema.tables WHERE table_schema=DATABASE();"),
 'roots':query('mor_risk','SELECT rule_set_id,decision_point_id,current_rule_set_version_id,status,version FROM t_rule_set ORDER BY rule_set_id;'),
 'versions':query('mor_risk',"SELECT rule_set_version_id,version_no,status,content_format,content_hash FROM t_rule_set_version ORDER BY rule_set_version_id;"),
 'releases':query('mor_risk',"SELECT environment_code,status,COALESCE(CAST(active_slot AS CHAR),'NULL'),COUNT(*) FROM t_decision_release GROUP BY environment_code,status,active_slot ORDER BY environment_code,status,active_slot;"),
 'runtimeCounts':query('mor_risk',"SELECT (SELECT COUNT(*) FROM t_decision_evaluation),(SELECT COUNT(*) FROM t_action_effect);"),
 'commands':query('mor_risk',"SELECT operation_type,status,COUNT(*) FROM t_command_idempotency GROUP BY operation_type,status ORDER BY operation_type,status;"),
 'modifiedColumns':query('mor_risk',"SELECT table_name,column_name,column_type,is_nullable,column_comment FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='t_risk_rule' AND column_name IN ('reason_code','reason_message')) OR(table_name='t_rule_action_parameter' AND column_name='string_value')) ORDER BY table_name,column_name;"),
 'newIndexCount':query('mor_risk',"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND ((table_name='t_risk_rule' AND index_name='idx_rule_reason') OR (table_name='t_rule_action_parameter' AND index_name='idx_rule_param_string'));"),
 'reasonCounts':query('mor_risk',"SELECT COUNT(*),COUNT(DISTINCT reason_code),SUM(reason_message IS NULL) FROM t_risk_rule WHERE reason_code IS NOT NULL;"),
 'actionColumns':query('mor_risk',"SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('t_rule_action','t_action_definition','t_action_parameter_definition') ORDER BY table_name,ordinal_position;")}
report['platform']={
 'resources':query('mor_platform',"SELECT resource_id,resource_code,status,version FROM t_console_resource WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000571 ORDER BY resource_id;"),
 'routes':query('mor_platform',"SELECT route_id,route_code,channel,http_method,external_path_template,status,version FROM t_gateway_route ORDER BY route_id;"),
 'role':query('mor_platform',"SELECT role_id,role_code,status,version FROM t_role WHERE role_id=900000000000000510;"),
 'counts':query('mor_platform',"SELECT (SELECT COUNT(*) FROM t_gateway_route),(SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE');"),'reservedCollisions':{}}
# 同一只读事务捕获维护关闭后失去全部有效入口的权限，以及全部原有效绑定和角色版本。
route_ids=','.join(r[0] for r in report['platform']['routes'] if r[1].startswith('operations-risk-'))
resource_ids=','.join(r[0] for r in report['platform']['resources'])
assert all(x.isdigit() for x in (route_ids+','+resource_ids).split(','))
closed_permissions=f"""SELECT p.permission_id FROM t_permission p WHERE
 (EXISTS(SELECT 1 FROM t_gateway_route x WHERE x.permission_id=p.permission_id AND x.route_id IN ({route_ids}))
  OR EXISTS(SELECT 1 FROM t_console_resource x WHERE x.permission_id=p.permission_id AND x.resource_id IN ({resource_ids})))
 AND NOT EXISTS(SELECT 1 FROM t_gateway_route x JOIN t_gateway_target t ON t.target_id=x.target_id AND t.channel=x.channel AND t.status='ACTIVE'
  WHERE x.permission_id=p.permission_id AND x.channel='OPERATIONS' AND x.status='ACTIVE' AND x.authorization_mode='HUMAN_PERMISSION' AND x.route_id NOT IN ({route_ids}))
 AND NOT EXISTS(SELECT 1 FROM t_console_resource x WHERE x.permission_id=p.permission_id AND x.channel='OPERATIONS' AND x.status='ACTIVE' AND x.resource_id NOT IN ({resource_ids}))"""
binding_where="rp.status='ACTIVE' AND rp.permission_id IN ("+closed_permissions+")"
rows=query('mor_platform',"SELECT 'P',p.permission_id FROM ("+closed_permissions+") p ORDER BY p.permission_id;\n"
 +"SELECT 'B',rp.role_permission_id,rp.role_id,rp.permission_id,rp.status,rp.version FROM t_role_permission rp WHERE "+binding_where+" ORDER BY rp.role_permission_id;\n"
 +"SELECT 'R',r.role_id,r.role_code,r.status,r.version FROM t_role r WHERE r.role_id=900000000000000510 OR EXISTS(SELECT 1 FROM t_role_permission rp WHERE rp.role_id=r.role_id AND "+binding_where+") ORDER BY r.role_id;")
report['platform']['closedPermissionIds']=[r[1] for r in rows if r[0]=='P']
report['platform']['affectedRoleBindings']=[r[1:] for r in rows if r[0]=='B']
report['platform']['affectedRoles']=[r[1:] for r in rows if r[0]=='R']
assert report['platform']['closedPermissionIds'] and report['platform']['affectedRoleBindings']

for table,rows in registration().items():
 key=next(iter(rows[0]));ids=','.join(str(r[key]) for r in rows)
 report['platform']['reservedCollisions'][table]=query('mor_platform',f'SELECT COUNT(*) FROM {table} WHERE {key} IN ({ids});')
 # Codes also own uniqueness, regardless of reserved ID.
 code=next((c for c in ['permission_code','resource_code','route_code'] if c in rows[0]),None)
 if code:
  vals=','.join("'"+r[code].replace("'","''")+"'" for r in rows)
  report['platform']['reservedCollisions'][table+'Codes']=query('mor_platform',f'SELECT COUNT(*) FROM {table} WHERE {code} IN ({vals});')
verified=json.loads((req/'assets/stage5/verification.json').read_text());checked=0
amendment=json.loads((req/'assets/gateway-development/verification.json').read_text())['applications']['operations-console-bff']['amendment_files_sha256']
for path,sha in amendment.items():
 assert hashlib.sha256((root/'operations-console-bff'/path).read_bytes()).hexdigest()==sha,'reviewed BFF amendment drift'
verified['applications']['operations-console-bff']['filesSha256'].update(amendment)
latest=json.loads((req/'assets/gateway-acceptance/permission-correction-verification.json').read_text())
for repo,entry in latest['source'].items():
 for path,sha in entry['files_sha256'].items():
  file=root/repo/path
  assert (hashlib.sha256(file.read_bytes()).hexdigest() if file.is_file() else None)==sha,repo+'/'+path+' stage 5 drift'
for repo,app in verified['applications'].items():
 if repo in latest['source']:
  for path in app.get('filesSha256',{}):
   if path in latest['source'][repo]['files_sha256']:
    app['filesSha256'][path]=latest['source'][repo]['files_sha256'][path]
report['reviewedSources']={'originalBusiness':'../stage5/verification.json','bffAmendment':'../gateway-development/verification.json','gatewayPlatformFinal':'../gateway-acceptance/permission-correction-verification.json'}
for name,app in verified['applications'].items():
 for path,sha in app.get('filesSha256',{}).items():
  assert hashlib.sha256((root/name/path).read_bytes()).hexdigest()==sha, name+'/'+path+' drift';checked+=1
report['verifiedSourceFiles']=checked
assert report['risk']['tableCounts']==[['72','0']]
assert report['risk']['newIndexCount']==[['0']]
assert all(v==[['0']] for v in report['platform']['reservedCollisions'].values())
assert report['platform']['counts']==[['37','22']]
work.mkdir(exist_ok=True)
(work/'preflight.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'containers':len(report['containers']),'riskRoots':len(report['risk']['roots']),'riskVersions':len(report['risk']['versions']),'releases':report['risk']['releases'],'reasonCounts':report['risk']['reasonCounts'],'routeResourceCounts':report['platform']['counts'],'resourceStates':report['platform']['resources'],'role':report['platform']['role'],'verifiedSourceFiles':checked},ensure_ascii=False))
