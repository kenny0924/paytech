-- 仅本机 local-environment 的测试库 mor_commercial；不含 DROP 库表；对应 paytech-commercial-test/src/test/resources/init/ddl.sql 2026-10-19 第二轮形状。
USE mor_commercial;
RENAME TABLE t_payment_method_profile TO t_pricing_profile, t_payment_method_profile_cond TO t_pricing_profile_cond;

ALTER TABLE t_pricing_profile
  RENAME COLUMN payment_method_profile_id TO pricing_profile_id,
  RENAME COLUMN payment_method_profile_code TO pricing_profile_code,
  MODIFY COLUMN payment_method_code VARCHAR(64) NULL COMMENT 'profile_kind=PAYMENT_METHOD 时必填并精确绑定的 Payment Method 稳定代码，其他种类为空；由 Payment 目录拥有，CCT 不建立 Payment Method 主表。取值来源：Payment 拥有的 Payment Method 稳定代码目录；非固定枚举。',
  ADD COLUMN profile_kind VARCHAR(32) NOT NULL DEFAULT 'PAYMENT_METHOD' COMMENT '定价画像描述的定价对象种类；ACTIVE 后冻结，决定 payment_method_code 是否必填及可用条件维度。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC。' AFTER profile_name,
  DROP INDEX uk_payment_method_profile_code,
  DROP INDEX idx_pm_profile_lookup,
  ADD UNIQUE KEY uk_pricing_profile_code (pricing_profile_code),
  ADD KEY idx_pricing_profile_lookup (status, payment_method_code, pricing_profile_code);
ALTER TABLE t_pricing_profile ALTER COLUMN profile_kind DROP DEFAULT;

ALTER TABLE t_pricing_profile_cond
  RENAME COLUMN payment_method_profile_id TO pricing_profile_id,
  DROP INDEX uk_pm_profile_cond_value,
  DROP INDEX idx_pm_cond_lookup,
  ADD UNIQUE KEY uk_pricing_profile_cond_value (pricing_profile_id, dimension_code, condition_value),
  ADD KEY idx_pricing_cond_lookup (dimension_code, condition_value, pricing_profile_id);

ALTER TABLE t_commercial_term_price
  RENAME COLUMN payment_method_profile_code TO pricing_profile_code,
  DROP INDEX uk_term_price_profile,
  DROP INDEX idx_term_price_profile,
  ADD UNIQUE KEY uk_term_price_profile (term_version_id, entity_no, pricing_profile_code),
  ADD KEY idx_term_price_profile (pricing_profile_code, term_version_id);

ALTER TABLE t_agreement
  ADD COLUMN business_mode VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的合同业务模式，PARTNER 时为空；创建后不可修改。允许值：MOR、PSP。' AFTER agreement_type,
  ADD COLUMN fund_mode VARCHAR(32) NULL COMMENT 'agreement_type=CLIENT 时必填的资金模式，PARTNER 时为空；MOR 只允许 SETTLE_THROUGH_PLATFORM，FEES_ONLY 只允许 OE 来源 CLIENT，本期只接受 SETTLE_THROUGH_PLATFORM。允许值：SETTLE_THROUGH_PLATFORM、FEES_ONLY。' AFTER business_mode,
  ADD COLUMN pricing_authority VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的定价权归属；PARTNER 表示价目由推荐伙伴提出、平台运营录入审批，Partner 门户只读；本期只接受 PLATFORM。允许值：PLATFORM、PARTNER。' AFTER fund_mode,
  ADD COLUMN referral_partner_id BIGINT NULL COMMENT '推荐该合同的 Local Partner，client-master 跨库引用只存值；签署时冻结，pricing_authority=PARTNER 时必须非空。' AFTER pricing_authority,
  ADD KEY idx_agreement_referral (referral_partner_id);
