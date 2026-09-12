"""One authorized local mor-test rollout; not an initializer or repeatable migration."""
from pathlib import Path
import dataclasses
import datetime
import hashlib
import json
import subprocess
import sys
import time
import urllib.request

ROOT = Path('/Users/kenny/Desktop/Workspace/Code-MoR')
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
sys.path.insert(0, str(ROOT / 'test-environment/scripts'))
from initialize import mysql, risk_operations_rollout_lock
from gateway_snapshot_metrics import decode_prometheus
from gateway_snapshot_wait import ComposeFetcher

PACKAGE = json.loads((REQ / 'assets/stage6-final/release-package.json').read_text())
BASELINE = json.loads((REQ / 'assets/stage6-final/preflight.json').read_text())
COMPOSE = str(ROOT / 'test-environment/compose.sh')
REPORT = {'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'authorization': 'User accepted Gateway scope and requested deployment; then explicitly waived backups and delegated functional acceptance to the user.',
          'backupPerformed': False, 'functionalAcceptancePerformed': False,
          'syntheticBusinessWrites': False, 'events': []}

def event(name, **data):
    REPORT['events'].append({'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'step': name, **data})
    (HERE / 'deployment.json').write_text(json.dumps(REPORT, ensure_ascii=False, indent=2) + '\n')
    print(name, flush=True)

def run(args, timeout=60):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(args[:4]) + '; exit=' + str(result.returncode))
    return result.stdout.strip()

def container(service):
    c = json.loads(run(['docker', 'inspect', 'mor-test-' + service + '-1']))[0]
    assert c['Config']['Labels']['com.docker.compose.project'] == 'mor-test'
    return {'service': service, 'containerId': c['Id'], 'imageId': c['Image'],
            'health': c['State'].get('Health', {}).get('Status'), 'status': c['State']['Status']}

def apply(path, database):
    text = (REQ / path).read_text()
    assert hashlib.sha256(text.encode()).hexdigest() == PACKAGE['sqlSha256'][path]
    event('Applying ' + path)
    mysql(text, database=database, timeout_seconds=90)
    event('Applied ' + path, database=database, sha256=PACKAGE['sqlSha256'][path])

def snapshot(count):
    deadline = time.monotonic() + 60
    while True:
        value = decode_prometheus(ComposeFetcher()('http://127.0.0.1:19080/actuator/prometheus', 12))
        if value.valid and value.refresh_result == 'SUCCESS' and value.failure_reason == 'NONE' and not value.last_valid_in_use and value.operations_route_count == count:
            event('Gateway loaded ' + str(count) + ' Operations routes', snapshot=dataclasses.asdict(value))
            return
        if time.monotonic() >= deadline:
            raise RuntimeError('Gateway did not load expected route count: ' + str(count))
        time.sleep(2)

def main():
    assert not (HERE / 'deployment.json').exists(), 'Do not replay this rollout; inspect completed checkpoints.'
    with risk_operations_rollout_lock():
        assert run(['docker', 'context', 'show']) == 'desktop-linux'
        assert run(['docker', 'context', 'inspect', '--format', '{{.Endpoints.docker.Host}}', 'desktop-linux']).startswith('unix://')
        original = {v['service']: v for v in BASELINE['containers']}
        for service, old in original.items():
            current = container(service)
            assert current['containerId'] == old['containerId'] and current['imageId'] == old['imageId'], service + ' changed'
            assert current['health'] == 'healthy', service + ' is not healthy'
        for v in PACKAGE['candidateImages']:
            assert run(['docker', 'image', 'inspect', '--format', '{{.Id}}', v['tag']]) == v['imageId']
        for path, sha in PACKAGE['sqlSha256'].items():
            assert hashlib.sha256((REQ / path).read_bytes()).hexdigest() == sha, path
        for path, sha in PACKAGE['configSha256'].items():
            assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == sha, path
        run([COMPOSE, 'config', '--quiet'])
        assert mysql("SELECT GROUP_CONCAT(CONCAT(role_id,':',version) ORDER BY role_id) FROM t_role WHERE role_id IN (900000000000000510,900000000000000511);") == '900000000000000510:9,900000000000000511:4'
        assert mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk';", database='mor_risk') == '72'
        assert mysql('SELECT COUNT(*) FROM t_gateway_route;') == '37'
        event('Exact mor-test target, candidate images and SQL checked')
        run([COMPOSE, 'stop', '--timeout', '30', 'operations-console-web', 'operations-console-bff', 'risk-service'], 110)
        event('Web, BFF and Risk stopped; backups skipped per user instruction')
        assert mysql("SELECT COUNT(*) FROM information_schema.processlist WHERE DB='mor_risk' AND USER<>'root';") == '0'
        apply('db/platform-support-service/test-maintenance.sql', 'mor_platform')
        snapshot(27)
        apply('db/risk-compliance-service/ddl.sql', 'mor_risk')
        apply('db/platform-support-service/ddl.sql', 'mor_platform')
        apply('db/platform-support-service/dml.sql', 'mor_platform')
        candidates = {v['service']: v for v in PACKAGE['candidateImages']}
        for service in ['risk-service', 'operations-console-bff', 'api-gateway', 'operations-console-web']:
            candidate = candidates[service]
            run(['docker', 'tag', candidate['imageId'], 'mor-test/' + service + ':development'])
            event('Starting ' + service)
            run([COMPOSE, 'up', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate', '--wait', '--wait-timeout', '240', service], 270)
            current = container(service)
            assert current['imageId'] == candidate['imageId'] and current['health'] == 'healthy'
            event(service + ' healthy', container=current)
        snapshot(27)
        apply('db/platform-support-service/test-enable-routes.sql', 'mor_platform')
        snapshot(51)
        apply('db/platform-support-service/test-expose-resources.sql', 'mor_platform')
        REPORT['finalDatabaseState'] = {
            'riskTableCount': int(mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk';", database='mor_risk')),
            'activeRoutes': int(mysql("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE';")),
            'activeOperationsResources': int(mysql("SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE';")),
            'roleVersions': mysql("SELECT role_code,version FROM t_role WHERE role_id IN (900000000000000510,900000000000000511) ORDER BY role_id;"),
            'nonOperationsRoutes': int(mysql("SELECT COUNT(*) FROM t_gateway_route WHERE channel<>'OPERATIONS';"))}
        assert REPORT['finalDatabaseState']['riskTableCount'] == 74
        assert REPORT['finalDatabaseState']['activeRoutes'] == 51
        assert REPORT['finalDatabaseState']['activeOperationsResources'] == 32
        REPORT['containers'] = [container(s) for s in original]
        for c in REPORT['containers']:
            assert c['health'] == 'healthy'
            if c['service'] not in candidates:
                assert c['containerId'] == original[c['service']]['containerId']
        with urllib.request.urlopen('http://localhost:28080', timeout=15) as response:
            REPORT['publicPageStatus'] = response.status
            REPORT['publicIndexSha256'] = hashlib.sha256(response.read()).hexdigest()
        assert REPORT['publicIndexSha256'] == candidates['operations-console-web']['indexSha256']
        REPORT['status'] = 'DEPLOYED_AWAITING_USER_FUNCTIONAL_ACCEPTANCE'
        event('Deployment complete; functional acceptance left to user')

if __name__ == '__main__':
    try:
        main()
    except BaseException as error:
        REPORT['status'] = 'STOPPED_AT_FAILED_CHECKPOINT'
        event('Stopped; no automatic SQL retry', errorType=type(error).__name__, error=str(error))
        raise
