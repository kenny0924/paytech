-- Stage 7 correction of three newly inserted display names to the approved UTF-8 text.
-- Cause: initial interactive MySQL client defaulted to latin1; SQL source was UTF-8.
-- Exact new resource IDs + before bytes + ACTIVE/version=2. No business data change.
-- Inspect each UPDATE=1 and all three UTF-8 bytes before explicit COMMIT.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE t_console_resource SET display_name=CONVERT(X'E7A79FE688B7E8AEBFE997AE' USING utf8mb4),version=version+1,update_time=UTC_TIMESTAMP(6) WHERE DATABASE()='mor_platform' AND resource_id=900000000000061301 AND channel='OPERATIONS' AND status='ACTIVE' AND version=2 AND HEX(display_name)='C3A7C2A7C5B8C3A6CB86C2B7C3A8C2AEC2BFC3A9E28094C2AE';
UPDATE t_console_resource SET display_name=CONVERT(X'E5AE89E585A8E69A82E5819CE4B88EE681A2E5A48D' USING utf8mb4),version=version+1,update_time=UTC_TIMESTAMP(6) WHERE DATABASE()='mor_platform' AND resource_id=900000000000061302 AND channel='OPERATIONS' AND status='ACTIVE' AND version=2 AND HEX(display_name)='C3A5C2AEE280B0C3A5E280A6C2A8C3A6C5A1E2809AC3A5C281C593C3A4C2B8C5BDC3A6C281C2A2C3A5C2A4C28D';
UPDATE t_console_resource SET display_name=CONVERT(X'E69FA5E79C8BE68890E59198E69D83E99990' USING utf8mb4),version=version+1,update_time=UTC_TIMESTAMP(6) WHERE DATABASE()='mor_platform' AND resource_id=900000000000061303 AND channel='OPERATIONS' AND status='ACTIVE' AND version=2 AND HEX(display_name)='C3A6C5B8C2A5C3A7C593E280B9C3A6CB86C290C3A5E28098CB9CC3A6C29DC692C3A9E284A2C290';
SELECT resource_id,display_name,HEX(display_name),status,version FROM t_console_resource WHERE resource_id BETWEEN 900000000000061301 AND 900000000000061303 ORDER BY resource_id;
-- Manual COMMIT only after 1/1/1 and exact names/version=3; else ROLLBACK.
