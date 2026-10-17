#!/usr/bin/env python3
"""Offline contract tests for the opt-in Risk database initializer."""
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import unittest
from unittest import mock
import urllib.request
import stat

CLI_ACTIONS = {
    '--apply-platform-sql': 'apply_platform_sql', '--nacos': 'initialize_nacos',
    '--apply-risk-sql': 'apply_risk_sql', '--verify-risk-catalog': 'verify_risk_catalog',
    '--verify-risk-operations-stage': 'verify_risk_operations_stage',
    '--apply-risk-operations-sql': 'apply_risk_operations_sql',
    '--enable-risk-operations-resources': 'enable_risk_operations_resources',
    '--disable-risk-operations-resources': 'disable_risk_operations_resources',
}


WORKSPACE = Path(__file__).resolve().parents[2]
SCRIPT = WORKSPACE / "test-environment/scripts/initialize.py"
POINT_COLUMNS = (
    "decision_point_id", "point_code", "point_name", "business_domain", "status", "create_time", "update_time")
ACTION_COLUMNS = (
    "action_version_id", "action_code", "action_name", "action_category", "version_no", "handler_code",
    "implementation_version", "implementation_hash", "terminal_flag", "decision_code", "merge_policy",
    "shadow_supported_flag", "status", "create_time", "update_time")
POINT_ROWS = (
    ("210001", "CHECKOUT_PREPARE", "Checkout Prepare", "CHECKOUT", "ACTIVE",
     "2026-09-05 00:00:00.000000", "2026-09-05 00:00:00.000000"),)
ACTION_ROWS = (
    ("240001", "ALLOW_CHECKOUT", "Allow Checkout", "DECISION", "1", "DECISION_ALLOW_V1", "1.0.0",
     "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1", "1", "ALLOW",
     "MOST_RESTRICTIVE", "1", "ACTIVE", "2026-09-05 00:00:00.000000", "2026-09-05 00:00:00.000000"),
    ("240002", "REVIEW_CHECKOUT", "Review Checkout", "DECISION", "1", "DECISION_REVIEW_V1", "1.0.0",
     "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716", "1", "REVIEW",
     "MOST_RESTRICTIVE", "1", "ACTIVE", "2026-09-05 00:00:00.000000", "2026-09-05 00:00:00.000000"),
    ("240003", "DENY_CHECKOUT", "Deny Checkout", "DECISION", "1", "DECISION_DENY_V1", "1.0.0",
     "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7", "1", "DENY",
     "MOST_RESTRICTIVE", "1", "ACTIVE", "2026-09-05 00:00:00.000000", "2026-09-05 00:00:00.000000"))

ACTION_ROWS += (
    ("240004", "APPLY_COOLDOWN", "冷却", "RESTRICTION", "1", "NULL", "NULL", "NULL", "0", "NULL", "MAX", "0", "CONFIGURATION_ONLY", "2026-09-11 00:00:00.000000", "2026-09-11 00:00:00.000000"),
    ("240005", "ADD_RISK_TAG", "添加主体标签", "TAG", "1", "NULL", "NULL", "NULL", "0", "NULL", "UNION", "0", "CONFIGURATION_ONLY", "2026-09-11 00:00:00.000000", "2026-09-11 00:00:00.000000"))
PARAMETER_COLUMNS = ("parameter_definition_id", "action_version_id", "parameter_code", "value_type", "required_flag", "allowed_source_type", "target_parameter_flag", "create_time")
PARAMETER_ROWS = (
    ("2400041", "240004", "SUBJECT_ID", "STRING", "1", "CONTEXT", "1", "2026-09-11 00:00:00.000000"),
    ("2400042", "240004", "DURATION", "DURATION", "1", "LITERAL", "0", "2026-09-11 00:00:00.000000"),
    ("2400051", "240005", "SUBJECT_ID", "STRING", "1", "CONTEXT", "1", "2026-09-11 00:00:00.000000"),
    ("2400052", "240005", "TAG", "STRING", "1", "LITERAL", "0", "2026-09-11 00:00:00.000000"))


def load_initializer():
    """Import with sentinels: importing this operator tool must do nothing."""
    spec = importlib.util.spec_from_file_location("risk_initializer_under_test", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    with mock.patch.object(__import__("os"), "umask", side_effect=AssertionError("import changed umask")), \
         mock.patch.object(subprocess, "run", side_effect=AssertionError("import ran a subprocess")), \
         mock.patch.object(urllib.request, "urlopen", side_effect=AssertionError("import opened a network connection")):
        spec.loader.exec_module(module)
    return module


class RiskDatabaseInitializationTest(unittest.TestCase):
    def setUp(self):
        self.module = load_initializer()

    def test_import_and_invalid_cli_have_no_side_effects(self):
        actions = []
        self.module.apply_platform_sql = lambda: actions.append("platform")
        self.module.initialize_nacos = lambda: actions.append("nacos")
        self.module.apply_risk_sql = lambda: actions.append("risk")
        self.module.verify_risk_catalog = lambda: actions.append("verify")
        for name in CLI_ACTIONS.values():
            setattr(self.module, name, lambda name=name: actions.append(name))
        forbidden = tuple(CLI_ACTIONS)
        with contextlib.redirect_stderr(io.StringIO()), \
             mock.patch.object(self.module, "load_risk_catalog", side_effect=AssertionError("CLI read Risk SQL")) as catalog, \
             mock.patch.object(self.module.Path, "open", side_effect=AssertionError("CLI read a file")) as file_open, \
             mock.patch.object(self.module.Path, "write_text", side_effect=AssertionError("CLI wrote a file")) as file_write, \
             mock.patch.object(self.module, "mysql", side_effect=AssertionError("CLI ran MySQL")) as mysql, \
             mock.patch.object(self.module, "nacos_request", side_effect=AssertionError("CLI called Nacos")) as nacos, \
             mock.patch.object(self.module, "_restricted_file_creation", side_effect=AssertionError("CLI acquired write scope")) as write_scope, \
             mock.patch.object(self.module.os, 'open', side_effect=AssertionError('CLI opened a lock')):
            with self.assertRaises(SystemExit):
                self.module.main([])
            for first in range(len(forbidden)):
                for second in range(first + 1, len(forbidden)):
                    with self.subTest(first=forbidden[first], second=forbidden[second]):
                        with self.assertRaises(SystemExit):
                            self.module.main([forbidden[first], forbidden[second]])
        catalog.assert_not_called()
        file_open.assert_not_called()
        file_write.assert_not_called()
        mysql.assert_not_called()
        nacos.assert_not_called()
        write_scope.assert_not_called()
        self.assertEqual([], actions)

    def test_each_cli_action_dispatches_exactly_one_action(self):
        for flag, expected in CLI_ACTIONS.items():
            argv = [flag]
            with self.subTest(argv=argv):
                actions = []
                for name in CLI_ACTIONS.values():
                    setattr(self.module, name, lambda name=name: actions.append(name))
                with contextlib.redirect_stdout(io.StringIO()):
                    self.module.main(argv)
                self.assertEqual([expected], actions)

    def test_owner_catalog_has_exact_complete_stored_tuple_contract(self):
        catalog = self.module.load_risk_catalog()
        self.assertEqual(71, len(catalog["tables"]))
        self.assertEqual(POINT_COLUMNS, catalog["point_columns"])
        self.assertEqual(ACTION_COLUMNS, catalog["action_columns"])
        self.assertEqual(POINT_ROWS, catalog["points"])
        self.assertEqual(ACTION_ROWS, catalog["actions"])
        self.assertEqual(PARAMETER_COLUMNS, catalog["parameter_columns"])
        self.assertEqual(PARAMETER_ROWS, catalog["parameters"])
        initial_data = (WORKSPACE / "paytech/risk-decision/docs/database/sql/risk-decision-initial-data.sql").read_text(encoding="utf-8")
        schema = (WORKSPACE / "paytech/risk-decision/docs/database/sql/risk-decision-schema.sql").read_text(encoding="utf-8")
        for column in POINT_COLUMNS + ACTION_COLUMNS + PARAMETER_COLUMNS:
            with self.subTest(omitted_parsed_column=column):
                with self.assertRaises(SystemExit):
                    self.module.load_risk_catalog(schema, initial_data.replace("`" + column + "`", "", 1))
        with self.assertRaises(SystemExit):
            self.module.load_risk_catalog(
                (WORKSPACE / "paytech/risk-decision/docs/database/sql/risk-decision-schema.sql").read_text(encoding="utf-8") + "\nSELECT 1;\n",
                (WORKSPACE / "paytech/risk-decision/docs/database/sql/risk-decision-initial-data.sql").read_text(encoding="utf-8"))

    def test_verifier_requires_empty_or_exact_prepared_catalog_in_mor_risk_decision(self):
        queries = []

        def empty_mysql(sql, database="mor_platform"):
            queries.append((sql, database))
            return ""

        self.module.mysql = empty_mysql
        self.assertEqual("EMPTY", self.module.verify_risk_catalog())
        self.assertTrue(queries)
        self.assertTrue(all(database == "mor_risk_decision" for _, database in queries))

        rows = "\n".join(name + "\tBASE TABLE" for name in sorted(self.module.load_risk_catalog()["tables"]))
        point_rows = "\n".join("\t".join(row) for row in POINT_ROWS)
        action_rows = "\n".join("\t".join(row) for row in ACTION_ROWS)

        def prepared_mysql(sql, database="mor_platform"):
            self.assertEqual("mor_risk_decision", database)
            if "information_schema.tables" in sql:
                return rows
            if "t_decision_point" in sql:
                self.assertIn(", ".join("`" + column + "`" for column in POINT_COLUMNS), sql)
                return point_rows
            if "t_action_version" in sql:
                self.assertIn(", ".join("`" + column + "`" for column in ACTION_COLUMNS), sql)
                return action_rows
            if "t_action_parameter_definition" in sql:
                return "\n".join("\t".join(row) for row in PARAMETER_ROWS)
            self.fail("unexpected query")

        self.module.mysql = prepared_mysql
        self.assertEqual("PREPARED", self.module.verify_risk_catalog())
        for changed in (PARAMETER_ROWS[:-1], PARAMETER_ROWS + PARAMETER_ROWS[:1],
                        (PARAMETER_ROWS[0][:3] + ("NUMBER",) + PARAMETER_ROWS[0][4:],) + PARAMETER_ROWS[1:]):
            def malformed_parameter_mysql(sql, database="mor_platform", changed=changed):
                if "t_action_parameter_definition" in sql:
                    return "\n".join("\t".join(row) for row in changed)
                return prepared_mysql(sql, database)
            self.module.mysql = malformed_parameter_mysql
            with self.assertRaises(SystemExit):
                self.module.verify_risk_catalog()
        self.module.mysql = prepared_mysql


        def drifted_mysql(sql, database="mor_platform"):
            if "information_schema.tables" in sql:
                return rows + "\nnot_a_table\tVIEW"
            return ""

        self.module.mysql = drifted_mysql
        with self.assertRaises(SystemExit):
            self.module.verify_risk_catalog()

        for row_name, row, columns in (("point", POINT_ROWS[0], POINT_COLUMNS),
                                       ("action", ACTION_ROWS[0], ACTION_COLUMNS)):
            for missing_index, column in enumerate(columns):
                with self.subTest(row_name=row_name, missing_column=column):
                    def missing_column_mysql(sql, database="mor_platform", row=row, missing_index=missing_index):
                        if "information_schema.tables" in sql:
                            return rows
                        if "t_decision_point" in sql:
                            return "\t".join(POINT_ROWS[0] if row_name == "action" else row[:missing_index] + row[missing_index + 1:])
                        if "t_action_version" in sql:
                            return "\n".join("\t".join(action) for action in
                                             (ACTION_ROWS if row_name == "point" else
                                              (row[:missing_index] + row[missing_index + 1:],) + ACTION_ROWS[1:]))
                        self.fail("unexpected query")

                    self.module.mysql = missing_column_mysql
                    with self.assertRaises(SystemExit):
                        self.module.verify_risk_catalog()

    def test_safe_risk_sql_read_rejects_missing_directory_and_symlink(self):
        missing = mock.Mock()
        missing.lstat.side_effect = FileNotFoundError
        with self.assertRaises(SystemExit):
            self.module._stable_sql_text(missing)
        for name, mode in (("directory", stat.S_IFDIR | 0o700), ("symlink", stat.S_IFLNK | 0o777)):
            with self.subTest(name=name):
                path = mock.MagicMock()
                path.lstat.return_value = mock.Mock(st_mode=mode)
                with self.assertRaises(SystemExit):
                    self.module._stable_sql_text(path)
                path.open.assert_not_called()

    def test_safe_risk_sql_read_rejects_each_inflight_identity_change(self):
        def metadata(**changes):
            values = {"st_mode": stat.S_IFREG | 0o600, "st_dev": 1, "st_ino": 2,
                      "st_size": 3, "st_mtime_ns": 4}
            values.update(changes)
            return mock.Mock(**values)

        for field, changed_value in (("st_dev", 11), ("st_ino", 12), ("st_size", 13), ("st_mtime_ns", 14)):
            with self.subTest(changed_field=field):
                path = mock.MagicMock()
                path.lstat.return_value = metadata()
                source = mock.MagicMock()
                source.fileno.return_value = 9
                source.read.return_value = "owner SQL"
                path.open.return_value.__enter__.return_value = source
                with mock.patch.object(self.module.os, "fstat", side_effect=(metadata(), metadata(**{field: changed_value}))):
                    with self.assertRaises(SystemExit):
                        self.module._stable_sql_text(path)

    def test_apply_runs_schema_then_dml_only_from_empty_and_rechecks_prepared(self):
        batches = []
        states = iter(("EMPTY", "PREPARED"))
        compliance_states = iter(("EMPTY", "PREPARED"))
        self.module.verify_risk_catalog = lambda: next(states)
        self.module.verify_compliance_schema = lambda: next(compliance_states)
        self.module.mysql = lambda sql, database="mor_platform": batches.append((sql, database)) or ""
        with contextlib.redirect_stdout(io.StringIO()):
            self.module.apply_risk_sql()
        # 第 06 步：risk-decision Schema 与初始化目录进 mor_risk_decision，Compliance Schema 进 mor_compliance。
        self.assertEqual(["mor_risk_decision", "mor_risk_decision", "mor_compliance"], [database for _, database in batches])
        self.assertIn("CREATE TABLE", batches[0][0])
        self.assertIn("INSERT INTO", batches[1][0])
        self.assertIn("CREATE TABLE `t_due_diligence_case`", batches[2][0])

        batches = []
        self.module.verify_risk_catalog = lambda: "EMPTY"
        self.module.verify_compliance_schema = lambda: "EMPTY"
        self.module.mysql = lambda sql, database="mor_platform": (
            batches.append((sql, database)), (_ for _ in ()).throw(SystemExit("schema failed")))[1]
        with contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit):
                self.module.apply_risk_sql()
        self.assertEqual(1, len(batches))

    def test_automated_callers_and_readme_cannot_combine_initialization_actions(self):
        prohibited = ("--apply-risk-sql", "apply_risk_sql", '--apply-risk-operations-sql',
                      '--enable-risk-operations-resources', '--disable-risk-operations-resources')
        callers = set(WORKSPACE.rglob("compose*.yaml"))
        callers.update(WORKSPACE.rglob("build.sh"))
        callers.update(WORKSPACE.rglob("deploy.sh"))
        callers.update(WORKSPACE.rglob("*Application.java"))
        for path in callers:
            self.assertFalse(any(value in path.read_text(encoding="utf-8") for value in prohibited), path)
        readme = (WORKSPACE / "test-environment/README.md").read_text(encoding="utf-8")
        for line in readme.splitlines():
            known = sum(flag in line for flag in
                        ("--apply-platform-sql", "--nacos", "--apply-risk-sql", "--verify-risk-catalog"))
            self.assertLessEqual(known, 1, line)


if __name__ == "__main__":
    unittest.main()
