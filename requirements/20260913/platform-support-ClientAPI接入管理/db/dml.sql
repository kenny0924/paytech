-- Approved design. Target Platform-owned database; apply manually only after environment approval.
-- 127 INSERT rows; 2 existing Route version updates. No Scope/Client/Credential seed data.
-- New routes/resources DISABLED and PLATFORM_ADMIN grants REVOKED; activation is separate.
-- BEFORE source: set @plt_api_operator to the approved real Operations account ID.
-- Run db/README.md preflight; no --force; ONE interactive connection; no COMMIT in this file.
SET @plt_api_time=UTC_TIMESTAMP(6);
SET @plt_api_target=(SELECT target_id FROM t_gateway_target WHERE target_service_code='operations-console-bff' AND channel='OPERATIONS' AND status='ACTIVE');
SET @plt_api_admin=(SELECT role_id FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE');
SET @plt_api_parent=(SELECT resource_id FROM t_console_resource WHERE resource_code='OPERATIONS_PLATFORM_CONFIGURATION' AND channel='OPERATIONS' AND status='ACTIVE');
SET @plt_api_gateway_read=(SELECT permission_id FROM t_permission WHERE permission_code='GATEWAY_CONFIG_READ' AND status='ACTIVE');
SET @plt_api_route_list=(SELECT route_id FROM t_gateway_route WHERE route_code='operations-gateway-route-list' AND channel='OPERATIONS');
SET @plt_api_audit_list=(SELECT route_id FROM t_gateway_route WHERE route_code='operations-audit-list' AND channel='OPERATIONS');
SELECT @plt_api_operator operator_id,@plt_api_target target_id,@plt_api_admin admin_role_id,@plt_api_parent parent_id,@plt_api_gateway_read permission_id,@plt_api_route_list route_list_id,@plt_api_audit_list audit_list_id;
-- All must identify the reviewed objects. Stop before START if not.
START TRANSACTION;
SELECT route_id,route_code,version,status FROM t_gateway_route
 WHERE route_id IN (@plt_api_route_list,@plt_api_audit_list) ORDER BY route_id FOR UPDATE;
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000051101, 'API_CREDENTIAL_READ', 'API_CREDENTIAL', 'READ', 'LOW', 'ACTIVE', 1, @plt_api_time, @plt_api_time),
  (900000000000051102, 'API_CREDENTIAL_REVOKE', 'API_CREDENTIAL', 'REVOKE', 'CRITICAL', 'ACTIVE', 1, @plt_api_time, @plt_api_time),
  (900000000000051103, 'API_SCOPE_READ', 'API_SCOPE', 'READ', 'LOW', 'ACTIVE', 1, @plt_api_time, @plt_api_time),
  (900000000000051104, 'API_SCOPE_MANAGE', 'API_SCOPE', 'MANAGE', 'HIGH', 'ACTIVE', 1, @plt_api_time, @plt_api_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000051201, @plt_api_admin, 900000000000051101, 'REVOKED', 1, @plt_api_time, @plt_api_time),
  (900000000000051202, @plt_api_admin, 900000000000051102, 'REVOKED', 1, @plt_api_time, @plt_api_time),
  (900000000000051203, @plt_api_admin, 900000000000051103, 'REVOKED', 1, @plt_api_time, @plt_api_time),
  (900000000000051204, @plt_api_admin, 900000000000051104, 'REVOKED', 1, @plt_api_time, @plt_api_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000051301, 'OPERATIONS_API_CREDENTIALS', 'OPERATIONS', 'MENU', @plt_api_parent, 'API 凭证', '/api-credentials', 'OPERATIONS_API_CREDENTIALS', 900000000000051101, 40, 'DISABLED', 1, @plt_api_time, @plt_api_time),
  (900000000000051302, 'OPERATIONS_API_CREDENTIAL_REVOKE', 'OPERATIONS', 'BUTTON', 900000000000051301, '紧急撤销', NULL, NULL, 900000000000051102, 10, 'DISABLED', 1, @plt_api_time, @plt_api_time),
  (900000000000051303, 'OPERATIONS_API_SCOPES', 'OPERATIONS', 'MENU', @plt_api_parent, 'API Scope', '/api-scopes', 'OPERATIONS_API_SCOPES', 900000000000051103, 50, 'DISABLED', 1, @plt_api_time, @plt_api_time),
  (900000000000051304, 'OPERATIONS_API_SCOPE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000051303, '维护 Scope', NULL, NULL, 900000000000051104, 10, 'DISABLED', 1, @plt_api_time, @plt_api_time);

INSERT INTO t_gateway_route (route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000051001, 'operations-api-credential-list', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials', '/api-credential-admin/v1/credentials', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051002, 'operations-api-credential-detail', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials/{credentialId}', '/api-credential-admin/v1/credentials/{credentialId}', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051003, 'operations-api-credential-scopes', 'OPERATIONS', 'GET', '/api/operations/api-credential-admin/v1/credentials/{credentialId}/scopes', '/api-credential-admin/v1/credentials/{credentialId}/scopes', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051101, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051004, 'operations-api-credential-revoke', 'OPERATIONS', 'POST', '/api/operations/api-credential-admin/v1/credentials/{credentialId}/revoke', '/api-credential-admin/v1/credentials/{credentialId}/revoke', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051102, NULL, 'DROP', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051005, 'operations-api-scope-list', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scopes', '/api-scope-admin/v1/scopes', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051006, 'operations-api-scope-detail', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scopes/{scopeCode}', '/api-scope-admin/v1/scopes/{scopeCode}', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051103, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051007, 'operations-api-scope-create', 'OPERATIONS', 'POST', '/api/operations/api-scope-admin/v1/scopes', '/api-scope-admin/v1/scopes', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051104, NULL, 'DROP', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051008, 'operations-api-scope-update', 'OPERATIONS', 'PUT', '/api/operations/api-scope-admin/v1/scopes/{scopeCode}', '/api-scope-admin/v1/scopes/{scopeCode}', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000051104, NULL, 'DROP', 'NONE', 8192, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time),
  (900000000000051009, 'operations-api-scope-options', 'OPERATIONS', 'GET', '/api/operations/api-scope-admin/v1/scope-options', '/api-scope-admin/v1/scope-options', @plt_api_target, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', @plt_api_gateway_read, NULL, 'DROP', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @plt_api_operator, @plt_api_operator, @plt_api_time, @plt_api_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000052001, 900000000000051001, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052002, 900000000000051001, 'origin', 'FORWARD_REQUEST'),
  (900000000000052003, 900000000000051002, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052004, 900000000000051002, 'origin', 'FORWARD_REQUEST'),
  (900000000000052005, 900000000000051003, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052006, 900000000000051003, 'origin', 'FORWARD_REQUEST'),
  (900000000000052007, 900000000000051004, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052008, 900000000000051004, 'origin', 'FORWARD_REQUEST'),
  (900000000000052009, 900000000000051004, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052010, 900000000000051004, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052011, 900000000000051004, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052012, 900000000000051004, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052013, 900000000000051005, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052014, 900000000000051005, 'origin', 'FORWARD_REQUEST'),
  (900000000000052015, 900000000000051006, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052016, 900000000000051006, 'origin', 'FORWARD_REQUEST'),
  (900000000000052017, 900000000000051007, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052018, 900000000000051007, 'origin', 'FORWARD_REQUEST'),
  (900000000000052019, 900000000000051007, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052020, 900000000000051007, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052021, 900000000000051007, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052022, 900000000000051007, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052023, 900000000000051008, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052024, 900000000000051008, 'origin', 'FORWARD_REQUEST'),
  (900000000000052025, 900000000000051008, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000052026, 900000000000051008, 'content-type', 'FORWARD_REQUEST'),
  (900000000000052027, 900000000000051008, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000052028, 900000000000051008, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000052029, 900000000000051009, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000052030, 900000000000051009, 'origin', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000053001, 900000000000051001, 'page'),
  (900000000000053002, 900000000000051001, 'size'),
  (900000000000053003, 900000000000051001, 'clientId'),
  (900000000000053004, 900000000000051001, 'clientName'),
  (900000000000053005, 900000000000051001, 'credentialId'),
  (900000000000053006, 900000000000051001, 'keyPrefix'),
  (900000000000053007, 900000000000051001, 'status'),
  (900000000000053008, 900000000000051001, 'scopeCode'),
  (900000000000053009, 900000000000051003, 'page'),
  (900000000000053010, 900000000000051003, 'size'),
  (900000000000053011, 900000000000051005, 'page'),
  (900000000000053012, 900000000000051005, 'size'),
  (900000000000053013, 900000000000051005, 'keyword'),
  (900000000000053014, 900000000000051006, 'referenceKind'),
  (900000000000053015, 900000000000051006, 'page'),
  (900000000000053016, 900000000000051006, 'size'),
  (900000000000053017, 900000000000051009, 'page'),
  (900000000000053018, 900000000000051009, 'size'),
  (900000000000053019, 900000000000051009, 'keyword'),
  (900000000000053020, @plt_api_route_list, 'requiredScopeCode'),
  (900000000000053021, @plt_api_audit_list, 'resourceType'),
  (900000000000053022, @plt_api_audit_list, 'resourceId');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000054001, 900000000000051001, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054002, 900000000000051001, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054003, 900000000000051001, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054004, 900000000000051001, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054005, 900000000000051001, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054006, 900000000000051001, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054007, 900000000000051002, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054008, 900000000000051002, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054009, 900000000000051002, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054010, 900000000000051002, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054011, 900000000000051002, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054012, 900000000000051002, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054013, 900000000000051003, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054014, 900000000000051003, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054015, 900000000000051003, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054016, 900000000000051003, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054017, 900000000000051003, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054018, 900000000000051003, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054019, 900000000000051004, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054020, 900000000000051004, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054021, 900000000000051004, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054022, 900000000000051004, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054023, 900000000000051004, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054024, 900000000000051004, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054025, 900000000000051005, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054026, 900000000000051005, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054027, 900000000000051005, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054028, 900000000000051005, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054029, 900000000000051005, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054030, 900000000000051005, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054031, 900000000000051006, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054032, 900000000000051006, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054033, 900000000000051006, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054034, 900000000000051006, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054035, 900000000000051006, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054036, 900000000000051006, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054037, 900000000000051007, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054038, 900000000000051007, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054039, 900000000000051007, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054040, 900000000000051007, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054041, 900000000000051007, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054042, 900000000000051007, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054043, 900000000000051008, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054044, 900000000000051008, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054045, 900000000000051008, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054046, 900000000000051008, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054047, 900000000000051008, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054048, 900000000000051008, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000054049, 900000000000051009, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000054050, 900000000000051009, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000054051, 900000000000051009, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000054052, 900000000000051009, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000054053, 900000000000051009, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000054054, 900000000000051009, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

UPDATE t_gateway_route SET version=version+1,updated_by=@plt_api_operator,update_time=@plt_api_time
 WHERE route_id IN (@plt_api_route_list,@plt_api_audit_list);
SELECT ROW_COUNT() AS expected_existing_route_updates_2;
SELECT COUNT(*) AS expected_new_routes_9 FROM t_gateway_route WHERE route_id BETWEEN 900000000000051001 AND 900000000000051009 AND status='DISABLED';
-- Inspect all row counts, target identities and registered child shapes from db/registration-counts.json.
-- COMMIT only if all match; otherwise ROLLBACK. Closing this connection without COMMIT rolls back DML.
