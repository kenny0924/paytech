package com.paytech.platform.support.delivery.service.processor.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 模块内普通邮件提交；调用方必须先校验业务权限，来源和租户不得直接照搬外部输入。 */
@Getter
@Setter
public class SubmitNotificationRequest {

    /**
     * 来源系统：调用方服务名，例如 payment-orchestration-service
     * <li>模块内调用方自行确定；HTTP 入口取已通过白名单的 X-Caller-Service，不取自 Body</li>
     */
    @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String sourceSystem;

    /** 调用方已授权的请求归属；平台通知为空。 */
    @Positive
    private Long tenantId;

    /** 来源系统内的一次通知意图，重复提交返回冲突，不重放旧响应。 */
    @NotBlank @Size(max = 128)
    private String sourceEventKey;

    /** 普通邮件模板编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String templateCode;

    /** BCP 47 默认语言，无语言回退。 */
    @NotBlank @Size(max = 35)
    private String defaultLocale;

    /** 可持久化的普通字符串参数；禁止 OTP、认证链接、密码和凭据。 */
    @Size(max = 64)
    private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String,
        @NotNull @Size(max = 2048) String> defaultParameters;

    /** 固定收件人集合，首期最多一百人；规范化后重复邮箱整体拒绝。 */
    @NotEmpty @Size(max = 100) @Valid
    private List<@NotNull Recipient> recipients;

    /** 每条记录只产生一封单收件人邮件，不支持 CC/BCC。 */
    @Getter
    @Setter
    public static class Recipient {

        /** 收件邮箱；SMS 不属于本期模块能力。 */
        @NotBlank @Size(max = 254)
        private String email;

        /** 可空语言覆盖。 */
        @Size(max = 35)
        private String locale;

        /** 按键覆盖默认值；空对象不覆盖，键级 null 不允许。 */
        @Size(max = 64)
        private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String,
            @NotNull @Size(max = 2048) String> parameters;
    }
}
