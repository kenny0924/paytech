package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 用指定账号发送测试邮件；toString 不输出收件邮箱。 */
@Getter
@AllArgsConstructor
public class TestSendNotificationProviderRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 账号编码。 */
    private final String providerCode;

    /** 测试收件邮箱。 */
    private final String email;

    /** 操作原因。 */
    private final String reason;

    /** 不输出收件邮箱。 */
    @Override
    public String toString() {
        return "TestSendNotificationProviderRequest(providerCode=" + providerCode + ")";
    }
}
