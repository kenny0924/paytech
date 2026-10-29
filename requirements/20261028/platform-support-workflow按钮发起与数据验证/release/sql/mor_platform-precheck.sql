-- REQ-05 · mor_platform 发布前只读预检（runbook ④；阶段 6 读数见 baseline.md 第 4 节）。在 READ ONLY 会话执行。
SET NAMES utf8mb4;
-- 1) 同一定义下按原文重复的 RUNNING business_key：必须 0 行
SELECT `definition_id`, BINARY `business_key` AS `business_key`, COUNT(*) AS `running`
FROM `t_workflow_instance` WHERE `status` = 'RUNNING' AND `business_key` IS NOT NULL
GROUP BY `definition_id`, BINARY `business_key` HAVING COUNT(*) > 1;
-- 2) 实例分布（阶段 6：COMPLETED 4 / RUNNING 3 / TERMINATED 2，均带 business_key）
SELECT `status`, COUNT(*) AS `n`, SUM(`business_key` IS NOT NULL) AS `keyed` FROM `t_workflow_instance` GROUP BY `status`;
-- 3) 新列 / 索引尚不存在：期望 0 / 0
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND ((TABLE_NAME = 't_workflow_instance' AND COLUMN_NAME = 'active_slot')
            OR (TABLE_NAME = 't_workflow_action_run' AND COLUMN_NAME IN ('last_check_time','check_result','check_detail','check_failure_count','last_check_success_time')))) AS `new_columns`,
       (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
          AND INDEX_NAME IN ('uk_workflow_inst_active','idx_workflow_action_check')) AS `new_indexes`;
-- 4) 现存 DATA_QUERY 动作模板 / 动作运行：期望 0 / 0（否则旧形状配置须先处理，见 stage5 报告）
SELECT (SELECT COUNT(*) FROM `t_workflow_action_template` WHERE `action_type` = 'DATA_QUERY') AS `dq_templates`,
       (SELECT COUNT(*) FROM `t_workflow_action_run` WHERE `action_type` = 'DATA_QUERY') AS `dq_runs`;
-- 5) 注册块 142 区间未占用：期望 0 / 0 / 0 / 0；新 route_code 与 (方法, 路径) 未占用：期望 0
SELECT (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000142001 AND 900000000000142999) AS `routes`,
       (SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000142001 AND 900000000000142999) AS `headers`,
       (SELECT COUNT(*) FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000142001 AND 900000000000142999) AS `queries`,
       (SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000142001 AND 900000000000142999) AS `guards`,
       (SELECT COUNT(*) FROM t_gateway_route WHERE route_code IN ('operations-workflow-data-query-check','operations-workflow-data-query-template-list','operations-workflow-template-validation')
          OR (channel = 'OPERATIONS' AND http_method = 'POST' AND external_path_template IN ('/api/operations/workflow/v1/action-runs/{actionRunId}/data-query-check','/api/operations/workflow-admin/v1/action-templates/validation'))
          OR (channel = 'OPERATIONS' AND http_method = 'GET' AND external_path_template = '/api/operations/workflow-admin/v1/data-query-templates')) AS `code_or_path_taken`;
-- 6) 依赖：权限 127103 / 127105 与 target 900000000000000001 均 ACTIVE；路由计数（阶段 6：ACTIVE 616 / DISABLED 6）
SELECT permission_id, permission_code, status FROM t_permission WHERE permission_id IN (900000000000127103, 900000000000127105);
SELECT target_id, status FROM t_gateway_target WHERE target_id = 900000000000000001;
SELECT SUM(status = 'ACTIVE') AS `active`, SUM(status = 'DISABLED') AS `disabled`, MAX(route_id) AS `max_route` FROM t_gateway_route;
