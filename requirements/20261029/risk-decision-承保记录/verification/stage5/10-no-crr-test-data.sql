-- REQ-03 阶段 5 本机测试数据（仅 mor_compliance_v2e2e，仅该已批准个案）：把 Client 240674482170114049 的入驻案件 CRR 置空，
-- 用于验证"尽调未评 CRR → 按 MEDIUM 处理、crrMissing"。真实流程中批准前必须评 CRR（RISK_RATING_MISSING 门禁），故只能用测试数据构造。
-- 回滚：10-no-crr-test-data-rollback.sql
UPDATE mor_compliance_v2e2e.t_due_diligence_case SET risk_rating = NULL
 WHERE case_id = 240674554333122560 AND status = 'APPROVED' AND risk_rating = 'LOW';
