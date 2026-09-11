-- 仅供本机 local-environment 127.0.0.1:13306/mor_risk；未执行。
-- 当前只读快照无历史规则版本，故 manifest 明确为空；执行前必须重新核对。
-- 先停止 local Risk 写入并备份 mor_risk，任何失败立即停止，不使用 --force。
-- 从本目录启动 mysql --batch，先执行 ddl.sql，再执行本文件。
SET @risk_rule_set_manifest_json = '[]';
SOURCE dml.sql;
