-- Break-glass recovery: restore the Operations Gateway management chain.
--
-- This file is an emergency, manually reviewed asset. It must never be called by
-- Compose, application startup, initialize.py, tests, or an ordinary release.
-- Run it only with an approved, individually attributable break-glass database
-- account. Execute this file as mysql batch mode stdin without --force; a failed
-- CALL must terminate the client before DROP or verification statements. Set the
-- non-secret approval ticket through --init-command in that same connection:
--
--   mysql --batch --init-command="SET @break_glass_ticket_ref = 'INC-2026-000001';" \
--     < test-environment/sql/2026-09-05-operations-gateway-control-plane-break-glass.sql
--
-- Actor convention — PRESERVE_APPLICATION_UPDATED_BY:
-- `updated_by` remains the last real application Account that changed the row.
-- This DML deliberately does not replace it with an administrator, application,
-- zero, or negative pseudo Account. The database account audit plus the required
-- approval ticket is the authority for this exceptional database operation.
--
-- The procedure is temporary operational scaffolding so MySQL can conditionally
-- SIGNAL and roll back. It uses invoker privileges and is dropped after success.
-- If validation fails, no row change commits; retain the database audit evidence,
-- inspect the failure, and drop the procedure only after incident review.

SET NAMES utf8mb4;
USE mor_platform;

DELIMITER //

CREATE PROCEDURE recover_operations_gateway_control_plane()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE v_target_candidate_count INT DEFAULT 0;
    DECLARE v_target_exact_count INT DEFAULT 0;
    DECLARE v_permission_candidate_count INT DEFAULT 0;
    DECLARE v_permission_exact_count INT DEFAULT 0;
    DECLARE v_route_candidate_count INT DEFAULT 0;
    DECLARE v_expected_route_count INT DEFAULT 0;
    DECLARE v_header_total_count INT DEFAULT 0;
    DECLARE v_header_exact_count INT DEFAULT 0;
    DECLARE v_query_total_count INT DEFAULT 0;
    DECLARE v_query_exact_count INT DEFAULT 0;
    DECLARE v_guard_total_count INT DEFAULT 0;
    DECLARE v_guard_exact_count INT DEFAULT 0;
    DECLARE v_active_target_count INT DEFAULT 0;
    DECLARE v_active_route_count INT DEFAULT 0;
    DECLARE v_active_route_overlap_count INT DEFAULT 0;
    DECLARE v_target_rows_changed INT DEFAULT 0;
    DECLARE v_route_rows_changed INT DEFAULT 0;
    DECLARE v_recovery_time DATETIME(6);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- SERIALIZABLE is local to this transaction. It gives the child-table
    -- FOR UPDATE ranges next-key protection even if the caller session normally
    -- uses READ COMMITTED, and serializes the final full-catalog validation.
    SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
    START TRANSACTION;

    IF @break_glass_ticket_ref IS NULL
       OR CHAR_LENGTH(TRIM(@break_glass_ticket_ref)) NOT BETWEEN 8 AND 128
       OR NOT REGEXP_LIKE(TRIM(@break_glass_ticket_ref), '^[A-Za-z0-9][A-Za-z0-9._:/-]+$') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Break-glass approval ticket is missing or invalid';
    END IF;

    SET v_recovery_time = UTC_TIMESTAMP(6);

    -- Lock both identity keys before validating the immutable Target identity.
    SELECT target_id, target_service_code
    FROM t_gateway_target
    WHERE target_id = 900000000000000001
       OR target_service_code = 'operations-console-bff'
    ORDER BY target_id
    FOR UPDATE;

    SELECT COUNT(*)
    INTO v_target_candidate_count
    FROM t_gateway_target
    WHERE target_id = 900000000000000001
       OR target_service_code = 'operations-console-bff';

    SELECT COUNT(*)
    INTO v_target_exact_count
    FROM t_gateway_target
    WHERE target_id = 900000000000000001
      AND target_service_code = 'operations-console-bff'
      AND discovery_service_name = 'operations-console-bff'
      AND channel = 'OPERATIONS'
      AND status IN ('ACTIVE', 'DISABLED')
      AND version BETWEEN 1 AND 2147483646
      AND created_by > 0
      AND updated_by > 0;

    IF v_target_candidate_count <> 1 OR v_target_exact_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Approved operations-console-bff Target identity is missing or inconsistent';
    END IF;

    -- The recovery does not grant or repair permissions. They must already be the
    -- approved ACTIVE definitions used by the six management Routes.
    SELECT permission_id, permission_code
    FROM t_permission
    WHERE permission_id IN (900000000000000534, 900000000000000535)
       OR permission_code IN ('GATEWAY_CONFIG_READ', 'GATEWAY_CONFIG_MANAGE')
    ORDER BY permission_id
    FOR UPDATE;

    SELECT COUNT(*)
    INTO v_permission_candidate_count
    FROM t_permission
    WHERE permission_id IN (900000000000000534, 900000000000000535)
       OR permission_code IN ('GATEWAY_CONFIG_READ', 'GATEWAY_CONFIG_MANAGE');

    SELECT COUNT(*)
    INTO v_permission_exact_count
    FROM t_permission
    WHERE (
        permission_id = 900000000000000534
        AND permission_code = 'GATEWAY_CONFIG_READ'
        AND resource_type = 'GATEWAY_CONFIGURATION'
        AND action_code = 'READ'
        AND risk_level = 'LOW'
        AND status = 'ACTIVE'
        AND version >= 1
    ) OR (
        permission_id = 900000000000000535
        AND permission_code = 'GATEWAY_CONFIG_MANAGE'
        AND resource_type = 'GATEWAY_CONFIGURATION'
        AND action_code = 'MANAGE'
        AND risk_level = 'HIGH'
        AND status = 'ACTIVE'
        AND version >= 1
    );

    IF v_permission_candidate_count <> 2 OR v_permission_exact_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Gateway management Permission definitions are missing, inactive, or inconsistent';
    END IF;

    -- Lock every row that could occupy an approved Route ID or Route Code. This
    -- catches swapped identities instead of updating by a broad prefix.
    SELECT route_id, route_code
    FROM t_gateway_route
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
       OR route_code IN (
           'operations-gateway-target-list',
           'operations-gateway-target-detail',
           'operations-gateway-target-status',
           'operations-gateway-route-list',
           'operations-gateway-route-detail',
           'operations-gateway-route-status'
       )
    ORDER BY route_id
    FOR UPDATE;

    SELECT COUNT(*)
    INTO v_route_candidate_count
    FROM t_gateway_route
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
       OR route_code IN (
           'operations-gateway-target-list',
           'operations-gateway-target-detail',
           'operations-gateway-target-status',
           'operations-gateway-route-list',
           'operations-gateway-route-detail',
           'operations-gateway-route-status'
       );

    WITH approved_routes AS (
        SELECT 900000000000001018 AS route_id,
               'operations-gateway-target-list' AS route_code,
               'GET' AS http_method,
               '/api/operations/gateway-admin/v1/target-services' AS external_path_template,
               '/gateway-admin/v1/target-services' AS downstream_path_template,
               900000000000000534 AS permission_id
        UNION ALL
        SELECT 900000000000001019,
               'operations-gateway-target-detail',
               'GET',
               '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}',
               '/gateway-admin/v1/target-services/{targetServiceCode}',
               900000000000000534
        UNION ALL
        SELECT 900000000000001020,
               'operations-gateway-target-status',
               'PATCH',
               '/api/operations/gateway-admin/v1/target-services/{targetServiceCode}/status',
               '/gateway-admin/v1/target-services/{targetServiceCode}/status',
               900000000000000535
        UNION ALL
        SELECT 900000000000001021,
               'operations-gateway-route-list',
               'GET',
               '/api/operations/gateway-admin/v1/routes',
               '/gateway-admin/v1/routes',
               900000000000000534
        UNION ALL
        SELECT 900000000000001022,
               'operations-gateway-route-detail',
               'GET',
               '/api/operations/gateway-admin/v1/routes/{routeCode}',
               '/gateway-admin/v1/routes/{routeCode}',
               900000000000000534
        UNION ALL
        SELECT 900000000000001023,
               'operations-gateway-route-status',
               'PATCH',
               '/api/operations/gateway-admin/v1/routes/{routeCode}/status',
               '/gateway-admin/v1/routes/{routeCode}/status',
               900000000000000535
    )
    SELECT COUNT(*)
    INTO v_expected_route_count
    FROM approved_routes approved
    JOIN t_gateway_route route
      ON route.route_id = approved.route_id
     AND route.route_code = approved.route_code
     AND route.channel = 'OPERATIONS'
     AND route.http_method = approved.http_method
     AND route.external_path_template = approved.external_path_template
     AND route.downstream_path_template = approved.downstream_path_template
     AND route.target_id = 900000000000000001
     AND route.security_profile_code = 'OPERATIONS_JWT'
     AND route.authorization_mode = 'HUMAN_PERMISSION'
     AND route.permission_id = approved.permission_id
     AND route.required_scope_code IS NULL
     AND route.request_cookie_mode = 'DROP'
     AND route.response_cookie_mode = 'NONE'
     AND route.body_limit_bytes = 16384
     AND route.body_receive_timeout_millis = 5000
     AND route.response_timeout_millis = 10000
     AND route.status IN ('ACTIVE', 'DISABLED')
     AND route.version BETWEEN 1 AND 2147483646
     AND route.created_by > 0
     AND route.updated_by > 0;

    IF v_route_candidate_count <> 6 OR v_expected_route_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Approved Operations Gateway Route identities or root configuration are inconsistent';
    END IF;

    -- Freeze all approved child configuration before checking its exact shape.
    -- Range locks also prevent a concurrent insert for these six Route IDs.
    SELECT route_id, route_header_id
    FROM t_gateway_route_header
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
    ORDER BY route_id, route_header_id
    FOR UPDATE;

    SELECT route_id, route_query_param_id
    FROM t_gateway_route_query_param
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
    ORDER BY route_id, route_query_param_id
    FOR UPDATE;

    SELECT route_id, guard_rule_id
    FROM t_gateway_guard_rule
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
    ORDER BY route_id, guard_rule_id
    FOR UPDATE;

    SELECT COUNT(*)
    INTO v_header_total_count
    FROM t_gateway_route_header
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023;

    WITH approved_headers AS (
        SELECT 900000000000001018 AS route_id, 'authorization' AS header_name,
               'CORS_REQUEST_ALLOWED' AS header_use
        UNION ALL SELECT 900000000000001018, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001019, 'authorization', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001019, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001020, 'authorization', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001020, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001020, 'content-type', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001020, 'content-type', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001020, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001020, 'idempotency-key', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001021, 'authorization', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001021, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001022, 'authorization', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001022, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001023, 'authorization', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001023, 'origin', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001023, 'content-type', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001023, 'content-type', 'FORWARD_REQUEST'
        UNION ALL SELECT 900000000000001023, 'idempotency-key', 'CORS_REQUEST_ALLOWED'
        UNION ALL SELECT 900000000000001023, 'idempotency-key', 'FORWARD_REQUEST'
    )
    SELECT COUNT(*)
    INTO v_header_exact_count
    FROM approved_headers approved
    JOIN t_gateway_route_header header
      ON header.route_id = approved.route_id
     AND header.header_name = approved.header_name
     AND header.header_use = approved.header_use;

    IF v_header_total_count <> 20 OR v_header_exact_count <> 20 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operations Gateway management Route Header allowlists are inconsistent';
    END IF;

    SELECT COUNT(*)
    INTO v_query_total_count
    FROM t_gateway_route_query_param
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023;

    WITH approved_query_parameters AS (
        SELECT 900000000000001018 AS route_id, 'page' AS parameter_name
        UNION ALL SELECT 900000000000001018, 'size'
        UNION ALL SELECT 900000000000001018, 'channel'
        UNION ALL SELECT 900000000000001018, 'status'
        UNION ALL SELECT 900000000000001018, 'targetServiceCode'
        UNION ALL SELECT 900000000000001021, 'page'
        UNION ALL SELECT 900000000000001021, 'size'
        UNION ALL SELECT 900000000000001021, 'channel'
        UNION ALL SELECT 900000000000001021, 'status'
        UNION ALL SELECT 900000000000001021, 'targetServiceCode'
        UNION ALL SELECT 900000000000001021, 'routeCode'
    )
    SELECT COUNT(*)
    INTO v_query_exact_count
    FROM approved_query_parameters approved
    JOIN t_gateway_route_query_param query_parameter
      ON query_parameter.route_id = approved.route_id
     AND query_parameter.parameter_name = approved.parameter_name;

    IF v_query_total_count <> 11 OR v_query_exact_count <> 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operations Gateway management Route Query allowlists are inconsistent';
    END IF;

    SELECT COUNT(*)
    INTO v_guard_total_count
    FROM t_gateway_guard_rule
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023;

    SELECT COUNT(*)
    INTO v_guard_exact_count
    FROM t_gateway_guard_rule
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
      AND (
          (rule_type = 'RATE_LIMIT' AND dimension = 'SHARED_ROUTE'
              AND capacity = 300 AND refill_tokens = 300 AND refill_period_millis = 60000
              AND concurrency_limit IS NULL)
       OR (rule_type = 'CONCURRENCY' AND dimension = 'SHARED_ROUTE'
              AND capacity IS NULL AND refill_tokens IS NULL AND refill_period_millis IS NULL
              AND concurrency_limit = 50)
       OR (rule_type = 'RATE_LIMIT' AND dimension = 'CLIENT_IP'
              AND capacity = 10 AND refill_tokens = 30 AND refill_period_millis = 60000
              AND concurrency_limit IS NULL)
       OR (rule_type = 'CONCURRENCY' AND dimension = 'CLIENT_IP'
              AND capacity IS NULL AND refill_tokens IS NULL AND refill_period_millis IS NULL
              AND concurrency_limit = 3)
       OR (rule_type = 'RATE_LIMIT' AND dimension = 'AUTHENTICATED_PRINCIPAL'
              AND capacity = 10 AND refill_tokens = 10 AND refill_period_millis = 60000
              AND concurrency_limit IS NULL)
       OR (rule_type = 'CONCURRENCY' AND dimension = 'AUTHENTICATED_PRINCIPAL'
              AND capacity IS NULL AND refill_tokens IS NULL AND refill_period_millis IS NULL
              AND concurrency_limit = 2)
      );

    IF v_guard_total_count <> 36 OR v_guard_exact_count <> 36 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operations Gateway management Route Guard configuration is inconsistent';
    END IF;

    -- Only DISABLED roots change. ACTIVE rows retain status, version and timestamp.
    -- `updated_by` is intentionally omitted under PRESERVE_APPLICATION_UPDATED_BY.
    UPDATE t_gateway_target
    SET status = 'ACTIVE',
        version = version + 1,
        update_time = v_recovery_time
    WHERE target_id = 900000000000000001
      AND target_service_code = 'operations-console-bff'
      AND discovery_service_name = 'operations-console-bff'
      AND channel = 'OPERATIONS'
      AND status = 'DISABLED';
    SET v_target_rows_changed = ROW_COUNT();

    WITH approved_routes AS (
        SELECT 900000000000001018 AS route_id, 'operations-gateway-target-list' AS route_code
        UNION ALL SELECT 900000000000001019, 'operations-gateway-target-detail'
        UNION ALL SELECT 900000000000001020, 'operations-gateway-target-status'
        UNION ALL SELECT 900000000000001021, 'operations-gateway-route-list'
        UNION ALL SELECT 900000000000001022, 'operations-gateway-route-detail'
        UNION ALL SELECT 900000000000001023, 'operations-gateway-route-status'
    )
    UPDATE t_gateway_route route
    JOIN approved_routes approved
      ON approved.route_id = route.route_id
     AND approved.route_code = route.route_code
    SET route.status = 'ACTIVE',
        route.version = route.version + 1,
        route.update_time = v_recovery_time
    WHERE route.channel = 'OPERATIONS'
      AND route.target_id = 900000000000000001
      AND route.status = 'DISABLED';
    SET v_route_rows_changed = ROW_COUNT();

    -- Match GatewayConfigurationCompiler.rejectOverlappingRoutes for every
    -- effective Route, including Routes outside this recovery set. A Route is
    -- effective only when both its Root and Target are ACTIVE. Shape identity is
    -- HTTP Method + segment count + distinct (case-sensitive) variable-name
    -- count; two equal shapes overlap when no position contains two different
    -- fixed segments.
    WITH effective_routes AS (
        SELECT route.route_id,
               route.route_code,
               route.http_method,
               route.external_path_template
        FROM t_gateway_route route
        JOIN t_gateway_target target
          ON target.target_id = route.target_id
         AND target.channel = route.channel
         AND target.status = 'ACTIVE'
        WHERE route.status = 'ACTIVE'
    ),
    route_segments AS (
        SELECT effective.route_id,
               path_segment.segment_position,
               path_segment.segment_value,
               REGEXP_LIKE(
                   path_segment.segment_value,
                   '^[{][A-Za-z][A-Za-z0-9_]{0,63}[}]$',
                   'c'
               ) AS is_variable
        FROM effective_routes effective
        JOIN JSON_TABLE(
            CASE
                WHEN effective.external_path_template = '/' THEN '[]'
                ELSE CONCAT(
                    '["',
                    REPLACE(SUBSTRING(effective.external_path_template, 2), '/', '","'),
                    '"]'
                )
            END,
            '$[*]' COLUMNS (
                segment_position FOR ORDINALITY,
                segment_value VARCHAR(1000) PATH '$'
            )
        ) AS path_segment ON TRUE
    ),
    route_shapes AS (
        SELECT effective.route_id,
               effective.route_code,
               effective.http_method,
               COUNT(segment.segment_position) AS segment_count,
               COUNT(DISTINCT CASE
                   WHEN segment.is_variable = 1 THEN CAST(segment.segment_value AS BINARY)
                   ELSE NULL
               END) AS variable_count
        FROM effective_routes effective
        LEFT JOIN route_segments segment ON segment.route_id = effective.route_id
        GROUP BY effective.route_id, effective.route_code, effective.http_method
    ),
    overlapping_routes AS (
        SELECT first_route.route_id AS first_route_id,
               second_route.route_id AS second_route_id
        FROM route_shapes first_route
        JOIN route_shapes second_route
          ON first_route.route_id < second_route.route_id
         AND first_route.http_method = second_route.http_method
         AND first_route.segment_count = second_route.segment_count
         AND first_route.variable_count = second_route.variable_count
        WHERE NOT EXISTS (
            SELECT 1
            FROM route_segments first_segment
            JOIN route_segments second_segment
              ON second_segment.route_id = second_route.route_id
             AND second_segment.segment_position = first_segment.segment_position
            WHERE first_segment.route_id = first_route.route_id
              AND first_segment.is_variable = 0
              AND second_segment.is_variable = 0
              AND CAST(first_segment.segment_value AS BINARY)
                    <> CAST(second_segment.segment_value AS BINARY)
        )
    )
    SELECT COUNT(*)
    INTO v_active_route_overlap_count
    FROM overlapping_routes;

    IF v_active_route_overlap_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Break-glass recovery would create an overlapping ACTIVE Gateway Route';
    END IF;

    SELECT COUNT(*)
    INTO v_active_target_count
    FROM t_gateway_target
    WHERE target_id = 900000000000000001
      AND target_service_code = 'operations-console-bff'
      AND discovery_service_name = 'operations-console-bff'
      AND channel = 'OPERATIONS'
      AND status = 'ACTIVE';

    SELECT COUNT(*)
    INTO v_active_route_count
    FROM t_gateway_route
    WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
      AND route_code IN (
          'operations-gateway-target-list',
          'operations-gateway-target-detail',
          'operations-gateway-target-status',
          'operations-gateway-route-list',
          'operations-gateway-route-detail',
          'operations-gateway-route-status'
      )
      AND channel = 'OPERATIONS'
      AND target_id = 900000000000000001
      AND status = 'ACTIVE';

    IF v_active_target_count <> 1 OR v_active_route_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Break-glass recovery postcondition failed';
    END IF;

    COMMIT;

    SELECT TRIM(@break_glass_ticket_ref) AS approval_ticket_ref,
           v_target_rows_changed AS target_rows_changed,
           v_route_rows_changed AS route_rows_changed,
           v_recovery_time AS recovered_at_utc;
END//

CALL recover_operations_gateway_control_plane()//
DROP PROCEDURE recover_operations_gateway_control_plane//

DELIMITER ;

-- Verification only. Exactly one Target and six Routes must be ACTIVE. Versions
-- increase only for rows that were DISABLED when this recovery ran.
SELECT target_id, target_service_code, discovery_service_name, channel, status,
       version, updated_by, update_time
FROM t_gateway_target
WHERE target_id = 900000000000000001
  AND target_service_code = 'operations-console-bff';

SELECT route_id, route_code, channel, http_method, external_path_template,
       downstream_path_template, target_id, authorization_mode, permission_id,
       status, version, updated_by, update_time
FROM t_gateway_route
WHERE route_id BETWEEN 900000000000001018 AND 900000000000001023
ORDER BY route_id;

-- Gateway runtime verification is intentionally outside this SQL file: wait for
-- the normal Snapshot poll, then verify a later successful load and all six public
-- management paths through the normal ingress. Never add a static fallback Route.
