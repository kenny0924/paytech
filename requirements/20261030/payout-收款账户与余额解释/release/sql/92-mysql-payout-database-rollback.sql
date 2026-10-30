-- payout S1 · 删除 mor_payout 库与 payout_app 账号（root，不指定库）。只在 91 之后、库内已无任何表时执行；否则拒绝（ERROR 1242），不删库。
-- 一般不需要：technique 第 9 节回退 = 停服务 + 保留库。只在首建当次决定整体撤回、且库 / 账号确由本需求创建（01 预检为 0 行）时使用。
-- 执行后 .env.test 中的 MOR_PAYOUT_DB_PASSWORD 保留（再次首建时 10 会以同一密码重建账号）。重复执行：IF EXISTS，为空操作。
SET @payout_table_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'mor_payout'));
DROP USER IF EXISTS 'payout_app'@'%';
DROP DATABASE IF EXISTS `mor_payout`;
SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'mor_payout') AS `schema_left`,
       (SELECT COUNT(*) FROM mysql.user WHERE user = 'payout_app') AS `account_left`;
