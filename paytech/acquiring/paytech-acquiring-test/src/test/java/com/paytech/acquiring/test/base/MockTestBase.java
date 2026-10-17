package com.paytech.commerce.test.base;

import com.paytech.commerce.test.mocker.external.CheckoutCommercialMocker;
import com.paytech.commerce.test.mocker.external.CheckoutRiskMocker;
import com.paytech.commerce.test.mocker.external.PlatformWebhookMocker;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.commerce.TestApplication;
import com.paytech.commerce.test.checker.checkout.CheckoutCreationChecker;
import com.paytech.commerce.test.checkout.AdjustableUtcClock;
import com.paytech.commerce.test.checkout.CctTransactionProbe;
import com.paytech.commerce.test.mocker.local.CheckoutDataMocker;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;

import tools.jackson.databind.ObjectMapper;

/** 新规范基类：每例固定公共状态、清理全部自有数据、DTO 与调用证据。 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class MockTestBase extends TestBase {
    @Autowired
    protected HttpTestInvoker httpTestInvoker;

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AdjustableUtcClock adjustableUtcClock;

    private static boolean asynchronousWorkStopped = true;

    protected static final String REQUEST_ID = "Commerce.CreateCheckout.Test";

    @Autowired
    protected MockHttpServer cctCheckoutMockServer;

    @Autowired
    protected CheckoutDataMocker checkoutDataMocker;

    @Autowired
    protected CheckoutCreationChecker checkoutChecker;

    @Autowired
    protected CctTransactionProbe cctTransactions;

    @Autowired
    protected CheckoutCommercialMocker preparationMocker;

    @Autowired
    protected CheckoutRiskMocker riskMocker;

    @Autowired
    protected PlatformWebhookMocker platformWebhookMocker;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    /** 两级初始化的第一层：公共时间和隔离身份固定，上一例必须已经干净。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        checkoutDataMocker.beforeEach();
        adjustableUtcClock.reset();
        // 支付成功、取消与退款都会登记 Webhook 提交；未单独配置的事件按 Platform 受理处理。
        platformWebhookMocker.acceptByDefault();
    }

    /** 断言失败仍执行全部清理，意外调用必须在清记录前检查。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            cctCheckoutMockServer.awaitIdle();
            // 同时观察协调器和真实 Handler；协调器超时退出也不代表 Handler 已经停止。
            await().atMost(Duration.ofSeconds(10)).until(() -> compensationDispatcher.activeTaskCount() == 0
                && compensationDispatcher.queuedTaskCount() == 0 && compensationHandlers.getActiveCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        }
        assertAll("检查外部 Mock 调用", preparationMocker::assertNoUnexpectedCalls,
            riskMocker::assertNoUnexpectedCalls, platformWebhookMocker::assertNoUnexpectedCalls,
            cctCheckoutMockServer::assertNoUnexpectedRequests);
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台请求回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }
}
