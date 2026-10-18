package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.domain.WebhookEventTypes;

import lombok.Getter;
import lombok.Setter;

/** Webhook 端点持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookEndpoint {

    /** 端点主键。 */
    private Long endpointId;

    /** 所属 CLIENT 租户。 */
    private Long tenantId;

    /** HTTPS 接收地址明文。 */
    private String url;

    /** 订阅事件类型的 JSON 字符串数组。 */
    private String eventTypes;

    /** 端点状态。 */
    private WebhookEndpointStatus status;

    /** 条件更新版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /** 端点启用且订阅了该事件类型。 */
    public boolean accepts(String eventType) {
        return status == WebhookEndpointStatus.ACTIVE && WebhookEventTypes.parse(eventTypes).contains(eventType);
    }
}
