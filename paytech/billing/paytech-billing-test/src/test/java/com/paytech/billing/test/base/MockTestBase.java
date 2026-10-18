package com.paytech.billing.test.base;

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
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.billing.BillingApplication;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.clear.BillingDataCleaner;
import com.paytech.billing.test.clear.BillingRedisCleaner;
import com.paytech.billing.test.probe.EconomicFactEventProbe;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.ObjectMapper;

/**
 * Billing 服务级入口测试基类
 * <li>复用正式启动类 BillingApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_billing）/Redis/RabbitMQ 需预先就绪。</li>
 * <li>本服务没有外部 HTTP 依赖，不启动 Mock HTTP 服务；EconomicFactRecorded 经补偿任务异步发布到真实本地 Broker，每例为 billing.events
 * 绑定一个临时观察队列（第 08 步前没有订阅方，发布端默认 mandatory 严格判定未路由）。</li>
 * <li>AfterEach 先等待补偿协调器与 Handler 全部空闲，再删除观察队列；Foundation TestBase 随后执行全部 Clear 组件。异步未停止时禁止清理与复用环境。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = BillingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({BillingTestMapperConfiguration.class, BillingDataCleaner.class, BillingRedisCleaner.class,
    EconomicFactEventProbe.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Billing.Test_01";

    /** 前例补偿发布是否已经停止；未停止时禁止清理与复用环境。 */
    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EconomicFactEventProbe events;

    @Autowired
    private BillingDataCleaner dataCleaner;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：只检查空基线并绑定本例观察队列，不承接上一例状态，也不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        dataCleaner.assertEmpty();
        events.bind();
    }

    /** 等待补偿发布结束后删除观察队列；父类随后执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            // 同时观察协调器和真实 Handler；协调器空闲不代表 Handler 已经停止。
            await().atMost(Duration.ofSeconds(15)).until(() -> compensationDispatcher.activeTaskCount() == 0
                && compensationDispatcher.queuedTaskCount() == 0 && compensationHandlers.getActiveCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        } finally {
            // 发布结束后才删除观察队列，避免异步发布因无路由被退回。
            events.unbind();
        }
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台发布回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    /** 发送内部 POST 请求。 */
    protected BillingResponseChecker post(String path, Object body) {
        return new BillingResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected BillingResponseChecker get(String path) {
        return new BillingResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
