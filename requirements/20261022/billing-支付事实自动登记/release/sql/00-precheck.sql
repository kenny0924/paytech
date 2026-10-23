-- billing 支付事实自动登记 · mor-test 发布前只读核对（阶段 6 编写并执行，阶段 7 第 1 步复跑；只有 SELECT）。
-- 执行：scripts/backup.sh 会把本文件经 root（MYSQL_PWD）跑一遍并把输出存进备份目录；单独执行：
--   docker exec -i -e MYSQL_PWD=<root> mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t < sql/00-precheck.sql
-- 每段输出 check / verdict / 实际值；verdict 只有 PASS、GAP 两种，GAP 的处理见 release.md 第 3 节。
-- ID 来自 2026-10-23 阶段 6 只读查询（不是秘密）；换数据后改下面的变量。

SET @oe := 'AE001';
SET @ccy := 'USD';
SET @fe_party := 880000000701;               -- PLATFORM_HQ legal_party_id（占位 Party）
SET @client_agreement := 238331035912310784; -- DEMO-CL-001
SET @client_pricing_ver := 238331390620405760; -- DEMO-CL-001-ACQ V1（CLIENT_PRICING）
SET @partner_agreement := 238331036726005760;  -- DEMO-PT-001
SET @partner_share_ver := 238331037560672256;  -- DEMO-PT-001-SHARE V1
SET @platform_charge_ver := 238331039527800832; -- DEMO-PT-001-PSC V1
SET @local_partner := 770000001001;
SET @mid := 238508278684950528;              -- DEMO_NI / DEMO-NI-AE001-01
SET @channel := 238508277900615680;          -- DEMO_NI
SET @country := 'AE';                        -- 验收载荷 consumerCountryCode
SET @payment_method := 'APPLE_PAY';          -- PLATFORM 场景支付方式（CARD 见 GAP-ISSUER）
SET @now := UTC_TIMESTAMP(6);

-- ========== mor_ledger ==========
SELECT 'L1 OE book AE001/USD OPEN' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(id, ' ', book_status) AS actual
FROM mor_ledger.t_ledger_book WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @oe AND currency = @ccy AND book_status = 'OPEN';

SELECT 'L2 OE fiscal calendar + period containing today OPEN' AS `check`,
       IF(COUNT(p.id) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(f.calendar_code, ' ', f.timezone, ' period ', p.fiscal_year, '-', p.period_no, ' ', p.start_date, '..', p.end_date, ' ', p.status) AS actual
FROM mor_ledger.t_fiscal_calendar f
LEFT JOIN mor_ledger.t_accounting_period p ON p.calendar_id = f.id AND p.status = 'OPEN'
  AND DATE(CONVERT_TZ(@now, '+00:00', '+04:00')) BETWEEN p.start_date AND p.end_date
WHERE f.entity_type = 'OPERATING_ENTITY' AND f.entity_id = @oe;

SELECT 'L3 FE PLATFORM_HQ (party 880000000701) ACTIVE' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(id, ' ', entity_code, ' ', status) AS actual
FROM mor_ledger.t_finance_entity WHERE legal_party_id = @fe_party AND status = 'ACTIVE';

SELECT 'L4 FE USD book OPEN + FE period containing today OPEN' AS `check`,
       IF(COUNT(DISTINCT b.id) = 1 AND COUNT(DISTINCT p.id) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(DISTINCT b.id, ' ', b.book_status, ' / ', f.calendar_code, ' ', p.fiscal_year, '-', p.period_no, ' ', p.status) AS actual
FROM mor_ledger.t_finance_entity e
JOIN mor_ledger.t_ledger_book b ON b.entity_type = 'FINANCE_ENTITY' AND b.entity_id = CAST(e.id AS CHAR) AND b.currency = @ccy AND b.book_status = 'OPEN'
LEFT JOIN mor_ledger.t_fiscal_calendar f ON f.entity_type = 'FINANCE_ENTITY' AND f.entity_id = CAST(e.id AS CHAR)
LEFT JOIN mor_ledger.t_accounting_period p ON p.calendar_id = f.id AND p.status = 'OPEN'
  AND DATE(CONVERT_TZ(@now, '+00:00', '+04:00')) BETWEEN p.start_date AND p.end_date
WHERE e.legal_party_id = @fe_party AND e.status = 'ACTIVE';

SELECT 'L5 STANDARD_TRANSACTION bundle ACTIVE, current version ACTIVE and effective now' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(b.id, ' ver ', v.id, ' #', v.bundle_ver, ' ', v.status) AS actual
FROM mor_ledger.t_accounting_rule_bundle b
JOIN mor_ledger.t_accounting_rule_bundle_ver v ON v.id = b.current_ver_id
WHERE b.bundle_code = 'STANDARD_TRANSACTION' AND b.status = 'ACTIVE' AND v.status = 'ACTIVE'
  AND v.effective_from <= @now AND (v.effective_to IS NULL OR v.effective_to > @now);

-- ========== mor_billing ==========
SELECT 'B1 business calendar DEFAULT ACTIVE' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(calendar_code, ' ', timezone, ' ', status) AS actual
FROM mor_billing.t_business_calendar WHERE calendar_code = 'DEFAULT' AND status = 'ACTIVE';

SELECT 'B2 PAYMENT facts before release (expect 0)' AS `check`,
       IF(COUNT(*) = 0, 'PASS', 'GAP') AS verdict, COUNT(*) AS actual
FROM mor_billing.t_billing_fact WHERE fact_type = 'PAYMENT';

SELECT 'B3 facts by type/origin/status (baseline, not a gate)' AS `check`, 'INFO' AS verdict,
       GROUP_CONCAT(x.k ORDER BY x.k SEPARATOR '; ') AS actual
FROM (SELECT CONCAT(fact_type, '/', origin_type, '/', status, '=', COUNT(*)) k FROM mor_billing.t_billing_fact GROUP BY fact_type, origin_type, status) x;

-- ========== mor_commercial ==========
SELECT 'C1 DEMO-CL-001 agreement ACTIVE (id / entityNo / clientId)' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(agreement_id, ' ', agreement_no, ' ', entity_no, ' client ', client_id, ' ', status) AS actual
FROM mor_commercial.t_agreement WHERE agreement_id = @client_agreement AND agreement_type = 'CLIENT' AND status = 'ACTIVE';

SELECT 'C2 CLIENT_PRICING version ACTIVE and covers now' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(v.term_version_id, ' ', p.plan_code, ' V', v.version_no, ' ', v.status, ' ', v.effective_from, '..', IFNULL(v.effective_to, '∞')) AS actual
FROM mor_commercial.t_commercial_term_version v JOIN mor_commercial.t_commercial_term_plan p ON p.term_plan_id = v.term_plan_id
WHERE v.term_version_id = @client_pricing_ver AND v.agreement_id = @client_agreement AND p.term_type = 'CLIENT_PRICING'
  AND v.status = 'ACTIVE' AND v.effective_from <= @now AND (v.effective_to IS NULL OR v.effective_to > @now);

SELECT 'C3 client USD prices: settlement four fields non-null' AS `check`,
       IF(COUNT(*) > 0 AND SUM(settlement_business_days IS NULL OR business_calendar_code IS NULL OR settlement_timezone IS NULL OR cutoff_second_of_day IS NULL) = 0, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(DISTINCT CONCAT(settlement_business_days, '/', business_calendar_code, '/', settlement_timezone, '/', cutoff_second_of_day)) AS actual
FROM mor_commercial.t_commercial_term_price WHERE term_version_id = @client_pricing_ver AND entity_no = @oe AND currency = @ccy;

SELECT 'C4 client price settlement calendar exists ACTIVE in mor_billing' AS `check`,
       IF(COUNT(DISTINCT pr.business_calendar_code) = COUNT(DISTINCT c.calendar_code), 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(DISTINCT pr.business_calendar_code, ' -> ', IFNULL(c.status, 'MISSING')) AS actual
FROM mor_commercial.t_commercial_term_price pr
LEFT JOIN mor_billing.t_business_calendar c ON CAST(c.calendar_code AS CHAR) = CAST(pr.business_calendar_code AS CHAR) AND c.status = 'ACTIVE'
WHERE pr.term_version_id = @client_pricing_ver AND pr.entity_no = @oe AND pr.currency = @ccy;

-- commercial 命中算法：同支付方式的任一候选价格使用了事实中未给出的维度 → 422 PRICING_FACTS_INCOMPLETE。
-- billing 只传 CURRENCY、CONSUMER_COUNTRY、CARD_BRAND、FUNDING_TYPE、CARD_LEVEL（后三项为空时不传）。
SELECT 'C5 client USD prices per payment method: dimensions billing cannot supply' AS `check`,
       IF(SUM(cd.dimension_code NOT IN ('CURRENCY', 'CONSUMER_COUNTRY', 'CARD_BRAND', 'FUNDING_TYPE', 'CARD_LEVEL')) > 0, 'GAP', 'PASS') AS verdict,
       CONCAT(pf.payment_method_code, ': ', GROUP_CONCAT(DISTINCT pr.pricing_profile_code ORDER BY pr.priority_no), ' | dims ', IFNULL(GROUP_CONCAT(DISTINCT cd.dimension_code), '-')) AS actual
FROM mor_commercial.t_commercial_term_price pr
JOIN mor_commercial.t_pricing_profile pf ON pf.pricing_profile_code = pr.pricing_profile_code
LEFT JOIN mor_commercial.t_pricing_profile_cond cd ON cd.pricing_profile_id = pf.pricing_profile_id
WHERE pr.term_version_id = @client_pricing_ver AND pr.entity_no = @oe AND pr.currency = @ccy
GROUP BY pf.payment_method_code;

SELECT 'C6 client USD SALE components (fee preview)' AS `check`, 'INFO' AS verdict,
       GROUP_CONCAT(CONCAT(pr.pricing_profile_code, '#', pr.priority_no, ' ', c.component_code, ' ', c.component_type, ' ', c.calculation_base, ' ', IFNULL(c.rate, '-'), ' ', IFNULL(c.fixed_amount, '-'))
         ORDER BY pr.priority_no, c.component_no SEPARATOR '; ') AS actual
FROM mor_commercial.t_commercial_term_price pr
JOIN mor_commercial.t_commercial_term_component c ON c.term_price_id = pr.term_price_id AND c.trigger_code = 'SALE'
WHERE pr.term_version_id = @client_pricing_ver AND pr.entity_no = @oe AND pr.currency = @ccy;

SELECT 'C7 DEMO-PT-001 agreement ACTIVE + localPartnerId + OE link' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(a.agreement_id, ' partner ', a.local_partner_id, ' aoe ', o.agreement_operating_entity_id, ' ', o.entity_no) AS actual
FROM mor_commercial.t_agreement a
JOIN mor_commercial.t_agreement_operating_entity o ON o.agreement_id = a.agreement_id AND o.entity_no = @oe
WHERE a.agreement_id = @partner_agreement AND a.agreement_type = 'PARTNER' AND a.status = 'ACTIVE' AND a.local_partner_id = @local_partner;

SELECT 'C8 PARTNER_SHARE / PLATFORM_SERVICE_CHARGE versions ACTIVE and cover now' AS `check`,
       IF(COUNT(*) = 2, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(p.term_type, ' ', v.term_version_id, ' V', v.version_no, ' ', v.status ORDER BY p.term_type SEPARATOR '; ') AS actual
FROM mor_commercial.t_commercial_term_version v JOIN mor_commercial.t_commercial_term_plan p ON p.term_plan_id = v.term_plan_id
WHERE v.term_version_id IN (@partner_share_ver, @platform_charge_ver) AND v.agreement_id = @partner_agreement AND v.status = 'ACTIVE'
  AND v.effective_from <= @now AND (v.effective_to IS NULL OR v.effective_to > @now);

SELECT 'C9 DEMO-PT-001 PLATFORM_SIGNATORY party = FE party' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict, GROUP_CONCAT(party_id, ' ', party_role) AS actual
FROM mor_commercial.t_agreement_party WHERE agreement_id = @partner_agreement AND party_role = 'PLATFORM_SIGNATORY' AND party_id = @fe_party;

-- PARTNER 场景要求：两条款都有 @payment_method 的 USD 价格，且该支付方式在 Client 侧可命中（C5 PASS）。
SELECT 'C10 partner USD SALE prices exist for @payment_method in both terms' AS `check`,
       IF(COUNT(DISTINCT IF(pf.payment_method_code = @payment_method, pr.term_version_id, NULL)) = 2, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(DISTINCT CONCAT(pr.term_version_id, ' ', pf.payment_method_code, ' ', pr.pricing_profile_code, ' ', c.component_code, ' ', c.calculation_base, ' ', IFNULL(c.rate, '-'))
         ORDER BY pr.term_version_id SEPARATOR '; ') AS actual
FROM mor_commercial.t_commercial_term_price pr
JOIN mor_commercial.t_pricing_profile pf ON pf.pricing_profile_code = pr.pricing_profile_code
JOIN mor_commercial.t_commercial_term_component c ON c.term_price_id = pr.term_price_id AND c.trigger_code = 'SALE'
WHERE pr.term_version_id IN (@partner_share_ver, @platform_charge_ver) AND pr.entity_no = @oe AND pr.currency = @ccy;

-- ========== mor_reconciliation ==========
SELECT 'R1 ACTIVE clearing account bound to MID, USD, AE001' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(c.id, ' ', c.clearing_account_code, ' ', c.channel_code, ' ', c.entity_no, ' mid ', m.mid_id) AS actual
FROM mor_reconciliation.t_clearing_account c JOIN mor_reconciliation.t_clearing_account_mid m ON m.clearing_account_id = c.id
WHERE m.mid_id = @mid AND m.currency = @ccy AND c.currency = @ccy AND c.entity_no = @oe AND c.status = 'ACTIVE';

-- ========== mor_channel_connector ==========
SELECT 'K1 MID ACTIVE under channel / channel account' AS `check`,
       IF(COUNT(*) = 1, 'PASS', 'GAP') AS verdict,
       GROUP_CONCAT(m.mid_id, ' ', ch.channel_code, ' ch ', m.channel_id, ' acct ', m.channel_account_id, ' ', m.entity_no, ' ', m.mid_status) AS actual
FROM mor_channel_connector.t_channel_mid m JOIN mor_channel_connector.t_channel ch ON ch.channel_id = m.channel_id
WHERE m.mid_id = @mid AND m.channel_id = @channel AND m.mid_status = 'ACTIVE' AND ch.channel_status = 'ACTIVE';

-- channel-connector 命中：Channel 唯一 ACTIVE 方案 → effective_from ≤ asOf < effective_to 的唯一 ACTIVE/INACTIVE 版本 → 六维精确命中范围。
SELECT 'K2 channel cost plan covers (channel, MID, AE001, country, USD, method, PAY) now' AS `check`,
       IF(COUNT(DISTINCT s.id) = 1, 'PASS', 'GAP') AS verdict,
       IFNULL(GROUP_CONCAT(DISTINCT p.plan_code, ' ver ', v.cost_ver, ' scope ', s.id), CONCAT('plans for channel: ',
         (SELECT COUNT(*) FROM mor_channel_connector.t_channel_cost_plan WHERE channel_id = @channel))) AS actual
FROM mor_channel_connector.t_channel_cost_plan p
JOIN mor_channel_connector.t_channel_cost_plan_ver v ON v.plan_id = p.id AND v.status IN ('ACTIVE', 'INACTIVE')
  AND v.effective_from <= @now AND (v.effective_to IS NULL OR v.effective_to > @now)
JOIN mor_channel_connector.t_channel_cost_scope s ON s.cost_ver_id = v.id AND s.mid_id = @mid AND s.entity_no = @oe
  AND s.country_code = @country AND s.currency = @ccy AND s.payment_method_code = @payment_method AND s.action_type = 'PAY'
WHERE p.channel_id = @channel AND p.status = 'ACTIVE';

-- ========== mor_acquiring（acquiring 新镜像 findCommercialContext 三表联查所需列） ==========
SELECT 'A1 acquiring columns for findCommercialContext (expect 23)' AS `check`,
       IF(COUNT(*) = 23, 'PASS', 'GAP') AS verdict, COUNT(*) AS actual
FROM information_schema.columns WHERE table_schema = 'mor_acquiring' AND (
  (table_name = 't_commerce_order' AND column_name IN ('order_id', 'client_id', 'entity_no', 'entity_source_type', 'local_partner_id', 'industry_code', 'mcc_code', 'total_amount', 'currency'))
  OR (table_name = 't_order_context' AND column_name IN ('order_id', 'order_context_id', 'commercial_context_time', 'client_pricing_version_id', 'partner_share_version_id', 'platform_charge_version_id', 'platform_service_party_id', 'finance_tax_snapshot_id', 'consumer_country_code'))
  OR (table_name = 't_order_legal' AND column_name IN ('order_context_id', 'order_legal_id', 'client_agreement_id', 'partner_agreement_id', 'partner_agreement_operating_entity_id')));

-- ========== 合成 ID 段未被占用（验收前应为 0） ==========
SELECT 'S1 synthetic ID range 9999000000000001xx unused' AS `check`,
       IF((SELECT COUNT(*) FROM mor_billing.t_billing_fact_ref WHERE ref_id LIKE '99990000000000%') = 0, 'PASS', 'GAP') AS verdict,
       (SELECT COUNT(*) FROM mor_billing.t_billing_fact_ref WHERE ref_id LIKE '99990000000000%') AS actual;

-- ========== 补偿任务基线（不是门禁；验收后与此比较，不应出现新的 FAILED） ==========
SELECT 'X1 compensation tasks by status (baseline)' AS `check`, 'INFO' AS verdict,
       CONCAT_WS('; ',
         (SELECT CONCAT('billing ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_billing.t_compensation_event GROUP BY 1) a),
         (SELECT CONCAT('account ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_account.t_compensation_event GROUP BY 1) b),
         (SELECT CONCAT('ledger ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_ledger.t_compensation_event GROUP BY 1) c),
         (SELECT CONCAT('reconciliation ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_reconciliation.t_compensation_event GROUP BY 1) d),
         (SELECT CONCAT('acquiring ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_acquiring.t_compensation_event GROUP BY 1) e),
         (SELECT CONCAT('payment ', GROUP_CONCAT(CONCAT(s, '=', n))) FROM (SELECT execute_status s, COUNT(*) n FROM mor_payment.t_compensation_event GROUP BY 1) f)) AS actual;
