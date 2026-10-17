package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;

import lombok.Getter;
import lombok.Setter;

/** 运营查询发送记录的筛选条件；templateId 由查询服务按模板编码解析后填入。 */
@Getter
@Setter
public class NotificationRequestFilter {

    /** 来源系统精确值，可空。 */
    private String sourceSystem;

    /** 来源事件键精确值，可空。 */
    private String sourceEventKey;

    /** 模板编码精确值，可空。 */
    private String templateCode;

    /** 模板编码解析出的身份；编码为空时为空。 */
    private Long templateId;

    /** 至少一个投递处于该状态，可空。 */
    private NotificationDeliveryStatus deliveryStatus;

    /** 创建时间下限（含），必填。 */
    private Instant createdFrom;

    /** 创建时间上限（不含），必填。 */
    private Instant createdTo;
}
