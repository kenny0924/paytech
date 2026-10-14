"""PayerMax 接入本机联调启动器。先载入 local-environment/.env.local（不输出内容），依次 payermax-mock → platform → bff → gateway；
Web 入口沿用外部 API 2e 的 web.mjs。Platform 使用本目录的 PlatformPayerMaxE2e，合成 PayerMax 与其共用 Platform 测试模块 classpath（Jackson）；
BFF、Gateway 沿用 2e 启动器。运行文件写入 E2E_RUNTIME 目录（权限 0700），不进入仓库。"""
import os, sys, json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

here = Path(__file__).resolve().parent
root = here.parents[4]
stage2e = root / 'requirements/20260914/platform-support-外部API配置与执行/assets/stage2e/harness'
runtime = Path(os.environ['E2E_RUNTIME'])
service = sys.argv[1]
java_home = subprocess.check_output(['/usr/libexec/java_home', '-v', '25'], text=True).strip()
java = java_home + '/bin/java'


def platform_classpath():
    report = next((root / 'platform-support-service/platform-support-test/target/surefire-reports').glob('TEST-*.xml'))
    return next(p.get('value') for p in ET.parse(report).find('properties') if p.get('name') == 'java.class.path')


def maven_classpath(project, module, scope, extra):
    out = runtime / f'{module}.cp'
    subprocess.check_call([str(root / project / 'mvnw'), '-q', '-o', '-pl', module, 'dependency:build-classpath',
                           f'-Dmdep.outputFile={out}', f'-Dmdep.includeScope={scope}'], cwd=root / project,
                          env={**os.environ, 'JAVA_HOME': java_home})
    return os.pathsep.join([*extra, out.read_text().strip()])


env = os.environ.copy()
if service == 'payermax-mock':
    os.execve(java, [java, '-Xmx256m', '--class-path', platform_classpath(), str(here / 'PayerMaxMock.java')], env)
env['PLATFORM_SUPPORT_TEST_DB_PASSWORD'] = env['MOR_PLATFORM_DB_PASSWORD']
env['PLATFORM_SUPPORT_TEST_REDIS_PASSWORD'] = env['MOR_REDIS_PASSWORD']
env['PAYTECH_REDIS_PASSWORD'] = env['MOR_REDIS_PASSWORD']
if service == 'platform':
    classpath, main, params, cwd = platform_classpath(), str(here / 'PlatformPayerMaxE2e.java'), [str(runtime / 'platform.json')], root / 'platform-support-service'
else:
    platform = json.loads((runtime / 'platform.json').read_text())
    web = json.loads((runtime / 'web.json').read_text())
    platform_uri = f"http://127.0.0.1:{platform['port']}"
    if service == 'bff':
        web_module = root / 'operations-console-bff/paytech-operations-console-bff-web'
        classpath = maven_classpath('operations-console-bff', 'paytech-operations-console-bff-web', 'runtime', [str(web_module / 'target/classes')])
        main, params, cwd = str(stage2e / 'BffE2e.java'), [platform_uri, web['origin'], str(runtime / 'bff.json')], root / 'operations-console-bff'
    else:
        bff = json.loads((runtime / 'bff.json').read_text())
        test = root / 'api-gateway/api-gateway-test'
        classpath = maven_classpath('api-gateway', 'api-gateway-test', 'test', [str(test / 'target/test-classes')])
        main = str(stage2e / 'GatewayE2e.java')
        params = [platform_uri, f"http://127.0.0.1:{bff['port']}", web['origin'], platform['marker'], web['dynamicService'],
                  str(runtime / 'gateway.json')]
        cwd = root / 'api-gateway'
os.chdir(cwd)
os.execve(java, [java, '-Xmx512m', '--class-path', classpath, main, *params], env)
