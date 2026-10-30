-- 本机专用（阶段 5，mor_tax_v2e2e）：acquiring 切换 REAL 后，把 tax 演示种子的 AE001 纳税主体由 MOCK 值 880000000702 改为
-- mor_client_master_v2e2e 中 AE001 的真实 Party 900000000001380102。只改种子 / 配置表（准入、注册、税票序列）；
-- 已有计算、正式交易与税票（S1—S3 证据，MOCK 期间产生）保留原值。备份：backup/mor_tax_v2e2e.full.before-real-party.20261029.sql。
-- 回滚：40-tax-demo-party-real-local-rollback.sql。
START TRANSACTION;
UPDATE t_tax_market SET subject_party_id = 900000000001380102, update_time = UTC_TIMESTAMP(6) WHERE subject_party_id = 880000000702;
UPDATE t_tax_registration SET subject_party_id = 900000000001380102, update_time = UTC_TIMESTAMP(6) WHERE subject_party_id = 880000000702;
UPDATE t_tax_document_sequence SET issuer_party_id = 900000000001380102, update_time = UTC_TIMESTAMP(6) WHERE issuer_party_id = 880000000702;
-- 预期各影响 3 行。
COMMIT;
SELECT 'market' t, subject_party_id, COUNT(*) FROM t_tax_market GROUP BY 2
UNION ALL SELECT 'registration', subject_party_id, COUNT(*) FROM t_tax_registration GROUP BY 2
UNION ALL SELECT 'sequence', issuer_party_id, COUNT(*) FROM t_tax_document_sequence GROUP BY 2;
