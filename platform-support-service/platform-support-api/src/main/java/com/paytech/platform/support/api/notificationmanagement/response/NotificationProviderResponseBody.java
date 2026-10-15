package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送账号列表项；不含密钥及其 Vault Token。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationProviderResponseBody {

    /** 账号编码。 */
    private String providerCode;

    /** 介质。 */
    private String medium;

    /** 服务商类型，如 AMAZON_SES。 */
    private String providerType;

    /** 账号状态：ACTIVE、DISABLED。 */
    private String status;

    /** 服务商区域；未填写为 null。 */
    private String region;

    /** 发件地址；未填写为 null。 */
    private String fromAddress;

    /** Configuration Set；未配置为 null。 */
    private String configurationSet;

    /** 是否已录入密钥。 */
    private boolean credentialConfigured;

    /** 最近一次录入或更换密钥的时间，UTC；未录入为 null。 */
    private Instant credentialUpdateTime;

    /**
     * 缺失的发送配置
     * <li>取值 region、fromAddress、credential；空数组表示配置完整，只有配置完整的账号可以启用。</li>
     */
    private List<String> missingConfiguration;

    /**
     * 本环境是否注册了该类型的发送适配器
     * <li>未开启通知发送或未启用该服务商时为 false。</li>
     */
    private boolean adapterConfigured;

    /** 账号版本。 */
    private int version;

    /** 更新时间，UTC。 */
    private Instant updateTime;

    /** 引用该账号的路由。 */
    private List<ProviderReferenceItem> references;
}
