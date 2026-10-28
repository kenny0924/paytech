package com.paytech.platform.support.referencedata.client.test.base;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.autoconfigure.ReferenceDataClientAutoConfiguration;
import com.paytech.platform.support.referencedata.client.test.MockReferenceDataApi;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataMocker;

/**
 * 参考数据客户端组件测试基类：真实自动配置（ApplicationContextRunner）+ 真实 HTTP 客户端访问独立 MockHttpServer。
 * <li>Mock 服务在类级复用启动成本；Mocker 的配置、调用记录与意外调用每例检查并清理（即使断言失败）。</li>
 * <li>默认关闭自带调度，用例经 {@code ReferenceDataRefresher.refreshNow()} 加载；需要验证调度的用例显式开启。</li>
 */
public abstract class ReferenceDataClientTestBase {

    /** 本类全部用例共用的 Platform 参考数据 Mocker。 */
    protected static final ReferenceDataMocker MOCKER = new ReferenceDataMocker();

    /** 测试宿主的 spring.application.name。 */
    protected static final String CALLER_SERVICE = "ledger-service";

    private static MockHttpServer server;

    /** 启动独立 Mock 服务（只承载 Platform 参考数据合同）。 */
    @BeforeAll
    static void startMockServer() {
        server = new MockHttpServer(new MockReferenceDataApi(MOCKER));
    }

    /** 关闭 Mock 服务。 */
    @AfterAll
    static void stopMockServer() {
        server.close();
    }

    /** 先等待请求结束并检查意外请求，再清理 Mocker 与服务记录；检查失败也会清理。 */
    @AfterEach
    final void tearDownMocks() {
        try {
            server.awaitIdle();
            assertAll(server::assertNoUnexpectedRequests, MOCKER::assertNoUnexpectedCalls);
        } finally {
            MOCKER.clear();
            server.clear();
        }
    }

    /**
     * 只装配参考数据客户端自动配置，base-url 直连 Mock 服务，关闭调度
     *
     * @param kinds 开启的种类
     * @return 上下文 Runner
     */
    protected static ApplicationContextRunner runner(ReferenceDataKind... kinds) {
        return discoveryRunner(kinds)
            .withPropertyValues("platform-support.reference-data.client.base-url=" + server.baseUrl());
    }

    /**
     * 不配置 base-url（经服务发现访问），调用服务名取 spring.application.name=ledger-service，关闭调度
     *
     * @param kinds 开启的种类
     * @return 上下文 Runner
     */
    protected static ApplicationContextRunner discoveryRunner(ReferenceDataKind... kinds) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReferenceDataClientAutoConfiguration.class))
            .withPropertyValues("spring.application.name=" + CALLER_SERVICE,
                "platform-support.reference-data.client.kinds=" + Arrays.stream(kinds).map(ReferenceDataKind::getCode)
                    .collect(Collectors.joining(",")),
                "platform-support.reference-data.client.scheduling-enabled=false");
    }

    /** Mock 服务地址。 */
    protected static String mockBaseUrl() {
        return server.baseUrl();
    }

    /** 当前健康状态（健康指标 Bean 名 referenceData）。 */
    protected static Health health(ApplicationContext context) {
        return context.getBean("referenceData", HealthIndicator.class).health();
    }
}
