"""Static safety contract for the manually reviewed Gateway break-glass DML."""

import re
from pathlib import Path
import unittest


TEST_ENVIRONMENT = Path(__file__).resolve().parents[1]
BREAK_GLASS_SQL = (
    TEST_ENVIRONMENT / "sql/2026-09-05-operations-gateway-control-plane-break-glass.sql"
)
README = TEST_ENVIRONMENT / "README.md"

TARGET_CODE = "operations-console-bff"
ROUTE_CODES = (
    "operations-gateway-target-list",
    "operations-gateway-target-detail",
    "operations-gateway-target-status",
    "operations-gateway-route-list",
    "operations-gateway-route-detail",
    "operations-gateway-route-status",
)


class GatewayControlPlaneBreakGlassContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = BREAK_GLASS_SQL.read_text(encoding="utf-8")
        cls.readme = README.read_text(encoding="utf-8")

    def test_recovery_is_guarded_by_one_explicit_transaction(self):
        self.assertIn("USE mor_platform;", self.sql)
        self.assertIn("SQL SECURITY INVOKER", self.sql)
        self.assertIn("@break_glass_ticket_ref", self.sql)
        self.assertEqual(1, len(re.findall(r"\bSTART TRANSACTION\b", self.sql)))
        self.assertEqual(1, len(re.findall(r"\bCOMMIT\b", self.sql)))
        self.assertIn("DECLARE EXIT HANDLER FOR SQLEXCEPTION", self.sql)
        self.assertIn("ROLLBACK;", self.sql)
        self.assertIn("RESIGNAL;", self.sql)
        self.assertIn("SIGNAL SQLSTATE '45000'", self.sql)
        isolation = self.sql.index("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;")
        transaction = self.sql.index("START TRANSACTION;", isolation)
        self.assertLess(isolation, transaction)

    def test_recovery_targets_only_the_approved_management_chain(self):
        self.assertIn(TARGET_CODE, self.sql)
        for route_code in ROUTE_CODES:
            self.assertIn(f"'{route_code}'", self.sql, route_code)
        self.assertIn("v_expected_route_count <> 6", self.sql)
        self.assertNotRegex(self.sql, r"(?im)^\s*(INSERT|DELETE)\s+(?:INTO|FROM)\s+t_gateway_")

    def test_recovery_preserves_the_last_application_actor(self):
        self.assertIn("PRESERVE_APPLICATION_UPDATED_BY", self.sql)
        self.assertIn("version BETWEEN 1 AND 2147483646", self.sql)
        self.assertGreaterEqual(self.sql.count("status = 'DISABLED'"), 2)
        version_increments = re.findall(
            r"(?:\w+\.)?version\s*=\s*(?:\w+\.)?version\s*\+\s*1", self.sql
        )
        self.assertGreaterEqual(len(version_increments), 2)
        self.assertNotRegex(self.sql, r"(?i)SET\s+[^;]*updated_by\s*=")
        self.assertNotIn("INSERT INTO t_audit_event", self.sql)

    def test_child_configuration_is_locked_before_validation(self):
        for table in (
            "t_gateway_route_header",
            "t_gateway_route_query_param",
            "t_gateway_guard_rule",
        ):
            lock = re.search(
                rf"SELECT\s+[^;]+\s+FROM\s+{table}\s+"
                rf"WHERE\s+route_id\s+BETWEEN\s+900000000000001018\s+AND\s+900000000000001023"
                rf"[^;]*FOR UPDATE;",
                self.sql,
                re.IGNORECASE | re.DOTALL,
            )
            self.assertIsNotNone(lock, table)
            validation = re.search(
                rf"SELECT\s+COUNT\(\*\)[^;]+FROM\s+{table}",
                self.sql,
                re.IGNORECASE | re.DOTALL,
            )
            self.assertIsNotNone(validation, table)
            self.assertLess(lock.start(), validation.start(), table)

    def test_recovery_rejects_compiler_equivalent_overlap_across_full_active_catalog(self):
        overlap_start = self.sql.index("WITH effective_routes AS")
        overlap_end = self.sql.index("IF v_active_route_overlap_count > 0", overlap_start)
        overlap = self.sql[overlap_start:overlap_end]

        self.assertIn("FROM t_gateway_route route", overlap)
        self.assertIn("JOIN t_gateway_target target", overlap)
        self.assertIn("target.status = 'ACTIVE'", overlap)
        self.assertIn("WHERE route.status = 'ACTIVE'", overlap)
        self.assertNotIn("900000000000001018", overlap)
        self.assertNotIn("900000000000001023", overlap)
        for shape_constraint in (
            "first_route.http_method = second_route.http_method",
            "first_route.segment_count = second_route.segment_count",
            "first_route.variable_count = second_route.variable_count",
            "first_segment.segment_position",
            "first_segment.is_variable = 0",
            "second_segment.is_variable = 0",
            "CAST(first_segment.segment_value AS BINARY)",
            "CAST(second_segment.segment_value AS BINARY)",
        ):
            self.assertIn(shape_constraint, overlap)
        self.assertIn("COUNT(DISTINCT CASE", overlap)
        self.assertIn("REGEXP_LIKE(", overlap)
        rejection = self.sql.index("IF v_active_route_overlap_count > 0", overlap_end)
        commit = self.sql.index("COMMIT;", rejection)
        self.assertLess(rejection, commit)
        self.assertIn(
            "Break-glass recovery would create an overlapping ACTIVE Gateway Route",
            self.sql[rejection:commit],
        )

    def test_runbook_requires_approval_database_audit_and_snapshot_verification(self):
        for required_text in (
            "Break-glass 恢复",
            "审批",
            "数据库审计",
            "Snapshot",
            "不得自动执行",
        ):
            self.assertIn(required_text, self.readme)
        self.assertNotIn("--abort-source-on-error", self.sql)
        self.assertNotIn("--abort-source-on-error", self.readme)
        for fail_stop_contract in ("mysql --batch", "--init-command", "< test-environment/sql/"):
            self.assertIn(fail_stop_contract, self.readme)
        self.assertIn("batch mode", self.sql)


if __name__ == "__main__":
    unittest.main()
