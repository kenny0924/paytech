-- payout 收款账户与余额解释（S1）· mor-test 建库与服务账号（root，不指定库）。照 test-environment/scripts/initialize.py --create-service-databases 的三句写法。
-- 密码不在本文件：执行前由包装命令在同一会话注入 SET @payout_app_password = '<值>';，值取自 test-environment/.env.test 的
--   MOR_PAYOUT_DB_PASSWORD（prepare.py 的 V2_DATABASE_PASSWORDS 追加该键并生成 secrets.token_hex(24)；W8 compose 以 ${MOR_PAYOUT_DB_PASSWORD:?prepare test secrets} 引用）。
--   包装（runbook）：{ printf "SET @payout_app_password='%s';\n" "$(grep '^MOR_PAYOUT_DB_PASSWORD=' test-environment/.env.test | cut -d= -f2-)"; cat 10-…sql; } | sqlr
--   密码只经管道进入 mysql 客户端，不进终端、日志或文件；本文件结尾不回显变量。
-- 权限：只 DML（SELECT, INSERT, UPDATE, DELETE ON mor_payout.*），与 tax_app / reconciliation_app 相同；DDL 由 root 执行 12-mor_payout-ddl.sql。
-- 重复执行：CREATE DATABASE / CREATE USER 均 IF NOT EXISTS，已有账号的密码不变；GRANT 幂等。
-- 前置检查：01-mysql-payout-database-precheck.sql；执行后核对：11-mysql-payout-database-verify.sql；回滚：92-mysql-payout-database-rollback.sql。
-- data-query：S1 不登记 mor_payout 数据源、不给 data_query_demo_reader 授权（需求无查询模板；后续需要时另行登记）。
-- 守卫：变量缺失或不符合 initialize.py 的密码格式 [A-Za-z0-9._@%+=:-]{16,128} → SET 标量子查询返回两行 → ERROR 1242，不做任何改动。
SET @payout_password_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE @payout_app_password IS NULL OR NOT REGEXP_LIKE(@payout_app_password, '^[A-Za-z0-9._@%+=:-]{16,128}$', 'c'));
CREATE DATABASE IF NOT EXISTS `mor_payout` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET @payout_create_user = CONCAT('CREATE USER IF NOT EXISTS ''payout_app''@''%'' IDENTIFIED BY ''', @payout_app_password, '''');
PREPARE payout_create_user_stmt FROM @payout_create_user;
EXECUTE payout_create_user_stmt;
DEALLOCATE PREPARE payout_create_user_stmt;
SET @payout_create_user = NULL, @payout_app_password = NULL;
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_payout`.* TO 'payout_app'@'%';
