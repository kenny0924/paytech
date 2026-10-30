package com.paytech.acquiring.domain.order;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布 OrderPaymentSucceeded 时回读的本库事实：订单冻结商业字段、订单支付投影与支付所依据的税务计算；任一可为空，由发布方判定能否发布。 */
@Getter
@AllArgsConstructor
public class OrderPaymentEventSource {
    /** 订单、冻结上下文与法律事实的商业字段；订单不存在时为空。 */
    private final OrderCommercialContextView context;

    /** 订单唯一支付投影；不存在时为空。 */
    private final OrderPaymentProjection payment;

    /** 订单当前有效报价（支付所依据）的税务计算；税务服务上线前的历史订单为空。 */
    private final OrderTaxCalculation tax;

    /**
     * 判断能否为登记的成功尝试发布
     * <li>订单存在且已冻结完整上下文，支付投影的成功尝试等于登记时的尝试。</li>
     *
     * @param paymentAttemptId 补偿任务登记的成功尝试
     * @return 可发布时为 true
     */
    public boolean publishable(long paymentAttemptId) {
        return context != null && context.hasContext() && context.isComplete() && payment != null
            && payment.getSuccessfulAttemptId() != null && payment.getSuccessfulAttemptId() == paymentAttemptId;
    }
}
