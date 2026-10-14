"""对照正式 Schema 文件与 mor-test 实际库：逐表比较列（顺序、类型、可空、默认、注释）、索引（列、唯一、注释）与表注释。

只读：经 docker exec 以 root 执行 information_schema 查询，密码来自 test-environment/.env.test，不打印。
用法：python3 -I compare_schema.py <schema.sql> <database> [table ...]
输出每张有差异的表及差异项；完全一致时退出码 0，否则 1。
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
ENV_TEST = ROOT / "test-environment/.env.test"

TYPE_ALIASES = {"boolean": "tinyint(1)", "bool": "tinyint(1)", "integer": "int"}


def password():
    for line in ENV_TEST.read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1]
    raise SystemExit("缺少 MOR_MYSQL_ROOT_PASSWORD")


def query(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + password(), "mor-test-mysql-1", "mysql", "-uroot",
         "--default-character-set=utf8mb4", "-N", "-B", "--raw"],
        input=sql, text=True, capture_output=True, timeout=60)
    if result.returncode:
        raise SystemExit("MySQL 查询失败：" + result.stderr.strip()[:300])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def unquote(text):
    """SQL 单引号字符串字面量 → 值。"""
    return text[1:-1].replace("''", "'").replace("\\'", "'")


def normalize_type(text):
    text = text.strip().lower()
    text = TYPE_ALIASES.get(text, text)
    # MySQL 8 不保留整数显示宽度（tinyint(1) 除外）。
    return re.sub(r"^(bigint|int|smallint|mediumint)\(\d+\)", r"\1", text)


def normalize_default(value):
    if value is None:
        return None
    value = value.strip()
    if value.upper() == "NULL":
        return None
    if value.startswith("'"):
        return unquote(value)
    return value.upper().replace("CURRENT_TIMESTAMP(6)", "CURRENT_TIMESTAMP(6)")


COLUMN = re.compile(
    r"^`(?P<name>\w+)`\s+(?P<type>[A-Za-z]+(?:\([^)]*\))?(?:\s+unsigned)?)"
    r"(?P<rest>.*?)(?:\s+COMMENT\s+(?P<comment>'(?:[^']|'')*'))?,?$", re.I)
INDEX = re.compile(
    r"^(?P<kind>PRIMARY KEY|UNIQUE KEY|KEY)\s*(?:`(?P<name>\w+)`)?\s*\((?P<cols>[^)]*)\)"
    r"(?:\s+COMMENT\s+(?P<comment>'(?:[^']|'')*'))?,?$", re.I)


def expected_tables(schema_text):
    tables = {}
    for match in re.finditer(r"CREATE TABLE\s+`(\w+)`\s*\((.*?)\n\)\s*([^;]*);", schema_text, re.S):
        name, body, options = match.groups()
        columns, indexes = [], {}
        for raw in body.split("\n"):
            line = raw.strip()
            if not line:
                continue
            index = INDEX.match(line)
            if index:
                key = "PRIMARY" if index["kind"].upper() == "PRIMARY KEY" else index["name"]
                cols = tuple(c.strip().strip("`") for c in index["cols"].split(","))
                comment = unquote(index["comment"]) if index["comment"] else ""
                indexes[key] = (cols, index["kind"].upper() != "KEY", comment)
                continue
            column = COLUMN.match(line)
            if not column:
                raise SystemExit(f"无法解析 {name}: {line[:120]}")
            rest = column["rest"]
            default = re.search(r"\bDEFAULT\s+('(?:[^']|'')*'|\S+)", rest, re.I)
            columns.append((column["name"], normalize_type(column["type"]),
                            not re.search(r"\bNOT NULL\b", rest, re.I),
                            normalize_default(default.group(1)) if default else None,
                            unquote(column["comment"]) if column["comment"] else ""))
        comment = re.search(r"COMMENT\s*=\s*('(?:[^']|'')*')", options)
        tables[name] = (columns, indexes, unquote(comment.group(1)) if comment else "")
    return tables


def actual_tables(database):
    columns, indexes, comments = {}, {}, {}
    for table, name, ctype, nullable, default, comment in query(
            "SELECT table_name, column_name, column_type, is_nullable, IFNULL(column_default, '\\\\N'), column_comment "
            f"FROM information_schema.columns WHERE table_schema='{database}' ORDER BY table_name, ordinal_position;"):
        value = None if default == "\\N" else default
        if value is not None and ctype.startswith(("datetime", "timestamp")):
            value = value.upper()
        columns.setdefault(table, []).append((name, normalize_type(ctype), nullable == "YES",
                                              normalize_default("'" + value.replace("'", "''") + "'")
                                              if value is not None and not value.upper().startswith("CURRENT_TIMESTAMP")
                                              else value, comment))
    for table, name, unique, cols, comment in query(
            "SELECT table_name, index_name, MIN(non_unique)=0, GROUP_CONCAT(column_name ORDER BY seq_in_index), "
            "MAX(index_comment) FROM information_schema.statistics "
            f"WHERE table_schema='{database}' GROUP BY table_name, index_name;"):
        indexes.setdefault(table, {})[name] = (tuple(cols.split(",")), unique == "1", comment)
    for table, comment in query(
            f"SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema='{database}';"):
        comments[table] = comment
    return {t: (columns.get(t, []), indexes.get(t, {}), comments[t]) for t in comments}


def compare(expected, actual, only):
    differences = 0
    names = sorted(only or (set(expected) | set(actual)))
    for table in names:
        if table not in actual:
            print(f"MISSING TABLE {table}")
            differences += 1
            continue
        if table not in expected:
            print(f"EXTRA TABLE {table}")
            differences += 1
            continue
        e_cols, e_idx, e_comment = expected[table]
        a_cols, a_idx, a_comment = actual[table]
        problems = []
        if [c[0] for c in e_cols] != [c[0] for c in a_cols]:
            problems.append(f"列顺序/集合: 期望 {[c[0] for c in e_cols]} 实际 {[c[0] for c in a_cols]}")
        a_by_name = {c[0]: c for c in a_cols}
        for column in e_cols:
            found = a_by_name.get(column[0])
            if found and found != column:
                problems.append(f"列 {column[0]}: 期望 {column[1:]} 实际 {found[1:]}")
        for key in sorted(set(e_idx) | set(a_idx)):
            if e_idx.get(key) != a_idx.get(key):
                problems.append(f"索引 {key}: 期望 {e_idx.get(key)} 实际 {a_idx.get(key)}")
        if e_comment != a_comment:
            problems.append(f"表注释: 期望 {e_comment!r} 实际 {a_comment!r}")
        if problems:
            differences += 1
            print(f"DIFF {table}")
            for problem in problems:
                print("  - " + problem[:400])
    print(f"比较 {len(names)} 张表，差异 {differences} 张")
    return differences


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    schema = expected_tables(Path(sys.argv[1]).read_text(encoding="utf-8"))
    tables = sys.argv[3:]
    sys.exit(1 if compare(schema, actual_tables(sys.argv[2]), tables) else 0)
