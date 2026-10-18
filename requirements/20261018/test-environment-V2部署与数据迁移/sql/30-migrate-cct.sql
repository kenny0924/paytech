-- 30-migrate-cct.sql · mor_cct → mor_client_master + mor_commercial
-- 影响：只向 mor_client_master、mor_commercial 插入行；源库 mor_cct 只读，不修改、不删除。
-- 前置：10 已建库与账号；各目标库已按 paytech/<svc>/docs/database/sql/<svc>-schema.sql 建表且目标表为空
--       （scripts/migrate.sh 在执行前逐表核对为空；重复执行会因主键冲突整体失败，事务不提交）。
-- 列：每张表显式列出 V2 与旧表的共同列（由 scripts/generate_sql.py 从演练库 information_schema 生成，勿手改）。
-- 事务：单个事务；任一语句失败则 mysql 客户端退出，未提交的事务随连接关闭回滚。
-- 回滚：目标库只含本次迁移写入的数据：DROP DATABASE mor_client_master、mor_commercial，再按 10 与 Schema 重建；
--       旧库未改动，回滚不需要恢复源数据。
-- 拆分：t_business_change_record / _version 按 record_type 归属（client-master 14 类、commercial 4 类，取自两个服务的
--       BusinessChangeRecordRecordType 枚举）；t_compensation_event 按 event_type 归 client-master。
-- 不迁移：t_command_idempotency（归档见 33）。

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- mor_cct.t_agreement -> mor_commercial.t_agreement
INSERT INTO `mor_commercial`.`t_agreement` (`agreement_id`, `agreement_no`, `agreement_name`, `agreement_type`, `client_id`, `local_partner_id`, `entity_no`, `agreement_format`, `status`, `effective_from`, `effective_to`, `signed_time`, `activated_time`, `create_by`, `create_time`, `update_time`)
SELECT `agreement_id`, `agreement_no`, `agreement_name`, `agreement_type`, `client_id`, `local_partner_id`, `entity_no`, `agreement_format`, `status`, `effective_from`, `effective_to`, `signed_time`, `activated_time`, `create_by`, `create_time`, `update_time`
  FROM `mor_cct`.`t_agreement`;

-- mor_cct.t_agreement_document_ref -> mor_commercial.t_agreement_document_ref
INSERT INTO `mor_commercial`.`t_agreement_document_ref` (`agreement_document_id`, `agreement_id`, `document_type`, `file_id`, `content_hash`, `create_time`, `update_time`)
SELECT `agreement_document_id`, `agreement_id`, `document_type`, `file_id`, `content_hash`, `create_time`, `update_time`
  FROM `mor_cct`.`t_agreement_document_ref`;

-- mor_cct.t_agreement_operating_entity -> mor_commercial.t_agreement_operating_entity
INSERT INTO `mor_commercial`.`t_agreement_operating_entity` (`agreement_operating_entity_id`, `agreement_id`, `entity_no`, `create_time`, `update_time`)
SELECT `agreement_operating_entity_id`, `agreement_id`, `entity_no`, `create_time`, `update_time`
  FROM `mor_cct`.`t_agreement_operating_entity`;

-- mor_cct.t_agreement_party -> mor_commercial.t_agreement_party
INSERT INTO `mor_commercial`.`t_agreement_party` (`agreement_party_id`, `agreement_id`, `party_id`, `party_profile_id`, `party_role`, `acceptance_method`, `accepted_by_actor_id`, `accepted_time`, `evidence_document_id`, `acceptance_reference`, `acceptance_ref_slot`, `acceptance_recorded_by`, `acceptance_recorded_at`, `create_by`, `create_time`, `update_time`)
SELECT `agreement_party_id`, `agreement_id`, `party_id`, `party_profile_id`, `party_role`, `acceptance_method`, `accepted_by_actor_id`, `accepted_time`, `evidence_document_id`, `acceptance_reference`, `acceptance_ref_slot`, `acceptance_recorded_by`, `acceptance_recorded_at`, `create_by`, `create_time`, `update_time`
  FROM `mor_cct`.`t_agreement_party`;

-- mor_cct.t_application_document -> mor_client_master.t_application_document
INSERT INTO `mor_client_master`.`t_application_document` (`document_id`, `client_application_id`, `partner_application_id`, `operating_entity_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_id`, `content_hash`, `create_time`, `update_time`)
SELECT `document_id`, `client_application_id`, `partner_application_id`, `operating_entity_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_id`, `content_hash`, `create_time`, `update_time`
  FROM `mor_cct`.`t_application_document`;

-- mor_cct.t_business_change_record -> mor_client_master.t_business_change_record（按条件拆分）
INSERT INTO `mor_client_master`.`t_business_change_record` (`history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`, `parent_history_id`, `current_version`, `create_time`, `update_time`)
SELECT `history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`, `parent_history_id`, `current_version`, `create_time`, `update_time`
  FROM `mor_cct`.`t_business_change_record` WHERE `record_type` IN ('PARTY', 'CLIENT', 'LOCAL_PARTNER', 'OPERATING_ENTITY', 'PARTY_PROFILE', 'CLIENT_BUSINESS_PROFILE', 'CLIENT_APPLICATION', 'PARTNER_APPLICATION', 'OPERATING_ENTITY_APPLICATION', 'PARTY_RELATION', 'CLIENT_ENTITY_AFFILIATION', 'ENTITY_SOURCE_RELATION', 'CONSUMER_LEGAL_DOCUMENT', 'SELLER_DISCLOSURE');

-- mor_cct.t_business_change_record -> mor_commercial.t_business_change_record（按条件拆分）
INSERT INTO `mor_commercial`.`t_business_change_record` (`history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`, `parent_history_id`, `current_version`, `create_time`, `update_time`)
SELECT `history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`, `parent_history_id`, `current_version`, `create_time`, `update_time`
  FROM `mor_cct`.`t_business_change_record` WHERE `record_type` IN ('COMMERCIAL_TERM_VERSION', 'AGREEMENT', 'COMMERCIAL_TERM_PLAN', 'PAYMENT_METHOD_PROFILE');

-- mor_cct.t_business_change_version -> mor_client_master.t_business_change_version（按条件拆分）
INSERT INTO `mor_client_master`.`t_business_change_version` (`change_version_id`, `history_id`, `version_no`, `operation_type`, `snapshot_json`, `snapshot_format`, `create_by`, `create_time`)
SELECT `change_version_id`, `history_id`, `version_no`, `operation_type`, `snapshot_json`, `snapshot_format`, `create_by`, `create_time`
  FROM `mor_cct`.`t_business_change_version` WHERE `history_id` IN (SELECT r.`history_id` FROM `mor_cct`.`t_business_change_record` r WHERE r.`record_type` IN ('PARTY', 'CLIENT', 'LOCAL_PARTNER', 'OPERATING_ENTITY', 'PARTY_PROFILE', 'CLIENT_BUSINESS_PROFILE', 'CLIENT_APPLICATION', 'PARTNER_APPLICATION', 'OPERATING_ENTITY_APPLICATION', 'PARTY_RELATION', 'CLIENT_ENTITY_AFFILIATION', 'ENTITY_SOURCE_RELATION', 'CONSUMER_LEGAL_DOCUMENT', 'SELLER_DISCLOSURE'));

-- mor_cct.t_business_change_version -> mor_commercial.t_business_change_version（按条件拆分）
INSERT INTO `mor_commercial`.`t_business_change_version` (`change_version_id`, `history_id`, `version_no`, `operation_type`, `snapshot_json`, `snapshot_format`, `create_by`, `create_time`)
SELECT `change_version_id`, `history_id`, `version_no`, `operation_type`, `snapshot_json`, `snapshot_format`, `create_by`, `create_time`
  FROM `mor_cct`.`t_business_change_version` WHERE `history_id` IN (SELECT r.`history_id` FROM `mor_cct`.`t_business_change_record` r WHERE r.`record_type` IN ('COMMERCIAL_TERM_VERSION', 'AGREEMENT', 'COMMERCIAL_TERM_PLAN', 'PAYMENT_METHOD_PROFILE'));

-- mor_cct.t_client -> mor_client_master.t_client
INSERT INTO `mor_client_master`.`t_client` (`client_id`, `client_no`, `party_id`, `status`, `activation_time`, `suspension_time`, `termination_time`, `create_time`, `update_time`)
SELECT `client_id`, `client_no`, `party_id`, `status`, `activation_time`, `suspension_time`, `termination_time`, `create_time`, `update_time`
  FROM `mor_cct`.`t_client`;

-- mor_cct.t_client_application -> mor_client_master.t_client_application
INSERT INTO `mor_client_master`.`t_client_application` (`application_id`, `application_no`, `client_id`, `requested_by_account_id`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_type`, `submitted_time`, `create_time`, `update_time`, `current_submission_id`)
SELECT `application_id`, `application_no`, `client_id`, `requested_by_account_id`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_type`, `submitted_time`, `create_time`, `update_time`, `current_submission_id`
  FROM `mor_cct`.`t_client_application`;

-- mor_cct.t_client_business_classification -> mor_client_master.t_client_business_classification
INSERT INTO `mor_client_master`.`t_client_business_classification` (`classification_id`, `business_profile_id`, `industry_code`, `mcc_code`, `primary_slot`, `create_time`, `update_time`)
SELECT `classification_id`, `business_profile_id`, `industry_code`, `mcc_code`, `primary_slot`, `create_time`, `update_time`
  FROM `mor_cct`.`t_client_business_classification`;

-- mor_cct.t_client_business_profile -> mor_client_master.t_client_business_profile
INSERT INTO `mor_client_master`.`t_client_business_profile` (`business_profile_id`, `client_id`, `business_model_code`, `website_url`, `expected_volume`, `expected_ticket`, `currency`, `content_hash`, `create_time`, `update_time`, `business_description`, `classification_case_id`, `classification_confirmed_by`, `classification_confirmed_time`)
SELECT `business_profile_id`, `client_id`, `business_model_code`, `website_url`, `expected_volume`, `expected_ticket`, `currency`, `content_hash`, `create_time`, `update_time`, `business_description`, `classification_case_id`, `classification_confirmed_by`, `classification_confirmed_time`
  FROM `mor_cct`.`t_client_business_profile`;

-- mor_cct.t_client_entity_affiliation -> mor_client_master.t_client_entity_affiliation
INSERT INTO `mor_client_master`.`t_client_entity_affiliation` (`affiliation_id`, `client_id`, `entity_no`, `priority_no`, `status`, `active_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`)
SELECT `affiliation_id`, `client_id`, `entity_no`, `priority_no`, `status`, `active_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`
  FROM `mor_cct`.`t_client_entity_affiliation`;

-- mor_cct.t_commercial_term_component -> mor_commercial.t_commercial_term_component
INSERT INTO `mor_commercial`.`t_commercial_term_component` (`term_component_id`, `term_price_id`, `component_no`, `component_code`, `component_type`, `trigger_code`, `calculation_base`, `rate`, `fixed_amount`, `currency`, `min_amount`, `max_amount`, `band_mode`, `create_time`, `update_time`)
SELECT `term_component_id`, `term_price_id`, `component_no`, `component_code`, `component_type`, `trigger_code`, `calculation_base`, `rate`, `fixed_amount`, `currency`, `min_amount`, `max_amount`, `band_mode`, `create_time`, `update_time`
  FROM `mor_cct`.`t_commercial_term_component`;

-- mor_cct.t_commercial_term_component_band -> mor_commercial.t_commercial_term_component_band
INSERT INTO `mor_commercial`.`t_commercial_term_component_band` (`term_band_id`, `term_component_id`, `band_no`, `lower_bound`, `upper_bound`, `rate`, `fixed_amount`, `create_time`, `update_time`)
SELECT `term_band_id`, `term_component_id`, `band_no`, `lower_bound`, `upper_bound`, `rate`, `fixed_amount`, `create_time`, `update_time`
  FROM `mor_cct`.`t_commercial_term_component_band`;

-- mor_cct.t_commercial_term_plan -> mor_commercial.t_commercial_term_plan
INSERT INTO `mor_commercial`.`t_commercial_term_plan` (`term_plan_id`, `plan_code`, `term_type`, `agreement_id`, `entity_no`, `client_id`, `local_partner_id`, `status`, `create_time`, `update_time`)
SELECT `term_plan_id`, `plan_code`, `term_type`, `agreement_id`, `entity_no`, `client_id`, `local_partner_id`, `status`, `create_time`, `update_time`
  FROM `mor_cct`.`t_commercial_term_plan`;

-- mor_cct.t_commercial_term_price -> mor_commercial.t_commercial_term_price
INSERT INTO `mor_commercial`.`t_commercial_term_price` (`term_price_id`, `term_version_id`, `entity_no`, `payment_method_profile_code`, `priority_no`, `settlement_business_days`, `business_calendar_code`, `settlement_timezone`, `cutoff_second_of_day`, `create_time`, `update_time`)
SELECT `term_price_id`, `term_version_id`, `entity_no`, `payment_method_profile_code`, `priority_no`, `settlement_business_days`, `business_calendar_code`, `settlement_timezone`, `cutoff_second_of_day`, `create_time`, `update_time`
  FROM `mor_cct`.`t_commercial_term_price`;

-- mor_cct.t_commercial_term_version -> mor_commercial.t_commercial_term_version
INSERT INTO `mor_commercial`.`t_commercial_term_version` (`term_version_id`, `term_plan_id`, `agreement_id`, `version_no`, `status`, `active_slot`, `effective_from`, `effective_to`, `rounding_mode`, `create_time`, `update_time`)
SELECT `term_version_id`, `term_plan_id`, `agreement_id`, `version_no`, `status`, `active_slot`, `effective_from`, `effective_to`, `rounding_mode`, `create_time`, `update_time`
  FROM `mor_cct`.`t_commercial_term_version`;

-- mor_cct.t_compensation_event -> mor_client_master.t_compensation_event（按条件拆分）
INSERT INTO `mor_client_master`.`t_compensation_event` (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`
  FROM `mor_cct`.`t_compensation_event` WHERE `event_type` IN ('CLIENT_REVIEW_CLASSIFICATION', 'CLIENT_REVIEW_REGISTRATION', 'CLIENT_TENANT_PROJECTION');

-- mor_cct.t_consumer_legal_document -> mor_client_master.t_consumer_legal_document
INSERT INTO `mor_client_master`.`t_consumer_legal_document` (`legal_document_id`, `document_code`, `document_type`, `entity_no`, `country_code`, `locale`, `content_file_id`, `content_hash`, `status`, `effective_from`, `effective_to`, `approved_time`, `published_time`, `create_by`, `create_time`, `update_time`)
SELECT `legal_document_id`, `document_code`, `document_type`, `entity_no`, `country_code`, `locale`, `content_file_id`, `content_hash`, `status`, `effective_from`, `effective_to`, `approved_time`, `published_time`, `create_by`, `create_time`, `update_time`
  FROM `mor_cct`.`t_consumer_legal_document`;

-- mor_cct.t_entity_source_relation -> mor_client_master.t_entity_source_relation
INSERT INTO `mor_client_master`.`t_entity_source_relation` (`source_relation_id`, `entity_no`, `source_type`, `source_client_id`, `source_partner_id`, `client_source_basis`, `status`, `active_source_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`)
SELECT `source_relation_id`, `entity_no`, `source_type`, `source_client_id`, `source_partner_id`, `client_source_basis`, `status`, `active_source_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`
  FROM `mor_cct`.`t_entity_source_relation`;

-- mor_cct.t_individual_profile -> mor_client_master.t_individual_profile
INSERT INTO `mor_client_master`.`t_individual_profile` (`profile_id`, `given_name`, `middle_name`, `family_name`, `birth_date`, `nationality_country`, `residence_country`, `create_time`, `update_time`)
SELECT `profile_id`, `given_name`, `middle_name`, `family_name`, `birth_date`, `nationality_country`, `residence_country`, `create_time`, `update_time`
  FROM `mor_cct`.`t_individual_profile`;

-- mor_cct.t_local_partner -> mor_client_master.t_local_partner
INSERT INTO `mor_client_master`.`t_local_partner` (`local_partner_id`, `partner_no`, `party_id`, `status`, `activation_time`, `termination_time`, `create_time`, `update_time`)
SELECT `local_partner_id`, `partner_no`, `party_id`, `status`, `activation_time`, `termination_time`, `create_time`, `update_time`
  FROM `mor_cct`.`t_local_partner`;

-- mor_cct.t_operating_entity -> mor_client_master.t_operating_entity
INSERT INTO `mor_client_master`.`t_operating_entity` (`entity_no`, `party_id`, `operating_country`, `status`, `create_time`, `update_time`)
SELECT `entity_no`, `party_id`, `operating_country`, `status`, `create_time`, `update_time`
  FROM `mor_cct`.`t_operating_entity`;

-- mor_cct.t_operating_entity_application -> mor_client_master.t_operating_entity_application
INSERT INTO `mor_client_master`.`t_operating_entity_application` (`application_id`, `application_no`, `application_type`, `base_entity_change_version`, `entity_no`, `party_profile_id`, `source_type`, `source_client_id`, `source_partner_id`, `client_source_basis`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_decision_version`, `qualification_type`, `submitted_time`, `submission_hash_schema_version`, `submission_content_hash`, `approved_source_relation_id`, `create_time`, `update_time`)
SELECT `application_id`, `application_no`, `application_type`, `base_entity_change_version`, `entity_no`, `party_profile_id`, `source_type`, `source_client_id`, `source_partner_id`, `client_source_basis`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_decision_version`, `qualification_type`, `submitted_time`, `submission_hash_schema_version`, `submission_content_hash`, `approved_source_relation_id`, `create_time`, `update_time`
  FROM `mor_cct`.`t_operating_entity_application`;

-- mor_cct.t_operating_entity_scope -> mor_client_master.t_operating_entity_scope
INSERT INTO `mor_client_master`.`t_operating_entity_scope` (`scope_id`, `entity_no`, `application_id`, `consumer_country_code`, `currency`, `industry_code`, `mcc_code`, `status`, `active_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`)
SELECT `scope_id`, `entity_no`, `application_id`, `consumer_country_code`, `currency`, `industry_code`, `mcc_code`, `status`, `active_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`
  FROM `mor_cct`.`t_operating_entity_scope`;

-- mor_cct.t_organization_profile -> mor_client_master.t_organization_profile
INSERT INTO `mor_client_master`.`t_organization_profile` (`profile_id`, `legal_name`, `trade_name`, `incorporation_country`, `organization_type`, `incorporation_date`, `create_time`, `update_time`)
SELECT `profile_id`, `legal_name`, `trade_name`, `incorporation_country`, `organization_type`, `incorporation_date`, `create_time`, `update_time`
  FROM `mor_cct`.`t_organization_profile`;

-- mor_cct.t_partner_application -> mor_client_master.t_partner_application
INSERT INTO `mor_client_master`.`t_partner_application` (`application_id`, `application_no`, `local_partner_id`, `target_party_profile_id`, `requested_by_account_id`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_decision_version`, `qualification_type`, `submitted_time`, `submission_hash_schema_version`, `submission_content_hash`, `create_by`, `create_time`, `update_time`)
SELECT `application_id`, `application_no`, `local_partner_id`, `target_party_profile_id`, `requested_by_account_id`, `status`, `active_slot`, `due_diligence_case_id`, `qualification_decision_id`, `qualification_decision_version`, `qualification_type`, `submitted_time`, `submission_hash_schema_version`, `submission_content_hash`, `create_by`, `create_time`, `update_time`
  FROM `mor_cct`.`t_partner_application`;

-- mor_cct.t_party -> mor_client_master.t_party
INSERT INTO `mor_client_master`.`t_party` (`party_id`, `party_no`, `party_type`, `create_time`, `update_time`)
SELECT `party_id`, `party_no`, `party_type`, `create_time`, `update_time`
  FROM `mor_cct`.`t_party`;

-- mor_cct.t_party_address -> mor_client_master.t_party_address
INSERT INTO `mor_client_master`.`t_party_address` (`address_id`, `profile_id`, `address_type`, `address_line1`, `address_line2`, `city`, `state_or_province`, `postal_code`, `country_code`, `create_time`, `update_time`)
SELECT `address_id`, `profile_id`, `address_type`, `address_line1`, `address_line2`, `city`, `state_or_province`, `postal_code`, `country_code`, `create_time`, `update_time`
  FROM `mor_cct`.`t_party_address`;

-- mor_cct.t_party_contact -> mor_client_master.t_party_contact
INSERT INTO `mor_client_master`.`t_party_contact` (`contact_id`, `profile_id`, `contact_type`, `contact_vault_token`, `contact_masked_value`, `create_time`, `update_time`)
SELECT `contact_id`, `profile_id`, `contact_type`, `contact_vault_token`, `contact_masked_value`, `create_time`, `update_time`
  FROM `mor_cct`.`t_party_contact`;

-- mor_cct.t_party_identity_key -> mor_client_master.t_party_identity_key
INSERT INTO `mor_client_master`.`t_party_identity_key` (`identity_key_id`, `party_id`, `identifier_type`, `issuing_country`, `issuer_scope_key`, `normalized_identifier_value`, `create_time`, `update_time`)
SELECT `identity_key_id`, `party_id`, `identifier_type`, `issuing_country`, `issuer_scope_key`, `normalized_identifier_value`, `create_time`, `update_time`
  FROM `mor_cct`.`t_party_identity_key`;

-- mor_cct.t_party_profile -> mor_client_master.t_party_profile
INSERT INTO `mor_client_master`.`t_party_profile` (`profile_id`, `party_id`, `content_hash`, `create_time`, `update_time`, `draft_application_id`, `approved_time`)
SELECT `profile_id`, `party_id`, `content_hash`, `create_time`, `update_time`, `draft_application_id`, `approved_time`
  FROM `mor_cct`.`t_party_profile`;

-- mor_cct.t_party_profile_identifier -> mor_client_master.t_party_profile_identifier
INSERT INTO `mor_client_master`.`t_party_profile_identifier` (`profile_identifier_id`, `profile_id`, `identity_key_id`, `valid_from`, `valid_to`, `create_time`, `update_time`, `identifier_type`, `issuing_country`, `issuer_scope_key`, `identifier_value`)
SELECT `profile_identifier_id`, `profile_id`, `identity_key_id`, `valid_from`, `valid_to`, `create_time`, `update_time`, `identifier_type`, `issuing_country`, `issuer_scope_key`, `identifier_value`
  FROM `mor_cct`.`t_party_profile_identifier`;

-- mor_cct.t_party_relation -> mor_client_master.t_party_relation
INSERT INTO `mor_client_master`.`t_party_relation` (`relation_id`, `organization_party_id`, `related_party_id`, `relation_type`, `ownership_percentage`, `effective_from`, `effective_to`, `status`, `create_time`, `update_time`, `client_application_id`)
SELECT `relation_id`, `organization_party_id`, `related_party_id`, `relation_type`, `ownership_percentage`, `effective_from`, `effective_to`, `status`, `create_time`, `update_time`, `client_application_id`
  FROM `mor_cct`.`t_party_relation`;

-- mor_cct.t_payment_method_profile -> mor_commercial.t_payment_method_profile
INSERT INTO `mor_commercial`.`t_payment_method_profile` (`payment_method_profile_id`, `payment_method_profile_code`, `profile_name`, `payment_method_code`, `status`, `create_time`, `update_time`)
SELECT `payment_method_profile_id`, `payment_method_profile_code`, `profile_name`, `payment_method_code`, `status`, `create_time`, `update_time`
  FROM `mor_cct`.`t_payment_method_profile`;

-- mor_cct.t_payment_method_profile_cond -> mor_commercial.t_payment_method_profile_cond
INSERT INTO `mor_commercial`.`t_payment_method_profile_cond` (`profile_condition_id`, `payment_method_profile_id`, `dimension_code`, `condition_value`, `create_time`, `update_time`)
SELECT `profile_condition_id`, `payment_method_profile_id`, `dimension_code`, `condition_value`, `create_time`, `update_time`
  FROM `mor_cct`.`t_payment_method_profile_cond`;

-- mor_cct.t_seller_disclosure -> mor_client_master.t_seller_disclosure
INSERT INTO `mor_client_master`.`t_seller_disclosure` (`disclosure_id`, `entity_no`, `country_code`, `locale`, `public_legal_name`, `public_registration_number`, `public_tax_registration_number`, `public_address_text`, `public_support_email`, `public_support_phone`, `status`, `effective_from`, `effective_to`, `approved_time`, `published_time`, `create_by`, `create_time`, `update_time`)
SELECT `disclosure_id`, `entity_no`, `country_code`, `locale`, `public_legal_name`, `public_registration_number`, `public_tax_registration_number`, `public_address_text`, `public_support_email`, `public_support_phone`, `status`, `effective_from`, `effective_to`, `approved_time`, `published_time`, `create_by`, `create_time`, `update_time`
  FROM `mor_cct`.`t_seller_disclosure`;

COMMIT;
