package com.paytech.platform.support.test.builder;

import java.util.List;
import java.util.Map;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;

/** 纯请求 Builder，不准备数据或调用被测业务。 */
public final class NotificationRequestBuilder {

    /** 返回普通通知合法基线，场景可直接修改字段构造负例。 */
    public static SubmitNotificationRequest template() {
        var request = new SubmitNotificationRequest();
        request.setSourceSystem("NOTIFICATION_TEST");
        request.setSourceEventKey("maintenance-001");
        request.setTemplateCode("NOTIFICATION_TEST");
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
