package com.paytech.platform.support.api.notification.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 普通通知受理结果；不表示邮件已发出或送达。 */
public class SubmitNotificationResponseBody {

    /**
     * 通知请求 ID（64 位，字符串传输）
     * <li>按租户权限解析为 0 人时为空：未受理、未落库。</li>
     */
    private final String requestId;

    /** 本次受理的独立收件人数。 */
    private final int acceptedCount;

    /** 本次创建的投递记录数；与 acceptedCount 相同，按租户权限解析为 0 人时为 0。 */
    private final int deliveryCount;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SubmitNotificationResponseBody(@JsonProperty("requestId") String requestId,
        @JsonProperty("acceptedCount") int acceptedCount, @JsonProperty("deliveryCount") int deliveryCount) {
        this.requestId = requestId;
        this.acceptedCount = acceptedCount;
        this.deliveryCount = deliveryCount;
    }

    /** 通知请求 ID */
    public String getRequestId() {
        return requestId;
    }

    /** 受理的独立收件人数 */
    public int getAcceptedCount() {
        return acceptedCount;
    }

    /** 创建的投递记录数 */
    public int getDeliveryCount() {
        return deliveryCount;
    }
}
