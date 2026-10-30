-- payout 收款账户与余额解释（S1）· mor-test 建库前只读预检。root 只读会话执行（--init-command="SET SESSION TRANSACTION READ ONLY"），不指定库。
-- 期望：payout_schema 0 行；payout_account 0 行；服务器 8.4.x、utf8mb4 / utf8mb4_0900_ai_ci（与 mor_tax、mor_reconciliation 首建一致）。
-- 任一不符（库或账号已存在）→ 停止，先查明是谁建的，不要覆盖其密码或授权。
SELECT schema_name AS `payout_schema`, default_character_set_name, default_collation_name
FROM information_schema.schemata WHERE schema_name = 'mor_payout';
SELECT user AS `payout_account`, host, account_locked FROM mysql.user WHERE user = 'payout_app';
SELECT @@version AS `server_version`, @@character_set_server AS `charset`, @@collation_server AS `collation`;
-- 对照：同类服务账号的授权粒度（期望 SELECT, INSERT, UPDATE, DELETE ON `mor_<服务>`.*）
SELECT grantee, table_schema, GROUP_CONCAT(privilege_type ORDER BY privilege_type) AS `privileges`
FROM information_schema.schema_privileges WHERE grantee IN ('''tax_app''@''%''', '''reconciliation_app''@''%''', '''payout_app''@''%''')
GROUP BY grantee, table_schema ORDER BY grantee;
