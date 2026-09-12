"""离线验证发布事务的状态/授权不变量；不连接数据库，不模拟 MySQL 锁。"""
from pathlib import Path
import importlib.util
import json
import re
import sqlite3

DIRECTORY = Path(__file__).resolve().parent
REQ = DIRECTORY.parents[2]
spec = importlib.util.spec_from_file_location('release_sql', DIRECTORY / 'prepare_sql.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
ADMIN = module.ADMIN
VIEWER = 900000000000000511
CUSTOM = 800000000000000010
PERMISSIONS = list(range(900000000000000536, 900000000000000542))
registration = json.loads((REQ / 'db/platform-support-service/registration.json').read_text())
registration_sql = (REQ / 'db/platform-support-service/dml.sql').read_text()


def sql_literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int) or value == '@rco16_registration_time' or re.fullmatch(r'\d{18}', str(value)):
        return str(value)
    return "'" + value.replace("'", "''") + "'"


# 清单与人工执行的 SQL 必须逐元组一致，避免文档说 REVOKED 而其中一份仍授予 ACTIVE。
assert sum(len(t['rows']) for t in registration['tables'].values()) == 182
for table, block in registration['tables'].items():
    for row in block['rows']:
        assert registration_sql.count('(' + ', '.join(map(sql_literal, row)) + ')') == 1, (table, row[0])
binding_table = registration['tables']['t_role_permission']
assert len(binding_table['rows']) == 6
assert all(row[binding_table['columns'].index('status')] == 'REVOKED' for row in binding_table['rows'])
# 故意使用不同版本并包含一个自定义角色，防止生成器假定只有固定管理员/Viewer。
base = {'platform': {
    'routes': [[str(900000000000001024 + i), 'operations-risk-fixture-' + str(i), 'OPERATIONS', 'GET', '/risk/' + str(i), 'ACTIVE', str(5 + i)] for i in range(10)],
    'resources': [[str(900000000000000565 + i), 'RISK_FIXTURE_' + str(i), 'ACTIVE', str(3 + i)] for i in range(7)],
    'closedPermissionIds': list(map(str, PERMISSIONS)),
    'affectedRoleBindings': [[str(7000 + i), str(ADMIN), str(p), 'ACTIVE', str(2 + i)] for i, p in enumerate(PERMISSIONS)]
        + [['7100', str(VIEWER), str(PERMISSIONS[0]), 'ACTIVE', '4'], ['7200', str(CUSTOM), str(PERMISSIONS[0]), 'ACTIVE', '5']],
    'affectedRoles': [[str(ADMIN), 'PLATFORM_ADMIN', 'ACTIVE', '9'], [str(VIEWER), 'OPERATIONS_VIEWER', 'ACTIVE', '4'], [str(CUSTOM), 'FIXTURE_CUSTOM', 'ACTIVE', '7']]}}
sources = module.generate(base, registration)
checks = ['all 182 registration tuples match the manifest, including six REVOKED grants']


def database():
    c = sqlite3.connect(':memory:', isolation_level=None)
    c.create_function('IF', 3, lambda value, yes, no: yes if value else no)
    c.create_function('DATABASE', 0, lambda: 'mor_platform')
    c.create_function('UTC_TIMESTAMP', 1, lambda precision: '2026-09-12 00:00:00.000000')
    c.execute('CREATE TABLE t_role(role_id INTEGER PRIMARY KEY,role_code TEXT,status TEXT,version INTEGER,update_time TEXT)')
    c.executemany('INSERT INTO t_role VALUES(?,?,?,?,NULL)', base['platform']['affectedRoles'])
    c.execute('CREATE TABLE t_role_permission(role_permission_id INTEGER PRIMARY KEY,role_id INTEGER,permission_id INTEGER,status TEXT,version INTEGER,update_time TEXT)')
    c.executemany('INSERT INTO t_role_permission VALUES(?,?,?,?,?,NULL)', base['platform']['affectedRoleBindings'])
    c.execute("INSERT INTO t_role_permission VALUES(7300,?,?,'REVOKED',8,NULL)", (CUSTOM, PERMISSIONS[1]))
    for table, prefix, rows in [('t_gateway_route', 'route', [(r[0], r[1], r[-2], r[-1]) for r in base['platform']['routes']]), ('t_console_resource', 'resource', base['platform']['resources'])]:
        c.execute(f'CREATE TABLE {table}({prefix}_id INTEGER PRIMARY KEY,{prefix}_code TEXT,status TEXT,version INTEGER,update_time TEXT)')
        c.executemany(f'INSERT INTO {table} VALUES(?,?,?,?,NULL)', rows)
    return c


def run(c, name):
    last = [0]
    values = {}
    c.create_function('ROW_COUNT', 0, lambda: last[0])
    try:
        for statement in re.sub(r'--[^\n]*', '', sources[name]).split(';'):
            statement = statement.strip()
            if not statement:
                continue
            if statement.startswith('SET @rco16_changed_role_'):
                variable, expression = statement[4:].split('=', 1)
                values[variable.strip()] = c.execute('SELECT ' + expression.strip()).fetchone()[0]
                continue
            if statement.startswith('SET '):
                continue
            statement = re.sub(r'@rco16_changed_role_\d+', lambda m: str(values[m[0]]), statement)
            statement = statement.replace('START TRANSACTION', 'BEGIN').replace(' FOR UPDATE', '').replace('DROP TEMPORARY TABLE', 'DROP TABLE')
            last[0] = c.execute(statement).rowcount
    except:
        c.rollback()
        c.execute('DROP TABLE IF EXISTS rco16_release_gate')
        raise


def register(c):
    for table, key, prefix in [('t_gateway_route', 'routes', 'route'), ('t_console_resource', 'resources', 'resource')]:
        c.executemany(f'INSERT INTO {table} VALUES(?,?,?,?,NULL)', [(r['id'], r['code'], 'DISABLED', 1) for r in registration[key]])
    c.executemany('INSERT INTO t_role_permission VALUES(?,?,?,?,?,NULL)', [(900000000000012201 + i, ADMIN, p['id'], 'REVOKED', 1) for i, p in enumerate(registration['permissions'])])
    c.execute('UPDATE t_role SET version=version+1 WHERE role_id=?', (ADMIN,))


def active(c, table):
    return c.execute(f"SELECT COUNT(*) FROM {table} WHERE status='ACTIVE'").fetchone()[0]


def grants(c):
    # 已撤销的自定义权限在任何回退路径都不能重新启用。
    assert c.execute('SELECT status,version FROM t_role_permission WHERE role_permission_id=7300').fetchone() == ('REVOKED', 8)
    return active(c, 't_role_permission')


def rejected(c, name, label):
    before = list(c.iterdump())
    try:
        run(c, name)
    except sqlite3.IntegrityError:
        assert list(c.iterdump()) == before, 'failed transaction must leave all rows unchanged'
        checks.append(label)
        return
    raise AssertionError('expected gate rejection: ' + label)


c = database(); run(c, 'test-maintenance.sql'); assert active(c, 't_gateway_route') == active(c, 't_console_resource') == grants(c) == 0; checks.append('maintenance closes all scoped grants including Viewer and custom role')
register(c); assert grants(c) == 0; checks.append('registration does not activate new grants')
run(c, 'test-enable-routes.sql'); assert active(c, 't_gateway_route') == 24 and active(c, 't_console_resource') == 0 and grants(c) == 14; checks.append('routes and exact original/new grants activate in one transaction')
run(c, 'test-expose-resources.sql'); assert active(c, 't_console_resource') == 17; checks.append('menu exposure follows accepted routes')
run(c, 'test-close-entry.sql'); assert active(c, 't_gateway_route') == active(c, 't_console_resource') == grants(c) == 0; checks.append('close revokes only owned grants without deleting history')
before = list(c.iterdump()); run(c, 'test-close-entry.sql'); assert before == list(c.iterdump()); checks.append('repeated close preserves every version')
c = database(); run(c, 'test-maintenance.sql'); run(c, 'test-restore-before-migration.sql'); assert grants(c) == 8 and active(c, 't_gateway_route') == 10; checks.append('restore recovers snapshot grants, never previously revoked grant')
for stage in ['initial', 'maintenance', 'registered', 'routes']:
    c = database()
    if stage != 'initial': run(c, 'test-maintenance.sql')
    if stage in ['registered', 'routes']: register(c)
    if stage == 'routes': run(c, 'test-enable-routes.sql')
    run(c, 'test-close-entry.sql'); assert grants(c) == active(c, 't_gateway_route') == active(c, 't_console_resource') == 0; checks.append('close from ' + stage)
c = database(); c.execute('UPDATE t_role_permission SET version=version+1 WHERE role_permission_id=7000'); rejected(c, 'test-maintenance.sql', 'grant version drift rejected without partial close')
c = database(); c.execute('UPDATE t_role SET version=version+1 WHERE role_id=?', (VIEWER,)); rejected(c, 'test-maintenance.sql', 'Viewer role drift rejected')
c = database(); c.execute("INSERT INTO t_role_permission VALUES(7400,?,?,'ACTIVE',1,NULL)", (CUSTOM, PERMISSIONS[2])); rejected(c, 'test-maintenance.sql', 'unrecorded active grant rejected')
c = database(); run(c, 'test-maintenance.sql'); rejected(c, 'test-enable-routes.sql', 'cannot open before registration')
c = database(); run(c, 'test-maintenance.sql'); register(c); rejected(c, 'test-expose-resources.sql', 'cannot expose before route and grant opening')
c = database(); run(c, 'test-maintenance.sql'); register(c); rejected(c, 'test-restore-before-migration.sql', 'cannot restore old app after registration')
c = database(); run(c, 'test-maintenance.sql'); register(c); c.execute('DELETE FROM t_role_permission WHERE role_permission_id=900000000000012201'); rejected(c, 'test-close-entry.sql', 'partial binding registration rejected')
c = database(); c.execute('UPDATE t_gateway_route SET version=1000'); rejected(c, 'test-close-entry.sql', 'unknown route state rejected')
try:
    module.generate({'platform': {}}, registration)
    raise AssertionError('old preflight cannot generate executable release files')
except ValueError:
    checks.append('legacy preflight without exact role/grant baseline rejected')
output = REQ / 'assets/gateway-acceptance/permission-release-sql-check.json'
output.write_text(json.dumps({'scope': 'Offline SQLite state/transaction simulation of generated SQL using explicit synthetic version/role fixtures; not MySQL locking, test preflight or deployment evidence.', 'checks': checks, 'passed': len(checks)}, indent=2) + '\n')
print(len(checks), 'offline permission and release SQL checks passed')
