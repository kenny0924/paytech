-- Client Webhook：mor-test 停用运营授权（回退用）。预期影响行数：2 / 1。
START TRANSACTION;
SET @whk_deact_time = UTC_TIMESTAMP(6);
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@whk_deact_time
 WHERE role_permission_id IN (900000000000090201, 900000000000090202) AND status='ACTIVE';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@whk_deact_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
