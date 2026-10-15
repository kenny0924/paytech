package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送账号列表项；不含发件地址、区域与凭据。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationProviderResponseBody {

    /** 账号编码。 */
    private String providerCode;

    /** 介质。 */
    private String medium;

    /** 账号状态：ACTIVE、DISABLED。 */
    private String status;

    /**
     * 本环境是否已配置该账号的发送适配器
     * <li>未开启通知发送时为 false。</li>
     */
    private boolean adapterConfigured;

    /** 账号版本。 */
    private int version;

    /** 更新时间，UTC。 */
    private Instant updateTime;

    /** 引用该账号的路由。 */
    private List<ProviderReferenceItem> references;
}
