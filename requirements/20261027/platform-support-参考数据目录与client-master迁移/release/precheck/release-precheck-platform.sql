-- 参考数据目录发布：mor_platform 只读核对（SELECT only）。rel-dev 2026-10-27；阶段 7 执行前紧接着重跑，结果应与 baseline.md 第 4 节一致。
SELECT 'tables' AS k, GROUP_CONCAT(table_name ORDER BY table_name) AS v FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('t_currency_spec','t_country_region','t_ref_currency','t_ref_country','t_ref_country_subdivision','t_ref_industry','t_ref_mcc');
SELECT 'routines' AS k, COUNT(*) AS v FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name IN ('ref_data_assert','ref_data_rollback_assert');
SELECT 'currency_version_col' AS k, COUNT(*) AS v FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_currency_spec' AND column_name = 'version';
SELECT 'currency_rows' AS k, id, currency, minor_unit, rounding_mode, status, version FROM t_currency_spec ORDER BY id;
SELECT 'country_seed_rows_2001_2249' AS k, COUNT(*) AS v FROM t_country_region WHERE id BETWEEN 2001 AND 2249;
SELECT 'country_rows_total' AS k, COUNT(*) AS v FROM t_country_region;
SELECT 'ops_target' AS k, target_id, target_service_code, channel, status FROM t_gateway_target WHERE target_id = 900000000000000001;
SELECT 'platform_config_menu_562' AS k, resource_id, resource_code, resource_type, status FROM t_console_resource WHERE resource_id = 900000000000000562;
SELECT 'sibling_menus_under_562' AS k, resource_code, sort_order, status FROM t_console_resource WHERE parent_resource_id = 900000000000000562 ORDER BY sort_order;
SELECT 'classification_edit_perm' AS k, permission_id, status FROM t_permission WHERE permission_code = 'CLIENT_ONBOARDING_CLASSIFICATION_EDIT';
SELECT 'currency_manage_perm_and_routes' AS k, p.permission_code, r.route_code, r.status FROM t_permission p JOIN t_gateway_route r ON r.permission_id = p.permission_id WHERE p.permission_code LIKE 'CURRENCY_SPEC%' ORDER BY r.route_code;
SELECT 'max_block_route_id' AS k, MAX(route_id) AS v FROM t_gateway_route WHERE route_id BETWEEN 900000000000100000 AND 900000000000999999;
SELECT 'gateway_routes_by_status' AS k, status, COUNT(*) AS n FROM t_gateway_route GROUP BY status;
SELECT 'same_shape_ops_get_guard' AS k, r.route_code, g.rule_type, g.dimension, g.capacity, g.concurrency_limit FROM t_gateway_route r JOIN t_gateway_guard_rule g ON g.route_id = r.route_id
 WHERE r.route_code IN ('operations-card-bins-list','client-onboarding-review-case-detail') ORDER BY r.route_code, g.dimension, g.rule_type;
SELECT 'i18n_apps' AS k, a.application_code, a.current_release_id, (SELECT release_no FROM t_i18n_release x WHERE x.release_id = a.current_release_id) AS current_release_no,
 (SELECT MAX(release_no) FROM t_i18n_release x WHERE x.application_id = a.application_id) AS max_release_no, a.required_locales, a.coverage_threshold_percent, a.key_set_revision, a.content_revision
 FROM t_i18n_application a ORDER BY a.application_code;
SELECT 'i18n_target_namespaces' AS k, a.application_code, n.namespace_code, n.sort_order FROM t_i18n_namespace n JOIN t_i18n_application a ON a.application_id = n.application_id
 WHERE n.namespace_code IN ('refdata','reference-data') ORDER BY 2,3;
SELECT 'i18n_ns_max_sort' AS k, a.application_code, COUNT(*) AS namespaces, MAX(n.sort_order) AS max_sort FROM t_i18n_namespace n JOIN t_i18n_application a ON a.application_id = n.application_id GROUP BY a.application_code;
SELECT 'public_buckets' AS k, b.bucket_id, b.bucket_name, b.access_level, b.status, b.last_check_result, b.public_base_url IS NOT NULL AS has_public_url FROM t_file_storage_bucket b ORDER BY b.bucket_id;
SELECT 'public_routes' AS k, source_system, access_level, bucket_id, scan_policy_code FROM t_file_storage_route WHERE access_level = 'PUBLIC';
