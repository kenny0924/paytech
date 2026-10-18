package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 用指定账号向一个邮箱发送固定内容的测试邮件。 */
@Getter
@Setter
public class TestSendNotificationProviderRequestBody {

    /**
     * 测试收件邮箱
     * <li>纯邮箱地址，按平台邮箱规则规范化；审计只记录摘要。</li>
     */
    @NotBlank
    @Size(max = 254)
    private String email;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 不输出收件邮箱。 */
    @Override
    public String toString() {
        return "TestSendNotificationProviderRequestBody";
    }
}
