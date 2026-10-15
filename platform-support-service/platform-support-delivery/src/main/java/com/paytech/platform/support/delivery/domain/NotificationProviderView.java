package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发送账号列表项，组合账号、路由与模板三表；不含密钥 Token。 */
@Getter
@AllArgsConstructor
public class NotificationProviderView {

    /** 账号编码。 */
    private final String providerCode;

    /** 介质。 */
    private final NotificationMedium medium;

    /** 服务商类型。 */
    private final String providerType;

    /** 账号启停状态。 */
    private final NotificationConfigurationStatus status;

    /** 服务商区域，未填写为空。 */
    private final String region;

    /** 发件地址，未填写为空。 */
    private final String fromAddress;

    /** Configuration Set，可空。 */
    private final String configurationSet;

    /** 是否已录入密钥。 */
    private final boolean credentialConfigured;

    /** 最近一次录入或更换密钥的时间，未录入为空。 */
    private final Instant credentialUpdateTime;

    /** 缺失的发送配置字段名，空列表表示配置完整。 */
    private final List<String> missingConfiguration;

    /** 本环境是否注册了该类型的唯一发送适配器；未开启发送时为 false。 */
    private final boolean adapterConfigured;

    /** 账号版本。 */
    private final int version;

    /** 账号更新时间。 */
    private final Instant updateTime;

    /** 引用该账号的路由，按路由创建顺序。 */
    private final List<NotificationProviderReference> references;
}
