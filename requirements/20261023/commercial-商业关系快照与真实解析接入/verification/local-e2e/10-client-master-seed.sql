-- 阶段 5 本机联调（只用于 local-environment 的 mor_client_master_v2e2e，合成数据）：client-master 主档种子。
-- 形态照 paytech-client-master-test fixtures/client-master/checkout-candidates.json（候选查询通过的最小主档），改为
-- AE001 × 消费者国家 US × USD × RETAIL / 5999 × locale en（acquiring 传 en）。
-- AE001 的来源先按“迁移前”的 PLATFORM 写入，随后由 setup.sh 执行 client-master 正式数据文件
-- paytech/client-master/docs/database/sql/changes/2026-10-24-relationship-snapshot-data.sql 把它迁到“我们的 PayFac”Partner
-- （900000000001370001，Party = AE001 Party），以此同时验证该数据文件。
-- ID 号段 9000000000013800xx—9000000000013802xx（避开数据文件使用的 900000000001370001—900000000001379999）。
-- 两个 Client：237161687952666625（MOR，沿用本机 e2e 既有 Client 编号，Platform / acquiring 已有其记录）、
-- 900000000001380201（PSP 变体）；两者都 ACTIVE、资料已批准、分类已确认，以 Affiliation 指向 AE001。
-- 平台签约方 Party 900000000001380900（资料 900000000001380901）：PayFac Partner 合同的 PLATFORM_SIGNATORY；commercial 解析经
-- client-master party-profiles 查询其已批准资料，所以必须在 client-master 中存在。
-- 幂等：每条 INSERT 以主键空操作吞重复键。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @t = '2026-10-01 00:00:00.000000';
SET @hash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';

INSERT INTO t_party (party_id, party_no, party_type, create_time, update_time) VALUES
  (900000000001380002, 'RELSNAP-PARTY-CLIENT-MOR', 'ORGANIZATION', @t, @t),
  (900000000001380102, 'RELSNAP-PARTY-AE001', 'ORGANIZATION', @t, @t),
  (900000000001380202, 'RELSNAP-PARTY-CLIENT-PSP', 'ORGANIZATION', @t, @t),
  (900000000001380900, 'RELSNAP-PARTY-PLATFORM', 'ORGANIZATION', @t, @t)
ON DUPLICATE KEY UPDATE party_id = party_id;

INSERT INTO t_party_profile (profile_id, party_id, content_hash, create_time, update_time, approved_time, draft_application_id) VALUES
  (900000000001380005, 900000000001380002, @hash, @t, @t, @t, NULL),
  (900000000001380111, 900000000001380102, @hash, @t, @t, @t, NULL),
  (900000000001380205, 900000000001380202, @hash, @t, @t, @t, NULL),
  (900000000001380901, 900000000001380900, @hash, @t, @t, @t, NULL)
ON DUPLICATE KEY UPDATE profile_id = profile_id;

INSERT INTO t_organization_profile (profile_id, legal_name, trade_name, incorporation_country, organization_type,
  incorporation_date, create_time, update_time) VALUES
  (900000000001380005, 'Relsnap Synthetic Client MOR LLC', NULL, 'US', 'LIMITED_COMPANY', NULL, @t, @t),
  (900000000001380111, 'Relsnap Synthetic AE001 FZ-LLC', NULL, 'AE', 'LIMITED_COMPANY', NULL, @t, @t),
  (900000000001380205, 'Relsnap Synthetic Client PSP LLC', NULL, 'US', 'LIMITED_COMPANY', NULL, @t, @t),
  (900000000001380901, 'Relsnap Synthetic Platform Ltd', NULL, 'AE', 'LIMITED_COMPANY', NULL, @t, @t)
ON DUPLICATE KEY UPDATE profile_id = profile_id;

INSERT INTO t_client (client_id, client_no, party_id, status, activation_time, suspension_time, termination_time,
  create_time, update_time) VALUES
  (237161687952666625, 'RELSNAP-CLIENT-MOR', 900000000001380002, 'ACTIVE', @t, NULL, NULL, @t, @t),
  (900000000001380201, 'RELSNAP-CLIENT-PSP', 900000000001380202, 'ACTIVE', @t, NULL, NULL, @t, @t)
ON DUPLICATE KEY UPDATE client_id = client_id;

INSERT INTO t_client_business_profile (business_profile_id, client_id, business_model_code, website_url, expected_volume,
  expected_ticket, currency, content_hash, create_time, update_time, business_description, classification_case_id,
  classification_confirmed_by, classification_confirmed_time) VALUES
  (900000000001380003, 237161687952666625, 'relsnap', NULL, 1.0, 1.0, 'USD', @hash, @t, @t,
   'Synthetic relsnap MOR business', 900000000001380980, 900000000001380981, @t),
  (900000000001380203, 900000000001380201, 'relsnap', NULL, 1.0, 1.0, 'USD', @hash, @t, @t,
   'Synthetic relsnap PSP business', 900000000001380982, 900000000001380981, @t)
ON DUPLICATE KEY UPDATE business_profile_id = business_profile_id;

INSERT INTO t_client_business_classification (classification_id, business_profile_id, industry_code, mcc_code, primary_slot,
  create_time, update_time) VALUES
  (900000000001380004, 900000000001380003, 'RETAIL', '5999', 1, @t, @t),
  (900000000001380204, 900000000001380203, 'RETAIL', '5999', 1, @t, @t)
ON DUPLICATE KEY UPDATE classification_id = classification_id;

INSERT INTO t_client_entity_affiliation (affiliation_id, client_id, entity_no, priority_no, status, active_slot, effective_from,
  effective_to, create_time, update_time) VALUES
  (900000000001380101, 237161687952666625, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001380206, 900000000001380201, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t)
ON DUPLICATE KEY UPDATE affiliation_id = affiliation_id;

-- AE001：PAYFAC（承接 MOR 与 PSP）；来源按迁移前形态 PLATFORM 写入，数据文件随后改为 PARTNER。
INSERT INTO t_operating_entity (entity_no, party_id, operating_country, status, acquiring_qualification, create_time, update_time)
VALUES ('AE001', 900000000001380102, 'AE', 'ACTIVE', 'PAYFAC', @t, @t)
ON DUPLICATE KEY UPDATE entity_no = entity_no;

INSERT INTO t_entity_source_relation (source_relation_id, entity_no, source_type, source_client_id, source_partner_id,
  client_source_basis, status, active_source_slot, effective_from, effective_to, create_time, update_time) VALUES
  (900000000001380103, 'AE001', 'PLATFORM', NULL, NULL, NULL, 'ACTIVE', 1, @t, NULL, @t, @t)
ON DUPLICATE KEY UPDATE source_relation_id = source_relation_id;

INSERT INTO t_operating_entity_application (application_id, application_no, application_type, base_entity_change_version,
  entity_no, party_profile_id, source_type, source_client_id, source_partner_id, client_source_basis, status, active_slot,
  due_diligence_case_id, qualification_decision_id, qualification_decision_version, qualification_type, submitted_time,
  submission_hash_schema_version, submission_content_hash, approved_source_relation_id, create_time, update_time) VALUES
  (900000000001380104, 'RELSNAP-AE001-APPLICATION', 'ONBOARDING', 1, 'AE001', 900000000001380111, 'PLATFORM', NULL, NULL, NULL,
   'APPROVED', NULL, 900000000001380131, 900000000001380132, 1, 'ENTITY_UNDERWRITING', NULL, 1, @hash, 900000000001380103, @t, @t)
ON DUPLICATE KEY UPDATE application_id = application_id;

INSERT INTO t_operating_entity_scope (scope_id, entity_no, application_id, consumer_country_code, currency, industry_code, mcc_code,
  status, active_slot, effective_from, effective_to, create_time, update_time) VALUES
  (900000000001380105, 'AE001', 900000000001380104, 'US', 'USD', 'RETAIL', '5999', 'ACTIVE', 1, @t, NULL, @t, @t)
ON DUPLICATE KEY UPDATE scope_id = scope_id;

INSERT INTO t_consumer_legal_document (legal_document_id, document_code, document_type, entity_no, country_code, locale,
  content_file_id, content_hash, status, effective_from, effective_to, approved_time, published_time, create_by, create_time,
  update_time) VALUES
  (900000000001380120, 'RELSNAP-AE001-US-EN-TERMS', 'CONSUMER_TERMS', 'AE001', 'US', 'en', 900000000001380125, @hash, 'ACTIVE',
   @t, NULL, @t, @t, 1, @t, @t),
  (900000000001380121, 'RELSNAP-AE001-US-EN-REFUND', 'REFUND_POLICY', 'AE001', 'US', 'en', 900000000001380126, @hash, 'ACTIVE',
   @t, NULL, @t, @t, 1, @t, @t),
  (900000000001380122, 'RELSNAP-AE001-US-EN-PRIVACY', 'PRIVACY_NOTICE', 'AE001', 'US', 'en', 900000000001380127, @hash, 'ACTIVE',
   @t, NULL, @t, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE legal_document_id = legal_document_id;

INSERT INTO t_seller_disclosure (disclosure_id, entity_no, country_code, locale, public_legal_name, public_registration_number,
  public_tax_registration_number, public_address_text, public_support_email, public_support_phone, status, effective_from,
  effective_to, approved_time, published_time, create_by, create_time, update_time) VALUES
  (900000000001380128, 'AE001', 'US', 'en', 'Relsnap Synthetic AE001 FZ-LLC', 'RELSNAP-REG-AE001', 'RELSNAP-TRN-AE001',
   '1 Synthetic Street, Dubai, AE', 'support-ae001@example.invalid', '+971400000001', 'ACTIVE', @t, NULL, @t, @t, 1, @t, @t)
ON DUPLICATE KEY UPDATE disclosure_id = disclosure_id;

INSERT INTO t_business_change_record (history_id, record_type, record_id, aggregate_root_type, aggregate_root_id,
  parent_history_id, current_version, create_time, update_time) VALUES
  (900000000001380140, 'OPERATING_ENTITY', 'AE001', 'OPERATING_ENTITY', 'AE001', NULL, 1, @t, @t),
  (900000000001380006, 'CLIENT', '237161687952666625', 'CLIENT', '237161687952666625', NULL, 1, @t, @t),
  (900000000001380207, 'CLIENT', '900000000001380201', 'CLIENT', '900000000001380201', NULL, 1, @t, @t)
ON DUPLICATE KEY UPDATE history_id = history_id;
