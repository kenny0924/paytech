"""Continue only the recorded post-registration checkpoint; never replay DDL/DML."""
import hashlib
import json
import urllib.request
import deploy as d

d.REPORT = json.loads((d.HERE / 'deployment.json').read_text())
patch = json.loads((d.HERE / 'bff-probe-image.json').read_text())

def main():
    with d.risk_operations_rollout_lock():
        assert d.REPORT['status'] == 'STOPPED_AT_FAILED_CHECKPOINT'
        assert d.REPORT['events'][-2]['step'] == 'Starting operations-console-bff'
        assert d.run(['docker', 'context', 'show']) == 'desktop-linux'
        assert d.mysql('SELECT COUNT(*) FROM t_gateway_route;') == '51'
        assert d.mysql("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE';") == '27'
        assert d.mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk';", database='mor_risk') == '74'
        assert d.mysql("SELECT GROUP_CONCAT(version ORDER BY role_id) FROM t_role WHERE role_id IN (900000000000000510,900000000000000511);") == '11,5'
        candidates = {v['service']: v for v in d.PACKAGE['candidateImages']}
        candidates['operations-console-bff'] = patch
        assert d.container('risk-service')['imageId'] == candidates['risk-service']['imageId']
        d.event('Continuing after isolated BFF readiness probe correction', probeImage=patch)
        for service in ['operations-console-bff', 'api-gateway', 'operations-console-web']:
            candidate = candidates[service]
            assert d.run(['docker', 'image', 'inspect', '--format', '{{.Id}}', candidate['tag']]) == candidate['imageId']
            d.run(['docker', 'tag', candidate['imageId'], 'mor-test/' + service + ':development'])
            d.event('Starting ' + service)
            d.run([d.COMPOSE, 'up', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate', '--wait', '--wait-timeout', '240', service], 270)
            current = d.container(service)
            assert current['imageId'] == candidate['imageId'] and current['health'] == 'healthy'
            d.event(service + ' healthy', container=current)
        d.snapshot(27)
        d.apply('db/platform-support-service/test-enable-routes.sql', 'mor_platform')
        d.snapshot(51)
        d.apply('db/platform-support-service/test-expose-resources.sql', 'mor_platform')
        d.REPORT['finalDatabaseState'] = {
            'riskTableCount': int(d.mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk';", database='mor_risk')),
            'activeRoutes': int(d.mysql("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE';")),
            'activeOperationsResources': int(d.mysql("SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE';")),
            'roleVersions': d.mysql("SELECT role_code,version FROM t_role WHERE role_id IN (900000000000000510,900000000000000511) ORDER BY role_id;"),
            'nonOperationsRoutes': int(d.mysql("SELECT COUNT(*) FROM t_gateway_route WHERE channel<>'OPERATIONS';"))}
        assert d.REPORT['finalDatabaseState']['activeRoutes'] == 51
        assert d.REPORT['finalDatabaseState']['activeOperationsResources'] == 32
        original = {v['service']: v for v in d.BASELINE['containers']}
        d.REPORT['containers'] = [d.container(s) for s in original]
        for c in d.REPORT['containers']:
            assert c['health'] == 'healthy'
            if c['service'] not in candidates:
                assert c['containerId'] == original[c['service']]['containerId']
        with urllib.request.urlopen('http://localhost:28080', timeout=15) as response:
            d.REPORT['publicPageStatus'] = response.status
            d.REPORT['publicIndexSha256'] = hashlib.sha256(response.read()).hexdigest()
        assert d.REPORT['publicIndexSha256'] == candidates['operations-console-web']['indexSha256']
        d.REPORT['status'] = 'DEPLOYED_AWAITING_USER_FUNCTIONAL_ACCEPTANCE'
        d.event('Deployment complete; functional acceptance left to user')

if __name__ == '__main__':
    try:
        main()
    except BaseException as error:
        d.REPORT['status'] = 'STOPPED_AT_FAILED_CHECKPOINT'
        d.event('Stopped; no automatic SQL retry', errorType=type(error).__name__, error=str(error))
        raise
