package com.paytech.ledger.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.ledger.LedgerApplication;
import com.paytech.ledger.test.checker.LedgerChecker;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.clear.LedgerDataCleaner;
import com.paytech.ledger.test.clear.LedgerRedisCleaner;
import com.paytech.ledger.test.configuration.LedgerTestMockConfiguration;
import com.paytech.ledger.test.mocker.external.AccountConsistencyMocker;
import com.paytech.ledger.test.mocker.external.BillingProgressMocker;
import com.paytech.ledger.test.mocker.local.LedgerMocker;
import com.paytech.ledger.test.probe.BillingEventPublisher;

import tools.jackson.databind.ObjectMapper;

/**
 * Ledger 服务级入口测试基类
 * <li>复用正式启动类 LedgerApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_ledger）/Redis/RabbitMQ 需预先就绪。</li>
 * <li>外部依赖（第 11b 步）：account consistency 与 billing pending 由独立 Loopback Mock HTTP 服务返回配置的 DTO；EconomicFactRecorded 由
 * {@link BillingEventPublisher} 扮演 billing 发到真实本机 RabbitMQ，经正式消费链路处理。</li>
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 ledger.billing.p0..p3 与 DLQ（其他模块测试的残留消息）。</li>
 * <li>每例开始前检查空基线并清空本服务的 billing 分区队列；结束时等待队列与 Mock 请求静止、检查意外调用，再由 Foundation 统一清理。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = LedgerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({LedgerTestMapperConfiguration.class, LedgerTestMockConfiguration.class, LedgerDataCleaner.class,
    LedgerRedisCleaner.class, LedgerMocker.class, LedgerChecker.class, BillingEventPublisher.class, PartitionQueueBaselineConfiguration.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Ledger.Test_01";

    /** 财务基础资料命令的操作人 Account Header。 */
    protected static final String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /** 财务基础资料命令的操作人会话 Header。 */
    protected static final String SESSION_HEADER = "X-Gateway-Session-Id";

    /** 测试操作人 Account ID。 */
    protected static final String OPERATOR_ACCOUNT_ID = "900000000000000001";

    /** 测试操作人会话 ID。 */
    protected static final String OPERATOR_SESSION_ID = "ledger-test-session";

    /** 等待消费完成的上限。 */
    protected static final Duration CONSUME_WAIT = Duration.ofSeconds(20);

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LedgerMocker ledgerMocker;

    @Autowired
    protected LedgerChecker ledgerChecker;

    @Autowired
    protected BillingEventPublisher billing;

    @Autowired
    protected AccountConsistencyMocker accountConsistency;

    @Autowired
    protected BillingProgressMocker billingProgress;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    private LedgerDataCleaner dataCleaner;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：清空本服务的 billing 分区队列并检查空基线，不承接上一例状态，也不删除任何业务数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        billing.purge();
        dataCleaner.assertEmpty();
    }

    /** 等待分区队列消费完毕与 Mock 请求结束、检查意外调用后清空本服务队列，父类继续执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            await().atMost(CONSUME_WAIT).until(() -> billing.pending() == 0);
            mockHttpServer.awaitIdle();
            assertAll("检查外部 Mock 调用", accountConsistency::assertNoUnexpectedCalls,
                billingProgress::assertNoUnexpectedCalls, mockHttpServer::assertNoUnexpectedRequests);
        } finally {
            billing.purge();
        }
    }

    /** 发送内部 POST 请求。 */
    protected LedgerResponseChecker post(String path, Object body) {
        return new LedgerResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 以 Operations 操作人身份发送财务基础资料命令（BFF 填入的两个操作人 Header）。 */
    protected LedgerResponseChecker postAsOperator(String path, Object body) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set(ACTOR_HEADER, OPERATOR_ACCOUNT_ID);
        headers.set(SESSION_HEADER, OPERATOR_SESSION_ID);
        return new LedgerResponseChecker(http.post(path, headers, body), objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected LedgerResponseChecker get(String path) {
        return new LedgerResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
