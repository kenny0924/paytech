package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationProvider {

    /** 不可复用的具体发送账号身份。 */
    private Long providerId;

    /** 与出站适配器绑定的账号编码。 */
    private String providerCode;

    /** 支持的发送媒介。 */
    private NotificationMedium medium;

    /** 停用阻止新发送，不阻止历史查询。 */
    private NotificationConfigurationStatus status;

    /** 当前配置的并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
