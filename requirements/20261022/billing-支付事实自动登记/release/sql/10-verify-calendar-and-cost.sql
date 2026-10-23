-- billing 支付事实自动登记 · SQL-1 与渠道成本方案的只读核对（阶段 7 第 3 步前后与第 4 步后执行；只有 SELECT）。
--   docker exec -i -e MYSQL_PWD=<root> mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t < sql/10-verify-calendar-and-cost.sql
-- 本机 local-environment 执行 SQL-1 后同样可跑 V1（库名相同）。

-- V1 SQL-1 前：0 行（GAP-CAL）；SQL-1 后：恰 1 行 ACTIVE。
SELECT 'V1 mor_billing AE_BUSINESS_DAYS' AS `check`, COUNT(*) AS `rows`,
       GROUP_CONCAT(calendar_code, ' ', timezone, ' ', status) AS actual
FROM mor_billing.t_business_calendar WHERE calendar_code = 'AE_BUSINESS_DAYS';

SELECT 'V2 AE_BUSINESS_DAYS exception days (expect 0 → Monday to Friday)' AS `check`, COUNT(*) AS `rows`
FROM mor_billing.t_business_calendar_day WHERE calendar_code = 'AE_BUSINESS_DAYS';

-- V3 回滚前提：没有事实引用该日历（有引用时不得执行回滚 DELETE）。
SELECT 'V3 facts referencing AE_BUSINESS_DAYS' AS `check`, COUNT(*) AS `rows`
FROM mor_billing.t_billing_fact_pricing WHERE business_calendar_code = 'AE_BUSINESS_DAYS';

-- V4 渠道成本方案（Q-2）：DEMO_NI 唯一方案 ACTIVE，当前版本 ACTIVE 且覆盖现在，范围六维精确命中，组成 PROCESSING_FEE 0.005。
SELECT 'V4 DEMO_NI cost plan / current version / scope / component' AS `check`,
       p.plan_code, p.status AS plan_status, v.cost_ver, v.status AS ver_status, v.effective_from, v.effective_to,
       v.maker_id, v.checker_id, s.id AS scope_id, s.channel_agreement_ref,
       c.component_code, c.component_type, c.calculation_base, c.rate_value, c.fixed_amount, c.rounding_mode
FROM mor_channel_connector.t_channel_cost_plan p
JOIN mor_channel_connector.t_channel_cost_plan_ver v ON v.id = p.current_ver_id
LEFT JOIN mor_channel_connector.t_channel_cost_scope s ON s.cost_ver_id = v.id AND s.mid_id = 238508278684950528
  AND s.entity_no = 'AE001' AND s.country_code = 'AE' AND s.currency = 'USD' AND s.payment_method_code = 'APPLE_PAY' AND s.action_type = 'PAY'
LEFT JOIN mor_channel_connector.t_channel_cost_component c ON c.scope_id = s.id
WHERE p.channel_id = 238508277900615680;

SELECT 'V5 expected: plan ACTIVE, ver ACTIVE covering now, 1 scope, 1 component, maker <> checker' AS `check`,
       IF(COUNT(DISTINCT s.id) = 1 AND COUNT(c.id) = 1 AND MAX(p.status) = 'ACTIVE' AND MAX(v.status) = 'ACTIVE'
          AND MAX(v.effective_from) <= UTC_TIMESTAMP(6) AND (MAX(v.effective_to) IS NULL OR MAX(v.effective_to) > UTC_TIMESTAMP(6))
          AND MAX(v.maker_id) <> MAX(v.checker_id) AND MAX(c.rate_value) = 0.005, 'PASS', 'GAP') AS verdict
FROM mor_channel_connector.t_channel_cost_plan p
JOIN mor_channel_connector.t_channel_cost_plan_ver v ON v.id = p.current_ver_id
JOIN mor_channel_connector.t_channel_cost_scope s ON s.cost_ver_id = v.id AND s.mid_id = 238508278684950528
  AND s.entity_no = 'AE001' AND s.country_code = 'AE' AND s.currency = 'USD' AND s.payment_method_code = 'APPLE_PAY' AND s.action_type = 'PAY'
JOIN mor_channel_connector.t_channel_cost_component c ON c.scope_id = s.id
WHERE p.channel_id = 238508277900615680;
