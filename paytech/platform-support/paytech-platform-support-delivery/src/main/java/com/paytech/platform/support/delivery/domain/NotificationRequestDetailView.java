package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发送记录详情，组合请求、模板、投递、尝试与账号五表。 */
@Getter
@AllArgsConstructor
public class NotificationRequestDetailView {

    /** 请求身份。 */
    private final long requestId;

    /** 来源系统。 */
    private final String sourceSystem;

    /** 来源事件键。 */
    private final String sourceEventKey;

    /** 模板编码；模板已不存在时为空。 */
    private final String templateCode;

    /** 租户，平台通知为空。 */
    private final Long tenantId;

    /** 默认语言。 */
    private final String defaultLocale;

    /** 默认参数名称，升序；不含值。 */
    private final List<String> parameterKeys;

    /** 受理时选择的路由。 */
    private final long routeId;

    /** 受理时间。 */
    private final Instant createTime;

    /** 投递，按创建先后。 */
    private final List<NotificationDeliveryItem> deliveries;
}
