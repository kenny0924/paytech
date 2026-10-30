package com.paytech.platform.support.api.notification.request;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 提交普通通知的请求；来源系统取自调用方 Header，Body 不提供。 */
public class SubmitNotificationRequestBody {

    /**
     * 请求归属租户 ID
     * <li>可空，平台通知为空；规范正整数字符串。</li>
     * <li>过渡信任下 Platform 不校验租户权限，调用方须保证归属正确。</li>
     */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "tenantId 必须为规范正整数字符串")
    private final String tenantId;

    /**
     * 调用方内的一次通知意图
     * <li>最多 128 字符，无首尾空白与控制字符；同一调用方重复提交返回 409。</li>
     */
    @NotBlank
    @Size(max = 128)
    private final String sourceEventKey;

    /**
     * 普通邮件模板编码
     * <li>须在该调用方的白名单内。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "templateCode 只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private final String templateCode;

    /**
     * 默认语言
     * <li>BCP 47，最多 35 字符；无语言回退。</li>
     */
    @NotBlank
    @Size(max = 35)
    private final String defaultLocale;

    /**
     * 公共模板参数
     * <li>可空，最多 64 项；值非空且不超过 2048 字符。</li>
     * <li>禁止 OTP、认证链接、密码和凭据。</li>
     */
    @Size(max = 64)
    private final Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String, @NotNull @Size(max = 2048) String> defaultParameters;

    /**
     * 显式收件人
     * <li>与 tenantRecipients 二选一；给出时 1—100 人；规范化后重复邮箱整单拒绝；每人一封单收件人邮件。</li>
     */
    @Size(min = 1, max = 100)
    @Valid
    private final List<@NotNull Recipient> recipients;

    /**
     * 按租户权限解析的收件人
     * <li>与 recipients 二选一；Platform 受理前在本库解析租户内具备该权限的成员，直接使用其已验证登录邮箱。</li>
     * <li>解析为 0 人时 200 且不受理（requestId 为空、deliveryCount 为 0），不占用 sourceEventKey；超过 100 人时 400。</li>
     */
    @Valid
    private final TenantRecipients tenantRecipients;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SubmitNotificationRequestBody(@JsonProperty("tenantId") String tenantId,
        @JsonProperty("sourceEventKey") String sourceEventKey, @JsonProperty("templateCode") String templateCode,
        @JsonProperty("defaultLocale") String defaultLocale,
        @JsonProperty("defaultParameters") Map<String, String> defaultParameters,
        @JsonProperty("recipients") List<Recipient> recipients,
        @JsonProperty("tenantRecipients") TenantRecipients tenantRecipients) {
        this.tenantId = tenantId;
        this.sourceEventKey = sourceEventKey;
        this.templateCode = templateCode;
        this.defaultLocale = defaultLocale;
        this.defaultParameters = defaultParameters;
        this.recipients = recipients;
        this.tenantRecipients = tenantRecipients;
    }

    /**
     * 收件人来源校验：recipients 与 tenantRecipients 必须且只能提供一个
     *
     * @return 恰好提供一个时为真；不参与 JSON 序列化
     */
    @JsonIgnore
    @AssertTrue(message = "recipients 与 tenantRecipients 必须且只能提供一个")
    public boolean isRecipientSourceExclusive() {
        return (recipients == null) != (tenantRecipients == null);
    }

    /** 请求归属租户 ID */
    public String getTenantId() {
        return tenantId;
    }

    /** 调用方内的一次通知意图 */
    public String getSourceEventKey() {
        return sourceEventKey;
    }

    /** 普通邮件模板编码 */
    public String getTemplateCode() {
        return templateCode;
    }

    /** 默认语言 */
    public String getDefaultLocale() {
        return defaultLocale;
    }

    /** 公共模板参数 */
    public Map<String, String> getDefaultParameters() {
        return defaultParameters;
    }

    /** 显式收件人 */
    public List<Recipient> getRecipients() {
        return recipients;
    }

    /** 按租户权限解析的收件人 */
    public TenantRecipients getTenantRecipients() {
        return tenantRecipients;
    }

    /** 按租户权限解析收件人的条件；Platform 只发给已验证登录邮箱，不接受邮箱原值。 */
    public static class TenantRecipients {

        /**
         * 租户类型
         * <li>CLIENT 或 PARTNER。</li>
         */
        @NotBlank
        @Pattern(regexp = "CLIENT|PARTNER", message = "tenantType 只能为 CLIENT 或 PARTNER")
        private final String tenantType;

        /**
         * 租户所有者引用
         * <li>Client ID 或 Local Partner ID，规范正整数字符串。</li>
         */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ownerRefId 必须为规范正整数字符串")
        private final String ownerRefId;

        /**
         * 收件人须持有的权限码
         * <li>成员经当前有效角色持有该 ACTIVE 权限才会收到；最多 128 字符。</li>
         */
        @NotBlank
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}", message = "permissionCode 只能由大写字母开头，包含大写字母、数字或下划线")
        private final String permissionCode;

        /**
         * 语言覆盖
         * <li>可空，BCP 47，最多 35 字符；为空时使用 defaultLocale。所有解析出的收件人使用同一语言。</li>
         */
        @Size(max = 35)
        private final String locale;

        /** 按 JSON 属性绑定。 */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public TenantRecipients(@JsonProperty("tenantType") String tenantType,
            @JsonProperty("ownerRefId") String ownerRefId, @JsonProperty("permissionCode") String permissionCode,
            @JsonProperty("locale") String locale) {
            this.tenantType = tenantType;
            this.ownerRefId = ownerRefId;
            this.permissionCode = permissionCode;
            this.locale = locale;
        }

        /** 租户类型 */
        public String getTenantType() {
            return tenantType;
        }

        /** 租户所有者引用 */
        public String getOwnerRefId() {
            return ownerRefId;
        }

        /** 收件人须持有的权限码 */
        public String getPermissionCode() {
            return permissionCode;
        }

        /** 语言覆盖 */
        public String getLocale() {
            return locale;
        }
    }

    /** 单个收件人；不支持 CC/BCC。 */
    public static class Recipient {

        /**
         * 收件邮箱
         * <li>最多 254 字符；服务端规范化后存入 Vault，不进入日志与错误正文。</li>
         */
        @NotBlank
        @Size(max = 254)
        private final String email;

        /**
         * 语言覆盖
         * <li>可空，BCP 47，最多 35 字符。</li>
         */
        @Size(max = 35)
        private final String locale;

        /**
         * 参数覆盖
         * <li>可空，按键覆盖公共参数；约束同 defaultParameters。</li>
         */
        @Size(max = 64)
        private final Map<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String, @NotNull @Size(max = 2048) String> parameters;

        /** 按 JSON 属性绑定。 */
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Recipient(@JsonProperty("email") String email, @JsonProperty("locale") String locale,
            @JsonProperty("parameters") Map<String, String> parameters) {
            this.email = email;
            this.locale = locale;
            this.parameters = parameters;
        }

        /** 收件邮箱 */
        public String getEmail() {
            return email;
        }

        /** 语言覆盖 */
        public String getLocale() {
            return locale;
        }

        /** 参数覆盖 */
        public Map<String, String> getParameters() {
            return parameters;
        }

        /** 不输出邮箱与参数值。 */
        @Override
        public String toString() {
            return "Recipient[redacted]";
        }
    }
}
