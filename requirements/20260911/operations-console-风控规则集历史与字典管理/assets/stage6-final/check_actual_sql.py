"""用刚读取的 test 版本离线校验五份实际发布 SQL；SQLite 不代表 MySQL 并发锁。"""
from pathlib import Path
import importlib.util, json, sqlite3

REQ = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('sql_checks', REQ / 'assets/stage6/preparation-scripts/check_sql.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)
base = json.loads((REQ / 'assets/stage6-final/preflight.json').read_text())
checks.sources = checks.module.generate(base, checks.registration)
for name, source in checks.sources.items():
    assert (REQ / 'db/platform-support-service' / name).read_text() == source


def database():
    c = sqlite3.connect(':memory:', isolation_level=None)
    c.create_function('IF', 3, lambda value, yes, no:yes if value else no)
    c.create_function('DATABASE', 0, lambda:'mor_platform')
    c.create_function('UTC_TIMESTAMP', 1, lambda precision:'2026-09-12 00:00:00.000000')
    c.execute('CREATE TABLE t_role(role_id INTEGER PRIMARY KEY,role_code TEXT,status TEXT,version INTEGER,update_time TEXT)')
    c.executemany('INSERT INTO t_role VALUES(?,?,?,?,NULL)', base['platform']['affectedRoles'])
    c.execute('CREATE TABLE t_role_permission(role_permission_id INTEGER PRIMARY KEY,role_id INTEGER,permission_id INTEGER,status TEXT,version INTEGER,update_time TEXT)')
    c.executemany('INSERT INTO t_role_permission VALUES(?,?,?,?,?,NULL)', base['platform']['affectedRoleBindings'])
    for table, prefix, rows in [
        ('t_gateway_route','route', [(r[0],r[1],r[-2],r[-1]) for r in base['platform']['routes'] if r[1].startswith('operations-risk-')]),
        ('t_console_resource','resource',base['platform']['resources'])]:
        c.execute(f'CREATE TABLE {table}({prefix}_id INTEGER PRIMARY KEY,{prefix}_code TEXT,status TEXT,version INTEGER,update_time TEXT)')
        c.executemany(f'INSERT INTO {table} VALUES(?,?,?,?,NULL)',rows)
    return c


def state(c):
    return {'roles':[list(r) for r in c.execute('SELECT role_id,version FROM t_role ORDER BY role_id')],
        'activeScopedBindings':checks.active(c,'t_role_permission'), 'activeScopedRoutes':checks.active(c,'t_gateway_route'),
        'activeScopedResources':checks.active(c,'t_console_resource')}


c = database(); states = {'before':state(c)}
checks.run(c,'test-maintenance.sql'); states['maintenance'] = state(c)
checks.register(c); states['registered'] = state(c)
checks.run(c,'test-enable-routes.sql'); states['routesEnabled'] = state(c)
checks.run(c,'test-expose-resources.sql'); states['resourcesExposed'] = state(c)
assert states['routesEnabled']['roles'] == [[900000000000000510,12],[900000000000000511,6]]
assert states['routesEnabled']['activeScopedBindings'] == 13
assert states['routesEnabled']['activeScopedRoutes'] == 24
assert states['resourcesExposed']['activeScopedResources'] == 17
checks.run(c,'test-close-entry.sql'); states['closed'] = state(c)
assert states['closed']['activeScopedBindings'] == states['closed']['activeScopedRoutes'] == states['closed']['activeScopedResources'] == 0
checks.run(c,'test-close-entry.sql'); assert state(c) == states['closed']
c = database(); checks.run(c,'test-maintenance.sql'); checks.run(c,'test-restore-before-migration.sql'); states['restoredBeforeMigration'] = state(c)
assert states['restoredBeforeMigration']['activeScopedBindings'] == 7
report = {'scope':'Offline SQLite transaction/state test from actual read-only preflight, only scoped rows loaded; no test SQL executed and no MySQL locking claim.',
    'fiveGeneratedFilesMatchReviewedSnapshot':True, 'syntheticEdgeCasesPassed':21, 'states':states, 'repeatedCloseStable':True}
(REQ / 'assets/stage6-final/sql-state-check.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
