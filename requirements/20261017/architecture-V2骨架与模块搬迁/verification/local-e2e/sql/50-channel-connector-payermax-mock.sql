-- 阶段 5 本机全链路（任务卡 14）：mor_channel_connector_v2e2e 登记合成 PayerMax 渠道（PAYERMAX_MOCK），供卡支付 Attempt 路由。
-- 评审后由主会话以 root 执行（在 apply-e2e.sh schemas 之后）；channel-connector 没有配置管理接口，只能以 SQL 登记。
-- 对应真实链路：Payment 按 Intent 冻结的 entityNo / 支付方式 / 国家 / 币种 / MCC 调 channel-connector 候选查询
--   → 本 MID → Platform 标准 API PAY（integrationCode = channel_code = PAYERMAX_MOCK）→ 本机合成 PayerMax 127.0.0.1:18190。
-- 取值依据：
--   entity_no AE001 / mcc 5999：acquiring 的固定 Mock 商业事实（CustomerCheckoutIntegrationConfiguration#checkoutCommercialPort）。
--   payment_method_code CARD：run-service.sh 的 COMMERCE_CARD_PAYMENT_METHOD_CODE 默认值；两处必须一致。
--   能力：ACTION.PAY（自动捕获路由必需，RouteServiceImpl）、ACTION.REFUND、AMOUNT.MIN 0.01 / MAX 100000（USD 主单位）、
--         SECURITY.3DS_MODE SUPPORTED；能力代码与值类型取自 channel-connector application.yml 的 capability-definitions。
-- 渠道账号以 DRAFT、account_vault_token 为空登记：Vault Token 只能在运行时由运营人工加密（/vault-admin/v1/manual/secret-protect）取得，
--   由 verification/local-e2e/channel_setup.py 写入并改为 ACTIVE（夹具写入，带前置条件，见该脚本）。
-- ID 段 9500000000000001—9500000000000405 为本步保留（新建空库，无冲突）。
-- 执行前核对（期望全部为 0）：
--   SELECT COUNT(*) FROM t_payment_channel WHERE channel_id=9500000000000001 OR channel_code='PAYERMAX_MOCK';
--   SELECT COUNT(*) FROM t_channel_mid WHERE mid_id=9500000000000201;
-- 预期：1 渠道、1 账号、1 MID、1 能力组、5 能力明细。
-- 回滚：DELETE FROM t_mid_capability_detail WHERE mid_capability_detail_id BETWEEN 9500000000000401 AND 9500000000000405;
--       DELETE FROM t_mid_capability WHERE mid_capability_id=9500000000000301; DELETE FROM t_channel_mid WHERE mid_id=9500000000000201;
--       DELETE FROM t_channel_account WHERE channel_account_id=9500000000000101; DELETE FROM t_payment_channel WHERE channel_id=9500000000000001;
USE mor_channel_connector_v2e2e;
SET @cc_seed_time = UTC_TIMESTAMP(6);
START TRANSACTION;
INSERT INTO t_payment_channel (channel_id, channel_code, channel_name, channel_status, create_time, update_time) VALUES
  (9500000000000001, 'PAYERMAX_MOCK', '本机合成 PayerMax', 'ACTIVE', @cc_seed_time, @cc_seed_time);
INSERT INTO t_channel_account (channel_account_id, channel_id, entity_no, account_name, account_vault_token, account_status, remark, create_time, update_time) VALUES
  (9500000000000101, 9500000000000001, 'AE001', '本机合成 PayerMax 账号', NULL, 'DRAFT', '阶段 5 本机全链路；Token 由 channel_setup.py 写入', @cc_seed_time, @cc_seed_time);
INSERT INTO t_channel_mid (mid_id, channel_id, channel_account_id, external_mid_code, entity_no, mid_status, activated_at, closed_at, create_time, update_time) VALUES
  (9500000000000201, 9500000000000001, 9500000000000101, 'PMX-MOCK-MID-001', 'AE001', 'ACTIVE', @cc_seed_time, NULL, @cc_seed_time, @cc_seed_time);
INSERT INTO t_mid_capability (mid_capability_id, mid_id, payment_method_code, scheme_code, country_code, currency, mcc_code, capability_status, create_time, update_time) VALUES
  (9500000000000301, 9500000000000201, 'CARD', '*', '*', 'USD', '5999', 'ENABLED', @cc_seed_time, @cc_seed_time);
INSERT INTO t_mid_capability_detail (mid_capability_detail_id, mid_capability_id, capability_code, value_type, boolean_value, decimal_value, code_value, create_time, update_time) VALUES
  (9500000000000401, 9500000000000301, 'ACTION.PAY', 'BOOLEAN', 1, NULL, NULL, @cc_seed_time, @cc_seed_time),
  (9500000000000402, 9500000000000301, 'ACTION.REFUND', 'BOOLEAN', 1, NULL, NULL, @cc_seed_time, @cc_seed_time),
  (9500000000000403, 9500000000000301, 'AMOUNT.MIN', 'DECIMAL', NULL, 0.01000000, NULL, @cc_seed_time, @cc_seed_time),
  (9500000000000404, 9500000000000301, 'AMOUNT.MAX', 'DECIMAL', NULL, 100000.00000000, NULL, @cc_seed_time, @cc_seed_time),
  (9500000000000405, 9500000000000301, 'SECURITY.3DS_MODE', 'CODE', NULL, NULL, 'SUPPORTED', @cc_seed_time, @cc_seed_time);
COMMIT;
