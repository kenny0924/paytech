package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送记录详情；不含收件邮箱、参数值和供应商流水号。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDetailResponseBody {

    /** 请求 ID。 */
    private String requestId;

    /** 来源系统。 */
    private String sourceSystem;

    /** 来源事件键。 */
    private String sourceEventKey;

    /** 模板编码。 */
    private String templateCode;

    /** 租户 ID，平台通知为空。 */
    private String tenantId;

    /** 默认语言。 */
    private String defaultLocale;

    /** 默认参数名称，不含值。 */
    private List<String> parameterKeys;

    /** 受理时选择的路由 ID。 */
    private String routeId;

    /** 受理时间，UTC。 */
    private Instant createTime;

    /** 投递，按先后。 */
    private List<DeliveryItem> deliveries;
}
