package com.paytech.platform.support.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.paytech.platform.support.test.notification.*;
import com.paytech.platform.support.test.dal.NotificationTestMapper;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/** 复用正式服务测试应用，只补通知类的 Fixture 与异步检查。 */
public abstract class NotificationTestBase extends MockTestBase {

    @Autowired protected NotificationFixture fixture;
    @Autowired protected NotificationVaultMock vault;
    @Autowired protected MockEmailProvider provider;
    @Autowired protected NotificationTestMapper mapper;
    @Autowired protected MockHttpServer mockServer;

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
            mockServer::assertNoUnexpectedRequests);
    }
}
