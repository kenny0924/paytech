-- 2026-10-27 client-master：国家包法务修订（DDL）。需求见 requirements/20261027/client-master-国家包法务修订/。
-- 内容：申请资料版本与声明绑定（technique 3.3）；t_rule_setting.scope_type 注释增加 DECLARATION（声明文档版本 DOCUMENT_VERSION）与 ATTRIBUTE（日期属性 DATE_NOT_PAST）两个作用层级（
--   类型、可空与默认不变，只改注释）。
--   t_client_application.draft_revision：每次实际改动草稿在同一事务加一；存量申请取默认 0。
--   t_application_declaration.draft_revision：声明针对的资料版本；存量行为空，按 0 解释，部署后到下一次改资料前仍有效。
-- 执行顺序：本文件 → client-master 新镜像（新镜像的 RuleScopeType 才认识 DECLARATION / ATTRIBUTE，这两类规则行须在新镜像之后写入） → 2026-10-27-legal-pack-revision.sql（增量 DML）。两条 ADD COLUMN 与一条只改注释的 MODIFY COLUMN，MySQL 8 均即时完成；
--   旧镜像不读新列，可先于镜像执行。不可重复执行（列已存在时报错，按需先核对 information_schema.COLUMNS）。
-- 回退：先回退 client-master 镜像（新代码依赖这两列），再视需要执行
--   ALTER TABLE `t_application_declaration` DROP COLUMN `draft_revision`;
--   ALTER TABLE `t_client_application` DROP COLUMN `draft_revision`;
--   scope_type 注释无须回退（DECLARATION / ATTRIBUTE 行由增量 DML 回退删除）。
--   列保留无害，默认不回退。
SET NAMES utf8mb4;

ALTER TABLE `t_client_application`
  ADD COLUMN `draft_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '申请资料版本：每次实际改动草稿（主体、证件、地址、业务、属性、关系人、关系、文件）在同一事务加一；声明绑定此值，不等即失效。';
ALTER TABLE `t_application_declaration`
  ADD COLUMN `draft_revision` BIGINT NULL COMMENT '声明针对的申请资料版本；2026-10-27 之前的存量行为空，按 0 解释。';
ALTER TABLE `t_rule_setting`
  MODIFY COLUMN `scope_type` VARCHAR(32) NOT NULL COMMENT '规则开关作用的目录层级；DECLARATION 的 scope_code 为声明类型代码（Schema 字段 declaration.<类型>），ATTRIBUTE 的 scope_code 为扩展属性代码。允许值：JURISDICTION、ORGANIZATION_TYPE、IDENTIFIER_TYPE、DECLARATION、ATTRIBUTE。';
