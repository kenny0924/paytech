-- =====================================================================================================================
-- REQ-03 data-query 模板 GOLIVE_UNDERWRITING_APPROVED 正文（决定 3.3；technique.md 第 10.2 节）。运行时登记数据，不是 DML：
-- 发布时在运营后台 data-query 模板管理登记（数据源 MOR_RISK_DECISION_REPLICA），由主会话执行；本文件只交付文本，不在任何库执行。
-- 模板登记字段：
--   编码 GOLIVE_UNDERWRITING_APPROVED；数据源 MOR_RISK_DECISION_REPLICA；参数 clientId（INTEGER，必填）；
--   约束 pagingMode=NONE、maxRows=1、timeoutSeconds=5；出参列 satisfied、out_requireDeposit、out_depositBlocksCollection、
--   out_highRiskMcc、out_suggestedSettlementDays、missing（不配屏蔽规则）。
-- REQ-05 DATA_QUERY 动作模板输出声明：requireDeposit BOOLEAN、depositBlocksCollection BOOLEAN、highRiskMcc BOOLEAN、
--   suggestedSettlementDays NUMBER（未给出 T+N 时为 null）。
-- 单行约定：始终返回一行。有效结论为 APPROVED 时 satisfied=1、missing 为 null；否则 satisfied=0、out_* 为 null，missing 为
--   最新版本 SUBMITTED → UNDERWRITING_PENDING_REVIEW、DECLINED → UNDERWRITING_DECLINED，其余（无记录、DRAFT、已被替代后无有效）→
--   UNDERWRITING_NOT_STARTED。只读 t_client_underwriting，不读规则、评估等其他表（S-10）。
-- auto 测试 ClientUnderwritingQueryApiAutoTest 读取下方正文（分隔线之后）在本机 mor_risk_decision 以只读查询执行四态。
-- ---------------------------------------------------------------- template ----------------------------------------------
SELECT
  IF(e.underwriting_id IS NOT NULL, 1, 0) AS satisfied,
  e.require_deposit AS out_requireDeposit,
  e.deposit_blocks_collection AS out_depositBlocksCollection,
  e.high_risk_mcc AS out_highRiskMcc,
  e.suggested_settlement_days AS out_suggestedSettlementDays,
  CASE
    WHEN e.underwriting_id IS NOT NULL THEN NULL
    WHEN l.status = 'SUBMITTED' THEN 'UNDERWRITING_PENDING_REVIEW'
    WHEN l.status = 'DECLINED' THEN 'UNDERWRITING_DECLINED'
    ELSE 'UNDERWRITING_NOT_STARTED'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN t_client_underwriting e ON e.client_id = #{clientId} AND e.effective_slot = 1
LEFT JOIN (
  SELECT status FROM t_client_underwriting WHERE client_id = #{clientId} ORDER BY version_no DESC LIMIT 1
) l ON 1 = 1
