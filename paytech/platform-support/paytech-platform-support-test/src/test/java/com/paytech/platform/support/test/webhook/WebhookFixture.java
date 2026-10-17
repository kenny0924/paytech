package com.paytech.platform.support.test.webhook;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;
import com.paytech.platform.support.delivery.ext.eventhandler.WebhookDeliveryHandler;
import com.paytech.platform.support.test.dal.WebhookTestMapper;
import com.paytech.platform.support.test.mock.external.WebhookReceiverMockController;

import lombok.RequiredArgsConstructor;

/**
 * 当前用例的 Webhook 前置数据：两个 CLIENT 租户、直接插入的端点与密钥，以及补偿任务的等待与扫描
 * <li>五张 Webhook 表由 PlatformDataCleaner 整表回收；本 Fixture 只清理自己插入的租户与 WEBHOOK_DELIVERY 任务</li>
 */
@TestComponent
@Order(-100)
@RequiredArgsConstructor
public class WebhookFixture implements Clear {

    /** 主测试租户与其 Client。 */
    public static final long TENANT_ID = 880000000000071001L;

    public static final long CLIENT_ID = 880000000000072001L;

    /** 另一个租户，用于验证订阅只按本租户分发。 */
    public static final long OTHER_TENANT_ID = 880000000000071002L;

    public static final long OTHER_CLIENT_ID = 880000000000072002L;

    /** 没有开通平台访问的 Client。 */
    public static final long UNKNOWN_CLIENT_ID = 880000000000072099L;

    /** 白名单中的测试调用方，与 application-mock.yml 一致。 */
    public static final String CALLER = "webhook-test";

    public static final String PEER_CALLER = "webhook-test-peer";

    private static final AtomicLong IDS = new AtomicLong(880000000000073000L);

    private final Environment environment;
    private final WebhookTestMapper mapper;
    private final WebhookVaultMock vault;
    private final CompensationEventScanner scanner;
    private final WebhookDeliveryHandler handler;
    private boolean used;
    private boolean reusable = true;

    /** 缺表快速失败；保留租户段与 Webhook 任务必须为空，再插入两个租户。 */
    public void prepare() {
        assertTrue(reusable, "Webhook 测试后台未结束，环境不可复用");
        try {
            assertEquals(0, mapper.reservedTenantCount(), "Webhook 测试租户 ID 段已被占用，请核对本地数据");
            assertEquals(0, mapper.taskCount(), "Webhook 测试需独占空的 WEBHOOK_DELIVERY 任务环境");
        } catch (DataAccessException unavailable) {
            throw new AssertionError("请手工执行 docs/database/sql/changes/2026-10-15-webhook-delivery.sql 准备五张 Webhook 表；测试不自动建表", unavailable);
        }
        used = true;
        mapper.tenant(TENANT_ID, CLIENT_ID);
        mapper.tenant(OTHER_TENANT_ID, OTHER_CLIENT_ID);
    }

    /** 接收端地址：独立 Mock HTTP 服务上的 /webhook-receiver/{name}。 */
    public String receiverUrl(String name) {
        return environment.getRequiredProperty("test.mock-http.base-url") + WebhookReceiverMockController.PREFIX + name;
    }

    /**
     * 直接插入一个 ACTIVE 端点与第 1 把 ACTIVE 密钥，并在 Vault Mock 登记该密钥
     *
     * @param eventTypesJson 订阅事件类型 JSON 数组
     * @return 端点与密钥
     */
    public Endpoint endpoint(long tenantId, String url, String eventTypesJson) {
        long endpointId = IDS.incrementAndGet();
        long secretId = IDS.incrementAndGet();
        String token = "vlt_whkfix" + String.format("%033d", secretId % 1_000_000_000L);
        String secret = "whsec_" + "A".repeat(42) + (char) ('a' + (int) (secretId % 26));
        vault.allow(token, secret);
        mapper.endpoint(endpointId, tenantId, url, eventTypesJson, WebhookEndpointStatus.ACTIVE.getCode());
        mapper.secret(secretId, endpointId, 1, token, WebhookSecretStatus.ACTIVE.getCode(), 1, null);
        return new Endpoint(endpointId, secretId, secret);
    }

    /** 给端点追加一把仍在宽限期内的 RETIRED 密钥。 */
    public String graceSecret(long endpointId, Instant validUntil) {
        long secretId = IDS.incrementAndGet();
        String token = "vlt_whkfix" + String.format("%033d", secretId % 1_000_000_000L);
        String secret = "whsec_" + "B".repeat(42) + (char) ('a' + (int) (secretId % 26));
        vault.allow(token, secret);
        mapper.secret(secretId, endpointId, 0, token, WebhookSecretStatus.RETIRED.getCode(), null, validUntil);
        return secret;
    }

    /** 订阅事件类型的 JSON 数组。 */
    public static String eventTypes(WebhookEventType... types) {
        return java.util.Arrays.stream(types).map(type -> "\"" + type.getCode() + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    /** 新的测试 ID。 */
    public long nextId() {
        return IDS.incrementAndGet();
    }

    /** 等待提交后唤醒的执行结束，再允许断言或清理。 */
    public void awaitIdle() {
        if (!used) {
            return;
        }
        try {
            await().atMost(Duration.ofSeconds(15)).until(() -> mapper.activeTasks() == 0);
        } catch (RuntimeException | AssertionError timeout) {
            reusable = false;
            throw timeout;
        }
    }

    /** 让等待重试的任务立即到期，并经正式 Scanner 执行一轮。 */
    public void retryDue() {
        awaitIdle();
        mapper.due();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(handler.eventType())));
        awaitIdle();
    }

    /** 背景任务未停止时阻止公共清理。 */
    public void assertReusable() {
        assertTrue(reusable, "Webhook 测试环境不可安全清理");
    }

    /** 先确认没有活动任务，再删除本例任务与租户。 */
    @Override
    public void clear() {
        if (!used) {
            return;
        }
        assertTrue(reusable, "后台任务未停止，拒绝清理");
        awaitIdle();
        try {
            mapper.clearTasks();
            mapper.clearTenants();
            used = false;
        } catch (RuntimeException failure) {
            reusable = false;
            throw failure;
        }
    }

    /** 直接插入的端点。 */
    public record Endpoint(long endpointId, long secretId, String secret) {
    }
}
