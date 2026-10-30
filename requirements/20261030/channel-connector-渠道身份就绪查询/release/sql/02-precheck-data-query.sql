-- REQ-02 · GOLIVE_MID_READY 登记前只读核对（mor_platform，root READ ONLY 会话）：数据源存在且 ACTIVE、模板编码未占用；
-- 并列出同数据源已有模板与 REQ-01 先例模板的约束格式，供后台登记时照抄 maxRows / timeout 写法。可重跑。
SELECT data_source_id, data_source_code, engine_type, host, port, database_name, status, last_check_result, last_check_time
  FROM t_data_source WHERE data_source_code = 'MOR_CHANNEL_CONNECTOR_REPLICA';
SELECT COUNT(*) AS golive_mid_ready_templates FROM t_query_template WHERE template_code = 'GOLIVE_MID_READY';
SELECT t.template_code, t.status, t.current_version_no, ds.data_source_code
  FROM t_query_template t JOIN t_data_source ds ON ds.data_source_id = t.data_source_id
 WHERE t.template_code LIKE 'GOLIVE%' OR ds.data_source_code = 'MOR_CHANNEL_CONNECTOR_REPLICA' ORDER BY t.template_code;
SELECT t.template_code, v.version_no, v.parameters_json, v.columns_json, v.constraints_json
  FROM t_query_template t JOIN t_query_template_version v ON v.template_id = t.template_id AND v.version_no = t.current_version_no
 WHERE t.template_code LIKE 'GOLIVE%' ORDER BY t.template_code LIMIT 3;
