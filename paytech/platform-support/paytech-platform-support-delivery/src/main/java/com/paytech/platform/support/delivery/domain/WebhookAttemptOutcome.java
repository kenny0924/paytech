package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 HTTP 发送的结果；不含响应体或异常原文。 */
@Getter
@AllArgsConstructor
public class WebhookAttemptOutcome {

    /** 端点返回 2xx。 */
    private final boolean succeeded;

    /** 收到响应时的状态码，否则为空。 */
    private final Integer httpStatus;

    /** 失败原因，成功时为空。 */
    private final WebhookFailureCode failureCode;

    /** 发出请求到结束的毫秒数。 */
    private final Integer durationMs;

    /** 收到响应：2xx 成功，其余（含 3xx）记 HTTP_STATUS。 */
    public static WebhookAttemptOutcome response(int httpStatus, int durationMs) {
        boolean succeeded = httpStatus >= 200 && httpStatus < 300;
        return new WebhookAttemptOutcome(succeeded, httpStatus, succeeded ? null : WebhookFailureCode.HTTP_STATUS,
            durationMs);
    }

    /** 未收到响应的失败。 */
    public static WebhookAttemptOutcome failure(WebhookFailureCode failureCode, Integer durationMs) {
        return new WebhookAttemptOutcome(false, null, failureCode, durationMs);
    }
}
