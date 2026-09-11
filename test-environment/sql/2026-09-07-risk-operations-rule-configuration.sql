-- Reviewed manual Operations Risk rollout DML. The batch client must stop on its first error.
-- Assertion failure raises a scalar-subquery cardinality error; disconnect rolls back the transaction.
-- No automatic caller may apply this file. Status/version/timestamps are excluded only from replay identity.
SET NAMES utf8mb4;
USE mor_platform;
START TRANSACTION;
SET @risk_operations_time = UTC_TIMESTAMP(6);

SET @identity = NULL;
SET @identity = (SELECT `target_id` FROM t_gateway_target WHERE `target_id` = 900000000000000001 AND HEX(`target_service_code`) = '6F7065726174696F6E732D636F6E736F6C652D626666' AND HEX(`discovery_service_name`) = '6F7065726174696F6E732D636F6E736F6C652D626666' AND HEX(`channel`) = '4F5045524154494F4E53' AND `created_by` = 1 AND HEX(`status`) = '414354495645' AND `version` > 0 FOR UPDATE);
SET @assertion = IF(COALESCE((@identity = 900000000000000001), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));

SET @identity = NULL;
SET @identity = (SELECT `role_id` FROM t_role WHERE `role_id` = 900000000000000510 AND HEX(`tenant_scope`) = '504C4154464F524D' AND `tenant_id` IS NULL AND HEX(`scope_key`) = '504C4154464F524D' AND HEX(`role_code`) = '504C4154464F524D5F41444D494E' AND HEX(`role_name`) = 'E5B9B3E58FB0E7AEA1E79086E59198' AND `system_role` = 1 AND HEX(`status`) = '414354495645' AND `version` > 0 FOR UPDATE);
SET @assertion = IF(COALESCE((@identity = 900000000000000510), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));

SET @identity = NULL;
SET @identity = (SELECT `role_id` FROM t_role WHERE `role_id` = 900000000000000511 AND HEX(`tenant_scope`) = '504C4154464F524D' AND `tenant_id` IS NULL AND HEX(`scope_key`) = '504C4154464F524D' AND HEX(`role_code`) = '4F5045524154494F4E535F564945574552' AND HEX(`role_name`) = 'E8BF90E890A5E58FAAE8AFBBE4BABAE59198' AND `system_role` = 1 AND HEX(`status`) = '414354495645' AND `version` > 0 FOR UPDATE);
SET @assertion = IF(COALESCE((@identity = 900000000000000511), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000536, 'RISK_DECISION_CONFIG_READ', 'RISK_DECISION_CONFIGURATION', 'READ', 'LOW', 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE `permission_id` = 900000000000000536 AND HEX(`permission_code`) = '5249534B5F4445434953494F4E5F434F4E4649475F52454144' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '52454144' AND HEX(`risk_level`) = '4C4F57'
);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000537, 'RISK_RULE_SET_SAVE', 'RISK_DECISION_CONFIGURATION', 'SAVE', 'HIGH', 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE `permission_id` = 900000000000000537 AND HEX(`permission_code`) = '5249534B5F52554C455F5345545F53415645' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '53415645' AND HEX(`risk_level`) = '48494748'
);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000538, 'RISK_RULE_SET_FREEZE', 'RISK_DECISION_CONFIGURATION', 'FREEZE', 'HIGH', 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE `permission_id` = 900000000000000538 AND HEX(`permission_code`) = '5249534B5F52554C455F5345545F465245455A45' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '465245455A45' AND HEX(`risk_level`) = '48494748'
);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000539, 'RISK_TEST_CANDIDATE_CREATE', 'RISK_DECISION_CONFIGURATION', 'CREATE_TEST_CANDIDATE', 'HIGH', 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE `permission_id` = 900000000000000539 AND HEX(`permission_code`) = '5249534B5F544553545F43414E4449444154455F435245415445' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '4352454154455F544553545F43414E444944415445' AND HEX(`risk_level`) = '48494748'
);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000540, 'RISK_TEST_CANDIDATE_VALIDATE', 'RISK_DECISION_CONFIGURATION', 'VALIDATE_TEST_CANDIDATE', 'HIGH', 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission WHERE `permission_id` = 900000000000000540 AND HEX(`permission_code`) = '5249534B5F544553545F43414E4449444154455F56414C4944415445' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '56414C49444154455F544553545F43414E444944415445' AND HEX(`risk_level`) = '48494748'
);

SET @admin_grants = 0;
SET @viewer_grants = 0;
INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000581, 900000000000000510, 900000000000000536, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000581 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000536
);
SET @admin_grants = @admin_grants + ROW_COUNT();

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000582, 900000000000000510, 900000000000000537, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000582 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000537
);
SET @admin_grants = @admin_grants + ROW_COUNT();

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000583, 900000000000000510, 900000000000000538, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000583 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000538
);
SET @admin_grants = @admin_grants + ROW_COUNT();

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000584, 900000000000000510, 900000000000000539, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000584 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000539
);
SET @admin_grants = @admin_grants + ROW_COUNT();

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000585, 900000000000000510, 900000000000000540, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000585 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000540
);
SET @admin_grants = @admin_grants + ROW_COUNT();

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000586, 900000000000000511, 900000000000000536, 'ACTIVE', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE `role_permission_id` = 900000000000000586 AND `role_id` = 900000000000000511 AND `permission_id` = 900000000000000536
);
SET @viewer_grants = @viewer_grants + ROW_COUNT();

UPDATE t_role SET version = version + 1, update_time = @risk_operations_time WHERE role_id = 900000000000000510 AND @admin_grants > 0;
UPDATE t_role SET version = version + 1, update_time = @risk_operations_time WHERE role_id = 900000000000000511 AND @viewer_grants > 0;
INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000565, 'OPERATIONS_RISK_MANAGEMENT', 'OPERATIONS', 'MENU', NULL, '风控管理', NULL, NULL, 900000000000000536, 30, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000565 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F4D414E4147454D454E54' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '4D454E55' AND `parent_resource_id` IS NULL AND HEX(`display_name`) = 'E9A38EE68EA7E7AEA1E79086' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000536 AND `sort_order` = 30
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000566, 'OPERATIONS_RISK_RULE_CONFIGURATION', 'OPERATIONS', 'MENU', 900000000000000565, '规则配置', '/risk/rule-configuration', 'OPERATIONS_RISK_RULE_CONFIGURATION', 900000000000000536, 10, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000566 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F434F4E46494755524154494F4E' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '4D454E55' AND `parent_resource_id` = 900000000000000565 AND HEX(`display_name`) = 'E8A784E58899E9858DE7BDAE' AND HEX(`frontend_route`) = '2F7269736B2F72756C652D636F6E66696775726174696F6E' AND HEX(`component_key`) = '4F5045524154494F4E535F5249534B5F52554C455F434F4E46494755524154494F4E' AND `permission_id` = 900000000000000536 AND `sort_order` = 10
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000567, 'OPERATIONS_RISK_RULE_SET_SAVE', 'OPERATIONS', 'BUTTON', 900000000000000566, '保存为新版本', NULL, NULL, 900000000000000537, 10, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000567 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F5345545F53415645' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E4BF9DE5AD98E4B8BAE696B0E78988E69CAC' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000537 AND `sort_order` = 10
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000568, 'OPERATIONS_RISK_RULE_SET_FREEZE', 'OPERATIONS', 'BUTTON', 900000000000000566, '冻结版本', NULL, NULL, 900000000000000538, 20, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000568 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F5345545F465245455A45' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E586BBE7BB93E78988E69CAC' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000538 AND `sort_order` = 20
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000569, 'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', 'OPERATIONS', 'BUTTON', 900000000000000566, '创建 TEST Candidate', NULL, NULL, 900000000000000539, 30, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000569 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F544553545F43414E4449444154455F435245415445' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E5889BE5BBBA20544553542043616E646964617465' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000539 AND `sort_order` = 30
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000570, 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE', 'OPERATIONS', 'BUTTON', 900000000000000566, '执行静态校验', NULL, NULL, 900000000000000540, 40, 'DISABLED', 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource WHERE `resource_id` = 900000000000000570 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F544553545F43414E4449444154455F56414C4944415445' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E689A7E8A18CE99D99E68081E6A0A1E9AA8C' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000540 AND `sort_order` = 40
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001024, 'operations-risk-rule-authoring-context', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}', '/decision-rule-sets/v1/authoring-context/{pointCode}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001024 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D617574686F72696E672D636F6E74657874' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F617574686F72696E672D636F6E746578742F7B706F696E74436F64657D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F617574686F72696E672D636F6E746578742F7B706F696E74436F64657D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001025, 'operations-risk-rule-set-list', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets', '/decision-rule-sets/v1/rule-sets', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001025 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D6C697374' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001026, 'operations-risk-rule-set-detail', 'OPERATIONS', 'GET', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}', '/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001026 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D64657461696C' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001027, 'operations-risk-rule-set-save', 'OPERATIONS', 'POST', '/api/operations/decision-rule-sets/v1/rule-sets', '/decision-rule-sets/v1/rule-sets', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000537, NULL, 'DROP', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001027 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D73617665' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000537 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 65536 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001028, 'operations-risk-rule-set-freeze', 'OPERATIONS', 'POST', '/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze', '/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000538, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001028 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D667265657A65' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D2F667265657A65' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D2F667265657A65' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000538 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001029, 'operations-risk-test-candidate-list', 'OPERATIONS', 'GET', '/api/operations/decision-releases/v1/candidates', '/decision-releases/v1/candidates', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001029 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D6C697374' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001030, 'operations-risk-test-candidate-detail', 'OPERATIONS', 'GET', '/api/operations/decision-releases/v1/candidates/{releaseId}', '/decision-releases/v1/candidates/{releaseId}', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000536, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001030 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D64657461696C' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001031, 'operations-risk-test-candidate-create', 'OPERATIONS', 'POST', '/api/operations/decision-releases/v1/candidates', '/decision-releases/v1/candidates', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000539, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001031 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D637265617465' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000539 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time)
SELECT 900000000000001032, 'operations-risk-test-candidate-validate', 'OPERATIONS', 'POST', '/api/operations/decision-releases/v1/candidates/{releaseId}/validate', '/decision-releases/v1/candidates/{releaseId}/validate', 900000000000000001, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000000540, NULL, 'DROP', 'NONE', 16384, 5000, 10000, 'DISABLED', 1, 1, 1, @risk_operations_time, @risk_operations_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route WHERE `route_id` = 900000000000001032 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D76616C6964617465' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D2F76616C6964617465' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D2F76616C6964617465' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000540 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005100, 900000000000001024, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005100 AND `route_id` = 900000000000001024 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005101, 900000000000001024, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005101 AND `route_id` = 900000000000001024 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005102, 900000000000001025, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005102 AND `route_id` = 900000000000001025 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005103, 900000000000001025, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005103 AND `route_id` = 900000000000001025 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005104, 900000000000001026, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005104 AND `route_id` = 900000000000001026 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005105, 900000000000001026, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005105 AND `route_id` = 900000000000001026 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005106, 900000000000001027, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005106 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005107, 900000000000001027, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005107 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005108, 900000000000001027, 'content-type', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005108 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005109, 900000000000001027, 'content-type', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005109 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005110, 900000000000001027, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005110 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005111, 900000000000001027, 'idempotency-key', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005111 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005112, 900000000000001028, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005112 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005113, 900000000000001028, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005113 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005114, 900000000000001028, 'content-type', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005114 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005115, 900000000000001028, 'content-type', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005115 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005116, 900000000000001028, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005116 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005117, 900000000000001028, 'idempotency-key', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005117 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005118, 900000000000001029, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005118 AND `route_id` = 900000000000001029 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005119, 900000000000001029, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005119 AND `route_id` = 900000000000001029 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005120, 900000000000001030, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005120 AND `route_id` = 900000000000001030 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005121, 900000000000001030, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005121 AND `route_id` = 900000000000001030 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005122, 900000000000001031, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005122 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005123, 900000000000001031, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005123 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005124, 900000000000001031, 'content-type', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005124 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005125, 900000000000001031, 'content-type', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005125 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005126, 900000000000001031, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005126 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005127, 900000000000001031, 'idempotency-key', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005127 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005128, 900000000000001032, 'authorization', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005128 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005129, 900000000000001032, 'origin', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005129 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005130, 900000000000001032, 'content-type', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005130 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005131, 900000000000001032, 'content-type', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005131 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005132, 900000000000001032, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005132 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544'
);

INSERT INTO t_gateway_route_header (route_header_id, route_id, header_name, header_use)
SELECT 900000000000005133, 900000000000001032, 'idempotency-key', 'FORWARD_REQUEST'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005133 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004031, 900000000000001025, 'page'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004031 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '70616765'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004032, 900000000000001025, 'size'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004032 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '73697A65'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004033, 900000000000001025, 'pointCode'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004033 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '706F696E74436F6465'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004034, 900000000000001025, 'ruleSetCode'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004034 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '72756C65536574436F6465'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004035, 900000000000001025, 'status'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004035 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '737461747573'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004036, 900000000000001029, 'page'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004036 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '70616765'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004037, 900000000000001029, 'size'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004037 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '73697A65'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004038, 900000000000001029, 'pointCode'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004038 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '706F696E74436F6465'
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004039, 900000000000001029, 'status'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004039 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '737461747573'
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006110, 900000000000001024, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006110 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006111, 900000000000001024, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006111 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006112, 900000000000001024, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006112 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006113, 900000000000001024, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006113 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006114, 900000000000001024, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006114 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006115, 900000000000001024, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006115 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006116, 900000000000001025, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006116 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006117, 900000000000001025, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006117 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006118, 900000000000001025, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006118 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006119, 900000000000001025, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006119 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006120, 900000000000001025, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006120 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006121, 900000000000001025, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006121 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006122, 900000000000001026, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006122 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006123, 900000000000001026, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006123 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006124, 900000000000001026, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006124 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006125, 900000000000001026, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006125 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006126, 900000000000001026, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006126 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006127, 900000000000001026, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006127 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006128, 900000000000001027, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006128 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006129, 900000000000001027, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006129 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006130, 900000000000001027, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006130 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006131, 900000000000001027, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006131 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006132, 900000000000001027, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006132 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006133, 900000000000001027, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006133 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006134, 900000000000001028, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006134 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006135, 900000000000001028, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006135 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006136, 900000000000001028, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006136 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006137, 900000000000001028, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006137 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006138, 900000000000001028, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006138 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006139, 900000000000001028, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006139 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006140, 900000000000001029, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006140 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006141, 900000000000001029, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006141 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006142, 900000000000001029, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006142 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006143, 900000000000001029, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006143 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006144, 900000000000001029, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006144 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006145, 900000000000001029, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006145 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006146, 900000000000001030, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006146 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006147, 900000000000001030, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006147 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006148, 900000000000001030, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006148 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006149, 900000000000001030, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006149 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006150, 900000000000001030, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006150 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006151, 900000000000001030, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006151 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006152, 900000000000001031, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006152 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006153, 900000000000001031, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006153 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006154, 900000000000001031, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006154 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006155, 900000000000001031, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006155 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006156, 900000000000001031, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006156 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006157, 900000000000001031, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006157 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006158, 900000000000001032, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006158 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006159, 900000000000001032, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006159 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006160, 900000000000001032, 'RATE_LIMIT', 'CLIENT_IP', 10, 30, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006160 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006161, 900000000000001032, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006161 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006162, 900000000000001032, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006162 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL
);

INSERT INTO t_gateway_guard_rule (guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens, refill_period_millis, concurrency_limit)
SELECT 900000000000006163, 900000000000001032, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006163 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2
);

SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_permission WHERE `permission_id` = 900000000000000536 AND HEX(`permission_code`) = '5249534B5F4445434953494F4E5F434F4E4649475F52454144' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '52454144' AND HEX(`risk_level`) = '4C4F57' AND HEX(`status`) IN ('414354495645', '494E414354495645') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_permission WHERE `permission_id` = 900000000000000537 AND HEX(`permission_code`) = '5249534B5F52554C455F5345545F53415645' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '53415645' AND HEX(`risk_level`) = '48494748' AND HEX(`status`) IN ('414354495645', '494E414354495645') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_permission WHERE `permission_id` = 900000000000000538 AND HEX(`permission_code`) = '5249534B5F52554C455F5345545F465245455A45' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '465245455A45' AND HEX(`risk_level`) = '48494748' AND HEX(`status`) IN ('414354495645', '494E414354495645') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_permission WHERE `permission_id` = 900000000000000539 AND HEX(`permission_code`) = '5249534B5F544553545F43414E4449444154455F435245415445' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '4352454154455F544553545F43414E444944415445' AND HEX(`risk_level`) = '48494748' AND HEX(`status`) IN ('414354495645', '494E414354495645') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_permission WHERE `permission_id` = 900000000000000540 AND HEX(`permission_code`) = '5249534B5F544553545F43414E4449444154455F56414C4944415445' AND HEX(`resource_type`) = '5249534B5F4445434953494F4E5F434F4E46494755524154494F4E' AND HEX(`action_code`) = '56414C49444154455F544553545F43414E444944415445' AND HEX(`risk_level`) = '48494748' AND HEX(`status`) IN ('414354495645', '494E414354495645') AND `version` > 0) = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000581 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000536 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000582 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000537 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000583 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000538 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000584 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000539 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000585 AND `role_id` = 900000000000000510 AND `permission_id` = 900000000000000540 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_role_permission WHERE `role_permission_id` = 900000000000000586 AND `role_id` = 900000000000000511 AND `permission_id` = 900000000000000536 AND HEX(`status`) IN ('414354495645', '5245564F4B4544') AND `version` > 0) = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000565 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F4D414E4147454D454E54' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '4D454E55' AND `parent_resource_id` IS NULL AND HEX(`display_name`) = 'E9A38EE68EA7E7AEA1E79086' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000536 AND `sort_order` = 30 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000566 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F434F4E46494755524154494F4E' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '4D454E55' AND `parent_resource_id` = 900000000000000565 AND HEX(`display_name`) = 'E8A784E58899E9858DE7BDAE' AND HEX(`frontend_route`) = '2F7269736B2F72756C652D636F6E66696775726174696F6E' AND HEX(`component_key`) = '4F5045524154494F4E535F5249534B5F52554C455F434F4E46494755524154494F4E' AND `permission_id` = 900000000000000536 AND `sort_order` = 10 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000567 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F5345545F53415645' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E4BF9DE5AD98E4B8BAE696B0E78988E69CAC' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000537 AND `sort_order` = 10 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000568 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F52554C455F5345545F465245455A45' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E586BBE7BB93E78988E69CAC' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000538 AND `sort_order` = 20 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000569 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F544553545F43414E4449444154455F435245415445' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E5889BE5BBBA20544553542043616E646964617465' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000539 AND `sort_order` = 30 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_console_resource WHERE `resource_id` = 900000000000000570 AND HEX(`resource_code`) = '4F5045524154494F4E535F5249534B5F544553545F43414E4449444154455F56414C4944415445' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`resource_type`) = '425554544F4E' AND `parent_resource_id` = 900000000000000566 AND HEX(`display_name`) = 'E689A7E8A18CE99D99E68081E6A0A1E9AA8C' AND `frontend_route` IS NULL AND `component_key` IS NULL AND `permission_id` = 900000000000000540 AND `sort_order` = 40 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001024 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D617574686F72696E672D636F6E74657874' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F617574686F72696E672D636F6E746578742F7B706F696E74436F64657D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F617574686F72696E672D636F6E746578742F7B706F696E74436F64657D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001025 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D6C697374' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001026 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D64657461696C' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001027 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D73617665' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D73657473' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000537 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 65536 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001028 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D72756C652D7365742D667265657A65' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D2F667265657A65' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72756C652D736574732F76312F72756C652D736574732F7B72756C6553657456657273696F6E49647D2F667265657A65' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000538 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001029 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D6C697374' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001030 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D64657461696C' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '474554' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000536 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 0 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001031 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D637265617465' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E64696461746573' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000539 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1 AND (SELECT COUNT(*) FROM t_gateway_route WHERE `route_id` = 900000000000001032 AND HEX(`route_code`) = '6F7065726174696F6E732D7269736B2D746573742D63616E6469646174652D76616C6964617465' AND HEX(`channel`) = '4F5045524154494F4E53' AND HEX(`http_method`) = '504F5354' AND HEX(`external_path_template`) = '2F6170692F6F7065726174696F6E732F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D2F76616C6964617465' AND HEX(`downstream_path_template`) = '2F6465636973696F6E2D72656C65617365732F76312F63616E646964617465732F7B72656C6561736549647D2F76616C6964617465' AND `target_id` = 900000000000000001 AND HEX(`security_profile_code`) = '4F5045524154494F4E535F4A5754' AND HEX(`authorization_mode`) = '48554D414E5F5045524D495353494F4E' AND `permission_id` = 900000000000000540 AND `required_scope_code` IS NULL AND HEX(`request_cookie_mode`) = '44524F50' AND HEX(`response_cookie_mode`) = '4E4F4E45' AND `body_limit_bytes` = 16384 AND `body_receive_timeout_millis` = 5000 AND `response_timeout_millis` = 10000 AND `created_by` = 1 AND HEX(`status`) IN ('414354495645', '44495341424C4544') AND `version` > 0) = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005100 AND `route_id` = 900000000000001024 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005101 AND `route_id` = 900000000000001024 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005102 AND `route_id` = 900000000000001025 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005103 AND `route_id` = 900000000000001025 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005104 AND `route_id` = 900000000000001026 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005105 AND `route_id` = 900000000000001026 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005106 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005107 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005108 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005109 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005110 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005111 AND `route_id` = 900000000000001027 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005112 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005113 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005114 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005115 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005116 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005117 AND `route_id` = 900000000000001028 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005118 AND `route_id` = 900000000000001029 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005119 AND `route_id` = 900000000000001029 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005120 AND `route_id` = 900000000000001030 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005121 AND `route_id` = 900000000000001030 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005122 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005123 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005124 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005125 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005126 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005127 AND `route_id` = 900000000000001031 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005128 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '617574686F72697A6174696F6E' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005129 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6F726967696E' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005130 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005131 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '636F6E74656E742D74797065' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005132 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '434F52535F524551554553545F414C4C4F574544') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_header WHERE `route_header_id` = 900000000000005133 AND `route_id` = 900000000000001032 AND HEX(`header_name`) = '6964656D706F74656E63792D6B6579' AND HEX(`header_use`) = '464F52574152445F52455155455354') = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004031 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '70616765') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004032 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '73697A65') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004033 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '706F696E74436F6465') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004034 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '72756C65536574436F6465') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004035 AND `route_id` = 900000000000001025 AND HEX(`parameter_name`) = '737461747573') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004036 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '70616765') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004037 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '73697A65') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004038 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '706F696E74436F6465') = 1 AND (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE `route_query_param_id` = 900000000000004039 AND `route_id` = 900000000000001029 AND HEX(`parameter_name`) = '737461747573') = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));
SET @assertion = IF(COALESCE(((SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006110 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006111 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006112 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006113 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006114 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006115 AND `route_id` = 900000000000001024 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006116 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006117 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006118 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006119 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006120 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006121 AND `route_id` = 900000000000001025 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006122 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006123 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006124 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006125 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006126 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006127 AND `route_id` = 900000000000001026 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006128 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006129 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006130 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006131 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006132 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006133 AND `route_id` = 900000000000001027 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006134 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006135 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006136 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006137 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006138 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006139 AND `route_id` = 900000000000001028 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006140 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006141 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006142 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006143 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006144 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006145 AND `route_id` = 900000000000001029 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006146 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006147 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006148 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006149 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006150 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006151 AND `route_id` = 900000000000001030 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006152 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006153 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006154 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006155 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006156 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006157 AND `route_id` = 900000000000001031 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006158 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` = 300 AND `refill_tokens` = 300 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006159 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '5348415245445F524F555445' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 50) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006160 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` = 10 AND `refill_tokens` = 30 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006161 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '434C49454E545F4950' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 3) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006162 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '524154455F4C494D4954' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` = 10 AND `refill_tokens` = 10 AND `refill_period_millis` = 60000 AND `concurrency_limit` IS NULL) = 1 AND (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE `guard_rule_id` = 900000000000006163 AND `route_id` = 900000000000001032 AND HEX(`rule_type`) = '434F4E43555252454E4359' AND HEX(`dimension`) = '41555448454E544943415445445F5052494E434950414C' AND `capacity` IS NULL AND `refill_tokens` IS NULL AND `refill_period_millis` IS NULL AND `concurrency_limit` = 2) = 1), 0), 1, (SELECT value FROM (SELECT 1 AS value UNION ALL SELECT 2 AS value) AS assertion_failure));

COMMIT;

-- Read-only post-commit operator evidence.
SELECT * FROM t_permission WHERE permission_id BETWEEN 900000000000000536 AND 900000000000000540 ORDER BY permission_id;
SELECT * FROM t_console_resource WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000570 ORDER BY resource_id;
SELECT * FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000000581 AND 900000000000000586 ORDER BY role_permission_id;
SELECT r.route_id, r.route_code, r.status, r.version, (SELECT COUNT(*) FROM t_gateway_route_header h WHERE h.route_id = r.route_id) AS header_count, (SELECT COUNT(*) FROM t_gateway_route_query_param q WHERE q.route_id = r.route_id) AS query_count, (SELECT COUNT(*) FROM t_gateway_guard_rule g WHERE g.route_id = r.route_id) AS guard_count FROM t_gateway_route r WHERE r.route_id BETWEEN 900000000000001024 AND 900000000000001032 ORDER BY r.route_id;
