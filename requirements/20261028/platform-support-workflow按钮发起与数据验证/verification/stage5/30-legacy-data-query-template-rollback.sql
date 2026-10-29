-- 回滚 30-legacy-data-query-template.sql（只在旧代码重新上线时需要）。
START TRANSACTION;
UPDATE t_workflow_action_template
SET config_json = JSON_OBJECT('passRule', JSON_OBJECT(), 'queryCode', 'E2E_PLACEHOLDER', 'parameters', JSON_ARRAY(), 'evidenceColumns', JSON_ARRAY()),
    status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE action_template_id = 238899130888359936;
COMMIT;
