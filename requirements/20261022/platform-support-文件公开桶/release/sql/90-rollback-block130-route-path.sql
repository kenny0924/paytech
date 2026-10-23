-- 文件公开桶 · 回滚：块 130 写入规则编辑 / 删除两条路由的路径改回 /{sourceSystem}（10-block130-route-path.sql 末尾注释的去注释版本）。
-- 只在 ops-web 与 ops-bff 都回滚到旧镜像时执行（旧 Web / 旧 BFF 只认旧路径）；只回滚 Platform 时不要执行。
-- 只在 mor_platform 执行，客户端 utf8mb4。预期 2 行；执行后等 Gateway Snapshot 刷新（≤ 60 秒）。
START TRANSACTION;
UPDATE t_gateway_route
   SET external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}',
       downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}',
       version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE route_id IN (900000000000130411, 900000000000130412)
   AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}';
SELECT ROW_COUNT() AS routes;
COMMIT;
