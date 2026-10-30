-- =====================================================================================================================
-- REQ-04 data-query 模板 GOLIVE_LIMIT_TIER 正文（决定 3.3；technique.md 第 8.5 节）。运行时登记数据，不是 DML：
-- 发布时在运营后台 data-query 模板管理登记（数据源 MOR_RISK_DECISION_REPLICA，REQ-03 已登记），由主会话执行；
-- 本文件只交付文本，不在任何库执行。登记前先执行 data-query-grant.sql 的表级授权。
-- 模板登记字段：
--   编码 GOLIVE_LIMIT_TIER；数据源 MOR_RISK_DECISION_REPLICA；参数 clientId（INTEGER，必填）、targetTier（ENUM：OBSERVATION、STANDARD，必填）；
--   约束 pagingMode=NONE、maxRows=1、timeoutSeconds=5；出参列 satisfied、out_tier、out_observationDue、missing（不配屏蔽规则）。
-- REQ-05 DATA_QUERY 动作模板输出声明：tier ENUM（VERIFICATION、OBSERVATION、STANDARD、RESTRICTED）、observationDue BOOLEAN。
-- 单行约定：始终返回一行。当前档位不低于目标档位（VERIFICATION < OBSERVATION < STANDARD，RESTRICTED 不满足）时 satisfied=1、
--   missing 为 null；否则 satisfied=0，missing 为 LIMIT_TIER_NOT_SET（无档位）/ LIMIT_TIER_RESTRICTED / LIMIT_TIER_BELOW_TARGET。
--   observationDue 只按天数判定（OBSERVATION 且已过整天数 ≥ 目标天数）；放行笔数不进模板，避免授权累计事件表（S-09）。
-- 只读 t_client_limit_tier（表级授权），不读变更记录、名单、累计事件等其他表。
-- ---------------------------------------------------------------- template ----------------------------------------------
SELECT
  CASE
    WHEN t.tier IS NULL OR t.tier = 'RESTRICTED' THEN 0
    WHEN #{targetTier} = 'OBSERVATION' AND t.tier IN ('OBSERVATION', 'STANDARD') THEN 1
    WHEN #{targetTier} = 'STANDARD' AND t.tier = 'STANDARD' THEN 1
    ELSE 0
  END AS satisfied,
  t.tier AS out_tier,
  IF(t.tier = 'OBSERVATION' AND t.observation_started_time IS NOT NULL
     AND TIMESTAMPDIFF(DAY, t.observation_started_time, UTC_TIMESTAMP(6)) >= t.observation_target_days, 1, 0) AS out_observationDue,
  CASE
    WHEN t.tier IS NULL THEN 'LIMIT_TIER_NOT_SET'
    WHEN t.tier = 'RESTRICTED' THEN 'LIMIT_TIER_RESTRICTED'
    WHEN (#{targetTier} = 'OBSERVATION' AND t.tier IN ('OBSERVATION', 'STANDARD')) OR (#{targetTier} = 'STANDARD' AND t.tier = 'STANDARD') THEN NULL
    ELSE 'LIMIT_TIER_BELOW_TARGET'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN t_client_limit_tier t ON t.client_id = #{clientId}
