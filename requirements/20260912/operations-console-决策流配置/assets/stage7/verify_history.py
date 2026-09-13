"""Compare old and candidate packaged Readers while original columns are still present."""
from deploy import ROOT, REQ, HERE, IMAGES, run, environment, sha
from pathlib import Path
import json, subprocess, zipfile

work=Path('/tmp/rco17-stage7');work.mkdir(exist_ok=True)
baseline=json.loads((HERE/'before-history.json').read_text())
outputs={}
for label,jar in [('old',Path('/tmp/rco17-stage6/risk-service-deployed.jar')),('candidate',ROOT/IMAGES['images']['risk-service']['jar'])]:
    expected=baseline['jarSha256'] if label=='old' else IMAGES['images']['risk-service']['jarSha256']
    assert sha(jar.read_bytes())==expected
    dest=work/label
    with zipfile.ZipFile(jar) as z:z.extractall(dest)
    boot=dest/'BOOT-INF';cp=':'.join([str(boot/'classes'),*(str(p) for p in sorted((boot/'lib').glob('*.jar')))])
    output=HERE/(label+'-history-corrected.json')
    result=subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java','--class-path',cp,str(HERE/'ReadRiskHistory.java'),str(output)],input=json.dumps({'password':environment()['MOR_MYSQL_ROOT_PASSWORD']}),capture_output=True,text=True,timeout=60)
    (work/(label+'-reader.log')).write_text(result.stdout+result.stderr)
    assert result.returncode==0,label+' reader failed; inspect private log'
    data=json.loads(output.read_text());data['jarSha256']=expected
    output.write_text(json.dumps(data,indent=2)+'\n');outputs[label]=data
    print(result.stdout.strip(),flush=True)
assert outputs['old']['rules']==baseline['rules'] and outputs['old']['releases']==baseline['releases'],'Original pre-migration baseline changed'
assert outputs['candidate']['rules']==baseline['rules'] and outputs['candidate']['releases']==baseline['releases'],'Candidate historical content changed'
(HERE/'migration-history-verification.json').write_text(json.dumps({'rules':33,'releases':10,'result':'Exact original rule/point/input identity hashes and Release detail/create replay preserved','helperSha256':sha((HERE/'ReadRiskHistory.java').read_bytes()),'oldJarSha256':outputs['old']['jarSha256'],'candidateJarSha256':outputs['candidate']['jarSha256'],'correction':'Ordinary configuration intentionally omits Point projection and physical input IDs. Invoke packaged legacy projection and use actual legacy fields for that part; ordinary rule hash still checked.'},indent=2)+'\n')
print('Original before-migration baseline and both actual Readers match',flush=True)
