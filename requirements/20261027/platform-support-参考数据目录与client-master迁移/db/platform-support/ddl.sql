-- 参考数据目录：mor_platform 结构变更（阶段 3 评审稿，未执行，不得直接执行）。
-- 需求：requirements/20261027/platform-support-参考数据目录与client-master迁移/technique.md 第 6 节。
-- 决定：agent-docs/decisions/2026-10-27-platform-reference-data.md。
-- 阶段 4 会把本文件拆成可执行的 changes/2026-10-27-reference-data.sql（含预检、回填、收紧非空与回退），并同步 platform-support-schema.sql、PDManer 与测试 DDL。
-- 发布约束：改名后旧 Platform 镜像无法读取，必须与新镜像在同一窗口切换；回退为反向 RENAME 加旧镜像。
SET NAMES utf8mb4;

-- 1. 币种：改名、列改名、补列（新列先可空，回填后在阶段 4 的执行文件中收紧为 NOT NULL）。
RENAME TABLE `t_currency_spec` TO `t_ref_currency`;
ALTER TABLE `t_ref_currency`
  RENAME COLUMN `currency` TO `currency_code`,
  ADD COLUMN `numeric_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ISO 4217 三位数字码，保留前导零；ISO 8583 与 EMV 3DS 报文使用。取值来源：ISO 4217；非固定枚举。' AFTER `currency_code`,
  ADD COLUMN `name` VARCHAR(128) NULL COMMENT 'ISO 4217 英文名称，如 UAE Dirham；是数据而不是界面文案，前端缺少翻译时回退显示。' AFTER `numeric_code`,
  ADD COLUMN `symbol` VARCHAR(8) NULL COMMENT '币种标准符号，如 $、د.إ；没有专用符号时等于币种代码。后端生成单据时使用，页面金额由前端 Intl 格式化。' AFTER `name`,
  ADD COLUMN `logo_file_address` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '币种图标的文件服务地址 /files/v1/{fileId}，文件位于公开桶；为空时前端使用内置图标。任何状态都可更换。' AFTER `rounding_mode`,
  DROP INDEX `uk_currency`,
  ADD UNIQUE KEY `uk_ref_currency_code` (`currency_code`) COMMENT '保证业务唯一性：currency_code',
  COMMENT = '全平台币种参考数据：ISO 4217 编码、名称、符号、精度、舍入方式与启停状态；Platform 唯一持有。只有 DRAFT 可修改标准属性，启用后冻结；停用只拦新用途。';

-- 2. 国家：改名、补列。
RENAME TABLE `t_country_region` TO `t_ref_country`;
ALTER TABLE `t_ref_country`
  ADD COLUMN `alpha3_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ISO 3166-1 alpha-3 代码；证件 MRZ 与部分供应商接口使用。取值来源：ISO 3166-1；非固定枚举。' AFTER `country_code`,
  ADD COLUMN `numeric_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ISO 3166-1 三位数字码，保留前导零；EMV 3DS 的国家字段使用。取值来源：ISO 3166-1；非固定枚举。' AFTER `alpha3_code`,
  ADD COLUMN `name` VARCHAR(128) NULL COMMENT 'ISO 3166-1 英文短名，如 Saudi Arabia；是数据而不是界面文案。' AFTER `numeric_code`,
  ADD COLUMN `calling_code` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '国际电话区号，不含加号，如 966；没有区号的地区为空。号码格式校验不使用本列。' AFTER `region_code`,
  ADD COLUMN `default_currency_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前法定货币，引用 t_ref_currency.currency_code；多币种国家取主币种，没有法定货币时为空。用于币种默认值。' AFTER `calling_code`,
  ADD COLUMN `logo_file_address` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '国旗图片的文件服务地址 /files/v1/{fileId}，文件位于公开桶；为空时前端使用占位图。' AFTER `default_currency_code`,
  ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '国家是否允许被新业务选择。允许值：DRAFT、ACTIVE、INACTIVE。' AFTER `logo_file_address`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '乐观并发版本，从 1 开始，每次维护成功加一。' AFTER `status`,
  DROP INDEX `uk_country_region_country`,
  ADD UNIQUE KEY `uk_ref_country_code` (`country_code`) COMMENT '保证业务唯一性：country_code',
  COMMENT = '全平台国家参考数据：ISO 3166-1 各编码、英文名、平台区域、国际区号、法定货币与国旗；Platform 唯一持有。';

-- 3. 行政区（ISO 3166-2）。
CREATE TABLE `t_ref_country_subdivision` (
  `id` BIGINT NOT NULL COMMENT '行政区主键；初始数据使用 3001 起的固定 ID，低于 Snowflake 范围。',
  `subdivision_code` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 3166-2 行政区代码，如 SA-01、AE-DU。取值来源：ISO 3166-2；非固定枚举。',
  `country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属国家，引用 t_ref_country.country_code。',
  `parent_subdivision_code` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上级行政区代码；只有两级行政区的国家使用，一级行政区为空。',
  `category` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 3166-2 定义的行政区类别，如 EMIRATE、REGION、GOVERNORATE。取值来源：ISO 3166-2；非固定枚举。',
  `name` VARCHAR(128) NOT NULL COMMENT 'ISO 3166-2 官方拉丁文名称；是数据而不是界面文案。',
  `local_name` VARCHAR(128) NULL COMMENT 'ISO 3166-2 公布的本地文字官方名称，如阿拉伯文；用于地址打印与登记信息比对，不是界面翻译。没有公布时为空。',
  `status` VARCHAR(16) NOT NULL COMMENT '行政区是否允许被新业务选择。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '乐观并发版本，从 1 开始，每次维护成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref_country_subdivision_code` (`subdivision_code`) COMMENT '保证业务唯一性：subdivision_code',
  KEY `idx_ref_subdivision_country` (`country_code`, `status`) COMMENT '按国家与状态查询行政区'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='全平台行政区参考数据（ISO 3166-2）；Platform 唯一持有，client-master 地址与 tax 读取。';

-- 4. 行业两级分类（平台统一分类）。
CREATE TABLE `t_ref_industry` (
  `id` BIGINT NOT NULL COMMENT '行业主键；初始数据使用 4001 起的固定 ID，低于 Snowflake 范围。',
  `industry_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台行业编码，如 RETAIL、RETAIL_APPAREL；发布后不改、不删、不复用。取值来源：平台行业分类；非固定枚举。',
  `parent_industry_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上级大类编码；大类为空，细类必填并引用本表的大类。',
  `industry_level` TINYINT NOT NULL COMMENT '分类层级。允许值：1、2。1 为大类，2 为细类；客户资格只能选细类。',
  `name` VARCHAR(128) NOT NULL COMMENT '行业英文名称；是数据而不是界面文案。',
  `description` VARCHAR(512) NULL COMMENT '英文选择提示：包括什么、不包括什么；为空表示无需提示。',
  `default_mcc_code` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '细类的建议 MCC，引用 t_ref_mcc.mcc_code；大类为空。只是默认值，最终 MCC 由运营确认。',
  `logo_file_address` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '行业图标的文件服务地址 /files/v1/{fileId}，文件位于公开桶；为空时前端使用内置图标。',
  `sort_no` INT NOT NULL COMMENT '同一层级内的显示顺序，数值越小越靠前。',
  `status` VARCHAR(16) NOT NULL COMMENT '行业是否允许被新业务选择。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '乐观并发版本，从 1 开始，每次维护成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref_industry_code` (`industry_code`) COMMENT '保证业务唯一性：industry_code',
  KEY `idx_ref_industry_parent` (`parent_industry_code`, `sort_no`) COMMENT '按大类读取细类并排序'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='全平台行业两级分类：大类与细类，细类给出建议 MCC；行业准入、审批与风险等级不在本表，归 compliance / risk。';

-- 5. MCC（ISO 18245）。
CREATE TABLE `t_ref_mcc` (
  `id` BIGINT NOT NULL COMMENT 'MCC 主键；初始数据使用 5001 起的固定 ID，低于 Snowflake 范围。',
  `mcc_code` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '四位 Merchant Category Code，保留前导零。取值来源：ISO 18245 与卡组织公开清单；非固定枚举。',
  `name` VARCHAR(128) NOT NULL COMMENT 'MCC 英文短名，如 Computer Software Stores；是数据而不是界面文案。',
  `description` VARCHAR(512) NOT NULL COMMENT 'MCC 标准英文描述全文。',
  `network_high_risk` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '卡组织是否公布为高风险类目。允许值：0、1。1 表示 Visa 或 Mastercard 公布为高风险；平台自己的禁止、审批与风险等级不在本列。',
  `status` VARCHAR(16) NOT NULL COMMENT 'MCC 是否允许被新业务选择。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '乐观并发版本，从 1 开始，每次维护成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ref_mcc_code` (`mcc_code`) COMMENT '保证业务唯一性：mcc_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='全平台 MCC 参考数据（ISO 18245）；Platform 唯一持有。';

-- 回退（阶段 4 执行文件中给出完整版本）：DROP 三张新表；t_ref_country 删除新增列并改回 t_country_region 与原唯一索引；
-- t_ref_currency 删除新增列、currency_code 改回 currency、恢复 uk_currency 并改回 t_currency_spec；随后换回旧 Platform 镜像。
