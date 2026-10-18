#!/usr/bin/env python3
"""Compare legacy mor-test tables with their V2 targets on one MySQL instance (read-only).

The old databases (mor_platform, mor_vault, mor_cct, mor_commerce, mor_risk) and reference copies of the
reviewed V2 schemas (v2ref_<db>) must live on the same server. Only information_schema and COUNT(*) are read;
no row values are printed. Connection: MT_HOST, MT_PORT, MYSQL_PWD (root).

Usage: schema_diff.py [--json]
"""
import json
import os
import subprocess
import sys

# Legacy database -> V2 reference databases that may own its tables (design/03 §2, table-ownership.yaml).
ROUTES = {
    "mor_platform": ["v2ref_platform"],
    "mor_vault": ["v2ref_vault"],
    "mor_cct": ["v2ref_client_master", "v2ref_commercial"],
    "mor_commerce": ["v2ref_acquiring", "v2ref_consumer", "v2ref_client_master"],
    "mor_risk": ["v2ref_risk_decision", "v2ref_compliance", "v2ref_dispute"],
}


def query(sql):
    command = ["mysql", "-h", os.environ.get("MT_HOST", "127.0.0.1"), "-P", os.environ.get("MT_PORT", "23307"),
               "-uroot", "--protocol=tcp", "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
               "-e", sql]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        sys.exit("query failed: " + result.stderr.strip().splitlines()[-1])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def columns(schema):
    rows = query("SELECT table_name, column_name, ordinal_position, column_type, is_nullable, "
                 "IFNULL(column_default,'<NULL>'), extra, character_set_name, collation_name FROM information_schema.columns "
                 "WHERE table_schema='%s' ORDER BY table_name, ordinal_position" % schema)
    tables = {}
    for table, column, position, ctype, nullable, default, extra, charset, collation in rows:
        tables.setdefault(table, {})[column] = {
            "pos": int(position), "type": ctype, "null": nullable == "YES", "default": default,
            "extra": extra, "collation": collation}
    return tables


def indexes(schema):
    rows = query("SELECT table_name, index_name, non_unique, GROUP_CONCAT(IFNULL(column_name, CONCAT('(', expression, ')')) "
                 "ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema='%s' "
                 "GROUP BY table_name, index_name, non_unique" % schema)
    tables = {}
    for table, name, non_unique, cols in rows:
        tables.setdefault(table, {})[name] = {"unique": non_unique == "0", "columns": cols}
    return tables


def row_counts(schema, tables):
    if not tables:
        return {}
    sql = " UNION ALL ".join("SELECT '%s', COUNT(*) FROM `%s`.`%s`" % (t, schema, t) for t in sorted(tables))
    return {t: int(c) for t, c in query(sql)}


def compare(old_cols, new_cols, old_idx, new_idx):
    diff = {"dropped": [], "added": [], "changed": [], "index_only_old": [], "index_only_new": [], "index_changed": []}
    for name, spec in old_cols.items():
        if name not in new_cols:
            diff["dropped"].append({"column": name, **spec})
        else:
            other = new_cols[name]
            changes = {k: [spec[k], other[k]] for k in ("type", "null", "default", "extra", "collation") if spec[k] != other[k]}
            if changes:
                diff["changed"].append({"column": name, **changes})
    for name, spec in new_cols.items():
        if name not in old_cols:
            diff["added"].append({"column": name, **spec})
    for name, spec in old_idx.items():
        if name not in new_idx:
            diff["index_only_old"].append({"index": name, **spec})
        elif new_idx[name] != spec:
            diff["index_changed"].append({"index": name, "old": spec, "new": new_idx[name]})
    for name, spec in new_idx.items():
        if name not in old_idx:
            diff["index_only_new"].append({"index": name, **spec})
    return diff


def main():
    report = {"tables": [], "new_only": {}}
    targets = {ref: (columns(ref), indexes(ref)) for refs in ROUTES.values() for ref in refs}
    claimed = {ref: set() for ref in targets}
    for old_db, refs in ROUTES.items():
        old_cols, old_idx = columns(old_db), indexes(old_db)
        counts = row_counts(old_db, old_cols)
        for table in sorted(old_cols):
            entry = {"old": old_db + "." + table, "rows": counts[table], "targets": []}
            for ref in refs:
                ref_cols, ref_idx = targets[ref]
                if table in ref_cols:
                    claimed[ref].add(table)
                    entry["targets"].append({"db": ref, "diff": compare(old_cols[table], ref_cols[table],
                                                                        old_idx.get(table, {}), ref_idx.get(table, {}))})
            report["tables"].append(entry)
    for ref, (ref_cols, _) in targets.items():
        report["new_only"][ref] = sorted(set(ref_cols) - claimed[ref])
    if "--json" in sys.argv:
        json.dump(report, sys.stdout, ensure_ascii=False, indent=1)
        return
    for entry in report["tables"]:
        heads = ", ".join(t["db"] for t in entry["targets"]) or "NO TARGET"
        print("%s rows=%d -> %s" % (entry["old"], entry["rows"], heads))
        for target in entry["targets"]:
            d = target["diff"]
            for key in ("dropped", "added", "changed", "index_only_old", "index_only_new", "index_changed"):
                for item in d[key]:
                    print("    [%s] %s %s" % (target["db"], key, json.dumps(item, ensure_ascii=False)))
    for ref, tables in report["new_only"].items():
        if tables:
            print("NEW ONLY %s: %s" % (ref, " ".join(tables)))


if __name__ == "__main__":
    main()
