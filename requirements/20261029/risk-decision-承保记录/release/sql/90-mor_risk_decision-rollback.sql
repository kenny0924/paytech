-- REQ-03 risk-decision 承保记录：回滚前滚 DDL（2026-10-29-client-underwriting.sql）。
-- 目标库：Risk Decision（mor_risk_decision）。须先回滚到不含承保代码的 risk-decision 镜像，再由开发者审核后手工执行。
-- 承保记录是审计依据：t_client_underwriting 有任何行时 SIGNAL 停止，不删除表，由人工决定数据去留。
-- 本脚本只删除三张新表；不触碰其他表。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS rq03_client_underwriting_rollback;
DELIMITER //
CREATE PROCEDURE rq03_client_underwriting_rollback()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_client_underwriting') THEN
    IF EXISTS (SELECT 1 FROM t_client_underwriting) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_client_underwriting 已有承保记录，拒绝回滚；请人工决定数据去留';
    END IF;
  END IF;
  DROP TABLE IF EXISTS t_client_underwriting;
  DROP TABLE IF EXISTS t_underwriting_rule_threshold;
  DROP TABLE IF EXISTS t_underwriting_rule_version;
END //
DELIMITER ;

CALL rq03_client_underwriting_rollback();
DROP PROCEDURE IF EXISTS rq03_client_underwriting_rollback;
