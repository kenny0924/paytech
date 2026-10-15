package com.paytech.platform.support.api.notification.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 普通通知受理结果；不表示邮件已发出或送达。 */
public class SubmitNotificationResponseBody {

    /** 通知请求 ID（64 位，字符串传输）。 */
    private final String requestId;

    /** 本次受理的独立收件人数。 */
    private final int acceptedCount;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SubmitNotificationResponseBody(@JsonProperty("requestId") String requestId,
        @JsonProperty("acceptedCount") int acceptedCount) {
        this.requestId = requestId;
        this.acceptedCount = acceptedCount;
    }

    /** 通知请求 ID */
    public String getRequestId() {
        return requestId;
    }

    /** 受理的独立收件人数 */
    public int getAcceptedCount() {
        return acceptedCount;
    }
}
