"""Read-only local CCT migration inventory; never applies DDL or business DML.

Uses credentials in memory from the verified repository MySQL container. Does not
read .env.local, print credentials, or inspect business values. Run at workspace
root with Python and PyMySQL. An inventory is not execution authorization.
"""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

import pymysql

BASE = Path(__file__).resolve().parents[1]
ROOT = BASE.parents[2]
REPO = ROOT / 'client-contract-service'
DB = 'mor_cct'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def query(connection, sql, args=()):
    """Permit only reads and local read-only transaction settings."""
    assert re.match(r'^(SELECT|SHOW|SET SESSION TRANSACTION READ ONLY)\b', sql)
    with connection.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute(sql, args or None)
        return list(cursor.fetchall())


def scalar(connection, sql, args=()):
    return next(iter(query(connection, sql, args)[0].values()))


def container_credentials():
    assert subprocess.check_output(['docker', 'context', 'show'], text=True).strip() == 'desktop-linux'
    info = json.loads(subprocess.check_output(['docker', 'inspect', 'mor-local-mysql-1']))[0]
    assert info['State']['Running']
    assert info['Config']['Labels']['com.docker.compose.project'] == 'mor-local'
    assert info['NetworkSettings']['Ports']['3306/tcp'] == [
        {'HostIp': '127.0.0.1', 'HostPort': '13306'}]
    return dict(item.split('=', 1) for item in info['Config']['Env'])


def connect(credentials, database):
    # An administrative read connection can see other active connections. Each
    # Owner uses its own selected database; there are no cross-database queries.
    connection = pymysql.connect(host='127.0.0.1', port=13306, database=database,
        user='root', password=credentials['MYSQL_ROOT_PASSWORD'], charset='utf8mb4',
        autocommit=True, connect_timeout=5, read_timeout=20)
    query(connection, 'SET SESSION TRANSACTION READ ONLY')
    assert scalar(connection, 'SELECT DATABASE()') == database
    return connection


def table_names(connection):
    names = [row['name'] for row in query(connection,
        'SELECT TABLE_NAME AS name FROM information_schema.tables '
        'WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME')]
    assert names and all(re.fullmatch(r't_[a-z0-9_]+', name) for name in names)
    return names


def table_counts(connection):
    return {name: scalar(connection, f'SELECT COUNT(*) FROM `{name}`')
        for name in table_names(connection)}


def compare_table(connection, sql, table):
    """Compare complete columns, indexes and table metadata, not column names only."""
    block = re.search(r'CREATE TABLE `' + table + r'` \([\s\S]*?\n\)[^;]*;', sql)[0]
    expected = []
    for name, definition in re.findall(r'^  `(\w+)` (.*?)(?:,)?$', block, re.M):
        default = re.search(r'\bDEFAULT (\S+)', definition)
        kind = definition.split()[0].lower()
        expected.append({'name': name, 'type': 'tinyint(1)' if kind == 'boolean' else kind,
            'nullable': 'NO' if 'NOT NULL' in definition else 'YES',
            'default': default[1].strip("'") if default else None,
            'comment': re.search(r"COMMENT '(.*)'", definition)[1].replace("''", "'")})
    actual = query(connection, 'SELECT COLUMN_NAME AS name,COLUMN_TYPE AS type,'
        'IS_NULLABLE AS nullable,COLUMN_DEFAULT AS `default`,COLUMN_COMMENT AS comment '
        'FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() '
        'AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION', (table,))
    expected_indexes = []
    for kind, name, columns, comment in re.findall(
            r'^  (PRIMARY KEY|UNIQUE KEY|KEY)(?: `(\w+)`)? \(([^)]+)\)(?: COMMENT \'([^\']*)\')?', block, re.M):
        for position, column in enumerate(re.findall(r'`(\w+)`', columns), 1):
            expected_indexes.append({'name': name or 'PRIMARY', 'unique': int(kind != 'KEY'),
                'position': position, 'column': column, 'comment': comment})
    actual_indexes = query(connection, 'SELECT INDEX_NAME AS name,1-NON_UNIQUE AS `unique`,'
        'SEQ_IN_INDEX AS position,COLUMN_NAME AS `column`,INDEX_COMMENT AS comment '
        'FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s', (table,))
    metadata = query(connection, 'SELECT ENGINE AS engine,TABLE_COLLATION AS collation,'
        'TABLE_COMMENT AS comment FROM information_schema.tables '
        'WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s', (table,))[0]
    expected_comment = re.search(r"COMMENT='(.*)';$", block)[1].replace("''", "'")
    key = lambda item: (item['name'], item['position'])
    return {'table': table, 'columnCount': len(actual),
        'indexCount': len({row['name'] for row in actual_indexes}),
        'columnsMatch': expected == actual,
        'indexesMatch': sorted(expected_indexes, key=key) == sorted(actual_indexes, key=key),
        'engineAndCollationMatch': metadata['engine'] == 'InnoDB'
            and metadata['collation'] == 'utf8mb4_0900_ai_ci',
        'tableCommentMatches': expected_comment == metadata['comment'],
        'commentDifferenceIsOnlyProviderToChannel': expected_comment != metadata['comment']
            and expected_comment == metadata['comment'].replace('Provider', 'Channel')}


def inspect_cct(connection, source, affected, backup):
    counts = table_counts(connection)
    assert set(counts) == set(re.findall(r'CREATE TABLE `(\w+)`', source)), 'CCT table baseline drift'
    comparison = [compare_table(connection, source, name) for name in counts]
    for row in comparison:
        assert all(row[key] for key in ('columnsMatch', 'indexesMatch', 'engineAndCollationMatch')), row['table']
        if row['table'] in affected:
            assert row['tableCommentMatches'], row['table']
        else:
            assert row['tableCommentMatches'] or row['commentDifferenceIsOnlyProviderToChannel'], row['table']
    statements = [query(connection, f'SHOW CREATE TABLE `{name}`')[0]['Create Table'] + ';'
        for name in counts]
    backup_file = backup / 'mor_cct-show-create.sql'
    backup_file.write_text('\n\n'.join(statements) + '\n')
    backup_file.chmod(0o600)
    preflight = BASE / 'db/cct-migration-preflight.sql'
    sql = re.sub(r'^--[^\n]*', '', preflight.read_text(), flags=re.M)
    checks = []
    for statement in filter(None, (item.strip() for item in sql.split(';'))):
        result = query(connection, statement)
        if result and ('row_count' in result[0] or 'missing_parent_count' in result[0]):
            checks.extend(result)
    return {'database': DB, 'rowCounts': counts, 'sourceComparison': comparison,
        'closureChecks': checks,
        'otherDatabaseConnections': scalar(connection, 'SELECT COUNT(*) FROM information_schema.PROCESSLIST '
            'WHERE ID<>CONNECTION_ID() AND DB=DATABASE()'),
        'physicalCheckOrForeignKeyCount': scalar(connection,
            "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() "
            "AND CONSTRAINT_TYPE IN ('CHECK','FOREIGN KEY')"),
        'structureBackup': {'path': str(backup_file), 'sha256': sha(backup_file.read_bytes()), 'mode': '0600'},
        'allTablesEmpty': all(count == 0 for count in counts.values())}


def inspect_other_owners(credentials):
    owners = []
    for name in ('commerce', 'payment', 'risk', 'finance', 'platform'):
        with connect(credentials, 'mor_' + name) as connection:
            item = {'owner': name, 'database': 'mor_' + name, 'rowCounts': table_counts(connection)}
            if name == 'commerce':
                item['compensationGroups'] = query(connection, 'SELECT event_type,sub_event_type,execute_status,'
                    'COUNT(*) AS count FROM t_compensation_event GROUP BY event_type,sub_event_type,execute_status')
            if name == 'platform':
                item['tenantTypes'] = query(connection,
                    'SELECT tenant_type,COUNT(*) AS count FROM t_access_tenant GROUP BY tenant_type')
            owners.append(item)
    return owners


def main():
    manifest_path = BASE / 'db/implementation/migration-manifest.json'
    manifest = json.loads(manifest_path.read_text())['systems']['CCT']
    model = subprocess.check_output(['git', 'show', 'HEAD:docs/database/client-contract.pdma.json'], cwd=REPO)
    assert sha(model) == manifest['sourceModelSha256'], 'Frozen model changed'
    source = subprocess.check_output(['git', 'show', 'HEAD:docs/database/sql/client-contract-schema.sql'], cwd=REPO)
    ddl_path = BASE / 'db/implementation' / manifest['ddl']
    assert sha(ddl_path.read_bytes()) == manifest['ddlSha256'], 'DDL fingerprint mismatch'
    affected = re.findall(r'ALTER TABLE `(\w+)`', ddl_path.read_text())
    assert affected == manifest['tables'] and len(affected) == 10
    backup = Path(tempfile.mkdtemp(prefix='client-cct-preflight-'))
    credentials = container_credentials()
    with connect(credentials, DB) as connection:
        cct = inspect_cct(connection, source.decode(), affected, backup)
    evidence = {'observedAt': datetime.now(timezone.utc).isoformat(),
        'status': 'READ_ONLY_PREFLIGHT; NOT_SQL_EXECUTION; NOT_AUTHORIZATION',
        'target': {'dockerContext': 'desktop-linux', 'composeProject': 'mor-local',
            'container': 'mor-local-mysql-1', 'host': '127.0.0.1', 'port': 13306},
        'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=REPO, text=True).strip(),
        'sourceModelSha256': sha(model), 'sourceSchemaSha256': sha(source),
        'ddl': {'path': str(ddl_path.relative_to(ROOT)), 'sha256': sha(ddl_path.read_bytes()), 'tables': affected},
        'cct': cct, 'otherOwnerInventories': inspect_other_owners(credentials),
        'limits': ['Point-in-time inventory, not a writer shutdown or exclusive migration lock.',
            'Only named local databases inspected; no shared environment or browser verification.',
            'External task contents and business values were not read; nonempty groups require Owner review.',
            'Legacy CCT compensation table is outside the ten-ALTER candidate and cannot use current Foundation.'],
        'scriptSha256': sha(Path(__file__).read_bytes())}
    output = backup / 'preflight.json'
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    output.chmod(0o600)
    print(json.dumps({'report': str(output), 'cctTables': len(cct['rowCounts']),
        'allCctTablesEmpty': cct['allTablesEmpty'], 'otherCctConnections': cct['otherDatabaseConnections'],
        'affectedTables': affected, 'ddlExecuted': False}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except pymysql.MySQLError as error:
        raise SystemExit(f'Local MySQL preflight failed with code {error.args[0]}; no DDL applied.') from None
