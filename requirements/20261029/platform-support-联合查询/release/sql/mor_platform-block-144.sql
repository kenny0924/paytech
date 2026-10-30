-- =====================================================================================================================
-- platform-support 联合查询 · 注册块 144（键类型 26 行 ACTIVE；菜单与路由 DISABLED）
-- 目标库：Platform Support（mor_platform）。与 platform-support-initial-data.sql 末尾“Joint query”块正文相同；
-- 已初始化的环境只执行本文件。先执行 2026-10-29-joint-query.sql。由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------------
-- Joint query (2026-10-29, requirements/20261029/platform-support-联合查询, technique.md 9.2-9.3).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block
-- (changes/2026-10-29-joint-query-registration-144.sql is the same text). Requires changes/2026-10-29-joint-query.sql.
-- Block 144 is provisional (block 143 is taken by REQ-03 risk-decision); before applying re-check on the live mor_platform
-- that no row in 900000000000144001-900000000000144999 exists (t_query_key_type included), that permissions
-- 900000000000131101 / 900000000000131103 / 900000000000131104 and menu 900000000000131301 (block 131) exist, and that the
-- key type codes, resource codes, route codes and (method, external path) pairs below are unused.
-- Key types: the first 26 (technique.md 9.2), ACTIVE, IDs 1441xx; sort_no follows the business flow, lane order = smallest
-- sort_no in a group: 收单 10, 支付 40, 渠道 75, 争议 90, 计费 100, 账户 110, 总账 120, 出款 130, 对账 140, 归属维度 900.
-- Patterns are whole-string matches without ^$ and use only syntax with identical Java / JavaScript behaviour.
-- Dormant like blocks 131/142: menus and routes DISABLED; activate with changes/2026-10-29-joint-query-registration-144-activate.sql
-- after Platform and operations-console-bff are deployed. No new permission, role grant or button.
--   Menus under 数据查询 (131301): 联合查询 /data-query/joint-query (sort 5, DATA_QUERY_EXECUTE) and 键类型
--   /data-query/key-types (sort 30, DATA_QUERY_READ; writes on the page need DATA_QUERY_TEMPLATE_MANAGE).
--   Routes 8, same path on operations-console-bff (QueryKeyTypeManagementApi / JointQueryApi on platform-support-service):
--   key type list (READ, query status/keyword/page/size), create / update / status / order (TEMPLATE_MANAGE), joint query
--   settings / graph (EXECUTE, graph query entryKeyType) and steps (EXECUTE, body 65536). Response timeout 10000 ms
--   (Platform step budget 8 s, BFF read timeout 9 s). Headers and guards copy block 131 (GET: authorization + origin;
--   bodies: plus content-type and idempotency-key); reads 30/min per IP and principal, writes 10/min per principal, and
--   steps 60/min per IP and principal because the page calls one step per layer (up to 6 per query).
-- Rows: query_key_type 26, console_resource 2, gateway_route 8, route_header 36, query_param 5, guard_rule 48 (125).
-- ID block 144: 1441xx query_key_type, 1443xx console_resource, 1444xx route, 1445xx route_header, 1446xx query_param,
-- 1447xx guard_rule. Compile the Gateway Snapshot offline before applying (DISABLED routes are compiled too).
-- Rollback (children first): changes/2026-10-29-joint-query-registration-144-rollback.sql
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @joint_query_time = UTC_TIMESTAMP(6);
SET @joint_query_actor_id = 1;
SET @joint_query_target_id = 900000000000000001;

INSERT INTO t_query_key_type (key_type_id,key_type_code,key_type_name,group_name,value_type,default_role,entry_allowed,value_pattern,input_hint,sort_no,description,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000144101, 'CHECKOUT_SESSION_ID', '收银台会话 ID', '收单', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 10, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144102, 'ORDER_ID', '交易订单 ID', '收单', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 20, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144103, 'ORDER_NO', '交易订单号', '收单', 'STRING', 'LINK', 1, 'ord_([0-9A-Za-z]{30}|[A-Za-z0-9_-]{22})', 'ord_ 开头的交易订单号', 21, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144104, 'CLIENT_ORDER_ID', '客户订单号', '收单', 'STRING', 'LINK', 1, '[!-~]{1,128}', '客户系统的订单号，1—128 个可见 ASCII 字符', 22, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144105, 'REFUND_REQUEST_ID', '退款申请 ID', '收单', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 30, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144106, 'REFUND_REQUEST_NO', '退款单号', '收单', 'STRING', 'LINK', 1, 'rfd_[0-9A-Za-z]{30}', 'rfd_ 开头的 34 位退款单号', 31, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144107, 'PAYMENT_INTENT_ID', '支付意图 ID', '支付', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 40, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144108, 'PAYMENT_ATTEMPT_ID', '支付尝试 ID', '支付', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 50, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144109, 'PAYMENT_ACTION_ID', '支付动作 ID', '支付', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 60, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144110, 'CHANNEL_REFUND_ID', '渠道退款 ID', '支付', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 65, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144111, 'CHANNEL_TRANSACTION_REF_ID', '渠道交易引用 ID', '支付', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 70, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144112, 'CHANNEL_INTERACTION_ID', '渠道交互 ID', '渠道', 'INTEGER', 'LINK', 0, NULL, NULL, 75, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144113, 'EXTERNAL_TRANSACTION_ID', '渠道交易号', '渠道', 'STRING', 'LINK', 1, NULL, '渠道返回的交易号', 80, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144114, 'DISPUTE_CASE_ID', '争议案件 ID', '争议', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 90, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144115, 'BILLING_FACT_ID', '计费事实 ID', '计费', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 100, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144116, 'ACCOUNT_POSTING_ID', '记账批次 ID', '账户', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 110, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144117, 'JOURNAL_ENTRY_ID', '会计凭证 ID', '总账', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 120, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144118, 'PAYOUT_ID', '出款 ID', '出款', 'INTEGER', 'LINK', 1, '[0-9]{1,19}', '1—19 位数字', 130, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144119, 'PLATFORM_TXN_ID', '平台流水 ID', '对账', 'INTEGER', 'LINK', 0, NULL, NULL, 140, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144120, 'CLIENT_ID', '客户 ID', '归属维度', 'INTEGER', 'DIMENSION', 1, '[0-9]{1,19}', '1—19 位数字', 900, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144121, 'ENTITY_NO', '运营主体编号', '归属维度', 'STRING', 'DIMENSION', 1, '[A-Z0-9]{5}', '5 位运营主体编号，如 AE001', 910, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144122, 'LOCAL_PARTNER_ID', '本地合作方 ID', '归属维度', 'INTEGER', 'DIMENSION', 0, NULL, NULL, 920, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144123, 'CHANNEL_ID', '渠道 ID', '归属维度', 'INTEGER', 'DIMENSION', 0, NULL, NULL, 930, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144124, 'CHANNEL_ACCOUNT_ID', '渠道账号 ID', '归属维度', 'INTEGER', 'DIMENSION', 0, NULL, NULL, 940, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144125, 'MID_ID', 'MID ID', '归属维度', 'INTEGER', 'DIMENSION', 0, NULL, NULL, 950, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144126, 'CLEARING_ACCOUNT_ID', '清算账户 ID', '归属维度', 'INTEGER', 'DIMENSION', 0, NULL, NULL, 960, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000144301, 'OPERATIONS_JOINT_QUERY', 'OPERATIONS', 'MENU', 900000000000131301, '联合查询', '/data-query/joint-query', 'OPERATIONS_JOINT_QUERY', 900000000000131104, 5, 'DISABLED', 1, @joint_query_time, @joint_query_time),
  (900000000000144302, 'OPERATIONS_QUERY_KEY_TYPES', 'OPERATIONS', 'MENU', 900000000000131301, '键类型', '/data-query/key-types', 'OPERATIONS_QUERY_KEY_TYPES', 900000000000131101, 30, 'DISABLED', 1, @joint_query_time, @joint_query_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000144401, 'operations-query-key-type-list', 'OPERATIONS', 'GET', '/api/operations/data-query-admin/v1/key-types', '/data-query-admin/v1/key-types', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144402, 'operations-query-key-type-create', 'OPERATIONS', 'POST', '/api/operations/data-query-admin/v1/key-types', '/data-query-admin/v1/key-types', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144403, 'operations-query-key-type-update', 'OPERATIONS', 'PUT', '/api/operations/data-query-admin/v1/key-types/{keyTypeId}', '/data-query-admin/v1/key-types/{keyTypeId}', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144404, 'operations-query-key-type-status', 'OPERATIONS', 'POST', '/api/operations/data-query-admin/v1/key-types/{keyTypeId}/status', '/data-query-admin/v1/key-types/{keyTypeId}/status', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144405, 'operations-query-key-type-order', 'OPERATIONS', 'PUT', '/api/operations/data-query-admin/v1/key-type-order', '/data-query-admin/v1/key-type-order', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144406, 'operations-joint-query-settings', 'OPERATIONS', 'GET', '/api/operations/data-query/v1/joint-query/settings', '/data-query/v1/joint-query/settings', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131104, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144407, 'operations-joint-query-graph', 'OPERATIONS', 'GET', '/api/operations/data-query/v1/joint-query/graph', '/data-query/v1/joint-query/graph', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131104, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144408, 'operations-joint-query-step', 'OPERATIONS', 'POST', '/api/operations/data-query/v1/joint-query/steps', '/data-query/v1/joint-query/steps', @joint_query_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000131104, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000144501, 900000000000144401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144502, 900000000000144401, 'origin', 'FORWARD_REQUEST'),
  (900000000000144503, 900000000000144402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144504, 900000000000144402, 'origin', 'FORWARD_REQUEST'),
  (900000000000144505, 900000000000144402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000144506, 900000000000144402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000144507, 900000000000144402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000144508, 900000000000144402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000144509, 900000000000144403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144510, 900000000000144403, 'origin', 'FORWARD_REQUEST'),
  (900000000000144511, 900000000000144403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000144512, 900000000000144403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000144513, 900000000000144403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000144514, 900000000000144403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000144515, 900000000000144404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144516, 900000000000144404, 'origin', 'FORWARD_REQUEST'),
  (900000000000144517, 900000000000144404, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000144518, 900000000000144404, 'content-type', 'FORWARD_REQUEST'),
  (900000000000144519, 900000000000144404, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000144520, 900000000000144404, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000144521, 900000000000144405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144522, 900000000000144405, 'origin', 'FORWARD_REQUEST'),
  (900000000000144523, 900000000000144405, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000144524, 900000000000144405, 'content-type', 'FORWARD_REQUEST'),
  (900000000000144525, 900000000000144405, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000144526, 900000000000144405, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000144527, 900000000000144406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144528, 900000000000144406, 'origin', 'FORWARD_REQUEST'),
  (900000000000144529, 900000000000144407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144530, 900000000000144407, 'origin', 'FORWARD_REQUEST'),
  (900000000000144531, 900000000000144408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000144532, 900000000000144408, 'origin', 'FORWARD_REQUEST'),
  (900000000000144533, 900000000000144408, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000144534, 900000000000144408, 'content-type', 'FORWARD_REQUEST'),
  (900000000000144535, 900000000000144408, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000144536, 900000000000144408, 'idempotency-key', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000144601, 900000000000144401, 'status'),
  (900000000000144602, 900000000000144401, 'keyword'),
  (900000000000144603, 900000000000144401, 'page'),
  (900000000000144604, 900000000000144401, 'size'),
  (900000000000144605, 900000000000144407, 'entryKeyType');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000144701, 900000000000144401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144702, 900000000000144401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144703, 900000000000144401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144704, 900000000000144401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144705, 900000000000144401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000144706, 900000000000144401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144707, 900000000000144402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144708, 900000000000144402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144709, 900000000000144402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144710, 900000000000144402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144711, 900000000000144402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000144712, 900000000000144402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144713, 900000000000144403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144714, 900000000000144403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144715, 900000000000144403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144716, 900000000000144403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144717, 900000000000144403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000144718, 900000000000144403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144719, 900000000000144404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144720, 900000000000144404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144721, 900000000000144404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144722, 900000000000144404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144723, 900000000000144404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000144724, 900000000000144404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144725, 900000000000144405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144726, 900000000000144405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144727, 900000000000144405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144728, 900000000000144405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144729, 900000000000144405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000144730, 900000000000144405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144731, 900000000000144406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144732, 900000000000144406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144733, 900000000000144406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144734, 900000000000144406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144735, 900000000000144406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000144736, 900000000000144406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144737, 900000000000144407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144738, 900000000000144407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144739, 900000000000144407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000144740, 900000000000144407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144741, 900000000000144407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000144742, 900000000000144407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000144743, 900000000000144408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000144744, 900000000000144408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000144745, 900000000000144408, 'RATE_LIMIT', 'CLIENT_IP', 60, 60, 60000, NULL),
  (900000000000144746, 900000000000144408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000144747, 900000000000144408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 60, 60, 60000, NULL),
  (900000000000144748, 900000000000144408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2);

COMMIT;
