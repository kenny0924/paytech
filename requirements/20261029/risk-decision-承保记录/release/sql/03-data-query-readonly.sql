-- REQ-03 · data-query 现状只读（runbook ⑨ 依据）。READ ONLY 会话，mor_platform。
SET NAMES utf8mb4;
SELECT data_source_id, data_source_code, engine_type, host, port, database_name, username, status, last_check_result, last_check_time
FROM t_data_source ORDER BY data_source_id;
SELECT t.template_code, d.data_source_code, t.status, t.current_version_no FROM t_query_template t
JOIN t_data_source d ON d.data_source_id = t.data_source_id ORDER BY t.template_code;
-- 本需求编码未占用：期望 0 / 0
SELECT (SELECT COUNT(*) FROM t_data_source WHERE data_source_code = 'MOR_RISK_DECISION_REPLICA') AS `source_taken`,
       (SELECT COUNT(*) FROM t_query_template WHERE template_code = 'GOLIVE_UNDERWRITING_APPROVED') AS `template_taken`;
