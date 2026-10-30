-- =====================================================================================================================
-- 联合查询 · 子行键类型（technique.md 8.7 修订 c，2026-10-30）
-- 目标库：Platform Support（mor_platform）。只给已执行过注册块 144（26 个键类型）的环境补 3 行；新初始化的环境由
-- platform-support-initial-data.sql / 2026-10-29-joint-query-registration-144.sql 直接得到 29 个，不执行本文件。
-- 子行模板（账户分录、凭证分录、对账单明细）以自身键类型为行主体，避免被“按自身 ID 查”跳过规则误判；默认角色 LINK、不作入口，
-- sort_no 紧跟父键类型（记账批次 110、会计凭证 120、平台流水 140）。键类型被引用前不影响任何模板。可重跑（INSERT IGNORE + 编码唯一）。
-- 也可经运营“键类型”页面或 K2 接口新建（ID 为 Snowflake），二者选一；回滚：2026-10-30-joint-query-child-key-types-rollback.sql。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
SET @joint_query_time = UTC_TIMESTAMP(6);
SET @joint_query_actor_id = 1;
INSERT IGNORE INTO t_query_key_type (key_type_id,key_type_code,key_type_name,group_name,value_type,default_role,entry_allowed,value_pattern,input_hint,sort_no,description,status,version,created_by,updated_by,create_time,update_time) VALUES
  (900000000000144127, 'ACCOUNT_ENTRY_ID', '账户分录 ID', '账户', 'INTEGER', 'LINK', 0, NULL, NULL, 111, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144128, 'JOURNAL_LINE_ID', '凭证分录 ID', '总账', 'INTEGER', 'LINK', 0, NULL, NULL, 121, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time),
  (900000000000144129, 'STATEMENT_LINE_ID', '对账单明细 ID', '对账', 'INTEGER', 'LINK', 0, NULL, NULL, 141, NULL, 'ACTIVE', 1, @joint_query_actor_id, @joint_query_actor_id, @joint_query_time, @joint_query_time);
COMMIT;

SELECT key_type_id, key_type_code, group_name, sort_no, status FROM t_query_key_type
WHERE key_type_code IN ('ACCOUNT_ENTRY_ID', 'JOURNAL_LINE_ID', 'STATEMENT_LINE_ID') ORDER BY sort_no;
