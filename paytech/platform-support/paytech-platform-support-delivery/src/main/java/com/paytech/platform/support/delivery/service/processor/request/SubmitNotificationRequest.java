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
     * 来源系统：调用方服务名，例如 payment-service
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

        /** 不输出邮箱、Token 与参数值。 */
        @Override
        public String toString() {
            return "Recipient[redacted]";
        }

        /**
         * 收件邮箱；SMS 不属于本期模块能力
         * <li>与 addressVaultToken 二选一：给出邮箱时受理前经 Vault Protect 为 EMAIL_ADDRESS_V1 稳定 Token。</li>
         */
        @Size(max = 254)
        private String email;

        /**
         * 已保护的收件邮箱 Token
         * <li>与 email 二选一；必须是 EMAIL_ADDRESS_V1 稳定 Token（例如账号登录邮箱 Token），受理时直接保存、不再 Protect。</li>
         * <li>只由模块内已解析收件人的入口提供，HTTP 调用方不能直接提交；禁止日志输出。</li>
         */
        @Size(max = 255)
        private String addressVaultToken;

        /** 可空语言覆盖。 */
        @Size(max = 35)
        private String locale;

        /** 按键覆盖默认值；空对象不覆盖，键级 null 不允许。 */
        @Size(max = 64)
        private Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String,
            @NotNull @Size(max = 2048) String> parameters;
    }
}
