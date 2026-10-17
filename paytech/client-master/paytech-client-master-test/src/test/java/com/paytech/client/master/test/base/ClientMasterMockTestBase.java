package com.paytech.client.master.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.client.master.ClientMasterTestApplication;
import com.paytech.client.master.test.dal.onboarding.OnboardingTestMapper;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker;
import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker;
import com.paytech.client.master.test.mocker.commercial.CommercialAgreementPartyMocker;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

/** client-master Mock 测试基类：真实生产链、本机 MySQL/Redis/RabbitMQ，Platform/Risk/commercial 走测试 HTTP 服务。 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = ClientMasterTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ClientMasterMockTestBase extends TestBase {
    /** 默认 CLIENT 账号。 */
    protected static final long ACCOUNT_ID = 9_300_000_001L;
    /** 默认 CLIENT 会话。 */
    protected static final long SESSION_ID = 9_300_000_002L;

    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected RestTestClient http;

    @Autowired
    protected MockHttpServer ownerServer;

    @Autowired
    protected OnboardingOwnerMocker owners;

    @Autowired
    protected OnboardingApplicationMocker applications;

    @Autowired
    protected OnboardingTestMapper mapper;

    @Autowired
    protected CommercialAgreementPartyMocker agreementParties;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    /** 两级初始化的第一层：上一例必须已清理，当前账号会话有效。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        applications.assertEmpty();
        owners.mockSession(ACCOUNT_ID, SESSION_ID);
        // commercial 合同参与方引用的公共基线：可用且无引用；人员共享场景在用例内覆盖。
        agreementParties.mockNoReferences();
    }

    /** 先等待补偿任务和 Mock 请求结束，再检查意外调用；清理由 TestBase 执行。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            ownerServer.awaitIdle();
            waitUntil(() -> compensationDispatcher.activeTaskCount() == 0 && compensationDispatcher.queuedTaskCount() == 0
                && compensationHandlers.getActiveCount() == 0, Duration.ofSeconds(10));
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        }
        assertAll("检查外部 Mock 调用", ownerServer::assertNoUnexpectedRequests);
    }

    /** 异步无法停止时保留现场。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    /** 有上限的条件等待。 */
    protected static void waitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("等待条件超时");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待被中断", interrupted);
            }
        }
    }
}
