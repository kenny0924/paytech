-- RCO-16 Gateway 数据库动态路由：待评审、待人工应用的表注释 DDL。
-- 目标：经单独确认的 local/test mor_platform；本文件本身不授权执行。
-- 执行前核对环境、备份 t_gateway_target 和 SHOW CREATE TABLE 输出。
-- 保持 10 列、所有索引及业务数据；不新建表，不改变业务字段。
-- 预期旧表注释：
-- 保存已通过架构与安全审批的公共入口目标当前状态；Gateway 仍以静态 Approved Target Registry 复核身份三元组
-- 若已为目标注释则跳过；若为第三种注释，先核对其他变更，不覆盖。
-- DDL 隐式提交，不能依赖事务 ROLLBACK；恢复注释使用已核对的备份值。

USE mor_platform;

ALTER TABLE t_gateway_target
    COMMENT = '保存由 Platform 统一管理的公共入口目标当前配置；Gateway 从完整快照读取目标身份与服务发现名称并执行通用校验';

-- 执行后核对表注释、列、索引与行数；本 DDL 不启用任何 Target/Route。
