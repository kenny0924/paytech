package com.paytech.commerce.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commerce.domain.order.CommerceDecimalRules;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

import lombok.*;

/** 单笔 Order 的强一致可退款金额位置。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundPosition {
    /** 本库预生成的退款位置主键。 */
    private long orderRefundPositionId;

    /** 所属 Commerce Order 主键。 */
    private long orderId;

    /** 成功支付后建立的可退款基准金额。 */
    private BigDecimal refundableBaseAmount;

    /** 未完成退款请求占用金额。 */
    private BigDecimal reservedAmount;

    /** 已确认成功退款累计金额。 */
    private BigDecimal refundedAmount;

    /** ISO 4217 三位币种代码。 */
    private String currency;

    /** 条件更新使用的单调版本。 */
    private long version;

    /** 本记录创建 UTC 时刻。 */
    private Instant createTime;

    /** 本记录最后更新 UTC 时刻。 */
    private Instant updateTime;

    /** 当前可退金额；调用前须确认持久化金额不变量。 */
    public BigDecimal refundableAmount() {
        return refundableBaseAmount.subtract(reservedAmount).subtract(refundedAmount);
    }

    /** 检查自身金额范围、币种精度、预占与已退之和，不读取其他记录。 */
    public void validateAmounts() {
        if (version < 1 || refundableBaseAmount == null || reservedAmount == null || refundedAmount == null
            || refundableBaseAmount.signum() < 0 || reservedAmount.signum() < 0 || refundedAmount.signum() < 0
            || refundableAmount().signum() < 0) {
            throw new ErrorException("退款金额位置不一致");
        }
        try {
            for (var amount : List.of(refundableBaseAmount, reservedAmount, refundedAmount)) {
                CommerceDecimalRules.requireRange(amount);
                Money.of(currency, amount);
            }
        } catch (IllegalArgumentException | InvalidParameterException exception) {
            throw new ErrorException("退款金额币种精度不一致", exception);
        }
    }
}
