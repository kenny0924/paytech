package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationTemplate {

    /** 模板标识。 */
    private Long templateId;

    /** 表达通知用途的唯一编码。 */
    private String templateCode;

    /** 模板所属媒介。 */
    private NotificationMedium medium;

    /** 模板启停状态。 */
    private NotificationConfigurationStatus status;

    /** 当前配置版本，不保留历史文案。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
