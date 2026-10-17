package com.paytech.platform.support.delivery.service.processor.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 模块内即时邮件发送：渲染后立即发送一次，不落库、不排队、不重试
 * <li>用于认证链接等不允许持久化的临时内容；调用方负责秘密的生成、失效与消费</li>
 * <li>toString 只输出模板与语言，不输出邮箱和参数</li>
 */
@Getter
@Setter
public class SendTransientEmailRequest {

    /** 即时发送模板编码，须在 notification.transient-template-codes 中。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String templateCode;

    /** 唯一收件邮箱。 */
    @NotBlank @Size(max = 254)
    private String email;

    /** BCP 47 语言，无语言回退。 */
    @NotBlank @Size(max = 35)
    private String locale;

    /** 只在本次渲染中使用的临时参数，如认证链接。 */
    @NotEmpty @Size(max = 8)
    private Map<String, String> parameters;

    /** 不输出邮箱和参数。 */
    @Override
    public String toString() {
        return "SendTransientEmailRequest(templateCode=" + templateCode + ", locale=" + locale + ")";
    }
}
