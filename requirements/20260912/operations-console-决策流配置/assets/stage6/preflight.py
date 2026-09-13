"""Read-only mor-test release inventory. Never applies SQL or changes running images."""
from pathlib import Path
import datetime, hashlib, json, re, sqlite3, subprocess, sys

ROOT = Path(__file__).resolve().parents[5]
REQ = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'test-environment/scripts'))
from initialize import mysql


def query(db, sql):
    return [line.split('\t') for line in mysql('START TRANSACTION READ ONLY;\n' + sql + '\nCOMMIT;', database=db, timeout_seconds=30).splitlines()]


def registration():
    connection = sqlite3.connect(':memory:')
    connection.row_factory = sqlite3.Row
    result = {}
    source = (REQ / 'db/platform-support-service/dml.sql').read_text()
    for table, cols, values in re.findall(r'INSERT INTO (t_\w+)\s*\((.*?)\)\s*VALUES\s*(.*?);', source, re.S):
        connection.execute('CREATE TABLE ' + table + ' (' + cols + ')')
        connection.execute('INSERT INTO ' + table + ' (' + cols + ') VALUES ' + values, {'rco17_time':'2026-09-12 00:00:00.000000'})
        result[table] = [dict(r) for r in connection.execute('SELECT * FROM ' + table)]
    assert sum(map(len, result.values())) == 118
    return result


def main():
    report = {'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(), 'scope':'Read-only test inventory; no migration or deployment', 'dockerContext':subprocess.check_output(['docker','context','show'], text=True).strip(), 'containers':[]}
    assert report['dockerContext'] == 'desktop-linux'
    for service in ['mysql','redis','nacos','risk-service','operations-console-bff','operations-console-web','platform-support-service','api-gateway','nginx','vault-service','rabbitmq']:
        c = json.loads(subprocess.check_output(['docker','inspect','mor-test-'+service+'-1'], text=True))[0]
        assert c['Config']['Labels']['com.docker.compose.project'] == 'mor-test'
        assert c['Config']['Labels']['com.docker.compose.service'] == service
        report['containers'].append({'service':service, 'containerId':c['Id'], 'imageId':c['Image'], 'imageTag':c['Config']['Image'], 'health':c['State'].get('Health',{}).get('Status'), 'startedAt':c['State']['StartedAt'], 'ports':c['NetworkSettings']['Ports']})
    assert all(c['health'] == 'healthy' for c in report['containers'])
    report['risk'] = {
        'identity':query('mor_risk', 'SELECT DATABASE(),@@server_uuid,@@version;'),
        'tableCount':query('mor_risk', 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();'),
        'flowTables':query('mor_risk', "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('t_decision_flow','t_decision_flow_version','t_decision_flow_node','t_decision_flow_edge');"),
        'precheck':query('mor_risk', (REQ/'db/risk-compliance-service/simplification/precheck.sql').read_text()),
        'roots':query('mor_risk', 'SELECT rule_set_id,decision_point_id,current_rule_set_version_id,status,version FROM t_rule_set ORDER BY rule_set_id;'),
        'versionHashes':query('mor_risk', 'SELECT rule_set_version_id,version_no,status,content_format,content_hash FROM t_rule_set_version ORDER BY rule_set_version_id;'),
        'commands':query('mor_risk', 'SELECT operation_type,status,COUNT(*) FROM t_command_idempotency GROUP BY operation_type,status ORDER BY operation_type,status;'),
        'runtimeCounts':query('mor_risk', 'SELECT (SELECT COUNT(*) FROM t_decision_evaluation),(SELECT COUNT(*) FROM t_action_effect);'),
        'migrationColumns':query('mor_risk', "SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('t_rule_set_version','t_rule_operand','t_rule_action_parameter') AND column_name IN ('input_field_id','field_code','input_contract_code','input_contract_version') ORDER BY table_name,column_name;")}
    assert not report['risk']['flowTables']
    assert all(int(count) == 0 for key,count in report['risk']['precheck'] if key.startswith('bad_'))
    assert len(report['risk']['migrationColumns']) == 2
    tables = ['t_gateway_target','t_role','t_permission','t_role_permission','t_console_resource','t_gateway_route','t_gateway_route_header','t_gateway_route_query_param','t_gateway_guard_rule']
    platform = {}
    queries = []
    for table in tables:
        cols = [r[0] for r in query('mor_platform', "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+table+"' ORDER BY ordinal_position;")]
        pairs = ','.join("'"+col+"',"+col for col in cols)
        queries.append("SELECT JSON_OBJECT('table','"+table+"','row',JSON_OBJECT("+pairs+')) FROM '+table+' ORDER BY '+cols[0]+';')
        platform[table] = []
    for line in mysql('START TRANSACTION READ ONLY;\n'+'\n'.join(queries)+'\nCOMMIT;', timeout_seconds=30).splitlines():
        row = json.loads(line)
        platform[row['table']].append(row['row'])
    report['platform'] = {'identity':query('mor_platform', 'SELECT DATABASE(),@@server_uuid,@@version;'), 'catalog':platform, 'reservedCollisions':{}}
    for table,rows in registration().items():
        key = next(iter(rows[0]))
        ids = {r[key] for r in rows}
        collisions = sum(r[key] in ids for r in platform[table])
        for code in ['permission_code','resource_code','route_code']:
            if code in rows[0]:
                codes = {r[code] for r in rows}
                collisions += sum(r[code] in codes for r in platform[table])
        report['platform']['reservedCollisions'][table] = collisions
        assert collisions == 0, table
    (OUT/'preflight.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'health':'11 healthy', 'risk':report['risk']['precheck'], 'routes':len(platform['t_gateway_route']), 'activeResources':sum(r['status']=='ACTIVE' for r in platform['t_console_resource']), 'roles':[[r['role_id'],r['role_code'],r['version']] for r in platform['t_role']], 'registrationConflicts':0}, ensure_ascii=False))


if __name__ == '__main__':
    main()
