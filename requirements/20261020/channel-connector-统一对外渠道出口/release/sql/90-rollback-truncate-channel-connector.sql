-- 统一对外渠道出口第一段 · 回退：清空 mor_channel_connector 中导入的 10 张协议表（技术方案第 3.5 节步骤 2 的回退）。
-- 适用：数据复制核对不一致，或 channel-connector / ops-bff 回滚到旧镜像之后（旧镜像不读这 10 张表）。
-- 前提：channel-connector-service 已是回滚镜像（sha256:4d31d529…），或尚未替换；Platform 的 10 张原表仍在。
-- 影响：新版本运行期间在 channel-connector 写入的外部 API 配置、验证用例与运行会丢失（回退前先用 sql/00-precheck.sql 的
--   P3 口径对比，确认是否有新写入；有则先导出留档）。t_channel_config_change 中新类型的变更记录不在本文件范围，保留。
-- 执行：root，目标库 mor_channel_connector；可重跑。
TRUNCATE TABLE `t_integration_verify_run_step`;
TRUNCATE TABLE `t_integration_verify_run`;
TRUNCATE TABLE `t_integration_verify_case`;
TRUNCATE TABLE `t_integration_verify_account`;
TRUNCATE TABLE `t_integration_standard_api`;
TRUNCATE TABLE `t_integration_script`;
TRUNCATE TABLE `t_integration_api`;
TRUNCATE TABLE `t_integration`;
TRUNCATE TABLE `t_api_field`;
TRUNCATE TABLE `t_standard_api`;
