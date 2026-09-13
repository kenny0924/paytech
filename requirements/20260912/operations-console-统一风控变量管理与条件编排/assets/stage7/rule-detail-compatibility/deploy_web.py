"""Same-release Web-only correction for the LEGACY rule-detail response."""
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent.parent))
from release_support import *
import time,http.client
HERE=Path(__file__).resolve().parent
baseline=json.loads((OUT/'images.json').read_text())['sourceSha256']
fixed=json.loads((OUT/'images-body-limit.json').read_text())
baseline.update(fixed['changedProductionFiles']);baseline['operations-console-web/src/risk/variableFixtures.ts']=fixed['fixtureSha256']
changed={p for p,d in baseline.items() if sha(ROOT/p)!=d}
assert changed=={'operations-console-web/src/risk/riskResponseValidation.ts','operations-console-web/src/risk/riskResponseValidation.test.ts'},changed
tag='mor-test/operations-console-web:rco19-legacy-detail-20260913'
assert subprocess.run(['docker','image','inspect',tag],capture_output=True).returncode!=0
cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'operations-console-web/Dockerfile'),'-t',tag,str(ROOT/'operations-console-web')]
result=subprocess.run(cmd,capture_output=True,text=True);Path('/tmp/rco19-legacy-detail-image.log').write_text(result.stdout+result.stderr);assert result.returncode==0
item=json.loads(run(['docker','image','inspect',tag]))[0]
report={'at':str(datetime.datetime.now(datetime.timezone.utc)),'authorization':'RCO-19 已授权 test 发布，同范围详情兼容修复；仅更新 Web','image':{'tag':tag,'imageId':item['Id']},'changedSourceSha256':{p:sha(ROOT/p) for p in changed},'logsSha256':{name:sha(Path('/tmp')/name) for name in ['rco19-legacy-detail-tests.log','rco19-legacy-detail-build.log']}}
with risk_operations_rollout_lock():
 before=containers();assert len(before)==11 and all(c['health']=='healthy' for c in before)
 report['before']=before
 run(['docker','tag',tag,'mor-test/operations-console-web:development'])
 run([str(ROOT/'test-environment/compose.sh'),'up','-d','--no-deps','--no-build','operations-console-web'],timeout=90)
 deadline=time.monotonic()+90
 while True:
  after=containers();web=next(c for c in after if c['name']=='mor-test-operations-console-web-1')
  if web['health']=='healthy' and web['image']==item['Id']:break
  assert time.monotonic()<deadline,'Web health timeout'
  time.sleep(2)
 for previous in before:
  if previous['name']!=web['name']:assert next(c for c in after if c['name']==previous['name'])==previous
 report['after']=after;report['otherContainersUnchanged']=10
 dist=ROOT/'operations-console-web/dist'
 files=[dist/'index.html',*sorted((dist/'assets').glob('riskResponseValidation-*.js')),*sorted((dist/'assets').glob('RiskRuleConfiguration-*.js'))]
 report['publicAssets']={}
 for file in files:
  path=str(file.relative_to(dist));c=http.client.HTTPConnection('localhost',28080,timeout=15)
  try:
   c.request('GET','/' if path=='index.html' else '/'+path);r=c.getresponse();raw=r.read();assert r.status==200
   digest=hashlib.sha256(raw).hexdigest();assert digest==sha(file),path;report['publicAssets'][path]=digest
  finally:c.close()
 report['completedAt']=str(datetime.datetime.now(datetime.timezone.utc))
 (HERE/'deployment.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print({'web':'healthy','image':tag,'otherContainersUnchanged':10,'publicAssetsMatched':len(files)})
