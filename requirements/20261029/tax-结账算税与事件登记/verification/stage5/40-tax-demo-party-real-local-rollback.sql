-- 回滚 40-tax-demo-party-real-local.sql（mor_tax_v2e2e）：种子主体改回 MOCK 值 880000000702；acquiring 切回 MOCK 时一并执行。
START TRANSACTION;
UPDATE t_tax_market SET subject_party_id = 880000000702, update_time = UTC_TIMESTAMP(6) WHERE subject_party_id = 900000000001380102;
UPDATE t_tax_registration SET subject_party_id = 880000000702, update_time = UTC_TIMESTAMP(6) WHERE subject_party_id = 900000000001380102;
UPDATE t_tax_document_sequence SET issuer_party_id = 880000000702, update_time = UTC_TIMESTAMP(6) WHERE issuer_party_id = 900000000001380102;
-- 预期各影响 3 行。
COMMIT;
SELECT 'market' t, subject_party_id, COUNT(*) FROM t_tax_market GROUP BY 2
UNION ALL SELECT 'registration', subject_party_id, COUNT(*) FROM t_tax_registration GROUP BY 2
UNION ALL SELECT 'sequence', issuer_party_id, COUNT(*) FROM t_tax_document_sequence GROUP BY 2;
