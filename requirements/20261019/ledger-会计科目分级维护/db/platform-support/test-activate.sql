-- ledger 会计科目分级维护：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 财务块 97 / 98 / 99 并已激活（20261018 需求 release/test-activate.sql 使 FINANCE_ACCOUNTING_READ /
-- MANAGE 授权 ACTIVE；20261019 明细账需求 release/test-activate.sql）；已执行「Operations finance chart maintenance and
-- ledger balance query console」块 91（8 条路由、8 个查询参数；不含试算运行路由与按钮）并 COMMIT；
-- ledger、account、Platform、Operations BFF、Operations Web 容器已换成候选镜像并 healthy。
-- 激活 8 条路由（无新增 Console Resource）；角色授权已存在不重复；递增 3 条已补查询参数的既有路由 version（只改 version，不改状态），
-- 使 Gateway Snapshot 带上新参数；递增 PLATFORM_ADMIN 版本使会话重新计算导航。
-- 预期影响行数：8 / 3 / 1。
START TRANSACTION;
SET @fin_chart_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@fin_chart_act_time
 WHERE route_id BETWEEN 900000000000091401 AND 900000000000091408 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_gateway_route SET version=version+1, update_time=@fin_chart_act_time
 WHERE route_id IN (900000000000097415, 900000000000097410, 900000000000099401);
SELECT ROW_COUNT() AS extended_routes;
UPDATE t_role SET version=version+1, update_time=@fin_chart_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
