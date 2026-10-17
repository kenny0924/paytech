package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 从供应商投递事件中解析出的白名单字段
 * <li>不含收件地址、邮件头或诊断原文</li>
 * <li>投递结果与投诉互斥：投诉事件的 outcome 为空、complaintCode 非空；不处理的事件两者都为空</li>
 */
@Getter
@AllArgsConstructor
public class NotificationProviderEvent {

    /** 事件类型标签，用于指标：DELIVERY、BOUNCE、COMPLAINT、REJECT、OTHER。 */
    private final String eventType;

    /** 由发送请求号标签解析出的尝试身份；标签缺失或不是本平台尝试时为空。 */
    private final Long attemptId;

    /** 供应商消息标识；不处理的事件可为空。 */
    private final String messageId;

    /** 投递结果；投诉与不处理的事件为空。 */
    private final NotificationEventOutcome outcome;

    /** 投递结果的安全分类码，送达为空。 */
    private final String outcomeCode;

    /** 投诉的安全分类码；非投诉事件为空。 */
    private final String complaintCode;

    /** 事件发生时间，取自供应商事件。 */
    private final Instant occurredTime;

    /** 是否为投诉事件。 */
    public boolean isComplaint() {
        return complaintCode != null;
    }

    /** 是否为不处理的事件类型。 */
    public boolean isIgnored() {
        return outcome == null && complaintCode == null;
    }
}
