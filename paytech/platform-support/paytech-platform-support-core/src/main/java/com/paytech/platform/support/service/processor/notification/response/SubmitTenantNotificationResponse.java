package com.paytech.platform.support.service.processor.notification.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 按租户权限解析收件人的受理结果；解析为 0 人时未受理。 */
@Getter
@RequiredArgsConstructor
public class SubmitTenantNotificationResponse {

    /** 通知请求标识；解析为 0 人时为空。 */
    private final Long requestId;

    /** 本次创建的投递记录数，即解析出的独立收件人数。 */
    private final int deliveryCount;
}
