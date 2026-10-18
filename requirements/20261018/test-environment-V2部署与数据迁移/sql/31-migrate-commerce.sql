-- 31-migrate-commerce.sql · mor_commerce → mor_acquiring + mor_consumer + mor_client_master
-- 影响：只向 mor_acquiring、mor_client_master、mor_consumer 插入行；源库 mor_commerce 只读，不修改、不删除。
-- 前置：10 已建库与账号；各目标库已按 paytech/<svc>/docs/database/sql/<svc>-schema.sql 建表且目标表为空
--       （scripts/migrate.sh 在执行前逐表核对为空；重复执行会因主键冲突整体失败，事务不提交）。
-- 列：每张表显式列出 V2 与旧表的共同列（由 scripts/generate_sql.py 从演练库 information_schema 生成，勿手改）。
-- 事务：单个事务；任一语句失败则 mysql 客户端退出，未提交的事务随连接关闭回滚。
-- 回滚：目标库只含本次迁移写入的数据：DROP DATABASE mor_acquiring、mor_client_master、mor_consumer，再按 10 与 Schema 重建；
--       旧库未改动，回滚不需要恢复源数据。
-- 拆分：t_customer → mor_consumer；t_client_stored_card_setting → mor_client_master；
--       t_compensation_event 按 event_type：STORED_CARD_INVALIDATION → consumer，其余已登记类型 → acquiring。
-- 不迁移：t_command_idempotency；t_commerce_order 的 context_format、preparation_command_id（第 03 步删除，原值归档见 33）。

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- mor_commerce.t_checkout_network_evidence -> mor_acquiring.t_checkout_network_evidence
INSERT INTO `mor_acquiring`.`t_checkout_network_evidence` (`network_evidence_id`, `order_id`, `checkout_session_id`, `evidence_scene`, `request_id`, `client_ip_address`, `user_agent`, `trusted_proxy_chain`, `ip_country_code`, `ip_region_code`, `ip_city`, `ip_asn`, `network_type`, `proxy_flag`, `vpn_flag`, `tor_flag`, `hosting_flag`, `browser_family`, `browser_major_version`, `os_family`, `os_major_version`, `device_type`, `occurred_at`, `create_time`)
SELECT `network_evidence_id`, `order_id`, `checkout_session_id`, `evidence_scene`, `request_id`, `client_ip_address`, `user_agent`, `trusted_proxy_chain`, `ip_country_code`, `ip_region_code`, `ip_city`, `ip_asn`, `network_type`, `proxy_flag`, `vpn_flag`, `tor_flag`, `hosting_flag`, `browser_family`, `browser_major_version`, `os_family`, `os_major_version`, `device_type`, `occurred_at`, `create_time`
  FROM `mor_commerce`.`t_checkout_network_evidence`;

-- mor_commerce.t_checkout_session -> mor_acquiring.t_checkout_session
INSERT INTO `mor_acquiring`.`t_checkout_session` (`checkout_session_id`, `order_id`, `public_token`, `public_origin`, `session_status`, `opened_at`, `completed_at`, `cancelled_at`, `version`, `create_time`, `update_time`)
SELECT `checkout_session_id`, `order_id`, `public_token`, `public_origin`, `session_status`, `opened_at`, `completed_at`, `cancelled_at`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_checkout_session`;

-- mor_commerce.t_client_stored_card_setting -> mor_client_master.t_client_stored_card_setting
INSERT INTO `mor_client_master`.`t_client_stored_card_setting` (`client_id`, `stored_card_enabled`, `version`, `create_time`, `update_time`)
SELECT `client_id`, `stored_card_enabled`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_client_stored_card_setting`;

-- mor_commerce.t_commerce_order -> mor_acquiring.t_commerce_order；不迁移列：context_format, preparation_command_id
INSERT INTO `mor_acquiring`.`t_commerce_order` (`order_id`, `order_no`, `client_id`, `client_order_id`, `customer_id`, `creation_mode`, `consumer_country_code`, `termination_requested_at`, `entity_no`, `entity_source_type`, `local_partner_id`, `industry_code`, `mcc_code`, `merchandise_amount`, `discount_amount`, `net_amount`, `tax_amount`, `rounding_amount`, `total_amount`, `currency`, `order_status`, `status_reason_code`, `expires_at`, `paid_at`, `cancelled_at`, `version`, `create_time`, `update_time`)
SELECT `order_id`, `order_no`, `client_id`, `client_order_id`, `customer_id`, `creation_mode`, `consumer_country_code`, `termination_requested_at`, `entity_no`, `entity_source_type`, `local_partner_id`, `industry_code`, `mcc_code`, `merchandise_amount`, `discount_amount`, `net_amount`, `tax_amount`, `rounding_amount`, `total_amount`, `currency`, `order_status`, `status_reason_code`, `expires_at`, `paid_at`, `cancelled_at`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_commerce_order`;

-- mor_commerce.t_compensation_event -> mor_acquiring.t_compensation_event（按条件拆分）
INSERT INTO `mor_acquiring`.`t_compensation_event` (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_compensation_event` WHERE `event_type` IN ('CLIENT_WEBHOOK_REQUESTED', 'SUBMISSION_EVENT_TYPE');

-- mor_commerce.t_compensation_event -> mor_consumer.t_compensation_event（按条件拆分）
INSERT INTO `mor_consumer`.`t_compensation_event` (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`, `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_compensation_event` WHERE `event_type` IN ('STORED_CARD_INVALIDATION');

-- mor_commerce.t_consumer_acceptance -> mor_acquiring.t_consumer_acceptance
INSERT INTO `mor_acquiring`.`t_consumer_acceptance` (`consumer_acceptance_id`, `consumer_contract_id`, `network_evidence_id`, `acceptance_type`, `accepted_at`, `command_idempotency_id`, `create_time`)
SELECT `consumer_acceptance_id`, `consumer_contract_id`, `network_evidence_id`, `acceptance_type`, `accepted_at`, `command_idempotency_id`, `create_time`
  FROM `mor_commerce`.`t_consumer_acceptance`;

-- mor_commerce.t_consumer_context -> mor_acquiring.t_consumer_context
INSERT INTO `mor_acquiring`.`t_consumer_context` (`consumer_context_id`, `order_id`, `consumer_reference`, `email_vault_token`, `phone_vault_token`, `consumer_profile_vault_token`, `email_domain`, `phone_country_code`, `billing_country_code`, `billing_region_code`, `billing_city`, `billing_postal_prefix`, `version`, `create_time`, `update_time`)
SELECT `consumer_context_id`, `order_id`, `consumer_reference`, `email_vault_token`, `phone_vault_token`, `consumer_profile_vault_token`, `email_domain`, `phone_country_code`, `billing_country_code`, `billing_region_code`, `billing_city`, `billing_postal_prefix`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_consumer_context`;

-- mor_commerce.t_consumer_contract -> mor_acquiring.t_consumer_contract
INSERT INTO `mor_acquiring`.`t_consumer_contract` (`consumer_contract_id`, `order_id`, `order_legal_id`, `rendered_file_id`, `rendered_content_hash`, `locale`, `presented_at`, `create_time`)
SELECT `consumer_contract_id`, `order_id`, `order_legal_id`, `rendered_file_id`, `rendered_content_hash`, `locale`, `presented_at`, `create_time`
  FROM `mor_commerce`.`t_consumer_contract`;

-- mor_commerce.t_customer -> mor_consumer.t_customer
INSERT INTO `mor_consumer`.`t_customer` (`customer_id`, `customer_no`, `client_id`, `consumer_reference`, `active_reference`, `customer_status`, `deleted_time`, `version`, `create_time`, `update_time`)
SELECT `customer_id`, `customer_no`, `client_id`, `consumer_reference`, `active_reference`, `customer_status`, `deleted_time`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_customer`;

-- mor_commerce.t_order_context -> mor_acquiring.t_order_context
INSERT INTO `mor_acquiring`.`t_order_context` (`order_context_id`, `order_id`, `commercial_context_time`, `affiliation_id`, `entity_scope_id`, `entity_source_relation_id`, `client_pricing_version_id`, `partner_share_version_id`, `platform_charge_version_id`, `platform_service_party_id`, `risk_checkout_assessment_id`, `finance_tax_snapshot_id`, `finance_tax_snapshot_hash`, `consumer_country_code`, `locale`, `create_time`)
SELECT `order_context_id`, `order_id`, `commercial_context_time`, `affiliation_id`, `entity_scope_id`, `entity_source_relation_id`, `client_pricing_version_id`, `partner_share_version_id`, `platform_charge_version_id`, `platform_service_party_id`, `risk_checkout_assessment_id`, `finance_tax_snapshot_id`, `finance_tax_snapshot_hash`, `consumer_country_code`, `locale`, `create_time`
  FROM `mor_commerce`.`t_order_context`;

-- mor_commerce.t_order_funds_availability_projection -> mor_acquiring.t_order_funds_availability_projection
INSERT INTO `mor_acquiring`.`t_order_funds_availability_projection` (`order_funds_availability_projection_id`, `order_id`, `finance_economics_id`, `maturity_journal_id`, `funds_availability_status`, `available_amount`, `currency`, `scheduled_at`, `available_at`, `economics_event_key`, `economics_source_version`, `economics_source_payload_hash`, `maturity_event_key`, `maturity_source_version`, `maturity_source_payload_hash`, `as_of`, `version`, `create_time`, `update_time`)
SELECT `order_funds_availability_projection_id`, `order_id`, `finance_economics_id`, `maturity_journal_id`, `funds_availability_status`, `available_amount`, `currency`, `scheduled_at`, `available_at`, `economics_event_key`, `economics_source_version`, `economics_source_payload_hash`, `maturity_event_key`, `maturity_source_version`, `maturity_source_payload_hash`, `as_of`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_order_funds_availability_projection`;

-- mor_commerce.t_order_item -> mor_acquiring.t_order_item
INSERT INTO `mor_acquiring`.`t_order_item` (`order_item_id`, `order_id`, `client_item_id`, `item_name`, `item_description`, `product_type_code`, `tax_category_code`, `quantity`, `unit_amount`, `line_gross_amount`, `line_discount_amount`, `line_net_amount`, `currency`, `sort_no`, `create_time`)
SELECT `order_item_id`, `order_id`, `client_item_id`, `item_name`, `item_description`, `product_type_code`, `tax_category_code`, `quantity`, `unit_amount`, `line_gross_amount`, `line_discount_amount`, `line_net_amount`, `currency`, `sort_no`, `create_time`
  FROM `mor_commerce`.`t_order_item`;

-- mor_commerce.t_order_legal -> mor_acquiring.t_order_legal
INSERT INTO `mor_acquiring`.`t_order_legal` (`order_legal_id`, `order_context_id`, `client_agreement_id`, `partner_agreement_id`, `partner_agreement_operating_entity_id`, `consumer_terms_document_id`, `consumer_terms_file_id`, `consumer_terms_content_hash`, `refund_policy_document_id`, `refund_policy_file_id`, `refund_policy_content_hash`, `privacy_notice_document_id`, `privacy_notice_file_id`, `privacy_notice_content_hash`, `seller_disclosure_id`, `public_legal_name`, `public_registration_number`, `public_tax_registration_number`, `public_address_text`, `public_support_email`, `public_support_phone`, `create_time`)
SELECT `order_legal_id`, `order_context_id`, `client_agreement_id`, `partner_agreement_id`, `partner_agreement_operating_entity_id`, `consumer_terms_document_id`, `consumer_terms_file_id`, `consumer_terms_content_hash`, `refund_policy_document_id`, `refund_policy_file_id`, `refund_policy_content_hash`, `privacy_notice_document_id`, `privacy_notice_file_id`, `privacy_notice_content_hash`, `seller_disclosure_id`, `public_legal_name`, `public_registration_number`, `public_tax_registration_number`, `public_address_text`, `public_support_email`, `public_support_phone`, `create_time`
  FROM `mor_commerce`.`t_order_legal`;

-- mor_commerce.t_order_payment_projection -> mor_acquiring.t_order_payment_projection
INSERT INTO `mor_acquiring`.`t_order_payment_projection` (`order_payment_projection_id`, `order_id`, `payment_intent_id`, `intent_status`, `intent_source_version`, `last_intent_event_key`, `last_intent_payload_hash`, `active_attempt_id`, `last_attempt_id`, `last_attempt_no`, `last_attempt_status`, `attempt_source_version`, `last_attempt_event_key`, `last_attempt_payload_hash`, `successful_attempt_id`, `captured_amount`, `currency`, `last_event_time`, `version`, `create_time`, `update_time`)
SELECT `order_payment_projection_id`, `order_id`, `payment_intent_id`, `intent_status`, `intent_source_version`, `last_intent_event_key`, `last_intent_payload_hash`, `active_attempt_id`, `last_attempt_id`, `last_attempt_no`, `last_attempt_status`, `attempt_source_version`, `last_attempt_event_key`, `last_attempt_payload_hash`, `successful_attempt_id`, `captured_amount`, `currency`, `last_event_time`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_order_payment_projection`;

-- mor_commerce.t_order_refund_position -> mor_acquiring.t_order_refund_position
INSERT INTO `mor_acquiring`.`t_order_refund_position` (`order_refund_position_id`, `order_id`, `refundable_base_amount`, `reserved_amount`, `refunded_amount`, `currency`, `version`, `create_time`, `update_time`)
SELECT `order_refund_position_id`, `order_id`, `refundable_base_amount`, `reserved_amount`, `refunded_amount`, `currency`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_order_refund_position`;

-- mor_commerce.t_refund_request -> mor_acquiring.t_refund_request
INSERT INTO `mor_acquiring`.`t_refund_request` (`refund_request_id`, `refund_request_no`, `order_id`, `client_id`, `request_origin`, `client_refund_id`, `refund_amount`, `currency`, `reason_code`, `reason_summary`, `refund_status`, `status_reason_code`, `channel_refund_id`, `completed_at`, `version`, `create_time`, `update_time`)
SELECT `refund_request_id`, `refund_request_no`, `order_id`, `client_id`, `request_origin`, `client_refund_id`, `refund_amount`, `currency`, `reason_code`, `reason_summary`, `refund_status`, `status_reason_code`, `channel_refund_id`, `completed_at`, `version`, `create_time`, `update_time`
  FROM `mor_commerce`.`t_refund_request`;

COMMIT;
