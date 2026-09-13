"""Read-only baseline now; --candidate allowed only after the approved backfill in stage 7."""
from preflight import ROOT, REQ, OUT
from initialize import environment
from pathlib import Path
import argparse, hashlib, json, subprocess, zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--candidate',action='store_true')
args=parser.parse_args()
work=Path('/tmp/rco17-stage6')
if args.candidate:
    jar=ROOT/'risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar'
else:
    jar=work/'risk-service-deployed.jar'
    subprocess.run(['docker','cp','mor-test-risk-service-1:/app/application.jar',str(jar)],check=True,capture_output=True)
dest=work/('risk-candidate-expanded' if args.candidate else 'risk-old-expanded')
with zipfile.ZipFile(jar) as z:z.extractall(dest)
boot=dest/'BOOT-INF'
cp=':'.join([str(boot/'classes'),*(str(p) for p in sorted((boot/'lib').glob('*.jar')))])
output=OUT/('candidate-history.json' if args.candidate else 'old-history.json')
result=subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java','--class-path',cp,str(OUT/'ReadRiskHistory.java'),str(output)],input=json.dumps({'password':environment()['MOR_MYSQL_ROOT_PASSWORD']}),text=True,capture_output=True)
# Errors may contain internal facts; retain locally, never echo query/credentials.
(work/'history-reader.log').write_text(result.stdout+result.stderr)
assert result.returncode==0,'Historical Reader failed; inspect /tmp/rco17-stage6/history-reader.log locally'
data=json.loads(output.read_text());data['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
output.write_text(json.dumps(data,indent=2)+'\n')
if args.candidate:
    before=json.loads((OUT/'old-history.json').read_text())
    assert data['rules']==before['rules'] and data['releases']==before['releases'],'Historical reconstruction drift; do not finalize DDL'
print(result.stdout.strip())
