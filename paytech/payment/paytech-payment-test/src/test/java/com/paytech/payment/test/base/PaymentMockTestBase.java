package com.paytech.payment.test.base;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.payment.PaymentApplication;
import com.paytech.payment.test.configuration.PaymentTestConfiguration;
import com.paytech.payment.test.mocker.ChannelConnectorMocker;
import com.paytech.payment.test.mocker.PaymentDataMocker;
import com.paytech.payment.test.mocker.RiskMocker;
import com.paytech.payment.test.mocker.VaultMocker;
import com.paytech.payment.test.probe.PaymentResultProbe;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 共用正式 Payment 应用；每例验证 mor_payment 空基线，等待异步补偿任务结束后由 Foundation 在最后统一清理。
 * <li>MockHttpServer 以服务发现名承载 channel-connector、Risk 与 Vault；channel-connector 的 DTO 由 {@link ChannelConnectorMocker} 配置。</li>
 */
@SpringBootTest(classes = PaymentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@Import({PaymentTestConfiguration.class, PaymentResultProbe.class})
public abstract class PaymentMockTestBase extends TestBase {
    @Autowired
    protected PaymentDataMocker data;

    @Autowired
    protected ChannelConnectorMocker channel;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected com.paytech.payment.test.mocker.VaultTransactionObserver transactions;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    protected RiskMocker risk;

    @Autowired
    protected PaymentResultProbe results;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    /** 前例补偿发布是否已经停止；未停止时禁止清理与复用环境。 */
    private static boolean asynchronousWorkStopped = true;

    /**
     * 全局初始化只核对空基线，不在前置阶段删除数据。
     * <li>为本例绑定两个临时观察队列：PaymentSucceeded 的发布要求 mandatory 路由到至少一个队列，第 08 步前 payment.events 没有订阅方，
     * 每例自带队列使任一收款成功的用例都不依赖共享 Broker 上其他服务的队列。</li>
     */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        data.assertEmpty();
        vault.assertEmpty();
        channel.assertEmpty();
        results.bind();
    }

    /** 所有 HTTP 请求与补偿发布结束并检查意外调用后，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        mockHttpServer.awaitIdle();
        try {
            // 同时观察协调器和真实 Handler；协调器空闲不代表 Handler 已经停止。
            await().atMost(Duration.ofSeconds(15)).until(() -> compensationDispatcher.activeTaskCount() == 0
                && compensationDispatcher.queuedTaskCount() == 0 && compensationHandlers.getActiveCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        }
        try {
            assertAll(vault::assertNoUnexpectedCalls, channel::assertNoUnexpectedCalls, risk::assertNoUnexpectedCalls,
                mockHttpServer::assertNoUnexpectedRequests,
                transactions::assertOutsideTransactions);
        } finally {
            // 补偿发布结束后才删除观察队列，避免异步发布因无路由被退回。
            results.unbind();
        }
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台发布回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }
}
