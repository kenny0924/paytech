-- platform-support：删除已迁往 channel-connector 的外部 API 配置与沙箱验证 10 张表（需求 requirements/20261020/channel-connector-统一对外渠道出口，
-- 技术方案第 3.4 节第 4 步、第 3.5 节第 5 步、第 4 节）。审阅后由 root 手工执行。
-- 执行时机：第一段 mor-test 验收后执行。验收通过之前保留原表，便于回退；部署删除了 integration 模块的 platform-support 之后再执行。
-- 目标库：mor_platform。前提：10 张表的数据已导入 mor_channel_connector 并逐表核对行数与 CHECKSUM TABLE。
-- 执行前备份：先用 mysqldump 单独备份这 10 张表（结构与数据），确认备份文件可读后再执行。
-- 影响：DROP 10 张表，不改其他表；执行后为 47 张表，与 platform-support-schema.sql、platform-support.pdma.json 一致。
--   initial-data 中 EXTERNAL_API_* 权限、菜单与 Route 注册行不在本文件范围，继续保留。
-- 可重跑性：不可重跑（表不存在时 DROP 报错）；部分执行失败时先确认已删除的表，再从失败处继续。
-- 回退：用执行前的备份恢复 10 张表（结构与数据），并恢复部署前的 platform-support 镜像。

DROP TABLE `t_integration_verify_run_step`;
DROP TABLE `t_integration_verify_run`;
DROP TABLE `t_integration_verify_case`;
DROP TABLE `t_integration_verify_account`;
DROP TABLE `t_integration_standard_api`;
DROP TABLE `t_integration_script`;
DROP TABLE `t_integration_api`;
DROP TABLE `t_integration`;
DROP TABLE `t_api_field`;
DROP TABLE `t_standard_api`;
