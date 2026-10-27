-- 2026-10-27 platform-support：参考数据目录与 client-master 迁移（client-master，DML；技术方案第 6.3 节、S-04、S-06）。
-- 需求：requirements/20261027/platform-support-参考数据目录与client-master迁移/technique.md；决定：agent-docs/decisions/2026-10-27-platform-reference-data.md。
-- 内容：
--   1. 删除 t_catalog_item 中由 Platform 参考数据接管的 48 行：INDUSTRY 10、CURRENCY 4、STATE_OR_PROVINCE 34。
--   2. Schema 字段 business.currency（8 个 Schema 版本）的 catalog_code CURRENCY → REF_CURRENCY。
--   3. 地址格式组件 stateOrProvince（AE_POBOX、EG_STANDARD 及其他引用者）的 catalog_code STATE_OR_PROVINCE → REF_COUNTRY_SUBDIVISION。
--   4. 存量地址 t_party_address.state_or_province 中 ^(AE|EG)_[A-Z0-9]+$ 的目录码转换为 ISO 3166-2 连字符格式（AE_DU → AE-DU），
--      存量均为测试数据（延续切片 1 的“存量按重写”）；执行前后各输出一次计数，记录到执行日志。
-- 前置：Platform 参考数据（t_ref_*、/platform/v1/reference-data/*）已上线且 AED / SAR / EGP 为 ACTIVE；
--       本文件必须与识别 REF_ 目录码的 client-master 镜像同一窗口上线（旧镜像会把 REF_ 当作本库目录，选项为空、提交全部报不在目录）。
-- 不改表结构：catalog_code 为 VARCHAR(64)，可容纳 REF_ 前缀。
-- 幂等：重复执行为空操作（删除与条件更新都按旧值匹配）。
-- 注意：2026-10-27-onboarding-country-pack.sql 为历史增量（INSERT … ON DUPLICATE KEY UPDATE 不改已存在行），本文件之后不得重跑，
--       否则会重新插入这 48 行目录项（Schema 字段与地址组件不会被改回）；新环境以 client-master-initial-data.sql 为准。
-- 回退：2026-10-27-reference-data-catalog-rollback.sql（须与旧镜像同时回退）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SELECT 'before' AS `phase`,
  (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_code` IN ('INDUSTRY', 'CURRENCY', 'STATE_OR_PROVINCE')) AS `retired_catalog_items`,
  (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `catalog_code` = 'CURRENCY') AS `currency_schema_fields`,
  (SELECT COUNT(*) FROM `t_address_format_component` WHERE `catalog_code` = 'STATE_OR_PROVINCE') AS `subdivision_components`,
  (SELECT COUNT(*) FROM `t_party_address` WHERE REGEXP_LIKE(`state_or_province`, '^(AE|EG)_[A-Z0-9]+$', 'c')) AS `underscore_addresses`,
  (SELECT COUNT(*) FROM `t_party_address` WHERE REGEXP_LIKE(`state_or_province`, '^(AE|EG)-[A-Z0-9]+$', 'c')) AS `iso_addresses`;

START TRANSACTION;

DELETE FROM `t_catalog_item` WHERE `catalog_code` IN ('INDUSTRY', 'CURRENCY', 'STATE_OR_PROVINCE');

UPDATE `t_onboarding_schema_field` SET `catalog_code` = 'REF_CURRENCY', `update_time` = UTC_TIMESTAMP(6)
WHERE `catalog_code` = 'CURRENCY';

UPDATE `t_address_format_component` SET `catalog_code` = 'REF_COUNTRY_SUBDIVISION', `update_time` = UTC_TIMESTAMP(6)
WHERE `catalog_code` = 'STATE_OR_PROVINCE';

UPDATE `t_party_address`
SET `state_or_province` = CONCAT(LEFT(`state_or_province`, 2), '-', SUBSTRING(`state_or_province`, 4)), `update_time` = UTC_TIMESTAMP(6)
WHERE REGEXP_LIKE(`state_or_province`, '^(AE|EG)_[A-Z0-9]+$', 'c');

COMMIT;

SELECT 'after' AS `phase`,
  (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_code` IN ('INDUSTRY', 'CURRENCY', 'STATE_OR_PROVINCE')) AS `retired_catalog_items`,
  (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `catalog_code` = 'REF_CURRENCY') AS `ref_currency_schema_fields`,
  (SELECT COUNT(*) FROM `t_address_format_component` WHERE `catalog_code` = 'REF_COUNTRY_SUBDIVISION') AS `ref_subdivision_components`,
  (SELECT COUNT(*) FROM `t_party_address` WHERE REGEXP_LIKE(`state_or_province`, '^(AE|EG)_[A-Z0-9]+$', 'c')) AS `underscore_addresses`,
  (SELECT COUNT(*) FROM `t_party_address` WHERE REGEXP_LIKE(`state_or_province`, '^(AE|EG)-[A-Z0-9]+$', 'c')) AS `iso_addresses`;
