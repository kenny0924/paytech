"""File-only migration safety checks; no database connections or artifact writes."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import unittest

HERE = Path(__file__).resolve().parent
DDL = HERE.parent / 'db/implementation'
spec = importlib.util.spec_from_file_location('migration_generator', DDL / 'generate_migrations.py')
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class CompensationMigrationTest(unittest.TestCase):
    def test_exact_single_table_and_column_transition(self):
        """Reject unrelated DDL and preserve the original primary key."""
        sql = (DDL / 'cct-compensation-empty.sql').read_text()
        self.assertEqual(re.findall(r'ALTER TABLE `(\w+)`', sql), ['t_compensation_event'])
        self.assertNotRegex(sql, r'(?im)^\s*(?:CREATE|DELETE|UPDATE|INSERT|TRUNCATE|DROP TABLE|USE)\b')
        self.assertNotRegex(sql, r'(?i)FOREIGN KEY|CHECK\s*\(|DROP PRIMARY KEY')
        self.assertEqual(set(re.findall(r'DROP COLUMN `(\w+)`', sql)),
                         {'execution_type', 'source_type', 'source_id', 'source_version', 'schema_version', 'payload_hash'})
        self.assertEqual(re.findall(r'ADD COLUMN `(\w+)`', sql), ['sub_event_type'])
        source = json.loads((DDL / 'cct-compensation-source.json').read_text())
        columns = [field['defKey'] for field in source['fields']]
        for clause in sql.splitlines():
            drop = re.fullmatch(r'  DROP COLUMN `(\w+)`,', clause)
            if drop:
                columns.remove(drop[1])
            change = re.fullmatch(r'  (MODIFY|ADD) COLUMN `(\w+)` .+ (FIRST|AFTER `(\w+)`),', clause)
            if not change:
                continue
            action, name, _, predecessor = change.groups()
            if action == 'MODIFY':
                columns.remove(name)
            else:
                self.assertNotIn(name, columns)
            columns.insert(columns.index(predecessor) + 1 if predecessor else 0, name)
        self.assertEqual(columns, ['id', 'event_key', 'event_type', 'sub_event_type', 'payload_json',
                         'execute_status', 'execute_count', 'next_execute_at', 'lock_owner', 'lock_until',
                         'last_error_code', 'last_error_summary', 'lock_version', 'create_time', 'update_time'])

    def test_manifest_pins_candidate_and_applied_sql(self):
        manifest = json.loads((DDL / 'cct-compensation-manifest.json').read_text())
        self.assertEqual(hashlib.sha256((DDL / 'cct-compensation-empty.sql').read_bytes()).hexdigest(), manifest['ddlSha256'])
        self.assertEqual(hashlib.sha256((DDL / 'client-contract-empty-closure.sql').read_bytes()).hexdigest(),
                         manifest['preservedExecutedTenTableSqlSha256'])

    def test_regeneration_preserves_applied_target_fingerprint(self):
        before = {path.name: path.read_bytes() for path in DDL.iterdir() if path.is_file()}
        outputs = generator.generate(write=False)
        self.assertEqual(outputs['migration-manifest.json'], before['migration-manifest.json'].decode())
        self.assertEqual(outputs['client-contract-empty-closure.sql'], before['client-contract-empty-closure.sql'].decode())
        self.assertEqual(before, {path.name: path.read_bytes() for path in DDL.iterdir() if path.is_file()})

    def test_changed_applied_migration_is_rejected_before_writing(self):
        approved = (DDL / 'client-contract-empty-closure.sql').read_bytes()
        with self.assertRaisesRegex(AssertionError, 'new migration'):
            generator.freeze_executed_cct(approved.decode() + '\nALTER TABLE t_party ADD bad INT;\n')
        self.assertEqual((DDL / 'client-contract-empty-closure.sql').read_bytes(), approved)


if __name__ == '__main__':
    unittest.main()
