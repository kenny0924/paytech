package com.paytech.acquiring.service.processor.refund.request;

import com.paytech.acquiring.domain.order.CommerceDecimalRules;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建退款的内部输入；传输幂等 Key 不进入业务流程。 */
@Getter
@AllArgsConstructor
public class CreateRefundRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 当前 Client 的公开订单号。 */
    private final String orderNo;

    /** Client 命名空间内永久唯一的业务退款号。 */
    private final String clientRefundId;

    /** 以订单币种主单位表示的金额文本。 */
    private final String amount;

    /** 退款原因代码。 */
    private final String reasonCode;

    /** 可空的非敏感退款说明。 */
    private final String reasonSummary;

    /** 取得订单币种后验证正数、范围和币种精度，不隐式舍入。 */
    public Money refundAmount(String currency) {
        try {
            return Money.of(currency, CommerceDecimalRules.parsePositive(amount));
        } catch (IllegalArgumentException | InvalidParameterException exception) {
            throw new FailException(CommonErrorCode.VALIDATION_ERROR, null, exception);
        }
    }
}
