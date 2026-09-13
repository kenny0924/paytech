from release_support import *
import os,time
from dataclasses import asdict
from gateway_snapshot_wait import ComposeFetcher,GATEWAY_METRICS_URL
from gateway_snapshot_metrics import decode_prometheus

pre=json.loads((OUT/'preflight.json').read_text());images=json.loads((OUT/'images.json').read_text())
report={'authorization':'用户：发布；既有 test 示例统一变量迁移已确认','startedAt':str(datetime.datetime.now(datetime.timezone.utc)),'events':[]}
def record(name,value=None):
 report['events'].append({'at':str(datetime.datetime.now(datetime.timezone.utc)),'step':name,'value':value});save('deployment.json',report);print(name,flush=True)
def healthy(service):
 deadline=time.monotonic()+180
 while time.monotonic()<deadline:
  found=[c for c in containers() if c['name']=='mor-test-'+service+'-1']
  if len(found)==1 and found[0]['health']=='healthy':return found[0]
  time.sleep(2)
 raise RuntimeError('Health timeout: '+service)
def apply_dml(db,path,counts,admin=False):
 with connect(db) as c:
  with c.cursor() as q:
   if admin:
    q.execute('SELECT role_id,version FROM t_role WHERE role_id=%s FOR UPDATE',(pre['admin'][0],));assert q.fetchone()==tuple(pre['admin'])
   actual=[]
   for s in statements(path.read_text()):
    if s.upper() in ['START TRANSACTION','COMMIT']:continue
    q.execute(s)
    if s.upper().startswith(('INSERT','UPDATE')):actual.append(q.rowcount)
   assert actual==counts,(db,actual)
  c.commit()
 record(db+' DML committed',{'sha256':sha(path),'affectedRows':actual})

with risk_operations_rollout_lock():
 assert not (OUT/'deployment.json').exists(),'Do not rerun a partial release; inspect its checkpoints'
 assert containers()==pre['containers'],'Container baseline changed'
 for p,digest in pre['sqlSha256'].items():assert sha(REQ/p)==digest
 assert query('mor_risk',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk' AND table_name IN ('t_risk_variable','t_risk_variable_member')")==((0,),)
 backup=Path.home()/'Code-MoR-backups'/('rco19-test-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
 backup.mkdir(mode=0o700,parents=True,exist_ok=False)
 captured={'mor_risk':{},'mor_platform':{}}
 for table in ['t_rule_set_version','t_rule_operand','t_rule_action_parameter','t_decision_flow_edge']:
  captured['mor_risk'][table]=query('mor_risk','SHOW CREATE TABLE '+table)[0][1]
 captured['mor_platform']['administratorRole']=query('mor_platform','SELECT * FROM t_role WHERE role_id=%s',(pre['admin'][0],))
 file=backup/'affected-schema-and-role.json'
 with os.fdopen(os.open(file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:json.dump(captured,f,ensure_ascii=False,indent=2,default=encode)
 record('Scoped backup saved',{'path':str(file),'sha256':sha(file)})
 run([str(ROOT/'test-environment/compose.sh'),'stop','operations-console-web','operations-console-bff','risk-service'],timeout=90)
 record('Three applications stopped')
 ddl=statements((REQ/'db/risk-compliance-service/ddl.sql').read_text());assert len(ddl)==6
 with connect('mor_risk') as c:
  with c.cursor() as q:
   for i,s in enumerate(ddl):q.execute(s);record('Risk DDL '+str(i+1)+' applied')
  c.commit()
 apply_dml('mor_risk',REQ/'db/risk-compliance-service/dml.sql',[17,9])
 apply_dml('mor_platform',REQ/'db/platform-support-service/dml.sql',[2,2,3,9,24,19,54,1],admin=True)
 for service in ['risk-service','operations-console-bff','operations-console-web']:
  item=images['images'][service]
  assert json.loads(run(['docker','image','inspect',item['tag']]))[0]['Id']==item['imageId']
  run(['docker','tag',item['tag'],'mor-test/'+service+':development'])
  run([str(ROOT/'test-environment/compose.sh'),'up','-d','--no-deps','--no-build',service],timeout=90)
  c=healthy(service);assert c['image']==item['imageId'];record(service+' healthy',c)
 with connect('mor_platform') as c:
  with c.cursor() as q:
   q.execute('SELECT version FROM t_role WHERE role_id=%s FOR UPDATE',(pre['admin'][0],));assert q.fetchone()==(16,)
   sql=statements((OUT/'enable.sql').read_text());assert len(sql)==4
   for s,n in zip(sql,[9,3,2,1]):q.execute(s);assert q.rowcount==n
  c.commit()
 record('Variable routes, menu and administrator permissions activated')
 deadline=time.monotonic()+60
 while True:
  value=decode_prometheus(ComposeFetcher()(GATEWAY_METRICS_URL,12))
  if value.valid and value.refresh_result=='SUCCESS' and value.failure_reason=='NONE' and not value.last_valid_in_use and value.operations_route_count==pre['activeRoutes']+9:break
  assert time.monotonic()<deadline,'Gateway snapshot did not accept new registrations'
  time.sleep(2)
 record('Gateway snapshot refreshed',asdict(value))
 after=containers();assert len(after)==11 and all(c['health']=='healthy' for c in after)
 for old in pre['containers']:
  if old['name'] not in ['mor-test-'+s+'-1' for s in images['images']]:assert next(c for c in after if c['name']==old['name'])==old
 assert query('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")==((pre['activeResources']+3,),)
 record('Release completed; other eight containers unchanged',{'containers':after,'activeRoutes':70,'activeResources':38})
