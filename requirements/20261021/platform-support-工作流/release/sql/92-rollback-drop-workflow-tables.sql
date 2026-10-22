-- 工作流 10 张表：回退（可选，回滚最后一步）。只在镜像已回滚到旧 Platform、且工作流表中没有需要保留的数据时执行。
-- 旧 Platform 不读写这些表，保留它们无害；一般不执行本文件。DDL 隐式提交、不可回滚，执行前确认备份目录有 mor_platform 整库备份。
SELECT 'about to drop' AS note, DATABASE() AS db,
       (SELECT COUNT(*) FROM t_workflow_instance) AS instances, (SELECT COUNT(*) FROM t_workflow_definition) AS definitions;
DROP TABLE `t_workflow_callback`;
DROP TABLE `t_workflow_transfer`;
DROP TABLE `t_workflow_approval`;
DROP TABLE `t_workflow_verification`;
DROP TABLE `t_workflow_action_run`;
DROP TABLE `t_workflow_node_run`;
DROP TABLE `t_workflow_instance`;
DROP TABLE `t_workflow_action_template`;
DROP TABLE `t_workflow_definition_version`;
DROP TABLE `t_workflow_definition`;
