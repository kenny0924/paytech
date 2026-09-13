"""Inspect candidate bytes using never-started disposable containers, then remove those containers."""
from preflight import ROOT, OUT
from pathlib import Path
import hashlib, json, subprocess, zipfile

work=Path('/tmp/rco17-stage6/image-inspection');work.mkdir(exist_ok=True)
package=json.loads((OUT/'images.json').read_text())
report={}
for service,item in package['images'].items():
    actual=subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',item['tag']],text=True).strip()
    assert actual==item['imageId']
    container=subprocess.check_output(['docker','create',item['imageId']],text=True).strip()
    try:
        assert subprocess.check_output(['docker','inspect','--format','{{.State.Status}}',container],text=True).strip()=='created'
        if service=='operations-console-web':
            dest=work/service;dest.mkdir(exist_ok=True)
            subprocess.run(['docker','cp',container+':/usr/share/nginx/operations-console/.',str(dest)],check=True,capture_output=True)
            files={str(p.relative_to(dest)):hashlib.sha256(p.read_bytes()).hexdigest() for p in dest.rglob('*') if p.is_file()}
            assert files==item['distFilesSha256']
            report[service]={'distFiles':len(files),'indexSha256':files['index.html']}
        else:
            jar=work/(service+'.jar')
            subprocess.run(['docker','cp',container+':/app/application.jar',str(jar)],check=True,capture_output=True)
            assert hashlib.sha256(jar.read_bytes()).hexdigest()==item['jarSha256']
            report[service]={'jarSha256':item['jarSha256']}
    finally:
        subprocess.run(['docker','rm',container],check=True,capture_output=True)
candidate=ROOT/package['images']['risk-service']['jar']
dest=work/'candidate-risk'
with zipfile.ZipFile(candidate) as z:z.extractall(dest)
boot=dest/'BOOT-INF'
cp=':'.join([str(boot/'classes'),*(str(p) for p in sorted((boot/'lib').glob('*.jar')))])
result=subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javac','--release','25','-cp',cp,'-d',str(work),str(OUT/'ReadRiskHistory.java')],text=True,capture_output=True)
(work/'candidate-reader-compile.log').write_text(result.stdout+result.stderr)
assert result.returncode==0,'Candidate history reader compilation failed'
report['candidateReader']='Compiled against exact candidate JAR; not executed before test backfill'
baseline=json.loads((OUT/'preflight.json').read_text())
for item in baseline['containers']:
    c=json.loads(subprocess.check_output(['docker','inspect',item['containerId']],text=True))[0]
    assert c['Image']==item['imageId'] and c['State']['StartedAt']==item['startedAt']
report['runningTest']='All 11 original container IDs, images and startup timestamps unchanged'
(OUT/'image-inspection.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
