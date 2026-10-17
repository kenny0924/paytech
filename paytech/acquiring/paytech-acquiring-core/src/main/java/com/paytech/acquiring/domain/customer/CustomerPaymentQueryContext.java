package com.paytech.commerce.domain.customer;

import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentReference;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已授权支付查询的本地上下文，只持有本次读取的数据，不保存 Token 或外部依赖。 */
@Getter
@AllArgsConstructor
public class CustomerPaymentQueryContext {
    /** Token 所属订单，提供原访问期限。 */
    private final CommerceOrder order;

    /** 原订单的唯一 Intent 投影，不以本地支付状态代替 Payment 当前结果。 */
    private final OrderPaymentProjection payment;

    /** 当前请求查询的支付提交业务编号。 */
    private final long submissionId;

    /** 从本地归属构造受限查询坐标，调用方不能自行指定订单或 Intent。 */
    public PaymentReference paymentReference() {
        return new PaymentReference(submissionId, order.getOrderId(), payment.getPaymentIntentId());
    }
}
