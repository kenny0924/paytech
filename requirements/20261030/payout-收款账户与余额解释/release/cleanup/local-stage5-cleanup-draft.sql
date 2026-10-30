-- payout S1 · 本机阶段 5 合成数据清理草稿（W9 阶段 6 整理；未执行，执行需用户另行授权）。目标：mor-local-mysql-1（13306）。
-- 来源：verification/stage5-backend-report.md 第 4 节；读数 2026-10-30 13:4xZ（root 只读）。
-- 前提：本机 e2e 共享栈（local-environment/e2e，会话 43 接手）若仍使用 mor_payout_v2e2e / mor_platform_v2e2e，先与其确认；执行前对涉及库 mysqldump。
-- 原 stage5 草稿的修正：① Vault 记录不能直接 DELETE——t_vault_access_event（95 行）与 t_vault_protect_request（48 行）以外键引用这些记录，
--   且属审计；与 data-query 清理先例相同，保留 Vault 记录（43 条：PAYOUT_BENEFICIARY_V1 26、PAYOUT_BANK_ACCOUNT_KEY_V1 17）。
--   ② account / billing 不物理删除：以 billing 反向 MANUAL_ADJUSTMENT 冲回或保留（会计分录不可变）。

-- A. mor_payout_v2e2e（全部为 S1E2E 合成数据：A 241533217348268033 账户 7 条、B 241533316505808897 账户 2 条；事件 46；补偿事件 8 条 FAILED）
SELECT owner_id, status, COUNT(*) FROM mor_payout_v2e2e.t_beneficiary_account GROUP BY 1, 2;          -- 期望 A 7 / B 2，其他 Owner 0
START TRANSACTION;
DELETE FROM mor_payout_v2e2e.t_beneficiary_account_event
 WHERE beneficiary_account_id IN (SELECT id FROM (SELECT id FROM mor_payout_v2e2e.t_beneficiary_account
                                                  WHERE owner_type = 'CLIENT' AND owner_id IN (241533217348268033, 241533316505808897)) x);
DELETE FROM mor_payout_v2e2e.t_compensation_event WHERE event_type = 'BENEFICIARY_NOTIFICATION';
DELETE FROM mor_payout_v2e2e.t_beneficiary_account WHERE owner_type = 'CLIENT' AND owner_id IN (241533217348268033, 241533316505808897);
-- 核对：三表应为 0 行后 COMMIT；否则 ROLLBACK。
SELECT (SELECT COUNT(*) FROM mor_payout_v2e2e.t_beneficiary_account) a, (SELECT COUNT(*) FROM mor_payout_v2e2e.t_beneficiary_account_event) e,
       (SELECT COUNT(*) FROM mor_payout_v2e2e.t_compensation_event) c;
COMMIT;

-- B. mor_vault：保留（见修正 ①）。如用户坚持删除，须先决定是否连带删除审计（不建议），本草稿不提供该 SQL。

-- C. mor_platform_v2e2e：经运营 IAM / Client 团队接口停用，不物理删除（审计引用）——
--   运营账号 241869362766548992（checker）、241869370471485440（noperm）；角色 241869367774547968（S1E2E_NOPERM_*）；
--   Client A 成员 241869379812200448（账号 241869378344194048，邀请 241869377308200960）。

-- D. billing / account（*_v2e2e）：不删除。已入账事实 241872762900099072（A 250 USD）、241872779794755584（B 80 USD）如需归零，
--   经运营登记反向 MANUAL_ADJUSTMENT；死信事实 241869769920262144、241870036384395264 留在 account.billing.dlq 或经 billing 更正作废，不要重放。

-- E. 疑似残留库 reh_payout_d（mor-local-mysql-1；87 张 Platform 表，建于 2026-10-30 07:49，像是阶段 4 块 147 本机演练拷贝；本需求文件未引用）：
--   待裁决——确认归属后再 DROP DATABASE reh_payout_d;（W9 未动）。
-- F. 测试库 mor_payout（本机 auto 测试用，0 行）与账号 payout_app：保留。
