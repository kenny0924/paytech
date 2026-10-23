-- 草稿（未执行）：块 130 写入规则编辑 / 删除两条路由的路径加 /{accessLevel}，只在 mor_platform 执行，客户端须 utf8mb4。
-- 新 Platform 与 ops-bff 只接受 PUT|DELETE /storage-admin/v1/routes/{sourceSystem}/{accessLevel}；旧路径在新版本中不存在。
-- 顺序：与 platform-support、ops-bff、ops-web 新镜像同一窗口执行（旧镜像下改路径后两条路由会 404，反之新页面调旧路径 404），
-- 执行后等 Gateway Snapshot 刷新（≤ 60 秒）。只改路径，状态、权限、Header、Guard 不变。预期影响行数：2。
-- 回滚：block130-route-path-rollback 段（本文件末尾注释）。
START TRANSACTION;
SET @route_path_change_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route
   SET external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}',
       downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}/{accessLevel}',
       version = version + 1, update_time = @route_path_change_time
 WHERE route_id IN (900000000000130411, 900000000000130412)
   AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}'
   AND downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}';
SELECT ROW_COUNT() AS routes;
COMMIT;
-- 回滚（回退到旧镜像时执行，预期 2 行）：
--   START TRANSACTION;
--   UPDATE t_gateway_route
--      SET external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}',
--          downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}',
--          version = version + 1, update_time = UTC_TIMESTAMP(6)
--    WHERE route_id IN (900000000000130411, 900000000000130412)
--      AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}';
--   COMMIT;
