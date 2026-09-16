"""Apply only the seven local CREATE statements explicitly approved on 2026-09-15.

Requires the approved digest as an explicit argument. Never reads .env.local,
prints credentials, applies DML, drops tables, or retries a partial execution.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re

import pymysql

BASE = Path(__file__).resolve().parents[1]
ROOT = BASE.parents[2]
DIGEST = '358960eae6650c7734eb940db5cbd35be85107dacbfd552e58d35383cae07791'
DATABASE = 'mor_platform_config_validation'
LOCK = 'platform-support:auto:global-catalog'
TABLES = ['t_file_upload', 't_file_scan_policy', 't_file_scan_requirement',
          't_file_object', 't_file_owner_ref', 't_file_scan_result', 't_compensation_event']


def sha(value):
    return hashlib.sha256(value).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-approved-sha256', required=True, choices=[DIGEST])
    parser.parse_args()
    spec = importlib.util.spec_from_file_location('preflight', BASE / 'verification/preflight-cct-local.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    source = BASE / 'db/implementation/platform-files-local.sql'
    sql = source.read_text()
    assert sha(source.read_bytes()) == DIGEST, 'Approved SQL changed'
    blocks = re.findall(r'CREATE TABLE `\w+` \([\s\S]*?\n\)[^;]*;', sql)
    assert [re.search(r'CREATE TABLE `(\w+)`', block)[1] for block in blocks] == TABLES
    remainder = re.sub(r'^--[^\n]*', '', sql, flags=re.M)
    for block in blocks:
        remainder = remainder.replace(block, '', 1)
    assert not remainder.strip(), 'Unexpected SQL outside the seven CREATEs'
    canonical = (ROOT / 'platform-support-service/docs/database/sql/platform-support-schema.sql').read_text()
    assert all(block in canonical for block in blocks), 'Canonical Schema differs'
    baseline = json.loads((BASE / 'assets/stage4/platform-files-execution-preflight-20260915.json').read_text())
    model_hash = sha((ROOT / 'platform-support-service/docs/database/platform-support.pdma.json').read_bytes())
    assert model_hash == baseline['modelSha256'], 'Approved model changed'
    output = BASE / 'assets/stage4/platform-files-execution-20260915.json'
    assert not output.exists(), 'An execution record already exists; inspect it, never retry blindly'
    evidence = {'startedAt': datetime.now(timezone.utc).isoformat(),
        'authorization': 'User confirmed the exact seven-table local execution scope with 可以.',
        'target': f'127.0.0.1:13306/{DATABASE}', 'container': 'mor-local-mysql-1',
        'dockerContext': 'desktop-linux', 'sqlSha256': DIGEST, 'modelSha256': model_hash,
        'scriptSha256': sha(Path(__file__).read_bytes()), 'createdTables': [], 'dmlStatements': 0}

    def save():
        output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
        output.chmod(0o600)

    with helper.connect(helper.container_credentials(), DATABASE) as connection:
        assert helper.scalar(connection, 'SELECT GET_LOCK(%s, 0)', (LOCK,)) == 1, 'Platform tests or migration hold the lock'
        try:
            counts = helper.table_counts(connection)
            assert counts == baseline['existingTableCounts'], 'Existing table/count drift; stop for inspection'
            definitions = {name: helper.query(connection, f'SHOW CREATE TABLE `{name}`')[0]['Create Table'] for name in counts}
            hashes = {name: sha(definition.encode()) for name, definition in definitions.items()}
            assert hashes == baseline['existingDefinitionHashes'], 'Existing definition drift'
            assert not set(TABLES).intersection(counts), 'A candidate table already exists'
            assert helper.scalar(connection, 'SELECT COUNT(*) FROM information_schema.PROCESSLIST '
                'WHERE ID<>CONNECTION_ID() AND DB=DATABASE()') == 0, 'Another target database connection is active'
            evidence.update({'beforeCounts': counts, 'beforeDefinitionHashes': hashes, 'advisoryLock': LOCK})
            save()
            with connection.cursor() as cursor:
                cursor.execute('SET SESSION TRANSACTION READ WRITE')
                for table, block in zip(TABLES, blocks):
                    cursor.execute(block)
                    evidence['createdTables'].append(table)
                    save()
                cursor.execute('SET SESSION TRANSACTION READ ONLY')
            comparisons = []
            for table, block in zip(TABLES, blocks):
                comparison = helper.compare_table(connection, sql, table)
                charset_expected = []
                for name, definition in re.findall(r'^  `(\w+)` (.*?)(?:,)?$', block, re.M):
                    charset = re.search(r'CHARACTER SET (\w+)', definition)
                    collation = re.search(r'COLLATE (\w+)', definition)
                    textual = bool(re.match(r'(VAR)?CHAR\(', definition))
                    charset_expected.append({'name': name,
                        'charset': charset[1] if charset else ('utf8mb4' if textual else None),
                        'collation': collation[1] if collation else ('utf8mb4_0900_ai_ci' if textual else None)})
                actual = helper.query(connection, 'SELECT COLUMN_NAME AS name,CHARACTER_SET_NAME AS charset,'
                    'COLLATION_NAME AS collation FROM information_schema.columns '
                    'WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION', (table,))
                comparison['columnCharsetAndCollationMatch'] = actual == charset_expected
                comparisons.append(comparison)
            after = helper.table_counts(connection)
            after_hashes = {name: sha(helper.query(connection, f'SHOW CREATE TABLE `{name}`')[0]['Create Table'].encode()) for name in counts}
            evidence.update({'comparisons': comparisons, 'afterCounts': after,
                'existingCountsUnchanged': all(after[name] == count for name, count in counts.items()),
                'existingDefinitionsUnchanged': after_hashes == hashes,
                'newTablesEmpty': all(after[name] == 0 for name in TABLES),
                'tableCount': len(after), 'finishedAt': datetime.now(timezone.utc).isoformat()})
            checks = ('columnsMatch', 'indexesMatch', 'engineAndCollationMatch', 'tableCommentMatches', 'columnCharsetAndCollationMatch')
            evidence['verified'] = (all(row[key] for row in comparisons for key in checks)
                and evidence['existingCountsUnchanged'] and evidence['existingDefinitionsUnchanged']
                and evidence['newTablesEmpty'] and len(after) == 33)
            save()
            assert evidence['verified'], 'DDL applied; verification mismatch. Inspect evidence; do not retry.'
        except pymysql.MySQLError as error:
            evidence['failure'] = {'mysqlCode': error.args[0], 'automaticRollbackOrRetry': False}
            save()
            raise SystemExit(f'Local CREATE failed with MySQL code {error.args[0]}; inspect the execution record.') from None
        finally:
            evidence['advisoryLockReleased'] = helper.scalar(connection, 'SELECT RELEASE_LOCK(%s)', (LOCK,)) == 1
            if output.exists():
                save()
    print(json.dumps({'record': str(output), 'createdTables': evidence['createdTables'],
        'verified': evidence['verified'], 'existingTablesPreserved': evidence['existingCountsUnchanged']
        and evidence['existingDefinitionsUnchanged'], 'newTablesEmpty': evidence['newTablesEmpty']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
