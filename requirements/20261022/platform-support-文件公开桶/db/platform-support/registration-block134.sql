-- 草稿（未执行）：Platform 注册块 134（文件记录 + 存储管理新增路由），只在 mor_platform 执行，客户端须 utf8mb4。
-- 来源：paytech/platform-support/docs/database/sql/platform-support-initial-data.sql 末尾块 134，内容逐字相同。
-- 前置：本需求 Schema 变更与 block130-route-path.sql 已执行；块 130 已存在。执行后为休眠状态，启用见 activate-block134.sql。
-- 预期插入：permission 2、role_permission 2、console_resource 2、gateway_route 12、route_header 56、query_param 11、
-- guard_rule 72，共 157 行。执行前核对：SELECT COUNT(*) 各表 ID 900000000000134001—900000000000134999 均为 0。
-- ---------------------------------------------------------------------------
-- Operations file records and file storage public buckets (2026-10-22, requirements/20261022/platform-support-文件公开桶,
-- R7, R9-R11; contract tasks/common.md section 3).
-- Self-contained block: on an environment already holding block 130 (file storage console), apply ONLY this block.
-- Requires the public-bucket schema change of the same requirement (t_file_version and the access_level columns) for the
-- Platform code it activates.
-- Dormant like block 130: permissions ACTIVE, administrator grants REVOKED, menu/button/routes DISABLED; activate them in a
-- separate reviewed change (requirements/20261022/platform-support-文件公开桶/db/platform-support/activate-block134.sql).
-- BFF routes forward to operations-console-bff on the same path (/storage-admin/v1/**), which calls
-- FileStorageManagementApi on platform-support-service; operator identity comes from the Gateway actor headers.
-- Permissions: FILE_RECORD_READ (resource FILE_RECORD, READ, LOW: file record list and detail) and FILE_RECORD_DOWNLOAD
-- (resource FILE_RECORD, DOWNLOAD, HIGH: authorize a download of a CURRENT or SUPERSEDED version). t_permission.action_code
-- is a controlled, non-fixed code; READ is already taken for FILE_RECORD by uk_resource_action and no existing action
-- code (EXECUTE, OPERATE, HANDLE, ...) names content retrieval, so DOWNLOAD is used. File contents may hold onboarding
-- personal data, hence HIGH.
-- Menu 文件记录 OPERATIONS_FILE_RECORDS under 平台配置 (900000000000000562), /file-records, sort 85 between 存储管理 (80) and
-- 卡 BIN 库 (90, block 132); the record detail is the frontend sub-route ?file=<id> and needs no registration. Button 下载
-- OPERATIONS_FILE_RECORD_DOWNLOAD (FILE_RECORD_DOWNLOAD) gates the version download.
-- Routes: 12. Bucket update 1 and scan policies 8 reuse FILE_STORAGE_READ / FILE_STORAGE_MANAGE (900000000000130101 /
-- 900000000000130102); file records 3 use the new permissions. Headers and guard rules copy block 130: GET authorization +
-- origin, read limits 30/min per principal; writes add content-type and idempotency-key, write limits 10/min per
-- principal. The revision discard DELETE carries an expectedVersion/reason body like the block 130 route delete; the
-- download POST carries no body (body limit 0) but forwards content-type like the block 130 bucket check, because
-- operations-console-bff requires a JSON content type on every write. Query whitelists: file list createdFrom, createdTo,
-- fileId, sourceSystem, reasonCode, accessLevel, scanStatus, hasPendingReplacement, page, size; download versionNo.
-- Rows: permission 2, role_permission 2, console_resource 2, gateway_route 12, route_header 56, query_param 11,
-- guard_rule 72 (157). ID block 134: 1341xx permission, 1342xx role_permission, 1343xx console_resource, 1344xx route,
-- 1345xx route_header, 1346xx query_param, 1347xx guard_rule.
-- Before applying, re-check on the live mor_platform that no row in 900000000000134001-900000000000134999 exists and that
-- the permission codes, resource codes, route codes and (method, external path) pairs above are unused.
-- Rollback: delete the rows by the 900000000000134001-900000000000134999 IDs below (children first: guard_rule, query_param,
-- route_header, gateway_route, console_resource, role_permission, permission).
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @file_record_console_time = UTC_TIMESTAMP(6);
SET @file_record_console_actor_id = 1;
SET @file_record_console_target_id = 900000000000000001;
SET @file_record_console_role_id = 900000000000000510;

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000134101, 'FILE_RECORD_READ', 'FILE_RECORD', 'READ', 'LOW', 'ACTIVE', 1, @file_record_console_time, @file_record_console_time),
  (900000000000134102, 'FILE_RECORD_DOWNLOAD', 'FILE_RECORD', 'DOWNLOAD', 'HIGH', 'ACTIVE', 1, @file_record_console_time, @file_record_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000134201, @file_record_console_role_id, 900000000000134101, 'REVOKED', 1, @file_record_console_time, @file_record_console_time),
  (900000000000134202, @file_record_console_role_id, 900000000000134102, 'REVOKED', 1, @file_record_console_time, @file_record_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000134301, 'OPERATIONS_FILE_RECORDS', 'OPERATIONS', 'MENU', 900000000000000562, '文件记录', '/file-records', 'OPERATIONS_FILE_RECORDS', 900000000000134101, 85, 'DISABLED', 1, @file_record_console_time, @file_record_console_time),
  (900000000000134302, 'OPERATIONS_FILE_RECORD_DOWNLOAD', 'OPERATIONS', 'BUTTON', 900000000000134301, '下载', NULL, NULL, 900000000000134102, 10, 'DISABLED', 1, @file_record_console_time, @file_record_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000134401, 'operations-file-storage-bucket-update', 'OPERATIONS', 'PUT', '/api/operations/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}', '/storage-admin/v1/instances/{instanceCode}/buckets/{bucketName}', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134402, 'operations-file-scan-policy-list', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/scan-policies', '/storage-admin/v1/scan-policies', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134403, 'operations-file-scan-policy-detail', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/scan-policies/{policyCode}', '/storage-admin/v1/scan-policies/{policyCode}', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134404, 'operations-file-scan-policy-create', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/scan-policies', '/storage-admin/v1/scan-policies', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134405, 'operations-file-scan-policy-revision-create', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions', '/storage-admin/v1/scan-policies/{policyCode}/revisions', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134406, 'operations-file-scan-policy-revision-update', 'OPERATIONS', 'PUT', '/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}', '/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134407, 'operations-file-scan-policy-revision-discard', 'OPERATIONS', 'DELETE', '/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}', '/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134408, 'operations-file-scan-policy-activate', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}/activation', '/storage-admin/v1/scan-policies/{policyCode}/revisions/{revisionNo}/activation', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134409, 'operations-file-scan-policy-retire', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/scan-policies/{policyCode}/retirement', '/storage-admin/v1/scan-policies/{policyCode}/retirement', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000130102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134410, 'operations-file-record-list', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/files', '/storage-admin/v1/files', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000134101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134411, 'operations-file-record-detail', 'OPERATIONS', 'GET', '/api/operations/storage-admin/v1/files/{fileId}', '/storage-admin/v1/files/{fileId}', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000134101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time),
  (900000000000134412, 'operations-file-record-download', 'OPERATIONS', 'POST', '/api/operations/storage-admin/v1/files/{fileId}/download', '/storage-admin/v1/files/{fileId}/download', @file_record_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000134102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @file_record_console_actor_id, @file_record_console_actor_id, @file_record_console_time, @file_record_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000134501, 900000000000134401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134502, 900000000000134401, 'origin', 'FORWARD_REQUEST'),
  (900000000000134503, 900000000000134401, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134504, 900000000000134401, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134505, 900000000000134401, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134506, 900000000000134401, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134507, 900000000000134402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134508, 900000000000134402, 'origin', 'FORWARD_REQUEST'),
  (900000000000134509, 900000000000134403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134510, 900000000000134403, 'origin', 'FORWARD_REQUEST'),
  (900000000000134511, 900000000000134404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134512, 900000000000134404, 'origin', 'FORWARD_REQUEST'),
  (900000000000134513, 900000000000134404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134514, 900000000000134404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134515, 900000000000134404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134516, 900000000000134404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134517, 900000000000134405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134518, 900000000000134405, 'origin', 'FORWARD_REQUEST'),
  (900000000000134519, 900000000000134405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134520, 900000000000134405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134521, 900000000000134405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134522, 900000000000134405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134523, 900000000000134406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134524, 900000000000134406, 'origin', 'FORWARD_REQUEST'),
  (900000000000134525, 900000000000134406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134526, 900000000000134406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134527, 900000000000134406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134528, 900000000000134406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134529, 900000000000134407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134530, 900000000000134407, 'origin', 'FORWARD_REQUEST'),
  (900000000000134531, 900000000000134407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134532, 900000000000134407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134533, 900000000000134407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134534, 900000000000134407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134535, 900000000000134408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134536, 900000000000134408, 'origin', 'FORWARD_REQUEST'),
  (900000000000134537, 900000000000134408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134538, 900000000000134408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134539, 900000000000134408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134540, 900000000000134408, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134541, 900000000000134409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134542, 900000000000134409, 'origin', 'FORWARD_REQUEST'),
  (900000000000134543, 900000000000134409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134544, 900000000000134409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134545, 900000000000134409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134546, 900000000000134409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000134547, 900000000000134410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134548, 900000000000134410, 'origin', 'FORWARD_REQUEST'),
  (900000000000134549, 900000000000134411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134550, 900000000000134411, 'origin', 'FORWARD_REQUEST'),
  (900000000000134551, 900000000000134412, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000134552, 900000000000134412, 'origin', 'FORWARD_REQUEST'),
  (900000000000134553, 900000000000134412, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000134554, 900000000000134412, 'content-type', 'FORWARD_REQUEST'),
  (900000000000134555, 900000000000134412, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000134556, 900000000000134412, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000134601, 900000000000134410, 'createdFrom'),
  (900000000000134602, 900000000000134410, 'createdTo'),
  (900000000000134603, 900000000000134410, 'fileId'),
  (900000000000134604, 900000000000134410, 'sourceSystem'),
  (900000000000134605, 900000000000134410, 'reasonCode'),
  (900000000000134606, 900000000000134410, 'accessLevel'),
  (900000000000134607, 900000000000134410, 'scanStatus'),
  (900000000000134608, 900000000000134410, 'hasPendingReplacement'),
  (900000000000134609, 900000000000134410, 'page'),
  (900000000000134610, 900000000000134410, 'size'),
  (900000000000134611, 900000000000134412, 'versionNo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000134701, 900000000000134401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134702, 900000000000134401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134703, 900000000000134401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134704, 900000000000134401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134705, 900000000000134401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134706, 900000000000134401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134707, 900000000000134402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134708, 900000000000134402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134709, 900000000000134402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134710, 900000000000134402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134711, 900000000000134402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000134712, 900000000000134402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134713, 900000000000134403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134714, 900000000000134403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134715, 900000000000134403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134716, 900000000000134403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134717, 900000000000134403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000134718, 900000000000134403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134719, 900000000000134404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134720, 900000000000134404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134721, 900000000000134404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134722, 900000000000134404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134723, 900000000000134404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134724, 900000000000134404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134725, 900000000000134405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134726, 900000000000134405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134727, 900000000000134405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134728, 900000000000134405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134729, 900000000000134405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134730, 900000000000134405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134731, 900000000000134406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134732, 900000000000134406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134733, 900000000000134406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134734, 900000000000134406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134735, 900000000000134406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134736, 900000000000134406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134737, 900000000000134407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134738, 900000000000134407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134739, 900000000000134407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134740, 900000000000134407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134741, 900000000000134407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134742, 900000000000134407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134743, 900000000000134408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134744, 900000000000134408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134745, 900000000000134408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134746, 900000000000134408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134747, 900000000000134408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134748, 900000000000134408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134749, 900000000000134409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134750, 900000000000134409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134751, 900000000000134409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134752, 900000000000134409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134753, 900000000000134409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134754, 900000000000134409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134755, 900000000000134410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134756, 900000000000134410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134757, 900000000000134410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134758, 900000000000134410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134759, 900000000000134410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000134760, 900000000000134410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134761, 900000000000134411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134762, 900000000000134411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134763, 900000000000134411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134764, 900000000000134411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134765, 900000000000134411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000134766, 900000000000134411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000134767, 900000000000134412, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000134768, 900000000000134412, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000134769, 900000000000134412, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000134770, 900000000000134412, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000134771, 900000000000134412, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000134772, 900000000000134412, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
