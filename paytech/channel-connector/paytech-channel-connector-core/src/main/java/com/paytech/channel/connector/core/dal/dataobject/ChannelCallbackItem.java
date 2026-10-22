package com.paytech.channel.connector.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackTargetType;

import lombok.Getter;
import lombok.Setter;

/** t_channel_callback_item：回调信封中一条不可变的归一化与目标映射证据。 */
@Getter
@Setter
public class ChannelCallbackItem {
    /** 回调项内部标识。 */
    private Long channelCallbackItemId;

    /** 所属回调信封。 */
    private Long channelCallbackId;

    /** 信封内从 1 开始的序号；支付结果通知只有 1 项。 */
    private Integer itemNo;

    /** 事件类型；支付结果通知为 PAYMENT_RESULT。 */
    private String eventType;

    /** 渠道交易编号；通知未携带时为空。 */
    private String externalTransactionId;

    /** 目标类型。 */
    private CallbackTargetType targetType;

    /** 目标 ID；未匹配时为空。 */
    private Long targetId;

    /** 通知中的渠道原始状态；未携带时为空。 */
    private String normalizedStatus;

    /** 渠道声明的事件时间；未携带或格式不符时为空。 */
    private Instant channelEventTime;

    /** 映射结论。 */
    private CallbackMappingStatus mappingStatus;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
