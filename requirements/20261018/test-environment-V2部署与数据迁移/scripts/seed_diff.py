#!/usr/bin/env python3
"""Compare reviewed V2 initial-data rows (loaded into v2ref_<db>) with migrated/legacy rows by primary key (read-only).

Prints per table: seed rows, seed keys missing in the target, and keys present whose non-timestamp columns differ.
Only primary-key values (numeric IDs or codes) are printed, never other column values.
Usage: seed_diff.py <reference_db> <target_db>   (MT_HOST, MT_PORT, MYSQL_PWD for root)
"""
import os
import subprocess
import sys

IGNORED = {"create_time", "update_time", "created_at", "updated_at"}


def query(sql):
    command = ["mysql", "-h", os.environ.get("MT_HOST", "127.0.0.1"), "-P", os.environ.get("MT_PORT", "23307"), "-uroot",
               "--protocol=tcp", "--default-character-set=utf8mb4", "--batch", "--skip-column-names", "-e", sql]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        sys.exit("query failed: " + result.stderr.strip().splitlines()[-1])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def main(reference, target):
    tables = [r[0] for r in query("SELECT table_name FROM information_schema.tables WHERE table_schema='%s' "
                                  "AND table_rows IS NOT NULL ORDER BY table_name" % reference)]
    existing = {r[0] for r in query("SELECT table_name FROM information_schema.tables WHERE table_schema='%s'" % target)}
    for table in tables:
        if table not in existing:
            print("%s target table absent" % table)
            continue
        seeds = int(query("SELECT COUNT(*) FROM `%s`.`%s`" % (reference, table))[0][0])
        if not seeds:
            continue
        pk = [r[0] for r in query("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema='%s' "
                                  "AND table_name='%s' AND constraint_name='PRIMARY' ORDER BY ordinal_position" % (reference, table))]
        cols = [r[0] for r in query("SELECT column_name FROM information_schema.columns WHERE table_schema='%s' AND table_name='%s' "
                                    "ORDER BY ordinal_position" % (reference, table)) if r[0] not in IGNORED]
        join = " AND ".join("r.`%s` <=> t.`%s`" % (c, c) for c in pk)
        key = "CONCAT_WS('/', %s)" % ", ".join("r.`%s`" % c for c in pk)
        missing = query("SELECT %s FROM `%s`.`%s` r LEFT JOIN `%s`.`%s` t ON %s WHERE t.`%s` IS NULL"
                        % (key, reference, table, target, table, join, pk[0]))
        differ = query("SELECT %s FROM `%s`.`%s` r JOIN `%s`.`%s` t ON %s WHERE NOT (%s)"
                       % (key, reference, table, target, table, join, " AND ".join("r.`%s` <=> t.`%s`" % (c, c) for c in cols)))
        columns_changed = [c for c in cols if differ and int(query("SELECT COUNT(*) FROM `%s`.`%s` r JOIN `%s`.`%s` t ON %s WHERE NOT (r.`%s` <=> t.`%s`)"
                                                    % (reference, table, target, table, join, c, c))[0][0])]
        print("%s seeds=%d missing=%d differ=%d%s%s%s" % (table, seeds, len(missing), len(differ),
              ("  differ_columns=" + ",".join(columns_changed)) if columns_changed else "",
              ("  missing_keys=" + ",".join(m[0] for m in missing)) if missing else "",
              ("  differ_keys=" + ",".join(d[0] for d in differ)) if differ else ""))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
