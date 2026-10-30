-- REQ-04 risk-decision 限额档位：回滚前滚 DDL（2026-10-30-client-limit-tier.sql）。
-- 目标库：Risk Decision（mor_risk_decision）。须先回滚到不含档位代码的 risk-decision 镜像，再由开发者审核后手工执行。
-- 档位与变更记录是审计与执行依据：任一新表有行，或任一 TIER 名单有成员时 SIGNAL 停止，不删除，由人工决定数据去留。
-- 先确认已无执行发布引用 TIER 名单变量（回滚激活旧流程版本的发布在前）；已建 DAILY_SUM_AMOUNT 变量或引用 TIER 名单的名单命中变量时同样拒绝。
-- 本脚本删除四张 TIER 名单、三张新表并恢复五处列注释；不触碰其他表与数据。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS rq04_client_limit_tier_rollback;
DELIMITER //
CREATE PROCEDURE rq04_client_limit_tier_rollback()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_client_limit_tier') THEN
    IF EXISTS (SELECT 1 FROM t_client_limit_tier) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_client_limit_tier 已有档位数据，拒绝回滚；请人工决定数据去留';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_client_limit_tier_change') THEN
    IF EXISTS (SELECT 1 FROM t_client_limit_tier_change) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_client_limit_tier_change 已有变更记录，拒绝回滚；请人工决定数据去留';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_client_allowed_daily_total') THEN
    IF EXISTS (SELECT 1 FROM t_client_allowed_daily_total) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_client_allowed_daily_total 已有放行汇总，拒绝回滚；请人工决定数据去留';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM t_velocity_feature_definition WHERE metric = 'DAILY_SUM_AMOUNT') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已有 DAILY_SUM_AMOUNT 累计变量，拒绝回滚';
  END IF;
  IF EXISTS (SELECT 1 FROM t_list_feature_definition WHERE list_id IN (300001, 300002, 300003, 300004)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '已有引用 TIER 名单的名单命中变量，拒绝回滚；请先确认无发布引用并人工处理变量';
  END IF;
  IF EXISTS (SELECT 1 FROM t_risk_list_entry WHERE list_id IN (300001, 300002, 300003, 300004)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TIER 名单已有成员，拒绝回滚；请人工决定数据去留';
  END IF;
  DELETE FROM t_risk_list WHERE list_id IN (300001, 300002, 300003, 300004) AND list_purpose = 'TIER';
  DROP TABLE IF EXISTS t_client_limit_tier_change;
  DROP TABLE IF EXISTS t_client_limit_tier;
  DROP TABLE IF EXISTS t_client_allowed_daily_total;
  ALTER TABLE `t_velocity_feature_definition`
    MODIFY COLUMN `metric` VARCHAR(64) NOT NULL COMMENT '累计算法的稳定业务代码。允许值：COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT、SECONDS_SINCE_LAST。',
    MODIFY COLUMN `window_seconds` INT NOT NULL COMMENT '窗口秒数的整数值。';
  ALTER TABLE `t_risk_list`
    MODIFY COLUMN `list_purpose` VARCHAR(64) NOT NULL COMMENT '名单用途的稳定业务代码。允许值：BLOCK、ALLOW。';
  ALTER TABLE `t_risk_list_entry`
    MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '成员来源的稳定业务代码。允许值：ACTION、MANUAL。';
  ALTER TABLE `t_velocity_feature_definition`
    MODIFY COLUMN `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED、CHECKOUT_ASSESSED、CHECKOUT_REJECTED。';
END //
DELIMITER ;

CALL rq04_client_limit_tier_rollback();
DROP PROCEDURE IF EXISTS rq04_client_limit_tier_rollback;
