#!/usr/bin/env python3
"""Helpers for migrate.sh; never prints secret values.

  render <sql file> <env file>     print SQL with ${VAR} placeholders replaced from the env file (stdout only, piped to mysql)
  tables <schema file>             print the table names created by a reviewed schema file, one per line
  seed-keys <initial-data file>    print "<table> <first numeric value>" for each seeded row (primary keys of the catalog)
  inserts <initial-data file>      print the number of INSERT statements
  fingerprint                      read "schema<TAB>table<TAB>column…" rows on stdin, print their SHA-256
"""
import hashlib
import re
import sys

PASSWORD = re.compile(r"[A-Za-z0-9._@%+=:-]{16,128}")


def read_env(path):
    values = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key] = value
    return values


def render(sql_path, env_path):
    values = read_env(env_path)
    text = open(sql_path, encoding="utf-8").read()
    names = sorted(set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)\}", text)))
    missing = [n for n in names if n not in values]
    if missing:
        sys.exit("missing in env file: " + ", ".join(missing))
    invalid = [n for n in names if not PASSWORD.fullmatch(values[n])]
    if invalid:
        sys.exit("invalid password format (expected [A-Za-z0-9._@%+=:-]{16,128}): " + ", ".join(invalid))
    sys.stdout.write(re.sub(r"\$\{([A-Z][A-Z0-9_]*)\}", lambda m: values[m.group(1)], text))


def tables(schema_path):
    text = open(schema_path, encoding="utf-8").read()
    for name in re.findall(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(t_[a-z0-9_]+)`?\s*\(", text, re.IGNORECASE):
        print(name)


def statements(path):
    text = open(path, encoding="utf-8").read()
    text = "\n".join(line for line in text.splitlines() if not line.lstrip().startswith("--"))
    return [s.strip() for s in text.split(";\n") if s.strip()]


def seed_keys(path):
    for statement in statements(path):
        match = re.match(r"INSERT\s+INTO\s+`?(t_[a-z0-9_]+)`?\s*\(", statement, re.IGNORECASE)
        if not match:
            continue
        body = statement.split("VALUES", 1)[1] if "VALUES" in statement else ""
        for key in re.findall(r"\(\s*(\d{6,})\s*,", body):
            print(match.group(1), key)


def inserts(path):
    print(sum(1 for s in statements(path) if re.match(r"INSERT\s+INTO", s, re.IGNORECASE)))


def fingerprint():
    print(hashlib.sha256(sys.stdin.read().encode("utf-8")).hexdigest())


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"render": render, "tables": tables, "seed-keys": seed_keys, "inserts": inserts, "fingerprint": fingerprint}[command](*args)
