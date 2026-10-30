package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道身份不就绪的原因，按"最接近满足"顺序判定，只返回第一个不满足的一项
 * <li>消费者：commercial 开通项前置门禁 ④ 原样作为原因码，运营控制台按原因码取文案与"去配置 MID"跳转。</li>
 */
@Getter
@AllArgsConstructor
public enum ChannelIdentityReadinessReason implements CodeEnum {
    /** 该 OE 没有 MID、渠道账号、渠道均 ACTIVE 的可用 MID。 */
    MID_NOT_FOUND("MID_NOT_FOUND"),

    /** 有可用 MID，但没有该支付方式的 ENABLED 能力行。 */
    PAYMENT_METHOD_NOT_SUPPORTED("PAYMENT_METHOD_NOT_SUPPORTED"),

    /** 有该支付方式的能力行，但 MCC 都不相同。 */
    MCC_NOT_COVERED("MCC_NOT_COVERED"),

    /** 支付方式与 MCC 覆盖，但币种既不相同也不是 *。 */
    CURRENCY_NOT_SUPPORTED("CURRENCY_NOT_SUPPORTED"),

    /** 能力行覆盖该组合，但任一体系 / 国家取值下都没有 ACTION.PAY 或 ACTION.AUTHORIZE 为 true。 */
    PAYMENT_ACTION_NOT_SUPPORTED("PAYMENT_ACTION_NOT_SUPPORTED");

    /** HTTP 与业务比较使用的稳定代码。 */
    private final String code;
}
