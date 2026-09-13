"""Resume the recorded checkpoint only; approved DDL/DML are never replayed."""
import deploy as d
import json, time, urllib.request

def main():
    d.REPORT=json.loads((d.HERE/'deployment.json').read_text())
    assert d.REPORT['status']=='STOPPED_AT_FAILED_CHECKPOINT'
    assert any(e['step']=='Risk backfill committed after same-transaction checks' for e in d.REPORT['events'])
    assert not any(e['step']=='Final DDL complete; original roots, history, releases and receipts unchanged' for e in d.REPORT['events'])
    with d.risk_operations_rollout_lock():
        result=json.loads((d.HERE/'migration-history-verification.json').read_text())
        assert result['helperSha256']==d.sha((d.HERE/'ReadRiskHistory.java').read_bytes())
        assert result['candidateJarSha256']==d.IMAGES['images']['risk-service']['jarSha256']
        original=d.REPORT['events'][0]['preserved']
        assert d.preserved()==original
        assert d.rows('mor_platform','SELECT role_id,version FROM t_role WHERE role_id IN (900000000000000510,900000000000000511) ORDER BY role_id')==((900000000000000510,13),(900000000000000511,7))
        for service in d.IMAGES['images']:
            c=json.loads(d.run(['docker','inspect','mor-test-'+service+'-1']))[0]
            assert c['State']['Status']=='exited'
        with d.connect('mor_risk') as c:
            with c.cursor() as cursor:
                cursor.execute('START TRANSACTION READ ONLY')
                for statement in d.statements(d.checked(d.REQ/'db/risk-compliance-service/simplification/postcheck.sql')):
                    cursor.execute(statement);assert all(row[1]==0 for row in cursor.fetchall())
        d.event('Corrected historical check passed against original baseline',verification=result)
        d.ddl(d.REQ/'db/risk-compliance-service/simplification/finalize-ddl.sql')
        assert d.preserved()==original
        d.event('Final DDL complete; original roots, history, releases and receipts unchanged')
        d.platform_transaction('02-registration.sql');d.snapshot(27)
        for service in ['risk-service','operations-console-bff','operations-console-web']:
            item=d.IMAGES['images'][service]
            d.run(['docker','tag',item['imageId'],'mor-test/'+service+':development'])
            d.run([d.COMPOSE,'up','-d','--no-deps','--no-build','--pull','never','--force-recreate',service],60)
            deadline=time.monotonic()+240
            while True:
                c=json.loads(d.run(['docker','inspect','mor-test-'+service+'-1']))[0]
                assert c['Image']==item['imageId']
                if c['State'].get('Health',{}).get('Status')=='healthy':break
                if time.monotonic()>deadline or c['State']['Status'] in ['exited','dead']:raise RuntimeError(service+' not healthy')
                time.sleep(3)
            d.event(service+' healthy',containerId=c['Id'],imageId=c['Image'])
        d.platform_transaction('03-enable.sql');d.snapshot(61)
        assert d.rows('mor_risk',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")[0][0]==78
        assert d.rows('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0]==35
        with urllib.request.urlopen('http://localhost:28080',timeout=15) as response:index=d.sha(response.read())
        assert index==d.IMAGES['images']['operations-console-web']['distFilesSha256']['index.html']
        for original in d.BASELINE['containers']:
            if original['service'] in d.IMAGES['images']:continue
            c=json.loads(d.run(['docker','inspect',original['containerId']]))[0]
            assert c['Image']==original['imageId'] and c['State']['StartedAt']==original['startedAt']
        d.REPORT['status']='DEPLOYED_PENDING_BOUNDED_ACCEPTANCE'
        d.event('Three services deployed; 61 routes and new public HTML verified',indexSha256=index)

if __name__=='__main__':
    try:main()
    except BaseException as error:
        d.REPORT['status']='STOPPED_AT_FAILED_CHECKPOINT'
        d.event('Stopped without replaying SQL',errorType=type(error).__name__)
        raise
