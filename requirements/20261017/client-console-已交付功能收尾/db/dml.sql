-- Client Console 已交付功能收尾 F-03：CCT 已批准 Client 主体资料回填 approved_time（执行文件，可重复执行）
-- 目标库：CCT 业务库（本机 mor_cct；mor-test mor_cct 须另行授权）。
-- 内容：存在 APPROVED 申请、主体资料 approved_time 仍为空的 Client，回填为该 Client 最早一份 APPROVED 申请的 update_time。
--       这是近似值：批准结论落库时申请 update_time 等于结论时间，之后若申请记录再被更新则偏晚（需求 Q3 已确认接受）。
--       只填空值，不覆盖已写入的批准时间；只处理 Client 主体资料（t_client.party_id），不处理关联人员资料（技术方案 D1）。
-- 执行顺序：先执行本文件，再部署写入 approved_time 的新 CCT（旧 CCT 不读该列，先执行无影响；新 CCT 读取时对空值有兜底）。
-- 预检：SELECT COUNT(*) FROM t_party_profile f JOIN t_client c ON c.party_id=f.party_id
--         JOIN t_client_application a ON a.client_id=c.client_id AND a.status='APPROVED' WHERE f.approved_time IS NULL;
-- 回滚：执行前导出受影响行的 profile_id；回滚时 UPDATE t_party_profile SET approved_time=NULL WHERE profile_id IN (<导出的 ID>);
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

START TRANSACTION;

UPDATE t_party_profile f
JOIN t_client c ON c.party_id = f.party_id
JOIN (SELECT client_id, MIN(update_time) AS approved_at
      FROM t_client_application
      WHERE status = 'APPROVED'
      GROUP BY client_id) a ON a.client_id = c.client_id
SET f.approved_time = a.approved_at, f.update_time = UTC_TIMESTAMP(6)
WHERE f.approved_time IS NULL;

COMMIT;
