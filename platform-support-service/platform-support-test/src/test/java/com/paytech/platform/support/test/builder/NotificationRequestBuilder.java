package com.paytech.platform.support.test.builder;

import java.util.List;
import java.util.Map;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;

/** 纯请求 Builder，不准备数据或调用被测业务。 */
public final class NotificationRequestBuilder {

    /** 测试来源系统（调用方服务名格式），与 application-mock.yml 的 HTTP 白名单一致；TestMapper 按前缀清理。 */
    public static final String SOURCE_SYSTEM = "notification-test";

    /** 白名单中的另一测试调用方，验证不同调用方的来源键互不冲突。 */
    public static final String PEER_SOURCE_SYSTEM = "notification-test-peer";

    /** 测试普通邮件模板编码，由 NotificationFixture 创建。 */
    public static final String TEMPLATE_CODE = "NOTIFICATION_TEST";

    /** 返回普通通知合法基线，场景可直接修改字段构造负例。 */
    public static SubmitNotificationRequest template() {
        var request = new SubmitNotificationRequest();
        request.setSourceSystem(SOURCE_SYSTEM);
        request.setSourceEventKey("maintenance-001");
        request.setTemplateCode(TEMPLATE_CODE);
        request.setDefaultLocale("zh-CN");
        request.setDefaultParameters(Map.of("notice", "Maintenance", "name", "Default"));
        request.setRecipients(List.of(recipient("alice@example.com", null, "Alice")));
        return request;
    }

    /** 只构造当前收件人差异，保留 null 用于验证。 */
    public static SubmitNotificationRequest.Recipient recipient(String email, String locale, String name) {
        var recipient = new SubmitNotificationRequest.Recipient();
        recipient.setEmail(email); recipient.setLocale(locale); recipient.setParameters(Map.of("name", name));
        return recipient;
    }

    /** 工具无实例。 */
    private NotificationRequestBuilder() { }
}
