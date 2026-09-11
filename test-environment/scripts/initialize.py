#!/usr/bin/env python3
"""Explicit operator actions; never invoked by Compose, application startup, or Java tests."""
import argparse
import contextlib
import fcntl
import json
import math
import os
from pathlib import Path
import re
import stat
import subprocess
import urllib.error
import urllib.parse
import urllib.request

from risk_rule_configuration_stage import classify_stage

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE_ROOT = ROOT.parent
RISK_SQL_DIR = WORKSPACE_ROOT / "risk-compliance-service/docs/database/sql"
RISK_SCHEMA = RISK_SQL_DIR / "risk-compliance-schema.sql"
RISK_INITIAL_DATA = RISK_SQL_DIR / "risk-compliance-initial-data.sql"
PLATFORM_CATALOG = WORKSPACE_ROOT / 'platform-support-service/docs/database/sql/platform-support-initial-data.sql'
RISK_OPERATIONS_SQL = ROOT / 'sql/2026-09-07-risk-operations-rule-configuration.sql'
ROLLOUT_LOCK = ROOT / 'private/risk-operations-rollout.lock'
RESERVED_RANGES = {
    't_permission': (536, 540), 't_role_permission': (581, 586),
    't_console_resource': (565, 570), 't_gateway_route': (1024, 1032),
    't_gateway_route_header': (5100, 5133), 't_gateway_route_query_param': (4031, 4039),
    't_gateway_guard_rule': (6110, 6163),
}
PLATFORM_TABLES = ('t_gateway_target', 't_role', *RESERVED_RANGES)
PLATFORM_STATUS_DOMAINS = {
    't_gateway_target': frozenset({'ACTIVE'}), 't_role': frozenset({'ACTIVE'}),
    't_permission': frozenset({'ACTIVE', 'INACTIVE'}),
    't_role_permission': frozenset({'ACTIVE', 'REVOKED'}),
    't_console_resource': frozenset({'ACTIVE', 'DISABLED'}),
    't_gateway_route': frozenset({'ACTIVE', 'DISABLED'}),
}
REPLAY_MASKS = {table: frozenset({'status', 'version', 'create_time', 'update_time'} |
                              ({'updated_by'} if table in {'t_gateway_route', 't_gateway_target'} else set()))
                for table in PLATFORM_TABLES}
SYSTEM_ROLE_IDS = frozenset({900000000000000510, 900000000000000511})
DEPLOYED_ACTION_PERMISSION_HISTORY_GRANT_IDS = frozenset({
    900000000000000531,
    900000000000000539,
    900000000000007001,
    900000000000007002,
    900000000000007003,
    900000000000007004,
    900000000000007005,
})


@contextlib.contextmanager
def risk_operations_rollout_lock(path=None):
    """One persistent inode coordinates every local Platform rollout writer."""
    path = Path(path) if path is not None else ROLLOUT_LOCK
    descriptor = None
    try:
        descriptor = os.open(str(path), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1 or metadata.st_uid != os.getuid():
            raise OSError('unsafe rollout lock')
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        os.fchmod(descriptor, 0o600)
        current = path.lstat()
        if (metadata.st_dev, metadata.st_ino) != (current.st_dev, current.st_ino):
            raise OSError('rollout lock changed')
    except OSError:
        if descriptor is not None:
            os.close(descriptor)
        raise SystemExit('Platform rollout lock unavailable or unsafe; prepare test-environment/private and retry after the current operator finishes.') from None
    try:
        yield descriptor
    finally:
        os.close(descriptor)


def environment():
    return dict(line.split("=", 1) for line in (ROOT / ".env.test").read_text().splitlines()
                if line and not line.startswith("#"))


def mysql(sql, database="mor_platform", timeout_seconds=None):
    if timeout_seconds is not None and (
            isinstance(timeout_seconds, bool)
            or not isinstance(timeout_seconds, (int, float))
            or not math.isfinite(float(timeout_seconds))
            or timeout_seconds <= 0):
        raise ValueError("Test SQL timeout must be a positive finite number.")
    options = {"input": sql, "encoding": "utf-8", "capture_output": True}
    if timeout_seconds is not None:
        options["timeout"] = float(timeout_seconds)
    try:
        result = subprocess.run(
            [str(ROOT / "compose.sh"), "exec", "-T", "mysql", "sh", "-c",
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot '
             '--default-character-set=utf8mb4 --batch --skip-column-names --skip-reconnect "$1"',
             "mysql", database],
            **options
        )
    except subprocess.TimeoutExpired:
        raise SystemExit(
            "Test SQL timed out; credentials, query and response omitted."
        ) from None
    if result.returncode:
        raise SystemExit("Test SQL failed; no retry or destructive recovery was performed. Inspect the reviewed SQL and test database.")
    return result.stdout.strip()


def count(sql, database="mor_platform"):
    return int(mysql(sql, database=database))



def apply_platform_sql():
    with risk_operations_rollout_lock():
        _apply_platform_sql_locked()


def _apply_platform_sql_locked():
    sql_dir = ROOT.parent / "platform-support-service/docs/database/sql"
    tables = count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_platform';")
    if tables == 0:
        mysql((sql_dir / "platform-support-schema.sql").read_text(encoding="utf-8"))
        mysql((sql_dir / "platform-support-initial-data.sql").read_text(encoding="utf-8"))
        print("Applied reviewed Platform schema and initial catalog to the new mor-test database.")
    elif tables != 22 or count("SELECT COUNT(*) FROM t_gateway_route;") not in (13, 17, 18, 19, 20, 21, 27, 36):
        raise SystemExit("Unexpected/partial test schema or catalog; manual review required, nothing replaced.")
    before_stage = None
    if count('SELECT COUNT(*) FROM t_gateway_route;') in (27, 36):
        before_stage = read_risk_operations_stage()['stage']
    accounts = count("SELECT COUNT(*) FROM t_user_account;")
    keys = count("SELECT COUNT(*) FROM t_jwt_signing_key;")
    if accounts == 0 and keys == 0:
        mysql((ROOT / "private/bootstrap.sql").read_text(encoding="utf-8"))
        print("Initialized the test-only Bootstrap verifier and matching real public signing key.")
    elif accounts == 0 or keys == 0:
        raise SystemExit("Partial authentication initialization; refusing to recreate/reset Bootstrap or keys.")
    else:
        print("Existing Accounts and signing authority preserved; no Bootstrap reset.")
    if count('SELECT COUNT(*) FROM t_gateway_route;') in (27, 36):
        after_stage = read_risk_operations_stage()['stage']
        if after_stage != before_stage:
            raise SystemExit('Platform catalog changed during initialization; manual review required.')
        print(after_stage)


def _stable_sql_text(path):
    try:
        first = path.lstat()
    except FileNotFoundError:
        raise SystemExit("Reviewed Risk SQL is missing; nothing was applied.") from None
    if stat.S_ISLNK(first.st_mode) or not stat.S_ISREG(first.st_mode):
        raise SystemExit("Reviewed Risk SQL must be a regular non-symlink file; nothing was applied.")
    try:
        with path.open("r", encoding="utf-8", newline="") as source:
            opened = os.fstat(source.fileno())
            text = source.read()
            finished = os.fstat(source.fileno())
    except OSError:
        raise SystemExit("Reviewed Risk SQL could not be read safely; nothing was applied.") from None
    identity = (first.st_dev, first.st_ino, first.st_size, first.st_mtime_ns)
    if identity != (opened.st_dev, opened.st_ino, opened.st_size, opened.st_mtime_ns) or identity != (finished.st_dev, finished.st_ino, finished.st_size, finished.st_mtime_ns):
        raise SystemExit("Reviewed Risk SQL changed while it was read; nothing was applied.")
    return text


def _sql_statements(text):
    statements, current = [], []
    quoted = line_comment = False
    index = 0
    while index < len(text):
        character = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""
        if line_comment:
            if character == "\n":
                line_comment = False
                current.append(character)
            index += 1
            continue
        if not quoted and character == "-" and following == "-" and (index == 0 or text[index - 1].isspace()):
            line_comment = True
            index += 2
            continue
        current.append(character)
        if character == "'":
            if quoted and following == "'":
                current.append(following)
                index += 2
                continue
            if index == 0 or text[index - 1] != "\\":
                quoted = not quoted
        elif character == ";" and not quoted:
            statement = "".join(current[:-1]).strip()
            if statement:
                statements.append(statement)
            current = []
        index += 1
    remainder = "".join(current).strip()
    if quoted or remainder:
        raise SystemExit("Reviewed Risk SQL contains an unaccounted executable statement; nothing was applied.")
    return statements


def _outer_items(text):
    items, current = [], []
    quoted = False
    depth = 0
    index = 0
    while index < len(text):
        character = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""
        if character == "'":
            if quoted and following == "'":
                current.extend((character, following))
                index += 2
                continue
            quoted = not quoted
        if not quoted:
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
                if depth < 0:
                    raise ValueError("unexpected close parenthesis")
            elif character == "," and depth == 0:
                items.append("".join(current).strip())
                current = []
                index += 1
                continue
        current.append(character)
        index += 1
    if quoted or depth != 0 or not "".join(current).strip():
        raise ValueError("incomplete SQL list")
    items.append("".join(current).strip())
    return items


def _values(row):
    values, current = [], []
    quoted = False
    index = 0
    while index < len(row):
        character = row[index]
        following = row[index + 1] if index + 1 < len(row) else ""
        if character == "'":
            if quoted and following == "'":
                current.append("'")
                index += 2
                continue
            quoted = not quoted
        elif character == "," and not quoted:
            values.append("".join(current).strip())
            current = []
        else:
            current.append(character)
        index += 1
    if quoted:
        raise ValueError("unterminated value")
    values.append("".join(current).strip())
    return tuple(values)


def _insert_rows(statement, table):
    match = re.fullmatch(r"INSERT\s+INTO\s+`" + re.escape(table) + r"`\s*\((.*?)\)\s*VALUES\s*(.+)", statement, re.IGNORECASE | re.DOTALL)
    if not match:
        raise SystemExit("Reviewed Risk DML contains an unaccounted executable statement; nothing was applied.")
    try:
        columns = tuple(value.strip().strip("`") for value in _outer_items(match.group(1)))
    except ValueError:
        raise SystemExit("Reviewed Risk DML has an invalid column list; nothing was applied.") from None
    if not columns or any(not re.fullmatch(r"[a-z_]+", value) for value in columns):
        raise SystemExit("Reviewed Risk DML has an invalid column list; nothing was applied.")
    try:
        rows = []
        for raw_row in _outer_items(match.group(2)):
            if not raw_row.startswith("(") or not raw_row.endswith(")"):
                raise ValueError("row is not parenthesized")
            row = _values(raw_row[1:-1])
            if len(row) != len(columns):
                raise ValueError("row column count differs")
            rows.append(row)
    except ValueError:
        raise SystemExit("Reviewed Risk DML has unsupported values; nothing was applied.") from None
    return columns, tuple(rows)


def load_risk_catalog(schema_text=None, initial_data_text=None):
    schema_statements = _sql_statements(schema_text if schema_text is not None else _stable_sql_text(RISK_SCHEMA))
    tables = set()
    for statement in schema_statements:
        match = re.match(r"CREATE\s+TABLE\s+`([^`]+)`\s*\(", statement, re.IGNORECASE | re.DOTALL)
        if not match or not re.fullmatch(r"t_[a-z0-9_]+", match.group(1)):
            raise SystemExit("Reviewed Risk schema contains an unaccounted executable statement; nothing was applied.")
        tables.add(match.group(1))
    if len(tables) != len(schema_statements) or len(tables) != 71:
        raise SystemExit("Reviewed Risk schema must contain exactly 71 unique tables; nothing was applied.")
    dml_statements = _sql_statements(initial_data_text if initial_data_text is not None else _stable_sql_text(RISK_INITIAL_DATA))
    if len(dml_statements) != 2:
        raise SystemExit("Reviewed Risk DML must contain exactly the Point and Action catalog statements; nothing was applied.")
    point_columns, points = _insert_rows(dml_statements[0], "t_decision_point")
    action_columns, actions = _insert_rows(dml_statements[1], "t_action_version")
    if len(points) != 1 or len(actions) != 3:
        raise SystemExit("Reviewed Risk DML must contain one Point and three Action versions; nothing was applied.")
    return {"tables": frozenset(tables), "point_columns": point_columns, "points": points, "action_columns": action_columns, "actions": actions}


def _tab_rows(result, expected_columns):
    if not result:
        return tuple()
    rows = tuple(tuple(line.split("\t")) for line in result.splitlines())
    if any(len(row) != expected_columns for row in rows):
        raise SystemExit("Risk catalog query returned an unexpected row shape; manual review required.")
    return rows


def verify_risk_catalog():
    catalog = load_risk_catalog()
    objects = _tab_rows(mysql("SELECT table_name, table_type FROM information_schema.tables WHERE table_schema='mor_risk' ORDER BY table_name;", database="mor_risk"), 2)
    if not objects:
        return "EMPTY"
    expected_objects = frozenset((name, "BASE TABLE") for name in catalog["tables"])
    if len(objects) != len(expected_objects) or frozenset(objects) != expected_objects:
        raise SystemExit("Unexpected/partial Risk schema or non-table object; manual review required, nothing replaced.")
    point_columns = ", ".join("`" + value + "`" for value in catalog["point_columns"])
    action_columns = ", ".join("`" + value + "`" for value in catalog["action_columns"])
    points = _tab_rows(mysql("SELECT " + point_columns + " FROM t_decision_point ORDER BY decision_point_id;", database="mor_risk"), len(catalog["point_columns"]))
    actions = _tab_rows(mysql("SELECT " + action_columns + " FROM t_action_version ORDER BY action_version_id;", database="mor_risk"), len(catalog["action_columns"]))
    if points != catalog["points"] or actions != catalog["actions"]:
        raise SystemExit("Unexpected/partial Risk decision catalog; manual review required, nothing replaced.")
    return "PREPARED"


def apply_risk_sql():
    if verify_risk_catalog() != "EMPTY":
        raise SystemExit("Risk database is not empty; refusing to replay reviewed SQL.")
    schema = _stable_sql_text(RISK_SCHEMA)
    initial_data = _stable_sql_text(RISK_INITIAL_DATA)
    load_risk_catalog(schema, initial_data)
    mysql(schema, database="mor_risk")
    mysql(initial_data, database="mor_risk")
    if verify_risk_catalog() != "PREPARED":
        raise SystemExit("Risk SQL did not produce the complete expected catalog; manual review required.")
    print("Applied reviewed Risk schema and initial decision catalog to the new mor-test database.")


def _platform_value(token, variables):
    token = token.strip()
    if token in variables:
        return variables[token]
    if token == 'NULL':
        return None
    if re.fullmatch(r'[0-9]+', token):
        return int(token)
    if token.startswith("'") and token.endswith("'"):
        return token[1:-1].replace("''", "'")
    raise SystemExit('Unsupported value in reviewed Platform catalog; manual review required.')


def load_platform_catalog():
    """Project the manually reviewed Owner catalog; timestamps are not replay identity."""
    variables = {}
    result = {table: [] for table in PLATFORM_TABLES}
    for statement in _sql_statements(_stable_sql_text(PLATFORM_CATALOG)):
        assignment = re.fullmatch(r'SET\s+(@\w+)\s*=\s*(.+)', statement, re.S)
        if assignment:
            name, value = assignment.groups()
            variables[name] = None if value == 'UTC_TIMESTAMP(6)' else _platform_value(value, variables)
            continue
        match = re.fullmatch(r'INSERT INTO (\w+)\s*\((.*?)\)\s*VALUES\s*(.+)', statement, re.S)
        if not match or match.group(1) not in result:
            continue
        table, raw_columns, raw_rows = match.groups()
        columns = [column.strip(' `\n') for column in _outer_items(raw_columns)]
        for raw_row in _outer_items(raw_rows):
            tokens = _outer_items(raw_row[1:-1])
            if len(tokens) != len(columns):
                raise SystemExit('Reviewed Platform catalog row shape differs; manual review required.')
            row = {column: _platform_value(token, variables) for column, token in zip(columns, tokens)
                   if column not in {'create_time', 'update_time'}}
            result[table].append(row)
    for table, rows in result.items():
        if not rows:
            raise SystemExit('Reviewed Platform catalog family missing; manual review required.')
        rows.sort(key=lambda row: next(iter(row.values())))
        if table in RESERVED_RANGES:
            low, high = RESERVED_RANGES[table]
            ids = [next(iter(row.values())) for row in rows if _reserved_row(table, row)]
            if ids != list(range(900000000000000000 + low, 900000000000000001 + high)):
                raise SystemExit('Reviewed Platform reserved range differs; manual review required.')
    return result


def _without_unprepared_risk_rows(catalog, legacy):
    return {
        table: [dict(row) for row in rows
                if not legacy or not _reserved_row(table, row)]
        for table, rows in catalog.items()
    }


def _deployed_action_permission_history_profile(canonical):
    """Model the one reviewed mor-test permission-split history exactly.

    The 2026-09-04 manual increment retained two coarse Permission/Grant
    tombstones and allocated five replacement Grant IDs from its documented
    7001 range.  A newly initialized database instead uses 570..574.  Both
    shapes express the same current system-role grants, but hybrids do not.
    """
    profile = {table: [dict(row) for row in rows]
               for table, rows in canonical.items()}
    permission_columns = tuple(profile['t_permission'][0])
    role_permission_columns = tuple(profile['t_role_permission'][0])
    historical_permissions = (
        {
            'permission_id': 900000000000000521,
            'permission_code': 'OPERATIONS_ACCOUNT_MANAGE',
            'resource_type': 'USER_ACCOUNT',
            'action_code': 'MANAGE',
            'risk_level': 'HIGH',
            'status': 'INACTIVE',
            'version': 2,
        },
        {
            'permission_id': 900000000000000525,
            'permission_code': 'OPERATIONS_ROLE_MANAGE',
            'resource_type': 'ROLE',
            'action_code': 'MANAGE',
            'risk_level': 'HIGH',
            'status': 'INACTIVE',
            'version': 2,
        },
    )
    if any(tuple(row) != permission_columns for row in historical_permissions):
        raise SystemExit('Platform Permission history shape differs; manual review required.')
    profile['t_permission'].extend(dict(row) for row in historical_permissions)
    profile['t_permission'].sort(key=lambda row: row['permission_id'])

    replacements = {}
    for row in profile['t_role_permission']:
        identity = row['role_permission_id']
        if 900000000000000570 <= identity <= 900000000000000574:
            replacement = 900000000000007001 + identity - 900000000000000570
            row['role_permission_id'] = replacement
            replacements[row['permission_id']] = replacement
    if replacements != {
            900000000000000526: 900000000000007001,
            900000000000000527: 900000000000007002,
            900000000000000528: 900000000000007003,
            900000000000000529: 900000000000007004,
            900000000000000530: 900000000000007005}:
        raise SystemExit('Platform Grant history shape differs; manual review required.')
    historical_grants = (
        {
            'role_permission_id': 900000000000000531,
            'role_id': 900000000000000510,
            'permission_id': 900000000000000521,
            'status': 'REVOKED',
            'version': 2,
        },
        {
            'role_permission_id': 900000000000000539,
            'role_id': 900000000000000510,
            'permission_id': 900000000000000525,
            'status': 'REVOKED',
            'version': 2,
        },
    )
    if any(tuple(row) != role_permission_columns for row in historical_grants):
        raise SystemExit('Platform Grant history shape differs; manual review required.')
    profile['t_role_permission'].extend(dict(row) for row in historical_grants)
    profile['t_role_permission'].sort(key=lambda row: row['role_permission_id'])
    return profile


def _deployed_action_permission_history_statuses_are_safe(persisted):
    required = {
        't_permission': {
            900000000000000521: 'INACTIVE',
            900000000000000525: 'INACTIVE',
        },
        't_role_permission': {
            900000000000000531: 'REVOKED',
            900000000000000539: 'REVOKED',
            900000000000007001: 'ACTIVE',
            900000000000007002: 'ACTIVE',
            900000000000007003: 'ACTIVE',
            900000000000007004: 'ACTIVE',
            900000000000007005: 'ACTIVE',
        },
    }
    for table, expected in required.items():
        rows = {next(iter(row.values())): row for row in persisted[table]}
        if any(rows.get(identity, {}).get('status') != status
               for identity, status in expected.items()):
            return False
    return True


def _owner_grant_ids(catalog):
    return frozenset(
        row['role_permission_id'] for row in catalog['t_role_permission']
    ) | DEPLOYED_ACTION_PERMISSION_HISTORY_GRANT_IDS


def _dynamic_risk_role_grant(row, owner_grant_ids):
    """Return whether a prepared-catalog row belongs to mutable tenant IAM."""
    identity = row.get('role_permission_id')
    role_id = row.get('role_id')
    permission_id = row.get('permission_id')
    return (
        type(identity) is int
        and type(role_id) is int
        and type(permission_id) is int
        and role_id not in SYSTEM_ROLE_IDS
        and identity not in owner_grant_ids
        and 900000000000000536 <= permission_id <= 900000000000000540
    )


def _reserved_row(table, row):
    low, high = RESERVED_RANGES.get(table, (0, -1))
    return 900000000000000000 + low <= next(iter(row.values())) <= 900000000000000000 + high


def _numeric_column(column):
    return column.endswith('_id') or column in {
        'version', 'sort_order', 'system_role', 'created_by', 'updated_by', 'body_limit_bytes',
        'body_receive_timeout_millis', 'response_timeout_millis', 'capacity', 'refill_tokens',
        'refill_period_millis', 'concurrency_limit'}


def _platform_snapshot_query(catalog):
    statements = ['SET NAMES utf8mb4;', 'SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;',
                  'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;']
    for table, rows in catalog.items():
        columns = list(rows[0])
        # Read child tables directly: orphan rows must remain visible. System-role
        # grants and reserved Grant IDs are exact; prepared tenant-role grants are
        # separated from the Owner catalog only after this snapshot is decoded.
        where = ''
        if table == 't_role':
            where = " WHERE role_id IN (900000000000000510, 900000000000000511) OR role_code IN ('PLATFORM_ADMIN', 'OPERATIONS_VIEWER')"
        elif table == 't_role_permission':
            owner_grant_ids = ', '.join(str(identity) for identity in sorted(_owner_grant_ids(catalog)))
            where = (' WHERE role_id IN (900000000000000510, 900000000000000511)'
                     ' OR role_permission_id IN (' + owner_grant_ids + ')'
                     ' OR role_permission_id BETWEEN 900000000000000581 AND 900000000000000586'
                     ' OR permission_id BETWEEN 900000000000000536 AND 900000000000000540')
        statements.append("SELECT 'BEGIN', '" + table + "', " + str(len(columns)) + ';')
        encoded = []
        for column in columns:
            value = "CONCAT('I', CAST(`" + column + "` AS CHAR))" if _numeric_column(column) else "CONCAT('S', HEX(`" + column + "`))"
            encoded.append("CASE WHEN `" + column + "` IS NULL THEN 'N' ELSE " + value + ' END')
        statements.append("SELECT 'ROW', '" + table + "', " + ', '.join(encoded) + ' FROM ' + table + where + ' ORDER BY `' + columns[0] + '`;')
        statements.append("SELECT 'END', '" + table + "', COUNT(*) FROM " + table + where + ';')
    statements.append('COMMIT;')
    return '\n'.join(statements)


def _decode_platform_snapshot(output, catalog):
    lines = iter(output.splitlines())
    result = {}
    try:
        for table, expected in catalog.items():
            columns = list(expected[0])
            if next(lines).split('\t') != ['BEGIN', table, str(len(columns))]:
                raise ValueError('missing typed section')
            rows = []
            while True:
                cells = next(lines).split('\t')
                if cells[:2] == ['END', table]:
                    if cells != ['END', table, str(len(rows))]:
                        raise ValueError('row count differs')
                    break
                if cells[:2] != ['ROW', table] or len(cells) != len(columns) + 2:
                    raise ValueError('row shape differs')
                row = {}
                for column, cell in zip(columns, cells[2:]):
                    if cell == 'N':
                        value = None
                    elif _numeric_column(column) and re.fullmatch(r'I-?(0|[1-9][0-9]*)', cell):
                        value = int(cell[1:])
                    elif not _numeric_column(column) and re.fullmatch(r'S(?:[0-9A-F]{2})*', cell):
                        value = bytes.fromhex(cell[1:]).decode('utf-8')
                    else:
                        raise ValueError('invalid typed value')
                    row[column] = value
                rows.append(row)
            result[table] = rows
        if next(lines, None) is not None:
            raise ValueError('unexpected extra section')
    except (StopIteration, ValueError, UnicodeError):
        raise SystemExit('Invalid typed Platform snapshot; manual review required.') from None
    return result


def read_risk_operations_catalog(timeout_seconds=None):
    """Verify exact persisted rows and status domains without a rollout-stage gate.

    Resource-first rollback needs this view even when visible Resources and
    Route statuses no longer form a legal combined stage.
    """
    catalog = load_platform_catalog()
    query = _platform_snapshot_query(catalog)
    output = (mysql(query) if timeout_seconds is None
              else mysql(query, timeout_seconds=timeout_seconds))
    persisted = _decode_platform_snapshot(output, catalog)
    legacy = all(not any(_reserved_row(table, row) for row in persisted[table]) for table in RESERVED_RANGES)
    canonical = _without_unprepared_risk_rows(catalog, legacy)
    owner_grant_ids = _owner_grant_ids(catalog)
    candidates = [
        ('canonical', canonical),
        ('deployed-action-permission-history',
         _deployed_action_permission_history_profile(canonical)),
    ]
    for table in catalog:
        actual = persisted[table]
        compared = [row for row in actual
                    if not (table == 't_role_permission' and not legacy
                            and _dynamic_risk_role_grant(row, owner_grant_ids))]
        def immutable(row):
            return tuple((column, value) for column, value in row.items() if column not in REPLAY_MASKS[table])
        candidates = [
            (name, profile) for name, profile in candidates
            if [immutable(row) for row in compared]
            == [immutable(row) for row in profile[table]]
        ]
        if not candidates:
            raise SystemExit('Partial or drifted Platform catalog (' + table + '); no mutation performed.')
        for row in actual:
            if 'status' in row and row['status'] not in PLATFORM_STATUS_DOMAINS[table]:
                raise SystemExit('Invalid Platform status (' + table + '); manual review required.')
            if 'version' in row and (type(row['version']) is not int or row['version'] <= 0):
                raise SystemExit('Invalid Platform version; manual review required.')
    if (candidates[0][0] == 'deployed-action-permission-history'
            and not _deployed_action_permission_history_statuses_are_safe(persisted)):
        raise SystemExit('Invalid Platform permission history status; manual review required.')
    resources = {row['resource_code']: row['display_name'] for row in persisted['t_console_resource'] if row['status'] == 'ACTIVE'}
    routes = {row['route_code']: row['status'] for row in persisted['t_gateway_route']}
    return {'resources': resources, 'routes': routes,
            'resource_rows': persisted['t_console_resource'], 'route_rows': persisted['t_gateway_route']}


def read_risk_operations_stage(timeout_seconds=None):
    """Classify only after the exact raw catalog has been verified."""
    persisted = (read_risk_operations_catalog() if timeout_seconds is None
                 else read_risk_operations_catalog(timeout_seconds=timeout_seconds))
    try:
        stage = classify_stage(persisted['resources'], persisted['routes'])
    except ValueError as error:
        raise SystemExit('Platform rollout is not a closed stage: ' + str(error)) from None
    return dict(persisted, stage=stage)


def verify_risk_operations_stage():
    return read_risk_operations_stage()['stage']


def apply_risk_operations_sql():
    with risk_operations_rollout_lock():
        before = read_risk_operations_stage()['stage']
        if before == 'LEGACY':
            mysql(_stable_sql_text(RISK_OPERATIONS_SQL))
        after = read_risk_operations_stage()['stage']
        if after != ('BASELINE' if before == 'LEGACY' else before):
            raise SystemExit('Incremental Platform postcondition failed; manual review required.')
        print(after)


def enable_risk_operations_resources():
    with risk_operations_rollout_lock():
        before = read_risk_operations_stage()['stage']
        if before not in {'ALL_ROUTES_ACTIVE', 'RESOURCES_EXPOSED'}:
            raise SystemExit('Resource action precondition failed; no SQL applied.')
        needs_change = before == 'ALL_ROUTES_ACTIVE'
        if needs_change:
            mysql(_stable_sql_text(RISK_OPERATIONS_SQL.with_name(RISK_OPERATIONS_SQL.stem + '-resources-enable.sql')))
        after = read_risk_operations_stage()['stage']
        expected = 'RESOURCES_EXPOSED'
        if after != expected:
            raise SystemExit('Resource action postcondition failed; manual review required.')
        print(after)


def disable_risk_operations_resources():
    with risk_operations_rollout_lock():
        before = read_risk_operations_catalog()
        resources = [row for row in before['resource_rows'] if _reserved_row('t_console_resource', row)]
        statuses = {row['status'] for row in resources}
        if len(resources) != 6 or statuses not in ({'ACTIVE'}, {'DISABLED'}):
            raise SystemExit('Resource disable requires all six ACTIVE or all six DISABLED; no SQL applied.')
        if statuses == {'ACTIVE'}:
            mysql(_stable_sql_text(RISK_OPERATIONS_SQL.with_name(RISK_OPERATIONS_SQL.stem + '-resources-disable.sql')))
        after = read_risk_operations_catalog()
        disabled = [row for row in after['resource_rows'] if _reserved_row('t_console_resource', row)]
        if len(disabled) != 6 or any(row['status'] != 'DISABLED' for row in disabled) or after['routes'] != before['routes']:
            raise SystemExit('Resource disable postcondition failed; manual review required.')
        try:
            stage = classify_stage(after['resources'], after['routes'])
        except ValueError:
            print('Risk Resources DISABLED; Route stage requires manual review.')
        else:
            print(stage)


def nacos_request(method, path, values=None, token=None):
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = urllib.parse.urlencode(values or {}).encode() if method != "GET" else None
    request = urllib.request.Request("http://127.0.0.1:18848/nacos" + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            result = json.loads(error.read())
        except (ValueError, UnicodeDecodeError):
            result = {}
        return error.code, result
    except urllib.error.URLError:
        raise SystemExit("Test Nacos is unreachable on 127.0.0.1:18848; check mor-test health and port mappings.") from None


def require_success(result, operation):
    status, body = result
    if status != 200 or body.get("code", 0) not in (0, 200):
        raise SystemExit(f"Nacos {operation} failed (HTTP {status}, code {body.get('code')}); secrets omitted.")
    return body


def login(username, password):
    body = require_success(nacos_request("POST", "/v3/auth/user/login", {"username": username, "password": password}), "login")
    token = body.get("accessToken") or (body.get("data") or {}).get("accessToken")
    if not token:
        raise SystemExit("Nacos login returned no token; secrets omitted.")
    return token


@contextlib.contextmanager
def _restricted_file_creation():
    previous = os.umask(0o077)
    try:
        yield
    finally:
        os.umask(previous)


def initialize_nacos():
    values = environment()
    marker = ROOT / "private/nacos-initialized"
    if marker.exists():
        login("mor-test-services", values["MOR_NACOS_SERVICE_PASSWORD"])
        print("Existing test Nacos service credentials verified.")
        return
    nacos_request("POST", "/v3/auth/user/admin", {"password": values["MOR_NACOS_ADMIN_PASSWORD"]})
    token = login("nacos", values["MOR_NACOS_ADMIN_PASSWORD"])
    result = nacos_request("POST", "/v3/admin/core/namespace", {"namespaceId": "mor-test", "namespaceName": "mor-test", "namespaceDesc": "Independent Operations Docker test environment"}, token)
    require_success(result, "namespace creation")
    require_success(nacos_request("POST", "/v3/auth/user", {"username": "mor-test-services", "password": values["MOR_NACOS_SERVICE_PASSWORD"]}, token), "service-user creation")
    require_success(nacos_request("POST", "/v3/auth/role", {"role": "MOR_TEST_SERVICES", "username": "mor-test-services"}, token), "role binding")
    require_success(nacos_request("POST", "/v3/auth/permission", {"role": "MOR_TEST_SERVICES", "resource": "mor-test:*:*", "action": "rw"}, token), "namespace permission")
    login("mor-test-services", values["MOR_NACOS_SERVICE_PASSWORD"])
    with _restricted_file_creation():
        marker.write_text("mor-test namespace and scoped service identity initialized\n")
        marker.chmod(0o600)
    print("Created mor-test Nacos namespace and its scoped service identity.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_mutually_exclusive_group(required=True)
    actions.add_argument("--apply-platform-sql", action="store_true", help="explicitly apply reviewed Platform SQL")
    actions.add_argument("--nacos", action="store_true", help="initialize the new test namespace and identities")
    actions.add_argument("--apply-risk-sql", action="store_true", help="explicitly apply reviewed Risk SQL to empty mor_risk")
    actions.add_argument("--verify-risk-catalog", action="store_true", help="read-only check for the Risk catalog")
    actions.add_argument('--verify-risk-operations-stage', action='store_true', help='read-only check for the persisted Operations stage')
    actions.add_argument('--apply-risk-operations-sql', action='store_true', help='explicitly apply disabled Operations Risk catalog')
    actions.add_argument('--enable-risk-operations-resources', action='store_true', help='expose Risk Resources after all nine Routes are ACTIVE')
    actions.add_argument('--disable-risk-operations-resources', action='store_true', help='hide Risk Resources as the first rollback step')
    options = parser.parse_args(argv)
    if options.apply_platform_sql:
        apply_platform_sql()
    elif options.nacos:
        initialize_nacos()
    elif options.apply_risk_sql:
        apply_risk_sql()
    elif options.verify_risk_catalog:
        print(verify_risk_catalog())
    elif options.verify_risk_operations_stage:
        print(verify_risk_operations_stage())
    elif options.apply_risk_operations_sql:
        apply_risk_operations_sql()
    elif options.enable_risk_operations_resources:
        enable_risk_operations_resources()
    else:
        disable_risk_operations_resources()


if __name__ == "__main__":
    main()
