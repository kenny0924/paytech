#!/usr/bin/env python3
"""Offline contract tests for the V2 service database actions and the append-only V2 test keys."""
import contextlib
import importlib.util
import io
from pathlib import Path
import stat
import tempfile
import unittest
from unittest import mock

from test_risk_database_initialization import load_initializer

WORKSPACE = Path(__file__).resolve().parents[2]
PREPARE = WORKSPACE / "test-environment/scripts/prepare.py"
COMPOSE_BASE = WORKSPACE / "test-environment/compose.yaml"
COMPOSE_APPLICATIONS = WORKSPACE / "test-environment/compose.applications.yaml"
VALID_PASSWORD = "a1b2c3d4e5f6a7b8c9d0e1f2"


def load_prepare():
    spec = importlib.util.spec_from_file_location("prepare_under_test", PREPARE)
    module = importlib.util.module_from_spec(spec)
    # prepare.py sets a restrictive umask at import; keep the test process umask unchanged.
    with mock.patch("os.umask"):
        spec.loader.exec_module(module)
    return module


class ServiceDatabaseCatalogTest(unittest.TestCase):
    def setUp(self):
        self.module = load_initializer()

    def test_catalog_matches_design_ten_databases_and_compose_wiring(self):
        names = [service.database for service in self.module.SERVICE_DATABASES]
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual(14, len(names))
        base = COMPOSE_BASE.read_text(encoding="utf-8")
        applications = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        for service in self.module.SERVICE_DATABASES:
            with self.subTest(service=service.service):
                self.assertRegex(service.database, r"^mor_[a-z_]+$")
                self.assertEqual(service.service.replace("-", "_") + "_app", service.account)
                self.assertIn(service.password_variable + ": ${" + service.password_variable + ":?", base)
                self.assertIn("/" + service.database + "?", applications)
                self.assertIn("${" + service.password_variable + ":?prepare test secrets}", applications)
        self.assertEqual(
            {"risk-decision", "compliance"},
            {service.service for service in self.module.SERVICE_DATABASES if not service.applies_sql},
        )

    def test_every_owner_schema_is_create_table_only_and_initial_data_only_inserts(self):
        for service in self.module.SERVICE_DATABASES:
            if not service.applies_sql:
                continue
            with self.subTest(service=service.service):
                directory = self.module._service_sql_directory(service)
                tables = self.module.load_service_schema_tables(
                    service, (directory / (service.service + "-schema.sql")).read_text(encoding="utf-8"))
                self.assertTrue(tables)
                initial = directory / (service.service + "-initial-data.sql")
                if initial.exists():
                    self.module.check_service_initial_data(service, initial.read_text(encoding="utf-8"))

    def test_schema_and_initial_data_parsers_fail_closed(self):
        service = self.module.SERVICE_DATABASES[0]
        valid = "SET NAMES utf8mb4;\nCREATE TABLE `t_a` (`id` BIGINT NOT NULL);\nCREATE TABLE t_b (`id` BIGINT);\n"
        self.assertEqual(frozenset({"t_a", "t_b"}), self.module.load_service_schema_tables(service, valid))
        for broken in (valid + "DROP TABLE t_a;\n", valid + "CREATE TABLE `t_a` (`id` BIGINT);\n",
                       "CREATE VIEW v AS SELECT 1;\n", "SET NAMES utf8mb4;\n"):
            with self.subTest(schema=broken), self.assertRaises(SystemExit):
                self.module.load_service_schema_tables(service, broken)
        self.module.check_service_initial_data(
            service, "SET NAMES utf8mb4;\nSTART TRANSACTION;\nSET @t = '2026-10-18';\nINSERT INTO `t_a` (`id`) VALUES (1);\nCOMMIT;\n")
        for broken in ("UPDATE t_a SET id = 2;\n", "DELETE FROM t_a;\n", "CREATE TABLE t_c (id BIGINT);\n"):
            with self.subTest(initial=broken), self.assertRaises(SystemExit):
                self.module.check_service_initial_data(service, broken)


class CreateServiceDatabasesTest(unittest.TestCase):
    def setUp(self):
        self.module = load_initializer()
        self.values = {service.password_variable: VALID_PASSWORD for service in self.module.SERVICE_DATABASES}

    def test_creates_every_database_and_least_privilege_account_in_one_root_batch(self):
        batches = []
        self.module.environment = lambda: self.values
        self.module.mysql = lambda sql, database="mor_platform": batches.append((sql, database)) or ""
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.module.create_service_databases()
        self.assertEqual(1, len(batches))
        sql, database = batches[0]
        self.assertEqual("mysql", database)
        for service in self.module.SERVICE_DATABASES:
            with self.subTest(service=service.service):
                self.assertIn("CREATE DATABASE IF NOT EXISTS `" + service.database + "`", sql)
                self.assertIn("CREATE USER IF NOT EXISTS '" + service.account + "'@'%'", sql)
                self.assertIn("GRANT SELECT, INSERT, UPDATE, DELETE ON `" + service.database + "`.* TO '"
                              + service.account + "'@'%';", sql)
        self.assertNotIn("ALL PRIVILEGES", sql)
        self.assertNotIn("DROP", sql)
        self.assertNotIn(VALID_PASSWORD, output.getvalue())

    def test_missing_or_invalid_keys_fail_before_any_sql(self):
        for name, change in (("missing", lambda values: values.pop("MOR_CARD_DB_PASSWORD")),
                             ("short", lambda values: values.update(MOR_CARD_DB_PASSWORD="short")),
                             ("quote", lambda values: values.update(MOR_CARD_DB_PASSWORD="abcdefghijklmnop'q"))):
            with self.subTest(case=name):
                values = dict(self.values)
                change(values)
                self.module.environment = lambda values=values: values
                self.module.mysql = mock.Mock(side_effect=AssertionError("SQL ran before validation"))
                with self.assertRaises(SystemExit) as raised:
                    self.module.create_service_databases()
                self.assertNotIn(VALID_PASSWORD, str(raised.exception))
                self.module.mysql.assert_not_called()


class ApplyServiceSqlTest(unittest.TestCase):
    def setUp(self):
        self.module = load_initializer()
        self.services = [service for service in self.module.SERVICE_DATABASES if service.applies_sql]
        self.tables = {}
        for service in self.services:
            directory = self.module._service_sql_directory(service)
            self.tables[service.database] = self.module.load_service_schema_tables(
                service, (directory / (service.service + "-schema.sql")).read_text(encoding="utf-8"))

    def fake_mysql(self, state, batches):
        def run(sql, database="mor_platform"):
            if "information_schema.schemata" in sql:
                return "1"
            if "information_schema.tables" in sql:
                name = sql.split("table_schema='", 1)[1].split("'", 1)[0]
                if state[name] == "EMPTY":
                    return ""
                tables = sorted(self.tables[name])
                if state[name] == "PARTIAL":
                    tables = tables[:-1]
                return "\n".join(table + "\tBASE TABLE" for table in tables)
            batches.append((database, sql))
            state[database] = "PREPARED"
            return ""
        return run

    def test_empty_databases_receive_schema_then_initial_data_and_prepared_ones_are_left_alone(self):
        state = {service.database: "EMPTY" for service in self.services}
        state["mor_payment"] = "PREPARED"
        batches = []
        self.module.mysql = self.fake_mysql(state, batches)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.module.apply_service_sql()
        written = [database for database, _ in batches]
        self.assertNotIn("mor_payment", written)
        self.assertNotIn("mor_risk_decision", written)
        self.assertNotIn("mor_compliance", written)
        for service in self.services:
            if service.database == "mor_payment":
                continue
            with self.subTest(service=service.service):
                own = [sql for database, sql in batches if database == service.database]
                self.assertIn("CREATE TABLE", own[0])
                has_initial = (self.module._service_sql_directory(service) / (service.service + "-initial-data.sql")).exists()
                self.assertEqual(2 if has_initial else 1, len(own))
        self.assertIn("mor_payment: PREPARED", output.getvalue())

    def test_partial_or_missing_database_stops_before_any_write(self):
        for case in ("PARTIAL", "MISSING"):
            with self.subTest(case=case):
                state = {service.database: "EMPTY" for service in self.services}
                batches = []
                fake = self.fake_mysql(state, batches)
                if case == "PARTIAL":
                    state["mor_tax"] = "PARTIAL"
                    self.module.mysql = fake
                else:
                    self.module.mysql = lambda sql, database="mor_platform": (
                        "0" if "information_schema.schemata" in sql and "mor_tax" in sql else fake(sql, database))
                with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
                    self.module.apply_service_sql()
                self.assertEqual([], batches)


class PrepareAppendOnlyTest(unittest.TestCase):
    def test_existing_material_only_gains_missing_v2_keys(self):
        prepare = load_prepare()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = root / ".env.test"
            env.write_text("# header\nMOR_CARD_DB_PASSWORD=keep-this-existing-value-1234\nOTHER=1\n", encoding="utf-8")
            env.chmod(0o600)
            prepare.ENV = env
            added = prepare.append_missing_v2_values()
            lines = env.read_text(encoding="utf-8").splitlines()
            self.assertEqual("# header", lines[0])
            self.assertIn("MOR_CARD_DB_PASSWORD=keep-this-existing-value-1234", lines)
            self.assertNotIn("MOR_CARD_DB_PASSWORD", added)
            expected = sorted(set(prepare.V2_DATABASE_PASSWORDS) - {"MOR_CARD_DB_PASSWORD"} | {"MOR_CARD_FINGERPRINT_KEY"})
            self.assertEqual(expected, added)
            self.assertEqual(0o600, stat.S_IMODE(env.stat().st_mode))
            keys = [line.split("=", 1)[0] for line in lines if line and not line.startswith("#")]
            self.assertEqual(len(keys), len(set(keys)))
            self.assertEqual([], prepare.append_missing_v2_values())


if __name__ == "__main__":
    unittest.main()
