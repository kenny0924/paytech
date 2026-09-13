"""Build and roll out the four production-file changes aligning the RCO-19 64 KiB limit."""
from release_support import *
import shutil,time,zipfile
initial=json.loads((OUT/'images.json').read_text())
allowed={
 'risk-compliance-service/risk-web/src/main/java/com/paytech/risk/configuration/RiskVariableRequestConfiguration.java',
 'risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/rest/decision/RiskVariableWireConverter.java',
 'operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskVariableRequestConfiguration.java',
 'operations-console-web/src/pages/RiskVariables.tsx'}
changed={p for p,d in initial['sourceSha256'].items() if sha(ROOT/p)!=d};assert changed==allowed|{'operations-console-web/src/risk/variableFixtures.ts'},changed
assert 'ALL_CHECKS_AND_BUILDS_PASSED' in Path('/tmp/rco19-limit-green-build.log').read_text()
build=ROOT/'test-environment/artifacts/rco19-variables-body-limit-20260913';build.mkdir(exist_ok=False)
shutil.copytree(ROOT/'test-environment/artifacts/rco19-variables-release-20260913/health-probe',build/'health-probe')
report={'at':str(datetime.datetime.now(datetime.timezone.utc)),'changedProductionFiles':{p:sha(ROOT/p) for p in sorted(allowed)},'fixtureSha256':sha(ROOT/'operations-console-web/src/risk/variableFixtures.ts'),'checks':{'riskBoundary':1,'bffVariableBoundary':7,'webBuild':'passed'},'images':{}}
api=[]
for service in ['risk-service','operations-console-bff']:
 jar=ROOT/initial['images'][service]['jar']
 with zipfile.ZipFile(jar) as z:api.append(hashlib.sha256(z.read('BOOT-INF/lib/risk-api-1.0.0-SNAPSHOT.jar')).hexdigest())
assert api[0]==api[1]
report['riskApiSha256']=api[0]
for service in ['risk-service','operations-console-bff','operations-console-web']:
 tag='mor-test/'+service+':rco19-variables-20260913-body-limit'
 assert subprocess.run(['docker','image','inspect',tag],capture_output=True).returncode!=0
 if service!='operations-console-web':
  jar=ROOT/initial['images'][service]['jar'];shutil.copy2(jar,build/jar.name)
  cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'test-environment/java/Dockerfile'),'--build-arg','JAR_FILE='+jar.name,'-t',tag,str(build)]
 else:cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'operations-console-web/Dockerfile'),'-t',tag,str(ROOT/'operations-console-web')]
 result=subprocess.run(cmd,capture_output=True,text=True);(build/(service+'-build.log')).write_text(result.stdout+result.stderr);assert result.returncode==0,service
 item={'tag':tag,'imageId':json.loads(run(['docker','image','inspect',tag]))[0]['Id']}
 if service!='operations-console-web':item.update({'jar':initial['images'][service]['jar'],'jarSha256':sha(jar)})
 else:item['distSha256']={str(p.relative_to(ROOT/'operations-console-web/dist')):sha(p) for p in sorted((ROOT/'operations-console-web/dist').rglob('*')) if p.is_file()}
 report['images'][service]=item;save('images-body-limit.json',report);print(service+' corrected image ready',flush=True)
with risk_operations_rollout_lock():
 for service,item in report['images'].items():
  run(['docker','tag',item['tag'],'mor-test/'+service+':development'])
  run([str(ROOT/'test-environment/compose.sh'),'up','-d','--no-deps','--no-build',service],timeout=90)
  deadline=time.monotonic()+120
  while True:
   found=[c for c in containers() if c['name']=='mor-test-'+service+'-1']
   if len(found)==1 and found[0]['health']=='healthy' and found[0]['image']==item['imageId']:break
   assert time.monotonic()<deadline,'Health timeout: '+service
   time.sleep(2)
  item['container']=found[0];save('images-body-limit.json',report);print(service+' corrected image healthy',flush=True)
 report['completedAt']=str(datetime.datetime.now(datetime.timezone.utc));save('images-body-limit.json',report)
