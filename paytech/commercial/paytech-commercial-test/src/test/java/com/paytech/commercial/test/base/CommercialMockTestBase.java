package com.paytech.commercial.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.commercial.CommercialTestApplication;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.test.mocker.clientmaster.ClientMasterCandidateMocker;
import com.paytech.commercial.test.mocker.compliance.QualificationEvaluationMocker;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutResolutionFixture;
import com.paytech.commercial.test.probe.CommercialEventProbe;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.api.TestBase;

/**
 * commercial Mock 测试基类：正式应用、真实本地 MySQL（mor_commercial）/ Redis / RabbitMQ，client-master 走测试 HTTP 服务。
 * <li>compliance 资格判定同样走测试 HTTP 服务（{@link QualificationEvaluationMocker}），由用例按主体配置实时状态。</li>
 * <li>commercial 只发布不消费，不装配分区队列基线；两个业务事件经补偿任务异步发布到真实本地 Broker，每例为 commercial.events 绑定一个
 * 临时观察队列（测试发布端 unroutable-policy 为默认 FAIL，未路由严格判定）。</li>
 * <li>AfterEach 先等待补偿协调器与 Handler 全部空闲，再删除观察队列并检查意外调用；Foundation TestBase 随后执行全部 Clear（含
 * t_compensation_event）。异步未停止时禁止清理与复用环境。</li>
 */
@ActiveProfiles("mock")
@SpringBootTest(classes = CommercialTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({CommercialMockTestBase.TimeConfiguration.class, CommercialEventProbe.class})
public abstract class CommercialMockTestBase extends TestBase {
    /** 前例补偿发布是否已经停止；未停止时禁止清理与复用环境。 */
    private static boolean asynchronousWorkStopped = true;

    @Autowired
    protected CheckoutResolutionFixture fixture;

    @Autowired
    protected ClientMasterCandidateMocker clientMaster;

    @Autowired
    protected QualificationEvaluationMocker compliance;

    @Autowired
    protected MockHttpServer ownerServer;

    @Autowired
    protected CommercialEventProbe events;

    @Autowired
    private CompensationEventDispatcher compensationDispatcher;

    @Autowired
    @Qualifier("compensationHandlerExecutor")
    private ThreadPoolExecutor compensationHandlers;

    /** 两级初始化的公共基线检查并绑定本例事件观察队列，具体业务数据由子类 BeforeEach 准备。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        Assertions.assertTrue(asynchronousWorkStopped, "前例异步处理未结束，禁止复用环境");
        fixture.assertEmpty();
        events.bind();
    }

    /** 先等待补偿发布结束再删除观察队列，然后等待 Mock 请求结束并检查意外调用；清理由 TestBase 执行。 */
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
        ownerServer.awaitIdle();
        assertAll("检查外部 Mock 调用", ownerServer::assertNoUnexpectedRequests);
    }

    /** 异步无法停止时保留现场，防止公共清理之后仍被后台发布回写。 */
    @Override
    protected void checkClearAllowed() {
        Assertions.assertTrue(asynchronousWorkStopped, "异步处理未结束，禁止清理环境");
    }

    /** 为内部入口用例构造同一固定商业时点，不读取或改变数据库；不带支付方式。 */
    protected CheckoutQuery query() {
        return query(null);
    }

    /** 同一固定商业时点、USD 与 AE 消费者，带指定支付方式。 */
    protected CheckoutQuery query(String paymentMethodCode) {
        return CheckoutQuery.resolve(Long.toString(CheckoutFixture.CLIENT_ID), "ORDER-1", "AE", "USD", paymentMethodCode,
            "en-AE", CheckoutFixture.NOW.toString(), CheckoutFixture.NOW);
    }

    /** 固定测试时点并约束测试 HTTP 客户端，不替换生产业务链路。 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class TimeConfiguration {
        /** 每次断言只发出一次 HTTP 请求，禁止 503 自动重试和重定向。 */
        @Bean
        ClientHttpRequestFactory resolutionHttpRequestFactory() {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(Duration.ofSeconds(5));
            return factory;
        }

        /** 在每例无可变时钟状态的前提下使用同一固定业务时点。 */
        @Bean
        @Primary
        Clock resolutionTestClock() {
            return Clock.fixed(CheckoutFixture.NOW, ZoneOffset.UTC);
        }
    }
}
