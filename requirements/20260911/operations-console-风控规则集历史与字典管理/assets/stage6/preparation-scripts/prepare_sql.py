"""根据已复核的环境快照生成发布 SQL；不连接数据库，不执行 SQL。"""
from pathlib import Path
import argparse
import json

REQ = Path(__file__).resolve().parents[3]
ADMIN = 900000000000000510


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def gate(condition, label):
    return f"INSERT INTO rco16_release_gate SELECT IF({condition},{quote(label)},NULL);\n"


def count(table, where):
    return f"(SELECT COUNT(*) FROM {table} WHERE {where})"


def predicate(rows, status=None, shift=0):
    terms = []
    for row in rows:
        fields = dict(row)
        if status is not None:
            fields['status'] = status
        fields['version'] += shift
        terms.append('(' + ' AND '.join(k + '=' + (str(v) if isinstance(v, int) else quote(v))
                                       for k, v in fields.items()) + ')')
    return '(' + ' OR '.join(terms) + ')' if terms else '(1=0)'


def subset(column, rows):
    return column + ' IN (' + ','.join(str(r[column]) for r in rows) + ')' if rows else '(1=0)'


def expected(table, rows, status=None, shift=0):
    return gate(count(table, predicate(rows, status, shift)) + '=' + str(len(rows)), 'exact ' + table + ' state')


def change(table, rows, before, shift, after):
    if not rows:
        return ''
    return (f"UPDATE {table} SET status={quote(after)},version=version+1,update_time=UTC_TIMESTAMP(6) WHERE "
            + predicate(rows, before, shift) + ';\n' + gate('ROW_COUNT()=' + str(len(rows)), 'changed ' + table))


def generate(base, registration):
    platform = base['platform']
    if not all(k in platform for k in ('affectedRoleBindings', 'affectedRoles', 'closedPermissionIds')):
        raise ValueError('旧 preflight 缺少权限绑定及角色快照；阶段 6 须只读重新采集，不得推测版本或恢复已撤销授权。')
    old = {
        't_gateway_route': [dict(route_id=int(r[0]), route_code=r[1], status=r[-2], version=int(r[-1]))
                            for r in platform['routes'] if r[1].startswith('operations-risk-')],
        't_console_resource': [dict(resource_id=int(r[0]), resource_code=r[1], status=r[-2], version=int(r[-1]))
                               for r in platform['resources']]}
    new = {table: [dict(**{idcol: int(r['id']), codecol: r['code']}, status='DISABLED', version=1)
                   for r in registration[key]]
           for table, key, idcol, codecol in [('t_gateway_route', 'routes', 'route_id', 'route_code'),
                                             ('t_console_resource', 'resources', 'resource_id', 'resource_code')]}
    bindings = [dict(role_permission_id=int(r[0]), role_id=int(r[1]), permission_id=int(r[2]),
                     status=r[3], version=int(r[4])) for r in platform['affectedRoleBindings']]
    roles = [dict(role_id=int(r[0]), role_code=r[1], status=r[2], version=int(r[3])) for r in platform['affectedRoles']]
    assert bindings and all(r['status'] == 'ACTIVE' for r in bindings)
    assert ADMIN in {r['role_id'] for r in roles}
    assert len({r['role_id'] for r in roles}) == len(roles)
    assert {r['role_id'] for r in bindings} <= {r['role_id'] for r in roles}
    assert all(r['status'] == 'ACTIVE' for rows in old.values() for r in rows)
    assert len(old['t_gateway_route']) == 10 and len(old['t_console_resource']) == 7
    fresh = [dict(role_permission_id=900000000000012201 + i, role_id=ADMIN, permission_id=int(p['id']),
                  status='REVOKED', version=1) for i, p in enumerate(registration['permissions'])]
    assert len(fresh) == 6
    allbindings = bindings + fresh
    ids = {'t_gateway_route': 'route_id', 't_console_resource': 'resource_id', 't_role_permission': 'role_permission_id'}
    start = '''-- 由已复核 preflight 生成；仅用于 mor-test 的 mor_platform，执行仍须发布授权。
-- 先备份所有受影响角色、绑定、Route/Resource，停止目录及 IAM 管理写入。
-- mysql --batch --skip-reconnect，禁止 --force；任一错误立即断开并回滚。
SET @rco16_release_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
CREATE TEMPORARY TABLE rco16_release_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO rco16_release_gate SELECT IF(DATABASE()='mor_platform','exact database',NULL);
START TRANSACTION;
'''
    start += 'SELECT role_id FROM t_role WHERE ' + subset('role_id', roles) + ' ORDER BY role_id FOR UPDATE;\n'
    for table, rows in [*[(t, old[t] + new[t]) for t in old], ('t_role_permission', allbindings)]:
        start += f'SELECT {ids[table]} FROM {table} WHERE ' + subset(ids[table], rows) + f' ORDER BY {ids[table]} FOR UPDATE;\n'
    end = 'COMMIT;\nDROP TEMPORARY TABLE rco16_release_gate;\nSET SESSION sql_mode = @rco16_release_sql_mode;\n'

    def role_shift(stage):
        shifts = {'initial': (0, 0), 'maintenance': (1, 0), 'registered': (1, 1), 'enabled': (2, 1)}
        delta, registration_delta = shifts[stage]
        bound_roles = {r['role_id'] for r in bindings}
        return [dict(r, version=r['version'] + (delta if r['role_id'] in bound_roles else 0)
                     + (registration_delta if r['role_id'] == ADMIN else 0)) for r in roles]

    def bump(rows):
        return ('UPDATE t_role SET version=version+1,update_time=UTC_TIMESTAMP(6) WHERE ' + predicate(rows) + ';\n'
                + gate('ROW_COUNT()=' + str(len(rows)), 'exact role version increments')) if rows else ''

    def absent():
        return ''.join(gate(count(t, subset(ids[t], new[t])) + '=0', 'registration absent ' + t) for t in old) \
            + gate(count('t_role_permission', subset('role_permission_id', fresh)) + '=0', 'new bindings absent')

    # Reject unrecorded active grants: closing an entry must never silently break another role.
    permission_ids = sorted(set(map(int, platform['closedPermissionIds'])) | {r['permission_id'] for r in fresh})
    outside = "status='ACTIVE' AND permission_id IN (" + ','.join(map(str, permission_ids)) + ') AND NOT ' + subset('role_permission_id', allbindings)
    guard = gate(count('t_role_permission', outside) + '=0', 'no unrecorded active risk binding')
    bound_roles = {r['role_id'] for r in bindings}
    result = {}
    body = expected('t_role', role_shift('initial')) + expected('t_role_permission', bindings) + absent() + guard
    for t in old:
        body += expected(t, old[t]) + change(t, old[t], 'ACTIVE', 0, 'DISABLED')
    body += change('t_role_permission', bindings, 'ACTIVE', 0, 'REVOKED')
    body += bump([r for r in role_shift('initial') if r['role_id'] in bound_roles])
    result['test-maintenance.sql'] = start + body + end

    body = expected('t_role', role_shift('registered')) + expected('t_role_permission', bindings, 'REVOKED', 1) + expected('t_role_permission', fresh) + guard
    for t in old:
        body += expected(t, old[t], 'DISABLED', 1) + expected(t, new[t])
    body += change('t_gateway_route', old['t_gateway_route'], 'DISABLED', 1, 'ACTIVE') + change('t_gateway_route', new['t_gateway_route'], 'DISABLED', 0, 'ACTIVE')
    body += change('t_role_permission', bindings, 'REVOKED', 1, 'ACTIVE') + change('t_role_permission', fresh, 'REVOKED', 0, 'ACTIVE')
    body += bump([r for r in role_shift('registered') if r['role_id'] in bound_roles | {ADMIN}])
    result['test-enable-routes.sql'] = start + body + end

    body = expected('t_role', role_shift('enabled')) + expected('t_role_permission', bindings, 'ACTIVE', 2) + expected('t_role_permission', fresh, 'ACTIVE', 1) + guard
    body += expected('t_gateway_route', old['t_gateway_route'], 'ACTIVE', 2) + expected('t_gateway_route', new['t_gateway_route'], 'ACTIVE', 1)
    body += '-- 已在外部核实 Gateway 接受完整快照后才开放页面，SQL 本身不能证明运行态收敛。\n'
    for rows, shift in [(old['t_console_resource'], 1), (new['t_console_resource'], 0)]:
        body += expected('t_console_resource', rows, 'DISABLED', shift) + change('t_console_resource', rows, 'DISABLED', shift, 'ACTIVE')
    result['test-expose-resources.sql'] = start + body + end

    body = expected('t_role', role_shift('maintenance')) + expected('t_role_permission', bindings, 'REVOKED', 1) + absent() + guard
    body += '-- Risk DDL 和本期注册均未开始时才可使用；两库分别只读核对，不跨库 SQL。\n'
    for t in old:
        body += expected(t, old[t], 'DISABLED', 1) + change(t, old[t], 'DISABLED', 1, 'ACTIVE')
    body += change('t_role_permission', bindings, 'REVOKED', 1, 'ACTIVE') + bump([r for r in role_shift('maintenance') if r['role_id'] in bound_roles])
    result['test-restore-before-migration.sql'] = start + body + end

    body = '-- 失败关闭仅撤销快照记录的有效绑定；不删除字典、历史或成功命令。重复关闭不递增版本。\n' + guard
    for t in old:
        known = ' OR '.join(predicate(old[t], state, shift) for state, shift in [('ACTIVE', 0), ('DISABLED', 1), ('ACTIVE', 2), ('DISABLED', 3)])
        body += gate(count(t, '(' + known + ')') + '=' + str(len(old[t])), 'known old entry state')
        known = ' OR '.join(predicate(new[t], state, shift) for state, shift in [('DISABLED', 0), ('ACTIVE', 1), ('DISABLED', 2)])
        body += gate(count(t, subset(ids[t], new[t]) + ' AND NOT (' + known + ')') + '=0', 'known new entry state')
    all_absent = [count(t, subset(ids[t], new[t])) + '=0' for t in old] + [count('t_role_permission', subset('role_permission_id', fresh)) + '=0']
    all_present = [count(t, subset(ids[t], new[t])) + '=' + str(len(new[t])) for t in old] + [count('t_role_permission', subset('role_permission_id', fresh)) + '=6']
    body += gate('(' + ' AND '.join(all_absent) + ') OR (' + ' AND '.join(all_present) + ')', 'registration is wholly absent or present')
    known = ' OR '.join(predicate(bindings, state, shift) for state, shift in [('ACTIVE', 0), ('REVOKED', 1), ('ACTIVE', 2), ('REVOKED', 3)])
    body += gate(count('t_role_permission', '(' + known + ')') + '=' + str(len(bindings)), 'known original grant states')
    known = ' OR '.join(predicate(fresh, state, shift) for state, shift in [('REVOKED', 0), ('ACTIVE', 1), ('REVOKED', 2)])
    body += gate(count('t_role_permission', subset('role_permission_id', fresh) + ' AND NOT (' + known + ')') + '=0', 'known new grant states')
    for r in roles:
        allowed = ' OR '.join(predicate([r], shift=i) for i in range(5 if r['role_id'] == ADMIN else 4))
        body += gate(count('t_role', '(' + allowed + ')') + '=1', 'known role version and identity')
        own = [b for b in allbindings if b['role_id'] == r['role_id']]
        body += f"SET @rco16_changed_role_{r['role_id']} = " + count('t_role_permission', subset('role_permission_id', own) + " AND status='ACTIVE'") + ';\n'
    for t in old:
        body += f"UPDATE {t} SET status='DISABLED',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE " + subset(ids[t], old[t] + new[t]) + " AND status='ACTIVE';\n"
    body += "UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE " + subset('role_permission_id', allbindings) + " AND status='ACTIVE';\n"
    for r in roles:
        body += f"UPDATE t_role SET version=version+1,update_time=UTC_TIMESTAMP(6) WHERE role_id={r['role_id']} AND @rco16_changed_role_{r['role_id']}>0;\n"
    result['test-close-entry.sql'] = start + body + end
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--preflight', type=Path, default=Path('/tmp/rco16-stage6/preflight.json'))
    parser.add_argument('--output', type=Path, default=REQ / 'db/platform-support-service')
    args = parser.parse_args()
    sources = generate(json.loads(args.preflight.read_text()), json.loads((REQ / 'db/platform-support-service/registration.json').read_text()))
    args.output.mkdir(parents=True, exist_ok=True)
    for name, sql in sources.items():
        (args.output / name).write_text(sql)
    print('Prepared five SQL files from reviewed role/permission snapshot. None executed.')
