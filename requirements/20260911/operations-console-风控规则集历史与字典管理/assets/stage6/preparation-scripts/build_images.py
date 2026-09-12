from pathlib import Path
import json,subprocess,hashlib,shutil,zipfile,datetime
root=Path('/Users/kenny/Desktop/Workspace/Code-MoR'); work=Path('/tmp/rco16-stage6'); req=root/'requirements/20260911/operations-console-风控规则集历史与字典管理'
verified=json.loads((req/'assets/stage5/verification.json').read_text())
for repo,app in verified['applications'].items():
 for path,sha in app.get('filesSha256',{}).items(): assert hashlib.sha256((root/repo/path).read_bytes()).hexdigest()==sha,repo+'/'+path
output=root/'test-environment/artifacts/rco16-history-dictionaries-20260911';assert not output.is_symlink();output.mkdir(exist_ok=True)
probe=output/'health-probe';probe.mkdir(exist_ok=True);java='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'
subprocess.run([java+'/bin/javac','--release','25','-encoding','UTF-8','-d',str(probe),str(root/'test-environment/java/LocalHttpHealthProbe.java'),str(root/'test-environment/java/LocalSnapshotEvidenceFetch.java')],check=True)
artifacts=[]; embedded=[]
for name,source in [('risk','risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar'),('bff','operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar')]:
 p=root/source;shutil.copyfile(p,output/(name+'.jar'));artifacts.append({'name':name,'path':source,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
 with zipfile.ZipFile(p) as z:
  a=z.read('BOOT-INF/lib/risk-api-1.0.0-SNAPSHOT.jar'); embedded.append(hashlib.sha256(a).hexdigest())
assert len(set(embedded))==1,'BFF Risk API differs'
assert embedded[0]==hashlib.sha256((work/'risk-api-release.jar').read_bytes()).hexdigest()
images=[]
for service,jar in [('risk-service','risk'),('operations-console-bff','bff')]:
 tag='mor-test/'+service+':rco16-history-dictionaries-20260911'
 subprocess.run(['docker','build','--pull=false','--network=none','-f',str(root/'test-environment/java/Dockerfile'),'--build-arg','JAR_FILE='+jar+'.jar','-t',tag,str(output)],check=True)
 images.append({'service':service,'tag':tag,'imageId':subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',tag],text=True).strip(),'jarSha256':next(a['sha256'] for a in artifacts if a['name']==jar)})
web=output/'web';web.mkdir();shutil.copyfile(root/'operations-console-web/Dockerfile',web/'Dockerfile');shutil.copytree(root/'operations-console-web/dist',web/'dist');shutil.copytree(root/'operations-console-web/deploy',web/'deploy')
tag='mor-test/operations-console-web:rco16-history-dictionaries-20260911'
subprocess.run(['docker','build','--pull=false','--network=none','-f',str(web/'Dockerfile'),'-t',tag,str(web)],check=True)
images.append({'service':'operations-console-web','tag':tag,'imageId':subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',tag],text=True).strip(),'indexSha256':hashlib.sha256((web/'dist/index.html').read_bytes()).hexdigest(),'staticFilesSha256':{str(p.relative_to(web/'dist')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((web/'dist').rglob('*')) if p.is_file()}})
report={'preparedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'artifacts':artifacts,'embeddedRiskApiSha256':embedded[0],'images':images,'scope':'Local isolated tags only; no development tag changes, no push, no deployment','sourceEvidence':'../stage5/verification.json'}
(work/'images.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(images,indent=2))
