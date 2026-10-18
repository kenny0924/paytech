package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.webhook.WebhookEventApi;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.WebhookTestMapper;
import com.paytech.platform.support.test.mocker.external.WebhookReceiverMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.platform.support.test.webhook.WebhookFixture;
import com.paytech.platform.support.test.webhook.WebhookVaultMock;

/**
 * Webhook 测试的公共准备：两个 CLIENT 租户、Webhook Vault Mock 与接收端 Mock
 * <li>继承 Operations 入口调用，管理接口测试在自己的 BeforeEach 中创建人员</li>
 */
public abstract class WebhookTestBase extends OperationsApiTestBase {

    protected static final String EVENTS = "/webhooks/v1/events";

    protected static final String ADMIN = "/webhook-admin/v1";

    @Autowired protected WebhookFixture fixture;
    @Autowired protected WebhookVaultMock vault;
    @Autowired protected WebhookReceiverMocker receiver;
    @Autowired protected WebhookTestMapper webhookData;
    @Autowired protected java.time.Clock clock;

    /** 父类全局初始化后插入两个租户。 */
    @BeforeEach
    void setUpWebhookMocks() {
        fixture.prepare();
    }

    /** 异步超时后不清理仍可能被后台访问的资源。 */
    @Override
    protected void checkClearAllowed() {
        fixture.assertReusable();
    }

    /** 等待投递执行结束，再检查接收端与 Vault 的意外调用。 */
    @AfterEach
    void checkWebhookCalls() {
        fixture.awaitIdle();
        Assertions.assertAll(receiver::assertNoUnexpectedCalls, vault::assertExpected);
    }

    /** 以人员身份 PATCH。 */
    protected PlatformResponseChecker patch(String path, com.paytech.platform.support.test.builder.OperatorBuilder.Operator operator,
        Object body) {
        return call(HttpMethod.PATCH, path, headers(operator), body);
    }

    /** 以调用方身份提交事件；caller 为空时不带 Header。 */
    protected PlatformResponseChecker submit(String caller, Object body) {
        HttpHeaders headers = TestRequestHeaders.internal("plt-whk-" + java.util.UUID.randomUUID());
        if (caller != null) {
            headers.set(WebhookEventApi.CALLER_SERVICE_HEADER, caller);
        }
        return call(HttpMethod.POST, EVENTS, headers, body);
    }
}
