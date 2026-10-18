package com.paytech.platform.support.delivery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用指定账号发一封固定内容的测试邮件
 * <li>toString 不输出收件邮箱</li>
 */
@Getter
@AllArgsConstructor
public class SendProviderTestEmailRequest {

    /** 账号编码。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String providerCode;

    /** 测试收件邮箱，尚未规范化。 */
    @NotBlank
    @Size(max = 254)
    private final String email;

    /** 不输出收件邮箱。 */
    @Override
    public String toString() {
        return "SendProviderTestEmailRequest(providerCode=" + providerCode + ")";
    }
}
