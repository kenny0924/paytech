package com.paytech.acquiring.domain.refund;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 退款创建持有的数据库对象；原退款分支无需支付与额度记录。 */
@Getter
@AllArgsConstructor
public class RefundCreationContext {
    /** 已按可信 Client 核对归属的订单。 */
    private final CommerceOrder order;

    /** 新退款验证及 SQL 并发保护使用的支付事实；原退款分支为空。 */
    private final OrderPaymentProjection payment;

    /** 新退款预占的本地金额位置；原退款分支为空。 */
    private final OrderRefundPosition position;

    /** 原退款，或本次已构造、待保存的退款。 */
    private final RefundRequest refund;

    /** 检查已加载的支付、订单与额度是否一致，并验证本次申请金额。 */
    public void validateCreation() {
        if (position == null || position.getOrderId() != order.getOrderId()
            || !order.getCurrency().equals(position.getCurrency())) {
            throw new ErrorException("退款金额位置缺失或归属不一致");
        }
        position.validateAmounts();
        if (payment == null || payment.getOrderId() != order.getOrderId()
            || payment.getIntentStatus() != PaymentIntentStatus.SUCCEEDED || payment.getSuccessfulAttemptId() == null
            || payment.getPaymentIntentId() <= 0 || payment.getVersion() < 1
            || !order.getCurrency().equals(payment.getCurrency()) || payment.getCapturedAmount() == null
            || order.getTotalAmount() == null || payment.getCapturedAmount().compareTo(order.getTotalAmount()) != 0
            || position.getRefundableBaseAmount().compareTo(payment.getCapturedAmount()) != 0) {
            throw new ErrorException("已支付订单缺少一致的退款权威投影");
        }
        if (position.refundableAmount().compareTo(refund.getRefundAmount()) < 0) {
            throw new FailException(CommerceCheckoutErrorCode.REFUND_AMOUNT_EXCEEDS_REFUNDABLE);
        }
    }
}
