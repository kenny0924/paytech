-- 阶段 5 本机联调（只用于 mor_commercial_v2e2e，合成数据）：commercial Client 合同种子。
-- 形态照 paytech-commercial-test fixtures/commercial/checkout-query.json：CLIENT 合同（签署两方 CLIENT / OPERATING_ENTITY、
-- 签署文件、ACQUIRING 产品、CARD 开通项 + CURRENCY=USD 限制、CLIENT_PRICING 方案 / ACTIVE 版本 / AE001·USD·卡画像价格）。
--   合同 A 900000000001372001：Client 237161687952666625 × AE001，MOR（C1）
--   合同 B 900000000001372101：Client 900000000001380201 × AE001，PSP（C2）
-- PayFac Partner 合同由 setup.sh 以变量执行正式草稿 paytech/commercial/docs/database/sql/changes/2026-10-24-payfac-partner-agreement.sql。
-- 卡定价画像 RELSNAP_CARD（初始化目录不含定价画像）。ID 号段 9000000000013720xx / 9000000000013721xx / 900000000001372900。
-- Party 与资料 ID 与 10-client-master-seed.sql 一致。幂等：主键空操作吞重复键。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @t = '2026-10-01 00:00:00.000000';
SET @hash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

INSERT INTO t_pricing_profile (pricing_profile_id, pricing_profile_code, profile_name, profile_kind, payment_method_code, status,
  create_time, update_time) VALUES
  (900000000001372900, 'RELSNAP_CARD', 'Relsnap Synthetic Card Profile', 'PAYMENT_METHOD', 'CARD', 'ACTIVE', @t, @t)
ON DUPLICATE KEY UPDATE pricing_profile_id = pricing_profile_id;

INSERT INTO t_agreement (agreement_id, agreement_no, agreement_name, agreement_type, business_mode, fund_mode, pricing_authority,
  referral_partner_id, client_id, local_partner_id, entity_no, agreement_format, status, effective_from, effective_to, signed_time,
  activated_time, create_by, create_time, update_time) VALUES
  (900000000001372001, 'RELSNAP-CLIENT-MOR-AE001', 'Relsnap MOR Client Agreement', 'CLIENT', 'MOR', 'SETTLE_THROUGH_PLATFORM',
   'PLATFORM', NULL, 237161687952666625, NULL, 'AE001', 'MERGED_PARTY', 'ACTIVE', @t, NULL, @t, @t, 1, @t, @t),
  (900000000001372101, 'RELSNAP-CLIENT-PSP-AE001', 'Relsnap PSP Client Agreement', 'CLIENT', 'PSP', 'SETTLE_THROUGH_PLATFORM',
   'PLATFORM', NULL, 900000000001380201, NULL, 'AE001', 'MERGED_PARTY', 'ACTIVE', @t, NULL, @t, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE agreement_id = agreement_id;

INSERT INTO t_agreement_document_ref (agreement_document_id, agreement_id, document_type, file_id, content_hash, create_time,
  update_time) VALUES
  (900000000001372002, 900000000001372001, 'SIGNING_DOCUMENT', 900000000001372003, @hash, @t, @t),
  (900000000001372102, 900000000001372101, 'SIGNING_DOCUMENT', 900000000001372103, @hash, @t, @t)
ON DUPLICATE KEY UPDATE agreement_document_id = agreement_document_id;

INSERT INTO t_agreement_party (agreement_party_id, agreement_id, party_id, party_profile_id, party_role, acceptance_method,
  accepted_by_actor_id, accepted_time, evidence_document_id, acceptance_reference, acceptance_ref_slot, acceptance_recorded_by,
  acceptance_recorded_at, create_by, create_time, update_time) VALUES
  (900000000001372004, 900000000001372001, 900000000001380002, 900000000001380005, 'CLIENT', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001372002, 'RELSNAP-SIGN-MOR-CLIENT', 1, 1, @t, 1, @t, @t),
  (900000000001372005, 900000000001372001, 900000000001380102, 900000000001380111, 'OPERATING_ENTITY', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001372002, 'RELSNAP-SIGN-MOR-OE', 1, 1, @t, 1, @t, @t),
  (900000000001372104, 900000000001372101, 900000000001380202, 900000000001380205, 'CLIENT', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001372102, 'RELSNAP-SIGN-PSP-CLIENT', 1, 1, @t, 1, @t, @t),
  (900000000001372105, 900000000001372101, 900000000001380102, 900000000001380111, 'OPERATING_ENTITY', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001372102, 'RELSNAP-SIGN-PSP-OE', 1, 1, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE agreement_party_id = agreement_party_id;

INSERT INTO t_agreement_product (agreement_product_id, agreement_id, product_code, create_time, update_time) VALUES
  (900000000001372006, 900000000001372001, 'ACQUIRING', @t, @t),
  (900000000001372106, 900000000001372101, 'ACQUIRING', @t, @t)
ON DUPLICATE KEY UPDATE agreement_product_id = agreement_product_id;

INSERT INTO t_agreement_capability (agreement_capability_id, agreement_id, entity_no, capability_code, status, effective_from,
  effective_to, rolling_reserve_rate, rolling_reserve_days, create_time, update_time) VALUES
  (900000000001372007, 900000000001372001, 'AE001', 'CARD', 'ENABLED', @t, NULL, NULL, NULL, @t, @t),
  (900000000001372107, 900000000001372101, 'AE001', 'CARD', 'ENABLED', @t, NULL, NULL, NULL, @t, @t)
ON DUPLICATE KEY UPDATE agreement_capability_id = agreement_capability_id;

INSERT INTO t_agreement_capability_limit (agreement_capability_limit_id, agreement_capability_id, limit_code, limit_value,
  create_time, update_time) VALUES
  (900000000001372008, 900000000001372007, 'CURRENCY', 'USD', @t, @t),
  (900000000001372108, 900000000001372107, 'CURRENCY', 'USD', @t, @t)
ON DUPLICATE KEY UPDATE agreement_capability_limit_id = agreement_capability_limit_id;

INSERT INTO t_commercial_term_plan (term_plan_id, plan_code, term_type, owner_type, agreement_id, entity_no, client_id,
  local_partner_id, status, create_time, update_time) VALUES
  (900000000001372010, 'RELSNAP-CLIENT-MOR-PRICING', 'CLIENT_PRICING', 'CLIENT', 900000000001372001, 'AE001', 237161687952666625,
   NULL, 'ACTIVE', @t, @t),
  (900000000001372110, 'RELSNAP-CLIENT-PSP-PRICING', 'CLIENT_PRICING', 'CLIENT', 900000000001372101, 'AE001', 900000000001380201,
   NULL, 'ACTIVE', @t, @t)
ON DUPLICATE KEY UPDATE term_plan_id = term_plan_id;

INSERT INTO t_commercial_term_version (term_version_id, term_plan_id, agreement_id, version_no, status, active_slot, effective_from,
  effective_to, rounding_mode, pricing_model, fee_tax_treatment, create_time, update_time) VALUES
  (900000000001372011, 900000000001372010, 900000000001372001, 1, 'ACTIVE', 1, @t, NULL, 'HALF_UP', 'BLENDED', 'EXCLUSIVE', @t, @t),
  (900000000001372111, 900000000001372110, 900000000001372101, 1, 'ACTIVE', 1, @t, NULL, 'HALF_UP', 'BLENDED', 'EXCLUSIVE', @t, @t)
ON DUPLICATE KEY UPDATE term_version_id = term_version_id;

INSERT INTO t_commercial_term_price (term_price_id, term_version_id, entity_no, currency, pricing_profile_code, priority_no,
  settlement_business_days, business_calendar_code, settlement_timezone, cutoff_second_of_day, create_time, update_time) VALUES
  (900000000001372012, 900000000001372011, 'AE001', 'USD', 'RELSNAP_CARD', 1, 2, 'AE_BUSINESS_DAYS', 'UTC', 86399, @t, @t),
  (900000000001372112, 900000000001372111, 'AE001', 'USD', 'RELSNAP_CARD', 1, 2, 'AE_BUSINESS_DAYS', 'UTC', 86399, @t, @t)
ON DUPLICATE KEY UPDATE term_price_id = term_price_id;

INSERT INTO t_commercial_term_component (term_component_id, term_price_id, component_no, component_code, component_type,
  trigger_code, calculation_base, refund_behavior, rate, fixed_amount, currency, min_amount, max_amount, band_basis, band_mode,
  create_time, update_time) VALUES
  (900000000001372013, 900000000001372012, 1, 'CLIENT_FEE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA', 0.025,
   NULL, NULL, NULL, NULL, NULL, NULL, @t, @t),
  (900000000001372113, 900000000001372112, 1, 'CLIENT_FEE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA', 0.025,
   NULL, NULL, NULL, NULL, NULL, NULL, @t, @t)
ON DUPLICATE KEY UPDATE term_component_id = term_component_id;

INSERT INTO t_business_change_record (history_id, record_type, record_id, aggregate_root_type, aggregate_root_id,
  parent_history_id, current_version, create_time, update_time) VALUES
  (900000000001372020, 'AGREEMENT', '900000000001372001', 'AGREEMENT', '900000000001372001', NULL, 1, @t, @t),
  (900000000001372120, 'AGREEMENT', '900000000001372101', 'AGREEMENT', '900000000001372101', NULL, 1, @t, @t)
ON DUPLICATE KEY UPDATE history_id = history_id;
