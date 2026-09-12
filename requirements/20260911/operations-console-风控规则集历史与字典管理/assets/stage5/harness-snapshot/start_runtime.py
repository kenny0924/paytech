import os, shlex, socket, subprocess, sys
from pathlib import Path

root = Path('/Users/kenny/Desktop/Workspace/Code-MoR')
env = os.environ.copy()
java_home = subprocess.check_output(['/usr/libexec/java_home', '-v', '25'], text=True).strip()
env['JAVA_HOME'] = java_home
if sys.argv[1] == 'risk':
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 18104))
    for line in (root/'local-environment/.env.local').read_text().splitlines():
        if line.startswith('MOR_RISK_DB_PASSWORD=') or line.startswith('MOR_REDIS_PASSWORD='):
            key, value = line.split('=', 1)
            env[{'MOR_RISK_DB_PASSWORD':'RISK_DB_PASSWORD', 'MOR_REDIS_PASSWORD':'RISK_REDIS_PASSWORD'}[key]] = shlex.split(value, comments=True)[0]
    env.update(RISK_DB_URL='jdbc:mysql://127.0.0.1:13306/mor_risk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
               RISK_DB_USERNAME='risk_app', RISK_SNOWFLAKE_REGION_ID='0', RISK_SNOWFLAKE_WORKER_ID='2')
    args = [java_home+'/bin/java', '-jar', str(root/'risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar'),
            '--spring.cloud.discovery.enabled=false', '--spring.cloud.service-registry.auto-registration.enabled=false',
            '--spring.cloud.nacos.discovery.enabled=false', '--spring.cloud.nacos.discovery.register-enabled=false',
            '--redis.key-prefix=rco16-stage5']
else:
    classpath = Path('/tmp/rco16-stage5/bff-classpath.txt').read_text()
    args = [java_home+'/bin/java', '--class-path', classpath, '/tmp/rco16-stage5/StageFiveBff.java']
os.execve(args[0], args, env)
