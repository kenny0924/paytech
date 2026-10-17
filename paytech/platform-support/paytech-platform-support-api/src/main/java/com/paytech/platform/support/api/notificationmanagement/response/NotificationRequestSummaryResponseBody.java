package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发送记录列表项；不含收件邮箱与参数值。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestSummaryResponseBody {

    /** 请求 ID，十进制字符串。 */
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

    /** 收件人数。 */
    private int deliveryCount;

    /**
     * 各投递状态的数量
     * <li>键为 PENDING、PROCESSING、RETRY_WAIT、SENT、DELIVERED、FAILED、UNKNOWN。</li>
     */
    private Map<String, Integer> statusCounts;

    /** 受理时间，UTC。 */
    private Instant createTime;
}
