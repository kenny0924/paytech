package com.paytech.channel.connector.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按通知定义从已验签 Body 读出的支付结果通知；只作为 Payment 查单的信号，不采信其中的状态。 */
@Getter
@AllArgsConstructor
public class ChannelPaymentNotice {
    /** 商户订单号对应的 Payment 支付尝试 ID。 */
    private final long attemptId;

    /** 商户订单号原文（十进制字符串）。 */
    private final String merchantReference;

    /** 渠道交易号；未携带为 null。 */
    private final String channelTransactionRef;

    /** 渠道原始状态；未携带为 null。 */
    private final String status;

    /** 渠道事件时间；未携带或格式不符为 null。 */
    private final Instant eventTime;
}
