"""Read-only verification of the approved local seven-table execution."""
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import re

BASE = Path(__file__).resolve().parents[1]


def main(output=None):
    spec = importlib.util.spec_from_file_location('preflight', BASE / 'verification/preflight-cct-local.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    executed = json.loads((BASE / 'assets/stage4/platform-files-execution-20260915.json').read_text())
    sql_file = BASE / 'db/implementation/platform-files-local.sql'
    assert helper.sha(sql_file.read_bytes()) == executed['sqlSha256']
    sql = sql_file.read_text()
    comparisons = []
    with helper.connect(helper.container_credentials(), 'mor_platform_config_validation') as connection:
        lock = 'platform-support:auto:global-catalog'
        assert helper.scalar(connection, 'SELECT GET_LOCK(%s,0)', (lock,)) == 1
        try:
            for table in executed['createdTables']:
                comparison = helper.compare_table(connection, sql, table)
                block = re.search(r'CREATE TABLE `' + table + r'` \([\s\S]*?\n\)[^;]*;', sql)[0]
                expected = []
                for name, definition in re.findall(r'^  `(\w+)` (.*?)(?:,)?$', block, re.M):
                    charset = re.search(r'CHARACTER SET (\w+)', definition)
                    collation = re.search(r'COLLATE (\w+)', definition)
                    text = bool(re.match(r'(VAR)?CHAR\(', definition))
                    expected.append({'name': name, 'charset': charset[1] if charset else ('utf8mb4' if text else None),
                        'collation': collation[1] if collation else ('utf8mb4_0900_ai_ci' if text else None)})
                comparison['columnCharsetAndCollationMatch'] = expected == helper.query(connection,
                    'SELECT COLUMN_NAME AS name,CHARACTER_SET_NAME AS charset,COLLATION_NAME AS collation '
                    'FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s '
                    'ORDER BY ORDINAL_POSITION', (table,))
                comparisons.append(comparison)
            counts = helper.table_counts(connection)
            hashes = {table: helper.sha(helper.query(connection, f'SHOW CREATE TABLE `{table}`')[0]['Create Table'].encode())
                for table in executed['beforeDefinitionHashes']}
            assert hashes == executed['beforeDefinitionHashes']
            assert all(counts[table] == count for table, count in executed['beforeCounts'].items())
            assert all(counts[table] == 0 for table in executed['createdTables'])
            assert len(counts) == 33
            assert all(row[key] for row in comparisons for key in ('columnsMatch', 'indexesMatch',
                'engineAndCollationMatch', 'tableCommentMatches', 'columnCharsetAndCollationMatch'))
            evidence = {'observedAt': datetime.now(timezone.utc).isoformat(), 'verified': True,
                'target': executed['target'], 'sqlSha256': executed['sqlSha256'],
                'scriptSha256': helper.sha(Path(__file__).read_bytes()), 'comparisons': comparisons,
                'afterCounts': counts, 'existing26TablesUnchanged': True, 'new7TablesEmpty': True,
                'ddlExecutedByVerification': False, 'dmlExecutedByVerification': False,
                'initialVerificationCorrection': 'Initial verifier incorrectly lowercased CURRENT_TIMESTAMP(6). '
                    'MySQL metadata and approved SQL both use uppercase. Rechecked original SQL unchanged; no DDL retry.'}
        finally:
            assert helper.scalar(connection, 'SELECT RELEASE_LOCK(%s)', (lock,)) == 1
    output = output or BASE / 'assets/stage4/platform-files-post-execution-verification-20260915.json'
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    output.chmod(0o600)
    print(json.dumps({'record': str(output), 'verified': True, 'tables': len(counts)}, ensure_ascii=False))


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='Read-only verification; never applies DDL or DML.')
    parser.add_argument('--output', type=Path, help='Separate evidence file to preserve an earlier observation.')
    main(parser.parse_args().output)
