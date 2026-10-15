package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import java.util.Set;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.platform.support.delivery.ext.eventhandler.NotificationDeliveryHandler;
import com.paytech.platform.support.test.notification.*;
import com.paytech.platform.support.test.dal.NotificationTestMapper;
import com.paytech.platform.support.test.mocker.external.SesMocker;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/** 复用正式服务测试应用，只补通知类的 Fixture 与异步检查。 */
public abstract class NotificationTestBase extends MockTestBase {

    @Autowired protected NotificationFixture fixture;
    @Autowired protected NotificationVaultMock vault;
    @Autowired protected MockEmailProvider provider;
    @Autowired protected NotificationTestMapper mapper;
    @Autowired protected MockHttpServer mockServer;
    @Autowired protected SesMocker ses;
    @Autowired protected CompensationEventScanner scanner;
    @Autowired protected NotificationDeliveryHandler handler;

    /** 父类全局初始化后创建当前通知类公共配置。 */
    @BeforeEach
    void setUpNotificationMocks() {
        fixture.prepare();
        vault.allow("alice@example.com", "vlt_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        vault.allow("bob@example.com", "vlt_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }

    /** 异步超时后不清理仍可能被后台访问的资源。 */
    @Override
    protected void checkClearAllowed() { fixture.assertReusable(); }

    /** 等待真实异步与 HTTP 完成，并检查被业务捕获的意外 Mock 调用。 */
    @AfterEach
    void checkNotificationCalls() {
        fixture.awaitIdle();
        mockServer.awaitIdle();
        org.junit.jupiter.api.Assertions.assertAll(provider::assertExpected, vault::assertExpected,
            ses::assertNoUnexpectedCalls, mockServer::assertNoUnexpectedRequests);
    }

    /** 只改变本例任务到期时间，后续 Claim、Handler 与完成回写全部走生产实现。 */
    protected void scanDue() {
        var previous = mapper.events();
        org.junit.jupiter.api.Assertions.assertEquals(1, previous.size());
        int executeCount = ((Number) previous.getFirst().get("execute_count")).intValue();
        mapper.due();
        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(handler.eventType())));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var current = mapper.events();
            return current.isEmpty() || ((Number) current.getFirst().get("execute_count")).intValue() > executeCount;
        });
        fixture.awaitIdle();
    }
}
