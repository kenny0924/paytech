package com.paytech.payment.execution.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新提交的渠道交互输入：只用于本次渠道请求，不持久化。 */
@Getter
@AllArgsConstructor
public class PayChannelInput {
    /** 交互完成后的回跳地址 */
    private final String returnUrl;

    /** 不含个人信息的付款人引用 */
    private final String consumerReference;

    /** 消费者 IP 地址，可为空 */
    private final String consumerIpAddress;

    /** 消费者浏览器 User-Agent，可为空 */
    private final String consumerUserAgent;

    @Override
    public String toString() {
        return "PayChannelInput[REDACTED]";
    }
}
