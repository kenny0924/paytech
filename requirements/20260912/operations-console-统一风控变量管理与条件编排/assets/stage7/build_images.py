from release_support import *
import shutil,zipfile

BUILD=ROOT/'test-environment/artifacts/rco19-variables-release-20260913'
BUILD.mkdir(exist_ok=False)
verified=json.loads((REQ/'assets/stage5/verification.json').read_text())
for path,digest in verified['sourceSha256'].items():assert sha(ROOT/path)==digest,path
apps={
 'risk-service':'risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar',
 'operations-console-bff':'operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar'}
api=[]
for path in apps.values():
 with zipfile.ZipFile(ROOT/path) as z:api.append(hashlib.sha256(z.read('BOOT-INF/lib/risk-api-1.0.0-SNAPSHOT.jar')).hexdigest())
assert api[0]==api[1],'Risk and BFF API artifacts differ'
jdk=run(['/usr/libexec/java_home','-v','25'])
probe=BUILD/'health-probe';probe.mkdir()
run([jdk+'/bin/javac','--release','25','-d',str(probe),str(ROOT/'test-environment/java/LocalHttpHealthProbe.java'),str(ROOT/'test-environment/java/LocalSnapshotEvidenceFetch.java')])
report={'at':str(datetime.datetime.now(datetime.timezone.utc)),'riskApiSha256':api[0],'images':{},'sourceSha256':{}}
for repo in ['risk-compliance-service','operations-console-bff','operations-console-web']:
 paths=run(['git','-C',str(ROOT/repo),'ls-files','--cached','--others','--exclude-standard','-z']).split('\0')
 for path in paths:
  file=ROOT/repo/path
  if file.is_file() and ('/src/main/' in path or path.startswith('src/') or path.endswith('pom.xml') or path in ['package.json','package-lock.json','Dockerfile','deploy/nginx.conf','vite.config.ts','tsconfig.json']):report['sourceSha256'][repo+'/'+path]=sha(file)
for service in [*apps,'operations-console-web']:
 tag='mor-test/'+service+':rco19-variables-20260913'
 assert subprocess.run(['docker','image','inspect',tag],capture_output=True).returncode!=0,'Tag already exists'
 if service in apps:
  jar=ROOT/apps[service];shutil.copy2(jar,BUILD/jar.name)
  cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'test-environment/java/Dockerfile'),'--build-arg','JAR_FILE='+jar.name,'-t',tag,str(BUILD)]
 else:cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'operations-console-web/Dockerfile'),'-t',tag,str(ROOT/'operations-console-web')]
 result=subprocess.run(cmd,text=True,capture_output=True)
 (BUILD/(service+'-build.log')).write_text(result.stdout+result.stderr)
 assert result.returncode==0,'Image build failed; inspect '+service+' local log'
 data=json.loads(run(['docker','image','inspect',tag]))[0]
 report['images'][service]={'tag':tag,'imageId':data['Id'],'architecture':data['Architecture']}
 if service in apps:report['images'][service].update({'jar':apps[service],'jarSha256':sha(ROOT/apps[service])})
 else:report['images'][service]['distSha256']={str(p.relative_to(ROOT/'operations-console-web/dist')):sha(p) for p in sorted((ROOT/'operations-console-web/dist').rglob('*')) if p.is_file()}
 save('images.json',report);print(service+' image ready',flush=True)
