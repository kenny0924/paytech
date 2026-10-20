package com.paytech.counterparty.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

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

import com.paytech.counterparty.CounterpartyTestApplication;
import com.paytech.counterparty.test.mocker.external.ClientMasterStoredCardSettingMocker;
import com.paytech.counterparty.test.mocker.local.CustomerDataMocker;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

import tools.jackson.databind.ObjectMapper;

/** counterparty 测试基类：每例检查上一例已清理，结束时等待补偿处理停止、检查意外调用后清理全部自有数据与 DTO。 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = CounterpartyTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class CounterpartyMockTestBase extends TestBase {
    protected static final String REQUEST_ID = "Counterparty.Customer.Test";

    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected HttpTestInvoker httpTestInvoker;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MockHttpServer counterpartyMockServer;

    @Autowired
    protected CustomerDataMocker customerDataMocker;

    @Autowired
    protected ClientMasterStoredCardSettingMocker storedCardSettings;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    /** 两级初始化的第一层：隔离身份固定，上一例必须已经干净。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        customerDataMocker.beforeEach();
    }

    /** 断言失败仍执行全部清理，意外调用必须在清记录前检查。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            counterpartyMockServer.awaitIdle();
            await().atMost(Duration.ofSeconds(10)).until(() -> compensationDispatcher.activeTaskCount() == 0
                && compensationDispatcher.queuedTaskCount() == 0 && compensationHandlers.getActiveCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        }
        assertAll("检查外部 Mock 调用", storedCardSettings::assertNoUnexpectedCalls,
            counterpartyMockServer::assertNoUnexpectedRequests);
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台请求回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }
}
