-- 未执行草案（2026-10-23 阶段 4，任务卡 B）：platform-support：多语言管理，目标库 mor_platform（platform-support-service）。
-- 三部分，各自一个事务，按顺序执行；只写不执行，执行（本地阶段 5、mor-test 阶段 7）另行授权。
--   第 1 部分：注册块 135（运营「多语言」权限、菜单、BFF 路由与两个 Console 的匿名 manifest 路由），休眠写入，激活另行执行。
--   第 2 部分：多语言业务初始数据（语言 en、zh-CN；应用 4 个）。前置：changes/2026-10-23-localization.sql 已执行。
--   第 3 部分（任务卡 F 集成）：文件检查策略 I18N_BUNDLE_JSON 与 OPERATIONS_BFF_IMPORT、platform-support × PUBLIC 写入路由，
--     并把 operations-console-bff × PRIVATE 写入路由由 CARD_BIN_CSV 改指 OPERATIONS_BFF_IMPORT。前置：文件公开桶已发布
--     （t_file_version、路由 access_level 列、FORMAT 检查支持 application/json 的 Platform 镜像）。
-- 阶段 4 集成时两部分并入 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql（由主会话合并，本卡不改该文件）。
-- 回滚：同目录 dml-rollback.sql（按 ID 删除本文件写入的全部行）。
--
-- 不在本文件（原因见 tasks/B-report.md、tasks/F-report.md）：
--   - 三个原因码 I18N_KEY_IMPORT、I18N_TRANSLATION_IMPORT、I18N_RELEASE_BUNDLE 没有可登记的表：Platform 不登记原因码，写入路由
--     t_file_storage_route 按 (来源系统, 访问级别) 选择桶与检查策略。导入文件走 operations-console-bff × PRIVATE 路由、语言包走
--     platform-support × PUBLIC 路由，两条路由的检查策略见第 3 部分。
--   - 补偿事件目录项：Platform 代码内 compensation-event-catalog.yaml（卡 A），不是表数据。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 1 部分：Operations localization console (2026-10-23, requirements/20261022/platform-support-多语言管理).
-- Self-contained block: on an environment already initialized by platform-support-initial-data.sql, apply ONLY this block.
-- Dormant like earlier Operations features: permissions ACTIVE, administrator grants REVOKED, menus and routes DISABLED;
-- activate them in a separate reviewed change. Operations BFF routes forward to operations-console-bff on the same path
-- (/localization-admin/v1/...), which calls LocalizationCatalogApi, LocalizationContentApi and LocalizationReleaseApi on
-- platform-support-service (/internal/localization/v1/..., same path without the prefix) and, for the two upload routes,
-- Platform InternalFileApi with reasonCode I18N_KEY_IMPORT / I18N_TRANSLATION_IMPORT; operator identity comes from the
-- Gateway actor headers. The two manifest routes are PUBLIC GETs to /i18n/v1/manifest on operations-console-bff and
-- client-console-bff; each BFF fixes its own application code (operations-console / client-console).
-- Permissions: I18N_VIEW (LOW: catalog, translation workbench, export, precheck, release history/detail/diff),
-- I18N_EDIT (MEDIUM: locales, applications, namespaces, uploads, key import, translation save/import; changes are not
-- visible to end users until published), I18N_PUBLISH (HIGH: publish and switch the current release, which changes the
-- live UI text of a whole console).
-- Menus: top-level group 多语言 (sort 70) with 翻译 /i18n/translations, 发布记录 /i18n/releases and 语言与应用 /i18n/settings.
-- Buttons: 编辑翻译 / 导入 key (I18N_EDIT) and 发布 (I18N_PUBLISH) under 翻译; 切回此版本 (I18N_PUBLISH) under 发布记录;
-- 新建应用 / 维护命名空间 / 维护设置 (I18N_EDIT) under 语言与应用.
-- Routes: 27 = Operations admin 23 + upload 2 (HUMAN_PERMISSION) + Operations manifest 1 + Client manifest 1 (PUBLIC).
-- Headers and guard rules copy block 131 (GET: authorization + origin; body: plus content-type and idempotency-key;
-- DELETE without body: authorization + origin + idempotency-key; idempotency_mode NONE). Translation save is used for
-- 保存并下一条, so its CLIENT_IP and AUTHENTICATED_PRINCIPAL limits are 120/min instead of 10. Manifest routes carry
-- origin and if-none-match only, with SHARED_ROUTE 600/min + 50 concurrent (the compiler ceilings) and CLIENT_IP 120/min + 10 (no principal).
-- Query whitelists: namespace delete expectedVersion; translation list locale/namespaceCode/state/keyword/page/size;
-- export locale; release list page/size; release diff baseReleaseNo; both manifests locale/releaseNo.
-- The Client manifest route uses the existing client-console-bff Gateway target (registered through gateway-admin, its ID
-- differs per environment); the block fails on NOT NULL target_id if that target does not exist.
-- Rows: permission 3, role_permission 3, console_resource 11, gateway_route 27, route_header 114, query_param 15,
-- guard_rule 158 (331). ID block 135: 1351xx permission, 1352xx role_permission, 1353xx console_resource, 1354xx route,
-- 1355xx-1356xx route_header, 1357xx query_param, 1358xx-1359xx guard_rule, 1350xx localization initial data (第 2 部分).
-- Before applying, re-check on the live mor_platform that no row in 900000000000135001-900000000000135999 exists, that
-- the permission codes, resource codes, route codes and (method, external path) pairs above are unused, and that
-- SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff' returns one row.
-- Rollback: dml-rollback.sql (children first: guard_rule, query_param, route_header, gateway_route, console_resource,
-- role_permission, permission).
-- Activation (separate reviewed change, after Platform, both BFFs and both web apps are deployed):
--   UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135203;
--   UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135311;
--   UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id BETWEEN 900000000000135401 AND 900000000000135427;
--   then republish the Gateway Snapshot.
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @i18n_console_time = UTC_TIMESTAMP(6);
SET @i18n_console_actor_id = 1;
SET @i18n_console_target_id = 900000000000000001;
SET @i18n_console_role_id = 900000000000000510;
SET @i18n_client_target_id = (SELECT target_id FROM t_gateway_target WHERE target_service_code = 'client-console-bff');

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
  (900000000000135101, 'I18N_VIEW', 'I18N', 'VIEW', 'LOW', 'ACTIVE', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135102, 'I18N_EDIT', 'I18N', 'EDIT', 'MEDIUM', 'ACTIVE', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135103, 'I18N_PUBLISH', 'I18N', 'PUBLISH', 'HIGH', 'ACTIVE', 1, @i18n_console_time, @i18n_console_time);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
  (900000000000135201, @i18n_console_role_id, 900000000000135101, 'REVOKED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135202, @i18n_console_role_id, 900000000000135102, 'REVOKED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135203, @i18n_console_role_id, 900000000000135103, 'REVOKED', 1, @i18n_console_time, @i18n_console_time);

INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES
  (900000000000135301, 'OPERATIONS_I18N', 'OPERATIONS', 'MENU', NULL, '多语言', NULL, NULL, 900000000000135101, 70, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135302, 'OPERATIONS_I18N_TRANSLATIONS', 'OPERATIONS', 'MENU', 900000000000135301, '翻译', '/i18n/translations', 'OPERATIONS_I18N_TRANSLATIONS', 900000000000135101, 10, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135303, 'OPERATIONS_I18N_RELEASES', 'OPERATIONS', 'MENU', 900000000000135301, '发布记录', '/i18n/releases', 'OPERATIONS_I18N_RELEASES', 900000000000135101, 20, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135304, 'OPERATIONS_I18N_SETTINGS', 'OPERATIONS', 'MENU', 900000000000135301, '语言与应用', '/i18n/settings', 'OPERATIONS_I18N_SETTINGS', 900000000000135101, 30, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135305, 'OPERATIONS_I18N_TRANSLATION_EDIT', 'OPERATIONS', 'BUTTON', 900000000000135302, '编辑翻译', NULL, NULL, 900000000000135102, 10, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135306, 'OPERATIONS_I18N_KEY_IMPORT', 'OPERATIONS', 'BUTTON', 900000000000135302, '导入 key', NULL, NULL, 900000000000135102, 20, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135307, 'OPERATIONS_I18N_RELEASE_PUBLISH', 'OPERATIONS', 'BUTTON', 900000000000135302, '发布', NULL, NULL, 900000000000135103, 30, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135308, 'OPERATIONS_I18N_RELEASE_ACTIVATE', 'OPERATIONS', 'BUTTON', 900000000000135303, '切回此版本', NULL, NULL, 900000000000135103, 10, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135309, 'OPERATIONS_I18N_APPLICATION_CREATE', 'OPERATIONS', 'BUTTON', 900000000000135304, '新建应用', NULL, NULL, 900000000000135102, 10, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135310, 'OPERATIONS_I18N_NAMESPACE_MANAGE', 'OPERATIONS', 'BUTTON', 900000000000135304, '维护命名空间', NULL, NULL, 900000000000135102, 20, 'DISABLED', 1, @i18n_console_time, @i18n_console_time),
  (900000000000135311, 'OPERATIONS_I18N_SETTINGS_EDIT', 'OPERATIONS', 'BUTTON', 900000000000135304, '维护设置', NULL, NULL, 900000000000135102, 30, 'DISABLED', 1, @i18n_console_time, @i18n_console_time);

INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000135401, 'operations-i18n-locale-list', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/locales', '/localization-admin/v1/locales', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135402, 'operations-i18n-locale-create', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/locales', '/localization-admin/v1/locales', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135403, 'operations-i18n-locale-update', 'OPERATIONS', 'PUT', '/api/operations/localization-admin/v1/locales/{localeCode}', '/localization-admin/v1/locales/{localeCode}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135404, 'operations-i18n-application-list', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications', '/localization-admin/v1/applications', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135405, 'operations-i18n-application-detail', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}', '/localization-admin/v1/applications/{applicationCode}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135406, 'operations-i18n-application-create', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications', '/localization-admin/v1/applications', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135407, 'operations-i18n-application-update', 'OPERATIONS', 'PUT', '/api/operations/localization-admin/v1/applications/{applicationCode}', '/localization-admin/v1/applications/{applicationCode}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135408, 'operations-i18n-namespace-list', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/namespaces', '/localization-admin/v1/applications/{applicationCode}/namespaces', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135409, 'operations-i18n-namespace-create', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/namespaces', '/localization-admin/v1/applications/{applicationCode}/namespaces', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135410, 'operations-i18n-namespace-update', 'OPERATIONS', 'PUT', '/api/operations/localization-admin/v1/applications/{applicationCode}/namespaces/{namespaceCode}', '/localization-admin/v1/applications/{applicationCode}/namespaces/{namespaceCode}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135411, 'operations-i18n-namespace-delete', 'OPERATIONS', 'DELETE', '/api/operations/localization-admin/v1/applications/{applicationCode}/namespaces/{namespaceCode}', '/localization-admin/v1/applications/{applicationCode}/namespaces/{namespaceCode}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135412, 'operations-i18n-key-import-preview', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/key-imports/previews', '/localization-admin/v1/applications/{applicationCode}/key-imports/previews', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135413, 'operations-i18n-key-import-confirm', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/key-imports', '/localization-admin/v1/applications/{applicationCode}/key-imports', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135414, 'operations-i18n-translation-list', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/translations', '/localization-admin/v1/applications/{applicationCode}/translations', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135415, 'operations-i18n-translation-save', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/translations/batch', '/localization-admin/v1/applications/{applicationCode}/translations/batch', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135416, 'operations-i18n-translation-export', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/translations/export', '/localization-admin/v1/applications/{applicationCode}/translations/export', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135417, 'operations-i18n-translation-import', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/translations/imports', '/localization-admin/v1/applications/{applicationCode}/translations/imports', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135418, 'operations-i18n-release-precheck', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/release-precheck', '/localization-admin/v1/applications/{applicationCode}/release-precheck', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135419, 'operations-i18n-release-publish', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/releases', '/localization-admin/v1/applications/{applicationCode}/releases', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135420, 'operations-i18n-release-list', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/releases', '/localization-admin/v1/applications/{applicationCode}/releases', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135421, 'operations-i18n-release-detail', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/releases/{releaseNo}', '/localization-admin/v1/applications/{applicationCode}/releases/{releaseNo}', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135422, 'operations-i18n-release-diff', 'OPERATIONS', 'GET', '/api/operations/localization-admin/v1/applications/{applicationCode}/releases/{releaseNo}/diff', '/localization-admin/v1/applications/{applicationCode}/releases/{releaseNo}/diff', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135101, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135423, 'operations-i18n-release-activate', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/applications/{applicationCode}/current-release', '/localization-admin/v1/applications/{applicationCode}/current-release', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135103, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135424, 'operations-i18n-upload-start', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/uploads', '/localization-admin/v1/uploads', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135425, 'operations-i18n-upload-complete', 'OPERATIONS', 'POST', '/api/operations/localization-admin/v1/uploads/{uploadId}/completion', '/localization-admin/v1/uploads/{uploadId}/completion', @i18n_console_target_id, 'OPERATIONS_JWT', 'HUMAN_PERMISSION', 900000000000135102, NULL, 'DROP', 'NONE', 'NONE', 65536, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135426, 'operations-i18n-manifest', 'OPERATIONS', 'GET', '/api/operations/i18n/v1/manifest', '/i18n/v1/manifest', @i18n_console_target_id, 'PUBLIC', 'PUBLIC', NULL, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time),
  (900000000000135427, 'client-i18n-manifest', 'CLIENT', 'GET', '/api/client/i18n/v1/manifest', '/i18n/v1/manifest', @i18n_client_target_id, 'PUBLIC', 'PUBLIC', NULL, NULL, 'DROP', 'NONE', 'NONE', 0, 5000, 10000, 'DISABLED', 1, @i18n_console_actor_id, @i18n_console_actor_id, @i18n_console_time, @i18n_console_time);

INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES
  (900000000000135501, 900000000000135401, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135502, 900000000000135401, 'origin', 'FORWARD_REQUEST'),
  (900000000000135503, 900000000000135402, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135504, 900000000000135402, 'origin', 'FORWARD_REQUEST'),
  (900000000000135505, 900000000000135402, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135506, 900000000000135402, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135507, 900000000000135402, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135508, 900000000000135402, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135509, 900000000000135403, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135510, 900000000000135403, 'origin', 'FORWARD_REQUEST'),
  (900000000000135511, 900000000000135403, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135512, 900000000000135403, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135513, 900000000000135403, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135514, 900000000000135403, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135515, 900000000000135404, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135516, 900000000000135404, 'origin', 'FORWARD_REQUEST'),
  (900000000000135517, 900000000000135405, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135518, 900000000000135405, 'origin', 'FORWARD_REQUEST'),
  (900000000000135519, 900000000000135406, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135520, 900000000000135406, 'origin', 'FORWARD_REQUEST'),
  (900000000000135521, 900000000000135406, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135522, 900000000000135406, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135523, 900000000000135406, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135524, 900000000000135406, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135525, 900000000000135407, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135526, 900000000000135407, 'origin', 'FORWARD_REQUEST'),
  (900000000000135527, 900000000000135407, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135528, 900000000000135407, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135529, 900000000000135407, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135530, 900000000000135407, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135531, 900000000000135408, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135532, 900000000000135408, 'origin', 'FORWARD_REQUEST'),
  (900000000000135533, 900000000000135409, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135534, 900000000000135409, 'origin', 'FORWARD_REQUEST'),
  (900000000000135535, 900000000000135409, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135536, 900000000000135409, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135537, 900000000000135409, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135538, 900000000000135409, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135539, 900000000000135410, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135540, 900000000000135410, 'origin', 'FORWARD_REQUEST'),
  (900000000000135541, 900000000000135410, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135542, 900000000000135410, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135543, 900000000000135410, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135544, 900000000000135410, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135545, 900000000000135411, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135546, 900000000000135411, 'origin', 'FORWARD_REQUEST'),
  (900000000000135547, 900000000000135411, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135548, 900000000000135411, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135549, 900000000000135412, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135550, 900000000000135412, 'origin', 'FORWARD_REQUEST'),
  (900000000000135551, 900000000000135412, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135552, 900000000000135412, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135553, 900000000000135412, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135554, 900000000000135412, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135555, 900000000000135413, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135556, 900000000000135413, 'origin', 'FORWARD_REQUEST'),
  (900000000000135557, 900000000000135413, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135558, 900000000000135413, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135559, 900000000000135413, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135560, 900000000000135413, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135561, 900000000000135414, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135562, 900000000000135414, 'origin', 'FORWARD_REQUEST'),
  (900000000000135563, 900000000000135415, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135564, 900000000000135415, 'origin', 'FORWARD_REQUEST'),
  (900000000000135565, 900000000000135415, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135566, 900000000000135415, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135567, 900000000000135415, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135568, 900000000000135415, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135569, 900000000000135416, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135570, 900000000000135416, 'origin', 'FORWARD_REQUEST'),
  (900000000000135571, 900000000000135417, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135572, 900000000000135417, 'origin', 'FORWARD_REQUEST'),
  (900000000000135573, 900000000000135417, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135574, 900000000000135417, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135575, 900000000000135417, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135576, 900000000000135417, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135577, 900000000000135418, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135578, 900000000000135418, 'origin', 'FORWARD_REQUEST'),
  (900000000000135579, 900000000000135419, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135580, 900000000000135419, 'origin', 'FORWARD_REQUEST'),
  (900000000000135581, 900000000000135419, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135582, 900000000000135419, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135583, 900000000000135419, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135584, 900000000000135419, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135585, 900000000000135420, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135586, 900000000000135420, 'origin', 'FORWARD_REQUEST'),
  (900000000000135587, 900000000000135421, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135588, 900000000000135421, 'origin', 'FORWARD_REQUEST'),
  (900000000000135589, 900000000000135422, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135590, 900000000000135422, 'origin', 'FORWARD_REQUEST'),
  (900000000000135591, 900000000000135423, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135592, 900000000000135423, 'origin', 'FORWARD_REQUEST'),
  (900000000000135593, 900000000000135423, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135594, 900000000000135423, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135595, 900000000000135423, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135596, 900000000000135423, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135597, 900000000000135424, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135598, 900000000000135424, 'origin', 'FORWARD_REQUEST'),
  (900000000000135599, 900000000000135424, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135600, 900000000000135424, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135601, 900000000000135424, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135602, 900000000000135424, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135603, 900000000000135425, 'authorization', 'CORS_REQUEST_ALLOWED'),
  (900000000000135604, 900000000000135425, 'origin', 'FORWARD_REQUEST'),
  (900000000000135605, 900000000000135425, 'content-type', 'CORS_REQUEST_ALLOWED'),
  (900000000000135606, 900000000000135425, 'content-type', 'FORWARD_REQUEST'),
  (900000000000135607, 900000000000135425, 'idempotency-key', 'CORS_REQUEST_ALLOWED'),
  (900000000000135608, 900000000000135425, 'idempotency-key', 'FORWARD_REQUEST'),
  (900000000000135609, 900000000000135426, 'origin', 'FORWARD_REQUEST'),
  (900000000000135610, 900000000000135426, 'if-none-match', 'CORS_REQUEST_ALLOWED'),
  (900000000000135611, 900000000000135426, 'if-none-match', 'FORWARD_REQUEST'),
  (900000000000135612, 900000000000135427, 'origin', 'FORWARD_REQUEST'),
  (900000000000135613, 900000000000135427, 'if-none-match', 'CORS_REQUEST_ALLOWED'),
  (900000000000135614, 900000000000135427, 'if-none-match', 'FORWARD_REQUEST');

INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES
  (900000000000135701, 900000000000135411, 'expectedVersion'),
  (900000000000135702, 900000000000135414, 'locale'),
  (900000000000135703, 900000000000135414, 'namespaceCode'),
  (900000000000135704, 900000000000135414, 'state'),
  (900000000000135705, 900000000000135414, 'keyword'),
  (900000000000135706, 900000000000135414, 'page'),
  (900000000000135707, 900000000000135414, 'size'),
  (900000000000135708, 900000000000135416, 'locale'),
  (900000000000135709, 900000000000135420, 'page'),
  (900000000000135710, 900000000000135420, 'size'),
  (900000000000135711, 900000000000135422, 'baseReleaseNo'),
  (900000000000135712, 900000000000135426, 'locale'),
  (900000000000135713, 900000000000135426, 'releaseNo'),
  (900000000000135714, 900000000000135427, 'locale'),
  (900000000000135715, 900000000000135427, 'releaseNo');

INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES
  (900000000000135801, 900000000000135401, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135802, 900000000000135401, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135803, 900000000000135401, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135804, 900000000000135401, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135805, 900000000000135401, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135806, 900000000000135401, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135807, 900000000000135402, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135808, 900000000000135402, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135809, 900000000000135402, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135810, 900000000000135402, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135811, 900000000000135402, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135812, 900000000000135402, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135813, 900000000000135403, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135814, 900000000000135403, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135815, 900000000000135403, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135816, 900000000000135403, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135817, 900000000000135403, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135818, 900000000000135403, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135819, 900000000000135404, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135820, 900000000000135404, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135821, 900000000000135404, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135822, 900000000000135404, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135823, 900000000000135404, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135824, 900000000000135404, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135825, 900000000000135405, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135826, 900000000000135405, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135827, 900000000000135405, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135828, 900000000000135405, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135829, 900000000000135405, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135830, 900000000000135405, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135831, 900000000000135406, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135832, 900000000000135406, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135833, 900000000000135406, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135834, 900000000000135406, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135835, 900000000000135406, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135836, 900000000000135406, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135837, 900000000000135407, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135838, 900000000000135407, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135839, 900000000000135407, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135840, 900000000000135407, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135841, 900000000000135407, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135842, 900000000000135407, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135843, 900000000000135408, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135844, 900000000000135408, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135845, 900000000000135408, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135846, 900000000000135408, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135847, 900000000000135408, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135848, 900000000000135408, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135849, 900000000000135409, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135850, 900000000000135409, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135851, 900000000000135409, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135852, 900000000000135409, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135853, 900000000000135409, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135854, 900000000000135409, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135855, 900000000000135410, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135856, 900000000000135410, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135857, 900000000000135410, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135858, 900000000000135410, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135859, 900000000000135410, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135860, 900000000000135410, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135861, 900000000000135411, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135862, 900000000000135411, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135863, 900000000000135411, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135864, 900000000000135411, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135865, 900000000000135411, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135866, 900000000000135411, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135867, 900000000000135412, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135868, 900000000000135412, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135869, 900000000000135412, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135870, 900000000000135412, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135871, 900000000000135412, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135872, 900000000000135412, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135873, 900000000000135413, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135874, 900000000000135413, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135875, 900000000000135413, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135876, 900000000000135413, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135877, 900000000000135413, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135878, 900000000000135413, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135879, 900000000000135414, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135880, 900000000000135414, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135881, 900000000000135414, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135882, 900000000000135414, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135883, 900000000000135414, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135884, 900000000000135414, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135885, 900000000000135415, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135886, 900000000000135415, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135887, 900000000000135415, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000135888, 900000000000135415, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135889, 900000000000135415, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 120, 120, 60000, NULL),
  (900000000000135890, 900000000000135415, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135891, 900000000000135416, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135892, 900000000000135416, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135893, 900000000000135416, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135894, 900000000000135416, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135895, 900000000000135416, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135896, 900000000000135416, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135897, 900000000000135417, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135898, 900000000000135417, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135899, 900000000000135417, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135900, 900000000000135417, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135901, 900000000000135417, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135902, 900000000000135417, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135903, 900000000000135418, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135904, 900000000000135418, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135905, 900000000000135418, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135906, 900000000000135418, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135907, 900000000000135418, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135908, 900000000000135418, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135909, 900000000000135419, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135910, 900000000000135419, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135911, 900000000000135419, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135912, 900000000000135419, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135913, 900000000000135419, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135914, 900000000000135419, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135915, 900000000000135420, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135916, 900000000000135420, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135917, 900000000000135420, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135918, 900000000000135420, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135919, 900000000000135420, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135920, 900000000000135420, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135921, 900000000000135421, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135922, 900000000000135421, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135923, 900000000000135421, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135924, 900000000000135421, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135925, 900000000000135421, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135926, 900000000000135421, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135927, 900000000000135422, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135928, 900000000000135422, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135929, 900000000000135422, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135930, 900000000000135422, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135931, 900000000000135422, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 30, 30, 60000, NULL),
  (900000000000135932, 900000000000135422, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135933, 900000000000135423, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135934, 900000000000135423, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135935, 900000000000135423, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135936, 900000000000135423, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135937, 900000000000135423, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135938, 900000000000135423, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135939, 900000000000135424, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135940, 900000000000135424, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135941, 900000000000135424, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135942, 900000000000135424, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135943, 900000000000135424, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135944, 900000000000135424, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135945, 900000000000135425, 'RATE_LIMIT', 'SHARED_ROUTE', 300, 300, 60000, NULL),
  (900000000000135946, 900000000000135425, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135947, 900000000000135425, 'RATE_LIMIT', 'CLIENT_IP', 30, 30, 60000, NULL),
  (900000000000135948, 900000000000135425, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 3),
  (900000000000135949, 900000000000135425, 'RATE_LIMIT', 'AUTHENTICATED_PRINCIPAL', 10, 10, 60000, NULL),
  (900000000000135950, 900000000000135425, 'CONCURRENCY', 'AUTHENTICATED_PRINCIPAL', NULL, NULL, NULL, 2),
  (900000000000135951, 900000000000135426, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000135952, 900000000000135426, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135953, 900000000000135426, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000135954, 900000000000135426, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10),
  (900000000000135955, 900000000000135427, 'RATE_LIMIT', 'SHARED_ROUTE', 600, 600, 60000, NULL),
  (900000000000135956, 900000000000135427, 'CONCURRENCY', 'SHARED_ROUTE', NULL, NULL, NULL, 50),
  (900000000000135957, 900000000000135427, 'RATE_LIMIT', 'CLIENT_IP', 120, 120, 60000, NULL),
  (900000000000135958, 900000000000135427, 'CONCURRENCY', 'CLIENT_IP', NULL, NULL, NULL, 10);

COMMIT;

-- ---------------------------------------------------------------------------
-- 第 2 部分：多语言业务初始数据（technique.md 3.9 末段）。固定 ID 取块 135 的 1350xx 段，低于 Snowflake 范围的做法同块 132 第 1 段。
-- 语言：en（基准，不可停用）、zh-CN。应用：operations-console 默认 zh-CN；client-console、partner-console、checkout 默认 en；
-- 四个应用必须覆盖 zh-CN，阈值 100%（technique 未给阈值，取最严格值，运营可在「语言与应用」调整）。均无发布，修订号 0。
-- 前置：changes/2026-10-23-localization.sql 已执行；两表中没有这些语言代码与应用编码。
-- 可重跑：否。回退：见 dml-rollback.sql 第 2 部分。
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @i18n_seed_time = UTC_TIMESTAMP(6);
SET @i18n_seed_actor_id = 1;

INSERT INTO t_i18n_locale (locale_id, locale_code, native_name, english_name, text_direction, status, sort_order, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000135001, 'en', 'English', 'English', 'LTR', 'ENABLED', 10, 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135002, 'zh-CN', '简体中文', 'Chinese (Simplified)', 'LTR', 'ENABLED', 20, 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time);

INSERT INTO t_i18n_application (application_id, application_code, application_name, default_locale, required_locales, coverage_threshold_percent, current_release_id, key_set_revision, content_revision, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000135011, 'operations-console', 'Operations Console', 'zh-CN', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135012, 'client-console', 'Client Console', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135013, 'partner-console', 'Partner Console', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135014, 'checkout', 'Checkout', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time);

COMMIT;

-- ---------------------------------------------------------------------------
-- 第 3 部分（任务卡 F 集成，2026-10-23）：多语言的文件检查策略与写入路由。固定 ID 取块 135 的 1350xx 段（900000000000135021—025）。
-- 1) 检查策略 I18N_BUNDLE_JSON 修订 1（ACTIVE）：只允许 application/json，单文件 1 MiB（1048576），一项 PLATFORM_FORMAT / FORMAT /
--    最低版本 1 / 只接受 FORMAT_VALID 的要求（与检查策略管理新建修订时自动写入的要求相同）。FORMAT 检查对 JSON 复核：
--    UTF-8、无 BOM、不含 NUL、全文恰好是一个 JSON 对象。
-- 2) 检查策略 OPERATIONS_BFF_IMPORT 修订 1（ACTIVE）：允许 text/csv 与 application/json，上限取两者较大值，即 CARD_BIN_CSV 的
--    52428800（50 MiB；多语言导入文件由 Platform 业务另限 2 MiB）；要求同上。
-- 3) 写入路由 platform-support × PUBLIC → 公开桶，检查策略 I18N_BUNDLE_JSON，授权有效期 300 秒（语言包在 Platform 进程内签发后
--    立即写入）。来源系统取文件服务为 Platform 进程内上传记录的来源（FileSourceCodes.PLATFORM_SUPPORT = 'platform-support'，
--    与工作流附件相同；语言包上传由 FileServiceReleaseBundleStore 以该来源调用文件入口）。
--    桶按 (实例代码, 桶名) 查找：mor-test 为文件公开桶发布时经「存储管理」登记并启用的 MINIO / mor-platform-public
--    （对外地址 https://minio.mor-test.localhost:29400/mor-platform-public，匿名 GetObject 已允许）；
--    其他环境执行前把下方两个变量改为该环境已登记且 ACTIVE 的 PUBLIC 桶。桶不存在时 bucket_id 为 NULL，INSERT 因 NOT NULL 失败，
--    整个事务不提交。
-- 4) 写入路由 operations-console-bff × PRIVATE（mor-test route_id 239087906591948800，块 132 发布时在「存储管理」页登记）：
--    只改策略引用，scan_policy_code 由旧值 CARD_BIN_CSV 改为 OPERATIONS_BFF_IMPORT，版本加一；route_id、桶与
--    max_upload_seconds=900 不变。WHERE 带 route_id 与旧值，必须恰好影响 1 行（执行后核对 ROW_COUNT()=1，否则 ROLLBACK）。
--    卡 BIN 库 CSV 上传继续可用（同一 FORMAT 检查：合法 UTF-8、无 NUL，上限 52428800）；多语言 key / 翻译导入的 JSON 由此能通过
--    Platform 检查。owner code-mor-6b 已确认；发布后由主会话安排卡 BIN 导入验收。其他环境的 route_id 不同，按前置 SQL 查出后替换。
-- 前置（在 live mor_platform 只读核对）：
--   SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code IN ('I18N_BUNDLE_JSON','OPERATIONS_BFF_IMPORT');            -- 0
--   SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_id BETWEEN 900000000000135021 AND 900000000000135025;           -- 0
--   SELECT COUNT(*) FROM t_file_scan_requirement WHERE requirement_id BETWEEN 900000000000135021 AND 900000000000135025; -- 0
--   SELECT COUNT(*) FROM t_file_storage_route WHERE route_id = 900000000000135025
--     OR (source_system = 'platform-support' AND access_level = 'PUBLIC');                                              -- 0
--   SELECT route_id, scan_policy_code, max_upload_seconds, version FROM t_file_storage_route
--     WHERE source_system = 'operations-console-bff' AND access_level = 'PRIVATE';
--     -- mor-test：一行，239087906591948800 / CARD_BIN_CSV / 900；记下 version 供回滚核对
--   SELECT b.bucket_id, b.status, b.access_level FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
--     WHERE i.instance_code = 'MINIO' AND b.bucket_name = 'mor-platform-public';                                       -- 一行，ACTIVE、PUBLIC
-- 执行后：Platform 存储快照由定时任务刷新（或在「存储管理」任一写操作后立即刷新）；刷新前新路由与新策略不生效。
-- 可重跑：否。回退：见 dml-rollback.sql 第 3 部分。
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @i18n_file_time = UTC_TIMESTAMP(6);
SET @i18n_public_instance = 'MINIO';
SET @i18n_public_bucket = 'mor-platform-public';
SET @ops_bff_private_route_id = 239087906591948800;

INSERT INTO t_file_scan_policy (policy_id, policy_code, revision_no, rule_schema_version, allowed_mime_rules, max_size_bytes,
  effective_from, effective_to, status, active_slot, version, create_time, update_time) VALUES
  (900000000000135021, 'I18N_BUNDLE_JSON', 1, 1, '{"mimeTypes":["application/json"]}', 1048576, @i18n_file_time, NULL,
   'ACTIVE', 1, 1, @i18n_file_time, @i18n_file_time),
  (900000000000135023, 'OPERATIONS_BFF_IMPORT', 1, 1, '{"mimeTypes":["text/csv","application/json"]}', 52428800,
   @i18n_file_time, NULL, 'ACTIVE', 1, 1, @i18n_file_time, @i18n_file_time);

INSERT INTO t_file_scan_requirement (requirement_id, policy_id, requirement_no, scanner_code, scan_type, minimum_scanner_version,
  accepted_result_codes, version, create_time, update_time) VALUES
  (900000000000135022, 900000000000135021, 1, 'PLATFORM_FORMAT', 'FORMAT', '1', '["FORMAT_VALID"]', 1, @i18n_file_time,
   @i18n_file_time),
  (900000000000135024, 900000000000135023, 1, 'PLATFORM_FORMAT', 'FORMAT', '1', '["FORMAT_VALID"]', 1, @i18n_file_time,
   @i18n_file_time);

INSERT INTO t_file_storage_route (route_id, source_system, access_level, bucket_id, scan_policy_code, max_upload_seconds, version,
  create_time, update_time) VALUES
  (900000000000135025, 'platform-support', 'PUBLIC',
   (SELECT b.bucket_id FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
     WHERE i.instance_code = @i18n_public_instance AND b.bucket_name = @i18n_public_bucket AND b.access_level = 'PUBLIC'
       AND b.status = 'ACTIVE' AND i.status = 'ACTIVE'),
   'I18N_BUNDLE_JSON', 300, 1, @i18n_file_time, @i18n_file_time);

-- 旧值：scan_policy_code = 'CARD_BIN_CSV'（max_upload_seconds = 900 不改）。
UPDATE t_file_storage_route
   SET scan_policy_code = 'OPERATIONS_BFF_IMPORT', version = version + 1, update_time = @i18n_file_time
 WHERE route_id = @ops_bff_private_route_id AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'CARD_BIN_CSV';
SELECT ROW_COUNT() AS operations_bff_route_updated; -- 必须为 1，否则 ROLLBACK

COMMIT;
