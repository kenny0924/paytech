-- Correct the RCO-19 registration to the existing 64 KiB Platform/Gateway absolute ceiling.
-- Only the just-activated test route is changed; the runner requires exactly one affected row.
UPDATE t_gateway_route SET body_limit_bytes=65536,version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE route_id=900000000000031008 AND route_code='operations-risk-variable-members-replace'
 AND body_limit_bytes=262144 AND status='ACTIVE' AND version=2;
