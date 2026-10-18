-- Commerce Customer Checkout 本地旧库兼容增量，待人工执行。
-- 目标：mor-local / 127.0.0.1:13306 / mor_commerce；不适用于 mor-test。
-- 前置：已应用 20260911 基础创建兼容增量；执行前备份下列三表结构。
-- 不删除历史列或数据；新写由应用保证 TERM_PRICE 必需引用完整，旧记录不改写。
-- MySQL DDL 隐式提交。重复执行前先核对 information_schema，不盲目重复 ADD COLUMN。
ALTER TABLE t_consumer_context
  MODIFY consumer_reference VARCHAR(128) NULL COMMENT 'Client命名空间内可选非PII消费者业务引用；当前Customer无该输入时为空，不生成假值。';
ALTER TABLE t_order_context
  MODIFY assignment_id BIGINT NULL COMMENT '仅历史LEGACY_SCOPE记录使用的Assignment引用；TERM_PRICE不写入。',
  MODIFY cct_commercial_context_hash CHAR(64) NULL COMMENT '仅历史LEGACY_SCOPE商业上下文Hash；TERM_PRICE不写入。',
  MODIFY client_pricing_scope_id BIGINT NULL COMMENT '仅历史LEGACY_SCOPE定价Scope引用；TERM_PRICE不写入。',
  ADD COLUMN affiliation_id BIGINT NULL COMMENT 'TERM_PRICE支付准备冻结的CCT全OE授权关系。',
  ADD COLUMN entity_scope_id BIGINT NULL COMMENT 'TERM_PRICE冻结的CCT准确国家币种行业MCC范围。',
  ADD COLUMN entity_source_relation_id BIGINT NULL COMMENT 'TERM_PRICE冻结的OE来源关系。',
  ADD COLUMN client_pricing_version_id BIGINT NULL COMMENT 'TERM_PRICE冻结的Client条款版本。',
  ADD COLUMN partner_share_version_id BIGINT NULL COMMENT 'PARTNER来源冻结的分成条款版本。',
  ADD COLUMN platform_charge_version_id BIGINT NULL COMMENT 'PARTNER来源冻结的平台服务费版本。';
ALTER TABLE t_order_legal
  MODIFY client_agreement_operating_entity_id BIGINT NULL COMMENT '仅历史LEGACY_SCOPE的Client合同OE关联；TERM_PRICE不写入。',
  ADD COLUMN client_agreement_id BIGINT NULL COMMENT 'TERM_PRICE准确Client与OE合同。',
  ADD COLUMN partner_agreement_id BIGINT NULL COMMENT 'PARTNER来源冻结的Partner合同。';
