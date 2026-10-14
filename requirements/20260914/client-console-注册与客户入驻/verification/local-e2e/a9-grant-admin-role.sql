-- 本机全链路 A9 补充（2026-10-14 用户同意）：PLATFORM_ADMIN 为系统角色，管理接口拒绝修改，
-- 沿用初始化目录做法以 SQL 授予 5 个已启用的入驻权限，并递增角色版本使会话重新计算授权。只在本机 mor_platform 执行一次。
-- 回滚：DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 9200000000000101 AND 9200000000000105;
--       UPDATE t_role SET version=version+1 WHERE role_id=900000000000000510;
START TRANSACTION;
SET @a9_time = UTC_TIMESTAMP(6);
INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time)
SELECT 9200000000000100 + seq, 900000000000000510, permission_id, 'ACTIVE', 1, @a9_time, @a9_time
FROM (SELECT permission_id, CASE permission_code
        WHEN 'CLIENT_ONBOARDING_REVIEW_READ' THEN 1 WHEN 'CLIENT_ONBOARDING_CLASSIFICATION_EDIT' THEN 2
        WHEN 'CLIENT_ONBOARDING_EVIDENCE_REVIEW' THEN 3 WHEN 'CLIENT_ONBOARDING_RETURN' THEN 4
        WHEN 'CLIENT_ONBOARDING_DECIDE' THEN 5 END AS seq
      FROM t_permission WHERE permission_code LIKE 'CLIENT\_ONBOARDING\_%' AND status='ACTIVE') p
WHERE seq IS NOT NULL;
UPDATE t_role SET version=version+1, update_time=@a9_time WHERE role_id=900000000000000510;
COMMIT;
