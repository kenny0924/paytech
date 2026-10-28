package com.paytech.platform.support.referencedata.client.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

import com.paytech.platform.support.referencedata.client.ReferenceData;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;
import com.paytech.platform.support.referencedata.client.test.base.MockServerLoadBalancerClient;
import com.paytech.platform.support.referencedata.client.test.base.ReferenceDataClientTestBase;

/**
 * 访问目标与调用身份：未配置 base-url 时经宿主 LoadBalancerClient 按 service-id + context-path 访问，没有 LoadBalancerClient 时启动失败；
 * 每个请求带 X-Caller-Service（caller-service 优先，其次 spring.application.name）。
 *
 * @see ReferenceDataRefresher#refreshNow()
 */
class ReferenceDataTargetTest extends ReferenceDataClientTestBase {

    private static final String PREFIX = "platform-support.reference-data.client.";

    /** 服务发现：默认服务名 platform-support-service 经 LoadBalancerClient 解析到实例，请求带调用服务名。 */
    @Test
    void test_normalFlow() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        discoveryRunner(ReferenceDataKind.CURRENCY)
            // Mock 服务没有 Context Path；默认值 /platform-support 的拼接由 ReferenceDataClientPropertiesTest 核对。
            .withPropertyValues(PREFIX + "context-path=")
            .withBean(LoadBalancerClient.class,
                () -> new MockServerLoadBalancerClient("platform-support-service", mockBaseUrl()))
            .run(context -> {
                assertTrue(context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst().succeeded());

                assertEquals(2, context.getBean(ReferenceData.class).currencies().require("USD").minorUnit());
                assertEquals(CALLER_SERVICE, MOCKER.lastCallerService());
                assertEquals(1, MOCKER.callCount(ReferenceDataKind.CURRENCY));
            });
    }

    /** 自定义 service-id 与 caller-service：按配置的服务名解析，caller-service 优先于 spring.application.name。 */
    @Test
    void test_customServiceIdentity() {
        MOCKER.currency(ReferenceDataBuilders.currency("USD"));
        discoveryRunner(ReferenceDataKind.CURRENCY)
            .withPropertyValues(PREFIX + "context-path=", PREFIX + "service-id=platform-support-blue",
                PREFIX + "caller-service=card-service")
            .withBean(LoadBalancerClient.class,
                () -> new MockServerLoadBalancerClient("platform-support-blue", mockBaseUrl()))
            .run(context -> {
                assertTrue(context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst().succeeded());
                assertEquals("card-service", MOCKER.lastCallerService());
            });
    }

    /** 服务名没有实例：该种类本次失败，不请求 Mock 服务。 */
    @Test
    void test_noDataFound() {
        discoveryRunner(ReferenceDataKind.CURRENCY)
            .withBean(LoadBalancerClient.class, () -> new MockServerLoadBalancerClient("other-service", mockBaseUrl()))
            .run(context -> {
                assertFalse(context.getBean(ReferenceDataRefresher.class).refreshNow().getFirst().succeeded());
                assertEquals(0, MOCKER.totalCalls());
            });
    }

    /** 未配置 base-url 且容器中没有 LoadBalancerClient：启动失败并说明需要服务发现或 base-url。 */
    @Test
    void test_invalidParameter() {
        discoveryRunner(ReferenceDataKind.CURRENCY).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            while (failure.getCause() != null) {
                failure = failure.getCause();
            }
            assertTrue(failure.getMessage().contains("LoadBalancerClient"), failure.getMessage());
            assertTrue(failure.getMessage().contains("base-url"), failure.getMessage());
        });
    }
}
