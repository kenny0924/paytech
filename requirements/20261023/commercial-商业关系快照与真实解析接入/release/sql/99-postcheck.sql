-- 只读后核对（跨库）。括号内是“全部 SQL 执行完、块 137 已注册、尚未经运营页登记资格 / 建 Partner 合同 / 切 REAL”时的期望值；
-- 中间步骤只看对应库的行。演练记录见 evidence/rehearsal.md。
SET SESSION TRANSACTION READ ONLY;

-- compliance（10、11 之后）
SELECT 'cmp.role_id_type (varchar,varchar)', GROUP_CONCAT(DATA_TYPE ORDER BY TABLE_NAME) FROM information_schema.columns
 WHERE table_schema='mor_compliance' AND table_name IN ('t_qualification_decision','t_role_qualification') AND column_name='role_id';
SELECT 'cmp.case_id_nullable (YES)', IS_NULLABLE FROM information_schema.columns
 WHERE table_schema='mor_compliance' AND table_name='t_qualification_decision' AND column_name='case_id';
SELECT 'cmp.basis_note (1)', COUNT(*) FROM information_schema.columns
 WHERE table_schema='mor_compliance' AND table_name='t_qualification_decision' AND column_name='basis_note';
SELECT 'cmp.client_qualifications APPROVED/NOT_REQUIRED (37)', COUNT(*) FROM mor_compliance.t_role_qualification
 WHERE role_type='CLIENT' AND status='APPROVED' AND review_status='NOT_REQUIRED'
UNION ALL SELECT 'cmp.other_qualifications (0)', COUNT(*) FROM mor_compliance.t_role_qualification WHERE NOT (role_type='CLIENT' AND status='APPROVED')
UNION ALL SELECT 'cmp.backfill_state_changes (37)', COUNT(*) FROM mor_compliance.t_qualification_state_change WHERE trigger_type='BACKFILL'
UNION ALL SELECT 'cmp.ac11_client_qualified (1)', COUNT(*) FROM mor_compliance.t_role_qualification
 WHERE role_type='CLIENT' AND role_id='237575207592763393' AND status='APPROVED' AND valid_to IS NULL;

-- client-master（20、21、22[、23] 之后）
SELECT 'cm.acquiring_qualification NOT NULL (NO)', IS_NULLABLE FROM information_schema.columns
 WHERE table_schema='mor_client_master' AND table_name='t_operating_entity' AND column_name='acquiring_qualification';
SELECT 'cm.new_tables (3)', COUNT(*) FROM information_schema.tables
 WHERE table_schema='mor_client_master' AND table_name IN ('t_partner_role','t_party_role','t_client_partner_relation');
SELECT 'cm.source_platform (0)', COUNT(*) FROM mor_client_master.t_entity_source_relation WHERE source_type='PLATFORM'
UNION ALL SELECT 'cm.application_platform (0)', COUNT(*) FROM mor_client_master.t_operating_entity_application WHERE source_type='PLATFORM'
UNION ALL SELECT 'cm.ae001_partner_source_payfac (1)', COUNT(*) FROM mor_client_master.t_entity_source_relation
 WHERE entity_no='AE001' AND source_type='PARTNER' AND source_partner_id=900000000001370001 AND status='ACTIVE'
UNION ALL SELECT 'cm.payfac_partner_party_eq_ae001 (1)', COUNT(*) FROM mor_client_master.t_local_partner lp
  JOIN mor_client_master.t_operating_entity e ON e.entity_no='AE001' AND e.party_id=lp.party_id
 WHERE lp.local_partner_id=900000000001370001 AND lp.status='ACTIVE' AND lp.partner_no='LP-PAYFAC-AE001'
UNION ALL SELECT 'cm.payfac_oe_provider_role (1)', COUNT(*) FROM mor_client_master.t_partner_role
 WHERE local_partner_id=900000000001370001 AND role_code='OE_PROVIDER' AND status='ACTIVE'
UNION ALL SELECT 'cm.party_role_client (54)', COUNT(*) FROM mor_client_master.t_party_role WHERE role_code='CLIENT'
UNION ALL SELECT 'cm.party_role_oe (1)', COUNT(*) FROM mor_client_master.t_party_role WHERE role_code='OPERATING_ENTITY'
UNION ALL SELECT 'cm.party_role_oe_provider (1)', COUNT(*) FROM mor_client_master.t_party_role WHERE role_code='OE_PROVIDER'
UNION ALL SELECT 'cm.ae001_root_version (2)', MAX(current_version) FROM mor_client_master.t_business_change_record
 WHERE record_type='OPERATING_ENTITY' AND record_id='AE001' AND parent_history_id IS NULL
UNION ALL SELECT 'cm.payfac_root_version (1)', MAX(current_version) FROM mor_client_master.t_business_change_record
 WHERE record_type='LOCAL_PARTNER' AND record_id='900000000001370001' AND parent_history_id IS NULL
UNION ALL SELECT 'cm.ae001_scope_us_usd_retail_5999 (1)', COUNT(*) FROM mor_client_master.t_operating_entity_scope
 WHERE entity_no='AE001' AND consumer_country_code='US' AND currency='USD' AND industry_code='RETAIL' AND mcc_code='5999' AND status='ACTIVE'
UNION ALL SELECT 'cm.ae001_us_en_legal_docs (3)', COUNT(*) FROM mor_client_master.t_consumer_legal_document
 WHERE entity_no='AE001' AND country_code='US' AND locale='en' AND status='ACTIVE'
UNION ALL SELECT 'cm.ae001_us_en_seller_disclosure (1)', COUNT(*) FROM mor_client_master.t_seller_disclosure
 WHERE entity_no='AE001' AND country_code='US' AND locale='en' AND status='ACTIVE'
UNION ALL SELECT 'cm.ae001_affiliations (7)', COUNT(*) FROM mor_client_master.t_client_entity_affiliation WHERE entity_no='AE001' AND status='ACTIVE'
UNION ALL SELECT 'cm.signatory_profiles_approved_final (2)', COUNT(*) FROM mor_client_master.t_party_profile
 WHERE profile_id IN (881000000701,881000000702) AND approved_time <= '2026-10-20 02:50:52' AND draft_application_id IS NULL AND content_hash REGEXP '^[0-9a-f]{64}$'
UNION ALL SELECT 'cm.ac11_profile_final (1 after 23, else 0)', COUNT(*) FROM mor_client_master.t_party_profile p
  JOIN mor_client_master.t_client_business_profile b ON b.business_profile_id=237575207592763396
 WHERE p.profile_id=237575207592763395 AND p.draft_application_id IS NULL AND p.content_hash REGEXP '^[0-9a-f]{64}$'
   AND b.content_hash REGEXP '^[0-9a-f]{64}$';

-- commercial（只在走 30 兜底时有值；运营 API 路径的合同 ID 由应用生成，按 local_partner_id 查）
SELECT 'com.payfac_partner_agreements (0 before stage-7 ops API / fallback, then 1)', COUNT(*) FROM mor_commercial.t_agreement
 WHERE agreement_type='PARTNER' AND local_partner_id=900000000001370001
UNION ALL SELECT 'com.payfac_partner_plans (0, then 2)', COUNT(*) FROM mor_commercial.t_commercial_term_plan WHERE local_partner_id=900000000001370001;

-- acquiring（40、41 之后）
SELECT 'acq.platform_orders (0)', COUNT(*) FROM mor_acquiring.t_commerce_order WHERE entity_source_type='PLATFORM'
UNION ALL SELECT 'acq.rewritten_partner_orders (46)', COUNT(*) FROM mor_acquiring.t_commerce_order
 WHERE entity_source_type='PARTNER' AND local_partner_id=900000000001370001
UNION ALL SELECT 'acq.partner_orders_without_lp (0)', COUNT(*) FROM mor_acquiring.t_commerce_order WHERE entity_source_type='PARTNER' AND local_partner_id IS NULL
UNION ALL SELECT 'acq.context_model_version_null (0)', COUNT(*) FROM mor_acquiring.t_order_context WHERE effective_relationship_model_version IS NULL
UNION ALL SELECT 'acq.binding_rows (0)', COUNT(*) FROM mor_acquiring.t_order_relationship_binding;
SELECT 'acq.context_new_cols', GROUP_CONCAT(CONCAT(COLUMN_NAME,':',COLUMN_TYPE,':',IS_NULLABLE) ORDER BY ORDINAL_POSITION) FROM information_schema.columns
 WHERE table_schema='mor_acquiring' AND table_name='t_order_context' AND column_name IN ('fund_flow','effective_relationship_model_version','snapshot_model_version');

-- risk-decision（50 之后）
SELECT 'risk.platform_snapshots (0)', COUNT(*) FROM mor_risk_decision.t_checkout_input_snapshot WHERE entity_source_type='PLATFORM'
UNION ALL SELECT 'risk.rewritten_snapshots (46)', COUNT(*) FROM mor_risk_decision.t_checkout_input_snapshot
 WHERE entity_source_type='PARTNER' AND local_partner_id=900000000001370001
UNION ALL SELECT 'risk.entity_source_members (2)', COUNT(*) FROM mor_risk_decision.t_risk_variable_member WHERE variable_code='ENTITY_SOURCE_VALUES';

-- platform（60 之后休眠；61 之后 ACTIVE）
SELECT 'plt.permission (6 ACTIVE)', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.role_permission (6 REVOKED -> ACTIVE)', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.console_resource (6 DISABLED -> ACTIVE)', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.gateway_route (11 DISABLED -> ACTIVE)', COUNT(*), GROUP_CONCAT(DISTINCT status) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.route_header (38)', COUNT(*), NULL FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.route_query_param (17)', COUNT(*), NULL FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.guard_rule (66)', COUNT(*), NULL FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.routes_active (574 dormant / 585 active)', COUNT(*), NULL FROM mor_platform.t_gateway_route WHERE status='ACTIVE';
SELECT 'plt.role', role_id, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
