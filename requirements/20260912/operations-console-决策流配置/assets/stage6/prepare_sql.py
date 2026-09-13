"""Prepare environment-specific manual SQL and simulate its CAS transitions in SQLite only."""
from preflight import ROOT, REQ, OUT, registration
import copy, hashlib, json, re, sqlite3

SQL = REQ/'db/platform-support-service/test-release'
SQL.mkdir(exist_ok=True)
baseline=json.loads((OUT/'preflight.json').read_text())['platform']['catalog']
catalog=copy.deepcopy(baseline)
new=registration()
admin=900000000000000510
routes=[r for r in catalog['t_gateway_route'] if r['route_code'].startswith('operations-risk-') and r['status']=='ACTIVE']
resources=[r for r in catalog['t_console_resource'] if r['resource_code'].startswith('OPERATIONS_RISK_') and r['status']=='ACTIVE']
closed_route_ids={r['route_id'] for r in routes}
closed_resource_ids={r['resource_id'] for r in resources}
permissions={r['permission_id'] for r in routes+resources if r['permission_id'] is not None}
for row in catalog['t_gateway_route']:
    if row['status']=='ACTIVE' and row['route_id'] not in closed_route_ids: permissions.discard(row['permission_id'])
for row in catalog['t_console_resource']:
    if row['status']=='ACTIVE' and row['resource_id'] not in closed_resource_ids: permissions.discard(row['permission_id'])
bindings=[r for r in catalog['t_role_permission'] if r['status']=='ACTIVE' and r['permission_id'] in permissions]
role_ids={admin,*[r['role_id'] for r in bindings]}
roles=[r for r in catalog['t_role'] if r['role_id'] in role_ids]
assert (len(routes),len(resources),len(bindings),len(roles))==(24,17,13,2)
manifest={'baseline':'../../../assets/stage6/preflight.json','database':'127.0.0.1:23306/mor_platform','registrationSource':'../dml.sql','stages':{}}
keys={'t_gateway_route':'route_id','t_console_resource':'resource_id','t_role_permission':'role_permission_id','t_role':'role_id'}
primary_keys={**keys,'t_gateway_target':'target_id','t_permission':'permission_id','t_gateway_route_header':'route_header_id','t_gateway_route_query_param':'route_query_param_id','t_gateway_guard_rule':'guard_rule_id'}

def transition(table, rows, status=None):
    statements=[]
    for row in rows:
        key=keys[table];identity=row[key];version=row['version'];before=row['status']
        assignment='version=version+1, update_time=@rco17_time'
        if status is not None: assignment="status='"+status+"', "+assignment
        statements.append(f"UPDATE {table} SET {assignment} WHERE {key}={identity} AND status='{before}' AND version={version};")
        statements.append(f"SELECT '{table}:{identity}' AS checked_row, ROW_COUNT() AS expected_one;")
        row['version']+=1
        if status is not None:row['status']=status
    return statements

def write(name, statements, expected_insert_rows=0):
    text="-- RCO-17 test only; generated from stage6/preflight.json, not yet executed.\n-- Each expected_one must be 1; otherwise ROLLBACK. COMMIT only in this same session after all checks.\nSTART TRANSACTION;\nSET @rco17_time=UTC_TIMESTAMP(6);\n"+'\n'.join(statements)+'\n-- Deliberately no COMMIT: inspect results, then explicitly COMMIT or ROLLBACK.\n'
    (SQL/name).write_text(text)
    manifest['stages'][name]={'sha256':hashlib.sha256(text.encode()).hexdigest(),'updates':sum(s.startswith('UPDATE ') for s in statements),'insertedRows':expected_insert_rows}

maintenance=transition('t_gateway_route',routes,'DISABLED')+transition('t_console_resource',resources,'DISABLED')+transition('t_role_permission',bindings,'REVOKED')+transition('t_role',roles)
write('01-maintenance.sql',maintenance)
source=(REQ/'db/platform-support-service/dml.sql').read_text()
inserts=[m.group(0) for m in re.finditer(r'INSERT INTO t_\w+\s*\(.*?;',source,re.S)]
registration_sql=inserts+transition('t_role',[r for r in roles if r['role_id']==admin])
write('02-registration.sql',registration_sql,118)
for table,rows in new.items():catalog[table].extend(rows)
disabled=copy.deepcopy(catalog)
enable=transition('t_gateway_route',routes+new['t_gateway_route'],'ACTIVE')+transition('t_console_resource',resources+new['t_console_resource'],'ACTIVE')+transition('t_role_permission',bindings+new['t_role_permission'],'ACTIVE')+transition('t_role',roles)
write('03-enable.sql',enable)
enabled=copy.deepcopy(catalog)
close=transition('t_gateway_route',routes+new['t_gateway_route'],'DISABLED')+transition('t_console_resource',resources+new['t_console_resource'],'DISABLED')+transition('t_role_permission',bindings+new['t_role_permission'],'REVOKED')+transition('t_role',roles)
write('04-emergency-close-after-enable.sql',close)
disabled_roles=[r for r in disabled['t_role'] if r['role_id'] in role_ids]
reopen=transition('t_gateway_route',[r for r in disabled['t_gateway_route'] if r['route_id'] in closed_route_ids],'ACTIVE')+transition('t_console_resource',[r for r in disabled['t_console_resource'] if r['resource_id'] in closed_resource_ids],'ACTIVE')+transition('t_role_permission',[r for r in disabled['t_role_permission'] if r['role_permission_id'] in {b['role_permission_id'] for b in bindings}],'ACTIVE')+transition('t_role',disabled_roles)
write('05-restore-old-before-enable.sql',reopen)

# Execute generated statements against an isolated in-memory copy, assert affected rows and unaffected facts.
def connection():
    c=sqlite3.connect(':memory:');c.row_factory=sqlite3.Row
    for table,rows in baseline.items():
        cols=list(rows[0]);c.execute('CREATE TABLE '+table+' ('+','.join(cols)+')')
        c.executemany('INSERT INTO '+table+' VALUES ('+','.join('?' for _ in cols)+')',[[r[col] for col in cols] for r in rows])
    return c
def execute(c, names):
    for name in names:
        source=(SQL/name).read_text()
        for match in re.finditer(r'(?:UPDATE|INSERT INTO)\s+t_\w+\s.*?;',source,re.S):
            statement=match.group(0)
            count=c.execute(statement,{'rco17_time':'2026-09-12 00:00:00.000000'}).rowcount
            if statement.startswith('UPDATE'):assert count==1,(name,statement,count)
    for table,rows in baseline.items():
        key=primary_keys[table];changes=set()
        if table in keys:
            changes={'status','version','update_time'}
        for row in rows:
            after=dict(c.execute('SELECT * FROM '+table+' WHERE '+key+'=?',(row[key],)).fetchone())
            differences=[col for col,value in row.items() if col not in changes and after[col]!=value]
            assert not differences,(table,row[key],differences)
    return c
c=execute(connection(),['01-maintenance.sql','02-registration.sql','03-enable.sql'])
assert c.execute("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE'").fetchone()[0]==61
assert c.execute("SELECT version FROM t_role WHERE role_id=?",(admin,)).fetchone()[0]==15
execute(c,['04-emergency-close-after-enable.sql'])
assert c.execute("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE'").fetchone()[0]==27
c=execute(connection(),['01-maintenance.sql','02-registration.sql','05-restore-old-before-enable.sql'])
assert c.execute("SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE'").fetchone()[0]==51
# The stale first maintenance CAS cannot silently succeed twice.
first_update=next(s for s in maintenance if s.startswith('UPDATE'))
c=execute(connection(),['01-maintenance.sql'])
assert c.execute(first_update,{'rco17_time':'2026-09-12 00:00:00.000000'}).rowcount==0
manifest['offlineChecks']='Enable 61 routes; emergency close 27; restore old 51; stale CAS 0; immutable columns preserved. SQLite simulation, not MySQL execution.'
manifest['affected']={'oldRoutes':24,'oldResources':17,'oldActiveBindings':13,'roles':[{'id':r['role_id'],'initialVersion':r['version']} for r in baseline['t_role'] if r['role_id'] in role_ids]}
(SQL/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest))
