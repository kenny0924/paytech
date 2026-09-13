"""Resume only the uncommitted activation after Python 3.9 rejected zip(strict=True)."""
from release_support import *
import time
from dataclasses import asdict
from gateway_snapshot_wait import ComposeFetcher,GATEWAY_METRICS_URL
from gateway_snapshot_metrics import decode_prometheus
pre=json.loads((OUT/'preflight.json').read_text());images=json.loads((OUT/'images.json').read_text())
report=json.loads((OUT/'deployment.json').read_text())
def record(name,value=None):
 report['events'].append({'at':str(datetime.datetime.now(datetime.timezone.utc)),'step':name,'value':value});save('deployment.json',report);print(name,flush=True)
with risk_operations_rollout_lock():
 assert report['events'][-1]['step']=='operations-console-web healthy'
 after=containers();assert len(after)==11 and all(c['health']=='healthy' for c in after)
 for service,item in images['images'].items():assert next(c for c in after if c['name']=='mor-test-'+service+'-1')['image']==item['imageId']
 record('Resuming uncommitted activation',{'cause':'Python 3.9 does not support zip(strict=True); no activation UPDATE executed; replaced with explicit length check'})
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
  assert time.monotonic()<deadline,'Gateway snapshot did not accept registrations'
  time.sleep(2)
 record('Gateway snapshot refreshed',asdict(value))
 after=containers();assert len(after)==11 and all(c['health']=='healthy' for c in after)
 for old in pre['containers']:
  if old['name'] not in ['mor-test-'+s+'-1' for s in images['images']]:assert next(c for c in after if c['name']==old['name'])==old
 assert query('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")==((38,),)
 record('Release completed; other eight containers unchanged',{'containers':after,'activeRoutes':70,'activeResources':38})
