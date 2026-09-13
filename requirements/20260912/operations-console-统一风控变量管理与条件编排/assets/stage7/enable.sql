-- RCO-19: existing mor-test only. Execute after all three candidate applications are healthy.
-- The runner asserts each affected-row count and the administrator role CAS in one transaction.
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE route_id BETWEEN 900000000000031001 AND 900000000000031009 AND status='DISABLED' AND version=1;
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE resource_id BETWEEN 900000000000031301 AND 900000000000031303 AND status='DISABLED' AND version=1;
UPDATE t_role_permission SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE role_permission_id IN (900000000000031201,900000000000031202) AND role_id=900000000000000510 AND status='REVOKED' AND version=1;
UPDATE t_role SET version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND status='ACTIVE' AND version=16;
