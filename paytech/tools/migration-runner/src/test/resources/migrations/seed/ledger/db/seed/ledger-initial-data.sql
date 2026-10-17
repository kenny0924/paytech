-- Fixture initial data: DML only, applied once inside one transaction.
INSERT INTO `t_mr_test_entry` (`entry_id`, `label`, `note`) VALUES (100, 'seed', 'initial; data');
INSERT INTO `t_mr_test_audit` (`audit_id`, `message`) VALUES (100, 'seeded');
