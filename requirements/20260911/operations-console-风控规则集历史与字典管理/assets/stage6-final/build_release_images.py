"""仅准备本机候选镜像并验证已有 Risk/Web 制品；不改运行标签、不推送、不启动容器。"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, json, shutil, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[5]
REQ = Path(__file__).resolve().parents[2]
OUT = ROOT / 'test-environment/artifacts/rco16-release-20260912'
JAVA = '/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def embedded(path, name):
    with zipfile.ZipFile(path) as jar:
        return hashlib.sha256(jar.read('BOOT-INF/lib/' + name + '-1.0.0-SNAPSHOT.jar')).hexdigest()


OUT.mkdir(exist_ok=False)
probe = OUT / 'health-probe'
probe.mkdir()
subprocess.run([JAVA + 'javac', '--release', '25', '-encoding', 'UTF-8', '-d', str(probe),
    str(ROOT / 'test-environment/java/LocalHttpHealthProbe.java'),
    str(ROOT / 'test-environment/java/LocalSnapshotEvidenceFetch.java')], check=True)
prior = json.loads((REQ / 'assets/stage6/images.json').read_text())
images = []
for image in prior['images']:
    if image['service'] not in ['risk-service', 'operations-console-web']:
        continue
    actual = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', image['tag']], text=True).strip()
    assert actual == image['imageId'], image['service']
    images.append(dict(image, preparation='Reused exact previously prepared image; reviewed source hashes rechecked.'))
assert len(images) == 2
risk = ROOT / 'risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar'
assert sha(risk) == next(i['jarSha256'] for i in images if i['service'] == 'risk-service')
bff = ROOT / 'operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar'
gateway = ROOT / 'api-gateway/api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar'
assert embedded(risk, 'risk-api') == embedded(bff, 'risk-api')
assert embedded(bff, 'platform-support-api') == embedded(gateway, 'platform-support-api')
for service, source, tag in [
    ('operations-console-bff', bff, 'mor-test/operations-console-bff:rco16-history-dictionaries-20260912'),
    ('api-gateway', gateway, 'mor-test/api-gateway:rco16-gateway-candidate-20260912')]:
    name = service + '.jar'
    shutil.copyfile(source, OUT / name)
    subprocess.run(['docker', 'build', '--pull=false', '--network=none', '-f',
        str(ROOT / 'test-environment/java/Dockerfile'), '--build-arg', 'JAR_FILE=' + name, '-t', tag, str(OUT)], check=True)
    image_id = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', tag], text=True).strip()
    images.append({'service':service, 'tag':tag, 'imageId':image_id, 'jarSha256':sha(source),
        'preparation':'Local candidate only; never assigned to development or started.'})
report = {'preparedAt':datetime.now(timezone.utc).isoformat(), 'images':images,
    'embeddedRiskApiSha256':embedded(risk, 'risk-api'), 'embeddedPlatformApiSha256':embedded(gateway, 'platform-support-api'),
    'scope':'No development tag changes, no deployment, no push. Gateway source-scope decision and stage 7 approval remain required.'}
(REQ / 'assets/stage6-final/images.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
