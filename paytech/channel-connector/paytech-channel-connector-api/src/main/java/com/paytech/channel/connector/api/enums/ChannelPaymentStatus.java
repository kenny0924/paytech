package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道已受理操作时，由 channel-connector 从 Platform 标准 API 输出解释出的支付状态（D06）。
 * <li>PAY 只取 SUCCEEDED、PENDING、FAILED；QUERY 另有 CLOSED，渠道无此交易时为 NOT_FOUND（此时结论为 FAIL）。</li>
 * <li>输出缺失或取值不认识时为空，调用方按结果未知处理。</li>
 */
@Getter
@AllArgsConstructor
public enum ChannelPaymentStatus implements CodeEnum {
    /** 渠道确认收款成功。 */
    SUCCEEDED("SUCCEEDED"),

    /** 待消费者认证或渠道仍在处理。 */
    PENDING("PENDING"),

    /** 渠道已创建交易但最终拒绝。 */
    FAILED("FAILED"),

    /** QUERY：渠道订单已关闭且未支付（例如持卡人放弃验证后超时关闭）。 */
    CLOSED("CLOSED"),

    /** QUERY：渠道没有这笔交易，结论为 FAIL。 */
    NOT_FOUND("NOT_FOUND");

    /** 合同中的稳定代码。 */
    private final String code;
}
