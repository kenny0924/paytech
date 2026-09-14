"""Local manual launcher. Source local-environment/.env.local before use; never prints secrets."""
import os, sys, json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[6]
runtime = Path('/tmp/plt-tenant-access-stage5')
service = sys.argv[1]
repos = {
    'platform': ('platform-support-service', 'platform-support-test', 'PlatformAcceptance.java'),
    'bff': ('operations-console-bff', 'paytech-operations-console-bff-test', 'BffAcceptance.java'),
    'gateway': ('api-gateway', 'api-gateway-test', 'GatewayAcceptance.java'),
}
repo, module, main = repos[service]
report = next((root/repo/module/'target/surefire-reports').glob('TEST-*.xml'))
properties = ET.parse(report).find('properties')
classpath = next(p.get('value') for p in properties if p.get('name') == 'java.class.path')
env = os.environ.copy()
env['PLATFORM_SUPPORT_TEST_DB_URL'] = 'jdbc:mysql://127.0.0.1:13306/mor_platform_config_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
env['PLATFORM_SUPPORT_TEST_DB_USERNAME'] = 'platform_app'
env['PLATFORM_SUPPORT_TEST_DB_PASSWORD'] = env['MOR_PLATFORM_DB_PASSWORD']
env['PLATFORM_SUPPORT_TEST_REDIS_PASSWORD'] = env['MOR_REDIS_PASSWORD']
env['PAYTECH_REDIS_PASSWORD'] = env['MOR_REDIS_PASSWORD']
java = subprocess.check_output(['/usr/libexec/java_home', '-v', '25'], text=True).strip() + '/bin/java'
params = [str(runtime/'platform.json')]
if service != 'platform':
    platform = json.loads((runtime/'platform.json').read_text())
    web = json.loads((runtime/'web.json').read_text())
    platform_uri = f"http://127.0.0.1:{platform['port']}"
    if service == 'bff':
        params = [platform_uri, web['origin'], str(runtime/'bff.json')]
    else:
        bff = json.loads((runtime/'bff.json').read_text())
        params = [platform_uri, f"http://127.0.0.1:{bff['port']}", web['origin'], platform['marker'], str(runtime/'gateway.json')]
args = [java, '-Xmx512m', '--class-path', classpath, str(Path(__file__).with_name(main)), *params]
os.chdir(root/repo)
os.execve(java, args, env)
