-- 工作流附件内嵌预览跟进 · mor-test：#28 附件下载授权路由 900000000000127432 允许可选 Query disposition（INLINE | ATTACHMENT）。
-- 内容同块 127 正式文件（main 42879401 platform-support-initial-data.sql 第 6293 行）与本机增量
-- verification/local-e2e/41-workflow-download-disposition.sql，只把库名保护改为 mor_platform。
-- 路由 version +1 使 Gateway 快照重新发布。期望影响行数 1 / 1。
-- 本文件不含 COMMIT（同上一个发布包的 test-activate.sql 约定）：
--   · 试运行：scripts/apply_sql.sh <B> query-param-dry —— 断开即隐式回滚，只看两个数；
--   · 提交：  scripts/apply_sql.sh <B> query-param     —— 在本文件后追加 COMMIT；guard 不符时报错，客户端停止，事务回滚。
-- 前置：sql/00-precheck.sql 全部 ok=1。回滚：sql/90-rollback.sql。
START TRANSACTION;
SET @wfi_time = UTC_TIMESTAMP(6);
INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT * FROM (VALUES ROW(900000000000127030, 900000000000127432, 'disposition')) AS v
 WHERE DATABASE() = 'mor_platform';
SET @wfi_param = ROW_COUNT();
UPDATE t_gateway_route SET version = version + 1, update_time = @wfi_time
 WHERE DATABASE() = 'mor_platform' AND route_id = 900000000000127432 AND status = 'ACTIVE';
SET @wfi_route = ROW_COUNT();
SELECT @wfi_param AS query_param_1, @wfi_route AS route_1,
       (SELECT version FROM t_gateway_route WHERE route_id = 900000000000127432) AS route_version_after;
-- guard：两个数不符时本语句报错（子查询多行），mysql 客户端停止，未提交的事务随断开回滚。
SELECT IF(@wfi_param = 1 AND @wfi_route = 1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
