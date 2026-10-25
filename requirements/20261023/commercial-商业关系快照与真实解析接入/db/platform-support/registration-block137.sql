-- 草稿（未执行）：Platform 注册块 137（商业关系快照与真实解析接入：主体资格、经营主体、Local Partner 运营页），只在 mor_platform
-- 执行，客户端须 utf8mb4。
-- 来源：requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md 第 3.3.5、3.4.4 节；已追加到
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾块 137（2026-10-24），内容逐字相同。
-- 前置：本需求的 client-master（ClientMasterEntityAdminApi）、compliance（QualificationAdminApi）与 operations-console-bff
-- （/entity-admin/v1、/qualification-admin/v1）新镜像已发布。执行后为休眠状态，启用见 test-activate.sql。
-- 预期插入：permission 6、role_permission 6、console_resource 6、gateway_route 11、route_header 38、query_param 17、
-- guard_rule 66，共 150 行。执行前核对：SELECT COUNT(*) 各表 ID 900000000000137001—900000000000137999 均为 0。
-- ---------------------------------------------------------------------------
-- Relationship snapshot and real resolution (2026-10-24, requirements/20261023/commercial-商业关系快照与真实解析接入;
-- technique.md sections 3.3.5 and 3.4.4).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus/buttons/routes DISABLED;
-- activate them in a separate reviewed change (requirements/20261023/commercial-商业关系快照与真实解析接入/db/platform-support/
-- test-activate.sql).
-- BFF routes forward to operations-console-bff on the same path (/entity-admin/v1/..., /qualification-admin/v1/...), which
-- calls client-master /client-master-admin/v1 and compliance /compliance-admin/v1 with the Gateway actor.
-- Permissions (resource, action, risk): QUALIFICATION_READ (QUALIFICATION, READ, LOW: subject lookup, qualification list and
-- history), QUALIFICATION_MANAGE (QUALIFICATION, MANAGE, HIGH: manual approval decision and revocation),
-- OPERATING_ENTITY_READ (OPERATING_ENTITY, READ, LOW: list and detail), OPERATING_ENTITY_MANAGE (OPERATING_ENTITY, MANAGE,
-- HIGH: acquiring qualification), LOCAL_PARTNER_READ (LOCAL_PARTNER, READ, LOW: list and detail), LOCAL_PARTNER_MANAGE
-- (LOCAL_PARTNER, MANAGE, HIGH: partner roles).
-- Menus: 主体资格 OPERATIONS_QUALIFICATIONS under 风控管理 (900000000000000565), /compliance/qualifications, sort 90 after
-- 评估查询 (80); 经营主体 OPERATIONS_OPERATING_ENTITIES and Local Partner OPERATIONS_LOCAL_PARTNERS under 商务
-- (900000000000095301), /master-data/operating-entities sort 60 and /master-data/local-partners sort 70, after 变更历史 (50).
-- Buttons (sort 10 under their menu): 登记与撤销 OPERATIONS_QUALIFICATIONS_MANAGE (QUALIFICATION_MANAGE), 维护收单资质
-- OPERATIONS_OPERATING_ENTITIES_MANAGE (OPERATING_ENTITY_MANAGE), 维护角色 OPERATIONS_LOCAL_PARTNERS_MANAGE
-- (LOCAL_PARTNER_MANAGE).
-- Routes: 11 (B1-B11). Headers and guard rules copy block 134/136: GET authorization + origin, body limit 0, read limits
-- 30/min per principal; PUT/POST add content-type and idempotency-key, body limit 65536, write limits 10/min per principal;
-- every route also SHARED_ROUTE 300/min + 50 concurrent, CLIENT_IP 30/min + 3 concurrent, principal 2 concurrent.
-- Query whitelist: operating entities status, sourceType, country, page, size; local partners status, role, page, size;
-- subject lookup roleType, subjectRef; qualifications roleType, roleId, qualificationType, status, page, size.
-- Rows: permission 6, role_permission 6, console_resource 6, gateway_route 11, route_header 38, query_param 17,
-- guard_rule 66 (150). ID block 137: 1371xx permission, 1372xx role_permission, 1373xx console_resource, 1374xx route,
-- 1375xx route_header, 1376xx query_param, 1377xx guard_rule.
-- Before applying, re-check on the live mor_platform that no row in 900000000000137001-900000000000137999 exists and that
-- the permission codes, resource codes, route codes and (method, external path) pairs above are unused.
-- Rollback: delete the rows by the 900000000000137001-900000000000137999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource (buttons before menus), role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @relsnap_console_time = UTC_TIMESTAMP(6);
SET @relsnap_console_actor_id = 1;
SET @relsnap_console_target_id = 900000000000000001;
SET @relsnap_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000137101, 'QUALIFICATION_READ', 'QUALIFICATION', 'READ', 'LOW', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137102, 'QUALIFICATION_MANAGE', 'QUALIFICATION', 'MANAGE', 'HIGH', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137103, 'OPERATING_ENTITY_READ', 'OPERATING_ENTITY', 'READ', 'LOW', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137104, 'OPERATING_ENTITY_MANAGE', 'OPERATING_ENTITY', 'MANAGE', 'HIGH', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137105, 'LOCAL_PARTNER_READ', 'LOCAL_PARTNER', 'READ', 'LOW', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137106, 'LOCAL_PARTNER_MANAGE', 'LOCAL_PARTNER', 'MANAGE', 'HIGH', 'ACTIVE', 1, @relsnap_console_time, @relsnap_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000137201, @relsnap_console_role_id, 900000000000137101, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137202, @relsnap_console_role_id, 900000000000137102, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137203, @relsnap_console_role_id, 900000000000137103, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137204, @relsnap_console_role_id, 900000000000137104, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137205, @relsnap_console_role_id, 900000000000137105, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137206, @relsnap_console_role_id, 900000000000137106, 'REVOKED', 1, @relsnap_console_time, @relsnap_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000137301, 'OPERATIONS_QUALIFICATIONS', 'OPERATIONS', 'MENU', 900000000000000565, '主体资格', '/compliance/qualifications', 'OPERATIONS_QUALIFICATIONS', 900000000000137101, 90, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137302, 'OPERATIONS_OPERATING_ENTITIES', 'OPERATIONS', 'MENU', 900000000000095301, '经营主体', '/master-data/operating-entities', 'OPERATIONS_OPERATING_ENTITIES', 900000000000137103, 60, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137303, 'OPERATIONS_LOCAL_PARTNERS', 'OPERATIONS', 'MENU', 900000000000095301, 'Local Partner', '/master-data/local-partners', 'OPERATIONS_LOCAL_PARTNERS', 900000000000137105, 70, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137304, 'OPERATIONS_QUALIFICATIONS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000137301, '登记与撤销', NULL, NULL, 900000000000137102, 10, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137305, 'OPERATIONS_OPERATING_ENTITIES_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000137302, '维护收单资质', NULL, NULL, 900000000000137104, 10, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time),
  (900000000000137306, 'OPERATIONS_LOCAL_PARTNERS_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000137303, '维护角色', NULL, NULL, 900000000000137106, 10, 'DISABLED', 1, @relsnap_console_time, @relsnap_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000137401, 'operations-operating-entity-list', 'OPERATIONS', 'GET', '/api/operations/entity-admin/v1/operating-entities', '/entity-admin/v1/operating-entities', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137402, 'operations-operating-entity-detail', 'OPERATIONS', 'GET', '/api/operations/entity-admin/v1/operating-entities/{entityNo}', '/entity-admin/v1/operating-entities/{entityNo}', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137103, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137403, 'operations-operating-entity-qualification-update', 'OPERATIONS', 'PUT', '/api/operations/entity-admin/v1/operating-entities/{entityNo}/acquiring-qualification', '/entity-admin/v1/operating-entities/{entityNo}/acquiring-qualification', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137404, 'operations-local-partner-list', 'OPERATIONS', 'GET', '/api/operations/entity-admin/v1/local-partners', '/entity-admin/v1/local-partners', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137105, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137405, 'operations-local-partner-detail', 'OPERATIONS', 'GET', '/api/operations/entity-admin/v1/local-partners/{localPartnerId}', '/entity-admin/v1/local-partners/{localPartnerId}', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137105, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137406, 'operations-local-partner-roles-update', 'OPERATIONS', 'PUT', '/api/operations/entity-admin/v1/local-partners/{localPartnerId}/roles', '/entity-admin/v1/local-partners/{localPartnerId}/roles', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137106, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137407, 'operations-qualification-subject-lookup', 'OPERATIONS', 'GET', '/api/operations/qualification-admin/v1/subjects', '/qualification-admin/v1/subjects', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137408, 'operations-qualification-list', 'OPERATIONS', 'GET', '/api/operations/qualification-admin/v1/qualifications', '/qualification-admin/v1/qualifications', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137409, 'operations-qualification-history', 'OPERATIONS', 'GET', '/api/operations/qualification-admin/v1/qualifications/{qualificationId}/history', '/qualification-admin/v1/qualifications/{qualificationId}/history', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137410, 'operations-qualification-manual-decision', 'OPERATIONS', 'POST', '/api/operations/qualification-admin/v1/qualifications/manual-decisions', '/qualification-admin/v1/qualifications/manual-decisions', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time),
  (900000000000137411, 'operations-qualification-revocation', 'OPERATIONS', 'POST', '/api/operations/qualification-admin/v1/qualifications/{qualificationId}/revocation', '/qualification-admin/v1/qualifications/{qualificationId}/revocation', @relsnap_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000137102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @relsnap_console_actor_id, @relsnap_console_actor_id, @relsnap_console_time, @relsnap_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000137501, 900000000000137401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137502, 900000000000137401, 'origin', 'FORWARD_REQUEST'),
  (900000000000137503, 900000000000137402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137504, 900000000000137402, 'origin', 'FORWARD_REQUEST'),
  (900000000000137505, 900000000000137403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137506, 900000000000137403, 'origin', 'FORWARD_REQUEST'),
  (900000000000137507, 900000000000137403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000137508, 900000000000137403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000137509, 900000000000137403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000137510, 900000000000137403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000137511, 900000000000137404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137512, 900000000000137404, 'origin', 'FORWARD_REQUEST'),
  (900000000000137513, 900000000000137405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137514, 900000000000137405, 'origin', 'FORWARD_REQUEST'),
  (900000000000137515, 900000000000137406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137516, 900000000000137406, 'origin', 'FORWARD_REQUEST'),
  (900000000000137517, 900000000000137406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000137518, 900000000000137406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000137519, 900000000000137406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000137520, 900000000000137406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000137521, 900000000000137407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137522, 900000000000137407, 'origin', 'FORWARD_REQUEST'),
  (900000000000137523, 900000000000137408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137524, 900000000000137408, 'origin', 'FORWARD_REQUEST'),
  (900000000000137525, 900000000000137409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137526, 900000000000137409, 'origin', 'FORWARD_REQUEST'),
  (900000000000137527, 900000000000137410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137528, 900000000000137410, 'origin', 'FORWARD_REQUEST'),
  (900000000000137529, 900000000000137410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000137530, 900000000000137410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000137531, 900000000000137410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000137532, 900000000000137410, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000137533, 900000000000137411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000137534, 900000000000137411, 'origin', 'FORWARD_REQUEST'),
  (900000000000137535, 900000000000137411, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000137536, 900000000000137411, 'content-type', 'FORWARD_REQUEST'),
  (900000000000137537, 900000000000137411, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000137538, 900000000000137411, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000137601, 900000000000137401, 'status'),
  (900000000000137602, 900000000000137401, 'sourceType'),
  (900000000000137603, 900000000000137401, 'country'),
  (900000000000137604, 900000000000137401, 'page'),
  (900000000000137605, 900000000000137401, 'size'),
  (900000000000137606, 900000000000137404, 'status'),
  (900000000000137607, 900000000000137404, 'role'),
  (900000000000137608, 900000000000137404, 'page'),
  (900000000000137609, 900000000000137404, 'size'),
  (900000000000137610, 900000000000137407, 'roleType'),
  (900000000000137611, 900000000000137407, 'subjectRef'),
  (900000000000137612, 900000000000137408, 'roleType'),
  (900000000000137613, 900000000000137408, 'roleId'),
  (900000000000137614, 900000000000137408, 'qualificationType'),
  (900000000000137615, 900000000000137408, 'status'),
  (900000000000137616, 900000000000137408, 'page'),
  (900000000000137617, 900000000000137408, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000137701, 900000000000137401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137702, 900000000000137401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137703, 900000000000137401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137704, 900000000000137401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137705, 900000000000137401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137706, 900000000000137401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137707, 900000000000137402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137708, 900000000000137402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137709, 900000000000137402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137710, 900000000000137402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137711, 900000000000137402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137712, 900000000000137402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137713, 900000000000137403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137714, 900000000000137403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137715, 900000000000137403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137716, 900000000000137403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137717, 900000000000137403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000137718, 900000000000137403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137719, 900000000000137404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137720, 900000000000137404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137721, 900000000000137404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137722, 900000000000137404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137723, 900000000000137404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137724, 900000000000137404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137725, 900000000000137405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137726, 900000000000137405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137727, 900000000000137405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137728, 900000000000137405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137729, 900000000000137405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137730, 900000000000137405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137731, 900000000000137406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137732, 900000000000137406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137733, 900000000000137406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137734, 900000000000137406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137735, 900000000000137406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000137736, 900000000000137406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137737, 900000000000137407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137738, 900000000000137407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137739, 900000000000137407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137740, 900000000000137407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137741, 900000000000137407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137742, 900000000000137407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137743, 900000000000137408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137744, 900000000000137408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137745, 900000000000137408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137746, 900000000000137408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137747, 900000000000137408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137748, 900000000000137408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137749, 900000000000137409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137750, 900000000000137409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137751, 900000000000137409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137752, 900000000000137409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137753, 900000000000137409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000137754, 900000000000137409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137755, 900000000000137410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137756, 900000000000137410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137757, 900000000000137410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137758, 900000000000137410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137759, 900000000000137410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000137760, 900000000000137410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000137761, 900000000000137411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000137762, 900000000000137411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000137763, 900000000000137411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000137764, 900000000000137411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000137765, 900000000000137411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000137766, 900000000000137411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
