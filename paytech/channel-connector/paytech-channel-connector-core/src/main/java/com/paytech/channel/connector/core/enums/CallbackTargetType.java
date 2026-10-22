package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 回调项映射到的业务对象类型（t_channel_callback_item.target_type）。 */
@Getter
@AllArgsConstructor
public enum CallbackTargetType implements CodeEnum {
    /** 支付结果通知按商户订单号映射到 Payment Attempt（target_id 为 Attempt ID）。 */
    PAYMENT_ATTEMPT("PAYMENT_ATTEMPT"),

    /** Payment 动作（本期不写入）。 */
    PAYMENT_ACTION("PAYMENT_ACTION"),

    /** 渠道退款（本期不写入）。 */
    CHANNEL_REFUND("CHANNEL_REFUND"),

    /** 渠道争议通知（本期不写入）。 */
    CHANNEL_DISPUTE_NOTICE("CHANNEL_DISPUTE_NOTICE"),

    /** 渠道争议动作（本期不写入）。 */
    CHANNEL_DISPUTE_ACTION("CHANNEL_DISPUTE_ACTION"),

    /** 无法确定目标。 */
    UNKNOWN("UNKNOWN");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}
