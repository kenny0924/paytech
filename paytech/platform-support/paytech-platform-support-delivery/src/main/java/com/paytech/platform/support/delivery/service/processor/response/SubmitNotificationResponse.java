package com.paytech.platform.support.delivery.service.processor.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 原子受理结果；不表示供应商已受理或邮件已送达。 */
@Getter
@RequiredArgsConstructor
public class SubmitNotificationResponse {

    /** 通知请求标识。 */
    private final long requestId;

    /** 本次完整受理的独立收件人数。 */
    private final int acceptedCount;
}
