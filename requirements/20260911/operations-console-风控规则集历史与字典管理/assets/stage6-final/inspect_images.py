"""从从未启动的临时容器核对镜像内文件，检查后只移除本脚本创建的容器。"""
from pathlib import Path
import hashlib, json, subprocess, tempfile, zipfile

REQ = Path(__file__).resolve().parents[2]
base = json.loads((REQ / 'assets/stage6-final/images.json').read_text())
results = []
for item in base['images']:
    container = subprocess.check_output(['docker','create','--network','none',item['imageId']],text=True).strip()
    try:
        with tempfile.TemporaryDirectory(prefix='rco16-image-inspect-') as directory:
            target = Path(directory) / 'payload'
            source = '/usr/share/nginx/operations-console' if item['service'] == 'operations-console-web' else '/app/application.jar'
            subprocess.run(['docker','cp',container + ':' + source,str(target)],check=True,capture_output=True)
            if item['service'] == 'operations-console-web':
                hashes = {str(p.relative_to(target)):hashlib.sha256(p.read_bytes()).hexdigest() for p in target.rglob('*') if p.is_file()}
                assert hashes == item['staticFilesSha256']
                result = {'actualNginxRoot':source,'staticFilesMatched':len(hashes),'indexSha256':hashes['index.html']}
            else:
                digest = hashlib.sha256(target.read_bytes()).hexdigest()
                assert digest == item['jarSha256']
                result = {'jarSha256':digest}
                if item['service'] == 'operations-console-bff':
                    with zipfile.ZipFile(target) as jar:
                        text = jar.read('BOOT-INF/classes/application.yml').decode()
                        assert '19201' in text and '127.0.0.1' in text
                    result['managementListenerConfiguration'] = '19201 loopback'
            results.append({'service':item['service'],'imageId':item['imageId'],**result})
    finally:
        subprocess.run(['docker','rm',container],check=True,capture_output=True)
(REQ / 'assets/stage6-final/image-content-check.json').write_text(json.dumps({
    'scope':'Stopped temporary inspection containers only; never started; exact owned containers removed after docker cp. Running mor-test untouched.',
    'images':results},indent=2)+'\n')
print('Exact packaged bytes verified for',len(results),'images; inspection containers removed.')
