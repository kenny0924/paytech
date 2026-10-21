package com.paytech.reconciliation.test.base;

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

import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.reconciliation.ReconciliationApplication;
import com.paytech.reconciliation.api.ReconciliationHeaders;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.clear.ReconciliationDataCleaner;
import com.paytech.reconciliation.test.configuration.ReconciliationTestConfiguration;
import com.paytech.reconciliation.test.dal.ReconciliationTestMapper;
import com.paytech.reconciliation.test.mocker.external.ChannelConnectorMocker;
import com.paytech.reconciliation.test.mocker.external.VaultMocker;
import com.paytech.reconciliation.test.mocker.local.ReconciliationDataMocker;
import com.paytech.reconciliation.test.probe.BillingEventPublisher;
import com.paytech.reconciliation.test.probe.ReconciliationEventProbe;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.ObjectMapper;

/**
 * Reconciliation 服务级入口测试基类
 * <li>复用正式启动类 ReconciliationApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_reconciliation）、Redis
 * （前缀 reconciliation-mock）与 RabbitMQ 需预先就绪，缺表时 BeforeEach 明确失败并提示按测试 init/ddl.sql 手工建表。</li>
 * <li>出站 HTTP：channel-connector ChannelMidApi 与 Vault Protect 经真实客户端打到独立 Mock HTTP 服务，由 {@link ChannelConnectorMocker}、
 * {@link VaultMocker} 按用例配置 DTO；未配置的调用在 AfterEach 使测试失败。</li>
 * <li>MQ：BillingFactRecorded 由 {@link BillingEventPublisher} 扮演 billing 发到 billing.events，经正式消费链路处理；四类对账事件由
 * {@link ReconciliationEventProbe} 以每例临时队列观察 reconciliation.events。{@link PartitionQueueBaselineConfiguration} 在监听容器启动前
 * 清空 reconciliation.billing.p0..p3 与 DLQ。</li>
 * <li>每例开始前清空 billing 分区队列、检查库与 Mock 空基线并绑定观察队列；结束时等待消费与补偿任务完成、检查意外调用，再由 Foundation
 * TestBase 执行全部 Clear（库、Redis 命名空间、Mock）。异步工作未在上限内结束时环境标记为不可复用。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = ReconciliationApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ReconciliationTestConfiguration.class, PartitionQueueBaselineConfiguration.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Reconciliation.Test_01";

    /** 等待消费与补偿任务完成的上限。 */
    protected static final Duration ASYNC_WAIT = Duration.ofSeconds(20);

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ReconciliationTestMapper testMapper;

    @Autowired
    protected ReconciliationDataMocker data;

    @Autowired
    protected ChannelConnectorMocker channelConnector;

    @Autowired
    protected VaultMocker vault;

    @Autowired
    protected BillingEventPublisher billing;

    @Autowired
    protected ReconciliationEventProbe events;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    private ReconciliationDataCleaner dataCleaner;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：清空本服务的 billing 分区队列、检查库与 Mock 空基线并绑定 reconciliation.events 观察队列，不删除任何业务数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        billing.purge();
        dataCleaner.assertEmpty();
        channelConnector.assertEmpty();
        vault.assertEmpty();
        events.bind();
    }

    /** 等待 Mock HTTP 请求、分区队列消费与补偿任务全部结束并检查意外调用；异步未结束时标记环境不可复用。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            mockHttpServer.awaitIdle();
            await().atMost(ASYNC_WAIT).until(() -> billing.pending() == 0);
            await().atMost(ASYNC_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        } catch (RuntimeException | AssertionError failure) {
            dataCleaner.markUnusable();
            throw new AssertionError("异步工作未结束: " + testMapper.selectCompensationEvents(), failure);
        } finally {
            events.unbind();
            billing.purge();
        }
        assertAll(channelConnector::assertNoUnexpectedCalls, vault::assertNoUnexpectedCalls,
            mockHttpServer::assertNoUnexpectedRequests);
    }

    /** 后台仍可能写入时拒绝清理。 */
    @Override
    protected void checkClearAllowed() {
        dataCleaner.assertReusable();
    }

    /** 扮演 billing 发布事实并等待 reconciliation 消费提交（登记 t_consumed_event）。 */
    protected String consumed(BillingFactPayload fact) {
        String eventId = billing.publish(fact);
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return eventId;
    }

    /** 运营写命令的请求头：内部头 + Gateway 写入的操作人与会话。 */
    protected static HttpHeaders operator(long actorId) {
        HttpHeaders headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set(ReconciliationHeaders.ACTOR_HEADER, String.valueOf(actorId));
        headers.set(ReconciliationHeaders.SESSION_HEADER, ReconciliationTestConstants.SESSION_ID);
        return headers;
    }

    /** 以提交人身份发送运营 POST 命令。 */
    protected ReconciliationResponseChecker postAsMaker(String path, Object body) {
        return post(path, operator(ReconciliationTestConstants.MAKER_ID), body);
    }

    /** 以复核人身份发送运营 POST 命令。 */
    protected ReconciliationResponseChecker postAsChecker(String path, Object body) {
        return post(path, operator(ReconciliationTestConstants.CHECKER_ID), body);
    }

    /** 发送带指定请求头的 POST 请求。 */
    protected ReconciliationResponseChecker post(String path, HttpHeaders headers, Object body) {
        return new ReconciliationResponseChecker(http.post(path, headers, body), objectMapper, REQUEST_ID);
    }

    /** 发送内部 POST 请求（服务间接口，不带操作人头）。 */
    protected ReconciliationResponseChecker post(String path, Object body) {
        return post(path, TestRequestHeaders.internal(REQUEST_ID), body);
    }

    /** 发送内部 GET 请求。 */
    protected ReconciliationResponseChecker get(String path) {
        return new ReconciliationResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
