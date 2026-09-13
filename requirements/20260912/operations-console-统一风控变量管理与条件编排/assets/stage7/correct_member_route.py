from release_support import *
import time
from dataclasses import asdict
from gateway_snapshot_wait import ComposeFetcher,GATEWAY_METRICS_URL
from gateway_snapshot_metrics import decode_prometheus
report=json.loads((OUT/'deployment.json').read_text())
with risk_operations_rollout_lock():
 with connect('mor_platform') as c:
  with c.cursor() as q:
   sql=statements((OUT/'member-body-limit.sql').read_text());assert len(sql)==1
   q.execute(sql[0]);assert q.rowcount==1
  c.commit()
 report['events'].append({'at':str(datetime.datetime.now(datetime.timezone.utc)),'step':'Corrected member route body limit to existing 64 KiB ceiling','value':{'previousLimit':262144,'limit':65536,'sqlSha256':sha(OUT/'member-body-limit.sql')}});save('deployment.json',report)
 deadline=time.monotonic()+45
 while True:
  value=decode_prometheus(ComposeFetcher()(GATEWAY_METRICS_URL,12))
  if value.valid and value.refresh_result=='SUCCESS' and value.failure_reason=='NONE' and not value.last_valid_in_use and value.operations_route_count==70:break
  assert time.monotonic()<deadline,'Snapshot still unavailable'
  time.sleep(2)
 report['events'].append({'at':str(datetime.datetime.now(datetime.timezone.utc)),'step':'Gateway snapshot refreshed','value':asdict(value)});save('deployment.json',report);print(value)
