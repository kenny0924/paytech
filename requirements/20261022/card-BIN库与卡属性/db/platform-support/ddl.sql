-- 未执行草案（2026-10-22 阶段 3 评审稿）：card：BIN 库与卡属性，目标库 mor_platform（platform-support-service）。
-- 性质：需求级增量 DDL，新增国家区域主数据表（E3，仿 t_currency_spec）。将同步为
--       paytech/platform-support/docs/database/sql/changes/2026-10-2x-country-region.sql，并更新 PDManer platform-support.pdma.json、
--       platform-support-schema.sql、arch-rules table-ownership.yaml（mor_platform 表数 +1）。
-- 顺序：本文件 → db/platform-support/dml.sql 第 1 段（国家区域种子）。可重跑：否。
-- 回退：DROP TABLE t_country_region;（须先回退读取它的 Platform 镜像）
SET NAMES utf8mb4;

CREATE TABLE `t_country_region` (
  `id` BIGINT NOT NULL COMMENT '国家区域映射主键；初始化数据使用 2001 起的固定 ID，低于 Snowflake 范围。',
  `country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 3166-1 alpha-2 国家或地区代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `region_code` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台地理区域代码。允许值：GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC。GCC 为海湾合作委员会六国；MEA 为其余中东与非洲。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_country_region_country` (`country_code`) COMMENT '保证业务唯一性：country_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存全平台使用的国家到地理区域映射；Platform 唯一持有，Card 导入 BIN 库时经内部 HTTP 读取并推导发卡区域。本期只经初始化数据维护，不提供维护页面。';
