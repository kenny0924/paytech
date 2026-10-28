package com.paytech.platform.support.referencedata.client.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.autoconfigure.ReferenceDataClientAutoConfiguration;
import com.paytech.platform.support.referencedata.client.autoconfigure.ReferenceDataClientProperties;
import com.paytech.platform.support.referencedata.client.test.base.ReferenceDataClientTestBase;

/**
 * 配置校验：base-url 配置时格式非法、kinds 缺失 / 为空 / 取值未知、服务名或 Context Path 格式非法、间隔低于下限、超时非正、
 * 缺少调用服务名时启动失败；合法配置按种类集合绑定。
 *
 * @see ReferenceDataClientAutoConfiguration
 * @see ReferenceDataClientProperties
 */
class ReferenceDataClientPropertiesTest extends ReferenceDataClientTestBase {

    private static final String PREFIX = "platform-support.reference-data.client.";

    /** 不合法配置：上下文启动失败，原因指向对应配置键。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"base-url=", "base-url=ftp://platform/platform-support",
        "base-url=http://user@platform/platform-support", "kinds=", "kinds=CURRENCY,FX_RATE",
        "service-id=Platform_Support", "context-path=platform-support", "context-path=/platform-support/",
        "caller-service=Ledger Service",
        "refresh-interval=PT59S", "initial-retry-interval=PT0.5S", "connect-timeout=0s", "read-timeout=-1s"})
    void test_invalidParameter(String override) {
        String key = override.substring(0, override.indexOf('='));
        valid().withPropertyValues(PREFIX + override).run(context -> {
            assertNotNull(context.getStartupFailure(), override);
            assertTrue(rootMessage(context.getStartupFailure()).contains(key)
                || rootMessage(context.getStartupFailure()).contains("FX_RATE"), () -> rootMessage(
                    context.getStartupFailure()));
        });
    }

    /** 未配置 kinds，或既没有 caller-service 也没有 spring.application.name：启动失败。 */
    @Test
    void test_noDataFound() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReferenceDataClientAutoConfiguration.class))
            .run(context -> assertTrue(rootMessage(context.getStartupFailure()).contains("kinds")));
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReferenceDataClientAutoConfiguration.class))
            .withPropertyValues(PREFIX + "base-url=" + mockBaseUrl(), PREFIX + "kinds=CURRENCY")
            .run(context -> assertTrue(rootMessage(context.getStartupFailure()).contains("caller-service"),
                () -> rootMessage(context.getStartupFailure())));
    }

    /** 合法配置：种类集合去重绑定，下限值本身允许。 */
    @Test
    void test_normalFlow() {
        valid().withPropertyValues(PREFIX + "kinds=MCC,CURRENCY,MCC", PREFIX + "refresh-interval=PT1M",
            PREFIX + "initial-retry-interval=PT1S").run(context -> {
                assertEquals(null, context.getStartupFailure());
                ReferenceDataClientProperties properties = context.getBean(ReferenceDataClientProperties.class);
                assertEquals(Set.of(ReferenceDataKind.CURRENCY, ReferenceDataKind.MCC), properties.kinds());
                assertEquals("platform-support-service", properties.serviceId());
                assertEquals("/platform-support", properties.contextPath());
                assertEquals("http://platform-support-service/platform-support", properties.discoveryBaseUrl());
            });
    }

    /** 指向 Mock 服务、关闭调度的合法配置。 */
    private static ApplicationContextRunner valid() {
        return runner(ReferenceDataKind.CURRENCY);
    }

    /** 最底层原因的消息。 */
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
