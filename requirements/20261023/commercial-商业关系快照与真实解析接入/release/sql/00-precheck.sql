-- 只读预检（阶段 7 第 ① 步；跨库，库名写全；不改任何数据）。期望值写在每行标签后；与阶段 6 只读盘点
-- （evidence/data-inventory.md，2026-10-25T11:12Z 备份）不一致时先停，重新盘点后再执行后续步骤。
SET SESSION TRANSACTION READ ONLY;

-- compliance：结构仍为旧形态（role_id BIGINT、case_id NOT NULL、无 basis_note），投影表为空。
SELECT 'cmp.role_id_type (bigint,bigint)', GROUP_CONCAT(DATA_TYPE ORDER BY TABLE_NAME) FROM information_schema.columns
 WHERE table_schema='mor_compliance' AND table_name IN ('t_qualification_decision','t_role_qualification') AND column_name='role_id';
SELECT 'cmp.basis_note_exists (0)', COUNT(*) FROM information_schema.columns
 WHERE table_schema='mor_compliance' AND table_name='t_qualification_decision' AND column_name='basis_note';
SELECT 'cmp.role_qualification_rows (0)', COUNT(*) FROM mor_compliance.t_role_qualification
UNION ALL SELECT 'cmp.state_change_rows (0)', COUNT(*) FROM mor_compliance.t_qualification_state_change
UNION ALL SELECT 'cmp.settlement_control_rows (0)', COUNT(*) FROM mor_compliance.t_settlement_control
UNION ALL SELECT 'cmp.client_reviewer_approved (37)', COUNT(*) FROM mor_compliance.t_qualification_decision
 WHERE role_type='CLIENT' AND decision_source='REVIEWER' AND outcome='APPROVED' AND decision_version IS NULL
UNION ALL SELECT 'cmp.client_reviewer_rejected (3)', COUNT(*) FROM mor_compliance.t_qualification_decision
 WHERE role_type='CLIENT' AND decision_source='REVIEWER' AND outcome='REJECTED' AND decision_version IS NULL
UNION ALL SELECT 'cmp.non_client_decisions (0)', COUNT(*) FROM mor_compliance.t_qualification_decision WHERE role_type<>'CLIENT';

-- client-master：没有任何 OE / Local Partner 主档（盘点 F-1）；新表 / 新列不存在；号段空闲。
SELECT 'cm.acquiring_qualification_col (0)', COUNT(*) FROM information_schema.columns
 WHERE table_schema='mor_client_master' AND table_name='t_operating_entity' AND column_name='acquiring_qualification';
SELECT 'cm.new_tables (0)', COUNT(*) FROM information_schema.tables
 WHERE table_schema='mor_client_master' AND table_name IN ('t_partner_role','t_party_role','t_client_partner_relation');
SELECT 'cm.operating_entity (0)', COUNT(*) FROM mor_client_master.t_operating_entity
UNION ALL SELECT 'cm.entity_source_relation (0)', COUNT(*) FROM mor_client_master.t_entity_source_relation
UNION ALL SELECT 'cm.source_platform (0)', COUNT(*) FROM mor_client_master.t_entity_source_relation WHERE source_type='PLATFORM'
UNION ALL SELECT 'cm.oe_application (0)', COUNT(*) FROM mor_client_master.t_operating_entity_application
UNION ALL SELECT 'cm.oe_scope (0)', COUNT(*) FROM mor_client_master.t_operating_entity_scope
UNION ALL SELECT 'cm.local_partner (0)', COUNT(*) FROM mor_client_master.t_local_partner
UNION ALL SELECT 'cm.affiliation (0)', COUNT(*) FROM mor_client_master.t_client_entity_affiliation
UNION ALL SELECT 'cm.seller_disclosure (0)', COUNT(*) FROM mor_client_master.t_seller_disclosure
UNION ALL SELECT 'cm.consumer_legal_document (0)', COUNT(*) FROM mor_client_master.t_consumer_legal_document
UNION ALL SELECT 'cm.client (54)', COUNT(*) FROM mor_client_master.t_client
UNION ALL SELECT 'cm.bcr_non_client (0)', COUNT(*) FROM mor_client_master.t_business_change_record WHERE record_type<>'CLIENT'
UNION ALL SELECT 'cm.placeholder_parties_880000000701_702 (0)', COUNT(*) FROM mor_client_master.t_party WHERE party_id IN (880000000701,880000000702)
UNION ALL SELECT 'cm.placeholder_profiles_881000000701_702 (0)', COUNT(*) FROM mor_client_master.t_party_profile WHERE profile_id IN (881000000701,881000000702)
UNION ALL SELECT 'cm.seed_and_data_id_ranges (0)', (SELECT COUNT(*) FROM mor_client_master.t_party WHERE party_id BETWEEN 900000000001370000 AND 900000000001389999)
  + (SELECT COUNT(*) FROM mor_client_master.t_business_change_record WHERE history_id BETWEEN 900000000001370000 AND 900000000001389999)
  + (SELECT COUNT(*) FROM mor_client_master.t_client_entity_affiliation WHERE affiliation_id BETWEEN 900000000001370000 AND 900000000001389999)
UNION ALL SELECT 'cm.ac11_client_237575207592763393_active (1)', COUNT(*) FROM mor_client_master.t_client WHERE client_id=237575207592763393 AND status='ACTIVE'
UNION ALL SELECT 'cm.ac11_client_profile_not_finalized (1, GAP-PROFILE)', COUNT(*) FROM mor_client_master.t_party_profile
 WHERE profile_id=237575207592763395 AND approved_time IS NOT NULL AND (draft_application_id IS NOT NULL OR content_hash IS NULL)
UNION ALL SELECT 'cm.ac11_business_profile_hash_null (1, GAP-PROFILE)', COUNT(*) FROM mor_client_master.t_client_business_profile
 WHERE business_profile_id=237575207592763396 AND content_hash IS NULL;

-- commercial：PayFac Partner 合同与 9000000000013710xx 号段未占用；AE001 下 Client 合同的 OE 签约方是占位 Party 880000000702。
SELECT 'com.payfac_agreement_or_plan (0)', (SELECT COUNT(*) FROM mor_commercial.t_agreement WHERE local_partner_id=900000000001370001 OR agreement_no LIKE 'PAYFAC-%'
    OR agreement_id BETWEEN 900000000001371000 AND 900000000001371099)
  + (SELECT COUNT(*) FROM mor_commercial.t_commercial_term_plan WHERE local_partner_id=900000000001370001 OR plan_code LIKE 'PAYFAC-%')
UNION ALL SELECT 'com.oe_party_rows_880000000702 (9)', COUNT(*) FROM mor_commercial.t_agreement_party WHERE party_role='OPERATING_ENTITY' AND party_id=880000000702 AND party_profile_id=881000000702
UNION ALL SELECT 'com.platform_signatory_880000000701 (2)', COUNT(*) FROM mor_commercial.t_agreement_party WHERE party_role='PLATFORM_SIGNATORY' AND party_id=880000000701 AND party_profile_id=881000000701
UNION ALL SELECT 'com.demo_cl_001_active (1)', COUNT(*) FROM mor_commercial.t_agreement WHERE agreement_id=238331035912310784 AND status='ACTIVE';
SELECT 'com.earliest_oe_or_platform_acceptance (2026-10-20 02:50:52.326809; seed profiles approved 2026-10-13 must not be later)', MIN(accepted_time) FROM mor_commercial.t_agreement_party
 WHERE party_id IN (880000000701,880000000702) AND accepted_time IS NOT NULL;

-- acquiring：46 条 PLATFORM 订单（都是 AE001、local_partner_id 为空），新表 / 新列不存在。
SELECT 'acq.platform_orders (46)', COUNT(*) FROM mor_acquiring.t_commerce_order WHERE entity_source_type='PLATFORM'
UNION ALL SELECT 'acq.platform_orders_not_ae001_or_with_lp (0)', COUNT(*) FROM mor_acquiring.t_commerce_order
 WHERE entity_source_type='PLATFORM' AND (entity_no<>'AE001' OR local_partner_id IS NOT NULL)
UNION ALL SELECT 'acq.other_source_orders (0)', COUNT(*) FROM mor_acquiring.t_commerce_order WHERE entity_source_type<>'PLATFORM'
UNION ALL SELECT 'acq.order_context (46)', COUNT(*) FROM mor_acquiring.t_order_context
UNION ALL SELECT 'acq.binding_table (0)', COUNT(*) FROM information_schema.tables WHERE table_schema='mor_acquiring' AND table_name='t_order_relationship_binding'
UNION ALL SELECT 'acq.context_new_cols (0)', COUNT(*) FROM information_schema.columns WHERE table_schema='mor_acquiring' AND table_name='t_order_context'
 AND column_name IN ('fund_flow','effective_relationship_model_version','snapshot_model_version');

-- risk-decision：46 条 PLATFORM 输入快照、目录行 PLATFORM 一条；规则 / 动作没有 PLATFORM 字面量。
SELECT 'risk.platform_snapshots (46)', COUNT(*) FROM mor_risk_decision.t_checkout_input_snapshot WHERE entity_source_type='PLATFORM'
UNION ALL SELECT 'risk.entity_source_members (3)', COUNT(*) FROM mor_risk_decision.t_risk_variable_member WHERE variable_code='ENTITY_SOURCE_VALUES'
UNION ALL SELECT 'risk.platform_member_900000000000092001 (1)', COUNT(*) FROM mor_risk_decision.t_risk_variable_member
 WHERE member_id=900000000000092001 AND value_key='PLATFORM'
UNION ALL SELECT 'risk.rule_operand_platform_literal (0)', COUNT(*) FROM mor_risk_decision.t_rule_operand WHERE value_type='STRING' AND string_value='PLATFORM'
UNION ALL SELECT 'risk.rule_action_param_platform (0)', COUNT(*) FROM mor_risk_decision.t_rule_action_parameter WHERE string_value='PLATFORM'
UNION ALL SELECT 'risk.rule_operand_entity_source_field (1, compares = CLIENT)', COUNT(*) FROM mor_risk_decision.t_rule_operand WHERE field_code='entity_source_type';

-- platform：块 137 号段 / 编码 / 路径 / 菜单排序未占用；角色 510 版本（记下供回滚核对，盘点时 49）；Gateway 有效路由数（盘点时 574 / 579）。
SELECT 'plt.permission_ids_or_codes (0)', COUNT(*) FROM mor_platform.t_permission
 WHERE permission_id BETWEEN 900000000000137001 AND 900000000000137999
    OR permission_code IN ('QUALIFICATION_READ','QUALIFICATION_MANAGE','OPERATING_ENTITY_READ','OPERATING_ENTITY_MANAGE','LOCAL_PARTNER_READ','LOCAL_PARTNER_MANAGE')
    OR resource_type IN ('QUALIFICATION','OPERATING_ENTITY','LOCAL_PARTNER')
UNION ALL SELECT 'plt.role_permission_ids (0)', COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.console_resource_ids_codes_routes (0)', COUNT(*) FROM mor_platform.t_console_resource
 WHERE resource_id BETWEEN 900000000000137001 AND 900000000000137999
    OR resource_code LIKE 'OPERATIONS\_QUALIFICATIONS%' OR resource_code LIKE 'OPERATIONS\_OPERATING\_ENTITIES%' OR resource_code LIKE 'OPERATIONS\_LOCAL\_PARTNERS%'
    OR (channel='OPERATIONS' AND frontend_route IN ('/compliance/qualifications','/master-data/operating-entities','/master-data/local-partners'))
UNION ALL SELECT 'plt.menu_sort_90_under_565 (0)', COUNT(*) FROM mor_platform.t_console_resource WHERE parent_resource_id=900000000000000565 AND sort_order=90
UNION ALL SELECT 'plt.menu_sort_60_70_under_95301 (0)', COUNT(*) FROM mor_platform.t_console_resource WHERE parent_resource_id=900000000000095301 AND sort_order IN (60,70)
UNION ALL SELECT 'plt.gateway_route_ids_codes_paths (0)', COUNT(*) FROM mor_platform.t_gateway_route
 WHERE route_id BETWEEN 900000000000137001 AND 900000000000137999
    OR route_code LIKE 'operations-operating-entity-%' OR route_code LIKE 'operations-local-partner-%' OR route_code LIKE 'operations-qualification-%'
    OR external_path_template LIKE '/api/operations/entity-admin/%' OR external_path_template LIKE '/api/operations/qualification-admin/%'
UNION ALL SELECT 'plt.route_header_ids (0)', COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.route_query_param_ids (0)', COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.guard_rule_ids (0)', COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000137001 AND 900000000000137999
UNION ALL SELECT 'plt.routes_active (574)', COUNT(*) FROM mor_platform.t_gateway_route WHERE status='ACTIVE'
UNION ALL SELECT 'plt.routes_total (579)', COUNT(*) FROM mor_platform.t_gateway_route;
SELECT 'plt.role', role_id, role_code, version FROM mor_platform.t_role WHERE role_id=900000000000000510;
SELECT 'plt.target', target_id, target_service_code, status FROM mor_platform.t_gateway_target WHERE target_id=900000000000000001;
SELECT 'plt.menu_parent', resource_id, resource_code, status FROM mor_platform.t_console_resource WHERE resource_id IN (900000000000000565,900000000000095301);
-- 多语言（盘点时 operations-console 当前发布 #7、21 个命名空间、8594 个 key；qualification / master-data 尚不存在）。
SELECT 'plt.i18n_ops_release_no (7)', r.release_no, r.key_count FROM mor_platform.t_i18n_application a
  JOIN mor_platform.t_i18n_release r ON r.release_id=a.current_release_id WHERE a.application_code='operations-console';
SELECT 'plt.i18n_ops_namespaces (21; new 0)', COUNT(*), SUM(n.namespace_code IN ('qualification','master-data')) FROM mor_platform.t_i18n_namespace n
  JOIN mor_platform.t_i18n_application a ON a.application_id=n.application_id WHERE a.application_code='operations-console';
