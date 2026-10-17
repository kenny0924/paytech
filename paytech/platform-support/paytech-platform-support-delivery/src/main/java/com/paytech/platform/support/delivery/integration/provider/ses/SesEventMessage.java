package com.paytech.platform.support.delivery.integration.provider.ses;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 队列中的一条事件消息；消息体可能含收件地址，禁止日志输出。 */
@Getter
@AllArgsConstructor
public class SesEventMessage {

    /** 消息体：SES 事件 JSON 或 SNS 信封。 */
    private final String body;

    /** 本次接收的删除凭据。 */
    private final String receiptHandle;

    /** 不输出消息体与删除凭据。 */
    @Override
    public String toString() {
        return "SesEventMessage";
    }
}
