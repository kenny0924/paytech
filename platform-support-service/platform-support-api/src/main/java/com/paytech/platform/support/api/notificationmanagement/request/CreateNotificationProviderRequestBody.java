package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 新建发送账号；新账号为停用状态，录入密钥且配置完整后才能启用。 */
@Getter
@Setter
public class CreateNotificationProviderRequestBody {

    /**
     * 账号编码
     * <li>创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String providerCode;

    /**
     * 服务商类型
     * <li>本期只支持 AMAZON_SES；创建后不可修改，介质由类型决定（EMAIL）。</li>
     */
    @NotBlank
    @Pattern(regexp = "AMAZON_SES", message = "providerType 只能为 AMAZON_SES")
    private String providerType;

    /**
     * 服务商区域
     * <li>如 me-central-1。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z]{2}(-[a-z]+)+-[0-9]{1,2}", message = "区域格式如 me-central-1")
    @Size(max = 32)
    private String region;

    /**
     * 发件地址
     * <li>纯邮箱地址，按平台邮箱规则规范化；须为该区域已验证身份，本接口不在线校验。</li>
     */
    @NotBlank
    @Size(max = 254)
    private String fromAddress;

    /**
     * Configuration Set
     * <li>可空，空白视为未配置。</li>
     */
    @Pattern(regexp = "|[A-Za-z0-9_-]{1,64}", message = "Configuration Set 只能包含字母、数字、下划线或连字符，最多 64 字符")
    private String configurationSet;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
