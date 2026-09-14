-- Stage 4 APPROVED DESIGN; manual execution requires environment approval: mor_platform only. No authorization to execute.
-- Manifest/PDManer and full Schema now contain this exact table and indexes.
-- Preconditions: existing Credential/Grant and Gateway Route tables match current Schema;
-- t_api_scope and the two new indexes do not exist. See db/README.md for old-table bootstrap.
-- One new model table / 7 columns / PK + 1 unique index; 2 indexes on existing tables.
-- MySQL DDL auto-commits; this file is not transactionally reversible. No CHECK/FK.
SET NAMES utf8mb4;

CREATE TABLE `t_api_scope` (
  `api_scope_id` BIGINT NOT NULL COMMENT 'API Scope 目录的服务侧 Snowflake 主键；与凭证授权明细 scope_id 不同。',
  `scope_code` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '机器 API 权限的稳定小写 ASCII 编码，取值由本目录管理；创建后不可修改或回收复用，Route 与授权记录按此编码引用。',
  `display_name` VARCHAR(255) NOT NULL COMMENT 'API Scope 的非空展示名称，可由获授权运营人员修改，不参与认证匹配。',
  `description` VARCHAR(500) NULL COMMENT 'API Scope 的业务能力说明；未填写时为 NULL，不保存秘密、凭证值或客户资料。',
  `version` INT NOT NULL COMMENT '目录记录的乐观并发版本，创建为 1；每次成功修改递增 1，不表示历史业务版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_scope_id`),
  UNIQUE KEY `uk_api_scope_code` (`scope_code`) COMMENT '保证业务唯一性：scope_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存机器 API Scope 的全局定义和展示说明；不保存凭证授权、目录状态或版本历史';

ALTER TABLE `t_api_credential_scope`
  ADD KEY `idx_api_scope_credential` (`scope_code`, `credential_id`) COMMENT '支持访问路径：scope_code、credential_id';

ALTER TABLE `t_gateway_route`
  ADD KEY `idx_gateway_route_scope` (`required_scope_code`, `route_id`) COMMENT '支持访问路径：required_scope_code、route_id';
