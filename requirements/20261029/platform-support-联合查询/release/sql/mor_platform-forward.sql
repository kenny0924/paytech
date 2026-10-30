-- =====================================================================================================================
-- Platform Support（mor_platform）· 2026-10-29 联合查询
-- 需求：requirements/20261029/platform-support-联合查询/（technique.md §9.1）
--
-- 内容：
--   1) 新增表 t_query_key_type（17 列，uk_query_key_type_code、idx_query_key_type_status）。
--   2) t_query_template_version 在 constraints_json 之后新增可空列 joint_json；历史版本为空（不参与联合查询），不迁移。
-- 不含数据：首批 26 个键类型与注册块 144 在 platform-support-initial-data.sql 末尾
-- （已初始化的环境执行 2026-10-29-joint-query-registration-144.sql）。
--
-- 可重跑：建表 IF NOT EXISTS；加列前检查 information_schema。
-- 部署顺序：本脚本 → Platform 新镜像；新列可空，旧代码不读写它（旧代码保存模板时不写 joint_json，新列保持为空）。
-- 执行后核对：SHOW CREATE TABLE t_query_key_type; SHOW CREATE TABLE t_query_template_version; 的字段、索引与表/字段注释
-- 须与 platform-support-schema.sql 和 platform-support.pdma.json（图组 data_query）一致。
-- 回滚：同目录 2026-10-29-joint-query-rollback.sql（先回退应用；会丢失已填的联合查询配置与键类型）。
-- 执行账号：具备 CREATE / ALTER 权限的管理账号；platform_app 只有 DML 权限。由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1) 键类型表
CREATE TABLE IF NOT EXISTS `t_query_key_type` (
  `key_type_id` BIGINT NOT NULL COMMENT '服务侧生成的查询键类型 Snowflake 主键。',
  `key_type_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的键类型编码，创建后不可修改；模板参数与列声明的 keyType 引用它。格式为大写字母开头的大写字母、数字与下划线。',
  `key_type_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的键类型名称。',
  `group_name` VARCHAR(32) NOT NULL COMMENT '业务分组显示名，如 收单、支付、入驻、出款；以该键类型为行主体的节点画在同名分组的泳道，分组顺序取组内最小 sort_no，颜色按分组顺序取色板。',
  `value_type` VARCHAR(32) NOT NULL COMMENT '键值类型，决定入口值规范化与可绑定的参数类型。允许值：INTEGER、STRING。',
  `default_role` VARCHAR(32) NOT NULL COMMENT '在模板列上标注该键类型时的默认角色；列上的 keyRole 才是实际角色，可与默认值不同。允许值：LINK、DIMENSION。',
  `entry_allowed` TINYINT NOT NULL DEFAULT 1 COMMENT '是否出现在联合查询入口下拉：1 是，0 否。',
  `value_pattern` VARCHAR(256) NULL COMMENT '入口值的校验正则，整串匹配；为空不校验格式。只校验用户在入口输入的值，不校验展开得到的值；只允许 Java 与 JavaScript 行为一致的语法。',
  `input_hint` VARCHAR(128) NULL COMMENT '入口输入框占位文字与格式不匹配时的提示。',
  `sort_no` INT NOT NULL COMMENT '业务流程顺序号，升序；决定入口下拉与列表顺序，以及链路图泳道（分组取组内最小值）与泳道内节点顺序；页面拖动排序整体重写。',
  `description` VARCHAR(512) NULL COMMENT '键类型含义等补充说明。',
  `status` VARCHAR(32) NOT NULL COMMENT '键类型状态；被 ACTIVE 模板当前版本引用时不能停用。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '键类型乐观并发版本，从 1 开始，每次修改或启停加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该键类型的操作人，取值为 t_user_account.account_id；初始化数据为系统账号。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该键类型的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`key_type_id`),
  UNIQUE KEY `uk_query_key_type_code` (`key_type_code`) COMMENT '保证业务唯一性：key_type_code',
  KEY `idx_query_key_type_status` (`status`, `sort_no`) COMMENT '支持访问路径：status、sort_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='联合查询的业务键类型注册表；模板版本的 joint_json 按编码引用，用于推导跨模板的展开路径与链路图位置。';

-- 2) 模板版本的联合查询配置列（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_query_template_version` ADD COLUMN `joint_json` JSON NULL COMMENT ''联合查询配置：行主体键类型 subjectKeyType、键参数 keyParameter{name,keyType}、列标注 columns[{name,keyType,keyRole}]；为空表示该版本不参与联合查询。与正文、参数、列声明同版本保存，不可变。'' AFTER `constraints_json`',
    'SELECT ''joint_json exists'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_template_version' AND COLUMN_NAME = 'joint_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
