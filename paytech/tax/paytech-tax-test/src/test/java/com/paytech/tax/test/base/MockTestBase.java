package com.paytech.tax.test.base;

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
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.tax.TaxApplication;
import com.paytech.tax.test.checker.TaxResponseChecker;
import com.paytech.tax.test.clear.TaxDataCleaner;
import com.paytech.tax.test.configuration.TaxTestMapperConfiguration;
import com.paytech.tax.test.configuration.TaxTestMockConfiguration;
import com.paytech.tax.test.dal.TaxTestMapper;
import com.paytech.tax.test.mocker.external.PlatformFileMocker;
import com.paytech.tax.test.mocker.local.TaxSubjectMocker;
import com.paytech.tax.test.probe.AcquiringEventPublisher;

import tools.jackson.databind.ObjectMapper;

/**
 * Tax 服务级入口测试基类
 * <li>复用正式启动类 TaxApplication 与生产组件扫描，只附加 mock Profile 与测试组件；本地 MySQL（mor_tax，含初始化 DML）与 RabbitMQ
 * 需预先就绪。</li>
 * <li>外部依赖只有 Platform 内部文件接口与对象存储直传地址，由独立 Loopback Mock HTTP 服务承载；全局不预设接受上传。</li>
 * <li>OrderPaymentSucceeded 由 {@link AcquiringEventPublisher} 扮演 acquiring 发布到真实 acquiring.events，经正式消费链路处理；
 * {@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 tax.acquiring.p0..p3 与 DLQ，每例前后再清空。</li>
 * <li>AfterEach 先等待 tax.acquiring 分区队列消费完毕、补偿协调器与 Handler（开票）全部空闲，再检查外部 Mock 没有意外调用；Foundation
 * TestBase 随后执行全部 Clear 组件。异步未停止时禁止清理与复用环境。</li>
 * <li>Actuator 在独立管理端口（业务端口 + 1000，测试随机），由 {@link #management(String)} 访问。</li>
 */
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@SpringBootTest(classes = TaxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TaxTestMapperConfiguration.class, TaxTestMockConfiguration.class, TaxDataCleaner.class,
    AcquiringEventPublisher.class, TaxSubjectMocker.class, PartitionQueueBaselineConfiguration.class})
public abstract class MockTestBase extends TestBase {
    /** 所有测试请求使用的 Request ID。 */
    protected static final String REQUEST_ID = "Tax.Test_01";

    /** 异步等待上限。 */
    protected static final Duration ASYNC_WAIT = Duration.ofSeconds(20);

    /** 前例异步处理是否已经停止；未停止时禁止清理与复用环境。 */
    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected HttpTestInvoker http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TaxTestMapper testMapper;

    @Autowired
    protected TaxSubjectMocker subjects;

    @Autowired
    protected PlatformFileMocker files;

    @Autowired
    protected AcquiringEventPublisher acquiringEvents;

    @Autowired
    protected MockHttpServer mockHttpServer;

    @Autowired
    private TaxDataCleaner dataCleaner;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    @LocalManagementPort
    private int managementPort;

    /** 全局初始化：检查空基线与外部 Mock 空基线，清空本服务分区队列；不承接上一例状态，也不删除任何数据。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        acquiringEvents.purge();
        dataCleaner.assertEmpty();
        files.assertEmpty();
    }

    /** 等待消费与开票补偿结束，检查外部 Mock 意外调用；父类随后执行全部 Clear。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            await().atMost(ASYNC_WAIT).until(() -> acquiringEvents.pending() == 0);
            await().atMost(ASYNC_WAIT).until(() -> compensationDispatcher.activeTaskCount() == 0
                && compensationDispatcher.queuedTaskCount() == 0 && compensationHandlers.getActiveCount() == 0);
        } catch (RuntimeException | AssertionError failure) {
            asynchronousWorkStopped = false;
            throw failure;
        } finally {
            acquiringEvents.purge();
        }
        mockHttpServer.awaitIdle();
        assertAll("检查外部 Mock 调用", files::assertNoUnexpectedCalls, mockHttpServer::assertNoUnexpectedRequests);
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    /** 发送内部 POST 请求。 */
    protected TaxResponseChecker post(String path, Object body) {
        return new TaxResponseChecker(http.post(path, TestRequestHeaders.internal(REQUEST_ID), body), objectMapper,
            REQUEST_ID);
    }

    /** 发送内部 GET 请求。 */
    protected TaxResponseChecker get(String path) {
        return new TaxResponseChecker(http.get(path, TestRequestHeaders.internal(REQUEST_ID)), objectMapper,
            REQUEST_ID);
    }

    /** 发送管理端口 GET 请求（Actuator）。 */
    protected EntityExchangeResult<byte[]> management(String path) {
        return RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build().get().uri(path)
            .exchange().expectBody().returnResult();
    }
}
