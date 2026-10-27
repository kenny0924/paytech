-- 回退 2026-10-27-reference-data-catalog-comment.sql（client-master，仅注释 DDL）：恢复为 mor-test 2026-10-27 备份中的原注释。
-- rel-dev 2026-10-27 依 /Users/kenny/mor-backups/refdata-20261027/mortest-mor_client_master-20261027T125606Z.sql.gz 的 SHOW CREATE TABLE 写出；
-- 列类型、可空、字符集与排序规则不变（与备份一致）。可重复执行。可选：只有要求库结构与备份逐字一致时执行。
SET NAMES utf8mb4;
ALTER TABLE `t_catalog_item` MODIFY COLUMN `catalog_code` VARCHAR(64) NOT NULL COMMENT '目录代码，如 INDUSTRY、BUSINESS_MODEL、REGISTRATION_AUTHORITY、CITY。取值来源：目录 DML；非固定枚举。', COMMENT='通用目录项；uk_catalog_item(catalog_code,jurisdiction_code,item_code)；SELECT 控件、属性与地址组件的取值来源；行业与经营模式目录由原 application.yml 迁入，运营分类与行业目录接口同样读取';
ALTER TABLE `t_address_format_component` MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT '组件取值必须属于的通用目录代码，如省、城市；为空表示自由文本。取值来源：t_catalog_item.catalog_code；非固定枚举。';
ALTER TABLE `t_onboarding_schema_field` MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT 'SELECT 控件的取值目录代码；其他控件为空。取值来源：t_catalog_item.catalog_code；非固定枚举。';
