package com.paytech.platform.support.auto.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.configuration.DeliveryConfiguration;
import com.paytech.platform.support.configuration.NotificationCallerProperties;
import com.paytech.platform.support.configuration.NotificationRequestConfiguration;
import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;
import com.paytech.platform.support.ext.rest.notification.NotificationController;

/**
 * 通知内部 HTTP 白名单的启动装配：非法配置拒绝启动，通知关闭时不暴露入口。
 * 只装配被测配置类；人工认证开关打开以跳过通知独立 ID 生成器。
 *
 * @see NotificationCallerProperties#NotificationCallerProperties(java.util.Map)
 */
class NotificationCallerPropertiesTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(DeliveryConfiguration.class, NotificationProperties.class)
        .withPropertyValues("notification.enabled=true", "operations.authentication.enabled=true");

    /** 合法白名单只放行已登记调用方的已登记模板；未配置时全部拒绝。 */
    @Test
    void test_authorize() {
        runner.withPropertyValues("notification.http.callers.payment-orchestration-service[0]=PAYMENT_REFUND_EMAIL",
            "notification.http.callers.payment-orchestration-service[1]=PAYMENT_RECEIPT_EMAIL").run(context -> {
                assertThat(context).hasNotFailed();
                var callers = context.getBean(NotificationCallerProperties.class);
                assertDoesNotThrow(() -> callers.authorize("payment-orchestration-service", "PAYMENT_RECEIPT_EMAIL"));
                assertThrows(BizCheckFailException.class,
                    () -> callers.authorize("payment-orchestration-service", "OTHER_EMAIL"));
                assertThrows(BizCheckFailException.class, () -> callers.authorize("commerce-service", "PAYMENT_RECEIPT_EMAIL"));
                assertThrows(BizCheckFailException.class, () -> callers.authorize(null, "PAYMENT_RECEIPT_EMAIL"));
            });
        runner.run(context -> assertThrows(BizCheckFailException.class,
            () -> context.getBean(NotificationCallerProperties.class).authorize("payment-orchestration-service", "X")));
    }

    /** 非法服务名、模板编码、空列表、重复模板或授权即时发送模板在启动时失败。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSettings")
    void test_invalidConfigurationFailsStartup(String scenario, String[] settings) {
        runner.withPropertyValues(settings).run(context -> assertThat(context).hasFailed().getFailure().rootCause()
            .hasMessageContaining("notification.http.callers"));
    }

    /** 每组只引入一项非法配置。 */
    static Stream<Arguments> invalidSettings() {
        String key = "notification.http.callers.";
        return Stream.of(
            Arguments.of("服务名含大写", new String[] {key + "Payment-Service[0]=PAYMENT_EMAIL"}),
            Arguments.of("模板编码小写", new String[] {key + "payment-service[0]=payment_email"}),
            Arguments.of("模板重复", new String[] {key + "payment-service[0]=PAYMENT_EMAIL",
                key + "payment-service[1]=PAYMENT_EMAIL"}),
            Arguments.of("空模板列表", new String[] {key + "payment-service="}),
            Arguments.of("使用即时发送模板", new String[] {key + "payment-service[0]=CLIENT_EMAIL_VERIFICATION",
                "notification.transient-template-codes[0]=CLIENT_EMAIL_VERIFICATION"}));
    }

    /** 绑定器无法产生的空值（服务名、模板列表、模板编码）在直接构造时同样拒绝；空配置视为全部拒绝。 */
    @Test
    void test_nullEntriesRejected() {
        var nullCaller = new java.util.HashMap<String, java.util.List<String>>();
        nullCaller.put(null, java.util.List.of("PAYMENT_EMAIL"));
        var nullTemplates = new java.util.HashMap<String, java.util.List<String>>();
        nullTemplates.put("payment-service", null);
        var nullTemplate = java.util.Map.of("payment-service", java.util.Arrays.asList((String) null));
        assertThrows(IllegalArgumentException.class, () -> new NotificationCallerProperties(nullCaller));
        assertThrows(IllegalArgumentException.class, () -> new NotificationCallerProperties(nullTemplates));
        assertThrows(IllegalArgumentException.class, () -> new NotificationCallerProperties(nullTemplate));
        assertThrows(BizCheckFailException.class, () -> new NotificationCallerProperties(null).authorize("a", "B"));
    }

    /** 通知关闭时不绑定白名单，也不创建 Controller 与 Body 转换配置。 */
    @Test
    void test_disabledExposesNoEndpoint() {
        new ApplicationContextRunner()
            .withUserConfiguration(DeliveryConfiguration.class, NotificationController.class,
                NotificationRequestConfiguration.class)
            .withPropertyValues("notification.enabled=false").run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(NotificationCallerProperties.class).doesNotHaveBean(NotificationController.class)
                .doesNotHaveBean(NotificationRequestConfiguration.class));
    }
}
