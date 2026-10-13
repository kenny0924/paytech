package com.paytech.platform.support.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.platform.support.PlatformSupportTestApplication;
import com.paytech.platform.support.core.audit.AuditDispatcher;
import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;
import com.paytech.platform.support.test.mocker.external.ChannelAccountVaultMocker;
import com.paytech.platform.support.test.mocker.external.ChannelMocker;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.mocker.external.SmtpMockServer;
import com.paytech.platform.support.test.mocker.local.ApiCredentialMocker;
import com.paytech.platform.support.test.mocker.local.OperationsMocker;
import com.paytech.platform.support.test.mocker.local.PlatformDataCleaner;
import com.paytech.platform.support.test.util.ApiCredentialQueryObserver;

import tools.jackson.databind.ObjectMapper;

/**
 * 服务级 Mock 入口测试基类：复用正式启动类，请求经过真实 Controller → Processor → Service →
 * Repository → Mapper → 本地 MySQL/Redis；外部依赖由独立 Mock HTTP 服务和本地 SMTP 返回配置 DTO。
 *
 * <p>全局准备先检查数据与 Mock 空基线，再重建所有用例共用的 Operations 目录（内部租户、签名公钥、权限与内置角色）。
 * 全局后置先等待异步审计写完、检查意外外部调用，再由 {@link TestBase} 统一执行全部 {@code Clear}。
 */
@ActiveProfiles("mock")
@ContextConfiguration(initializers = LocalMockEnvironmentInitializer.class)
@AutoConfigureRestTestClient
@SpringBootTest(classes = PlatformSupportTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MockTestBase extends TestBase {

    @Autowired
    protected ApiCredentialMocker credentialMocker;

    @Autowired
    protected ApiCredentialQueryObserver credentialQueryObserver;

    @Autowired
    protected PlatformDataCleaner platformDataCleaner;

    @Autowired
    protected OperationsMocker operationsMocker;

    @Autowired
    protected LoginEmailVaultMocker loginEmailVault;

    @Autowired
    protected ChannelMocker channel;

    @Autowired
    protected ChannelAccountVaultMocker channelAccountVault;

    @Autowired
    protected SmtpMockServer smtp;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    protected AuditDispatcher auditDispatcher;

    @Autowired
    protected HttpTestInvoker httpTestInvoker;

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 全局初始化：检查上例清理和本地基础设施，不偷偷删除数据；再重建所有用例共用的目录。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        credentialMocker.assertEmpty();
        credentialQueryObserver.assertEmpty();
        platformDataCleaner.assertEmpty();
        operationsMocker.assertEmpty();
        loginEmailVault.assertEmpty();
        smtp.assertEmpty();
        operationsMocker.mockCatalog();
    }

    /** 异步审计写完后才允许回收；再检查被业务捕获的意外外部调用和未断言邮件。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20))
                .until(() -> auditDispatcher.pendingCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            platformDataCleaner.markUnusable();
            throw failure;
        }
        mockHttpServer.awaitIdle();
        assertAll(loginEmailVault::assertNoUnexpectedCalls, smtp::assertNoUnexpectedMail,
            channel::assertNoUnexpectedCalls, channelAccountVault::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests);
    }
}
