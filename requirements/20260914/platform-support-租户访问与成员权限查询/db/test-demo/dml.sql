-- User requested one test sample after deployment, 2026-09-14.
-- Exact target: desktop-linux / mor-test-mysql-1 / 127.0.0.1:23306 / mor_platform.
-- One synthetic CLIENT tenant + one member + one tenant-scoped role + two demo permissions.
-- 9 INSERT rows across 7 existing tables. No UPDATE, DDL, login credentials, sessions, or API keys.
-- Synthetic CCT owner reference only; no real CCT projection/event is claimed (source fields NULL).
-- Run in one interactive UTF-8 connection. All exact IDs/codes were absent at preflight.
SET NAMES utf8mb4;
SET time_zone='+00:00';
SET @tenant_demo_time=UTC_TIMESTAMP(6);
SELECT DATABASE(),@@hostname,@@character_set_client;
START TRANSACTION;
INSERT INTO t_access_tenant(tenant_id,tenant_type,owner_ref_id,tenant_name,owner_status,source_version,source_payload_hash,security_status,version,create_time,update_time) VALUES
(900000000000065001,'CLIENT',900000000000065101,'DEMO 客户租户（验收）','ACTIVE',NULL,NULL,'ACTIVE',1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_user_account(account_id,realm,account_type,login_name,email_vault_token,email_masked,email_verified_time,status,consecutive_password_failures,locked_until,last_password_failure_time,credential_version,auth_epoch,version,create_time,update_time) VALUES
(900000000000065002,'CUSTOMER','HUMAN',NULL,NULL,'d***@example.test',NULL,'ACTIVE',0,NULL,NULL,1,1,1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_tenant_member(member_id,tenant_id,account_id,member_type,status,invited_by,version,create_time,update_time) VALUES
(900000000000065003,900000000000065001,900000000000065002,'OWNER','ACTIVE',NULL,1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_role(role_id,tenant_scope,tenant_id,scope_key,role_code,role_name,status,system_role,version,create_time,update_time) VALUES
(900000000000065004,'TENANT',900000000000065001,'900000000000065001','DEMO_CLIENT_VIEWER','示例客户只读角色','ACTIVE',0,1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_permission(permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES
(900000000000065006,'DEMO_CLIENT_ORDER_READ','DEMO_CLIENT_ORDER','READ','LOW','ACTIVE',1,@tenant_demo_time,@tenant_demo_time),
(900000000000065007,'DEMO_CLIENT_REFUND_READ','DEMO_CLIENT_REFUND','READ','LOW','ACTIVE',1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_role_permission(role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES
(900000000000065008,900000000000065004,900000000000065006,'ACTIVE',1,@tenant_demo_time,@tenant_demo_time),
(900000000000065009,900000000000065004,900000000000065007,'ACTIVE',1,@tenant_demo_time,@tenant_demo_time);
INSERT INTO t_tenant_member_role(tenant_member_role_id,tenant_member_id,tenant_id,role_id,role_scope_key,effective_from,effective_to,granted_by,status,version,create_time,update_time) VALUES
(900000000000065005,900000000000065003,900000000000065001,900000000000065004,'900000000000065001',@tenant_demo_time,NULL,221546852372267008,'ACTIVE',1,@tenant_demo_time,@tenant_demo_time);
SELECT 'tenant',tenant_id,tenant_name,tenant_type,owner_status,security_status,version FROM t_access_tenant WHERE tenant_id=900000000000065001;
SELECT 'member_role_permissions',m.member_id,a.realm,a.account_type,a.email_masked,r.role_code,r.scope_key,p.permission_code,rp.status,g.status
FROM t_tenant_member m JOIN t_user_account a ON a.account_id=m.account_id
JOIN t_tenant_member_role g ON g.tenant_member_id=m.member_id AND g.tenant_id=m.tenant_id
JOIN t_role r ON r.role_id=g.role_id AND r.scope_key=g.role_scope_key AND r.tenant_id=m.tenant_id
JOIN t_role_permission rp ON rp.role_id=r.role_id JOIN t_permission p ON p.permission_id=rp.permission_id
WHERE m.tenant_id=900000000000065001 AND m.member_id=900000000000065003 ORDER BY p.permission_id;
-- Inspect INSERT counts 1/1/1/1/2/2/1 and exactly two valid permission sources, then explicit COMMIT.
-- Any error or mismatch: ROLLBACK. Do not rerun or use INSERT IGNORE/REPLACE/--force.
