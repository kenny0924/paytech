-- 卡 BIN 库：新增全平台国家区域主数据表 t_country_region（待执行，需授权）。
-- 需求：requirements/20261022/card-BIN库与卡属性/technique.md 第 2.3 节 E3、第 3.5.3 节。
-- 目标库：Platform Support（mor_platform）。
-- 预检：SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_country_region';
--       结果须为 0；否则说明已执行，跳过本文件。
-- 执行顺序：先执行本文件，再执行 platform-support-initial-data.sql 末尾的「Country region seed」块（249 行），
--          之后部署提供 GET /platform/v1/country-regions 的新 Platform；旧 Platform 不读该表，建表后仍可正常运行。
-- 执行后核对：SHOW CREATE TABLE t_country_region; 与 platform-support-schema.sql、platform-support.pdma.json 一致。
-- 回退：先回退读取它的 Platform 与 Card 镜像，再执行 DROP TABLE `t_country_region`;
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
