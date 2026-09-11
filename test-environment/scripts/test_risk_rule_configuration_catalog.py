#!/usr/bin/env python3
"""Offline independent Platform SQL oracle and persisted rollout contract."""
import contextlib
import copy
import fcntl
import functools
import io
import os
from pathlib import Path
import re
import sqlite3
import stat
import tempfile
import unittest
from unittest import mock

from test_risk_database_initialization import load_initializer

WORKSPACE = Path(__file__).resolve().parents[2]
SQL = WORKSPACE / 'test-environment/sql/2026-09-07-risk-operations-rule-configuration.sql'
CANONICAL = WORKSPACE / 'platform-support-service/docs/database/sql/platform-support-initial-data.sql'
BASE = 900000000000000000
RANGES = {'t_permission': (536, 540), 't_role_permission': (581, 586),
          't_console_resource': (565, 570), 't_gateway_route': (1024, 1032),
          't_gateway_route_header': (5100, 5133), 't_gateway_route_query_param': (4031, 4039),
          't_gateway_guard_rule': (6110, 6163)}
TABLES = ('t_gateway_target', 't_role', *RANGES)
MUTABLE = {'status', 'version', 'create_time', 'update_time'}
BUSINESS_KEYS = {
    't_permission': ('permission_code',), 't_role_permission': ('role_id', 'permission_id'),
    't_console_resource': ('resource_code',), 't_gateway_route': ('route_code',),
    't_gateway_route_header': ('route_id', 'header_name', 'header_use'),
    't_gateway_route_query_param': ('route_id', 'parameter_name'),
    't_gateway_guard_rule': ('route_id', 'rule_type', 'dimension'),
}


def canonical_rows():
    return copy.deepcopy(_canonical_rows())


@functools.lru_cache(maxsize=1)
def _canonical_rows():
    """SQLite parses Owner VALUES independently of initialize's production parser."""
    source = CANONICAL.read_text(encoding='utf-8')
    variables = dict(re.findall(r'SET\s+(@\w+)\s*=\s*([^;]+);', source))
    variables['@platform_initial_time'] = "'2026-09-07 00:00:00.000000'"
    source = re.sub(r'@\w+', lambda m: variables[m.group()], source)
    result = {}
    with sqlite3.connect(':memory:') as db:
        for match in re.finditer(r'INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES\s*(.*?);', source, re.S):
            table, raw_columns, values = match.groups()
            if table not in TABLES:
                continue
            columns = [c.strip() for c in raw_columns.split(',')]
            if table not in result:
                db.execute('CREATE TABLE ' + table + ' (' + raw_columns + ')')
                result[table] = []
            db.execute('INSERT INTO ' + table + ' VALUES ' + values)
        for table in result:
            cursor = db.execute('SELECT * FROM ' + table + ' ORDER BY 1')
            columns = [c[0] for c in cursor.description]
            result[table] = [dict(zip(columns, row)) for row in cursor]
    return result


def reserved(table, row):
    low, high = RANGES.get(table, (0, -1))
    return BASE + low <= next(iter(row.values())) <= BASE + high


def fixture(stage='BASELINE'):
    rows = canonical_rows()
    for table in rows:
        rows[table] = [{c: v for c, v in row.items() if c not in {'create_time', 'update_time'}}
                       for row in rows[table] if stage != 'LEGACY' or not reserved(table, row)]
        if table in {'t_console_resource', 't_gateway_route'} and stage != 'RESOURCES_EXPOSED':
            for row in rows[table]:
                if reserved(table, row):
                    row['status'] = 'ACTIVE' if table == 't_gateway_route' and stage == 'ALL_ROUTES_ACTIVE' else 'DISABLED'
    return rows


def deployed_action_permission_history_fixture(stage='LEGACY'):
    """Exact mor-test shape after the reviewed 2026-09-04 permission split."""
    rows = fixture(stage)
    permission_template = rows['t_permission'][0]
    for identity, code, resource_type in (
            (BASE + 521, 'OPERATIONS_ACCOUNT_MANAGE', 'USER_ACCOUNT'),
            (BASE + 525, 'OPERATIONS_ROLE_MANAGE', 'ROLE')):
        row = dict(permission_template)
        row.update(permission_id=identity, permission_code=code,
                   resource_type=resource_type, action_code='MANAGE',
                   risk_level='HIGH', status='INACTIVE', version=2)
        rows['t_permission'].append(row)
    rows['t_permission'].sort(key=lambda row: row['permission_id'])

    for row in rows['t_role_permission']:
        identity = row['role_permission_id']
        if BASE + 570 <= identity <= BASE + 574:
            row['role_permission_id'] = BASE + 7001 + identity - (BASE + 570)
    grant_template = rows['t_role_permission'][0]
    for identity, permission_id in (
            (BASE + 531, BASE + 521),
            (BASE + 539, BASE + 525)):
        row = dict(grant_template)
        row.update(role_permission_id=identity, role_id=BASE + 510,
                   permission_id=permission_id, status='REVOKED', version=2)
        rows['t_role_permission'].append(row)
    rows['t_role_permission'].sort(key=lambda row: row['role_permission_id'])
    return rows


def with_dynamic_risk_grant(rows, identity=None, role_id=None):
    result = copy.deepcopy(rows)
    row = dict(result['t_role_permission'][0])
    row.update(
        role_permission_id=identity or BASE + 8001,
        role_id=role_id or BASE + 8801,
        permission_id=BASE + 536,
        status='ACTIVE',
        version=1,
    )
    result['t_role_permission'].append(row)
    result['t_role_permission'].sort(key=lambda value: value['role_permission_id'])
    return result


def persisted_projection(stage):
    rows = fixture(stage)
    return {'stage': stage, 'resource_rows': rows['t_console_resource'], 'route_rows': rows['t_gateway_route'],
            'resources': {r['resource_code']: r['display_name'] for r in rows['t_console_resource'] if r['status'] == 'ACTIVE'},
            'routes': {r['route_code']: r['status'] for r in rows['t_gateway_route']}}


def wire(rows):
    lines = []
    for table in TABLES:
        values = rows[table]
        columns = [c for c in canonical_rows()[table][0] if c not in {'create_time', 'update_time'}]
        lines.append('BEGIN\t' + table + '\t' + str(len(columns)))
        for row in values:
            cells = [('N' if row[c] is None else 'I' + str(row[c]) if isinstance(row[c], int)
                      else 'S' + row[c].encode('utf-8').hex().upper()) for c in columns]
            lines.append('\t'.join(['ROW', table, *cells]))
        lines.append('END\t' + table + '\t' + str(len(values)))
    return '\n'.join(lines)


def rows_visible_to_snapshot_query(rows, query):
    """Project fixtures through the generated role-grant WHERE predicate.

    The ordinary catalog tests deliberately feed the decoded reader directly.
    This helper instead emulates the narrow SQL snapshot so collision tests can
    prove that a drift row is actually selected before the reader rejects it.
    """
    result = copy.deepcopy(rows)
    row_statement = next(
        line for line in query.splitlines()
        if line.startswith("SELECT 'ROW', 't_role_permission'")
    )
    where = row_statement.split(' FROM t_role_permission ', 1)[1].split(
        ' ORDER BY ', 1)[0]

    def in_values(column):
        values = set()
        for group in re.findall(r'\b' + column + r'\s+IN\s+\(([^)]*)\)', where):
            values.update(int(value.strip()) for value in group.split(','))
        return values

    def between_ranges(column):
        return [
            (int(low), int(high))
            for low, high in re.findall(
                r'\b' + column + r'\s+BETWEEN\s+(\d+)\s+AND\s+(\d+)', where)
        ]

    role_ids = in_values('role_id')
    grant_ids = in_values('role_permission_id')
    grant_ranges = between_ranges('role_permission_id')
    permission_ids = in_values('permission_id')
    permission_ranges = between_ranges('permission_id')

    def visible(row):
        role_id = row['role_id']
        grant_id = row['role_permission_id']
        permission_id = row['permission_id']
        return (
            role_id in role_ids
            or grant_id in grant_ids
            or any(low <= grant_id <= high for low, high in grant_ranges)
            or permission_id in permission_ids
            or any(low <= permission_id <= high for low, high in permission_ranges)
        )

    result['t_role_permission'] = [
        row for row in result['t_role_permission'] if visible(row)
    ]
    return result


def incremental_rows(text):
    rows = {table: [] for table in RANGES}
    with sqlite3.connect(':memory:') as db:
        for table, columns, values in re.findall(
                r'INSERT INTO (\w+)\s*\(([^)]+)\)\s*SELECT\s+(.*?)\s+WHERE NOT EXISTS', text, re.S):
            if table not in rows:
                raise AssertionError('unexpected inserted family ' + table)
            values = values.replace('@risk_operations_time', "'2026-09-07 00:00:00.000000'")
            parsed = db.execute('SELECT ' + values).fetchone()
            rows[table].append(dict(zip([c.strip(' `\n') for c in columns.split(',')], parsed)))
    return rows


def run_artifact_offline(db, source):
    """Exercise artifact conditions/DML in SQLite; this does not certify MySQL execution.

    Only MySQL session assignments, FOR UPDATE and cardinality assertions need
    adaptation. Assertion conditions and actual INSERT/UPDATE predicates execute
    unchanged; missing locks are checked separately by the static oracle.
    """
    variables = {}
    changed = 0
    db.create_function('IF', 3, lambda condition, yes, no: yes if condition else no)
    statements = re.sub(r'(?m)^--.*$', '', source).split(';')
    try:
        for raw in statements:
            statement = raw.strip()
            if not statement or statement.startswith(('SET NAMES ', 'USE ')):
                continue
            if statement == 'START TRANSACTION':
                db.execute('BEGIN')
                continue
            assignment = re.fullmatch(r'SET (@\w+) = (.*)', statement, re.S)
            if assignment:
                name, expression = assignment.groups()
                if name == '@assertion':
                    condition = re.fullmatch(r'IF\(COALESCE\(\((.*)\), 0\), 1, \(SELECT value FROM \(SELECT 1 AS value UNION ALL SELECT 2 AS value\) AS assertion_failure\)\)', expression, re.S)
                    if not condition:
                        raise AssertionError('unrecognized failure assertion')
                    expression = condition[1]
                expression = expression.replace('UTC_TIMESTAMP(6)', "'2026-09-07 00:00:00.000000'").replace('ROW_COUNT()', str(changed)).replace(' FOR UPDATE', '')
                expression = re.sub(r'@\w+', lambda m: 'NULL' if variables[m[0]] is None else str(variables[m[0]]), expression)
                variables[name] = db.execute('SELECT ' + expression).fetchone()[0]
                if name == '@assertion' and variables[name] != 1:
                    raise ValueError('artifact pre/postcondition rejected')
                continue
            def sql_variable(match):
                value = variables[match[0]]
                return 'NULL' if value is None else str(value) if isinstance(value, int) else "'" + value + "'"
            statement = re.sub(r'@\w+', sql_variable, statement)
            cursor = db.execute(statement)
            if statement.startswith(('INSERT ', 'UPDATE ')):
                changed = cursor.rowcount
    except Exception:
        db.rollback()
        raise


def resource_database(stage, tables=('t_console_resource', 't_gateway_route')):
    db = sqlite3.connect(':memory:')
    for table in tables:
        columns = list(canonical_rows()[table][0])
        db.execute('CREATE TABLE ' + table + ' (' + ', '.join(columns) + ', PRIMARY KEY (' + columns[0] + '))')
        for row in fixture(stage)[table]:
            db.execute('INSERT INTO ' + table + ' VALUES (' + ','.join('?' for _ in columns) + ')',
                       tuple(row.get(c, '2026-09-06 00:00:00.000000') for c in columns))
    db.commit()
    return db


class StaticCatalogTest(unittest.TestCase):
    def test_incremental_postconditions_use_each_table_status_domain(self):
        source = SQL.read_text(encoding='utf-8')
        for table, legal in [('t_permission', 'INACTIVE'), ('t_role_permission', 'REVOKED')]:
            for status, accepted in [(legal, True), ('DISABLED', False), (legal.lower(), False), (legal + ' ', False)]:
                with self.subTest(table=table, status=status), resource_database('BASELINE', TABLES) as db:
                    row = next(row for row in fixture()[table] if reserved(table, row))
                    key = next(iter(row))
                    db.execute('UPDATE ' + table + ' SET status = ? WHERE ' + key + ' = ?', (status, row[key]))
                    db.commit()
                    if accepted:
                        run_artifact_offline(db, source)
                        self.assertEqual(status, db.execute('SELECT status FROM ' + table + ' WHERE ' + key + ' = ?', (row[key],)).fetchone()[0])
                    else:
                        with self.assertRaises(ValueError):
                            run_artifact_offline(db, source)

    def test_readme_checkpoint_names_exact_reviewed_image_set(self):
        readme = (WORKSPACE / 'test-environment/README.md').read_text(encoding='utf-8')
        section = readme.split('两个阶段通过持久检查点', 1)[1].split('\n\n', 1)[0]
        self.assertIn('Nacos、Risk、Platform、Gateway、BFF、Web 六个经审阅本地镜像', section)
        self.assertIn('不包含 Nginx 或 Vault', section)
        self.assertNotIn('六个应用', section)

    def test_incremental_sql_first_apply_and_replay_preserve_mutable_state(self):
        source = SQL.read_text(encoding='utf-8')
        with resource_database('LEGACY', TABLES) as db:
            run_artifact_offline(db, source)
            for table, (low, high) in RANGES.items():
                key = next(iter(canonical_rows()[table][0]))
                self.assertEqual(high-low+1, db.execute('SELECT COUNT(*) FROM ' + table + ' WHERE ' + key + ' BETWEEN ? AND ?', (BASE+low, BASE+high)).fetchone()[0])
            self.assertEqual([(2,), (2,)], db.execute('SELECT version FROM t_role ORDER BY role_id').fetchall())
            self.assertEqual([('DISABLED', 1)] * 6, db.execute('SELECT status, version FROM t_console_resource WHERE resource_id BETWEEN ? AND ? ORDER BY resource_id', (BASE+565, BASE+570)).fetchall())
            self.assertEqual([('DISABLED', 1)] * 9, db.execute('SELECT status, version FROM t_gateway_route WHERE route_id BETWEEN ? AND ? ORDER BY route_id', (BASE+1024, BASE+1032)).fetchall())
            db.execute('UPDATE t_gateway_route SET updated_by = 987, version = 12')
            db.execute("UPDATE t_permission SET status = 'INACTIVE', version = 7")
            db.commit()
            before = {table: db.execute('SELECT * FROM ' + table + ' ORDER BY 1').fetchall() for table in TABLES}
            run_artifact_offline(db, source)
            self.assertEqual(before, {table: db.execute('SELECT * FROM ' + table + ' ORDER BY 1').fetchall() for table in TABLES})

    def test_incremental_sql_dependency_identity_missing_inactive_or_drifted_rejects_before_writes(self):
        source = SQL.read_text(encoding='utf-8')
        for table, identity in [('t_gateway_target', BASE+1), ('t_role', BASE+510), ('t_role', BASE+511)]:
            original = next(row for row in fixture('LEGACY')[table] if next(iter(row.values())) == identity)
            key = next(iter(original))
            for column, value in original.items():
                if column in {'version', 'updated_by'}:
                    continue
                changed = value + 10000 if isinstance(value, int) else 'NULL' if value is None else value + ' '
                with self.subTest(table=table, identity=identity, column=column), resource_database('LEGACY', TABLES) as db:
                    db.execute('UPDATE ' + table + ' SET ' + column + ' = ? WHERE ' + key + ' = ?', (changed, identity))
                    db.commit()
                    before = {name: db.execute('SELECT * FROM ' + name + ' ORDER BY 1').fetchall() for name in TABLES}
                    with self.assertRaises(ValueError):
                        run_artifact_offline(db, source)
                    self.assertEqual(before, {name: db.execute('SELECT * FROM ' + name + ' ORDER BY 1').fetchall() for name in TABLES})

    def test_executable_replay_predicates_noop_only_for_exact_immutable_content(self):
        self.assertTrue(SQL.is_file(), 'incremental SQL does not exist')
        statements = re.findall(r'INSERT INTO \w+\s*\([^;]+?\);', SQL.read_text(encoding='utf-8'), re.S)
        self.assertEqual(123, len(statements))
        with sqlite3.connect(':memory:') as db:
            for table, rows in incremental_rows(SQL.read_text(encoding='utf-8')).items():
                columns = list(rows[0])
                db.execute('CREATE TABLE ' + table + ' (' + ', '.join(columns) + ', PRIMARY KEY (' + columns[0] + '), UNIQUE (' + ', '.join(BUSINESS_KEYS[table]) + '))')
            for statement in statements:
                table = re.search(r'INSERT INTO (\w+)', statement)[1]
                statement = statement.replace('@risk_operations_time', "'2026-09-07 00:00:00.000000'")
                self.assertEqual(1, db.execute(statement).rowcount)
                identity = db.execute('SELECT MAX(' + next(iter(canonical_rows()[table][0])) + ') FROM ' + table).fetchone()[0]
                self.assertEqual(0, db.execute(statement).rowcount)
                columns = list(canonical_rows()[table][0])
                key = columns[0]
                row = dict(zip(columns, db.execute('SELECT * FROM ' + table + ' WHERE ' + key + ' = ?', (identity,)).fetchone()))
                for column, value in row.items():
                    mutable = column in MUTABLE or table == 't_gateway_route' and column == 'updated_by'
                    changes = ([value + 100000] if column == key else [value + 1] if isinstance(value, int) else ['NULL'] if value is None
                               else [value + ' ', value.lower(), None])
                    for changed in changes:
                        if changed == value:
                            continue
                        with self.subTest(table=table, column=column, value=changed):
                            db.execute('SAVEPOINT mutation')
                            db.execute('UPDATE ' + table + ' SET ' + column + ' = ? WHERE ' + key + ' = ?', (changed, identity))
                            if mutable:
                                self.assertEqual(0, db.execute(statement).rowcount)
                            else:
                                with self.assertRaises(sqlite3.IntegrityError):
                                    db.execute(statement)
                            db.execute('ROLLBACK TO mutation')
                            db.execute('RELEASE mutation')

    def test_resource_sql_executes_closed_state_changes_and_idempotent_replays(self):
        for action, stage, desired in [('disable', 'RESOURCES_EXPOSED', 'DISABLED'),
                                        ('disable', 'BASELINE', 'DISABLED'),
                                        ('enable', 'ALL_ROUTES_ACTIVE', 'ACTIVE'),
                                        ('enable', 'RESOURCES_EXPOSED', 'ACTIVE')]:
            source = SQL.with_name(SQL.stem + '-resources-' + action + '.sql').read_text(encoding='utf-8')
            with self.subTest(action=action, stage=stage), resource_database(stage) as db:
                original = db.execute('SELECT resource_id, status, version FROM t_console_resource ORDER BY resource_id').fetchall()
                run_artifact_offline(db, source)
                expected = [(identity, desired, version + (status != desired)) if BASE + 565 <= identity <= BASE + 570
                            else (identity, status, version) for identity, status, version in original]
                self.assertEqual(expected, db.execute('SELECT resource_id, status, version FROM t_console_resource ORDER BY resource_id').fetchall())
                run_artifact_offline(db, source)
                self.assertEqual(expected, db.execute('SELECT resource_id, status, version FROM t_console_resource ORDER BY resource_id').fetchall())

    def test_resource_sql_rejects_partial_states_missing_rows_and_every_immutable_drift(self):
        for action in ('disable', 'enable'):
            source = SQL.with_name(SQL.stem + '-resources-' + action + '.sql').read_text(encoding='utf-8')
            for table in ('t_console_resource', 't_gateway_route') if action == 'enable' else ('t_console_resource',):
                row = next(r for r in fixture('RESOURCES_EXPOSED')[table] if reserved(table, r))
                key = next(iter(row))
                for column, value in row.items():
                    if column in {'version', 'updated_by'}:
                        continue
                    changed = value + 100000 if isinstance(value, int) else 'NULL' if value is None else value + ' '
                    with self.subTest(action=action, table=table, column=column), resource_database('RESOURCES_EXPOSED') as db:
                        db.execute('UPDATE ' + table + ' SET ' + column + ' = ? WHERE ' + key + ' = ?', (changed, row[key]))
                        db.commit()
                        before = db.execute('SELECT * FROM t_console_resource ORDER BY resource_id').fetchall()
                        with self.assertRaises(ValueError):
                            run_artifact_offline(db, source)
                        self.assertEqual(before, db.execute('SELECT * FROM t_console_resource ORDER BY resource_id').fetchall())
            with resource_database('RESOURCES_EXPOSED') as db:
                db.execute("UPDATE t_console_resource SET status = 'DISABLED' WHERE resource_id = ?", (BASE + 565,))
                db.commit()
                with self.assertRaises(ValueError):
                    run_artifact_offline(db, source)
        source = SQL.with_name(SQL.stem + '-resources-enable.sql').read_text(encoding='utf-8')
        with resource_database('BASELINE') as db:
            with self.assertRaises(ValueError):
                run_artifact_offline(db, source)

    def test_increment_is_exact_owner_catalog_with_disabled_entry_points(self):
        self.assertTrue(SQL.is_file(), 'incremental SQL does not exist')
        source = SQL.read_text(encoding='utf-8')
        actual = incremental_rows(source)
        for table, (low, high) in RANGES.items():
            expected = [row for row in canonical_rows()[table] if reserved(table, row)]
            for row in expected:
                if table in {'t_console_resource', 't_gateway_route'}:
                    row['status'] = 'DISABLED'
            self.assertEqual(list(range(BASE + low, BASE + high + 1)),
                             [next(iter(row.values())) for row in actual[table]], table)
            self.assertEqual(expected, actual[table], table)
        self.assertEqual(1, source.count('START TRANSACTION;'))
        self.assertEqual(1, source.count('COMMIT;'))
        self.assertIn('SET NAMES utf8mb4;', source)
        self.assertIn('USE mor_platform;', source)
        self.assertNotRegex(source, r'(?im)^\s*(CREATE|ALTER|DROP|DELETE|REPLACE)\b')
        self.assertIn('ROW_COUNT()', source)

    def test_every_replay_predicate_compares_exact_immutable_columns(self):
        self.assertTrue(SQL.is_file(), 'incremental SQL does not exist')
        source = SQL.read_text(encoding='utf-8')
        statements = re.findall(r'INSERT INTO (\w+).*?WHERE NOT EXISTS\s*\(\s*SELECT 1 FROM \w+\s+WHERE (.*?)\);', source, re.S)
        self.assertEqual(123, len(statements))
        by_table = {table: iter(rows) for table, rows in incremental_rows(source).items()}
        for table, predicate in statements:
            row = next(by_table[table])
            for column, value in row.items():
                mutable = column in MUTABLE or (table == 't_gateway_route' and column == 'updated_by')
                if mutable:
                    self.assertNotRegex(predicate, r'\b' + column + r'\b')
                elif value is None:
                    self.assertIn('`' + column + '` IS NULL', predicate)
                elif isinstance(value, str):
                    self.assertIn('HEX(`' + column + "`) = '" + value.encode().hex().upper() + "'", predicate)
                else:
                    self.assertIn('`' + column + '` = ' + str(value), predicate)

    def test_resource_artifacts_have_full_locks_closed_states_and_postconditions(self):
        for action in ('disable', 'enable'):
            path = SQL.with_name(SQL.stem + '-resources-' + action + '.sql')
            self.assertTrue(path.is_file(), 'Resource ' + action + ' SQL does not exist')
            source = path.read_text(encoding='utf-8')
            self.assertEqual(1, source.count('START TRANSACTION;'))
            self.assertEqual(1, source.count('COMMIT;'))
            self.assertNotRegex(source, r'(?im)^\s*(CREATE|ALTER|DROP|DELETE|REPLACE)\b')
            self.assertIn('version = version + 1', source)
            self.assertIn('ROW_COUNT()', source)
            self.assertIn('UTC_TIMESTAMP(6)', source)
            self.assertIn('@active_count IN (0, 6)', source)
            self.assertIn('@changed = @expected_changes', source)
            for row in fixture()['t_console_resource']:
                if not reserved('t_console_resource', row):
                    continue
                for column, value in row.items():
                    if column in MUTABLE:
                        continue
                    predicate = ('`' + column + '` IS NULL' if value is None else
                                 'HEX(`' + column + "`) = '" + value.encode().hex().upper() + "'" if isinstance(value, str)
                                 else '`' + column + '` = ' + str(value))
                    self.assertIn(predicate, source)
            if action == 'enable':
                self.assertIn('@active_routes = 9', source)
                self.assertGreaterEqual(source.count('FOR UPDATE'), 15)
            else:
                self.assertNotIn('t_gateway_route', source)


class PersistedCatalogTest(unittest.TestCase):
    def setUp(self):
        self.module = load_initializer()

    def test_mysql_timeout_is_bounded_validated_and_redacted(self):
        completed = mock.Mock(returncode=0, stdout="ok")
        with mock.patch.object(
            self.module.subprocess, "run", return_value=completed
        ) as runner:
            self.assertEqual(
                "ok", self.module.mysql("SELECT 1;", timeout_seconds=12.5)
            )
        self.assertEqual(12.5, runner.call_args.kwargs["timeout"])

        for timeout in (True, 0, -1, float("inf"), float("nan"), "12"):
            with self.subTest(timeout=timeout), self.assertRaises(ValueError):
                self.module.mysql("SELECT 1;", timeout_seconds=timeout)

        timeout_error = self.module.subprocess.TimeoutExpired(
            cmd=["CANARY_COMMAND"], timeout=12.5, output="CANARY_OUTPUT"
        )
        with mock.patch.object(
            self.module.subprocess, "run", side_effect=timeout_error
        ), self.assertRaises(SystemExit) as failure:
            self.module.mysql("SELECT 'CANARY_SQL';", timeout_seconds=12.5)
        diagnostic = str(failure.exception)
        self.assertIn("timed out", diagnostic)
        self.assertNotIn("CANARY", diagnostic)

    def test_persisted_stage_reader_propagates_the_sql_timeout(self):
        rows = fixture("BASELINE")
        timeouts = []

        def mysql(sql, database="mor_platform", timeout_seconds=None):
            timeouts.append(timeout_seconds)
            return wire(rows)

        self.module.mysql = mysql
        result = self.module.read_risk_operations_stage(timeout_seconds=12.5)
        self.assertEqual("BASELINE", result["stage"])
        self.assertEqual([12.5], timeouts)

    def test_reader_accepts_legal_alternates_and_rejects_cross_table_statuses(self):
        domains = {'t_permission': ('ACTIVE', 'INACTIVE'), 't_role_permission': ('ACTIVE', 'REVOKED'),
                   't_console_resource': ('ACTIVE', 'DISABLED'), 't_gateway_route': ('ACTIVE', 'DISABLED'),
                   't_gateway_target': ('ACTIVE',), 't_role': ('ACTIVE',)}
        for table, legal in domains.items():
            # Resource/Route ACTIVE is exercised by the exposed positive above;
            # use BASELINE here so rejection cannot come from a partial stage.
            for status in ('ACTIVE', 'INACTIVE', 'REVOKED', 'DISABLED', 'active', 'ACTIVE ', None):
                if status in legal and table in {'t_console_resource', 't_gateway_route'}:
                    continue
                rows = fixture()
                row = next((row for row in rows[table] if reserved(table, row)), rows[table][0])
                row['status'] = status
                with self.subTest(table=table, status=status):
                    if status in legal:
                        self.assertEqual('BASELINE', self.read(rows)['stage'])
                    else:
                        with self.assertRaises(SystemExit):
                            self.read(rows)

    def test_resource_first_disable_handles_visible_resources_over_eight_active_routes(self):
        for nonprefix in (False, True):
            rows = fixture('RESOURCES_EXPOSED')
            next(row for row in rows['t_gateway_route'] if row['route_id'] == BASE + (1024 if nonprefix else 1032))['status'] = 'DISABLED'
            original_routes = copy.deepcopy(rows['t_gateway_route'])
            events = []
            with self.subTest(nonprefix=nonprefix), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'lock'
                self.module.ROLLOUT_LOCK = path
                def mysql(sql, database='mor_platform'):
                    with path.open('rb') as handle:
                        with self.assertRaises(BlockingIOError):
                            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    if 'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;' in sql:
                        events.append('read')
                        return wire(rows)
                    events.append('disable')
                    self.assertIn("SET status = 'DISABLED'", sql)
                    for row in rows['t_console_resource']:
                        if reserved('t_console_resource', row):
                            row['status'] = 'DISABLED'
                            row['version'] += 1
                    return ''
                self.module.mysql = mysql
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    self.module.disable_risk_operations_resources()
                self.assertEqual(['read', 'disable', 'read'], events)
                self.assertEqual(original_routes, rows['t_gateway_route'])
                self.assertTrue(all(row['status'] == 'DISABLED' for row in rows['t_console_resource'] if reserved('t_console_resource', row)))
                self.assertIn('Route stage requires manual review' if nonprefix else 'CREATE_ACTIVE', output.getvalue())

    def read(self, rows, project_snapshot_query=False):
        self.assertTrue(hasattr(self.module, 'read_risk_operations_stage'), 'persisted reader is missing')
        calls = []
        def mysql(sql, database='mor_platform'):
            calls.append((sql, database))
            visible = rows_visible_to_snapshot_query(rows, sql) if project_snapshot_query else rows
            return wire(visible)
        self.module.mysql = mysql
        result = self.module.read_risk_operations_stage()
        self.assertEqual(1, len(calls))
        query, database = calls[0]
        self.assertEqual('mor_platform', database)
        self.assertIn('WITH CONSISTENT SNAPSHOT, READ ONLY', query)
        self.assertNotRegex(query, r'(?im)^\s*(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)\b')
        for table in TABLES:
            self.assertIn("'BEGIN', '" + table + "'", query)
        self.assertIn('HEX(', query)
        self.assertNotIn(' JOIN ', query, 'orphan children cannot disappear behind a join')
        return result

    def test_legacy_and_complete_stage_projections(self):
        for stage, resources, routes in [('LEGACY', 15, 27), ('BASELINE', 15, 36),
                                          ('ALL_ROUTES_ACTIVE', 15, 36), ('RESOURCES_EXPOSED', 21, 36)]:
            with self.subTest(stage=stage):
                result = self.read(fixture(stage))
                self.assertEqual(stage, result['stage'])
                self.assertEqual(resources, len(result['resources']))
                self.assertEqual(routes, len(result['routes']))

    def test_exact_deployed_action_permission_history_is_accepted_before_and_after_increment(self):
        for stage in ('LEGACY', 'BASELINE', 'ALL_ROUTES_ACTIVE', 'RESOURCES_EXPOSED'):
            with self.subTest(stage=stage):
                result = self.read(deployed_action_permission_history_fixture(stage))
                self.assertEqual(stage, result['stage'])

    def test_deployed_action_permission_history_rejects_partial_mixed_or_reactivated_tombstones(self):
        mutations = []

        missing_tombstone = deployed_action_permission_history_fixture()
        missing_tombstone['t_permission'] = [
            row for row in missing_tombstone['t_permission']
            if row['permission_id'] != BASE + 521
        ]
        mutations.append(missing_tombstone)

        mixed_grant_ids = deployed_action_permission_history_fixture()
        next(row for row in mixed_grant_ids['t_role_permission']
             if row['role_permission_id'] == BASE + 7001)['role_permission_id'] = BASE + 570
        mixed_grant_ids['t_role_permission'].sort(key=lambda row: row['role_permission_id'])
        mutations.append(mixed_grant_ids)

        duplicate_grant_lineages = deployed_action_permission_history_fixture()
        duplicate_grant_lineages['t_role_permission'].extend(
            copy.deepcopy(row) for row in fixture()['t_role_permission']
            if BASE + 570 <= row['role_permission_id'] <= BASE + 574
        )
        duplicate_grant_lineages['t_role_permission'].sort(
            key=lambda row: row['role_permission_id'])
        mutations.append(duplicate_grant_lineages)

        missing_replacement_grant = deployed_action_permission_history_fixture()
        missing_replacement_grant['t_role_permission'] = [
            row for row in missing_replacement_grant['t_role_permission']
            if row['role_permission_id'] != BASE + 7002
        ]
        mutations.append(missing_replacement_grant)

        wrong_grant_mapping = deployed_action_permission_history_fixture()
        next(row for row in wrong_grant_mapping['t_role_permission']
             if row['role_permission_id'] == BASE + 7001)['permission_id'] = BASE + 527
        mutations.append(wrong_grant_mapping)

        wrong_grant_role = deployed_action_permission_history_fixture()
        next(row for row in wrong_grant_role['t_role_permission']
             if row['role_permission_id'] == BASE + 7003)['role_id'] = BASE + 511
        mutations.append(wrong_grant_role)

        historical_permissions_canonical_grants = deployed_action_permission_history_fixture()
        historical_permissions_canonical_grants['t_role_permission'] = copy.deepcopy(
            fixture()['t_role_permission'])
        mutations.append(historical_permissions_canonical_grants)

        canonical_permissions_historical_grants = deployed_action_permission_history_fixture()
        canonical_permissions_historical_grants['t_permission'] = copy.deepcopy(
            fixture()['t_permission'])
        mutations.append(canonical_permissions_historical_grants)

        unknown_permission = deployed_action_permission_history_fixture()
        extra = dict(unknown_permission['t_permission'][0])
        extra.update(permission_id=BASE + 599, permission_code='UNKNOWN_PERMISSION')
        unknown_permission['t_permission'].append(extra)
        unknown_permission['t_permission'].sort(key=lambda row: row['permission_id'])
        mutations.append(unknown_permission)

        reactivated_tombstone = deployed_action_permission_history_fixture()
        next(row for row in reactivated_tombstone['t_permission']
             if row['permission_id'] == BASE + 521)['status'] = 'ACTIVE'
        mutations.append(reactivated_tombstone)

        reactivated_grant = deployed_action_permission_history_fixture()
        next(row for row in reactivated_grant['t_role_permission']
             if row['role_permission_id'] == BASE + 531)['status'] = 'ACTIVE'
        mutations.append(reactivated_grant)

        for index, rows in enumerate(mutations):
            with self.subTest(mutation=index), self.assertRaises(SystemExit):
                self.read(rows)

    def test_role_permission_snapshot_reads_dynamic_risk_grants_for_stage_aware_validation(self):
        query = self.module._platform_snapshot_query(self.module.load_platform_catalog())
        role_permission_lines = [
            line for line in query.splitlines() if ' FROM t_role_permission ' in line
        ]
        self.assertEqual(2, len(role_permission_lines))
        for line in role_permission_lines:
            self.assertIn(
                'role_id IN (900000000000000510, 900000000000000511)', line)
            self.assertIn(
                'role_permission_id BETWEEN 900000000000000581 AND 900000000000000586', line)
            self.assertIn(
                'permission_id BETWEEN 900000000000000536 AND 900000000000000540', line)

    def test_snapshot_exposes_every_owner_grant_id_collision_before_validation(self):
        canonical_collision = with_dynamic_risk_grant(
            fixture('BASELINE'), identity=BASE + 7001)
        next(row for row in canonical_collision['t_role_permission']
             if row['role_permission_id'] == BASE + 7001)['permission_id'] = BASE + 526

        migrated_collision = with_dynamic_risk_grant(
            deployed_action_permission_history_fixture('BASELINE'),
            identity=BASE + 570)
        next(row for row in migrated_collision['t_role_permission']
             if row['role_permission_id'] == BASE + 570)['permission_id'] = BASE + 526

        for profile, rows in (
                ('canonical-owner-id', canonical_collision),
                ('migrated-owner-id', migrated_collision)):
            with self.subTest(profile=profile), self.assertRaises(SystemExit):
                self.read(rows, project_snapshot_query=True)

    def test_dynamic_risk_grants_are_accepted_only_after_the_owner_catalog_is_complete(self):
        for source in (fixture('BASELINE'),
                       deployed_action_permission_history_fixture('BASELINE')):
            with self.subTest(profile=len(source['t_permission'])):
                self.assertEqual(
                    'BASELINE',
                    self.read(with_dynamic_risk_grant(source))['stage'],
                )

        for rows in (
                with_dynamic_risk_grant(fixture('LEGACY')),
                with_dynamic_risk_grant(
                    fixture('BASELINE'), identity=BASE + 581),
                with_dynamic_risk_grant(
                    deployed_action_permission_history_fixture('BASELINE'),
                    identity=BASE + 570),
                with_dynamic_risk_grant(
                    fixture('BASELINE'), identity=BASE + 7001),
                with_dynamic_risk_grant(
                    fixture('BASELINE'), role_id=BASE + 510)):
            with self.subTest(rows=len(rows['t_role_permission'])), self.assertRaises(SystemExit):
                self.read(rows)

        for field, value in (('status', 'DISABLED'), ('version', 0)):
            rows = with_dynamic_risk_grant(fixture('BASELINE'))
            dynamic = next(row for row in rows['t_role_permission']
                           if row['role_permission_id'] == BASE + 8001)
            dynamic[field] = value
            with self.subTest(field=field), self.assertRaises(SystemExit):
                self.read(rows)

    def test_mutation_of_every_immutable_cell_rejects_and_mutable_cells_survive(self):
        baseline = fixture()
        for table in TABLES:
            for original in baseline[table]:
                if table in RANGES and not reserved(table, original):
                    continue
                for column, value in original.items():
                    if column in MUTABLE or column == 'updated_by' and table in {'t_gateway_route', 't_gateway_target'}:
                        continue
                    changed = copy.deepcopy(baseline)
                    row = changed[table][baseline[table].index(original)]
                    row[column] = value + 1 if isinstance(value, int) else 'NULL' if value is None else value + ' '
                    with self.subTest(table=table, column=column, identity=next(iter(original.values()))):
                        with self.assertRaises(SystemExit):
                            self.read(changed)
        changed = copy.deepcopy(baseline)
        for table, rows in changed.items():
            for row in rows:
                if 'version' in row:
                    row['version'] = 9
                if table == 't_gateway_route':
                    row['updated_by'] = 765
        self.assertEqual('BASELINE', self.read(changed)['stage'])

    def test_partial_or_orphan_families_wrong_ids_and_nonexact_strings_reject(self):
        for table in RANGES:
            rows = fixture('LEGACY')
            child = copy.deepcopy(next(row for row in fixture()[table] if reserved(table, row)))
            rows[table].append(child)
            with self.subTest(orphan_family=table), self.assertRaises(SystemExit):
                self.read(rows)
            child[next(iter(child))] += 50000
            with self.subTest(wrong_id_family=table), self.assertRaises(SystemExit):
                self.read(rows)
        for value in ('risk_decision_config_read', 'RISK_DECISION_CONFIG_READ ', None, 'RÍSK_DECISION_CONFIG_READ'):
            rows = fixture()
            next(row for row in rows['t_permission'] if reserved('t_permission', row))['permission_code'] = value
            with self.subTest(value=value), self.assertRaises(SystemExit):
                self.read(rows)
        for value in ('active', 'ACTIVE ', None, 'RETIRED'):
            rows = fixture()
            rows['t_permission'][0]['status'] = value
            with self.subTest(status=value), self.assertRaises(SystemExit):
                self.read(rows)
        for value in (0, -1, None):
            rows = fixture()
            rows['t_gateway_route'][0]['version'] = value
            with self.subTest(version=value), self.assertRaises(SystemExit):
                self.read(rows)

    def test_shared_lock_is_persistent_private_nonblocking_and_released_on_failure(self):
        self.assertTrue(hasattr(self.module, 'risk_operations_rollout_lock'), 'rollout lock missing')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'rollout.lock'
            with self.assertRaisesRegex(RuntimeError, 'interrupted'):
                with self.module.risk_operations_rollout_lock(path) as fd:
                    self.assertTrue(stat.S_ISREG(os.fstat(fd).st_mode))
                    self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
                    inode = path.stat().st_ino
                    with self.assertRaises(SystemExit):
                        with self.module.risk_operations_rollout_lock(path):
                            self.fail('contention was ignored')
                    raise RuntimeError('interrupted')
            self.assertTrue(path.exists())
            with self.module.risk_operations_rollout_lock(path):
                self.assertEqual(inode, path.stat().st_ino)
            link = Path(directory) / 'link'
            link.symlink_to(path)
            with self.assertRaises(SystemExit):
                with self.module.risk_operations_rollout_lock(link):
                    self.fail('symlink accepted')

    def test_mutations_keep_one_lock_across_preread_sql_and_postread(self):
        self.assertTrue(hasattr(self.module, 'apply_risk_operations_sql'), 'incremental action missing')
        for action, before, after, writes in (
                ('apply_risk_operations_sql', 'LEGACY', 'BASELINE', 1),
                ('apply_risk_operations_sql', 'BASELINE', 'BASELINE', 0),
                ('disable_risk_operations_resources', 'RESOURCES_EXPOSED', 'ALL_ROUTES_ACTIVE', 1),
                ('disable_risk_operations_resources', 'BASELINE', 'BASELINE', 0),
                ('enable_risk_operations_resources', 'ALL_ROUTES_ACTIVE', 'RESOURCES_EXPOSED', 1),
                ('enable_risk_operations_resources', 'RESOURCES_EXPOSED', 'RESOURCES_EXPOSED', 0)):
            with self.subTest(action=action, before=before), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'lock'
                self.module.ROLLOUT_LOCK = path
                events = []
                states = iter((before, after))
                def locked():
                    with path.open('rb') as handle:
                        with self.assertRaises(BlockingIOError):
                            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    events.append(path.stat().st_ino)
                def read():
                    locked()
                    return persisted_projection(next(states))
                def mysql(sql, database='mor_platform'):
                    locked()
                    self.assertIn('START TRANSACTION;', sql)
                    return ''
                self.module.read_risk_operations_stage = read
                self.module.read_risk_operations_catalog = read
                self.module.mysql = mock.Mock(side_effect=mysql)
                with contextlib.redirect_stdout(io.StringIO()):
                    getattr(self.module, action)()
                self.assertEqual(writes, self.module.mysql.call_count)
                self.assertEqual(2 + writes, len(events))
                self.assertEqual(1, len(set(events)))

    def test_platform_initialization_holds_same_lock_before_and_after_existing_catalog(self):
        with tempfile.TemporaryDirectory() as directory:
            self.module.ROLLOUT_LOCK = Path(directory) / 'lock'
            events = []
            def checked(value, event):
                with self.module.ROLLOUT_LOCK.open('rb') as handle:
                    with self.assertRaises(BlockingIOError):
                        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                events.append(event)
                return value
            def count(sql, database='mor_platform'):
                return checked(22 if 'information_schema' in sql else 27 if 't_gateway_route' in sql else 1, 'count')
            self.module.count = count
            self.module.read_risk_operations_stage = lambda: checked({'stage': 'LEGACY'}, 'read')
            self.module.mysql = mock.Mock(side_effect=AssertionError('prepared Platform replay executed SQL'))
            with contextlib.redirect_stdout(io.StringIO()):
                self.module.apply_platform_sql()
            self.assertEqual(2, events.count('read'), 'authoritative catalog must be checked before and after')

    def test_resource_precondition_and_failed_postcondition_do_not_continue(self):
        for action, stage in [('enable_risk_operations_resources', 'BASELINE'),
                              ('enable_risk_operations_resources', 'LEGACY'),
                              ('disable_risk_operations_resources', 'LEGACY')]:
            with tempfile.TemporaryDirectory() as directory:
                self.module.ROLLOUT_LOCK = Path(directory) / 'lock'
                self.module.read_risk_operations_stage = lambda: {'stage': stage}
                self.module.read_risk_operations_catalog = lambda: persisted_projection(stage)
                self.module.mysql = mock.Mock(side_effect=AssertionError('invalid stage mutated'))
                with self.assertRaises(SystemExit):
                    getattr(self.module, action)()
                self.module.mysql.assert_not_called()
        with tempfile.TemporaryDirectory() as directory:
            self.module.ROLLOUT_LOCK = Path(directory) / 'lock'
            self.module.read_risk_operations_stage = lambda: {'stage': 'LEGACY'}
            self.module.mysql = mock.Mock(return_value='')
            with self.assertRaises(SystemExit):
                self.module.apply_risk_operations_sql()
            self.assertEqual(1, self.module.mysql.call_count)


if __name__ == '__main__':
    unittest.main()
