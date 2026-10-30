-- =====================================================================================================================
-- REQ-02 channel-connector 渠道身份就绪查询 · data-query 查询模板正文（technique.md §11；文本交付，非迁移脚本）
--
-- 登记：发布阶段（单独授权）由运营在 Platform data-query 后台登记到已有数据源 MOR_CHANNEL_CONNECTOR_REPLICA；
--       模板正文即下段 SQL，参数用 data-query 模板语法 #{name}。本文件不在任何数据库执行。
-- 约定（2026-10-28 客户上线决定附件 3.3）：恰好返回一行；satisfied 0 / 1；out_<name> 写入同名流程变量；missing 为逗号分隔原因码，
--   满足时为 NULL；列表参数与输出为逗号分隔字符串（REQ-05 S-05）。
-- 口径：与就绪查询接口 B1—B3 相同的筛选（MID / 渠道账号 / 渠道 ACTIVE；能力行 ENABLED；支付方式、MCC 精确；币种精确或 *；
--   体系、国家不限；该能力行声明 ACTION.PAY 或 ACTION.AUTHORIZE = 1）。已知简化：运行期按"最精确能力行"逐代码取值，若更精确的
--   能力行把收款动作显式设为 0，本模板仍可能判为满足；流程最终是否可收款以 N8（开通项生效，走接口精确判定）为准。
-- 列表拆分（2026-10-30 阶段 7 修订）：data-query 以预编译参数绑定执行，MySQL 的 JSON_TABLE 不接受绑定参数（试跑 500），改为
--   WITH RECURSIVE 按逗号拆分；绑定 NULL 一律按空串处理。
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- GOLIVE_MID_READY（N5c / N5d）
-- 参数：entityNo（必填，五位 OE）、paymentMethods（必填，逗号分隔支付方式代码）、mccCode（必填，四位）、
--       currencies（可选，逗号分隔币种；空串或 NULL 表示任一币种即可；给出时列出的币种中任一就绪即该支付方式就绪——2026-10-30 Q-4 修订）。
-- 输出：out_missing_methods（未就绪的支付方式，逗号分隔，字典序；满足时为空串）。
-- missing：PAYMENT_METHODS_EMPTY（未传支付方式，视为未满足）/ MID_NOT_FOUND（该 OE 无可用 MID）/
--          PAYMENT_METHOD_NOT_READY（有可用 MID，但列出的支付方式未全部就绪）。
-- ---------------------------------------------------------------------------------------------------------------------
WITH RECURSIVE
  method_split (method, rest) AS (
    SELECT TRIM(SUBSTRING_INDEX(s.p, ',', 1)), IF(LOCATE(',', s.p) > 0, SUBSTRING(s.p, LOCATE(',', s.p) + 1), NULL)
    FROM (SELECT CAST(COALESCE(#{paymentMethods}, '') AS CHAR(2000)) AS p) s
    UNION ALL
    SELECT TRIM(SUBSTRING_INDEX(r.rest, ',', 1)), IF(LOCATE(',', r.rest) > 0, SUBSTRING(r.rest, LOCATE(',', r.rest) + 1), NULL)
    FROM method_split r WHERE r.rest IS NOT NULL
  ),
  currency_split (currency, rest) AS (
    SELECT TRIM(SUBSTRING_INDEX(s.p, ',', 1)), IF(LOCATE(',', s.p) > 0, SUBSTRING(s.p, LOCATE(',', s.p) + 1), NULL)
    FROM (SELECT CAST(COALESCE(#{currencies}, '') AS CHAR(500)) AS p) s
    UNION ALL
    SELECT TRIM(SUBSTRING_INDEX(r.rest, ',', 1)), IF(LOCATE(',', r.rest) > 0, SUBSTRING(r.rest, LOCATE(',', r.rest) + 1), NULL)
    FROM currency_split r WHERE r.rest IS NOT NULL
  ),
  req AS (SELECT DISTINCT method FROM method_split WHERE method <> ''),
  cur AS (SELECT DISTINCT currency FROM currency_split WHERE currency <> ''),
  ready_cap AS (
    SELECT cap.payment_method_code, cap.currency
    FROM t_channel_mid mid
      JOIN t_channel_account acc ON acc.channel_account_id = mid.channel_account_id AND acc.account_status = 'ACTIVE'
      JOIN t_channel ch ON ch.channel_id = mid.channel_id AND ch.channel_status = 'ACTIVE'
      JOIN t_mid_capability cap ON cap.mid_id = mid.mid_id AND cap.capability_status = 'ENABLED'
    WHERE mid.entity_no = #{entityNo} AND mid.mid_status = 'ACTIVE' AND cap.mcc_code = #{mccCode}
      AND EXISTS (SELECT 1 FROM t_mid_capability_detail d WHERE d.mid_capability_id = cap.mid_capability_id
                   AND d.capability_code IN ('ACTION.PAY', 'ACTION.AUTHORIZE') AND d.boolean_value = 1)
  ),
  summary AS (
    SELECT
      (SELECT COUNT(*) FROM req) AS requested_methods,
      (SELECT COUNT(*) FROM t_channel_mid mid
         JOIN t_channel_account acc ON acc.channel_account_id = mid.channel_account_id AND acc.account_status = 'ACTIVE'
         JOIN t_channel ch ON ch.channel_id = mid.channel_id AND ch.channel_status = 'ACTIVE'
        WHERE mid.entity_no = #{entityNo} AND mid.mid_status = 'ACTIVE') AS usable_mids,
      COALESCE((
        SELECT GROUP_CONCAT(req.method ORDER BY req.method SEPARATOR ',')
        FROM req
        WHERE NOT EXISTS (
          SELECT 1 FROM ready_cap rc
          WHERE rc.payment_method_code = req.method
            AND ((SELECT COUNT(*) FROM cur) = 0
                 OR rc.currency = '*'
                 OR EXISTS (SELECT 1 FROM cur WHERE cur.currency = rc.currency)))
      ), '') AS missing_methods
  )
SELECT
  IF(m.requested_methods > 0 AND m.missing_methods = '', 1, 0) AS satisfied,
  m.missing_methods AS out_missing_methods,
  CASE
    WHEN m.requested_methods = 0 THEN 'PAYMENT_METHODS_EMPTY'
    WHEN m.missing_methods = '' THEN NULL
    WHEN m.usable_mids = 0 THEN 'MID_NOT_FOUND'
    ELSE 'PAYMENT_METHOD_NOT_READY'
  END AS missing
FROM summary m;
