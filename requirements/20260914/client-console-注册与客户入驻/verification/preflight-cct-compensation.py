"""Read-only CCT compensation preflight. Never executes DDL or reads .env.local."""
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
DDL_DIR = HERE.parent / 'db/implementation'
spec = importlib.util.spec_from_file_location('cct_preflight', HERE / 'preflight-cct-local.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def run():
    """Verify exact local source and preserve private structural evidence."""
    subprocess.run(['node', str(DDL_DIR / 'generate-cct-compensation.mjs'), '--check'],
                   check=True, capture_output=True)
    manifest = json.loads((DDL_DIR / 'cct-compensation-manifest.json').read_text())
    backup = Path(tempfile.mkdtemp(prefix='client-cct-compensation-preflight-'))
    backup.chmod(0o700)
    credentials = helper.container_credentials()
    with helper.connect(credentials, 'mor_cct') as connection:
        counts = helper.table_counts(connection)
        assert len(counts) == 38 and all(value == 0 for value in counts.values()), 'CCT must be empty'
        source = helper.compare_table(connection, (DDL_DIR / 'cct-compensation-source.sql').read_text(),
                                      't_compensation_event')
        assert all(source[key] for key in ('columnsMatch', 'indexesMatch',
                   'engineAndCollationMatch', 'tableCommentMatches')), 'Source table drift'
        encodings = helper.query(connection, 'SELECT COLUMN_NAME AS name, CHARACTER_SET_NAME AS charset, '
            'COLLATION_NAME AS collation, EXTRA AS extra FROM information_schema.columns '
            'WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION',
            ('t_compensation_event',))
        for field in encodings:
            assert field['extra'] == '', 'Unexpected generated/default column'
            if field['charset']:
                assert (field['charset'], field['collation']) == ('utf8mb4', 'utf8mb4_0900_ai_ci')
        connections = helper.query(connection, 'SELECT ID,COMMAND FROM information_schema.processlist '
            'WHERE DB=DATABASE() AND ID<>CONNECTION_ID()')
        assert not connections, 'Stop other CCT connections before migration'
        definitions = {name: helper.query(connection, f'SHOW CREATE TABLE `{name}`')[0]['Create Table']
                       for name in counts}
        assert counts == helper.table_counts(connection), 'CCT counts changed during preflight'
    # Query the known CCT port without exposing process arguments or environment.
    listener = subprocess.run(['lsof', '-nP', '-iTCP:18101', '-sTCP:LISTEN', '-t'],
                              capture_output=True, text=True)
    assert listener.returncode in (0, 1) and not listener.stdout.strip(), 'Stop CCT listener on 18101'
    schema = backup / 'mor_cct-show-create.sql'
    schema.write_text('\n\n'.join(definitions.values()) + '\n')
    schema.chmod(0o600)
    report = {
        'status': 'read-only preflight passed; no DDL/DML executed; approval still required',
        'verifiedAtUtc': datetime.now(timezone.utc).isoformat(),
        'target': {'dockerContext': 'desktop-linux', 'composeProject': 'mor-local',
                   'container': 'mor-local-mysql-1', 'host': '127.0.0.1', 'port': 13306,
                   'database': 'mor_cct', 'table': 't_compensation_event'},
        'source': source, 'tableCounts': counts, 'otherCctConnections': 0, 'cct18101Listeners': 0,
        'sourceEncodingAndExtraMatch': True, 'migration': manifest,
        'showCreateSha256ByTable': {name: hashlib.sha256(sql.encode()).hexdigest()
                                   for name, sql in definitions.items()},
        'schemaBackup': str(schema), 'schemaBackupSha256': helper.sha(schema.read_bytes()),
        'scope': 'CCT only; no cross-database queries, no shared environment changes',
    }
    receipt = backup / 'preflight.json'
    receipt.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    receipt.chmod(0o600)
    print(json.dumps({'status': report['status'], 'receipt': str(receipt),
                      'tableCount': 38, 'sourceColumnCount': source['columnCount'],
                      'ddlSha256': manifest['ddlSha256']}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        run()
    except Exception as error:
        # Connector exceptions can contain credentials or connection details.
        print(json.dumps({'status': 'preflight failed; no DDL/DML executed',
                          'errorType': type(error).__name__}))
        raise SystemExit(1) from None
