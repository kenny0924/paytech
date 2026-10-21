-- commercial 开通与定价模型调整（一期）数据迁移：mor_commercial，在 ddl.sql 之后、新版本应用启动之前人工执行（technique.md §3.3–§3.4）。
-- 一个事务：先执行【核对】段的只读 SELECT 并保存输出（发布记录附件），再整体执行【迁移】段；任一语句失败执行 ROLLBACK。
-- 不可逆：执行前必须完成 mor_commercial 整库备份；回滚 = 备份恢复。价格、画像与条件不改动（命中结果不变）。
-- 临时表只在本会话存在，不触发隐式提交；迁移生成的条件行 ID 使用保留段 9100000000900000001 起（不与 Snowflake 主键重叠）。
SET NAMES utf8mb4;

-- ============================================================================================================
-- 【核对 1】迁移前的卡类开通项（CARD_VISA / CARD_MC / CARD_AMEX / CARD_UNIONPAY），按合同 × OE 分组
-- ============================================================================================================
SELECT ac.agreement_id, ac.entity_no, ac.agreement_capability_id, ac.capability_code, pc.dimension_value AS card_brand,
       ac.status, ac.effective_from, ac.effective_to, ac.rolling_reserve_rate, ac.rolling_reserve_days,
       (SELECT GROUP_CONCAT(CONCAT(l.limit_code, '=', l.limit_value) ORDER BY l.limit_code, l.limit_value SEPARATOR ',')
          FROM t_agreement_capability_limit l WHERE l.agreement_capability_id = ac.agreement_capability_id) AS limits
  FROM t_agreement_capability ac
  JOIN t_product_capability pc ON pc.capability_code = ac.capability_code
 WHERE ac.capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY')
 ORDER BY ac.agreement_id, ac.entity_no, ac.create_time, ac.agreement_capability_id;

-- 【核对 2】已存在 CARD 开通项的合同 × OE（应为 0 行；非 0 时停止并人工处理）
SELECT agreement_id, entity_no, agreement_capability_id FROM t_agreement_capability WHERE capability_code = 'CARD';

-- 【核对 3】REQUIRE_3DS 约束行（迁移为 t_agreement_capability.require_3ds=1 后删除）
SELECT l.agreement_capability_id, ac.agreement_id, ac.entity_no, ac.capability_code, l.limit_value
  FROM t_agreement_capability_limit l JOIN t_agreement_capability ac ON ac.agreement_capability_id = l.agreement_capability_id
 WHERE l.limit_code = 'REQUIRE_3DS';

-- 【核对 4】方案 Owner 回填预览与存量版本免签预览
SELECT CASE WHEN local_partner_id IS NOT NULL THEN 'LOCAL_PARTNER' ELSE 'CLIENT' END AS owner_type, COUNT(*) AS plans
  FROM t_commercial_term_plan GROUP BY 1;
SELECT status, COUNT(*) AS versions FROM t_commercial_term_version GROUP BY status ORDER BY status;

-- ============================================================================================================
-- 【迁移】
-- ============================================================================================================
START TRANSACTION;

-- 1. 目录：新增 CARD（支付方式）与 TOKEN_STORAGE（功能）；ID 与 commercial-initial-data.sql 一致
INSERT INTO `t_product_capability` (`product_capability_id`, `product_code`, `capability_code`, `capability_name`, `capability_kind`, `payment_method_code`, `status`, `create_time`, `update_time`) VALUES
  (9100000000000002012, 'ACQUIRING', 'CARD', '银行卡', 'PAYMENT_METHOD', 'CARD', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000002013, 'ACQUIRING', 'TOKEN_STORAGE', '存卡', 'FEATURE', NULL, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE `product_capability_id` = `product_capability_id`;

-- 1.1 可配维度（与 commercial-initial-data.sql 一致）
INSERT INTO `t_product_capability_dimension` (`capability_dimension_id`, `capability_code`, `dimension_code`, `required`, `create_time`, `update_time`) VALUES
  (9100000000000004001, 'CARD', 'CURRENCY', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004002, 'CARD', 'CONSUMER_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004003, 'CARD', 'CARD_BRAND', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004004, 'CARD', 'FUNDING_TYPE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004005, 'CARD', 'ISSUER_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004006, 'CARD', 'ISSUER_REGION', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004007, 'CARD', 'CARD_LEVEL', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004008, 'APPLE_PAY', 'CURRENCY', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004009, 'APPLE_PAY', 'CONSUMER_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004010, 'APPLE_PAY', 'CARD_BRAND', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004011, 'APPLE_PAY', 'FUNDING_TYPE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004012, 'GOOGLE_PAY', 'CURRENCY', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004013, 'GOOGLE_PAY', 'CONSUMER_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004014, 'GOOGLE_PAY', 'CARD_BRAND', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004015, 'GOOGLE_PAY', 'FUNDING_TYPE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004016, 'PAYOUT_LOCAL', 'CURRENCY', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004017, 'PAYOUT_LOCAL', 'DESTINATION_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004018, 'PAYOUT_SWIFT', 'CURRENCY', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004019, 'PAYOUT_SWIFT', 'DESTINATION_COUNTRY', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9100000000000004020, 'FX_SPOT', 'CURRENCY_PAIR', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE `capability_dimension_id` = `capability_dimension_id`;

-- 1.2 计费事件挂接（technique.md §3.3）
UPDATE t_product_fee_event SET capability_kind = 'PAYMENT_METHOD', capability_code = NULL, update_time = UTC_TIMESTAMP(6)
 WHERE product_code = 'ACQUIRING' AND fee_event_code IN ('AUTH', 'CAPTURE', 'SALE', 'REFUND', 'CHARGEBACK');
UPDATE t_product_fee_event SET capability_kind = 'FEATURE', capability_code = 'TOKEN_STORAGE', update_time = UTC_TIMESTAMP(6)
 WHERE fee_event_code = 'TOKEN_STORED';
UPDATE t_product_fee_event SET capability_kind = 'PAYOUT_RAIL', capability_code = NULL, update_time = UTC_TIMESTAMP(6)
 WHERE product_code = 'PAYOUT';
UPDATE t_product_fee_event SET capability_kind = 'FX', capability_code = NULL, update_time = UTC_TIMESTAMP(6)
 WHERE product_code = 'FX';
UPDATE t_product_fee_event SET capability_kind = 'DISPUTE', capability_code = NULL, update_time = UTC_TIMESTAMP(6)
 WHERE product_code = 'DISPUTE';
UPDATE t_product_fee_event SET capability_kind = NULL, capability_code = NULL, update_time = UTC_TIMESTAMP(6)
 WHERE product_code = 'PLATFORM';

-- 2. 合同开通项：每个合同 × OE 的卡类开通项合并为一条 CARD
-- 2.1 分组：保留最早创建的一行；状态任一 ENABLED 则 ENABLED；区间取最早起点、最晚终点（任一为空则空）
CREATE TEMPORARY TABLE tmp_card_group (
  agreement_id BIGINT NOT NULL,
  entity_no CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  keep_id BIGINT NOT NULL,
  member_count INT NOT NULL,
  any_enabled TINYINT(1) NOT NULL,
  min_from DATETIME(6) NOT NULL,
  max_to DATETIME(6) NULL,
  reserve_variants INT NOT NULL,
  period_variants INT NOT NULL,
  PRIMARY KEY (agreement_id, entity_no)
);
INSERT INTO tmp_card_group
SELECT agreement_id, entity_no,
       CAST(SUBSTRING_INDEX(GROUP_CONCAT(agreement_capability_id ORDER BY create_time, agreement_capability_id), ',', 1) AS UNSIGNED),
       COUNT(*), MAX(status = 'ENABLED'), MIN(effective_from),
       CASE WHEN SUM(effective_to IS NULL) > 0 THEN NULL ELSE MAX(effective_to) END,
       COUNT(DISTINCT CONCAT_WS('|', COALESCE(rolling_reserve_rate, ''), COALESCE(rolling_reserve_days, ''))),
       COUNT(DISTINCT CONCAT_WS('|', effective_from, COALESCE(effective_to, '')))
  FROM t_agreement_capability
 WHERE capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY')
 GROUP BY agreement_id, entity_no;

-- 2.2 成员：每条卡类开通项、其卡组织、保留行，以及该成员是否限制币种 / 消费者国家
CREATE TEMPORARY TABLE tmp_card_member (
  agreement_capability_id BIGINT NOT NULL PRIMARY KEY,
  keep_id BIGINT NOT NULL,
  card_brand VARCHAR(128) NOT NULL,
  currencies VARCHAR(1024) NULL,
  countries VARCHAR(1024) NULL
);
INSERT INTO tmp_card_member
SELECT ac.agreement_capability_id, g.keep_id, pc.dimension_value,
       (SELECT GROUP_CONCAT(l.limit_value ORDER BY l.limit_value) FROM t_agreement_capability_limit l
         WHERE l.agreement_capability_id = ac.agreement_capability_id AND l.limit_code = 'CURRENCY'),
       (SELECT GROUP_CONCAT(l.limit_value ORDER BY l.limit_value) FROM t_agreement_capability_limit l
         WHERE l.agreement_capability_id = ac.agreement_capability_id AND l.limit_code = 'CONSUMER_COUNTRY')
  FROM t_agreement_capability ac
  JOIN tmp_card_group g ON g.agreement_id = ac.agreement_id AND g.entity_no = ac.entity_no
  JOIN t_product_capability pc ON pc.capability_code = ac.capability_code
 WHERE ac.capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY');

-- 2.3 分组的并集规则：任一成员不限币种（或国家）时合并后不限，否则取并集
CREATE TEMPORARY TABLE tmp_card_bound (
  keep_id BIGINT NOT NULL PRIMARY KEY,
  currency_unbounded TINYINT(1) NOT NULL,
  country_unbounded TINYINT(1) NOT NULL,
  currency_variants INT NOT NULL,
  country_variants INT NOT NULL,
  card_brands VARCHAR(512) NOT NULL
);
INSERT INTO tmp_card_bound
SELECT keep_id, MAX(currencies IS NULL), MAX(countries IS NULL), COUNT(DISTINCT COALESCE(currencies, '<ALL>')),
       COUNT(DISTINCT COALESCE(countries, '<ALL>')), GROUP_CONCAT(DISTINCT card_brand ORDER BY card_brand)
  FROM tmp_card_member GROUP BY keep_id;

-- 【核对 5】需要在发布记录中人工确认的合并：多成员且币种 / 国家集合不同（并集放宽）、保证金或区间不一致、存在不限币种的成员
SELECT g.agreement_id, g.entity_no, g.keep_id, g.member_count, b.card_brands, b.currency_variants, b.currency_unbounded,
       b.country_variants, b.country_unbounded, g.reserve_variants, g.period_variants
  FROM tmp_card_group g JOIN tmp_card_bound b ON b.keep_id = g.keep_id
 WHERE g.member_count > 1 AND (b.currency_variants > 1 OR b.country_variants > 1 OR g.reserve_variants > 1
       OR g.period_variants > 1) OR b.currency_unbounded = 1
 ORDER BY g.agreement_id, g.entity_no;

-- 2.4 待写入保留行的条件：CARD_BRAND = 各成员卡组织；有界的 CURRENCY / CONSUMER_COUNTRY 取各成员并集
CREATE TEMPORARY TABLE tmp_card_condition (
  keep_id BIGINT NOT NULL,
  limit_code VARCHAR(64) NOT NULL,
  limit_value VARCHAR(128) NOT NULL,
  PRIMARY KEY (keep_id, limit_code, limit_value)
);
INSERT INTO tmp_card_condition SELECT DISTINCT keep_id, 'CARD_BRAND', card_brand FROM tmp_card_member;
INSERT IGNORE INTO tmp_card_condition
SELECT m.keep_id, l.limit_code, l.limit_value
  FROM t_agreement_capability_limit l
  JOIN tmp_card_member m ON m.agreement_capability_id = l.agreement_capability_id
  JOIN tmp_card_bound b ON b.keep_id = m.keep_id
 WHERE (l.limit_code = 'CURRENCY' AND b.currency_unbounded = 0) OR (l.limit_code = 'CONSUMER_COUNTRY' AND b.country_unbounded = 0);

-- 2.5 保留行的条件整体改写：先删除其 CURRENCY / CONSUMER_COUNTRY 行（无界维度合并后不限），再写入上一步条件
DELETE l FROM t_agreement_capability_limit l JOIN tmp_card_bound b ON b.keep_id = l.agreement_capability_id
 WHERE l.limit_code IN ('CURRENCY', 'CONSUMER_COUNTRY', 'CARD_BRAND');
INSERT INTO t_agreement_capability_limit (agreement_capability_limit_id, agreement_capability_id, limit_code, limit_value, create_time, update_time)
SELECT 9100000000900000000 + ROW_NUMBER() OVER (ORDER BY c.keep_id, c.limit_code, c.limit_value), c.keep_id, c.limit_code, c.limit_value,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM tmp_card_condition c;

-- 2.6 3DS：任一成员要求 3DS 则保留行要求 3DS；所有开通项的 REQUIRE_3DS 行迁移到 require_3ds
UPDATE t_agreement_capability ac
  JOIN (SELECT DISTINCT m.keep_id FROM tmp_card_member m
          JOIN t_agreement_capability_limit l ON l.agreement_capability_id = m.agreement_capability_id AND l.limit_code = 'REQUIRE_3DS') r
    ON r.keep_id = ac.agreement_capability_id
   SET ac.require_3ds = 1, ac.update_time = UTC_TIMESTAMP(6);
UPDATE t_agreement_capability ac
  JOIN (SELECT DISTINCT agreement_capability_id FROM t_agreement_capability_limit WHERE limit_code = 'REQUIRE_3DS') r
    ON r.agreement_capability_id = ac.agreement_capability_id
   SET ac.require_3ds = 1, ac.update_time = UTC_TIMESTAMP(6);
DELETE FROM t_agreement_capability_limit WHERE limit_code = 'REQUIRE_3DS';

-- 2.7 保留行改为 CARD，状态与区间取分组值，保证金沿用保留行
UPDATE t_agreement_capability ac JOIN tmp_card_group g ON g.keep_id = ac.agreement_capability_id
   SET ac.capability_code = 'CARD',
       ac.status = CASE WHEN g.any_enabled = 1 THEN 'ENABLED' ELSE ac.status END,
       ac.effective_from = g.min_from, ac.effective_to = g.max_to, ac.update_time = UTC_TIMESTAMP(6);

-- 2.8 删除被合并的成员及其条件行（BCR 历史保留，可追溯）
DELETE l FROM t_agreement_capability_limit l JOIN tmp_card_member m ON m.agreement_capability_id = l.agreement_capability_id
 WHERE m.agreement_capability_id <> m.keep_id;
DELETE ac FROM t_agreement_capability ac JOIN tmp_card_member m ON m.agreement_capability_id = ac.agreement_capability_id
 WHERE m.agreement_capability_id <> m.keep_id;

-- 2.9 删除旧卡类目录行；其余目录行的废弃维度列置空
DELETE FROM t_product_capability WHERE capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY');
UPDATE t_product_capability SET dimension_code = NULL, dimension_value = NULL WHERE dimension_code IS NOT NULL OR dimension_value IS NOT NULL;

-- 3. 方案 Owner 回填
UPDATE t_commercial_term_plan
   SET owner_type = CASE WHEN local_partner_id IS NOT NULL THEN 'LOCAL_PARTNER' ELSE 'CLIENT' END;

-- 4. 存量已排期 / 生效 / 被替代 / 过期版本免签
UPDATE t_commercial_term_version SET signature_exempt = 1 WHERE status IN ('ACTIVE', 'SCHEDULED', 'SUPERSEDED', 'EXPIRED');

-- ============================================================================================================
-- 【迁移后核对】（提交前检查；任一不符执行 ROLLBACK）
-- ============================================================================================================
-- 应为 0：残留卡类开通项与目录行、REQUIRE_3DS 行
SELECT (SELECT COUNT(*) FROM t_agreement_capability WHERE capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY')) AS legacy_capabilities,
       (SELECT COUNT(*) FROM t_product_capability WHERE capability_code IN ('CARD_VISA', 'CARD_MC', 'CARD_AMEX', 'CARD_UNIONPAY')) AS legacy_catalog,
       (SELECT COUNT(*) FROM t_agreement_capability_limit WHERE limit_code = 'REQUIRE_3DS') AS require_3ds_rows;
-- 合并结果：每个合同 × OE 至多一条 CARD 及其接受条件
SELECT ac.agreement_id, ac.entity_no, ac.agreement_capability_id, ac.status, ac.require_3ds, ac.effective_from, ac.effective_to,
       (SELECT GROUP_CONCAT(CONCAT(l.limit_code, '=', l.limit_value) ORDER BY l.limit_code, l.limit_value SEPARATOR ',')
          FROM t_agreement_capability_limit l WHERE l.agreement_capability_id = ac.agreement_capability_id) AS conditions
  FROM t_agreement_capability ac WHERE ac.capability_code = 'CARD' ORDER BY ac.agreement_id, ac.entity_no;
-- 缺少必填接受条件（CURRENCY）的开通项：发布记录中人工确认，下次修改开通项时须补齐
SELECT ac.agreement_id, ac.entity_no, ac.agreement_capability_id, ac.capability_code
  FROM t_agreement_capability ac
  JOIN t_product_capability_dimension d ON d.capability_code = ac.capability_code AND d.required = 1
 WHERE NOT EXISTS (SELECT 1 FROM t_agreement_capability_limit l
                    WHERE l.agreement_capability_id = ac.agreement_capability_id AND l.limit_code = d.dimension_code)
 ORDER BY ac.agreement_id, ac.entity_no, ac.capability_code;
-- 计费事件挂接与方案 Owner、免签分布
SELECT capability_kind, capability_code, COUNT(*) AS fee_events FROM t_product_fee_event GROUP BY capability_kind, capability_code ORDER BY 1, 2;
SELECT owner_type, COUNT(*) AS plans FROM t_commercial_term_plan GROUP BY owner_type;
SELECT status, signature_exempt, COUNT(*) AS versions FROM t_commercial_term_version GROUP BY status, signature_exempt ORDER BY 1, 2;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_card_condition;
DROP TEMPORARY TABLE IF EXISTS tmp_card_bound;
DROP TEMPORARY TABLE IF EXISTS tmp_card_member;
DROP TEMPORARY TABLE IF EXISTS tmp_card_group;
