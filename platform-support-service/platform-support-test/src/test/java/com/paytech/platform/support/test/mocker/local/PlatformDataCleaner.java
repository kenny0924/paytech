package com.paytech.platform.support.test.mocker.local;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.test.dal.PlatformDataTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 回收由测试整体拥有的 Platform 表和 Redis 测试命名空间。
 *
 * <p>人工认证、IAM、配置目录、网关、文件与审计表中的内部租户、平台角色和权限编码全局唯一，无法按用例分区；
 * 因此每例开始前要求这些表为空，确认后才在 AfterEach 整表回收。非空时拒绝运行，避免误删本地开发数据。
 * 外部 API 配置六表的编码同样全局唯一，且多数行由被测接口创建、无法预先登记，一并整表回收。
 * Webhook 五表的行同样多由被测接口创建，测试独占并整表回收。
 * 租户、凭证与通知表仍由各自 Mocker 按本例标识回收。
 * 文件扫描策略与要求可能存在已授权的本机开发配置（如 CLIENT_ONBOARDING_FORMAT），不纳入整表回收，
 * 由 {@link OnboardingFileMocker} 按本例插入的标识回收。
 */
@TestComponent
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class PlatformDataCleaner implements Clear {

    /** 测试独占的表；顺序为子表在前，便于定位残留。 */
    public static final List<String> OWNED_TABLES = List.of(
        "t_auth_session", "t_refresh_token_generation", "t_authentication_challenge", "t_password_credential",
        "t_tenant_member_role", "t_tenant_member", "t_user_account",
        "t_role_permission", "t_role", "t_console_resource", "t_permission", "t_jwt_signing_key",
        "t_api_scope", "t_gateway_guard_rule", "t_gateway_route_header", "t_gateway_route_query_param",
        "t_gateway_route", "t_gateway_target", "t_command_idempotency",
        "t_file_scan_result", "t_file_owner_ref", "t_file_upload", "t_file_object", "t_security_event",
        "t_audit_event_attr", "t_audit_event", "t_audit_chain",
        "t_integration_verify_run_step", "t_integration_verify_run", "t_integration_verify_case",
        "t_integration_verify_account", "t_api_field", "t_integration_standard_api", "t_integration_script",
        "t_integration_api", "t_integration", "t_standard_api",
        "t_webhook_attempt", "t_webhook_delivery", "t_webhook_event", "t_webhook_signing_secret", "t_webhook_endpoint");

    private static final Set<String> ALLOWED_DATABASES = Set.of("mor_platform");

    private final PlatformDataTestMapper mapper;
    private final RedisConnectionFactory redis;
    private final Environment environment;
    private boolean owned;
    private boolean unusable;

    /** 全局 BeforeEach 调用：确认本地库与空基线后取得本例回收权。 */
    public void assertEmpty() {
        assertFalse(unusable, "上一例 Platform 测试数据回收失败，当前环境不可复用");
        assertTrue(ALLOWED_DATABASES.contains(mapper.databaseName()), "Platform 测试只允许本地 mor_platform");
        var residual = new ArrayList<String>();
        for (String table : OWNED_TABLES) {
            if (mapper.count(table) > 0) {
                residual.add(table);
            }
        }
        assertTrue(residual.isEmpty(), "测试独占表存在数据，可能是本地开发数据或上次中断的残留，请人工确认后清空："
            + residual);
        assertTrue(redisKeys().isEmpty(), "Redis 测试命名空间存在残留 Key：" + keyPrefix());
        owned = true;
    }

    /** 异步审计等后台写入未能按时结束时调用；之后拒绝回收，避免后台继续写回已清空的表。 */
    public void markUnusable() {
        unusable = true;
    }

    /** 在其他 Mocker 之后执行；只有本例确认过空基线才整表回收。 */
    @Override
    public void clear() {
        if (!owned) {
            return;
        }
        assertFalse(unusable, "后台写入未结束，拒绝回收 Platform 测试数据；当前环境不可复用");
        var actions = new ArrayList<Executable>();
        OWNED_TABLES.forEach(table -> actions.add(() -> mapper.deleteAll(table)));
        actions.add(this::deleteRedisKeys);
        try {
            assertAll("回收 Platform 测试独占数据", actions);
            owned = false;
        } catch (RuntimeException | AssertionError failure) {
            unusable = true;
            throw failure;
        }
    }

    /** 当前测试 Redis 命名空间下的全部原始 Key；绕过业务模板的自动前缀。 */
    public List<String> redisKeys() {
        var keys = new ArrayList<String>();
        try (var connection = redis.getConnection();
            var cursor = connection.keyCommands().scan(
                ScanOptions.scanOptions().match(keyPrefix() + ":*").count(500).build())) {
            cursor.forEachRemaining(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
        }
        return keys;
    }

    private void deleteRedisKeys() {
        var keys = redisKeys();
        if (keys.isEmpty()) {
            return;
        }
        try (var connection = redis.getConnection()) {
            connection.keyCommands().del(keys.stream().map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toArray(byte[][]::new));
        }
    }

    private String keyPrefix() {
        return environment.getRequiredProperty("redis.key-prefix");
    }
}
