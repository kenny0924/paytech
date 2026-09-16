"""Explicit loopback HTTPS integration; no DDL, shared configuration, or real credentials on disk."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import uuid

import pymysql
import redis

ROOT = Path(__file__).resolve().parents[4]
DATABASE = 'mor_platform_config_validation'
LOCK = 'platform-support:auto:global-catalog'
JAVA = '/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java'
runtime = Path(tempfile.mkdtemp(prefix='client-https-'))
os.umask(0o077)
run = str(uuid.uuid4())
children = []
log_threads = []
stop = threading.Event()
emails = [f'client-https-{kind}-{run}@example.test' for kind in ('browser', 'protocol')]


def container_environment(name, internal, external):
    assert subprocess.check_output(['docker', 'context', 'show'], text=True).strip() == 'desktop-linux'
    info = json.loads(subprocess.check_output(['docker', 'inspect', name], text=True))[0]
    assert info['State']['Running'] and info['Config']['Labels']['com.docker.compose.project'] == 'mor-local'
    assert info['NetworkSettings']['Ports'][f'{internal}/tcp'] == [{'HostIp': '127.0.0.1', 'HostPort': str(external)}]
    return dict(item.split('=', 1) for item in info['Config']['Env'])


def query(connection, sql, args=()):
    with connection.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute(sql, args or None)
        return cursor.fetchall()


def scalar(connection, sql, args=()):
    return next(iter(query(connection, sql, args)[0].values()))


def counts(connection):
    return {table: scalar(connection, 'SELECT COUNT(*) FROM `' + table + '`') for table in (
        't_user_account', 't_password_credential', 't_auth_session', 't_refresh_token_generation',
        't_authentication_challenge', 't_jwt_signing_key', 't_audit_chain', 't_audit_event',
        't_audit_event_attr', 't_security_event')}


def start(name, command, env, cwd):
    child = subprocess.Popen(command, cwd=cwd, env=env, stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, text=True, start_new_session=True)
    children.append(child)

    def log():
        with (runtime / (name + '.log')).open('w') as target:
            for line in child.stdout:
                for secret in secrets:
                    line = line.replace(secret, '[REDACTED]')
                line = re.sub(r'(CLIENT_LOCAL_(?:HMAC_KEY|SIGNING_PRIVATE_KEY)_BASE64=)[^,\s]+', r'\1[REDACTED]', line)
                target.write(line)
                target.flush()
    thread = threading.Thread(target=log, daemon=True)
    thread.start()
    log_threads.append(thread)
    return child


def start_java(name, repository, main, directories, env):
    dependency_file = Path('/tmp/client-https-' + name + '-cp')
    assert dependency_file.is_file(), 'Run documented Maven test-compile/classpath preparation first'
    classpath = os.pathsep.join([str(ROOT / repository / path) for path in directories] + [dependency_file.read_text().strip()])
    child = start(name, [JAVA, '-Xmx512m', '-cp', classpath, main], env, ROOT / repository)
    state = runtime / (name + '.json')
    deadline = time.monotonic() + 60
    while not state.exists():
        assert child.poll() is None, name + ' startup failed; inspect sanitized runtime log'
        assert time.monotonic() < deadline, name + ' startup timeout'
        time.sleep(0.2)
    return json.loads(state.read_text())


def cleanup(connection, platform, before):
    # A new, empty chain is created only after the outer lock and preflight. Every
    # appended request must also have traversed this run's Nginx before deletion.
    if platform:
        observed = set()
        access = runtime / 'access.jsonl'
        if access.exists():
            observed = {json.loads(line)['requestId'] for line in access.read_text().splitlines()}
        events = query(connection, 'SELECT audit_event_id,request_id FROM t_audit_event WHERE chain_id=%s', (platform['chainId'],))
        assert all(event['request_id'] in observed for event in events), 'Audit ownership not proven; leave data for inspection'
        audit = query(connection, 'SELECT action_code,result,COUNT(*) AS count FROM t_audit_event WHERE chain_id=%s GROUP BY action_code,result', (platform['chainId'],))
        (runtime / 'audit-results.json').write_text(json.dumps(audit, indent=2))
        connection.begin()
        try:
            for event in events:
                query(connection, "DELETE FROM t_security_event WHERE source_system='PLATFORM' AND source_event_key=%s", (str(event['audit_event_id']),))
            query(connection, 'DELETE a FROM t_audit_event_attr a JOIN t_audit_event e ON e.audit_event_id=a.audit_event_id WHERE e.chain_id=%s', (platform['chainId'],))
            query(connection, 'DELETE FROM t_audit_event WHERE chain_id=%s', (platform['chainId'],))
            query(connection, 'DELETE FROM t_audit_chain WHERE chain_id=%s', (platform['chainId'],))
            query(connection, "DELETE FROM t_jwt_signing_key WHERE signing_key_id=%s AND kid=%s AND realm='CUSTOMER'", (platform['signingKeyId'], 'client-https-' + run))
            for email in emails:
                token = base64.urlsafe_b64encode(hashlib.sha256(email.encode()).digest()).decode().rstrip('=')
                accounts = query(connection, "SELECT account_id FROM t_user_account WHERE realm='CUSTOMER' AND email_vault_token=%s", (token,))
                for account in accounts:
                    identity = account['account_id']
                    query(connection, "DELETE r FROM t_refresh_token_generation r JOIN t_auth_session s ON s.session_id=r.session_id WHERE s.account_id=%s AND s.realm='CUSTOMER'", (identity,))
                    for table in ('t_auth_session', 't_authentication_challenge', 't_password_credential'):
                        query(connection, 'DELETE FROM `' + table + '` WHERE account_id=%s', (identity,))
                    query(connection, "DELETE FROM t_user_account WHERE account_id=%s AND realm='CUSTOMER' AND email_vault_token=%s", (identity, token))
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
    after = counts(connection)
    assert after == before, 'Database baseline mismatch after owned fixture cleanup'
    for prefix in ('client-https:run-' + run, 'gateway-auto:' + run):
        keys = list(cache.scan_iter(match=prefix + ':*'))
        if keys:
            cache.delete(*keys)
        assert not list(cache.scan_iter(match=prefix + ':*'))
    (runtime / 'cleanup.json').write_text(json.dumps({'database': DATABASE, 'before': before, 'after': after,
        'ownedRedisPrefixesEmpty': True, 'listenersStopped': all(child.poll() is not None for child in children)}, indent=2))


mysql = container_environment('mor-local-mysql-1', 3306, 13306)
redis_env = container_environment('mor-local-redis-1', 6379, 16379)
secrets = [mysql['MOR_PLATFORM_DB_PASSWORD'], redis_env['MOR_REDIS_PASSWORD']]
connection = pymysql.connect(host='127.0.0.1', port=13306, database=DATABASE, user='platform_app',
    password=mysql['MOR_PLATFORM_DB_PASSWORD'], autocommit=True, charset='utf8mb4', connect_timeout=5)
cache = redis.Redis(host='127.0.0.1', port=16379, password=redis_env['MOR_REDIS_PASSWORD'])
assert cache.ping()
assert scalar(connection, 'SELECT GET_LOCK(%s,0)', (LOCK,)) == 1, 'Another Platform integration owns the catalog lock'
before = counts(connection)
platform = None
try:
    assert before['t_audit_chain'] == before['t_audit_event'] == 0, 'An empty audit fixture area is required'
    assert scalar(connection, "SELECT COUNT(*) FROM t_jwt_signing_key WHERE realm='CUSTOMER'") == 0
    assert scalar(connection, "SELECT COUNT(*) FROM t_user_account WHERE realm='CUSTOMER'") == 0
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', int(os.environ.get('CLIENT_HTTPS_PORT', '0'))))
        port = probe.getsockname()[1]
    origin = 'https://localhost:' + str(port)
    env = os.environ.copy()
    env.update(CLIENT_HTTPS_RUN_ID=run, CLIENT_HTTPS_ORIGIN=origin, CLIENT_HTTPS_STATE_DIR=str(runtime),
        PLATFORM_SUPPORT_TEST_DB_URL='jdbc:mysql://127.0.0.1:13306/' + DATABASE + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
        PLATFORM_SUPPORT_TEST_DB_USERNAME='platform_app', PLATFORM_SUPPORT_TEST_DB_PASSWORD=secrets[0],
        PLATFORM_SUPPORT_TEST_REDIS_PASSWORD=secrets[1], PAYTECH_REDIS_PASSWORD=secrets[1])
    platform = start_java('platform', 'platform-support-service', 'com.paytech.platform.support.manual.authentication.ClientHttpsManualApplication',
        ['platform-support-test/target/test-classes'] + ['platform-support-' + module + '/target/classes' for module in ('web', 'access-control', 'audit-security', 'api')], env)
    env['CLIENT_HTTPS_PLATFORM_URL'] = 'http://127.0.0.1:' + str(platform['port'])
    bff = start_java('bff', 'client-console-bff', 'com.paytech.client.console.bff.manual.ClientHttpsManualApplication',
        ['paytech-client-console-bff-test/target/test-classes', 'paytech-client-console-bff-web/target/classes'], env)
    env['CLIENT_HTTPS_BFF_URL'] = 'http://127.0.0.1:' + str(bff['port'])
    gateway = start_java('gateway', 'api-gateway', 'com.paytech.gateway.manual.ClientHttpsManualApplication',
        ['api-gateway-test/target/test-classes', 'api-gateway-web/target/classes'], env)
    certificate_directory = os.environ.get('CLIENT_HTTPS_CERT_DIR')
    if certificate_directory:
        source = Path(certificate_directory).resolve()
        assert source.parent == runtime.parent.resolve() and source.name.startswith('client-https-')
        for filename in ('localhost.key', 'localhost.crt'):
            shutil.copyfile(source / filename, runtime / filename)
    else:
        subprocess.run(['/opt/homebrew/bin/openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
            '-keyout', str(runtime / 'localhost.key'), '-out', str(runtime / 'localhost.crt'), '-subj', '/CN=localhost',
            '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    configuration = '''daemon off;
pid "RUNTIME/nginx.pid";
error_log "RUNTIME/nginx-error.log";
events { worker_connections 128; }
http {
  include /opt/homebrew/etc/nginx/mime.types;
  log_format evidence escape=json '{"method":"$request_method","path":"$uri","status":$status,"requestId":"$upstream_http_x_request_id"}';
  access_log "RUNTIME/access.jsonl" evidence;
  server {
    listen 127.0.0.1:PORT ssl;
    server_name localhost;
    ssl_certificate "RUNTIME/localhost.crt";
    ssl_certificate_key "RUNTIME/localhost.key";
    root "WEBROOT";
    add_header Referrer-Policy no-referrer always;
    location /api/ {
      proxy_pass http://127.0.0.1:GATEWAY;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Entry-Channel CLIENT;
      proxy_set_header X-Forwarded-For "";
      proxy_set_header Forwarded "";
      proxy_set_header X-Forwarded-Proto "";
      proxy_set_header X-Forwarded-Host "";
      proxy_set_header Host $host;
    }
    location / { try_files $uri /index.html; }
  }
}
'''.replace('RUNTIME', str(runtime)).replace('PORT', str(port)).replace('GATEWAY', str(gateway['port'])).replace('WEBROOT', str(ROOT / 'client-console-web/dist'))
    (runtime / 'nginx.conf').write_text(configuration)
    start('nginx', ['/opt/homebrew/bin/nginx', '-p', str(runtime), '-c', str(runtime / 'nginx.conf')], os.environ.copy(), runtime)
    state = {'runId': run, 'origin': origin, 'runtime': str(runtime), 'browserEmail': emails[0],
        'protocolEmail': emails[1], 'mailboxPort': platform['mailboxPort'], 'database': DATABASE}
    Path('/tmp/client-https-current.json').write_text(json.dumps(state))
    print(json.dumps(state), flush=True)
    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, lambda *_: stop.set())
    while not stop.wait(1) and not (runtime / 'stop').exists():
        assert all(child.poll() is None for child in children), 'An integration component stopped unexpectedly'
        connection.ping(reconnect=False)
finally:
    for child in reversed(children):
        if child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait(timeout=5)
    for thread in log_threads:
        thread.join(timeout=2)
    if platform is None and (runtime / 'platform.json').exists():
        platform = json.loads((runtime / 'platform.json').read_text())
    try:
        cleanup(connection, platform, before)
    finally:
        query(connection, 'SELECT RELEASE_LOCK(%s)', (LOCK,))
        connection.close()
    print('Stopped; owned fixture cleanup verified: ' + str(runtime), flush=True)
