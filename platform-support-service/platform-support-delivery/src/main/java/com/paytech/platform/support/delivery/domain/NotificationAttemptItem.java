package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次发送尝试，不含供应商请求号与消息标识。 */
@Getter
@AllArgsConstructor
public class NotificationAttemptItem {

    /** 尝试身份。 */
    private final long attemptId;

    /** 实际使用的账号编码；账号已不存在时为空。 */
    private final String providerCode;

    /** 尝试状态。 */
    private final NotificationAttemptStatus status;

    /** 发送响应码，可空。 */
    private final String responseCode;

    /** 结果确认来源，可空。 */
    private final NotificationResolutionSource resolutionSource;

    /** 确认结果码，可空。 */
    private final String resolutionCode;

    /** 开始时间。 */
    private final Instant startedTime;

    /** 发送调用结束时间，可空。 */
    private final Instant finishedTime;

    /** 结果确认时间，可空。 */
    private final Instant resolvedTime;
}
