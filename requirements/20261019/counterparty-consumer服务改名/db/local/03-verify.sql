-- counterparty 改名 · 本机 · 第 3 步：逐表行数对比（old_rows 必须等于 new_rows）。
SELECT 't_customer' AS table_name,
       (SELECT COUNT(*) FROM `mor_consumer`.`t_customer`) AS old_rows,
       (SELECT COUNT(*) FROM `mor_counterparty`.`t_customer`) AS new_rows
UNION ALL
SELECT 't_compensation_event',
       (SELECT COUNT(*) FROM `mor_consumer`.`t_compensation_event`),
       (SELECT COUNT(*) FROM `mor_counterparty`.`t_compensation_event`);

-- 结构核对：新库的表与列数（应为 t_customer 10 列、t_compensation_event 15 列）。
SELECT table_name, COUNT(*) AS column_count
FROM information_schema.columns
WHERE table_schema = 'mor_counterparty'
GROUP BY table_name
ORDER BY table_name;
