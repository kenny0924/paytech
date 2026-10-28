-- =====================================================================================================================
-- REQ-01 commercial 开通项前置门禁 · data-query 查询模板正文（technique.md §14；文本交付，非迁移脚本）
--
-- 登记：发布阶段（S-08，单独授权）由运营在 Platform data-query 后台登记数据源 mor_commercial（只读账号）与下列三条模板；
--       模板正文即各段 SQL，参数用 data-query 模板语法 #{name}。本文件不在任何数据库执行。
-- 约定（修订 REQ-00 契约 3.3，供 REQ-05 统一解释）：
--   * 每条模板恰好返回一行（以 (SELECT 1) 锚行 LEFT JOIN，查不到业务行也返回一行）；
--   * satisfied 列 0 / 1；out_<name> 列写入同名流程变量；missing 列为逗号分隔的原因码，满足时为 NULL；
--   * 无行视为未满足（防御性约定；本文件三条模板都保证有行）。
-- 本机验证：2026-10-28 以 root 账号在本机 local-environment mor_commercial 上把 #{...} 替换为字面量逐条执行（只读），记录见
--   verification/stage4-report.md。
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- GOLIVE_AGREEMENT_DRAFTED（参数 clientId）
-- 该 Client 最新一份 CLIENT 合同（PENDING_SIGNATURE / SIGNED / ACTIVE，按创建时间、ID 倒序取一份），且其 CLIENT_PRICING 方案存在
-- APPROVED / CLIENT_SIGNED / SCHEDULED / ACTIVE 版本。
-- 输出：out_agreement_id、out_business_mode、out_entity_no、out_capability_codes（成员资格 ACTIVE 的开通项代码，逗号分隔）。
-- missing：AGREEMENT_NOT_DRAFTED / TERM_VERSION_NOT_APPROVED。
-- ---------------------------------------------------------------------------------------------------------------------
SELECT
  IF(a.agreement_id IS NOT NULL AND EXISTS (
       SELECT 1 FROM t_commercial_term_plan p JOIN t_commercial_term_version v ON v.term_plan_id = p.term_plan_id
       WHERE p.agreement_id = a.agreement_id AND p.term_type = 'CLIENT_PRICING'
         AND v.status IN ('APPROVED', 'CLIENT_SIGNED', 'SCHEDULED', 'ACTIVE')), 1, 0) AS satisfied,
  a.agreement_id AS out_agreement_id,
  a.business_mode AS out_business_mode,
  a.entity_no AS out_entity_no,
  (SELECT GROUP_CONCAT(c.capability_code ORDER BY c.capability_code SEPARATOR ',') FROM t_agreement_capability c
   WHERE c.agreement_id = a.agreement_id AND c.status = 'ACTIVE') AS out_capability_codes,
  CASE
    WHEN a.agreement_id IS NULL THEN 'AGREEMENT_NOT_DRAFTED'
    WHEN NOT EXISTS (
       SELECT 1 FROM t_commercial_term_plan p JOIN t_commercial_term_version v ON v.term_plan_id = p.term_plan_id
       WHERE p.agreement_id = a.agreement_id AND p.term_type = 'CLIENT_PRICING'
         AND v.status IN ('APPROVED', 'CLIENT_SIGNED', 'SCHEDULED', 'ACTIVE')) THEN 'TERM_VERSION_NOT_APPROVED'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN (
  SELECT agreement_id, business_mode, entity_no FROM t_agreement
  WHERE client_id = #{clientId} AND agreement_type = 'CLIENT' AND status IN ('PENDING_SIGNATURE', 'SIGNED', 'ACTIVE')
  ORDER BY create_time DESC, agreement_id DESC LIMIT 1
) a ON 1 = 1;

-- ---------------------------------------------------------------------------------------------------------------------
-- GOLIVE_AGREEMENT_ACTIVE（参数 agreementId）
-- 合同 ACTIVE，且其 CLIENT_PRICING 方案有 ACTIVE 版本。
-- missing：AGREEMENT_NOT_ACTIVE（合同不存在或不是 ACTIVE）/ TERM_VERSION_NOT_ACTIVE；两者都不满足时只报前者。
-- ---------------------------------------------------------------------------------------------------------------------
SELECT
  IF(a.status = 'ACTIVE' AND EXISTS (
       SELECT 1 FROM t_commercial_term_plan p JOIN t_commercial_term_version v ON v.term_plan_id = p.term_plan_id
       WHERE p.agreement_id = a.agreement_id AND p.term_type = 'CLIENT_PRICING' AND v.status = 'ACTIVE'), 1, 0) AS satisfied,
  CASE
    WHEN a.agreement_id IS NULL OR a.status <> 'ACTIVE' THEN 'AGREEMENT_NOT_ACTIVE'
    WHEN NOT EXISTS (
       SELECT 1 FROM t_commercial_term_plan p JOIN t_commercial_term_version v ON v.term_plan_id = p.term_plan_id
       WHERE p.agreement_id = a.agreement_id AND p.term_type = 'CLIENT_PRICING' AND v.status = 'ACTIVE')
      THEN 'TERM_VERSION_NOT_ACTIVE'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN t_agreement a ON a.agreement_id = #{agreementId};

-- ---------------------------------------------------------------------------------------------------------------------
-- GOLIVE_COLLECTION_CAPABILITIES_ENABLED（参数 agreementId）
-- 合同成员资格 ACTIVE 的 PAYMENT_METHOD 类开通项至少一条，且全部执行状态 ENABLED。
-- 输出：out_pending_capabilities（未 ENABLED 的 capability_code，逗号分隔；全部开通时为 NULL）。
-- missing：CAPABILITY_NOT_ENABLED（没有支付方式开通项，或有未开通的）。
-- ---------------------------------------------------------------------------------------------------------------------
SELECT
  IF(COUNT(c.agreement_capability_id) > 0 AND SUM(c.execution_status <> 'ENABLED' OR c.execution_status IS NULL) = 0, 1, 0)
    AS satisfied,
  GROUP_CONCAT(IF(c.execution_status = 'ENABLED', NULL, c.capability_code) ORDER BY c.capability_code SEPARATOR ',')
    AS out_pending_capabilities,
  IF(COUNT(c.agreement_capability_id) > 0 AND SUM(c.execution_status <> 'ENABLED' OR c.execution_status IS NULL) = 0, NULL,
    'CAPABILITY_NOT_ENABLED') AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN (
  SELECT ac.agreement_capability_id, ac.capability_code, ac.execution_status FROM t_agreement_capability ac
  JOIN t_product_capability pc ON pc.capability_code = ac.capability_code
  WHERE ac.agreement_id = #{agreementId} AND ac.status = 'ACTIVE' AND pc.capability_kind = 'PAYMENT_METHOD'
) c ON 1 = 1;
