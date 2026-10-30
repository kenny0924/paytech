-- =====================================================================================================================
-- REQ-02 渠道身份就绪查询 · 发布前只读巡检：按新口径（technique 8.2 / 10.2）预测每个支付方式开通项的前置 ④ 结论，
-- 列出现网 ENABLED 中会被暂停的开通项（Q-2 发布前告知）。
-- 只读：root 会话 + SET SESSION TRANSACTION READ ONLY；跨库读 mor_commercial × mor_client_master × mor_channel_connector，
-- 仅用于巡检（业务代码不跨库）。可重跑，不写任何东西。需要 MySQL 8.0（CTE）。
-- 用法（mor-test）：sqlro < 本文件     其中 sqlro 见 runbook 公共变量。
--
-- 口径与已知近似：
--   * 范围：成员资格 ACTIVE、目录种类 PAYMENT_METHOD、execution_status 为 ENABLED / PENDING_PREREQUISITES / SUSPENDED_BY_PREREQUISITE。
--   * 待核对币种（④ 用 ② 的"已覆盖币种"）：开通项有 CURRENCY 条件时取条件币种中、合同 ACTIVE 条款版本里存在
--     同 OE × 同币种 × 画像支付方式相同的价格的币种；没有 CURRENCY 条件时取这些价格的币种。
--     与代码的差异：代码还要求"其余条件组合（卡品牌 / 卡类型等）每个都命中价格"，本 SQL 不展开组合，故为上界。
--     对 ENABLED 开通项（上一次检查 ② 已满足 = 每个条件币种每个组合都命中）二者相同；对等待 / 暂停项仅供参考。
--   * Client MCC：client-master 业务资料已确认（classification_confirmed_time 非空）且 primary_slot = 1 的 mcc_code。
--   * 就绪五步（与 QueryChannelIdentityReadinessProcessor 同序）：MID / 渠道账号 / 渠道 ACTIVE → 能力行 ENABLED 且支付方式相同
--     → MCC 相同 → 币种相同或 * → 该能力行声明 ACTION.PAY 或 ACTION.AUTHORIZE = 1。
--     第 5 步简化：不复现"更精确能力行把动作设为 0"的覆盖（与 GOLIVE_MID_READY 模板相同的已知简化）。
--   * Q-4（2026-10-30 用户确认修订）：已覆盖币种中任一就绪即 ④ 满足；部分币种不就绪时 SATISFIED + CHANNEL_IDENTITY_PARTIAL，
--     not_ready_currencies 列出缺 MID 的币种作为提醒；全部不就绪时 UNSATISFIED，原因取字典序第一个不就绪币种的原因。
-- =====================================================================================================================

WITH caps AS (
  SELECT ac.agreement_capability_id, ac.agreement_id, a.agreement_no, a.client_id, ac.entity_no, ac.capability_code,
         pc.payment_method_code AS pm, ac.execution_status
    FROM mor_commercial.t_agreement_capability ac
    JOIN mor_commercial.t_agreement a ON a.agreement_id = ac.agreement_id
    JOIN mor_commercial.t_product_capability pc ON pc.capability_code = ac.capability_code
   WHERE ac.status = 'ACTIVE' AND pc.capability_kind = 'PAYMENT_METHOD'
     AND ac.execution_status IN ('ENABLED', 'PENDING_PREREQUISITES', 'SUSPENDED_BY_PREREQUISITE')
),
priced AS (
  SELECT DISTINCT c.agreement_capability_id, p.currency
    FROM caps c
    JOIN mor_commercial.t_commercial_term_version tv ON tv.agreement_id = c.agreement_id AND tv.status = 'ACTIVE'
    JOIN mor_commercial.t_commercial_term_price p ON p.term_version_id = tv.term_version_id AND p.entity_no = c.entity_no
    JOIN mor_commercial.t_pricing_profile pp ON pp.pricing_profile_code = p.pricing_profile_code AND pp.payment_method_code = c.pm
),
limited AS (
  SELECT l.agreement_capability_id, l.limit_value AS currency
    FROM mor_commercial.t_agreement_capability_limit l
    JOIN caps c ON c.agreement_capability_id = l.agreement_capability_id
   WHERE l.limit_code = 'CURRENCY'
),
covered AS (
  SELECT pr.agreement_capability_id, pr.currency
    FROM priced pr
   WHERE NOT EXISTS (SELECT 1 FROM limited x WHERE x.agreement_capability_id = pr.agreement_capability_id)
      OR EXISTS (SELECT 1 FROM limited x WHERE x.agreement_capability_id = pr.agreement_capability_id AND x.currency = pr.currency)
),
mcc AS (
  SELECT bp.client_id, MIN(bc.mcc_code) AS mcc_code
    FROM mor_client_master.t_client_business_profile bp
    JOIN mor_client_master.t_client_business_classification bc
      ON bc.business_profile_id = bp.business_profile_id AND bc.primary_slot = 1
   WHERE bp.classification_confirmed_time IS NOT NULL
   GROUP BY bp.client_id
),
usable AS (
  SELECT m.mid_id, m.entity_no
    FROM mor_channel_connector.t_channel_mid m
    JOIN mor_channel_connector.t_channel_account acc ON acc.channel_account_id = m.channel_account_id AND acc.account_status = 'ACTIVE'
    JOIN mor_channel_connector.t_channel ch ON ch.channel_id = m.channel_id AND ch.channel_status = 'ACTIVE'
   WHERE m.mid_status = 'ACTIVE'
),
caprow AS (
  SELECT u.entity_no, cap.mid_capability_id, cap.payment_method_code, cap.mcc_code, cap.currency,
         EXISTS (SELECT 1 FROM mor_channel_connector.t_mid_capability_detail d
                  WHERE d.mid_capability_id = cap.mid_capability_id
                    AND d.capability_code IN ('ACTION.PAY', 'ACTION.AUTHORIZE') AND d.boolean_value = 1) AS collects
    FROM usable u
    JOIN mor_channel_connector.t_mid_capability cap ON cap.mid_id = u.mid_id AND cap.capability_status = 'ENABLED'
),
per_currency AS (
  SELECT c.agreement_capability_id, cv.currency, m.mcc_code,
         CASE
           WHEN NOT EXISTS (SELECT 1 FROM usable u WHERE u.entity_no = c.entity_no) THEN 'MID_NOT_FOUND'
           WHEN NOT EXISTS (SELECT 1 FROM caprow r WHERE r.entity_no = c.entity_no AND r.payment_method_code = c.pm)
             THEN 'PAYMENT_METHOD_NOT_SUPPORTED'
           WHEN NOT EXISTS (SELECT 1 FROM caprow r WHERE r.entity_no = c.entity_no AND r.payment_method_code = c.pm
                              AND r.mcc_code = m.mcc_code) THEN 'MCC_NOT_COVERED'
           WHEN NOT EXISTS (SELECT 1 FROM caprow r WHERE r.entity_no = c.entity_no AND r.payment_method_code = c.pm
                              AND r.mcc_code = m.mcc_code AND r.currency IN ('*', cv.currency)) THEN 'CURRENCY_NOT_SUPPORTED'
           WHEN NOT EXISTS (SELECT 1 FROM caprow r WHERE r.entity_no = c.entity_no AND r.payment_method_code = c.pm
                              AND r.mcc_code = m.mcc_code AND r.currency IN ('*', cv.currency) AND r.collects)
             THEN 'PAYMENT_ACTION_NOT_SUPPORTED'
           ELSE NULL
         END AS reason
    FROM caps c
    JOIN covered cv ON cv.agreement_capability_id = c.agreement_capability_id
    JOIN mcc m ON m.client_id = c.client_id
),
verdict AS (
  SELECT c.*, m.mcc_code,
         (SELECT GROUP_CONCAT(cv.currency ORDER BY cv.currency) FROM covered cv
           WHERE cv.agreement_capability_id = c.agreement_capability_id) AS covered_currencies,
         (SELECT GROUP_CONCAT(pc.currency ORDER BY pc.currency) FROM per_currency pc
           WHERE pc.agreement_capability_id = c.agreement_capability_id AND pc.reason IS NOT NULL) AS not_ready_currencies,
         (SELECT pc.reason FROM per_currency pc
           WHERE pc.agreement_capability_id = c.agreement_capability_id AND pc.reason IS NOT NULL
           ORDER BY pc.currency LIMIT 1) AS first_reason,
         (SELECT COUNT(*) FROM per_currency pc
           WHERE pc.agreement_capability_id = c.agreement_capability_id AND pc.reason IS NULL) AS ready_count
    FROM caps c
    LEFT JOIN mcc m ON m.client_id = c.client_id
)
SELECT agreement_no, agreement_capability_id, client_id, entity_no, capability_code, pm AS payment_method_code,
       execution_status, mcc_code, covered_currencies,
       CASE
         WHEN covered_currencies IS NULL THEN 'SKIPPED'
         WHEN mcc_code IS NULL THEN 'UNSATISFIED'
         WHEN not_ready_currencies IS NULL OR ready_count > 0 THEN 'SATISFIED'
         ELSE 'UNSATISFIED'
       END AS predicted_channel_identity,
       CASE
         WHEN covered_currencies IS NULL THEN 'AWAITING_PRICE'
         WHEN mcc_code IS NULL THEN 'CLIENT_MCC_MISSING'
         WHEN not_ready_currencies IS NULL THEN NULL
         WHEN ready_count > 0 THEN 'CHANNEL_IDENTITY_PARTIAL'
         ELSE first_reason
       END AS predicted_reason,
       not_ready_currencies,
       CASE
         WHEN execution_status = 'ENABLED' AND covered_currencies IS NOT NULL
              AND (mcc_code IS NULL OR (not_ready_currencies IS NOT NULL AND ready_count = 0)) THEN 'WILL_SUSPEND'
         WHEN execution_status = 'ENABLED' AND not_ready_currencies IS NOT NULL THEN 'STAYS_ENABLED_WITH_REMINDER'
         WHEN execution_status = 'ENABLED' THEN 'STAYS_ENABLED'
         ELSE 'NO_STATE_CHANGE_FROM_CHANNEL_IDENTITY'
       END AS impact
  FROM verdict
 ORDER BY (impact = 'WILL_SUSPEND') DESC, execution_status, agreement_no, capability_code;

-- 汇总：按现状 × 预测分组计数（阶段 7 发布后与手动 includeEnabled 重查结果核对）。
WITH caps AS (
  SELECT ac.agreement_capability_id, ac.execution_status
    FROM mor_commercial.t_agreement_capability ac
    JOIN mor_commercial.t_product_capability pc ON pc.capability_code = ac.capability_code
   WHERE ac.status = 'ACTIVE' AND pc.capability_kind = 'PAYMENT_METHOD'
)
SELECT execution_status, COUNT(*) AS capabilities FROM caps GROUP BY execution_status ORDER BY execution_status;
