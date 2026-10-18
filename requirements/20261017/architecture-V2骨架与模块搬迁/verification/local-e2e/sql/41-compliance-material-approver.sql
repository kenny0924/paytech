-- mor_compliance_v2e2e：补材料程序版本的审批人（任务卡 14 第二轮）。
-- 原因：risk-generic-materials-init.sql 要求执行前 SET @approved_by=<真实 Operations 审批账号 ID>；apply-e2e.sh 第一轮未设置，
--   3 行 t_dd_program_version.approved_by 为 NULL，MaterialProgram#isApprovedAuthorityAt 判为无效，材料要求接口返回 503。
-- 取值：236033080844881920 = mor_platform_v2e2e 中最早的 ACTIVE 运营真人账号（交接后的 E2E 管理员），与本机 mor_compliance 现值相同。
-- 执行前核对（期望 3）：SELECT COUNT(*) FROM mor_compliance_v2e2e.t_dd_program_version WHERE approved_by IS NULL;
-- 预期：3 行更新。回滚：UPDATE ... SET approved_by=NULL WHERE program_version_id IN (9100000000000101,9100000000000102,9100000000000103);
USE mor_compliance_v2e2e;
START TRANSACTION;
UPDATE t_dd_program_version SET approved_by = 236033080844881920
 WHERE program_version_id IN (9100000000000101, 9100000000000102, 9100000000000103) AND approved_by IS NULL;
SELECT ROW_COUNT() AS updated_3;
COMMIT;
