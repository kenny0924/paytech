-- REQ-05 阶段 5（本机 mor_platform_v2e2e 专用）：作废 e2e 库唯一一条旧形状 DATA_QUERY 动作模板（technique 12.2）。
-- 旧配置 {"passRule": {}, "queryCode": "E2E_PLACEHOLDER", "parameters": [], "evidenceColumns": []} 新读取器不再接受；
-- 只被草稿 WF_E2E_BAD_093951 v1 引用（未发布）。改为新形状占位并停用。回滚：30-legacy-data-query-template-rollback.sql。
START TRANSACTION;
UPDATE t_workflow_action_template
SET config_json = JSON_OBJECT('queryCode', 'E2E_PLACEHOLDER', 'parameters', JSON_ARRAY(), 'outputs', JSON_ARRAY()),
    status = 'INACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE action_template_id = 238899130888359936 AND JSON_CONTAINS_PATH(config_json, 'one', '$.passRule');
COMMIT;
SELECT action_template_id, status, version, config_json FROM t_workflow_action_template WHERE action_template_id = 238899130888359936;
