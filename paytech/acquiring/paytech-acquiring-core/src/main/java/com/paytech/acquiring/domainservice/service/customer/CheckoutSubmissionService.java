package com.paytech.commerce.domainservice.service.customer;

import com.paytech.commerce.domain.customer.SubmitCustomerPaymentContext;

/** 支付受理阶段的本地原子保存，不调用其他 Service 或外部能力。 */
public interface CheckoutSubmissionService {
    /** REQUIRED 事务保存订单版本、首次法律证据与三份接受；已有接受保持不变。 */
    void saveSubmissionContext(SubmitCustomerPaymentContext context);
    /** REQUIRED 事务同时保存 Intent 关联并确认订单仍允许支付，任一失败全部回滚。 */
    void bindPayment(SubmitCustomerPaymentContext context);
}
