"""Build three uniquely tagged local candidates from verified stage 5 artifacts; no deployment."""
from preflight import ROOT, REQ, OUT
import hashlib, io, json, re, shutil, subprocess, zipfile

BUILD = ROOT/'test-environment/artifacts/rco17-decision-flow-release'
BUILD.mkdir(exist_ok=True)
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
artifacts = json.loads((REQ/'assets/stage5/real-local-artifacts.json').read_text())['sha256']
for row in json.loads((REQ/'assets/stage5/recheck-local-artifacts.json').read_text())['rebuiltForThisRecheck']:
    artifacts[row['path']] = row['sha256']
apps = {
    'risk-service':'risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar',
    'operations-console-bff':'operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar'}
for path in apps.values(): assert sha(ROOT/path)==artifacts[path], 'Verified JAR changed: '+path
def embedded(path, name):
    with zipfile.ZipFile(ROOT/path) as z: return hashlib.sha256(z.read('BOOT-INF/lib/'+name+'-1.0.0-SNAPSHOT.jar')).hexdigest()
api_files=[]
api_entries=[]
for service,path in apps.items():
    with zipfile.ZipFile(ROOT/path) as z: data=z.read('BOOT-INF/lib/risk-api-1.0.0-SNAPSHOT.jar')
    file=BUILD/(service+'-risk-api.jar');file.write_bytes(data);api_files.append(file)
    with zipfile.ZipFile(io.BytesIO(data)) as z: api_entries.append({n:z.read(n) for n in z.namelist() if not n.endswith('/')})
assert api_entries[0].keys()==api_entries[1].keys()
debug_differences=[]
for name in api_entries[0]:
    if api_entries[0][name]==api_entries[1][name]: continue
    assert name.endswith('.class'), 'Non-class API content differs: '+name
    views=[]
    for file in api_files:
        view=subprocess.check_output(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javap','-p','-v','-classpath',str(file),name[:-6].replace('/','.')],text=True)
        views.append('\n'.join(line for line in view.splitlines() if not line.startswith(('Classfile ','  SHA-256 checksum ')) and not re.fullmatch(r'\s*line \d+: \d+',line)))
    assert views[0]==views[1], 'API bytecode/annotations differ beyond debug line numbers: '+name
    debug_differences.append(name)
assert embedded(apps['operations-console-bff'],'paytech-http-client-support')==artifacts['paytech-foundation/paytech-http-client-support/target/paytech-http-client-support-1.0.0-SNAPSHOT.jar']
probe = BUILD/'health-probe'
probe.mkdir(exist_ok=True)
subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/javac','--release','25','-d',str(probe),str(ROOT/'test-environment/java/LocalHttpHealthProbe.java'),str(ROOT/'test-environment/java/LocalSnapshotEvidenceFetch.java')],check=True)
report = {'scope':'Local candidate image build only. No development retag, running-container change or shared artifact publication.', 'riskApiComparison':{'entryCount':len(api_entries[0]),'debugLineOnlyDifferences':debug_differences,'methodCodeAndAnnotations':'identical; full javap -p -v compared excluding class path, checksum and source line numbers'}, 'images':{}, 'sourceFilesSha256':{}, 'sourceHeads':{}}
for repo in ['risk-compliance-service','operations-console-bff','operations-console-web','paytech-foundation']:
    report['sourceHeads'][repo] = subprocess.check_output(['git','-C',str(ROOT/repo),'rev-parse','HEAD'],text=True).strip()
    paths = subprocess.check_output(['git','-C',str(ROOT/repo),'ls-files','--cached','--others','--exclude-standard','-z']).decode().split('\0')
    for path in paths:
        file=ROOT/repo/path
        if file.is_file() and ('/src/main/' in path or path.startswith('src/') or path in ['pom.xml','package.json','package-lock.json','Dockerfile','deploy/nginx.conf','vite.config.ts','tsconfig.json'] or path.endswith('/pom.xml')):
            report['sourceFilesSha256'][repo+'/'+path]=sha(file)
for service in [*apps,'operations-console-web']:
    tag='mor-test/'+service+':rco17-decision-flow-20260912'
    exists=subprocess.run(['docker','image','inspect',tag],capture_output=True).returncode==0
    assert not exists, 'Candidate tag exists: inspect and reuse its recorded hash instead of overwriting'
    if service in apps:
        jar=ROOT/apps[service]
        shutil.copy2(jar,BUILD/jar.name)
        cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'test-environment/java/Dockerfile'),'--build-arg','JAR_FILE='+jar.name,'-t',tag,str(BUILD)]
    else:
        cmd=['docker','build','--pull=false','--network=none','-f',str(ROOT/'operations-console-web/Dockerfile'),'-t',tag,str(ROOT/'operations-console-web')]
    result=subprocess.run(cmd,text=True,capture_output=True)
    (BUILD/(service+'-build.log')).write_text(result.stdout+result.stderr)
    assert result.returncode==0, service+': inspect build log'
    data=json.loads(subprocess.check_output(['docker','image','inspect',tag],text=True))[0]
    report['images'][service]={'tag':tag,'imageId':data['Id'],'architecture':data['Architecture']}
    if service in apps: report['images'][service].update({'jar':apps[service],'jarSha256':sha(ROOT/apps[service])})
    else: report['images'][service]['distFilesSha256']={str(p.relative_to(ROOT/'operations-console-web/dist')):sha(p) for p in sorted((ROOT/'operations-console-web/dist').rglob('*')) if p.is_file()}
    print(service+' built '+data['Id'],flush=True)
(OUT/'images.json').write_text(json.dumps(report,indent=2)+'\n')
