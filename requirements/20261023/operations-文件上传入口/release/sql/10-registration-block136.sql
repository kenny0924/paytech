-- 草稿（未执行）：Platform 注册块 136（运营控制台文件上传入口），只在 mor_platform 执行，客户端须 utf8mb4。
-- 来源：paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾块 136，内容逐字相同。
-- 前置：本需求的 platform-support 新镜像（InternalFileApi 写入限制与调用方文件列表）已发布。执行后为休眠状态，启用见
-- test-activate.sql。
-- 预期插入：permission 2、role_permission 2、console_resource 2、gateway_route 5、route_header 18、query_param 5、
-- guard_rule 30，共 64 行。执行前核对：SELECT COUNT(*) 各表 ID 900000000000136001—900000000000136999 均为 0。
-- ---------------------------------------------------------------------------
-- Operations file upload entry (2026-10-24, requirements/20261023/operations-文件上传入口, R1, R7; technique.md
-- sections 6 and 8).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Requires the Platform code of the same requirement (InternalFileApi upload-constraints and caller file list) for the
-- operations-console-bff code it activates.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menu/button/routes DISABLED;
-- activate them in a separate reviewed change (requirements/20261023/operations-文件上传入口/db/platform-support/test-activate.sql).
-- BFF routes forward to operations-console-bff on the same path (/file-upload/v1/...), which calls Platform InternalFileApi
-- (/internal/files/v1/upload-constraints, /files, /files/{fileId}, /uploads, /uploads/{uploadId}/completion) as caller
-- operations-console-bff with reasonCode OPERATIONS_PUBLIC_ASSET and accessLevel PUBLIC; the uploader is the Gateway actor.
-- Permissions: FILE_UPLOAD_READ (resource FILE_UPLOAD, READ, LOW: upload constraints, recent uploads and the status of
-- one upload) and FILE_UPLOAD_CREATE (resource FILE_UPLOAD, CREATE, HIGH: start and complete a public upload; the content
-- becomes anonymously readable once its check passes).
-- Menu 文件上传 OPERATIONS_FILE_UPLOAD under 平台配置 (900000000000000562), /file-upload, sort 86 right after 文件记录 (85,
-- block 134) and before 卡 BIN 库 (90, block 132). Button 上传 OPERATIONS_FILE_UPLOAD_CREATE (FILE_UPLOAD_CREATE) shows
-- the upload area.
-- Routes: 5. Headers and guard rules copy block 134: GET authorization + origin, read limits 30/min per principal; POST
-- adds content-type and idempotency-key, write limits 10/min per principal; every route also SHARED_ROUTE 300/min + 50
-- concurrent and CLIENT_IP 30/min + 3 concurrent. The completion POST carries no body (body limit 0) but forwards
-- content-type like the block 134 download, because operations-console-bff requires a JSON content type on every write.
-- Query whitelist: recent uploads createdFrom, createdTo, mine, page, size.
-- Rows: permission 2, role_permission 2, console_resource 2, gateway_route 5, route_header 18, query_param 5,
-- guard_rule 30 (64). ID block 136: 1361xx permission, 1362xx role_permission, 1363xx console_resource, 1364xx route,
-- 1365xx route_header, 1366xx query_param, 1367xx guard_rule.
-- Before applying, re-check on the live mor_platform that no row in 900000000000136001-900000000000136999 exists and that
-- the permission codes, resource codes, route codes and (method, external path) pairs above are unused.
-- Rollback: delete the rows by the 900000000000136001-900000000000136999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @file_upload_console_time = UTC_TIMESTAMP(6);
SET @file_upload_console_actor_id = 1;
SET @file_upload_console_target_id = 900000000000000001;
SET @file_upload_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000136101, 'FILE_UPLOAD_READ', 'FILE_UPLOAD', 'READ', 'LOW', 'ACTIVE', 1, @file_upload_console_time, @file_upload_console_time),
  (900000000000136102, 'FILE_UPLOAD_CREATE', 'FILE_UPLOAD', 'CREATE', 'HIGH', 'ACTIVE', 1, @file_upload_console_time, @file_upload_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000136201, @file_upload_console_role_id, 900000000000136101, 'REVOKED', 1, @file_upload_console_time, @file_upload_console_time),
  (900000000000136202, @file_upload_console_role_id, 900000000000136102, 'REVOKED', 1, @file_upload_console_time, @file_upload_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000136301, 'OPERATIONS_FILE_UPLOAD', 'OPERATIONS', 'MENU', 900000000000000562, '文件上传', '/file-upload', 'OPERATIONS_FILE_UPLOAD', 900000000000136101, 86, 'DISABLED', 1, @file_upload_console_time, @file_upload_console_time),
  (900000000000136302, 'OPERATIONS_FILE_UPLOAD_CREATE', 'OPERATIONS', 'BUTTON', 900000000000136301, '上传', NULL, NULL, 900000000000136102, 10, 'DISABLED', 1, @file_upload_console_time, @file_upload_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000136401, 'operations-file-upload-constraints', 'OPERATIONS', 'GET', '/api/operations/file-upload/v1/constraints', '/file-upload/v1/constraints', @file_upload_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000136101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_upload_console_actor_id, @file_upload_console_actor_id, @file_upload_console_time, @file_upload_console_time),
  (900000000000136402, 'operations-file-upload-list', 'OPERATIONS', 'GET', '/api/operations/file-upload/v1/files', '/file-upload/v1/files', @file_upload_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000136101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_upload_console_actor_id, @file_upload_console_actor_id, @file_upload_console_time, @file_upload_console_time),
  (900000000000136403, 'operations-file-upload-detail', 'OPERATIONS', 'GET', '/api/operations/file-upload/v1/files/{fileId}', '/file-upload/v1/files/{fileId}', @file_upload_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000136101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_upload_console_actor_id, @file_upload_console_actor_id, @file_upload_console_time, @file_upload_console_time),
  (900000000000136404, 'operations-file-upload-start', 'OPERATIONS', 'POST', '/api/operations/file-upload/v1/uploads', '/file-upload/v1/uploads', @file_upload_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000136102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_upload_console_actor_id, @file_upload_console_actor_id, @file_upload_console_time, @file_upload_console_time),
  (900000000000136405, 'operations-file-upload-complete', 'OPERATIONS', 'POST', '/api/operations/file-upload/v1/uploads/{uploadId}/completion', '/file-upload/v1/uploads/{uploadId}/completion', @file_upload_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000136102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_upload_console_actor_id, @file_upload_console_actor_id, @file_upload_console_time, @file_upload_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000136501, 900000000000136401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000136502, 900000000000136401, 'origin', 'FORWARD_REQUEST'),
  (900000000000136503, 900000000000136402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000136504, 900000000000136402, 'origin', 'FORWARD_REQUEST'),
  (900000000000136505, 900000000000136403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000136506, 900000000000136403, 'origin', 'FORWARD_REQUEST'),
  (900000000000136507, 900000000000136404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000136508, 900000000000136404, 'origin', 'FORWARD_REQUEST'),
  (900000000000136509, 900000000000136404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000136510, 900000000000136404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000136511, 900000000000136404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000136512, 900000000000136404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000136513, 900000000000136405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000136514, 900000000000136405, 'origin', 'FORWARD_REQUEST'),
  (900000000000136515, 900000000000136405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000136516, 900000000000136405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000136517, 900000000000136405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000136518, 900000000000136405, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000136601, 900000000000136402, 'createdFrom'),
  (900000000000136602, 900000000000136402, 'createdTo'),
  (900000000000136603, 900000000000136402, 'mine'),
  (900000000000136604, 900000000000136402, 'page'),
  (900000000000136605, 900000000000136402, 'size');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000136701, 900000000000136401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000136702, 900000000000136401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000136703, 900000000000136401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000136704, 900000000000136401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000136705, 900000000000136401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000136706, 900000000000136401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000136707, 900000000000136402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000136708, 900000000000136402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000136709, 900000000000136402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000136710, 900000000000136402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000136711, 900000000000136402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000136712, 900000000000136402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000136713, 900000000000136403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000136714, 900000000000136403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000136715, 900000000000136403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000136716, 900000000000136403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000136717, 900000000000136403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000136718, 900000000000136403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000136719, 900000000000136404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000136720, 900000000000136404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000136721, 900000000000136404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000136722, 900000000000136404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000136723, 900000000000136404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000136724, 900000000000136404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000136725, 900000000000136405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000136726, 900000000000136405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000136727, 900000000000136405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000136728, 900000000000136405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000136729, 900000000000136405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000136730, 900000000000136405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
