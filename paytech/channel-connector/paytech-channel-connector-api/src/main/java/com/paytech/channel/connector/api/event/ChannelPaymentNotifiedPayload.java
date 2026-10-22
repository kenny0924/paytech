package com.paytech.channel.connector.api.event;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * ChannelPaymentNotified Schema v1 的 Payload（事件目录 paytech-channel-connector-api/event/ChannelPaymentNotified/v1；3DS 全链路
 * technique 3.3.5）
 * <li>channel-connector 收到并验签通过一条支付渠道的支付结果通知后发布；Payment 只把它当作“去查单”的信号，不采信其中的状态。</li>
 * <li>聚合为一次渠道回调：aggregateType={@link #AGGREGATE_TYPE}、aggregateId=channel_callback_id、aggregateVersion 固定为 1（回调不可变），
 * strictOrder=false。</li>
 * <li>不含通知原文、签名、卡数据或金额；ID 一律十进制字符串，时间为 UTC RFC 3339。</li>
 */
@Getter
@Setter
public class ChannelPaymentNotifiedPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "ChannelPaymentNotified";

    /** Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：一次渠道回调。 */
    public static final String AGGREGATE_TYPE = "CHANNEL_CALLBACK";

    /** 聚合版本：回调记录不可变，固定为 1。 */
    public static final long AGGREGATE_VERSION = 1;

    /** Payload 字段及顺序。 */
    public static final List<String> FIELDS = List.of("channelId", "channelAccountId", "merchantReference",
        "channelTransactionRef", "reportedStatus", "receivedAt");

    /** 收到通知的渠道 ID。 */
    private String channelId;

    /** 收到通知的渠道技术账号 ID（通知路径中的账号）。 */
    private String channelAccountId;

    /** 商户订单号：Payment 支付尝试 ID（PayerMax outTradeNo）。 */
    private String merchantReference;

    /** 渠道交易编号；通知未携带时为空。 */
    private String channelTransactionRef;

    /** 通知中的渠道原始状态，仅供排障；通知未携带时为空。 */
    private String reportedStatus;

    /** channel-connector 首次收到通知的时间，UTC RFC 3339。 */
    private String receivedAt;
}
