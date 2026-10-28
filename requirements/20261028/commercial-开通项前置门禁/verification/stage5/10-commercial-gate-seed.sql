-- REQ-01 阶段 5 本机联调（只用于 mor_commercial_v2e2e，合成数据）：开通项前置门禁演示用 CLIENT 合同骨架。
-- 只建 ACTIVE 合同、签署两方、ACQUIRING 订阅、CLIENT_PRICING 方案与 ACTIVE 版本（只有 AE001·USD·RELSNAP_CARD 一条卡价格）和 BCR Root；
-- 开通项一律经运营 API 新增 / 编辑 / 暂停（走真实门禁链路），不在此插入。
--   合同 900000000001390001 GATE-CLIENT-MOR-AE001：Client 240674482170114049（compliance APPROVED）× AE001（APPROVED），MOR
-- 形态照 requirements/20261023/commercial-商业关系快照与真实解析接入/verification/local-e2e/20-commercial-seed.sql；OE 方 Party 同该文件。
-- ID 号段 9000000000013900xx。幂等：主键空操作吞重复键。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @t = '2026-10-01 00:00:00.000000';
SET @hash = 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';

INSERT INTO t_agreement (agreement_id, agreement_no, agreement_name, agreement_type, business_mode, fund_mode, pricing_authority,
  referral_partner_id, client_id, local_partner_id, entity_no, agreement_format, status, effective_from, effective_to, signed_time,
  activated_time, create_by, create_time, update_time) VALUES
  (900000000001390001, 'GATE-CLIENT-MOR-AE001', 'Gate Demo MOR Client Agreement', 'CLIENT', 'MOR', 'SETTLE_THROUGH_PLATFORM',
   'PLATFORM', NULL, 240674482170114049, NULL, 'AE001', 'MERGED_PARTY', 'ACTIVE', @t, NULL, @t, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE agreement_id = agreement_id;

INSERT INTO t_agreement_document_ref (agreement_document_id, agreement_id, document_type, file_id, content_hash, create_time,
  update_time) VALUES
  (900000000001390002, 900000000001390001, 'SIGNING_DOCUMENT', 900000000001390003, @hash, @t, @t)
ON DUPLICATE KEY UPDATE agreement_document_id = agreement_document_id;

INSERT INTO t_agreement_party (agreement_party_id, agreement_id, party_id, party_profile_id, party_role, acceptance_method,
  accepted_by_actor_id, accepted_time, evidence_document_id, acceptance_reference, acceptance_ref_slot, acceptance_recorded_by,
  acceptance_recorded_at, create_by, create_time, update_time) VALUES
  (900000000001390004, 900000000001390001, 240674482170114050, 240674482170114051, 'CLIENT', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001390002, 'GATE-SIGN-MOR-CLIENT', 1, 1, @t, 1, @t, @t),
  (900000000001390005, 900000000001390001, 900000000001380102, 900000000001380111, 'OPERATING_ENTITY', 'CLICK_ACCEPTANCE', 1, @t,
   900000000001390002, 'GATE-SIGN-MOR-OE', 1, 1, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE agreement_party_id = agreement_party_id;

INSERT INTO t_agreement_product (agreement_product_id, agreement_id, product_code, create_time, update_time) VALUES
  (900000000001390006, 900000000001390001, 'ACQUIRING', @t, @t)
ON DUPLICATE KEY UPDATE agreement_product_id = agreement_product_id;

INSERT INTO t_commercial_term_plan (term_plan_id, plan_code, term_type, owner_type, agreement_id, entity_no, client_id,
  local_partner_id, status, create_time, update_time) VALUES
  (900000000001390010, 'GATE-CLIENT-MOR-PRICING', 'CLIENT_PRICING', 'CLIENT', 900000000001390001, 'AE001', 240674482170114049,
   NULL, 'ACTIVE', @t, @t)
ON DUPLICATE KEY UPDATE term_plan_id = term_plan_id;

INSERT INTO t_commercial_term_version (term_version_id, term_plan_id, agreement_id, version_no, status, active_slot, effective_from,
  effective_to, rounding_mode, pricing_model, fee_tax_treatment, create_time, update_time) VALUES
  (900000000001390011, 900000000001390010, 900000000001390001, 1, 'ACTIVE', 1, @t, NULL, 'HALF_UP', 'BLENDED', 'EXCLUSIVE', @t, @t)
ON DUPLICATE KEY UPDATE term_version_id = term_version_id;

INSERT INTO t_commercial_term_price (term_price_id, term_version_id, entity_no, currency, pricing_profile_code, priority_no,
  settlement_business_days, business_calendar_code, settlement_timezone, cutoff_second_of_day, create_time, update_time) VALUES
  (900000000001390012, 900000000001390011, 'AE001', 'USD', 'RELSNAP_CARD', 1, 2, 'AE_BUSINESS_DAYS', 'UTC', 86399, @t, @t)
ON DUPLICATE KEY UPDATE term_price_id = term_price_id;

INSERT INTO t_commercial_term_component (term_component_id, term_price_id, component_no, component_code, component_type,
  trigger_code, calculation_base, refund_behavior, rate, fixed_amount, currency, min_amount, max_amount, band_basis, band_mode,
  create_time, update_time) VALUES
  (900000000001390013, 900000000001390012, 1, 'CLIENT_FEE', 'PERCENTAGE', 'SALE', 'GROSS_AMOUNT', 'RETURN_PRORATA', 0.025,
   NULL, NULL, NULL, NULL, NULL, NULL, @t, @t)
ON DUPLICATE KEY UPDATE term_component_id = term_component_id;

INSERT INTO t_business_change_record (history_id, record_type, record_id, aggregate_root_type, aggregate_root_id,
  parent_history_id, current_version, create_time, update_time) VALUES
  (900000000001390020, 'AGREEMENT', '900000000001390001', 'AGREEMENT', '900000000001390001', NULL, 1, @t, @t)
ON DUPLICATE KEY UPDATE history_id = history_id;
