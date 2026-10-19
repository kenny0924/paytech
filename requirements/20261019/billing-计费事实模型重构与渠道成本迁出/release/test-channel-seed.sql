-- mor-test 验收渠道种子（阶段 7 第 8 步前执行；任务卡 F 补齐 E2E_TARGET=test 的前置）。
-- 原因：mor-test 的 mor_channel_connector 中 t_payment_channel / t_channel_mid 均为 0 行（F 线 2026-10-19 19:18Z 只读核对），
--   local_acceptance.py 的 S1 只从库中选取 ACTIVE Channel + ACTIVE MID，本脚本不插入种子，因此需要先登记一组合成渠道。
-- 内容：1 渠道（ACTIVE）、1 技术账号（DRAFT，无 Vault Token）、1 MID（ACTIVE，主体 ZZ901）。与本机 50-channel-connector-payermax-mock.sql
--   使用相同 ID 段 9500000000000001 / 101 / 201，以便 acceptance-cleanup-local.sql 的 @channel_seed 段原样删除；
--   **不登记 t_mid_capability / t_mid_capability_detail**：Payment 路由按能力筛选候选，没有能力行的 MID 不会被任何 Attempt 选中，
--   避免影响其他会话在 mor-test 的支付验收。主体 ZZ901 为合成值，不对应任何 Operating Entity（渠道成本范围只校验与 MID 主体一致）。
-- 执行账号：channel_connector_app（本库 SELECT / INSERT / UPDATE / DELETE），客户端 --default-character-set=utf8mb4。
-- 执行前核对（期望全部为 0）：
--   SELECT COUNT(*) FROM t_payment_channel WHERE channel_id = 9500000000000001 OR channel_code = 'E2E_BILLING_FACT_MOCK';
--   SELECT COUNT(*) FROM t_channel_account WHERE channel_account_id = 9500000000000101;
--   SELECT COUNT(*) FROM t_channel_mid WHERE mid_id = 9500000000000201 OR external_mid_code = 'E2E-BF-MID-001';
-- 预期：1 渠道、1 账号、1 MID。
-- 删除：验收后 `E2E_TARGET=test python3 -I verification/local_acceptance.py --cleanup --drop-seed`（执行 @channel_seed 段），
--   或手工：DELETE FROM t_channel_mid WHERE mid_id = 9500000000000201; DELETE FROM t_channel_account WHERE channel_account_id = 9500000000000101;
--         DELETE FROM t_payment_channel WHERE channel_id = 9500000000000001;
SET NAMES utf8mb4;
SET @cc_seed_time = UTC_TIMESTAMP(6);
START TRANSACTION;
INSERT INTO t_payment_channel (channel_id, channel_code, channel_name, channel_status, create_time, update_time) VALUES
  (9500000000000001, 'E2E_BILLING_FACT_MOCK', 'mor-test 计费事实验收合成渠道', 'ACTIVE', @cc_seed_time, @cc_seed_time);
INSERT INTO t_channel_account (channel_account_id, channel_id, entity_no, account_name, account_vault_token, account_status, remark, create_time, update_time) VALUES
  (9500000000000101, 9500000000000001, 'ZZ901', 'mor-test 计费事实验收合成账号', NULL, 'DRAFT', '20261019 billing 计费事实需求阶段 7 验收；验收后删除', @cc_seed_time, @cc_seed_time);
INSERT INTO t_channel_mid (mid_id, channel_id, channel_account_id, external_mid_code, entity_no, mid_status, activated_at, closed_at, create_time, update_time) VALUES
  (9500000000000201, 9500000000000001, 9500000000000101, 'E2E-BF-MID-001', 'ZZ901', 'ACTIVE', @cc_seed_time, NULL, @cc_seed_time, @cc_seed_time);
COMMIT;
SELECT (SELECT COUNT(*) FROM t_payment_channel) AS channels, (SELECT COUNT(*) FROM t_channel_account) AS accounts,
       (SELECT COUNT(*) FROM t_channel_mid) AS mids, (SELECT COUNT(*) FROM t_mid_capability) AS capabilities;
