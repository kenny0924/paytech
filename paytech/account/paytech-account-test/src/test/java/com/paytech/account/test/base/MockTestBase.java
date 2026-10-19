package com.paytech.account.test.base;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.account.AccountApplication;
import com.paytech.account.test.checker.AccountResponseChecker;
import com.paytech.account.test.clear.AccountDataCleaner;
import com.paytech.account.test.configuration.AccountTestConfiguration;
import com.paytech.account.test.dal.AccountTestMapper;
import com.paytech.account.test.mocker.local.OwnerAccountMocker;
import com.paytech.account.test.probe.AccountPostedProbe;
import com.paytech.account.test.probe.BillingEventPublisher;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * Account 服务级入口测试基类
 * <li>复用正式启动类 AccountApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_account）与 RabbitMQ 需预先就绪。</li>
 * <li>account 没有出站 HTTP 依赖：EconomicFactRecorded 由 {@link BillingEventPublisher} 扮演 billing 发到真实本机 RabbitMQ，经正式消费链路处理；
 * AccountPosted 由 {@link AccountPostedProbe} 以每例临时队列观察 account.events。</li>
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 account.billing.p0..p3 与 DLQ（其他模块测试的残留消息）。</li>
 * <li>每例开始前清空本服务的 billing 分区队列、检查空基线并绑定观察队列；结束时等待消费与 AccountPosted 发布任务完成，再由 Foundation 统一清理。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = AccountApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AccountTestConfiguration.class, AccountDataCleaner.class, BillingEventPublisher.class, AccountPostedProbe.class,
    OwnerAccountMocker.class, PartitionQueueBaselineConfiguration.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Account.Test_01";

    /** 等待消费与发布完成的上限。 */
    protected static final Duration CONSUME_WAIT = Duration.ofSeconds(20);

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AccountTestMapper testMapper;

    @Autowired
    protected BillingEventPublisher billing;

    @Autowired
    protected AccountPostedProbe posted;

    @Autowired
    protected OwnerAccountMocker accounts;

    @Autowired
    private AccountDataCleaner dataCleaner;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：清空本服务的 billing 分区队列、检查空基线并绑定 account.events 观察队列，不删除任何业务数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        billing.purge();
        dataCleaner.assertEmpty();
        posted.bind();
    }

    /** 等待分区队列消费完毕与 AccountPosted 发布任务全部完成后解绑观察队列；未完成时标记环境不可复用。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            await().atMost(CONSUME_WAIT).until(() -> billing.pending() == 0);
            await().atMost(CONSUME_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        } catch (RuntimeException | AssertionError failure) {
            dataCleaner.markUnusable();
            throw new AssertionError("异步工作未结束: " + testMapper.selectCompensationEvents(), failure);
        } finally {
            posted.unbind();
            billing.purge();
        }
    }

    /** 后台仍可能写入时拒绝清理。 */
    @Override
    protected void checkClearAllowed() {
        dataCleaner.assertReusable();
    }

    /** 扮演 billing 发布事实并等待 account 消费提交。 */
    protected String consumed(EconomicFactPayload fact) {
        String eventId = billing.publish(fact);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return eventId;
    }

    /** 发送内部 POST 请求。 */
    protected AccountResponseChecker post(String path, Object body) {
        return new AccountResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected AccountResponseChecker get(String path) {
        return new AccountResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
