package com.paytech.acquiring.api.client.v1.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单当前有效报价的金额状态（t_commerce_order.amount_status）
 * <li>Client API 与 Customer API 的 amountStatus 共用本 Code；只有 FINAL 可以支付。</li>
 */
@Getter
@AllArgsConstructor
public enum OrderAmountStatus implements CodeEnum {
    /** 建单按 Client 声明国家的暂估税额，消费者提交账单地址后重算；不可支付。 */
    ESTIMATED("ESTIMATED"),

    /** 最终金额：消费者资料提交后计算完成，或税务不由平台计算（税额为 Client 自报值）。 */
    FINAL("FINAL"),

    /** 无法正确计税：税额、舍入额与总额为空，原因见 taxBlockReason；不可支付。 */
    BLOCKED("BLOCKED");

    /** 持久化及对外传输的稳定代码。 */
    private final String code;
}
