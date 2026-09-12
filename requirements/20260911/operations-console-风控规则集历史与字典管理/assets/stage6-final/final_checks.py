"""最终只读核对运行环境、候选制品与审核文件；不应用 SQL 或启动服务。"""
from pathlib import Path
from datetime import datetime, timezone
from urllib.parse import unquote
import ast, hashlib, json, re, subprocess, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[5]
REQ = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


base = json.loads((HERE / 'preflight.json').read_text())
result = subprocess.run(['python3',str(REQ / 'assets/stage6/preparation-scripts/preflight.py')],text=True,capture_output=True)
assert result.returncode == 0, 'read-only preflight failed; inspect source/baseline drift before release'
fresh = json.loads(Path('/tmp/rco16-stage6-final/preflight.json').read_text())
for key in ['risk','platform','containers']:
    assert fresh[key] == base[key], key + ' changed during preparation'
for old in base['containers']:
    assert old['health'] == 'healthy'
    if old['service'] in ['risk-service','operations-console-bff','operations-console-web','api-gateway']:
        actual = subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',old['imageTag']],text=True).strip()
        assert actual == old['imageId'], old['service'] + ' runtime tag changed'
images = json.loads((HERE / 'images.json').read_text())
for item in images['images']:
    assert subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',item['tag']],text=True).strip() == item['imageId']
report_files = list((ROOT/'operations-console-bff/paytech-operations-console-bff-test/target/surefire-reports').glob('TEST-*.xml'))
tests = {k:0 for k in ['tests','failures','errors','skipped']}
for file in report_files:
    xml = ET.parse(file).getroot()
    for key in tests: tests[key] += int(xml.get(key))
assert tests == {'tests':1041,'failures':0,'errors':0,'skipped':0}, tests
bff = ROOT / 'operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar'
assert sha(bff) == next(i['jarSha256'] for i in images['images'] if i['service']=='operations-console-bff'), 'tested BFF jar changed from candidate'
site = Path('/tmp/rco16-stage6-requirements-site.log').read_text()
assert 'pass 136' in site and 'fail 0' in site
for repo in ['api-gateway','platform-support-service','risk-compliance-service','operations-console-bff','operations-console-web']:
    subprocess.run(['git','diff','--check'],cwd=ROOT/repo,check=True,capture_output=True)
subprocess.run(['git','diff','--check'],cwd=ROOT,check=True,capture_output=True)
subprocess.run([str(ROOT/'test-environment/compose.sh'),'config','--quiet'],check=True,capture_output=True)
links = 0
docs = list(REQ.glob('*.md')) + [ROOT/'agent-docs/CURRENT_IMPLEMENTATION.md']
for file in docs:
    source = file.read_text()
    assert len(re.findall(r'^```',source,re.M)) % 2 == 0, file
    for target in re.findall(r'\]\(([^)]+)\)',source):
        target = target.strip('<>').split('#')[0]
        if target and not re.match(r'[a-zA-Z]+:',target):
            destination = (file.parent/unquote(target)).resolve()
            assert destination.exists() or destination == HERE/'final-checks.json', (file,target)
            links += 1
for directory in [HERE,REQ/'assets/stage6/preparation-scripts']:
    for file in directory.glob('*.py'): ast.parse(file.read_text(),filename=str(file))
    cache = directory/'__pycache__'
    for file in cache.glob('*.pyc'): file.unlink()
    if cache.exists() and not any(cache.iterdir()): cache.rmdir()
out = {'checkedAt':datetime.now(timezone.utc).isoformat(), 'unchangedHealthyContainers':len(base['containers']),
    'fourDevelopmentTagsUnchanged':True,'candidateImageIdsVerified':4,'riskAndPlatformReadOnlyBaselineUnchanged':True,
    'sourceFilesMatchedApprovedEvidence':fresh['verifiedSourceFiles'],'bffFullVerify':tests,'testedBffJarMatchesImage':True,
    'requirementsSitePassed':136,'diffChecksPassed':True,'composeQuietValidationPassed':True,'localMarkdownLinksChecked':links,
    'pythonSyntaxPassed':True,'ownedPythonCacheRemoved':True,
    'scope':'Read-only test checks; no test SQL, deployment, restart, git commit/push or remote artifact publication.',
    'logSha256':{str(p):sha(p) for p in [Path('/tmp/rco16-stage6-bff-package.log'),Path('/tmp/rco16-stage6-bff-full.log'),Path('/tmp/rco16-stage6-requirements-site.log'),Path('/tmp/rco16-stage6-build-images.log')]}}
(HERE/'final-checks.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(out,ensure_ascii=False,indent=2))
