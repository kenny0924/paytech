-- 2026-10-27 platform-support：参考数据目录与 client-master 迁移（client-master，仅注释 DDL）。
-- 内容：同步 client-master-schema.sql / client-master.pdma.json 中 t_catalog_item 表注释及三处 catalog_code 列注释
--       （平台参考数据不得在 t_catalog_item 维护；REF_ 保留码引用 Platform）。列类型、可空、字符集与排序规则不变。
-- 执行：与 2026-10-27-reference-data-catalog.sql 同一窗口，先后顺序不限；可重复执行。
-- 回退：不需要（只改注释）；如需恢复旧文字，按上一版 client-master-schema.sql 的注释重新执行同样的语句。
SET NAMES utf8mb4;

ALTER TABLE `t_catalog_item` MODIFY COLUMN `catalog_code` VARCHAR(64) NOT NULL COMMENT '目录代码，如 BUSINESS_MODEL、REGISTRATION_AUTHORITY、CITY；不得为 REF_ 开头的保留码，也不得承载国家、币种、行政区或行业等 Platform 参考数据。取值来源：目录 DML；非固定枚举。', COMMENT='通用目录项；uk_catalog_item(catalog_code,jurisdiction_code,item_code)；SELECT 控件、属性与地址组件的本服务取值来源；国家、币种、一级行政区、行业与 MCC 等全平台参考数据不得在本表维护（Platform 参考数据为 Owner），Schema 以 REF_ 保留目录码引用并在提交时向 Platform 校验；本表不得出现 REF_ 开头的目录码';
ALTER TABLE `t_address_format_component` MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT '组件取值必须属于的目录代码，如城市；REF_COUNTRY_SUBDIVISION 表示 Platform 一级行政区（须属于地址国家）；为空表示自由文本。取值来源：t_catalog_item.catalog_code 或 REF_ 保留码；非固定枚举。';
ALTER TABLE `t_onboarding_schema_field` MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT 'SELECT 控件的取值目录代码；REF_ 开头时为 Platform 参考数据（如 REF_CURRENCY）；其他控件为空。取值来源：t_catalog_item.catalog_code 或 REF_ 保留码；非固定枚举。';
